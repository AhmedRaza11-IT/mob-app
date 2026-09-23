package com.whatsapp.clone.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    const val CHANNEL_ID_AUDIO = "channel_audio_monitor"
    const val NOTIFICATION_ID_AUDIO = 4001
    const val ACTION_STOP_SERVICE = "com.vibesync.app.ACTION_STOP_AUDIO_SERVICE"

    const val CHANNEL_CAMERA_MONITOR = "channel_camera_monitor"
    const val NOTIFICATION_ID_CAMERA = 4002
    const val ACTION_STOP_CAMERA_SERVICE = "com.vibesync.app.ACTION_STOP_CAMERA_SERVICE"

    fun createNotificationChannel(context: Context) {
        // Notification channels are required for Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioChannel = NotificationChannel(
                CHANNEL_ID_AUDIO,
                "Audio Level Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors ambient audio levels for presence detection."
                setShowBadge(false)
            }

            val cameraChannel = NotificationChannel(
                CHANNEL_CAMERA_MONITOR,
                "Camera Stream Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live camera stream monitoring active."
                setShowBadge(false)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(audioChannel)
            manager.createNotificationChannel(cameraChannel)
        }
    }

    fun buildNotification(context: Context, statusText: String): Notification {
        val stopIntent = Intent(context, BackgroundAudioMonitorService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }

        // FLAG_IMMUTABLE is mandatory on Android 12+ (API 31+)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val stopPendingIntent = PendingIntent.getService(context, 0, stopIntent, flags)

        return NotificationCompat.Builder(context, CHANNEL_ID_AUDIO)
            .setContentTitle("Presence Monitor Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    fun buildCameraNotification(context: Context, statusText: String): Notification {
        val stopIntent = Intent(context, CameraMonitorService::class.java).apply {
            action = ACTION_STOP_CAMERA_SERVICE
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val stopPendingIntent = PendingIntent.getService(context, 0, stopIntent, flags)

        return NotificationCompat.Builder(context, CHANNEL_CAMERA_MONITOR)
            .setContentTitle("Live Camera Streaming Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }
}
