package org.cheipstudio.speedlauncher.ui

import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.cheipstudio.speedlauncher.data.SettingsRepository
import kotlin.math.min

/**
 * v18: applica forma personalizzata alle icone.
 * - SHAPE_ORIGINAL: niente
 * - SHAPE_SQUIRCLE: quadrato superellipse
 * - SHAPE_CIRCLE: cerchio
 * - SHAPE_SQUARE: quadrato con angoli leggeri
 * - SHAPE_TEARDROP: goccia
 *
 * Funziona meglio con AdaptiveIconDrawable (foreground+background separati),
 * ma applica la maschera anche su icone normali rasterizzandole prima.
 */
object IconShaper {

    private const val ICON_SIZE = 192  // dp-equivalent base render size

    fun shape(drawable: Drawable, shape: String): Drawable {
        if (shape == SettingsRepository.SHAPE_ORIGINAL) return drawable
        val bmp = renderToBitmap(drawable, ICON_SIZE)
        val masked = applyMask(bmp, shape)
        return BitmapDrawable(null, masked)
    }

    private fun renderToBitmap(drawable: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (drawable is AdaptiveIconDrawable) {
            // background + foreground separati, foreground viene scalato
            val bg = drawable.background
            val fg = drawable.foreground
            // adaptive layers: 108dp totali con safe zone 72dp.
            // Renderizziamo con un overdraw del 33% per simulare la safe zone.
            val outset = (size * 0.166f).toInt()
            val drawSize = size + outset * 2
            val bigBmp = Bitmap.createBitmap(drawSize, drawSize, Bitmap.Config.ARGB_8888)
            val bigCanvas = Canvas(bigBmp)
            bg?.setBounds(0, 0, drawSize, drawSize)
            bg?.draw(bigCanvas)
            fg?.setBounds(0, 0, drawSize, drawSize)
            fg?.draw(bigCanvas)
            // crop centrale
            val cropped = Bitmap.createBitmap(bigBmp, outset, outset, size, size)
            canvas.drawBitmap(cropped, 0f, 0f, null)
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

        // disegna la maschera come clip
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
                path.addCircle(s / 2, s / 2, s / 2 - 2, Path.Direction.CW)
            }
            SettingsRepository.SHAPE_SQUARE -> {
                val r = s * 0.12f
                path.addRoundRect(2f, 2f, s - 2, s - 2, r, r, Path.Direction.CW)
            }
            SettingsRepository.SHAPE_TEARDROP -> {
                // angolo top-right squadrato, gli altri tondi
                val r = s * 0.45f
                val rTop = s * 0.10f
                path.addRoundRect(
                    2f, 2f, s - 2, s - 2,
                    floatArrayOf(r, r,  rTop, rTop,  r, r,  r, r),
                    Path.Direction.CW
                )
            }
            SettingsRepository.SHAPE_SQUIRCLE -> {
                // superellipse approssimata con cubic bezier
                buildSquirclePath(path, s)
            }
            else -> path.addRect(0f, 0f, s, s, Path.Direction.CW)
        }
        return path
    }

    private fun buildSquirclePath(path: Path, size: Float) {
        // Approssimazione superellipse n=4 con curve bezier — molto simile alla maschera Material You
        val n = 4f
        val r = size / 2
        val cx = r
        val cy = r
        val steps = 64
        var first = true
        for (i in 0..steps) {
            val t = i.toFloat() / steps * (Math.PI * 2)
            val cosT = Math.cos(t)
            val sinT = Math.sin(t)
            val x = cx + Math.signum(cosT) * Math.pow(Math.abs(cosT), 2.0 / n) * r
            val y = cy + Math.signum(sinT) * Math.pow(Math.abs(sinT), 2.0 / n) * r
            if (first) { path.moveTo(x.toFloat(), y.toFloat()); first = false }
            else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
    }
}
