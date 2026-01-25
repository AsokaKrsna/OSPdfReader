package com.ospdf.reader.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ospdf.reader.domain.model.AnnotationTool
import com.ospdf.reader.domain.model.ShapeType
import com.ospdf.reader.domain.model.ToolState
import com.ospdf.reader.ui.theme.HighlighterColors
import com.ospdf.reader.ui.theme.InkColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.atan2

/**
 * Floating draggable annotation toolbar with a static "Double Arc" glassmorphic design.
 * Features two concentric rings of tools for efficiency without rotation.
 */
@Composable
fun FloatingAnnotationToolbar(
    modifier: Modifier = Modifier,
    toolState: ToolState,
    canUndo: Boolean,
    canRedo: Boolean,
    isZoomed: Boolean = false,
    onToolSelected: (AnnotationTool) -> Unit,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onShapeTypeSelected: (ShapeType) -> Unit = {},
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClose: () -> Unit,
    onResetZoom: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    // Screen dimensions in pixels
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // Config
    val fabSizeDp = 56.dp // standard compact FAB
    val fabSizePx = with(density) { fabSizeDp.toPx() }
    
    // Position state - start at mid-right side of screen
    var offsetX by remember { mutableFloatStateOf(screenWidthPx - fabSizePx - 48f) }
    var offsetY by remember { mutableFloatStateOf(screenHeightPx / 2 - fabSizePx / 2) }
    
    // Expansion state
    var isExpanded by remember { mutableStateOf(false) }
    var showSecondaryMenu by remember { mutableStateOf<String?>(null) }
    var showCustomColorPicker by remember { mutableStateOf(false) }
    
    // Detect quadrant for arc orientation
    // We want the arc to open *towards the center of the screen*
    val centerX = screenWidthPx / 2
    val centerY = screenHeightPx / 2
    
    val isOnRightSide = offsetX > centerX
    val isOnBottomHalf = offsetY > centerY
    
    // Animation
    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessLow
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
        // Scrim - invisible, but intercepts clicks to close menu
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                isExpanded = false
                                showSecondaryMenu = null
                                showCustomColorPicker = false
                            }
                        )
                    }
            )
        }

        // Main Interaction Layer
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .then(
                    // Only draggable when collapsed (or we can allow dragging the center button)
                    // Let's allow dragging when collapsed to avoid accidental drags while clicking tools
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
            // Dynamic Angle Calculation
            // Center of FAB
            val fabCenterX = offsetX + fabSizePx / 2
            val fabCenterY = offsetY + fabSizePx / 2
            
            // Vector to Screen Center
            val dx = centerX - fabCenterX
            val dy = centerY - fabCenterY
            
            // Angle to center (0..360)
            val angleRad = atan2(dy, dx)
            var angleDeg = (angleRad * 180 / PI).toFloat()
            if (angleDeg < 0) angleDeg += 360f
            
            // Dynamic Spacing / Collision Detection
            // We want to maximize spread (up to 160) but avoid clipping.
            // Loop to find safe spread
            var safeSpread = 160f
            val minSpread = 90f
            val outerRadiusPx = with(density) { 130.dp.toPx() } // Max extrusion
            
            // Simple iterative check: shrink if tips are off-screen
            while (safeSpread > minSpread) {
                // Check tips of the outer arc
                val startTip = angleDeg - (safeSpread / 2)
                val endTip = angleDeg + (safeSpread / 2)
                
                // Project tips
                val startRad = startTip * PI.toFloat() / 180f
                val endRad = endTip * PI.toFloat() / 180f
                
                val startX = fabCenterX + cos(startRad) * outerRadiusPx
                val startY = fabCenterY + sin(startRad) * outerRadiusPx
                
                val endX = fabCenterX + cos(endRad) * outerRadiusPx
                val endY = fabCenterY + sin(endRad) * outerRadiusPx
                
                // Margin of safety (e.g. 10px)
                val margin = 20f
                val isStartSafe = startX in margin..(screenWidthPx - margin) && startY in margin..(screenHeightPx - margin)
                val isEndSafe = endX in margin..(screenWidthPx - margin) && endY in margin..(screenHeightPx - margin)
                
                if (isStartSafe && isEndSafe) {
                    break // Safe!
                } else {
                    safeSpread -= 5f // Shrink and retry
                }
            }

            // Expanded Arc Menu
            if (expandProgress > 0.05f) {
                DoubleArcMenu(
                    toolState = toolState,
                    expandProgress = expandProgress,
                    centerAngle = angleDeg,
                    arcSpread = safeSpread, // Pass dynamic spread
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onToolSelected = { tool ->
                        onToolSelected(tool)
                        isExpanded = false
                        showSecondaryMenu = null
                    },
                    onUndo = onUndo,
                    onRedo = onRedo,
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
            
            // Main FAB (Center)
            // Premium Glassmorphic FAB
            Box(
                modifier = Modifier
                    .size(fabSizeDp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = if (isZoomed) {
                                listOf(Color(0xFFEF5350), Color(0xFFC62828))
                            } else {
                                listOf(Color(0xFF42A5F5), Color(0xFF1976D2))
                            }
                        )
                    )
                    .clickable {
                        if (isZoomed) {
                            onResetZoom()
                        } else {
                            isExpanded = !isExpanded
                            if (!isExpanded) showSecondaryMenu = null
                        }
                    }
                    .shadow(elevation = 8.dp, shape = CircleShape, spotColor = Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                 // Shine effect
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(0f, 0f),
                                radius = 100f
                            )
                        )
                )
                
                Icon(
                    imageVector = when {
                        isZoomed -> Icons.Filled.ZoomOut
                        isExpanded -> Icons.Filled.Close
                        toolState.currentTool == AnnotationTool.NONE -> Icons.Filled.Construction
                        else -> getToolIcon(toolState.currentTool, toolState.shapeType)
                    },
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // Secondary Menu (Settings Panel)
        AnimatedVisibility(
            visible = isExpanded && showSecondaryMenu != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
        ) {
            GlassmorphicPanel {
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
}

/**
 * Renders the two concentric arcs of tools.
 */
@Composable
private fun DoubleArcMenu(
    toolState: ToolState,
    expandProgress: Float,
    centerAngle: Float,
    arcSpread: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolSelected: (AnnotationTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onColorClick: () -> Unit,
    onStrokeClick: () -> Unit,
    onShapeClick: () -> Unit,
    onClose: () -> Unit
) {
    // Config
    val innerRadius = 68.dp
    val outerRadius = 112.dp 
    
    // Tools Lists - 5 Inner, 7 Outer
    // Inner Ring: Primary Essentials
    val innerTools = listOf(
        ArcTool(Icons.Filled.Edit, "Pen", AnnotationTool.PEN, "tool"),
        ArcTool(Icons.Filled.Highlight, "Highlighter", AnnotationTool.HIGHLIGHTER, "tool"),
        ArcTool(Icons.Outlined.Delete, "Eraser", AnnotationTool.ERASER, "tool"),
        ArcTool(getShapeIcon(toolState.shapeType), "Shapes", AnnotationTool.SHAPE, "shape"),
        ArcTool(Icons.Outlined.Gesture, "Lasso", AnnotationTool.LASSO, "tool")
    )
    
    // Outer Ring: Secondary & Actions
    val outerTools = listOf(
        ArcTool(Icons.Filled.Create, "Pen 2", AnnotationTool.PEN_2, "tool"),
        ArcTool(Icons.Filled.BorderColor, "Highlighter 2", AnnotationTool.HIGHLIGHTER_2, "tool"),
        ArcTool(Icons.Filled.Palette, "Color", null, "color"),
        ArcTool(Icons.Filled.LineWeight, "Stroke", null, "stroke"),
        ArcTool(Icons.Default.Undo, "Undo", null, "undo"),
        ArcTool(Icons.Default.Redo, "Redo", null, "redo"),
        ArcTool(Icons.Filled.ExitToApp, "Exit", null, "exit")
    )

    // Spreads
    // Synced for radial alignment (dynamic based on collision)
    val innerSpread = arcSpread
    val outerSpread = arcSpread
    
    // Center the spread on the dynamic centerAngle
    val innerStartAngle = centerAngle - (innerSpread / 2)
    val outerStartAngle = centerAngle - (outerSpread / 2)
    
    // Draw Glass Backgrounds for Arcs
    
    // Inner Ring
    RingSector(
        radius = innerRadius * expandProgress,
        startAngle = innerStartAngle,
        sweepAngle = innerSpread,
        items = innerTools,
        toolState = toolState,
        canUndo = canUndo, 
        canRedo = canRedo,
        iconScale = 1f,
        onAction = { tool -> handleToolAction(tool, onToolSelected, onShapeClick, onUndo, onRedo, onColorClick, onStrokeClick, onClose) }
    )
    
    // Outer Ring
    RingSector(
        radius = outerRadius * expandProgress,
        startAngle = outerStartAngle,
        sweepAngle = outerSpread,
        items = outerTools,
        toolState = toolState,
        canUndo = canUndo, 
        canRedo = canRedo,
        iconScale = 0.9f,
        onAction = { tool -> handleToolAction(tool, onToolSelected, onShapeClick, onUndo, onRedo, onColorClick, onStrokeClick, onClose) }
    )
}

private fun handleToolAction(
    tool: ArcTool,
    onToolSelected: (AnnotationTool) -> Unit,
    onShapeClick: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onColorClick: () -> Unit,
    onStrokeClick: () -> Unit,
    onClose: () -> Unit
) {
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

@Composable
private fun RingSector(
    radius: Dp,
    startAngle: Float,
    sweepAngle: Float,
    items: List<ArcTool>,
    toolState: ToolState,
    canUndo: Boolean,
    canRedo: Boolean,
    iconScale: Float,
    onAction: (ArcTool) -> Unit
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    
    if (radiusPx < 10f) return

    val step = if (items.size > 1) sweepAngle / (items.size - 1) else 0f
    
    items.forEachIndexed { index, tool ->
        val angle = startAngle + (index * step)
        val angleRad = angle * PI.toFloat() / 180f
        
        val x = cos(angleRad) * radiusPx
        val y = sin(angleRad) * radiusPx
        
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
                .alpha(if (radiusPx > 50f) 1f else 0f) // Hide when collapsed to center
        ) {
            GlassmorphicButton(
                icon = tool.icon,
                description = tool.description,
                isSelected = isSelected,
                isEnabled = isEnabled,
                iconScale = iconScale,
                ringColor = if (tool.type == "color") toolState.currentColor else null,
                onClick = { onAction(tool) }
            )
        }
    }
}

@Composable
private fun GlassmorphicButton(
    icon: ImageVector,
    description: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    iconScale: Float,
    ringColor: Color? = null,
    onClick: () -> Unit
) {
    val size = 42.dp
    
    Box(
        modifier = Modifier
            .size(size)
            .shadow(
                elevation = if (isSelected) 8.dp else 4.dp,
                shape = CircleShape,
                spotColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black
            )
            .clip(CircleShape)
            // Glass effect background
            .background(
                Brush.verticalGradient(
                    colors = if (isSelected) {
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    } else {
                        listOf(
                            Color(0xFF424242).copy(alpha = 0.75f),
                            Color(0xFF212121).copy(alpha = 0.95f)
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
            .clickable(enabled = isEnabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Color indicator ring
        if (ringColor != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, ringColor, CircleShape)
            )
        }
        
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (isEnabled) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(24.dp * iconScale)
        )
    }
}

@Composable
fun GlassmorphicPanel(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2C2C2C).copy(alpha = 0.95f),
                        Color(0xFF1A1A1A).copy(alpha = 0.98f)
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                RoundedCornerShape(24.dp)
            )
    ) {
        content()
    }
}

// Data Classes & Helpers (Reused/Refined)

private data class ArcTool(
    val icon: ImageVector,
    val description: String,
    val annotationTool: AnnotationTool?,
    val type: String // "tool", "shape", "undo", "redo", "color", "stroke", "exit"
)


/* ... existing helper functions ... */
// Reusing getToolIcon, getShapeIcon, ShapePickerRow, ColorPickerRow, StrokeWidthSlider, FloatingCustomColorPickerDialog 
// But ensure they are inside the file or accessible. 
// Since we are replacing the file content, I must re-include them.

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
private fun SecondaryMenuPanel(
    menuType: String?,
    toolState: ToolState,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onShapeSelected: (ShapeType) -> Unit,
    onCustomColorClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (menuType) {
            "color" -> {
                Text("Pick Color", style = MaterialTheme.typography.labelLarge, color = Color.White)
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
                Text("Stroke Width", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                StrokeWidthSlider(
                    strokeWidth = toolState.strokeWidth,
                    color = toolState.currentColor,
                    onStrokeWidthChanged = onStrokeWidthChanged
                )
            }
            "shape" -> {
                Text("Shape Type", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                ShapePickerRow(
                    selectedShape = toolState.shapeType,
                    onShapeSelected = onShapeSelected
                )
            }
        }
    }
}

@Composable
private fun ShapePickerRow(
    selectedShape: ShapeType,
    onShapeSelected: (ShapeType) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        ShapeType.entries.forEach { shape ->
            GlassmorphicButton(
                icon = getShapeIcon(shape),
                description = shape.name,
                isSelected = shape == selectedShape,
                isEnabled = true,
                iconScale = 0.9f,
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (color == selectedColor) {
                            Modifier.border(3.dp, Color.White, CircleShape)
                        } else {
                            Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        }
                    )
                    .clickable { onColorSelected(color) }
            )
        }
        item {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickable { onCustomColorClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, "Custom", tint = Color.White, modifier = Modifier.size(20.dp))
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
            modifier = Modifier.fillMaxWidth().height(24.dp),
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
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
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
        GlassmorphicPanel {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Pick a Color", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                        .border(2.dp, Color.White, CircleShape)
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Color Sliders
                val sliderColors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(alpha=0.8f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )

                Text("Hue", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Slider(
                    value = hue, onValueChange = { hue = it }, valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.hsv(hue, 1f, 1f),
                        activeTrackColor = Color.hsv(hue, 1f, 1f)
                    )
                )
                
                Text("Saturation", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Slider(
                    value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f,
                    colors = sliderColors
                )
                
                Text("Brightness", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Slider(
                    value = value, onValueChange = { value = it }, valueRange = 0f..1f,
                    colors = sliderColors
                )
                
                if (isHighlighter) {
                    Text("Opacity", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0.2f..0.8f, colors = sliderColors)
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(alpha=0.7f)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onColorSelected(selectedColor) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Apply") }
                }
            }
        }
    }
}
