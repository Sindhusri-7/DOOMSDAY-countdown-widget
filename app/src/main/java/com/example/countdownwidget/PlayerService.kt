package com.example.countdownwidget

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/** Plays the user's chosen sound/music. Started by a widget tap (TOGGLE) or the end alarm (END). */
class PlayerService : Service() {
    private var mp: MediaPlayer? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        val action = i?.action
        val id = i?.getIntExtra("id", -1) ?: -1
        ServiceCompat.startForeground(
            this, 1, notif(id, action == END), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        when {
            action == STOP || (action == TOGGLE && mp?.isPlaying == true) -> stopAll()
            else -> play(id, action == END)
        }
        return START_NOT_STICKY
    }

    private fun play(id: Int, alarm: Boolean) {
        mp?.release()
        val g = if (Store.has(this, id)) Store.load(this, id) else Cfg()
        val uri = g.sound?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try {
            mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                )
                setDataSource(this@PlayerService, uri)
                prepare(); start()
                setOnCompletionListener { stopAll() }
            }
        } catch (e: Exception) { stopAll() }
    }

    private fun stopAll() {
        mp?.release(); mp = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun notif(id: Int, ended: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("cd", "Countdown", NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null) }          // the MediaPlayer makes the sound
        )
        val g = if (Store.has(this, id)) Store.load(this, id) else Cfg()
        val stop = PendingIntent.getService(
            this, 0, Intent(this, PlayerService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "cd")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(g.title)
            .setContentText(if (ended) "Countdown finished!" else "Playing…")
            .addAction(0, "Stop", stop).setOngoing(true).build()
    }

    override fun onDestroy() { mp?.release(); super.onDestroy() }

    companion object {
        const val TOGGLE = "toggle"; const val END = "end"; const val STOP = "stop"
    }
}
