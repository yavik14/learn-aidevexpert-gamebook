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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playbook.core.gdd.GddSection
import com.playbook.core.gdd.buildGdd
import com.playbook.core.gdd.buildGddMarkdown
import com.playbook.core.model.Note
import kotlinx.coroutines.launch

/**
 * Vista de GDD: proyección agregada de las Notas agrupadas por `track`, sin
 * persistencia propia. Es render fino sobre [buildGdd] (la agrupación vive en
 * `:core`, cubierta por tests cross-platform).
 *
 * - Estado vacío explícito ("Todavía no hay notas") sin secciones.
 * - Encabezado con el total de notas y los botones "Volver" y **"Compartir"**
 *   (este último deshabilitado si no hay Notas).
 * - Una sección por `track` (`mecánicas`/`personajes`/`historia`) y, si hay
 *   notas sin `track`, "Sin track" al final; cada sección muestra su conteo.
 * - Cada nota resume el cuerpo (máx. 2 líneas) y muestra chips textuales de
 *   `status`, Géneros y "Imagen" (si tiene adjuntos). Toda la información se
 *   comunica con texto, no sólo color (`DESIGN.md`).
 * - Si la Nota tiene un Adjunto `IMAGE`, la tarjeta suma su **miniatura**
 *   ([imagePaths] + [loadThumbnail]); si el archivo falta o no decodifica, no
 *   emite imagen y el resto de la tarjeta no cambia.
 * - Tocar una nota emite `onOpenNote(note.id)`: `App` la abre en el editor.
 *
 * "Compartir" construye el Markdown con [buildGddMarkdown] (timestamp de
 * plataforma) y lo entrega a [GddSharer]; el resultado se comunica con un
 * snackbar ("Listo para compartir"/"No disponible") sin salir de la pantalla.
 *
 * La pantalla es "tonta": recibe `notes`/`attachmentCounts` y sólo emite
 * `onOpenNote`/`onBack`. El [GddSharer] se obtiene con [rememberGddSharer]
 * (sin parámetro nuevo, por lo que `App` no cambia). Sin DI, `ViewModel`, Flow ni
 * librería de navegación.
 */
@Composable
fun GddScreen(
    notes: List<Note>,
    attachmentCounts: Map<String, Int>,
    imagePaths: Map<String, String>,
    loadThumbnail: suspend (String) -> ImageBitmap?,
    onOpenNote: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = remember(notes) { buildGdd(notes) }
    val sharer = rememberGddSharer()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text(text = "Volver")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        val markdown = buildGddMarkdown(notes, currentTimestampLabel())
                        val shared = sharer.share(markdown)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = if (shared) "Listo para compartir" else "No disponible",
                            )
                        }
                    },
                    enabled = notes.isNotEmpty(),
                ) {
                    Text(text = "Compartir")
                }
            }
            Text(text = "GDD", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notesSummary(notes.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (notes.isEmpty()) {
                EmptyGdd(modifier = Modifier.fillMaxWidth().weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    sections.forEach { section ->
                        item(key = sectionKey(section)) {
                            SectionHeader(section = section)
                        }
                        if (section.notes.isEmpty()) {
                            item(key = "${sectionKey(section)}-empty") {
                                Text(
                                    text = "Sin notas en este track",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        } else {
                            items(section.notes, key = { it.id }) { note ->
                                GddNoteCard(
                                    note = note,
                                    hasAttachment = (attachmentCounts[note.id] ?: 0) > 0,
                                    imagePath = imagePaths[note.id],
                                    loadThumbnail = loadThumbnail,
                                    onClick = { onOpenNote(note.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGdd(modifier: Modifier = Modifier) {
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
            text = "Tus ideas capturadas van a aparecer acá, agrupadas por track.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SectionHeader(section: GddSection) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = section.track?.code ?: "Sin track",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = notesSummary(section.notes.size),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun GddNoteCard(
    note: Note,
    hasAttachment: Boolean,
    imagePath: String?,
    loadThumbnail: suspend (String) -> ImageBitmap?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Abrir nota", role = Role.Button, onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (imagePath != null) {
                NoteThumbnail(path = imagePath, loadThumbnail = loadThumbnail)
            }
            Text(
                text = note.body.ifBlank { "Imagen adjunta" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelChip(text = note.status.code)
                if (hasAttachment) {
                    LabelChip(text = "Imagen")
                }
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

private fun notesSummary(count: Int): String =
    "$count ${if (count == 1) "nota" else "notas"}"

private fun sectionKey(section: GddSection): String = section.track?.name ?: "no-track"
