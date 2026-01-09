package com.ospdf.reader.ui.reader

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ospdf.reader.domain.model.AnnotationTool
import com.ospdf.reader.domain.model.ReadingMode
import com.ospdf.reader.ui.components.AnnotationToolbar
import com.ospdf.reader.ui.components.InkingCanvas
import com.ospdf.reader.ui.tools.LassoSelectionCanvas
import com.ospdf.reader.ui.tools.ShapeCanvas
import kotlinx.coroutines.launch

/**
 * Main PDF reader screen with page viewing, zoom/pan, and annotation support.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    fileUri: Uri,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSelection by viewModel.currentSelection.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Initialize document on first composition
    LaunchedEffect(fileUri) {
        viewModel.loadDocument(fileUri)
    }
    
    // Show success message in snackbar
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSuccessMessage()
        }
    }
    
    // Pager state for page navigation
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { uiState.pageCount }
    )
    
    // Sync pager with viewmodel
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }
    
    // Show/hide UI controls
    var showControls by remember { mutableStateOf(true) }
    
    // Disable page scrolling when any annotation tool is active
    val isAnnotationMode = uiState.toolState.currentTool != AnnotationTool.NONE
    
    // Zoom temporarily disabled to simplify annotation alignment
    val zoomLevel = 1f
    val zoomOffset = Offset.Zero
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedVisibility(
                visible = showControls && !uiState.showAnnotationToolbar,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                ReaderTopBar(
                    title = uiState.documentName,
                    hasUnsavedChanges = uiState.hasUnsavedChanges,
                    showMoreMenu = uiState.showMoreMenu,
                    onBack = onBack,
                    onSearch = { viewModel.toggleSearch() },
                    onBookmarks = { viewModel.toggleBookmarks() },
                    onAnnotate = { viewModel.openAnnotationMode() },
                    onSave = { viewModel.saveAnnotations() },
                    onMore = { viewModel.toggleMoreMenu() },
                    onDismissMenu = { viewModel.dismissMoreMenu() },
                    onFlattenAnnotations = { viewModel.flattenAnnotationsToPdf() }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showControls || uiState.showAnnotationToolbar, // Show during annotation mode to allow zoom
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                ReaderBottomBar(
                    currentPage = uiState.currentPage + 1,
                    pageCount = uiState.pageCount,
                    readingMode = uiState.readingMode,
                    onPageChange = { page ->
                        scope.launch {
                            pagerState.animateScrollToPage(page - 1)
                        }
                    },
                    onToggleMode = { viewModel.toggleReadingMode() }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF2A2A2A))
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                uiState.error != null -> {
                    ErrorMessage(
                        message = uiState.error!!,
                        onRetry = { viewModel.loadDocument(fileUri) }
                    )
                }
                else -> {
                    // PDF Page viewer
                    when (uiState.readingMode) {
                        ReadingMode.HORIZONTAL_SWIPE -> {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                // Disable scrolling when annotation tool is active
                                userScrollEnabled = !isAnnotationMode
                            ) { pageIndex ->
                                PdfPageWithAnnotations(
                                    pageIndex = pageIndex,
                                    viewModel = viewModel,
                                    toolState = uiState.toolState,
                                    onTap = { showControls = !showControls }
                                )
                            }
                        }
                        ReadingMode.VERTICAL_SCROLL -> {
                            VerticalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                // Disable scrolling when annotation tool is active
                                userScrollEnabled = !isAnnotationMode
                            ) { pageIndex ->
                                PdfPageWithAnnotations(
                                    pageIndex = pageIndex,
                                    viewModel = viewModel,
                                    toolState = uiState.toolState,
                                    onTap = { showControls = !showControls }
                                )
                            }
                        }
                    }
                    
                    // Page indicator overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (uiState.showAnnotationToolbar) 80.dp else 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${uiState.currentPage + 1} / ${uiState.pageCount}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // Lasso selection actions
                    if (uiState.toolState.currentTool == AnnotationTool.LASSO && currentSelection?.hasSelection == true) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Selected", style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { viewModel.deleteSelection() }) { Text("Delete") }
                            TextButton(onClick = { viewModel.setLassoSelection(null) }) { Text("Clear") }
                        }
                    }
                    
                    // Annotation toolbar
                    AnimatedVisibility(
                        visible = uiState.showAnnotationToolbar,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        AnnotationToolbar(
                            modifier = Modifier.padding(bottom = 16.dp),
                            toolState = uiState.toolState,
                            canUndo = uiState.canUndo,
                            canRedo = uiState.canRedo,
                            onToolSelected = { viewModel.setTool(it) },
                            onColorSelected = { viewModel.setColor(it) },
                            onStrokeWidthChanged = { viewModel.setStrokeWidth(it) },
                            onShapeTypeSelected = { viewModel.setShapeType(it) },
                            onUndo = { viewModel.undo() },
                            onRedo = { viewModel.redo() },
                            onClose = { viewModel.closeAnnotationMode() }
                        )
                    }
                    
                    // Saving indicator
                    if (uiState.isSaving) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Text("Saving annotations...")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * PDF page view with integrated inking canvas for annotations.
 */
