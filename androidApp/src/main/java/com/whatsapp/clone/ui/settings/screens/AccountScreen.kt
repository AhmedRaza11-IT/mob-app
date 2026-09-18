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
import com.whatsapp.clone.WaGreenDark
import com.whatsapp.clone.ui.settings.components.SettingsCategoryHeader
import com.whatsapp.clone.ui.settings.components.SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account", color = Color.White, fontWeight = FontWeight.Bold) },
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
            item { SettingsCategoryHeader("Security") }
            item {
                SettingsItem(
                    icon = Icons.Default.NotificationsActive,
                    title = "Security notifications",
                    subtitle = "Get notified about security events"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Fingerprint,
                    title = "Passkeys",
                    subtitle = "Manage your passkeys"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.PhonelinkLock,
                    title = "Two-step verification",
                    subtitle = "Add a PIN for extra security"
                )
            }

            item { SettingsCategoryHeader("Account") }
            item {
                SettingsItem(
                    icon = Icons.Default.Email,
                    title = "Email",
                    subtitle = "Add a backup email address"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.PhoneAndroid,
                    title = "Change number",
                    subtitle = "Transfer account to a different number"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Download,
                    title = "Request account info",
                    subtitle = "A report of your VibeSync account info"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.PersonAdd,
                    title = "Add account",
                    subtitle = "Switch between multiple accounts"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "Delete account",
                    subtitle = "Delete your account and all data",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
