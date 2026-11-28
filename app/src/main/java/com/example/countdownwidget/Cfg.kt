package com.example.countdownwidget

import android.content.Context
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// Avengers: Doomsday opens Dec 18 2026 (midnight US Eastern = same moment worldwide)
private val NY: ZoneId = ZoneId.of("America/New_York")
val DOOMSDAY_TARGET: Long = ZonedDateTime.of(2026, 12, 18, 0, 0, 0, 0, NY).toInstant().toEpochMilli()
val DOOMSDAY_START: Long = ZonedDateTime.of(2025, 12, 18, 0, 0, 0, 0, NY).toInstant().toEpochMilli()

/** Everything a single widget remembers. Defaults = the Doomsday preset. */
data class Cfg(
    val title: String = "DOOMSDAY IS COMING",
    val target: Long = DOOMSDAY_TARGET,
    val created: Long = DOOMSDAY_START,      // start of the progress bar
    val theme: Int = 0,                      // 0 = Doomsday poster picture
    val photo: String? = null,               // user's own picture (overrides theme)
    val dim: Int = 25,                       // 0..80 % darkening over the background
    val digitColor: Int = 0xFFFFFFFF.toInt(),
    val sound: String? = null,               // content:// uri of chosen audio
    val mode: Int = 0                        // 0 auto by size, 1 always boxes, 2 always one-line
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
            .putInt("$id.mode", g.mode)
            .apply()
    }

    fun load(c: Context, id: Int): Cfg {
        val s = p(c); val d = Cfg()
        return Cfg(
            s.getString("$id.title", d.title) ?: d.title,
            s.getLong("$id.target", d.target), s.getLong("$id.created", d.created),
            s.getInt("$id.theme", d.theme), s.getString("$id.photo", null),
            s.getInt("$id.dim", d.dim), s.getInt("$id.color", d.digitColor),
            s.getString("$id.sound", null), s.getInt("$id.mode", 0)
        )
    }

    fun delete(c: Context, id: Int) {
        val e = p(c).edit()
        p(c).all.keys.filter { it.startsWith("$id.") }.forEach { e.remove(it) }
        e.apply()
    }
}

/** Months / days / hours / minutes / seconds left, like the Doomsday poster. */
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
