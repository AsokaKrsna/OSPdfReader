package com.ospdf.reader.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ospdf.reader.ui.theme.InkColors
import java.util.UUID

/**
 * Represents a text annotation on a PDF page.
 */
data class TextAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val pageNumber: Int = 0,
    val x: Float,
    val y: Float,
    val text: String,
    val color: Color = InkColors.Black,
    val fontSize: Float = 16f,
    val backgroundColor: Color = Color.Transparent
)

/**
 * Floating text input dialog for adding text annotations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextAnnotationInput(
    initialText: String = "",
    initialColor: Color = InkColors.Black,
    initialFontSize: Float = 16f,
    onConfirm: (String, Color, Float) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var fontSize by remember { mutableFloatStateOf(initialFontSize) }
    var showColorPicker by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Text",
                    style = MaterialTheme.typography.titleMedium
                )
                Row {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                    IconButton(
                        onClick = { 
                            if (text.isNotBlank()) {
                                onConfirm(text, selectedColor, fontSize)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.Check, 
                            contentDescription = "Confirm",
                            tint = if (text.isNotBlank()) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Text input
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = selectedColor,
                    fontSize = fontSize.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (text.isNotBlank()) {
                            focusManager.clearFocus()
                            onConfirm(text, selectedColor, fontSize)
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                text = "Enter text...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = fontSize.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Options row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InkColors.all.take(5).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                                .then(
                                    if (color == selectedColor) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(4.dp)
                                        )
                                    } else Modifier
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }
                
                // Font size
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Size:", style = MaterialTheme.typography.bodySmall)
                    listOf(12f, 16f, 20f, 24f).forEach { size ->
                        FilterChip(
                            selected = fontSize == size,
                            onClick = { fontSize = size },
                            label = { Text("${size.toInt()}") },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Overlay component for displaying text annotations on a PDF page.
 * Note: x and y are expected to be in dp values already.
 */
@Composable
fun TextAnnotationOverlay(
    annotations: List<TextAnnotation>,
    onAnnotationClick: (TextAnnotation) -> Unit = {},
    onAnnotationMove: (String, Float, Float) -> Unit = { _, _, _ -> }
) {
    Box(modifier = Modifier.fillMaxSize()) {
        annotations.forEach { annotation ->
            Box(
                modifier = Modifier
                    .absoluteOffset(x = annotation.x.dp, y = annotation.y.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (annotation.backgroundColor != Color.Transparent) {
                            Modifier.background(annotation.backgroundColor)
                        } else Modifier
                    )
                    .clickable { onAnnotationClick(annotation) }
                    .padding(4.dp)
            ) {
                Text(
                    text = annotation.text,
                    color = annotation.color,
                    fontSize = annotation.fontSize.sp
                )
            }
        }
    }
}
