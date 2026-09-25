package com.vibesync.admin.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibesync.admin.R
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

    var isCreateAccount by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(AdminNetworkConfig.DEFAULT_ADB_URL) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val detected = AdminNetworkConfig.autoDetectWorkingHost(context)
        serverUrl = detected
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // VibeSync Logo
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF6C3AEB)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "VibeSync Logo",
                        modifier = Modifier.size(68.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isCreateAccount) "Create Admin Account" else "VibeSync Admin",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )

                Text(
                    text = if (isCreateAccount) "Register new administrative credentials" else "Fleet Management & Remote Control",
                    fontSize = 12.sp,
                    color = Slate500,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isCreateAccount) {
                    // Username / Name Input
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Admin Name", color = Slate500, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Slate400)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandPurple,
                            unfocusedBorderColor = Slate200,
                            focusedTextColor = Slate900,
                            unfocusedTextColor = Slate900,
                            focusedContainerColor = Slate50,
                            unfocusedContainerColor = Slate50
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Email Input
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Admin Email", color = Slate500, fontSize = 12.sp) },
                    placeholder = { Text("admin@vibesync.com", color = Slate400, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null, tint = Slate400)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPurple,
                        unfocusedBorderColor = Slate200,
                        focusedTextColor = Slate900,
                        unfocusedTextColor = Slate900,
                        focusedContainerColor = Slate50,
                        unfocusedContainerColor = Slate50
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Password Input
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (isCreateAccount) "Create Password" else "Admin Password", color = Slate500, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Slate400)
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPurple,
                        unfocusedBorderColor = Slate200,
                        focusedTextColor = Slate900,
                        unfocusedTextColor = Slate900,
                        focusedContainerColor = Slate50,
                        unfocusedContainerColor = Slate50
                    ),
                    singleLine = true
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = RedDelete,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Submit Button
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                AdminNetworkConfig.saveServerUrl(context, serverUrl)
                                val trimmedEmail = email.trim()
                                val trimmedPassword = password.trim()
                                if (trimmedPassword.isEmpty()) {
                                    errorMessage = "Please enter a password"
                                    return@launch
                                }
                                val success = if (isCreateAccount) {
                                    if (trimmedEmail.isEmpty()) {
                                        errorMessage = "Please enter an email address"
                                        return@launch
                                    }
                                    AdminApiClient.register(
                                        username = username.trim().ifEmpty { trimmedEmail.split("@")[0] },
                                        email = trimmedEmail,
                                        password = trimmedPassword
                                    )
                                } else {
                                    AdminApiClient.login(trimmedEmail, trimmedPassword)
                                }
                                if (success) {
                                    AdminNetworkConfig.saveAuthToken(context, "admin_session")
                                    onLoginSuccess()
                                } else {
                                    errorMessage = if (isCreateAccount) "Failed to create admin account" else "Invalid admin credentials"
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
                            text = if (isCreateAccount) "Create Account & Enter" else "Authenticate & Enter",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Create Account / Sign In Toggle
                TextButton(
                    onClick = {
                        isCreateAccount = !isCreateAccount
                        errorMessage = null
                    }
                ) {
                    Text(
                        text = if (isCreateAccount) "Already have an account? Sign In" else "Don't have an account? Create Account",
                        color = BrandPurple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
