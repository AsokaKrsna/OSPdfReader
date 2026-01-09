package com.ospdf.reader.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ospdf.reader.data.cloud.GoogleDriveAuth
import com.ospdf.reader.data.cloud.GoogleDriveSync
import com.ospdf.reader.data.export.PdfExporter
import com.ospdf.reader.data.local.RecentDocumentDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
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
    private val exporter: PdfExporter,
    private val recentDocumentDao: RecentDocumentDao
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "pdf_sync_work"
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
         * Creates a periodic sync work request.
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
    }
    
    override suspend fun doWork(): Result {
        // Check if signed in
        if (!driveAuth.isSignedIn()) {
            return Result.failure()
        }
        
        val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_FULL
        
        return when (syncType) {
            SYNC_TYPE_UPLOAD -> handleUpload()
            SYNC_TYPE_DOWNLOAD -> handleDownload()
            SYNC_TYPE_FULL -> handleFullSync()
            else -> Result.failure()
        }
    }
    
    private suspend fun handleUpload(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH)
            ?: return Result.failure()
        
        val file = File(filePath)
        if (!file.exists()) {
            return Result.failure()
        }
        
        val result = driveSync.uploadPdf(file)
        return if (result.success) {
            Result.success()
        } else {
            Result.retry()
        }
    }
    
    private suspend fun handleDownload(): Result {
        // Download implementation for specific file
        return Result.success()
    }
    
    private suspend fun handleFullSync(): Result {
        try {
            // Get recent documents that need syncing
            val recentDocs = recentDocumentDao.getRecentDocuments().first()
            
            // Upload each document that has been modified
            for (doc in recentDocs) {
                val file = File(doc.path)
                if (file.exists()) {
                    // Check if file is a flattened export
                    if (doc.path.contains("_annotated") || doc.path.contains("_flattened")) {
                        driveSync.uploadPdf(file)
                    }
                }
            }
            
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
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
    }
    
    /**
     * Stops periodic sync.
     */
    fun stopPeriodicSync() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
    }
    
    /**
     * Gets the current sync status.
     */
    fun getSyncStatus() = workManager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORK_NAME)
    
    /**
     * Triggers an immediate sync.
     */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.KEY_SYNC_TYPE to SyncWorker.SYNC_TYPE_FULL))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        
        workManager.enqueue(request)
    }
}
