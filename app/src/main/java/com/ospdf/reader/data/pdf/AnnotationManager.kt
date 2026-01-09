package com.ospdf.reader.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import com.artifex.mupdf.fitz.*
import com.ospdf.reader.domain.model.ShapeType
import com.ospdf.reader.ui.components.InkStroke
import com.ospdf.reader.ui.tools.ShapeAnnotation
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
     * Gets the temporary directory for intermediate PDF operations.
     */
    private fun getTempDirectory(): File {
        val dir = File(context.cacheDir, "pdf_temp")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Gets the output directory for saving annotated PDFs.
     */
    fun getOutputDirectory(): File {
        val dir = File(context.getExternalFilesDir(null), "annotated_pdfs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Generates an output path for a flattened PDF.
     */
    fun generateOutputPath(originalName: String): String {
        val baseName = originalName.replace(".pdf", "", ignoreCase = true)
        val outputFile = File(getOutputDirectory(), "${baseName}_permanent.pdf")
        return outputFile.absolutePath
    }
    
    /**
     * Saves annotations permanently to the original PDF file via URI.
     * This writes back to the original file location, preserving Google backup sync.
     *
     * @param originalUri The URI of the original PDF file
     * @param sourcePath The cached/temp path where the PDF was copied for reading
     * @param strokes Map of page number to list of strokes on that page
     * @param shapes Map of page number to list of shapes on that page
     * @return Result indicating success or failure
     */
    suspend fun saveAnnotationsToOriginalFile(
        originalUri: Uri,
        sourcePath: String,
        strokes: Map<Int, List<InkStroke>>,
        shapes: Map<Int, List<ShapeAnnotation>> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            var tempFile: File? = null
            var workingCopy: File? = null
            var document: Document? = null
            try {
                // Create a fresh working copy from the original URI
                workingCopy = File(getTempDirectory(), "working_copy_${System.currentTimeMillis()}.pdf")
                context.contentResolver.openInputStream(originalUri)?.use { inputStream ->
                    workingCopy.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: throw Exception("Cannot read original file")
                
                // Create a temporary file for the annotated PDF
                tempFile = File(getTempDirectory(), "temp_annotated_${System.currentTimeMillis()}.pdf")
                
                // Open the working copy
                document = Document.openDocument(workingCopy.absolutePath)
                
                // Collect all pages that have annotations
                val annotatedPages = (strokes.keys + shapes.keys).distinct()
                
                // Process each page with annotations
                for (pageNumber in annotatedPages) {
                    val pageStrokes = strokes[pageNumber] ?: emptyList()
                    val pageShapes = shapes[pageNumber] ?: emptyList()
                    
                    if (pageStrokes.isEmpty() && pageShapes.isEmpty()) continue
                    
                    val page = document.loadPage(pageNumber) as PDFPage
                    val pdfDocument = document as PDFDocument
                    
                    // Add each stroke as an ink annotation
                    for (stroke in pageStrokes) {
                        addInkAnnotation(pdfDocument, page, stroke)
                    }
                    
                    // Add each shape as an ink annotation
                    for (shape in pageShapes) {
                        addShapeAnnotation(pdfDocument, page, shape)
                    }
                    
                    page.destroy()
                }
                
                // Save to temp file
                (document as PDFDocument).save(tempFile.absolutePath, "")
                document.destroy()
                document = null
                
                // Now copy temp file back to original URI
                context.contentResolver.openOutputStream(originalUri, "wt")?.use { outputStream ->
                    tempFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: throw Exception("Cannot open output stream for original file")
                
                Result.success(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            } finally {
                // Clean up
                document?.destroy()
                tempFile?.delete()
                workingCopy?.delete()
            }
        }
    }
    
    /**
     * Adds ink annotations to a PDF and saves a new flattened copy.
     * 
     * @param sourcePath Path to the original PDF
     * @param strokes Map of page number to list of strokes on that page
     * @param shapes Map of page number to list of shapes on that page (optional)
     * @param outputPath Path where the flattened PDF will be saved
     * @return Result indicating success or failure
     */
    suspend fun saveAnnotatedPdf(
        sourcePath: String,
        strokes: Map<Int, List<InkStroke>>,
        outputPath: String,
        shapes: Map<Int, List<ShapeAnnotation>> = emptyMap()
    ): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val document = Document.openDocument(sourcePath)
                
                // Collect all pages that have annotations
                val annotatedPages = (strokes.keys + shapes.keys).distinct()
                
                // Process each page with annotations
                for (pageNumber in annotatedPages) {
                    val pageStrokes = strokes[pageNumber] ?: emptyList()
                    val pageShapes = shapes[pageNumber] ?: emptyList()
                    
                    if (pageStrokes.isEmpty() && pageShapes.isEmpty()) continue
                    
                    val page = document.loadPage(pageNumber) as PDFPage
                    val pdfDocument = document as PDFDocument
                    
                    // Add each stroke as an ink annotation
                    for (stroke in pageStrokes) {
                        addInkAnnotation(pdfDocument, page, stroke)
                    }
                    
                    // Add each shape as an ink annotation (converted to points)
                    for (shape in pageShapes) {
                        addShapeAnnotation(pdfDocument, page, shape)
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
     * Adds a shape annotation to a PDF page by converting it to an ink annotation.
     */
    private fun addShapeAnnotation(document: PDFDocument, page: PDFPage, shape: ShapeAnnotation) {
        try {
            // Create ink annotation
            val annot = page.createAnnotation(PDFAnnotation.TYPE_INK)
            
            // Convert shape to points based on type
            val points = when (shape.type) {
                ShapeType.LINE -> listOf(
                    Point(shape.startX, shape.startY),
                    Point(shape.endX, shape.endY)
                )
                ShapeType.ARROW -> {
                    // Arrow with arrowhead
                    val dx = shape.endX - shape.startX
                    val dy = shape.endY - shape.startY
                    val length = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (length > 0) {
                        val arrowHeadLength = kotlin.math.min(20f, length * 0.3f)
                        val angle = kotlin.math.atan2(dy, dx)
                        val arrowAngle = Math.PI.toFloat() / 6f // 30 degrees
                        
                        val leftX = shape.endX - arrowHeadLength * kotlin.math.cos(angle - arrowAngle)
                        val leftY = shape.endY - arrowHeadLength * kotlin.math.sin(angle - arrowAngle)
                        val rightX = shape.endX - arrowHeadLength * kotlin.math.cos(angle + arrowAngle)
                        val rightY = shape.endY - arrowHeadLength * kotlin.math.sin(angle + arrowAngle)
                        
                        listOf(
                            Point(shape.startX, shape.startY),
                            Point(shape.endX, shape.endY),
                            Point(leftX, leftY),
                            Point(shape.endX, shape.endY),
                            Point(rightX, rightY)
                        )
                    } else {
                        listOf(Point(shape.startX, shape.startY), Point(shape.endX, shape.endY))
                    }
                }
                ShapeType.RECTANGLE -> listOf(
                    Point(shape.startX, shape.startY),
                    Point(shape.endX, shape.startY),
                    Point(shape.endX, shape.endY),
                    Point(shape.startX, shape.endY),
                    Point(shape.startX, shape.startY) // Close the rectangle
                )
                ShapeType.CIRCLE -> {
                    // Approximate circle with points
                    val centerX = (shape.startX + shape.endX) / 2
                    val centerY = (shape.startY + shape.endY) / 2
                    val radiusX = kotlin.math.abs(shape.endX - shape.startX) / 2
                    val radiusY = kotlin.math.abs(shape.endY - shape.startY) / 2
                    val segments = 36
                    (0..segments).map { i ->
                        val angle = 2 * Math.PI * i / segments
                        Point(
                            (centerX + radiusX * kotlin.math.cos(angle)).toFloat(),
                            (centerY + radiusY * kotlin.math.sin(angle)).toFloat()
                        )
                    }
                }
            }
            
            val inkList = arrayOf(points.toTypedArray())
            annot.setInkList(inkList)
            
            // Set color
            val r = shape.color.red
            val g = shape.color.green
            val b = shape.color.blue
            annot.setColor(floatArrayOf(r, g, b))
            
            // Set border width
            annot.border = shape.strokeWidth
            
            annot.update()
        } catch (e: Exception) {
            e.printStackTrace()
            // Continue with other shapes if one fails
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
