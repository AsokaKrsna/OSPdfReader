package com.ospdf.reader.ui.cloud

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Route composable that uses ViewModel and handles navigation.
 */
@Composable
fun CloudSyncRoute(
    onBack: () -> Unit,
    onOpenFile: (Uri) -> Unit,
    viewModel: CloudSyncViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // File picker launcher for uploading PDFs
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadFile(it) }
    }
    
    // Show error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    // Show success snackbar
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSuccessMessage()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        CloudSyncScreen(
            authState = authState,
            driveFiles = uiState.driveFiles,
            isLoading = uiState.isLoading,
            onSignIn = { viewModel.getSignInIntent() },
            onSignInResult = { data -> viewModel.handleSignInResult(data) },
            onSignOut = { viewModel.signOut() },
            onRefresh = { viewModel.loadDriveFiles() },
            onFileClick = { driveFile ->
                viewModel.downloadAndOpenFile(driveFile) { localPath ->
                    // Convert local path to Uri and open
                    onOpenFile(Uri.parse("file://$localPath"))
                }
            },
            onUploadClick = { 
                filePickerLauncher.launch(arrayOf("application/pdf"))
            },
            onBack = onBack
        )
        
        // Download/Upload progress overlay
        if (uiState.isDownloading || uiState.isUploading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 8.dp,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(if (uiState.isUploading) "Uploading..." else "Downloading...")
                        (uiState.uploadingFileName ?: uiState.downloadingFileName)?.let { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
