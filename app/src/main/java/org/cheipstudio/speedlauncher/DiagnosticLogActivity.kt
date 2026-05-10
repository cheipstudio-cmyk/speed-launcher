package org.cheipstudio.speedlauncher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * v224: Activity diagnostico - mostra events.txt e last_crash.txt in-app.
 * Permette di copiare il log negli appunti.
 */
class DiagnosticLogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (24 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
            val tv = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true)
            setBackgroundColor(tv.data)
        }

        // Title
        root.addView(TextView(this).apply {
            text = "Log diagnostico"
            textSize = 22f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            val tv = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true)
            setTextColor(tv.data)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (12 * d).toInt()
            layoutParams = lp
        })

        // Bottoni
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (12 * d).toInt()
            layoutParams = lp
        }
        
        val logText = readLogs()
        
        val copyBtn = MaterialButton(this).apply {
            text = "Copia tutto"
            cornerRadius = (24 * d).toInt()
            val lp = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            lp.marginEnd = (8 * d).toInt()
            layoutParams = lp
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("speed_log", logText))
                Toast.makeText(this@DiagnosticLogActivity, "Log copiato negli appunti", Toast.LENGTH_LONG).show()
            }
        }
        btnRow.addView(copyBtn)
        
        val clearBtn = MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Cancella log"
            cornerRadius = (24 * d).toInt()
            val lp = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            layoutParams = lp
            setOnClickListener {
                clearLogs()
                Toast.makeText(this@DiagnosticLogActivity, "Log cancellato", Toast.LENGTH_SHORT).show()
                recreate()
            }
        }
        btnRow.addView(clearBtn)
        
        root.addView(btnRow)

        // Log scrollable
        val scroll = ScrollView(this)
        val logTv = TextView(this).apply {
            text = logText
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            val tv = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true)
            setTextColor(tv.data)
            setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            val tv2 = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHigh, tv2, true)
            setBackgroundColor(tv2.data)
            setTextIsSelectable(true)
        }
        scroll.addView(logTv)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
    }
    
    private fun readLogs(): String {
        val sb = StringBuilder()
        sb.append("=== events.txt ===\n")
        try {
            val f = java.io.File(getExternalFilesDir(null), "events.txt")
            if (f.exists()) {
                sb.append(f.readText().takeLast(15000))
            } else {
                sb.append("(nessun evento ancora registrato)")
            }
        } catch (e: Throwable) {
            sb.append("(errore lettura: ${e.message})")
        }
        sb.append("\n\n=== last_crash.txt ===\n")
        try {
            val f = java.io.File(getExternalFilesDir(null), "last_crash.txt")
            if (f.exists()) {
                sb.append(f.readText())
            } else {
                sb.append("(nessun crash registrato)")
            }
        } catch (e: Throwable) {
            sb.append("(errore lettura: ${e.message})")
        }
        return sb.toString()
    }
    
    private fun clearLogs() {
        try {
            java.io.File(getExternalFilesDir(null), "events.txt").delete()
            java.io.File(getExternalFilesDir(null), "last_crash.txt").delete()
        } catch (_: Throwable) {}
    }
}
