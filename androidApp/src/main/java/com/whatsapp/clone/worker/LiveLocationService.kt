package com.whatsapp.clone.worker

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.whatsapp.clone.MainActivity
import kotlinx.coroutines.*

class LiveLocationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var trackingJob: Job? = null
    private var currentIntervalMinutes: Long = 1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stopping LiveLocationService by request")
            stopTracking()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val requestedInterval = intent?.getLongExtra(EXTRA_INTERVAL_MINUTES, 1L) ?: 1L
        currentIntervalMinutes = requestedInterval.coerceAtLeast(1L)
        Log.i(TAG, "LiveLocationService started with interval: $currentIntervalMinutes min")

        startForegroundWithNotification(currentIntervalMinutes)
        startTrackingLoop(currentIntervalMinutes)

        return START_STICKY
    }

    private fun startForegroundWithNotification(intervalMinutes: Long) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationText = if (intervalMinutes == 1L) {
            "Live safety tracking active (syncing every 1 min)"
        } else {
            "Live safety tracking active (syncing every $intervalMinutes mins)"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VibeSync Live Location")
            .setContentText(notificationText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun startTrackingLoop(intervalMinutes: Long) {
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val intervalMs = intervalMinutes * 60 * 1000L

            // Immediate initial sync
            syncLocationWithWakeLock(powerManager)

            while (isActive) {
                delay(intervalMs)
                syncLocationWithWakeLock(powerManager)
            }
        }
    }

    private suspend fun syncLocationWithWakeLock(powerManager: PowerManager?) {
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "vibesync:live_location_tick"
        )
        try {
            wakeLock?.acquire(15_000L) // Safe 15-second wake lock to complete network sync
            Log.d(TAG, "Executing LiveLocationService background sync tick")
            LocationSyncWorker.runOnce(applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "Error in live location sync tick: ${e.message}")
        } finally {
            if (wakeLock?.isHeld == true) {
                try { wakeLock.release() } catch (_: Exception) {}
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
        Log.i(TAG, "LiveLocationService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status while live emergency location tracking is active"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "LiveLocationService"
        private const val CHANNEL_ID = "vibesync_live_location"
        private const val NOTIFICATION_ID = 9021
        const val EXTRA_INTERVAL_MINUTES = "extra_interval_minutes"
        const val ACTION_STOP = "com.vibesync.app.action.STOP_LIVE_LOCATION"

        fun start(context: Context, intervalMinutes: Long) {
            val intent = Intent(context, LiveLocationService::class.java).apply {
                putExtra(EXTRA_INTERVAL_MINUTES, intervalMinutes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
