package com.playbook.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playbook.core.model.GenreCatalog
import com.playbook.core.model.normalizeGenres

/**
 * Selector de Géneros de una Nota.
 *
 * - "Seleccionados": chips removibles (`×`) del conjunto actual, en el orden ya
 *   normalizado recibido.
 * - "Catálogo": los 12 Géneros de [GenreCatalog.GENRES], siempre visibles y en
 *   orden de catálogo. Tocar uno lo agrega o lo quita (toggle).
 * - "Género personalizado": campo libre + "Agregar"; se habilita sólo si el texto
 *   no está en blanco. No hay límites de cantidad ni de largo.
 *
 * Toda alta y remoción pasa por [normalizeGenres] (trim, canonización
 * case-insensitive contra el catálogo, dedupe y orden), de modo que, p. ej.,
 * escribir `"jrpg"` produce `"JRPG"` sin duplicar el chip del catálogo.
 *
 * La selección se comunica con más que color (`DESIGN.md`): `✓`, borde de 2.dp y
 * estado semántico `selected` en el chip del catálogo. Los chips interactivos
 * usan etiquetas de acción ("Agregar género X"/"Quitar género X"), `role =
 * Role.Button` y un objetivo táctil de al menos 48.dp. Las etiquetas largas se
 * truncan visualmente a una línea con ellipsis; el dato persistido no se recorta.
 */
@Composable
internal fun GenrePicker(
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newGenre by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "Géneros", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        if (selected.isNotEmpty()) {
            Text(text = "Seleccionados", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selected.forEach { genre ->
                    SelectedGenreChip(
                        label = genre,
                        onRemove = {
                            onChange(
                                normalizeGenres(
                                    selected.filterNot { it.equals(genre, ignoreCase = true) },
                                ),
                            )
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(text = "Catálogo", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GenreCatalog.GENRES.forEach { genre ->
                val isSelected = selected.any { it.equals(genre, ignoreCase = true) }
                CatalogGenreChip(
                    label = genre,
                    selected = isSelected,
                    onClick = {
                        onChange(
                            if (isSelected) {
                                normalizeGenres(
                                    selected.filterNot { it.equals(genre, ignoreCase = true) },
                                )
                            } else {
                                normalizeGenres(selected + genre)
                            },
                        )
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newGenre,
                onValueChange = { newGenre = it },
                label = { Text(text = "Género personalizado") },
                placeholder = { Text(text = "Escribí uno nuevo…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onChange(normalizeGenres(selected + newGenre))
                    newGenre = ""
                },
                enabled = newGenre.isNotBlank(),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(text = "Agregar")
            }
        }
    }
}

/**
 * Chip del catálogo con toggle. Seleccionado: `✓`, borde de 2.dp y estado
 * semántico `selected`; no seleccionado: etiqueta simple. Usa `primaryContainer`
 * como proxy de `chip-genre` (`DESIGN.md`).
 */
@Composable
private fun CatalogGenreChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics { this.selected = selected }
            .clickable(
                onClickLabel = if (selected) "Quitar género $label" else "Agregar género $label",
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/**
 * Chip de un Género ya elegido: se quita con `×`. Catálogo y personalizado
 * conservan sus variantes de color (`primaryContainer` vs
 * `secondaryContainer`) para mantener consistencia con las superficies de
 * lectura.
 */
@Composable
private fun SelectedGenreChip(
    label: String,
    onRemove: () -> Unit,
) {
    val isCatalog = GenreCatalog.GENRES.any { it.equals(label, ignoreCase = true) }
    Surface(
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                onClickLabel = "Quitar género $label",
                role = Role.Button,
                onClick = onRemove,
            )
            .semantics { this.selected = true },
    ) {
        Text(
            text = "$label ×",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
