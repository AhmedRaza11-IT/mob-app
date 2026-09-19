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

    private fun openConnection(endpoint: String, method: String = "GET"): HttpURLConnection {
        val base = AdminNetworkConfig.getBaseUrl()
        val fullUrl = if (endpoint.startsWith("http")) endpoint else "$base$endpoint"
        val url = URL(fullUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/json")
        return conn
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

    private fun postJson(conn: HttpURLConnection, jsonBody: String) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
        writer.write(jsonBody)
        writer.flush()
        writer.close()
    }

    // 1. Admin Authentication
    suspend fun login(password: String): Boolean = withContext(Dispatchers.IO) {
        val conn = openConnection("/api/admin/login", "POST")
        val body = JSONObject().apply { put("password", password) }.toString()
        postJson(conn, body)
        val res = readResponse(conn)
        val obj = JSONObject(res)
        obj.optString("status") == "success"
    }

    // 2. Fetch Fleet Devices
    suspend fun fetchDevices(): List<DeviceItem> = withContext(Dispatchers.IO) {
        val conn = openConnection("/api/admin/devices", "GET")
        val res = readResponse(conn)
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
        val conn = openConnection("/api/admin/config", "GET")
        val res = readResponse(conn)
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
        val conn = openConnection("/api/admin/config", "POST")
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
        postJson(conn, body)
        readResponse(conn)
        true
    }

    suspend fun broadcastOta(): Boolean = withContext(Dispatchers.IO) {
        val conn = openConnection("/api/admin/ota/broadcast", "POST")
        conn.doOutput = true
        readResponse(conn)
        true
    }

    // 4. Device Management Operations
    suspend fun assignUsername(deviceId: String, username: String): Boolean = withContext(Dispatchers.IO) {
        val conn = openConnection("/api/admin/assign-username", "POST")
        val body = JSONObject().apply {
            put("target_device_id", deviceId)
            put("assigned_username", username)
        }.toString()
        postJson(conn, body)
        readResponse(conn)
        true
    }

    suspend fun updateDevice(deviceId: String, model: String, username: String, email: String): Boolean = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(deviceId, "UTF-8")
        val conn = openConnection("/api/admin/devices/$encodedId", "PUT")
        val body = JSONObject().apply {
            put("device_model", model)
            put("username", username)
            put("email", email)
        }.toString()
        postJson(conn, body)
        readResponse(conn)
        true
    }

    suspend fun toggleBlockDevice(deviceId: String, isBlocked: Boolean): Boolean = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(deviceId, "UTF-8")
        val conn = openConnection("/api/admin/devices/$encodedId/block", "POST")
        val body = JSONObject().apply {
            put("is_blocked", isBlocked)
        }.toString()
        postJson(conn, body)
        readResponse(conn)
        true
    }

    suspend fun deleteDevice(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(deviceId, "UTF-8")
        val conn = openConnection("/api/admin/devices/$encodedId", "DELETE")
        readResponse(conn)
        true
    }

    suspend fun sanitizeDevice(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(deviceId, "UTF-8")
        val conn = openConnection("/api/admin/devices/$encodedId/sanitize", "POST")
        conn.doOutput = true
        readResponse(conn)
        true
    }

    suspend fun deprovisionDevice(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(deviceId, "UTF-8")
        val conn = openConnection("/api/admin/devices/$encodedId/deprovision", "POST")
        conn.doOutput = true
        readResponse(conn)
        true
    }

    // 5. User Directory Operations
    suspend fun fetchUsers(): List<AdminUser> = withContext(Dispatchers.IO) {
        val conn = openConnection("/api/users/all", "GET")
        val res = readResponse(conn)
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
        val conn = openConnection("/api/admin/users", "POST")
        val body = JSONObject().apply {
            put("username", username)
            put("display_name", displayName)
            put("bio", bio)
            put("is_banned", isBanned)
        }.toString()
        postJson(conn, body)
        readResponse(conn)
        true
    }

    suspend fun updateUser(userId: String, displayName: String, bio: String, isBanned: Boolean): Boolean = withContext(Dispatchers.IO) {
        val conn = openConnection("/api/admin/users/$userId", "PUT")
        val body = JSONObject().apply {
            put("display_name", displayName)
            put("bio", bio)
            put("is_banned", if (isBanned) 1 else 0)
        }.toString()
        postJson(conn, body)
        readResponse(conn)
        true
    }

    suspend fun deleteUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        val conn = openConnection("/api/admin/users/$userId", "DELETE")
        readResponse(conn)
        true
    }

    // 6. Messaging
    suspend fun fetchMessages(userId: String): List<AdminChatMessage> = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(userId, "UTF-8")
        val conn = openConnection("/api/admin/messages/$encodedId", "GET")
        val res = readResponse(conn)
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
        val conn = openConnection("/api/admin/messages/send", "POST")
        val body = JSONObject().apply {
            put("recipient_id", recipientId)
            put("content", content)
            put("message_type", "TEXT")
        }.toString()
        postJson(conn, body)
        readResponse(conn)
        true
    }

    // 7. Data & Storage
    suspend fun fetchDataSummary(): DataSummary = withContext(Dispatchers.IO) {
        val conn = openConnection("/api/admin/data-summary", "GET")
        val res = readResponse(conn)
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
        val conn = openConnection("/api/admin/files", "GET")
        val res = readResponse(conn)
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
        val conn = openConnection("/api/admin/files/$encodedName", "DELETE")
        readResponse(conn)
        true
    }

    // 8. Call Signaling
    suspend fun initiateCall(targetUsername: String, isVideo: Boolean): JSONObject = withContext(Dispatchers.IO) {
        val conn = openConnection("/api/admin/calls/initiate", "POST")
        val body = JSONObject().apply {
            put("target_user", targetUsername)
            put("is_video", isVideo)
        }.toString()
        postJson(conn, body)
        val res = readResponse(conn)
        JSONObject(res)
    }

    suspend fun endCall(targetUsername: String, channelName: String): Boolean = withContext(Dispatchers.IO) {
        val conn = openConnection("/api/admin/calls/end", "POST")
        val body = JSONObject().apply {
            put("target_user", targetUsername)
            put("channel_name", channelName)
        }.toString()
        postJson(conn, body)
        readResponse(conn)
        true
    }
}
