package com.ospdf.reader.ui.components

import android.graphics.Path
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import com.ospdf.reader.domain.model.AnnotationTool
import com.ospdf.reader.domain.model.StrokePoint
import com.ospdf.reader.domain.model.ToolState
import com.ospdf.reader.ui.theme.HighlighterColors
import kotlin.math.abs

/**
 * Represents a complete stroke with all its properties.
 */
data class InkStroke(
    val id: String = java.util.UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    val color: Color,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false,
    val pageNumber: Int = 0
) {
    /**
     * Generates an Android Path for efficient rendering.
     */
    fun toPath(): Path {
        val path = Path()
        if (points.isEmpty()) return path
        
        path.moveTo(points.first().x, points.first().y)
        
        if (points.size == 1) {
            // Single point - draw a small circle
            path.addCircle(points.first().x, points.first().y, strokeWidth / 2, Path.Direction.CW)
        } else if (points.size == 2) {
            // Two points - simple line
            path.lineTo(points[1].x, points[1].y)
        } else {
            // Multiple points - use quadratic bezier for smooth curves
            for (i in 1 until points.size - 1) {
                val p0 = points[i - 1]
                val p1 = points[i]
                val p2 = points[i + 1]
                
                // Control point is the current point
                // End point is midpoint between current and next
                val midX = (p1.x + p2.x) / 2
                val midY = (p1.y + p2.y) / 2
                
                path.quadTo(p1.x, p1.y, midX, midY)
            }
            
            // Connect to the last point
            val last = points.last()
            path.lineTo(last.x, last.y)
        }
        
        return path
    }
    
    /**
     * Calculates variable stroke width based on pressure.
     */
    fun getWidthAtPoint(index: Int): Float {
        if (index < 0 || index >= points.size) return strokeWidth
        val pressure = points[index].pressure.coerceIn(0.1f, 1f)
        return strokeWidth * (0.5f + 0.5f * pressure)
    }
}

