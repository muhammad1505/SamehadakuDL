package app.samehadaku.dl.presentation.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.samehadaku.dl.presentation.about.AboutScreen
import app.samehadaku.dl.presentation.cloudflare.CloudflareScreen
import app.samehadaku.dl.presentation.collections.CollectionsScreen
import app.samehadaku.dl.presentation.calendar.ReleaseCalendarScreen
import app.samehadaku.dl.presentation.components.AppBackground
import app.samehadaku.dl.presentation.components.AppTab
import app.samehadaku.dl.presentation.episodes.EpisodesScreen
import app.samehadaku.dl.presentation.home.HomeScreen
import app.samehadaku.dl.presentation.logs.LogsScreen
import app.samehadaku.dl.presentation.notifications.NotificationsScreen
import app.samehadaku.dl.presentation.player.PlayerScreen
import app.samehadaku.dl.presentation.quality.QualityScreen
import app.samehadaku.dl.presentation.queue.QueueScreen
import app.samehadaku.dl.presentation.search.SearchScreen
import app.samehadaku.dl.presentation.search.SearchDetailScreen
import app.samehadaku.dl.presentation.settings.SettingsScreen
import app.samehadaku.dl.presentation.smartstorage.SmartStorageDetailScreen
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
    const val SEARCH = "search"
    const val SEARCH_DETAIL = "search-detail"
    const val NOTIFICATIONS = "notifications"
    const val ABOUT = "about"
    const val LOGS = "logs"
    const val CALENDAR = "calendar"
    const val SMART_STORAGE = "smart-storage"
    const val PLAYER = "player"

    /** Player for a video handed over by another app through "Buka dengan". */
    const val PLAYER_EXTERNAL = "player-external"

    /** Player route; both parts are URI-encoded because file names may contain `/`. */
    fun player(folderName: String, fileName: String): String =
        "$PLAYER/${Uri.encode(folderName)}/${Uri.encode(fileName)}"

    /** External video route; the whole URI is encoded so query strings survive. */
    fun playerExternal(uri: String): String = "$PLAYER_EXTERNAL/${Uri.encode(uri)}"
}

/** Tabs cross-fade; every other route is a pushed detail page. */
private fun NavBackStackEntry.isDetailRoute(): Boolean {
    val route = destination.route ?: return false
    return AppTab.entries.none { it.route == route }
}

