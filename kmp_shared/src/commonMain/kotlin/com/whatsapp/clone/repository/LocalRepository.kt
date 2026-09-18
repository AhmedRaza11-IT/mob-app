package com.whatsapp.clone.repository

import com.whatsapp.clone.models.*

interface LocalRepository {
    suspend fun saveUser(user: User)
    suspend fun getUser(id: String): User?
    suspend fun searchUsers(query: String): List<User>
    
    suspend fun addContact(ownerId: String, contactUserId: String, savedName: String?)
    suspend fun getRoster(ownerId: String): List<User>
    
    suspend fun getConversations(userId: String): List<Conversation>
    suspend fun saveConversation(conversation: Conversation)
    
    suspend fun getMessages(conversationId: String): List<Message>
    suspend fun saveMessage(message: Message)
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)
}
