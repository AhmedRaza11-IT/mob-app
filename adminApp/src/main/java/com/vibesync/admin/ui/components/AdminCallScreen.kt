package com.vibesync.admin.ui.components

import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vibesync.admin.network.AdminApiClient
import com.vibesync.admin.ui.theme.*
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val AGORA_APP_ID = "e63a3e4f21124b659d8eeaef92f367c2"

@Composable
fun AdminCallScreen(
    targetUsername: String,
    isVideo: Boolean,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rtcEngine by remember { mutableStateOf<RtcEngine?>(null) }
    var channelName by remember { mutableStateOf("") }
    var callStatus by remember { mutableStateOf("Initiating call...") }
    var durationSec by remember { mutableIntStateOf(0) }
    var isConnected by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isVideoDisabled by remember { mutableStateOf(!isVideo) }

    var localSurfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    var remoteSurfaceView by remember { mutableStateOf<SurfaceView?>(null) }

    // Initialize Agora & call API
    LaunchedEffect(targetUsername) {
        try {
            val res = AdminApiClient.initiateCall(targetUsername, isVideo)
            val channel = res.optString("channel_name")
            val token = res.optString("token")
            channelName = channel
            callStatus = "Ringing @$targetUsername..."

            // Init Agora RTC
            val config = RtcEngineConfig().apply {
                mContext = context
                mAppId = AGORA_APP_ID
                mEventHandler = object : IRtcEngineEventHandler() {
                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                        callStatus = "Channel joined, awaiting answer..."
                    }

                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        isConnected = true
                        callStatus = "Connected"
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        onCallEnded()
                    }

                    override fun onError(err: Int) {
                        callStatus = "Error: $err"
                    }
                }
            }
            val engine = RtcEngine.create(config)
            rtcEngine = engine

            engine.enableAudio()
            if (isVideo) {
                engine.enableVideo()
                engine.startPreview()
            }

            // Join Channel as Admin UID = 0 (auto-assigned by Agora)
            engine.joinChannel(token, channel, "", 0)
        } catch (e: Exception) {
            callStatus = "Call failed: ${e.message}"
        }
    }

    // Call Duration Timer
    LaunchedEffect(isConnected) {
        if (isConnected) {
            while (true) {
                delay(1000)
                durationSec++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                rtcEngine?.leaveChannel()
                RtcEngine.destroy()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        // Video Views if in video call
        if (isVideo) {
            // Remote Video Fullscreen
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).also { sv ->
                        remoteSurfaceView = sv
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Local Preview PiP
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 16.dp)
                    .size(width = 110.dp, height = 160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slate900)
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).also { sv ->
                            localSurfaceView = sv
                            rtcEngine?.setupLocalVideo(VideoCanvas(sv, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Voice Call or Overlay Info
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isVideo) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(BrandPurple.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = targetUsername.take(1).uppercase(),
                        color = BrandPurpleLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 42.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            Text(
                text = targetUsername,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isConnected) {
                    val m = durationSec / 60
                    val s = durationSec % 60
                    String.format("%02d:%02d", m, s)
                } else {
                    callStatus
                },
                color = if (isConnected) EmeraldSuccess else Slate400,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Bottom Controls Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 50.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute Button
            IconButton(
                onClick = {
                    isMuted = !isMuted
                    rtcEngine?.muteLocalAudioStream(isMuted)
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(if (isMuted) RedDelete.copy(alpha = 0.2f) else Slate800, CircleShape)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mute",
                    tint = if (isMuted) RedDelete else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // End Call Button
            IconButton(
                onClick = {
                    scope.launch {
                        try {
                            AdminApiClient.endCall(targetUsername, channelName)
                        } catch (_: Exception) {}
                        onCallEnded()
                    }
                },
                modifier = Modifier
                    .size(68.dp)
                    .background(RedDelete, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Video Toggle Button
            if (isVideo) {
                IconButton(
                    onClick = {
                        isVideoDisabled = !isVideoDisabled
                        rtcEngine?.muteLocalVideoStream(isVideoDisabled)
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(if (isVideoDisabled) RedDelete.copy(alpha = 0.2f) else Slate800, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isVideoDisabled) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = "Toggle Video",
                        tint = if (isVideoDisabled) RedDelete else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(56.dp))
            }
        }
    }
}
