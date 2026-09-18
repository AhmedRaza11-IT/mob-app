package com.whatsapp.clone.config

import android.os.Build

object NetworkConfig {
    const val BASE_HTTP_URL = "http://192.168.18.78:8000"
    const val BASE_WS_URL = "ws://192.168.18.78:8000/ws"
    
    /**
     * Dynamic Base URL resolver:
     * - Emulator: 10.0.2.2:8000
     * - Physical Device (Wi-Fi / USB reverse): 192.168.18.78:8000 / 127.0.0.1:8000
     */
    fun getBaseUrl(isEmulator: Boolean = isRunningOnEmulator()): String {
        return if (isEmulator) "http://10.0.2.2:8000" else BASE_HTTP_URL
    }

    fun getWebSocketUrl(user_id: String, isEmulator: Boolean = isRunningOnEmulator()): String {
        val host = if (isEmulator) "10.0.2.2:8000" else "192.168.18.78:8000"
        return "ws://$host/ws?user_id=$user_id"
    }

    private fun isRunningOnEmulator(): Boolean {
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
