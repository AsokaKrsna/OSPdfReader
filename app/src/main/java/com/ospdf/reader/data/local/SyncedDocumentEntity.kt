package com.ospdf.reader.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Sync status for tracked documents.
 */
enum class SyncStatus {
    SYNCED,           // Local and Drive are in sync
    PENDING_UPLOAD,   // Local changes need to be uploaded
    PENDING_DOWNLOAD, // Drive has newer version
    CONFLICT,         // Both local and Drive modified
    UPLOADING,        // Currently uploading
    DOWNLOADING,      // Currently downloading
    ERROR             // Sync failed
}

/**
 * Entity representing a PDF document that syncs with Google Drive.
 * Only documents in this table will sync - others are offline-only.
 */
@Entity(
    tableName = "synced_documents",
    indices = [
        Index(value = ["local_path"], unique = true),
        Index(value = ["drive_file_id"]),
        Index(value = ["sync_status"])
    ]
)
data class SyncedDocumentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "local_path")
    val localPath: String, // Path in sync folder
    
    @ColumnInfo(name = "file_name")
    val fileName: String,
    
    @ColumnInfo(name = "drive_file_id")
    val driveFileId: String? = null, // null if not yet uploaded
    
    @ColumnInfo(name = "local_modified_at")
    val localModifiedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "drive_modified_at")
    val driveModifiedAt: Long? = null,
    
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,
    
    @ColumnInfo(name = "last_sync_at")
    val lastSyncAt: Long? = null,
    
    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0,
    
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * DAO for synced document operations.
 */
@Dao
interface SyncedDocumentDao {
    
    /**
     * Get all synced documents.
     */
    @Query("SELECT * FROM synced_documents ORDER BY file_name ASC")
    fun getAllSyncedDocuments(): Flow<List<SyncedDocumentEntity>>
    
    /**
     * Get documents by sync status.
     */
    @Query("SELECT * FROM synced_documents WHERE sync_status = :status")
    suspend fun getDocumentsByStatus(status: SyncStatus): List<SyncedDocumentEntity>
    
    /**
     * Get documents pending upload.
     */
    @Query("SELECT * FROM synced_documents WHERE sync_status = :uploadStatus OR sync_status = :conflictStatus")
    suspend fun getPendingUploads(
        uploadStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,
        conflictStatus: SyncStatus = SyncStatus.CONFLICT
    ): List<SyncedDocumentEntity>
    
    /**
     * Get document by local path.
     */
    @Query("SELECT * FROM synced_documents WHERE local_path = :localPath")
    suspend fun getByLocalPath(localPath: String): SyncedDocumentEntity?
    
    /**
     * Get document by Drive file ID.
     */
    @Query("SELECT * FROM synced_documents WHERE drive_file_id = :driveFileId")
    suspend fun getByDriveFileId(driveFileId: String): SyncedDocumentEntity?
    
    /**
     * Insert or update a synced document.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: SyncedDocumentEntity)
    
    /**
     * Update sync status.
     */
    @Query("UPDATE synced_documents SET sync_status = :status, error_message = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: SyncStatus, error: String? = null)
    
    /**
     * Update after successful upload.
     */
    @Query("""
        UPDATE synced_documents 
        SET sync_status = :status, 
            drive_file_id = :driveFileId,
            drive_modified_at = :driveModifiedAt,
            last_sync_at = :lastSyncAt,
            error_message = null
        WHERE id = :id
    """)
    suspend fun updateAfterUpload(
        id: String,
        driveFileId: String,
        driveModifiedAt: Long,
        lastSyncAt: Long = System.currentTimeMillis(),
        status: SyncStatus = SyncStatus.SYNCED
    )
    
    /**
     * Update local modification timestamp.
     */
    @Query("SELECT * FROM synced_documents")
    suspend fun getAllDocumentsSnapshot(): List<SyncedDocumentEntity>

    /**
     * Update local modification timestamp.
     */
    @Query("UPDATE synced_documents SET local_modified_at = :timestamp, sync_status = :status WHERE local_path = :localPath")
    suspend fun markModified(
        localPath: String,
        timestamp: Long = System.currentTimeMillis(),
        status: SyncStatus = SyncStatus.PENDING_UPLOAD
    ): Int
    
    /**
     * Delete a synced document entry.
     */
    @Delete
    suspend fun delete(document: SyncedDocumentEntity)
    
    /**
     * Delete by local path.
     */
    @Query("DELETE FROM synced_documents WHERE local_path = :localPath")
    suspend fun deleteByPath(localPath: String)
    
    /**
     * Count documents by status.
     */
    @Query("SELECT COUNT(*) FROM synced_documents WHERE sync_status = :status")
    suspend fun countByStatus(status: SyncStatus): Int
    
    /**
     * Check if a document is synced.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM synced_documents WHERE local_path = :localPath)")
    suspend fun isSynced(localPath: String): Boolean
}
