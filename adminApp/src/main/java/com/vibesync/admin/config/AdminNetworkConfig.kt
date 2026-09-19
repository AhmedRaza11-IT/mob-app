package com.vibesync.admin.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Context.adminDataStore by preferencesDataStore(name = "admin_settings")

object AdminNetworkConfig {
    private val KEY_SERVER_URL = stringPreferencesKey("admin_server_url")
    private val KEY_AUTH_TOKEN = stringPreferencesKey("admin_auth_token")

    // Default candidates for auto-fallback
    const val DEFAULT_ADB_URL = "http://127.0.0.1:8000"
    const val DEFAULT_WIFI_URL = "http://192.168.18.78:8000"
    const val DEFAULT_EMULATOR_URL = "http://10.0.2.2:8000"

    private var cachedBaseUrl: String = DEFAULT_ADB_URL

    fun getBaseUrl(): String = cachedBaseUrl

    fun setBaseUrl(url: String) {
        cachedBaseUrl = url.trimEnd('/')
    }

    fun getServerUrlFlow(context: Context): Flow<String> {
        return context.adminDataStore.data.map { prefs ->
            prefs[KEY_SERVER_URL] ?: DEFAULT_ADB_URL
        }
    }

    suspend fun saveServerUrl(context: Context, url: String) {
        val sanitized = url.trimEnd('/')
        context.adminDataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = sanitized
        }
        setBaseUrl(sanitized)
    }

    fun getAuthTokenFlow(context: Context): Flow<String?> {
        return context.adminDataStore.data.map { prefs ->
            prefs[KEY_AUTH_TOKEN]
        }
    }

    suspend fun saveAuthToken(context: Context, token: String) {
        context.adminDataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = token
        }
    }

    suspend fun clearAuthToken(context: Context) {
        context.adminDataStore.edit { prefs ->
            prefs.remove(KEY_AUTH_TOKEN)
        }
    }

    suspend fun autoDetectWorkingHost(context: Context): String = withContext(Dispatchers.IO) {
        val saved = context.adminDataStore.data.first()[KEY_SERVER_URL]
        val candidates = mutableListOf<String>()
        if (!saved.isNullOrBlank()) candidates.add(saved)
        if (!candidates.contains(DEFAULT_ADB_URL)) candidates.add(DEFAULT_ADB_URL)
        if (!candidates.contains(DEFAULT_WIFI_URL)) candidates.add(DEFAULT_WIFI_URL)
        if (!candidates.contains(DEFAULT_EMULATOR_URL)) candidates.add(DEFAULT_EMULATOR_URL)

        for (host in candidates) {
            try {
                val conn = URL("$host/").openConnection() as HttpURLConnection
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
                conn.requestMethod = "GET"
                if (conn.responseCode in 200..399) {
                    conn.disconnect()
                    setBaseUrl(host)
                    return@withContext host
                }
                conn.disconnect()
            } catch (_: Exception) {
                // Try next
            }
        }
        val fallback = saved ?: DEFAULT_ADB_URL
        setBaseUrl(fallback)
        return@withContext fallback
    }
}
