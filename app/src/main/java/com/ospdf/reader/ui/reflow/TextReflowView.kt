package com.ospdf.reader.ui.reflow

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Settings for text reflow display.
 */
data class ReflowSettings(
    val fontSize: Float = 16f,
    val lineSpacing: Float = 1.5f,
    val fontFamily: FontFamily = FontFamily.Default,
    val isDarkMode: Boolean = false,
    val marginHorizontal: Int = 16,
    val marginVertical: Int = 24
)

/**
 * Text reflow view that displays extracted text in a readable format.
 * Allows users to read PDFs as reflowable text, especially useful
 * on smaller screens or for accessibility.
 */
@Composable
fun TextReflowView(
    text: String,
    settings: ReflowSettings = ReflowSettings(),
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    val backgroundColor = if (settings.isDarkMode) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.background
    }
    
    val textColor = if (settings.isDarkMode) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(scrollState)
            .padding(
                horizontal = settings.marginHorizontal.dp,
                vertical = settings.marginVertical.dp
            )
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = settings.fontSize.sp,
                lineHeight = (settings.fontSize * settings.lineSpacing).sp,
                fontFamily = settings.fontFamily,
                color = textColor
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Reflow toolbar for adjusting text display settings.
 */
@Composable
fun ReflowSettingsBar(
    settings: ReflowSettings,
    onSettingsChange: (ReflowSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Font size decrease
        TextButton(onClick = {
            if (settings.fontSize > 12f) {
                onSettingsChange(settings.copy(fontSize = settings.fontSize - 2f))
            }
        }) {
            Text("A-", fontSize = 20.sp)
        }
        
        // Current size display
        Text(
            text = "${settings.fontSize.toInt()}sp",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        // Font size increase
        TextButton(onClick = {
            if (settings.fontSize < 32f) {
                onSettingsChange(settings.copy(fontSize = settings.fontSize + 2f))
            }
        }) {
            Text("A+", fontSize = 20.sp)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Line spacing toggle
        TextButton(onClick = {
            val newSpacing = when (settings.lineSpacing) {
                1.0f -> 1.5f
                1.5f -> 2.0f
                else -> 1.0f
            }
            onSettingsChange(settings.copy(lineSpacing = newSpacing))
        }) {
            Text("≡ ${settings.lineSpacing}x")
        }
    }
}

@Composable
private fun TextButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        content()
    }
}

/**
 * Processes raw PDF text for better reflow display.
 * Handles common formatting issues.
 */
object TextProcessor {
    
    /**
     * Cleans and formats extracted text for reflow display.
     */
    fun processForReflow(rawText: String): String {
        return rawText
            // Remove excessive whitespace
            .replace(Regex("\\s+"), " ")
            // Fix hyphenated line breaks
            .replace(Regex("-\\s*\\n\\s*"), "")
            // Convert single line breaks to spaces
            .replace(Regex("(?<!\\n)\\n(?!\\n)"), " ")
            // Keep paragraph breaks (double newlines)
            .replace(Regex("\\n{2,}"), "\n\n")
            // Clean up spaces around paragraphs
            .replace(Regex("\\n +"), "\n")
            .replace(Regex(" +\\n"), "\n")
            .trim()
    }
    
    /**
     * Splits text into paragraphs for better formatting.
     */
    fun splitIntoParagraphs(text: String): List<String> {
        return text.split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    
    /**
     * Estimates reading time in minutes.
     */
    fun estimateReadingTime(text: String, wordsPerMinute: Int = 250): Int {
        val wordCount = text.split(Regex("\\s+")).size
        return (wordCount / wordsPerMinute).coerceAtLeast(1)
    }
}
