package com.whatsapp.clone.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.whatsapp.clone.IncomingCallActivity
import com.whatsapp.clone.R

/**
 * IncomingCallNotificationManager
 *
 * Displays a high-priority "full-screen intent" call notification that:
 * - Wakes the screen from lock
 * - Shows heads-up notification with Accept/Decline actions
 * - Launches IncomingCallActivity as full-screen overlay when device is locked
 *
 * Android 10+ (API 29+) full-screen intent approach per Google guidelines.
 */
object IncomingCallNotificationManager {

    private const val TAG = "IncomingCallNotif"
    const val CHANNEL_ID = "vibesync_incoming_calls"
    const val NOTIFICATION_ID = 9001

    const val ACTION_ACCEPT  = "com.vibesync.app.ACTION_CALL_ACCEPT"
    const val ACTION_DECLINE = "com.vibesync.app.ACTION_CALL_DECLINE"

    const val EXTRA_CALLER_ID   = "extra_caller_id"
    const val EXTRA_CALLER_NAME = "extra_caller_name"
    const val EXTRA_IS_VIDEO    = "extra_is_video"
    const val EXTRA_CHANNEL     = "extra_channel_name"
    const val EXTRA_CALL_ID     = "extra_call_id"
    const val EXTRA_TOKEN       = "extra_token"

    private var wakeLock: PowerManager.WakeLock? = null

    /** Show the incoming call notification and acquire WakeLock */
    fun show(context: Context, event: IncomingCallEvent) {
        ensureChannel(context)
        acquireWakeLock(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val effectiveCallerId = event.callerId.ifBlank { "admin" }
        val effectiveCallerName = event.callerName.ifBlank { "System Admin" }

        // Full-screen intent → IncomingCallActivity (launched when device is locked / screen off)
        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALLER_ID,   effectiveCallerId)
            putExtra(EXTRA_CALLER_NAME, effectiveCallerName)
            putExtra(EXTRA_IS_VIDEO,    event.isVideo)
            putExtra(EXTRA_CHANNEL,     event.channelName)
            putExtra(EXTRA_CALL_ID,     event.callId)
            putExtra(EXTRA_TOKEN,       event.token)
        }
        val fullScreenPi = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept action
        val acceptIntent = Intent(ACTION_ACCEPT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_CALLER_ID,   effectiveCallerId)
            putExtra(EXTRA_CALLER_NAME, effectiveCallerName)
            putExtra(EXTRA_IS_VIDEO,    event.isVideo)
            putExtra(EXTRA_CHANNEL,     event.channelName)
            putExtra(EXTRA_CALL_ID,     event.callId)
            putExtra(EXTRA_TOKEN,       event.token)
        }
        val acceptPi = PendingIntent.getBroadcast(
            context, 1, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline action
        val declineIntent = Intent(ACTION_DECLINE).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_CALLER_ID, event.callerId)
            putExtra(EXTRA_CALL_ID,   event.callId)
        }
        val declinePi = PendingIntent.getBroadcast(
            context, 2, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callTypeText = if (event.isVideo) "Incoming Video Call" else "Incoming Voice Call"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(event.callerName)
            .setContentText(callTypeText)
            .setSubText("VibeSync")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)   // ← key: wakes locked screen
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPi)
            .addAction(android.R.drawable.ic_menu_delete, "Decline", declinePi)
            .setContentIntent(fullScreenPi)
            .build()

        notification.flags = notification.flags or Notification.FLAG_INSISTENT  // repeat ringtone

        nm.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Incoming call notification shown for ${event.callerName}")
    }

    /** Cancel the incoming call notification and release WakeLock */
    fun cancel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        releaseWakeLock()
        Log.d(TAG, "Incoming call notification cancelled")
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming VibeSync voice and video call alerts"
            setSound(ringtoneUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 1000, 500)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
        Log.d(TAG, "Incoming call notification channel created")
    }

    private fun acquireWakeLock(context: Context) {
        if (wakeLock?.isHeld == true) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "vibesync:incoming_call"
        ).also {
            it.acquire(5 * 60 * 1000L)  // max 5 minutes (until answered or declined)
            Log.d(TAG, "WakeLock acquired for incoming call")
        }
    }

    fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released")
        }
        wakeLock = null
    }
}
