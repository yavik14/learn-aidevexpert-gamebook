package com.playbook.app

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playbook.core.model.AttachmentKind
import com.playbook.core.model.Note

/**
 * Lista de notas locales. Permite crear una nota de texto, capturar/adjuntar una
 * imagen, dictar una nota de voz ("Dictar nota"), abrir una tarjeta para
 * editarla y abrir la vista de GDD ("Ver GDD"). El refresco tras la mutación lo
 * maneja [App]: esta pantalla sólo emite intenciones.
 *
 * El estado vacío y el estado con notas se manejan de forma explícita. El
 * `track` y el `status` se muestran como texto (nunca sólo con color), según la
 * regla de accesibilidad de `DESIGN.md`; las notas con adjuntos muestran chips
 * textuales por tipo ("Imagen" y/o "Audio").
 */
@Composable
fun NotesListScreen(
    notes: List<Note>,
    attachmentKinds: Map<String, Set<AttachmentKind>>,
    onCreate: () -> Unit,
    onCaptureImage: () -> Unit,
    onEdit: (String) -> Unit,
    onDictate: () -> Unit,
    onOpenGdd: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(onClick = onCreate) {
                    Text(text = "Nueva nota")
                }
                ExtendedFloatingActionButton(onClick = onCaptureImage) {
                    Text(text = "Foto")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
        ) {
            Text(text = "Notas", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onDictate,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "Dictar nota")
                }
                FilledTonalButton(
                    onClick = onOpenGdd,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "Ver GDD")
                }
                FilledTonalButton(
                    onClick = onAsk,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "Preguntar")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (notes.isEmpty()) {
                EmptyNotes(
                    onCreate = onCreate,
                    onCaptureImage = onCaptureImage,
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
                            onClick = { onEdit(note.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotes(
    onCreate: () -> Unit,
    onCaptureImage: () -> Unit,
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
            text = "Tus ideas capturadas van a aparecer acá.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreate) {
                Text(text = "Crear primera nota")
            }
            OutlinedButton(onClick = onCaptureImage) {
                Text(text = "Agregar foto")
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: Note,
    attachmentKinds: Set<AttachmentKind>,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Editar nota", role = Role.Button, onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
            if (note.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    note.tags.forEach { tag ->
                        LabelChip(text = tag)
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
