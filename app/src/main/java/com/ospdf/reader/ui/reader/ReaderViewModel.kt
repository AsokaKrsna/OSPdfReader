package com.ospdf.reader.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospdf.reader.data.pdf.AnnotationManager
import com.ospdf.reader.data.pdf.MuPdfRenderer
import com.ospdf.reader.data.pdf.SearchResult
import com.ospdf.reader.data.cloud.GoogleDriveSync
import com.ospdf.reader.data.preferences.PreferencesManager
import com.ospdf.reader.domain.model.AnnotationTool
import com.ospdf.reader.domain.model.ReadingMode
import com.ospdf.reader.domain.model.ShapeType
import com.ospdf.reader.domain.model.ToolState
import com.ospdf.reader.domain.usecase.AddStrokeAction
import com.ospdf.reader.domain.usecase.RemoveStrokeAction
import com.ospdf.reader.domain.usecase.UndoRedoManager
import com.ospdf.reader.ui.components.InkStroke
import com.ospdf.reader.ui.tools.ShapeAnnotation
import com.ospdf.reader.ui.tools.TextAnnotation
import com.ospdf.reader.ui.tools.LassoSelection
import com.ospdf.reader.ui.theme.InkColors
import com.ospdf.reader.data.local.RecentDocumentsRepository
import com.ospdf.reader.data.local.AnnotationRepository
import com.ospdf.reader.data.sync.SyncRepository
import com.ospdf.reader.util.BitmapCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * UI state for the reader screen.
 */
data class ReaderUiState(
    val documentName: String = "",
    val documentPath: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val readingMode: ReadingMode = ReadingMode.HORIZONTAL_SWIPE,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val toolState: ToolState = ToolState(),
    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val currentSearchIndex: Int = -1,
    val isSearching: Boolean = false,
    val showBookmarks: Boolean = false,
    val showMoreMenu: Boolean = false,
    val showAnnotationToolbar: Boolean = false,
    val zoomLevel: Float = 1f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isExiting: Boolean = false,
    // User preferences that affect the reader
    val keepScreenOn: Boolean = true,
    val reducedMotion: Boolean = false,
    val autoSaveAnnotations: Boolean = true
)

