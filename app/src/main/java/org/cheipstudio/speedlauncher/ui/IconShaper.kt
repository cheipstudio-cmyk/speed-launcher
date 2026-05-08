package org.cheipstudio.speedlauncher.ui

import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.cheipstudio.speedlauncher.data.SettingsRepository

/**
 * v19: applicazione maschera SENZA outset/crop.
 * - ORIGINAL: ritorna il drawable invariato (nessun bug)
 * - Per altre forme: renderizza il drawable a dimensione esatta dello slot,
 *   e applica la maschera senza croppare. Le adaptive icons appaiono come definite,
 *   le bitmap normali idem.
 */
object IconShaper {

    private const val ICON_SIZE = 192

    /** v75: cache lazy dell\'icon pack manager per il pack attivo. */
    @Volatile private var iconPackManager: org.cheipstudio.speedlauncher.tools.IconPackManager? = null
    @Volatile private var lastLoadedPack: String = ""

    private fun ensureIconPack(context: android.content.Context): org.cheipstudio.speedlauncher.tools.IconPackManager? {
        val pack = org.cheipstudio.speedlauncher.SpeedApp.instance.settingsRepository
            .iconPackPackage.value ?: ""
        if (pack.isEmpty()) {
            iconPackManager = null
            lastLoadedPack = ""
            return null
        }
        if (pack != lastLoadedPack) {
            val mgr = org.cheipstudio.speedlauncher.tools.IconPackManager(context)
            if (mgr.load(pack)) {
                iconPackManager = mgr
                lastLoadedPack = pack
            } else {
                iconPackManager = null
                lastLoadedPack = ""
            }
        }
        return iconPackManager
    }

    fun shape(drawable: Drawable, shape: String): Drawable {
        if (shape == SettingsRepository.SHAPE_ORIGINAL) return drawable
        val bmp = renderToBitmap(drawable, ICON_SIZE)
        val masked = applyMask(bmp, shape)
        return BitmapDrawable(null, masked)
    }

    /**
     * v75: overload con package/activity per applicare icon pack se attivo.
     * Se un icon pack è attivo e ha l\'icona per questo component, la sostituisce
     * PRIMA di applicare la maschera della forma.
     */
    fun shape(
        drawable: Drawable,
        shape: String,
        context: android.content.Context,
        packageName: String,
        activityName: String
    ): Drawable {
        val mgr = ensureIconPack(context)
        val effectiveDrawable = if (mgr != null) {
            mgr.getIconForComponent(packageName, activityName) ?: drawable
        } else drawable
        if (shape == SettingsRepository.SHAPE_ORIGINAL) return effectiveDrawable
        val bmp = renderToBitmap(effectiveDrawable, ICON_SIZE)
        val masked = applyMask(bmp, shape)
        return BitmapDrawable(null, masked)
    }

    private fun renderToBitmap(drawable: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (drawable is AdaptiveIconDrawable) {
            // Niente outset: renderizziamo le foreground/background a dimensione esatta.
            // Le icone Google e adaptive in genere hanno il loro contenuto nella safe zone
            // ma il background si estende sempre fino ai bordi. Disegnando a fullsize la
            // maschera taglia solo il bg che è uniforme: nessun crop del foreground.
            val bg = drawable.background
            val fg = drawable.foreground
            bg?.setBounds(0, 0, size, size)
            bg?.draw(canvas)
            fg?.setBounds(0, 0, size, size)
            fg?.draw(canvas)
        } else {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
        return bmp
    }

    private fun applyMask(source: Bitmap, shape: String): Bitmap {
        val size = source.width
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val maskPath = buildMaskPath(shape, size)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        canvas.save()
        canvas.clipPath(maskPath)
        canvas.drawBitmap(source, 0f, 0f, paint)
        canvas.restore()

        return out
    }

    private fun buildMaskPath(shape: String, size: Int): Path {
        val s = size.toFloat()
        val path = Path()
        when (shape) {
            SettingsRepository.SHAPE_CIRCLE -> {
                path.addCircle(s / 2, s / 2, s / 2 - 1, Path.Direction.CW)
            }
            SettingsRepository.SHAPE_SQUARE -> {
                val r = s * 0.18f
                path.addRoundRect(1f, 1f, s - 1, s - 1, r, r, Path.Direction.CW)
            }
            SettingsRepository.SHAPE_TEARDROP -> {
                val r = s * 0.45f
                val rTop = s * 0.10f
                path.addRoundRect(
                    1f, 1f, s - 1, s - 1,
                    floatArrayOf(r, r,  rTop, rTop,  r, r,  r, r),
                    Path.Direction.CW
                )
            }
            SettingsRepository.SHAPE_SQUIRCLE -> {
                buildSquirclePath(path, s)
            }
            else -> path.addRect(0f, 0f, s, s, Path.Direction.CW)
        }
        return path
    }

    private fun buildSquirclePath(path: Path, size: Float) {
        val n = 4f
        val r = size / 2
        val cx = r; val cy = r
        val steps = 64
        var first = true
        for (i in 0..steps) {
            val t = i.toFloat() / steps * (Math.PI * 2)
            val cosT = Math.cos(t); val sinT = Math.sin(t)
            val x = cx + Math.signum(cosT) * Math.pow(Math.abs(cosT), 2.0 / n) * r
            val y = cy + Math.signum(sinT) * Math.pow(Math.abs(sinT), 2.0 / n) * r
            if (first) { path.moveTo(x.toFloat(), y.toFloat()); first = false }
            else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
    }
}
