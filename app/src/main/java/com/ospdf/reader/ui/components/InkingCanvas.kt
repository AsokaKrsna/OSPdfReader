package com.ospdf.reader.ui.components

import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.viewinterop.AndroidView
import com.ospdf.reader.domain.model.AnnotationTool
import com.ospdf.reader.domain.model.StrokePoint
import com.ospdf.reader.domain.model.ToolState
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
     * Generates an Android Path for efficient rendering with smooth curves.
     */
    fun toPath(): Path {
        val path = Path()
        if (points.isEmpty()) return path
        
        path.moveTo(points.first().x, points.first().y)
        
        if (points.size == 1) {
            path.addCircle(points.first().x, points.first().y, strokeWidth / 2, Path.Direction.CW)
        } else if (points.size == 2) {
            path.lineTo(points[1].x, points[1].y)
        } else {
            // Use quadratic bezier for smooth curves
            for (i in 1 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val midX = (p1.x + p2.x) / 2
                val midY = (p1.y + p2.y) / 2
                path.quadTo(p1.x, p1.y, midX, midY)
            }
            path.lineTo(points.last().x, points.last().y)
        }
        
        return path
    }
}

/**
 * High-performance inking canvas optimized for stylus input.
 * 
 * Key features:
 * - Stylus-only annotation (finger passes through for page navigation)
 * - Smooth curve rendering with quadratic beziers
 * - Uses requestDisallowInterceptTouchEvent to prevent pager from stealing stylus events
 */
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
    
    // Use rememberUpdatedState to capture latest values for the AndroidView callbacks
    val currentToolState by rememberUpdatedState(toolState)
    val currentStrokes by rememberUpdatedState(strokes)
    val currentTextLines by rememberUpdatedState(textLines)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnStrokeStart by rememberUpdatedState(onStrokeStart)
    val currentOnStrokeEnd by rememberUpdatedState(onStrokeEnd)
    val currentOnStrokeErase by rememberUpdatedState(onStrokeErase)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnStylusActiveChange by rememberUpdatedState(onStylusActiveChange)
    
    Box(modifier = modifier.fillMaxSize()) {
        // Drawing canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
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
                    
                    val nearestLine = textLines.minByOrNull { line ->
                        val lineCenter = line.y + line.height / 2
                        abs(lineCenter - avgY)
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
        
        // Invisible touch interceptor using AndroidView for proper parent touch interception control
        AndroidView(
            factory = { context ->
                object : View(context) {
                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        if (!currentEnabled) return false
                        
                        val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                                       event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                        val isFinger = event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
                        
                        // In NONE mode, let everything pass through
                        if (currentToolState.currentTool == AnnotationTool.NONE) {
                            if (event.action == MotionEvent.ACTION_UP) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime < 300) {
                                    currentOnTap()
                                }
                                lastTapTime = now
                            }
                            return false
                        }
                        
                        // STYLUS ONLY DRAWING: Let finger input pass through for page navigation
                        if (isFinger) {
                            return false
                        }
                        
                        // Allow multi-touch for zoom/pan
                        if (event.pointerCount > 1) {
                            parent?.requestDisallowInterceptTouchEvent(false)
                            return false
                        }
                        
                        // For stylus input, prevent parent from intercepting
                        if (isStylus && event.action == MotionEvent.ACTION_DOWN) {
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        
                        return when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                isDrawing = true
                                currentOnStylusActiveChange(true)
                                currentOnStrokeStart()
                                
                                val point = StrokePoint(
                                    x = event.x,
                                    y = event.y,
                                    pressure = 1f,
                                    timestamp = System.currentTimeMillis()
                                )
                                currentPoints = listOf(point)
                                
                                // For eraser, check if we hit any stroke
                                if (currentToolState.currentTool == AnnotationTool.ERASER) {
                                    checkEraserHit(event.x, event.y, currentStrokes, currentOnStrokeErase)
                                }
                                
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (isDrawing) {
                                    val newPoints = mutableListOf<StrokePoint>()
                                    
                                    // Handle historical events for smoother curves
                                    for (i in 0 until event.historySize) {
                                        newPoints.add(
                                            StrokePoint(
                                                x = event.getHistoricalX(i),
                                                y = event.getHistoricalY(i),
                                                pressure = 1f,
                                                timestamp = event.getHistoricalEventTime(i)
                                            )
                                        )
                                    }
                                    
                                    newPoints.add(
                                        StrokePoint(
                                            x = event.x,
                                            y = event.y,
                                            pressure = 1f,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                    
                                    currentPoints = currentPoints + newPoints
                                    
                                    // For eraser, continuously check hits
                                    if (currentToolState.currentTool == AnnotationTool.ERASER) {
                                        for (point in newPoints) {
                                            checkEraserHit(point.x, point.y, currentStrokes, currentOnStrokeErase)
                                        }
                                    }
                                }
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                parent?.requestDisallowInterceptTouchEvent(false)
                                currentOnStylusActiveChange(false)
                                
                                if (isDrawing && currentPoints.isNotEmpty()) {
                                    val tool = currentToolState.currentTool
                                    if (tool == AnnotationTool.PEN ||
                                        tool == AnnotationTool.PEN_2 ||
                                        tool == AnnotationTool.HIGHLIGHTER ||
                                        tool == AnnotationTool.HIGHLIGHTER_2) {
                                        
                                        val isHighlighterTool = tool == AnnotationTool.HIGHLIGHTER ||
                                                               tool == AnnotationTool.HIGHLIGHTER_2
                                        
                                        val finalPoints: List<StrokePoint>
                                        val finalWidth: Float
                                        
                                        if (tool == AnnotationTool.HIGHLIGHTER_2 && currentPoints.size > 1) {
                                            val startX = currentPoints.first().x
                                            val endX = currentPoints.last().x
                                            val avgY = (currentPoints.first().y + currentPoints.last().y) / 2
                                            
                                            val nearestLine = currentTextLines.minByOrNull { line ->
                                                val lineCenter = line.y + line.height / 2
                                                abs(lineCenter - avgY)
                                            }?.takeIf { line ->
                                                avgY >= line.y - line.height && avgY <= line.y + line.height * 2
                                            }
                                            
                                            if (nearestLine != null) {
                                                val lineY = nearestLine.y + nearestLine.height / 2
                                                finalPoints = listOf(
                                                    StrokePoint(x = startX, y = lineY, pressure = 1f),
                                                    StrokePoint(x = endX, y = lineY, pressure = 1f)
                                                )
                                                finalWidth = nearestLine.height * 0.9f
                                            } else {
                                                val straightY = currentPoints.first().y
                                                finalPoints = listOf(
                                                    StrokePoint(x = startX, y = straightY, pressure = 1f),
                                                    StrokePoint(x = endX, y = straightY, pressure = 1f)
                                                )
                                                finalWidth = currentToolState.strokeWidth
                                            }
                                        } else {
                                            finalPoints = currentPoints
                                            finalWidth = currentToolState.strokeWidth
                                        }
                                        
                                        val stroke = InkStroke(
                                            points = finalPoints,
                                            color = currentToolState.currentColor,
                                            strokeWidth = finalWidth,
                                            isHighlighter = isHighlighterTool
                                        )
                                        currentOnStrokeEnd(stroke)
                                    }
                                }
                                
                                currentPoints = emptyList()
                                isDrawing = false
                                true
                            }
                            else -> false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
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
    
    // Draw the path with appropriate style
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
    val hitRadius = 20f
    
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
