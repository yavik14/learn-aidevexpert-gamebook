package com.playbook.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.Track
import com.playbook.core.model.normalizeGenres

/**
 * Editor de nota de texto. Sirve para crear (modo `isEditing = false`) y editar
 * (modo `isEditing = true`).
 *
 * - El `body` es multilínea. Guardar se deshabilita si el `body` está vacío **y**
 *   no hay imagen: una nota sólo-imagen es válida.
 * - El `track` es opcional ("Sin track" por defecto); la selección se comunica
 *   con más que color (borde + marca textual), según `DESIGN.md`.
 * - Los Géneros son múltiples y se normalizan ([normalizeGenres]) al agregarlos;
 *   cada uno se muestra como chip con control de quitar "×". El editor todavía es
 *   de **texto libre** (el picker del catálogo llega con `genres-catalog-ui`). No
 *   hay Categoría/Tipo/Nivel: el `track` ya cubre la clasificación fija.
 * - Si hay una [initialImage] se muestra una previsualización y "Quitar imagen".
 * - En modo edición se ofrece "Borrar", con `AlertDialog` de confirmación. La
 *   acción no destructiva es explícita: "Cancelar". Si [onOpenRelated] no es null
 *   (modo edición) también se ofrece "Ver relacionadas".
 * - En modo edición, si llega [initialStatus], se muestra un `StatusBadge`
 *   (sólo lectura, nunca sólo color) y —cuando [onReindex] no es null— el botón
 *   "Re-indexar", habilitado sólo si el estado no es `INDEXED` y el `body` del
 *   borrador no está en blanco (sin texto no hay nada que embeber). Re-indexar
 *   **no** reescribe `track`/`genres`/`body`: sólo dispara el enriquecimiento.
 *
 * La pantalla es "tonta": mantiene el borrador local y emite `onSave`/`onDelete`/
 * `onCancel`/`onRemoveImage`/`onOpenRelated`/`onReindex`; la persistencia, el
 * refresco y la navegación los maneja [App].
 */
@Composable
fun NoteEditorScreen(
    initialBody: String,
    initialTrack: Track?,
    initialGenres: List<String>,
    initialImage: PickedImage?,
    isEditing: Boolean,
    onSave: (body: String, track: Track?, genres: List<String>) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
    onRemoveImage: (() -> Unit)?,
    onOpenRelated: (() -> Unit)? = null,
    initialStatus: NoteStatus? = null,
    onReindex: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var body by remember { mutableStateOf(initialBody) }
    var selectedTrack by remember { mutableStateOf(initialTrack) }
    var genres by remember { mutableStateOf(normalizeGenres(initialGenres)) }
    var newGenre by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val canSave = body.isNotBlank() || initialImage != null

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
        if (isEditing && initialStatus != null) {
            // Estado de indexado de sólo lectura + re-indexado manual. El badge
            // comunica con texto/forma y el botón sólo se habilita si hay algo
            // que embeber (body no vacío) y la Nota no está ya indexada.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(status = initialStatus)
                Spacer(modifier = Modifier.weight(1f))
                if (onReindex != null) {
                    OutlinedButton(
                        onClick = onReindex,
                        enabled = initialStatus != NoteStatus.INDEXED && body.isNotBlank(),
                    ) {
                        Text(text = "Re-indexar")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (initialImage != null) {
            val bitmap = remember(initialImage) { decodeImageBitmap(initialImage.bytes) }
            Text(text = "Imagen adjunta", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Previsualización de la imagen adjunta",
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = "No se pudo previsualizar la imagen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = { onRemoveImage?.invoke() }) {
                Text(text = "Quitar imagen")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
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
        Text(text = "Géneros", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newGenre,
                onValueChange = { newGenre = it },
                label = { Text(text = "Nuevo género") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    genres = normalizeGenres(genres + newGenre)
                    newGenre = ""
                },
                enabled = newGenre.isNotBlank(),
            ) {
                Text(text = "Agregar")
            }
        }
        if (genres.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                genres.forEach { genre ->
                    GenreChip(label = genre, onRemove = { genres = genres - genre })
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (isEditing && onOpenRelated != null) {
            // Fila propia: en pantallas angostas, "Ver relacionadas" junto a
            // Borrar/Cancelar/Guardar empujaba "Guardar" fuera del borde visible.
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onOpenRelated) {
                    Text(text = "Ver relacionadas")
                }
            }
        }
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
                onClick = { onSave(body, selectedTrack, genres) },
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
 * Chip de Género con control de quitar "×". Todo el chip es clickeable para
 * quitarlo, con `onClickLabel` semántico y `role = Role.Button`. Se comunica con
 * texto, no sólo color (`DESIGN.md`).
 */
@Composable
private fun GenreChip(
    label: String,
    onRemove: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.clickable(
            onClickLabel = "Quitar género $label",
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
