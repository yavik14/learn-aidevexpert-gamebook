package com.playbook.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Radios de Playbook: `sm` 8dp, `md` 16dp y `lg` 24dp, con los extremos de la
 * escala M3 alineados a la misma progresión. Toda la UI hereda estos valores vía
 * `MaterialTheme.shapes`.
 */
internal val PlaybookShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
