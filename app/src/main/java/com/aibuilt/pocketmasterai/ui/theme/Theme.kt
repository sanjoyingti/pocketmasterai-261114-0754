package com.aibuilt.pocketmasterai.ui.theme

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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PedalboardPrimary,
    onPrimary = PedalboardOnPrimary,
    primaryContainer = PedalboardPrimaryContainer,
    onPrimaryContainer = PedalboardOnPrimaryContainer,
    secondary = PedalboardSecondary,
    secondaryContainer = PedalboardSecondaryContainer,
    tertiary = Pink80,
    error = PedalboardError,
    onError = PedalboardBackground,
    background = PedalboardBackground,
    onBackground = PedalboardOnSurface,
    surface = PedalboardSurface,
    onSurface = PedalboardOnSurface,
    surfaceVariant = PedalboardSurfaceVariant,
    onSurfaceVariant = PedalboardOnSurfaceVariant,
    outline = PedalboardOutline,
    outlineVariant = PedalboardOutlineVariant,
    inverseSurface = PedalboardSurfaceVariant,
    inverseOnSurface = PedalboardSurface,
)

@Composable
fun PocketMasterTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> lightColorScheme(
            primary = Purple40,
            onPrimary = Purple80,
            primaryContainer = Purple80,
            onPrimaryContainer = Purple40,
            secondary = PurpleGrey40,
            secondaryContainer = PurpleGrey80,
            tertiary = Pink40,
            onError = PedalboardError,
            background = PedalboardBackground,
            onBackground = PedalboardOnSurface,
            surface = PedalboardSurface,
            onSurface = PedalboardOnSurface,
            surfaceVariant = PedalboardSurfaceVariant,
            onSurfaceVariant = PedalboardOnSurfaceVariant,
            outline = PedalboardOutline,
            outlineVariant = PedalboardOutlineVariant,
            inverseSurface = PedalboardSurfaceVariant,
            inverseOnSurface = PedalboardSurface,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
