package com.playbook.app.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.playbook.app.resources.Res
import com.playbook.app.resources.bricolage_grotesque_regular
import com.playbook.app.resources.bricolage_grotesque_semibold
import com.playbook.app.resources.jetbrains_mono_medium
import com.playbook.app.resources.jetbrains_mono_regular
import org.jetbrains.compose.resources.Font

/**
 * Tipografía Playbook (único archivo con tamaños `.sp` de la app).
 *
 * - **Bricolage Grotesque** (Regular/SemiBold) para `display*`, `headline*` y
 *   `title*`. Peso de títulos: SemiBold 600 (desviación documentada desde 700:
 *   sólo se versionan Regular y SemiBold).
 * - **JetBrains Mono** (Regular/Medium) para `body*` y `label*` (incluye
 *   botones, chips y badges, que usan `labelLarge`/`labelMedium`).
 *
 * La escala base es 34/16/13 (headline/body/label) con alturas de línea
 * proporcionales; el tamaño del sistema (font scale) sigue aplicando porque
 * todos los valores están en `sp`.
 */
@Composable
internal fun playbookTypography(): Typography {
    val display = FontFamily(
        Font(Res.font.bricolage_grotesque_regular, FontWeight.Normal),
        Font(Res.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
    )
    val mono = FontFamily(
        Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
    )

    return Typography(
        displayLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 40.sp,
            lineHeight = 48.sp,
        ),
        displayMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 34.sp,
            lineHeight = 42.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            lineHeight = 38.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 34.sp,
            lineHeight = 40.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
    )
}
