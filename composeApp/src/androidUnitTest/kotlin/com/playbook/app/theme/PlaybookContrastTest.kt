package com.playbook.app.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifica el contraste WCAG de los pares de color que usa la app: ≥ 4.5:1 para
 * texto y ≥ 3:1 para elementos no textuales (bordes/indicadores). Los tokens de
 * track/estado no se usan como texto; por eso no entran en los pares de texto.
 */
class PlaybookContrastTest {
    @Test
    fun textPairsMeetAa() {
        val pairs = mapOf(
            "Ink/Base" to (PlaybookPalette.Ink to PlaybookPalette.Base),
            "Ink/Surface" to (PlaybookPalette.Ink to PlaybookPalette.Surface),
            "White/ActionTeal" to (PlaybookPalette.Surface to PlaybookPalette.ActionTeal),
            "Ink/Sky" to (PlaybookPalette.Ink to PlaybookPalette.Sky),
            "Ink/Yellow" to (PlaybookPalette.Ink to PlaybookPalette.Yellow),
            "Error/Surface" to (PlaybookPalette.Error to PlaybookPalette.Surface),
            "OnSurfaceVariant/SurfaceVariant" to (
                PlaybookPalette.OnSurfaceVariant to PlaybookPalette.SurfaceVariant
                ),
            "Brown/Surface" to (PlaybookPalette.Brown to PlaybookPalette.Surface),
            "OnErrorContainer/ErrorContainer" to (
                PlaybookPalette.OnErrorContainer to PlaybookPalette.ErrorContainer
                ),
            "Ink/SurfaceContainerHigh" to (
                PlaybookPalette.Ink to PlaybookPalette.SurfaceContainerHigh
                ),
            "OliveText/Base" to (PlaybookPalette.OliveText to PlaybookPalette.Base),
        )
        pairs.forEach { (name, pair) ->
            val ratio = contrast(pair.first, pair.second)
            assertTrue(ratio >= 4.5, "$name debe ser ≥ 4.5:1 (fue $ratio)")
        }
    }

    @Test
    fun nonTextPairsMeetAa() {
        val pairs = mapOf(
            "Outline/Surface" to (PlaybookPalette.Outline to PlaybookPalette.Surface),
            "ActionTeal/Surface" to (PlaybookPalette.ActionTeal to PlaybookPalette.Surface),
            "ActionTeal/Base" to (PlaybookPalette.ActionTeal to PlaybookPalette.Base),
            "Olive/Surface" to (PlaybookPalette.Olive to PlaybookPalette.Surface),
            "Error/Surface" to (PlaybookPalette.Error to PlaybookPalette.Surface),
        )
        pairs.forEach { (name, pair) ->
            val ratio = contrast(pair.first, pair.second)
            assertTrue(ratio >= 3.0, "$name debe ser ≥ 3:1 (fue $ratio)")
        }
    }

    @Test
    fun scrimIsInkAtThirtyTwoPercent() {
        // 0x52 = 82/255 ≈ 32 % de opacidad sobre tinta.
        val alpha = PlaybookPalette.Scrim.alpha
        assertTrue(alpha in 0.31f..0.33f, "El scrim debe ser tinta al 32 % (fue $alpha)")
        assertTrue(
            PlaybookPalette.Scrim.red == PlaybookPalette.Ink.red &&
                PlaybookPalette.Scrim.green == PlaybookPalette.Ink.green &&
                PlaybookPalette.Scrim.blue == PlaybookPalette.Ink.blue,
            "El scrim debe usar la tinta de la paleta",
        )
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return ((lighter + 0.05) / (darker + 0.05) * 100).toInt() / 100.0
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
}
