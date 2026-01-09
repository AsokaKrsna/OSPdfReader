package com.ospdf.reader.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ospdf.reader.domain.model.AnnotationTool
import com.ospdf.reader.domain.model.ShapeType
import com.ospdf.reader.domain.model.ToolState
import com.ospdf.reader.ui.theme.HighlighterColors
import com.ospdf.reader.ui.theme.InkColors
import kotlin.math.roundToInt

/**
 * Floating annotation toolbar with all tools.
 * Minimalist design with expandable sections.
 */
@Composable
fun AnnotationToolbar(
    modifier: Modifier = Modifier,
    toolState: ToolState,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolSelected: (AnnotationTool) -> Unit,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onShapeTypeSelected: (ShapeType) -> Unit = {},
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClose: () -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showStrokeWidth by remember { mutableStateOf(false) }
    var showShapePicker by remember { mutableStateOf(false) }
    var showCustomColorPicker by remember { mutableStateOf(false) }
    
    // Custom color picker dialog
    if (showCustomColorPicker) {
        CustomColorPickerDialog(
            initialColor = toolState.currentColor,
            isHighlighter = toolState.currentTool == AnnotationTool.HIGHLIGHTER || 
                           toolState.currentTool == AnnotationTool.HIGHLIGHTER_2,
            onColorSelected = { color ->
                onColorSelected(color)
                showCustomColorPicker = false
                showColorPicker = false
            },
            onDismiss = { showCustomColorPicker = false }
        )
    }
    
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main tool row
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button
            ToolbarIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close tools",
                onClick = onClose
            )
            
            Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
            
            // Undo/Redo
            ToolbarIconButton(
                icon = Icons.Default.Undo,
                contentDescription = "Undo",
                enabled = canUndo,
                onClick = onUndo
            )
            ToolbarIconButton(
                icon = Icons.Default.Redo,
                contentDescription = "Redo",
                enabled = canRedo,
                onClick = onRedo
            )
            
            Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
            
            // Pen tool
            ToolbarIconButton(
                icon = Icons.Filled.Edit,
                contentDescription = "Pen",
                isSelected = toolState.currentTool == AnnotationTool.PEN,
                onClick = { onToolSelected(AnnotationTool.PEN) }
            )
            
            // Pen 2 tool (second pen with separate color)
            ToolbarIconButton(
                icon = Icons.Filled.Create,
                contentDescription = "Pen 2",
                isSelected = toolState.currentTool == AnnotationTool.PEN_2,
                onClick = { onToolSelected(AnnotationTool.PEN_2) }
            )
            
            // Highlighter
            ToolbarIconButton(
                icon = Icons.Filled.Highlight,
                contentDescription = "Highlighter",
                isSelected = toolState.currentTool == AnnotationTool.HIGHLIGHTER,
                onClick = { onToolSelected(AnnotationTool.HIGHLIGHTER) }
            )
            
            // Highlighter 2 (second highlighter with separate color)
            ToolbarIconButton(
                icon = Icons.Filled.BorderColor,
                contentDescription = "Highlighter 2",
                isSelected = toolState.currentTool == AnnotationTool.HIGHLIGHTER_2,
                onClick = { onToolSelected(AnnotationTool.HIGHLIGHTER_2) }
            )
            
            // Eraser
            ToolbarIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = "Eraser",
                isSelected = toolState.currentTool == AnnotationTool.ERASER,
                onClick = { onToolSelected(AnnotationTool.ERASER) }
            )
            
            Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
            
            // Shapes tool
            ToolbarIconButton(
                icon = getShapeIcon(toolState.shapeType),
                contentDescription = "Shapes",
                isSelected = toolState.currentTool == AnnotationTool.SHAPE,
                onClick = { 
                    if (toolState.currentTool == AnnotationTool.SHAPE) {
                        showShapePicker = !showShapePicker
                    } else {
                        onToolSelected(AnnotationTool.SHAPE)
                    }
                }
            )
            
            // Lasso selection
            ToolbarIconButton(
                icon = Icons.Outlined.Gesture,
                contentDescription = "Lasso",
                isSelected = toolState.currentTool == AnnotationTool.LASSO,
                onClick = { onToolSelected(AnnotationTool.LASSO) }
            )
            
            Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
            
            // Color picker button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(toolState.currentColor)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { 
                        showColorPicker = !showColorPicker 
                        showStrokeWidth = false
                        showShapePicker = false
                    }
            )
            
            // Stroke width button
            ToolbarIconButton(
                icon = Icons.Filled.LineWeight,
                contentDescription = "Stroke width",
                onClick = { 
                    showStrokeWidth = !showStrokeWidth 
                    showColorPicker = false
                    showShapePicker = false
                }
            )
        }
        
        // Shape picker row (expandable)
        AnimatedVisibility(
            visible = showShapePicker && toolState.currentTool == AnnotationTool.SHAPE,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ShapePickerRow(
                selectedShape = toolState.shapeType,
                onShapeSelected = { shape ->
                    onShapeTypeSelected(shape)
                    showShapePicker = false
                }
            )
        }
        
        // Color picker row (expandable)
        AnimatedVisibility(
            visible = showColorPicker,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ColorPickerRow(
                colors = if (toolState.currentTool == AnnotationTool.HIGHLIGHTER || 
                            toolState.currentTool == AnnotationTool.HIGHLIGHTER_2) 
                    HighlighterColors.all else InkColors.all,
                selectedColor = toolState.currentColor,
                onColorSelected = { color ->
                    onColorSelected(color)
                    showColorPicker = false
                },
                onCustomColorClick = { showCustomColorPicker = true }
            )
        }
        
        // Stroke width slider (expandable)
        AnimatedVisibility(
            visible = showStrokeWidth,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            StrokeWidthSlider(
                strokeWidth = toolState.strokeWidth,
                color = toolState.currentColor,
                onStrokeWidthChanged = onStrokeWidthChanged
            )
        }
    }
}

