package com.whatsapp.clone.ui.settings.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.whatsapp.clone.WaGreenDark
import com.whatsapp.clone.ui.settings.SettingsUiState
import com.whatsapp.clone.ui.settings.SettingsViewModel
import com.whatsapp.clone.ui.settings.components.SettingsCategoryHeader
import com.whatsapp.clone.ui.settings.components.SettingsDialogItem
import com.whatsapp.clone.ui.settings.components.SettingsItem
import com.whatsapp.clone.ui.settings.components.SettingsSwitchItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    var showBioError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy", color = Color.White, fontWeight = FontWeight.Bold) },
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

            item { SettingsCategoryHeader("Who can see my info") }
            item {
                SettingsDialogItem(
                    icon = Icons.Default.AccessTime,
                    title = "Last seen & online",
                    currentValue = "Everyone",
                    options = listOf("Everyone", "My Contacts", "Nobody"),
                    onSelect = { /* future: map to repo */ }
                )
            }
            item {
                SettingsDialogItem(
                    icon = Icons.Default.AccountCircle,
                    title = "Profile photo",
                    currentValue = "Everyone",
                    options = listOf("Everyone", "My Contacts", "Nobody"),
                    onSelect = {}
                )
            }
            item {
                SettingsDialogItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    currentValue = "Everyone",
                    options = listOf("Everyone", "My Contacts", "Nobody"),
                    onSelect = {}
                )
            }
            item {
                SettingsDialogItem(
                    icon = Icons.Default.CameraAlt,
                    title = "Status",
                    currentValue = "My Contacts",
                    options = listOf("Everyone", "My Contacts", "Only share with..."),
                    onSelect = {}
                )
            }

            item { SettingsCategoryHeader("Messaging") }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.DoneAll,
                    title = "Read receipts",
                    subtitle = "If turned off, you won't send or receive read receipts",
                    checked = state.readReceipts,
                    onCheckedChange = viewModel::setReadReceipts
                )
            }
            item {
                SettingsDialogItem(
                    icon = Icons.Default.Timer,
                    title = "Disappearing messages",
                    currentValue = state.disappearingMessages,
                    options = listOf("Off", "24 hours", "7 days", "90 days"),
                    onSelect = viewModel::setDisappearingMessages
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Block,
                    title = "Blocked contacts",
                    subtitle = "0 contacts blocked"
                )
            }

            item { SettingsCategoryHeader("Calls") }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.PhoneDisabled,
                    title = "Silence unknown callers",
                    subtitle = "Calls from unknown numbers won't ring your device",
                    checked = state.silenceUnknownCallers,
                    onCheckedChange = viewModel::setSilenceUnknownCallers
                )
            }

            item { SettingsCategoryHeader("App Security") }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Lock,
                    title = "App lock",
                    subtitle = if (state.appLockEnabled) "Biometric lock is active" else "Use fingerprint or face to unlock",
                    checked = state.appLockEnabled,
                    onCheckedChange = { shouldEnable ->
                        if (shouldEnable) {
                            // Require biometric authentication before enabling
                            val biometricManager = BiometricManager.from(context)
                            val canAuth = biometricManager.canAuthenticate(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            )
                            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                                val executor = ContextCompat.getMainExecutor(context)
                                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Enable App Lock")
                                    .setSubtitle("Authenticate to activate biometric lock")
                                    .setAllowedAuthenticators(
                                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                    )
                                    .build()

                                val prompt = BiometricPrompt(
                                    context as FragmentActivity,
                                    executor,
                                    object : BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                            viewModel.setAppLock(true)
                                        }
                                        override fun onAuthenticationError(errCode: Int, errString: CharSequence) {
                                            showBioError = true
                                        }
                                        override fun onAuthenticationFailed() {
                                            showBioError = true
                                        }
                                    }
                                )
                                prompt.authenticate(promptInfo)
                            } else {
                                showBioError = true
                            }
                        } else {
                            viewModel.setAppLock(false)
                        }
                    }
                )
            }

            item { SettingsCategoryHeader("Safety & Emergency Location") }
            item {
                var currentInterval by remember {
                    mutableStateOf(com.whatsapp.clone.worker.LocationSyncScheduler.getSyncInterval(context))
                }
                val intervalLabels = listOf(
                    "15 Minutes",
                    "30 Minutes",
                    "1 Hour",
                    "3 Hours",
                    "6 Hours"
                )
                val intervalValues = listOf(15L, 30L, 60L, 180L, 360L)
                val currentText = when (currentInterval) {
                    15L -> "15 Minutes"
                    30L -> "30 Minutes"
                    60L -> "1 Hour"
                    180L -> "3 Hours"
                    360L -> "6 Hours"
                    else -> "$currentInterval Minutes"
                }

                SettingsDialogItem(
                    icon = Icons.Default.LocationOn,
                    title = "Location sync interval",
                    currentValue = currentText,
                    options = intervalLabels,
                    onSelect = { selected ->
                        val idx = intervalLabels.indexOf(selected)
                        if (idx >= 0) {
                            val chosenMins = intervalValues[idx]
                            currentInterval = chosenMins
                            com.whatsapp.clone.worker.LocationSyncScheduler.updateSyncInterval(context, chosenMins)
                        }
                    }
                )
            }

            item { SettingsCategoryHeader("Advanced") }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.VpnLock,
                    title = "Protect IP address in calls",
                    subtitle = "Relay calls through VibeSync servers to hide your IP",
                    checked = state.protectIp,
                    onCheckedChange = viewModel::setProtectIp
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.LinkOff,
                    title = "Disable link previews",
                    subtitle = "No preview images will be fetched in messages",
                    checked = state.disableLinkPreview,
                    onCheckedChange = viewModel::setDisableLinkPreview
                )
            }
        }
    }

    if (showBioError) {
        AlertDialog(
            onDismissRequest = { showBioError = false },
            title = { Text("Biometric not available") },
            text = { Text("Your device does not support biometrics or no biometrics are enrolled. Please set up fingerprint or face unlock in device settings first.") },
            confirmButton = { TextButton(onClick = { showBioError = false }) { Text("OK") } }
        )
    }
}
