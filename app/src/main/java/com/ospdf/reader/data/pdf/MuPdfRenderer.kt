package com.ospdf.reader.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.ospdf.reader.domain.model.PdfDocument
import com.ospdf.reader.domain.model.PdfPage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper around MuPDF for PDF rendering and manipulation.
 * Thread-safe with mutex locks for document operations.
 */
@Singleton
class MuPdfRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var document: Document? = null
    private var currentUri: Uri? = null
    private val mutex = Mutex()
    
    /**
     * Opens a PDF document from a URI.
     */
    suspend fun openDocument(uri: Uri): Result<PdfDocument> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                // Close any existing document
                closeDocumentInternal()
                
                // Get file path - if it's a file:// URI, use directly; if content://, copy to cache
                val file = when (uri.scheme) {
                    "file" -> File(uri.path!!)
                    else -> copyToCache(uri)
                }
                
                // Open with MuPDF
                document = Document.openDocument(file.absolutePath)
                currentUri = uri
                
                val pageCount = document?.countPages() ?: 0
                val name = getFileName(uri) ?: file.name ?: "Unknown"
                
                Result.success(
                    PdfDocument(
                        uri = uri,
                        name = name,
                        path = file.absolutePath,
                        pageCount = pageCount,
                        fileSize = file.length()
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Renders a page to a bitmap.
     * @param pageNumber 0-indexed page number
     * @param scale Scale factor for rendering (1.0 = 72 DPI)
     */
    suspend fun renderPage(pageNumber: Int, scale: Float = 2f): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val doc = document ?: return@withContext null
                if (pageNumber < 0 || pageNumber >= doc.countPages()) {
                    return@withContext null
                }
                
                val page = doc.loadPage(pageNumber)
                val bounds = page.bounds
                
                val width = (bounds.x1 - bounds.x0) * scale
                val height = (bounds.y1 - bounds.y0) * scale
                
                val bitmap = Bitmap.createBitmap(
                    width.toInt(),
                    height.toInt(),
                    Bitmap.Config.ARGB_8888
                )
                
                val matrix = Matrix(scale)
                val device = AndroidDrawDevice(bitmap, 0, 0, 0, 0, bitmap.width, bitmap.height)
                
                page.run(device, matrix, null)
                device.close()
                page.destroy()
                
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Gets page information without rendering.
     */
    suspend fun getPageInfo(pageNumber: Int): PdfPage? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val doc = document ?: return@withContext null
                if (pageNumber < 0 || pageNumber >= doc.countPages()) {
                    return@withContext null
                }
                
                val page = doc.loadPage(pageNumber)
                val bounds = page.bounds
                
                val pdfPage = PdfPage(
                    pageNumber = pageNumber,
                    width = (bounds.x1 - bounds.x0).toInt(),
                    height = (bounds.y1 - bounds.y0).toInt()
                )
                
                page.destroy()
                pdfPage
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Gets the total page count.
     */
    fun getPageCount(): Int = document?.countPages() ?: 0
    
    /**
     * Searches for text in the document.
     */
    suspend fun searchText(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val results = mutableListOf<SearchResult>()
            val doc = document ?: return@withContext results
            
            for (i in 0 until doc.countPages()) {
                try {
                    val page = doc.loadPage(i)
                    val hits = page.search(query)
                    
                    // hits is Array<Array<Quad>> - each hit can have multiple quads
                    hits?.forEach { quadArray ->
                        quadArray.firstOrNull()?.let { quad ->
                            results.add(
                                SearchResult(
                                    pageNumber = i,
                                    bounds = quad
                                )
                            )
                        }
                    }
                    
                    page.destroy()
                } catch (e: Exception) {
                    // Skip pages that fail to load
                }
            }
            
            results
        }
    }
    
    /**
     * Extracts text from a page.
     */
    suspend fun extractText(pageNumber: Int): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val doc = document ?: return@withContext ""
                if (pageNumber < 0 || pageNumber >= doc.countPages()) {
                    return@withContext ""
                }
                
                val page = doc.loadPage(pageNumber)
                val textBytes = page.textAsHtml()
                val text = String(textBytes, Charsets.UTF_8)
                page.destroy()
                
                // Strip HTML tags for plain text
                text.replace(Regex("<[^>]*>"), "")
            } catch (e: Exception) {
                ""
            }
        }
    }
    
    /**
     * Gets text line bounding boxes for smart highlighter.
     * Returns a list of TextLine objects with their bounding rectangles.
     */
    suspend fun getTextLines(pageNumber: Int, scale: Float = 2f): List<TextLine> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val lines = mutableListOf<TextLine>()
            try {
                val doc = document ?: return@withContext lines
                if (pageNumber < 0 || pageNumber >= doc.countPages()) {
                    return@withContext lines
                }
                
                val page = doc.loadPage(pageNumber)
                val stext = page.toStructuredText()
                
                // MuPDF Java bindings use getBlocks() method
                val blocks = stext.getBlocks()
                android.util.Log.d("MuPdfRenderer", "Page $pageNumber: Found ${blocks?.size ?: 0} text blocks")
                
                blocks?.forEach { block ->
                    val blockLines = block.lines
                    blockLines?.forEach { line ->
                        val bbox = line.bbox
                        if (bbox != null) {
                            lines.add(
                                TextLine(
                                    x = bbox.x0 * scale,
                                    y = bbox.y0 * scale,
                                    width = (bbox.x1 - bbox.x0) * scale,
                                    height = (bbox.y1 - bbox.y0) * scale,
                                    text = line.chars?.mapNotNull { it?.c?.toChar() }?.joinToString("") ?: ""
                                )
                            )
                        }
                    }
                }
                
                android.util.Log.d("MuPdfRenderer", "Page $pageNumber: Extracted ${lines.size} text lines")
                page.destroy()
            } catch (e: Exception) {
                android.util.Log.e("MuPdfRenderer", "Error extracting text lines", e)
            }
            lines
        }
    }
    
    /**
     * Closes the current document.
     */
    suspend fun closeDocument() {
        mutex.withLock {
            closeDocumentInternal()
        }
    }
    
    private fun closeDocumentInternal() {
        document?.destroy()
        document = null
        currentUri = null
    }
    
    /**
     * Copies a content URI to the cache directory.
     */
    private fun copyToCache(uri: Uri): File {
        val fileName = getFileName(uri) ?: "document_${System.currentTimeMillis()}.pdf"
        val cacheFile = File(context.cacheDir, "pdf_cache/$fileName")
        cacheFile.parentFile?.mkdirs()
        
        // Delete existing file if it exists (to ensure fresh copy)
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open input stream for URI: $uri. Make sure the file is accessible.")
        
        inputStream.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }
        
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            throw IllegalStateException("Failed to copy file to cache")
        }
        
        return cacheFile
    }
    
    /**
     * Gets the file name from a URI.
     */
    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = cursor.getString(index)
                    }
                }
            }
        }
        
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        
        return name
    }
}

/**
 * Represents a search result.
 */
data class SearchResult(
    val pageNumber: Int,
    val bounds: com.artifex.mupdf.fitz.Quad
)

/**
 * Represents a text line with its bounding box for smart highlighting.
 */
data class TextLine(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String = ""
)
