package com.selteck1.phonetoobs

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private lateinit var url: EditText
    private lateinit var status: TextView
    private val capturePermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
                putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(CaptureService.EXTRA_URL, url.text.toString().trim())
            }
            startForegroundService(intent)
            status.text = "Запущена передача. Открой RTMP-поток в OBS."
        } else status.text = "Разрешение на захват экрана не предоставлено."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 28, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "PhoneToOBS"
            textSize = 28f
        })
        root.addView(TextView(this).apply {
            text = "Передача экрана Android в OBS через USB"
            textSize = 16f
            setPadding(0, 12, 0, 18)
        })
        url = EditText(this).apply {
            hint = "RTMP URL"
            setSingleLine(true)
            setText("rtmp://127.0.0.1:1935/phone")
        }
        root.addView(url, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(Button(this).apply {
            text = "Начать трансляцию"
            setOnClickListener {
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                capturePermission.launch(manager.createScreenCaptureIntent())
            }
        })
        root.addView(Button(this).apply {
            text = "Остановить"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                status.text = "Передача остановлена."
            }
        })
        status = TextView(this).apply {
            text = "Готово. Подключи USB, запусти ADB reverse и RTMP-сервер на ПК."
            textSize = 14f
            setPadding(0, 18, 0, 0)
        }
        root.addView(status)
        setContentView(root)
    }
}
