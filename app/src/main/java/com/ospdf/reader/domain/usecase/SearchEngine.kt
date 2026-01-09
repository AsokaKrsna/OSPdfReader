package com.ospdf.reader.domain.usecase

import com.artifex.mupdf.fitz.Quad
import com.ospdf.reader.data.pdf.MuPdfRenderer
import com.ospdf.reader.data.pdf.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Represents a search hit with page location.
 */
data class SearchHit(
    val pageNumber: Int,
    val text: String,
    val bounds: List<FloatArray>, // [left, top, right, bottom] for each quad
    val context: String = ""      // Surrounding text for preview
)

/**
 * Search state for the UI.
 */
data class SearchState(
    val query: String = "",
    val results: List<SearchHit> = emptyList(),
    val currentIndex: Int = 0,
    val isSearching: Boolean = false,
    val totalCount: Int = 0
) {
    val hasResults: Boolean get() = results.isNotEmpty()
    val currentResult: SearchHit? get() = results.getOrNull(currentIndex)
}

/**
 * Handles full-text search in PDF documents.
 */
class SearchEngine @Inject constructor(
    private val pdfRenderer: MuPdfRenderer
) {
    
    /**
     * Searches for text in the document.
     * Returns a flow that emits results as they're found (page by page).
     */
    fun search(query: String): Flow<List<SearchHit>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }
        
        val allResults = mutableListOf<SearchHit>()
        val searchResults = pdfRenderer.searchText(query)
        
        // Group by page for better organization
        val resultsByPage = searchResults.groupBy { it.pageNumber }
        
        for ((pageNumber, pageResults) in resultsByPage) {
            val hits = pageResults.map { result ->
                SearchHit(
                    pageNumber = result.pageNumber,
                    text = query,
                    bounds = listOf(quadToFloatArray(result.bounds)),
                    context = extractContext(pageNumber, result.bounds)
                )
            }
            allResults.addAll(hits)
            emit(allResults.toList())
        }
        
        if (allResults.isEmpty()) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Extracts surrounding text for context preview.
     */
    private suspend fun extractContext(pageNumber: Int, bounds: Quad): String {
        return try {
            val fullText = pdfRenderer.extractText(pageNumber)
            // Return truncated text as context (simplified)
            if (fullText.length > 100) {
                fullText.take(100) + "..."
            } else {
                fullText
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Converts a Quad to a float array [left, top, right, bottom].
     */
    private fun quadToFloatArray(quad: Quad): FloatArray {
        return floatArrayOf(
            quad.ul_x, quad.ul_y,
            quad.lr_x, quad.lr_y
        )
    }
}

/**
 * Manager for search operations in the reader.
 */
class SearchManager {
    private var currentState = SearchState()
    
    fun updateQuery(query: String): SearchState {
        currentState = currentState.copy(
            query = query,
            results = emptyList(),
            currentIndex = 0,
            isSearching = query.isNotBlank()
        )
        return currentState
    }
    
    fun updateResults(results: List<SearchHit>): SearchState {
        currentState = currentState.copy(
            results = results,
            totalCount = results.size,
            isSearching = false,
            currentIndex = if (results.isEmpty()) 0 else 0
        )
        return currentState
    }
    
    fun nextResult(): SearchState {
        if (currentState.results.isEmpty()) return currentState
        currentState = currentState.copy(
            currentIndex = (currentState.currentIndex + 1) % currentState.results.size
        )
        return currentState
    }
    
    fun previousResult(): SearchState {
        if (currentState.results.isEmpty()) return currentState
        currentState = currentState.copy(
            currentIndex = if (currentState.currentIndex > 0) 
                currentState.currentIndex - 1 
            else 
                currentState.results.size - 1
        )
        return currentState
    }
    
    fun clear(): SearchState {
        currentState = SearchState()
        return currentState
    }
    
    fun getState(): SearchState = currentState
}