/**
 * ViewModel for the PDF reader screen with annotation support.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val pdfRenderer: MuPdfRenderer,
    private val annotationManager: AnnotationManager,
    private val recentDocumentsRepository: RecentDocumentsRepository,
    private val googleDriveSync: GoogleDriveSync,
    private val preferencesManager: PreferencesManager,
    private val annotationRepository: AnnotationRepository,
    private val syncRepository: SyncRepository,
    private val bitmapCache: BitmapCache
) : ViewModel() {

    // Render scale used for MuPDF bitmap generation (applied as DPI multiplier).
    val renderScale: Float = 2.5f
    
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    
    init {
        // Collect user preferences and update UI state
        viewModelScope.launch {
            preferencesManager.userPreferences.collect { prefs ->
                _uiState.update { state ->
                    state.copy(
                        keepScreenOn = prefs.keepScreenOn,
                        reducedMotion = prefs.reducedMotion,
                        autoSaveAnnotations = prefs.autoSaveAnnotations
                    )
                }
            }
        }
    }
    
    // Page bitmap flows for UI observation
    private val pageBitmapFlows = mutableMapOf<Int, MutableStateFlow<Bitmap?>>()
    
    // Annotation storage - per page
    private val _pageStrokes = mutableMapOf<Int, MutableList<InkStroke>>()
    private val pageStrokesFlows = mutableMapOf<Int, MutableStateFlow<List<InkStroke>>>()
    
    // Shape annotations - per page
    private val _pageShapes = mutableMapOf<Int, MutableList<ShapeAnnotation>>()
    private val pageShapesFlows = mutableMapOf<Int, MutableStateFlow<List<ShapeAnnotation>>>()
    
    // Text annotations - per page
    private val _pageTexts = mutableMapOf<Int, MutableList<TextAnnotation>>()
    private val pageTextsFlows = mutableMapOf<Int, MutableStateFlow<List<TextAnnotation>>>()
    
    // Lasso selection state
    private val _currentSelection = MutableStateFlow<LassoSelection?>(null)
    val currentSelection: StateFlow<LassoSelection?> = _currentSelection.asStateFlow()
    
    // Undo/redo manager
    private val undoRedoManager = UndoRedoManager()
    
    // Currently open document path and URI
    private var currentDocumentPath: String = ""
    private var currentDocumentUri: Uri? = null
    
    // Text lines cache for smart highlighter
    private val textLinesCache = mutableMapOf<Int, List<com.ospdf.reader.data.pdf.TextLine>>()
    
    /**
     * Gets text lines for a page (for smart highlighter snapping).
     */
    suspend fun getTextLines(pageIndex: Int): List<com.ospdf.reader.data.pdf.TextLine> {
        return textLinesCache.getOrPut(pageIndex) {
            pdfRenderer.getTextLines(pageIndex)
        }
    }
    
    /**
     * Finds the best text line to snap a highlight to based on touch position.
     */
    suspend fun findTextLineAt(pageIndex: Int, y: Float): com.ospdf.reader.data.pdf.TextLine? {
        val lines = getTextLines(pageIndex)
        // Find the line that contains this Y position (with some tolerance)
        return lines.minByOrNull { line ->
            val lineCenter = line.y + line.height / 2
            kotlin.math.abs(lineCenter - y)
        }?.takeIf { line ->
            // Must be within reasonable distance of the line
            y >= line.y - line.height && y <= line.y + line.height * 2
        }
    }
    
    /**
     * Gets the strokes for a specific page.
     */
    fun getPageStrokes(pageIndex: Int): StateFlow<List<InkStroke>> {
        return pageStrokesFlows.getOrPut(pageIndex) {
            MutableStateFlow(_pageStrokes[pageIndex]?.toList() ?: emptyList())
        }
    }
    
    /**
     * Gets the shapes for a specific page.
     */
    fun getPageShapes(pageIndex: Int): StateFlow<List<ShapeAnnotation>> {
        return pageShapesFlows.getOrPut(pageIndex) {
            MutableStateFlow(_pageShapes[pageIndex]?.toList() ?: emptyList())
        }
    }
    
    /**
     * Gets the text annotations for a specific page.
     */
    fun getPageTexts(pageIndex: Int): StateFlow<List<TextAnnotation>> {
        return pageTextsFlows.getOrPut(pageIndex) {
            MutableStateFlow(_pageTexts[pageIndex]?.toList() ?: emptyList())
        }
    }
    
    /**
     * Loads a PDF document from a URI.
     */
    fun loadDocument(uri: Uri) {
        android.util.Log.d("ReaderViewModel", "loadDocument called with URI: $uri")
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            pdfRenderer.openDocument(uri).fold(
                onSuccess = { document ->
                    currentDocumentPath = document.path
                    currentDocumentUri = uri
                    android.util.Log.d("ReaderViewModel", "Document loaded. currentDocumentUri=$currentDocumentUri, path=${document.path}")
                    
                    // Record recent open
                    viewModelScope.launch(Dispatchers.IO) {
                        recentDocumentsRepository.recordOpen(
                            com.ospdf.reader.domain.model.PdfDocument(
                                uri = uri,
                                name = document.name,
                                path = document.path,
                                pageCount = document.pageCount,
                                currentPage = 0,
                                fileSize = document.fileSize,
                                lastOpened = System.currentTimeMillis()
                            )
                        )
                    }
                    _uiState.update {
                        it.copy(
                            documentName = document.name,
                            documentPath = document.path,
                            pageCount = document.pageCount,
                            isLoading = false
                        )
                    }
                    // Pre-render first few pages
                    preRenderPages(0, minOf(3, document.pageCount))
                    
                    // Load saved annotations from database
                    loadAnnotationsFromDatabase(document.path)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load document"
                        )
                    }
                }
            )
        }
    }
    
    /**
     * Loads all saved annotations from the database for the current document.
     */
    private fun loadAnnotationsFromDatabase(documentPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Load ink strokes
                val savedStrokes = annotationRepository.loadInkStrokes(documentPath)
                savedStrokes.forEach { (pageNumber, strokes) ->
                    _pageStrokes[pageNumber] = strokes.toMutableList()
                    withContext(Dispatchers.Main) {
                        updatePageStrokes(pageNumber)
                    }
                }
                
                // Load shape annotations
                val savedShapes = annotationRepository.loadShapes(documentPath)
                savedShapes.forEach { (pageNumber, shapes) ->
                    _pageShapes[pageNumber] = shapes.toMutableList()
                    withContext(Dispatchers.Main) {
                        updatePageShapes(pageNumber)
                    }
                }
            } catch (e: Exception) {
                // Log error but don't crash - annotations just won't be loaded
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Saves all annotations for the current document to the database.
     */
    private fun saveAnnotationsToDatabase() {
        val documentPath = currentDocumentPath
        if (documentPath.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Save ink strokes
                annotationRepository.saveAllInkStrokes(documentPath, _pageStrokes.mapValues { it.value.toList() })
                
                // Save shape annotations
                annotationRepository.saveAllShapes(documentPath, _pageShapes.mapValues { it.value.toList() })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Gets a flow that emits the bitmap for a specific page.
     */
    fun getPageBitmap(pageIndex: Int): StateFlow<Bitmap?> {
        return pageBitmapFlows.getOrPut(pageIndex) {
            MutableStateFlow<Bitmap?>(null).also { flow ->
                viewModelScope.launch {
                    renderPage(pageIndex)?.let { bitmap ->
                        flow.value = bitmap
                    }
                }
            }
        }
    }
    
    /**
     * Renders a page and caches the result.
     */
    private suspend fun renderPage(pageIndex: Int): Bitmap? {
        val cacheKey = bitmapCache.pageKey(currentDocumentPath, pageIndex, renderScale)
        
        // Return cached if available
        bitmapCache.get(cacheKey)?.let { return it }
        
        return withContext(Dispatchers.IO) {
            pdfRenderer.renderPage(pageIndex, renderScale)?.also { bitmap ->
                // Cache the bitmap
                bitmapCache.put(cacheKey, bitmap)
                
                // Update the flow
                pageBitmapFlows[pageIndex]?.value = bitmap
            }
        }
    }
    
    /**
     * Pre-renders pages in background.
     */
    private fun preRenderPages(startPage: Int, count: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            for (i in startPage until minOf(startPage + count, _uiState.value.pageCount)) {
                val cacheKey = bitmapCache.pageKey(currentDocumentPath, i, renderScale)
                if (bitmapCache.get(cacheKey) == null) {
                    renderPage(i)
                }
            }
        }
    }
    
    /**
     * Called when the visible page changes.
     */
    fun onPageChanged(pageIndex: Int) {
        _uiState.update { it.copy(currentPage = pageIndex) }
        
        // Pre-render adjacent pages
        viewModelScope.launch {
            val pagesToRender = listOf(
                pageIndex - 1,
                pageIndex + 1,
                pageIndex + 2
            ).filter { it >= 0 && it < _uiState.value.pageCount }
            
            pagesToRender.forEach { page ->
                val cacheKey = bitmapCache.pageKey(currentDocumentPath, page, renderScale)
                if (bitmapCache.get(cacheKey) == null) {
                    renderPage(page)
                }
            }
        }
    }
    
    /**
     * Toggles between horizontal swipe and vertical scroll modes.
     */
    fun toggleReadingMode() {
        _uiState.update { state ->
            state.copy(
                readingMode = when (state.readingMode) {
                    ReadingMode.HORIZONTAL_SWIPE -> ReadingMode.VERTICAL_SCROLL
                    ReadingMode.VERTICAL_SCROLL -> ReadingMode.HORIZONTAL_SWIPE
                }
            )
        }
    }
    
    // -------------------- Annotation Methods --------------------
    
    /**
     * Sets the current annotation tool.
     */
    fun setTool(tool: AnnotationTool) {
        _uiState.update { state ->
            state.copy(
                toolState = state.toolState.copy(currentTool = tool),
                showAnnotationToolbar = tool != AnnotationTool.NONE
            )
        }
    }
    
    /**
     * Opens the annotation toolbar with pen selected by default.
     */
    fun openAnnotationMode() {
        _uiState.update { state ->
            state.copy(
                toolState = state.toolState.copy(currentTool = AnnotationTool.PEN),
                showAnnotationToolbar = true
            )
        }
    }
    
    /**
     * Closes annotation mode and returns to navigation.
     */
    fun closeAnnotationMode() {
        _uiState.update { state ->
            state.copy(
                toolState = state.toolState.copy(currentTool = AnnotationTool.NONE),
                showAnnotationToolbar = false
            )
        }
    }
    
    /**
     * Sets the current ink color for the active tool.
     */
    fun setColor(color: Color) {
        _uiState.update { state ->
            val newToolState = when (state.toolState.currentTool) {
                AnnotationTool.PEN -> state.toolState.copy(
                    penSettings = state.toolState.penSettings.copy(color = color)
                )
                AnnotationTool.PEN_2 -> state.toolState.copy(
                    pen2Settings = state.toolState.pen2Settings.copy(color = color)
                )
                AnnotationTool.HIGHLIGHTER -> state.toolState.copy(
                    highlighterSettings = state.toolState.highlighterSettings.copy(color = color)
                )
                AnnotationTool.TEXT -> state.toolState.copy(
                    textSettings = state.toolState.textSettings.copy(color = color)
                )
                AnnotationTool.SHAPE -> state.toolState.copy(
                    shapeSettings = state.toolState.shapeSettings.copy(color = color)
                )
                else -> state.toolState
            }
            state.copy(toolState = newToolState)
        }
    }
    
    /**
     * Sets the stroke width for the active tool.
     */
    fun setStrokeWidth(width: Float) {
        _uiState.update { state ->
            val newToolState = when (state.toolState.currentTool) {
                AnnotationTool.PEN -> state.toolState.copy(
                    penSettings = state.toolState.penSettings.copy(strokeWidth = width)
                )
                AnnotationTool.PEN_2 -> state.toolState.copy(
                    pen2Settings = state.toolState.pen2Settings.copy(strokeWidth = width)
                )
                AnnotationTool.HIGHLIGHTER -> state.toolState.copy(
                    highlighterSettings = state.toolState.highlighterSettings.copy(strokeWidth = width)
                )
                AnnotationTool.TEXT -> state.toolState.copy(
                    textSettings = state.toolState.textSettings.copy(strokeWidth = width)
                )
                AnnotationTool.SHAPE -> state.toolState.copy(
                    shapeSettings = state.toolState.shapeSettings.copy(strokeWidth = width)
                )
                AnnotationTool.ERASER -> state.toolState.copy(eraserWidth = width)
                else -> state.toolState
            }
            state.copy(toolState = newToolState)
        }
    }
    
    /**
     * Sets the current shape type.
     */
    fun setShapeType(shapeType: ShapeType) {
        _uiState.update { state ->
            state.copy(
                toolState = state.toolState.copy(shapeType = shapeType)
            )
        }
    }
    
    /**
     * Adds a shape annotation to the current page.
     */
    fun addShape(shape: ShapeAnnotation) {
        val pageIndex = _uiState.value.currentPage
        val shapeWithPage = shape.copy(pageNumber = pageIndex)
        
        val shapes = _pageShapes.getOrPut(pageIndex) { mutableListOf() }
        shapes.add(shapeWithPage)
        
        pageShapesFlows.getOrPut(pageIndex) { MutableStateFlow(emptyList()) }.value = shapes.toList()
        _uiState.update { it.copy(hasUnsavedChanges = true) }
        
        // Persist to database
        saveAnnotationsToDatabase()
    }
    
    /**
     * Adds a text annotation to the current page.
     */
    fun addText(text: String, x: Float, y: Float, color: Color? = null, fontSize: Float? = null) {
        val pageIndex = _uiState.value.currentPage
        val annotation = TextAnnotation(
            pageNumber = pageIndex,
            x = x,
            y = y,
            text = text,
            color = color ?: _uiState.value.toolState.currentColor,
            fontSize = fontSize ?: 16f
        )
        
        val texts = _pageTexts.getOrPut(pageIndex) { mutableListOf() }
        texts.add(annotation)
        
        pageTextsFlows.getOrPut(pageIndex) { MutableStateFlow(emptyList()) }.value = texts.toList()
        _uiState.update { it.copy(hasUnsavedChanges = true) }
    }
    
    /**
     * Updates lasso selection.
     */
    fun setLassoSelection(selection: LassoSelection?) {
        _currentSelection.value = selection
    }
    
    /**
     * Deletes selected items from lasso selection.
     */
    fun deleteSelection() {
        val selection = _currentSelection.value ?: return
        val pageIndex = _uiState.value.currentPage
        
        // Remove selected strokes
        _pageStrokes[pageIndex]?.removeAll { it.id in selection.selectedStrokeIds }
        updatePageStrokes(pageIndex)
        
        // Remove selected shapes
        _pageShapes[pageIndex]?.removeAll { it.id in selection.selectedShapeIds }
        pageShapesFlows[pageIndex]?.value = _pageShapes[pageIndex]?.toList() ?: emptyList()
        
        _currentSelection.value = null
        _uiState.update { it.copy(hasUnsavedChanges = true) }
        
        // Persist to database
        saveAnnotationsToDatabase()
    }
    
    /**
     * Moves selected items by a delta.
     * @param pageDeltaX delta in page-space X
     * @param pageDeltaY delta in page-space Y  
     * @param screenDeltaX delta in screen-space X (for updating selection bounds)
     * @param screenDeltaY delta in screen-space Y (for updating selection bounds)
     */
    fun moveSelection(pageDeltaX: Float, pageDeltaY: Float, screenDeltaX: Float, screenDeltaY: Float) {
        val selection = _currentSelection.value ?: return
        val pageIndex = _uiState.value.currentPage
        
        // Move selected strokes
        val strokes = _pageStrokes[pageIndex]
        if (strokes != null) {
            val updatedStrokes = strokes.map { stroke ->
                if (stroke.id in selection.selectedStrokeIds) {
                    stroke.copy(
                        points = stroke.points.map { pt ->
                            com.ospdf.reader.domain.model.StrokePoint(
                                x = pt.x + pageDeltaX,
                                y = pt.y + pageDeltaY,
                                pressure = pt.pressure,
                                timestamp = pt.timestamp
                            )
                        }
                    )
                } else stroke
            }
            _pageStrokes[pageIndex] = updatedStrokes.toMutableList()
            updatePageStrokes(pageIndex)
        }
        
        // Move selected shapes
        val shapes = _pageShapes[pageIndex]
        if (shapes != null) {
            val updatedShapes = shapes.map { shape ->
                if (shape.id in selection.selectedShapeIds) {
                    shape.copy(
                        startX = shape.startX + pageDeltaX,
                        startY = shape.startY + pageDeltaY,
                        endX = shape.endX + pageDeltaX,
                        endY = shape.endY + pageDeltaY
                    )
                } else shape
            }
            _pageShapes[pageIndex] = updatedShapes.toMutableList()
            pageShapesFlows[pageIndex]?.value = updatedShapes
        }
        
        // Update selection bounds (in screen-space)
        val oldBounds = selection.bounds
        if (oldBounds != null) {
            _currentSelection.value = selection.copy(
                bounds = androidx.compose.ui.geometry.Rect(
                    left = oldBounds.left + screenDeltaX,
                    top = oldBounds.top + screenDeltaY,
                    right = oldBounds.right + screenDeltaX,
                    bottom = oldBounds.bottom + screenDeltaY
                )
            )
        }
        
        _uiState.update { it.copy(hasUnsavedChanges = true) }
    }
    
    /**
     * Commits the current selection move and persists to database.
     * Call this when drag ends.
     */
    fun commitSelectionMove() {
        if (_currentSelection.value != null) {
            saveAnnotationsToDatabase()
        }
    }
    
    /**
     * Adds a completed stroke to the current page.
     */
    fun addStroke(stroke: InkStroke) {
        val pageIndex = _uiState.value.currentPage
        val strokeWithPage = stroke.copy(pageNumber = pageIndex)
        
        // Get or create the strokes list for this page
        val strokes = _pageStrokes.getOrPut(pageIndex) { mutableListOf() }
        
        // Execute via undo manager
        undoRedoManager.execute(AddStrokeAction(strokeWithPage), strokes)
        
        // Update the flow
        updatePageStrokes(pageIndex)
        updateUndoRedoState()
        
        _uiState.update { it.copy(hasUnsavedChanges = true) }
        
        // Persist to database
        saveAnnotationsToDatabase()
    }
    
    /**
     * Removes a stroke (eraser action).
     */
    fun removeStroke(strokeId: String) {
        val pageIndex = _uiState.value.currentPage
        val strokes = _pageStrokes[pageIndex] ?: return
        
        val strokeIndex = strokes.indexOfFirst { it.id == strokeId }
        if (strokeIndex == -1) return
        
        val stroke = strokes[strokeIndex]
        
        // Execute via undo manager
        undoRedoManager.execute(RemoveStrokeAction(stroke, strokeIndex), strokes)
        
        // Update the flow
        updatePageStrokes(pageIndex)
        updateUndoRedoState()
        
        _uiState.update { it.copy(hasUnsavedChanges = true) }
        
        // Persist to database
        saveAnnotationsToDatabase()
    }
    
    /**
     * Undoes the last action.
     */
    fun undo() {
        val pageIndex = _uiState.value.currentPage
        val strokes = _pageStrokes.getOrPut(pageIndex) { mutableListOf() }
        
        if (undoRedoManager.undo(strokes)) {
            updatePageStrokes(pageIndex)
            updateUndoRedoState()
            saveAnnotationsToDatabase()
        }
    }
    
    /**
     * Redoes the last undone action.
     */
    fun redo() {
        val pageIndex = _uiState.value.currentPage
        val strokes = _pageStrokes.getOrPut(pageIndex) { mutableListOf() }
        
        if (undoRedoManager.redo(strokes)) {
            updatePageStrokes(pageIndex)
            updateUndoRedoState()
            saveAnnotationsToDatabase()
        }
    }
    
    private fun updatePageStrokes(pageIndex: Int) {
        val strokes = _pageStrokes[pageIndex]?.toList() ?: emptyList()
        pageStrokesFlows.getOrPut(pageIndex) { MutableStateFlow(emptyList()) }.value = strokes
    }
    
    private fun updatePageShapes(pageIndex: Int) {
        val shapes = _pageShapes[pageIndex]?.toList() ?: emptyList()
        pageShapesFlows.getOrPut(pageIndex) { MutableStateFlow(emptyList()) }.value = shapes
    }
    
    private fun updateUndoRedoState() {
        _uiState.update { state ->
            state.copy(
                canUndo = undoRedoManager.canUndo,
                canRedo = undoRedoManager.canRedo
            )
        }
    }
    
    /**
     * Saves annotations to the PDF.
     */
    fun saveAnnotations() {
        if (_pageStrokes.isEmpty() && _pageShapes.isEmpty()) {
            // No annotations to save, but maybe we want to exit?
            if (_uiState.value.isExiting) {
                // Determine completion deferred to UI side observing hasUnsavedChanges=false
            }
            return
        }
        
        val uri = currentDocumentUri ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            
            // Convert to map of page -> strokes/shapes
            val strokesMap = _pageStrokes.mapValues { it.value.toList() }
            val shapesMap = _pageShapes.mapValues { it.value.toList() }
            
            // Try to save effectively to the original file
            val result = annotationManager.saveAnnotationsToOriginalFile(
                originalUri = uri,
                sourcePath = currentDocumentPath,
                strokes = strokesMap,
                shapes = shapesMap
            )
            
            result.fold(
                onSuccess = { path ->
                    // Helper to normalize path
                    val savedPath = if (path == "original") currentDocumentPath else path
                    
                    // Mark as modified if it's a synced document
                    if (savedPath != null && savedPath.isNotEmpty()) {
                        syncRepository.markModified(savedPath)
                    }
                    
                    _uiState.update { 
                        it.copy(
                            isSaving = false,
                            hasUnsavedChanges = false,
                            successMessage = "Saved successfully"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            isSaving = false,
                            error = "Failed to save: ${error.message}",
                            isExiting = false // Cancel exit on error
                        )
                    }
                }
            )
        }
    }
    
    fun saveAndExit() {
        val currentState = _uiState.value
        _uiState.update { it.copy(isExiting = true) }
        
        // Only save if auto-save is enabled and there are unsaved changes
        if (currentState.autoSaveAnnotations && currentState.hasUnsavedChanges) {
            saveAnnotations()
        } else {
            // Just mark as no unsaved changes to trigger exit
            _uiState.update { it.copy(hasUnsavedChanges = false) }
        }
    }
    
    // -------------------- UI State Toggles --------------------
    
    fun toggleSearch() {
        _uiState.update { 
            if (it.showSearch) {
                // Closing search - clear results
                it.copy(
                    showSearch = false,
                    searchQuery = "",
                    searchResults = emptyList(),
                    currentSearchIndex = -1
                )
            } else {
                it.copy(showSearch = true)
            }
        }
    }
    
    /**
     * Performs a search across all pages.
     */
    fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update { 
                it.copy(
                    searchQuery = "",
                    searchResults = emptyList(),
                    currentSearchIndex = -1,
                    isSearching = false
                )
            }
            return
        }
        
        _uiState.update { 
            it.copy(
                searchQuery = query,
                isSearching = true
            )
        }
        
        viewModelScope.launch {
            val results = pdfRenderer.searchText(query)
            _uiState.update { 
                it.copy(
                    searchResults = results,
                    currentSearchIndex = if (results.isNotEmpty()) 0 else -1,
                    isSearching = false
                )
            }
            
            // Navigate to first result
            if (results.isNotEmpty()) {
                goToSearchResult(0)
            }
        }
    }
    
    /**
     * Goes to the next search result.
     */
    fun goToNextResult() {
        val state = _uiState.value
        if (state.searchResults.isEmpty()) return
        
        val nextIndex = (state.currentSearchIndex + 1) % state.searchResults.size
        goToSearchResult(nextIndex)
    }
    
    /**
     * Goes to the previous search result.
     */
    fun goToPreviousResult() {
        val state = _uiState.value
        if (state.searchResults.isEmpty()) return
        
        val prevIndex = if (state.currentSearchIndex <= 0) {
            state.searchResults.size - 1
        } else {
            state.currentSearchIndex - 1
        }
        goToSearchResult(prevIndex)
    }
    
    /**
     * Navigates to a specific search result.
     */
    private fun goToSearchResult(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.searchResults.size) return
        
        val result = state.searchResults[index]
        _uiState.update { 
            it.copy(
                currentSearchIndex = index,
                currentPage = result.pageNumber
            )
        }
    }
    
    /**
     * Clears search results.
     */
    fun clearSearch() {
        _uiState.update { 
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                currentSearchIndex = -1
            )
        }
    }
    
    fun toggleBookmarks() {
        _uiState.update { it.copy(showBookmarks = !it.showBookmarks) }
    }
    
    fun toggleMoreMenu() {
        _uiState.update { it.copy(showMoreMenu = !it.showMoreMenu) }
    }
    
    fun dismissMoreMenu() {
        _uiState.update { it.copy(showMoreMenu = false) }
    }
    
    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }
    
    /**
     * Flattens all annotations permanently into the original PDF file.
     * This saves annotations directly to the original file, preserving Google backup sync.
     */
    fun flattenAnnotationsToPdf() {
        val hasAnnotations = _pageStrokes.values.any { it.isNotEmpty() } || 
                             _pageShapes.values.any { it.isNotEmpty() }
        
        if (!hasAnnotations) {
            _uiState.update { it.copy(error = "No annotations to flatten") }
            return
        }
        
        val uri = currentDocumentUri
        if (uri == null) {
            _uiState.update { it.copy(error = "Cannot save: document URI not available") }
            return
        }
        
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(isSaving = true, showMoreMenu = false) }
            
            // Collect annotations before any async operations
            val strokesMap = _pageStrokes.mapValues { it.value.toList() }
            val shapesMap = _pageShapes.mapValues { it.value.toList() }
            val savedDocumentPath = currentDocumentPath
            
            try {
                // Close document first so we can modify the file (on IO thread)
                withContext(Dispatchers.IO) {
                    pdfRenderer.closeDocument()
                }
                
                // Save annotations directly to the original file with baking
                // bakeAnnotations = true burns annotations into page content (uneditable)
                val saveResult = annotationManager.saveAnnotationsToOriginalFile(
                    originalUri = uri,
                    sourcePath = savedDocumentPath,
                    strokes = strokesMap,
                    shapes = shapesMap,
                    bakeAnnotations = true
                )
                
                if (saveResult.isSuccess) {
                    val outputPath = saveResult.getOrNull()!!
                    val savedToOriginal = outputPath == "original"
                    
                    // Clear in-memory annotations since they're now in the PDF
                    _pageStrokes.clear()
                    _pageShapes.clear()
                    _pageTexts.clear()
                    
                    // Update all annotation flows
                    pageStrokesFlows.forEach { (_, flow) -> flow.value = emptyList() }
                    pageShapesFlows.forEach { (_, flow) -> flow.value = emptyList() }
                    pageTextsFlows.forEach { (_, flow) -> flow.value = emptyList() }
                    
                    // Delete annotations from database (fire and forget)
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            annotationRepository.saveAllInkStrokes(savedDocumentPath, emptyMap())
                            annotationRepository.saveAllShapes(savedDocumentPath, emptyMap())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    // Clear page cache and reset bitmap flows
                    bitmapCache.clear()
                    pageBitmapFlows.forEach { (_, flow) -> flow.value = null }
                    
                    if (savedToOriginal) {
                        // Reopen the document from original URI
                        val reopenResult = pdfRenderer.reopenDocument(uri)
                        
                        if (reopenResult.isSuccess) {
                            val doc = reopenResult.getOrNull()!!
                            currentDocumentPath = doc.path
                            currentDocumentUri = uri
                            
                            _uiState.update { 
                                it.copy(
                                    isSaving = false,
                                    hasUnsavedChanges = false,
                                    error = null,
                                    successMessage = "Annotations saved permanently to PDF"
                                )
                            }
                            
                            // Re-render current page
                            val currentPage = _uiState.value.currentPage
                            withContext(Dispatchers.IO) {
                                renderPage(currentPage)
                            }
                        } else {
                            _uiState.update { 
                                it.copy(
                                    isSaving = false,
                                    hasUnsavedChanges = false,
                                    error = null,
                                    successMessage = "Saved! Please reopen the file to see changes."
                                )
                            }
                        }
                    } else {
                        // Saved to app storage - show the path
                        _uiState.update { 
                            it.copy(
                                isSaving = false,
                                hasUnsavedChanges = false,
                                error = null,
                                successMessage = "Saved to: $outputPath"
                            )
                        }
                    }
                } else {
                    // Save failed - try to reopen document
                    try {
                        pdfRenderer.reopenDocument(uri)
                    } catch (reopenEx: Exception) {
                        android.util.Log.e("ReaderViewModel", "Failed to reopen document after save failure", reopenEx)
                    }
                    
                    _uiState.update { 
                        it.copy(
                            isSaving = false,
                            error = "Failed to save: ${saveResult.exceptionOrNull()?.message ?: "Unknown error"}"
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                
                // Try to reopen document on exception
                try {
                    pdfRenderer.reopenDocument(uri)
                } catch (reopenEx: Exception) {
                    android.util.Log.e("ReaderViewModel", "Failed to reopen document after exception", reopenEx)
                }
                
                _uiState.update { 
                    it.copy(
                        isSaving = false,
                        error = "Failed to save: ${e.message}"
                    )
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Clear cached bitmaps (BitmapCache is singleton, clearing here removes this document's pages)
        bitmapCache.clear()
        
        // Close the document
        viewModelScope.launch {
            pdfRenderer.closeDocument()
        }
    }
    
    /**
     * Export the current PDF to the public Downloads folder for testing.
     * This is useful for verifying that the saved PDF is valid in other readers.
     */
    fun exportToDownloads() {
        val sourcePath = currentDocumentPath
        if (sourcePath.isBlank()) {
            _uiState.update { it.copy(error = "No document to export") }
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = java.io.File(sourcePath)
                if (!sourceFile.exists()) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(error = "Source file not found") }
                    }
                    return@launch
                }
                
                // Get public Downloads directory
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                
                val originalName = sourcePath.substringAfterLast("/").substringAfterLast("\\")
                val baseName = originalName.substringBeforeLast(".")
                val extension = originalName.substringAfterLast(".", "pdf")
                val exportName = "${baseName}_exported.${extension}"
                
                val destFile = java.io.File(downloadsDir, exportName)
                
                android.util.Log.d("ReaderViewModel", "Exporting from: $sourcePath")
                android.util.Log.d("ReaderViewModel", "Exporting to: ${destFile.absolutePath}")
                
                sourceFile.copyTo(destFile, overwrite = true)
                
                android.util.Log.d("ReaderViewModel", "Export complete: ${destFile.length()} bytes")
                
                // Notify MediaScanner so file appears in file managers
                android.media.MediaScannerConnection.scanFile(
                    appContext,
                    arrayOf(destFile.absolutePath),
                    arrayOf("application/pdf")
                ) { path, uri ->
                    android.util.Log.d("ReaderViewModel", "MediaScanner scanned: $path -> $uri")
                }
                
                withContext(Dispatchers.Main) {
                    _uiState.update { 
                        it.copy(successMessage = "Exported to Downloads: $exportName") 
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ReaderViewModel", "Export failed", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(error = "Export failed: ${e.message}") }
                }
            }
        }
    }
}
