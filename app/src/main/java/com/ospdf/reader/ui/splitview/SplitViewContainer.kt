package com.ospdf.reader.ui.splitview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Split view orientation.
 */
enum class SplitOrientation {
    HORIZONTAL, // Side by side
    VERTICAL    // Top and bottom
}

/**
 * Configuration for split view.
 */
data class SplitViewConfig(
    val orientation: SplitOrientation = SplitOrientation.HORIZONTAL,
    val ratio: Float = 0.5f,      // 0.0 to 1.0
    val minRatio: Float = 0.2f,
    val maxRatio: Float = 0.8f,
    val dividerWidth: Dp = 8.dp
)

/**
 * Split view container that divides the screen for viewing two documents.
 * Supports both horizontal (side-by-side) and vertical (top-bottom) layouts.
 */
@Composable
fun SplitViewContainer(
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable () -> Unit,
    config: SplitViewConfig = SplitViewConfig(),
    onRatioChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var ratio by remember { mutableFloatStateOf(config.ratio) }
    val configuration = LocalConfiguration.current
    
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalSize = when (config.orientation) {
            SplitOrientation.HORIZONTAL -> maxWidth
            SplitOrientation.VERTICAL -> maxHeight
        }
        
        when (config.orientation) {
            SplitOrientation.HORIZONTAL -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Primary pane
                    Box(
                        modifier = Modifier
                            .weight(ratio)
                            .fillMaxHeight()
                    ) {
                        primaryContent()
                    }
                    
                    // Divider
                    SplitDivider(
                        orientation = config.orientation,
                        width = config.dividerWidth,
                        onDrag = { delta ->
                            val newRatio = ratio + (delta / totalSize.value)
                            ratio = newRatio.coerceIn(config.minRatio, config.maxRatio)
                            onRatioChange(ratio)
                        }
                    )
                    
                    // Secondary pane
                    Box(
                        modifier = Modifier
                            .weight(1f - ratio)
                            .fillMaxHeight()
                    ) {
                        secondaryContent()
                    }
                }
            }
            SplitOrientation.VERTICAL -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Primary pane
                    Box(
                        modifier = Modifier
                            .weight(ratio)
                            .fillMaxWidth()
                    ) {
                        primaryContent()
                    }
                    
                    // Divider
                    SplitDivider(
                        orientation = config.orientation,
                        width = config.dividerWidth,
                        onDrag = { delta ->
                            val newRatio = ratio + (delta / totalSize.value)
                            ratio = newRatio.coerceIn(config.minRatio, config.maxRatio)
                            onRatioChange(ratio)
                        }
                    )
                    
                    // Secondary pane
                    Box(
                        modifier = Modifier
                            .weight(1f - ratio)
                            .fillMaxWidth()
                    ) {
                        secondaryContent()
                    }
                }
            }
        }
    }
}

/**
 * Draggable divider between split panes.
 */
@Composable
private fun SplitDivider(
    orientation: SplitOrientation,
    width: Dp,
    onDrag: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .then(
                when (orientation) {
                    SplitOrientation.HORIZONTAL -> Modifier
                        .width(width)
                        .fillMaxHeight()
                    SplitOrientation.VERTICAL -> Modifier
                        .height(width)
                        .fillMaxWidth()
                }
            )
            .background(MaterialTheme.colorScheme.outlineVariant)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = when (orientation) {
                        SplitOrientation.HORIZONTAL -> dragAmount.x
                        SplitOrientation.VERTICAL -> dragAmount.y
                    }
                    onDrag(delta)
                }
            }
    ) {
        // Divider grip indicator
        Box(
            modifier = Modifier
                .then(
                    when (orientation) {
                        SplitOrientation.HORIZONTAL -> Modifier
                            .width(4.dp)
                            .height(40.dp)
                        SplitOrientation.VERTICAL -> Modifier
                            .height(4.dp)
                            .width(40.dp)
                    }
                )
                .background(
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                )
        )
    }
}

/**
 * Quick toggle button for split view.
 */
@Composable
fun SplitViewToggle(
    isEnabled: Boolean,
    orientation: SplitOrientation,
    onToggle: () -> Unit,
    onOrientationChange: (SplitOrientation) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (isEnabled) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = "Toggle split view"
            )
        }
        
        if (isEnabled) {
            IconButton(
                onClick = {
                    onOrientationChange(
                        if (orientation == SplitOrientation.HORIZONTAL) 
                            SplitOrientation.VERTICAL 
                        else 
                            SplitOrientation.HORIZONTAL
                    )
                }
            ) {
                Icon(
                    imageVector = if (orientation == SplitOrientation.HORIZONTAL) Icons.Filled.Menu else Icons.Filled.List,
                    contentDescription = "Change orientation"
                )
            }
        }
    }
}

