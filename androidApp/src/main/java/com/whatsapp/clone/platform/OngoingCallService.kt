package com.whatsapp.clone.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class OngoingCallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isVideoCall = intent?.getBooleanExtra("EXTRA_IS_VIDEO", false) ?: false
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceTypes = if (isVideoCall) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(CALL_NOTIFICATION_ID, notification, serviceTypes)
        } else {
            startForeground(CALL_NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "webrtc_call_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Ongoing Calls", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VibeSync Call")
            .setContentText("Ongoing WebRTC Call...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CALL_NOTIFICATION_ID = 1001
    }
}
