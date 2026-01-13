package com.ospdf.reader.di

import android.content.Context
import androidx.room.Room
import com.ospdf.reader.data.cloud.GoogleDriveAuth
import com.ospdf.reader.data.cloud.GoogleDriveSync
import com.ospdf.reader.data.export.PdfExporter
import com.ospdf.reader.data.local.AnnotationRepository
import com.ospdf.reader.data.local.BookmarkDao
import com.ospdf.reader.data.local.InkAnnotationDao
import com.ospdf.reader.data.local.MiniNoteDao
import com.ospdf.reader.data.local.PdfReaderDatabase
import com.ospdf.reader.data.local.RecentDocumentDao
import com.ospdf.reader.data.local.RecentDocumentsRepository
import com.ospdf.reader.data.local.ShapeAnnotationDao
import com.ospdf.reader.data.local.SyncedDocumentDao
import com.ospdf.reader.data.ocr.OcrEngine
import com.ospdf.reader.data.pdf.AnnotationManager
import com.ospdf.reader.data.pdf.HyperlinkHandler
import com.ospdf.reader.data.pdf.MuPdfRenderer
import com.ospdf.reader.domain.usecase.SearchEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for app-wide dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): PdfReaderDatabase {
        return Room.databaseBuilder(
            context,
            PdfReaderDatabase::class.java,
            "pdf_reader_db"
        ).addMigrations(
            PdfReaderDatabase.MIGRATION_2_3,
            PdfReaderDatabase.MIGRATION_3_4
        )
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
    }
    
    @Provides
    fun provideBookmarkDao(database: PdfReaderDatabase): BookmarkDao {
        return database.bookmarkDao()
    }
    
    @Provides
    fun provideMiniNoteDao(database: PdfReaderDatabase): MiniNoteDao {
        return database.miniNoteDao()
    }
    
    @Provides
    fun provideRecentDocumentDao(database: PdfReaderDatabase): RecentDocumentDao {
        return database.recentDocumentDao()
    }
    
    @Provides
    fun provideInkAnnotationDao(database: PdfReaderDatabase): InkAnnotationDao {
        return database.inkAnnotationDao()
    }
    
    @Provides
    fun provideShapeAnnotationDao(database: PdfReaderDatabase): ShapeAnnotationDao {
        return database.shapeAnnotationDao()
    }
    
    @Provides
    fun provideSyncedDocumentDao(database: PdfReaderDatabase): SyncedDocumentDao {
        return database.syncedDocumentDao()
    }
    
    @Provides
    @Singleton
    fun provideAnnotationRepository(
        inkAnnotationDao: InkAnnotationDao,
        shapeAnnotationDao: ShapeAnnotationDao
    ): AnnotationRepository {
        return AnnotationRepository(inkAnnotationDao, shapeAnnotationDao)
    }

    @Provides
    @Singleton
    fun provideRecentDocumentsRepository(
        recentDocumentDao: RecentDocumentDao
    ): RecentDocumentsRepository {
        return RecentDocumentsRepository(recentDocumentDao)
    }
    
    @Provides
    @Singleton
    fun provideMuPdfRenderer(
        @ApplicationContext context: Context
    ): MuPdfRenderer {
        return MuPdfRenderer(context)
    }
    
    @Provides
    @Singleton
    fun provideAnnotationManager(
        @ApplicationContext context: Context
    ): AnnotationManager {
        return AnnotationManager(context)
    }
    
    @Provides
    @Singleton
    fun provideOcrEngine(
        @ApplicationContext context: Context
    ): OcrEngine {
        return OcrEngine(context)
    }
    
    @Provides
    @Singleton
    fun provideSearchEngine(
        pdfRenderer: MuPdfRenderer
    ): SearchEngine {
        return SearchEngine(pdfRenderer)
    }
    
    @Provides
    @Singleton
    fun provideHyperlinkHandler(
        @ApplicationContext context: Context
    ): HyperlinkHandler {
        return HyperlinkHandler(context)
    }
    
    @Provides
    @Singleton
    fun provideGoogleDriveAuth(
        @ApplicationContext context: Context
    ): GoogleDriveAuth {
        return GoogleDriveAuth(context)
    }
    
    @Provides
    @Singleton
    fun provideGoogleDriveSync(
        auth: GoogleDriveAuth
    ): GoogleDriveSync {
        return GoogleDriveSync(auth)
    }
    
    @Provides
    @Singleton
    fun providePdfExporter(
        @ApplicationContext context: Context,
        annotationManager: AnnotationManager
    ): PdfExporter {
        return PdfExporter(context, annotationManager)
    }
    
    @Provides
    @Singleton
    fun provideSyncRepository(
        @ApplicationContext context: Context,
        syncedDocumentDao: SyncedDocumentDao,
        driveSync: GoogleDriveSync,
        driveAuth: GoogleDriveAuth
    ): com.ospdf.reader.data.sync.SyncRepository {
        return com.ospdf.reader.data.sync.SyncRepository(
            context, syncedDocumentDao, driveSync, driveAuth
        )
    }
}
