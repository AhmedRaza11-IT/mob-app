package com.whatsapp.clone.ui.settings.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsapp.clone.UserApiClient
import com.whatsapp.clone.WaGreenDark
import com.whatsapp.clone.WaGreenPrimary
import com.whatsapp.clone.ui.components.AvatarCache
import com.whatsapp.clone.ui.components.UserAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarScreen(
    onBack: () -> Unit,
    username: String = "VibeSync User"
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf(username) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Load initial user profile to get existing avatar
    LaunchedEffect(username) {
        if (username.isNotBlank() && username != "Current User") {
            val profile = withContext(Dispatchers.IO) {
                UserApiClient.fetchUserProfile(username, context)
            }
            if (profile != null) {
                avatarUrl = profile.avatarUrl
                displayName = profile.displayName.ifBlank { username }
            }
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isLoading = true
                statusMessage = "Processing and uploading image..."
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val origBmp = BitmapFactory.decodeStream(stream) ?: return@use null
                            val maxDim = 1024
                            val width = origBmp.width
                            val height = origBmp.height
                            val scaledBmp = if (width > maxDim || height > maxDim) {
                                val ratio = if (width > height) maxDim.toFloat() / width else maxDim.toFloat() / height
                                Bitmap.createScaledBitmap(origBmp, (width * ratio).toInt(), (height * ratio).toInt(), true)
                            } else {
                                origBmp
                            }
                            val baos = ByteArrayOutputStream()
                            scaledBmp.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                            baos.toByteArray()
                        }
                    }

                    if (bytes != null && bytes.isNotEmpty()) {
                        val newAvatarUrl = withContext(Dispatchers.IO) {
                            UserApiClient.uploadAvatar(username, bytes, "image/jpeg", context)
                        }

                        if (!newAvatarUrl.isNullOrBlank()) {
                            AvatarCache.clear()
                            avatarUrl = newAvatarUrl
                            Toast.makeText(context, "Profile photo updated successfully!", Toast.LENGTH_SHORT).show()
                            statusMessage = null
                        } else {
                            Toast.makeText(context, "Failed to upload photo. Please check server.", Toast.LENGTH_SHORT).show()
                            statusMessage = "Upload failed. Please check network connection."
                        }
                    } else {
                        Toast.makeText(context, "Could not read selected photo", Toast.LENGTH_SHORT).show()
                        statusMessage = null
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    statusMessage = "Error: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile Photo", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WaGreenDark),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Profile Picture Container with Edit Badge
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.padding(16.dp)
            ) {
                UserAvatar(
                    avatarUrl = avatarUrl,
                    displayName = displayName,
                    modifier = Modifier
                        .size(150.dp)
                        .border(3.dp, WaGreenPrimary, CircleShape),
                    fallbackBackgroundColor = WaGreenPrimary,
                    fallbackTextColor = Color.White,
                    fontSize = 54.sp
                )

                // Circular Camera action button badge
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(WaGreenPrimary)
                        .clickable(enabled = !isLoading) { photoPickerLauncher.launch("image/*") }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Change Photo",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = displayName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "@$username",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    color = WaGreenPrimary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (statusMessage != null) {
                    Text(
                        text = statusMessage ?: "",
                        fontSize = 13.sp,
                        color = WaGreenPrimary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Action Buttons Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Change / Upload Photo Button
                    Button(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = WaGreenPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (avatarUrl != null) "Change Photo" else "Upload Photo",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Remove Photo Button (shown only if avatar exists)
                    if (avatarUrl != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Remove Photo",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }


            Spacer(modifier = Modifier.height(20.dp))

            // Information note
            Text(
                text = "Your profile photo is visible to all your contacts and friends on VibeSync.",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 36.dp)
            )
        }
    }

    // Confirmation dialog before removing avatar
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Remove Profile Photo?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove your profile photo? It will be replaced with your initials.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        coroutineScope.launch {
                            isLoading = true
                            statusMessage = "Removing photo..."
                            val success = withContext(Dispatchers.IO) {
                                UserApiClient.deleteAvatar(username, context)
                            }
                            if (success) {
                                AvatarCache.clear()
                                avatarUrl = null
                                Toast.makeText(context, "Profile photo removed", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to remove photo. Please try again.", Toast.LENGTH_SHORT).show()
                            }
                            isLoading = false
                            statusMessage = null
                        }
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
