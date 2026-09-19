package com.whatsapp.clone.platform

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val channelName: String
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
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var currentUserId: String? = null
    private var isConnected = false

    private val _incomingMessages = MutableSharedFlow<IncomingMessageEvent>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<IncomingMessageEvent> = _incomingMessages.asSharedFlow()

    private val _incomingCalls = MutableSharedFlow<IncomingCallEvent>(extraBufferCapacity = 10)
    val incomingCalls: SharedFlow<IncomingCallEvent> = _incomingCalls.asSharedFlow()

    private val _callSignals = MutableSharedFlow<CallSignalEvent>(extraBufferCapacity = 20)
    val callSignals: SharedFlow<CallSignalEvent> = _callSignals.asSharedFlow()

    private fun getCandidateWsUrls(userId: String): List<String> {
        val list = mutableListOf<String>()
        val encodedUserId = java.net.URLEncoder.encode(userId.trim(), "UTF-8")
        // 1. Active configured URL
        list.add(com.whatsapp.clone.config.NetworkConfig.getWebSocketUrl(userId))
        // 2. Candidate hosts
        for (host in com.whatsapp.clone.config.NetworkConfig.buildCandidateHosts(null)) {
            val wsBase = host.replace("http://", "ws://").replace("https://", "wss://")
            list.add("$wsBase/ws?user_id=$encodedUserId")
        }
        return list.distinct()
    }

    private fun cleanId(id: String?): String {
        if (id == null) return ""
        return id.trim().removePrefix("@").lowercase()
    }

    fun start(userId: String) {
        val clean = cleanId(userId)
        if (clean.isBlank()) return
        if (currentUserId == clean && isConnected) return
        currentUserId = clean
        connect()
    }

    private fun connect() {
        val userId = currentUserId ?: return
        scope.launch {
            if (isConnected) return@launch
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
                            this@WebSocketSignalingManager.webSocket = webSocket
                            connectionLatch.complete(true)
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            handleIncomingMessage(text)
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            Log.d(TAG, "WebSocket closed: $reason")
                            if (this@WebSocketSignalingManager.webSocket == webSocket) {
                                isConnected = false
                                this@WebSocketSignalingManager.webSocket = null
                            }
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            Log.w(TAG, "WebSocket connection failed to $url: ${t.message}")
                            if (this@WebSocketSignalingManager.webSocket == webSocket) {
                                isConnected = false
                                this@WebSocketSignalingManager.webSocket = null
                            }
                            connectionLatch.complete(false)
                        }
                    }
                    val ws = okHttpClient.newWebSocket(request, listener)
                    val result = kotlinx.coroutines.withTimeoutOrNull(2500) {
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
                "CALL_INITIATE" -> {
                    val callId = json.optString("call_id", java.util.UUID.randomUUID().toString())
                    val callerName = json.optString("caller_name", senderId)
                    val isVideo = json.optBoolean("is_video", false)
                    val channelName = json.optString("channel_name", senderId)

                    val event = IncomingCallEvent(
                        callId = callId,
                        callerId = senderId,
                        callerName = callerName,
                        isVideo = isVideo,
                        channelName = channelName
                    )
                    _incomingCalls.tryEmit(event)
                    _callSignals.tryEmit(CallSignalEvent(type, senderId, recipientId, channelName, isVideo, callerName))
                }
                "CALL_ACCEPTED", "CALL_REJECTED", "CALL_ENDED" -> {
                    val channelName = json.optString("channel_name")
                    val isVideo = json.optBoolean("is_video", false)
                    val callerName = json.optString("caller_name")
                    _callSignals.tryEmit(CallSignalEvent(type, senderId, recipientId, channelName, isVideo, callerName))
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

    companion object {
        private const val TAG = "WebSocketSignaling"
        val instance: WebSocketSignalingManager by lazy { WebSocketSignalingManager() }
    }
}
