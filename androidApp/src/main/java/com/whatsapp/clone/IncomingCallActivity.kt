package com.whatsapp.clone

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsapp.clone.platform.AgoraChatManager
import com.whatsapp.clone.platform.IncomingCallNotificationManager
import com.whatsapp.clone.platform.WebSocketSignalingManager
import com.whatsapp.clone.platform.OngoingCallService

/**
 * IncomingCallActivity — full-screen call overlay activity.
 *
 * Launched as a full-screen intent from IncomingCallNotificationManager, with:
 *   - android:showWhenLocked="true"  → appears over lock screen
 *   - android:turnScreenOn="true"    → wakes display
 *
 * This is the Android 10+ (API 29+) compliant approach for incoming call UI.
 */
class IncomingCallActivity : ComponentActivity() {

    private val callerId   by lazy { intent?.getStringExtra(IncomingCallNotificationManager.EXTRA_CALLER_ID)?.ifBlank { "admin" } ?: "admin" }
    private val callerName by lazy { intent?.getStringExtra(IncomingCallNotificationManager.EXTRA_CALLER_NAME)?.ifBlank { "System Admin" } ?: "System Admin" }
    private val isVideo    by lazy { intent?.getBooleanExtra(IncomingCallNotificationManager.EXTRA_IS_VIDEO, false) ?: false }
    private val channelName by lazy { intent?.getStringExtra(IncomingCallNotificationManager.EXTRA_CHANNEL)?.ifBlank { "admin_call" } ?: "admin_call" }
    private val callId     by lazy { intent?.getStringExtra(IncomingCallNotificationManager.EXTRA_CALL_ID)     ?: "" }
    private val token      by lazy { intent?.getStringExtra(IncomingCallNotificationManager.EXTRA_TOKEN)       ?: "" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow activity to show over the lock screen and wake the display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        setContent {
            IncomingCallScreen(
                callerName = callerName,
                isVideo = isVideo,
                onAccept = { acceptCall() },
                onDecline = { declineCall() }
            )
        }
    }

    private fun acceptCall() {
        // Send accept signal via Agora Chat + WebSocket relay
        AgoraChatManager.instance.sendCallAccept(callerId, channelName)
        WebSocketSignalingManager.instance.sendCallAccepted(callerId, channelName)

        // Start persistent foreground service for the call
        val serviceIntent = Intent(this, OngoingCallService::class.java).apply {
            putExtra("EXTRA_IS_VIDEO", isVideo)
            putExtra("EXTRA_CHANNEL", channelName)
        }
        startForegroundService(serviceIntent)

        // Cancel the incoming call notification + release WakeLock
        IncomingCallNotificationManager.cancel(this)

        // Return to MainActivity which will show the CallOverlayScreen
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ACCEPTED_CALL_CHANNEL", channelName)
            putExtra("ACCEPTED_CALL_CALLER_ID", callerId)
            putExtra("ACCEPTED_CALL_CALLER_NAME", callerName)
            putExtra("ACCEPTED_CALL_IS_VIDEO", isVideo)
            putExtra("ACCEPTED_CALL_TOKEN", token)
        }
        startActivity(mainIntent)
        finish()
    }

    private fun declineCall() {
        // Send reject signal
        AgoraChatManager.instance.sendCallReject(callerId)
        WebSocketSignalingManager.instance.sendCallRejected(callerId)

        // Cancel notification and release WakeLock
        IncomingCallNotificationManager.cancel(this)
        finish()
    }
}

@Composable
fun IncomingCallScreen(
    callerName: String,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))  // deep dark navy
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            // Caller info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF128C7E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callerName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isVideo) "Incoming Video Call…" else "Incoming Voice Call…",
                    color = Color(0xFF25D366),
                    fontSize = 16.sp
                )
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 60.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onDecline,
                        containerColor = Color(0xFFEF4444),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Decline", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                }

                // Accept
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onAccept,
                        containerColor = Color(0xFF25D366),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = "Accept",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Accept", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                }
            }
        }
    }
}