/**
 * High-performance inking canvas optimized for low-latency stylus input.
 * 
 * Key features:
 * - Separates active stroke from committed strokes for faster rendering
 * - Uses Android Path for efficient bezier curve rendering
 * - Supports pressure sensitivity for natural pen feel
 * - Distinguishes between stylus and finger input
 * - Smart highlighter snaps to text lines (XODO-style)
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InkingCanvas(
    modifier: Modifier = Modifier,
    strokes: List<InkStroke>,
    toolState: ToolState,
    textLines: List<com.ospdf.reader.data.pdf.TextLine> = emptyList(),
    enabled: Boolean = true,
    onStrokeStart: () -> Unit = {},
    onStrokeEnd: (InkStroke) -> Unit = {},
    onStrokeErase: (String) -> Unit = {},
    onTap: () -> Unit = {},
    onStylusActiveChange: (Boolean) -> Unit = {}
) {
    // Current stroke being drawn
    var currentPoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    var isDrawing by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableStateOf(0L) }
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                if (!enabled) return@pointerInteropFilter false
                
                // Check if this is stylus input
                val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                               event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                
                // In NONE mode, only handle finger for navigation, not drawing
                if (toolState.currentTool == AnnotationTool.NONE) {
                    if (event.action == MotionEvent.ACTION_UP) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 300) {
                            // Quick tap - toggle controls
                            onTap()
                        }
                        lastTapTime = now
                    }
                    return@pointerInteropFilter false
                }
                
                // Allow multi-touch for zoom/pan (ignore here, let parent handle)
                if (event.pointerCount > 1) {
                    return@pointerInteropFilter false
                }
                
                // Allow finger drawing - we simply proceed if tool != NONE
                // (Restrictive checks for stylus-only were removing finger drawing capability)
                
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDrawing = true
                        onStylusActiveChange(true) // Notify that stylus is active
                        onStrokeStart()
                        
                        val point = StrokePoint(
                            x = event.x,
                            y = event.y,
                            pressure = event.pressure,
                            timestamp = System.currentTimeMillis()
                        )
                        currentPoints = listOf(point)
                        
                        // For eraser, check if we hit any stroke
                        if (toolState.currentTool == AnnotationTool.ERASER) {
                            checkEraserHit(event.x, event.y, strokes, onStrokeErase)
                        }
                        
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDrawing) {
                            // Handle historical events for better accuracy
                            val newPoints = mutableListOf<StrokePoint>()
                            
                            for (i in 0 until event.historySize) {
                                newPoints.add(
                                    StrokePoint(
                                        x = event.getHistoricalX(i),
                                        y = event.getHistoricalY(i),
                                        pressure = event.getHistoricalPressure(i),
                                        timestamp = event.getHistoricalEventTime(i)
                                    )
                                )
                            }
                            
                            // Add current point
                            newPoints.add(
                                StrokePoint(
                                    x = event.x,
                                    y = event.y,
                                    pressure = event.pressure,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            
                            currentPoints = currentPoints + newPoints
                            
                            // For eraser, continuously check hits
                            if (toolState.currentTool == AnnotationTool.ERASER) {
                                for (point in newPoints) {
                                    checkEraserHit(point.x, point.y, strokes, onStrokeErase)
                                }
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        onStylusActiveChange(false) // Notify that stylus is no longer active
                        if (isDrawing && currentPoints.isNotEmpty()) {
                            // Only create stroke for pen/pen2/highlighter/highlighter2 (not eraser)
                            if (toolState.currentTool == AnnotationTool.PEN ||
                                toolState.currentTool == AnnotationTool.PEN_2 ||
                                toolState.currentTool == AnnotationTool.HIGHLIGHTER ||
                                toolState.currentTool == AnnotationTool.HIGHLIGHTER_2) {
                                
                                val isHighlighterTool = toolState.currentTool == AnnotationTool.HIGHLIGHTER ||
                                                       toolState.currentTool == AnnotationTool.HIGHLIGHTER_2
                                
                                // HIGHLIGHTER_2: straight line that snaps to text line
                                val finalPoints: List<StrokePoint>
                                val finalWidth: Float
                                
                                if (toolState.currentTool == AnnotationTool.HIGHLIGHTER_2 && currentPoints.size > 1) {
                                    val startX = currentPoints.first().x
                                    val endX = currentPoints.last().x
                                    val avgY = (currentPoints.first().y + currentPoints.last().y) / 2
                                    
                                    // Find nearest text line to snap to
                                    val nearestLine = textLines.minByOrNull { line ->
                                        val lineCenter = line.y + line.height / 2
                                        kotlin.math.abs(lineCenter - avgY)
                                    }?.takeIf { line ->
                                        // Must be within reasonable distance
                                        avgY >= line.y - line.height && avgY <= line.y + line.height * 2
                                    }
                                    
                                    if (nearestLine != null) {
                                        // Snap to text line
                                        val lineY = nearestLine.y + nearestLine.height / 2
                                        finalPoints = listOf(
                                            StrokePoint(x = startX, y = lineY, pressure = 1f),
                                            StrokePoint(x = endX, y = lineY, pressure = 1f)
                                        )
                                        finalWidth = nearestLine.height * 0.9f
                                    } else {
                                        // No text line found - just draw straight horizontal
                                        val straightY = currentPoints.first().y
                                        finalPoints = listOf(
                                            StrokePoint(x = startX, y = straightY, pressure = 1f),
                                            StrokePoint(x = endX, y = straightY, pressure = 1f)
                                        )
                                        finalWidth = toolState.strokeWidth
                                    }
                                } else {
                                    finalPoints = currentPoints
                                    finalWidth = toolState.strokeWidth
                                }
                                
                                val stroke = InkStroke(
                                    points = finalPoints,
                                    color = toolState.currentColor,
                                    strokeWidth = finalWidth,
                                    isHighlighter = isHighlighterTool
                                )
                                onStrokeEnd(stroke)
                            }
                        }
                        
                        currentPoints = emptyList()
                        isDrawing = false
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Draw committed strokes
        strokes.forEach { stroke ->
            drawStroke(stroke)
        }
        
        // Draw current active stroke
        if (currentPoints.isNotEmpty() && 
            (toolState.currentTool == AnnotationTool.PEN || 
             toolState.currentTool == AnnotationTool.PEN_2 ||
             toolState.currentTool == AnnotationTool.HIGHLIGHTER ||
             toolState.currentTool == AnnotationTool.HIGHLIGHTER_2)) {
            
            val isHighlighterTool = toolState.currentTool == AnnotationTool.HIGHLIGHTER ||
                                   toolState.currentTool == AnnotationTool.HIGHLIGHTER_2
            
            // HIGHLIGHTER_2: show straight line preview snapped to text line
            val previewPoints: List<StrokePoint>
            val previewWidth: Float
            
            if (toolState.currentTool == AnnotationTool.HIGHLIGHTER_2 && currentPoints.size > 1) {
                val startX = currentPoints.first().x
                val endX = currentPoints.last().x
                val avgY = (currentPoints.first().y + currentPoints.last().y) / 2
                
                // Find nearest text line to snap preview to
                val nearestLine = textLines.minByOrNull { line ->
                    val lineCenter = line.y + line.height / 2
                    kotlin.math.abs(lineCenter - avgY)
                }?.takeIf { line ->
                    avgY >= line.y - line.height && avgY <= line.y + line.height * 2
                }
                
                if (nearestLine != null) {
                    val lineY = nearestLine.y + nearestLine.height / 2
                    previewPoints = listOf(
                        StrokePoint(x = startX, y = lineY, pressure = 1f),
                        StrokePoint(x = endX, y = lineY, pressure = 1f)
                    )
                    previewWidth = nearestLine.height * 0.9f
                } else {
                    val straightY = currentPoints.first().y
                    previewPoints = listOf(
                        StrokePoint(x = startX, y = straightY, pressure = 1f),
                        StrokePoint(x = endX, y = straightY, pressure = 1f)
                    )
                    previewWidth = toolState.strokeWidth
                }
            } else {
                previewPoints = currentPoints
                previewWidth = toolState.strokeWidth
            }
            
            val activeStroke = InkStroke(
                points = previewPoints,
                color = toolState.currentColor,
                strokeWidth = previewWidth,
                isHighlighter = isHighlighterTool
            )
            drawStroke(activeStroke)
        }
    }
}

/**
 * Draws a stroke on the canvas with appropriate styling.
 */
