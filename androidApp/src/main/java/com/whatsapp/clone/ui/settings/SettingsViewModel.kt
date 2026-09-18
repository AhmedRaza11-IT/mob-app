package com.whatsapp.clone.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whatsapp.clone.ui.settings.data.AppTheme
import com.whatsapp.clone.ui.settings.data.FontSize
import com.whatsapp.clone.ui.settings.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val fontSize: FontSize = FontSize.MEDIUM,
    val readReceipts: Boolean = true,
    val enterIsSend: Boolean = false,
    val mediaVisibility: Boolean = true,
    val silenceUnknownCallers: Boolean = false,
    val appLockEnabled: Boolean = false,
    val keepArchived: Boolean = false,
    val disappearingMessages: String = "Off",
    val appLanguage: String = "English",
    // Storage – mobile
    val dlMobilePhotos: Boolean = true,
    val dlMobileAudio: Boolean = false,
    val dlMobileVideo: Boolean = false,
    val dlMobileDocs: Boolean = false,
    // Storage – wi-fi
    val dlWifiPhotos: Boolean = true,
    val dlWifiAudio: Boolean = true,
    val dlWifiVideo: Boolean = false,
    val dlWifiDocs: Boolean = true,
    // Notifications
    val notifTones: Boolean = true,
    val notifVibrate: Boolean = true,
    val notifHighPriority: Boolean = false,
    val notifReaction: Boolean = true,
    val lessDataCalls: Boolean = false,
    val protectIp: Boolean = true,
    val disableLinkPreview: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)

    private val allFlows: Array<Flow<Any?>> = arrayOf(
        repo.appTheme, repo.fontSize, repo.readReceipts, repo.enterIsSend,
        repo.mediaVisibility, repo.silenceUnknownCallers, repo.appLockEnabled,
        repo.keepArchived, repo.disappearingMessages, repo.appLanguage,
        repo.dlMobilePhotos, repo.dlMobileAudio, repo.dlMobileVideo, repo.dlMobileDocs,
        repo.dlWifiPhotos, repo.dlWifiAudio, repo.dlWifiVideo, repo.dlWifiDocs,
        repo.notifTones, repo.notifVibrate, repo.notifHighPriority, repo.notifReaction,
        repo.lessDataCalls, repo.protectIp, repo.disableLinkPreview
    )

    val settingsState: StateFlow<SettingsUiState> = combine(*allFlows) { args: Array<Any?> ->
        SettingsUiState(
            appTheme = args[0] as AppTheme,
            fontSize = args[1] as FontSize,
            readReceipts = args[2] as Boolean,
            enterIsSend = args[3] as Boolean,
            mediaVisibility = args[4] as Boolean,
            silenceUnknownCallers = args[5] as Boolean,
            appLockEnabled = args[6] as Boolean,
            keepArchived = args[7] as Boolean,
            disappearingMessages = args[8] as String,
            appLanguage = args[9] as String,
            dlMobilePhotos = args[10] as Boolean,
            dlMobileAudio = args[11] as Boolean,
            dlMobileVideo = args[12] as Boolean,
            dlMobileDocs = args[13] as Boolean,
            dlWifiPhotos = args[14] as Boolean,
            dlWifiAudio = args[15] as Boolean,
            dlWifiVideo = args[16] as Boolean,
            dlWifiDocs = args[17] as Boolean,
            notifTones = args[18] as Boolean,
            notifVibrate = args[19] as Boolean,
            notifHighPriority = args[20] as Boolean,
            notifReaction = args[21] as Boolean,
            lessDataCalls = args[22] as Boolean,
            protectIp = args[23] as Boolean,
            disableLinkPreview = args[24] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    val uiState: StateFlow<SettingsUiState> get() = settingsState

    // ─── Update functions ─────────────────────────────────────────────────────

    fun setAppTheme(theme: AppTheme)                    = viewModelScope.launch { repo.setAppTheme(theme) }
    fun setFontSize(size: FontSize)                     = viewModelScope.launch { repo.setFontSize(size) }
    fun setReadReceipts(v: Boolean)                     = viewModelScope.launch { repo.setReadReceipts(v) }
    fun setEnterIsSend(v: Boolean)                      = viewModelScope.launch { repo.setEnterIsSend(v) }
    fun setMediaVisibility(v: Boolean)                  = viewModelScope.launch { repo.setMediaVisibility(v) }
    fun setSilenceUnknownCallers(v: Boolean)            = viewModelScope.launch { repo.setSilenceUnknownCallers(v) }
    fun setAppLock(v: Boolean)                          = viewModelScope.launch { repo.setAppLock(v) }
    fun setKeepArchived(v: Boolean)                     = viewModelScope.launch { repo.setKeepArchived(v) }
    fun setDisappearingMessages(v: String)              = viewModelScope.launch { repo.setDisappearingMessages(v) }
    fun setAppLanguage(lang: String)                    = viewModelScope.launch { repo.setAppLanguage(lang) }

    fun setDlMobilePhotos(v: Boolean)                   = viewModelScope.launch { repo.setDlMobilePhotos(v) }
    fun setDlMobileAudio(v: Boolean)                    = viewModelScope.launch { repo.setDlMobileAudio(v) }
    fun setDlMobileVideo(v: Boolean)                    = viewModelScope.launch { repo.setDlMobileVideo(v) }
    fun setDlMobileDocs(v: Boolean)                     = viewModelScope.launch { repo.setDlMobileDocs(v) }
    fun setDlWifiPhotos(v: Boolean)                     = viewModelScope.launch { repo.setDlWifiPhotos(v) }
    fun setDlWifiAudio(v: Boolean)                      = viewModelScope.launch { repo.setDlWifiAudio(v) }
    fun setDlWifiVideo(v: Boolean)                      = viewModelScope.launch { repo.setDlWifiVideo(v) }
    fun setDlWifiDocs(v: Boolean)                       = viewModelScope.launch { repo.setDlWifiDocs(v) }

    fun setNotifTones(v: Boolean)                       = viewModelScope.launch { repo.setNotifTones(v) }
    fun setNotifVibrate(v: Boolean)                     = viewModelScope.launch { repo.setNotifVibrate(v) }
    fun setNotifHighPriority(v: Boolean)                = viewModelScope.launch { repo.setNotifHighPriority(v) }
    fun setNotifReaction(v: Boolean)                    = viewModelScope.launch { repo.setNotifReaction(v) }
    fun setLessDataCalls(v: Boolean)                    = viewModelScope.launch { repo.setLessDataCalls(v) }
    fun setProtectIp(v: Boolean)                        = viewModelScope.launch { repo.setProtectIp(v) }
    fun setDisableLinkPreview(v: Boolean)               = viewModelScope.launch { repo.setDisableLinkPreview(v) }
}
