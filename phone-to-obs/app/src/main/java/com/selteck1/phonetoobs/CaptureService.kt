package com.selteck1.phonetoobs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.InternalAudioSource
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.library.generic.GenericStream

class CaptureService : Service(), ConnectChecker {
    companion object {
        const val ACTION_START = "com.selteck1.phonetoobs.START"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_URL = "url"
        private const val CHANNEL = "phonetoobs_capture"
    }

    private var stream: GenericStream? = null
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "PhoneToOBS capture", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL)
                .setContentTitle("PhoneToOBS")
                .setContentText("Передача экрана в OBS активна")
                .setSmallIcon(android.R.drawable.presence_video_online)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
                .setContentTitle("PhoneToOBS")
                .setContentText("Передача экрана в OBS активна")
                .setSmallIcon(android.R.drawable.presence_video_online)
                .build()
        }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(41, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(41, notification)

        try {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                       else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
            if (data == null) throw IllegalStateException("MediaProjection result missing")
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels.coerceAtLeast(2) / 2 * 2
            val height = metrics.heightPixels.coerceAtLeast(2) / 2 * 2
            val bitrate = 50_000_000
            val currentProjection = projection ?: throw IllegalStateException("MediaProjection unavailable")
            val screen = ScreenSource(applicationContext, currentProjection)
            val audio = InternalAudioSource(currentProjection)
            val output = GenericStream(this, this, screen, audio)
            stream = output
            val videoReady = output.prepareVideo(width, height, bitrate, fps = 60, rotation = 0)
            val audioReady = output.prepareAudio(48000, true, 320000, echoCanceler = false, noiseSuppressor = false)
            if (!videoReady || !audioReady) throw IllegalStateException("Устройство не поддерживает выбранные параметры кодирования")
            output.startStream(intent.getStringExtra(EXTRA_URL) ?: "rtmp://127.0.0.1:1935/phone")
        } catch (e: Exception) {
            android.util.Log.e("PhoneToOBS", "Unable to start stream", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { stream?.stopStream() } catch (_: Exception) {}
        try { stream?.release() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        stream = null
        projection = null
        super.onDestroy()
    }

    override fun onConnectionStarted(url: String) { android.util.Log.i("PhoneToOBS", "Connecting: $url") }
    override fun onConnectionSuccess() { android.util.Log.i("PhoneToOBS", "Connected") }
    override fun onNewBitrate(bitrate: Long) { }
    override fun onConnectionFailed(reason: String) { android.util.Log.e("PhoneToOBS", "Connection failed: $reason") }
    override fun onDisconnect() { android.util.Log.i("PhoneToOBS", "Disconnected") }
    override fun onAuthError() { android.util.Log.e("PhoneToOBS", "Auth error") }
    override fun onAuthSuccess() { android.util.Log.i("PhoneToOBS", "Auth success") }
}
