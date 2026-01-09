package com.ospdf.reader.ui.notes

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ospdf.reader.data.local.MiniNoteEntity
import kotlin.math.roundToInt

/**
 * A draggable mini note component that can be placed anywhere on a PDF page.
 * Supports expand/collapse, editing, and custom colors.
 */
@Composable
fun MiniNote(
    note: MiniNoteEntity,
    onUpdate: (MiniNoteEntity) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(note.isExpanded) }
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(note.content) }
    var offsetX by remember { mutableFloatStateOf(note.xPosition) }
    var offsetY by remember { mutableFloatStateOf(note.yPosition) }
    
    val noteColor = Color(note.color)
    
    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    
                    // Update position when dragging ends
                    onUpdate(note.copy(xPosition = offsetX, yPosition = offsetY))
                }
            }
    ) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
            },
            label = "note_expand"
        ) { expanded ->
            if (expanded) {
                // Expanded note card
                ExpandedNoteCard(
                    content = if (isEditing) editText else note.content,
                    color = noteColor,
                    isEditing = isEditing,
                    onContentChange = { editText = it },
                    onCollapse = {
                        if (isEditing) {
                            onUpdate(note.copy(content = editText, isExpanded = false))
                            isEditing = false
                        }
                        isExpanded = false
                    },
                    onEdit = { isEditing = true },
                    onDelete = onDelete
                )
            } else {
                // Collapsed note icon
                CollapsedNoteIcon(
                    color = noteColor,
                    hasContent = note.content.isNotEmpty(),
                    onClick = { isExpanded = true }
                )
            }
        }
    }
}

@Composable
private fun CollapsedNoteIcon(
    color: Color,
    hasContent: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (hasContent) Icons.Filled.StickyNote2 else Icons.Filled.Add,
            contentDescription = "Expand note",
            modifier = Modifier.size(18.dp),
            tint = Color.Black.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ExpandedNoteCard(
    content: String,
    color: Color,
    isEditing: Boolean,
    onContentChange: (String) -> Unit,
    onCollapse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .shadow(8.dp, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header with actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!isEditing) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Black.copy(alpha = 0.6f)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Black.copy(alpha = 0.6f)
                    )
                }
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Black.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Content area
            if (isEditing) {
                BasicTextField(
                    value = content,
                    onValueChange = onContentChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 150.dp)
                        .padding(4.dp),
                    textStyle = TextStyle(
                        color = Color.Black.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(Color.Black),
                    decorationBox = { innerTextField ->
                        Box {
                            if (content.isEmpty()) {
                                Text(
                                    "Write a note...",
                                    color = Color.Black.copy(alpha = 0.4f),
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            } else {
                Text(
                    text = content.ifEmpty { "Tap edit to add content" },
                    color = Color.Black.copy(alpha = if (content.isEmpty()) 0.4f else 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                )
            }
        }
    }
}

/**
 * Floating button to add a new mini note.
 */
@Composable
fun AddMiniNoteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = Color(0xFFFFEB3B),
        contentColor = Color.Black
    ) {
        Icon(Icons.Filled.NoteAdd, contentDescription = "Add note")
    }
}
