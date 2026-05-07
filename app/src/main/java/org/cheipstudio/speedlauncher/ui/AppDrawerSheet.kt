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

    var onAppLongPress: ((AppInfo) -> Unit)? = null

    private lateinit var adapter: AppListAdapter
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
            onLongClick = { app, _ ->
                dismissAllowingStateLoss()
                onAppLongPress?.invoke(app)
            }
        )
        binding.recycler.layoutManager = GridLayoutManager(requireContext(), 4)
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

    private fun applyFilter(q: String) {
        val normalized = normalize(q)
        val filtered = if (normalized.isBlank()) allApps
        else allApps.filter { normalize(it.label).contains(normalized) }
        adapter.submitList(filtered)
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
