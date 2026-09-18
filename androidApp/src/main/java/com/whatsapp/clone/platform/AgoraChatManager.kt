package com.whatsapp.clone.platform

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * AgoraChatManager coordinates call signaling for the Agora RTC engine.
 *
 * It bridges signal events (CALL_INVITE, CALL_ACCEPT, CALL_REJECT, CALL_END)
 * through the high-reliability WebSocket signaling channel, ensuring fast,
 * real-time call setup without external AAR library package conflicts.
 */
class AgoraChatManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _incomingCalls = MutableSharedFlow<IncomingCallEvent>(replay = 0, extraBufferCapacity = 1)
    val incomingCalls: SharedFlow<IncomingCallEvent> = _incomingCalls.asSharedFlow()

    private val _callSignals = MutableSharedFlow<CallSignalEvent>(replay = 0, extraBufferCapacity = 8)
    val callSignals: SharedFlow<CallSignalEvent> = _callSignals.asSharedFlow()

    private var currentUserId: String? = null
    private var isInitialized = false

    fun init(context: Context, appKey: String): Boolean {
        if (isInitialized) return true
        isInitialized = true
        Log.d(TAG, "AgoraChatManager initialized (using WebSocket signaling gateway)")
        return true
    }

    fun login(userId: String, token: String = "") {
        val clean = userId.trim().removePrefix("@").lowercase()
        if (clean.isBlank() || currentUserId == clean) return
        currentUserId = clean
        WebSocketSignalingManager.instance.start(clean)
        Log.d(TAG, "AgoraChatManager logged in user: $clean")
    }

    fun sendCallInvite(recipientId: String, callerName: String, isVideo: Boolean, channelName: String) {
        val callId = java.util.UUID.randomUUID().toString()
        WebSocketSignalingManager.instance.sendCallInitiate(
            recipientId = recipientId,
            callerName  = callerName,
            isVideo     = isVideo,
            channelName = channelName
        )
        Log.d(TAG, "Sent CALL_INVITE to $recipientId channel=$channelName video=$isVideo")
    }

    fun sendCallAccept(callerId: String, channelName: String) {
        WebSocketSignalingManager.instance.sendCallAccepted(callerId, channelName)
        Log.d(TAG, "Sent CALL_ACCEPT to $callerId channel=$channelName")
    }

    fun sendCallReject(callerId: String) {
        WebSocketSignalingManager.instance.sendCallRejected(callerId)
        Log.d(TAG, "Sent CALL_REJECT to $callerId")
    }

    fun sendCallEnd(partnerId: String) {
        WebSocketSignalingManager.instance.sendCallEnded(partnerId)
        Log.d(TAG, "Sent CALL_END to $partnerId")
    }

    companion object {
        private const val TAG = "AgoraChatManager"
        const val SIGNAL_CALL_INVITE = "CALL_INVITE"
        const val SIGNAL_CALL_ACCEPT = "CALL_ACCEPT"
        const val SIGNAL_CALL_REJECT = "CALL_REJECT"
        const val SIGNAL_CALL_END    = "CALL_END"

        val instance: AgoraChatManager by lazy { AgoraChatManager() }
    }
}
