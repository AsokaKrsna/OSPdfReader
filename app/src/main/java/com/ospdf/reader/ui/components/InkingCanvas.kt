package com.ospdf.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.ospdf.reader.domain.model.AnnotationTool
import com.ospdf.reader.domain.model.StrokePoint
import com.ospdf.reader.domain.model.ToolState
import com.ospdf.reader.data.pdf.TextLine
import com.ospdf.reader.domain.model.InkStroke
import kotlin.math.abs

/**
 * Result of snapping a highlighter stroke to a text line.
 */
private data class HighlighterSnapResult(
    val points: List<StrokePoint>,
    val strokeWidth: Float
)

// ==================== Helper Functions ====================

/**
 * Checks if the tool is a drawing tool (pen or highlighter).
 */
private fun AnnotationTool.isDrawingTool(): Boolean = when (this) {
    AnnotationTool.PEN, AnnotationTool.PEN_2,
    AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> true
    else -> false
}

/**
 * Checks if the tool is a highlighter variant.
 */
private fun AnnotationTool.isHighlighter(): Boolean = when (this) {
    AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2 -> true
    else -> false
}

/**
 * Checks if the tool is the smart highlighter that snaps to text.
 */
private fun AnnotationTool.isSmartHighlighter(): Boolean = this == AnnotationTool.HIGHLIGHTER_2

/**
 * Finds the nearest text line to a given Y position.
 * Returns null if no line is within snapping distance.
 */
private fun findNearestTextLine(
    y: Float,
    textLines: List<TextLine>
): TextLine? {
    return textLines.minByOrNull { line ->
        val lineCenter = line.y + line.height / 2
        abs(lineCenter - y)
    }?.takeIf { line ->
        y >= line.y - line.height && y <= line.y + line.height * 2
    }
}

/**
 * Snaps a highlighter stroke to the nearest text line if available.
 * Returns adjusted points and stroke width for text-aligned highlighting.
 */
private fun snapHighlighterToTextLine(
    points: List<StrokePoint>,
    textLines: List<TextLine>,
    defaultWidth: Float
): HighlighterSnapResult {
    if (points.size < 2) {
        return HighlighterSnapResult(points, defaultWidth)
    }
    
    val startX = points.first().x
    val endX = points.last().x
    val avgY = (points.first().y + points.last().y) / 2
    
    val nearestLine = findNearestTextLine(avgY, textLines)
    
    return if (nearestLine != null) {
        val lineY = nearestLine.y + nearestLine.height / 2
        HighlighterSnapResult(
            points = listOf(
                StrokePoint(x = startX, y = lineY, pressure = 1f),
                StrokePoint(x = endX, y = lineY, pressure = 1f)
            ),
            strokeWidth = nearestLine.height * 0.9f
        )
    } else {
        val straightY = points.first().y
        HighlighterSnapResult(
            points = listOf(
                StrokePoint(x = startX, y = straightY, pressure = 1f),
                StrokePoint(x = endX, y = straightY, pressure = 1f)
            ),
            strokeWidth = defaultWidth
        )
    }
}

/**
 * Creates a StrokePoint from a PointerInputChange.
 */
private fun createStrokePoint(change: PointerInputChange): StrokePoint {
    return StrokePoint(
        x = change.position.x,
        y = change.position.y,
        pressure = change.pressure,
        timestamp = change.uptimeMillis
    )
}

/**
 * Creates a finalized InkStroke from drawing state.
 */
private fun createFinalStroke(
    points: List<StrokePoint>,
    tool: AnnotationTool,
    toolState: ToolState,
    textLines: List<TextLine>
): InkStroke {
    val isHighlighterTool = tool.isHighlighter()
    
    val (finalPoints, finalWidth) = if (tool.isSmartHighlighter()) {
        val result = snapHighlighterToTextLine(points, textLines, toolState.strokeWidth)
        result.points to result.strokeWidth
    } else {
        points to toolState.strokeWidth
    }
    
    return InkStroke(
        points = finalPoints,
        color = toolState.currentColor,
        strokeWidth = finalWidth,
        isHighlighter = isHighlighterTool
    )
}

