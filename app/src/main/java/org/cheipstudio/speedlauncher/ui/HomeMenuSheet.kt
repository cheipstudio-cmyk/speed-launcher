package org.cheipstudio.speedlauncher.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.cheipstudio.speedlauncher.R

class HomeMenuSheet : BottomSheetDialogFragment() {

    var onWallpaper: (() -> Unit)? = null
    var onAddWidget: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null

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
        view.findViewById<View>(R.id.menuWidget).setOnClickListener {
            dismissAllowingStateLoss()
            onAddWidget?.invoke()
        }
        view.findViewById<View>(R.id.menuSettings).setOnClickListener {
            dismissAllowingStateLoss()
            onSettings?.invoke()
        }
    }
}
