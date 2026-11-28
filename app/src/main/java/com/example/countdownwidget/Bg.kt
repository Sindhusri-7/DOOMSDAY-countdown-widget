package com.example.countdownwidget

import android.content.Context
import android.graphics.*
import kotlin.math.max

/** Draws the widget background (gradient theme or user photo + dim + rounded corners). */
object Bg {
    // Built-in themes. To add real photos: drop webp files in res/drawable-nodpi and
    // decode them here the same way the user photo is decoded.
    val themes = listOf(
        "Doomsday" to intArrayOf(0xFF07140A.toInt(), 0xFF1B6B2A.toInt(), 0xFFA6E86B.toInt()),
        "Galaxy" to intArrayOf(0xFF0B0B2B.toInt(), 0xFF4B2A8C.toInt(), 0xFFD76BFF.toInt()),
        "Ember" to intArrayOf(0xFF1A0500.toInt(), 0xFFB3210A.toInt(), 0xFFFFB347.toInt()),
        "Neon city" to intArrayOf(0xFF0A0F2E.toInt(), 0xFF0FB5D6.toInt(), 0xFFFF2E93.toInt()),
        "Mono" to intArrayOf(0xFF0D0D0D.toInt(), 0xFF2B2B2B.toInt(), 0xFF5A5A5A.toInt())
    )

    fun make(c: Context, g: Cfg, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val r = 24 * c.resources.displayMetrics.density
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) }
        cv.clipPath(clip)

        val photo = g.photo?.let { BitmapFactory.decodeFile(it) }
        if (photo != null) {                                   // center-crop the photo
            val sc = max(w / photo.width.toFloat(), h / photo.height.toFloat())
            val dw = photo.width * sc; val dh = photo.height * sc
            cv.drawBitmap(photo, null,
                RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2),
                Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val cols = themes[g.theme.coerceIn(themes.indices)].second
            val p = Paint().apply {
                shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), cols, null, Shader.TileMode.CLAMP)
            }
            cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        cv.drawColor(Color.argb(g.dim * 255 / 100, 0, 0, 0))   // darken so digits stay readable
        return bmp
    }
}
