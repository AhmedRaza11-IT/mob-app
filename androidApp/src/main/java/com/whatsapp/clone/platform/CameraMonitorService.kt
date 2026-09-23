package com.whatsapp.clone.platform

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.whatsapp.clone.AGORA_APP_ID
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig

class CameraMonitorService : Service() {

    private var rtcEngine: RtcEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isStreaming = false
    private var currentChannel: String? = null

    companion object {
        private const val TAG = "CameraMonitorService"
        const val EXTRA_CHANNEL_NAME = "EXTRA_CHANNEL_NAME"
        const val EXTRA_TOKEN = "EXTRA_TOKEN"
        const val EXTRA_APP_ID = "EXTRA_APP_ID"

        fun startCameraMonitor(
            context: Context,
            channelName: String,
            token: String,
            appId: String
        ) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Cannot start service: CAMERA permission missing.")
                return
            }

            val serviceIntent = Intent(context, CameraMonitorService::class.java).apply {
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_APP_ID, appId)
            }
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CameraMonitorService: ${e.message}", e)
            }
        }

        const val ACTION_SWITCH_CAMERA = "com.vibesync.app.ACTION_SWITCH_CAMERA"

        fun switchCamera(context: Context) {
            val serviceIntent = Intent(context, CameraMonitorService::class.java).apply {
                action = ACTION_SWITCH_CAMERA
            }
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch camera: ${e.message}", e)
            }
        }

        fun stopCameraMonitor(context: Context) {
            val serviceIntent = Intent(context, CameraMonitorService::class.java).apply {
                action = NotificationHelper.ACTION_STOP_CAMERA_SERVICE
            }
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop CameraMonitorService: ${e.message}", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SWITCH_CAMERA) {
            Log.i(TAG, "Received ACTION_SWITCH_CAMERA, toggling camera lens")
            val ret = rtcEngine?.switchCamera()
            Log.i(TAG, "switchCamera result code: $ret")
            return START_NOT_STICKY
        }

        if (intent?.action == NotificationHelper.ACTION_STOP_CAMERA_SERVICE) {
            Log.i(TAG, "Received STOP action in onStartCommand")
            stopStreamingAndSelf()
            return START_NOT_STICKY
        }

        val channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""
        val token = intent?.getStringExtra(EXTRA_TOKEN) ?: ""
        val appId = intent?.getStringExtra(EXTRA_APP_ID) ?: ""

        if (channelName.isBlank()) {
            Log.w(TAG, "Missing channelName in start intent, stopping service")
            stopStreamingAndSelf()
            return START_NOT_STICKY
        }

        startForegroundWithCamera()
        startStreaming(channelName, token, appId)

        return START_NOT_STICKY
    }

    private fun startForegroundWithCamera() {
        val notification = NotificationHelper.buildCameraNotification(
            this,
            "Authorized live camera stream is active"
        )

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID_CAMERA,
                notification,
                serviceType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera foreground service: ${e.message}", e)
        }
    }

    private fun startStreaming(channelName: String, token: String, appId: String) {
        if (isStreaming && currentChannel == channelName) {
            Log.d(TAG, "Already streaming to channel: $channelName")
            return
        }

        stopAgora()

        val effectiveAppId = if (appId.isNotBlank()) appId else AGORA_APP_ID
        currentChannel = channelName

        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = effectiveAppId
                mEventHandler = object : IRtcEngineEventHandler() {
                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                        Log.i(TAG, "Successfully joined Agora camera stream channel: $channel (uid=$uid)")
                        isStreaming = true
                    }

                    override fun onError(err: Int) {
                        Log.e(TAG, "Agora RTC error code: $err")
                    }

                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        Log.i(TAG, "Admin viewer joined Agora channel: $uid")
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        Log.i(TAG, "Admin viewer went offline: $uid (reason=$reason)")
                    }

                    override fun onTokenPrivilegeWillExpire(token: String?) {
                        Log.w(TAG, "Agora RTC token will expire soon")
                    }
                }
                mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            }

            val engine = RtcEngine.create(config).apply {
                enableVideo()
                disableAudio()
                startPreview()
            }
            rtcEngine = engine

            val ret = engine.joinChannel(token, channelName, "", 0)
            Log.i(TAG, "joinChannel result code: $ret for channel: $channelName")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Agora for camera monitor: ${e.message}", e)
            stopStreamingAndSelf()
        }
    }

    private fun stopStreamingAndSelf() {
        stopAgora()
        releaseWakeLock()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground: ${e.message}")
        }
        stopSelf()
    }

    private fun stopAgora() {
        try {
            rtcEngine?.let { engine ->
                engine.stopPreview()
                engine.leaveChannel()
                RtcEngine.destroy()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying RtcEngine: ${e.message}")
        } finally {
            rtcEngine = null
            isStreaming = false
            currentChannel = null
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VibeSync:CameraMonitorWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake lock: ${e.message}")
        } finally {
            wakeLock = null
        }
    }

    override fun onDestroy() {
        stopStreamingAndSelf()
        super.onDestroy()
    }
}
