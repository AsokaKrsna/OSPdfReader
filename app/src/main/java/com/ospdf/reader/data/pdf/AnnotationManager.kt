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
     * If writing to original fails (permission denied), saves to app storage instead.
     *
     * @param originalUri The URI of the original PDF file
     * @param sourcePath The cached/temp path where the PDF was copied for reading
     * @param strokes Map of page number to list of strokes on that page
     * @param shapes Map of page number to list of shapes on that page
     * @param bakeAnnotations If true, annotations are burned into page content (uneditable). 
     *                        If false, annotations remain as separate PDF annotation objects.
     * @return Result with the output path (original URI path or fallback path)
     */
    suspend fun saveAnnotationsToOriginalFile(
        originalUri: Uri,
        sourcePath: String,
        strokes: Map<Int, List<InkStroke>>,
        shapes: Map<Int, List<ShapeAnnotation>> = emptyMap(),
        bakeAnnotations: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
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
                    
                    android.util.Log.d("AnnotationManager", "Processing page $pageNumber: ${pageStrokes.size} strokes, ${pageShapes.size} shapes")
                    
                    val page = document.loadPage(pageNumber) as PDFPage
                    val pdfDocument = document as PDFDocument

                    if (bakeAnnotations) {
                        // When baking, we write DIRECTLY to the page content stream
                        // This bypasses the annotation system entirely - no AP streams needed
                        // The strokes become permanent vector graphics in the PDF
                        android.util.Log.d("AnnotationManager", "Writing directly to content stream (bake mode)")
                        writeContentToPage(pdfDocument, page, pageStrokes, pageShapes)
                    } else {
                        // Add each stroke as an ink annotation (temporary, editable)
                        for (stroke in pageStrokes) {
                            addInkAnnotation(pdfDocument, page, stroke)
                        }
                        
                        // Add each shape as an ink annotation
                        for (shape in pageShapes) {
                            addShapeAnnotation(pdfDocument, page, shape)
                        }
                    }
                    
                    android.util.Log.d("AnnotationManager", "Finished processing page $pageNumber")
                    page.destroy()
                }
                
                val pdfDocument = document as PDFDocument
                
                // Note: When bakeAnnotations=true, we've already written directly to content stream
                // bakeAnnotationsIntoPages is only useful for flattening pre-existing annotations
                if (bakeAnnotations) {
                    android.util.Log.d("AnnotationManager", "Annotations already baked to content stream, skipping annotation flattening")
                    bakeAnnotationsIntoPages(pdfDocument)
                }
                
                // Save to temp file - use standard save without special options
                // Previous attempts with "clean", "garbage", "pretty" caused PDF corruption
                android.util.Log.d("AnnotationManager", "Saving to temp file with no special options")
                pdfDocument.save(tempFile.absolutePath, "")
                android.util.Log.d("AnnotationManager", "Temp file size: ${tempFile.length()} bytes")
                pdfDocument.destroy()
                document = null
                
                // Try to write back to original URI
                var savedToOriginal = false
                try {
                    android.util.Log.d("AnnotationManager", "Attempting to write to original URI: $originalUri (scheme=${originalUri.scheme})")
                    
                    if (originalUri.scheme == "file") {
                        // For file:// URIs, write directly using FileOutputStream
                        val filePath = originalUri.path
                        if (filePath != null) {
                            val targetFile = File(filePath)
                            android.util.Log.d("AnnotationManager", "Writing directly to file: $filePath")
                            tempFile.copyTo(targetFile, overwrite = true)
                            android.util.Log.d("AnnotationManager", "Wrote ${targetFile.length()} bytes to file")
                            savedToOriginal = true
                            
                            // Notify MediaStore so file managers see the updated file
                            android.media.MediaScannerConnection.scanFile(
                                context,
                                arrayOf(filePath),
                                arrayOf("application/pdf")
                            ) { path, uri ->
                                android.util.Log.d("AnnotationManager", "MediaScanner scanned: $path -> $uri")
                            }
                        } else {
                            android.util.Log.w("AnnotationManager", "file:// URI has null path")
                        }
                    } else {
                        // For content:// URIs, use ContentResolver
                        context.contentResolver.openOutputStream(originalUri, "wt")?.use { outputStream ->
                            tempFile.inputStream().use { inputStream ->
                                val bytesCopied = inputStream.copyTo(outputStream)
                                android.util.Log.d("AnnotationManager", "Wrote $bytesCopied bytes via ContentResolver")
                            }
                            savedToOriginal = true
                        }
                        if (!savedToOriginal) {
                            android.util.Log.w("AnnotationManager", "openOutputStream returned null for URI")
                        }
                    }
                } catch (e: SecurityException) {
                    // Permission denied - will save to app storage instead
                    android.util.Log.w("AnnotationManager", "Cannot write to original URI, saving to app storage", e)
                } catch (e: Exception) {
                    android.util.Log.w("AnnotationManager", "Failed to write to original URI", e)
                }
                
                if (savedToOriginal) {
                    android.util.Log.d("AnnotationManager", "SUCCESS: Saved to original URI")
                    Result.success("original")
                } else {
                    // Save to app's output directory instead
                    val originalName = sourcePath.substringAfterLast("/").substringAfterLast("\\")
                    val outputPath = generateOutputPath(originalName)
                    android.util.Log.d("AnnotationManager", "Saving to fallback path: $outputPath")
                    tempFile.copyTo(File(outputPath), overwrite = true)
                    android.util.Log.d("AnnotationManager", "SUCCESS: Saved to fallback path")
                    Result.success(outputPath)
                }
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
     * writes annotations directly to the page's content stream.
     * This makes them part of the page description itself (vector graphics),
     * rather than "Annotation" objects floating on top.
     */
    private fun writeContentToPage(
        document: PDFDocument,
        page: PDFPage,
        strokes: List<InkStroke>,
        shapes: List<ShapeAnnotation>
    ) {
        try {
            val pageObj = page.getObject()
            val bounds = page.bounds // MuPDF Rect is usually [x0, y0, x1, y1] in PDF units
            
            // Log page bounds for debugging
            android.util.Log.d("AnnotationManager", "Page bounds: x0=${bounds.x0}, y0=${bounds.y0}, x1=${bounds.x1}, y1=${bounds.y1}")
            if (strokes.isNotEmpty() && strokes[0].points.isNotEmpty()) {
                val pt = strokes[0].points[0]
                android.util.Log.d("AnnotationManager", "First stroke point (input): x=${pt.x}, y=${pt.y}")
                android.util.Log.d("AnnotationManager", "Transformed: x=${pt.x + bounds.x0}, y=${bounds.y1 - pt.y}")
            }
            
            // Helper for safe PDF float formatting (no scientific notation, dot separator)
            fun fmt(value: Float): String {
                return String.format(java.util.Locale.US, "%.2f", value)
            }
            
            // Check if we need transparency (highlighters)
            val hasHighlighters = strokes.any { it.isHighlighter }
            var highlightGsName = ""
            
            if (hasHighlighters) {
                // Use 0.5f alpha for translucent highlighter effect
                highlightGsName = ensureTransparencyResource(document, pageObj, 0.5f)
                android.util.Log.d("AnnotationManager", "Created transparency resource: $highlightGsName")
                
                // Ensure page has a transparency group for blending to work
                ensureTransparencyGroup(document, pageObj)
            }
            
            val contentBuffer = StringBuilder()
            
            // Start with newline and save state
            contentBuffer.append("\nq ")
            // Set line cap and join to round (1)
            contentBuffer.append("1 J 1 j ")
            
            // 1. Draw strokes
            for (stroke in strokes) {
                if (stroke.points.isEmpty()) continue
                
                // Set color
                val r = fmt(stroke.color.red)
                val g = fmt(stroke.color.green)
                val b = fmt(stroke.color.blue)
                contentBuffer.append("$r $g $b RG ")
                
                // Set line width
                contentBuffer.append("${fmt(stroke.strokeWidth)} w ")
                
                // Set transparency state if needed
                if (stroke.isHighlighter && highlightGsName.isNotEmpty()) {
                    contentBuffer.append("/$highlightGsName gs ")
                }
                
                // FLIP Y-COORDINATE: PDF origin is Bottom-Left, App is Top-Left
                // Using bounds.y1 - y to flip.
                // Using bounds.y1 - y to flip.
            // DRIFT CORRECTION: Adjusted to +8f per user feedback.
            val driftCorrection = 8f
            
            val points = stroke.points
                val startX = points[0].x + bounds.x0
                val startY = bounds.y1 - points[0].y + driftCorrection
                
                contentBuffer.append("${fmt(startX)} ${fmt(startY)} m ")
                
                for (i in 1 until points.size) {
                    val px = points[i].x + bounds.x0
                    val py = bounds.y1 - points[i].y + driftCorrection
                    contentBuffer.append("${fmt(px)} ${fmt(py)} l ")
                }
                
                contentBuffer.append("S ") // Stroke path
            }
            
            // 2. Draw shapes
            for (shape in shapes) {
                // Set color
                val r = fmt(shape.color.red)
                val g = fmt(shape.color.green)
                val b = fmt(shape.color.blue)
                contentBuffer.append("$r $g $b RG ")
                
                // Set line width
                contentBuffer.append("${fmt(shape.strokeWidth)} w ")
                
                val shapePoints = getPointsForShape(shape)
                if (shapePoints.isNotEmpty()) {
                val driftCorrection = 8f
                val start = shapePoints[0]
                    val sX = start.x + bounds.x0
                    val sY = bounds.y1 - start.y + driftCorrection
                    contentBuffer.append("${fmt(sX)} ${fmt(sY)} m ")
                    
                    for (i in 1 until shapePoints.size) {
                        val p = shapePoints[i]
                        val pX = p.x + bounds.x0
                        val pY = bounds.y1 - p.y + driftCorrection
                        contentBuffer.append("${fmt(pX)} ${fmt(pY)} l ")
                    }
                    
                    contentBuffer.append("S ")
                }
            }
            
            // Pop graphics state
            contentBuffer.append("Q\n")
            
            // Log the beginning of the stream for debugging
            val logSnippet = if (contentBuffer.length > 100) contentBuffer.substring(0, 100) + "..." else contentBuffer.toString()
            android.util.Log.d("AnnotationManager", "Writing content stream: $logSnippet")
            
            // Append to page contents using direct stream manipulation
            addStreamToPage(document, pageObj, contentBuffer.toString())
            
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("AnnotationManager", "Error writing content to page", e)
        }
    }
    
    /**
     * Manually generates an Appearance Stream (AP) for an ink annotation.
     * MuPDF Java binding doesn't expose updateAppearance(), so we must create AP manually.
     * This is CRITICAL for annotations to be visible in strict PDF readers like Adobe.
     */
    private fun generateInkAppearanceStream(document: PDFDocument, annot: PDFAnnotation, stroke: InkStroke) {
        try {
            val annotObj = annot.getObject()
            
            // Get annotation rectangle
            val rect = annotObj.get("Rect")
            if (rect == null || !rect.isArray || rect.size() < 4) return
            
            val x0 = rect.get(0).asFloat()
            val y0 = rect.get(1).asFloat()
            val x1 = rect.get(2).asFloat()
            val y1 = rect.get(3).asFloat()
            
            // Helper for safe float formatting
            fun fmt(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)
            
            // Build appearance stream content
            val content = StringBuilder()
            content.append("q ")
            content.append("1 J 1 j ") // Round line cap and join
            
            // Set color
            content.append("${fmt(stroke.color.red)} ${fmt(stroke.color.green)} ${fmt(stroke.color.blue)} RG ")
            
            // Set line width
            content.append("${fmt(stroke.strokeWidth)} w ")
            
            // Draw path (coordinates relative to BBox origin)
            if (stroke.points.isNotEmpty()) {
                val first = stroke.points[0]
                content.append("${fmt(first.x - x0)} ${fmt(first.y - y0)} m ")
                
                for (i in 1 until stroke.points.size) {
                    val pt = stroke.points[i]
                    content.append("${fmt(pt.x - x0)} ${fmt(pt.y - y0)} l ")
                }
                content.append("S ")
            }
            
            content.append("Q")
            
            // Create Form XObject for appearance
            // IMPORTANT: addStream returns an indirect reference, must resolve before modifying
            val apStreamRef = document.addStream(content.toString())
            android.util.Log.d("AnnotationManager", "addStream returned: isIndirect=${apStreamRef.isIndirect}, isStream=${apStreamRef.isStream}")
            
            val apStream = apStreamRef.resolve()
            android.util.Log.d("AnnotationManager", "After resolve: isStream=${apStream.isStream}, isDictionary=${apStream.isDictionary}")
            
            apStream.put("Type", document.newName("XObject"))
            apStream.put("Subtype", document.newName("Form"))
            apStream.put("FormType", 1)
            
            // Set BBox
            val bbox = document.newArray()
            bbox.push(0f)
            bbox.push(0f)
            bbox.push(x1 - x0)
            bbox.push(y1 - y0)
            apStream.put("BBox", bbox)
            
            android.util.Log.d("AnnotationManager", "After put: apStream.isStream=${apStream.isStream}, Subtype=${apStream.get("Subtype")?.asName()}")
            
            // Create AP dictionary and use the REFERENCE (not the resolved object)
            val ap = document.newDictionary()
            ap.put("N", apStreamRef)
            
            // Verify what we're putting
            val apN = ap.get("N")
            android.util.Log.d("AnnotationManager", "AP/N after put: isIndirect=${apN?.isIndirect}, resolved.isStream=${apN?.resolve()?.isStream}")
            
            // Set AP on annotation
            annotObj.put("AP", ap)
            
            // Final verification 
            val finalAP = annotObj.get("AP")?.resolve()
            val finalN = finalAP?.get("N")?.resolve()
            android.util.Log.d("AnnotationManager", "FINAL: AP exists=${finalAP != null}, N.isStream=${finalN?.isStream}")
            
        } catch (e: Exception) {
            android.util.Log.e("AnnotationManager", "Error generating ink appearance stream", e)
        }
    }
    
    /**
     * Manually generates an Appearance Stream (AP) for a shape annotation.
     */
    private fun generateShapeAppearanceStream(document: PDFDocument, annot: PDFAnnotation, shape: ShapeAnnotation) {
        try {
            val annotObj = annot.getObject()
            
            // Get annotation rectangle
            val rect = annotObj.get("Rect")
            if (rect == null || !rect.isArray || rect.size() < 4) return
            
            val x0 = rect.get(0).asFloat()
            val y0 = rect.get(1).asFloat()
            val x1 = rect.get(2).asFloat()
            val y1 = rect.get(3).asFloat()
            
            // Helper for safe float formatting
            fun fmt(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)
            
            // Build appearance stream content
            val content = StringBuilder()
            content.append("q ")
            content.append("1 J 1 j ") // Round line cap and join
            
            // Set color
            content.append("${fmt(shape.color.red)} ${fmt(shape.color.green)} ${fmt(shape.color.blue)} RG ")
            
            // Set line width
            content.append("${fmt(shape.strokeWidth)} w ")
            
            // Draw shape path (coordinates relative to BBox origin)
            val points = getPointsForShape(shape)
            if (points.isNotEmpty()) {
                val first = points[0]
                content.append("${fmt(first.x - x0)} ${fmt(first.y - y0)} m ")
                
                for (i in 1 until points.size) {
                    val pt = points[i]
                    content.append("${fmt(pt.x - x0)} ${fmt(pt.y - y0)} l ")
                }
                content.append("S ")
            }
            
            content.append("Q")
            
            // Create Form XObject for appearance
            // IMPORTANT: addStream returns an indirect reference, must resolve before modifying
            val apStreamRef = document.addStream(content.toString())
            val apStream = apStreamRef.resolve()
            
            apStream.put("Type", document.newName("XObject"))
            apStream.put("Subtype", document.newName("Form"))
            apStream.put("FormType", 1)
            
            // Set BBox
            val bbox = document.newArray()
            bbox.push(0f)
            bbox.push(0f)
            bbox.push(x1 - x0)
            bbox.push(y1 - y0)
            apStream.put("BBox", bbox)
            
            // Create AP dictionary and use the REFERENCE (not the resolved object)
            val ap = document.newDictionary()
            ap.put("N", apStreamRef)
            
            // Set AP on annotation
            annotObj.put("AP", ap)
            
        } catch (e: Exception) {
            android.util.Log.e("AnnotationManager", "Error generating shape appearance stream", e)
        }
    }
    
    /**
     * Helper to append a raw string as a new content stream to a page.
     */
    private fun addStreamToPage(document: PDFDocument, pageObj: PDFObject, content: String) {
        val newStream = document.addStream(content)
        android.util.Log.d("AnnotationManager", "addStreamToPage: newStream created, isIndirect=${newStream.isIndirect}")
        
        val contents = pageObj.get("Contents")
        
        if (contents == null || contents.isNull) {
            android.util.Log.d("AnnotationManager", "addStreamToPage: No existing Contents, setting new stream")
            pageObj.put("Contents", newStream)
        } else {
            // We need to create a new array and replace Contents entirely
            // This ensures the modification is properly tracked by MuPDF
            val contentsArray = document.newArray()
            
            val resolvedContents = contents.resolve()
            if (resolvedContents.isArray) {
                // Copy existing array items
                val len = resolvedContents.size()
                android.util.Log.d("AnnotationManager", "addStreamToPage: Existing Contents is array with $len items")
                for (i in 0 until len) {
                    contentsArray.push(resolvedContents.get(i))
                }
            } else {
                // Single stream - add it first
                android.util.Log.d("AnnotationManager", "addStreamToPage: Existing Contents is single stream")
                contentsArray.push(contents)
            }
            
            // Add our new stream
            contentsArray.push(newStream)
            android.util.Log.d("AnnotationManager", "addStreamToPage: Final array has ${contentsArray.size()} items")
            
            // Replace Contents with new array
            pageObj.put("Contents", contentsArray)
        }
    }

    /**
     * Adds an ExtGState resource for transparency and returns its name (key).
     */
    private fun ensureTransparencyResource(document: PDFDocument, pageObj: PDFObject, alpha: Float): String {
        // Ensure Resources dictionary exists and is resolved
        var resourcesRef = pageObj.get("Resources")
        var resources = resourcesRef?.resolve()
        
        if (resources == null || resources.isNull) {
            resources = document.newDictionary()
            pageObj.put("Resources", resources)
        }
        
        // Ensure ExtGState dictionary exists and is resolved
        var extGStateRef = resources!!.get("ExtGState")
        var extGState = extGStateRef?.resolve()
        
        if (extGState == null || extGState.isNull) {
            extGState = document.newDictionary()
            resources!!.put("ExtGState", extGState)
        }
        
        // Create a simple alphanumeric key
        // e.g. "GSA50" for alpha 0.5
        val alphaInt = (alpha * 100).toInt()
        val key = "GSA$alphaInt"
        
        // Debug check
        if (extGState!!.get(key) == null) {
            val dict = document.newDictionary()
            dict.put("Type", document.newName("ExtGState"))
            
            if (alpha < 1f) {
                // Set alpha values
                dict.put("CA", document.newReal(alpha))
                dict.put("ca", document.newReal(alpha))
                
                // Use Multiply blending
                dict.put("BM", document.newName("Multiply"))
            }
            
            // Add to resources immediately
            extGState.put(key, dict)
            
            android.util.Log.d("AnnotationManager", "Created ExtGState: $key (CA=$alpha, BM=Multiply)")
        } else {
             android.util.Log.d("AnnotationManager", "Reusing existing ExtGState: $key")
        }
        
        return key
    }

    /**
     * Ensures the page has a transparency Group dictionary.
     * This is required for alpha blending to work in PDF.
     */
    private fun ensureTransparencyGroup(document: PDFDocument, pageObj: PDFObject) {
        try {
            // Force update/create the Group dictionary
            
            val group = document.newDictionary()
            group.put("Type", document.newName("Group"))
            group.put("S", document.newName("Transparency"))
            group.put("I", true) // Isolated
            group.put("K", false) // Knockout false
            // Simplified: Removing explicit CS (ColorSpace) to avoid potential mismatch with DeviceGray/CMYK documents
            // The viewer should handle blending in current context
            
            pageObj.put("Group", group)
            android.util.Log.d("AnnotationManager", "Forcing transparency Group: S=Transparency, I=true")
            
        } catch (e: Exception) {
            android.util.Log.e("AnnotationManager", "Error ensuring transparency group", e)
        }
    }

    private fun getPointsForShape(shape: ShapeAnnotation): List<Point> {
         return when (shape.type) {
            ShapeType.LINE -> listOf(
                Point(shape.startX, shape.startY),
                Point(shape.endX, shape.endY)
            )
            ShapeType.ARROW -> {
                val dx = shape.endX - shape.startX
                val dy = shape.endY - shape.startY
                val length = kotlin.math.sqrt(dx * dx + dy * dy)
                if (length > 0) {
                    val arrowHeadLength = kotlin.math.min(20f, length * 0.3f)
                    val angle = kotlin.math.atan2(dy, dx)
                    val arrowAngle = Math.PI.toFloat() / 6f
                    
                    val leftX = shape.endX - arrowHeadLength * kotlin.math.cos(angle - arrowAngle)
                    val leftY = shape.endY - arrowHeadLength * kotlin.math.sin(angle - arrowAngle)
                    val rightX = shape.endX - arrowHeadLength * kotlin.math.cos(angle + arrowAngle)
                    val rightY = shape.endY - arrowHeadLength * kotlin.math.sin(angle + arrowAngle)
                    
                    // PDF path: Start -> End, then Draw Arrowhead (End -> Left, End -> Right)
                    // We can just draw it as one continuous line if we retrace or break it
                    // Simple path: Start -> End; Left -> End -> Right
                    listOf(
                        Point(shape.startX, shape.startY),
                        Point(shape.endX, shape.endY), // Main shaft
                        Point(leftX, leftY),           // Jump to left wing
                        Point(shape.endX, shape.endY), // Back to tip
                        Point(rightX, rightY)          // Right wing
                    )
                    // NOTE: This draws a continuous line. For "Left -> End", we need a "MoveTo" if we were doing it via addShapeAnnotation.
                    // But here in writeContentToPage we use 'l' (LineTo). 
                    // If we want a jump, we need to return 'null' separator or handle multiple paths.
                    // Since 'getPointsForShape' returns a simple list, let's treat it as a single connected path for now.
                    // Arrowhead drawn as "Start -> End -> Left -> End -> Right" works, but "Left -> End" traces a line.
                    // A better single path: Start -> End; then Left -> End -> Right is separate?
                    // Let's stick to the previous point returning logic which was:
                    // Start, End, Left, End, Right.
                    // This traces: Start->End, End->Left, Left->End (double draw), End->Right. 
                    // This is acceptable for a simple arrow.
                } else {
                    listOf(Point(shape.startX, shape.startY), Point(shape.endX, shape.endY))
                }
            }
            ShapeType.RECTANGLE -> listOf(
                Point(shape.startX, shape.startY),
                Point(shape.endX, shape.startY),
                Point(shape.endX, shape.endY),
                Point(shape.startX, shape.endY),
                Point(shape.startX, shape.startY)
            )
            ShapeType.CIRCLE -> {
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
            android.util.Log.d("AnnotationManager", "Created Ink annotation with ${stroke.points.size} points")
            
            // Get page bounds for debugging
            val pageBounds = page.bounds
            
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
            
            // Log the Rect that MuPDF calculated for this annotation
            val annotObj = annot.getObject()
            val rect = annotObj.get("Rect")
            if (rect != null && rect.isArray && rect.size() >= 4) {
                val rx0 = rect.get(0).asFloat()
                val ry0 = rect.get(1).asFloat()
                val rx1 = rect.get(2).asFloat()
                val ry1 = rect.get(3).asFloat()
                android.util.Log.d("AnnotationManager", "Ink annotation Rect: x0=$rx0, y0=$ry0, x1=$rx1, y1=$ry1")
                android.util.Log.d("AnnotationManager", "First point input: (${stroke.points[0].x}, ${stroke.points[0].y})")
                android.util.Log.d("AnnotationManager", "Page bounds: y1=${pageBounds.y1}")
            }
            
            android.util.Log.d("AnnotationManager", "Ink annotation updated, generating AP stream...")
            
            // Manually generate appearance stream since MuPDF Java doesn't expose updateAppearance()
            generateInkAppearanceStream(document, annot, stroke)
            android.util.Log.d("AnnotationManager", "Ink annotation AP stream generated")
        } catch (e: Exception) {
            android.util.Log.e("AnnotationManager", "Error creating ink annotation", e)
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
            
            val points = getPointsForShape(shape)
            
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
            
            // Manually generate appearance stream since MuPDF Java doesn't expose updateAppearance()
            generateShapeAppearanceStream(document, annot, shape)
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
     * This manually bakes annotations into page content streams,
     * making them uneditable static graphics.
     */
    suspend fun flattenPdf(
        sourcePath: String,
        outputPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val document = Document.openDocument(sourcePath) as PDFDocument
                
                // Manually bake annotations into page content streams
                bakeAnnotationsIntoPages(document)
                
                // Save the document with baked annotations
                document.save(outputPath, "garbage,compress")
                document.destroy()
                
                Result.success(outputPath)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
    
    /**
     * Manually bakes annotations into page content streams.
     * This converts annotation appearances into static XObjects drawn on the page,
     * then removes the annotations. Works like Chrome/Xodo flattening.
     */
    private fun bakeAnnotationsIntoPages(document: PDFDocument) {
        val pageCount = document.countPages()
        android.util.Log.d("AnnotationManager", "bakeAnnotationsIntoPages: processing $pageCount pages")
        
        for (pageIndex in 0 until pageCount) {
            try {
                val page = document.loadPage(pageIndex) as PDFPage
                val pageObj = page.getObject()
                
                // Get annotations array
                val annots = pageObj.get("Annots")
                if (annots == null || !annots.isArray) {
                    android.util.Log.d("AnnotationManager", "Page $pageIndex: no Annots array")
                    page.destroy()
                    continue
                }
                
                android.util.Log.d("AnnotationManager", "Page $pageIndex: found ${annots.size()} annotations")
                
                // Ensure Resources and XObject dictionaries exist
                var resources = pageObj.get("Resources")
                if (resources == null || resources.isNull) {
                    resources = document.newDictionary()
                    pageObj.put("Resources", resources)
                }
                
                var xobjects = resources.get("XObject")
                if (xobjects == null || xobjects.isNull) {
                    xobjects = document.newDictionary()
                    resources.put("XObject", xobjects)
                }
                
                // Build content stream additions for baked annotations
                val contentAdditions = StringBuilder()
                val annotsToRemove = mutableListOf<Int>()
                
                for (i in 0 until annots.size()) {
                    try {
                        val annot = annots.get(i).resolve()
                        val subtype = annot.get("Subtype")?.asName()
                        android.util.Log.d("AnnotationManager", "Page $pageIndex, Annot $i: subtype=$subtype")
                        
                        // Skip Link annotations (keep them interactive)
                        if (subtype == "Link" || subtype == "Widget") continue
                        
                        // Get appearance dictionary
                        val ap = annot.get("AP")?.resolve()
                        if (ap == null) {
                            android.util.Log.d("AnnotationManager", "Page $pageIndex, Annot $i: NO AP dictionary!")
                            continue
                        }
                        var appearance = ap.get("N")?.resolve()
                        
                        // Handle appearance states (AS)
                        if (appearance != null && appearance.isDictionary && !appearance.isStream) {
                            val asName = annot.get("AS")?.asName()
                            if (asName != null) {
                                appearance = appearance.get(asName)?.resolve()
                            }
                        }
                        
                        if (appearance == null || !appearance.isStream) {
                            android.util.Log.d("AnnotationManager", "Page $pageIndex, Annot $i: AP/N is not a stream")
                            continue
                        }
                        
                        android.util.Log.d("AnnotationManager", "Page $pageIndex, Annot $i: Found valid AP stream, will flatten")
                        
                        // Get annotation rectangle
                        val rect = annot.get("Rect")
                        if (rect == null || !rect.isArray || rect.size() < 4) continue
                        
                        val x0 = rect.get(0).asFloat()
                        val y0 = rect.get(1).asFloat()
                        val x1 = rect.get(2).asFloat()
                        val y1 = rect.get(3).asFloat()
                        
                        // Get appearance BBox
                        val bbox = appearance.get("BBox")
                        val bx0: Float
                        val by0: Float
                        val bx1: Float
                        val by1: Float
                        if (bbox != null && bbox.isArray && bbox.size() >= 4) {
                            bx0 = bbox.get(0).asFloat()
                            by0 = bbox.get(1).asFloat()
                            bx1 = bbox.get(2).asFloat()
                            by1 = bbox.get(3).asFloat()
                        } else {
                            bx0 = 0f
                            by0 = 0f
                            bx1 = x1 - x0
                            by1 = y1 - y0
                        }
                        
                        // Get appearance Matrix if present
                        val matrixArr = appearance.get("Matrix")
                        var ma = 1f; var mb = 0f; var mc = 0f; var md = 1f; var me = 0f; var mf = 0f
                        if (matrixArr != null && matrixArr.isArray && matrixArr.size() >= 6) {
                            ma = matrixArr.get(0).asFloat()
                            mb = matrixArr.get(1).asFloat()
                            mc = matrixArr.get(2).asFloat()
                            md = matrixArr.get(3).asFloat()
                            me = matrixArr.get(4).asFloat()
                            mf = matrixArr.get(5).asFloat()
                        }
                        
                        // Transform bbox by matrix
                        val tbx0 = ma * bx0 + mc * by0 + me
                        val tby0 = mb * bx0 + md * by0 + mf
                        val tbx1 = ma * bx1 + mc * by1 + me
                        val tby1 = mb * bx1 + md * by1 + mf
                        
                        // Calculate transformation from bbox to rect
                        val bboxWidth = tbx1 - tbx0
                        val bboxHeight = tby1 - tby0
                        val rectWidth = x1 - x0
                        val rectHeight = y1 - y0
                        
                        if (bboxWidth == 0f || bboxHeight == 0f) continue
                        
                        val scaleX = rectWidth / bboxWidth
                        val scaleY = rectHeight / bboxHeight
                        val translateX = x0 - tbx0 * scaleX
                        val translateY = y0 - tby0 * scaleY
                        
                        // Create unique XObject name
                        val xobjName = "Annot${pageIndex}_$i"
                        
                        // Add appearance stream as XObject
                        appearance.put("Type", document.newName("XObject"))
                        appearance.put("Subtype", document.newName("Form"))
                        xobjects.put(xobjName, appearance)
                        
                        // Add drawing commands to content stream
                        contentAdditions.append("q ")
                        contentAdditions.append("$scaleX 0 0 $scaleY $translateX $translateY cm ")
                        contentAdditions.append("/$xobjName Do ")
                        contentAdditions.append("Q\n")
                        
                        annotsToRemove.add(i)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Continue with next annotation
                    }
                }
                
                // Append content to page
                if (contentAdditions.isNotEmpty()) {
                    addStreamToPage(document, pageObj, contentAdditions.toString())
                }
                
                // Remove baked annotations (in reverse order to preserve indices)
                for (i in annotsToRemove.sortedDescending()) {
                    annots.delete(i)
                }
                
                // If all annotations were removed, remove the Annots array
                if (annots.size() == 0) {
                    pageObj.delete("Annots")
                }
                
                page.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
                // Continue with next page
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
