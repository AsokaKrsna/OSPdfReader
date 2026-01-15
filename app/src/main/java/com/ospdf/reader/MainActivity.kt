package com.ospdf.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.ospdf.reader.data.preferences.PreferencesManager
import com.ospdf.reader.data.preferences.UserPreferences
import com.ospdf.reader.ui.navigation.AppNavigation
import com.ospdf.reader.ui.theme.OSPdfReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main entry point for the OSPdfReader app.
 * Uses Jetpack Compose for the UI and Hilt for dependency injection.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            // Collect user preferences to apply theme settings
            val preferences by preferencesManager.userPreferences.collectAsState(
                initial = UserPreferences()
            )
            
            OSPdfReaderTheme(
                themeMode = preferences.themeMode,
                dynamicColor = preferences.useDynamicColors,
                highContrast = preferences.highContrast
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        intent = intent
                    )
                }
            }
        }
    }
}
