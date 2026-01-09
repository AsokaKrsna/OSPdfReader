package com.ospdf.reader.data.local

import androidx.compose.ui.graphics.Color
import com.ospdf.reader.domain.model.ShapeType
import com.ospdf.reader.domain.model.StrokePoint
import com.ospdf.reader.ui.components.InkStroke
import com.ospdf.reader.ui.tools.ShapeAnnotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing annotation persistence.
 * Handles conversion between domain models and database entities.
 */
@Singleton
class AnnotationRepository @Inject constructor(
    private val inkAnnotationDao: InkAnnotationDao,
    private val shapeAnnotationDao: ShapeAnnotationDao
) {
    /**
     * Load all ink strokes for a document.
     */
    suspend fun loadInkStrokes(documentPath: String): Map<Int, List<InkStroke>> = withContext(Dispatchers.IO) {
        val entities = inkAnnotationDao.getAnnotationsForDocument(documentPath)
        entities.groupBy { it.pageNumber }
            .mapValues { (_, pageEntities) ->
                pageEntities.map { entity -> entity.toInkStroke() }
            }
    }
    
    /**
     * Load ink strokes for a specific page.
     */
    suspend fun loadInkStrokesForPage(documentPath: String, pageNumber: Int): List<InkStroke> = withContext(Dispatchers.IO) {
        val entities = inkAnnotationDao.getAnnotationsForPage(documentPath, pageNumber)
        entities.map { it.toInkStroke() }
    }
    
    /**
     * Save a single ink stroke.
     */
    suspend fun saveInkStroke(documentPath: String, pageNumber: Int, stroke: InkStroke) = withContext(Dispatchers.IO) {
        val entity = stroke.toEntity(documentPath, pageNumber)
        inkAnnotationDao.insertAnnotation(entity)
    }
    
    /**
     * Save all ink strokes for a document (replaces existing).
     */
    suspend fun saveAllInkStrokes(documentPath: String, strokesByPage: Map<Int, List<InkStroke>>) = withContext(Dispatchers.IO) {
        // Delete all existing annotations for this document
        inkAnnotationDao.deleteAllForDocument(documentPath)
        
        // Insert all new annotations
        val entities = strokesByPage.flatMap { (pageNumber, strokes) ->
            strokes.map { stroke -> stroke.toEntity(documentPath, pageNumber) }
        }
        if (entities.isNotEmpty()) {
            inkAnnotationDao.insertAnnotations(entities)
        }
    }
    
    /**
     * Delete a single ink stroke.
     */
    suspend fun deleteInkStroke(strokeId: String) = withContext(Dispatchers.IO) {
        inkAnnotationDao.deleteAnnotationById(strokeId)
    }
    
    /**
     * Load all shape annotations for a document.
     */
    suspend fun loadShapes(documentPath: String): Map<Int, List<ShapeAnnotation>> = withContext(Dispatchers.IO) {
        val entities = shapeAnnotationDao.getAnnotationsForDocument(documentPath)
        entities.groupBy { it.pageNumber }
            .mapValues { (_, pageEntities) ->
                pageEntities.map { entity -> entity.toShapeAnnotation() }
            }
    }
    
    /**
     * Save a single shape annotation.
     */
    suspend fun saveShape(documentPath: String, pageNumber: Int, shape: ShapeAnnotation) = withContext(Dispatchers.IO) {
        val entity = shape.toEntity(documentPath, pageNumber)
        shapeAnnotationDao.insertAnnotation(entity)
    }
    
    /**
     * Save all shape annotations for a document (replaces existing).
     */
    suspend fun saveAllShapes(documentPath: String, shapesByPage: Map<Int, List<ShapeAnnotation>>) = withContext(Dispatchers.IO) {
        // Delete all existing shape annotations for this document
        shapeAnnotationDao.deleteAllForDocument(documentPath)
        
        // Insert all new annotations
        val entities = shapesByPage.flatMap { (pageNumber, shapes) ->
            shapes.map { shape -> shape.toEntity(documentPath, pageNumber) }
        }
        if (entities.isNotEmpty()) {
            shapeAnnotationDao.insertAnnotations(entities)
        }
    }
    
    /**
     * Delete a single shape annotation.
     */
    suspend fun deleteShape(shapeId: String) = withContext(Dispatchers.IO) {
        shapeAnnotationDao.deleteAnnotationById(shapeId)
    }
    
    // ========== Conversion Functions ==========
    
    private fun InkStroke.toEntity(documentPath: String, pageNumber: Int): InkAnnotationEntity {
        val pointsJson = JSONArray().apply {
            points.forEach { point ->
                put(JSONObject().apply {
                    put("x", point.x.toDouble())
                    put("y", point.y.toDouble())
                    put("pressure", point.pressure.toDouble())
                    put("timestamp", point.timestamp)
                })
            }
        }.toString()
        
        return InkAnnotationEntity(
            id = this.id,
            documentPath = documentPath,
            pageNumber = pageNumber,
            pointsJson = pointsJson,
            color = colorToLong(this.color),
            strokeWidth = this.strokeWidth,
            isHighlighter = this.isHighlighter
        )
    }
    
    private fun InkAnnotationEntity.toInkStroke(): InkStroke {
        val pointsArray = JSONArray(this.pointsJson)
        val points = (0 until pointsArray.length()).map { i ->
            val obj = pointsArray.getJSONObject(i)
            StrokePoint(
                x = obj.getDouble("x").toFloat(),
                y = obj.getDouble("y").toFloat(),
                pressure = obj.optDouble("pressure", 1.0).toFloat(),
                timestamp = obj.optLong("timestamp", 0L)
            )
        }
        
        return InkStroke(
            id = this.id,
            points = points,
            color = longToColor(this.color),
            strokeWidth = this.strokeWidth,
            isHighlighter = this.isHighlighter,
            pageNumber = this.pageNumber
        )
    }
    
    private fun ShapeAnnotation.toEntity(documentPath: String, pageNumber: Int): ShapeAnnotationEntity {
        return ShapeAnnotationEntity(
            id = this.id,
            documentPath = documentPath,
            pageNumber = pageNumber,
            shapeType = this.type.name,
            startX = this.startX,
            startY = this.startY,
            endX = this.endX,
            endY = this.endY,
            color = colorToLong(this.color),
            strokeWidth = this.strokeWidth,
            isFilled = this.isFilled
        )
    }
    
    private fun ShapeAnnotationEntity.toShapeAnnotation(): ShapeAnnotation {
        return ShapeAnnotation(
            id = this.id,
            pageNumber = this.pageNumber,
            type = ShapeType.valueOf(this.shapeType),
            startX = this.startX,
            startY = this.startY,
            endX = this.endX,
            endY = this.endY,
            color = longToColor(this.color),
            strokeWidth = this.strokeWidth,
            isFilled = this.isFilled
        )
    }
    
    private fun colorToLong(color: Color): Long {
        val alpha = (color.alpha * 255).toLong()
        val red = (color.red * 255).toLong()
        val green = (color.green * 255).toLong()
        val blue = (color.blue * 255).toLong()
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
    
    private fun longToColor(value: Long): Color {
        val alpha = ((value shr 24) and 0xFF) / 255f
        val red = ((value shr 16) and 0xFF) / 255f
        val green = ((value shr 8) and 0xFF) / 255f
        val blue = (value and 0xFF) / 255f
        return Color(red, green, blue, alpha)
    }
}
