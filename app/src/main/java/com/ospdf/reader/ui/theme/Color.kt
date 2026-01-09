package com.ospdf.reader.ui.theme

import androidx.compose.ui.graphics.Color

// Primary - Deep Blue
val Primary = Color(0xFF2563EB)
val PrimaryVariant = Color(0xFF1D4ED8)
val OnPrimary = Color.White

// Secondary - Warm Orange
val Secondary = Color(0xFFF59E0B)
val SecondaryVariant = Color(0xFFD97706)
val OnSecondary = Color.Black

// Light Theme
val BackgroundLight = Color(0xFFFAFAFA)
val SurfaceLight = Color.White
val OnBackgroundLight = Color(0xFF1F2937)
val OnSurfaceLight = Color(0xFF1F2937)
val SurfaceVariantLight = Color(0xFFF3F4F6)

// Dark Theme
val BackgroundDark = Color(0xFF111827)
val SurfaceDark = Color(0xFF1F2937)
val OnBackgroundDark = Color(0xFFF9FAFB)
val OnSurfaceDark = Color(0xFFF9FAFB)
val SurfaceVariantDark = Color(0xFF374151)

// AMOLED Dark Theme (true black)
val BackgroundAmoled = Color(0xFF000000)
val SurfaceAmoled = Color(0xFF0D0D0D)
val SurfaceVariantAmoled = Color(0xFF1A1A1A)

// High Contrast (Accessibility)
val HighContrastBackground = Color.White
val HighContrastSurface = Color.White
val HighContrastOnBackground = Color.Black
val HighContrastOnSurface = Color.Black
val HighContrastPrimary = Color(0xFF0000DD)

// Error
val Error = Color(0xFFDC2626)
val OnError = Color.White

// Ink Colors for annotations
object InkColors {
    val Black = Color(0xFF000000)
    val Blue = Color(0xFF2563EB)
    val Red = Color(0xFFDC2626)
    val Green = Color(0xFF059669)
    val Yellow = Color(0xFFFBBF24)
    val Orange = Color(0xFFF97316)
    val Purple = Color(0xFF7C3AED)
    val White = Color(0xFFFFFFFF)
    
    val all = listOf(Black, Blue, Red, Green, Yellow, Orange, Purple)
    val allDark = listOf(White, Blue, Red, Green, Yellow, Orange, Purple) // For dark backgrounds
}

// Highlighter Colors (with transparency)
object HighlighterColors {
    val Yellow = Color(0x80FBBF24)
    val Green = Color(0x8034D399)
    val Blue = Color(0x8060A5FA)
    val Pink = Color(0x80F472B6)
    val Orange = Color(0x80FB923C)
    
    val all = listOf(Yellow, Green, Blue, Pink, Orange)
}

// Reading mode colors
object ReadingColors {
    val Sepia = Color(0xFFF5F0E6)
    val Night = Color(0xFF1A1A1A)
    val DarkGreen = Color(0xFF0D1F14)
}
