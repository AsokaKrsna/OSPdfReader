package com.ospdf.reader.ui.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.services.drive.model.File as DriveFile
import com.ospdf.reader.data.cloud.AuthState
import com.ospdf.reader.data.cloud.GoogleDriveAuth
import com.ospdf.reader.data.cloud.GoogleDriveSync
import com.ospdf.reader.data.sync.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class CloudSyncUiState(
    val driveFiles: List<DriveFile> = emptyList(),
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadingFileName: String? = null,
    val isUploading: Boolean = false,
    val uploadingFileName: String? = null,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveAuth: GoogleDriveAuth,
    private val driveSync: GoogleDriveSync,
    private val syncRepository: SyncRepository
) : ViewModel() {
    
    val authState: StateFlow<AuthState> = driveAuth.authState
    
    private val _uiState = MutableStateFlow(CloudSyncUiState())
    val uiState: StateFlow<CloudSyncUiState> = _uiState.asStateFlow()
    
    init {
        // Try silent sign-in and load files if already signed in
        viewModelScope.launch {
            driveAuth.silentSignIn()
            if (driveAuth.isSignedIn()) {
                loadDriveFiles()
            }
        }
    }
    
    fun getSignInIntent(): Intent = driveAuth.getSignInIntent()
    
    suspend fun handleSignInResult(data: Intent?) {
        val result = driveAuth.handleSignInResult(data)
        if (result.isSuccess) {
            loadDriveFiles()
        }
    }
    
    fun signOut() {
        viewModelScope.launch {
            driveAuth.signOut()
            _uiState.value = CloudSyncUiState()
        }
    }
    
    fun loadDriveFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val files = driveSync.listPdfs()
                _uiState.value = _uiState.value.copy(
                    driveFiles = files,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load files: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Uploads a file from the device to Google Drive.
     */
    fun uploadFile(uri: Uri) {
        viewModelScope.launch {
            val fileName = getFileNameFromUri(uri) ?: "document.pdf"
            
            _uiState.value = _uiState.value.copy(
                isUploading = true,
                uploadingFileName = fileName
            )
            
            try {
                // Copy file to a temp location first
                val tempFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Upload to Drive
                val result = driveSync.uploadPdf(tempFile)
                
                // Clean up temp file
                tempFile.delete()
                
                if (result.success) {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        uploadingFileName = null,
                        successMessage = "Uploaded: $fileName"
                    )
                    // Refresh file list
                    loadDriveFiles()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        uploadingFileName = null,
                        error = "Upload failed: ${result.error}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadingFileName = null,
                    error = "Upload failed: ${e.message}"
                )
            }
        }
    }
    
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Downloads a file from Drive and adds it to sync folder.
     * Returns the local path if successful.
     */
    fun downloadAndOpenFile(
        driveFile: DriveFile,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadingFileName = driveFile.name
            )
            
            try {
                val result = syncRepository.importFromDrive(
                    driveFileId = driveFile.id,
                    fileName = driveFile.name ?: "document.pdf"
                )
                
                result.fold(
                    onSuccess = { entity ->
                        _uiState.value = _uiState.value.copy(
                            isDownloading = false,
                            downloadingFileName = null,
                            successMessage = "Downloaded: ${entity.fileName}"
                        )
                        onSuccess(entity.localPath)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isDownloading = false,
                            downloadingFileName = null,
                            error = "Download failed: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadingFileName = null,
                    error = "Download failed: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
