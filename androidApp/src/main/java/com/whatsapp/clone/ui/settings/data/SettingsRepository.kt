package com.whatsapp.clone.ui.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vibesync_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_APP_THEME = stringPreferencesKey("app_theme")
        private val KEY_FONT_SIZE = stringPreferencesKey("font_size")
        private val KEY_READ_RECEIPTS = booleanPreferencesKey("read_receipts")
        private val KEY_ENTER_IS_SEND = booleanPreferencesKey("enter_is_send")
        private val KEY_MEDIA_VISIBILITY = booleanPreferencesKey("media_visibility")
        private val KEY_SILENCE_UNKNOWN_CALLERS = booleanPreferencesKey("silence_unknown_callers")
        private val KEY_APP_LOCK = booleanPreferencesKey("app_lock")
        private val KEY_KEEP_ARCHIVED = booleanPreferencesKey("keep_archived")
        private val KEY_DISAPPEARING_MESSAGES = stringPreferencesKey("disappearing_messages")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")

        // Storage — mobile data
        private val KEY_DL_MOBILE_PHOTOS = booleanPreferencesKey("dl_mobile_photos")
        private val KEY_DL_MOBILE_AUDIO = booleanPreferencesKey("dl_mobile_audio")
        private val KEY_DL_MOBILE_VIDEO = booleanPreferencesKey("dl_mobile_video")
        private val KEY_DL_MOBILE_DOCS = booleanPreferencesKey("dl_mobile_docs")

        // Storage — wi-fi
        private val KEY_DL_WIFI_PHOTOS = booleanPreferencesKey("dl_wifi_photos")
        private val KEY_DL_WIFI_AUDIO = booleanPreferencesKey("dl_wifi_audio")
        private val KEY_DL_WIFI_VIDEO = booleanPreferencesKey("dl_wifi_video")
        private val KEY_DL_WIFI_DOCS = booleanPreferencesKey("dl_wifi_docs")

        // Notifications
        private val KEY_NOTIF_TONES = booleanPreferencesKey("notif_tones")
        private val KEY_NOTIF_VIBRATE = booleanPreferencesKey("notif_vibrate")
        private val KEY_NOTIF_HIGH_PRIORITY = booleanPreferencesKey("notif_high_priority")
        private val KEY_NOTIF_REACTION = booleanPreferencesKey("notif_reaction")
        private val KEY_LESS_DATA_CALLS = booleanPreferencesKey("less_data_calls")

        private val KEY_PROTECT_IP = booleanPreferencesKey("protect_ip")
        private val KEY_DISABLE_LINK_PREVIEW = booleanPreferencesKey("disable_link_preview")
    }

    private val flow: Flow<Preferences> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    // ─── Readers ─────────────────────────────────────────────────────────────

    val appTheme: Flow<AppTheme> = flow.map {
        AppTheme.entries.firstOrNull { e -> e.name == it[KEY_APP_THEME] } ?: AppTheme.SYSTEM
    }
    val fontSize: Flow<FontSize> = flow.map {
        FontSize.entries.firstOrNull { e -> e.name == it[KEY_FONT_SIZE] } ?: FontSize.MEDIUM
    }
    val readReceipts: Flow<Boolean>              = flow.map { it[KEY_READ_RECEIPTS] ?: true }
    val enterIsSend: Flow<Boolean>               = flow.map { it[KEY_ENTER_IS_SEND] ?: false }
    val mediaVisibility: Flow<Boolean>           = flow.map { it[KEY_MEDIA_VISIBILITY] ?: true }
    val silenceUnknownCallers: Flow<Boolean>     = flow.map { it[KEY_SILENCE_UNKNOWN_CALLERS] ?: false }
    val appLockEnabled: Flow<Boolean>            = flow.map { it[KEY_APP_LOCK] ?: false }
    val keepArchived: Flow<Boolean>              = flow.map { it[KEY_KEEP_ARCHIVED] ?: false }
    val disappearingMessages: Flow<String>       = flow.map { it[KEY_DISAPPEARING_MESSAGES] ?: "Off" }
    val appLanguage: Flow<String>                = flow.map { it[KEY_APP_LANGUAGE] ?: "English" }

    val dlMobilePhotos: Flow<Boolean>            = flow.map { it[KEY_DL_MOBILE_PHOTOS] ?: true }
    val dlMobileAudio: Flow<Boolean>             = flow.map { it[KEY_DL_MOBILE_AUDIO] ?: false }
    val dlMobileVideo: Flow<Boolean>             = flow.map { it[KEY_DL_MOBILE_VIDEO] ?: false }
    val dlMobileDocs: Flow<Boolean>              = flow.map { it[KEY_DL_MOBILE_DOCS] ?: false }
    val dlWifiPhotos: Flow<Boolean>              = flow.map { it[KEY_DL_WIFI_PHOTOS] ?: true }
    val dlWifiAudio: Flow<Boolean>               = flow.map { it[KEY_DL_WIFI_AUDIO] ?: true }
    val dlWifiVideo: Flow<Boolean>               = flow.map { it[KEY_DL_WIFI_VIDEO] ?: false }
    val dlWifiDocs: Flow<Boolean>                = flow.map { it[KEY_DL_WIFI_DOCS] ?: true }

    val notifTones: Flow<Boolean>                = flow.map { it[KEY_NOTIF_TONES] ?: true }
    val notifVibrate: Flow<Boolean>              = flow.map { it[KEY_NOTIF_VIBRATE] ?: true }
    val notifHighPriority: Flow<Boolean>         = flow.map { it[KEY_NOTIF_HIGH_PRIORITY] ?: false }
    val notifReaction: Flow<Boolean>             = flow.map { it[KEY_NOTIF_REACTION] ?: true }
    val lessDataCalls: Flow<Boolean>             = flow.map { it[KEY_LESS_DATA_CALLS] ?: false }
    val protectIp: Flow<Boolean>                 = flow.map { it[KEY_PROTECT_IP] ?: true }
    val disableLinkPreview: Flow<Boolean>        = flow.map { it[KEY_DISABLE_LINK_PREVIEW] ?: false }

    // ─── Writers ─────────────────────────────────────────────────────────────

    private suspend fun <T> edit(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setAppTheme(theme: AppTheme)                    = edit(KEY_APP_THEME, theme.name)
    suspend fun setFontSize(size: FontSize)                     = edit(KEY_FONT_SIZE, size.name)
    suspend fun setReadReceipts(enabled: Boolean)               = edit(KEY_READ_RECEIPTS, enabled)
    suspend fun setEnterIsSend(enabled: Boolean)                = edit(KEY_ENTER_IS_SEND, enabled)
    suspend fun setMediaVisibility(enabled: Boolean)            = edit(KEY_MEDIA_VISIBILITY, enabled)
    suspend fun setSilenceUnknownCallers(enabled: Boolean)      = edit(KEY_SILENCE_UNKNOWN_CALLERS, enabled)
    suspend fun setAppLock(enabled: Boolean)                    = edit(KEY_APP_LOCK, enabled)
    suspend fun setKeepArchived(enabled: Boolean)               = edit(KEY_KEEP_ARCHIVED, enabled)
    suspend fun setDisappearingMessages(value: String)          = edit(KEY_DISAPPEARING_MESSAGES, value)
    suspend fun setAppLanguage(language: String)                = edit(KEY_APP_LANGUAGE, language)

    suspend fun setDlMobilePhotos(v: Boolean)                   = edit(KEY_DL_MOBILE_PHOTOS, v)
    suspend fun setDlMobileAudio(v: Boolean)                    = edit(KEY_DL_MOBILE_AUDIO, v)
    suspend fun setDlMobileVideo(v: Boolean)                    = edit(KEY_DL_MOBILE_VIDEO, v)
    suspend fun setDlMobileDocs(v: Boolean)                     = edit(KEY_DL_MOBILE_DOCS, v)
    suspend fun setDlWifiPhotos(v: Boolean)                     = edit(KEY_DL_WIFI_PHOTOS, v)
    suspend fun setDlWifiAudio(v: Boolean)                      = edit(KEY_DL_WIFI_AUDIO, v)
    suspend fun setDlWifiVideo(v: Boolean)                      = edit(KEY_DL_WIFI_VIDEO, v)
    suspend fun setDlWifiDocs(v: Boolean)                       = edit(KEY_DL_WIFI_DOCS, v)

    suspend fun setNotifTones(v: Boolean)                       = edit(KEY_NOTIF_TONES, v)
    suspend fun setNotifVibrate(v: Boolean)                     = edit(KEY_NOTIF_VIBRATE, v)
    suspend fun setNotifHighPriority(v: Boolean)                = edit(KEY_NOTIF_HIGH_PRIORITY, v)
    suspend fun setNotifReaction(v: Boolean)                    = edit(KEY_NOTIF_REACTION, v)
    suspend fun setLessDataCalls(v: Boolean)                    = edit(KEY_LESS_DATA_CALLS, v)
    suspend fun setProtectIp(v: Boolean)                        = edit(KEY_PROTECT_IP, v)
    suspend fun setDisableLinkPreview(v: Boolean)               = edit(KEY_DISABLE_LINK_PREVIEW, v)
}
