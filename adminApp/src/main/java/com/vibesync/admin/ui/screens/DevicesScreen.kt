package com.vibesync.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibesync.admin.model.DeviceItem
import com.vibesync.admin.model.RemoteConfig
import com.vibesync.admin.network.AdminApiClient
import com.vibesync.admin.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onOpenChat: (username: String, displayName: String) -> Unit,
    onStartCall: (username: String, isVideo: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var remoteConfig by remember { mutableStateOf<RemoteConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var editingDevice by remember { mutableStateOf<DeviceItem?>(null) }
    var sanitizingDevice by remember { mutableStateOf<DeviceItem?>(null) }
    var deprovisioningDevice by remember { mutableStateOf<DeviceItem?>(null) }
    var deletingDevice by remember { mutableStateOf<DeviceItem?>(null) }

    fun refreshAll() {
        scope.launch {
            try {
                isLoading = true
                val dev = AdminApiClient.fetchDevices()
                val cfg = AdminApiClient.fetchRemoteConfig()
                devices = dev
                remoteConfig = cfg
            } catch (e: Exception) {
                actionMessage = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Auto refresh every 15s
    LaunchedEffect(Unit) {
        refreshAll()
        while (true) {
            delay(15000)
            try {
                devices = AdminApiClient.fetchDevices()
                remoteConfig = AdminApiClient.fetchRemoteConfig()
            } catch (_: Exception) {}
        }
    }

    val filtered = devices.filter {
        searchQuery.isBlank() ||
                it.deviceId.contains(searchQuery, ignoreCase = true) ||
                it.deviceModel.contains(searchQuery, ignoreCase = true) ||
                it.username.contains(searchQuery, ignoreCase = true) ||
                (it.email ?: "").contains(searchQuery, ignoreCase = true)
    }

    val activeCount = devices.count { System.currentTimeMillis() - it.lastSyncTimestamp < 3600000 && !it.isBlocked }
    val unassignedCount = devices.count { it.username == "Current User" }
    val blockedCount = devices.count { it.isBlocked }

    Scaffold(
        containerColor = Slate950,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Device Fleet Management", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Remote identification, policies & kill switch", color = Slate400, fontSize = 11.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Slate300)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(6.dp)) }

            // 1. Enterprise Policy & OTA Control Center
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(BrandPurple.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🛡️", fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Enterprise Policy & OTA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("MDM Live Enforcement", color = BrandPurpleLight, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            AdminApiClient.broadcastOta()
                                            Toast.makeText(context, "OTA Update Broadcasted!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "OTA failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("🚀 Broadcast OTA", fontSize = 11.sp, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 3 Policy Toggles: Screenshot, Voice Calling, Maintenance
                        val cfg = remoteConfig
                        if (cfg != null) {
                            PolicyToggleRow(
                                title = "📸 Screenshot Policy",
                                subtitle = if (cfg.allowScreenshots) "Screenshots Permitted" else "FLAG_SECURE Active",
                                isChecked = cfg.allowScreenshots,
                                onCheckedChange = { newVal ->
                                    val updated = cfg.copy(allowScreenshots = newVal)
                                    remoteConfig = updated
                                    scope.launch {
                                        AdminApiClient.updateRemoteConfig(updated)
                                        Toast.makeText(context, "Screenshot policy updated", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            Divider(color = Slate800, modifier = Modifier.padding(vertical = 8.dp))

                            PolicyToggleRow(
                                title = "📞 Calling Enabled",
                                subtitle = if (cfg.voiceCallingEnabled) "Agora RTC Active" else "Calling Suspended",
                                isChecked = cfg.voiceCallingEnabled,
                                onCheckedChange = { newVal ->
                                    val updated = cfg.copy(voiceCallingEnabled = newVal)
                                    remoteConfig = updated
                                    scope.launch {
                                        AdminApiClient.updateRemoteConfig(updated)
                                        Toast.makeText(context, "Calling policy updated", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            Divider(color = Slate800, modifier = Modifier.padding(vertical = 8.dp))

                            PolicyToggleRow(
                                title = "🚧 Maintenance Mode",
                                subtitle = if (cfg.maintenanceMode) "App Locked to Splash" else "Operational",
                                isChecked = cfg.maintenanceMode,
                                onCheckedChange = { newVal ->
                                    val updated = cfg.copy(maintenanceMode = newVal)
                                    remoteConfig = updated
                                    scope.launch {
                                        AdminApiClient.updateRemoteConfig(updated)
                                        Toast.makeText(context, "Maintenance mode updated", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        } else {
                            Text("Loading remote policies...", color = Slate400, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 2. Stats Grid (4 cards)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBox(label = "Total", value = devices.size.toString(), color = BrandPurple, modifier = Modifier.weight(1f))
                    StatBox(label = "Active", value = activeCount.toString(), color = BrandTeal, modifier = Modifier.weight(1f))
                    StatBox(label = "Unassigned", value = unassignedCount.toString(), color = AmberWarning, modifier = Modifier.weight(1f))
                    StatBox(label = "Blocked", value = blockedCount.toString(), color = RedDelete, modifier = Modifier.weight(1f))
                }
            }

            // 3. Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by ANDROID_ID, model, username...", color = Slate500, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPurple,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Slate900,
                        unfocusedContainerColor = Slate900
                    ),
                    singleLine = true
                )
            }

            // 4. Device Items List
            if (isLoading && devices.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandPurple)
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No devices match your criteria", color = Slate500, fontSize = 13.sp)
                    }
                }
            } else {
                items(filtered) { dev ->
                    DeviceCard(
                        device = dev,
                        onOpenChat = { onOpenChat(dev.username, dev.deviceModel) },
                        onStartVoiceCall = { onStartCall(dev.username, false) },
                        onStartVideoCall = { onStartCall(dev.username, true) },
                        onEdit = { editingDevice = dev },
                        onSanitize = { sanitizingDevice = dev },
                        onDeprovision = { deprovisioningDevice = dev },
                        onToggleBlock = {
                            scope.launch {
                                try {
                                    AdminApiClient.toggleBlockDevice(dev.deviceId, !dev.isBlocked)
                                    refreshAll()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Block error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDelete = { deletingDevice = dev }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    // Edit Dialog
    editingDevice?.let { dev ->
        var editModel by remember { mutableStateOf(dev.deviceModel) }
        var editUsername by remember { mutableStateOf(dev.username) }
        var editEmail by remember { mutableStateOf(dev.email ?: "") }

        AlertDialog(
            onDismissRequest = { editingDevice = null },
            title = { Text("Edit Device Details", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editModel,
                        onValueChange = { editModel = it },
                        label = { Text("Model") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it },
                        label = { Text("Assigned Username") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Email") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            AdminApiClient.updateDevice(dev.deviceId, editModel, editUsername, editEmail)
                            editingDevice = null
                            refreshAll()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingDevice = null }) { Text("Cancel", color = Slate400) }
            },
            containerColor = Slate900
        )
    }

    // Sanitize Dialog
    sanitizingDevice?.let { dev ->
        AlertDialog(
            onDismissRequest = { sanitizingDevice = null },
            icon = { Text("🧹", fontSize = 28.sp) },
            title = { Text("Sanitize Device Data?", color = Color.White) },
            text = {
                Text(
                    "This will remotely wipe local SQLite database, cache, media and session tokens on ${dev.deviceModel}.",
                    color = Slate300, fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            AdminApiClient.sanitizeDevice(dev.deviceId)
                            Toast.makeText(context, "Sanitization dispatched!", Toast.LENGTH_SHORT).show()
                            sanitizingDevice = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning)
                ) { Text("Execute Sanitize", color = Color.Black) }
            },
            dismissButton = {
                TextButton(onClick = { sanitizingDevice = null }) { Text("Cancel", color = Slate400) }
            },
            containerColor = Slate900
        )
    }

    // De-provision Dialog (Kill switch)
    deprovisioningDevice?.let { dev ->
        AlertDialog(
            onDismissRequest = { deprovisioningDevice = null },
            icon = { Text("⚡", fontSize = 28.sp) },
            title = { Text("Remote De-provision & Uninstall?", color = Color.White) },
            text = {
                Text(
                    "1. Sanitizes all local app databases & credentials\n2. Invokes Android package uninstall prompt\n3. Terminates the app process immediately on ${dev.deviceModel}.",
                    color = Slate300, fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            AdminApiClient.deprovisionDevice(dev.deviceId)
                            Toast.makeText(context, "De-provisioning kill switch sent!", Toast.LENGTH_SHORT).show()
                            deprovisioningDevice = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseDanger)
                ) { Text("Trigger Kill Switch", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { deprovisioningDevice = null }) { Text("Cancel", color = Slate400) }
            },
            containerColor = Slate900
        )
    }

    // Delete Dialog
    deletingDevice?.let { dev ->
        AlertDialog(
            onDismissRequest = { deletingDevice = null },
            title = { Text("Delete Device Record?", color = Color.White) },
            text = { Text("Permanently delete record for ${dev.deviceModel}?", color = Slate300) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            AdminApiClient.deleteDevice(dev.deviceId)
                            deletingDevice = null
                            refreshAll()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedDelete)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingDevice = null }) { Text("Cancel", color = Slate400) }
            },
            containerColor = Slate900
        )
    }
}

@Composable
fun PolicyToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = Slate400, fontSize = 11.sp)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandPurple,
                uncheckedThumbColor = Slate400,
                uncheckedTrackColor = Slate800
            )
        )
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = Slate400)
        }
    }
}

@Composable
fun DeviceCard(
    device: DeviceItem,
    onOpenChat: () -> Unit,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onEdit: () -> Unit,
    onSanitize: () -> Unit,
    onDeprovision: () -> Unit,
    onToggleBlock: () -> Unit,
    onDelete: () -> Unit
) {
    val isOnline = System.currentTimeMillis() - device.lastSyncTimestamp < 3600000
    val isUnassigned = device.username == "Current User"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Model + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    device.isBlocked -> RedDelete
                                    isOnline -> EmeraldSuccess
                                    else -> Slate600
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = device.deviceModel,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (device.isBlocked) {
                    Text(
                        "🚫 Blocked",
                        color = RedDelete,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(RedDelete.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                } else if (isOnline) {
                    Text(
                        "● Active",
                        color = EmeraldSuccess,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(EmeraldSuccess.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // User + ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isUnassigned) "⚠ Unassigned" else "@${device.username}",
                    color = if (isUnassigned) AmberWarning else BrandTealLight,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = device.deviceId.take(14) + "...",
                    color = Slate500,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isUnassigned) {
                    Button(
                        onClick = onOpenChat,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple.copy(alpha = 0.25f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("💬 Chat", fontSize = 11.sp, color = BrandPurpleLight, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onStartVoiceCall,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess.copy(alpha = 0.2f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("📞 Call", fontSize = 11.sp, color = EmeraldSuccess, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onStartVideoCall,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7).copy(alpha = 0.2f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("📹 Video", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = onEdit,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("✏ Edit", fontSize = 11.sp, color = Slate300)
                }

                Button(
                    onClick = onSanitize,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning.copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("🧹 Nuke", fontSize = 11.sp, color = AmberWarning)
                }

                Button(
                    onClick = onDeprovision,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoseDanger.copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("⚡ Kill", fontSize = 11.sp, color = RoseDanger)
                }
            }
        }
    }
}
