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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playbook.core.linking.RelatedNote
import com.playbook.core.model.Note
import kotlin.math.abs
import kotlin.math.round

/**
 * Pantalla "Notas relacionadas": vista de lectura de los Enlaces Nota↔Nota que
 * `semantic-linking` persiste. Es render fino sobre [RelatedNote] (la
 * resolución/orden vive en `:core`, cubierta por tests cross-platform): sólo
 * emite [onOpenNote]/[onBack].
 *
 * - Encabezado "Notas relacionadas" y el cuerpo de la Nota de origen (máx. 2
 *   líneas, ellipsis).
 * - Estado vacío explícito ("No hay notas relacionadas") con una línea de apoyo.
 * - Con resultados, un `LazyColumn` de tarjetas: el cuerpo de la relacionada
 *   (máx. 2 líneas), el `score` como **texto** ("Similitud 0.92", nunca sólo
 *   color) y chips textuales de `track`/`status`/Géneros, según `DESIGN.md`.
 * - Tocar una tarjeta emite `onOpenNote(note.id)`; "Volver" vuelve a la lista.
 */
@Composable
fun RelatedNotesScreen(
    note: Note,
    related: List<RelatedNote>,
    onOpenNote: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(text = "Volver")
            }
        }
        Text(text = "Notas relacionadas", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = note.body.ifBlank { "Sin texto" },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (related.isEmpty()) {
            EmptyRelatedNotes(modifier = Modifier.fillMaxWidth().weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(related, key = { it.note.id }) { relatedNote ->
                    RelatedNoteCard(
                        relatedNote = relatedNote,
                        onClick = { onOpenNote(relatedNote.note.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRelatedNotes(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No hay notas relacionadas",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Cuando otras Notas se parezcan a esta, las verás aquí.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RelatedNoteCard(relatedNote: RelatedNote, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Abrir nota", role = Role.Button, onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = relatedNote.note.body.ifBlank { "Sin texto" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelChip(text = "Similitud ${formatScore(relatedNote.score)}")
                LabelChip(text = relatedNote.note.track?.code ?: "Sin track")
                LabelChip(text = relatedNote.note.status.code)
            }
            if (relatedNote.note.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    relatedNote.note.genres.forEach { genre ->
                        GenreLabelChip(text = genre)
                    }
                }
            }
        }
    }
}

/**
 * Formatea el `score` (coseno en `[-1, 1]`) con dos decimales de forma
 * determinista y **sin depender del locale** de la plataforma (a diferencia de
 * `String.format`, que no existe en Kotlin common y varía por plataforma): p. ej.
 * `0.92`.
 */
private fun formatScore(score: Double): String {
    val hundredths = round(score * 100.0).toLong()
    val sign = if (hundredths < 0) "-" else ""
    val magnitude = abs(hundredths)
    return "$sign${magnitude / 100}.${(magnitude % 100).toString().padStart(2, '0')}"
}
