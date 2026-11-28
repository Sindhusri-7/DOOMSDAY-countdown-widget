package com.example.countdownwidget

import android.content.Context
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Everything a single widget remembers. */
data class Cfg(
    val title: String = "DOOMSDAY IS COMING",
    val target: Long = System.currentTimeMillis() + 30L * 24 * 3600 * 1000,
    val created: Long = System.currentTimeMillis(),
    val theme: Int = 0,
    val photo: String? = null,
    val dim: Int = 40,                       // 0..80 % darkening over the background
    val digitColor: Int = 0xFFFFFFFF.toInt(),
    val sound: String? = null                // content:// uri of chosen audio, null = default alarm
)

/** Per-widget storage (SharedPreferences, keyed by widget id). */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun has(c: Context, id: Int) = p(c).contains("$id.target")

    fun save(c: Context, id: Int, g: Cfg) {
        p(c).edit()
            .putString("$id.title", g.title).putLong("$id.target", g.target)
            .putLong("$id.created", g.created).putInt("$id.theme", g.theme)
            .putString("$id.photo", g.photo).putInt("$id.dim", g.dim)
            .putInt("$id.color", g.digitColor).putString("$id.sound", g.sound)
            .apply()
    }

    fun load(c: Context, id: Int): Cfg {
        val s = p(c); val d = Cfg()
        return Cfg(
            s.getString("$id.title", d.title) ?: d.title,
            s.getLong("$id.target", d.target), s.getLong("$id.created", d.created),
            s.getInt("$id.theme", 0), s.getString("$id.photo", null),
            s.getInt("$id.dim", 40), s.getInt("$id.color", d.digitColor),
            s.getString("$id.sound", null)
        )
    }

    fun delete(c: Context, id: Int) {
        val e = p(c).edit()
        p(c).all.keys.filter { it.startsWith("$id.") }.forEach { e.remove(it) }
        e.apply()
    }
}

/** Months / days / hours / minutes / seconds left, like the "Doomsday" poster. */
data class Parts(val mo: Long, val d: Long, val h: Long, val m: Long, val s: Long) {
    companion object {
        fun of(target: Long): Parts {
            val z = ZoneId.systemDefault()
            val now = LocalDateTime.now(z)
            val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(target), z)
            if (!end.isAfter(now)) return Parts(0, 0, 0, 0, 0)
            var t = now
            val mo = ChronoUnit.MONTHS.between(t, end); t = t.plusMonths(mo)
            val d = ChronoUnit.DAYS.between(t, end); t = t.plusDays(d)
            val h = ChronoUnit.HOURS.between(t, end); t = t.plusHours(h)
            val m = ChronoUnit.MINUTES.between(t, end); t = t.plusMinutes(m)
            val s = ChronoUnit.SECONDS.between(t, end)
            return Parts(mo, d, h, m, s)
        }
    }
}
