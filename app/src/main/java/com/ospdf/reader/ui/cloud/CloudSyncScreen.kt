package com.ospdf.reader.ui.cloud

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ospdf.reader.data.cloud.AuthState
import com.ospdf.reader.data.local.SyncStatus
import com.ospdf.reader.ui.components.ErrorMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Cloud sync screen showing Google Drive integration status and files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    authState: AuthState,
    items: List<CloudFileItem>,
    isLoading: Boolean,
    onSignIn: () -> Intent,
    onSignInResult: suspend (Intent?) -> Unit,
    onSignOut: () -> Unit,
    onRefresh: () -> Unit,
    onFileClick: (CloudFileItem) -> Unit,
    onUploadClick: () -> Unit,
    onForceSync: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    onDismissError: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                onSignInResult(result.data)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Sync") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (authState is AuthState.SignedIn) {
                        IconButton(onClick = onForceSync) {
                            Icon(Icons.Filled.Sync, "Force Sync")
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, "Refresh")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (authState is AuthState.SignedIn) {
                FloatingActionButton(onClick = onUploadClick) {
                    Icon(Icons.Filled.Upload, "Upload PDF")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Account status card
            AccountStatusCard(
                authState = authState,
                onSignIn = { signInLauncher.launch(onSignIn()) },
                onSignOut = onSignOut
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Content based on auth state
            when (authState) {
                is AuthState.NotSignedIn -> {
                    NotSignedInContent()
                }
                is AuthState.SigningIn -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is AuthState.SignedIn -> {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (items.isEmpty()) {
                        EmptyDriveContent()
                    } else {
                        DriveFilesList(
                            files = items,
                            onFileClick = onFileClick,
                            onForceFileUpload = onForceSync
                        )
                    }
                }
                is AuthState.Error -> {
                    ErrorContent(message = authState.message)
                }
            }
            
            if (error != null) {
                ErrorMessage(message = error, onDismiss = onDismissError)
            }
        }
    }
}

@Composable
private fun AccountStatusCard(
    authState: AuthState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Google Drive icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Google Drive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = when (authState) {
                        is AuthState.SignedIn -> authState.account.email ?: "Connected"
                        is AuthState.SigningIn -> "Signing in..."
                        is AuthState.Error -> "Error connecting"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Action button
            when (authState) {
                is AuthState.SignedIn -> {
                    OutlinedButton(onClick = onSignOut) {
                        Text("Sign Out")
                    }
                }
                is AuthState.NotSignedIn, is AuthState.Error -> {
                    Button(onClick = onSignIn) {
                        Text("Sign In")
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun NotSignedInContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sign in to sync your PDFs",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Your annotated PDFs will be backed up to Google Drive",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyDriveContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(
            Icons.Outlined.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No PDFs in Drive yet",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Tap + to upload your first PDF",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(
            Icons.Outlined.Error,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun DriveFilesList(
    files: List<CloudFileItem>,
    onFileClick: (CloudFileItem) -> Unit,
    onForceFileUpload: () -> Unit
) {
    Text(
        text = "Your PDFs",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    Spacer(modifier = Modifier.height(8.dp))
    
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files) { file ->
            DriveFileItem(
                file = file, 
                onClick = { onFileClick(file) },
                onForceFileUpload = onForceFileUpload
            )
        }
    }
}

@Composable
private fun DriveFileItem(
    file: CloudFileItem,
    onClick: () -> Unit,
    onForceFileUpload: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val syncStatus = file.syncStatus
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.PictureAsPdf,
                contentDescription = null,
                tint = Color(0xFFE53935),
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    // Removed modified time checking for simplicity or add back if CloudFileItem has it
                    // file.size check
                    Text(
                        text = formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            when (syncStatus) {
                SyncStatus.PENDING_UPLOAD -> {
                    IconButton(onClick = onForceFileUpload) {
                        Icon(
                            Icons.Filled.CloudUpload,
                            contentDescription = "Upload Changes",
                            tint = Color(0xFFFF9800) // Orange
                        )
                    }
                }
                SyncStatus.SYNCED -> {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Synced",
                        tint = Color(0xFF4CAF50) // Green
                    )
                }
                SyncStatus.UPLOADING, SyncStatus.DOWNLOADING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                else -> {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
