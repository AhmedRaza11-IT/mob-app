package com.whatsapp.clone.ui.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whatsapp.clone.WaGreenDark
import com.whatsapp.clone.ui.settings.SettingsUiState
import com.whatsapp.clone.ui.settings.SettingsViewModel
import com.whatsapp.clone.ui.settings.components.SettingsCategoryHeader
import com.whatsapp.clone.ui.settings.components.SettingsItem
import com.whatsapp.clone.ui.settings.components.SettingsSwitchItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WaGreenDark),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            item { SettingsCategoryHeader("Conversations") }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.MusicNote,
                    title = "Conversation tones",
                    subtitle = "Play sounds for incoming messages",
                    checked = state.notifTones,
                    onCheckedChange = viewModel::setNotifTones
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.NotificationsPaused,
                    title = "Notification tone",
                    subtitle = "Default (Ding)"
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Vibration,
                    title = "Vibrate",
                    subtitle = "Vibrate on new notifications",
                    checked = state.notifVibrate,
                    onCheckedChange = viewModel::setNotifVibrate
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Wysiwyg,
                    title = "Popup notification",
                    subtitle = "Off"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Lightbulb,
                    title = "Light",
                    subtitle = "White"
                )
            }

            item { SettingsCategoryHeader("Priority & Reactions") }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.PriorityHigh,
                    title = "High priority notifications",
                    subtitle = "Show message previews at top of screen",
                    checked = state.notifHighPriority,
                    onCheckedChange = viewModel::setNotifHighPriority
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.EmojiEmotions,
                    title = "Reaction notifications",
                    subtitle = "Get notified when someone reacts to your message",
                    checked = state.notifReaction,
                    onCheckedChange = viewModel::setNotifReaction
                )
            }

            item { SettingsCategoryHeader("Groups & Calls") }
            item {
                SettingsItem(
                    icon = Icons.Default.Group,
                    title = "Group notifications",
                    subtitle = "Default notification settings for groups"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.RingVolume,
                    title = "Call ringtone",
                    subtitle = "Default ringtone"
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
