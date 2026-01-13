package com.ospdf.reader.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ospdf.reader.data.cloud.GoogleDriveAuth
import com.ospdf.reader.data.cloud.GoogleDriveSync
import com.ospdf.reader.data.local.SyncedDocumentDao
import com.ospdf.reader.data.local.SyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for background sync with Google Drive.
 * Implements offline-first architecture - works locally, syncs when online.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val driveAuth: GoogleDriveAuth,
    private val driveSync: GoogleDriveSync,
    private val syncedDocumentDao: SyncedDocumentDao
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "pdf_sync_work"
        const val WORK_NAME_IMMEDIATE = "pdf_sync_immediate"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_SYNC_TYPE = "sync_type"
        
        const val SYNC_TYPE_UPLOAD = "upload"
        const val SYNC_TYPE_DOWNLOAD = "download"
        const val SYNC_TYPE_FULL = "full"
        
        /**
         * Creates a one-time upload work request.
         */
        fun createUploadRequest(filePath: String): OneTimeWorkRequest {
            val data = workDataOf(
                KEY_FILE_PATH to filePath,
                KEY_SYNC_TYPE to SYNC_TYPE_UPLOAD
            )
            
            return OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.MINUTES
                )
                .build()
        }
        
        /**
         * Creates a periodic sync work request (every 15 minutes).
         */
        fun createPeriodicSyncRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setInputData(workDataOf(KEY_SYNC_TYPE to SYNC_TYPE_FULL))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
        }
        
        /**
         * Creates an immediate sync request.
         */
        fun createImmediateSyncRequest(): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(KEY_SYNC_TYPE to SYNC_TYPE_FULL))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        }
    }
    
    override suspend fun doWork(): Result {
        // Check if signed in
        if (!driveAuth.isSignedIn()) {
            android.util.Log.d("SyncWorker", "Not signed in, skipping sync")
            return Result.success() // Don't retry if not signed in
        }
        
        val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_FULL
        
        return when (syncType) {
            SYNC_TYPE_UPLOAD -> handleSingleUpload()
            SYNC_TYPE_FULL -> handleFullSync()
            else -> Result.success()
        }
    }
    
    /**
     * Handles uploading a single file.
     */
    private suspend fun handleSingleUpload(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        
        val document = syncedDocumentDao.getByLocalPath(filePath)
            ?: return Result.failure()
        
        val file = File(filePath)
        if (!file.exists()) {
            syncedDocumentDao.updateStatus(document.id, SyncStatus.ERROR, "File not found")
            return Result.failure()
        }
        
        // Update status to uploading
        syncedDocumentDao.updateStatus(document.id, SyncStatus.UPLOADING)
        
        val result = driveSync.uploadPdf(file)
        
        return if (result.success && result.fileId != null) {
            syncedDocumentDao.updateAfterUpload(
                id = document.id,
                driveFileId = result.fileId,
                driveModifiedAt = System.currentTimeMillis()
            )
            android.util.Log.d("SyncWorker", "Uploaded: ${file.name}")
            Result.success()
        } else {
            syncedDocumentDao.updateStatus(document.id, SyncStatus.ERROR, result.error)
            android.util.Log.e("SyncWorker", "Upload failed: ${result.error}")
            Result.retry()
        }
    }
    
    /**
     * Handles full sync - uploads all pending documents.
     */
    private suspend fun handleFullSync(): Result {
        android.util.Log.d("SyncWorker", "Starting full sync")
        
        try {
            // Get all pending uploads
            val pending = syncedDocumentDao.getPendingUploads()
            android.util.Log.d("SyncWorker", "Found ${pending.size} pending documents")
            
            var successCount = 0
            var failCount = 0
            
            for (doc in pending) {
                val file = File(doc.localPath)
                if (!file.exists()) {
                    syncedDocumentDao.updateStatus(doc.id, SyncStatus.ERROR, "File not found")
                    failCount++
                    continue
                }
                
                syncedDocumentDao.updateStatus(doc.id, SyncStatus.UPLOADING)
                
                val result = driveSync.uploadPdf(file)
                
                if (result.success && result.fileId != null) {
                    syncedDocumentDao.updateAfterUpload(
                        id = doc.id,
                        driveFileId = result.fileId,
                        driveModifiedAt = System.currentTimeMillis()
                    )
                    successCount++
                    android.util.Log.d("SyncWorker", "Uploaded: ${file.name}")
                } else {
                    syncedDocumentDao.updateStatus(doc.id, SyncStatus.PENDING_UPLOAD, result.error)
                    failCount++
                    android.util.Log.e("SyncWorker", "Failed to upload ${file.name}: ${result.error}")
                }
            }
            
            android.util.Log.d("SyncWorker", "Sync complete: $successCount success, $failCount failed")
            
            // Return retry if there were failures, success otherwise
            return if (failCount > 0 && successCount == 0) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync error", e)
            return Result.retry()
        }
    }
    
    override suspend fun getForegroundInfo(): ForegroundInfo {
        // For expedited work on older Android versions
        val notification = androidx.core.app.NotificationCompat.Builder(
            applicationContext,
            "sync_channel"
        )
            .setContentTitle("Syncing PDFs")
            .setContentText("Uploading to Google Drive...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()
        
        return ForegroundInfo(1001, notification)
    }
}

/**
 * Manager for scheduling and controlling sync operations.
 */
class SyncManager(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * Schedules a one-time upload for a file.
     */
    fun scheduleUpload(filePath: String) {
        val request = SyncWorker.createUploadRequest(filePath)
        workManager.enqueue(request)
    }
    
    /**
     * Starts periodic background sync.
     */
    fun startPeriodicSync() {
        val request = SyncWorker.createPeriodicSyncRequest()
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        android.util.Log.d("SyncManager", "Started periodic sync")
    }
    
    /**
     * Stops periodic sync.
     */
    fun stopPeriodicSync() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
        android.util.Log.d("SyncManager", "Stopped periodic sync")
    }
    
    /**
     * Triggers an immediate sync.
     */
    fun syncNow() {
        val request = SyncWorker.createImmediateSyncRequest()
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            request
        )
        android.util.Log.d("SyncManager", "Triggered immediate sync")
    }
    
    /**
     * Gets the current sync work status.
     */
    fun getSyncStatus() = workManager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORK_NAME)
}
