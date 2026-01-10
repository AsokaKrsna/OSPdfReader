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
import com.ospdf.reader.data.pdf.TextLine
import kotlin.math.abs

// ==================== Data Classes ====================

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
 * Creates a StrokePoint from a MotionEvent at the current position.
 */
private fun createStrokePoint(event: MotionEvent): StrokePoint {
    return StrokePoint(
        x = event.x,
        y = event.y,
        pressure = 1f,
        timestamp = System.currentTimeMillis()
    )
}

/**
 * Creates a StrokePoint from historical event data.
 */
private fun createHistoricalPoint(event: MotionEvent, historyIndex: Int): StrokePoint {
    return StrokePoint(
        x = event.getHistoricalX(historyIndex),
        y = event.getHistoricalY(historyIndex),
        pressure = 1f,
        timestamp = event.getHistoricalEventTime(historyIndex)
    )
}

/**
 * Extracts all points from a move event, including historical points for smooth curves.
 */
private fun extractMovePoints(event: MotionEvent): List<StrokePoint> {
    val points = mutableListOf<StrokePoint>()
    
    // Add historical points for smoother curves
    for (i in 0 until event.historySize) {
        points.add(createHistoricalPoint(event, i))
    }
    
    // Add current point
    points.add(createStrokePoint(event))
    
    return points
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
        
        // Invisible touch interceptor using AndroidView for proper parent touch interception control
        AndroidView(
            factory = { context ->
                InkingTouchHandler(
                    context = context,
                    isEnabled = { currentEnabled },
                    toolState = { currentToolState },
                    strokes = { currentStrokes },
                    textLines = { currentTextLines },
                    isDrawing = { isDrawing },
                    setDrawing = { isDrawing = it },
                    currentPoints = { currentPoints },
                    setCurrentPoints = { currentPoints = it },
                    lastTapTime = { lastTapTime },
                    setLastTapTime = { lastTapTime = it },
                    onStrokeStart = { currentOnStrokeStart() },
                    onStrokeEnd = { currentOnStrokeEnd(it) },
                    onStrokeErase = { currentOnStrokeErase(it) },
                    onTap = { currentOnTap() },
                    onStylusActiveChange = { currentOnStylusActiveChange(it) }
                )
            },
            modifier = Modifier.fillMaxSize()
        )
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

// ==================== Touch Handler ====================

/**
 * Custom View that handles touch events for inking.
 * Extracted from the inline object to reduce complexity and improve testability.
 */
private class InkingTouchHandler(
    context: android.content.Context,
    private val isEnabled: () -> Boolean,
    private val toolState: () -> ToolState,
    private val strokes: () -> List<InkStroke>,
    private val textLines: () -> List<TextLine>,
    private val isDrawing: () -> Boolean,
    private val setDrawing: (Boolean) -> Unit,
    private val currentPoints: () -> List<StrokePoint>,
    private val setCurrentPoints: (List<StrokePoint>) -> Unit,
    private val lastTapTime: () -> Long,
    private val setLastTapTime: (Long) -> Unit,
    private val onStrokeStart: () -> Unit,
    private val onStrokeEnd: (InkStroke) -> Unit,
    private val onStrokeErase: (String) -> Unit,
    private val onTap: () -> Unit,
    private val onStylusActiveChange: (Boolean) -> Unit
) : View(context) {
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled()) return false
        
        val inputType = classifyInputType(event)
        
        // Handle pass-through cases first
        if (shouldPassThrough(inputType, event)) {
            return false
        }
        
        // Configure parent interception for stylus
        configureParentInterception(event, inputType)
        
        // Dispatch to appropriate handler
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> handleActionDown(event)
            MotionEvent.ACTION_MOVE -> handleActionMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleActionUp()
            else -> false
        }
    }
    
    private fun classifyInputType(event: MotionEvent): InputType {
        return when (event.getToolType(0)) {
            MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> InputType.STYLUS
            MotionEvent.TOOL_TYPE_FINGER -> InputType.FINGER
            else -> InputType.OTHER
        }
    }
    
    private fun shouldPassThrough(inputType: InputType, event: MotionEvent): Boolean {
        // In NONE mode, handle tap detection but pass through
        if (toolState().currentTool == AnnotationTool.NONE) {
            handleTapDetection(event)
            return true
        }
        
        // Let finger input pass through for page navigation
        if (inputType == InputType.FINGER) {
            return true
        }
        
        // Allow multi-touch for zoom/pan
        if (event.pointerCount > 1) {
            parent?.requestDisallowInterceptTouchEvent(false)
            return true
        }
        
        return false
    }
    
    private fun handleTapDetection(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_UP) {
            val now = System.currentTimeMillis()
            if (now - lastTapTime() < 300) {
                onTap()
            }
            setLastTapTime(now)
        }
    }
    
    private fun configureParentInterception(event: MotionEvent, inputType: InputType) {
        if (inputType == InputType.STYLUS && event.action == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
    }
    
    private fun handleActionDown(event: MotionEvent): Boolean {
        setDrawing(true)
        onStylusActiveChange(true)
        onStrokeStart()
        
        val point = createStrokePoint(event)
        setCurrentPoints(listOf(point))
        
        // For eraser, check if we hit any stroke
        if (toolState().currentTool == AnnotationTool.ERASER) {
            checkEraserHit(event.x, event.y, strokes(), onStrokeErase)
        }
        
        return true
    }
    
    private fun handleActionMove(event: MotionEvent): Boolean {
        if (!isDrawing()) return true
        
        val newPoints = extractMovePoints(event)
        setCurrentPoints(currentPoints() + newPoints)
        
        // For eraser, continuously check hits
        if (toolState().currentTool == AnnotationTool.ERASER) {
            newPoints.forEach { point ->
                checkEraserHit(point.x, point.y, strokes(), onStrokeErase)
            }
        }
        
        return true
    }
    
    private fun handleActionUp(): Boolean {
        parent?.requestDisallowInterceptTouchEvent(false)
        onStylusActiveChange(false)
        
        if (isDrawing() && currentPoints().isNotEmpty()) {
            val tool = toolState().currentTool
            if (tool.isDrawingTool()) {
                val stroke = createFinalStroke(
                    points = currentPoints(),
                    tool = tool,
                    toolState = toolState(),
                    textLines = textLines()
                )
                onStrokeEnd(stroke)
            }
        }
        
        setCurrentPoints(emptyList())
        setDrawing(false)
        return true
    }
    
    private enum class InputType {
        STYLUS, FINGER, OTHER
    }
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
