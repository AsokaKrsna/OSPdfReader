package com.ospdf.reader.data.local

import android.net.Uri
import com.ospdf.reader.domain.model.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentDocumentsRepository @Inject constructor(
    private val dao: RecentDocumentDao
) {
    fun recentDocuments(): Flow<List<PdfDocument>> = dao.getRecentDocuments().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun recordOpen(document: PdfDocument) = withContext(Dispatchers.IO) {
        dao.insertDocument(document.toEntity())
    }
    
    suspend fun removeDocument(path: String) = withContext(Dispatchers.IO) {
        dao.deleteByPath(path)
    }

    private fun RecentDocumentEntity.toDomain(): PdfDocument {
        // Use original URI if available, otherwise fall back to file path (old records)
        val uri = if (originalUri.isNotBlank()) {
            android.net.Uri.parse(originalUri)
        } else {
            android.net.Uri.fromFile(java.io.File(path))
        }
        return PdfDocument(
            uri = uri,
            name = name,
            path = path,
            pageCount = totalPages,
            currentPage = lastPage,
            lastOpened = lastOpened,
            thumbnailPath = thumbnailPath
        )
    }

    private fun PdfDocument.toEntity(): RecentDocumentEntity {
        return RecentDocumentEntity(
            path = path,
            originalUri = uri.toString(),  // Store the original URI
            name = name,
            lastPage = currentPage,
            totalPages = pageCount,
            lastOpened = System.currentTimeMillis(),
            thumbnailPath = thumbnailPath
        )
    }
}
