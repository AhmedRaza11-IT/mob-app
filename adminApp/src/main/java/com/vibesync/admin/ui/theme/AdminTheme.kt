package com.vibesync.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Slate Gray Scale (matching web dashboard Tailwind slate palette)
val Slate950 = Color(0xFF020617)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

// Core Brand Colors (SaaS Product Palette)
val BrandPurple = Color(0xFF7C3AED)        // Violet 600
val BrandPurpleHover = Color(0xFF6D28D9)   // Violet 700
val BrandPurpleLight = Color(0xFFEDE9FE)   // Violet 100
val BrandPurpleSurface = Color(0xFFF5F3FF) // Violet 50
val BrandTeal = Color(0xFF0D9488)          // Teal 600
val BrandTealLight = Color(0xFFCCFBF1)     // Teal 100

// Status & Semantic Highlights (Light Theme Pill Tones)
val EmeraldSuccess = Color(0xFF059669)     // Emerald 600
val EmeraldBg = Color(0xFFECFDF5)          // Emerald 50
val EmeraldBorder = Color(0xFFA7F3D0)      // Emerald 200

val AmberWarning = Color(0xFFD97706)       // Amber 600
val AmberBg = Color(0xFFFFFBEB)            // Amber 50
val AmberBorder = Color(0xFFFDE68A)        // Amber 200

val RoseDanger = Color(0xFFE11D48)         // Rose 600
val RoseBg = Color(0xFFFFF1F2)             // Rose 50
val RoseBorder = Color(0xFFFECDD3)         // Rose 200

val RedDelete = Color(0xFFDC2626)          // Red 600
val RedBg = Color(0xFFFEF2F2)              // Red 50
val RedBorder = Color(0xFFFECACA)          // Red 200

val SkyInfo = Color(0xFF0284C7)            // Sky 600
val SkyBg = Color(0xFFF0F9FF)              // Sky 50
val SkyBorder = Color(0xFFBAE6FD)          // Sky 200

private val AdminLightColorScheme = lightColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = BrandPurpleSurface,
    onPrimaryContainer = BrandPurpleHover,
    secondary = BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = BrandTealLight,
    onSecondaryContainer = Color(0xFF115E59),
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate200,
    outlineVariant = Slate300,
    error = RedDelete,
    onError = Color.White
)

@Composable
fun AdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AdminLightColorScheme,
        content = content
    )
}
