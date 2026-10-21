package com.playbook.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Theme Playbook: **sólo modo claro**, sin `isSystemInDarkTheme` ni
 * `dynamicColor`. Es el único `MaterialTheme` de la app y todos los destinos
 * (pantallas, diálogos y snackbars) lo heredan desde [com.playbook.app.App].
 */
@Composable
fun PlaybookTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPlaybookColors provides PlaybookExtraColors) {
        MaterialTheme(
            colorScheme = PlaybookLightColors,
            typography = playbookTypography(),
            shapes = PlaybookShapes,
            content = content,
        )
    }
}

/** Tokens extra de Playbook (acción/track/estado/scrim). */
val LocalPlaybookColors = staticCompositionLocalOf { PlaybookExtraColors }

/** Azúcar para leer los tokens extra desde cualquier composable. */
val MaterialTheme.playbookColors: PlaybookColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPlaybookColors.current

internal val PlaybookLightColors = lightColorScheme(
    primary = PlaybookPalette.ActionTeal,
    onPrimary = PlaybookPalette.Surface,
    primaryContainer = PlaybookPalette.Sky,
    onPrimaryContainer = PlaybookPalette.Ink,
    inversePrimary = PlaybookPalette.Sky,
    secondary = PlaybookPalette.Brown,
    onSecondary = PlaybookPalette.Surface,
    secondaryContainer = PlaybookPalette.Yellow,
    onSecondaryContainer = PlaybookPalette.Ink,
    tertiary = PlaybookPalette.Yellow,
    onTertiary = PlaybookPalette.Ink,
    tertiaryContainer = PlaybookPalette.Yellow,
    onTertiaryContainer = PlaybookPalette.Ink,
    background = PlaybookPalette.Base,
    onBackground = PlaybookPalette.Ink,
    surface = PlaybookPalette.Surface,
    onSurface = PlaybookPalette.Ink,
    surfaceVariant = PlaybookPalette.SurfaceVariant,
    onSurfaceVariant = PlaybookPalette.OnSurfaceVariant,
    surfaceTint = PlaybookPalette.ActionTeal,
    surfaceDim = PlaybookPalette.SurfaceDim,
    surfaceBright = PlaybookPalette.SurfaceBright,
    surfaceContainerLowest = PlaybookPalette.SurfaceContainerLowest,
    surfaceContainerLow = PlaybookPalette.SurfaceContainerLow,
    surfaceContainer = PlaybookPalette.SurfaceContainer,
    surfaceContainerHigh = PlaybookPalette.SurfaceContainerHigh,
    surfaceContainerHighest = PlaybookPalette.SurfaceContainerHighest,
    inverseSurface = PlaybookPalette.Ink,
    inverseOnSurface = PlaybookPalette.Base,
    error = PlaybookPalette.Error,
    onError = PlaybookPalette.Surface,
    errorContainer = PlaybookPalette.ErrorContainer,
    onErrorContainer = PlaybookPalette.OnErrorContainer,
    outline = PlaybookPalette.Outline,
    outlineVariant = PlaybookPalette.OutlineVariant,
    scrim = PlaybookPalette.Ink,
)
