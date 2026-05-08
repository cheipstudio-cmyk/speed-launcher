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
        val aiOn = SpeedApp.instance.settingsRepository.aiLauncherMode.value == true
        if (aiOn) {
            _binding?.recommendedRow?.visibility = View.VISIBLE
            _binding?.recommendedRow?.refresh("drawer")
            _binding?.recommendedRow?.onAppClick = { app ->
                SpeedApp.instance.appRepository.launch(app)
                dismissAllowingStateLoss()
            }
            _binding?.recommendedRow?.onAppLongPress = { app ->
                onAppLongPress?.invoke(app)
            }
        } else {
            _binding?.recommendedRow?.visibility = View.GONE
        }
        binding.recycler.adapter = adapter

        SpeedApp.instance.appRepository.apps.observe(viewLifecycleOwner) { apps ->
            allApps = apps
            applyFilter(binding.searchInput.text?.toString().orEmpty())
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
        when (layout) {
            org.cheipstudio.speedlauncher.data.SettingsRepository.DRAWER_GRID3 -> {
                adapter.listMode = false
                binding.recycler.layoutManager = GridLayoutManager(requireContext(), 3)
                _binding?.alphaScrollBar?.visibility = View.GONE
            }
            org.cheipstudio.speedlauncher.data.SettingsRepository.DRAWER_GRID5 -> {
                adapter.listMode = false
                binding.recycler.layoutManager = GridLayoutManager(requireContext(), 5)
                _binding?.alphaScrollBar?.visibility = View.GONE
            }
            org.cheipstudio.speedlauncher.data.SettingsRepository.DRAWER_LIST -> {
                adapter.listMode = true
                binding.recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                // v29: scrollbar A-Z visibile solo in lista
                _binding?.alphaScrollBar?.visibility = View.VISIBLE
                setupAlphaScrollbar()
            }
            else -> {
                adapter.listMode = false
                binding.recycler.layoutManager = GridLayoutManager(requireContext(), 4)
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
        val ownPkg = context?.packageName ?: ""
        // v55: backup filter — Speed Launcher non deve mai apparire nel proprio drawer
        val visible = allApps.filter { !hidden.contains(it.key) && it.packageName != ownPkg }
        val filtered = if (normalized.isBlank()) visible
        else visible.filter { normalize(it.label).contains(normalized) }
        adapter.submitList(filtered) {
            // v29: dopo che la lista è aggiornata, refresh della scrollbar
            if (_binding?.alphaScrollBar?.visibility == View.VISIBLE) {
                setupAlphaScrollbar()
            }
        }
    }

    private fun normalize(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
        return n.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): BottomSheetDialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isHideable = true
                it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

                // Slide callback per fade fluido dei contenuti durante drag
                behavior.addBottomSheetCallback(object : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {}
                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        // slideOffset: -1 (hidden) → 0 (collapsed) → 1 (expanded)
                        val alpha = ((slideOffset + 1f).coerceIn(0f, 1f))
                        _binding?.recycler?.alpha = alpha
                        _binding?.searchInput?.alpha = alpha
                        // v38: applica parallax + fade alle raccomandate
                        _binding?.recommendedRow?.applyDrawerSlide(slideOffset)
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

    companion object {
        private const val ARG_FOCUS_SEARCH = "focus_search"
        fun newInstance(focusSearch: Boolean): AppDrawerSheet {
            return AppDrawerSheet().apply {
                arguments = Bundle().apply { putBoolean(ARG_FOCUS_SEARCH, focusSearch) }
            }
        }
    }
}
