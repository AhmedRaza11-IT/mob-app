package com.whatsapp.clone.ui.settings.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.whatsapp.clone.ui.settings.SettingsViewModel
import com.whatsapp.clone.ui.settings.SettingsScreen
import com.whatsapp.clone.ui.settings.screens.*

sealed class SettingsRoute(val route: String) {
    object Root          : SettingsRoute("settings_root")
    object Account       : SettingsRoute("settings_account")
    object Privacy       : SettingsRoute("settings_privacy")
    object Avatar        : SettingsRoute("settings_avatar")
    object Chats         : SettingsRoute("settings_chats")
    object Notifications : SettingsRoute("settings_notifications")
    object Storage       : SettingsRoute("settings_storage")
    object Language      : SettingsRoute("settings_language")
    object Help          : SettingsRoute("settings_help")
}

private const val ANIM_DURATION = 300

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsNavHost(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    username: String = "VibeSync User"
) {
    val navController = rememberNavController()
    val state by viewModel.settingsState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = SettingsRoute.Root.route,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(ANIM_DURATION)
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(ANIM_DURATION)
            )
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(ANIM_DURATION)
            )
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(ANIM_DURATION)
            )
        }
    ) {
        composable(SettingsRoute.Root.route) {
            SettingsScreen(
                onBack = onBack,
                onNavigate = { route -> navController.navigate(route.route) },
                username = username
            )
        }
        composable(SettingsRoute.Account.route) {
            AccountScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoute.Privacy.route) {
            PrivacyScreen(
                onBack = { navController.popBackStack() },
                state = state,
                viewModel = viewModel
            )
        }
        composable(SettingsRoute.Avatar.route) {
            AvatarScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoute.Chats.route) {
            ChatsScreen(
                onBack = { navController.popBackStack() },
                state = state,
                viewModel = viewModel
            )
        }
        composable(SettingsRoute.Notifications.route) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                state = state,
                viewModel = viewModel
            )
        }
        composable(SettingsRoute.Storage.route) {
            StorageScreen(
                onBack = { navController.popBackStack() },
                state = state,
                viewModel = viewModel
            )
        }
        composable(SettingsRoute.Language.route) {
            LanguageScreen(
                onBack = { navController.popBackStack() },
                currentLanguage = state.appLanguage,
                onSelect = viewModel::setAppLanguage
            )
        }
        composable(SettingsRoute.Help.route) {
            HelpScreen(onBack = { navController.popBackStack() })
        }
    }
}
