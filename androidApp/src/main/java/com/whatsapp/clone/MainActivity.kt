package com.whatsapp.clone

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.widget.Toast
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import com.whatsapp.clone.config.NetworkConfig
import org.json.JSONObject
import com.whatsapp.clone.platform.AgoraCallManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatsapp.clone.ui.settings.SettingsViewModel
import com.whatsapp.clone.ui.settings.data.AppTheme
import com.whatsapp.clone.ui.settings.navigation.SettingsNavHost

const val AGORA_APP_ID = "e63a3e4f21124b659d8eeaef92f367c2"

object UserApiClient {
    private val BASE_URL get() = NetworkConfig.getBaseUrl()

    suspend fun fetchAllUsers(context: Context? = null): List<UserUI> = withContext(Dispatchers.IO) {
        val hosts = getCandidateHosts(context)
        for (host in hosts) {
            try {
                val url = URL("$host/api/users/all")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                if (conn.responseCode == 200) {
                    NetworkConfig.setWorkingBaseUrl(host, context)
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(text)
                    val list = mutableListOf<UserUI>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            UserUI(
                                id = obj.optString("id", System.currentTimeMillis().toString()),
                                username = obj.optString("username"),
                                displayName = obj.optString("display_name"),
                                bio = obj.optString("bio", "VibeSync User")
                            )
                        )
                    }
                    if (list.isNotEmpty()) return@withContext list
                }
            } catch (_: Exception) {}
        }
        emptyList()
    }

    suspend fun fetchFriends(usernameOrId: String, context: Context? = null): List<UserUI> = withContext(Dispatchers.IO) {
        val cleanIdent = usernameOrId.trim()
        if (cleanIdent.isBlank() || cleanIdent.equals("Current User", ignoreCase = true)) {
            return@withContext emptyList()
        }
        val encoded = try {
            java.net.URLEncoder.encode(cleanIdent, "UTF-8")
        } catch (_: Exception) {
            cleanIdent
        }
        val hosts = getCandidateHosts(context)
        for (host in hosts) {
            try {
                val url = URL("$host/api/users/$encoded/friends")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                if (conn.responseCode == 200) {
                    NetworkConfig.setWorkingBaseUrl(host, context)
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(text)
                    val list = mutableListOf<UserUI>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            UserUI(
                                id = obj.optString("id", System.currentTimeMillis().toString()),
                                username = obj.optString("username"),
                                displayName = obj.optString("display_name"),
                                bio = obj.optString("bio", "VibeSync Friend")
                            )
                        )
                    }
                    return@withContext list
                }
            } catch (_: Exception) {}
        }
        emptyList()
    }

    suspend fun searchUsers(query: String, context: Context? = null): List<UserUI> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext fetchAllUsers(context)
        val hosts = getCandidateHosts(context)
        for (host in hosts) {
            try {
                val url = URL("$host/api/users/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                if (conn.responseCode == 200) {
                    NetworkConfig.setWorkingBaseUrl(host, context)
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(text)
                    val list = mutableListOf<UserUI>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            UserUI(
                                id = obj.optString("id", System.currentTimeMillis().toString()),
                                username = obj.optString("username"),
                                displayName = obj.optString("display_name"),
                                bio = obj.optString("bio", "VibeSync User")
                            )
                        )
                    }
                    if (list.isNotEmpty()) return@withContext list
                }
            } catch (_: Exception) {}
        }
        emptyList()
    }

    suspend fun createUser(username: String, displayName: String, bio: String): UserUI? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/auth/register")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 4000
                readTimeout = 4000
            }
            val body = JSONObject().apply {
                put("username", username)
                put("display_name", displayName)
                put("bio", bio)
            }.toString()

            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(response)
                UserUI(
                    id = obj.optString("id", System.currentTimeMillis().toString()),
                    username = obj.optString("username"),
                    displayName = obj.optString("display_name"),
                    bio = obj.optString("bio", bio)
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun getCandidateHosts(context: Context? = null): List<String> {
        val currentBase = NetworkConfig.getBaseUrl()
        val hosts = NetworkConfig.buildCandidateHosts(context).toMutableList()
        if (!hosts.contains(currentBase)) {
            hosts.add(0, currentBase)
        }
        return hosts.distinct()
    }

    suspend fun syncDeviceWithBackend(context: Context? = null, deviceId: String, deviceModel: String, savedUsername: String? = null): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("device_id", deviceId)
            put("device_model", deviceModel)
            if (!savedUsername.isNullOrBlank() && savedUsername != "Current User") {
                put("username", savedUsername)
            }
        }.toString()

        val hosts = getCandidateHosts(context)
        for (host in hosts) {
            try {
                val url = URL("$host/api/devices/sync")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 2500
                    readTimeout = 2500
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(text)
                    NetworkConfig.setWorkingBaseUrl(host, context)
                    return@withContext obj.optString("username", savedUsername ?: "Current User")
                }
            } catch (_: Exception) {}
        }
        savedUsername ?: "Current User"
    }

    suspend fun signupDeviceWithBackend(context: Context? = null, deviceId: String, deviceModel: String, email: String, pass: String, username: String): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("device_id", deviceId)
            put("device_model", deviceModel)
            put("email", email)
            put("password", pass)
            put("username", username)
        }.toString()

        val endpoint = "/api/devices/signup"
        var lastErr = "Server unreachable"
        val hosts = getCandidateHosts(context)

        for (host in hosts) {
            try {
                val url = URL("$host$endpoint")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code in 200..299) {
                    NetworkConfig.setWorkingBaseUrl(host, context)
                    return@withContext null
                } else {
                    val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    lastErr = "HTTP $code from $host: $errText"
                }
            } catch (e: Exception) {
                lastErr = "Failed to connect ($host): ${e.localizedMessage ?: "Connection error"}"
            }
        }
        lastErr
    }

    suspend fun deleteUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/users/$userId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 3000
                readTimeout = 3000
            }
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateUser(userId: String, displayName: String, bio: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/users/$userId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                doOutput = true
                connectTimeout = 4000
                readTimeout = 4000
            }
            val body = "display_name=${java.net.URLEncoder.encode(displayName, "UTF-8")}&bio=${java.net.URLEncoder.encode(bio, "UTF-8")}"
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchConversations(userId: String = "me", context: Context? = null): List<JSONObject> = withContext(Dispatchers.IO) {
        val hosts = getCandidateHosts(context)
        for (host in hosts) {
            try {
                val encoded = java.net.URLEncoder.encode(userId.trim(), "UTF-8")
                val url = URL("$host/api/conversations/$encoded")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                if (conn.responseCode == 200) {
                    NetworkConfig.setWorkingBaseUrl(host, context)
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(text)
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until array.length()) {
                        list.add(array.getJSONObject(i))
                    }
                    return@withContext list
                }
            } catch (_: Exception) {}
        }
        emptyList()
    }

    suspend fun fetchMessages(conversationId: String, currentUser: String? = null, context: Context? = null): List<MessageUI> = withContext(Dispatchers.IO) {
        val hosts = getCandidateHosts(context)
        android.util.Log.d("VibeSync", "fetchMessages started for conv=$conversationId, user=$currentUser, candidate hosts=$hosts")
        for (host in hosts) {
            try {
                val encoded = java.net.URLEncoder.encode(conversationId.trim(), "UTF-8")
                val queryParams = if (!currentUser.isNullOrBlank()) "?current_user=${java.net.URLEncoder.encode(currentUser.trim(), "UTF-8")}" else ""
                val fullUrl = "$host/api/messages/$encoded$queryParams"
                android.util.Log.d("VibeSync", "fetchMessages trying: $fullUrl")
                val url = URL(fullUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                val code = conn.responseCode
                android.util.Log.d("VibeSync", "fetchMessages code: $code from $host")
                if (code == 200) {
                    NetworkConfig.setWorkingBaseUrl(host, context)
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    android.util.Log.d("VibeSync", "fetchMessages response: $text")
                    val array = JSONArray(text)
                    val list = mutableListOf<MessageUI>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val wfArr = obj.optJSONArray("waveform_data")
                        val wfList = mutableListOf<Int>()
                        if (wfArr != null) {
                            for (j in 0 until wfArr.length()) {
                                wfList.add(wfArr.getInt(j))
                            }
                        }
                        list.add(
                            MessageUI(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                senderId = obj.optString("sender_id"),
                                text = obj.optString("content"),
                                isVoiceNote = obj.optString("message_type") == "VOICE_NOTE",
                                waveform = wfList,
                                timestamp = obj.optLong("created_at", System.currentTimeMillis())
                            )
                        )
                    }
                    return@withContext list
                }
            } catch (e: Exception) {
                android.util.Log.e("VibeSync", "fetchMessages error on host $host: ${e.message}")
            }
        }
        emptyList()
    }
}

class MainActivity : ComponentActivity() {

    companion object {
        val activeCallIntent = MutableStateFlow<android.content.Intent?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleCallIntent(intent)
        requestRequiredStoragePermissions()
        setContent {
            val settingsVm: SettingsViewModel = viewModel()
            val settingsState by settingsVm.settingsState.collectAsState()
            WhatsAppTheme(appTheme = settingsState.appTheme) {
                VibeSyncApp(settingsVm = settingsVm)
            }
        }
    }

    private fun requestRequiredStoragePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permissions.add(android.Manifest.permission.RECORD_AUDIO)
        permissions.add(android.Manifest.permission.CAMERA)

        val ungranted = permissions.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (ungranted.isNotEmpty()) {
            requestPermissions(ungranted.toTypedArray(), 1001)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallIntent(intent)
    }

    private fun handleCallIntent(intent: android.content.Intent?) {
        if (intent?.hasExtra("ACCEPTED_CALL_CHANNEL") == true) {
            val ch = intent.getStringExtra("ACCEPTED_CALL_CHANNEL")
            android.util.Log.d("VibeSync", "MainActivity received accepted call intent for channel: $ch")
            activeCallIntent.value = intent
        }
    }
}

