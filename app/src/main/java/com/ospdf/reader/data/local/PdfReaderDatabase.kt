package com.ospdf.reader.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Entity representing an ink stroke annotation in a PDF document.
 */
@Entity(
    tableName = "ink_annotations",
    indices = [Index(value = ["document_path", "page_number"])]
)
data class InkAnnotationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "document_path")
    val documentPath: String,
    
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    
    @ColumnInfo(name = "points_json")
    val pointsJson: String, // JSON serialized list of points
    
    @ColumnInfo(name = "color")
    val color: Long, // Color as ARGB long
    
    @ColumnInfo(name = "stroke_width")
    val strokeWidth: Float,
    
    @ColumnInfo(name = "is_highlighter")
    val isHighlighter: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Entity representing a shape annotation in a PDF document.
 */
@Entity(
    tableName = "shape_annotations",
    indices = [Index(value = ["document_path", "page_number"])]
)
data class ShapeAnnotationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "document_path")
    val documentPath: String,
    
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    
    @ColumnInfo(name = "shape_type")
    val shapeType: String, // LINE, RECTANGLE, CIRCLE, ARROW
    
    @ColumnInfo(name = "start_x")
    val startX: Float,
    
    @ColumnInfo(name = "start_y")
    val startY: Float,
    
    @ColumnInfo(name = "end_x")
    val endX: Float,
    
    @ColumnInfo(name = "end_y")
    val endY: Float,
    
    @ColumnInfo(name = "color")
    val color: Long,
    
    @ColumnInfo(name = "stroke_width")
    val strokeWidth: Float,
    
    @ColumnInfo(name = "is_filled")
    val isFilled: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Entity representing a custom bookmark in a PDF document.
 */
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["document_path", "page_number"])]
)
data class BookmarkEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "document_path")
    val documentPath: String,
    
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "note")
    val note: String = "",
    
    @ColumnInfo(name = "color")
    val color: Int = 0xFF5722, // Default orange
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "scroll_position")
    val scrollPosition: Float = 0f
)

/**
 * Entity representing a mini note attached to a location in a PDF.
 */
@Entity(
    tableName = "mini_notes",
    indices = [Index(value = ["document_path", "page_number"])]
)
data class MiniNoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "document_path")
    val documentPath: String,
    
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    
    @ColumnInfo(name = "x_position")
    val xPosition: Float,
    
    @ColumnInfo(name = "y_position")
    val yPosition: Float,
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "color")
    val color: Int = 0xFFEB3B, // Default yellow
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "is_expanded")
    val isExpanded: Boolean = false
)

/**
 * Entity representing a recently opened document.
 */
@Entity(
    tableName = "recent_documents",
    indices = [Index(value = ["path"], unique = true)]
)
data class RecentDocumentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "path")
    val path: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "last_page")
    val lastPage: Int = 0,
    
    @ColumnInfo(name = "total_pages")
    val totalPages: Int = 0,
    
    @ColumnInfo(name = "last_opened")
    val lastOpened: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null
)

/**
 * DAO for bookmark operations.
 */
@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE document_path = :documentPath ORDER BY page_number ASC")
    fun getBookmarksForDocument(documentPath: String): Flow<List<BookmarkEntity>>
    
    @Query("SELECT * FROM bookmarks WHERE document_path = :documentPath AND page_number = :pageNumber")
    suspend fun getBookmarkForPage(documentPath: String, pageNumber: Int): BookmarkEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)
    
    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
    
    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmarkById(bookmarkId: String)
    
    @Update
    suspend fun updateBookmark(bookmark: BookmarkEntity)
}

/**
 * DAO for mini notes operations.
 */
@Dao
interface MiniNoteDao {
    @Query("SELECT * FROM mini_notes WHERE document_path = :documentPath ORDER BY page_number ASC")
    fun getNotesForDocument(documentPath: String): Flow<List<MiniNoteEntity>>
    
