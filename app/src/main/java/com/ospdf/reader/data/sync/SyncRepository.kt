package com.ospdf.reader.data.sync

import android.content.Context
import com.ospdf.reader.data.cloud.GoogleDriveAuth
import com.ospdf.reader.data.cloud.GoogleDriveSync
import com.ospdf.reader.data.local.SyncedDocumentDao
import com.ospdf.reader.data.local.SyncedDocumentEntity
import com.ospdf.reader.data.local.SyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing synced documents.
 * Handles sync folder operations, document tracking, and sync orchestration.
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncedDocumentDao: SyncedDocumentDao,
    private val driveSync: GoogleDriveSync,
    private val driveAuth: GoogleDriveAuth
) {
    companion object {
        private const val SYNC_FOLDER_NAME = "sync_pdfs"
    }
    
    /**
     * Gets the sync folder directory, creating it if needed.
     */
    fun getSyncFolder(): File {
        val folder = File(context.filesDir, SYNC_FOLDER_NAME)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }
    
    /**
     * Gets all synced documents as a Flow.
     */
    fun getAllSyncedDocuments(): Flow<List<SyncedDocumentEntity>> {
        return syncedDocumentDao.getAllSyncedDocuments()
    }
    
    /**
     * Checks if a document path is in the sync folder.
     */
    fun isInSyncFolder(path: String): Boolean {
        return path.startsWith(getSyncFolder().absolutePath)
    }
    
    /**
     * Imports a PDF file into the sync folder for Drive sync.
     * Copies the file and creates a database entry.
     */
    suspend fun importToSyncFolder(sourceFile: File): Result<SyncedDocumentEntity> = 
        withContext(Dispatchers.IO) {
            try {
                val syncFolder = getSyncFolder()
                val destFile = File(syncFolder, sourceFile.name)
                
                // Handle name collision
                val finalDest = if (destFile.exists()) {
                    val nameWithoutExt = sourceFile.nameWithoutExtension
                    val ext = sourceFile.extension
                    var counter = 1
                    var newFile: File
                    do {
                        newFile = File(syncFolder, "${nameWithoutExt}_$counter.$ext")
                        counter++
                    } while (newFile.exists())
                    newFile
                } else {
                    destFile
                }
                
                // Copy file
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(finalDest).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Create database entry
                val entity = SyncedDocumentEntity(
                    localPath = finalDest.absolutePath,
                    fileName = finalDest.name,
                    fileSize = finalDest.length(),
                    syncStatus = SyncStatus.PENDING_UPLOAD
                )
                
                syncedDocumentDao.upsert(entity)
                
                Result.success(entity)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    /**
     * Downloads a file from Drive and adds it to the sync folder.
     */
    suspend fun importFromDrive(driveFileId: String, fileName: String): Result<SyncedDocumentEntity> =
        withContext(Dispatchers.IO) {
            try {
                val syncFolder = getSyncFolder()
                var destFile = File(syncFolder, fileName)
                
                // Handle name collision
                if (destFile.exists()) {
                    val nameWithoutExt = destFile.nameWithoutExtension
                    val ext = destFile.extension
                    var counter = 1
                    do {
                        destFile = File(syncFolder, "${nameWithoutExt}_$counter.$ext")
                        counter++
                    } while (destFile.exists())
                }
                
                // Download from Drive
                val result = driveSync.downloadPdf(driveFileId, destFile.absolutePath)
                if (!result.success) {
                    return@withContext Result.failure(Exception(result.error ?: "Download failed"))
                }
                
                // Create database entry
                val entity = SyncedDocumentEntity(
                    localPath = destFile.absolutePath,
                    fileName = destFile.name,
                    driveFileId = driveFileId,
                    fileSize = destFile.length(),
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncAt = System.currentTimeMillis(),
                    driveModifiedAt = System.currentTimeMillis()
                )
                
                syncedDocumentDao.upsert(entity)
                
                Result.success(entity)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    /**
     * Marks a document as modified (needing sync).
     */
    suspend fun markModified(localPath: String) {
        syncedDocumentDao.markModified(localPath)
    }
    
    /**
     * Removes a document from sync tracking.
     * Optionally deletes the local file.
     */
    suspend fun removeFromSync(localPath: String, deleteFile: Boolean = false) {
        syncedDocumentDao.deleteByPath(localPath)
        if (deleteFile) {
            File(localPath).delete()
        }
    }
    
    /**
     * Gets documents that need to be uploaded.
     */
    suspend fun getPendingUploads(): List<SyncedDocumentEntity> {
        return syncedDocumentDao.getPendingUploads()
    }
    
    /**
     * Uploads a single document to Drive.
     */
    suspend fun uploadDocument(entity: SyncedDocumentEntity): Result<SyncedDocumentEntity> =
        withContext(Dispatchers.IO) {
            try {
                // Check if signed in
                if (!driveAuth.isSignedIn()) {
                    return@withContext Result.failure(Exception("Not signed in"))
                }
                
                // Update status to uploading
                syncedDocumentDao.updateStatus(entity.id, SyncStatus.UPLOADING)
                
                val localFile = File(entity.localPath)
                if (!localFile.exists()) {
                    syncedDocumentDao.updateStatus(entity.id, SyncStatus.ERROR, "File not found")
                    return@withContext Result.failure(Exception("File not found"))
                }
                
                val result = driveSync.uploadPdf(localFile)
                
                if (result.success && result.fileId != null) {
                    syncedDocumentDao.updateAfterUpload(
                        id = entity.id,
                        driveFileId = result.fileId,
                        driveModifiedAt = System.currentTimeMillis()
                    )
                    
                    val updated = syncedDocumentDao.getByLocalPath(entity.localPath)
                    Result.success(updated ?: entity.copy(syncStatus = SyncStatus.SYNCED))
                } else {
                    syncedDocumentDao.updateStatus(entity.id, SyncStatus.ERROR, result.error)
                    Result.failure(Exception(result.error ?: "Upload failed"))
                }
            } catch (e: Exception) {
                syncedDocumentDao.updateStatus(entity.id, SyncStatus.ERROR, e.message)
                Result.failure(e)
            }
        }
    
    /**
     * Syncs all pending documents.
     * Returns count of successfully synced documents.
     */
    suspend fun syncAllPending(): Int {
        var successCount = 0
        val pending = getPendingUploads()
        
        for (doc in pending) {
            val result = uploadDocument(doc)
            if (result.isSuccess) {
                successCount++
            }
        }
        
        return successCount
    }
    
    /**
     * Checks if there are any pending uploads.
     */
    suspend fun hasPendingUploads(): Boolean {
        return syncedDocumentDao.countByStatus(SyncStatus.PENDING_UPLOAD) > 0
    }
    
    /**
     * Gets count of pending uploads.
     */
    suspend fun getPendingUploadCount(): Int {
        return syncedDocumentDao.countByStatus(SyncStatus.PENDING_UPLOAD)
    }
    
    /**
     * Checks if user is signed in to Drive.
     */
    fun isSignedIn(): Boolean {
        return driveAuth.isSignedIn()
    }
}
