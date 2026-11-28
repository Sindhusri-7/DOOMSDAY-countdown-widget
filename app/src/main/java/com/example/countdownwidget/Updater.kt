package com.example.countdownwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds the RemoteViews for a widget (picks Small / Medium / Large by size) and schedules ticks. */
object Updater {
    const val ACTION_TICK = "com.example.countdownwidget.TICK"
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun update(c: Context, m: AppWidgetManager, id: Int) {
        if (!Store.has(c, id)) return
        val g = Store.load(c, id)
        val o = m.getAppWidgetOptions(id)
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 } ?: 110

        // STEP 3: choose layout from the current widget size
        val size = when { hDp < 80 || wDp < 150 -> 0; hDp < 160 -> 1; else -> 2 }
        val layout = intArrayOf(R.layout.widget_small, R.layout.widget_medium, R.layout.widget_large)[size]

        val d = c.resources.displayMetrics.density
        val bmp = Bg.make(c, g, (wDp * d).toInt().coerceIn(100, 900), (hDp * d).toInt().coerceIn(60, 900))

        val rv = RemoteViews(c.packageName, layout)
        rv.setImageViewBitmap(R.id.bg, bmp)
        rv.setTextViewText(R.id.title, g.title.uppercase())

        val now = System.currentTimeMillis()
        val remaining = (g.target - now).coerceAtLeast(0)
        val running = remaining > 0
        val base = SystemClock.elapsedRealtime()

        if (size == 0) {
            // Small: one fully live H:MM:SS counter, no updates needed
            rv.setChronometerCountDown(R.id.live, true)
            rv.setChronometer(R.id.live, base + remaining, null, running)
            rv.setTextColor(R.id.live, g.digitColor)
        } else {
            val p = Parts.of(g.target)
            rv.setTextViewText(R.id.mo, "%02d".format(p.mo))
            rv.setTextViewText(R.id.dd, "%02d".format(p.d))
            rv.setTextViewText(R.id.hh, "%02d".format(p.h))
            rv.setTextViewText(R.id.mm, "%02d".format(p.m))
            // Seconds box: live chronometer counting down the rest of the current minute
            rv.setChronometerCountDown(R.id.sec, true)
            rv.setChronometer(R.id.sec, base + remaining % 60000, null, running)
            for (v in intArrayOf(R.id.mo, R.id.dd, R.id.hh, R.id.mm, R.id.sec)) rv.setTextColor(v, g.digitColor)

            rv.setOnClickPendingIntent(
                R.id.gear, PendingIntent.getActivity(
                    c, id + 500000,
                    Intent(c, ConfigActivity::class.java)
                        .setAction("cfg.$id")
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), FLAGS
                )
            )
            if (size == 2) {
                val total = (g.target - g.created).coerceAtLeast(1)
                val done = ((now - g.created) * 1000 / total).toInt().coerceIn(0, 1000)
                rv.setProgressBar(R.id.progress, 1000, done, false)
                rv.setTextViewText(R.id.caption,
                    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(g.target)))
            }
        }

        // STEP 4: tap the widget -> play / stop the chosen sound
        rv.setOnClickPendingIntent(
            R.id.root, PendingIntent.getForegroundService(
                c, id,
                Intent(c, PlayerService::class.java).setAction(PlayerService.TOGGLE).putExtra("id", id), FLAGS
            )
        )

        m.updateAppWidget(id, rv)
        schedule(c, id, size, g.target)
    }

    // ---------- alarms ----------
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

    private fun schedule(c: Context, id: Int, size: Int, target: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(tickPi(c, id)); am.cancel(endPi(c, id))
        val now = System.currentTimeMillis()
        val remaining = target - now
        if (remaining <= 0) return
        set(am, target, endPi(c, id))                       // sound when it hits zero
        if (size != 0) {                                    // refresh the boxes at each minute change
            val next = now + (if (remaining % 60000 == 0L) 60000 else remaining % 60000)
            set(am, minOf(next, target), tickPi(c, id))
        }
    }

    fun cancel(c: Context, id: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(tickPi(c, id)); am.cancel(endPi(c, id))
    }
}
