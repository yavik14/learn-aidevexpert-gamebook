package com.playbook.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.playbook.core.model.GenreCatalog
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.Track
import com.playbook.core.model.normalizeGenres

/**
 * Editor de nota de texto. Sirve para crear (modo `isEditing = false`) y editar
 * (modo `isEditing = true`).
 *
 * - El `body` es multilínea. Guardar se deshabilita si el `body` está vacío **y**
 *   no hay imagen: una nota sólo-imagen es válida. También se deshabilita
 *   mientras hay un texto dictado pendiente de anexarse (`dictatedText != null`),
 *   para no guardar el audio sin su texto.
 * - En **Nueva nota** (`!isEditing`) se muestra un panel de dictado inline
 *   (estado [DictationUiState], progreso de 30s y parcial en vivo). El texto
 *   final llega en [dictatedText], se anexa una sola vez con [appendDictation] y
 *   se avisa con [onDictatedTextConsumed]; la sesión y el audio pendiente viven
 *   en [App]. En edición el panel no existe.
 * - El `track` es opcional ("Sin track" por defecto); la selección se comunica
 *   con más que color (borde + marca textual), según `DESIGN.md`.
 * - Los Géneros se eligen del [GenreCatalog] fijo (toggle de chips) o se escriben
 *   como personalizados en [GenrePicker]; el conjunto inicial y toda alta/remoción
 *   pasan por [normalizeGenres]. No hay Categoría/Tipo/Nivel: el `track` ya cubre
 *   la clasificación fija.
 * - El contenido es scrollable y la fila Borrar/Cancelar/Guardar queda fija al
 *   pie: el picker puede crecer sin ocultar las acciones y el cuerpo conserva una
 *   altura mínima usable.
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
    dictationState: DictationUiState = DictationUiState.Idle,
    dictatedText: String? = null,
    onStartDictation: (() -> Unit)? = null,
    onStopDictation: (() -> Unit)? = null,
    onDictatedTextConsumed: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var body by remember { mutableStateOf(initialBody) }
    var selectedTrack by remember { mutableStateOf(initialTrack) }
    var genres by remember { mutableStateOf(normalizeGenres(initialGenres)) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // El texto final del dictado se anexa una sola vez. Mientras esté pendiente,
    // Guardar queda deshabilitado para no persistir el audio sin su texto.
    LaunchedEffect(dictatedText) {
        val dictated = dictatedText
        if (!dictated.isNullOrBlank()) {
            body = appendDictation(body, dictated)
            onDictatedTextConsumed?.invoke(dictated)
        }
    }

    val canSave =
        (body.isNotBlank() || initialImage != null) && dictatedText == null

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 320.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (!isEditing && onStartDictation != null && onStopDictation != null) {
                DictationPanel(
                    state = dictationState,
                    onStart = onStartDictation,
                    onStop = onStopDictation,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
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
            GenrePicker(selected = genres, onChange = { genres = it })
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
        }
        Spacer(modifier = Modifier.height(16.dp))
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
 * Panel de dictado inline de Nueva nota: `Idle` invita a dictar; `Listening`
 * muestra la barra de progreso (`elapsed / 30s`), el parcial en vivo y "Detener";
 * `Processing` deja la acción deshabilitada; `Error` muestra el mensaje textual y
 * "Reintentar". Todos los estados se comunican con texto, no sólo color.
 */
@Composable
private fun DictationPanel(
    state: DictationUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "Dictado", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        when (state) {
            DictationUiState.Idle -> {
                Button(onClick = onStart) {
                    Text(text = "Dictar")
                }
            }

            is DictationUiState.Listening -> {
                LinearProgressIndicator(
                    progress = {
                        (state.elapsedMillis.toFloat() / DICTATION_MAX_MILLIS)
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Escuchando… (${state.elapsedMillis / 1000}s / 30s)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.partial.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.partial,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onStop) {
                    Text(text = "Detener")
                }
            }

            DictationUiState.Processing -> {
                Button(onClick = {}, enabled = false) {
                    Text(text = "Transcribiendo…")
                }
            }

            is DictationUiState.Error -> {
                Text(
                    text = state.error.message(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onStart) {
                    Text(text = "Reintentar")
                }
            }
        }
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