    @Query("SELECT * FROM mini_notes WHERE document_path = :documentPath AND page_number = :pageNumber")
    fun getNotesForPage(documentPath: String, pageNumber: Int): Flow<List<MiniNoteEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: MiniNoteEntity)
    
    @Delete
    suspend fun deleteNote(note: MiniNoteEntity)
    
    @Update
    suspend fun updateNote(note: MiniNoteEntity)
}

/**
 * DAO for recent documents.
 */
@Dao
interface RecentDocumentDao {
    @Query("SELECT * FROM recent_documents ORDER BY last_opened DESC LIMIT 20")
    fun getRecentDocuments(): Flow<List<RecentDocumentEntity>>
    
    @Query("SELECT * FROM recent_documents WHERE path = :path")
    suspend fun getDocumentByPath(path: String): RecentDocumentEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: RecentDocumentEntity)
    
    @Delete
    suspend fun deleteDocument(document: RecentDocumentEntity)
    
    @Query("DELETE FROM recent_documents WHERE path = :path")
    suspend fun deleteByPath(path: String)
}

/**
 * DAO for ink annotation operations.
 */
@Dao
interface InkAnnotationDao {
    @Query("SELECT * FROM ink_annotations WHERE document_path = :documentPath ORDER BY page_number ASC, created_at ASC")
    suspend fun getAnnotationsForDocument(documentPath: String): List<InkAnnotationEntity>
    
    @Query("SELECT * FROM ink_annotations WHERE document_path = :documentPath AND page_number = :pageNumber ORDER BY created_at ASC")
    suspend fun getAnnotationsForPage(documentPath: String, pageNumber: Int): List<InkAnnotationEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: InkAnnotationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(annotations: List<InkAnnotationEntity>)
    
    @Delete
    suspend fun deleteAnnotation(annotation: InkAnnotationEntity)
    
    @Query("DELETE FROM ink_annotations WHERE id = :annotationId")
    suspend fun deleteAnnotationById(annotationId: String)
    
    @Query("DELETE FROM ink_annotations WHERE document_path = :documentPath")
    suspend fun deleteAllForDocument(documentPath: String)
    
    @Query("DELETE FROM ink_annotations WHERE document_path = :documentPath AND page_number = :pageNumber")
    suspend fun deleteAllForPage(documentPath: String, pageNumber: Int)
}

/**
 * DAO for shape annotation operations.
 */
@Dao
interface ShapeAnnotationDao {
    @Query("SELECT * FROM shape_annotations WHERE document_path = :documentPath ORDER BY page_number ASC, created_at ASC")
    suspend fun getAnnotationsForDocument(documentPath: String): List<ShapeAnnotationEntity>
    
    @Query("SELECT * FROM shape_annotations WHERE document_path = :documentPath AND page_number = :pageNumber ORDER BY created_at ASC")
    suspend fun getAnnotationsForPage(documentPath: String, pageNumber: Int): List<ShapeAnnotationEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: ShapeAnnotationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(annotations: List<ShapeAnnotationEntity>)
    
    @Delete
    suspend fun deleteAnnotation(annotation: ShapeAnnotationEntity)
    
    @Query("DELETE FROM shape_annotations WHERE id = :annotationId")
    suspend fun deleteAnnotationById(annotationId: String)
    
    @Query("DELETE FROM shape_annotations WHERE document_path = :documentPath")
    suspend fun deleteAllForDocument(documentPath: String)
}

/**
 * Room database for the PDF reader application.
 */
@Database(
    entities = [
        BookmarkEntity::class,
        MiniNoteEntity::class,
        RecentDocumentEntity::class,
        InkAnnotationEntity::class,
        ShapeAnnotationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PdfReaderDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun miniNoteDao(): MiniNoteDao
    abstract fun recentDocumentDao(): RecentDocumentDao
    abstract fun inkAnnotationDao(): InkAnnotationDao
    abstract fun shapeAnnotationDao(): ShapeAnnotationDao
}
