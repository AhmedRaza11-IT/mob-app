package com.vibesync.admin.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
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
import com.vibesync.admin.model.AdminChatMessage
import com.vibesync.admin.network.AdminApiClient
import com.vibesync.admin.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminChatSheet(
    targetUsername: String,
    targetDisplayName: String,
    onClose: () -> Unit,
    onStartCall: (isVideo: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<AdminChatMessage>>(emptyList()) }
    var inputMessage by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    var adminAlias by remember { mutableStateOf("Admin") }
    var showEditAliasDialog by remember { mutableStateOf(false) }
    var aliasInput by remember { mutableStateOf("") }

    // Fetch custom admin alias for this user
    LaunchedEffect(targetUsername) {
        val currentAlias = AdminApiClient.getAdminAlias(targetUsername)
        adminAlias = currentAlias
        aliasInput = currentAlias
    }

    // Poll messages every 3 seconds matching web dashboard
    LaunchedEffect(targetUsername) {
        while (true) {
            try {
                val msgs = AdminApiClient.fetchMessages(targetUsername)
                messages = msgs
            } catch (_: Exception) {}
            delay(3000)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Slate300, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            // Header: Target Info + Call Buttons + Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BrandPurpleSurface)
                            .border(1.dp, BrandPurpleLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = targetUsername.take(1).uppercase(),
                            color = BrandPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = targetDisplayName,
                            color = Slate900,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "@$targetUsername",
                            color = BrandPurple,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onStartCall(false) }) {
                        Icon(Icons.Default.Phone, contentDescription = "Voice Call", tint = EmeraldSuccess)
                    }
                    IconButton(onClick = { onStartCall(true) }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = SkyInfo)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate500)
                    }
                }
            }

            HorizontalDivider(color = Slate100)

            // Admin Identity Customization Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandPurpleSurface)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Contacting as: ",
                        color = Slate600,
                        fontSize = 12.sp
                    )
                    Text(
                        text = adminAlias,
                        color = BrandPurple,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                TextButton(
                    onClick = {
                        aliasInput = adminAlias
                        showEditAliasDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Name",
                        tint = BrandPurple,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Change",
                        color = BrandPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = Slate200)

            // Message Bubble History
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Slate50)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No messages yet. Send a direct message to @$targetUsername.",
                                color = Slate400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    items(messages) { msg ->
                        val isAdmin = msg.senderId.equals("admin", ignoreCase = true)
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isAdmin) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isAdmin) BrandPurple else Color.White
                                ),
                                border = if (isAdmin) null else CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate200)),
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = msg.content,
                                        color = if (isAdmin) Color.White else Slate900,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.align(Alignment.End),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isAdmin) "Admin • ${msg.status}" else "@$targetUsername",
                                            color = if (isAdmin) BrandPurpleLight.copy(alpha = 0.9f) else Slate400,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Slate200)

            // Bottom Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = { Text("Type message as $adminAlias...", color = Slate400, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPurple,
                        unfocusedBorderColor = Slate200,
                        focusedTextColor = Slate900,
                        unfocusedTextColor = Slate900,
                        focusedContainerColor = Slate50,
                        unfocusedContainerColor = Slate50
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        val text = inputMessage.trim()
                        if (text.isNotBlank()) {
                            scope.launch {
                                isSending = true
                                try {
                                    AdminApiClient.sendMessage(targetUsername, text, adminAlias)
                                    inputMessage = ""
                                    val updated = AdminApiClient.fetchMessages(targetUsername)
                                    messages = updated
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Send error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSending = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(BrandPurple, CircleShape),
                    enabled = !isSending && inputMessage.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // Change Admin Username Dialog
    if (showEditAliasDialog) {
        AlertDialog(
            onDismissRequest = { showEditAliasDialog = false },
            title = {
                Text(
                    text = "Change Admin Username",
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Set the username that @$targetUsername will see for you when messaging or calling them:",
                        fontSize = 13.sp,
                        color = Slate600,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        placeholder = { Text("e.g. Support, Alex, HR...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandPurple,
                            unfocusedBorderColor = Slate300
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newAlias = aliasInput.trim().ifBlank { "Admin" }
                        scope.launch {
                            try {
                                AdminApiClient.setAdminAlias(targetUsername, newAlias)
                                adminAlias = newAlias
                                showEditAliasDialog = false
                                Toast.makeText(context, "Username for @$targetUsername updated to '$newAlias'", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditAliasDialog = false }) {
                    Text("Cancel", color = Slate500)
                }
            }
        )
    }
}
