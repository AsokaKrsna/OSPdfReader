package com.ospdf.reader.ui.tabs

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Represents an open document tab.
 */
data class DocumentTab(
    val id: String,
    val documentPath: String,
    val documentName: String,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val isModified: Boolean = false
)

/**
 * Tab bar for displaying open documents.
 * Supports multiple open PDFs with quick switching.
 */
@Composable
fun TabBar(
    tabs: List<DocumentTab>,
    activeTabId: String,
    onTabClick: (DocumentTab) -> Unit,
    onTabClose: (DocumentTab) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                TabItem(
                    tab = tab,
                    isActive = tab.id == activeTabId,
                    onClick = { onTabClick(tab) },
                    onClose = { onTabClose(tab) }
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            
            // New tab button
            IconButton(
                onClick = onNewTab,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "New tab",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: DocumentTab,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .clickable(onClick = onClick),
        color = if (isActive) 
            MaterialTheme.colorScheme.surface 
        else 
            Color.Transparent,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Modified indicator
            if (tab.isModified) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            
            // Tab title
            Text(
                text = tab.documentName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isActive) 
                    MaterialTheme.colorScheme.onSurface 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 120.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Close button
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close tab",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Manages multiple document tabs.
 */
class TabManager {
    private val _tabs = mutableStateListOf<DocumentTab>()
    val tabs: List<DocumentTab> get() = _tabs
    
    private var _activeTabId = mutableStateOf<String?>(null)
    val activeTabId: String? get() = _activeTabId.value
    
    val activeTab: DocumentTab?
        get() = _tabs.find { it.id == _activeTabId.value }
    
    fun openTab(documentPath: String, documentName: String, totalPages: Int): DocumentTab {
        // Check if already open
        val existing = _tabs.find { it.documentPath == documentPath }
        if (existing != null) {
            _activeTabId.value = existing.id
            return existing
        }
        
        // Create new tab
        val tab = DocumentTab(
            id = java.util.UUID.randomUUID().toString(),
            documentPath = documentPath,
            documentName = documentName,
            totalPages = totalPages
        )
        _tabs.add(tab)
        _activeTabId.value = tab.id
        return tab
    }
    
    fun closeTab(tabId: String) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            _tabs.removeAt(index)
            
            // If we closed the active tab, switch to another
            if (_activeTabId.value == tabId) {
                _activeTabId.value = when {
                    _tabs.isEmpty() -> null
                    index > 0 -> _tabs[index - 1].id
                    else -> _tabs.firstOrNull()?.id
                }
            }
        }
    }
    
    fun switchToTab(tabId: String) {
        if (_tabs.any { it.id == tabId }) {
            _activeTabId.value = tabId
        }
    }
    
    fun updateTabPage(tabId: String, currentPage: Int) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            _tabs[index] = _tabs[index].copy(currentPage = currentPage)
        }
    }
    
    fun setTabModified(tabId: String, isModified: Boolean) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            _tabs[index] = _tabs[index].copy(isModified = isModified)
        }
    }
}
