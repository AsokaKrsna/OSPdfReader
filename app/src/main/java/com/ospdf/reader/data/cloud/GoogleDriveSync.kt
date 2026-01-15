package com.ospdf.reader.data.cloud

import com.google.api.client.http.FileContent
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of a sync operation.
 */
data class SyncResult(
    val success: Boolean,
    val fileId: String? = null,
    val fileName: String? = null,
    val error: String? = null
)

/**
 * Manages syncing PDFs to/from Google Drive.
 * Implements offline-first architecture with background sync.
 */
@Singleton
class GoogleDriveSync @Inject constructor(
    private val auth: GoogleDriveAuth
) {
    companion object {
        private const val FOLDER_NAME = "OSPdfReader"
        private const val MIME_TYPE_PDF = "application/pdf"
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
    }
    
    private var appFolderId: String? = null
    
    /**
     * Uploads a PDF file to Google Drive.
     */
    suspend fun uploadPdf(localFile: File, existingFileId: String? = null): SyncResult = withContext(Dispatchers.IO) {
        try {
            val drive = auth.getDriveService()
                ?: return@withContext SyncResult(false, error = "Not signed in to Google Drive")
            
            val mediaContent = FileContent(MIME_TYPE_PDF, localFile)
            
            // Case 1: Update existing file by ID (if provided)
            if (existingFileId != null) {
                val fileMetadata = DriveFile().apply {
                    name = localFile.name
                }
                
                val updatedFile = drive.files().update(existingFileId, fileMetadata, mediaContent)
                    .setFields("id, name")
                    .execute()
                    
                return@withContext SyncResult(
                    success = true,
                    fileId = updatedFile.id,
                    fileName = updatedFile.name
                )
            }
            
            // Case 2: New upload or find by name in app folder
            // Ensure app folder exists
            val folderId = getOrCreateAppFolder()
                ?: return@withContext SyncResult(false, error = "Failed to create app folder")
            
            // Check if file already exists in app folder
            val existingFile = findFile(localFile.name, folderId)
            
            val fileMetadata = DriveFile().apply {
                name = localFile.name
                parents = listOf(folderId)
            }
            
            val uploadedFile = if (existingFile != null) {
                // Update existing file in app folder
                drive.files().update(existingFile.id, fileMetadata, mediaContent)
                    .setFields("id, name")
                    .execute()
            } else {
                // Create new file
                drive.files().create(fileMetadata, mediaContent)
                    .setFields("id, name")
                    .execute()
            }
            
            SyncResult(
                success = true,
                fileId = uploadedFile.id,
                fileName = uploadedFile.name
            )
        } catch (e: Exception) {
            e.printStackTrace()
            SyncResult(false, error = "Upload failed: ${e.message}")
        }
    }
    
    /**
     * Downloads a PDF file from Google Drive.
     */
    suspend fun downloadPdf(fileId: String, localPath: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            val drive = auth.getDriveService()
                ?: return@withContext SyncResult(false, error = "Not signed in to Google Drive")
            
            val outputStream = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            
            val localFile = File(localPath)
            FileOutputStream(localFile).use { fos ->
                outputStream.writeTo(fos)
            }
            
            val file = drive.files().get(fileId).setFields("name").execute()
            
            SyncResult(
                success = true,
                fileId = fileId,
                fileName = file.name
            )
        } catch (e: Exception) {
            e.printStackTrace()
            SyncResult(false, error = "Download failed: ${e.message}")
        }
    }
    
    /**
     * Lists all PDFs in the app folder.
     */
    suspend fun listPdfs(): List<DriveFile> = withContext(Dispatchers.IO) {
        try {
            val drive = auth.getDriveService() ?: return@withContext emptyList()
            
            // Search globally for PDFs
            val result = drive.files().list()
                .setQ("mimeType='$MIME_TYPE_PDF' and trashed=false")
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime, size)")
                .execute()
            
            result.files ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Deletes a file from Google Drive.
     */
    suspend fun deleteFile(fileId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive = auth.getDriveService() ?: return@withContext false
            drive.files().delete(fileId).execute()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Gets or creates the app folder in Google Drive.
     */
    private suspend fun getOrCreateAppFolder(): String? {
        if (appFolderId != null) return appFolderId
        
        try {
            val drive = auth.getDriveService() ?: return null
            
            // Search for existing folder
            val result = drive.files().list()
                .setQ("mimeType='$MIME_TYPE_FOLDER' and name='$FOLDER_NAME' and trashed=false")
                .setSpaces("drive")
                .setFields("files(id)")
                .execute()
            
            val existingFolder = result.files?.firstOrNull()
            
            appFolderId = if (existingFolder != null) {
                existingFolder.id
            } else {
                // Create new folder
                val folderMetadata = DriveFile().apply {
                    name = FOLDER_NAME
                    mimeType = MIME_TYPE_FOLDER
                }
                
                val folder = drive.files().create(folderMetadata)
                    .setFields("id")
                    .execute()
                
                folder.id
            }
            
            return appFolderId
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Finds a file by name in a folder.
     */
    private fun findFile(fileName: String, folderId: String): DriveFile? {
        return try {
            val drive = auth.getDriveService() ?: return null
            
            // Sanitize file name to prevent query injection
            val safeFileName = escapeForDriveQuery(fileName)
            val safeFolderId = escapeForDriveQuery(folderId)
            
            val result = drive.files().list()
                .setQ("'$safeFolderId' in parents and name='$safeFileName' and trashed=false")
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            
            result.files?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Escapes special characters for use in Google Drive API queries.
     */
    private fun escapeForDriveQuery(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
    }
}
