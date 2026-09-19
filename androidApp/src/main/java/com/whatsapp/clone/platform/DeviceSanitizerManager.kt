package com.whatsapp.clone.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.File
import kotlin.system.exitProcess

/**
 * Enterprise Mobile Device Management (MDM) Sanitization Engine.
 *
 * Provides two primary administrative lifecycle operations:
 * 1. executeDataSanitization: Silently clears databases, encrypted preferences, cached files,
 *    and revokes active communication sessions.
 * 2. executeDeviceDeprovision: Executes silent data sanitization, launches the platform
 *    package uninstaller prompt, and terminates the client process.
 */
object DeviceSanitizerManager {

    private const val TAG = "DeviceSanitizer"

    /**
     * Phase 1: Silent Instant Data Sanitization.
     */
    fun executeDataSanitization(context: Context) {
        val appCtx = context.applicationContext
        Log.w(TAG, "Executing Emergency Data Sanitization on device...")

        try {
            // 1. Terminate & leave active Agora RTC & Chat sessions
            try {
                AgoraCallManager.instance.leaveCall()
            } catch (e: Exception) {
                Log.e(TAG, "Error leaving Agora call: ${e.message}")
            }

            try {
                AgoraChatManager.instance.logout()
            } catch (e: Exception) {
                Log.e(TAG, "Error logging out Agora chat: ${e.message}")
            }

            try {
                WebSocketSignalingManager.instance.disconnectPermanently()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting WebSocket: ${e.message}")
            }

            // 2. Delete all SQLite / Room database files
            try {
                val dbList = appCtx.databaseList() ?: emptyArray()
                for (dbName in dbList) {
                    val deleted = appCtx.deleteDatabase(dbName)
                    Log.d(TAG, "Deleted database $dbName: $deleted")
                }
                appCtx.deleteDatabase("vibesync.db")
                appCtx.deleteDatabase("vibesync_offline.db")
                appCtx.deleteDatabase("app.db")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting databases: ${e.message}")
            }

            // 3. Purge all SharedPreferences files
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
                    Log.d(TAG, "Cleared preferences: $pref")
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing pref $pref: ${e.message}")
                }
            }

            // 4. Recursively purge internal filesDir, cacheDir, externalCacheDir
            deleteRecursively(appCtx.filesDir)
            deleteRecursively(appCtx.cacheDir)
            appCtx.externalCacheDir?.let { deleteRecursively(it) }

            Log.i(TAG, "Data Sanitization completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during data sanitization: ${e.message}", e)
        }
    }

    /**
     * Phase 2: Remote Managed Client De-provisioning (Uninstallation Sequence).
     */
    fun executeDeviceDeprovision(context: Context) {
        val appCtx = context.applicationContext
        Log.w(TAG, "Initiating Managed Client De-provisioning Sequence...")

        // Step 1: Ensure full data wipe before launching uninstaller
        executeDataSanitization(appCtx)

        // Step 2: Trigger platform package uninstaller intent
        try {
            val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${appCtx.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            appCtx.startActivity(uninstallIntent)
            Log.i(TAG, "Dispatched system package uninstallation prompt.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package uninstaller: ${e.message}")
        }

        // Step 3: Gracefully terminate process after handoff
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                Process.killProcess(Process.myPid())
                exitProcess(0)
            } catch (_: Exception) {}
        }, 1200)
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
