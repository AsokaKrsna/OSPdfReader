package com.ospdf.reader.util

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.SoftReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LRU bitmap cache for PDF pages with memory-aware sizing.
 */
@Singleton
class BitmapCache @Inject constructor() {
    
    private val maxMemory = Runtime.getRuntime().maxMemory() / 1024
    private val cacheSize = (maxMemory / 4).toInt() // Use 1/4 of available memory
    
    private val cache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
        
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (evicted && !oldValue.isRecycled) {
                // Store in soft reference cache for potential reuse
                softCache[key] = SoftReference(oldValue)
            }
        }
    }
    
    // Secondary soft reference cache for recently evicted bitmaps (bounded to prevent unbounded growth)
    private val maxSoftCacheSize = 20
    private val softCache = object : LinkedHashMap<String, SoftReference<Bitmap>>(maxSoftCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SoftReference<Bitmap>>?): Boolean {
            return size > maxSoftCacheSize
        }
    }
    
    /**
     * Gets a cached bitmap or null if not found.
     */
    fun get(key: String): Bitmap? {
        // Try primary cache first
        cache.get(key)?.let { return it }
        
        // Try soft reference cache
        softCache[key]?.get()?.let { bitmap ->
            if (!bitmap.isRecycled) {
                // Promote back to primary cache
                cache.put(key, bitmap)
                softCache.remove(key)
                return bitmap
            } else {
                softCache.remove(key)
            }
        }
        
        return null
    }
    
    /**
     * Puts a bitmap in the cache.
     */
    fun put(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            cache.put(key, bitmap)
        }
    }
    
    /**
     * Generates a cache key for a page.
     */
    fun pageKey(documentPath: String, pageIndex: Int, scale: Float): String {
        return "${documentPath}_${pageIndex}_$scale"
    }
    
    /**
     * Clears all cached bitmaps.
     */
    fun clear() {
        cache.evictAll()
        softCache.clear()
    }
    
    /**
     * Removes specific page from cache.
     */
    fun remove(key: String) {
        cache.remove(key)
        softCache.remove(key)
    }
    
    /**
     * Gets cache statistics.
     */
    fun stats(): CacheStats {
        return CacheStats(
            size = cache.size(),
            maxSize = cache.maxSize(),
            hitCount = cache.hitCount(),
            missCount = cache.missCount(),
            evictionCount = cache.evictionCount()
        )
    }
}

data class CacheStats(
    val size: Int,
    val maxSize: Int,
    val hitCount: Int,
    val missCount: Int,
    val evictionCount: Int
) {
    val hitRate: Float get() = if (hitCount + missCount > 0) {
        hitCount.toFloat() / (hitCount + missCount)
    } else 0f
}

/**
 * Pre-fetcher for PDF pages to ensure smooth scrolling.
 */
class PagePreFetcher(
    private val scope: CoroutineScope,
    private val cache: BitmapCache,
    private val renderPage: suspend (Int, Float) -> Bitmap?
) {
    private val mutex = Mutex()
    private var currentPrefetchJob: Job? = null
    private val prefetchRadius = 2 // Pages to prefetch on each side
    
    /**
     * Starts prefetching pages around the current page.
     */
    fun prefetchAround(
        currentPage: Int,
        totalPages: Int,
        documentPath: String,
        scale: Float
    ) {
        currentPrefetchJob?.cancel()
        
        currentPrefetchJob = scope.launch(Dispatchers.IO) {
            val pagesToPrefetch = buildList {
                // Current page first (highest priority)
                add(currentPage)
                
                // Then adjacent pages
                for (offset in 1..prefetchRadius) {
                    if (currentPage + offset < totalPages) add(currentPage + offset)
                    if (currentPage - offset >= 0) add(currentPage - offset)
                }
            }
            
            for (pageIndex in pagesToPrefetch) {
                if (!isActive) break
                
                val key = cache.pageKey(documentPath, pageIndex, scale)
                if (cache.get(key) == null) {
                    mutex.withLock {
                        // Double-check after acquiring lock
                        if (cache.get(key) == null) {
                            renderPage(pageIndex, scale)?.let { bitmap ->
                                cache.put(key, bitmap)
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Cancels any ongoing prefetch operations.
     */
    fun cancel() {
        currentPrefetchJob?.cancel()
    }
}

/**
 * Debouncer for search and other frequent operations.
 */
class Debouncer(
    private val scope: CoroutineScope,
    private val delayMs: Long = 300
) {
    private var debounceJob: Job? = null
    
    fun debounce(action: suspend () -> Unit) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(delayMs)
            action()
        }
    }
    
    fun cancel() {
        debounceJob?.cancel()
    }
}

/**
 * Throttler for gestures and scroll events.
 */
class Throttler(
    private val intervalMs: Long = 16 // ~60 FPS
) {
    private var lastExecutionTime = 0L
    
    fun throttle(action: () -> Unit): Boolean {
        val currentTime = System.currentTimeMillis()
        return if (currentTime - lastExecutionTime >= intervalMs) {
            lastExecutionTime = currentTime
            action()
            true
        } else {
            false
        }
    }
}
