package app.samehadaku.dl.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.samehadaku.dl.presentation.cloudflare.CloudflareScreen
import app.samehadaku.dl.presentation.collections.CollectionsScreen
import app.samehadaku.dl.presentation.components.AppBackground
import app.samehadaku.dl.presentation.components.AppTab
import app.samehadaku.dl.presentation.episodes.EpisodesScreen
import app.samehadaku.dl.presentation.home.HomeScreen
import app.samehadaku.dl.presentation.quality.QualityScreen
import app.samehadaku.dl.presentation.queue.QueueScreen
import app.samehadaku.dl.presentation.settings.SettingsScreen
import app.samehadaku.dl.presentation.subscriptions.SubscriptionsScreen

object Routes {
    const val HOME = "home"
    const val CLOUDFLARE = "cloudflare"
    const val EPISODES = "episodes"
    const val QUALITY = "quality"
    const val QUEUE = "queue"
    const val COLLECTIONS = "collections"
    const val SETTINGS = "settings"
    const val SUBSCRIPTIONS = "subscriptions"
}

@Composable
fun SamehadakuNavHost(
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    fun navigateToTab(tab: AppTab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) navigateToTab(AppTab.HOME)
    }

    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        runCatching {
            navController.navigate(route) { launchSingleTop = true }
        }
        onPendingRouteConsumed()
    }

    AppBackground {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(240)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(240)) },
            popExitTransition = { fadeOut(tween(180)) },
        ) {
            composable(Routes.HOME) { entry ->
                val verified by entry.savedStateHandle
                    .getStateFlow("verified", false)
                    .collectAsStateWithLifecycle()

                HomeScreen(
                    onOpenEpisodes = { navController.navigate(Routes.EPISODES) },
                    onOpenQuality = { navController.navigate(Routes.QUALITY) },
                    onOpenCloudflare = { navController.navigate(Routes.CLOUDFLARE) },
                    onOpenDownloads = { navigateToTab(AppTab.QUEUE) },
                    onOpenSubscriptions = { navController.navigate(Routes.SUBSCRIPTIONS) },
                    onTabSelected = ::navigateToTab,
                    verified = verified,
                    onVerifiedHandled = { entry.savedStateHandle["verified"] = false },
                    sharedUrl = sharedUrl,
                    onSharedUrlConsumed = onSharedUrlConsumed,
                )
            }

            composable(Routes.CLOUDFLARE) {
                CloudflareScreen(
                    onVerified = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("verified", true)
                        navController.popBackStack()
                    },
                    onClose = { navController.popBackStack() },
                )
            }

            composable(Routes.EPISODES) {
                EpisodesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenQueue = { navigateToTab(AppTab.QUEUE) },
                    onOpenQuality = { navController.navigate(Routes.QUALITY) },
                )
            }

            composable(Routes.QUALITY) { entry ->
                val verified by entry.savedStateHandle
                    .getStateFlow("verified", false)
                    .collectAsStateWithLifecycle()

                QualityScreen(
                    onBack = { navController.popBackStack() },
                    onOpenQueue = { navigateToTab(AppTab.QUEUE) },
                    onOpenCloudflare = { navController.navigate(Routes.CLOUDFLARE) },
                    verified = verified,
                    onVerifiedHandled = { entry.savedStateHandle["verified"] = false },
                )
            }

            composable(Routes.QUEUE) {
                QueueScreen(
                    onTabSelected = ::navigateToTab,
                    onBrowse = { navigateToTab(AppTab.HOME) },
                )
            }

            composable(Routes.COLLECTIONS) {
                CollectionsScreen(
                    onTabSelected = ::navigateToTab,
                    onBrowse = { navigateToTab(AppTab.HOME) },
                )
            }

            composable(Routes.SUBSCRIPTIONS) { entry ->
                val verified by entry.savedStateHandle
                    .getStateFlow("verified", false)
                    .collectAsStateWithLifecycle()

                SubscriptionsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEpisodes = { navController.navigate(Routes.EPISODES) },
                    onOpenQueue = { navigateToTab(AppTab.QUEUE) },
                    onOpenCloudflare = { navController.navigate(Routes.CLOUDFLARE) },
                    verified = verified,
                    onVerifiedHandled = { entry.savedStateHandle["verified"] = false },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onTabSelected = ::navigateToTab,
                    onOpenCloudflare = { navController.navigate(Routes.CLOUDFLARE) },
                )
            }
        }
    }
}
