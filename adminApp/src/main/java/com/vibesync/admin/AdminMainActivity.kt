package com.vibesync.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vibesync.admin.config.AdminNetworkConfig
import com.vibesync.admin.ui.components.AdminCallScreen
import com.vibesync.admin.ui.components.AdminChatSheet
import com.vibesync.admin.ui.screens.ChatsScreen
import com.vibesync.admin.ui.screens.DataScreen
import com.vibesync.admin.ui.screens.DevicesScreen
import com.vibesync.admin.ui.screens.LoginScreen
import com.vibesync.admin.ui.screens.UsersScreen
import com.vibesync.admin.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AdminMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdminNetworkConfig.appContext = applicationContext
        setContent {
            AdminTheme {
                AdminAppRoot()
            }
        }
    }
}

@Composable
fun AdminAppRoot() {
    val context = LocalContext.current
    val navController = rememberNavController()

    // Determine initial route based on existing session token
    val startDestination = remember {
        val token = runBlocking { AdminNetworkConfig.getAuthTokenFlow(context).first() }
        if (!token.isNullOrBlank()) "devices" else "login"
    }

    // Communication Overlays
    var activeChatTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeCallTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf("devices", "chats", "users", "data")

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Slate50,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = Color.White,
                        contentColor = Slate900,
                        tonalElevation = 2.dp
                    ) {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = "Devices") },
                            label = { Text("Devices", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            selected = currentRoute == "devices",
                            onClick = { navController.navigate("devices") { launchSingleTop = true } },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandPurple,
                                selectedTextColor = BrandPurple,
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate500,
                                indicatorColor = BrandPurpleLight
                            )
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                            label = { Text("Chats", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            selected = currentRoute == "chats",
                            onClick = { navController.navigate("chats") { launchSingleTop = true } },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandPurple,
                                selectedTextColor = BrandPurple,
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate500,
                                indicatorColor = BrandPurpleLight
                            )
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Group, contentDescription = "Users") },
                            label = { Text("Users", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            selected = currentRoute == "users",
                            onClick = { navController.navigate("users") { launchSingleTop = true } },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandPurple,
                                selectedTextColor = BrandPurple,
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate500,
                                indicatorColor = BrandPurpleLight
                            )
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Storage, contentDescription = "Data") },
                            label = { Text("Data", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            selected = currentRoute == "data",
                            onClick = { navController.navigate("data") { launchSingleTop = true } },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandPurple,
                                selectedTextColor = BrandPurple,
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate500,
                                indicatorColor = BrandPurpleLight
                            )
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(padding)
            ) {
                composable("login") {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate("devices") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    )
                }
                composable("devices") {
                    DevicesScreen(
                        onOpenChat = { user, name -> activeChatTarget = Pair(user, name) },
                        onStartCall = { user, isVideo -> activeCallTarget = Pair(user, isVideo) }
                    )
                }
                composable("chats") {
                    ChatsScreen(
                        onOpenChat = { user, name -> activeChatTarget = Pair(user, name) },
                        onStartCall = { user, isVideo -> activeCallTarget = Pair(user, isVideo) }
                    )
                }
                composable("users") {
                    UsersScreen(
                        onOpenChat = { user, name -> activeChatTarget = Pair(user, name) },
                        onStartCall = { user, isVideo -> activeCallTarget = Pair(user, isVideo) }
                    )
                }
                composable("data") {
                    DataScreen()
                }
            }
        }

        // Active Chat Sheet overlay
        activeChatTarget?.let { (username, name) ->
            AdminChatSheet(
                targetUsername = username,
                targetDisplayName = name,
                onClose = { activeChatTarget = null },
                onStartCall = { isVideo ->
                    activeChatTarget = null
                    activeCallTarget = Pair(username, isVideo)
                }
            )
        }

        // Active Call Overlay
        activeCallTarget?.let { (username, isVideo) ->
            AdminCallScreen(
                targetUsername = username,
                isVideo = isVideo,
                onCallEnded = { activeCallTarget = null }
            )
        }
    }
}
