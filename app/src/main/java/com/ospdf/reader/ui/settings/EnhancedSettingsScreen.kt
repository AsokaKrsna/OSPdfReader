package com.ospdf.reader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ospdf.reader.data.preferences.ThemeMode
import com.ospdf.reader.data.preferences.ReadingModePreference
import com.ospdf.reader.data.preferences.UserPreferences
import com.ospdf.reader.data.preferences.displayName

/**
 * Complete settings screen with all app preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedSettingsScreen(
    preferences: UserPreferences,
    isSignedIn: Boolean,
    accountEmail: String?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorsChange: (Boolean) -> Unit,
    onReadingModeChange: (ReadingModePreference) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onAutoSaveChange: (Boolean) -> Unit,
    onSyncEnabledChange: (Boolean) -> Unit,
    onSyncWifiOnlyChange: (Boolean) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onCloudSyncClick: () -> Unit,
    onAboutClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showReadingModeDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance Section
            SettingsSection(title = "Appearance") {
                SettingsItem(
                    icon = Icons.Outlined.DarkMode,
                    title = "Theme",
                    subtitle = preferences.themeMode.displayName,
                    onClick = { showThemeDialog = true }
                )
                
                SwitchSettingsItem(
                    icon = Icons.Outlined.Palette,
                    title = "Dynamic colors",
                    subtitle = "Use Material You colors from wallpaper",
                    checked = preferences.useDynamicColors,
                    onCheckedChange = onDynamicColorsChange
                )
            }
            
            // Reading Section
            SettingsSection(title = "Reading") {
                SettingsItem(
                    icon = Icons.Outlined.ViewCarousel,
                    title = "Default reading mode",
                    subtitle = preferences.defaultReadingMode.displayName,
                    onClick = { showReadingModeDialog = true }
                )
                
                SwitchSettingsItem(
                    icon = Icons.Outlined.Lightbulb,
                    title = "Keep screen on",
                    subtitle = "Prevent screen from turning off while reading",
                    checked = preferences.keepScreenOn,
                    onCheckedChange = onKeepScreenOnChange
                )
                
                SwitchSettingsItem(
                    icon = Icons.Outlined.Save,
                    title = "Auto-save annotations",
                    subtitle = "Automatically save changes",
                    checked = preferences.autoSaveAnnotations,
                    onCheckedChange = onAutoSaveChange
                )
            }
            
            // Cloud Sync Section
            SettingsSection(title = "Cloud Sync") {
                if (isSignedIn) {
                    SettingsItem(
                        icon = Icons.Filled.Cloud,
                        title = "Google Drive",
                        subtitle = accountEmail ?: "Connected",
                        onClick = onCloudSyncClick
                    )
                    
                    SwitchSettingsItem(
                        icon = Icons.Outlined.Sync,
                        title = "Auto-sync",
                        subtitle = "Sync annotated PDFs automatically",
                        checked = preferences.syncEnabled,
                        onCheckedChange = onSyncEnabledChange
                    )
                    
                    if (preferences.syncEnabled) {
                        SwitchSettingsItem(
                            icon = Icons.Outlined.Wifi,
                            title = "Sync only on Wi-Fi",
                            subtitle = "Save mobile data",
                            checked = preferences.syncOnlyOnWifi,
                            onCheckedChange = onSyncWifiOnlyChange
                        )
                    }
                } else {
                    SettingsItem(
                        icon = Icons.Outlined.CloudOff,
                        title = "Sign in to Google",
                        subtitle = "Backup and sync your PDFs",
                        onClick = onSignInClick
                    )
                }
            }
            
            // Accessibility Section
            SettingsSection(title = "Accessibility") {
                SwitchSettingsItem(
                    icon = Icons.Outlined.MotionPhotosOff,
                    title = "Reduce motion",
                    subtitle = "Minimize animations",
                    checked = preferences.reducedMotion,
                    onCheckedChange = onReducedMotionChange
                )
                
                SwitchSettingsItem(
                    icon = Icons.Outlined.Contrast,
                    title = "High contrast",
                    subtitle = "Increase text and UI contrast",
                    checked = preferences.highContrast,
                    onCheckedChange = onHighContrastChange
                )
            }
            
            // About Section
            SettingsSection(title = "About") {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "About OSPdfReader",
                    subtitle = "Version 1.0.0 • Open Source",
                    onClick = onAboutClick
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    // Theme selection dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeModeChange(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferences.themeMode == mode,
                                onClick = {
                                    onThemeModeChange(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Reading mode dialog
    if (showReadingModeDialog) {
        AlertDialog(
            onDismissRequest = { showReadingModeDialog = false },
            title = { Text("Default reading mode") },
            text = {
                Column {
                    ReadingModePreference.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onReadingModeChange(mode)
                                    showReadingModeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferences.defaultReadingMode == mode,
                                onClick = {
                                    onReadingModeChange(mode)
                                    showReadingModeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReadingModeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SwitchSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val ThemeMode.displayName: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System default"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
        ThemeMode.AMOLED -> "AMOLED Dark"
    }

private val ReadingModePreference.displayName: String
    get() = when (this) {
        ReadingModePreference.HORIZONTAL -> "Horizontal swipe"
        ReadingModePreference.VERTICAL -> "Vertical scroll"
    }
