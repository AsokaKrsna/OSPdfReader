package com.ospdf.reader.domain.model

import android.net.Uri

/**
 * Represents a PDF document with its metadata.
 */
data class PdfDocument(
    val id: Long = 0,
    val uri: Uri,
    val name: String,
    val path: String,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val fileSize: Long = 0,
    val lastOpened: Long = System.currentTimeMillis(),
    val lastModified: Long = 0,
    val thumbnailPath: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)

/**
 * Sync status for Google Drive integration.
 */
enum class SyncStatus {
    LOCAL_ONLY,      // File exists only locally
    SYNCED,          // File is synced with Drive
    PENDING_UPLOAD,  // Changes pending upload
    PENDING_DOWNLOAD // New version available on Drive
}

/**
 * Represents a bookmark in a PDF document.
 */
data class Bookmark(
    val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Reading mode options.
 */
enum class ReadingMode {
    HORIZONTAL_SWIPE,  // Book-style page flip
    VERTICAL_SCROLL    // Continuous scroll
}

/**
 * Represents a PDF page with its rendered bitmap.
 */
data class PdfPage(
    val pageNumber: Int,
    val width: Int,
    val height: Int,
    val x: Int = 0,
    val y: Int = 0
)
