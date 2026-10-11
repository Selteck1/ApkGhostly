package ru.selteck.ghostly.recorder

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private lateinit var resolutionSpinner: Spinner
    private lateinit var fpsSpinner: Spinner
    private lateinit var orientationSpinner: Spinner
    private lateinit var bitrateSeek: SeekBar
    private lateinit var bitrateValue: TextView
    private lateinit var micSwitch: Switch
    private lateinit var hevcSwitch: Switch
    private lateinit var recordButton: Button
    private lateinit var quickSettingsButton: Button
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView

    private var isRecording = false
    private var receiverRegistered = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ScreenRecorderService.ACTION_STATE) return
            val active = intent.getBooleanExtra(ScreenRecorderService.EXTRA_ACTIVE, false)
            isRecording = active
            val message = intent.getStringExtra(ScreenRecorderService.EXTRA_MESSAGE).orEmpty()
            val error = intent.getStringExtra(ScreenRecorderService.EXTRA_ERROR).orEmpty()
            updateStateUi(message)
            if (error.isNotBlank()) {
                Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
            } else if (!active && message.isNotBlank()) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = C_BG
        window.navigationBarColor = C_BG
        window.decorView.systemUiVisibility = 0
        isRecording = prefs.getBoolean(KEY_RECORDING, false)
        buildUi()
        restoreSettings()
        updateStateUi(if (isRecording) "Запись уже идёт" else "Всё готово к записи")
        if (intent?.action == ACTION_FROM_TILE && !isRecording) {
            window.decorView.post { onRecordButtonClick() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!receiverRegistered) {
            val filter = IntentFilter(ScreenRecorderService.ACTION_STATE)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(stateReceiver, filter)
            }
            receiverRegistered = true
        }
        isRecording = prefs.getBoolean(KEY_RECORDING, false)
        updateStateUi(if (isRecording) "Запись уже идёт" else "Всё готово к записи")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) setIntent(intent)
        if (intent?.action == ACTION_FROM_TILE && !isRecording) {
            window.decorView.post { onRecordButtonClick() }
        }
    }

    override fun onPause() {
        if (receiverRegistered) {
            unregisterReceiver(stateReceiver)
            receiverRegistered = false
        }
        super.onPause()
    }

    private fun buildUi() {
        val frame = FrameLayout(this).apply { setBackgroundColor(C_BG) }
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }

        val brandRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val ghost = TextView(this).apply {
            text = "◈"
            textSize = 29f
            setTextColor(C_ACCENT)
            gravity = Gravity.CENTER
            background = rounded(C_PANEL, C_STROKE, 18)
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        }
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, 0, 0)
        }
        brandText.addView(label("GHOSTLY", 22f, C_TEXT, true))
        brandText.addView(label("SCREEN RECORDER", 11f, C_MUTED, true).apply {
            letterSpacing = 0.14f
        })
        brandRow.addView(ghost)
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(brandRow)

        page.addView(label("Запись экрана", 29f, C_TEXT, true), margin(top = 26, bottom = 5))
        page.addView(label("Чистая запись для игр без лишних оверлеев", 14f, C_MUTED), margin(bottom = 20))

        val statusCard = card()
        val statusRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val statusDot = TextView(this).apply {
            text = "●"
            textSize = 17f
            setTextColor(C_GREEN)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        }
        val statusColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        statusTitle = label("ГОТОВО К ЗАПИСИ", 14f, C_TEXT, true)
        statusDetail = label("Выбери качество и нажми «Начать запись»", 12f, C_MUTED)
        statusColumn.addView(statusTitle)
        statusColumn.addView(statusDetail, margin(top = 4))
        statusRow.addView(statusDot)
        statusRow.addView(statusColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusCard.addView(statusRow)
        page.addView(statusCard, margin(bottom = 18))

        val qualityCard = card()
        qualityCard.addView(sectionTitle("КАЧЕСТВО ВИДЕО"))
        qualityCard.addView(label("Разрешение", 13f, C_MUTED), margin(top = 14, bottom = 7))
        resolutionSpinner = makeSpinner(listOf("480p · экономия", "720p · сбалансированно", "1080p · Full HD", "1440p · 2K / максимум"))
        qualityCard.addView(resolutionSpinner, matchWrap())
        qualityCard.addView(label("Частота кадров", 13f, C_MUTED), margin(top = 15, bottom = 7))
        fpsSpinner = makeSpinner(listOf("30 FPS · меньше нагрузка", "60 FPS · плавно", "90 FPS · высокая частота", "120 FPS · максимум"))
        qualityCard.addView(fpsSpinner, matchWrap())

        qualityCard.addView(label("Ориентация записи", 13f, C_MUTED), margin(top = 15, bottom = 7))
        orientationSpinner = makeSpinner(listOf(
            "Альбомная · для игр",
            "Книжная · вертикальное видео",
            "Авто · по положению экрана"
        ))
        qualityCard.addView(orientationSpinner, matchWrap())

        qualityCard.addView(label("Видеокодек", 13f, C_MUTED), margin(top = 15, bottom = 7))
        hevcSwitch = Switch(this).apply {
            text = "H.265 / HEVC"
            textSize = 14f
            setTextColor(C_TEXT)
            isChecked = true
            thumbTintList = android.content.res.ColorStateList.valueOf(C_TEXT)
            trackTintList = android.content.res.ColorStateList.valueOf(C_ACCENT)
        }
        qualityCard.addView(hevcSwitch)
        qualityCard.addView(label("HEVC помогает сохранить детали при том же битрейте, если телефон поддерживает аппаратное кодирование. Иначе будет H.264.", 11f, C_MUTED), margin(top = 2))

        qualityCard.addView(label("Битрейт", 13f, C_MUTED), margin(top = 17, bottom = 3))
        val bitrateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bitrateRow.addView(label("4 Мбит/с", 11f, C_MUTED), LinearLayout.LayoutParams(0, dp(22), 1f))
        bitrateValue = label("28 Мбит/с", 15f, C_ACCENT, true)
        bitrateRow.addView(bitrateValue)
        bitrateRow.addView(label("50 Мбит/с", 11f, C_MUTED).apply {
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(0, dp(22), 1f))
        qualityCard.addView(bitrateRow, margin(top = 8))
        bitrateSeek = SeekBar(this).apply {
            max = 23
            progress = 12
            progressTintList = android.content.res.ColorStateList.valueOf(C_ACCENT)
            thumbTintList = android.content.res.ColorStateList.valueOf(C_ACCENT)
        }
        qualityCard.addView(bitrateSeek, margin(top = 0))
        qualityCard.addView(label("1080p/90 FPS: попробуй 25–35 Мбит/с. 2K: 35–50 Мбит/с. Если игра начнёт терять FPS, снизь битрейт или отключи HEVC.", 11f, C_MUTED), margin(top = 2))
        page.addView(qualityCard, margin(bottom = 18))

        val audioCard = card()
        audioCard.addView(sectionTitle("ЗВУК"))
        val audioRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val audioCopy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        audioCopy.addView(label("Записывать микрофон", 15f, C_TEXT, true))
        audioCopy.addView(label("Можно отключить для записи без звука", 12f, C_MUTED), margin(top = 4))
        micSwitch = Switch(this).apply {
            text = ""
            isChecked = true
            thumbTintList = android.content.res.ColorStateList.valueOf(C_TEXT)
            trackTintList = android.content.res.ColorStateList.valueOf(C_ACCENT)
        }
        audioRow.addView(audioCopy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        audioRow.addView(micSwitch)
        audioCard.addView(audioRow)
        audioCard.addView(label("В этой версии звук берётся с микрофона. Прямой захват звука игры/системы не включён.", 11f, C_MUTED), margin(top = 12))
        page.addView(audioCard, margin(bottom = 18))

        page.addView(label("ПРОФИЛЬ НАГРУЗКИ", 12f, C_MUTED, true), margin(bottom = 8))
        val tipCard = card()
        tipCard.addView(label("Для Standoff 2 попробуй: Альбомная · 1080p · 90 FPS · 25–35 Мбит/с · HEVC. Начни запись и сразу переключись в игру. Для 2K поставь 35–50 Мбит/с. Если FPS проседает, отключи HEVC или снизь до 60 FPS.", 12f, C_MUTED))
        page.addView(tipCard, margin(bottom = 22))

        recordButton = Button(this).apply {
            text = "●   НАЧАТЬ ЗАПИСЬ"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(C_ACCENT_DARK, C_ACCENT_DARK, 18)
            minHeight = dp(60)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { onRecordButtonClick() }
        }
        page.addView(recordButton, matchWrap())
        page.addView(label("Видео сохраняется в Галерею → Movies/Ghostly Recorder", 11f, C_MUTED).apply {
            gravity = Gravity.CENTER
        }, margin(top = 13))

        bitrateSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val mbps = 4 + progress * 2
                bitrateValue.text = "$mbps Мбит/с"
                if (fromUser) prefs.edit().putInt(KEY_BITRATE, mbps).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        resolutionSpinner.onItemSelectedListener = simpleSelection { position ->
            prefs.edit().putInt(KEY_RESOLUTION, position).apply()
        }
        fpsSpinner.onItemSelectedListener = simpleSelection { position ->
            prefs.edit().putInt(KEY_FPS, position).apply()
        }
        orientationSpinner.onItemSelectedListener = simpleSelection { position ->
            prefs.edit().putInt(KEY_ORIENTATION, position).apply()
        }
        hevcSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_HEVC, checked).apply()
        }
        micSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_MIC, checked).apply()
        }

        scroll.addView(page)
        frame.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(frame)
    }

    private fun restoreSettings() {
        // Existing installs stored a 2–12 Mbit/s slider value. Move that old
        // maximum to a better starting point once, without overriding later choices.
        if (!prefs.getBoolean(KEY_QUALITY_MIGRATED, false)) {
            val oldBitrate = prefs.getInt(KEY_BITRATE, 28)
            if (oldBitrate <= 12) prefs.edit().putInt(KEY_BITRATE, 28).apply()
            prefs.edit().putBoolean(KEY_QUALITY_MIGRATED, true).apply()
        }
        resolutionSpinner.setSelection(prefs.getInt(KEY_RESOLUTION, 2).coerceIn(0, 3))
        fpsSpinner.setSelection(prefs.getInt(KEY_FPS, 1).coerceIn(0, 3))
        orientationSpinner.setSelection(prefs.getInt(KEY_ORIENTATION, 0).coerceIn(0, 2))
        bitrateSeek.progress = ((prefs.getInt(KEY_BITRATE, 28).coerceIn(4, 50) - 4) / 2).coerceIn(0, 23)
        micSwitch.isChecked = prefs.getBoolean(KEY_MIC, true)
        hevcSwitch.isChecked = prefs.getBoolean(KEY_HEVC, true)
        bitrateValue.text = "${4 + bitrateSeek.progress * 2} Мбит/с"
    }

    private fun onRecordButtonClick() {
        if (isRecording) {
            startService(Intent(this, ScreenRecorderService::class.java).setAction(ScreenRecorderService.ACTION_STOP))
            return
        }
        if (micSwitch.isChecked && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        } else {
            requestScreenCapturePermission()
        }
    }

    private fun requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            val statusBar = getSystemService(StatusBarManager::class.java)
            statusBar?.requestAddTileService(
                ComponentName(this, RecorderTileService::class.java),
                "Ghostly Recorder",
                Icon.createWithResource(this, android.R.drawable.ic_menu_camera),
                mainExecutor
            ) { result ->
                val message = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Ghostly Recorder добавлен в быстрые настройки"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Плитка Ghostly Recorder уже добавлена"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "Добавление отменено"
                    else -> "Не удалось добавить плитку. Попробуй через меню изменения быстрых настроек."
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Открой шторку Android → Изменить (карандаш) → добавь Ghostly Recorder.", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestScreenCapturePermission() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Uses the system MediaProjection consent flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Запись экрана не разрешена", Toast.LENGTH_SHORT).show()
            return
        }

        val serviceIntent = Intent(this, ScreenRecorderService::class.java).apply {
            action = ScreenRecorderService.ACTION_START
            putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecorderService.EXTRA_PROJECTION_DATA, data)
            putExtra(ScreenRecorderService.EXTRA_RESOLUTION, RESOLUTION_SHORT_EDGES[resolutionSpinner.selectedItemPosition])
            putExtra(ScreenRecorderService.EXTRA_FPS, FPS_VALUES[fpsSpinner.selectedItemPosition])
            putExtra(ScreenRecorderService.EXTRA_BITRATE, (4 + bitrateSeek.progress * 2) * 1_000_000)
            putExtra(ScreenRecorderService.EXTRA_MIC, micSwitch.isChecked)
            putExtra(ScreenRecorderService.EXTRA_HEVC, hevcSwitch.isChecked)
            putExtra(ScreenRecorderService.EXTRA_ORIENTATION, when (orientationSpinner.selectedItemPosition) {
                0 -> ORIENTATION_LANDSCAPE
                1 -> ORIENTATION_PORTRAIT
                else -> ORIENTATION_AUTO
            })
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
        statusTitle.text = "ПОДГОТОВКА ЗАПИСИ…"
        statusDetail.text = "Разрешение получено, запускаем кодировщик"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                requestScreenCapturePermission()
            } else {
                Toast.makeText(this, "Микрофон отключён: можно продолжить без аудио.", Toast.LENGTH_LONG).show()
                micSwitch.isChecked = false
                requestScreenCapturePermission()
            }
        }
    }

    private fun updateStateUi(message: String) {
        val active = isRecording
        statusTitle.text = if (active) "●  ЗАПИСЬ ИДЁТ" else if (message.contains("ошиб", true) || message.contains("не удалось", true)) "НЕ УДАЛОСЬ ЗАПУСТИТЬ" else "ГОТОВО К ЗАПИСИ"
        statusTitle.setTextColor(if (active) C_RED else C_TEXT)
        statusDetail.text = if (active) message.ifBlank { "Идёт захват экрана" } else message.ifBlank { "Выбери качество и нажми «Начать запись»" }
        recordButton.text = if (active) "■   ОСТАНОВИТЬ И СОХРАНИТЬ" else "●   НАЧАТЬ ЗАПИСЬ"
        recordButton.background = rounded(if (active) C_RED else C_ACCENT_DARK, if (active) C_RED else C_ACCENT_DARK, 18)
        resolutionSpinner.isEnabled = !active
        fpsSpinner.isEnabled = !active
        orientationSpinner.isEnabled = !active
        bitrateSeek.isEnabled = !active
        micSwitch.isEnabled = !active
        hevcSwitch.isEnabled = !active
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(C_PANEL, C_STROKE, 18)
    }

    private fun sectionTitle(value: String) = label(value, 12f, C_ACCENT, true).apply {
        letterSpacing = 0.09f
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = true
        }

    private fun makeSpinner(items: List<String>): Spinner {
        val spinner = Spinner(this)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            private fun makeLabel(text: String, parent: ViewGroup?, dropdown: Boolean): TextView =
                TextView(this@MainActivity).apply {
                    this.text = text
                    textSize = 13f
                    setTextColor(C_TEXT)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    if (dropdown) setBackgroundColor(C_PANEL) else background = rounded(C_INPUT, C_STROKE, 12)
                    minHeight = dp(48)
                }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                makeLabel(getItem(position).orEmpty(), parent, false)

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                makeLabel(getItem(position).orEmpty(), parent, true)
        }
        spinner.adapter = adapter
        spinner.background = rounded(C_INPUT, C_STROKE, 12)
        spinner.setPopupBackgroundDrawable(rounded(C_PANEL, C_STROKE, 12))
        return spinner
    }

    private fun simpleSelection(onSelected: (Int) -> Unit) = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = onSelected(position)
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_CAPTURE = 110
        private const val REQUEST_MIC = 111
        private const val PREFS = "ghostly_recorder"
        private const val KEY_RECORDING = "recording_active"
        private const val KEY_RESOLUTION = "resolution_index"
        private const val KEY_FPS = "fps_index"
        private const val KEY_BITRATE = "bitrate_mbps"
        private const val KEY_MIC = "microphone_enabled"
        private const val KEY_HEVC = "hevc_enabled"
        private const val KEY_ORIENTATION = "orientation_index"
        private const val KEY_QUALITY_MIGRATED = "quality_slider_v2_migrated"

        private const val ORIENTATION_LANDSCAPE = 0
        private const val ORIENTATION_PORTRAIT = 1
        private const val ORIENTATION_AUTO = 2
        const val ACTION_FROM_TILE = "ru.selteck.ghostly.recorder.action.FROM_TILE"

        private val RESOLUTION_SHORT_EDGES = intArrayOf(480, 720, 1080, 1440)
        private val FPS_VALUES = intArrayOf(30, 60, 90, 120)

        private val C_BG = Color.rgb(11, 11, 18)
        private val C_PANEL = Color.rgb(21, 21, 33)
        private val C_INPUT = Color.rgb(30, 30, 45)
        private val C_STROKE = Color.rgb(49, 47, 69)
        private val C_TEXT = Color.rgb(244, 242, 255)
        private val C_MUTED = Color.rgb(157, 156, 179)
        private val C_ACCENT = Color.rgb(167, 139, 250)
        private val C_ACCENT_DARK = Color.rgb(111, 67, 224)
        private val C_GREEN = Color.rgb(74, 222, 128)
        private val C_RED = Color.rgb(248, 113, 113)
    }
}
