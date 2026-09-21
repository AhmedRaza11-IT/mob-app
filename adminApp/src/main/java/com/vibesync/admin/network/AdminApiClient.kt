package com.vibesync.admin.network

import com.vibesync.admin.config.AdminNetworkConfig
import com.vibesync.admin.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AdminApiClient {

    private fun postJson(conn: HttpURLConnection, jsonBody: String) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
        writer.write(jsonBody)
        writer.flush()
        writer.close()
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..399) conn.inputStream else conn.errorStream
            ?: conn.inputStream
        val reader = BufferedReader(InputStreamReader(stream))
        val response = reader.readText()
        reader.close()
        if (conn.responseCode !in 200..399) {
            val detail = try {
                JSONObject(response).optString("detail", "HTTP ${conn.responseCode}")
            } catch (_: Exception) {
                "HTTP ${conn.responseCode}: $response"
            }
            throw RuntimeException(detail)
        }
        return response
    }

    /**
     * Executes HTTP requests with dynamic network failover.
     * Automatically attempts USB (127.0.0.1) and Wi-Fi LAN (192.168.18.78),
     * instantly adapting when the cable is connected or disconnected.
     */
    private fun executeRequest(endpoint: String, method: String = "GET", body: String? = null): String {
        val candidates = AdminNetworkConfig.buildCandidateHosts()
        var lastException: Exception? = null

        for (base in candidates) {
            var conn: HttpURLConnection? = null
            try {
                val fullUrl = if (endpoint.startsWith("http")) endpoint else "$base$endpoint"
                val url = URL(fullUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 2500
                    readTimeout = 4000
                    setRequestProperty("Accept", "application/json")
                }
                if (body != null) {
                    postJson(conn, body)
                }
                val res = readResponse(conn)
                // Fast-latch to the active network interface
                AdminNetworkConfig.setBaseUrl(base)
                return res
            } catch (e: Exception) {
                lastException = e
            } finally {
                conn?.disconnect()
            }
        }
        throw lastException ?: RuntimeException("Network unreachable across all candidate hosts")
    }

    // 1. Admin Authentication
    suspend fun login(password: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("password", password) }.toString()
        val res = executeRequest("/api/admin/login", "POST", body)
        JSONObject(res).optString("status") == "success"
    }

    // 2. Fetch Fleet Devices
    suspend fun fetchDevices(): List<DeviceItem> = withContext(Dispatchers.IO) {
        val res = executeRequest("/api/admin/devices", "GET")
        val arr = JSONArray(res)
        val list = mutableListOf<DeviceItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                DeviceItem(
                    deviceId = o.optString("device_id"),
                    deviceModel = o.optString("device_model", "Unknown Device"),
                    username = o.optString("username", "Current User"),
                    email = o.optString("email").takeIf { it.isNotEmpty() && it != "null" },
                    lastSyncTimestamp = o.optLong("last_sync_timestamp", System.currentTimeMillis()),
                    isBlocked = o.optBoolean("is_blocked", false),
                    totalFiles = o.optInt("total_files", 0)
                )
            )
        }
        list
    }

    // 3. Remote Config / Policies
    suspend fun fetchRemoteConfig(): RemoteConfig = withContext(Dispatchers.IO) {
        val res = executeRequest("/api/admin/config", "GET")
        val o = JSONObject(res)
        RemoteConfig(
            allowScreenshots = o.optBoolean("allow_screenshots", true),
            voiceCallingEnabled = o.optBoolean("voice_calling_enabled", true),
            maintenanceMode = o.optBoolean("maintenance_mode", false),
            minRequiredVersion = o.optInt("min_required_version", 1),
            latestVersionCode = o.optInt("latest_version_code", 1),
            latestVersionName = o.optString("latest_version_name", "2.4.0"),
            apkUrl = o.optString("apk_url", "/static/vibesync-release.apk"),
            releaseNotes = o.optString("release_notes", "")
        )
    }

    suspend fun updateRemoteConfig(config: RemoteConfig): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("allow_screenshots", config.allowScreenshots)
            put("voice_calling_enabled", config.voiceCallingEnabled)
            put("maintenance_mode", config.maintenanceMode)
            put("min_required_version", config.minRequiredVersion)
            put("latest_version_code", config.latestVersionCode)
            put("latest_version_name", config.latestVersionName)
            put("apk_url", config.apkUrl)
            put("release_notes", config.releaseNotes)
        }.toString()
        executeRequest("/api/admin/config", "POST", body)
        true
    }

    suspend fun broadcastOta(): Boolean = withContext(Dispatchers.IO) {
        executeRequest("/api/admin/ota/broadcast", "POST")
        true
    }

    // 4. Device Management Operations
    suspend fun assignUsername(deviceId: String, username: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("target_device_id", deviceId)
            put("assigned_username", username)
        }.toString()
        executeRequest("/api/admin/assign-username", "POST", body)
        true
    }

    suspend fun updateDevice(deviceId: String, model: String, username: String, email: String): Boolean = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(deviceId, "UTF-8")
        val body = JSONObject().apply {
            put("device_model", model)
            put("username", username)
            put("email", email)
        }.toString()
        executeRequest("/api/admin/devices/$encodedId", "PUT", body)
        true
    }

    suspend fun toggleBlockDevice(deviceId: String, isBlocked: Boolean): Boolean = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(deviceId, "UTF-8")
        val body = JSONObject().apply {
            put("is_blocked", isBlocked)
        }.toString()
        executeRequest("/api/admin/devices/$encodedId/block", "POST", body)
        true
    }

    suspend fun deleteDevice(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(deviceId, "UTF-8")
        executeRequest("/api/admin/devices/$encodedId", "DELETE")
        true
    }

    suspend fun sanitizeDevice(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(deviceId, "UTF-8")
        executeRequest("/api/admin/devices/$encodedId/sanitize", "POST")
        true
    }

    suspend fun deprovisionDevice(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(deviceId, "UTF-8")
        executeRequest("/api/admin/devices/$encodedId/deprovision", "POST")
        true
    }

    // 5. User Directory Operations
    suspend fun fetchUsers(): List<AdminUser> = withContext(Dispatchers.IO) {
        val res = executeRequest("/api/users/all", "GET")
        val arr = JSONArray(res)
        val list = mutableListOf<AdminUser>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                AdminUser(
                    id = o.optString("id"),
                    username = o.optString("username"),
                    displayName = o.optString("display_name", o.optString("username")),
                    bio = o.optString("bio").takeIf { it.isNotEmpty() && it != "null" },
                    isBanned = o.optInt("is_banned", 0) == 1 || o.optBoolean("is_banned", false),
                    createdAt = o.optLong("created_at", System.currentTimeMillis()),
                    deviceId = o.optString("device_id").takeIf { it.isNotEmpty() && it != "null" },
                    deviceModel = o.optString("device_model").takeIf { it.isNotEmpty() && it != "null" }
                )
            )
        }
        list
    }

    suspend fun createUser(username: String, displayName: String, bio: String, isBanned: Boolean): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("username", username)
            put("display_name", displayName)
            put("bio", bio)
            put("is_banned", isBanned)
        }.toString()
        executeRequest("/api/admin/users", "POST", body)
        true
    }

    suspend fun updateUser(userId: String, displayName: String, bio: String, isBanned: Boolean): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("display_name", displayName)
            put("bio", bio)
            put("is_banned", if (isBanned) 1 else 0)
        }.toString()
        executeRequest("/api/admin/users/$userId", "PUT", body)
        true
    }

    suspend fun deleteUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        executeRequest("/api/admin/users/$userId", "DELETE")
        true
    }

    // 6. Messaging
    suspend fun fetchMessages(userId: String): List<AdminChatMessage> = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(userId, "UTF-8")
        val res = executeRequest("/api/admin/messages/$encodedId", "GET")
        val arr = JSONArray(res)
        val list = mutableListOf<AdminChatMessage>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                AdminChatMessage(
                    id = o.optString("id"),
                    conversationId = o.optString("conversation_id"),
                    senderId = o.optString("sender_id"),
                    recipientId = o.optString("recipient_id"),
                    messageType = o.optString("message_type", "TEXT"),
                    content = o.optString("content"),
                    status = o.optString("status", "SENT"),
                    createdAt = o.optLong("created_at", System.currentTimeMillis())
                )
            )
        }
        list
    }

    suspend fun sendMessage(recipientId: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("recipient_id", recipientId)
            put("content", content)
            put("message_type", "TEXT")
        }.toString()
        executeRequest("/api/admin/messages/send", "POST", body)
        true
    }

    suspend fun fetchConversations(): List<AdminConversationItem> = withContext(Dispatchers.IO) {
        val res = executeRequest("/api/conversations/admin", "GET")
        val arr = JSONArray(res)
        val list = mutableListOf<AdminConversationItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                AdminConversationItem(
                    id = o.optString("id"),
                    partnerId = o.optString("partner_id"),
                    partnerUsername = o.optString("partner_username"),
                    partnerDisplayName = o.optString("partner_display_name", o.optString("partner_username")),
                    lastMessagePreview = o.optString("last_message_preview", ""),
                    lastMessageTime = o.optLong("last_message_time", 0L),
                    unreadCount = o.optInt("unread_count", 0),
                    isOnline = o.optBoolean("is_online", false),
                    partnerAvatarUrl = o.optString("partner_avatar_url").takeIf { it.isNotBlank() && it != "null" }
                )
            )
        }
        list
    }

    // 7. Data & Storage
    suspend fun fetchDataSummary(): DataSummary = withContext(Dispatchers.IO) {
        val res = executeRequest("/api/admin/data-summary", "GET")
        val o = JSONObject(res)
        val catsArr = o.optJSONArray("categories") ?: JSONArray()
        val catList = mutableListOf<CategoryStorage>()
        var totalBytes = 0L
        for (i in 0 until catsArr.length()) {
            val c = catsArr.getJSONObject(i)
            val bytes = c.optLong("total_bytes", 0)
            totalBytes += bytes
            catList.add(
                CategoryStorage(
                    category = c.optString("category", "General"),
                    totalItems = c.optInt("total_items", 0),
                    totalBytes = bytes
                )
            )
        }
        DataSummary(
            totalStorageMb = totalBytes / (1024.0 * 1024.0),
            totalItems = o.optInt("total_files", 0),
            deviceCount = o.optInt("total_devices", 0),
            categoryBreakdown = catList
        )
    }

    suspend fun fetchFiles(): List<StoredFile> = withContext(Dispatchers.IO) {
        val res = executeRequest("/api/admin/files", "GET")
        val arr = JSONArray(res)
        val list = mutableListOf<StoredFile>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                StoredFile(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    category = o.optString("category", "Document"),
                    sizeBytes = o.optLong("size_bytes", 0),
                    sizeFormatted = o.optString("size_formatted", "0 KB"),
                    username = o.optString("username"),
                    displayName = o.optString("display_name", o.optString("username")),
                    deviceId = o.optString("device_id"),
                    createdAt = o.optLong("created_at", System.currentTimeMillis()),
                    downloadUrl = o.optString("download_url")
                )
            )
        }
        list
    }

    suspend fun deleteFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val encodedName = URLEncoder.encode(fileName, "UTF-8")
        executeRequest("/api/admin/files/$encodedName", "DELETE")
        true
    }

    // 8. Call Signaling
    suspend fun initiateCall(targetUsername: String, isVideo: Boolean): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("target_user", targetUsername)
            put("is_video", isVideo)
        }.toString()
        val res = executeRequest("/api/admin/calls/initiate", "POST", body)
        JSONObject(res)
    }

    suspend fun endCall(targetUsername: String, channelName: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("target_user", targetUsername)
            put("channel_name", channelName)
        }.toString()
        executeRequest("/api/admin/calls/end", "POST", body)
        true
    }
}
