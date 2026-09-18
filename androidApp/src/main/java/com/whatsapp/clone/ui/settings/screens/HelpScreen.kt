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
import com.whatsapp.clone.ui.settings.components.SettingsCategoryHeader
import com.whatsapp.clone.ui.settings.components.SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help", color = Color.White, fontWeight = FontWeight.Bold) },
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
            item { SettingsCategoryHeader("Support") }
            item {
                SettingsItem(
                    icon = Icons.Default.HelpCenter,
                    title = "Help Center",
                    subtitle = "Browse articles and guides"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.ContactSupport,
                    title = "Contact us",
                    subtitle = "Need help? Send us a message"
                )
            }

            item { SettingsCategoryHeader("Legal") }
            item {
                SettingsItem(
                    icon = Icons.Default.Gavel,
                    title = "Terms of Service",
                    subtitle = "Read our terms and conditions"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "How we handle your data"
                )
            }

            item { SettingsCategoryHeader("About") }
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "App info",
                    subtitle = "VibeSync v2.4.0 • Build 240600"
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
