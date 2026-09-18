package com.whatsapp.clone.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ─── Permission result model ───────────────────────────────────────────────────

sealed class PermissionResult {
    object Granted : PermissionResult()
    data class Denied(val permissions: List<String>) : PermissionResult()
    data class PermanentlyDenied(val permissions: List<String>) : PermissionResult()
}

// ─── Scope helpers ─────────────────────────────────────────────────────────────

object PermissionSets {

    /** Correct storage permission set for the running API level */
    val storage: List<String>
        get() = when {
            Build.VERSION.SDK_INT >= 34 -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                // READ_MEDIA_VISUAL_USER_SELECTED is a complement, not a replacement
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            else -> listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

    val voiceNote: List<String>
        get() = listOf(Manifest.permission.RECORD_AUDIO)

    /** Full call permission scope — gated by API level */
    val call: List<String>
        get() = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
}

// ─── PermissionManager ─────────────────────────────────────────────────────────

class PermissionManager(private val activity: FragmentActivity) {

    // Reactive state — observed by UI to show rationale banners / settings CTA
    private val _storageResult  = MutableStateFlow<PermissionResult?>(null)
    private val _voiceResult    = MutableStateFlow<PermissionResult?>(null)
    private val _callResult     = MutableStateFlow<PermissionResult?>(null)

    val storageResult:  StateFlow<PermissionResult?> = _storageResult.asStateFlow()
    val voiceResult:    StateFlow<PermissionResult?> = _voiceResult.asStateFlow()
    val callResult:     StateFlow<PermissionResult?> = _callResult.asStateFlow()

    // Launchers registered during Activity creation (before onStart)
    private val storageLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            _storageResult.value = classify(results, PermissionSets.storage)
        }

    private val voiceLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            _voiceResult.value = classify(results, PermissionSets.voiceNote)
        }

    private val callLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            _callResult.value = classify(results, PermissionSets.call)
        }

    // ── Public request APIs ────────────────────────────────────────────────────

    fun requestStorage()  = storageLauncher.launch(PermissionSets.storage.toTypedArray())
    fun requestVoiceNote() = voiceLauncher.launch(PermissionSets.voiceNote.toTypedArray())
    fun requestCall()     = callLauncher.launch(PermissionSets.call.toTypedArray())

    /** Direct-check: true if ALL storage permissions for current API are granted */
    fun isStorageGranted(): Boolean = PermissionSets.storage.all { perm ->
        activity.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun isVoiceGranted(): Boolean =
        activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Opens the app's system settings page so user can manually re-grant */
    fun openAppSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // ── Classification logic ───────────────────────────────────────────────────

    private fun classify(
        results: Map<String, Boolean>,
        requestedPermissions: List<String>
    ): PermissionResult {
        val denied   = results.filterValues { !it }.keys.toList()
        if (denied.isEmpty()) return PermissionResult.Granted

        // A permission is "permanently denied" when:
        // - the system returned false AND
        // - shouldShowRequestPermissionRationale also returns false
        val permanent = denied.filter { perm ->
            !activity.shouldShowRequestPermissionRationale(perm)
        }

        return if (permanent.isNotEmpty()) {
            PermissionResult.PermanentlyDenied(permanent)
        } else {
            PermissionResult.Denied(denied)
        }
    }
}
