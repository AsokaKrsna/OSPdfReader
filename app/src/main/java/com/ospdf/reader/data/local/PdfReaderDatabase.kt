package com.ospdf.reader.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

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
 * Room database for the PDF reader application.
 */
@Database(
    entities = [
        BookmarkEntity::class,
        MiniNoteEntity::class,
        RecentDocumentEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PdfReaderDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun miniNoteDao(): MiniNoteDao
    abstract fun recentDocumentDao(): RecentDocumentDao
}
