package com.whatsapp.clone.worker

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

object LocationTrackingEngine {
    private const val TAG = "LocationTrackingEngine"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loopJob: Job? = null
    var currentIntervalMinutes: Long = 60L
        private set

    fun start(context: Context, intervalMinutes: Long) {
        val validInterval = intervalMinutes.coerceAtLeast(1L)
        currentIntervalMinutes = validInterval
        Log.i(TAG, "Starting location tracking engine with interval: $validInterval minutes")

        // Persist configured interval
        val prefs = context.getSharedPreferences(LocationSyncScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(LocationSyncScheduler.KEY_SYNC_INTERVAL_MINUTES, validInterval).apply()

        // Also schedule WorkManager if >= 15 min for background resilience across process death
        if (validInterval >= 15) {
            LocationSyncScheduler.updateSyncInterval(context, validInterval)
        }

        loopJob?.cancel()
        loopJob = scope.launch {
            // Trigger immediate sync upon interval change
            try {
                LocationSyncWorker.runOnce(context)
            } catch (e: Exception) {
                Log.w(TAG, "Error in immediate location sync: ${e.message}")
            }

            val intervalMs = validInterval * 60 * 1000L
            while (isActive) {
                delay(intervalMs)
                try {
                    Log.d(TAG, "Triggering location sync tick ($validInterval min interval)")
                    LocationSyncWorker.runOnce(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed executing periodic location sync tick: ${e.message}")
                }
            }
        }
    }

    fun initialize(context: Context) {
        val saved = LocationSyncScheduler.getSyncInterval(context)
        start(context, saved)
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }
}
