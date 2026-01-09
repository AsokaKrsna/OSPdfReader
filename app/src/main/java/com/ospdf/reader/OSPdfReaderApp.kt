package com.ospdf.reader

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * OSPdfReader Application class.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class OSPdfReaderApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize any app-wide configurations here
    }
}
