package com.ospdf.reader.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ospdf.reader.data.cloud.AuthState

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.collectAsState()
    val authState by viewModel.authState.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(result.data)
    }

    val isSignedIn = authState is AuthState.SignedIn
    val accountEmail = (authState as? AuthState.SignedIn)?.account?.email

    EnhancedSettingsScreen(
        preferences = preferences,
        isSignedIn = isSignedIn,
        accountEmail = accountEmail,
        onThemeModeChange = { viewModel.onThemeModeChange(it) },
        onDynamicColorsChange = { viewModel.onDynamicColorsChange(it) },
        onReadingModeChange = { viewModel.onReadingModeChange(it) },
        onKeepScreenOnChange = { viewModel.onKeepScreenOnChange(it) },
        onAutoSaveChange = { viewModel.onAutoSaveChange(it) },
        onSyncEnabledChange = { viewModel.onSyncEnabledChange(it) },
        onSyncWifiOnlyChange = { viewModel.onSyncWifiOnlyChange(it) },
        onReducedMotionChange = { viewModel.onReducedMotionChange(it) },
        onHighContrastChange = { viewModel.onHighContrastChange(it) },
        onSignInClick = { signInLauncher.launch(viewModel.buildSignInIntent()) },
        onSignOutClick = { viewModel.signOut() },
        onCloudSyncClick = { /* future: open Drive sync details */ },
        onAboutClick = { /* TODO: show OSS licenses */ },
        onBack = onBack
    )
}
