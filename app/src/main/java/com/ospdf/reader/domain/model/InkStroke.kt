package com.ospdf.reader.domain.model

import android.graphics.Path
import androidx.compose.ui.graphics.Color

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
