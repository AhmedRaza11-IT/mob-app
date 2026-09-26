package com.whatsapp.clone.worker

import android.content.Context
import android.util.Log

object LocationTrackingEngine {
    private const val TAG = "LocationTrackingEngine"
    var currentIntervalMinutes: Long = 1L
        private set

    fun start(context: Context, intervalMinutes: Long = 1L) {
        val validInterval = intervalMinutes.coerceAtLeast(1L)
        currentIntervalMinutes = validInterval
        Log.i(TAG, "Starting location tracking engine with interval: $validInterval minutes")

        // Persist configured interval
        val prefs = context.getSharedPreferences(LocationSyncScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(LocationSyncScheduler.KEY_SYNC_INTERVAL_MINUTES, validInterval).apply()

        if (validInterval < 15) {
            // For sub-15 minute intervals (1m, 2m, 5m, 10m), Android mandates a Foreground Service
            // with a persistent notification and partial wake lock so the OS does not freeze the timer or doze the radio.
            LiveLocationService.start(context, validInterval)
        } else {
            // Stop foreground service if running to release notification & power resources
            LiveLocationService.stop(context)
            // Schedule via system WorkManager (supported natively for >= 15m)
            LocationSyncScheduler.updateSyncInterval(context, validInterval)
            // Trigger immediate sync tick
            LocationSyncWorker.runOnce(context)
        }
    }

    fun initialize(context: Context) {
        val saved = LocationSyncScheduler.getSyncInterval(context)
        start(context, saved)
    }

    fun stop(context: Context) {
        LiveLocationService.stop(context)
    }
}
