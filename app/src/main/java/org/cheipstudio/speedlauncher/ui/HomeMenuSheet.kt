package org.cheipstudio.speedlauncher.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.HomeItem
import org.cheipstudio.speedlauncher.data.HomeLayoutStore

class HomeMenuSheet : BottomSheetDialogFragment() {
    /** v283: true se la pagina corrente ha già un widget (nasconde "Aggiungi widget") */
    var currentPageHasWidget: Boolean = false


    var onSettings: (() -> Unit)? = null
    var onSorted: (() -> Unit)? = null
    var onManagePages: (() -> Unit)? = null
    var onAddWidget: (() -> Unit)? = null  // v228: aggiungi widget

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.sheet_home_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.menuWallpaper).setOnClickListener {
            dismissAllowingStateLoss()
            try {
                val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                startActivity(Intent.createChooser(intent, getString(R.string.menu_wallpaper)))
            } catch (_: Throwable) {}
        }
        view.findViewById<View>(R.id.menuSortAlpha)?.setOnClickListener {
            sortHomeAlphabetically()
            Toast.makeText(context, R.string.sort_done, Toast.LENGTH_SHORT).show()
            dismissAllowingStateLoss()
            onSorted?.invoke()
        }
        view.findViewById<View>(R.id.menuPages)?.setOnClickListener {
            dismissAllowingStateLoss()
            onManagePages?.invoke()
        }
        // v243+v283: nascondi "Aggiungi widget" se spazio widget OFF o se pagina ha già un widget
        val widgetMenuItem = view.findViewById<View>(R.id.menuAddWidget)
        val widgetOn = SpeedApp.instance.settingsRepository.showWidgetSlot.value == true
        val pageHasWidget = currentPageHasWidget
        if (!widgetOn || pageHasWidget) {
            widgetMenuItem?.visibility = View.GONE
        } else {
            widgetMenuItem?.visibility = View.VISIBLE
            widgetMenuItem?.setOnClickListener {
                dismissAllowingStateLoss()
                onAddWidget?.invoke()
            }
        }
        view.findViewById<View>(R.id.menuSettings).setOnClickListener {
            dismissAllowingStateLoss()
            onSettings?.invoke()
        }
    }

    /**
     * v24: riordina TUTTE le app pinnate sulla home (su tutte le pagine) in ordine alfabetico.
     * Mantiene le folder dove sono. Ridistribuisce su pagine se serve.
     */
    private fun sortHomeAlphabetically() {
        val ctx = context ?: return
        val store = HomeLayoutStore(ctx)
        val settings = SpeedApp.instance.settingsRepository
        val cols = settings.gridCols.value ?: 5
        val rows = settings.gridRows.value ?: 5
        val cellsPerPage = cols * rows
        val allApps = SpeedApp.instance.appRepository.apps.value ?: emptyList()
        val byKey = allApps.associateBy { it.key }

        // Carica tutto il layout esistente
        val existingItems = store.load()
        val folders = existingItems.filter { it.type == HomeItem.TYPE_FOLDER }
        val apps = existingItems.filter { it.type == HomeItem.TYPE_APP }

        // Ordina apps per label
        val sortedApps = apps.sortedBy { item ->
            byKey[item.key]?.label?.lowercase() ?: item.key
        }

        // Fold le folder rimangono dove sono (mantieni page+cellX+cellY); le app si distribuiscono nelle posizioni rimanenti
        // Strategia semplice: riempio tutte le pagine sequenzialmente saltando le celle occupate dalle folder
        val occupied = folders.associateBy { it.page * 10000 + it.cellY * cols + it.cellX }
        val newLayout = mutableListOf<HomeItem>()
        newLayout.addAll(folders)

        var idx = 0
        for (app in sortedApps) {
            // trova la prossima cella libera
            while (true) {
                val page = idx / cellsPerPage
                val posInPage = idx % cellsPerPage
                val cellY = posInPage / cols
                val cellX = posInPage % cols
                val key = page * 10000 + cellY * cols + cellX
                if (!occupied.containsKey(key)) {
                    newLayout.add(app.copy(page = page, cellX = cellX, cellY = cellY))
                    idx++
                    break
                }
                idx++
            }
        }

        // Salva pagina per pagina
        val maxPage = newLayout.maxOfOrNull { it.page } ?: 0
        for (p in 0..maxPage) {
            val pageItems = newLayout.filter { it.page == p }
            store.savePage(p, pageItems)
        }
    }
}
