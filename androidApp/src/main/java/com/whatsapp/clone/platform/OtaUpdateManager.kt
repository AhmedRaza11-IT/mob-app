package com.whatsapp.clone.platform

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.whatsapp.clone.BuildConfig
import com.whatsapp.clone.config.NetworkConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class OtaVersionInfo(
    val versionCode: Int,
    val versionName: String,
    val minRequiredVersion: Int,
    val apkUrl: String,
    val releaseNotes: String
)

/**
 * Over-The-Air (OTA) Application Distribution Engine.
 * Handles background version checks against enterprise management endpoints,
 * downloads APK packages, and launches the Android Package Installer.
 */
class OtaUpdateManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _updateState = MutableStateFlow<OtaVersionInfo?>(null)
    val updateState: StateFlow<OtaVersionInfo?> = _updateState.asStateFlow()

    private var isDownloading = false
    private var downloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null

    fun checkForUpdates(context: Context, force: Boolean = false) {
        val appCtx = context.applicationContext
        scope.launch {
            try {
                val hosts = NetworkConfig.buildCandidateHosts(appCtx)
                var versionInfo: OtaVersionInfo? = null

                for (host in hosts) {
                    try {
                        val url = URL("$host/api/v1/app/version")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 3000
                            readTimeout = 3000
                        }
                        if (conn.responseCode == 200) {
                            val body = conn.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(body)
                            versionInfo = OtaVersionInfo(
                                versionCode = json.optInt("versionCode", 1),
                                versionName = json.optString("versionName", "1.0.0"),
                                minRequiredVersion = json.optInt("minRequiredVersion", 1),
                                apkUrl = json.optString("apkUrl", "$host/static/vibesync-release.apk"),
                                releaseNotes = json.optString("releaseNotes", "")
                            )
                            break
                        }
                    } catch (_: Exception) {}
                }

                if (versionInfo != null) {
                    val currentVersion = BuildConfig.VERSION_CODE
                    Log.i(TAG, "OTA check: currentVersion=$currentVersion, remoteVersion=${versionInfo.versionCode}")

                    if (versionInfo.versionCode > currentVersion || force) {
                        _updateState.value = versionInfo
                        startApkDownload(appCtx, versionInfo)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking OTA updates: ${e.message}")
            }
        }
    }

    private fun startApkDownload(context: Context, info: OtaVersionInfo) {
        if (isDownloading) {
            Log.w(TAG, "Download already in progress. Skipping.")
            return
        }
        isDownloading = true

        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                Log.e(TAG, "DownloadManager service unavailable.")
                isDownloading = false
                return
            }

            val targetFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "vibesync-update.apk")
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle("VibeSync Update v${info.versionName}")
                .setDescription("Downloading latest release package...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(targetFile))

            downloadId = downloadManager.enqueue(request)
            Log.i(TAG, "Enqueued download ID: $downloadId for APK: ${info.apkUrl}")

            // Register completion listener
            downloadReceiver?.let {
                try { context.unregisterReceiver(it) } catch (_: Exception) {}
            }

            downloadReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id == downloadId) {
                        Log.i(TAG, "Download completed for APK. Launching package installer...")
                        isDownloading = false
                        try {
                            ctx.unregisterReceiver(this)
                        } catch (_: Exception) {}
                        launchPackageInstaller(ctx, targetFile)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    downloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    downloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate DownloadManager: ${e.message}", e)
            isDownloading = false
        }
    }

    fun launchPackageInstaller(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Log.e(TAG, "Cannot launch installer: APK file does not exist at ${apkFile.absolutePath}")
                return
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
            Log.i(TAG, "Successfully launched package installer for URI: $apkUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Android package installer: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "OtaUpdateManager"
        val instance: OtaUpdateManager by lazy { OtaUpdateManager() }
    }
}