private fun DrawScope.drawStroke(stroke: InkStroke) {
    if (stroke.points.isEmpty()) return
    
    val path = androidx.compose.ui.graphics.Path()
    val points = stroke.points
    
    path.moveTo(points.first().x, points.first().y)
    
    if (points.size == 1) {
        // Single point - draw a circle
        drawCircle(
            color = stroke.color,
            radius = stroke.strokeWidth / 2,
            center = Offset(points.first().x, points.first().y)
        )
        return
    }
    
    // Build smooth path using quadratic beziers
    for (i in 1 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        
        val midX = (p1.x + p2.x) / 2
        val midY = (p1.y + p2.y) / 2
        
        path.quadraticBezierTo(p1.x, p1.y, midX, midY)
    }
    
    // Connect to last point
    path.lineTo(points.last().x, points.last().y)
    
    // Draw the path
    val alpha = if (stroke.isHighlighter) 0.4f else 1f
    
    drawPath(
        path = path,
        color = stroke.color.copy(alpha = alpha),
        style = Stroke(
            width = stroke.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = if (stroke.isHighlighter) BlendMode.Multiply else BlendMode.SrcOver
    )
}

/**
 * Checks if the eraser position hits any stroke.
 */
private fun checkEraserHit(
    x: Float,
    y: Float,
    strokes: List<InkStroke>,
    onStrokeErase: (String) -> Unit
) {
    val hitRadius = 20f // Eraser hit area radius
    
    for (stroke in strokes) {
        for (point in stroke.points) {
            val dx = abs(point.x - x)
            val dy = abs(point.y - y)
            
            if (dx < hitRadius && dy < hitRadius) {
                onStrokeErase(stroke.id)
                break
            }
        }
    }
}
