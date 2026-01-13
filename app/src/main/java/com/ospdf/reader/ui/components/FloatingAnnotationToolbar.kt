package com.ospdf.reader.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ospdf.reader.domain.model.AnnotationTool
import com.ospdf.reader.domain.model.ShapeType
import com.ospdf.reader.domain.model.ToolState
import com.ospdf.reader.ui.theme.HighlighterColors
import com.ospdf.reader.ui.theme.InkColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Floating draggable annotation toolbar with semicircle wheel design.
 * Shows as a small FAB, expands to show tools in a rotating arc.
 */
@Composable
fun FloatingAnnotationToolbar(
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
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    // Screen dimensions in pixels
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // FAB and arc sizes
    val fabSizeDp = 56.dp
    val fabSizePx = with(density) { fabSizeDp.toPx() }
    val arcRadiusDp = 90.dp
    val arcRadiusPx = with(density) { arcRadiusDp.toPx() }
    
    // Position state - start at bottom right with good margin
    var offsetX by remember { mutableFloatStateOf(screenWidthPx - fabSizePx - 32f) }
    var offsetY by remember { mutableFloatStateOf(screenHeightPx - fabSizePx - 250f) }
    
    // Expansion and rotation state
    var isExpanded by remember { mutableStateOf(false) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var showSecondaryMenu by remember { mutableStateOf<String?>(null) }
    var showCustomColorPicker by remember { mutableStateOf(false) }
    
    // Detect which side of screen we're on to show arc correctly
    val isOnRightSide = offsetX > screenWidthPx / 2
    val isOnBottomHalf = offsetY > screenHeightPx / 2
    
    // Animation
    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "expandProgress"
    )
    
    // Custom color picker dialog
    if (showCustomColorPicker) {
        FloatingCustomColorPickerDialog(
            initialColor = toolState.currentColor,
            isHighlighter = toolState.currentTool == AnnotationTool.HIGHLIGHTER || 
                           toolState.currentTool == AnnotationTool.HIGHLIGHTER_2,
            onColorSelected = { color ->
                onColorSelected(color)
                showCustomColorPicker = false
                showSecondaryMenu = null
                isExpanded = false
            },
            onDismiss = { showCustomColorPicker = false }
        )
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Main FAB and arc menu
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .then(
                    if (!isExpanded) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidthPx - fabSizePx)
                                offsetY = (offsetY + dragAmount.y).coerceIn(0f, screenHeightPx - fabSizePx - 100f)
                            }
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            // Arc background and tool buttons
            if (expandProgress > 0f) {
                ArcToolMenu(
                    toolState = toolState,
                    expandProgress = expandProgress,
                    arcRadius = arcRadiusDp,
                    rotationAngle = rotationAngle,
                    isOnRightSide = isOnRightSide,
                    isOnBottomHalf = isOnBottomHalf,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onRotate = { delta -> rotationAngle += delta },
                    onToolSelected = { tool ->
                        onToolSelected(tool)
                        isExpanded = false
                        showSecondaryMenu = null
                    },
                    onUndo = {
                        onUndo()
                        // Don't close on undo/redo
                    },
                    onRedo = {
                        onRedo()
                        // Don't close on undo/redo
                    },
                    onColorClick = { 
                        showSecondaryMenu = if (showSecondaryMenu == "color") null else "color"
                    },
                    onStrokeClick = { 
                        showSecondaryMenu = if (showSecondaryMenu == "stroke") null else "stroke"
                    },
                    onShapeClick = { 
                        if (toolState.currentTool == AnnotationTool.SHAPE) {
                            showSecondaryMenu = if (showSecondaryMenu == "shape") null else "shape"
                        } else {
                            onToolSelected(AnnotationTool.SHAPE)
                            isExpanded = false
                        }
                    },
                    onClose = {
                        isExpanded = false
                        showSecondaryMenu = null
                        onClose()
                    }
                )
            }
            
            // Center FAB
            FloatingActionButton(
                onClick = { 
                    isExpanded = !isExpanded
                    if (!isExpanded) showSecondaryMenu = null
                },
                modifier = Modifier
                    .size(fabSizeDp)
                    .shadow(if (isExpanded) 4.dp else 8.dp, CircleShape),
                containerColor = if (isExpanded) 
                    MaterialTheme.colorScheme.surface
                else 
                    MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isExpanded)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = if (isExpanded) 
                        Icons.Filled.Close 
                    else if (toolState.currentTool == AnnotationTool.NONE)
                        Icons.Filled.Construction
                    else 
                        getToolIcon(toolState.currentTool, toolState.shapeType),
                    contentDescription = if (isExpanded) "Close" else "Annotation tools",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // Secondary menu panel
        AnimatedVisibility(
            visible = isExpanded && showSecondaryMenu != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
        ) {
            SecondaryMenuPanel(
                menuType = showSecondaryMenu,
                toolState = toolState,
                onColorSelected = { 
                    onColorSelected(it)
                    showSecondaryMenu = null
                    isExpanded = false
                },
                onStrokeWidthChanged = onStrokeWidthChanged,
                onShapeSelected = {
                    onShapeTypeSelected(it)
                    showSecondaryMenu = null
                    isExpanded = false
                },
                onCustomColorClick = { showCustomColorPicker = true },
                onDismiss = { showSecondaryMenu = null }
            )
        }
    }
}

