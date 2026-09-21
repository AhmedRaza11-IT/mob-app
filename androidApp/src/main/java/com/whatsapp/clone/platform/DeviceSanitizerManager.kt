package com.whatsapp.clone.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import kotlin.system.exitProcess

/**
 * Enterprise Mobile Device Management (MDM) Sanitization Engine.
 *
 * 1. executeDataSanitization: Sanitizes all chat messages and voice/media cache on the device
 *    and triggers real-time in-memory message wiping. Preserves user session and credentials.
 * 2. executeDeviceDeprovision: Full client decommissioning (silent data sanitization, preference
 *    wipes, platform package uninstaller prompt, and client termination).
 */
object DeviceSanitizerManager {

    private const val TAG = "DeviceSanitizer"

    // Real-time broadcast flow to instantly wipe messages from in-memory Compose ViewModels
    private val _sanitizeMessagesEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
    val sanitizeMessagesEvent: SharedFlow<Unit> = _sanitizeMessagesEvent.asSharedFlow()

    /**
     * Phase 1: Chat Messages Sanitization.
     * Deletes all local message databases and media cache, and emits sanitizeMessagesEvent
     * to clear all open chat screens in real-time. Preserves user account and active connection.
     */
    fun executeDataSanitization(context: Context) {
        val appCtx = context.applicationContext
        Log.w(TAG, "Executing Chat Message Sanitization on device...")

        try {
            // 1. Delete local chat message databases
            try {
                appCtx.deleteDatabase("vibesync.db")
                appCtx.deleteDatabase("vibesync_offline.db")
                appCtx.deleteDatabase("app.db")
                Log.d(TAG, "Deleted local chat databases.")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting chat databases: ${e.message}")
            }

            // 2. Purge cached voice notes, media recordings, and temporary chat attachments
            try {
                val audioDir = File(appCtx.filesDir, "audio")
                if (audioDir.exists()) deleteRecursively(audioDir)

                val mediaDir = File(appCtx.filesDir, "media")
                if (mediaDir.exists()) deleteRecursively(mediaDir)

                deleteRecursively(appCtx.cacheDir)
                appCtx.externalCacheDir?.let { deleteRecursively(it) }
                Log.d(TAG, "Purged chat media cache.")
            } catch (e: Exception) {
                Log.e(TAG, "Error purging media cache: ${e.message}")
            }

            // 3. Emit real-time event to instantly clear messages from active UI screens
            _sanitizeMessagesEvent.tryEmit(Unit)

            Log.i(TAG, "Chat message sanitization completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during chat message sanitization: ${e.message}", e)
        }
    }

    @Volatile
    private var isDeprovisioning = false

    /**
     * Phase 2: Remote Managed Client De-provisioning (Complete Uninstallation Sequence).
     */
    fun executeDeviceDeprovision(context: Context) {
        if (isDeprovisioning) return
        isDeprovisioning = true

        val appCtx = context.applicationContext
        Log.w(TAG, "Initiating Managed Client De-provisioning Sequence...")

        // Step 1: Wipe all databases, preferences, and sessions
        try {
            AgoraCallManager.instance.leaveCall()
        } catch (_: Exception) {}

        try {
            AgoraChatManager.instance.logout()
        } catch (_: Exception) {}

        try {
            WebSocketSignalingManager.instance.disconnectPermanently()
        } catch (_: Exception) {}

        try {
            val dbList = appCtx.databaseList() ?: emptyArray()
            for (dbName in dbList) {
                appCtx.deleteDatabase(dbName)
            }
        } catch (_: Exception) {}

        val prefsToClear = listOf(
            "vibe_sync_prefs",
            "vibe_sync_network_prefs",
            "vibe_sync_remote_config",
            "vibe_sync_settings_prefs",
            "androidx.work.util.preferences"
        )
        for (pref in prefsToClear) {
            try {
                appCtx.getSharedPreferences(pref, Context.MODE_PRIVATE).edit().clear().commit()
            } catch (_: Exception) {}
        }

        deleteRecursively(appCtx.filesDir)
        deleteRecursively(appCtx.cacheDir)
        appCtx.externalCacheDir?.let { deleteRecursively(it) }

        // Step 2: Terminate client process immediately without showing any prompt
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                Process.killProcess(Process.myPid())
                exitProcess(0)
            } catch (_: Exception) {}
        }, 300)
    }

    private fun deleteRecursively(fileOrDir: File?): Boolean {
        if (fileOrDir == null || !fileOrDir.exists()) return true
        var success = true
        if (fileOrDir.isDirectory) {
            val children = fileOrDir.listFiles()
            if (children != null) {
                for (child in children) {
                    success = deleteRecursively(child) && success
                }
            }
        }
        return fileOrDir.delete() && success
    }
}
