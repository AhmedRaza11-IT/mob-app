package com.whatsapp.clone.ui.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsapp.clone.WaGreenDark
import com.whatsapp.clone.WaGreenPrimary
import com.whatsapp.clone.ui.settings.SettingsUiState
import com.whatsapp.clone.ui.settings.SettingsViewModel
import com.whatsapp.clone.ui.settings.components.SettingsCategoryHeader
import com.whatsapp.clone.ui.settings.components.SettingsItem
import com.whatsapp.clone.ui.settings.components.SettingsSwitchItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage and Data", color = Color.White, fontWeight = FontWeight.Bold) },
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

            // ── Usage breakdown ──────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Storage Usage", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        StorageBar(label = "Photos", fraction = 0.45f, color = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.height(6.dp))
                        StorageBar(label = "Videos", fraction = 0.30f, color = Color(0xFF2196F3))
                        Spacer(modifier = Modifier.height(6.dp))
                        StorageBar(label = "Audio",  fraction = 0.15f, color = Color(0xFFFF9800))
                        Spacer(modifier = Modifier.height(6.dp))
                        StorageBar(label = "Other",  fraction = 0.10f, color = Color(0xFF9E9E9E))
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = WaGreenPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Manage Storage") }
                    }
                }
            }

            // ── Network ──────────────────────────────────────────────────────
            item { SettingsCategoryHeader("Network") }
            item {
                SettingsItem(
                    icon = Icons.Default.NetworkCheck,
                    title = "Network usage",
                    subtitle = "View data usage for each chat"
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.PhoneForwarded,
                    title = "Less data for calls",
                    subtitle = "Use less data during calls (lower quality)",
                    checked = state.lessDataCalls,
                    onCheckedChange = viewModel::setLessDataCalls
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Router,
                    title = "Proxy",
                    subtitle = "Connect through a proxy server"
                )
            }

            // ── Auto-Download: Mobile Data ────────────────────────────────────
            item { SettingsCategoryHeader("Auto-Download — Mobile Data") }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.PhotoLibrary,
                    title = "Photos",
                    checked = state.dlMobilePhotos,
                    onCheckedChange = viewModel::setDlMobilePhotos
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.AudioFile,
                    title = "Audio",
                    checked = state.dlMobileAudio,
                    onCheckedChange = viewModel::setDlMobileAudio
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.VideoFile,
                    title = "Videos",
                    checked = state.dlMobileVideo,
                    onCheckedChange = viewModel::setDlMobileVideo
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.FileCopy,
                    title = "Documents",
                    checked = state.dlMobileDocs,
                    onCheckedChange = viewModel::setDlMobileDocs
                )
            }

            // ── Auto-Download: Wi-Fi ─────────────────────────────────────────
            item { SettingsCategoryHeader("Auto-Download — Wi-Fi") }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.PhotoLibrary,
                    title = "Photos",
                    checked = state.dlWifiPhotos,
                    onCheckedChange = viewModel::setDlWifiPhotos
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.AudioFile,
                    title = "Audio",
                    checked = state.dlWifiAudio,
                    onCheckedChange = viewModel::setDlWifiAudio
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.VideoFile,
                    title = "Videos",
                    checked = state.dlWifiVideo,
                    onCheckedChange = viewModel::setDlWifiVideo
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.FileCopy,
                    title = "Documents",
                    checked = state.dlWifiDocs,
                    onCheckedChange = viewModel::setDlWifiDocs
                )
            }

            item { SettingsCategoryHeader("Upload Quality") }
            item {
                SettingsItem(
                    icon = Icons.Default.HighQuality,
                    title = "Media upload quality",
                    subtitle = "Auto (Recommended)"
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StorageBar(label: String, fraction: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = MaterialTheme.shapes.small)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, modifier = Modifier.width(70.dp), fontSize = 13.sp)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("${(fraction * 100).toInt()}%", fontSize = 12.sp, color = Color.Gray)
    }
}
