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
    strokes: List<com.ospdf.reader.domain.model.InkStroke> = emptyList(),
    toolState: ToolState,
    enabled: Boolean = true,
    onShapeComplete: (ShapeAnnotation) -> Unit = {},
    onShapeErase: (String) -> Unit = {},
    onStrokeErase: (String) -> Unit = {},
    onStylusActiveChange: (Boolean) -> Unit = {}
) {
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var currentPoint by remember { mutableStateOf<Offset?>(null) }
    var isDrawing by remember { mutableStateOf(false) }
    
    // Use rememberUpdatedState to capture latest values for AndroidView callbacks
    val currentToolState by rememberUpdatedState(toolState)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentShapes by rememberUpdatedState(shapes)
    val currentStrokes by rememberUpdatedState(strokes)
    val currentOnShapeComplete by rememberUpdatedState(onShapeComplete)
    val currentOnShapeErase by rememberUpdatedState(onShapeErase)
    val currentOnStrokeErase by rememberUpdatedState(onStrokeErase)
    val currentOnStylusActiveChange by rememberUpdatedState(onStylusActiveChange)
    
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
                        val isFinger = event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
                        
                        // Let finger input pass through for page navigation
                        if (isFinger) {
                            return false
                        }
                        
                        // Allow multi-touch for zoom/pan
                        if (event.pointerCount > 1) {
                            parent?.requestDisallowInterceptTouchEvent(false)
                            return false
                        }
                        
                        // Prevent parent from intercepting stylus AND update lock state
                        if (isStylus) {
                            if (event.action == MotionEvent.ACTION_DOWN) {
                                parent?.requestDisallowInterceptTouchEvent(true)
                                currentOnStylusActiveChange(true)
                            } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                                // We'll unlock in the ACTION_UP block below, ensuring it happens even if we return true earlier
                            }
                        }
                        
                        return when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                // Check if eraser is active
                                if (currentToolState.currentTool == com.ospdf.reader.domain.model.AnnotationTool.ERASER) {
                                    // Handle eraser: check hits for BOTH shapes and strokes
                                    val shapeHit = checkShapeEraserHit(event.x, event.y, currentShapes, currentOnShapeErase, currentToolState.eraserWidth)
                                    val strokeHit = checkStrokeEraserHit(event.x, event.y, currentStrokes, currentOnStrokeErase, currentToolState.eraserWidth)
                                    
                                    // Always consume Stylus/Eraser events in Eraser mode to prevent Pager from scrolling.
                                    // Even if we didn't hit anything, we don't want the page to move while using the eraser tool.
                                    true
                                } else {
                                    isDrawing = true
                                    startPoint = Offset(event.x, event.y)
                                    currentPoint = Offset(event.x, event.y)
                                    true
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                // For eraser, continuously check hits
                                if (currentToolState.currentTool == com.ospdf.reader.domain.model.AnnotationTool.ERASER) {
                                    checkShapeEraserHit(event.x, event.y, currentShapes, currentOnShapeErase, currentToolState.eraserWidth)
                                    checkStrokeEraserHit(event.x, event.y, currentStrokes, currentOnStrokeErase, currentToolState.eraserWidth)
                                    true
                                } else if (isDrawing) {
                                    currentPoint = Offset(event.x, event.y)
                                    true
                                } else {
                                    false
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                parent?.requestDisallowInterceptTouchEvent(false)
                                currentOnStylusActiveChange(false)
                                
                                if (currentToolState.currentTool != com.ospdf.reader.domain.model.AnnotationTool.ERASER 
                                    && isDrawing && startPoint != null && currentPoint != null) {
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
 * Checks if the eraser position hits any stroke.
 * Reusing logic similar to InkingCanvas but adapted for this context.
 */
private fun checkStrokeEraserHit(
    x: Float,
    y: Float,
    strokes: List<com.ospdf.reader.domain.model.InkStroke>,
    onStrokeErase: (String) -> Unit,
    eraserRadius: Float
): Boolean {
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
            return true
        }
    }
    return false
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

/**
 * Checks if the eraser position hits any shape.
 * Returns true if a shape was hit and erased, false otherwise.
 */
private fun checkShapeEraserHit(
    x: Float,
    y: Float,
    shapes: List<ShapeAnnotation>,
    onShapeErase: (String) -> Unit,
    eraserRadius: Float = 20f
): Boolean {
    val hitPoint = Offset(x, y)
    
    for (shape in shapes) {
        val isHit = when (shape.type) {
            ShapeType.LINE -> {
                // Check distance from point to line segment
                distanceToLineSegment(
                    hitPoint,
                    Offset(shape.startX, shape.startY),
                    Offset(shape.endX, shape.endY)
                ) < eraserRadius + shape.strokeWidth
            }
            ShapeType.RECTANGLE -> {
                // Check if point is near rectangle edges
                val bounds = shape.bounds
                val expanded = androidx.compose.ui.geometry.Rect(
                    left = bounds.left - eraserRadius,
                    top = bounds.top - eraserRadius,
                    right = bounds.right + eraserRadius,
                    bottom = bounds.bottom + eraserRadius
                )
                expanded.contains(hitPoint) && !shape.bounds.deflate(eraserRadius).contains(hitPoint)
            }
            ShapeType.CIRCLE -> {
                // Check if point is near circle edge
                val center = Offset(shape.centerX, shape.centerY)
                val radius = maxOf(shape.width, shape.height) / 2
                val dist = (hitPoint - center).getDistance()
                abs(dist - radius) < eraserRadius + shape.strokeWidth
            }
            ShapeType.ARROW -> {
                // Same as line for arrow
                distanceToLineSegment(
                    hitPoint,
                    Offset(shape.startX, shape.startY),
                    Offset(shape.endX, shape.endY)
                ) < eraserRadius + shape.strokeWidth
            }
        }
        
        if (isHit) {
            onShapeErase(shape.id)
            return true // Shape was hit and erased
        }
    }
    
    return false // No shape was hit
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
