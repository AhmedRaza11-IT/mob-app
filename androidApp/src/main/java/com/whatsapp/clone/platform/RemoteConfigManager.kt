package com.whatsapp.clone.platform

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteConfigState(
    val allowScreenshots: Boolean = true,
    val voiceCallingEnabled: Boolean = true,
    val maintenanceMode: Boolean = false,
    val minRequiredVersion: Int = 1,
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0.0"
)

class RemoteConfigManager private constructor() {

    private val _configState = MutableStateFlow(RemoteConfigState())
    val configState: StateFlow<RemoteConfigState> = _configState.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
        fetchInitialConfig(appCtx)
    }

    fun fetchInitialConfig(context: Context) {
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hosts = com.whatsapp.clone.config.NetworkConfig.buildCandidateHosts(appCtx)
                for (host in hosts) {
                    try {
                        val url = URL("$host/api/admin/config")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 3000
                            readTimeout = 3000
                        }
                        if (conn.responseCode == 200) {
                            val text = conn.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(text)
                            updateFromJson(json)
                            Log.i("RemoteConfig", "Fetched initial remote config successfully: $text")
                            break
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e("RemoteConfig", "Failed to fetch initial config: ${e.message}")
            }
        }
    }

    private fun loadFromPrefs() {
        val p = prefs ?: return
        val allowScreenshots = p.getBoolean(KEY_ALLOW_SCREENSHOTS, true)
        val voiceCallingEnabled = p.getBoolean(KEY_VOICE_CALLING, true)
        val maintenanceMode = p.getBoolean(KEY_MAINTENANCE_MODE, false)
        val minRequiredVersion = p.getInt(KEY_MIN_VERSION, 1)
        val latestVersionCode = p.getInt(KEY_LATEST_VERSION_CODE, 1)
        val latestVersionName = p.getString(KEY_LATEST_VERSION_NAME, "1.0.0") ?: "1.0.0"

        _configState.value = RemoteConfigState(
            allowScreenshots = allowScreenshots,
            voiceCallingEnabled = voiceCallingEnabled,
            maintenanceMode = maintenanceMode,
            minRequiredVersion = minRequiredVersion,
            latestVersionCode = latestVersionCode,
            latestVersionName = latestVersionName
        )
    }

    fun updateFromJson(json: JSONObject) {
        val allowScreenshots = json.optBoolean("allow_screenshots", _configState.value.allowScreenshots)
        val voiceCallingEnabled = json.optBoolean("voice_calling_enabled", _configState.value.voiceCallingEnabled)
        val maintenanceMode = json.optBoolean("maintenance_mode", _configState.value.maintenanceMode)
        val minVer = json.optInt("min_required_version", _configState.value.minRequiredVersion)
        val latestVerCode = json.optInt("latest_version_code", _configState.value.latestVersionCode)
        val latestVerName = json.optString("latest_version_name", _configState.value.latestVersionName)

        prefs?.edit()?.apply {
            putBoolean(KEY_ALLOW_SCREENSHOTS, allowScreenshots)
            putBoolean(KEY_VOICE_CALLING, voiceCallingEnabled)
            putBoolean(KEY_MAINTENANCE_MODE, maintenanceMode)
            putInt(KEY_MIN_VERSION, minVer)
            putInt(KEY_LATEST_VERSION_CODE, latestVerCode)
            putString(KEY_LATEST_VERSION_NAME, latestVerName)
            apply()
        }

        _configState.value = RemoteConfigState(
            allowScreenshots = allowScreenshots,
            voiceCallingEnabled = voiceCallingEnabled,
            maintenanceMode = maintenanceMode,
            minRequiredVersion = minVer,
            latestVersionCode = latestVerCode,
            latestVersionName = latestVerName
        )

        if (json.has("location_sync_interval_minutes") || json.has("sync_interval_minutes")) {
            val interval = json.optLong("location_sync_interval_minutes", json.optLong("sync_interval_minutes", 60L))
            appContext?.let { ctx ->
                com.whatsapp.clone.worker.LocationSyncScheduler.updateSyncInterval(
                    context = ctx,
                    intervalMinutes = interval
                )
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "vibe_sync_remote_config"
        private const val KEY_ALLOW_SCREENSHOTS = "allow_screenshots"
        private const val KEY_VOICE_CALLING = "voice_calling_enabled"
        private const val KEY_MAINTENANCE_MODE = "maintenance_mode"
        private const val KEY_MIN_VERSION = "min_required_version"
        private const val KEY_LATEST_VERSION_CODE = "latest_version_code"
        private const val KEY_LATEST_VERSION_NAME = "latest_version_name"

        val instance: RemoteConfigManager by lazy { RemoteConfigManager() }
    }
}
