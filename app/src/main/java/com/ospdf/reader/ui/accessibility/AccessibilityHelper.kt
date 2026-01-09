package com.ospdf.reader.ui.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accessibility state holder.
 */
data class AccessibilityState(
    val isScreenReaderEnabled: Boolean = false,
    val isTouchExplorationEnabled: Boolean = false,
    val isReducedMotionEnabled: Boolean = false,
    val isHighContrastEnabled: Boolean = false,
    val fontScale: Float = 1.0f
)

/**
 * Helper for accessibility features.
 */
@Singleton
class AccessibilityHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    
    /**
     * Gets the current accessibility state.
     */
    fun getAccessibilityState(): AccessibilityState {
        return AccessibilityState(
            isScreenReaderEnabled = accessibilityManager.isEnabled,
            isTouchExplorationEnabled = accessibilityManager.isTouchExplorationEnabled,
            fontScale = context.resources.configuration.fontScale
        )
    }
    
    /**
     * Checks if a screen reader is active.
     */
    fun isScreenReaderActive(): Boolean {
        return accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled
    }
    
    /**
     * Gets the current font scale from system settings.
     */
    fun getFontScale(): Float {
        return context.resources.configuration.fontScale
    }
}

/**
 * Composable to provide accessibility state.
 */
@Composable
fun rememberAccessibilityState(): AccessibilityState {
    val context = LocalContext.current
    val accessibilityManager = remember {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    
    var state by remember {
        mutableStateOf(
            AccessibilityState(
                isScreenReaderEnabled = accessibilityManager.isEnabled,
                isTouchExplorationEnabled = accessibilityManager.isTouchExplorationEnabled,
                fontScale = context.resources.configuration.fontScale
            )
        )
    }
    
    DisposableEffect(accessibilityManager) {
        val listener = AccessibilityManager.AccessibilityStateChangeListener { enabled ->
            state = state.copy(isScreenReaderEnabled = enabled)
        }
        accessibilityManager.addAccessibilityStateChangeListener(listener)
        
        onDispose {
            accessibilityManager.removeAccessibilityStateChangeListener(listener)
        }
    }
    
    return state
}

/**
 * Modifier extension for common accessibility patterns.
 */
fun Modifier.pdfPageSemantics(
    pageNumber: Int,
    totalPages: Int,
    contentDescription: String = ""
): Modifier = this.semantics {
    this.contentDescription = if (contentDescription.isNotEmpty()) {
        contentDescription
    } else {
        "Page ${pageNumber + 1} of $totalPages"
    }
    stateDescription = "Page ${pageNumber + 1}"
}

fun Modifier.toolButtonSemantics(
    toolName: String,
    isSelected: Boolean
): Modifier = this.semantics {
    contentDescription = "$toolName tool${if (isSelected) ", selected" else ""}"
    selected = isSelected
    role = Role.Button
}

fun Modifier.bookmarkSemantics(
    title: String,
    pageNumber: Int
): Modifier = this.semantics {
    contentDescription = "Bookmark: $title on page ${pageNumber + 1}"
    role = Role.Button
}

/**
 * Accessibility-friendly announcement.
 */
@Composable
fun AccessibilityAnnouncement(
    message: String,
    onAnnounced: () -> Unit = {}
) {
    val context = LocalContext.current
    
    LaunchedEffect(message) {
        if (message.isNotEmpty()) {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            if (accessibilityManager.isEnabled) {
                val event = android.view.accessibility.AccessibilityEvent.obtain().apply {
                    eventType = android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT
                    className = javaClass.name
                    packageName = context.packageName
                    text.add(message)
                }
                accessibilityManager.sendAccessibilityEvent(event)
            }
            onAnnounced()
        }
    }
}
