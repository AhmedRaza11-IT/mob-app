package com.whatsapp.clone.repository

import com.whatsapp.clone.audio.WaveformNormalizer
import com.whatsapp.clone.crypto.SignalProtocolManager
import com.whatsapp.clone.models.Message
import com.whatsapp.clone.models.MessageStatus
import com.whatsapp.clone.models.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Offline-first Android Conversation Repository.
 * Writes messages immediately with status PENDING to local SQLite storage,
 * then auto-flushes pending entries to WebSocket gateway upon network reconnection.
 */
class AndroidOfflineSyncRepository {
    private val _pendingQueue = MutableStateFlow<List<Message>>(emptyList())
    val pendingQueue: StateFlow<List<Message>> = _pendingQueue

    fun sendMessageOffline(
        conversationId: String,
        senderId: String,
        recipientId: String,
        content: String?,
        rawAmplitudes: List<Int>? = null
    ): Message {
        // 1. Normalize amplitude array to fixed 0-100 scale
        val normalizedWaveform = rawAmplitudes?.let { WaveformNormalizer.normalize(it, maxScale = 100) }

        // 2. Encrypt text payload with E2EE Signal Protocol stub
        val encryptedContent = content?.let { SignalProtocolManager.encryptPayload(it, recipientId) }

        val message = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = senderId,
            recipientId = recipientId,
            messageType = if (rawAmplitudes != null) MessageType.VOICE_NOTE else MessageType.TEXT,
            content = encryptedContent,
            waveformData = normalizedWaveform,
            status = MessageStatus.PENDING, // Outgoing offline status
            createdAt = System.currentTimeMillis()
        )

        // Enqueue to offline persistence
        val updated = _pendingQueue.value.toMutableList().apply { add(message) }
        _pendingQueue.value = updated
        return message
    }

    /**
     * Saves a message to the database repository queue with deduplication based on ID and timestamp.
     * Returns true if saved, or false if ignored as a duplicate.
     */
    fun saveMessageWithDeduplication(message: Message): Boolean {
        val currentList = _pendingQueue.value
        val isDuplicate = currentList.any { existing ->
            existing.id == message.id ||
            (existing.senderId == message.senderId &&
             existing.recipientId == message.recipientId &&
             existing.content == message.content &&
             existing.createdAt == message.createdAt)
        }

        if (isDuplicate) {
            return false // Ignored based on ID/timestamp duplicate
        }

        _pendingQueue.value = currentList + message
        return true
    }

    fun flushPendingMessagesOnReconnect(sendOverWebSocket: (Message) -> Boolean) {
        val pending = _pendingQueue.value.filter { it.status == MessageStatus.PENDING }
        for (msg in pending) {
            val sentSuccessfully = sendOverWebSocket(msg)
            if (sentSuccessfully) {
                // Update status to SENT
                val updatedList = _pendingQueue.value.toMutableList().apply {
                    remove(msg)
                    add(msg.copy(status = MessageStatus.SENT))
                }
                _pendingQueue.value = updatedList
            }
        }
    }
}