@Composable
private fun ArcToolMenu(
    toolState: ToolState,
    expandProgress: Float,
    arcRadius: androidx.compose.ui.unit.Dp,
    rotationAngle: Float,
    isOnRightSide: Boolean,
    isOnBottomHalf: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onRotate: (Float) -> Unit,
    onToolSelected: (AnnotationTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onColorClick: () -> Unit,
    onStrokeClick: () -> Unit,
    onShapeClick: () -> Unit,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { arcRadius.toPx() }
    
    // Define tools for the arc
    val tools = listOf(
        ArcTool(Icons.Filled.Edit, "Pen", AnnotationTool.PEN, "tool"),
        ArcTool(Icons.Filled.Create, "Pen 2", AnnotationTool.PEN_2, "tool"),
        ArcTool(Icons.Filled.Highlight, "Highlighter", AnnotationTool.HIGHLIGHTER, "tool"),
        ArcTool(Icons.Filled.BorderColor, "Highlighter 2", AnnotationTool.HIGHLIGHTER_2, "tool"),
        ArcTool(Icons.Outlined.Delete, "Eraser", AnnotationTool.ERASER, "tool"),
        ArcTool(getShapeIcon(toolState.shapeType), "Shapes", AnnotationTool.SHAPE, "shape"),
        ArcTool(Icons.Outlined.Gesture, "Lasso", AnnotationTool.LASSO, "tool"),
        ArcTool(Icons.Default.Undo, "Undo", null, "undo"),
        ArcTool(Icons.Default.Redo, "Redo", null, "redo"),
        ArcTool(Icons.Filled.Palette, "Color", null, "color"),
        ArcTool(Icons.Filled.LineWeight, "Stroke", null, "stroke"),
        ArcTool(Icons.Filled.ExitToApp, "Exit", null, "exit"),
    )
    
    // Calculate arc direction based on position
    // If on right side → arc opens to the left (180° to 360°/0°)
    // If on left side → arc opens to the right (0° to 180°)
    // If on bottom → arc opens upward
    val baseAngle = when {
        isOnRightSide && isOnBottomHalf -> 180f  // Open to upper-left
        isOnRightSide && !isOnBottomHalf -> 180f // Open to lower-left  
        !isOnRightSide && isOnBottomHalf -> 0f   // Open to upper-right
        else -> 0f                                // Open to lower-right
    }
    
    // Arc spans full circle for even distribution
    val arcSpan = 360f
    val angleStep = arcSpan / tools.size
    
    // Drag to rotate
    Box(
        modifier = Modifier
            .size((arcRadius * 2 + 60.dp) * expandProgress)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Calculate rotation based on drag around center
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val prevAngle = atan2(
                        change.previousPosition.y - centerY,
                        change.previousPosition.x - centerX
                    )
                    val newAngle = atan2(
                        change.position.y - centerY,
                        change.position.x - centerX
                    )
                    val angleDelta = ((newAngle - prevAngle) * 180f / PI).toFloat()
                    onRotate(angleDelta)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Semi-transparent arc background
        Box(
            modifier = Modifier
                .size((arcRadius * 2 + 50.dp) * expandProgress)
                .alpha(expandProgress * 0.8f)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = CircleShape
                )
        )
        
        // Tool buttons arranged in arc
        tools.forEachIndexed { index, tool ->
            val angle = baseAngle + (index * angleStep) + rotationAngle
            val angleRad = angle * PI.toFloat() / 180f
            
            val x = cos(angleRad) * radiusPx * expandProgress
            val y = sin(angleRad) * radiusPx * expandProgress
            
            val isSelected = when (tool.type) {
                "tool", "shape" -> tool.annotationTool != null && toolState.currentTool == tool.annotationTool
                else -> false
            }
            
            val isEnabled = when (tool.type) {
                "undo" -> canUndo
                "redo" -> canRedo
                else -> true
            }
            
            Box(
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .alpha(expandProgress)
            ) {
                ArcToolButton(
                    icon = tool.icon,
                    contentDescription = tool.description,
                    isSelected = isSelected,
                    enabled = isEnabled,
                    showColorRing = tool.type == "color",
                    ringColor = if (tool.type == "color") toolState.currentColor else null,
                    onClick = {
                        when (tool.type) {
                            "tool" -> tool.annotationTool?.let { onToolSelected(it) }
                            "shape" -> onShapeClick()
                            "undo" -> onUndo()
                            "redo" -> onRedo()
                            "color" -> onColorClick()
                            "stroke" -> onStrokeClick()
                            "exit" -> onClose()
                        }
                    }
                )
            }
        }
    }
}

