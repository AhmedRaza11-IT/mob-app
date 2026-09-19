package com.vibesync.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibesync.admin.config.AdminNetworkConfig
import com.vibesync.admin.network.AdminApiClient
import com.vibesync.admin.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf(AdminNetworkConfig.DEFAULT_ADB_URL) }
    var password by remember { mutableStateOf("vibesync@admin2024") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hostDetectStatus by remember { mutableStateOf("Ready") }

    LaunchedEffect(Unit) {
        val detected = AdminNetworkConfig.autoDetectWorkingHost(context)
        serverUrl = detected
        hostDetectStatus = "Connected to $detected"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Admin Badge / Logo
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(BrandPurple.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Admin Shield",
                        tint = BrandPurple,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "VibeSync Admin",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Fleet Management & Remote Control",
                    fontSize = 12.sp,
                    color = Slate400,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Server URL selector (Supports ADB reverse + Wi-Fi IP)
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server Host URL", color = Slate400, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Router, contentDescription = null, tint = Slate400)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPurple,
                        unfocusedBorderColor = Slate700,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Slate950,
                        unfocusedContainerColor = Slate950
                    ),
                    singleLine = true
                )

                // Quick buttons for host selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            serverUrl = AdminNetworkConfig.DEFAULT_ADB_URL
                            AdminNetworkConfig.setBaseUrl(serverUrl)
                        },
                        label = { Text("ADB (127.0.0.1)", fontSize = 10.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (serverUrl.contains("127.0.0.1")) BrandPurple.copy(alpha = 0.2f) else Slate800,
                            labelColor = if (serverUrl.contains("127.0.0.1")) BrandPurpleLight else Slate300
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {
                            serverUrl = AdminNetworkConfig.DEFAULT_WIFI_URL
                            AdminNetworkConfig.setBaseUrl(serverUrl)
                        },
                        label = { Text("Wi-Fi (192.168.18.78)", fontSize = 10.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (serverUrl.contains("192.168")) BrandPurple.copy(alpha = 0.2f) else Slate800,
                            labelColor = if (serverUrl.contains("192.168")) BrandPurpleLight else Slate300
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Password Input
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Admin Master Password", color = Slate400, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Slate400)
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPurple,
                        unfocusedBorderColor = Slate700,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Slate950,
                        unfocusedContainerColor = Slate950
                    ),
                    singleLine = true
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = RedDelete,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Login Button
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                AdminNetworkConfig.saveServerUrl(context, serverUrl)
                                val success = AdminApiClient.login(password.trim())
                                if (success) {
                                    AdminNetworkConfig.saveAuthToken(context, "admin_session")
                                    onLoginSuccess()
                                } else {
                                    errorMessage = "Invalid admin password"
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Failed to connect to backend"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Authenticate & Enter",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = hostDetectStatus,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Slate500
                )
            }
        }
    }
}
