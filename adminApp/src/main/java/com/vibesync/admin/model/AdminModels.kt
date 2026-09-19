package com.vibesync.admin.model

data class DeviceItem(
    val deviceId: String,
    val deviceModel: String,
    val username: String,
    val email: String?,
    val lastSyncTimestamp: Long,
    val isBlocked: Boolean = false,
    val totalFiles: Int = 0
)

data class RemoteConfig(
    val allowScreenshots: Boolean = true,
    val voiceCallingEnabled: Boolean = true,
    val maintenanceMode: Boolean = false,
    val minRequiredVersion: Int = 1,
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "2.4.0",
    val apkUrl: String = "/static/vibesync-release.apk",
    val releaseNotes: String = ""
)

data class AdminUser(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String?,
    val isBanned: Boolean = false,
    val createdAt: Long,
    val deviceId: String? = null,
    val deviceModel: String? = null
)

data class StoredFile(
    val id: String,
    val name: String,
    val category: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val username: String,
    val displayName: String,
    val deviceId: String,
    val createdAt: Long,
    val downloadUrl: String
)

data class DataSummary(
    val totalStorageMb: Double,
    val totalItems: Int,
    val deviceCount: Int,
    val categoryBreakdown: List<CategoryStorage> = emptyList()
)

data class CategoryStorage(
    val category: String,
    val totalItems: Int,
    val totalBytes: Long
)

data class AdminChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val messageType: String,
    val content: String,
    val status: String,
    val createdAt: Long
)
