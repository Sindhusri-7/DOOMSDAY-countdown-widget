package com.example.countdownwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Builds the widget for ANY size: text sizes come from the widget's current width/height, scaled by
 * your "text size" setting, and the text block is placed by your "text position" setting.
 * Hours:minutes:seconds is a system Chronometer, so it ticks live every second.
 */
object Updater {
    const val ACTION_TICK = "com.example.countdownwidget.TICK"
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    private const val DAY = 86_400_000L

    private fun RemoteViews.sz(id: Int, dp: Float) =
        setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, dp)

    fun update(c: Context, m: AppWidgetManager, id: Int) {
        if (!Store.has(c, id)) Store.save(c, id, Cfg())          // first run: Doomsday preset
        val g = Store.load(c, id)

        val o = m.getAppWidgetOptions(id)
        val wDp = (o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250).toFloat()
        val hDp = (o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 } ?: 110).toFloat()

        // modes: 0 = auto (one-line when very short), 1 = always boxes, 2 = always one-line
        val compact = when (g.mode) { 1 -> false; 2 -> true; else -> hDp < 70f }

        val d = c.resources.displayMetrics.density
        val bmp = Bg.make(c, g, (wDp * d).toInt().coerceIn(100, 900), (hDp * d).toInt().coerceIn(60, 900))

        val rv = RemoteViews(c.packageName, if (compact) R.layout.widget_small else R.layout.widget_medium)
        rv.setImageViewBitmap(R.id.bg, bmp)
        rv.setTextViewText(R.id.title, g.title.uppercase())

        val now = System.currentTimeMillis()
        val remaining = (g.target - now).coerceAtLeast(0)
        val running = remaining > 0
        val dayPart = if (remaining > 0 && remaining % DAY == 0L) DAY else remaining % DAY

        // LIVE part: HH:MM:SS left in the current day, counted down by the system every second
        rv.setChronometerCountDown(R.id.sec, true)
        rv.setChronometer(R.id.sec, SystemClock.elapsedRealtime() + dayPart, null, running)
        rv.setTextColor(R.id.sec, g.digitColor)

        val sc = g.textScale / 100f
        val availW = wDp - 24f
        val availH = hDp - 20f
        val titleSz = (min(availH * 0.17f, availW / (max(g.title.length, 1) * 0.72f)) * sc).coerceIn(8f, 26f)
        rv.sz(R.id.title, titleSz)

        val contentH: Float
        if (compact) {
            val num = (min(availW / 7.6f, availH * 0.5f) * sc).coerceIn(10f, 60f)
            rv.setTextViewText(R.id.dd, "%dd".format(remaining / DAY))
            rv.setTextColor(R.id.dd, g.digitColor)
            rv.sz(R.id.dd, num); rv.sz(R.id.sec, num)
            contentH = titleSz * 1.25f + num * 1.25f
        } else {
            val p = Parts.of(g.target)
            rv.setTextViewText(R.id.mo, "%02d".format(p.mo))
            rv.setTextViewText(R.id.dd, "%02d".format(p.d))
            rv.setTextColor(R.id.mo, g.digitColor)
            rv.setTextColor(R.id.dd, g.digitColor)

            val num = (min(availW / 8.4f, availH * 0.36f) * sc).coerceIn(10f, 60f)
            val lab = (num * 0.28f).coerceIn(6f, 13f)
            for (v in intArrayOf(R.id.mo, R.id.dd, R.id.sec, R.id.c1, R.id.c2)) rv.sz(v, num)
            for (v in intArrayOf(R.id.l_mo, R.id.l_dd, R.id.l_h, R.id.l_m, R.id.l_s)) rv.sz(v, lab)

            // progress line + date only if you switched it on
            val line = g.showLine && hDp >= 110f
            val vis = if (line) View.VISIBLE else View.GONE
            rv.setViewVisibility(R.id.progress, vis)
            rv.setViewVisibility(R.id.caption, vis)
            if (line) {
                val total = (g.target - g.created).coerceAtLeast(1)
                val done = ((now - g.created) * 1000 / total).toInt().coerceIn(0, 1000)
                rv.setProgressBar(R.id.progress, 1000, done, false)
                rv.setTextViewText(R.id.caption,
                    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(g.target)))
                rv.sz(R.id.caption, lab + 2f)
            }
            contentH = titleSz * 1.25f + 6f + num * 1.2f + lab * 1.4f +
                (if (line) 26f + (lab + 2f) * 1.3f else 0f)
        }

        // text position: 0 = top ... 100 = bottom of the widget
        val topDp = ((hDp - contentH) * g.textPos / 100f).coerceAtLeast(0f)
        rv.setViewPadding(R.id.content, (10 * d).toInt(), (topDp * d).toInt(), (10 * d).toInt(), 0)

        rv.setViewVisibility(R.id.gear, if (hDp >= 60f) View.VISIBLE else View.GONE)
        rv.setOnClickPendingIntent(
            R.id.gear, PendingIntent.getActivity(
                c, id + 500000,
                Intent(c, ConfigActivity::class.java)
                    .setAction("cfg.$id")
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), FLAGS
            )
        )
        // tap the widget -> play / stop the chosen sound
        rv.setOnClickPendingIntent(
            R.id.root, PendingIntent.getForegroundService(
                c, id,
                Intent(c, PlayerService::class.java).setAction(PlayerService.TOGGLE).putExtra("id", id), FLAGS
            )
        )

        m.updateAppWidget(id, rv)
        schedule(c, id, g.target)
    }

    // ---------- alarms: one at each day change + one at zero ----------
    private fun tickPi(c: Context, id: Int) = PendingIntent.getBroadcast(
        c, id,
        Intent(c, CountdownProvider::class.java).setAction(ACTION_TICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), FLAGS
    )

    private fun endPi(c: Context, id: Int) = PendingIntent.getBroadcast(
        c, id + 1_000_000, Intent(c, AlarmReceiver::class.java).putExtra("id", id), FLAGS
    )

    private fun set(am: AlarmManager, at: Long, pi: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun schedule(c: Context, id: Int, target: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(tickPi(c, id)); am.cancel(endPi(c, id))
        val now = System.currentTimeMillis()
        val remaining = target - now
        if (remaining <= 0) return
        set(am, target, endPi(c, id))                              // sound when it hits zero
        val next = now + (if (remaining % DAY == 0L) DAY else remaining % DAY)
        set(am, minOf(next, target), tickPi(c, id))                // refresh months/days at day change
    }

    fun cancel(c: Context, id: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(tickPi(c, id)); am.cancel(endPi(c, id))
    }
}
