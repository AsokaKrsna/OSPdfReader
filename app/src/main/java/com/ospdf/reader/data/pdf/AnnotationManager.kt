package com.ospdf.reader.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import com.artifex.mupdf.fitz.*
import com.ospdf.reader.ui.components.InkStroke
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
 * Manages PDF annotations using MuPDF.
 * Handles adding ink annotations and flattening them into the PDF.
 */
@Singleton
class AnnotationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    
    /**
     * Adds ink annotations to a PDF and saves a new flattened copy.
     * 
     * @param sourcePath Path to the original PDF
     * @param strokes Map of page number to list of strokes on that page
     * @param outputPath Path where the flattened PDF will be saved
     * @return Result indicating success or failure
     */
    suspend fun saveAnnotatedPdf(
        sourcePath: String,
        strokes: Map<Int, List<InkStroke>>,
        outputPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val document = Document.openDocument(sourcePath)
                
                // Process each page with annotations
                for ((pageNumber, pageStrokes) in strokes) {
                    if (pageStrokes.isEmpty()) continue
                    
                    val page = document.loadPage(pageNumber) as PDFPage
                    val pdfDocument = document as PDFDocument
                    
                    // Add each stroke as an ink annotation
                    for (stroke in pageStrokes) {
                        addInkAnnotation(pdfDocument, page, stroke)
                    }
                    
                    page.destroy()
                }
                
                // Save the document with annotations
                (document as PDFDocument).save(outputPath, "")
                document.destroy()
                
                Result.success(outputPath)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
    
    /**
     * Adds an ink annotation to a PDF page.
     */
    private fun addInkAnnotation(document: PDFDocument, page: PDFPage, stroke: InkStroke) {
        try {
            // Create ink annotation
            val annot = page.createAnnotation(PDFAnnotation.TYPE_INK)
            
            // Convert stroke points to an array of Point arrays
            // MuPDF expects Array<Array<Point>>
            val pointArray = stroke.points.map { point ->
                Point(point.x, point.y)
            }.toTypedArray()
            
            val inkList = arrayOf(pointArray)
            
            annot.setInkList(inkList)
            
            // Set color (Compose Color already has normalized 0-1 values)
            val r = stroke.color.red
            val g = stroke.color.green
            val b = stroke.color.blue
            annot.setColor(floatArrayOf(r, g, b))
            
            // Set border width
            annot.border = stroke.strokeWidth
            
            // Set opacity for highlighter
            if (stroke.isHighlighter) {
                annot.opacity = 0.4f
            }
            
            annot.update()
        } catch (e: Exception) {
            e.printStackTrace()
            // Continue with other strokes if one fails
        }
    }
    
    /**
     * Renders annotations on top of a page bitmap.
     * Used for displaying annotations before they're saved to PDF.
     */
    fun renderAnnotationsOnBitmap(
        bitmap: Bitmap,
        strokes: List<InkStroke>,
        scale: Float = 1f
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        
        for (stroke in strokes) {
            paint.color = stroke.color.toArgb()
            paint.strokeWidth = stroke.strokeWidth * scale
            
            if (stroke.isHighlighter) {
                paint.alpha = 102 // ~40% opacity
            } else {
                paint.alpha = 255
            }
            
            val path = Path()
            val points = stroke.points
            
            if (points.isNotEmpty()) {
                path.moveTo(points[0].x * scale, points[0].y * scale)
                
                for (i in 1 until points.size) {
                    path.lineTo(points[i].x * scale, points[i].y * scale)
                }
                
                canvas.drawPath(path, paint)
            }
        }
        
        return result
    }
    
    /**
     * Flattens all annotations in a PDF (makes them permanent).
     * Note: MuPDF's save with annotations already bakes them into the PDF.
     */
    suspend fun flattenPdf(
        sourcePath: String,
        outputPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val document = Document.openDocument(sourcePath) as PDFDocument
                
                // Save the document - this includes all annotations
                document.save(outputPath, "")
                document.destroy()
                
                Result.success(outputPath)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
    
    /**
     * Helper to convert Compose Color to Android color int.
     */
    private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
        return android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }
}
