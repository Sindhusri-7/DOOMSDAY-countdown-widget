package com.example.countdownwidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Fires when the countdown reaches zero: plays the sound and refreshes the widget. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        if (id < 0) return
        ContextCompat.startForegroundService(
            c, Intent(c, PlayerService::class.java).setAction(PlayerService.END).putExtra("id", id)
        )
        Updater.update(c, AppWidgetManager.getInstance(c), id)
    }
}

/** Alarms are wiped on reboot / time change, so re-schedule everything. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val m = AppWidgetManager.getInstance(c)
        listOf(CountdownProvider::class.java, CountdownMusicProvider::class.java).forEach { cls ->
            m.getAppWidgetIds(ComponentName(c, cls)).forEach { Updater.update(c, m, it) }
        }
    }
}
