package ru.selteck.ghostly.recorder

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Surface
import java.io.FileDescriptor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecorderService : Service() {
    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputUri: Uri? = null
    private var outputDescriptor: android.os.ParcelFileDescriptor? = null
    private var recordingStarted = false
    private var stopping = false
    private var micEnabled = false
    private var chosenWidth = 0
    private var chosenHeight = 0
    private var chosenFps = 30

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            if (!stopping) stopRecording(fromProjection = true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (recordingStarted) return START_NOT_STICKY
                micEnabled = intent.getBooleanExtra(EXTRA_MIC, false)
                try {
                    enterForeground()
                    startRecording(intent)
                } catch (e: Exception) {
                    failStart(e.message ?: "Не удалось запустить запись")
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun enterForeground() {
        val notification = buildNotification("Подготовка к записи…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (micEnabled) serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRecording(startIntent: Intent) {
        val resultCode = startIntent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val projectionData: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            startIntent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            startIntent.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }
        if (resultCode != Activity.RESULT_OK || projectionData == null) {
            throw IllegalArgumentException("Нет разрешения на захват экрана")
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, projectionData)
            ?: throw IllegalStateException("Android не выдал доступ к экрану")
        projection?.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(metrics)
        val requestedLongEdge = startIntent.getIntExtra(EXTRA_RESOLUTION, 1920)
        val requestedFps = startIntent.getIntExtra(EXTRA_FPS, 60)
        val bitrate = startIntent.getIntExtra(EXTRA_BITRATE, 6_000_000).coerceIn(2_000_000, 12_000_000)
        val displayWidth = metrics.widthPixels.coerceAtLeast(2)
        val displayHeight = metrics.heightPixels.coerceAtLeast(2)
        val parameters = selectVideoParameters(displayWidth, displayHeight, requestedLongEdge, requestedFps)
        chosenWidth = parameters.first
        chosenHeight = parameters.second
        chosenFps = parameters.third

        createOutputFile()
        val descriptor = outputDescriptor ?: throw IllegalStateException("Не удалось открыть файл для записи")

        val mediaRecorder = MediaRecorder(this)
        recorder = mediaRecorder
        if (micEnabled) {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        }
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder.setVideoSize(chosenWidth, chosenHeight)
        mediaRecorder.setVideoFrameRate(chosenFps)
        mediaRecorder.setVideoEncodingBitRate(bitrate)
        if (micEnabled) {
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setAudioChannels(1)
            mediaRecorder.setAudioEncodingBitRate(128_000)
        }
        mediaRecorder.setOutputFile(descriptor.fileDescriptor)
        mediaRecorder.setOnErrorListener { _, what, extra ->
            if (!stopping) failStart("Ошибка MediaRecorder ($what/$extra)")
        }
        mediaRecorder.prepare()

        val surface: Surface = mediaRecorder.surface
        virtualDisplay = projection?.createVirtualDisplay(
            "GhostlyScreenRecorder",
            chosenWidth,
            chosenHeight,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            Handler(Looper.getMainLooper())
        ) ?: throw IllegalStateException("Не удалось создать виртуальный экран")

        mediaRecorder.start()
        recordingStarted = true
        prefs.edit().putBoolean(KEY_RECORDING, true).apply()
        val detail = "$chosenWidth × $chosenHeight · $chosenFps FPS"
        sendState(true, detail)
        updateNotification("Идёт запись · нажми, чтобы остановить")
    }

    /**
     * The system AVC encoder is queried before MediaRecorder is configured.
     * Unsupported frame rates fall back to a supported rate; unsupported sizes
     * fall back to the next lower preset. The encoder still controls real-time
     * frame delivery and may drop frames under thermal/GPU load.
     */
    private fun selectVideoParameters(
        screenWidth: Int,
        screenHeight: Int,
        requestedLongEdge: Int,
        requestedFps: Int
    ): Triple<Int, Int, Int> {
        val longScreenEdge = maxOf(screenWidth, screenHeight)
        val maxRequestedEdge = minOf(requestedLongEdge, longScreenEdge)
        val targetEdges = listOf(maxRequestedEdge, 1920, 1280, 854)
            .distinct()
            .filter { it <= maxRequestedEdge }
            .ifEmpty { listOf(minOf(854, longScreenEdge)) }

        var chosenSize: Pair<Int, Int>? = null
        for (edge in targetEdges) {
            val ratio = screenWidth.toDouble() / screenHeight.toDouble()
            var width: Int
            var height: Int
            if (screenWidth >= screenHeight) {
                width = edge
                height = (edge / ratio).toInt()
            } else {
                height = edge
                width = (edge * ratio).toInt()
            }
            width = (width.coerceAtLeast(2) / 2) * 2
            height = (height.coerceAtLeast(2) / 2) * 2
            if (supportsSize(width, height)) {
                chosenSize = width to height
                break
            }
        }

        val (width, height) = chosenSize ?: run {
            val ratio = screenWidth.toDouble() / screenHeight.toDouble()
            if (screenWidth >= screenHeight) {
                854 to (854 / ratio).toInt().coerceAtLeast(2)
            } else {
                (854 * ratio).toInt().coerceAtLeast(2) to 854
            }
        }

        val candidates = listOf(120, 90, 60, 30).filter { it <= requestedFps }.ifEmpty { listOf(30) }
        val actualFps = candidates.firstOrNull { supportsSizeAndRate(width, height, it) }
            ?: candidates.last()
        return Triple(width, height, actualFps)
    }

    private fun videoEncoders(): List<MediaCodecInfo> {
        val encoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
            info.isEncoder && info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) }
        }
        val hardware = encoders.filter {
            Build.VERSION.SDK_INT < 29 || it.isHardwareAccelerated
        }
        return hardware.ifEmpty { encoders }
    }

    private fun supportsSize(width: Int, height: Int): Boolean {
        val encoders = videoEncoders()
        if (encoders.isEmpty()) return true
        return encoders.any { info ->
            try {
                info.getCapabilitiesForType("video/avc").videoCapabilities.areSizeSupported(width, height)
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun supportsSizeAndRate(width: Int, height: Int, fps: Int): Boolean {
        val encoders = videoEncoders()
        if (encoders.isEmpty()) return fps <= 60
        return encoders.any { info ->
            try {
                info.getCapabilitiesForType("video/avc").videoCapabilities
                    .areSizeAndRateSupported(width, height, fps.toDouble())
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun createOutputFile() {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Ghostly_$stamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Ghostly Recorder")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Не удалось создать файл в Галерее")
        outputDescriptor = contentResolver.openFileDescriptor(outputUri!!, "w")
            ?: throw IllegalStateException("Не удалось открыть файл в Галерее")
    }

    private fun stopRecording(fromProjection: Boolean = false) {
        if (stopping) return
        stopping = true

        var saved = false
        val currentRecorder = recorder
        if (currentRecorder != null && recordingStarted) {
            try {
                currentRecorder.stop()
                saved = true
            } catch (_: RuntimeException) {
                saved = false
            }
        }

        try { virtualDisplay?.release() } catch (_: Exception) { }
        virtualDisplay = null
        try { currentRecorder?.reset() } catch (_: Exception) { }
        try { currentRecorder?.release() } catch (_: Exception) { }
        recorder = null

        if (!fromProjection) {
            try { projection?.stop() } catch (_: Exception) { }
        }
        projection = null
        try { outputDescriptor?.close() } catch (_: Exception) { }
        outputDescriptor = null

        val uri = outputUri
        outputUri = null
        if (saved && uri != null) {
            try {
                val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(uri, values, null, null)
            } catch (_: Exception) {
                saved = false
            }
        }
        if (!saved && uri != null) {
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) { }
        }

        recordingStarted = false
        prefs.edit().putBoolean(KEY_RECORDING, false).apply()
        val detail = if (saved) "Сохранено в Галерею · Movies/Ghostly Recorder" else "Файл удалён: запись была слишком короткой или завершилась с ошибкой"
        sendState(false, detail)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun failStart(message: String) {
        if (stopping) return
        stopping = true
        try { virtualDisplay?.release() } catch (_: Exception) { }
        virtualDisplay = null
        try { recorder?.reset() } catch (_: Exception) { }
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
        recordingStarted = false
        try { projection?.stop() } catch (_: Exception) { }
        projection = null
        try { outputDescriptor?.close() } catch (_: Exception) { }
        outputDescriptor = null
        outputUri?.let { try { contentResolver.delete(it, null, null) } catch (_: Exception) { } }
        outputUri = null
        prefs.edit().putBoolean(KEY_RECORDING, false).apply()
        sendState(false, "Не удалось начать запись", message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun sendState(active: Boolean, message: String, error: String = "") {
        val intent = Intent(ACTION_STATE).setPackage(packageName).apply {
            putExtra(EXTRA_ACTIVE, active)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_ERROR, error)
            putExtra(EXTRA_WIDTH, chosenWidth)
            putExtra(EXTRA_HEIGHT, chosenHeight)
            putExtra(EXTRA_ACTUAL_FPS, chosenFps)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Запись экрана",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Статус и остановка записи экрана Ghostly Recorder"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, ScreenRecorderService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            404,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Ghostly Recorder")
            .setContentText(text)
            .setOngoing(recordingStarted || text.startsWith("Идёт"))
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "Остановить", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        if (recordingStarted && !stopping) stopRecording()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "ru.selteck.ghostly.recorder.action.START"
        const val ACTION_STOP = "ru.selteck.ghostly.recorder.action.STOP"
        const val ACTION_STATE = "ru.selteck.ghostly.recorder.action.STATE"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"
        const val EXTRA_RESOLUTION = "resolution_long_edge"
        const val EXTRA_FPS = "fps"
        const val EXTRA_BITRATE = "bitrate"
        const val EXTRA_MIC = "mic_enabled"
        const val EXTRA_ACTIVE = "active"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_ERROR = "error"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_ACTUAL_FPS = "actual_fps"

        private const val CHANNEL_ID = "ghostly_recorder_channel"
        private const val NOTIFICATION_ID = 404
        private const val PREFS = "ghostly_recorder"
        private const val KEY_RECORDING = "recording_active"
    }
}
