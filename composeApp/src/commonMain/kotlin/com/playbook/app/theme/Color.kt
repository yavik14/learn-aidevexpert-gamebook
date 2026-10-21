package com.playbook.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Paleta Playbook (sólo modo claro) derivada de `docs/design/paleta.png`.
 *
 * Este es el **único** archivo de la app con literales `Color(0x…)`; el resto de
 * la UI consume `MaterialTheme.colorScheme` o los tokens de [PlaybookColors].
 * Los derivados de acción/estado se usan para forma, borde o relleno: el texto
 * siempre se apoya en un par con contraste AA (ver `PlaybookContrastTest`).
 */
internal object PlaybookPalette {
    /** Tinta cálida: texto principal y fondo del scrim. */
    val Ink = Color(0xFF2C2926)

    /** Teal de acción (botón primario, FAB, selección). */
    val ActionTeal = Color(0xFF0E4B54)

    /** Celeste de acento (chips de catálogo, contenedores). */
    val Sky = Color(0xFF98E4FF)

    /** Amarillo de realce (chips de género libre, contenedores secundarios). */
    val Yellow = Color(0xFFFFE17D)

    /** Papel neutro de fondo. */
    val Base = Color(0xFFF9F6F8)

    /** Superficie de tarjetas y diálogos. */
    val Surface = Color(0xFFFFFFFF)

    /** Marrón cálido para acciones alternativas y el track de personajes. */
    val Brown = Color(0xFF6B4A2A)

    /** Oliva para advertencia/pendiente (relleno y borde). */
    val Olive = Color(0xFF7A7A2E)

    /** Oliva oscuro para texto sobre base (6.2:1, AA). */
    val OliveText = Color(0xFF5F5F22)

    /** Rojo de error/estado fallido. */
    val Error = Color(0xFFC0392B)
    val ErrorContainer = Color(0xFFF7D9D5)
    val OnErrorContainer = Color(0xFF7A1F16)

    /** Neutros derivados: variantes y bordes. */
    val SurfaceVariant = Color(0xFFEFEAF0)
    val OnSurfaceVariant = Color(0xFF4A4440)
    val Outline = Color(0xFF6F6862)
    val OutlineVariant = Color(0xFFD8D2D6)

    /** Rampa de superficies M3 derivada de base/superficie (sin violeta default). */
    val SurfaceDim = Color(0xFFDED8DC)
    val SurfaceBright = Color(0xFFFFFFFF)
    val SurfaceContainerLowest = Color(0xFFFFFFFF)
    val SurfaceContainerLow = Color(0xFFFCFAFC)
    val SurfaceContainer = Color(0xFFF9F6F8)
    val SurfaceContainerHigh = Color(0xFFF2EDF1)
    val SurfaceContainerHighest = Color(0xFFEFEAF0)

    /** Scrim de tinta al 32 % sobre el contenido (menú de captura). */
    val Scrim = Color(0x522C2926)
}

/**
 * Tokens extra que Material 3 no expone: acciones, tracks y estados. Los
 * colores de track/estado son para forma/borde/relleno, nunca texto sin AA.
 */
@Immutable
data class PlaybookColors(
    val actionPrimary: Color,
    val actionAlt: Color,
    val actionWarn: Color,
    val actionWarnText: Color,
    val trackMechanics: Color,
    val trackCharacters: Color,
    val trackStory: Color,
    val statusCaptured: Color,
    val statusPending: Color,
    val statusIndexed: Color,
    val statusFailed: Color,
    val scrim: Color,
)

internal val PlaybookExtraColors = PlaybookColors(
    actionPrimary = PlaybookPalette.ActionTeal,
    actionAlt = PlaybookPalette.Brown,
    actionWarn = PlaybookPalette.Olive,
    actionWarnText = PlaybookPalette.OliveText,
    trackMechanics = PlaybookPalette.ActionTeal,
    trackCharacters = PlaybookPalette.Brown,
    trackStory = PlaybookPalette.Olive,
    statusCaptured = PlaybookPalette.OnSurfaceVariant,
    statusPending = PlaybookPalette.Olive,
    statusIndexed = PlaybookPalette.ActionTeal,
    statusFailed = PlaybookPalette.Error,
    scrim = PlaybookPalette.Scrim,
)
