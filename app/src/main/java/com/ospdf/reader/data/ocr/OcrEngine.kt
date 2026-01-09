package com.ospdf.reader.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OCR result for a page.
 */
data class OcrResult(
    val pageNumber: Int,
    val text: String,
    val confidence: Float,
    val words: List<OcrWord> = emptyList()
)

/**
 * Individual word with bounding box.
 */
data class OcrWord(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float
)

/**
 * Tesseract-based OCR engine for scanned PDF pages.
 * Uses Tesseract4Android for fully offline OCR.
 */
@Singleton
class OcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tessApi: TessBaseAPI? = null
    private val mutex = Mutex()
    private var isInitialized = false
    
    // Supported languages - start with English
    private val defaultLanguage = "eng"
    
    /**
     * Initializes Tesseract with language data.
     * Must be called before performing OCR.
     */
    suspend fun initialize(language: String = defaultLanguage): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (isInitialized && tessApi != null) {
                    return@withContext Result.success(Unit)
                }
                
                // Ensure tessdata directory exists
                val tessDataPath = File(context.filesDir, "tessdata")
                if (!tessDataPath.exists()) {
                    tessDataPath.mkdirs()
                }
                
                // Check if language data exists, if not copy from assets
                val langFile = File(tessDataPath, "$language.traineddata")
                if (!langFile.exists()) {
                    copyTrainedData(language, langFile)
                }
                
                // Initialize Tesseract
                tessApi = TessBaseAPI()
                val success = tessApi!!.init(context.filesDir.absolutePath, language)
                
                if (!success) {
                    tessApi = null
                    return@withContext Result.failure(Exception("Failed to initialize Tesseract"))
                }
                
                // Configure for best quality
                tessApi!!.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, 
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.,!?@#\$%&*()-+=;:'\"<>/\\[]{}|~ ")
                tessApi!!.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                
                isInitialized = true
                Result.success(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
    
    /**
     * Performs OCR on a bitmap image.
     */
    suspend fun recognizeText(
        bitmap: Bitmap,
        pageNumber: Int = 0
    ): Result<OcrResult> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (!isInitialized || tessApi == null) {
                    initialize().getOrThrow()
                }
                
                val api = tessApi ?: return@withContext Result.failure(
                    Exception("Tesseract not initialized")
                )
                
                // Set the image
                api.setImage(bitmap)
                
                // Get the recognized text
                val text = api.utF8Text ?: ""
                val confidence = api.meanConfidence().toFloat()
                
                // Get word-level results for highlighting
                val words = mutableListOf<OcrWord>()
                val iterator = api.resultIterator
                
                if (iterator != null) {
                    do {
                        val wordText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                        val wordConf = iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                        val rect = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                        
                        if (wordText != null && rect != null) {
                            words.add(
                                OcrWord(
                                    text = wordText,
                                    left = rect.left,
                                    top = rect.top,
                                    right = rect.right,
                                    bottom = rect.bottom,
                                    confidence = wordConf
                                )
                            )
                        }
                    } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
                    
                    iterator.delete()
                }
                
                // Clear the image from memory
                api.clear()
                
                Result.success(
                    OcrResult(
                        pageNumber = pageNumber,
                        text = text,
                        confidence = confidence,
                        words = words
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
    
    /**
     * Copies trained data from assets to internal storage.
     */
    private fun copyTrainedData(language: String, destFile: File) {
        try {
            context.assets.open("tessdata/$language.traineddata").use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            // If asset doesn't exist, we'll need to download it
            // For now, throw the exception
            throw Exception(
                "Tesseract language data not found. Please download '$language.traineddata' " +
                "and place it in app/src/main/assets/tessdata/"
            )
        }
    }
    
    /**
     * Releases Tesseract resources.
     */
    suspend fun release() {
        mutex.withLock {
            tessApi?.recycle()
            tessApi = null
            isInitialized = false
        }
    }
    
    /**
     * Checks if OCR is available (language data exists).
     */
    fun isAvailable(language: String = defaultLanguage): Boolean {
        val langFile = File(context.filesDir, "tessdata/$language.traineddata")
        return langFile.exists()
    }
}
