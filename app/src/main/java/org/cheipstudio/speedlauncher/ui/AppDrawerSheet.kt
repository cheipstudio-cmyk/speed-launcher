package org.cheipstudio.speedlauncher.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.cheipstudio.speedlauncher.R
import org.cheipstudio.speedlauncher.SpeedApp
import org.cheipstudio.speedlauncher.data.AppInfo
import org.cheipstudio.speedlauncher.databinding.SheetAppDrawerBinding
import java.text.Normalizer

class AppDrawerSheet : BottomSheetDialogFragment() {

    private var _binding: SheetAppDrawerBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AppListAdapter
    private var allApps: List<AppInfo> = emptyList()

    /** v20: callback per long-press dal drawer */
    var onAppLongPress: ((AppInfo) -> Unit)? = null
    /** v122: callback richiamata quando il drawer si chiude */
    var onDismissCallback: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AppListAdapter(
            onClick = { app, source ->
                SpeedApp.instance.appRepository.launch(app, source)
                dismissAllowingStateLoss()
            },
            onLongPress = { app ->
                onAppLongPress?.invoke(app)
            }
        )
        applyDrawerLayout()
        // v30: setup Raccomandate
        // v88: observe live per garantire che la card si aggiorni quando le app si caricano
        // o quando l\'utente cambia recommendedMode/recommendedManualApps in Settings.
        fun setupRecommendedDrawer() {
            val aiOn = SpeedApp.instance.settingsRepository.aiLauncherMode.value == true
            if (aiOn) {
                _binding?.recommendedRow?.visibility = View.VISIBLE
                _binding?.recommendedRow?.refresh("drawer")
                _binding?.recommendedRow?.onAppClick = { app ->
                    SpeedApp.instance.appRepository.launch(app)
                    dismissAllowingStateLoss()
                }
                // v87: niente long press sulla dock raccomandate
                _binding?.recommendedRow?.onAppLongPress = null
            } else {
                _binding?.recommendedRow?.visibility = View.GONE
            }
        }
        setupRecommendedDrawer()
        // v88: observers per refresh live
        SpeedApp.instance.appRepository.apps.observe(viewLifecycleOwner) { setupRecommendedDrawer() }
        SpeedApp.instance.settingsRepository.aiLauncherMode.observe(viewLifecycleOwner) { setupRecommendedDrawer() }
        SpeedApp.instance.settingsRepository.recommendedMode.observe(viewLifecycleOwner) { setupRecommendedDrawer() }
        // v132: refresh icone drawer quando cambia forma
        SpeedApp.instance.settingsRepository.iconShape.observe(viewLifecycleOwner) {
            try { (_binding?.recycler?.adapter as? AppListAdapter)?.notifyDataSetChanged() } catch (_: Throwable) {}
        }
        // v139: refresh quando toggle label drawer cambia
        SpeedApp.instance.settingsRepository.showDrawerLabels.observe(viewLifecycleOwner) {
            try { (_binding?.recycler?.adapter as? AppListAdapter)?.notifyDataSetChanged() } catch (_: Throwable) {}
        }
        SpeedApp.instance.settingsRepository.recommendedManualApps.observe(viewLifecycleOwner) { setupRecommendedDrawer() }
        SpeedApp.instance.settingsRepository.recommendedCount.observe(viewLifecycleOwner) { setupRecommendedDrawer() }
        // v62: optimization recycler — fixed size + no overdraw
        binding.recycler.setHasFixedSize(true)
        binding.recycler.itemAnimator = null  // niente animazioni costose
        binding.recycler.adapter = adapter

        // v74+v120: parallasse + collapse delle raccomandate.
        // Bug precedente: translationY muoveva visivamente la riga ma lasciava lo spazio
        // allocato → lista non saliva, parte grigia rimaneva. Ora modifico anche
        // l'altezza del LayoutParams così la lista sale insieme.
        binding.recycler.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            private var totalScroll = 0
            private var originalHeight = -1
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                totalScroll = (totalScroll + dy).coerceAtLeast(0)
                val rec = _binding?.recommendedRow ?: return
                if (rec.visibility != View.VISIBLE) return
                if (originalHeight < 0 && rec.height > 0) originalHeight = rec.height
                if (originalHeight <= 0) return
                
                val marginPx = (12 * resources.displayMetrics.density).toInt()
                val maxScroll = (originalHeight + marginPx).toFloat()
                val progress = (totalScroll / maxScroll).coerceIn(0f, 1f)
                
