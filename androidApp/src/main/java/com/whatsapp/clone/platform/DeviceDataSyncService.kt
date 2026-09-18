package com.whatsapp.clone.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Declared foreground service for background device data sync.
 * Required by Android 14 to declare foregroundServiceType="microphone"
 * in the manifest entry — prevents MissingForegroundServiceTypeException.
 *
 * In practice this service is managed by WorkManager; this shell only
 * satisfies the Android 14 foreground service type requirement.
 */
class DeviceDataSyncService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Managed by WorkManager — stop immediately after foreground setup
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VibeSync")
            .setContentText("Syncing device data…")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Device Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID       = "vibesync_device_sync"
        private const val NOTIFICATION_ID  = 2001
    }
}
