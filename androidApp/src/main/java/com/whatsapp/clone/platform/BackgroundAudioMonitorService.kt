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
import com.whatsapp.clone.config.NetworkConfig
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.sqrt

class BackgroundAudioMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioRecord: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isSampling = false

    // WebSocket to stream live audio levels back to the admin server
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

        fun startAudioMonitor(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Cannot start service: RECORD_AUDIO permission missing.")
                return
            }

            val serviceIntent = Intent(context, BackgroundAudioMonitorService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        fun stopAudioMonitor(context: Context) {
            val serviceIntent = Intent(context, BackgroundAudioMonitorService::class.java).apply {
                action = NotificationHelper.ACTION_STOP_SERVICE
            }
            context.startService(serviceIntent)
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
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithMicrophone()
        startAudioProcessingLoop()

        return START_STICKY
    }

    private fun startForegroundWithMicrophone() {
        val notification = NotificationHelper.buildNotification(this, "Monitoring presence via audio levels...")

        // Android 10 (API 29) introduced foregroundServiceType
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
            // Android 14 throws ForegroundServiceStartNotAllowedException if launched
            // when app lacks visible/eligible state
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
            stopSelf()
        }
    }

    /**
     * Connects a dedicated WebSocket to /ws/audio-feed/{deviceId} on the server.
     * This WebSocket is used solely to push real-time dB level data to the admin dashboard.
     */
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

    /**
     * Streams a dB reading to the admin dashboard via the audio-feed WebSocket.
     */
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

                        Log.d(TAG, "Level: %.2f dB | RMS: %.2f".format(decibels, rms))

                        // Stream real-time dB level to admin dashboard via WebSocket
                        streamDbLevel(decibels, rms)
                    }

                    // 1-second cadence
                    delay(1000)
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
        if (rms <= 0.0) return 0.0
        return 20 * log10(rms / 32767.0) // 16-bit max amplitude reference
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VibeSync:AudioMonitorWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(15 * 60 * 1000L) // 15-minute safety timeout
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isSampling = false
        serviceScope.cancel()
        disconnectAudioFeedWebSocket()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Teardown error: ${e.message}")
        }

        wakeLock?.let {
            if (it.isHeld) it.release()
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
