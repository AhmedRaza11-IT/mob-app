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
import com.whatsapp.clone.ui.settings.components.SettingsDialogItem
import com.whatsapp.clone.ui.settings.components.SettingsItem
import com.whatsapp.clone.ui.settings.components.SettingsSwitchItem
import com.whatsapp.clone.ui.settings.data.AppTheme
import com.whatsapp.clone.ui.settings.data.FontSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onBack: () -> Unit,
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats", color = Color.White, fontWeight = FontWeight.Bold) },
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

            item { SettingsCategoryHeader("Appearance") }
            item {
                SettingsDialogItem(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    currentValue = state.appTheme,
                    options = AppTheme.entries,
                    labelFor = { it.label },
                    onSelect = viewModel::setAppTheme
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Wallpaper,
                    title = "Wallpaper",
                    subtitle = "Change chat background"
                )
            }
            item {
                SettingsDialogItem(
                    icon = Icons.Default.TextFields,
                    title = "Font size",
                    currentValue = state.fontSize,
                    options = FontSize.entries,
                    labelFor = { it.label },
                    onSelect = viewModel::setFontSize
                )
            }

            item { SettingsCategoryHeader("Input") }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Keyboard,
                    title = "Enter is send",
                    subtitle = "Press Enter key to send messages",
                    checked = state.enterIsSend,
                    onCheckedChange = viewModel::setEnterIsSend
                )
            }

            item { SettingsCategoryHeader("Media") }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Image,
                    title = "Media visibility",
                    subtitle = "Show newly downloaded media in your gallery",
                    checked = state.mediaVisibility,
                    onCheckedChange = viewModel::setMediaVisibility
                )
            }

            item { SettingsCategoryHeader("Archive") }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Archive,
                    title = "Keep chats archived",
                    subtitle = "Archived chats will stay archived when you receive new messages",
                    checked = state.keepArchived,
                    onCheckedChange = viewModel::setKeepArchived
                )
            }

            item { SettingsCategoryHeader("History") }
            item {
                SettingsItem(
                    icon = Icons.Default.Backup,
                    title = "Chat backup",
                    subtitle = "Back up chats to Google Drive"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.SwapHoriz,
                    title = "Transfer chats",
                    subtitle = "Move chats to a new phone"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.History,
                    title = "Chat history",
                    subtitle = "Export or clear chat history"
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
