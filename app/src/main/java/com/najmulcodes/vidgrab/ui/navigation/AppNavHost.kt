package com.najmulcodes.vidgrab.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.najmulcodes.vidgrab.ui.downloads.DownloadsScreen
import com.najmulcodes.vidgrab.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val DOWNLOADS = "downloads"
}

@Composable
fun AppNavHost(
    incomingShare: String?,
    onShareConsumed: () -> Unit,
    openDownloads: Boolean,
    onOpenDownloadsConsumed: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    // A shared link is handled by the Home screen, so bring it back if Downloads is showing.
    LaunchedEffect(incomingShare) {
        if (incomingShare != null) navController.popBackStack(Routes.HOME, inclusive = false)
    }
    LaunchedEffect(openDownloads) {
        if (openDownloads) {
            navController.navigate(Routes.DOWNLOADS) { launchSingleTop = true }
            onOpenDownloadsConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                incomingShare = incomingShare,
                onShareConsumed = onShareConsumed,
                onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) { launchSingleTop = true } },
            )
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(onBack = { navController.popBackStack() })
        }
    }
}
