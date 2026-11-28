package com.example.countdownwidget

import android.content.Context
import android.graphics.*
import kotlin.math.max

/** Draws the widget background: poster picture, gradient theme or user photo, dimmed, rounded corners. */
object Bg {
    val themes = listOf(
        "Doomsday poster" to intArrayOf(),                 // uses res/drawable-nodpi/doomsday_bg.jpg
        "Green glow" to intArrayOf(0xFF07140A.toInt(), 0xFF1B6B2A.toInt(), 0xFFA6E86B.toInt()),
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

        val pic: Bitmap? = g.photo?.let { BitmapFactory.decodeFile(it) }
            ?: if (g.theme == 0) BitmapFactory.decodeResource(c.resources, R.drawable.doomsday_bg) else null

        if (pic != null) {                                      // center-crop the picture
            val sc = max(w / pic.width.toFloat(), h / pic.height.toFloat())
            val dw = pic.width * sc; val dh = pic.height * sc
            cv.drawBitmap(pic, null,
                RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2),
                Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val cols = themes[g.theme.coerceIn(themes.indices)].second
            if (cols.size >= 2) {
                val p = Paint().apply {
                    shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), cols, null, Shader.TileMode.CLAMP)
                }
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            } else cv.drawColor(Color.BLACK)
        }
        cv.drawColor(Color.argb(g.dim * 255 / 100, 0, 0, 0))    // darken so digits stay readable
        return bmp
    }
}
