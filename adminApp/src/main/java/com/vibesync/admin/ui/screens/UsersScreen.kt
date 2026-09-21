package com.vibesync.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import com.vibesync.admin.model.AdminUser
import com.vibesync.admin.network.AdminApiClient
import com.vibesync.admin.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    onOpenChat: (username: String, displayName: String) -> Unit,
    onStartCall: (username: String, isVideo: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<AdminUser?>(null) }
    var deletingUser by remember { mutableStateOf<AdminUser?>(null) }

    fun refreshUsers() {
        scope.launch {
            try {
                isLoading = true
                users = AdminApiClient.fetchUsers()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshUsers()
    }

    val filtered = users.filter { u ->
        val matchesSearch = searchQuery.isBlank() ||
                u.username.contains(searchQuery, ignoreCase = true) ||
                u.displayName.contains(searchQuery, ignoreCase = true) ||
                (u.bio ?: "").contains(searchQuery, ignoreCase = true)

        val matchesFilter = when (selectedFilter) {
            "ACTIVE" -> System.currentTimeMillis() - u.createdAt < 86400000
            "HAS_DEVICE" -> !u.deviceId.isNullOrBlank()
            "BANNED" -> u.isBanned
            else -> true
        }

        matchesSearch && matchesFilter
    }

    Scaffold(
        containerColor = Slate50,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("User Registry & Directory", color = Slate900, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Manage user profiles & communications", color = Slate500, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add User", tint = BrandPurple)
                    }
                    IconButton(onClick = { refreshUsers() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Slate600)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Stats Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBox("Total", users.size.toString(), BrandTeal, Modifier.weight(1f))
                    StatBox("Today", users.count { System.currentTimeMillis() - it.createdAt < 86400000 }.toString(), BrandPurple, Modifier.weight(1f))
                    StatBox("Devices", users.count { !it.deviceId.isNullOrBlank() }.toString(), SkyInfo, Modifier.weight(1f))
                    StatBox("Banned", users.count { it.isBanned }.toString(), RedDelete, Modifier.weight(1f))
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by @username, display name...", color = Slate400, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPurple,
                        unfocusedBorderColor = Slate200,
                        focusedTextColor = Slate900,
                        unfocusedTextColor = Slate900,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    singleLine = true
                )
            }

            // Filter Chips Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL" to "All Users", "ACTIVE" to "Active", "HAS_DEVICE" to "Device Linked", "BANNED" to "Banned").forEach { (key, label) ->
                        FilterChip(
                            selected = selectedFilter == key,
                            onClick = { selectedFilter = key },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandPurple,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = Slate600
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedFilter == key,
                                borderColor = if (selectedFilter == key) BrandPurple else Slate200
                            )
                        )
                    }
                }
            }

            // User List
            if (isLoading && users.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandPurple)
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No users found matching criteria", color = Slate400, fontSize = 13.sp)
                    }
                }
            } else {
                items(filtered) { user ->
                    UserCard(
                        user = user,
                        onOpenChat = { onOpenChat(user.username, user.displayName) },
                        onStartVoiceCall = { onStartCall(user.username, false) },
                        onStartVideoCall = { onStartCall(user.username, true) },
                        onEdit = { editingUser = user },
                        onToggleBan = {
                            scope.launch {
                                try {
                                    AdminApiClient.updateUser(user.id, user.displayName, user.bio ?: "", !user.isBanned)
                                    refreshUsers()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Ban error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDelete = { deletingUser = user }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    // Create User Dialog
    if (showCreateDialog) {
        var newUsername by remember { mutableStateOf("") }
        var newDisplayName by remember { mutableStateOf("") }
        var newBio by remember { mutableStateOf("Available | Powered by VibeSync") }
        var newIsBanned by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Add New User", color = Slate900, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Username (@)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate900, unfocusedTextColor = Slate900,
                            focusedBorderColor = BrandPurple, unfocusedBorderColor = Slate200,
                            focusedContainerColor = Slate50, unfocusedContainerColor = Slate50
                        )
                    )
                    OutlinedTextField(
                        value = newDisplayName,
                        onValueChange = { newDisplayName = it },
                        label = { Text("Display Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate900, unfocusedTextColor = Slate900,
                            focusedBorderColor = BrandPurple, unfocusedBorderColor = Slate200,
                            focusedContainerColor = Slate50, unfocusedContainerColor = Slate50
                        )
                    )
                    OutlinedTextField(
                        value = newBio,
                        onValueChange = { newBio = it },
                        label = { Text("Bio") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate900, unfocusedTextColor = Slate900,
                            focusedBorderColor = BrandPurple, unfocusedBorderColor = Slate200,
                            focusedContainerColor = Slate50, unfocusedContainerColor = Slate50
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            AdminApiClient.createUser(newUsername.trim(), newDisplayName.trim(), newBio.trim(), newIsBanned)
                            showCreateDialog = false
                            refreshUsers()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) { Text("Create User", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = Slate500) }
            },
            containerColor = Color.White
        )
    }

    // Edit User Dialog
    editingUser?.let { user ->
        var editDisplayName by remember { mutableStateOf(user.displayName) }
        var editBio by remember { mutableStateOf(user.bio ?: "") }
        var editIsBanned by remember { mutableStateOf(user.isBanned) }

        AlertDialog(
            onDismissRequest = { editingUser = null },
            title = { Text("Edit @${user.username}", color = Slate900, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it },
                        label = { Text("Display Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate900, unfocusedTextColor = Slate900,
                            focusedBorderColor = BrandPurple, unfocusedBorderColor = Slate200,
                            focusedContainerColor = Slate50, unfocusedContainerColor = Slate50
                        )
                    )
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("Bio") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate900, unfocusedTextColor = Slate900,
                            focusedBorderColor = BrandPurple, unfocusedBorderColor = Slate200,
                            focusedContainerColor = Slate50, unfocusedContainerColor = Slate50
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = editIsBanned,
                            onCheckedChange = { editIsBanned = it },
                            colors = CheckboxDefaults.colors(checkedColor = RedDelete)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Account Banned Status", color = Slate700, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            AdminApiClient.updateUser(user.id, editDisplayName.trim(), editBio.trim(), editIsBanned)
                            editingUser = null
                            refreshUsers()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) { Text("Save Changes", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { editingUser = null }) { Text("Cancel", color = Slate500) }
            },
            containerColor = Color.White
        )
    }

    // Delete User Dialog
    deletingUser?.let { u ->
        AlertDialog(
            onDismissRequest = { deletingUser = null },
            title = { Text("Delete User Account?", color = Slate900, fontWeight = FontWeight.Bold) },
            text = { Text("Delete @${u.username}? Associated devices will be unlinked.", color = Slate600) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            AdminApiClient.deleteUser(u.id)
                            deletingUser = null
                            refreshUsers()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedDelete)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { deletingUser = null }) { Text("Cancel", color = Slate500) }
            },
            containerColor = Color.White
        )
    }
}

@Composable
fun UserCard(
    user: AdminUser,
    onOpenChat: () -> Unit,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onEdit: () -> Unit,
    onToggleBan: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate200))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandPurpleSurface)
                            .border(1.dp, BrandPurpleLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.username.take(1).uppercase(),
                            color = BrandPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("@${user.username}", color = Slate900, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                            if (user.isBanned) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "BANNED",
                                    color = RedDelete,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(RedBg, RoundedCornerShape(4.dp))
                                        .border(1.dp, RedBorder, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(user.displayName, color = Slate500, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                if (!user.deviceId.isNullOrBlank()) {
                    Text(
                        "📱 Linked",
                        color = SkyInfo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(SkyBg, RoundedCornerShape(6.dp))
                            .border(1.dp, SkyBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (!user.bio.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(user.bio, color = Slate600, fontSize = 12.sp, maxLines = 2)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onOpenChat,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurpleSurface),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandPurpleLight),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("💬 Chat", fontSize = 11.sp, color = BrandPurple, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onStartVoiceCall,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldBg),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldBorder),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("📞 Call", fontSize = 11.sp, color = EmeraldSuccess, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onStartVideoCall,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBg),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SkyBorder),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("📹 Video", fontSize = 11.sp, color = SkyInfo, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onEdit,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate100),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("✏ Edit", fontSize = 11.sp, color = Slate700, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = onToggleBan,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (user.isBanned) EmeraldBg else AmberBg),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (user.isBanned) EmeraldBorder else AmberBorder),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        if (user.isBanned) "Unban" else "Ban",
                        fontSize = 11.sp,
                        color = if (user.isBanned) EmeraldSuccess else AmberWarning,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onDelete,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedBg),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RedBorder),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("🗑", fontSize = 11.sp, color = RedDelete)
                }
            }
        }
    }
}
