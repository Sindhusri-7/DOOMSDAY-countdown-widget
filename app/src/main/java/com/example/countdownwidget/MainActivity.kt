package com.example.countdownwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.pin).setOnClickListener { pin(CountdownProvider::class.java) }
        findViewById<Button>(R.id.pin_music).setOnClickListener { pin(CountdownMusicProvider::class.java) }
    }

    private fun pin(cls: Class<*>) {
        val m = AppWidgetManager.getInstance(this)
        if (m.isRequestPinAppWidgetSupported)
            m.requestPinAppWidget(ComponentName(this, cls), null, null)
        else Toast.makeText(this, "Long-press home screen > Widgets > Countdown", Toast.LENGTH_LONG).show()
    }
}
