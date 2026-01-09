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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.ospdf.reader.ui.components.InkStroke
import com.ospdf.reader.ui.theme.InkColors
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Represents a lasso selection with selected items.
 */
data class LassoSelection(
    val path: List<Offset>,
    val selectedStrokeIds: Set<String> = emptySet(),
    val selectedShapeIds: Set<String> = emptySet(),
    val bounds: Rect? = null
) {
    val isEmpty: Boolean get() = selectedStrokeIds.isEmpty() && selectedShapeIds.isEmpty()
    val hasSelection: Boolean get() = !isEmpty
}

/**
 * Canvas for lasso selection of annotations.
 * Allows selecting, moving, and deleting groups of strokes/shapes.
 * Uses stylus-only input with finger pass-through for page navigation.
 */
@Composable
fun LassoSelectionCanvas(
    modifier: Modifier = Modifier,
    strokes: List<InkStroke>,
    shapes: List<ShapeAnnotation>,
    enabled: Boolean = true,
    zoomScale: Float = 1f,
    zoomOffset: Offset = Offset.Zero,
    containerSize: IntSize = IntSize.Zero,
    currentSelection: LassoSelection?,
    onSelectionComplete: (LassoSelection) -> Unit,
    onSelectionMove: (Offset) -> Unit,
    onSelectionClear: () -> Unit
) {
    var lassoPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isDrawingLasso by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var lastDragPoint by remember { mutableStateOf<Offset?>(null) }
    
    // Use rememberUpdatedState to capture latest values for AndroidView callbacks
    val currentStrokes by rememberUpdatedState(strokes)
    val currentShapes by rememberUpdatedState(shapes)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentSelectionState by rememberUpdatedState(currentSelection)
    val currentOnSelectionComplete by rememberUpdatedState(onSelectionComplete)
    val currentOnSelectionMove by rememberUpdatedState(onSelectionMove)
    val currentOnSelectionClear by rememberUpdatedState(onSelectionClear)
    val currentZoomScale by rememberUpdatedState(zoomScale)
    val currentZoomOffset by rememberUpdatedState(zoomOffset)
    val currentContainerSize by rememberUpdatedState(containerSize)
    
    // Function to transform screen coordinates to content coordinates
    fun transformPoint(screenX: Float, screenY: Float): Offset {
        if (currentZoomScale == 1f && currentZoomOffset == Offset.Zero) {
            return Offset(screenX, screenY)
        }
        val centerX = currentContainerSize.width / 2f
        val centerY = currentContainerSize.height / 2f
        val contentX = (screenX - centerX - currentZoomOffset.x) / currentZoomScale + centerX
        val contentY = (screenY - centerY - currentZoomOffset.y) / currentZoomScale + centerY
        return Offset(contentX, contentY)
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Drawing canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw current lasso being drawn
            if (isDrawingLasso && lassoPoints.isNotEmpty()) {
                drawLassoPath(lassoPoints)
            }
            
            // Draw selection bounds
            currentSelection?.bounds?.let { bounds ->
                drawSelectionBounds(bounds)
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
                        
                        // When zoomed in, disable lasso - let user zoom out first
                        if (currentZoomScale > 1.05f) return false
                        
                        val point = transformPoint(event.x, event.y)
                        
                        // Prevent parent from intercepting stylus
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        
                        return when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                // Check if tapping inside existing selection to drag it
                                if (currentSelectionState?.bounds?.contains(point) == true) {
                                    isDragging = true
                                    lastDragPoint = point
                                } else {
                                    // Start new lasso
                                    isDrawingLasso = true
                                    lassoPoints = listOf(point)
                                    currentOnSelectionClear()
                                }
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (isDragging && lastDragPoint != null) {
                                    val delta = point - lastDragPoint!!
                                    currentOnSelectionMove(delta)
                                    lastDragPoint = point
                                } else if (isDrawingLasso) {
                                    lassoPoints = lassoPoints + point
                                }
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                parent?.requestDisallowInterceptTouchEvent(false)
                                
                                if (isDrawingLasso && lassoPoints.size > 3) {
                                    val selection = performLassoSelection(
                                        lassoPath = lassoPoints,
                                        strokes = currentStrokes,
                                        shapes = currentShapes
                                    )
                                    currentOnSelectionComplete(selection)
                                }
                                
                                lassoPoints = emptyList()
                                isDrawingLasso = false
                                isDragging = false
                                lastDragPoint = null
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
 * Draws the lasso selection path.
 */
private fun DrawScope.drawLassoPath(points: List<Offset>) {
    if (points.size < 2) return
    
    val path = Path()
    path.moveTo(points.first().x, points.first().y)
    
    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }
    
    // Close the path
    path.close()
    
    // Draw fill with low opacity
    drawPath(
        path = path,
        color = InkColors.Blue.copy(alpha = 0.1f)
    )
    
    // Draw stroke
    drawPath(
        path = path,
        color = InkColors.Blue.copy(alpha = 0.8f),
        style = Stroke(
            width = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    )
}

/**
 * Draws selection bounds with handles.
 */
private fun DrawScope.drawSelectionBounds(bounds: Rect) {
    // Draw bounding rectangle
    drawRect(
        color = InkColors.Blue.copy(alpha = 0.3f),
        topLeft = bounds.topLeft,
        size = bounds.size,
        style = Stroke(
            width = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
        )
    )
    
    // Draw corner handles
    val handleSize = 12f
    val handles = listOf(
        bounds.topLeft,
        bounds.topRight,
        bounds.bottomLeft,
        bounds.bottomRight
    )
    
    handles.forEach { handle ->
        drawCircle(
            color = InkColors.Blue,
            radius = handleSize / 2,
            center = handle
        )
        drawCircle(
            color = Color.White,
            radius = handleSize / 3,
            center = handle
        )
    }
}

/**
 * Performs lasso selection to find strokes and shapes inside the lasso path.
 */
private fun performLassoSelection(
    lassoPath: List<Offset>,
    strokes: List<InkStroke>,
    shapes: List<ShapeAnnotation>
): LassoSelection {
    val selectedStrokeIds = mutableSetOf<String>()
    val selectedShapeIds = mutableSetOf<String>()
    
    // Check each stroke
    for (stroke in strokes) {
        // Stroke is selected if most of its points are inside the lasso
        val insideCount = stroke.points.count { point ->
            isPointInPolygon(Offset(point.x, point.y), lassoPath)
        }
        
        if (insideCount > stroke.points.size * 0.5) {
            selectedStrokeIds.add(stroke.id)
        }
    }
    
    // Check each shape
    for (shape in shapes) {
        // Shape is selected if its center is inside the lasso
        val center = Offset(shape.centerX, shape.centerY)
        if (isPointInPolygon(center, lassoPath)) {
            selectedShapeIds.add(shape.id)
        }
    }
    
    // Calculate combined bounds
    val bounds = calculateSelectionBounds(
        strokes.filter { it.id in selectedStrokeIds },
        shapes.filter { it.id in selectedShapeIds }
    )
    
    return LassoSelection(
        path = lassoPath,
        selectedStrokeIds = selectedStrokeIds,
        selectedShapeIds = selectedShapeIds,
        bounds = bounds
    )
}

/**
 * Calculates bounding rectangle for selected items.
 */
private fun calculateSelectionBounds(
    strokes: List<InkStroke>,
    shapes: List<ShapeAnnotation>
): Rect? {
    val allPoints = mutableListOf<Offset>()
    
    strokes.forEach { stroke ->
        allPoints.addAll(stroke.points.map { Offset(it.x, it.y) })
    }
    
    shapes.forEach { shape ->
        allPoints.add(Offset(shape.startX, shape.startY))
        allPoints.add(Offset(shape.endX, shape.endY))
    }
    
    if (allPoints.isEmpty()) return null
    
    return Rect(
        left = allPoints.minOf { it.x } - 10,
        top = allPoints.minOf { it.y } - 10,
        right = allPoints.maxOf { it.x } + 10,
        bottom = allPoints.maxOf { it.y } + 10
    )
}

/**
 * Ray casting algorithm to check if point is inside polygon.
 */
private fun isPointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false
    
    var inside = false
    var j = polygon.size - 1
    
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        
        if ((pi.y > point.y) != (pj.y > point.y) &&
            point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x) {
            inside = !inside
        }
        
        j = i
    }
    
    return inside
}
