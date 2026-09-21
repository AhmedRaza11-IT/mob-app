package com.vibesync.admin.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibesync.admin.config.AdminNetworkConfig
import com.vibesync.admin.model.DataSummary
import com.vibesync.admin.model.StoredFile
import com.vibesync.admin.network.AdminApiClient
import com.vibesync.admin.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var summary by remember { mutableStateOf<DataSummary?>(null) }
    var files by remember { mutableStateOf<List<StoredFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    fun refreshData() {
        scope.launch {
            try {
                isLoading = true
                val s = AdminApiClient.fetchDataSummary()
                val f = AdminApiClient.fetchFiles()
                summary = s
                files = f
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    val filtered = files.filter { f ->
        val matchesSearch = searchQuery.isBlank() ||
                f.name.contains(searchQuery, ignoreCase = true) ||
                f.username.contains(searchQuery, ignoreCase = true) ||
                f.displayName.contains(searchQuery, ignoreCase = true) ||
                f.deviceId.contains(searchQuery, ignoreCase = true)

        val matchesCategory = selectedCategory == "ALL" ||
                f.category.equals(selectedCategory, ignoreCase = true)

        matchesSearch && matchesCategory
    }

    Scaffold(
        containerColor = Slate50,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Data & Storage Telemetry", color = Slate900, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("File repository & remote storage control", color = Slate500, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshData() }) {
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

            // Storage Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val mbText = summary?.let { String.format("%.2f MB", it.totalStorageMb) } ?: "0.00 MB"
                    StatBox("Storage", mbText, Slate900, Modifier.weight(1f))
                    StatBox("Total Files", (summary?.totalItems ?: 0).toString(), BrandPurple, Modifier.weight(1f))
                    StatBox("Devices", (summary?.deviceCount ?: 0).toString(), BrandTeal, Modifier.weight(1f))
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search files, user, or device ID...", color = Slate400, fontSize = 12.sp) },
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

            // Category Filter Pills
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL", "Document", "Image", "Audio", "Video", "Spreadsheet").forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandPurple,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = Slate600
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCategory == cat,
                                borderColor = if (selectedCategory == cat) BrandPurple else Slate200
                            )
                        )
                    }
                }
            }

            // File items
            if (isLoading && files.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandPurple)
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No files found in repository", color = Slate400, fontSize = 13.sp)
                    }
                }
            } else {
                items(filtered) { file ->
                    FileCard(
                        file = file,
                        onDownload = {
                            val fullUrl = if (file.downloadUrl.startsWith("http")) file.downloadUrl else "${AdminNetworkConfig.getBaseUrl()}${file.downloadUrl}"
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open download: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                try {
                                    AdminApiClient.deleteFile(file.name)
                                    Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                                    refreshData()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun FileCard(
    file: StoredFile,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate200))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.name.split('.').lastOrNull()?.uppercase() ?: "FILE",
                        color = Slate600,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Slate100, RoundedCornerShape(4.dp))
                            .border(1.dp, Slate200, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = file.name,
                        color = Slate900,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "@${file.username} • ${file.sizeFormatted} • ${file.category}",
                    color = Slate500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = BrandPurple)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedDelete)
                }
            }
        }
    }
}
