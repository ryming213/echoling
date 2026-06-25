package com.echoling.app.presentation.ui.theme

import androidx.compose.ui.graphics.Color

// Primary colors - Deep Purple (matches app icon gradient bottom: #7A2BE0)
val Primary = Color(0xFF7C3AED)            // violet-600, deep vibrant purple
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFDDD6FE)   // light purple tint (container for contrast)
val OnPrimaryContainer = Color(0xFF2E1065) // very dark purple text on container

// Secondary colors - deep purple, slightly different hue
val Secondary = Color(0xFF8B5CF6)          // violet-500
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFEDE9FE)
val OnSecondaryContainer = Color(0xFF2E1065)

// Tertiary colors - even deeper purple
val Tertiary = Color(0xFF6D28D9)           // violet-700
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFC4B5FD)
val OnTertiaryContainer = Color(0xFF1E1147)

// Error colors
val Error = Color(0xFFB00020)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFCD8DF)
val OnErrorContainer = Color(0xFF370617)

// Surface colors - Light
val Surface = Color(0xFFFFFBFE)
val OnSurface = Color(0xFF1C1B1F)
val SurfaceVariant = Color(0xFFE7E0EC)
val OnSurfaceVariant = Color(0xFF49454F)
val Outline = Color(0xFF79747E)
val OutlineVariant = Color(0xFFCAC4D0)

// Surface colors - Dark
val SurfaceDark = Color(0xFF1C1B1F)
val OnSurfaceDark = Color(0xFFE6E1E5)
val SurfaceVariantDark = Color(0xFF49454F)
val OnSurfaceVariantDark = Color(0xFFCAC4D0)
val OutlineDark = Color(0xFF938F99)
val OutlineVariantDark = Color(0xFF49454F)

// Background
val Background = Color(0xFFFFFBFE)
val OnBackground = Color(0xFF1C1B1F)
val BackgroundDark = Color(0xFF1C1B1F)
val OnBackgroundDark = Color(0xFFE6E1E5)

// Inverse
val InverseSurface = Color(0xFF313033)
val InverseOnSurface = Color(0xFFF4EFF4)
val InversePrimary = Color(0xFFA78BFA)     // lighter purple for dark theme contrast

// Scrim
val Scrim = Color(0xFF000000)

// Seed color for dynamic theming (kept for reference; not used when dynamicColor=false)
val SeedColor = Color(0xFF7C3AED)
