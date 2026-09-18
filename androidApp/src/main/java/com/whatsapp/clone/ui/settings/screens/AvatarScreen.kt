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
fun AvatarScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Avatar", color = Color.White, fontWeight = FontWeight.Bold) },
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
            item { SettingsCategoryHeader("Your Avatar") }
            item {
                SettingsItem(
                    icon = Icons.Default.Edit,
                    title = "Edit avatar",
                    subtitle = "Customize your avatar appearance"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.EmojiEmotions,
                    title = "Browse avatar stickers",
                    subtitle = "Use your avatar as stickers in chats"
                )
            }

            item { SettingsCategoryHeader("Profile Photo") }
            item {
                SettingsItem(
                    icon = Icons.Default.AddAPhoto,
                    title = "Create profile photo",
                    subtitle = "Generate a profile photo from your avatar"
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "Delete avatar",
                    subtitle = "Remove your avatar permanently",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