@Composable
fun SamehadakuNavHost(
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
    externalVideoUri: String? = null,
    onExternalVideoConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    /**
     * Tabs always replace whatever is on top of the start destination, so selecting a
     * tab can never leave the previous screen visible (the bug with Langganan before it
     * became a tab). Detail screens (episodes, quality, verification) are popped too.
     */
    fun navigateToTab(tab: AppTab) {
        if (navController.currentDestination?.route == tab.route) return
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id)
            launchSingleTop = true
        }
    }

    /** Opens a tab route when possible, otherwise a detail route such as verification. */
    fun openRoute(route: String) {
        if (route.isBlank()) return
        when (route) {
            Routes.SEARCH, Routes.NOTIFICATIONS -> navController.navigate(route) {
                launchSingleTop = true
            }
            else -> {
                val tab = AppTab.entries.firstOrNull { it.route == route }
                if (tab != null) {
                    navigateToTab(tab)
                } else {
                    runCatching {
                        navController.navigate(route) { launchSingleTop = true }
                    }
                }
            }
        }
    }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) navigateToTab(AppTab.HOME)
    }

    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        openRoute(route)
        onPendingRouteConsumed()
    }

    // A video opened with "Buka dengan" jumps straight into the internal player.
    LaunchedEffect(externalVideoUri) {
        val uri = externalVideoUri ?: return@LaunchedEffect
        runCatching {
            navController.navigate(Routes.playerExternal(uri)) { launchSingleTop = true }
        }
        onExternalVideoConsumed()
    }

    AppBackground {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            // Full-screen detail pages slide in from the right, while tab
            // switches cross-fade with a tiny scale so the bottom bar feels
            // anchored instead of flashing between screens.
            enterTransition = {
                if (targetState.isDetailRoute()) {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(220))
                } else {
                    fadeIn(tween(220)) + scaleIn(
                        initialScale = 0.985f,
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                    )
                }
            },
            exitTransition = {
                if (targetState.isDetailRoute()) {
                    fadeOut(tween(150))
                } else {
                    fadeOut(tween(150)) + scaleOut(
                        targetScale = 0.99f,
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    )
                }
            },
            popEnterTransition = {
                if (initialState.isDetailRoute()) {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(200))
                } else {
                    fadeIn(tween(220)) + scaleIn(
                        initialScale = 0.985f,
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                    )
                }
            },
            popExitTransition = {
                if (initialState.isDetailRoute()) {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                    ) + fadeOut(tween(200))
                } else {
                    fadeOut(tween(150))
                }
            },
        ) {
            composable(Routes.HOME) { entry ->
                val verified by entry.savedStateHandle
                    .getStateFlow("verified", false)
                    .collectAsStateWithLifecycle()

                HomeScreen(
                    onOpenEpisodes = { navController.navigate(Routes.EPISODES) },
                    onOpenQuality = { navController.navigate(Routes.QUALITY) },
                    onOpenCloudflare = { navController.navigate(Routes.CLOUDFLARE) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) { launchSingleTop = true } },
                    onOpenNotifications = {
                        navController.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true }
                    },
                    onOpenCalendar = { navController.navigate(Routes.CALENDAR) { launchSingleTop = true } },
                    onTabSelected = ::navigateToTab,
                    verified = verified,
                    onVerifiedHandled = { entry.savedStateHandle["verified"] = false },
                    sharedUrl = sharedUrl,
                    onSharedUrlConsumed = onSharedUrlConsumed,
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { navController.navigate(Routes.SEARCH_DETAIL) },
                    onOpenEpisodes = { navController.navigate(Routes.EPISODES) },
                    onOpenQuality = { navController.navigate(Routes.QUALITY) },
                    onOpenCloudflare = { navController.navigate(Routes.CLOUDFLARE) },
                )
            }

            composable(Routes.SEARCH_DETAIL) { entry ->
                val verified by entry.savedStateHandle
                    .getStateFlow("verified", false)
                    .collectAsStateWithLifecycle()

                SearchDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEpisodes = { navController.navigate(Routes.EPISODES) },
                    onOpenQuality = { navController.navigate(Routes.QUALITY) },
                    onOpenCloudflare = { navController.navigate(Routes.CLOUDFLARE) },
                    verified = verified,
                    onVerifiedHandled = { entry.savedStateHandle["verified"] = false },
                )
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRoute = { route ->
                        navController.popBackStack()
                        openRoute(route)
                    },
                )
            }

            composable(Routes.CALENDAR) {
                ReleaseCalendarScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCloudflare = { navController.navigate(Routes.CLOUDFLARE) },
                    onOpenDetail = {
                        navController.navigate(Routes.SEARCH_DETAIL) { launchSingleTop = true }
                    },
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.LOGS) {
                LogsScreen(onBack = { navController.popBackStack() })
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
                    onPlay = { folderName, fileName ->
                        navController.navigate(Routes.player(folderName, fileName))
                    },
                )
            }

            composable(Routes.COLLECTIONS) {
                CollectionsScreen(
                    onTabSelected = ::navigateToTab,
                    onBrowse = { navigateToTab(AppTab.HOME) },
                    onPlay = { folderName, fileName ->
                        navController.navigate(Routes.player(folderName, fileName))
                    },
                )
            }

            composable(
                route = "${Routes.PLAYER}/{folder}/{file}",
                arguments = listOf(
                    navArgument("folder") { type = NavType.StringType },
                    navArgument("file") { type = NavType.StringType },
                ),
            ) {
                PlayerScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = "${Routes.PLAYER_EXTERNAL}/{uri}",
                arguments = listOf(
                    navArgument("uri") { type = NavType.StringType },
                ),
            ) {
                PlayerScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SUBSCRIPTIONS) { entry ->
                val verified by entry.savedStateHandle
                    .getStateFlow("verified", false)
                    .collectAsStateWithLifecycle()

                SubscriptionsScreen(
                    onTabSelected = ::navigateToTab,
                    onOpenEpisodes = { navController.navigate(Routes.EPISODES) },
                    onOpenQueue = { navigateToTab(AppTab.QUEUE) },
                    onOpenCloudflare = { navController.navigate(Routes.CLOUDFLARE) },
                    verified = verified,
                    onVerifiedHandled = { entry.savedStateHandle["verified"] = false },
                )
            }

            composable(Routes.SMART_STORAGE) {
                SmartStorageDetailScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onTabSelected = ::navigateToTab,
                    onOpenCloudflare = { navController.navigate(Routes.CLOUDFLARE) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenLogs = { navController.navigate(Routes.LOGS) },
                    onOpenSmartStorage = { navController.navigate(Routes.SMART_STORAGE) },
                )
            }
        }
    }
}