@Composable
fun VibeSyncApp(settingsVm: SettingsViewModel = viewModel()) {
    WhatsAppMainScreen(settingsVm = settingsVm)
}

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val scale = remember { androidx.compose.animation.core.Animatable(0.5f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(key1 = true) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
        delay(1200)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WaGreenDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "VibeSync Logo",
                    tint = WaGreenPrimary,
                    modifier = Modifier.size(54.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "VibeSync",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Fast • Secure • E2E Encrypted",
                color = Color(0xFFB2DFDB),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "POWERED BY",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = WaGreenLight,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Signal Protocol & WebRTC",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

val WaGreenDark = Color(0xFF075E54)
val WaGreenPrimary = Color(0xFF128C7E)
val WaGreenLight = Color(0xFF25D366)
val WaBackgroundChat = Color(0xFFEFEAE2)
val WaBubbleOut = Color(0xFFDCF8C6)

@Composable
fun WhatsAppTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (appTheme) {
        AppTheme.LIGHT  -> false
        AppTheme.DARK   -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) {
        darkColorScheme(
            primary       = WaGreenPrimary,
            secondary     = WaGreenLight,
            background    = Color(0xFF121212),
            surface       = Color(0xFF1E1E1E),
            onSurface     = Color.White,
            onBackground  = Color.White
        )
    } else {
        lightColorScheme(
            primary    = WaGreenPrimary,
            secondary  = WaGreenLight,
            background = Color.White
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

data class UserUI(
    val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val lastMessagePreview: String = "",
    val isBanned: Boolean = false
)

data class MessageUI(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val isVoiceNote: Boolean = false,
    val waveform: List<Int> = emptyList(),
    val isAttachment: Boolean = false,
    val attachmentType: String = "", // "IMAGE", "DOCUMENT", "AUDIO", "VIDEO"
    val attachmentName: String = "",
    val attachmentUri: String = "",
    val attachmentSize: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class CallLogUI(
    val id: String = java.util.UUID.randomUUID().toString(),
    val partner: UserUI,
    val isVideo: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppMainScreen(settingsVm: SettingsViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(0) }
    var activeChatPartner by remember { mutableStateOf<UserUI?>(null) }
    var isInCall by remember { mutableStateOf(false) }
    var isCallVideo by remember { mutableStateOf(false) }
    var activeCallChannel by remember { mutableStateOf<String?>(null) }
    var activeCallToken by remember { mutableStateOf("") }
    var isAdminMode by remember { mutableStateOf(false) }

    val offlineRepository = remember { com.whatsapp.clone.repository.AndroidOfflineSyncRepository() }

    val recentChats = remember {
        mutableStateListOf(
            UserUI("admin", "admin", "System Admin", "Official VibeSync Administrator")
        )
    }
    val callLogs = remember { mutableStateListOf<CallLogUI>() }
    val chatThreads = remember {
        mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<MessageUI>>()
    }

    var isSettingsOpen by remember { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val signalingScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("vibe_sync_prefs", android.content.Context.MODE_PRIVATE) }
    var assignedDeviceUsername by remember { 
        mutableStateOf(prefs.getString("saved_username", "Current User") ?: "Current User") 
    }
    var showSplashScreen by remember { mutableStateOf(true) }
    var showSignupScreen by remember { mutableStateOf(assignedDeviceUsername == "Current User") }

    // Dynamic Remote Policy & Feature Configuration
    val remoteConfigManager = remember {
        com.whatsapp.clone.platform.RemoteConfigManager.instance.apply { init(context) }
    }
    val remoteConfig by remoteConfigManager.configState.collectAsState()

    // Dynamic Screenshot Policy (FLAG_SECURE)
    LaunchedEffect(remoteConfig.allowScreenshots) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        if (!remoteConfig.allowScreenshots) {
            activity.window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
            android.util.Log.d("VibeSync", "Policy applied: FLAG_SECURE active (screenshots blocked)")
        } else {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            android.util.Log.d("VibeSync", "Policy applied: FLAG_SECURE cleared (screenshots allowed)")
        }
    }

    // Dynamic Voice Calling policy tab reset
    LaunchedEffect(remoteConfig.voiceCallingEnabled) {
        if (!remoteConfig.voiceCallingEnabled && selectedTab == 2) {
            selectedTab = 0
        }
    }

    // Real-time Chat Sanitization: wipe in-memory threads and previews when Admin triggers Sanitize
    LaunchedEffect(Unit) {
        com.whatsapp.clone.platform.DeviceSanitizerManager.sanitizeMessagesEvent.collect {
            android.util.Log.i("VibeSync", "Sanitization event received: clearing chat threads & message previews")
            chatThreads.clear()
            for (i in recentChats.indices) {
                recentChats[i] = recentChats[i].copy(lastMessagePreview = "")
            }
        }
    }

    // Helper to sync conversations and messages from backend
    suspend fun syncConversationsWithBackend(username: String) {
        if (username.isBlank() || username == "Current User") return
        try {
            val dbConvs = UserApiClient.fetchConversations(username, context)
            if (dbConvs.isNotEmpty()) {
                val realUsers = mutableListOf<UserUI>()
                for (conv in dbConvs) {
                    val partnerId = conv.optString("partner_id")
                    val partnerUsername = conv.optString("partner_username", partnerId)
                    val partnerName = conv.optString("partner_display_name", partnerUsername)
                    val preview = conv.optString("last_message_preview", "")
                    if (partnerId.isNotBlank()) {
                        val partnerUser = UserUI(
                            id = partnerId,
                            username = partnerUsername,
                            displayName = partnerName,
                            bio = "Available | Powered by VibeSync",
                            lastMessagePreview = preview
                        )
                        realUsers.add(partnerUser)

                        val convId = conv.optString("id")
                        val dbMsgs = if (convId.isNotEmpty()) {
                            UserApiClient.fetchMessages(convId, username, context)
                        } else {
                            UserApiClient.fetchMessages(partnerId, username, context)
                        }
                        if (dbMsgs.isNotEmpty()) {
                            val threadList = chatThreads.getOrPut(partnerId) { mutableStateListOf() }
                            dbMsgs.forEach { msg ->
                                if (!threadList.any { it.id == msg.id }) {
                                    threadList.add(msg)
                                }
                            }
                            if (partnerUsername != partnerId) {
                                chatThreads[partnerUsername] = threadList
                            }
                        }
                    }
                }
                if (realUsers.isNotEmpty()) {
                    recentChats.clear()
                    recentChats.addAll(realUsers)
                }
            }

            // Always ensure System Admin appears in recent chats and load admin message history
            val adminEntry = UserUI("admin", "admin", "System Admin", "Official VibeSync Administrator")
            if (!recentChats.any { it.id == "admin" }) {
                recentChats.add(0, adminEntry)
            }
            // Load admin messages from backend using the admin-specific endpoint
            try {
                val adminMsgs = UserApiClient.fetchMessages("admin", username, context)
                if (adminMsgs.isNotEmpty()) {
                    val adminThread = chatThreads.getOrPut("admin") { mutableStateListOf() }
                    adminMsgs.forEach { msg ->
                        if (!adminThread.any { it.id == msg.id }) {
                            adminThread.add(msg)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("VibeSync", "Admin message sync failed: ${e.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("VibeSync", "Error loading conversations: ${e.message}")
        }
    }


    // Dual-Path Call Signaling Gateway
    // Starts WebSocket relay + Agora Chat listener after identity is resolved.
    // Incoming CALL_INVITE signals -> IncomingCallNotificationManager (wakes screen).
    LaunchedEffect(assignedDeviceUsername) {
        if (assignedDeviceUsername == "Current User" || assignedDeviceUsername.isBlank()) return@LaunchedEffect

        // 1. WebSocket relay (backend /ws)
        val wsManager = com.whatsapp.clone.platform.WebSocketSignalingManager.instance
        wsManager.start(assignedDeviceUsername, context)

        // 2. Agora Chat (if App Key is configured — gracefully skips if not)
        val chatManager = com.whatsapp.clone.platform.AgoraChatManager.instance
        chatManager.init(context, "YOUR_AGORA_CHAT_APP_KEY") // replace with real key from Agora Console
        chatManager.login(assignedDeviceUsername)

        // Observe incoming call events from WebSocket relay
        signalingScope.launch {
            wsManager.incomingCalls.collect { event ->
                android.util.Log.d("VibeSync", "Incoming call from ${event.callerName} (WS relay)")
                com.whatsapp.clone.platform.IncomingCallNotificationManager.show(context, event)
            }
        }

        // Observe incoming call events from Agora Chat
        signalingScope.launch {
            chatManager.incomingCalls.collect { event ->
                android.util.Log.d("VibeSync", "Incoming call from ${event.callerName} (Agora Chat)")
                com.whatsapp.clone.platform.IncomingCallNotificationManager.show(context, event)
            }
        }

        // Observe incoming real-time messages from WebSocket relay (e.g. from Admin or other users)
        signalingScope.launch {
            wsManager.incomingMessages.collect { msg ->
                android.util.Log.d("VibeSync", "Incoming message from ${msg.senderDisplayName}: ${msg.content}")
                
                val partnerId = msg.senderId
                val partnerUsername = msg.senderUsername
                val partnerDisplayName = msg.senderDisplayName.ifBlank { partnerUsername }
                val partnerUser = UserUI(
                    id = partnerId,
                    username = partnerUsername,
                    displayName = partnerDisplayName,
                    bio = "Available | Powered by VibeSync",
                    lastMessagePreview = msg.content
                )

                // 1. Move/add sender to top of recent chats
                val existingIndex = recentChats.indexOfFirst { 
                    it.id.equals(partnerId, ignoreCase = true) || it.username.equals(partnerUsername, ignoreCase = true) 
                }
                if (existingIndex >= 0) {
                    val existing = recentChats.removeAt(existingIndex)
                    recentChats.add(0, existing.copy(lastMessagePreview = msg.content))
                } else {
                    recentChats.add(0, partnerUser)
                }

                // 2. Add message to thread (share same list instance across partnerId and partnerUsername)
                val newMsg = MessageUI(
                    id = msg.id,
                    senderId = partnerId,
                    text = msg.content,
                    isVoiceNote = msg.isVoiceNote,
                    timestamp = msg.timestamp
                )
                val thread = chatThreads.getOrPut(partnerId) { mutableStateListOf() }
                if (partnerUsername.isNotBlank()) {
                    chatThreads[partnerUsername] = thread
                    val cleanUsername = partnerUsername.trim().lowercase().removePrefix("@")
                    if (cleanUsername.isNotBlank()) {
                        chatThreads[cleanUsername] = thread
                    }
                }
                if (!thread.any { it.id == newMsg.id }) {
                    thread.add(newMsg)
                }

                // 3. Update offline repository reactive flow
                val domainMsg = com.whatsapp.clone.models.Message(
                    id = newMsg.id,
                    conversationId = msg.conversationId.ifBlank { partnerId },
                    senderId = partnerId,
                    recipientId = assignedDeviceUsername,
                    messageType = if (newMsg.isVoiceNote) com.whatsapp.clone.models.MessageType.VOICE_NOTE else com.whatsapp.clone.models.MessageType.TEXT,
                    content = newMsg.text,
                    status = com.whatsapp.clone.models.MessageStatus.DELIVERED,
                    createdAt = msg.timestamp
                )
                offlineRepository.addMessageToConversation(partnerId, domainMsg)
                if (partnerUsername.isNotBlank() && partnerUsername != partnerId) {
                    offlineRepository.addMessageToConversation(partnerUsername, domainMsg)
                }

                // 4. Show system notification for incoming message (so user sees it even when not in chat)
                try {
                    val notifManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    val channelId = "vibesync_messages"
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val channel = android.app.NotificationChannel(
                            channelId,
                            "Messages",
                            android.app.NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Incoming messages from VibeSync"
                            enableVibration(true)
                        }
                        notifManager.createNotificationChannel(channel)
                    }
                    val openIntent = android.content.Intent(context, MainActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context, (System.currentTimeMillis() % 10000).toInt(), openIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_email)
                        .setContentTitle(partnerDisplayName)
                        .setContentText(msg.content.take(100))
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .build()
                    notifManager.notify(partnerId.hashCode(), notification)
                } catch (e: Exception) {
                    android.util.Log.w("VibeSync", "Failed to show notification: ${e.message}")
                }
            }
        }


        // Fetch user conversations from backend on login / startup
        signalingScope.launch {
            syncConversationsWithBackend(assignedDeviceUsername)
        }

        // Observe CALL_ACCEPTED / CALL_REJECTED / CALL_ENDED signals
        signalingScope.launch {
            wsManager.callSignals.collect { signal ->
                when (signal.type) {
                    "CALL_REJECTED", "CALL_REJECT" -> {
                        com.whatsapp.clone.platform.IncomingCallNotificationManager.cancel(context)
                        if (isInCall) {
                            isInCall = false
                            android.widget.Toast.makeText(context, "Call was declined", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    "CALL_ENDED", "CALL_END" -> {
                        com.whatsapp.clone.platform.IncomingCallNotificationManager.cancel(context)
                        if (isInCall) {
                            isInCall = false
                            android.widget.Toast.makeText(context, "Call ended", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // Observe accepted calls from MainActivity intent (handles both cold starts and onNewIntent)
    val pendingCallIntent by MainActivity.activeCallIntent.collectAsState()
    LaunchedEffect(pendingCallIntent) {
        val callIntent = pendingCallIntent ?: return@LaunchedEffect
        val channel = callIntent.getStringExtra("ACCEPTED_CALL_CHANNEL")
        if (!channel.isNullOrBlank()) {
            val callerId = callIntent.getStringExtra("ACCEPTED_CALL_CALLER_ID")?.ifBlank { "admin" } ?: "admin"
            val callerName = callIntent.getStringExtra("ACCEPTED_CALL_CALLER_NAME")?.ifBlank { "System Admin" } ?: "System Admin"
            val isVideo = callIntent.getBooleanExtra("ACCEPTED_CALL_IS_VIDEO", false)
            val token = callIntent.getStringExtra("ACCEPTED_CALL_TOKEN") ?: ""

            android.util.Log.i("VibeSync", "Connecting incoming call: caller=$callerName channel=$channel video=$isVideo tokenLength=${token.length}")

            activeChatPartner = UserUI(
                id = callerId,
                username = callerName,
                displayName = callerName
            )
            activeCallChannel = channel
            activeCallToken = token
            isCallVideo = isVideo
            isInCall = true

            // Clear processed intent
            MainActivity.activeCallIntent.value = null
        }
    }

    // Hardware Identity Sync & Splash Transition
    LaunchedEffect(Unit) {
        try {
            // Check for OTA Enterprise Package Updates
            com.whatsapp.clone.platform.OtaUpdateManager.instance.checkForUpdates(context)

            // Auto-discover working server IP on current Wi-Fi/Network
            NetworkConfig.discoverServer(context)

            val localSaved = prefs.getString("saved_username", null)
            if (!localSaved.isNullOrBlank() && localSaved != "Current User") {
                assignedDeviceUsername = localSaved
                showSignupScreen = false
            }

            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "emulator_device_id"
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            val remoteUsername = UserApiClient.syncDeviceWithBackend(context, deviceId, deviceModel, localSaved)
            
            val finalUsername = if (remoteUsername != "Current User" && remoteUsername.isNotBlank()) {
                assignedDeviceUsername = remoteUsername
                prefs.edit().putString("saved_username", remoteUsername).apply()
                showSignupScreen = false
                remoteUsername
            } else if (!localSaved.isNullOrBlank() && localSaved != "Current User") {
                assignedDeviceUsername = localSaved
                showSignupScreen = false
                localSaved
            } else {
                showSignupScreen = true
                null
            }

            if (!finalUsername.isNullOrBlank()) {
                syncConversationsWithBackend(finalUsername)
            }
        } catch (_: Exception) {
            val localSaved = prefs.getString("saved_username", null)
            if (!localSaved.isNullOrBlank() && localSaved != "Current User") {
                assignedDeviceUsername = localSaved
                showSignupScreen = false
                syncConversationsWithBackend(localSaved)
            } else {
                showSignupScreen = true
            }
        } finally {
            showSplashScreen = false
        }
    }

    if (showSplashScreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF075E54)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "VibeSync",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Secure Messaging Platform", fontSize = 14.sp, color = Color(0xFFDCF8C6))
            }
        }
        return
    }

    if (remoteConfig.maintenanceMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Maintenance",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "System Maintenance",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "VibeSync services are currently undergoing scheduled maintenance. Please check back shortly.",
                    fontSize = 15.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    if (showSignupScreen) {
        SignupScreen(
            onSignupComplete = { username ->
                assignedDeviceUsername = username
                prefs.edit().putString("saved_username", username).apply()
                showSignupScreen = false
                signalingScope.launch {
                    syncConversationsWithBackend(username)
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            if (!isSettingsOpen) {
                TopAppBar(
                    title = { 
                        Text(
                            if (isAdminMode) "VibeSync Admin Portal" 
                            else (activeChatPartner?.displayName ?: "VibeSync"), 
                            color = Color.White, 
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isAdminMode) Color(0xFF1E293B) else WaGreenDark),
                    navigationIcon = {
                        if (isAdminMode) {
                            IconButton(onClick = { isAdminMode = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Admin Mode", tint = Color.White)
                            }
                        } else if (activeChatPartner != null) {
                            IconButton(onClick = { activeChatPartner = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        }
                    },
                    actions = {
                        if (!isAdminMode && activeChatPartner != null) {
                            if (remoteConfig.voiceCallingEnabled) {
                                IconButton(onClick = {
                                    isCallVideo = false
                                    val p = activeChatPartner!!
                                    callLogs.add(0, CallLogUI(partner = p, isVideo = false))
                                    val channelName = p.id
                                    activeCallChannel = channelName
                                    activeCallToken = ""
                                    isInCall = true

                                    // Send dual-path CALL_INVITE signals (Agora Chat + WebSocket relay)
                                    com.whatsapp.clone.platform.AgoraChatManager.instance.sendCallInvite(
                                        recipientId = p.id,
                                        callerName  = assignedDeviceUsername,
                                        isVideo     = false,
                                        channelName = channelName
                                    )
                                    com.whatsapp.clone.platform.WebSocketSignalingManager.instance.sendCallInitiate(
                                        recipientId = p.id,
                                        callerName  = assignedDeviceUsername,
                                        isVideo     = false,
                                        channelName = channelName
                                    )

                                    // Join Agora RTC channel (caller side)
                                    com.whatsapp.clone.platform.AgoraCallManager.instance.let { cm ->
                                        cm.init(context, AGORA_APP_ID)
                                        cm.joinCall("", channelName, 0, false)
                                    }

                                    // Log call to backend
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        try {
                                            val url = java.net.URL("${com.whatsapp.clone.config.NetworkConfig.BASE_HTTP_URL}/api/calls/log")
                                            val conn = url.openConnection() as java.net.HttpURLConnection
                                            conn.requestMethod = "POST"
                                            conn.setRequestProperty("Content-Type", "application/json")
                                            conn.doOutput = true
                                            val jsonPayload = """{"recipient_id": "${p.id}", "is_video": false}"""
                                            conn.outputStream.write(jsonPayload.toByteArray(Charsets.UTF_8))
                                            conn.responseCode
                                        } catch (_: Exception) {}
                                    }
                                }) {
                                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
                                }
                                IconButton(onClick = {
                                    isCallVideo = true
                                    val p = activeChatPartner!!
                                    callLogs.add(0, CallLogUI(partner = p, isVideo = true))
                                    val channelName = p.id
                                    activeCallChannel = channelName
                                    activeCallToken = ""
                                    isInCall = true

                                    // Send dual-path CALL_INVITE signals (Agora Chat + WebSocket relay)
                                    com.whatsapp.clone.platform.AgoraChatManager.instance.sendCallInvite(
                                        recipientId = p.id,
                                        callerName  = assignedDeviceUsername,
                                        isVideo     = true,
                                        channelName = channelName
                                    )
                                    com.whatsapp.clone.platform.WebSocketSignalingManager.instance.sendCallInitiate(
                                        recipientId = p.id,
                                        callerName  = assignedDeviceUsername,
                                        isVideo     = true,
                                        channelName = channelName
                                    )

                                    // Join Agora RTC channel (caller side)
                                    com.whatsapp.clone.platform.AgoraCallManager.instance.let { cm ->
                                        cm.init(context, AGORA_APP_ID)
                                        cm.joinCall("", channelName, 0, true)
                                    }

                                    // Log call to backend
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        try {
                                            val url = java.net.URL("${com.whatsapp.clone.config.NetworkConfig.BASE_HTTP_URL}/api/calls/log")
                                            val conn = url.openConnection() as java.net.HttpURLConnection
                                            conn.requestMethod = "POST"
                                            conn.setRequestProperty("Content-Type", "application/json")
                                            conn.doOutput = true
                                            val jsonPayload = """{"recipient_id": "${p.id}", "is_video": true}"""
                                            conn.outputStream.write(jsonPayload.toByteArray(Charsets.UTF_8))
                                            conn.responseCode
                                        } catch (_: Exception) {}
                                    }
                                }) {
                                    Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = Color.White)
                                }
                            }
                        } else if (!isAdminMode) {
                            IconButton(onClick = { selectedTab = 1 }) {
                                Icon(Icons.Default.PersonSearch, contentDescription = "Global Search", tint = Color.White)
                            }
                            Box {
                                IconButton(onClick = { isMenuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = isMenuExpanded,
                                    onDismissRequest = { isMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("New group") },
                                        leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                                        onClick = { isMenuExpanded = false; selectedTab = 1 }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Starred messages") },
                                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                        onClick = { isMenuExpanded = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick = {
                                            isMenuExpanded = false
                                            isSettingsOpen = true
                                        }
                                    )
                                    // Legacy in-app admin menu replaced by dedicated Web Admin Dashboard
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        val currentUsername = if (assignedDeviceUsername != "Current User" && assignedDeviceUsername.isNotBlank()) {
            assignedDeviceUsername
        } else {
            prefs.getString("saved_username", "VibeSync User") ?: "VibeSync User"
        }

        Box(modifier = Modifier.padding(if (isSettingsOpen) PaddingValues(0.dp) else padding)) {
            if (isAdminMode) {
                AdminDashboardScreen(onExit = { isAdminMode = false })
            } else if (isSettingsOpen) {
                SettingsNavHost(
                    onBack = { isSettingsOpen = false },
                    viewModel = settingsVm,
                    username = currentUsername
                )
            } else if (activeChatPartner != null) {
                val currentPartner = activeChatPartner!!
                val partnerKey = currentPartner.id
                val partnerUsername = currentPartner.username
                val sharedThread = chatThreads.getOrPut(partnerKey) {
                    chatThreads[partnerUsername] ?: mutableStateListOf()
                }
                if (partnerUsername.isNotBlank()) {
                    chatThreads[partnerUsername] = sharedThread
                }

                ChatRoomScreen(
                    partner = currentPartner,
                    currentUsername = currentUsername,
                    messages = sharedThread,
                    onSendMessage = { text ->
                        val partnerId = currentPartner.id
                        val currentSender = currentUsername

                        // 1. Add locally to snapshot state list
                        val localMsg = MessageUI(
                            id = java.util.UUID.randomUUID().toString(),
                            senderId = "me",
                            text = text,
                            isVoiceNote = text.startsWith("🎵"),
                            timestamp = System.currentTimeMillis()
                        )
                        val thread = chatThreads.getOrPut(partnerId) { mutableStateListOf() }
                        if (currentPartner.username.isNotBlank()) {
                            chatThreads[currentPartner.username] = thread
                        }
                        if (!thread.any { it.id == localMsg.id }) {
                            thread.add(localMsg)
                        }

                        // 2. Update recent chats
                        val existingIndex = recentChats.indexOfFirst { 
                            it.id == partnerId || it.username.equals(activeChatPartner!!.username, ignoreCase = true) 
                        }
                        if (existingIndex >= 0) {
                            val existing = recentChats.removeAt(existingIndex)
                            recentChats.add(0, existing.copy(lastMessagePreview = text))
                        } else {
                            recentChats.add(0, activeChatPartner!!.copy(lastMessagePreview = text))
                        }

                        // 3. Create Message domain model and attempt deduplicated persistence
                        val domainMsg = com.whatsapp.clone.models.Message(
                            id = localMsg.id,
                            conversationId = partnerId,
                            senderId = currentSender,
                            recipientId = partnerId,
                            messageType = if (text.startsWith("🎵")) com.whatsapp.clone.models.MessageType.VOICE_NOTE else com.whatsapp.clone.models.MessageType.TEXT,
                            content = text,
                            status = com.whatsapp.clone.models.MessageStatus.PENDING,
                            createdAt = System.currentTimeMillis()
                        )
                        offlineRepository.saveMessageWithDeduplication(domainMsg)

                        // 4. Asynchronously post to backend API endpoint to persist in app.db dynamically
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            val hosts = UserApiClient.getCandidateHosts(context)
                            val isVoice = text.startsWith("🎵")
                            val payload = JSONObject().apply {
                                put("recipient_id", partnerId)
                                put("sender_id", currentSender)
                                put("message_type", if (isVoice) "VOICE_NOTE" else "TEXT")
                                put("content", text)
                            }.toString()

                            for (host in hosts) {
                                try {
                                    val url = java.net.URL("$host/api/messages/send")
                                    val conn = url.openConnection() as java.net.HttpURLConnection
                                    conn.requestMethod = "POST"
                                    conn.setRequestProperty("Content-Type", "application/json")
                                    conn.doOutput = true
                                    conn.connectTimeout = 3000
                                    conn.readTimeout = 3000
                                    conn.outputStream.write(payload.toByteArray(Charsets.UTF_8))
                                    if (conn.responseCode in 200..299) {
                                        com.whatsapp.clone.config.NetworkConfig.setWorkingBaseUrl(host, context)
                                        break
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    },
                    onBack = { activeChatPartner = null }
                )
            } else {
                Column {
                    TabRow(selectedTabIndex = selectedTab, containerColor = WaGreenDark, contentColor = Color.White) {
                        androidx.compose.material3.Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                            Text("CHATS", modifier = Modifier.padding(12.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        androidx.compose.material3.Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                            Text("FRIENDS", modifier = Modifier.padding(12.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        if (remoteConfig.voiceCallingEnabled) {
                            androidx.compose.material3.Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                                Text("CALLS", modifier = Modifier.padding(12.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    when (selectedTab) {
                        0 -> ChatsTabScreen(
                            recentChats = recentChats,
                            chatThreads = chatThreads,
                            onSelectUser = { activeChatPartner = it }
                        )
                        1 -> GlobalSearchTabScreen(
                            currentUsername = currentUsername,
                            onSelectUser = {
                                if (!recentChats.any { c -> c.id == it.id }) {
                                    recentChats.add(0, it)
                                }
                                activeChatPartner = it
                            }
                        )
                        2 -> if (remoteConfig.voiceCallingEnabled) {
                            CallsTabScreen(
                                callLogs = callLogs,
                                onCallUser = { partner, isVideo ->
                                    activeChatPartner = partner
                                    isCallVideo = isVideo
                                    callLogs.add(0, CallLogUI(partner = partner, isVideo = isVideo))
                                    isInCall = true
                                }
                            )
                        } else {
                            ChatsTabScreen(
                                recentChats = recentChats,
                                chatThreads = chatThreads,
                                onSelectUser = { activeChatPartner = it }
                            )
                        }
                    }
                }
            }

            if (isInCall) {
                CallOverlayScreen(
                    partner = activeChatPartner,
                    channelName = activeCallChannel ?: (activeChatPartner?.id ?: "vibe_sync_channel"),
                    token = activeCallToken,
                    isVideo = isCallVideo,
                    onEndCall = {
                        // Send CALL_END signal to remote party
                        activeChatPartner?.let { partner ->
                            com.whatsapp.clone.platform.AgoraChatManager.instance.sendCallEnd(partner.id)
                            com.whatsapp.clone.platform.WebSocketSignalingManager.instance.sendCallEnded(partner.id)
                        }
                        com.whatsapp.clone.platform.AgoraCallManager.instance.leaveCall()
                        com.whatsapp.clone.platform.IncomingCallNotificationManager.cancel(context)
                        try {
                            context.stopService(android.content.Intent(context, com.whatsapp.clone.platform.OngoingCallService::class.java))
                        } catch (_: Exception) {}
                        isInCall = false
                        activeCallChannel = null
                        activeCallToken = ""
                    }
                )
            }
        }
    }
}

@Composable
fun ChatsTabScreen(
    recentChats: List<UserUI>,
    chatThreads: Map<String, List<MessageUI>>,
    onSelectUser: (UserUI) -> Unit
) {
    if (recentChats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No recent conversations. Use FRIENDS tab to start a chat!", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(recentChats) { user ->
                val lastMsg = chatThreads[user.id]?.lastOrNull() ?: chatThreads[user.username]?.lastOrNull()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectUser(user) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(WaGreenPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(user.displayName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        val previewText = when {
                            lastMsg != null -> if (lastMsg.isVoiceNote) "🎵 Voice Note" else lastMsg.text
                            user.lastMessagePreview.isNotBlank() -> user.lastMessagePreview
                            else -> "@${user.username} • ${user.bio}"
                        }
                        Text(
                            text = previewText,
                            color = Color.Gray,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))
            }
        }
    }
}

@Composable
fun GlobalSearchTabScreen(
    currentUsername: String = "",
    onSelectUser: (UserUI) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var friendsList by remember { mutableStateOf<List<UserUI>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val reloadFriends: suspend () -> Unit = {
        isLoading = true
        val resolvedUser = if (currentUsername.isNotBlank() && !currentUsername.equals("Current User", ignoreCase = true)) {
            currentUsername
        } else {
            val p = context.getSharedPreferences("vibe_sync_prefs", android.content.Context.MODE_PRIVATE)
            p.getString("saved_username", "") ?: ""
        }

        if (resolvedUser.isNotBlank() && !resolvedUser.equals("Current User", ignoreCase = true)) {
            val assigned = UserApiClient.fetchFriends(resolvedUser, context)
            friendsList = assigned
        } else {
            friendsList = emptyList()
        }
        isLoading = false
    }

    LaunchedEffect(currentUsername) {
        reloadFriends()
    }

    // Live reactive listener for admin assignments/deletions over WebSocket
    LaunchedEffect(Unit) {
        com.whatsapp.clone.platform.WebSocketSignalingManager.instance.friendsUpdates.collect {
            reloadFriends()
        }
    }

    val displayList = remember(searchQuery, friendsList) {
        if (searchQuery.isBlank()) {
            friendsList
        } else {
            val q = searchQuery.trim()
            friendsList.filter {
                it.username.contains(q, ignoreCase = true) || it.displayName.contains(q, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search assigned friends...", color = Color(0xFF94A3B8)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = WaGreenPrimary,
                unfocusedBorderColor = Color(0xFF334155),
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isBlank()) "MY ASSIGNED FRIENDS (${friendsList.size})" else "SEARCH RESULTS (${displayList.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WaGreenPrimary
            )
            if (isLoading) {
                Text("Syncing...", fontSize = 11.sp, color = Color.Gray)
            }
        }

        if (displayList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PeopleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isBlank()) "No friends assigned yet"
                        else "No matching friends found",
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (searchQuery.isBlank()) "Ask your administrator to assign friends for your account from the Admin Dashboard."
                        else "Only friends assigned by your administrator appear here.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn {
                items(displayList) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectUser(user) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(WaGreenDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(user.displayName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, fontWeight = FontWeight.Bold)
                            Text("@${user.username}", color = Color.Gray, fontSize = 13.sp)
                        }
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat", tint = WaGreenPrimary)
                    }
                    HorizontalDivider(color = Color(0xFFF5F5F5))
                }
            }
        }
    }
}

@Composable
fun CallsTabScreen(
    callLogs: List<CallLogUI>,
    onCallUser: (UserUI, Boolean) -> Unit
) {
    if (callLogs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(64.dp), tint = WaGreenPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("No recent calls", color = Color.Gray)
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(callLogs) { log ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(WaGreenDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(log.partner.displayName.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(log.partner.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (log.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = null,
                                tint = WaGreenPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Outgoing • ${if (log.isVideo) "Video" else "Voice"} • Just now",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                    IconButton(onClick = { onCallUser(log.partner, log.isVideo) }) {
                        Icon(
                            imageVector = if (log.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = "Re-call",
                            tint = WaGreenPrimary
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))
            }
        }
    }
}

class VoiceRecorder(private val context: android.content.Context) {
    private var recorder: MediaRecorder? = null
    var currentFile: File? = null

    fun startRecording() {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        currentFile = file
        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    fun stopRecording(): File? {
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            currentFile
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            null
        }
    }
}

@Composable
fun ChatRoomScreen(
    partner: UserUI,
    currentUsername: String = "me",
    messages: androidx.compose.runtime.snapshots.SnapshotStateList<MessageUI>,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var hasMicPermission by remember { mutableStateOf(false) }

    // Attachment State
    var pendingAttachmentUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingAttachmentType by remember { mutableStateOf("") }
    var pendingAttachmentName by remember { mutableStateOf("") }
    var pendingAttachmentSize by remember { mutableStateOf("") }

    val voiceRecorder = remember { VoiceRecorder(context) }
    val emojis = remember {
        listOf("😀", "😂", "😍", "👍", "🔥", "🎉", "❤️", "🙏", "😎", "🥳", "🥺", "🤔", "👏", "💯", "🚀")
    }

    val listState = rememberLazyListState()

    // 1. Reactive WebSocket listener for the active conversation (instant real-time delivery)
    LaunchedEffect(partner.id, partner.username) {
        val wsManager = com.whatsapp.clone.platform.WebSocketSignalingManager.instance
        wsManager.incomingMessages.collect { msg ->
            val pIdClean = partner.id.trim().lowercase().removePrefix("@")
            val pUserClean = partner.username.trim().lowercase().removePrefix("@")
            val sIdClean = msg.senderId.trim().lowercase().removePrefix("@")
            val sUserClean = msg.senderUsername.trim().lowercase().removePrefix("@")

            val isMatch = sIdClean == pIdClean || sUserClean == pUserClean ||
                          sIdClean == pUserClean || sUserClean == pIdClean ||
                          (pIdClean == "admin" && (sIdClean == "admin" || sUserClean == "admin"))

            if (isMatch) {
                val newMsg = MessageUI(
                    id = msg.id,
                    senderId = msg.senderId,
                    text = msg.content,
                    isVoiceNote = msg.isVoiceNote,
                    timestamp = msg.timestamp
                )
                if (!messages.any { it.id == newMsg.id }) {
                    messages.add(newMsg)
                    android.util.Log.d("VibeSync", "ChatRoomScreen added real-time incoming msg: ${newMsg.text}")
                }
            }
        }
    }

    // Real-time Chat Sanitization: wipe current chat messages instantly
    LaunchedEffect(Unit) {
        com.whatsapp.clone.platform.DeviceSanitizerManager.sanitizeMessagesEvent.collect {
            android.util.Log.i("VibeSync", "ChatRoomScreen received sanitization event: clearing in-memory messages")
            messages.clear()
        }
    }

    // 2. Initial fetch & reactive continuous background sync while conversation is open
    LaunchedEffect(partner.id, partner.username) {
        while (isActive) {
            try {
                var remoteMsgs = UserApiClient.fetchMessages(partner.id, currentUsername, context)
                if (remoteMsgs.isEmpty() && partner.username.isNotBlank() && partner.username != partner.id) {
                    remoteMsgs = UserApiClient.fetchMessages(partner.username, currentUsername, context)
                }
                if (remoteMsgs.isEmpty()) {
                    if (messages.isNotEmpty()) {
                        messages.clear()
                    }
                } else {
                    val remoteIds = remoteMsgs.map { it.id }.toSet()
                    messages.removeAll { it.id !in remoteIds }
                    remoteMsgs.forEach { msg ->
                        if (!messages.any { it.id == msg.id }) {
                            messages.add(msg)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VibeSync", "ChatRoomScreen fetch error: ${e.message}")
            }
            kotlinx.coroutines.delay(2500)
        }
    }

    // 3. Auto-scroll to bottom whenever messages list size changes
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val contentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            pendingAttachmentUri = uri
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var name = "attachment"
            var size = "Unknown size"
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIndex != -1) name = it.getString(nameIndex) ?: "attachment"
                    if (sizeIndex != -1) {
                        val sizeBytes = it.getLong(sizeIndex)
                        size = if (sizeBytes > 0) "${sizeBytes / 1024} KB" else "Unknown size"
                    }
                }
            }
            pendingAttachmentName = name
            pendingAttachmentSize = size
            Toast.makeText(context, "Attached: $name ($size)", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Microphone permission required for voice notes", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone permission granted! Tap mic again to record.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(WaBackgroundChat)) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(12.dp)) {
            items(messages) { msg ->
                val isOutgoing = msg.senderId == "me" || (!msg.senderId.equals(partner.id, ignoreCase = true) && !msg.senderId.equals(partner.username, ignoreCase = true))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (isOutgoing) WaBubbleOut else Color.White,
                        contentColor = Color(0xFF111B21),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp).widthIn(max = 280.dp),
                        shadowElevation = 1.dp
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (msg.isAttachment) {
                                when (msg.attachmentType) {
                                    "IMAGE" -> {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Image, contentDescription = "Photo", tint = WaGreenPrimary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(msg.attachmentName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF111B21))
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(120.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFFE2E8F0)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.PhotoSizeSelectActual, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                                                    Text("Photo Attachment • ${msg.attachmentSize}", fontSize = 11.sp, color = Color.Gray)
                                                }
                                            }
                                            if (msg.text.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(msg.text, fontSize = 14.sp, color = Color(0xFF111B21))
                                            }
                                        }
                                    }
                                    "DOCUMENT" -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Description, contentDescription = "Document", tint = WaGreenPrimary, modifier = Modifier.size(32.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(msg.attachmentName, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, color = Color(0xFF111B21))
                                                Text("Document • ${msg.attachmentSize}", fontSize = 11.sp, color = Color.Gray)
                                            }
                                            Icon(Icons.Default.FileDownload, contentDescription = "Download", tint = WaGreenPrimary)
                                        }
                                        if (msg.text.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(msg.text, fontSize = 14.sp, color = Color(0xFF111B21))
                                        }
                                    }
                                    else -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.InsertDriveFile, contentDescription = "File", tint = WaGreenPrimary)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("${msg.attachmentName} (${msg.attachmentSize})", fontSize = 14.sp, color = Color(0xFF111B21))
                                        }
                                        if (msg.text.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(msg.text, fontSize = 14.sp, color = Color(0xFF111B21))
                                        }
                                    }
                                }
                            } else if (msg.isVoiceNote) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        Toast.makeText(context, "Playing voice note...", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play Voice Note", tint = WaGreenPrimary)
                                    }
                                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        msg.waveform.forEach { amp ->
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height((amp / 4).dp)
                                                    .background(WaGreenPrimary, RoundedCornerShape(2.dp))
                                            )
                                        }
                                    }
                                    Text("1.5x", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111B21), modifier = Modifier.padding(start = 6.dp))
                                }
                            } else {
                                Text(msg.text, fontSize = 15.sp, color = Color(0xFF111B21))
                            }
                        }
                    }
                }
            }
        }

        Surface(color = Color(0xFFF0F2F5)) {
            Column {
                // Pending Attachment Banner
                if (pendingAttachmentUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE2E8F0))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (pendingAttachmentType == "IMAGE") Icons.Default.Image else Icons.Default.Description,
                            contentDescription = null,
                            tint = WaGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pendingAttachmentName, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${pendingAttachmentType} • ${pendingAttachmentSize}", fontSize = 10.sp, color = Color.Gray)
                        }
                        IconButton(
                            onClick = {
                                pendingAttachmentUri = null
                                pendingAttachmentName = ""
                                pendingAttachmentType = ""
                                pendingAttachmentSize = ""
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove Attachment", tint = Color.Gray)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        showEmojiPicker = !showEmojiPicker
                        showAttachmentSheet = false
                    }) {
                        Icon(
                            Icons.Default.SentimentSatisfiedAlt,
                            contentDescription = "Emoji",
                            tint = if (showEmojiPicker) WaGreenPrimary else Color.Gray
                        )
                    }

                    IconButton(onClick = {
                        showAttachmentSheet = !showAttachmentSheet
                        showEmojiPicker = false
                    }) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "Attach File",
                            tint = if (showAttachmentSheet) WaGreenPrimary else Color.Gray
                        )
                    }

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text(if (isRecording) "Recording voice note..." else "Type a message...") },
                        modifier = Modifier.weight(1f).testTag("chat_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        enabled = !isRecording
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FloatingActionButton(
                        onClick = {
                            if (textInput.isNotBlank() || pendingAttachmentUri != null) {
                                val newMsg = if (pendingAttachmentUri != null) {
                                    MessageUI(
                                        id = System.currentTimeMillis().toString(),
                                        senderId = "me",
                                        text = textInput,
                                        isAttachment = true,
                                        attachmentType = pendingAttachmentType,
                                        attachmentName = pendingAttachmentName,
                                        attachmentUri = pendingAttachmentUri.toString(),
                                        attachmentSize = pendingAttachmentSize
                                    )
                                } else {
                                    MessageUI(System.currentTimeMillis().toString(), "me", textInput)
                                }
                                messages.add(newMsg)
                                onSendMessage(if (newMsg.isAttachment) "📎 ${newMsg.attachmentName}" else textInput)

                                textInput = ""
                                pendingAttachmentUri = null
                                pendingAttachmentName = ""
                                pendingAttachmentType = ""
                                pendingAttachmentSize = ""
                                showEmojiPicker = false
                                showAttachmentSheet = false
                            } else {
                                if (!hasMicPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    if (!isRecording) {
                                        try {
                                            voiceRecorder.startRecording()
                                            isRecording = true
                                            Toast.makeText(context, "Recording voice note...", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to start recording: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        val file = voiceRecorder.stopRecording()
                                        isRecording = false
                                        if (file != null && file.exists()) {
                                            messages.add(
                                                MessageUI(
                                                    id = System.currentTimeMillis().toString(),
                                                    senderId = "me",
                                                    text = "Voice Note (${file.name})",
                                                    isVoiceNote = true,
                                                    waveform = listOf(20, 45, 80, 35, 60, 90, 40, 75, 30, 50)
                                                )
                                            )
                                            onSendMessage("🎵 Voice Note")
                                            Toast.makeText(context, "Voice note saved!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        },
                        containerColor = if (isRecording) Color.Red else WaGreenPrimary,
                        contentColor = Color.White,
                        modifier = Modifier.size(48.dp).testTag(if (textInput.isNotBlank() || pendingAttachmentUri != null) "send_button" else if (isRecording) "stop_button" else "mic_button")
                    ) {
                        Icon(
                            if (textInput.isNotBlank() || pendingAttachmentUri != null) Icons.AutoMirrored.Filled.Send
                            else if (isRecording) Icons.Default.Stop
                            else Icons.Default.Mic,
                            contentDescription = if (textInput.isNotBlank() || pendingAttachmentUri != null) "Send" else if (isRecording) "Stop" else "Voice Note"
                        )
                    }
                }

                // Attachment Selection Sheet
                if (showAttachmentSheet) {
                    Surface(
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    pendingAttachmentType = "IMAGE"
                                    showAttachmentSheet = false
                                    contentPickerLauncher.launch("image/*")
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFAC44CF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = "Photos", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Photos", fontSize = 12.sp, color = Color.DarkGray)
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    pendingAttachmentType = "DOCUMENT"
                                    showAttachmentSheet = false
                                    contentPickerLauncher.launch("*/*")
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF5F66CD)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = "Document", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Document", fontSize = 12.sp, color = Color.DarkGray)
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    pendingAttachmentType = "AUDIO"
                                    showAttachmentSheet = false
                                    contentPickerLauncher.launch("audio/*")
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE05C66)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AudioFile, contentDescription = "Audio", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Audio", fontSize = 12.sp, color = Color.DarkGray)
                            }
                        }
                    }
                }

                if (showEmojiPicker) {
                    Surface(
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(Color.White)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(emojis.size) { index ->
                                val emoji = emojis[index]
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clickable {
                                            textInput += emoji
                                        }
                                ) {
                                    Text(text = emoji, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallOverlayScreen(
    partner: UserUI?,
    channelName: String = partner?.id ?: "vibe_sync_channel",
    token: String = "",
    isVideo: Boolean = false,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    val callManager = remember { AgoraCallManager.instance }
    val isMuted by callManager.isMuted.collectAsState()
    val isSpeakerOn by callManager.isSpeakerOn.collectAsState()
    val remoteUid by callManager.remoteUid.collectAsState()
    val isJoined by callManager.isJoined.collectAsState()

    var callSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(remoteUid) {
        if (remoteUid != null) {
            while (true) {
                delay(1000)
                callSeconds++
            }
        } else {
            callSeconds = 0
        }
    }

    val formattedDuration = remember(callSeconds) {
        val mins = callSeconds / 60
        val secs = callSeconds % 60
        "%02d:%02d".format(mins, secs)
    }

    // Permission launcher for in-call safety
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val perms = if (isVideo) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        permissionLauncher.launch(perms)
    }

    LaunchedEffect(channelName) {
        if (channelName.isNotBlank()) {
            callManager.init(context, AGORA_APP_ID)
            callManager.joinCall(
                token = token,
                channelName = channelName,
                uid = 0,
                isVideo = isVideo
            )
        }
    }

    DisposableEffect(channelName) {
        onDispose {
            callManager.leaveCall()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111B21))
    ) {
        if (isVideo) {
            // Remote Video Stream
            if (remoteUid != null) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).also { surface ->
                            callManager.setupRemoteVideo(surface, remoteUid!!)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Top Overlay with duration
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 20.dp, end = 20.dp)
                        .align(Alignment.TopStart)
                ) {
                    Column {
                        Text(partner?.displayName ?: "User", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Connected ($formattedDuration) • HD Video", color = WaGreenLight, fontSize = 13.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = WaGreenPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (isJoined) "Connecting to Admin Video..." else "Joining Agora Channel...",
                            color = Color.White
                        )
                    }
                }
            }

            // Local Video Preview Box Overlay
            Box(
                modifier = Modifier
                    .padding(top = 40.dp, end = 16.dp)
                    .size(120.dp, 160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .align(Alignment.TopEnd)
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).also { surface ->
                            callManager.setupLocalVideo(surface)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Voice Call Screen UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(partner?.displayName ?: "User", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (remoteUid != null) "Connected ($formattedDuration) • Agora HD Voice"
                        else if (isJoined) "Connecting to Admin Voice..."
                        else "Ringing...",
                        color = if (remoteUid != null) WaGreenLight else Color.LightGray,
                        fontSize = 14.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(WaGreenPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(partner?.displayName?.take(1) ?: "U", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Bottom Controls Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionButton(
                onClick = { callManager.toggleMute(!isMuted) },
                containerColor = if (isMuted) Color.White else Color(0xFF333333)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mute",
                    tint = if (isMuted) Color.Black else Color.White
                )
            }

            FloatingActionButton(
                onClick = {
                    callManager.leaveCall()
                    onEndCall()
                },
                containerColor = Color(0xFFEF4444)
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White)
            }

            FloatingActionButton(
                onClick = { callManager.toggleSpeaker(!isSpeakerOn) },
                containerColor = if (isSpeakerOn) WaGreenPrimary else Color(0xFF333333)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Speaker",
                    tint = Color.White
                )
            }

            if (isVideo) {
                FloatingActionButton(
                    onClick = { callManager.switchCamera() },
                    containerColor = Color(0xFF333333)
                ) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                }
            }
        }
    }
}

data class AdminUserUI(
    val id: String,
    val username: String,
    val displayName: String,
    val isBanned: Boolean = false,
    val keyStatus: String = "ACTIVE_E2EE"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(onExit: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onExit)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var broadcastMessage by remember { mutableStateOf("") }
    var broadcastSentNotice by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    var showCreateUserDialog by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }
    var newDisplayName by remember { mutableStateOf("") }
    var newBio by remember { mutableStateOf("") }

    var userToEdit by remember { mutableStateOf<AdminUserUI?>(null) }
    var editDisplayName by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    var userToDelete by remember { mutableStateOf<AdminUserUI?>(null) }

    val adminUsers = remember {
        mutableStateListOf(
            AdminUserUI("1", "alice", "Alice Smith", isBanned = false),
            AdminUserUI("2", "bob_dev", "Bob Builder", isBanned = false),
            AdminUserUI("3", "spammer_bot", "Bot Spammer", isBanned = true),
            AdminUserUI("4", "charlie_kmp", "Charlie Dev", isBanned = false),
            AdminUserUI("5", "ahmed", "Ahmed", isBanned = false)
        )
    }

    LaunchedEffect(Unit) {
        val remoteUsers = UserApiClient.fetchAllUsers(context)
        if (remoteUsers.isNotEmpty()) {
            remoteUsers.forEach { remote ->
                if (adminUsers.none { it.username.equals(remote.username, ignoreCase = true) }) {
                    adminUsers.add(
                        AdminUserUI(
                            id = remote.id,
                            username = remote.username,
                            displayName = remote.displayName,
                            isBanned = false
                        )
                    )
                }
            }
        }
    }

    val filteredUsers = adminUsers.filter {
        it.username.contains(searchQuery, ignoreCase = true) || it.displayName.contains(searchQuery, ignoreCase = true)
    }

    if (showCreateUserDialog) {
        AlertDialog(
            onDismissRequest = { showCreateUserDialog = false },
            title = { Text("Create New User", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Username (e.g. johndoe)", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaGreenLight,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newDisplayName,
                        onValueChange = { newDisplayName = it },
                        label = { Text("Display Name (e.g. John Doe)", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaGreenLight,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newBio,
                        onValueChange = { newBio = it },
                        label = { Text("Bio / Status", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaGreenLight,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uName = newUsername.trim().lowercase()
                        val dName = newDisplayName.trim()
                        val bioText = newBio.trim()
                        if (uName.isNotBlank() && dName.isNotBlank()) {
                            adminUsers.add(
                                AdminUserUI(
                                    id = System.currentTimeMillis().toString(),
                                    username = uName,
                                    displayName = dName,
                                    isBanned = false
                                )
                            )
                            coroutineScope.launch {
                                UserApiClient.createUser(uName, dName, bioText)
                            }
                            Toast.makeText(context, "User @$uName created & stored in SQLite DB!", Toast.LENGTH_SHORT).show()
                            newUsername = ""
                            newDisplayName = ""
                            newBio = ""
                            showCreateUserDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaGreenPrimary)
                ) {
                    Text("CREATE USER", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateUserDialog = false }) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (userToEdit != null) {
        AlertDialog(
            onDismissRequest = { userToEdit = null },
            title = { Text("Edit User Profile (@${userToEdit?.username})", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it },
                        label = { Text("Display Name", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaGreenLight,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("Bio / Status", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaGreenLight,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = userToEdit
                        if (target != null && editDisplayName.isNotBlank()) {
                            val idx = adminUsers.indexOfFirst { it.id == target.id }
                            if (idx != -1) {
                                adminUsers[idx] = adminUsers[idx].copy(displayName = editDisplayName.trim())
                            }
                            coroutineScope.launch {
                                UserApiClient.updateUser(target.id, editDisplayName.trim(), editBio.trim())
                            }
                            Toast.makeText(context, "User profile updated in DB!", Toast.LENGTH_SHORT).show()
                            userToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaGreenPrimary)
                ) {
                    Text("SAVE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToEdit = null }) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Confirm User Deletion", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete user @${userToDelete?.username}? This action cannot be undone.", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = {
                        val target = userToDelete
                        if (target != null) {
                            adminUsers.removeIf { it.id == target.id }
                            coroutineScope.launch {
                                UserApiClient.deleteUser(target.id)
                            }
                            Toast.makeText(context, "User @${target.username} deleted from SQLite DB!", Toast.LENGTH_SHORT).show()
                            userToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("DELETE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        item {
            Text(
                "SYSTEM TELEMETRY & METRICS",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminStatCard("Total Users", "${adminUsers.size}", WaGreenLight, modifier = Modifier.weight(1f))
                AdminStatCard("Active WS", "342", Color(0xFF38BDF8), modifier = Modifier.weight(1f))
                AdminStatCard("Agora Calls", "18", Color(0xFFF43F5E), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "GLOBAL SYSTEM BROADCAST",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = broadcastMessage,
                        onValueChange = { broadcastMessage = it },
                        placeholder = { Text("Broadcast announcement to all VibeSync users...", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaGreenLight,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (broadcastMessage.isNotBlank()) {
                                broadcastSentNotice = true
                                broadcastMessage = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaGreenPrimary),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Broadcast", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    if (broadcastSentNotice) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("✅ Announcement broadcasted to 342 active clients!", color = WaGreenLight, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "USER MODERATION & DIRECTORY",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Button(
                    onClick = { showCreateUserDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = WaGreenPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CREATE USER", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter admin users by username...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WaGreenLight,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(filteredUsers) { user ->
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (user.isBanned) Color(0xFFEF4444) else WaGreenPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(user.displayName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            text = "@${user.username} • ${if (user.isBanned) "BANNED" else "ACTIVE"}",
                            color = if (user.isBanned) Color(0xFFFCA5A5) else WaGreenLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(
                        onClick = {
                            editDisplayName = user.displayName
                            editBio = ""
                            userToEdit = user
                        }
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit User", tint = WaGreenLight)
                    }

                    IconButton(
                        onClick = { userToDelete = user }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete User", tint = Color(0xFFEF4444))
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = {
                            val index = adminUsers.indexOfFirst { it.id == user.id }
                            if (index != -1) {
                                adminUsers[index] = adminUsers[index].copy(isBanned = !adminUsers[index].isBanned)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (user.isBanned) Color(0xFF22C55E) else Color(0xFFEF4444)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(if (user.isBanned) "UNBAN" else "BAN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminStatCard(title: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = accentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(title, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
    }
}

enum class SubSettingsRoute {
    ACCOUNT, PRIVACY, CHATS, NOTIFICATIONS, STORAGE, LANGUAGE, HELP
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showSwitchUserDialog by remember { mutableStateOf(false) }
    var availableUsers by remember { mutableStateOf<List<UserUI>>(emptyList()) }
    var currentUser by remember { mutableStateOf(UserUI("me", "current_user", "Current User", "Available | Powered by VibeSync")) }
    var userDisplayName by remember { mutableStateOf(currentUser.displayName) }
    var userBio by remember { mutableStateOf(currentUser.bio) }
    var activeSubSettings by remember { mutableStateOf<SubSettingsRoute?>(null) }

    LaunchedEffect(Unit) {
        try {
            val all = UserApiClient.fetchAllUsers(context)
            if (all.isNotEmpty()) {
                availableUsers = all
                currentUser = all.first()
                userDisplayName = currentUser.displayName
                userBio = currentUser.bio
            }
        } catch (_: Exception) {}
    }

    val route = activeSubSettings
    if (route != null) {
        when (route) {
            SubSettingsRoute.ACCOUNT -> SubSettingsPage(title = "Account", onBack = { activeSubSettings = null }) {
                SubSettingsItem("Security notifications", "Get notified when your security code changes")
                SubSettingsItem("Passkeys", "A simple way to sign in safely")
                SubSettingsItem("Email address", "Add an email address to protect your account")
                SubSettingsItem("Two-step verification", "For extra security, turn on two-step verification")
                SubSettingsItem("Change number", "Transfer your account info, groups & settings")
                SubSettingsItem("Request account info", "Get a report of your VibeSync account information")
                SubSettingsItem("Delete account", "Delete your account and clear all message data", isDestructive = true)
            }
            SubSettingsRoute.PRIVACY -> SubSettingsPage(title = "Privacy", onBack = { activeSubSettings = null }) {
                SubSettingsItem("Last seen and online", "Everyone")
                SubSettingsItem("Profile photo", "Everyone")
                SubSettingsItem("About", "Everyone")
                SubSettingsItem("Status", "My contacts")
                SubSettingsItem("Read receipts", "If turned off, you won't send or receive Read receipts", isSwitch = true)
                SubSettingsItem("Disappearing messages", "Default message timer: Off")
                SubSettingsItem("Groups", "Everyone")
                SubSettingsItem("Live location", "None")
                SubSettingsItem("Blocked contacts", "2 contacts blocked")
                SubSettingsItem("Fingerprint lock", "Disabled")
            }
            SubSettingsRoute.CHATS -> SubSettingsPage(title = "Chats", onBack = { activeSubSettings = null }) {
                SubSettingsItem("Theme", "System default")
                SubSettingsItem("Wallpaper", "Change chat background wallpaper")
                SubSettingsItem("Enter is send", "Enter key will send your message", isSwitch = true)
                SubSettingsItem("Media visibility", "Show newly downloaded media in your phone's gallery", isSwitch = true)
                SubSettingsItem("Font size", "Medium")
                SubSettingsItem("Archived chats", "Keep chats archived when new messages arrive", isSwitch = true)
                SubSettingsItem("Chat backup", "Last backup: Today, 03:45 AM")
                SubSettingsItem("Chat history", "Export, archive, or clear all chats")
            }
            SubSettingsRoute.NOTIFICATIONS -> SubSettingsPage(title = "Notifications", onBack = { activeSubSettings = null }) {
                SubSettingsItem("Conversation tones", "Play sounds for incoming and outgoing messages", isSwitch = true)
                SubSettingsItem("Notification tone", "Default (Whistle)")
                SubSettingsItem("Vibrate", "Default")
                SubSettingsItem("Popup notification", "Not available")
                SubSettingsItem("Light", "White")
                SubSettingsItem("Use high priority notifications", "Show previews of notifications at the top of the screen", isSwitch = true)
                SubSettingsItem("Reaction notifications", "Show notifications for reactions to messages you send", isSwitch = true)
            }
            SubSettingsRoute.STORAGE -> SubSettingsPage(title = "Storage and data", onBack = { activeSubSettings = null }) {
                SubSettingsItem("Manage storage", "1.2 GB used of 64 GB")
                SubSettingsItem("Network usage", "Bytes sent: 45 MB • Bytes received: 120 MB")
                SubSettingsItem("Use less data for calls", "Reduce mobile data usage during audio calls", isSwitch = true)
                SubSettingsItem("When using mobile data", "Photos only")
                SubSettingsItem("When connected on Wi-Fi", "All media")
                SubSettingsItem("When roaming", "No media")
                SubSettingsItem("Photo upload quality", "Auto (recommended)")
            }
            SubSettingsRoute.LANGUAGE -> SubSettingsPage(title = "App language", onBack = { activeSubSettings = null }) {
                var selectedLanguage by remember { mutableStateOf("English (device's language)") }
                val languages = listOf(
                    "English (device's language)",
                    "Urdu (اردو)",
                    "Hindi (हिंदी)",
                    "Spanish (Español)",
                    "Arabic (العربية)",
                    "French (Français)",
                    "German (Deutsch)"
                )
                languages.forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedLanguage = lang
                                Toast.makeText(context, "Language set to $lang", Toast.LENGTH_SHORT).show()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (lang == selectedLanguage),
                            onClick = {
                                selectedLanguage = lang
                                Toast.makeText(context, "Language set to $lang", Toast.LENGTH_SHORT).show()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = WaGreenPrimary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(lang, fontSize = 16.sp, color = Color(0xFF0F172A), fontWeight = if (lang == selectedLanguage) FontWeight.Bold else FontWeight.Normal)
                    }
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                }
            }
            SubSettingsRoute.HELP -> SubSettingsPage(title = "Help & App Info", onBack = { activeSubSettings = null }) {
                SubSettingsItem("Help center", "Get help, contact us, privacy policy")
                SubSettingsItem("Terms and Privacy Policy", "Read terms and conditions")
                SubSettingsItem("Channel reports", "Check status of channel reports")
                SubSettingsItem("App info", "VibeSync Build v2.4.0 (Signal Protocol E2E Encrypted)")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .verticalScroll(rememberScrollState())
    ) {
        // User Profile Header Card
        Surface(
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEditProfileDialog = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(WaGreenPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(userDisplayName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(userBio, fontSize = 13.sp, color = Color(0xFF64748B))
                    Text("@${currentUser.username}", fontSize = 12.sp, color = WaGreenPrimary, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = { showSwitchUserDialog = true }) {
                    Icon(Icons.Default.SwitchAccount, contentDescription = "Switch Account / User", tint = WaGreenPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Settings Items List
        Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsItem(
                    icon = Icons.Default.Key,
                    title = "Account",
                    subtitle = "Security notifications, change number",
                    onClick = { activeSubSettings = SubSettingsRoute.ACCOUNT }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = Color(0xFFE2E8F0))

                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "Privacy",
                    subtitle = "Block contacts, disappearing messages",
                    onClick = { activeSubSettings = SubSettingsRoute.PRIVACY }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = Color(0xFFE2E8F0))

                SettingsItem(
                    icon = Icons.Default.Chat,
                    title = "Chats",
                    subtitle = "Theme, wallpapers, chat history",
                    onClick = { activeSubSettings = SubSettingsRoute.CHATS }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = Color(0xFFE2E8F0))

                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Message, group & call tones",
                    onClick = { activeSubSettings = SubSettingsRoute.NOTIFICATIONS }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = Color(0xFFE2E8F0))

                SettingsItem(
                    icon = Icons.Default.DataSaverOn,
                    title = "Storage and data",
                    subtitle = "Network usage, auto-download",
                    onClick = { activeSubSettings = SubSettingsRoute.STORAGE }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = Color(0xFFE2E8F0))

                SettingsItem(
                    icon = Icons.Default.Language,
                    title = "App language",
                    subtitle = "English (device's language)",
                    onClick = { activeSubSettings = SubSettingsRoute.LANGUAGE }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = Color(0xFFE2E8F0))

                SettingsItem(
                    icon = Icons.Default.HelpOutline,
                    title = "Help & App Info",
                    subtitle = "VibeSync v2.4.0 • E2E Encryption Active",
                    onClick = { activeSubSettings = SubSettingsRoute.HELP }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("from", fontSize = 11.sp, color = Color(0xFF94A3B8))
            Text("VIBESYNC PLATFORM", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WaGreenPrimary, letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = userDisplayName,
                        onValueChange = { userDisplayName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = userBio,
                        onValueChange = { userBio = it },
                        label = { Text("About / Status") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditProfileDialog = false
                        Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaGreenPrimary)
                ) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showSwitchUserDialog) {
        var newUsernameInput by remember { mutableStateOf("") }
        var newDisplayNameInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSwitchUserDialog = false },
            title = { Text("Select Device Identity") },
            text = {
                Column {
                    Text("Choose which user profile this device operates as:", fontSize = 13.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    if (availableUsers.isNotEmpty()) {
                        availableUsers.forEach { usr ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentUser = usr
                                        userDisplayName = usr.displayName
                                        userBio = usr.bio
                                        showSwitchUserDialog = false
                                        Toast.makeText(context, "Device assigned to @${usr.username}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (currentUser.id == usr.id),
                                    onClick = {
                                        currentUser = usr
                                        userDisplayName = usr.displayName
                                        userBio = usr.bio
                                        showSwitchUserDialog = false
                                        Toast.makeText(context, "Device assigned to @${usr.username}", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = WaGreenPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(usr.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("@${usr.username}", fontSize = 12.sp, color = WaGreenPrimary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Or Register New Device @username:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newUsernameInput,
                        onValueChange = { newUsernameInput = it },
                        label = { Text("New @username (e.g. phone2_user)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newDisplayNameInput,
                        onValueChange = { newDisplayNameInput = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUsernameInput.isNotBlank()) {
                            val uName = newUsernameInput.trim().replace("@", "")
                            val dName = if (newDisplayNameInput.isNotBlank()) newDisplayNameInput.trim() else uName
                            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val url = URL("${NetworkConfig.BASE_HTTP_URL}/api/users/register")
                                    val conn = (url.openConnection() as HttpURLConnection).apply {
                                        requestMethod = "POST"
                                        setRequestProperty("Content-Type", "application/json")
                                        doOutput = true
                                    }
                                    val json = """{"username": "$uName", "display_name": "$dName", "bio": "Available | Powered by VibeSync"}"""
                                    conn.outputStream.write(json.toByteArray(Charsets.UTF_8))
                                    if (conn.responseCode == 200) {
                                        val text = conn.inputStream.bufferedReader().readText()
                                        val obj = JSONObject(text)
                                        val newUser = UserUI(
                                            id = obj.optString("id", UUID.randomUUID().toString()),
                                            username = obj.optString("username", uName),
                                            displayName = obj.optString("display_name", dName),
                                            bio = obj.optString("bio")
                                        )
                                        withContext(Dispatchers.Main) {
                                            currentUser = newUser
                                            userDisplayName = newUser.displayName
                                            userBio = newUser.bio
                                            showSwitchUserDialog = false
                                            Toast.makeText(context, "Registered & assigned to @$uName", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaGreenPrimary)
                ) {
                    Text("REGISTER DEVICE USER")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchUserDialog = false }) {
                    Text("CLOSE")
                }
            }
        )
    }
}

@Composable
fun SubSettingsPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
    ) {
        Surface(color = WaGreenDark, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(content = content)
            }
        }
    }
}

@Composable
fun SubSettingsItem(
    title: String,
    subtitle: String,
    isSwitch: Boolean = false,
    isDestructive: Boolean = false
) {
    val context = LocalContext.current
    var switchState by remember { mutableStateOf(true) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSwitch) {
                    switchState = !switchState
                } else {
                    Toast.makeText(context, "$title clicked", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDestructive) Color(0xFFEF4444) else Color(0xFF0F172A)
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF64748B))
            }
        }
        if (isSwitch) {
            Switch(
                checked = switchState,
                onCheckedChange = { switchState = it },
                colors = SwitchDefaults.colors(checkedThumbColor = WaGreenPrimary, checkedTrackColor = WaGreenLight)
            )
        }
    }
    HorizontalDivider(color = Color(0xFFE2E8F0))
}


@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF64748B))
        }
    }
}

@Composable
fun SignupScreen(onSignupComplete: (String) -> Unit) {
    val context = LocalContext.current
    var isSignInMode by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var activeServerUrl by remember { mutableStateOf(NetworkConfig.getBaseUrl()) }
    val coroutineScope = rememberCoroutineScope()

    if (showServerDialog) {
        com.whatsapp.clone.config.ServerConfigDialog(
            onDismiss = {
                showServerDialog = false
                activeServerUrl = NetworkConfig.getBaseUrl()
            },
            onConfigChanged = {
                activeServerUrl = NetworkConfig.getBaseUrl()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Server Configuration Bar
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier
                        .clickable { showServerDialog = true }
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Server: ${activeServerUrl.replace("http://", "")}",
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Server Settings",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(WaGreenPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSignInMode) Icons.Default.Lock else Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Welcome to VibeSync",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (isSignInMode) "Sign in to connect existing account" else "Create your account linked to this device",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text(if (isSignInMode) "Email Address or Username" else "Email Address") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF0F172A),
                        unfocusedTextColor = Color(0xFF0F172A),
                        focusedBorderColor = WaGreenPrimary,
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedLabelColor = WaGreenPrimary,
                        unfocusedLabelColor = Color(0xFF64748B)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF0F172A),
                        unfocusedTextColor = Color(0xFF0F172A),
                        focusedBorderColor = WaGreenPrimary,
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedLabelColor = WaGreenPrimary,
                        unfocusedLabelColor = Color(0xFF64748B)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                if (!isSignInMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text("Username (Optional)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 16.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A),
                            focusedBorderColor = WaGreenPrimary,
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedLabelColor = WaGreenPrimary,
                            unfocusedLabelColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (emailInput.isBlank() || passwordInput.isBlank()) {
                            Toast.makeText(context, "Email/Username and Password are required", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        coroutineScope.launch {
                            val deviceId = android.provider.Settings.Secure.getString(
                                context.contentResolver,
                                android.provider.Settings.Secure.ANDROID_ID
                            ) ?: "emulator_device_id"
                            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                            val uName = if (usernameInput.isNotBlank()) usernameInput.trim().replace("@", "") else emailInput.trim().split("@")[0]

                            val errorMsg = UserApiClient.signupDeviceWithBackend(
                                context = context,
                                deviceId = deviceId,
                                deviceModel = deviceModel,
                                email = emailInput.trim(),
                                pass = passwordInput.trim(),
                                username = uName
                            )
                            isLoading = false
                            if (errorMsg == null) {
                                val msg = if (isSignInMode) "Signed in and device linked!" else "Account registered successfully!"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onSignupComplete(uName)
                            } else {
                                Toast.makeText(context, "Auth failed: $errorMsg", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaGreenPrimary),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            if (isSignInMode) "SIGN IN & CONNECT DEVICE" else "CREATE ACCOUNT & CONTINUE",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { isSignInMode = !isSignInMode }
                ) {
                    Text(
                        if (isSignInMode) "Need a new account? Register here" else "Already have an account? Sign In",
                        color = WaGreenPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

