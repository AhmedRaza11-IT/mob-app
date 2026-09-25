package com.whatsapp.clone.worker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.whatsapp.clone.config.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LocationSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext

        // 1. Permission check
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            Log.w(TAG, "Location permissions not granted, skipping LocationSyncWorker")
            return@withContext Result.failure()
        }

        try {
            // 2. Fetch location: getCurrentLocation with 10s timeout, fallback to lastLocation
            val location = fetchCurrentLocation()

            if (location == null) {
                Log.w(TAG, "Unable to obtain location from FusedLocationProviderClient")
                return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            // 3. Resolve metadata
            val deviceId = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val userId = prefs.getString(KEY_USER_ID, null) ?: deviceId

            val timestamp = System.currentTimeMillis()

            // 4. Construct payload
            val payload = JSONObject().apply {
                put("deviceId", deviceId)
                put("userId", userId)
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("accuracy", location.accuracy.toDouble())
                put("timestamp", timestamp)
            }

            // 5. Transmit to backend
            val success = transmitLocation(payload)
            if (success) {
                Log.i(TAG, "Location successfully synced: lat=${location.latitude}, lng=${location.longitude}, acc=${location.accuracy}m")
                Result.success()
            } else {
                Log.w(TAG, "Location sync POST failed, scheduling retry")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in LocationSyncWorker: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchCurrentLocation(): Location? {
        val cts = CancellationTokenSource()
        return try {
            withTimeoutOrNull(10_000L) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cts.token
                ).await()
            } ?: fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentLocation failed, falling back to lastLocation: ${e.message}")
            try {
                fusedLocationClient.lastLocation.await()
            } catch (ex: Exception) {
                null
            }
        } finally {
            cts.cancel()
        }
    }

    private fun transmitLocation(jsonBody: JSONObject): Boolean {
        val baseCandidateUrls = NetworkConfig.buildCandidateHosts(applicationContext)
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toString().toRequestBody(jsonMediaType)

        for (base in baseCandidateUrls) {
            try {
                val url = "${base.trimEnd('/')}/api/devices/location"
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("Accept", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        NetworkConfig.setWorkingBaseUrl(base, applicationContext)
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed posting location to $base: ${e.message}")
            }
        }
        return false
    }

    companion object {
        private const val TAG = "LocationSyncWorker"
        const val WORK_NAME = "vibesync_location_sync"
        const val PREFS_NAME = "vibe_sync_user_prefs"
        const val KEY_USER_ID = "assigned_user_id"

        /**
         * Enqueues periodic location sync with user/server configured interval (default 60 minutes).
         */
        fun schedule(context: Context) {
            LocationSyncScheduler.initialize(context)
        }

        /**
         * Runs a one-time immediate location sync (e.g., when permission is granted or app opens).
         */
        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<LocationSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_immediate",
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
            Log.d(TAG, "LocationSyncWorker immediate sync triggered")
        }

        /**
         * Persists active user id for location synchronization payload.
         */
        fun setUserId(context: Context, userId: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_USER_ID, userId.trim()).apply()
        }
    }
}
