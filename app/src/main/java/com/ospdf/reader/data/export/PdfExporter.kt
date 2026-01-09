package com.ospdf.reader.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ospdf.reader.data.pdf.AnnotationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export format options.
 */
enum class ExportFormat {
    PDF,           // Standard PDF
    FLATTENED_PDF, // PDF with annotations flattened
    IMAGES         // Export as images
}

/**
 * Result of an export operation.
 */
data class ExportResult(
    val success: Boolean,
    val outputPath: String? = null,
    val error: String? = null
)

/**
 * Handles exporting PDFs with annotations.
 */
@Singleton
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val annotationManager: AnnotationManager
) {
    private val exportDir = File(context.getExternalFilesDir(null), "exports").apply {
        if (!exists()) mkdirs()
    }

    // Must match the authority declared in AndroidManifest.xml
    private val fileProviderAuthority = "${context.packageName}.fileprovider"
    
    /**
     * Exports a PDF with all annotations flattened.
     */
    suspend fun exportFlattened(
        sourcePath: String,
        outputFileName: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                return@withContext ExportResult(false, error = "Source file not found")
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = outputFileName 
                ?: "${sourceFile.nameWithoutExtension}_flattened_$timestamp.pdf"
            
            val outputFile = File(exportDir, fileName)
            
            // Use annotation manager to flatten
            annotationManager.flattenPdf(sourcePath, outputFile.absolutePath).fold(
                onSuccess = {
                    ExportResult(
                        success = true,
                        outputPath = outputFile.absolutePath
                    )
                },
                onFailure = { e ->
                    ExportResult(
                        success = false,
                        error = "Flatten failed: ${e.message}"
                    )
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ExportResult(false, error = "Export failed: ${e.message}")
        }
    }
    
    /**
     * Creates a copy of the PDF for sharing.
     */
    suspend fun createShareableCopy(sourcePath: String): ExportResult = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                return@withContext ExportResult(false, error = "Source file not found")
            }
            
            val shareFile = File(context.cacheDir, "share_${sourceFile.name}")
            sourceFile.copyTo(shareFile, overwrite = true)
            
            ExportResult(
                success = true,
                outputPath = shareFile.absolutePath
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ExportResult(false, error = "Failed to create shareable copy: ${e.message}")
        }
    }
    
    /**
     * Gets a shareable URI for a file using FileProvider.
     */
    fun getShareableUri(filePath: String): Uri? {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                FileProvider.getUriForFile(
                    context,
                    fileProviderAuthority,
                    file
                )
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Creates a share intent for a PDF file.
     */
    fun createShareIntent(filePath: String, title: String = "Share PDF"): Intent? {
        val uri = getShareableUri(filePath) ?: return null
        
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    /**
     * Cleans up old export files.
     */
    suspend fun cleanupOldExports(maxAgeDays: Int = 7) = withContext(Dispatchers.IO) {
        try {
            val maxAgeMillis = maxAgeDays * 24 * 60 * 60 * 1000L
            val cutoffTime = System.currentTimeMillis() - maxAgeMillis
            
            exportDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Gets the list of exported files.
     */
    fun getExportedFiles(): List<File> {
        return exportDir.listFiles()?.toList() ?: emptyList()
    }
}
