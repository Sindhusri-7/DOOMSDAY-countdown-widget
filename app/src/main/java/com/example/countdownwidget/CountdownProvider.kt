package com.example.countdownwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class CountdownProvider : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        ids.forEach { Updater.update(c, m, it) }
    }

    // Called when the user resizes the widget -> pick Small / Medium / Large
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle?) {
        Updater.update(c, m, id)
    }

    override fun onDeleted(c: Context, ids: IntArray) {
        ids.forEach { Updater.cancel(c, it); Store.delete(c, it) }
    }

    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        if (i.action == Updater.ACTION_TICK) {
            val id = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (id != -1) Updater.update(c, AppWidgetManager.getInstance(c), id)
        }
    }
}
