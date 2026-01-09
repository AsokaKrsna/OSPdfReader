package com.ospdf.reader.domain.model

import androidx.compose.ui.graphics.Color
import com.ospdf.reader.ui.theme.InkColors
import com.ospdf.reader.ui.theme.HighlighterColors

/**
 * Types of annotation tools available.
 */
enum class AnnotationTool {
    NONE,        // No tool selected (finger mode for navigation)
    PEN,         // Freehand drawing (pen 1)
    PEN_2,       // Second pen with separate settings
    HIGHLIGHTER, // Semi-transparent highlighting (highlighter 1)
    HIGHLIGHTER_2, // Second highlighter with separate settings
    ERASER,      // Erase annotations
    LASSO,       // Select annotations
    TEXT,        // Text annotations
    SHAPE        // Shapes (line, rectangle, circle, arrow)
}

/**
 * Types of shapes for the shape tool.
 */
enum class ShapeType {
    LINE,
    RECTANGLE,
    CIRCLE,
    ARROW
}

/**
 * Represents a stroke (ink annotation).
 */
data class Stroke(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pageNumber: Int,
    val points: List<StrokePoint>,
    val color: Color = InkColors.Black,
    val strokeWidth: Float = 3f,
    val isHighlighter: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A single point in a stroke with pressure information.
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a shape annotation.
 */
data class ShapeAnnotation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pageNumber: Int,
    val type: ShapeType,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val color: Color = InkColors.Black,
    val strokeWidth: Float = 2f,
    val isFilled: Boolean = false
)

/**
 * Represents a text annotation.
 */
data class TextAnnotation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pageNumber: Int,
    val x: Float,
    val y: Float,
    val text: String,
    val color: Color = InkColors.Black,
    val fontSize: Float = 14f
)

/**
 * Settings for an individual tool.
 */
data class ToolSettings(
    val color: Color,
    val strokeWidth: Float
)

/**
 * Current tool state for the annotation toolbar with per-tool settings.
 */
data class ToolState(
    val currentTool: AnnotationTool = AnnotationTool.NONE,
    val shapeType: ShapeType = ShapeType.LINE,
    val isEraserStrokeBased: Boolean = true, // true = erase whole stroke, false = partial erase
    
    // Per-tool settings
    val penSettings: ToolSettings = ToolSettings(InkColors.Black, 3f),
    val pen2Settings: ToolSettings = ToolSettings(InkColors.Red, 3f),
    val highlighterSettings: ToolSettings = ToolSettings(HighlighterColors.Yellow, 20f),
    val highlighter2Settings: ToolSettings = ToolSettings(HighlighterColors.Green, 20f),
    val textSettings: ToolSettings = ToolSettings(InkColors.Black, 14f),
    val shapeSettings: ToolSettings = ToolSettings(InkColors.Black, 2f),
    val eraserWidth: Float = 20f
) {
    /**
     * Get the current color based on selected tool.
     */
    val currentColor: Color
        get() = when (currentTool) {
            AnnotationTool.PEN -> penSettings.color
            AnnotationTool.PEN_2 -> pen2Settings.color
            AnnotationTool.HIGHLIGHTER -> highlighterSettings.color
            AnnotationTool.HIGHLIGHTER_2 -> highlighter2Settings.color
            AnnotationTool.TEXT -> textSettings.color
            AnnotationTool.SHAPE -> shapeSettings.color
            else -> InkColors.Black
        }
    
    /**
     * Get the current stroke width based on selected tool.
     */
    val strokeWidth: Float
        get() = when (currentTool) {
            AnnotationTool.PEN -> penSettings.strokeWidth
            AnnotationTool.PEN_2 -> pen2Settings.strokeWidth
            AnnotationTool.HIGHLIGHTER -> highlighterSettings.strokeWidth
            AnnotationTool.HIGHLIGHTER_2 -> highlighter2Settings.strokeWidth
            AnnotationTool.TEXT -> textSettings.strokeWidth
            AnnotationTool.SHAPE -> shapeSettings.strokeWidth
            AnnotationTool.ERASER -> eraserWidth
            else -> 3f
        }
}
