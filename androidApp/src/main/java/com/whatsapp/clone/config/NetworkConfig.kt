package com.whatsapp.clone.config

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object NetworkConfig {
    private const val TAG = "NetworkConfig"
    private const val PREFS_NAME = "vibe_sync_network_prefs"
    private const val KEY_CUSTOM_HOST = "custom_server_host"
    private const val KEY_CACHED_HOST = "cached_working_host"

    // Default Fallback Hosts
    private const val DEFAULT_PC_IP = "192.168.18.75"
    private const val DEFAULT_PORT = "8000"

    @Volatile
    private var resolvedBaseUrl: String? = null

    val BASE_HTTP_URL: String
        get() = getBaseUrl()

    /**
     * Returns the active base HTTP URL (e.g. "http://192.168.18.75:8000")
     */
    fun getBaseUrl(): String {
        return resolvedBaseUrl ?: "http://$DEFAULT_PC_IP:$DEFAULT_PORT"
    }

    /**
     * Returns the active base WebSocket URL (e.g. "ws://192.168.18.75:8000/ws?user_id=alice")
     */
    fun getWebSocketUrl(userId: String): String {
        val base = getBaseUrl()
        val wsBase = base.replace("http://", "ws://").replace("https://", "wss://")
        return "$wsBase/ws?user_id=$userId"
    }

    /**
     * Saves a user-specified custom server host/IP in SharedPreferences.
     */
    fun setCustomServerUrl(context: Context, hostOrUrl: String) {
        val formatted = sanitizeUrl(hostOrUrl)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_HOST, formatted).apply()
        resolvedBaseUrl = formatted
        Log.i(TAG, "User configured custom server URL: $formatted")
    }

    fun getCustomServerUrl(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_HOST, null)
    }

    /**
     * Discovers and tests server connectivity across all candidate IPs in parallel.
     * Caches and returns the first healthy server endpoint.
     */
    suspend fun discoverServer(context: Context? = null): String = withContext(Dispatchers.IO) {
        val candidates = buildCandidateHosts(context)
        Log.d(TAG, "Probing candidates for backend server: $candidates")

        // 1. Parallel probe to find the fastest responding host
        val verifiedHost = probeCandidatesInParallel(candidates)

        if (verifiedHost != null) {
            resolvedBaseUrl = verifiedHost
            context?.let { ctx ->
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_CACHED_HOST, verifiedHost).apply()
            }
            Log.i(TAG, "Successfully resolved active backend server: $verifiedHost")
            return@withContext verifiedHost
        }

        // 2. Fallback to cached or default if probe failed (e.g. offline)
        val fallback = getCachedHost(context) ?: "http://$DEFAULT_PC_IP:$DEFAULT_PORT"
        resolvedBaseUrl = fallback
        Log.w(TAG, "No candidate responded to health check. Using fallback: $fallback")
        return@withContext fallback
    }

    /**
     * Builds list of candidate host URLs based on user settings, local network interface, emulator status, and USB.
     */
    fun buildCandidateHosts(context: Context?): List<String> {
        val list = mutableListOf<String>()

        // 1. User configured custom host (highest priority)
        if (context != null) {
            val custom = getCustomServerUrl(context)
            if (!custom.isNullOrBlank()) {
                list.add(custom)
            }
            val cached = getCachedHost(context)
            if (!cached.isNullOrBlank() && cached != custom) {
                list.add(cached)
            }
        }

        // 2. Current active PC IPv4
        list.add("http://$DEFAULT_PC_IP:$DEFAULT_PORT")
        list.add("http://192.168.18.78:$DEFAULT_PORT")

        // 3. Wi-Fi Gateway / Subnet Auto-Detection (if on Wi-Fi)
        if (context != null) {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val dhcpInfo = wifiManager?.dhcpInfo
                if (dhcpInfo != null && dhcpInfo.gateway != 0) {
                    val gatewayIp = intToIp(dhcpInfo.gateway)
                    val subnetPrefix = gatewayIp.substringBeforeLast(".")
                    list.add("http://$gatewayIp:$DEFAULT_PORT")
                    // Add common PC host offsets on the same subnet
                    list.add("http://$subnetPrefix.75:$DEFAULT_PORT")
                    list.add("http://$subnetPrefix.78:$DEFAULT_PORT")
                    list.add("http://$subnetPrefix.100:$DEFAULT_PORT")
                    list.add("http://$subnetPrefix.2:$DEFAULT_PORT")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Wi-Fi subnet detection skipped: ${e.message}")
            }
        }

        // 4. Android Emulator loopback
        list.add("http://10.0.2.2:$DEFAULT_PORT")

        // 5. USB ADB Reverse / Localhost loopback
        list.add("http://127.0.0.1:$DEFAULT_PORT")
        list.add("http://localhost:$DEFAULT_PORT")

        return list.distinct()
    }

    private suspend fun probeCandidatesInParallel(candidates: List<String>): String? = coroutineScope {
        val deferreds = candidates.map { host ->
            async(Dispatchers.IO) {
                if (checkHealth(host)) host else null
            }
        }
        val results = deferreds.awaitAll()
        results.firstOrNull { it != null }
    }

    /**
     * Quick health check against server GET / (timeout 1500ms)
     */
    suspend fun checkHealth(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(baseUrl.trimEnd('/') + "/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1500
                readTimeout = 1500
                instanceFollowRedirects = false
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (_: Exception) {
            false
        }
    }

    private fun getCachedHost(context: Context?): String? {
        if (context == null) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CACHED_HOST, null)
    }

    fun sanitizeUrl(input: String): String {
        var clean = input.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        val hostPart = clean.substringAfter("://")
        if (!hostPart.contains(":") && !clean.endsWith(".com") && !clean.endsWith(".app")) {
            clean = "$clean:$DEFAULT_PORT"
        }
        return clean.trimEnd('/')
    }

    private fun intToIp(i: Int): String {
        return "${i and 0xFF}.${(i shr 8) and 0xFF}.${(i shr 16) and 0xFF}.${(i shr 24) and 0xFF}"
    }

    fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }
}
