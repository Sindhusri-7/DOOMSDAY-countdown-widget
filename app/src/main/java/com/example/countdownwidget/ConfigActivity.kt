package com.example.countdownwidget

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** STEP 1: customization screen (title, date/time, theme, photo, dim, digit color, sound). */
class ConfigActivity : AppCompatActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var g = Cfg()
    private lateinit var preview: ImageView
    private lateinit var sample: TextView
    private lateinit var dateBtn: Button
    private lateinit var timeBtn: Button

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { savePhoto(it) }
    }
    private val pickSound = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: Exception) {}
            g = g.copy(sound = it.toString())
            Toast.makeText(this, "Sound selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        if (Store.has(this, widgetId)) g = Store.load(this, widgetId)
        setContentView(R.layout.activity_config)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        preview = findViewById(R.id.preview); sample = findViewById(R.id.sample)
        dateBtn = findViewById(R.id.date); timeBtn = findViewById(R.id.time)
        val title = findViewById<EditText>(R.id.title); title.setText(g.title)

        dateBtn.setOnClickListener {
            val c = Calendar.getInstance().apply { timeInMillis = g.target }
            DatePickerDialog(this, { _, y, m, d ->
                val n = Calendar.getInstance().apply { timeInMillis = g.target; set(y, m, d) }
                g = g.copy(target = n.timeInMillis); refresh()
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
        timeBtn.setOnClickListener {
            val c = Calendar.getInstance().apply { timeInMillis = g.target }
            TimePickerDialog(this, { _, h, mi ->
                val n = Calendar.getInstance().apply {
                    timeInMillis = g.target; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi); set(Calendar.SECOND, 0)
                }
                g = g.copy(target = n.timeInMillis); refresh()
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
        }

        val group = findViewById<RadioGroup>(R.id.themes)
        Bg.themes.forEachIndexed { i, t ->
            group.addView(RadioButton(this).apply {
                text = t.first; tag = i; this.id = View.generateViewId()
                isChecked = (g.photo == null && g.theme == i)
            })
        }
        group.setOnCheckedChangeListener { gr, checked ->
            val i = gr.findViewById<View>(checked)?.tag as? Int ?: return@setOnCheckedChangeListener
            g = g.copy(theme = i, photo = null); refresh()
        }

        val row = findViewById<LinearLayout>(R.id.colors)
        listOf(0xFFFFFFFF, 0xFF8BFF6A, 0xFFFFD54F, 0xFF4DD0E1).forEach { col ->
            row.addView(View(this).apply {
                setBackgroundColor(col.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) }
                setOnClickListener { g = g.copy(digitColor = col.toInt()); refresh() }
            })
        }

        val dim = findViewById<SeekBar>(R.id.dim); dim.max = 80; dim.progress = g.dim
        dim.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { g = g.copy(dim = p); refresh() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        findViewById<Button>(R.id.photo).setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<Button>(R.id.sound).setOnClickListener { pickSound.launch(arrayOf("audio/*")) }

        findViewById<Button>(R.id.save).setOnClickListener {
            g = g.copy(title = title.text.toString().ifBlank { "COUNTDOWN" })
            Store.save(this, widgetId, g)
            Updater.update(this, AppWidgetManager.getInstance(this), widgetId)
            if (Build.VERSION.SDK_INT >= 31 &&
                !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            ) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
        refresh()
    }

    private fun refresh() {
        preview.setImageBitmap(Bg.make(this, g, 700, 280))
        sample.setTextColor(g.digitColor)
        val d = Date(g.target)
        dateBtn.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(d)
        timeBtn.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(d)
    }

    /** Downscale to max 1080px and save as JPEG: big bitmaps make widgets fail to load. */
    private fun savePhoto(uri: Uri) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var s = 1
        while (opts.outWidth / s > 2000) s *= 2
        val bm = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
        } ?: return
        val sc = minOf(1f, 1080f / max(bm.width, bm.height))
        val out = android.graphics.Bitmap.createScaledBitmap(
            bm, (bm.width * sc).toInt(), (bm.height * sc).toInt(), true
        )
        val f = File(filesDir, "bg_$widgetId.jpg")
        FileOutputStream(f).use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        g = g.copy(photo = f.absolutePath); refresh()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
