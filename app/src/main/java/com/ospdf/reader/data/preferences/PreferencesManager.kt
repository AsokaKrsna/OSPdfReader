package com.ospdf.reader.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Theme mode options.
 */
enum class ThemeMode {
    SYSTEM,  // Follow system
    LIGHT,   // Always light
    DARK,    // Always dark
    AMOLED   // True black for OLED
}

val ThemeMode.displayName: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
        ThemeMode.AMOLED -> "AMOLED"
    }

/**
 * Reading mode for PDFs.
 */
enum class ReadingModePreference {
    HORIZONTAL,
    VERTICAL
}

val ReadingModePreference.displayName: String
    get() = when (this) {
        ReadingModePreference.HORIZONTAL -> "Horizontal"
        ReadingModePreference.VERTICAL -> "Vertical"
    }

/**
 * User preferences data class.
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColors: Boolean = false,
    val defaultReadingMode: ReadingModePreference = ReadingModePreference.HORIZONTAL,
    val keepScreenOn: Boolean = true,
    val autoSaveAnnotations: Boolean = true,
    val syncEnabled: Boolean = false,
    val syncOnlyOnWifi: Boolean = true,
    val fontSize: Float = 1.0f,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val screenReaderOptimized: Boolean = false
)

/**
 * Manages user preferences using DataStore.
 */
@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val DEFAULT_READING_MODE = stringPreferencesKey("default_reading_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val AUTO_SAVE = booleanPreferencesKey("auto_save_annotations")
        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val SYNC_WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val SCREEN_READER = booleanPreferencesKey("screen_reader_optimized")
    }
    
    val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                themeMode = ThemeMode.valueOf(
                    preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
                ),
                useDynamicColors = preferences[PreferencesKeys.DYNAMIC_COLORS] ?: false,
                defaultReadingMode = ReadingModePreference.valueOf(
                    preferences[PreferencesKeys.DEFAULT_READING_MODE] ?: ReadingModePreference.HORIZONTAL.name
                ),
                keepScreenOn = preferences[PreferencesKeys.KEEP_SCREEN_ON] ?: true,
                autoSaveAnnotations = preferences[PreferencesKeys.AUTO_SAVE] ?: true,
                syncEnabled = preferences[PreferencesKeys.SYNC_ENABLED] ?: false,
                syncOnlyOnWifi = preferences[PreferencesKeys.SYNC_WIFI_ONLY] ?: true,
                fontSize = preferences[PreferencesKeys.FONT_SIZE] ?: 1.0f,
                reducedMotion = preferences[PreferencesKeys.REDUCED_MOTION] ?: false,
                highContrast = preferences[PreferencesKeys.HIGH_CONTRAST] ?: false,
                screenReaderOptimized = preferences[PreferencesKeys.SCREEN_READER] ?: false
            )
        }
    
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }
    
    suspend fun setDynamicColors(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLORS] = enabled
        }
    }
    
    suspend fun setDefaultReadingMode(mode: ReadingModePreference) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_READING_MODE] = mode.name
        }
    }
    
    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = enabled
        }
    }
    
    suspend fun setAutoSaveAnnotations(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_SAVE] = enabled
        }
    }
    
    suspend fun setSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_ENABLED] = enabled
        }
    }
    
    suspend fun setSyncWifiOnly(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_WIFI_ONLY] = enabled
        }
    }
    
    suspend fun setFontSize(size: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FONT_SIZE] = size.coerceIn(0.5f, 2.0f)
        }
    }
    
    suspend fun setReducedMotion(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REDUCED_MOTION] = enabled
        }
    }
    
    suspend fun setHighContrast(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIGH_CONTRAST] = enabled
        }
    }
    
    suspend fun setScreenReaderOptimized(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCREEN_READER] = enabled
        }
    }
}
