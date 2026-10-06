package com.najmulcodes.zapflick.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.ui.background.BackgroundSetupScreen
import com.najmulcodes.zapflick.ui.browser.TabScreen
import com.najmulcodes.zapflick.ui.finished.FinishedScreen
import com.najmulcodes.zapflick.ui.player.PlayerScreen
import com.najmulcodes.zapflick.ui.progress.ProgressScreen
import com.najmulcodes.zapflick.ui.security.PrivateFolderScreen
import com.najmulcodes.zapflick.ui.settings.SettingsScreen

object Routes {
    const val TAB = "tab"
    const val PROGRESS = "progress"
    const val FINISHED = "finished"
    const val SETTINGS = "settings"
    const val BACKGROUND = "background"
    const val PRIVATE = "private"
    const val PLAYER = "player/{id}"

    fun player(id: Long) = "player/$id"
}

/** The four bottom-bar buttons. Browse and Home both live on the Tab route; the active tab decides which is lit. */
private enum class BarItem(val label: Int, @DrawableRes val icon: Int) {
    BROWSE(R.string.nav_tab, R.drawable.ic_nav_browse),
    HOME(R.string.nav_home, R.drawable.ic_nav_home),
    DOWNLOADS(R.string.nav_progress, R.drawable.ic_nav_downloads),
    LIBRARY(R.string.nav_finished, R.drawable.ic_nav_library),
}

private val BAR_ROUTES = setOf(Routes.TAB, Routes.PROGRESS, Routes.FINISHED)

/** Opens a bottom-bar destination, keeping its own back stack so returning to it restores where you were. */
private fun NavHostController.switchTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavHost(
    incomingShare: String?,
    onShareConsumed: () -> Unit,
    openProgress: Boolean,
    onOpenProgressConsumed: () -> Unit,
    secureScreens: Boolean,
    navController: NavHostController = rememberNavController(),
    barViewModel: NavBarViewModel = hiltViewModel(),
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val activeCount by barViewModel.activeCount.collectAsStateWithLifecycle()
    var fullscreenVideo by remember { mutableStateOf(false) }
    var typingAddress by remember { mutableStateOf(false) }
    var onStartPage by remember { mutableStateOf(true) }
    var homeRequested by remember { mutableStateOf(false) }
    var browseRequested by remember { mutableStateOf(false) }

    // A shared link is handled by the Tab screen, so bring it back to the front.
    LaunchedEffect(incomingShare) {
        if (incomingShare != null) navController.switchTo(Routes.TAB)
    }
    LaunchedEffect(openProgress) {
        if (openProgress) {
            navController.switchTo(Routes.PROGRESS)
            onOpenProgressConsumed()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(visible = !fullscreenVideo && !typingAddress && current in BAR_ROUTES) {
                NavigationBar {
                    BarItem.values().forEach { item ->
                        NavigationBarItem(
                            selected = when (item) {
                                BarItem.BROWSE -> current == Routes.TAB && !onStartPage
                                BarItem.HOME -> current == Routes.TAB && onStartPage
                                BarItem.DOWNLOADS -> current == Routes.PROGRESS
                                BarItem.LIBRARY -> current == Routes.FINISHED
                            },
                            onClick = {
                                when (item) {
                                    BarItem.BROWSE -> {
                                        browseRequested = true
                                        navController.switchTo(Routes.TAB)
                                    }
                                    BarItem.HOME -> {
                                        homeRequested = true
                                        navController.switchTo(Routes.TAB)
                                    }
                                    BarItem.DOWNLOADS -> navController.switchTo(Routes.PROGRESS)
                                    BarItem.LIBRARY -> navController.switchTo(Routes.FINISHED)
                                }
                            },
                            icon = {
                                if (item == BarItem.DOWNLOADS && activeCount > 0) {
                                    BadgedBox(badge = { Badge { Text(activeCount.toString()) } }) {
                                        Icon(painterResource(item.icon), contentDescription = null)
                                    }
                                } else {
                                    Icon(painterResource(item.icon), contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TAB,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.TAB) {
                TabScreen(
                    incomingShare = incomingShare,
                    onShareConsumed = onShareConsumed,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    onOpenProgress = { navController.switchTo(Routes.PROGRESS) },
                    onFullscreenChange = { fullscreenVideo = it },
                    onTypingChange = { typingAddress = it },
                    homeRequested = homeRequested,
                    browseRequested = browseRequested,
                    onNavRequestHandled = {
                        homeRequested = false
                        browseRequested = false
                    },
                    onStartPageChange = { onStartPage = it },
                )
            }
            composable(Routes.PROGRESS) {
                ProgressScreen(onOpenBackgroundSetup = { navController.navigate(Routes.BACKGROUND) { launchSingleTop = true } })
            }
            composable(Routes.FINISHED) {
                FinishedScreen(
                    onOpenPrivate = { navController.navigate(Routes.PRIVATE) { launchSingleTop = true } },
                    onPlay = { id -> navController.navigate(Routes.player(id)) { launchSingleTop = true } },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBackgroundSetup = { navController.navigate(Routes.BACKGROUND) { launchSingleTop = true } },
                )
            }
            composable(Routes.BACKGROUND) {
                BackgroundSetupScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PRIVATE) {
                PrivateFolderScreen(
                    secureScreens = secureScreens,
                    onBack = { navController.popBackStack() },
                    onPlay = { id -> navController.navigate(Routes.player(id)) { launchSingleTop = true } },
                )
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { _: NavBackStackEntry ->
                PlayerScreen(
                    secureScreens = secureScreens,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
