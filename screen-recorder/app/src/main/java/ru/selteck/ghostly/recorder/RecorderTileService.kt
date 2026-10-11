package ru.selteck.ghostly.recorder

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile that sits alongside Wi-Fi / Hotspot / Internet controls.
 *
 * Android does not permit silent screen capture. The idle tile opens the app and
 * triggers the normal MediaProjection consent flow; tapping it while recording
 * stops the active recording.
 */
class RecorderTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        val recording = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_RECORDING, false)

        if (recording) {
            startService(
                Intent(this, ScreenRecorderService::class.java)
                    .setAction(ScreenRecorderService.ACTION_STOP)
            )
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.label = "Ghostly Recorder"
            qsTile?.subtitle = "Остановка записи"
            qsTile?.updateTile()
            return
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_FROM_TILE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                TILE_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }

    private fun syncTile() {
        val recording = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_RECORDING, false)
        qsTile?.apply {
            label = "Ghostly Recorder"
            subtitle = if (recording) "Нажми, чтобы остановить" else "Нажми, чтобы записать"
            state = if (recording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    companion object {
        private const val PREFS = "ghostly_recorder"
        private const val KEY_RECORDING = "recording_active"
        private const val TILE_REQUEST_CODE = 882
    }
}
