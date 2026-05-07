package org.cheipstudio.speedlauncher.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.cheipstudio.speedlauncher.R

object WidgetRemoveSheet {
    fun show(context: Context, onConfirm: () -> Unit) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.sheet_widget_remove, null, false)
        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }
}