                // v120: riduco anche l'altezza per far salire la lista insieme
                val lp = rec.layoutParams
                val newHeight = (originalHeight * (1f - progress)).toInt().coerceAtLeast(0)
                if (lp.height != newHeight) {
                    lp.height = newHeight
                    rec.layoutParams = lp
                }
                rec.alpha = 1f - progress
                rec.translationY = 0f
            }
        })

        SpeedApp.instance.appRepository.apps.observe(viewLifecycleOwner) { apps ->
            allApps = apps
            applyFilter(binding.searchInput.text?.toString().orEmpty())
        }
        // v177: refresh drawer quando un'app viene nascosta/mostrata
        SpeedApp.instance.settingsRepository.hiddenApps.observe(viewLifecycleOwner) {
            applyFilter(binding.searchInput.text?.toString().orEmpty())
        }

        // v208: hint dinamico in base modalità ricerca
        val mode = SpeedApp.instance.settingsRepository.searchMode.value
        binding.searchInput.hint = if (mode == 
            org.cheipstudio.speedlauncher.data.SettingsRepository.MODE_UNIVERSAL) {
            getString(R.string.search_universal)
        } else {
            getString(R.string.search_apps)
        }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        if (arguments?.getBoolean(ARG_FOCUS_SEARCH) == true) {
            binding.searchInput.post {
                binding.searchInput.requestFocus()
                requireContext().getSystemService<InputMethodManager>()
                    ?.showSoftInput(binding.searchInput, 0)
            }
        }
    }

    private fun applyDrawerLayout() {
        val layout = SpeedApp.instance.settingsRepository.drawerLayout.value
            ?: org.cheipstudio.speedlauncher.data.SettingsRepository.DRAWER_GRID4
        // v198: in landscape aumenta colonne per usare lo spazio orizzontale
        val isLandscape = resources.configuration.orientation == 
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val landscapeBoost = if (isLandscape) 2 else 0
        when (layout) {
            org.cheipstudio.speedlauncher.data.SettingsRepository.DRAWER_GRID3 -> {
                adapter.listMode = false
                binding.recycler.layoutManager = GridLayoutManager(requireContext(), 3 + landscapeBoost)
                _binding?.alphaScrollBar?.visibility = View.GONE
            }
            org.cheipstudio.speedlauncher.data.SettingsRepository.DRAWER_GRID5 -> {
                adapter.listMode = false
                binding.recycler.layoutManager = GridLayoutManager(requireContext(), 5 + landscapeBoost)
                _binding?.alphaScrollBar?.visibility = View.GONE
            }
            org.cheipstudio.speedlauncher.data.SettingsRepository.DRAWER_LIST -> {
                adapter.listMode = true
                if (isLandscape) {
                    // v198: in landscape la lista verticale spreca spazio - uso griglia 6 colonne
                    binding.recycler.layoutManager = GridLayoutManager(requireContext(), 6)
                    adapter.listMode = false
                    _binding?.alphaScrollBar?.visibility = View.GONE
                } else {
                    binding.recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                    _binding?.alphaScrollBar?.visibility = View.VISIBLE
                    setupAlphaScrollbar()
                }
            }
            else -> {
                adapter.listMode = false
                binding.recycler.layoutManager = GridLayoutManager(requireContext(), 4 + landscapeBoost)
                _binding?.alphaScrollBar?.visibility = View.GONE
            }
        }
    }

    /**
     * v29: collega la scrollbar A-Z al recycler.
     * - Calcola le iniziali presenti nella lista corrente (allApps filtrate)
     * - Quando l'utente seleziona una lettera, scrolla alla prima app con quella iniziale
     */
    private fun setupAlphaScrollbar() {
        val sb = _binding?.alphaScrollBar ?: return
        // calcola lettere presenti
        val current = adapter.currentList
        val letters = current
            .map { firstLetter(it.label) }
            .distinct()
            .sortedWith(compareBy { if (it == "#") "ZZ" else it })  // # in fondo
        sb.setLetters(letters)
        sb.onLetterSelected = { letter ->
            val idx = current.indexOfFirst { firstLetter(it.label) == letter }
            if (idx >= 0) {
                (binding.recycler.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
                    ?.scrollToPositionWithOffset(idx, 0)
            }
        }
    }

    private fun firstLetter(label: String): String {
        if (label.isEmpty()) return "#"
        val ch = label.trim().firstOrNull()?.uppercaseChar() ?: return "#"
        return if (ch.isLetter()) ch.toString() else "#"
    }

    private fun applyFilter(q: String) {
        val normalized = normalize(q)
        val hidden = SpeedApp.instance.settingsRepository.hiddenApps.value ?: mutableSetOf()
        val visible = allApps.filter { !hidden.contains(it.key) }
        val filtered = if (normalized.isBlank()) visible
        else visible.filter { normalize(it.label).contains(normalized) }
        adapter.submitList(filtered) {
            if (_binding?.alphaScrollBar?.visibility == View.VISIBLE) {
                setupAlphaScrollbar()
            }
        }
        // v139: ricerca globale — mostra actions web/contatti/maps quando query non blank
        updateSearchActions(q)
    }
    
    /** v139: popola la barra "ricerca globale" con shortcut web, maps, telefono */
    private fun updateSearchActions(q: String) {
        val container = _binding?.searchActionsContainer ?: return
        val ctx = container.context
        val query = q.trim()
        if (query.isBlank()) {
            container.visibility = View.GONE
            container.removeAllViews()
            return
        }
        container.removeAllViews()
        container.visibility = View.VISIBLE
        val density = ctx.resources.displayMetrics.density
        
        // Helper per creare action row (icona + label)
        fun addAction(label: String, iconRes: Int, preserveColors: Boolean = false, onClick: () -> Unit) {
            val row = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                isClickable = true; isFocusable = true
                run {
                    val tvSel = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tvSel, true)
                    setBackgroundResource(tvSel.resourceId)
                }
                setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            }
            // v207: icona dentro chip rotondo colorSurfaceContainerHighest M3 style
            val iconFrame = android.widget.FrameLayout(ctx).apply {
                val sz = (40 * density).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(sz, sz).apply {
                    marginEnd = (16 * density).toInt()
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    val tv = android.util.TypedValue()
                    ctx.theme.resolveAttribute(
                        com.google.android.material.R.attr.colorSurfaceContainerHighest, tv, true
                    )
                    setColor(tv.data)
                }
            }
            val icon = android.widget.ImageView(ctx).apply {
                setImageResource(iconRes)
                val s = (22 * density).toInt()
                layoutParams = android.widget.FrameLayout.LayoutParams(s, s).apply {
                    gravity = android.view.Gravity.CENTER
                }
                if (!preserveColors) {
                    val tv = android.util.TypedValue()
                    ctx.theme.resolveAttribute(
                        com.google.android.material.R.attr.colorOnSurface, tv, true
                    )
                    setColorFilter(tv.data)
                }
            }
            iconFrame.addView(icon)
            row.addView(iconFrame)
            val txt = android.widget.TextView(ctx).apply {
                text = label
                textSize = 15f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                val tv = android.util.TypedValue()
                ctx.theme.resolveAttribute(
                    com.google.android.material.R.attr.colorOnSurface, tv, true
                )
                setTextColor(tv.data)
            }
            row.addView(txt)
            row.setOnClickListener {
                onClick()
                dismissAllowingStateLoss()
            }
            container.addView(row)
        }
        
        // 1. Cerca su Google
        addAction(ctx.getString(R.string.search_action_google, query), R.drawable.ic_search_google, preserveColors = true) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(android.app.SearchManager.QUERY, query)
                }
                startActivity(intent)
            } catch (_: Throwable) {
                // Fallback: browser
                try {
                    val u = android.net.Uri.parse("https://www.google.com/search?q=${android.net.Uri.encode(query)}")
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, u))
                } catch (_: Throwable) {}
            }
        }
        
        // 2. Cerca su Maps (se la query suona come una location > 2 char)
        if (query.length > 2) {
            addAction(ctx.getString(R.string.search_action_maps, query), R.drawable.ic_search_maps) {
                try {
                    val u = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(query)}")
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, u))
                } catch (_: Throwable) {}
            }
        }
        
        // 3. Telefono se la query è solo numeri
        if (query.matches(Regex("""^[+\d\s().\-]{4,}$"""))) {
            addAction(ctx.getString(R.string.search_action_call, query), R.drawable.ic_search_call) {
                try {
                    val u = android.net.Uri.parse("tel:${android.net.Uri.encode(query)}")
                    startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, u))
                } catch (_: Throwable) {}
            }
        }
        
        // 4. YouTube search
        addAction(ctx.getString(R.string.search_action_youtube, query), R.drawable.ic_search_youtube, preserveColors = true) {
            try {
                val u = android.net.Uri.parse("https://www.youtube.com/results?search_query=${android.net.Uri.encode(query)}")
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, u))
            } catch (_: Throwable) {}
        }
        
        // v166: ricerca universale — aggiunge Contatti, File, Play Store
        val mode = SpeedApp.instance.settingsRepository.searchMode.value
        if (mode == org.cheipstudio.speedlauncher.data.SettingsRepository.MODE_UNIVERSAL) {
            // Cerca tra i contatti
            addAction(ctx.getString(R.string.search_action_contacts, query), R.drawable.ic_search_contacts) {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.provider.ContactsContract.Contacts.CONTENT_URI
                        putExtra(android.app.SearchManager.QUERY, query)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Throwable) {}
            }
            
            // Cerca tra i file
            addAction(ctx.getString(R.string.search_action_files, query), R.drawable.ic_search_files) {
                try {
                    // Apre il file picker di sistema con pre-filtro nome
                    val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(android.content.Intent.createChooser(intent, ctx.getString(R.string.search_action_files, query)))
                } catch (_: Throwable) {}
            }
            
            // Cerca su Play Store
            addAction(ctx.getString(R.string.search_action_playstore, query), R.drawable.ic_search_playstore, preserveColors = true) {
                try {
                    val u = android.net.Uri.parse("market://search?q=${android.net.Uri.encode(query)}")
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, u).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        startActivity(intent)
                    } catch (_: Throwable) {
                        // Fallback web
                        val u2 = android.net.Uri.parse("https://play.google.com/store/search?q=${android.net.Uri.encode(query)}")
                        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, u2))
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    private fun normalize(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
        return n.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): BottomSheetDialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        // v76: skip animazione di show del BottomSheet (default ~250ms slide-up).
        // L\'utente percepisce il drawer come istantaneo. Riapertura immediata possibile.
        dialog.window?.setWindowAnimations(0)
        // v60: drawer dialog full-screen edge-to-edge — nav bar trasparente, bg si estende sotto
        dialog.window?.let { w ->
            w.navigationBarColor = android.graphics.Color.TRANSPARENT
            w.statusBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                w.setDecorFitsSystemWindows(false)
            } else {
                @Suppress("DEPRECATION")
                w.decorView.systemUiVisibility = w.decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                // v57: configurazione che forza chiusura con UN solo swipe (no peek state)
                behavior.peekHeight = 0
                behavior.isFitToContents = true
                behavior.skipCollapsed = true
                behavior.isHideable = true
                behavior.halfExpandedRatio = 0.0001f  // niente half-expanded
                // v78: skippa l'animazione di entry PRIMA di settare lo state.
                // setStateInternal non triggera ViewDragHelper (no settle 600ms),
                // poi behavior.state già combacia → niente animazione, drawer immediatamente aperto.
                skipEntryAnimation(behavior)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

                // v76: velocizzo le animazioni del BottomSheet via reflection.
                // BottomSheetBehavior usa internamente ViewDragHelper con MAX_SETTLE_DURATION = 600ms.
                // Quel campo è static final → non modificabile direttamente.
                // Però posso forzare lo state senza animazione via setStateInternal (privato):
                // → quando l\'utente fa drag down, la chiusura diventa istantanea.
                // Onestà: questo è fragile e potrebbe rompersi con update di Material lib.
                applyFastSheet(behavior)

                // v58: applica tema drawer — usa ColorDrawable che sovrascrive il default Material
                val theme = SpeedApp.instance.settingsRepository.drawerTheme.value ?: "system"
                val isLight = when (theme) {
                    "light" -> true
                    "dark" -> false
                    "transparent" -> false
                    else -> {
                        val nm = resources.configuration.uiMode and
                            android.content.res.Configuration.UI_MODE_NIGHT_MASK
                        nm != android.content.res.Configuration.UI_MODE_NIGHT_YES
                    }
                }
                val bgColor = when (theme) {
                    "transparent" -> android.graphics.Color.argb(150, 0, 0, 0)
                    "light" -> android.graphics.Color.argb(245, 245, 245, 245)
                    "dark" -> android.graphics.Color.argb(245, 27, 27, 31)
                    else -> if (isLight) android.graphics.Color.argb(245, 245, 245, 245)
                            else android.graphics.Color.argb(245, 27, 27, 31)
                }
                // v58: BottomSheet wrapper trasparente, il colore del tema va sul nostro root LinearLayout
                it.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                _binding?.root?.background = android.graphics.drawable.ColorDrawable(bgColor)
                // v62: forzo il root LinearLayout a estendersi tutto il MATCH_PARENT (no fit insets)
                _binding?.root?.fitsSystemWindows = false
                _binding?.root?.setPadding(0, 0, 0, 0)
                // Il bottomSheet wrapper anch'esso non deve fittare gli insets
                it.fitsSystemWindows = false
                it.setPadding(0, 0, 0, 0)

                // v62: padding bottom dinamico al recycler in base all'altezza della nav bar
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(it) { _, insets ->
                    val sysBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    val navBarH = sysBars.bottom
                    val statusBarH = sysBars.top
                    _binding?.recycler?.let { rv ->
                        rv.setPadding(rv.paddingLeft, rv.paddingTop, rv.paddingRight,
                            navBarH + (16 * resources.displayMetrics.density).toInt())
                        rv.clipToPadding = false
                    }
                    // Status bar padding sul root (per non sovrapporsi al notch)
                    _binding?.root?.setPadding(0, (statusBarH * 0.4f).toInt(), 0, 0)  // v76: gap top ridotto
                    insets
                }

                // Slide callback per fade fluido dei contenuti durante drag
                behavior.addBottomSheetCallback(object : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        // v57: dismiss immediato a HIDDEN, e se per caso entra in COLLAPSED forza HIDDEN
                        if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                            try { dismissAllowingStateLoss() } catch (_: Throwable) {}
                        } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                            behavior.state = BottomSheetBehavior.STATE_HIDDEN
                        }
                    }
                    private var hasReachedExpanded = false
                    private var dismissedEarly = false
                    private val callbackStartTime = System.currentTimeMillis()
                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        // slideOffset: -1 (hidden) → 0 (collapsed) → 1 (expanded)
                        // v78: grace period — ignoro onSlide nei primi 250ms post-show
                        // per evitare race condition con setStateInternal che ancora deve stabilizzarsi.
                        val elapsedMs = System.currentTimeMillis() - callbackStartTime
                        if (elapsedMs < 250) {
                            // Forzo aspetto "completamente aperto" per niente flicker
                            _binding?.recycler?.alpha = 1f
                            _binding?.searchInput?.alpha = 1f
                            _binding?.recommendedRow?.applyDrawerSlide(1f)
                            return
                        }
                        val alpha = ((slideOffset + 1f).coerceIn(0f, 1f))
                        _binding?.recycler?.alpha = alpha
                        _binding?.searchInput?.alpha = alpha
                        // v38: applica parallax + fade alle raccomandate
                        _binding?.recommendedRow?.applyDrawerSlide(slideOffset)
                        // v76: traccio se è arrivato a EXPANDED almeno una volta
                        if (slideOffset >= 0.95f) hasReachedExpanded = true
                        // v76: dismiss immediato se drag down dopo essere stato expanded
                        if (hasReachedExpanded && !dismissedEarly && slideOffset < 0.55f) {
                            dismissedEarly = true
                            try { dismissAllowingStateLoss() } catch (_: Throwable) {}
                        }
                    }
                })
            }
        }
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    /**
     * v76: tenta di velocizzare le animazioni del BottomSheet via reflection.
     * Tocca due campi privati di BottomSheetBehavior:
     * - hideFriction: friction usata durante il fling (default 0.1f, alzo per dismiss più veloce su drag down)
     * - viewDragHelper: tramite questo posso accorciare la duration
     */
    private fun applyFastSheet(behavior: com.google.android.material.bottomsheet.BottomSheetBehavior<View>) {
        // 1. Aumento hideFriction (più alto = chiude più facilmente con poco drag)
        try {
            val f = com.google.android.material.bottomsheet.BottomSheetBehavior::class.java
                .getDeclaredField("hideFriction")
            f.isAccessible = true
            f.setFloat(behavior, 0.7f)  // v76: ancora più aggressivo, default 0.1f
        } catch (_: Throwable) {}
    }

    /**
     * v76: skippa l\'animazione di entry impostando lo stato direttamente via setStateInternal.
     * setStateInternal è un metodo privato di BottomSheetBehavior che cambia lo stato SENZA
     * passare per ViewDragHelper.smoothSlideViewTo (che usa MAX_SETTLE_DURATION = 600ms hardcoded).
     * Risultato: drawer appare già aperto, niente animazione lenta.
     */
    private fun skipEntryAnimation(behavior: com.google.android.material.bottomsheet.BottomSheetBehavior<View>) {
        try {
            val m = com.google.android.material.bottomsheet.BottomSheetBehavior::class.java
                .getDeclaredMethod("setStateInternal", Int::class.javaPrimitiveType)
            m.isAccessible = true
            m.invoke(behavior, com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED)
        } catch (_: Throwable) {
            // fallback: imposto lo stato normalmente, almeno c\'è qualcosa
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
    }

    /** v122: avvisa MainActivity quando il drawer si chiude (per resettare drawerSheet) */
    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        try { onDismissCallback?.invoke() } catch (_: Throwable) {}
    }

    companion object {
        private const val ARG_FOCUS_SEARCH = "focus_search"
        fun newInstance(focusSearch: Boolean): AppDrawerSheet {
            return AppDrawerSheet().apply {
                arguments = Bundle().apply { putBoolean(ARG_FOCUS_SEARCH, focusSearch) }
            }
        }
    }
}
