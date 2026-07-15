package com.echoling.app.presentation.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    inversePrimary = InversePrimary,
    scrim = Scrim
)

private val DarkColorScheme = darkColorScheme(
    primary = InversePrimary,
    onPrimary = OnPrimaryContainer,
    primaryContainer = Primary,
    onPrimaryContainer = OnPrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = Surface,
    inverseOnSurface = OnSurface,
    inversePrimary = Primary,
    scrim = Scrim
)

@Composable
fun EchoLingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Use static light-purple theme by default so the app matches the
    // app-icon gradient on all Android versions (Material You dynamic
    // colors would otherwise pick a blue tint from the user's wallpaper).
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // (2026-06-28) Do NOT set `window.statusBarColor` here. We
            // call `enableEdgeToEdge()` in MainActivity which already
            // makes the status bar transparent and configures the
            // appropriate scrim. Re-painting it with the surface color
            // undoes the edge-to-edge setup and makes the status bar
            // look like a separate "band" at the top of the screen,
            // visually separated from the page content below — the
            // user reports the page is not "adjacent to the top of
            // the phone screen", but in fact the content IS correctly
            // positioned right below the status bar inset; it's the
            // opaque status bar background that creates the visible
            // step. Letting the bar stay transparent lets the
            // activity's Surface (drawn by `setContent`) fill the
            // status-bar area too, so the user sees one continuous
            // color from the very top of the screen down to the
            // body content, with the system icons floating on top.
            //
            // We still configure the icon tint (dark icons on light
            // backgrounds, light icons on dark backgrounds) — that's
            // the only thing that's safe to set after enableEdgeToEdge.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