private data class ArcTool(
    val icon: ImageVector,
    val description: String,
    val annotationTool: AnnotationTool?,
    val type: String // "tool", "shape", "undo", "redo", "color", "stroke", "exit"
)

@Composable
private fun ArcToolButton(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    showColorRing: Boolean = false,
    ringColor: Color? = null,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    }
    
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SecondaryMenuPanel(
    menuType: String?,
    toolState: ToolState,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onShapeSelected: (ShapeType) -> Unit,
    onCustomColorClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.widthIn(max = 280.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (menuType) {
                "color" -> {
                    Text("Pick Color", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    ColorPickerRow(
                        colors = if (toolState.currentTool == AnnotationTool.HIGHLIGHTER || 
                                    toolState.currentTool == AnnotationTool.HIGHLIGHTER_2) 
                            HighlighterColors.all else InkColors.all,
                        selectedColor = toolState.currentColor,
                        onColorSelected = onColorSelected,
                        onCustomColorClick = onCustomColorClick
                    )
                }
                "stroke" -> {
                    Text("Stroke Width", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    StrokeWidthSlider(
                        strokeWidth = toolState.strokeWidth,
                        color = toolState.currentColor,
                        onStrokeWidthChanged = onStrokeWidthChanged
                    )
                }
                "shape" -> {
                    Text("Shape Type", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    ShapePickerRow(
                        selectedShape = toolState.shapeType,
                        onShapeSelected = onShapeSelected
                    )
                }
            }
        }
    }
}

private fun getToolIcon(tool: AnnotationTool, shapeType: ShapeType): ImageVector {
    return when (tool) {
        AnnotationTool.PEN -> Icons.Filled.Edit
        AnnotationTool.PEN_2 -> Icons.Filled.Create
        AnnotationTool.HIGHLIGHTER -> Icons.Filled.Highlight
        AnnotationTool.HIGHLIGHTER_2 -> Icons.Filled.BorderColor
        AnnotationTool.ERASER -> Icons.Outlined.Delete
        AnnotationTool.SHAPE -> getShapeIcon(shapeType)
        AnnotationTool.LASSO -> Icons.Outlined.Gesture
        AnnotationTool.TEXT -> Icons.Filled.TextFields
        AnnotationTool.NONE -> Icons.Filled.Construction
    }
}

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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        ShapeType.entries.forEach { shape ->
            ArcToolButton(
                icon = getShapeIcon(shape),
                contentDescription = shape.name,
                isSelected = shape == selectedShape,
                onClick = { onShapeSelected(shape) }
            )
        }
    }
}

@Composable
private fun ColorPickerRow(
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onCustomColorClick: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        items(colors) { color ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (color == selectedColor) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                        }
                    )
                    .clickable { onColorSelected(color) }
            )
        }
        item {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                        )
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                    .clickable { onCustomColorClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, "Custom", tint = Color.White, modifier = Modifier.size(18.dp))
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(strokeWidth.dp)
                    .clip(RoundedCornerShape(strokeWidth.dp / 2))
                    .background(color)
            )
        }
        Slider(
            value = strokeWidth,
            onValueChange = onStrokeWidthChanged,
            valueRange = 1f..20f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FloatingCustomColorPickerDialog(
    initialColor: Color,
    isHighlighter: Boolean,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
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
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Pick a Color", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Hue", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = hue, onValueChange = { hue = it }, valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.hsv(hue, 1f, 1f),
                        activeTrackColor = Color.hsv(hue, 1f, 1f)
                    )
                )
                
                Text("Saturation", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.hsv(hue, saturation, value),
                        activeTrackColor = Color.hsv(hue, 1f, value)
                    )
                )
                
                Text("Brightness", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = value, onValueChange = { value = it }, valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.hsv(hue, saturation, value),
                        activeTrackColor = Color.hsv(hue, saturation, 1f)
                    )
                )
                
                if (isHighlighter) {
                    Text("Opacity", style = MaterialTheme.typography.labelSmall)
                    Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0.2f..0.8f)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onColorSelected(selectedColor) }) { Text("Apply") }
                }
            }
        }
    }
}
