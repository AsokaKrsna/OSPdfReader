# Add project specific ProGuard rules here.

# Keep MuPDF native methods
-keep class com.artifex.mupdf.fitz.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# Keep Google API classes
-keep class com.google.api.** { *; }
-keep class com.google.auth.** { *; }

# Keep Tesseract4Android classes
-keep class com.googlecode.tesseract.android.** { *; }
-keep class cz.adaptech.tesseract4android.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Serialization
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Don't warn about missing classes from optional dependencies
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
