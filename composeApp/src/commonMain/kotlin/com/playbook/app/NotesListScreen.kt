package com.playbook.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playbook.core.model.AttachmentKind
import com.playbook.core.model.Note

/**
 * Pantalla principal Playbook: notas locales con el shell definido en `DESIGN.md`.
 *
 * - `TopAppBar`: "Playbook" + acciones GDD, Brainstorming y Preguntar (cada una con
 *   un único `contentDescription`). Sin `material-icons` en `commonMain`, las
 *   acciones usan glifos textuales (`GDD`, `✦`, `?`) como fallback accesible.
 * - FAB desplegable: Nota de texto, Galería y Cámara. El menú se cierra al elegir
 *   una acción, al tocar el scrim o con Back; el scrim no entra en accesibilidad.
 * - Galería abre el diálogo "Añadir imagen" existente (que conserva Galería,
 *   Cámara y "Generar con Playbook"); Cámara lanza la captura nativa directo.
 * - El dictado ya no vive acá: sólo existe dentro del editor de Nueva nota.
 *
 * Se conservan la lista, el estado vacío, las tarjetas, miniaturas, chips y
 * badges. El refresco y la navegación los maneja [App]: esta pantalla sólo emite
 * intenciones.
 */
@Composable
fun NotesListScreen(
    notes: List<Note>,
    attachmentKinds: Map<String, Set<AttachmentKind>>,
    imagePaths: Map<String, String>,
    loadThumbnail: suspend (String) -> ImageBitmap?,
    onCreate: () -> Unit,
    onOpenImageSource: () -> Unit,
    onCaptureCamera: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenGdd: () -> Unit,
    onAsk: () -> Unit,
    onOpenBrainstorming: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var captureMenuExpanded by remember { mutableStateOf(false) }
    PlaybookBackHandler(enabled = captureMenuExpanded) { captureMenuExpanded = false }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            PlaybookTopBar(
                onOpenGdd = onOpenGdd,
                onOpenBrainstorming = onOpenBrainstorming,
                onAsk = onAsk,
            )
        },
        floatingActionButton = {
            CaptureSpeedDial(
                expanded = captureMenuExpanded,
                onExpandedChange = { captureMenuExpanded = it },
                onTextNote = {
                    captureMenuExpanded = false
                    onCreate()
                },
                onGallery = {
                    captureMenuExpanded = false
                    onOpenImageSource()
                },
                onCamera = {
                    captureMenuExpanded = false
                    onCaptureCamera()
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                if (notes.isEmpty()) {
                    EmptyNotes(
                        onCreate = onCreate,
                        onOpenImageSource = onOpenImageSource,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(notes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                attachmentKinds = attachmentKinds[note.id].orEmpty(),
                                imagePath = imagePaths[note.id],
                                loadThumbnail = loadThumbnail,
                                onClick = { onEdit(note.id) },
                            )
                        }
                    }
                }
            }
            if (captureMenuExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .pointerInput(Unit) {
                            detectTapGestures { captureMenuExpanded = false }
                        }
                        .clearAndSetSemantics { },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybookTopBar(
    onOpenGdd: () -> Unit,
    onOpenBrainstorming: () -> Unit,
    onAsk: () -> Unit,
) {
    TopAppBar(
        title = { Text(text = "Playbook") },
        actions = {
            PlaybookTopBarAction(label = "Ver GDD", glyph = "GDD", onClick = onOpenGdd)
            PlaybookTopBarAction(
                label = "Brainstorming",
                glyph = "✦",
                onClick = onOpenBrainstorming,
            )
            PlaybookTopBarAction(label = "Preguntar", glyph = "?", onClick = onAsk)
        },
    )
}

@Composable
private fun PlaybookTopBarAction(
    label: String,
    glyph: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = label
        },
    ) {
        IconButton(onClick = onClick) {
            Text(
                text = glyph,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun CaptureSpeedDial(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTextNote: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (expanded) {
            SpeedDialAction(label = "Nota de texto", onClick = onTextNote)
            SpeedDialAction(
                label = "Galería",
                description = "Galería y generar imagen con Playbook",
                onClick = onGallery,
            )
            SpeedDialAction(label = "Cámara", onClick = onCamera)
        }
        Box(
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = if (expanded) {
                    "Cerrar menú de captura"
                } else {
                    "Nueva nota"
                }
            },
        ) {
            FloatingActionButton(onClick = { onExpandedChange(!expanded) }) {
                Text(
                    text = if (expanded) "×" else "+",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

@Composable
private fun SpeedDialAction(
    label: String,
    onClick: () -> Unit,
    description: String = label,
) {
    Box(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        },
    ) {
        ExtendedFloatingActionButton(onClick = onClick) {
            Text(
                text = label,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun EmptyNotes(
    onCreate: () -> Unit,
    onOpenImageSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Todavía no hay notas",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tus ideas capturadas aparecerán aquí.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreate) {
                Text(text = "Crear primera nota")
            }
            OutlinedButton(onClick = onOpenImageSource) {
                Text(text = "Añadir imagen")
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: Note,
    attachmentKinds: Set<AttachmentKind>,
    imagePath: String?,
    loadThumbnail: suspend (String) -> ImageBitmap?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Editar nota", role = Role.Button, onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (imagePath != null) {
                NoteThumbnail(path = imagePath, loadThumbnail = loadThumbnail)
            }
            Text(
                text = note.body.ifBlank { placeholderFor(attachmentKinds) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Chips de texto por tipo (orden estable: Imagen, luego Audio).
                AttachmentKind.entries.filter { it in attachmentKinds }.forEach { kind ->
                    LabelChip(text = attachmentLabel(kind))
                }
                LabelChip(text = note.track?.code ?: "Sin track")
                StatusBadge(status = note.status)
            }
            if (note.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    note.genres.forEach { genre ->
                        GenreLabelChip(text = genre)
                    }
                }
            }
        }
    }
}

/** Etiqueta textual del tipo de Adjunto (la UI nunca comunica sólo con color). */
private fun attachmentLabel(kind: AttachmentKind): String = when (kind) {
    AttachmentKind.IMAGE -> "Imagen"
    AttachmentKind.AUDIO -> "Audio"
}

/** Placeholder del cuerpo vacío según el/los Adjunto(s) presentes. */
private fun placeholderFor(attachmentKinds: Set<AttachmentKind>): String = when {
    AttachmentKind.IMAGE in attachmentKinds -> "Imagen adjunta"
    AttachmentKind.AUDIO in attachmentKinds -> "Audio adjunto"
    else -> "Sin texto"
}
