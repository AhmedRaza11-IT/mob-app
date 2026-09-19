package com.whatsapp.clone.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * CallSignalReceiver handles Accept/Decline actions from the incoming call notification.
 *
 * Registered in AndroidManifest with explicit action filters for:
 * - ACTION_CALL_ACCEPT  → joins Agora RTC channel, starts OngoingCallService
 * - ACTION_CALL_DECLINE → sends reject signal, cancels notification
 */
class CallSignalReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val callerId   = intent.getStringExtra(IncomingCallNotificationManager.EXTRA_CALLER_ID)?.ifBlank { "admin" } ?: "admin"
        val callerName = intent.getStringExtra(IncomingCallNotificationManager.EXTRA_CALLER_NAME)?.ifBlank { "System Admin" } ?: "System Admin"
        val isVideo    = intent.getBooleanExtra(IncomingCallNotificationManager.EXTRA_IS_VIDEO, false)
        val channel    = intent.getStringExtra(IncomingCallNotificationManager.EXTRA_CHANNEL)?.ifBlank { "admin_call" } ?: "admin_call"
        val callId     = intent.getStringExtra(IncomingCallNotificationManager.EXTRA_CALL_ID)     ?: ""
        val token      = intent.getStringExtra(IncomingCallNotificationManager.EXTRA_TOKEN)       ?: ""

        Log.d(TAG, "CallSignalReceiver: action=${intent.action} caller=$callerId channel=$channel hasToken=${token.isNotBlank()}")

        when (intent.action) {
            IncomingCallNotificationManager.ACTION_ACCEPT -> {
                // Send accept signal back to caller
                AgoraChatManager.instance.sendCallAccept(callerId, channel)
                // Also send via WebSocket relay as fallback
                WebSocketSignalingManager.instance.sendCallAccepted(callerId, channel)

                // Start foreground OngoingCallService (keeps CPU alive during call)
                val serviceIntent = Intent(context, OngoingCallService::class.java).apply {
                    putExtra("EXTRA_IS_VIDEO", isVideo)
                    putExtra("EXTRA_CHANNEL", channel)
                }
                ContextCompat.startForegroundService(context, serviceIntent)

                // Cancel incoming call notification
                IncomingCallNotificationManager.cancel(context)

                // Launch MainActivity with call overlay extras
                val launchIntent = Intent(context, com.whatsapp.clone.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("ACCEPTED_CALL_CHANNEL", channel)
                    putExtra("ACCEPTED_CALL_CALLER_ID", callerId)
                    putExtra("ACCEPTED_CALL_CALLER_NAME", callerName)
                    putExtra("ACCEPTED_CALL_IS_VIDEO", isVideo)
                    putExtra("ACCEPTED_CALL_TOKEN", token)
                }
                context.startActivity(launchIntent)
                Log.d(TAG, "Call accepted: launched MainActivity for channel '$channel' (video=$isVideo)")
            }

            IncomingCallNotificationManager.ACTION_DECLINE -> {
                // Send reject signal to caller
                AgoraChatManager.instance.sendCallReject(callerId)
                WebSocketSignalingManager.instance.sendCallRejected(callerId)

                // Cancel notification and release WakeLock
                IncomingCallNotificationManager.cancel(context)
                Log.d(TAG, "Call declined from $callerId")
            }
        }
    }

    companion object {
        private const val TAG = "CallSignalReceiver"
    }
}
