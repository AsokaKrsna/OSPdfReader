package com.ospdf.reader.data.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.artifex.mupdf.fitz.Link
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents a hyperlink in a PDF document.
 */
data class PdfLink(
    val pageNumber: Int,
    val bounds: FloatArray, // [left, top, right, bottom]
    val uri: String?,       // External URL
    val targetPage: Int?,   // Internal page link
    val isExternal: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as PdfLink
        
        if (pageNumber != other.pageNumber) return false
        if (!bounds.contentEquals(other.bounds)) return false
        if (uri != other.uri) return false
        if (targetPage != other.targetPage) return false
        if (isExternal != other.isExternal) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = pageNumber
        result = 31 * result + bounds.contentHashCode()
        result = 31 * result + (uri?.hashCode() ?: 0)
        result = 31 * result + (targetPage ?: 0)
        result = 31 * result + isExternal.hashCode()
        return result
    }
}

/**
 * Handles PDF hyperlinks - both internal page links and external URLs.
 */
@Singleton
class HyperlinkHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Extracts all links from a PDF page.
     */
    fun extractLinksFromPage(links: Array<Link>?, pageNumber: Int): List<PdfLink> {
        if (links == null) return emptyList()
        
        return links.mapNotNull { link ->
            try {
                val bounds = floatArrayOf(
                    link.bounds.x0,
                    link.bounds.y0,
                    link.bounds.x1,
                    link.bounds.y1
                )
                
                val uri = link.uri
                
                if (uri != null) {
                    when {
                        // Internal page link (format: "#pageN" or just page number)
                        uri.startsWith("#page") -> {
                            val targetPage = uri.removePrefix("#page").toIntOrNull()
                            if (targetPage != null) {
                                PdfLink(
                                    pageNumber = pageNumber,
                                    bounds = bounds,
                                    uri = null,
                                    targetPage = targetPage - 1, // 0-indexed
                                    isExternal = false
                                )
                            } else null
                        }
                        // External URL
                        uri.startsWith("http://") || uri.startsWith("https://") -> {
                            PdfLink(
                                pageNumber = pageNumber,
                                bounds = bounds,
                                uri = uri,
                                targetPage = null,
                                isExternal = true
                            )
                        }
                        // Email link
                        uri.startsWith("mailto:") -> {
                            PdfLink(
                                pageNumber = pageNumber,
                                bounds = bounds,
                                uri = uri,
                                targetPage = null,
                                isExternal = true
                            )
                        }
                        // Other internal link (destination name)
                        else -> {
                            PdfLink(
                                pageNumber = pageNumber,
                                bounds = bounds,
                                uri = uri,
                                targetPage = null,
                                isExternal = false
                            )
                        }
                    }
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Handles a link click - either navigates internally or opens external URL.
     */
    fun handleLinkClick(
        link: PdfLink,
        onInternalNavigate: (Int) -> Unit,
        onExternalLink: (String) -> Unit = { uri -> openExternalUrl(uri) }
    ) {
        when {
            link.targetPage != null -> {
                onInternalNavigate(link.targetPage)
            }
            link.isExternal && link.uri != null -> {
                onExternalLink(link.uri)
            }
        }
    }
    
    /**
     * Opens an external URL in the default browser.
     */
    fun openExternalUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Checks if a point is within a link's bounds.
     */
    fun hitTest(
        x: Float,
        y: Float,
        links: List<PdfLink>,
        scale: Float = 1f
    ): PdfLink? {
        return links.find { link ->
            val bounds = link.bounds
            val left = bounds[0] * scale
            val top = bounds[1] * scale
            val right = bounds[2] * scale
            val bottom = bounds[3] * scale
            
            x >= left && x <= right && y >= top && y <= bottom
        }
    }
}
