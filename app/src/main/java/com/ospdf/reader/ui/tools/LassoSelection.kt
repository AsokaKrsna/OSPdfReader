package com.ospdf.reader.ui.tools

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
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
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LassoSelectionCanvas(
    modifier: Modifier = Modifier,
    strokes: List<InkStroke>,
    shapes: List<ShapeAnnotation>,
    enabled: Boolean = true,
    currentSelection: LassoSelection?,
    onSelectionComplete: (LassoSelection) -> Unit,
    onSelectionMove: (Offset) -> Unit,
    onSelectionClear: () -> Unit
) {
    var lassoPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isDrawingLasso by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var lastDragPoint by remember { mutableStateOf<Offset?>(null) }
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                if (!enabled) return@pointerInteropFilter false
                
                val point = Offset(event.x, event.y)
                
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Check if tapping inside existing selection to drag it
                        if (currentSelection?.bounds?.contains(point) == true) {
                            isDragging = true
                            lastDragPoint = point
                        } else {
                            // Start new lasso
                            isDrawingLasso = true
                            lassoPoints = listOf(point)
                            onSelectionClear()
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging && lastDragPoint != null) {
                            // Move selection
                            val delta = point - lastDragPoint!!
                            onSelectionMove(delta)
                            lastDragPoint = point
                        } else if (isDrawingLasso) {
                            lassoPoints = lassoPoints + point
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDrawingLasso && lassoPoints.size > 3) {
                            // Complete lasso selection
                            val selection = performLassoSelection(
                                lassoPath = lassoPoints,
                                strokes = strokes,
                                shapes = shapes
                            )
                            onSelectionComplete(selection)
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
    ) {
        // Draw current lasso being drawn
        if (isDrawingLasso && lassoPoints.isNotEmpty()) {
            drawLassoPath(lassoPoints)
        }
        
        // Draw selection bounds
        currentSelection?.bounds?.let { bounds ->
            drawSelectionBounds(bounds)
        }
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
