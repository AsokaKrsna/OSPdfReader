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
import com.ospdf.reader.data.local.SyncStatus
import com.ospdf.reader.data.local.SyncedDocumentEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class CloudFileItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String = "application/pdf",
    val syncStatus: SyncStatus? = null,
    val localPath: String? = null,
    val driveFileId: String? = null
)

data class CloudSyncUiState(
    val items: List<CloudFileItem> = emptyList(),
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadingFileName: String? = null,
    val isUploading: Boolean = false,
    val uploadingFileName: String? = null,
    val error: String? = null,
    val successMessage: String? = null,
    val isSyncing: Boolean = false
)

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveAuth: GoogleDriveAuth,
    private val driveSync: GoogleDriveSync,
    private val syncRepository: SyncRepository
) : ViewModel() {
    
    private val _driveFiles = MutableStateFlow<List<DriveFile>>(emptyList())
    private val _localDocs = MutableStateFlow<List<SyncedDocumentEntity>>(emptyList())
    
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
        
        // Observe local docs
        syncRepository.getAllSyncedDocuments()
            .onEach { _localDocs.value = it }
            .launchIn(viewModelScope)
            
        // Combine flows to build UI state
        kotlinx.coroutines.flow.combine(_driveFiles, _localDocs) { driveFiles, localDocs ->
            mergeFiles(driveFiles, localDocs)
        }.onEach { mergedItems ->
            _uiState.update { it.copy(items = mergedItems) }
        }.launchIn(viewModelScope)
    }
    
    private fun mergeFiles(driveFiles: List<DriveFile>, localDocs: List<SyncedDocumentEntity>): List<CloudFileItem> {
        val localMap = localDocs.associateBy { it.driveFileId }
        val mergedList = mutableListOf<CloudFileItem>()
        val processedDriveIds = mutableSetOf<String>()
        
        // Process remote files
        for (driveFile in driveFiles) {
            val localDoc = localMap[driveFile.id]
            processedDriveIds.add(driveFile.id)
            
            mergedList.add(CloudFileItem(
                id = driveFile.id, // Use Drive ID
                name = driveFile.name ?: "Unknown",
                size = driveFile.getSize() ?: 0L,
                mimeType = driveFile.mimeType ?: "application/pdf",
                syncStatus = localDoc?.syncStatus,
                localPath = localDoc?.localPath,
                driveFileId = driveFile.id
            ))
        }
        
        // Process local-only files (Pending Upload or Disappeared from Remote)
        for (doc in localDocs) {
            if (doc.driveFileId == null || !processedDriveIds.contains(doc.driveFileId)) {
                mergedList.add(CloudFileItem(
                    id = doc.id, // Use Local UUID since Drive ID might be null
                    name = doc.fileName,
                    size = doc.fileSize,
                    syncStatus = doc.syncStatus,
                    localPath = doc.localPath,
                    driveFileId = doc.driveFileId
                ))
            }
        }
        
        return mergedList.sortedBy { it.name.lowercase() }
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
                _driveFiles.value = files
                _uiState.value = _uiState.value.copy(isLoading = false)
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
        fileItem: CloudFileItem,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadingFileName = fileItem.name
            )
            
            try {
                val result = syncRepository.importFromDrive(
                    driveFileId = fileItem.driveFileId ?: fileItem.id, // Use driveId if available, else id
                    fileName = fileItem.name
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
    
    fun forceSync() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            try {
                // Upload pending changes
                syncRepository.syncAllPending()
                
                // Refresh list
                loadDriveFiles()
                
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    successMessage = "Sync completed"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    error = "Sync failed: ${e.message}"
                )
            }
        }
    }
}
