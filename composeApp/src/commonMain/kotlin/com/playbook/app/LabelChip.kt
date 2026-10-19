package com.playbook.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playbook.core.model.GenreCatalog

/**
 * Chip textual reutilizable (sin color como única señal, según `DESIGN.md`).
 * Se usa en la lista de notas, el GDD, las relacionadas y las citas del Q&A para
 * `track`, `status`, similitud y el chip "Imagen"/"Audio".
 */
@Composable
internal fun LabelChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * Chip de lectura de un Género. Distingue el **catálogo** (comparación
 * case-insensitive contra [GenreCatalog.GENRES]) del **personalizado** con
 * `primaryContainer` vs `secondaryContainer`; el texto es la señal principal
 * (no sólo color) y se trunca a una línea con ellipsis si no entra.
 */
@Composable
internal fun GenreLabelChip(text: String, modifier: Modifier = Modifier) {
    val isCatalog = GenreCatalog.GENRES.any { it.equals(text, ignoreCase = true) }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (isCatalog) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (isCatalog) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
