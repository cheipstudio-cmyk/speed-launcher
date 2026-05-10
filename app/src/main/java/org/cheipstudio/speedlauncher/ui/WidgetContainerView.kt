package org.cheipstudio.speedlauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.WidgetItem
import org.cheipstudio.speedlauncher.data.WidgetStore
import org.cheipstudio.speedlauncher.widgets.WidgetHostController
import kotlin.math.abs

/**
 * v240: Container multi-widget per una singola pagina home.
 *
 * - Carica widget dal WidgetStore per la pagina specificata
 * - Monta ogni widget come AppWidgetHostView con LayoutParams calcolati da (cellX, cellY, spanX, spanY)
 * - Long press su widget → modal con opzione Rimuovi
 *
 * Per v2.4.0/2.4.1: niente drag/resize handles. Solo posizionamento celle-based + add/remove.
 */
class WidgetContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var pageIndex: Int = 0
        set(value) {
            field = value
            refresh()
        }

    private val store = WidgetStore(context)
    /** v271: callback per swipe orizzontale - HomeView lo gira al pagedHome */
    var onHorizontalSwipe: ((MotionEvent) -> Unit)? = null
    private var horizSwipeDetected = false
    private var hostController: WidgetHostController? = null
    private val mountedViews = mutableMapOf<String, View>()  // uuid → view montata
    
    // v244: callback long press su area vuota (no widget sotto) → apre HomeMenuSheet
    var onEmptyLongPress: (() -> Unit)? = null
    private val emptyHoldRunnable = Runnable {
        try { HapticHelper.longPress(this) } catch (_: Throwable) {}
        onEmptyLongPress?.invoke()
    }

    // Long press detection per widget montati
    private val holdHandler = Handler(Looper.getMainLooper())
    private var pressedWidgetUuid: String? = null
    private var pressX = 0f
    private var pressY = 0f
    private val holdRunnable = Runnable {
        val uuid = pressedWidgetUuid ?: return@Runnable
        // v260: rispetta setting hapticEnabled
        HapticHelper.longPress(this)
        showWidgetActions(uuid)
    }

    init {
        // v272: clickable per ricevere touch anche su area vuota (per swipe cambio pagina)
        isClickable = true
        // v244: clip false, lascia che i widget disegnino oltre i bounds se necessario
        clipChildren = false
        clipToPadding = false
    }
    
    fun setHostController(controller: WidgetHostController) {
        hostController = controller
        refresh()
    }

    /** Ricarica tutti i widget della pagina dal store */
    fun refresh() {
        val host = hostController ?: return
        removeAllViews()
        mountedViews.clear()
        val items = store.loadPage(pageIndex)
        for (item in items) {
            mountWidget(item, host)
        }
        // v241: forza layout pass dopo mount (caso width già > 0 al refresh)
        if (width > 0 && height > 0) applyLayoutToChildren()
        else post { 
            requestLayout()
            if (width > 0 && height > 0) applyLayoutToChildren()
        }
    }

    /** Aggiunge un nuovo widget (chiamato dopo bind+configure successo) */
    fun addWidget(appWidgetId: Int) {
        val existing = store.loadPage(pageIndex)
        // v245: default più alto - 4x3 per dare al widget spazio decente
        val sizesToTry = listOf(
            4 to 3, 4 to 2, 4 to 1, 2 to 3, 2 to 2, 2 to 1, 1 to 1
        )
        for ((sx, sy) in sizesToTry) {
            val (cellX, cellY) = findFirstFreeCell(existing, sx, sy) ?: continue
            val item = WidgetItem(
                uuid = "w_${System.currentTimeMillis()}_${appWidgetId}",
                appWidgetId = appWidgetId,
                pageIndex = pageIndex,
                cellX = cellX,
                cellY = cellY,
                spanX = sx,
                spanY = sy
            )
            store.addWidget(item)
            refresh()
            return
        }
        // Niente spazio per nessuna taglia: avviso utente e abort
        try {
            android.widget.Toast.makeText(
                context,
                context.getString(org.cheipstudio.speedlauncher.R.string.widget_no_space_in_page),
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (_: Throwable) {}
        // Pulisci appWidgetId allocato per evitare leak
        try { hostController?.deleteWidget(appWidgetId) } catch (_: Throwable) {}
    }

    /** Rimuove un widget specifico */
    fun removeWidget(uuid: String) {
        val items = store.loadPage(pageIndex)
        val target = items.firstOrNull { it.uuid == uuid } ?: return
        // Rimuovi da AppWidgetHost
        try { hostController?.deleteWidget(target.appWidgetId) } catch (_: Throwable) {}
        // Rimuovi dal store
        store.removeWidget(pageIndex, uuid)
        refresh()
    }

    /** Trova prima cella libera per un widget di dimensione spanX×spanY. null se niente spazio. */
    private fun findFirstFreeCell(existing: List<WidgetItem>, spanX: Int, spanY: Int): Pair<Int, Int>? {
        val occupied = Array(WidgetItem.GRID_ROWS) { BooleanArray(WidgetItem.GRID_COLS) }
        for (w in existing) {
            for (dy in 0 until w.spanY) {
                for (dx in 0 until w.spanX) {
                    val y = w.cellY + dy
                    val x = w.cellX + dx
                    if (y in 0 until WidgetItem.GRID_ROWS && x in 0 until WidgetItem.GRID_COLS) {
                        occupied[y][x] = true
                    }
                }
            }
        }
        // First-fit row-by-row, top-to-bottom
        for (y in 0..WidgetItem.GRID_ROWS - spanY) {
            outer@ for (x in 0..WidgetItem.GRID_COLS - spanX) {
                for (dy in 0 until spanY) {
                    for (dx in 0 until spanX) {
                        if (occupied[y + dy][x + dx]) continue@outer
                    }
                }
                return x to y
            }
        }
        return null
    }

    private fun mountWidget(item: WidgetItem, host: WidgetHostController) {
        try {
            val info = host.appWidgetManager.getAppWidgetInfo(item.appWidgetId)
            if (info == null) {
                // Provider rimosso, pulisco da store
                store.removeWidget(pageIndex, item.uuid)
                return
            }
            val view = host.createView(item.appWidgetId, info)
            view.setAppWidget(item.appWidgetId, info)
            // Aggiungo come child con LayoutParams calcolati al layout pass
            addView(view, layoutParamsForItem(item))
            mountedViews[item.uuid] = view
            // Forza updateAppWidgetOptions con dimensioni calcolate per dare al widget min/max
            post { updateWidgetOptions(item, view) }
        } catch (_: Throwable) {}
    }

    private fun layoutParamsForItem(item: WidgetItem): FrameLayout.LayoutParams {
        // v241: dimensioni iniziali calcolate al volo se width disponibile
        val lp = if (width > 0 && height > 0) {
            val cellW = width / WidgetItem.GRID_COLS
            val cellH = height / WidgetItem.GRID_ROWS
            FrameLayout.LayoutParams(cellW * item.spanX, cellH * item.spanY).apply {
                leftMargin = cellW * item.cellX
                topMargin = cellH * item.cellY
            }
        } else {
            // Fallback: match_parent così il widget non è 0x0 mentre aspettiamo onSizeChanged
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        return lp
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // v241: assicura layout corretto al primo attach
        post { applyLayoutToChildren() }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        applyLayoutToChildren()
    }

    private fun applyLayoutToChildren() {
        val cols = WidgetItem.GRID_COLS
        val rows = WidgetItem.GRID_ROWS
        if (width <= 0 || height <= 0) return
        val cellW = width / cols
        val cellH = height / rows
        val items = store.loadPage(pageIndex)
        for (item in items) {
            val view = mountedViews[item.uuid] ?: continue
            val lp = view.layoutParams as? FrameLayout.LayoutParams ?: continue
            // v279: forzo spanY = GRID_ROWS (altezza piena sempre)
            lp.width = cellW * item.spanX
            lp.height = cellH * WidgetItem.GRID_ROWS
            lp.leftMargin = (width - lp.width) / 2
            lp.topMargin = 0
            view.layoutParams = lp
            updateWidgetOptions(item, view)
        }
    }

    private fun updateWidgetOptions(item: WidgetItem, view: View) {
        try {
            val cellW = width / WidgetItem.GRID_COLS
            val cellH = height / WidgetItem.GRID_ROWS
            val widthDp = ((cellW * item.spanX) / resources.displayMetrics.density).toInt().coerceAtLeast(40)
            val heightDp = ((cellH * WidgetItem.GRID_ROWS) / resources.displayMetrics.density).toInt().coerceAtLeast(40)
            val opts = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            }
            AppWidgetManager.getInstance(context).updateAppWidgetOptions(item.appWidgetId, opts)
        } catch (_: Throwable) {}
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                pressX = ev.x; pressY = ev.y
                horizSwipeDetected = false
                pressedWidgetUuid = findWidgetAt(ev.x, ev.y)
                if (pressedWidgetUuid != null) {
                    holdHandler.postDelayed(holdRunnable, 700L)
                } else {
                    holdHandler.postDelayed(emptyHoldRunnable, 700L)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - pressX
                val dy = ev.y - pressY
                val adx = abs(dx); val ady = abs(dy)
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                // v271: rilevo swipe orizzontale → giro al pagedHome
                if (!horizSwipeDetected && adx > slop && adx > ady * 1.2f) {
                    horizSwipeDetected = true
                    holdHandler.removeCallbacks(holdRunnable)
                    holdHandler.removeCallbacks(emptyHoldRunnable)
                    pressedWidgetUuid = null
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    // v274: sintetizzo un DOWN al callback prima del MOVE (pagedHome ha bisogno di downX)
                    val synthDown = MotionEvent.obtain(
                        ev.downTime, ev.eventTime, MotionEvent.ACTION_DOWN,
                        pressX, pressY, 0
                    )
                    onHorizontalSwipe?.invoke(synthDown)
                    synthDown.recycle()
                }
                if (horizSwipeDetected) {
                    onHorizontalSwipe?.invoke(ev)
                    return true
                }
                if (adx > slop * 2 || ady > slop * 2) {
                    holdHandler.removeCallbacks(holdRunnable)
                    holdHandler.removeCallbacks(emptyHoldRunnable)
                    pressedWidgetUuid = null
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                holdHandler.removeCallbacks(holdRunnable)
                holdHandler.removeCallbacks(emptyHoldRunnable)
                pressedWidgetUuid = null
                if (horizSwipeDetected) {
                    onHorizontalSwipe?.invoke(ev)
                    horizSwipeDetected = false
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** v276: pubblico per check esterno (HomeView decide se long press home o widget) */
    fun isWidgetAt(localX: Float, localY: Float): Boolean = findWidgetAt(localX, localY) != null
    
    private fun findWidgetAt(x: Float, y: Float): String? {
        val items = store.loadPage(pageIndex)
        for (item in items) {
            val view = mountedViews[item.uuid] ?: continue
            val lp = view.layoutParams as? FrameLayout.LayoutParams ?: continue
            val left = lp.leftMargin
            val top = lp.topMargin
            val right = left + lp.width
            val bottom = top + lp.height
            if (x >= left && x < right && y >= top && y < bottom) {
                return item.uuid
            }
        }
        return null
    }

    private fun showWidgetActions(uuid: String) {
        try {
            val activity = context as? FragmentActivity ?: return
            val sheet = WidgetActionsSheet.newInstance(uuid, pageIndex)
            sheet.onRemove = { removedUuid -> removeWidget(removedUuid) }
            // v244: refresh dopo cambio dimensioni / spostamento pagina
            sheet.onChanged = { refresh() }
            sheet.show(activity.supportFragmentManager, "widget_actions")
        } catch (_: Throwable) {}
    }


    // ============= Picker e bind flow =============
    
    /** Apre il widget picker e sul risultato avvia bind+configure+add */
    fun openPicker() {
        val activity = context as? FragmentActivity ?: return
        val sheet = WidgetPickerSheet.newInstance(width, height)
        sheet.onWidgetSelected = { info -> bindAndAdd(info) }
        sheet.show(activity.supportFragmentManager, "widget_picker")
    }
    
    /** Avvia bind/configure flow per un nuovo widget. Risultato → addWidget(appWidgetId) */
    private fun bindAndAdd(info: android.appwidget.AppWidgetProviderInfo) {
        val controller = hostController ?: return
        val activity = context as? Activity ?: return
        val appWidgetId = controller.host.allocateAppWidgetId()
        val canBind = controller.appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
        if (!canBind) {
            val bindIntent = android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            controller.pendingBindWidget = info
            controller.pendingBindAppWidgetId = appWidgetId
            controller.pendingPlaceCallback = { _ -> addWidget(appWidgetId) }
            activity.startActivityForResult(bindIntent, WidgetHostController.REQ_BIND)
            return
        }
        if (info.configure != null) {
            val configIntent = android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            controller.pendingBindWidget = info
            controller.pendingBindAppWidgetId = appWidgetId
            controller.pendingPlaceCallback = { _ -> addWidget(appWidgetId) }
            activity.startActivityForResult(configIntent, WidgetHostController.REQ_CONFIGURE)
        } else {
            controller.markLastWidget(appWidgetId)
            addWidget(appWidgetId)
        }
    }
    
    /** Stub per compatibilità con WidgetSlotView API (ritorna false: niente edit mode in v2.4.1) */
    fun isInWidgetEditMode(): Boolean = false
    
    /** Numero di widget in questa pagina */
    fun widgetCount(): Int = mountedViews.size
}
