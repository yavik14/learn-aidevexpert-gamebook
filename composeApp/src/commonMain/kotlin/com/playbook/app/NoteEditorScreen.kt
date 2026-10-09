package com.playbook.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.playbook.core.model.Track
import com.playbook.core.model.normalizeTags

/**
 * Editor de nota de texto. Sirve para crear (modo `isEditing = false`) y editar
 * (modo `isEditing = true`).
 *
 * - El `body` es multilínea y obligatorio: guardar se deshabilita si está vacío
 *   o sólo con espacios.
 * - El `track` es opcional ("Sin track" por defecto); la selección se comunica
 *   con más que color (borde + marca textual), según `DESIGN.md`.
 * - Las Etiquetas son libres, múltiples y se normalizan ([normalizeTags]) al
 *   agregarlas; cada una se muestra como chip con control de quitar "×". No hay
 *   Categoría/Tipo/Nivel: el `track` ya cubre la clasificación fija.
 * - En modo edición se ofrece "Borrar", con `AlertDialog` de confirmación. La
 *   acción no destructiva es explícita: "Cancelar".
 *
 * La pantalla es "tonta": mantiene el borrador local y emite `onSave`/`onDelete`/
 * `onCancel`; la persistencia y el refresco los maneja [App].
 */
@Composable
fun NoteEditorScreen(
    initialBody: String,
    initialTrack: Track?,
    initialTags: List<String>,
    isEditing: Boolean,
    onSave: (body: String, track: Track?, tags: List<String>) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var body by remember { mutableStateOf(initialBody) }
    var selectedTrack by remember { mutableStateOf(initialTrack) }
    var tags by remember { mutableStateOf(normalizeTags(initialTags)) }
    var newTag by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val canSave = body.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = if (isEditing) "Editar nota" else "Nueva nota",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text(text = "Cuerpo") },
            placeholder = { Text(text = "Escribí tu idea…") },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Track", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrackOption(
                label = "Sin track",
                selected = selectedTrack == null,
                onClick = { selectedTrack = null },
            )
            Track.entries.forEach { track ->
                TrackOption(
                    label = track.code,
                    selected = selectedTrack == track,
                    onClick = { selectedTrack = track },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Etiquetas", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it },
                label = { Text(text = "Nueva etiqueta") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    tags = normalizeTags(tags + newTag)
                    newTag = ""
                },
                enabled = newTag.isNotBlank(),
            ) {
                Text(text = "Agregar")
            }
        }
        if (tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    TagChip(label = tag, onRemove = { tags = tags - tag })
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isEditing && onDelete != null) {
                OutlinedButton(onClick = { showDeleteDialog = true }) {
                    Text(text = "Borrar")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) {
                Text(text = "Cancelar")
            }
            Button(
                onClick = { onSave(body, selectedTrack, tags) },
                enabled = canSave,
            ) {
                Text(text = "Guardar")
            }
        }
    }

    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = "¿Borrar esta nota?") },
            text = { Text(text = "Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text(text = "Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = "Cancelar")
                }
            },
        )
    }
}

/**
 * Opción de `track` seleccionable. La selección se comunica con texto (marca
 * "✓"), grosor de borde y color, no sólo color (`DESIGN.md`).
 */
@Composable
private fun TrackOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/**
 * Chip de Etiqueta con control de quitar "×". Todo el chip es clickeable para
 * quitarlo, con `onClickLabel` semántico y `role = Role.Button`. Se comunica con
 * texto, no sólo color (`DESIGN.md`).
 */
@Composable
private fun TagChip(
    label: String,
    onRemove: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.clickable(
            onClickLabel = "Quitar etiqueta $label",
            role = Role.Button,
            onClick = onRemove,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = "×", style = MaterialTheme.typography.labelMedium)
        }
    }
}
