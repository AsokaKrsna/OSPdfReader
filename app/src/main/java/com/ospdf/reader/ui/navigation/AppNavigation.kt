package com.ospdf.reader.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ospdf.reader.ui.browser.FileBrowserScreen
import com.ospdf.reader.ui.cloud.CloudSyncRoute
import com.ospdf.reader.ui.reader.ReaderScreen
import com.ospdf.reader.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Navigation routes for the app.
 */
object Routes {
    const val FILE_BROWSER = "file_browser"
    const val READER = "reader/{fileUri}"
    const val SETTINGS = "settings"
    const val CLOUD_SYNC = "cloud_sync"
    
    fun reader(fileUri: String): String {
        val encodedUri = URLEncoder.encode(fileUri, StandardCharsets.UTF_8.toString())
        return "reader/$encodedUri"
    }
}

/**
 * Main navigation composable for the app.
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    intent: Intent?
) {
    // Handle PDF file opened from external app
    LaunchedEffect(intent?.data?.toString()) {
        intent?.data?.let { uri ->
            navController.navigate(Routes.reader(uri.toString())) {
                popUpTo(Routes.FILE_BROWSER) { inclusive = false }
            }
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = Routes.FILE_BROWSER
    ) {
        composable(Routes.FILE_BROWSER) {
            FileBrowserScreen(
                onFileSelected = { uri ->
                    navController.navigate(Routes.reader(uri.toString()))
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                },
                onOpenFromDriveClick = {
                    navController.navigate(Routes.CLOUD_SYNC)
                }
            )
        }
        
        composable(
            route = Routes.READER,
            arguments = listOf(
                navArgument("fileUri") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("fileUri") ?: return@composable
            val fileUri = URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString())
            
            ReaderScreen(
                fileUri = Uri.parse(fileUri),
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onCloudSyncClick = { navController.navigate(Routes.CLOUD_SYNC) }
            )
        }
        
        composable(Routes.CLOUD_SYNC) {
            CloudSyncRoute(
                onBack = { navController.popBackStack() },
                onOpenFile = { uri ->
                    navController.navigate(Routes.reader(uri.toString())) {
                        popUpTo(Routes.CLOUD_SYNC) { inclusive = true }
                    }
                }
            )
        }
    }
}

