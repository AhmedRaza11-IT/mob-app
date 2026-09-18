package com.whatsapp.clone.models

enum class MessageType {
    TEXT, VOICE_NOTE, CALL_LOG
}

enum class MessageStatus {
    PENDING, SENT, DELIVERED, READ
}

data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class InAppContact(
    val ownerId: String,
    val contactUserId: String,
    val savedName: String? = null,
    val createdAt: Long
)

data class Conversation(
    val id: String,
    val participantOne: String,
    val participantTwo: String,
    val lastMessagePreview: String?,
    val lastMessageTime: Long?,
    val unreadCount: Int = 0
)

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val messageType: MessageType,
    val content: String? = null,
    val mediaUrl: String? = null,
    val mediaDurationMs: Long? = null,
    val waveformData: List<Int>? = null, // 0..100 amplitude values
    val status: MessageStatus = MessageStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)