/**
 * Returns the appropriate icon for each shape type.
 */
private fun getShapeIcon(shapeType: ShapeType): ImageVector {
    return when (shapeType) {
        ShapeType.LINE -> Icons.Filled.HorizontalRule
        ShapeType.RECTANGLE -> Icons.Outlined.Crop54
        ShapeType.CIRCLE -> Icons.Outlined.Circle
        ShapeType.ARROW -> Icons.Filled.ArrowRightAlt
    }
}

@Composable
private fun ShapePickerRow(
    selectedShape: ShapeType,
    onShapeSelected: (ShapeType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        ShapeType.entries.forEach { shape ->
            ToolbarIconButton(
                icon = getShapeIcon(shape),
                contentDescription = shape.name,
                isSelected = shape == selectedShape,
                onClick = { onShapeSelected(shape) }
            )
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ColorPickerRow(
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onCustomColorClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(colors) { color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (color == selectedColor) {
                                Modifier.border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                            } else {
                                Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                            }
                        )
                        .clickable { onColorSelected(color) }
                )
            }
            
            // Custom color picker button (rainbow gradient with +)
            item {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color.Red,
                                    Color.Yellow,
                                    Color.Green,
                                    Color.Cyan,
                                    Color.Blue,
                                    Color.Magenta,
                                    Color.Red
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .clickable { onCustomColorClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Custom color",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StrokeWidthSlider(
    strokeWidth: Float,
    color: Color,
    onStrokeWidthChanged: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
    ) {
        // Preview line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(strokeWidth.dp)
                    .clip(RoundedCornerShape(strokeWidth.dp / 2))
                    .background(color)
            )
        }
        
        // Slider
        Slider(
            value = strokeWidth,
            onValueChange = onStrokeWidthChanged,
            valueRange = 1f..20f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Compact floating action buttons for quick tool access.
 */
@Composable
fun QuickToolFab(
    modifier: Modifier = Modifier,
    onAnnotateClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onAnnotateClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Icon(
            Icons.Filled.Edit,
            contentDescription = "Annotate",
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Custom color picker dialog with HSV color selection.
 */
@Composable
private fun CustomColorPickerDialog(
    initialColor: Color,
    isHighlighter: Boolean,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    // Convert initial color to HSV
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.argb(
                (initialColor.alpha * 255).roundToInt(),
                (initialColor.red * 255).roundToInt(),
                (initialColor.green * 255).roundToInt(),
                (initialColor.blue * 255).roundToInt()
            ),
            hsv
        )
        hsv
    }
    
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember { mutableFloatStateOf(if (isHighlighter) 0.5f else 1f) }
    
    val selectedColor = remember(hue, saturation, value, alpha) {
        Color.hsv(hue, saturation, value, alpha)
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Pick a Color",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Color preview
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Hue slider (rainbow)
                Text("Hue", style = MaterialTheme.typography.labelSmall)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) }
                            )
                        )
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                hue = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                            }
                        }
                ) {
                    // Hue indicator
                    Box(
                        modifier = Modifier
                            .offset(x = ((hue / 360f) * 280).dp) // Approximate
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, Color.Black, CircleShape)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Saturation slider
                Text("Saturation", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.hsv(hue, saturation, value),
                        activeTrackColor = Color.hsv(hue, 1f, value)
                    )
                )
                
                // Brightness slider
                Text("Brightness", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.hsv(hue, saturation, value),
                        activeTrackColor = Color.hsv(hue, saturation, 1f)
                    )
                )
                
                // Alpha slider for highlighters
                if (isHighlighter) {
                    Text("Opacity", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = alpha,
                        onValueChange = { alpha = it },
                        valueRange = 0.2f..0.8f
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Quick color presets
                Text("Presets", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val presets = if (isHighlighter) {
                        listOf(
                            Color(0xFFFFEB3B), // Yellow
                            Color(0xFF4CAF50), // Green
                            Color(0xFF2196F3), // Blue
                            Color(0xFFE91E63), // Pink
                            Color(0xFFFF9800), // Orange
                            Color(0xFF9C27B0)  // Purple
                        )
                    } else {
                        listOf(
                            Color.Black,
                            Color.Red,
                            Color.Blue,
                            Color.Green,
                            Color(0xFFFF9800), // Orange
                            Color(0xFF9C27B0)  // Purple
                        )
                    }
                    
                    presets.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isHighlighter) color.copy(alpha = 0.5f) else color)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(
                                        android.graphics.Color.argb(
                                            255,
                                            (color.red * 255).roundToInt(),
                                            (color.green * 255).roundToInt(),
                                            (color.blue * 255).roundToInt()
                                        ),
                                        hsv
                                    )
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    value = hsv[2]
                                }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onColorSelected(selectedColor) }) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}
