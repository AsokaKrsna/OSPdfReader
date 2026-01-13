package com.ospdf.reader.ui.browser

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ospdf.reader.ui.theme.Primary
import com.ospdf.reader.ui.theme.Secondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * File browser screen for selecting PDF files.
 * Features a modern, minimal design with gradient accents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onFileSelected: (Uri) -> Unit,
    onSettingsClick: () -> Unit,
    onOpenFromDriveClick: () -> Unit = {},
    viewModel: FileBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Check if we have All Files Access permission
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Not needed on older Android versions
        }
    }
    
    var showPermissionDialog by remember { mutableStateOf(!hasAllFilesAccess()) }
    
    // Permission dialog for All Files Access
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = {
                Icon(
                    Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("File Access Required") },
            text = { 
                Text(
                    "OSPdfReader needs access to manage all files to read and save PDFs. " +
                    "Please grant 'All files access' permission in the next screen."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback for devices that don't support this intent
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                context.startActivity(intent)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Grant Access")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Later")
                }
            }
        )
    }
    
    // Dialog for removing recent file
    var fileToRemove by remember { mutableStateOf<com.ospdf.reader.domain.model.PdfDocument?>(null) }
    
    if (fileToRemove != null) {
        AlertDialog(
            onDismissRequest = { fileToRemove = null },
            title = { Text("Remove from Recents") },
            text = { Text("Remove '${fileToRemove?.name}' from recent files list?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        fileToRemove?.let { viewModel.removeRecentFile(it) }
                        fileToRemove = null
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            // Try to get the actual file path for direct access
            val filePath = getFilePathFromUri(context, selectedUri)
            
            val finalUri = if (hasAllFilesAccess() && filePath != null && java.io.File(filePath).exists()) {
                // We have All Files Access and found the real file path - use file:// URI
                Uri.fromFile(java.io.File(filePath))
            } else {
                // Fall back to copying to cache (needed for SAF-only access)
                copyFileToCache(context, selectedUri) ?: selectedUri
            }

            // Best-effort persist permission for reading/writing later
            try {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // If we can't persist write permission, at least try read-only
                try {
                    context.contentResolver.takePersistableUriPermission(
                        selectedUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
            }

            // Pass the file URI to navigation
            onFileSelected(finalUri)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "OSPdfReader",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Open from Drive button - only visible when signed in
                    if (uiState.isSignedInToDrive) {
                        IconButton(onClick = onOpenFromDriveClick) {
                            Icon(
                                Icons.Filled.Cloud,
                                contentDescription = "Open from Drive"
                            )
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                icon = {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null
                    )
                },
                text = { Text("Open PDF") },
                containerColor = Primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Hero section with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Primary.copy(alpha = 0.1f),
                                Secondary.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column {
                    Text(
                        text = "Welcome back!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your documents are ready",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Recent files section
            if (uiState.recentFiles.isNotEmpty()) {
                Text(
                    text = "Recent Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.recentFiles) { file ->
                        RecentFileCard(
                            fileName = file.name,
                            lastOpened = file.lastOpened,
                            pageCount = file.pageCount,
                            onClick = { onFileSelected(file.uri) },
                            onLongClick = { fileToRemove = file }
                        )
                    }
                }
            } else {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No recent files",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap + to open a PDF",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentFileCard(
    fileName: String,
    lastOpened: Long,
    pageCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PDF icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // File info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = formatDate(lastOpened),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (pageCount > 0) {
                        Text(
                            text = " • $pageCount pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            // Arrow
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Gets the real file path from a content:// URI.
 * Works for Downloads, Documents, and other common locations.
 */
@Suppress("DEPRECATION")
private fun getFilePathFromUri(context: android.content.Context, uri: Uri): String? {
    android.util.Log.d("FileBrowser", "getFilePathFromUri: uri=$uri, authority=${uri.authority}")
    
    // For file:// URIs, just return the path
    if (uri.scheme == "file") {
        return uri.path
    }
    
    // For content:// URIs from DocumentsProvider
    if (uri.authority == "com.android.providers.media.documents") {
        // MediaDocumentsProvider - format: document:ID or image:ID etc
        val docId = android.provider.DocumentsContract.getDocumentId(uri)
        android.util.Log.d("FileBrowser", "MediaDocumentsProvider docId: $docId")
        
        val split = docId.split(":")
        val type = split.getOrNull(0)
        val id = split.getOrNull(1)
        
        if (id != null) {
            // Query MediaStore for the actual file path
            val contentUri = when (type) {
                "image" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "video" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "audio" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                "document" -> android.provider.MediaStore.Files.getContentUri("external")
                else -> android.provider.MediaStore.Files.getContentUri("external")
            }
            
            val selection = "_id=?"
            val selectionArgs = arrayOf(id)
            val path = getDataColumn(context, contentUri, selection, selectionArgs)
            android.util.Log.d("FileBrowser", "MediaStore query result: $path")
            if (path != null) return path
        }
    }
    
    if (uri.authority == "com.android.providers.downloads.documents") {
        // DownloadsProvider
        val docId = android.provider.DocumentsContract.getDocumentId(uri)
        android.util.Log.d("FileBrowser", "DownloadsProvider docId: $docId")
        
        if (docId.startsWith("raw:")) {
            return docId.removePrefix("raw:")
        }
        
        // For msf: format (Media Store File)
        if (docId.startsWith("msf:")) {
            val id = docId.removePrefix("msf:")
            val contentUri = android.provider.MediaStore.Files.getContentUri("external")
            val selection = "_id=?"
            val selectionArgs = arrayOf(id)
            val path = getDataColumn(context, contentUri, selection, selectionArgs)
            if (path != null) return path
        }
        
        // Try numeric ID
        try {
            val id = docId.toLongOrNull()
            if (id != null) {
                val contentUri = android.content.ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), id
                )
                val path = getDataColumn(context, contentUri, null, null)
                if (path != null) return path
            }
        } catch (_: Exception) {}
        
        // Try to find in Downloads folder by filename
        val fileName = getFileName(context, uri)
        if (fileName != null) {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, fileName)
            if (file.exists()) {
                android.util.Log.d("FileBrowser", "Found file in Downloads: ${file.absolutePath}")
                return file.absolutePath
            }
        }
    }
    
    if (uri.authority == "com.android.externalstorage.documents") {
        // ExternalStorageProvider
        val docId = android.provider.DocumentsContract.getDocumentId(uri)
        val split = docId.split(":")
        val type = split.getOrNull(0)
        val relativePath = split.getOrNull(1) ?: ""
        
        if ("primary".equals(type, ignoreCase = true)) {
            return "${Environment.getExternalStorageDirectory().absolutePath}/${relativePath}"
        }
    }
    
    // Try to get _data column directly (works for some content URIs)
    val path = getDataColumn(context, uri, null, null)
    android.util.Log.d("FileBrowser", "Direct _data query result: $path")
    if (path != null) return path
    
    // Last resort: try to find file by display name
    val fileName = getFileName(context, uri)
    if (fileName != null) {
        // Check common locations
        val locations = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStorageDirectory()
        )
        for (location in locations) {
            val file = java.io.File(location, fileName)
            if (file.exists()) {
                android.util.Log.d("FileBrowser", "Found file by name in ${location.name}: ${file.absolutePath}")
                return file.absolutePath
            }
        }
    }
    
    android.util.Log.d("FileBrowser", "Failed to get file path for URI")
    return null
}

/**
 * Gets the display name of a file from a URI.
 */
private fun getFileName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } else null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Gets the _data column value from a content URI.
 */
private fun getDataColumn(
    context: android.content.Context,
    uri: Uri,
    selection: String?,
    selectionArgs: Array<String>?
): String? {
    val column = "_data"
    val projection = arrayOf(column)
    
    return try {
        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(column)
                cursor.getString(columnIndex)
            } else null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Copies a content:// URI file to the app's cache directory.
 * Returns the file:// URI of the cached copy, or null if copy failed.
 */
private fun copyFileToCache(context: android.content.Context, sourceUri: Uri): Uri? {
    return try {
        // Get file name from the source URI
        var fileName = "document_${System.currentTimeMillis()}.pdf"
        context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }
        }
        
        // Create cache directory
        val cacheDir = java.io.File(context.cacheDir, "pdf_cache")
        cacheDir.mkdirs()
        
        // Create cache file
        val cacheFile = java.io.File(cacheDir, fileName)
        
        // Copy content
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            java.io.FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }
        
        // Return file URI
        if (cacheFile.exists() && cacheFile.length() > 0) {
            Uri.fromFile(cacheFile)
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

