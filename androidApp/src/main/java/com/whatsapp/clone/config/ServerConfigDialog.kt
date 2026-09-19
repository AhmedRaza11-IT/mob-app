package com.whatsapp.clone.config

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

@Composable
fun ServerConfigDialog(
    onDismiss: () -> Unit,
    onConfigChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var currentUrl by remember { mutableStateOf(NetworkConfig.getBaseUrl()) }
    var inputUrl by remember { mutableStateOf(NetworkConfig.getCustomServerUrl(context) ?: NetworkConfig.getBaseUrl()) }
    var isChecking by remember { mutableStateOf(false) }
    var isOnline by remember { mutableStateOf<Boolean?>(null) }
    var statusMessage by remember { mutableStateOf("Testing connection...") }
    val candidateHosts = remember { NetworkConfig.buildCandidateHosts(context) }

    // Run initial health check
    LaunchedEffect(currentUrl) {
        isChecking = true
        val ok = NetworkConfig.checkHealth(currentUrl)
        isOnline = ok
        statusMessage = if (ok) "Connected to server (OK)" else "Server unreachable on $currentUrl"
        isChecking = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsEthernet,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Server Connection",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Configure backend IP or domain to connect this device across any Wi-Fi or USB network.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Connection Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (isOnline) {
                            true -> Color(0xFFDCFCE7)
                            false -> Color(0xFFFEE2E2)
                            else -> Color(0xFFF1F5F9)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF0F766E)
                            )
                        } else if (isOnline == true) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isOnline == true) "Online" else if (isOnline == false) "Offline" else "Checking...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isOnline == true) Color(0xFF15803D) else if (isOnline == false) Color(0xFFB91C1C) else Color(0xFF334155)
                            )
                            Text(
                                text = statusMessage,
                                fontSize = 11.sp,
                                color = if (isOnline == true) Color(0xFF166534) else if (isOnline == false) Color(0xFF991B1B) else Color(0xFF475569)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Candidate Hosts
                Text(
                    "Detected Candidate IPs (Tap to select):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(candidateHosts) { host ->
                        val isSelected = host == inputUrl || host == currentUrl
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF075E54) else Color(0xFFE2E8F0),
                            modifier = Modifier.clickable {
                                inputUrl = host
                                coroutineScope.launch {
                                    isChecking = true
                                    val ok = NetworkConfig.checkHealth(host)
                                    isOnline = ok
                                    statusMessage = if (ok) "Connected to server (OK)" else "Server unreachable on $host"
                                    isChecking = false
                                }
                            }
                        ) {
                            Text(
                                text = host.replace("http://", ""),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFF1E293B),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Host Input
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = { Text("Server Host or IP (e.g. 192.168.18.75:8000)") },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Auto-Detect Button
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isChecking = true
                            statusMessage = "Probing local network..."
                            val found = NetworkConfig.discoverServer(context)
                            currentUrl = found
                            inputUrl = found
                            val ok = NetworkConfig.checkHealth(found)
                            isOnline = ok
                            statusMessage = if (ok) "Auto-detected: $found (Connected)" else "Could not auto-detect active server"
                            isChecking = false
                            if (ok) {
                                NetworkConfig.setCustomServerUrl(context, found)
                                onConfigChanged()
                                Toast.makeText(context, "Connected to $found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isChecking
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-Detect Active Server", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions: Save & Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isChecking = true
                                val sanitized = NetworkConfig.sanitizeUrl(inputUrl)
                                val ok = NetworkConfig.checkHealth(sanitized)
                                isOnline = ok
                                isChecking = false
                                if (ok) {
                                    NetworkConfig.setCustomServerUrl(context, sanitized)
                                    currentUrl = sanitized
                                    onConfigChanged()
                                    Toast.makeText(context, "Saved & Connected: $sanitized", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    statusMessage = "Could not reach $sanitized. Please verify backend is running."
                                    Toast.makeText(context, "Server unreachable at $sanitized", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF075E54)),
                        modifier = Modifier.weight(1f),
                        enabled = !isChecking && inputUrl.isNotBlank()
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
