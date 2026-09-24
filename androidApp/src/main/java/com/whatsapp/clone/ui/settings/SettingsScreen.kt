package com.whatsapp.clone.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsapp.clone.WaGreenDark
import com.whatsapp.clone.WaGreenPrimary
import com.whatsapp.clone.ui.settings.components.SettingsCategoryHeader
import com.whatsapp.clone.ui.settings.components.SettingsItem
import com.whatsapp.clone.ui.settings.navigation.SettingsRoute

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.platform.LocalContext
import com.whatsapp.clone.UserApiClient
import com.whatsapp.clone.ui.components.UserAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (SettingsRoute) -> Unit,
    username: String = "VibeSync User"
) {
    val context = LocalContext.current
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf(username) }

    androidx.compose.runtime.LaunchedEffect(username) {
        if (username.isNotBlank() && username != "Current User") {
            val profile = withContext(Dispatchers.IO) {
                UserApiClient.fetchUserProfile(username, context)
            }
            if (profile != null) {
                avatarUrl = profile.avatarUrl
                displayName = profile.displayName.ifBlank { username }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WaGreenDark),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Profile Header ────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(SettingsRoute.Avatar) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(
                        avatarUrl = avatarUrl,
                        displayName = displayName,
                        modifier = Modifier.size(72.dp),
                        fallbackBackgroundColor = WaGreenPrimary,
                        fallbackTextColor = Color.White,
                        fontSize = 28.sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            displayName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap to view or change profile photo",
                            fontSize = 13.sp,
                            color = WaGreenPrimary
                        )
                    }
                    IconButton(onClick = { onNavigate(SettingsRoute.Avatar) }) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Edit Profile Photo",
                            tint = WaGreenPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                HorizontalDivider()
            }

            // ── Account ───────────────────────────────────────────────────────
            item { SettingsCategoryHeader("Account") }
            item {
                SettingsItem(
                    icon = Icons.Default.Key,
                    title = "Account",
                    subtitle = "Security notifications, email, passkeys",
                    onClick = { onNavigate(SettingsRoute.Account) }
                )
            }

            // ── Privacy ───────────────────────────────────────────────────────
            item { SettingsCategoryHeader("Privacy") }
            item {
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "Privacy",
                    subtitle = "Last seen, blocked contacts, read receipts",
                    onClick = { onNavigate(SettingsRoute.Privacy) }
                )
            }

            // ── Avatar ────────────────────────────────────────────────────────
            item {
                SettingsItem(
                    icon = Icons.Default.AccountCircle,
                    title = "Profile Photo & Avatar",
                    subtitle = "View, change, or remove profile photo",
                    onClick = { onNavigate(SettingsRoute.Avatar) }
                )
            }

            // ── Chats ─────────────────────────────────────────────────────────
            item { SettingsCategoryHeader("Chats & Appearance") }
            item {
                SettingsItem(
                    icon = Icons.Default.ChatBubbleOutline,
                    title = "Chats",
                    subtitle = "Theme, wallpaper, chat history",
                    onClick = { onNavigate(SettingsRoute.Chats) }
                )
            }

            // ── Notifications ─────────────────────────────────────────────────
            item {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Message, group & call tones",
                    onClick = { onNavigate(SettingsRoute.Notifications) }
                )
            }

            // ── Storage ───────────────────────────────────────────────────────
            item { SettingsCategoryHeader("Data") }
            item {
                SettingsItem(
                    icon = Icons.Default.Storage,
                    title = "Storage and Data",
                    subtitle = "Media auto-download, network usage",
                    onClick = { onNavigate(SettingsRoute.Storage) }
                )
            }

            // ── Language ──────────────────────────────────────────────────────
            item {
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = "App Language",
                    subtitle = "English",
                    onClick = { onNavigate(SettingsRoute.Language) }
                )
            }

            // ── Help ──────────────────────────────────────────────────────────
            item { SettingsCategoryHeader("Support") }
            item {
                SettingsItem(
                    icon = Icons.Default.HelpOutline,
                    title = "Help",
                    subtitle = "Help center, contact us, privacy policy",
                    onClick = { onNavigate(SettingsRoute.Help) }
                )
            }

            // ── Invite ────────────────────────────────────────────────────────
            item {
                SettingsItem(
                    icon = Icons.Default.Share,
                    title = "Invite a Friend",
                    subtitle = "Share VibeSync with your contacts",
                    onClick = { /* TODO: launch share intent */ }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
