package com.whatsapp.clone.db

import com.whatsapp.clone.models.Message

interface MessageDao {
    suspend fun getPendingMessages(): List<Message>
    suspend fun updateMessageStatus(messageId: String, status: String)
    suspend fun saveMessage(message: Message)
}
