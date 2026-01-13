package com.ospdf.reader.ui.browser

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospdf.reader.data.cloud.GoogleDriveAuth
import com.ospdf.reader.data.local.RecentDocumentsRepository
import com.ospdf.reader.domain.model.PdfDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the file browser screen.
 */
data class FileBrowserUiState(
    val recentFiles: List<PdfDocument> = emptyList(),
    val isLoading: Boolean = false,
    val isSignedInToDrive: Boolean = false
)

/**
 * ViewModel for the file browser screen.
 */
@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val recentRepository: RecentDocumentsRepository,
    private val driveAuth: GoogleDriveAuth
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()
    
    init {
        recentRepository.recentDocuments()
            .onEach { recents ->
                _uiState.value = _uiState.value.copy(
                    recentFiles = recents,
                    isLoading = false
                )
            }
            .launchIn(viewModelScope)
        
        // Observe Drive auth state
        driveAuth.authState
            .onEach { authState ->
                _uiState.value = _uiState.value.copy(
                    isSignedInToDrive = driveAuth.isSignedIn()
                )
            }
            .launchIn(viewModelScope)
        
        // Try silent sign-in on startup
        viewModelScope.launch {
            driveAuth.silentSignIn()
        }
    }
    
    fun addToRecentFiles(document: PdfDocument) {
        viewModelScope.launch {
            recentRepository.recordOpen(document)
        }
    }
}
