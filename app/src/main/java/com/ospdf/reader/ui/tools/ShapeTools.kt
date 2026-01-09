package com.ospdf.reader.ui.tools

import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.ospdf.reader.domain.model.ShapeType
import com.ospdf.reader.domain.model.ToolState
import com.ospdf.reader.ui.theme.InkColors
import kotlin.math.*

/**
 * Represents a shape annotation.
 */
data class ShapeAnnotation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: ShapeType,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val color: Color = InkColors.Black,
    val strokeWidth: Float = 3f,
    val isFilled: Boolean = false,
    val pageNumber: Int = 0
) {
    val bounds: Rect get() = Rect(
        left = minOf(startX, endX),
        top = minOf(startY, endY),
        right = maxOf(startX, endX),
        bottom = maxOf(startY, endY)
    )
    
    val width: Float get() = abs(endX - startX)
    val height: Float get() = abs(endY - startY)
    val centerX: Float get() = (startX + endX) / 2
    val centerY: Float get() = (startY + endY) / 2
}

/**
 * Canvas for drawing shapes (line, rectangle, circle, arrow).
 * Supports smart shape detection and snapping.
 * Uses stylus-only input with finger pass-through for page navigation.
 */
@Composable
fun ShapeCanvas(
    modifier: Modifier = Modifier,
    shapes: List<ShapeAnnotation>,
    toolState: ToolState,
    enabled: Boolean = true,
    zoomScale: Float = 1f,
    zoomOffset: Offset = Offset.Zero,
    containerSize: IntSize = IntSize.Zero,
    onShapeComplete: (ShapeAnnotation) -> Unit = {},
    onShapeErase: (String) -> Unit = {}
) {
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var currentPoint by remember { mutableStateOf<Offset?>(null) }
    var isDrawing by remember { mutableStateOf(false) }
    
    // Use rememberUpdatedState to capture latest values for AndroidView callbacks
    val currentToolState by rememberUpdatedState(toolState)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnShapeComplete by rememberUpdatedState(onShapeComplete)
    val currentZoomScale by rememberUpdatedState(zoomScale)
    
    // Transform screen coordinates to content coordinates (accounting for zoom/pan)
    fun transformPoint(screenX: Float, screenY: Float): Offset {
        val contentX = (screenX - zoomOffset.x) / zoomScale
        val contentY = (screenY - zoomOffset.y) / zoomScale
        return Offset(contentX, contentY)
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Drawing canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw existing shapes
            shapes.forEach { shape ->
                drawShape(shape)
            }
            
            // Draw current shape being drawn
            if (isDrawing && startPoint != null && currentPoint != null) {
                val previewShape = ShapeAnnotation(
                    type = toolState.shapeType,
                    startX = startPoint!!.x,
                    startY = startPoint!!.y,
                    endX = snapToAngle(startPoint!!, currentPoint!!).x,
                    endY = snapToAngle(startPoint!!, currentPoint!!).y,
                    color = toolState.currentColor,
                    strokeWidth = toolState.strokeWidth
                )
                drawShape(previewShape, isPreview = true)
            }
        }
        
        // Touch interceptor for stylus-only input
        AndroidView(
            factory = { context ->
                object : View(context) {
                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        if (!currentEnabled) return false
                        
                        val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                                       event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                        
                        // Let Compose handle all finger gestures (zoom, pan, double-tap)
                        if (!isStylus) return false
                        
                        // When zoomed in, disable drawing - let user zoom out first
                        if (currentZoomScale > 1.05f) return false
                        
                        // Prevent parent from intercepting stylus
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        
                        return when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                isDrawing = true
                                val transformed = transformPoint(event.x, event.y)
                                startPoint = transformed
                                currentPoint = transformed
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (isDrawing) {
                                    currentPoint = transformPoint(event.x, event.y)
                                }
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                parent?.requestDisallowInterceptTouchEvent(false)
                                
                                if (isDrawing && startPoint != null && currentPoint != null) {
                                    val start = startPoint!!
                                    val end = currentPoint!!
                                    val snappedEnd = snapToAngle(start, end)
                                    
                                    val shape = ShapeAnnotation(
                                        type = currentToolState.shapeType,
                                        startX = start.x,
                                        startY = start.y,
                                        endX = snappedEnd.x,
                                        endY = snappedEnd.y,
                                        color = currentToolState.currentColor,
                                        strokeWidth = currentToolState.strokeWidth
                                    )
                                    
                                    if (shape.width > 5 || shape.height > 5) {
                                        currentOnShapeComplete(shape)
                                    }
                                }
                                
                                startPoint = null
                                currentPoint = null
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
 * Draws a shape on the canvas.
 */
private fun DrawScope.drawShape(shape: ShapeAnnotation, isPreview: Boolean = false) {
    val alpha = if (isPreview) 0.6f else 1f
    val color = shape.color.copy(alpha = alpha)
    val style = Stroke(
        width = shape.strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )
    
    when (shape.type) {
        ShapeType.LINE -> {
            drawLine(
                color = color,
                start = Offset(shape.startX, shape.startY),
                end = Offset(shape.endX, shape.endY),
                strokeWidth = shape.strokeWidth,
                cap = StrokeCap.Round
            )
        }
        ShapeType.RECTANGLE -> {
            drawRect(
                color = color,
                topLeft = Offset(shape.bounds.left, shape.bounds.top),
                size = Size(shape.width, shape.height),
                style = if (shape.isFilled) androidx.compose.ui.graphics.drawscope.Fill else style
            )
        }
        ShapeType.CIRCLE -> {
            val radius = maxOf(shape.width, shape.height) / 2
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(shape.centerX, shape.centerY),
                style = if (shape.isFilled) androidx.compose.ui.graphics.drawscope.Fill else style
            )
        }
        ShapeType.ARROW -> {
            drawArrow(
                color = color,
                start = Offset(shape.startX, shape.startY),
                end = Offset(shape.endX, shape.endY),
                strokeWidth = shape.strokeWidth
            )
        }
    }
}

/**
 * Draws an arrow with arrowhead.
 */
private fun DrawScope.drawArrow(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float
) {
    // Draw the line
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    
    // Calculate arrowhead
    val angle = atan2(end.y - start.y, end.x - start.x)
    val arrowLength = strokeWidth * 5
    val arrowAngle = Math.toRadians(30.0).toFloat()
    
    val arrow1 = Offset(
        end.x - arrowLength * cos(angle - arrowAngle),
        end.y - arrowLength * sin(angle - arrowAngle)
    )
    val arrow2 = Offset(
        end.x - arrowLength * cos(angle + arrowAngle),
        end.y - arrowLength * sin(angle + arrowAngle)
    )
    
    // Draw arrowhead
    drawLine(
        color = color,
        start = end,
        end = arrow1,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = end,
        end = arrow2,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

/**
 * Snaps line endpoints to common angles (0°, 45°, 90°, etc.)
 * for cleaner drawing.
 */
private fun snapToAngle(start: Offset, end: Offset): Offset {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    
    // Get current angle
    val angle = atan2(dy, dx)
    val degrees = Math.toDegrees(angle.toDouble())
    
    // Snap threshold (in degrees)
    val threshold = 10.0
    
    // Check for horizontal/vertical snapping
    val snappedDegrees = when {
        abs(degrees) < threshold -> 0.0
        abs(degrees - 90) < threshold -> 90.0
        abs(degrees + 90) < threshold -> -90.0
        abs(abs(degrees) - 180) < threshold -> if (degrees > 0) 180.0 else -180.0
        abs(degrees - 45) < threshold -> 45.0
        abs(degrees + 45) < threshold -> -45.0
        abs(degrees - 135) < threshold -> 135.0
        abs(degrees + 135) < threshold -> -135.0
        else -> degrees
    }
    
    if (snappedDegrees != degrees) {
        val snappedRad = Math.toRadians(snappedDegrees).toFloat()
        return Offset(
            start.x + length * cos(snappedRad),
            start.y + length * sin(snappedRad)
        )
    }
    
    return end
}

/**
 * Detects if a rough freehand stroke looks like a shape.
 * Used for "smart shapes" feature.
 */
object ShapeDetector {
    
    data class DetectionResult(
        val isShape: Boolean,
        val shapeType: ShapeType? = null,
        val confidence: Float = 0f
    )
    
    /**
     * Analyzes stroke points to detect if they form a recognizable shape.
     */
    fun detectShape(points: List<Offset>): DetectionResult {
        if (points.size < 5) return DetectionResult(false)
        
        // Check if stroke is closed (start and end are close)
        val isClosed = distance(points.first(), points.last()) < 50f
        
        if (isClosed) {
            // Could be rectangle or circle
            val boundingRect = getBoundingRect(points)
            val aspectRatio = boundingRect.width / boundingRect.height
            
            // Check circularity
            val circularity = calculateCircularity(points)
            
            return when {
                circularity > 0.8f -> DetectionResult(true, ShapeType.CIRCLE, circularity)
                aspectRatio in 0.5f..2.0f -> DetectionResult(true, ShapeType.RECTANGLE, 0.7f)
                else -> DetectionResult(false)
            }
        } else {
            // Could be a line or arrow
            val linearity = calculateLinearity(points)
            
            return when {
                linearity > 0.9f -> DetectionResult(true, ShapeType.LINE, linearity)
                else -> DetectionResult(false)
            }
        }
    }
    
    private fun distance(p1: Offset, p2: Offset): Float {
        return sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2))
    }
    
    private fun getBoundingRect(points: List<Offset>): Rect {
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        return Rect(minX, minY, maxX, maxY)
    }
    
    private fun calculateCircularity(points: List<Offset>): Float {
        // Calculate how circular the shape is
        val rect = getBoundingRect(points)
        val center = Offset(rect.center.x, rect.center.y)
        val avgRadius = points.map { distance(it, center) }.average().toFloat()
        
        // Calculate variance in radius (lower = more circular)
        val variance = points.map { 
            (distance(it, center) - avgRadius).pow(2) 
        }.average().toFloat()
        
        // Normalize to 0-1 range
        return (1 - (sqrt(variance) / avgRadius)).coerceIn(0f, 1f)
    }
    
    private fun calculateLinearity(points: List<Offset>): Float {
        if (points.size < 2) return 0f
        
        // Direct distance from start to end
        val directDist = distance(points.first(), points.last())
        
        // Path length (sum of all segments)
        var pathLength = 0f
        for (i in 1 until points.size) {
            pathLength += distance(points[i-1], points[i])
        }
        
        // Linearity = direct / path (1.0 = perfectly straight)
        return if (pathLength > 0) (directDist / pathLength).coerceIn(0f, 1f) else 0f
    }
}
