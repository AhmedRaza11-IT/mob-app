package com.whatsapp.clone.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

object LocationSyncScheduler {
    private const val TAG = "LocationSyncScheduler"
    const val PREFS_NAME = "vibesync_location_prefs"
    const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
    const val DEFAULT_INTERVAL_MINUTES: Long = 60L

    val INTERVAL_OPTIONS = listOf(15L, 30L, 60L, 180L, 360L)

    fun getSyncInterval(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_SYNC_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
    }

    fun updateSyncInterval(context: Context, intervalMinutes: Long) {
        val clampedInterval = intervalMinutes.coerceAtLeast(15) // Android limits periodic workers to >= 15 min
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_SYNC_INTERVAL_MINUTES, clampedInterval).apply()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<LocationSyncWorker>(
            clampedInterval, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            LocationSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.i(TAG, "Location sync interval updated to $clampedInterval minutes")
    }

    fun initialize(context: Context) {
        val currentInterval = getSyncInterval(context)
        updateSyncInterval(context, currentInterval)
    }
}