@Composable
private fun PdfPageWithAnnotations(
    pageIndex: Int,
    viewModel: ReaderViewModel,
    toolState: com.ospdf.reader.domain.model.ToolState,
    onTap: () -> Unit
) {
    val bitmap by viewModel.getPageBitmap(pageIndex).collectAsState(initial = null)
    val pageStrokes by viewModel.getPageStrokes(pageIndex).collectAsState()
    val pageShapes by viewModel.getPageShapes(pageIndex).collectAsState()
    val currentSelection by viewModel.currentSelection.collectAsState()
    
    // Text lines for smart highlighter
    var textLines by remember { mutableStateOf<List<com.ospdf.reader.data.pdf.TextLine>>(emptyList()) }
    
    // Fetch text lines when page loads (for smart highlighter)
    LaunchedEffect(pageIndex) {
        textLines = viewModel.getTextLines(pageIndex)
    }
    
    // Container size for pan limits
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    // Calculate image layout for correct text snapping
    val imageLayout by remember(bitmap, containerSize) {
        derivedStateOf {
            val bmp = bitmap
            if (bmp != null && containerSize.width > 0 && containerSize.height > 0) {
                val imageWidth = bmp.width.toFloat()
                val imageHeight = bmp.height.toFloat()
                val scale = minOf(
                    containerSize.width.toFloat() / imageWidth,
                    containerSize.height.toFloat() / imageHeight
                )
                val scaledWidth = imageWidth * scale
                val scaledHeight = imageHeight * scale
                val left = (containerSize.width.toFloat() - scaledWidth) / 2
                val top = (containerSize.height.toFloat() - scaledHeight) / 2
                Triple(scale, left, top)
            } else {
                Triple(1f, 0f, 0f)
            }
        }
    }
    val (fitScale, imageLeft, imageTop) = imageLayout

    // Render scale used when generating the bitmap; needed to map between PDF/page space and screen space.
    val renderScale = viewModel.renderScale

    val density = LocalDensity.current

    // Scale text lines to match displayed image coordinates
    val scaledTextLines = remember(textLines, fitScale, imageLeft, imageTop) {
        textLines.map { line ->
            line.copy(
                x = line.x * fitScale + imageLeft,
                y = line.y * fitScale + imageTop,
                width = line.width * fitScale,
                height = line.height * fitScale
            )
        }
    }
    
    // Convert page-space strokes to screen-space for rendering on the current layout.
    val screenStrokes = remember(pageStrokes, fitScale, imageLeft, imageTop, renderScale) {
        pageStrokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { pt ->
                    com.ospdf.reader.domain.model.StrokePoint(
                        x = pt.x * renderScale * fitScale + imageLeft,
                        y = pt.y * renderScale * fitScale + imageTop,
                        pressure = pt.pressure,
                        timestamp = pt.timestamp
                    )
                },
                strokeWidth = stroke.strokeWidth * renderScale * fitScale
            )
        }
    }

    val screenShapes = remember(pageShapes, fitScale, imageLeft, imageTop, renderScale) {
        pageShapes.map { shape ->
            shape.copy(
                startX = shape.startX * renderScale * fitScale + imageLeft,
                startY = shape.startY * renderScale * fitScale + imageTop,
                endX = shape.endX * renderScale * fitScale + imageLeft,
                endY = shape.endY * renderScale * fitScale + imageTop,
                strokeWidth = shape.strokeWidth * renderScale * fitScale
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .then(
                // Tap gestures when not in annotation mode
                if (toolState.currentTool == AnnotationTool.NONE) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onTap() }
                            )
                        }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // PDF page bitmap
            bitmap?.let { bmp ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val imageBitmap = bmp.asImageBitmap()
                    
                    val imageWidth = imageBitmap.width.toFloat()
                    val imageHeight = imageBitmap.height.toFloat()
                    val scaledWidth = imageWidth * fitScale
                    val scaledHeight = imageHeight * fitScale
                    
                    drawImage(
                        image = imageBitmap,
                        dstOffset = androidx.compose.ui.unit.IntOffset(imageLeft.toInt(), imageTop.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(
                            scaledWidth.toInt(),
                            scaledHeight.toInt()
                        )
                    )
                }
            } ?: run {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            
            // Always render existing shapes (disabled canvas just draws, no input)
            ShapeCanvas(
                modifier = Modifier.fillMaxSize(),
                shapes = screenShapes,
                toolState = toolState,
                enabled = false
            )
            
            // Always render existing strokes (when not in ink mode, use disabled canvas)
            if (toolState.currentTool != AnnotationTool.PEN && 
                toolState.currentTool != AnnotationTool.PEN_2 && 
                toolState.currentTool != AnnotationTool.HIGHLIGHTER &&
                toolState.currentTool != AnnotationTool.ERASER) {
                InkingCanvas(
                    modifier = Modifier.fillMaxSize(),
                    strokes = screenStrokes,
                    toolState = toolState,
                    enabled = false
                )
            }
            
            // Tool-specific overlays
            when (toolState.currentTool) {
                AnnotationTool.PEN, AnnotationTool.PEN_2, AnnotationTool.HIGHLIGHTER, AnnotationTool.HIGHLIGHTER_2, AnnotationTool.ERASER -> {
                    InkingCanvas(
                        modifier = Modifier.fillMaxSize(),
                        strokes = screenStrokes,
                        toolState = toolState,
                        textLines = scaledTextLines,
                        enabled = true,
                        onStrokeStart = { /* Optional: haptic feedback */ },
                        onStrokeEnd = { stroke ->
                            // Convert the drawn stroke from screen space to PDF page space so saved annotations align.
                            val scale = fitScale * renderScale
                            val mapped = stroke.copy(
                                points = stroke.points.map { pt ->
                                    com.ospdf.reader.domain.model.StrokePoint(
                                        x = (pt.x - imageLeft) / scale,
                                        y = (pt.y - imageTop) / scale,
                                        pressure = pt.pressure,
                                        timestamp = pt.timestamp
                                    )
                                },
                                strokeWidth = stroke.strokeWidth / scale
                            )
                            viewModel.addStroke(mapped)
                        },
                        onStrokeErase = { strokeId -> viewModel.removeStroke(strokeId) },
                        onTap = onTap
                    )
                }
                AnnotationTool.SHAPE -> {
                    ShapeCanvas(
                        modifier = Modifier.fillMaxSize(),
                        shapes = emptyList(), // Shapes already rendered above; this handles input only
                        toolState = toolState,
                        enabled = true,
                        onShapeComplete = { shape ->
                            val scale = fitScale * renderScale
                            val mapped = shape.copy(
                                startX = (shape.startX - imageLeft) / scale,
                                startY = (shape.startY - imageTop) / scale,
                                endX = (shape.endX - imageLeft) / scale,
                                endY = (shape.endY - imageTop) / scale,
                                strokeWidth = shape.strokeWidth / scale,
                                pageNumber = pageIndex
                            )
                            viewModel.addShape(mapped)
                        }
                    )
                }
                AnnotationTool.LASSO -> {
                    LassoSelectionCanvas(
                        modifier = Modifier.fillMaxSize(),
                        strokes = screenStrokes,
                        shapes = screenShapes,
                        enabled = true,
                        currentSelection = currentSelection,
                        onSelectionComplete = { selection -> viewModel.setLassoSelection(selection) },
                        onSelectionMove = { screenDelta ->
                            // Convert screen delta to page-space delta
                            val scale = fitScale * renderScale
                            val pageDeltaX = screenDelta.x / scale
                            val pageDeltaY = screenDelta.y / scale
                            viewModel.moveSelection(pageDeltaX, pageDeltaY, screenDelta.x, screenDelta.y)
                        },
                        onSelectionClear = { viewModel.setLassoSelection(null) }
                    )
                }
                AnnotationTool.TEXT, AnnotationTool.NONE -> { /* navigation mode or unsupported tool, no overlay */ }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    hasUnsavedChanges: Boolean,
    showMoreMenu: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onBookmarks: () -> Unit,
    onAnnotate: () -> Unit,
    onSave: () -> Unit,
    onMore: () -> Unit,
    onDismissMenu: () -> Unit,
    onFlattenAnnotations: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (hasUnsavedChanges) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, contentDescription = "Search")
            }
            IconButton(onClick = onAnnotate) {
                Icon(Icons.Outlined.Edit, contentDescription = "Annotate")
            }
            if (hasUnsavedChanges) {
                IconButton(onClick = onSave) {
                    Icon(Icons.Outlined.Save, contentDescription = "Save")
                }
            }
            IconButton(onClick = onBookmarks) {
                Icon(Icons.Outlined.Bookmarks, contentDescription = "Bookmarks")
            }
            Box {
                IconButton(onClick = onMore) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = onDismissMenu
                ) {
                    DropdownMenuItem(
                        text = { Text("Make Annotations Permanent") },
                        onClick = onFlattenAnnotations,
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    )
}

@Composable
private fun ReaderBottomBar(
    currentPage: Int,
    pageCount: Int,
    readingMode: ReadingMode,
    onPageChange: (Int) -> Unit,
    onToggleMode: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Page navigation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onToggleMode) {
                    Icon(
                        imageVector = when (readingMode) {
                            ReadingMode.HORIZONTAL_SWIPE -> Icons.Filled.SwipeRight
                            ReadingMode.VERTICAL_SCROLL -> Icons.Filled.SwipeDown
                        },
                        contentDescription = "Toggle reading mode"
                    )
                }
                
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$currentPage",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = currentPage.toFloat(),
                        onValueChange = { onPageChange(it.toInt()) },
                        valueRange = 1f..pageCount.toFloat().coerceAtLeast(1f),
                        steps = maxOf(0, pageCount - 2),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        text = "$pageCount",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            
        }
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