/**
 * High-performance inking canvas optimized for stylus input using Compose gestures.
 * 
 * Key features:
 * - Stylus/Eraser-only annotation (finger passes through for page navigation)
 * - Pure Compose input handling for better gesture arbitration
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
    
    // Use rememberUpdatedState to capture latest values for the gesture callbacks
    val currentToolState by rememberUpdatedState(toolState)
    val currentStrokes by rememberUpdatedState(strokes)
    val currentTextLines by rememberUpdatedState(textLines)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnStrokeStart by rememberUpdatedState(onStrokeStart)
    val currentOnStrokeEnd by rememberUpdatedState(onStrokeEnd)
    val currentOnStrokeErase by rememberUpdatedState(onStrokeErase)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnStylusActiveChange by rememberUpdatedState(onStylusActiveChange)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    
                    val isStylusOrEraser = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                    
                    if (isStylusOrEraser) {
                        // Consume the down event to claim the gesture and prevent Pager from intercepting
                        down.consume()
                        
                        // Start Gesture
                        isDrawing = true
                        currentOnStylusActiveChange(true)
                        currentOnStrokeStart()
                        
                        val startPoint = createStrokePoint(down)
                        currentPoints = listOf(startPoint)
                        
                        // Initial eraser check
                        if (currentToolState.currentTool == AnnotationTool.ERASER) {
                            checkEraserHit(
                                down.position.x, 
                                down.position.y, 
                                currentStrokes, 
                                currentOnStrokeErase,
                                currentToolState.eraserWidth
                            )
                        }
                        
                        var pointerId = down.id
                        
                        // Drag Loop
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            
                            // If pointer lifted or canceled
                            if (change == null || !change.pressed) {
                                break
                            }
                            
                            if (change.position != change.previousPosition) {
                                change.consume()
                                val newPoint = createStrokePoint(change)
                                currentPoints = currentPoints + newPoint
                                
                                // Eraser check during move
                                if (currentToolState.currentTool == AnnotationTool.ERASER) {
                                    checkEraserHit(
                                        newPoint.x, 
                                        newPoint.y, 
                                        currentStrokes, 
                                        currentOnStrokeErase,
                                        currentToolState.eraserWidth
                                    )
                                }
                            }
                        }
                        
                        // End Gesture
                        currentOnStylusActiveChange(false)
                        
                        if (currentPoints.isNotEmpty()) {
                            val tool = currentToolState.currentTool
                            if (tool.isDrawingTool()) {
                                val stroke = createFinalStroke(
                                    points = currentPoints,
                                    tool = tool,
                                    toolState = currentToolState,
                                    textLines = currentTextLines
                                )
                                currentOnStrokeEnd(stroke)
                            }
                        }
                        
                        currentPoints = emptyList()
                        isDrawing = false
                        
                    } else {
                        // Finger Input: Do NOT consume. 
                        // Let it pass through to parent Pager for navigation.
                        // We can check for Tap here if needed, but Pager usually handles taps too.
                        // For simple tap detection (toggle UI), we can add a detector if Pager doesn't consume it.
                    }
                }
            }
            .pointerInput(Unit) {
                 // Separate tap detector that works nicely with Pager
                 detectTapGestures(onTap = { currentOnTap() })
            }
    ) {
        // Drawing canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw committed strokes
            strokes.forEach { stroke ->
                drawStroke(stroke)
            }
            
            // Draw current active stroke preview
            if (currentPoints.isNotEmpty() && toolState.currentTool.isDrawingTool()) {
                val previewStroke = createPreviewStroke(
                    points = currentPoints,
                    tool = toolState.currentTool,
                    toolState = toolState,
                    textLines = textLines
                )
                drawStroke(previewStroke)
            }
        }
    }
}

/**
 * Creates a preview stroke for the current drawing, with smart highlighter snapping.
 */
private fun createPreviewStroke(
    points: List<StrokePoint>,
    tool: AnnotationTool,
    toolState: ToolState,
    textLines: List<TextLine>
): InkStroke {
    val isHighlighterTool = tool.isHighlighter()
    
    val (previewPoints, previewWidth) = if (tool.isSmartHighlighter() && points.size > 1) {
        val result = snapHighlighterToTextLine(points, textLines, toolState.strokeWidth)
        result.points to result.strokeWidth
    } else {
        points to toolState.strokeWidth
    }
    
    return InkStroke(
        points = previewPoints,
        color = toolState.currentColor,
        strokeWidth = previewWidth,
        isHighlighter = isHighlighterTool
    )
}

// ==================== Drawing Functions ====================

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
        // FIX: Use Darken instead of Multiply for consistent transparency
        // Multiply causes solid appearance on non-white PDF content
        blendMode = if (stroke.isHighlighter) BlendMode.Darken else BlendMode.SrcOver
    )
}

/**
 * Checks if the eraser position hits any stroke using robust segment intersection.
 */
private fun checkEraserHit(
    x: Float,
    y: Float,
    strokes: List<InkStroke>,
    onStrokeErase: (String) -> Unit,
    eraserRadius: Float
) {
    val hitPoint = Offset(x, y)
    
    for (stroke in strokes) {
        var isHit = false
        val points = stroke.points
        
        // 1. Check if any point is within radius (fast check)
        for (point in points) {
            val dx = abs(point.x - x)
            val dy = abs(point.y - y)
            if (dx < eraserRadius && dy < eraserRadius) {
                isHit = true
                break
            }
        }
        
        // 2. If no point hit, check segments (robust check)
        if (!isHit && points.size > 1) {
             for (i in 0 until points.size - 1) {
                 val p1 = Offset(points[i].x, points[i].y)
                 val p2 = Offset(points[i+1].x, points[i+1].y)
                 
                 val dist = distanceToLineSegment(hitPoint, p1, p2)
                 if (dist < eraserRadius + stroke.strokeWidth / 2) {
                     isHit = true
                     break
                 }
             }
        }
        
        if (isHit) {
            onStrokeErase(stroke.id)
            // Continue checking other strokes? Usually ink apps erase all hit strokes.
            // But if we modify the list while iterating, we might crash if 'strokes' was mutable.
            // Here 'strokes' is a List (immutable usually), onStrokeErase is a callback.
            // Returning after first hit makes it "single stroke eraser per frame", 
            // which is safer performance-wise for real-time.
            // However, erasing multiple overlapping strokes is better UX.
            // But since this is called frequently on Move, "one per frame" is fine.
            return 
        }
    }
}

/**
 * Calculates the minimum distance from a point to a line segment.
 */
private fun distanceToLineSegment(point: Offset, lineStart: Offset, lineEnd: Offset): Float {
    val lineLength = (lineEnd - lineStart).getDistance()
    if (lineLength < 0.001f) {
        // Line is essentially a point
        return (point - lineStart).getDistance()
    }
    
    // Calculate the projection of point onto the line
    val t = ((point - lineStart).dotProduct(lineEnd - lineStart) / (lineLength * lineLength)).coerceIn(0f, 1f)
    val projection = lineStart + (lineEnd - lineStart) * t
    
    return (point - projection).getDistance()
}

/**
 * Dot product of two 2D vectors represented as Offsets.
 */
private fun Offset.dotProduct(other: Offset): Float = this.x * other.x + this.y * other.y
