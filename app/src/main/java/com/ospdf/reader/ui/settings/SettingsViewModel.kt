package com.ospdf.reader.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospdf.reader.data.cloud.AuthState
import com.ospdf.reader.data.cloud.GoogleDriveAuth
import com.ospdf.reader.data.preferences.PreferencesManager
import com.ospdf.reader.data.preferences.ReadingModePreference
import com.ospdf.reader.data.preferences.ThemeMode
import com.ospdf.reader.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    private val driveAuth: GoogleDriveAuth
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefs.userPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val authState: StateFlow<AuthState> = driveAuth.authState

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun onDynamicColorsChange(enabled: Boolean) {
        viewModelScope.launch { prefs.setDynamicColors(enabled) }
    }

    fun onReadingModeChange(mode: ReadingModePreference) {
        viewModelScope.launch { prefs.setDefaultReadingMode(mode) }
    }

    fun onKeepScreenOnChange(enabled: Boolean) {
        viewModelScope.launch { prefs.setKeepScreenOn(enabled) }
    }

    fun onAutoSaveChange(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoSaveAnnotations(enabled) }
    }

    fun onSyncEnabledChange(enabled: Boolean) {
        viewModelScope.launch { prefs.setSyncEnabled(enabled) }
    }

    fun onSyncWifiOnlyChange(enabled: Boolean) {
        viewModelScope.launch { prefs.setSyncWifiOnly(enabled) }
    }

    fun onReducedMotionChange(enabled: Boolean) {
        viewModelScope.launch { prefs.setReducedMotion(enabled) }
    }

    fun onHighContrastChange(enabled: Boolean) {
        viewModelScope.launch { prefs.setHighContrast(enabled) }
    }

    fun buildSignInIntent(): Intent = driveAuth.getSignInIntent()

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch { driveAuth.handleSignInResult(data) }
    }

    fun signOut() {
        viewModelScope.launch { driveAuth.signOut() }
    }
}
