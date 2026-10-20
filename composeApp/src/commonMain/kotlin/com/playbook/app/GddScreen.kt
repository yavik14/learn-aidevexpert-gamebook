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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.playbook.core.gdd.GddDocumentSection
import com.playbook.core.gdd.GddProjectBlock
import com.playbook.core.gdd.buildGddDocument
import com.playbook.core.gdd.buildGddNarration
import com.playbook.core.gdd.buildGddMarkdown
import com.playbook.core.model.GameProject
import com.playbook.core.model.Note
import kotlinx.coroutines.launch

/**
 * Vista del GDD como **documento híbrido** del Proyecto: ficha estructurada
 * arriba y Notas agrupadas por `track` debajo, sin persistencia propia. Es render
 * fino sobre [buildGddDocument] (la proyección vive en `:core`, cubierta por
 * tests cross-platform).
 *
 * - Cabecera fija `GDD — <título>` (o sólo "GDD") con el total de Notas y los
 *   botones "Volver" y **"Compartir"** (habilitado con Notas o con ficha con
 *   título/campos; deshabilitado sólo sin contenido exportable).
 * - Ficha en una `Card` destacada con encabezado "Proyecto": campos verticales
 *   (label arriba, valor debajo) y estados explícitos "Proyecto sin datos
 *   adicionales" / "Todavía no hay Proyecto".
 * - Una sección por `track` (`mecánicas`/`personajes`/`historia`) y, si hay
 *   notas sin `track`, "Sin track" al final; cada sección muestra su conteo.
 * - Sin Notas: se muestra la ficha (si existe) y "Todavía no hay notas", sin
 *   secciones de Track vacías.
 * - Cada nota resume el cuerpo (máx. 2 líneas) y muestra chips textuales de
 *   `status`, Géneros y "Imagen" (si tiene adjuntos). Toda la información se
 *   comunica con texto, no sólo color (`DESIGN.md`).
 * - Si la Nota tiene un Adjunto `IMAGE`, la tarjeta suma su **miniatura**
 *   ([imagePaths] + [loadThumbnail]); si el archivo falta o no decodifica, no
 *   emite imagen y el resto de la tarjeta no cambia.
 * - Tocar una nota emite `onOpenNote(note.id)`: `App` la abre en el editor.
 *
 * "Compartir" construye el Markdown híbrido con [buildGddMarkdown] (ficha del
 * Proyecto + Notas, timestamp de plataforma) y lo entrega a [GddSharer]; se
 * habilita con Notas o con contenido en la ficha. El resultado se comunica con un
 * snackbar ("Listo para compartir"/"No disponible") sin salir de la pantalla.
 *
 * La pantalla es "tonta": recibe `notes`/`project`/`attachmentCounts` y sólo
 * emite `onOpenNote`/`onBack`. Sin DI, `ViewModel`, Flow ni librería de
 * navegación.
 */
@Composable
fun GddScreen(
    notes: List<Note>,
    project: GameProject?,
    attachmentCounts: Map<String, Int>,
    imagePaths: Map<String, String>,
    loadThumbnail: suspend (String) -> ImageBitmap?,
    onOpenNote: (String) -> Unit,
    onBack: () -> Unit,
    readAloudState: ReadAloudUiState = ReadAloudUiState.Detenido,
    onReadAloud: (String) -> Unit = {},
    onStopReadAloud: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val document = remember(project, notes) { buildGddDocument(project, notes) }
    val narration = remember(project, notes) { buildGddNarration(project, notes) }
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
                ReadAloudButton(
                    surface = ReadAloudSurface.GDD,
                    surfaceLabel = "GDD",
                    state = readAloudState,
                    enabled = narration.isNotBlank(),
                    onRead = { onReadAloud(narration) },
                    onStop = onStopReadAloud,
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val markdown = buildGddMarkdown(
                            project = project,
                            notes = notes,
                            now = currentTimestampLabel(),
                        )
                        val shared = sharer.share(markdown)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = if (shared) "Listo para compartir" else "No disponible",
                            )
                        }
                    },
                    enabled = notes.isNotEmpty() ||
                        document.project?.let { it.title != null || it.fields.isNotEmpty() } == true,
                ) {
                    Text(text = "Compartir")
                }
            }
            Text(
                text = gddTitle(document.project?.title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notesSummary(notes.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "project") {
                    ProjectCard(project = document.project)
                }
                if (notes.isEmpty()) {
                    item(key = "empty-notes") {
                        EmptyGdd()
                    }
                } else {
                    document.sections.forEach { section ->
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
private fun ProjectCard(project: GddProjectBlock?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Proyecto",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            when {
                project == null -> {
                    Text(
                        text = "Todavía no hay Proyecto",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Completalo desde Brainstorming.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                project.fields.isEmpty() -> Text(
                    text = "Proyecto sin datos adicionales",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> project.fields.forEachIndexed { index, field ->
                    if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = field.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = field.value,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGdd(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
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
private fun SectionHeader(section: GddDocumentSection) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = section.title,
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

private fun gddTitle(projectTitle: String?): String =
    projectTitle?.let { "GDD — $it" } ?: "GDD"

private fun notesSummary(count: Int): String =
    "$count ${if (count == 1) "nota" else "notas"}"

private fun sectionKey(section: GddDocumentSection): String =
    section.track?.name ?: "no-track"
