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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ospdf.reader.domain.model.AnnotationTool
import com.ospdf.reader.domain.model.ReadingMode
import com.ospdf.reader.ui.components.FloatingAnnotationToolbar
import com.ospdf.reader.ui.components.InkingCanvas
import com.ospdf.reader.ui.components.SearchBar
import com.ospdf.reader.ui.tools.LassoSelectionCanvas
import com.ospdf.reader.ui.tools.ShapeCanvas
import com.ospdf.reader.data.pdf.SearchResult
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
    
    // Sync pager with viewmodel (bidirectional)
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }
    
    // Sync pager when viewmodel currentPage changes (from search navigation)
    LaunchedEffect(uiState.currentPage) {
        if (pagerState.currentPage != uiState.currentPage) {
            pagerState.animateScrollToPage(uiState.currentPage)
        }
    }
    
    // Show/hide UI controls
    var showControls by remember { mutableStateOf(true) }
    
    // Disable page scrolling when any annotation tool is active
    val isAnnotationMode = uiState.toolState.currentTool != AnnotationTool.NONE
    
    // Zoom state - shared across pages
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    
    // Annotations are disabled when zoomed in
    val isZoomed = zoomLevel > 1.05f
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Search bar (shown when search is active)
            AnimatedVisibility(
                visible = uiState.showSearch,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.performSearch(it) },
                    onSearch = { viewModel.performSearch(it) },
                    onPrevious = { viewModel.goToPreviousResult() },
                    onNext = { viewModel.goToNextResult() },
                    onClose = { viewModel.toggleSearch() },
                    resultCount = uiState.searchResults.size,
                    currentIndex = uiState.currentSearchIndex,
                    isSearching = uiState.isSearching
                )
            }
            
            // Normal top bar (hidden during search)
            AnimatedVisibility(
                visible = showControls && !uiState.showAnnotationToolbar && !uiState.showSearch,
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
                    onFlattenAnnotations = { viewModel.flattenAnnotationsToPdf() },
                    onExportToDownloads = { viewModel.exportToDownloads() }
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
                                // Allow finger scrolling - stylus only for annotations
                                userScrollEnabled = zoomLevel <= 1.05f // Disable paging when zoomed
                            ) { pageIndex ->
                                PdfPageWithAnnotations(
                                    pageIndex = pageIndex,
                                    viewModel = viewModel,
                                    toolState = uiState.toolState,
                                    zoomLevel = zoomLevel,
                                    panOffset = panOffset,
                                    onZoomChange = { newZoom, newOffset ->
                                        zoomLevel = newZoom.coerceIn(1f, 5f)
                                        panOffset = newOffset
                                    },
                                    isZoomed = isZoomed,
                                    searchResults = uiState.searchResults.filter { it.pageNumber == pageIndex },
                                    currentSearchIndex = uiState.currentSearchIndex,
                                    allSearchResults = uiState.searchResults,
                                    onTap = { showControls = !showControls }
                                )
                            }
                        }
                        ReadingMode.VERTICAL_SCROLL -> {
                            VerticalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                // Allow finger scrolling - stylus only for annotations
                                userScrollEnabled = zoomLevel <= 1.05f
                            ) { pageIndex ->
                                PdfPageWithAnnotations(
                                    pageIndex = pageIndex,
                                    viewModel = viewModel,
                                    toolState = uiState.toolState,
                                    zoomLevel = zoomLevel,
                                    panOffset = panOffset,
                                    onZoomChange = { newZoom, newOffset ->
                                        zoomLevel = newZoom.coerceIn(1f, 5f)
                                        panOffset = newOffset
                                    },
                                    isZoomed = isZoomed,
                                    searchResults = uiState.searchResults.filter { it.pageNumber == pageIndex },
                                    currentSearchIndex = uiState.currentSearchIndex,
                                    allSearchResults = uiState.searchResults,
                                    onTap = { showControls = !showControls }
                                )
                            }
                        }
                    }
                    
                    // Page indicator overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
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
                    
                    // Floating Annotation toolbar
                    if (uiState.showAnnotationToolbar) {
                        FloatingAnnotationToolbar(
                            toolState = uiState.toolState,
                            canUndo = uiState.canUndo,
                            canRedo = uiState.canRedo,
                            isZoomed = isZoomed,
                            onToolSelected = { viewModel.setTool(it) },
                            onColorSelected = { viewModel.setColor(it) },
                            onStrokeWidthChanged = { viewModel.setStrokeWidth(it) },
                            onShapeTypeSelected = { viewModel.setShapeType(it) },
                            onUndo = { viewModel.undo() },
                            onRedo = { viewModel.redo() },
                            onClose = { viewModel.closeAnnotationMode() },
                            onResetZoom = {
                                zoomLevel = 1f
                                panOffset = Offset.Zero
                            }
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
    zoomLevel: Float,
    panOffset: Offset,
    onZoomChange: (Float, Offset) -> Unit,
    isZoomed: Boolean,
    searchResults: List<SearchResult> = emptyList(),
    currentSearchIndex: Int = -1,
    allSearchResults: List<SearchResult> = emptyList(),
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
    
    // Effective tool - disable annotations when zoomed
    val effectiveTool = if (isZoomed) AnnotationTool.NONE else toolState.currentTool
    
    // Transformable state for smoother pinch-to-zoom
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newZoom = (zoomLevel * zoomChange).coerceIn(1f, 5f)
        val maxPanX = (containerSize.width * (newZoom - 1)) / 2
        val maxPanY = (containerSize.height * (newZoom - 1)) / 2
        val newPan = Offset(
            (panOffset.x + offsetChange.x).coerceIn(-maxPanX, maxPanX),
            (panOffset.y + offsetChange.y).coerceIn(-maxPanY, maxPanY)
        )
        onZoomChange(newZoom, newPan)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .graphicsLayer {
                scaleX = zoomLevel
                scaleY = zoomLevel
                translationX = panOffset.x
                translationY = panOffset.y
            }
            .transformable(state = transformableState)
            .then(
                // Tap gestures when not in annotation mode and not zoomed
                if (effectiveTool == AnnotationTool.NONE && !isZoomed) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { onTap() })
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
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
            
            // Draw search result highlights
            if (searchResults.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    searchResults.forEachIndexed { localIndex, result ->
                        val quad = result.bounds
                        // Convert from PDF coordinates to screen coordinates
                        // MuPDF Quad has ul_x, ul_y, ur_x, ur_y, ll_x, ll_y, lr_x, lr_y
                        val left = quad.ul_x * renderScale * fitScale + imageLeft
                        val top = quad.ul_y * renderScale * fitScale + imageTop
                        val right = quad.lr_x * renderScale * fitScale + imageLeft
                        val bottom = quad.lr_y * renderScale * fitScale + imageTop
                        
                        // Find if this is the current result globally
                        val globalIndex = allSearchResults.indexOfFirst { 
                            it.pageNumber == result.pageNumber && 
                            it.bounds.ul_x == result.bounds.ul_x &&
                            it.bounds.ul_y == result.bounds.ul_y
                        }
                        val isCurrentResult = globalIndex == currentSearchIndex
                        
                        // Draw yellow highlight for all results
                        drawRect(
                            color = if (isCurrentResult) 
                                Color(0xFFFFA000).copy(alpha = 0.5f) // Orange for current
                            else 
                                Color.Yellow.copy(alpha = 0.35f), // Yellow for others
                            topLeft = androidx.compose.ui.geometry.Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                        )
                        
                        // Draw border for current result
                        if (isCurrentResult) {
                            drawRect(
                                color = Color(0xFFE65100),
                                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )
                        }
                    }
                }
            }
            
            // Always render existing shapes (disabled canvas just draws, no input)
            ShapeCanvas(
                modifier = Modifier.fillMaxSize(),
                shapes = screenShapes,
                toolState = toolState,
                enabled = false
            )
            
            // Always render existing strokes (when not in ink mode, use disabled canvas)
            if (effectiveTool != AnnotationTool.PEN && 
                effectiveTool != AnnotationTool.PEN_2 && 
                effectiveTool != AnnotationTool.HIGHLIGHTER &&
                effectiveTool != AnnotationTool.ERASER) {
                InkingCanvas(
                    modifier = Modifier.fillMaxSize(),
                    strokes = screenStrokes,
                    toolState = toolState,
                    enabled = false
                )
            }
            
            // Tool-specific overlays (disabled when zoomed)
            when (effectiveTool) {
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
                            android.util.Log.d("ReaderScreen", "Coordinate transform: fitScale=$fitScale, renderScale=$renderScale, scale=$scale")
                            android.util.Log.d("ReaderScreen", "Image offset: left=$imageLeft, top=$imageTop")
                            android.util.Log.d("ReaderScreen", "Bitmap size: ${bitmap?.width}x${bitmap?.height}")
                            
                            // Log original screen coords
                            if (stroke.points.isNotEmpty()) {
                                val firstPt = stroke.points.first()
                                val lastPt = stroke.points.last()
                                android.util.Log.d("ReaderScreen", "Screen coords: first=(${firstPt.x}, ${firstPt.y}), last=(${lastPt.x}, ${lastPt.y})")
                            }
                            
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
                            
                            // Log mapped PDF coords
                            if (mapped.points.isNotEmpty()) {
                                val firstPt = mapped.points.first()
                                val lastPt = mapped.points.last()
                                android.util.Log.d("ReaderScreen", "PDF coords: first=(${firstPt.x}, ${firstPt.y}), last=(${lastPt.x}, ${lastPt.y})")
                            }
                            
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
    onFlattenAnnotations: () -> Unit,
    onExportToDownloads: () -> Unit
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
                    DropdownMenuItem(
                        text = { Text("Export to Downloads") },
                        onClick = onExportToDownloads,
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Share,
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
