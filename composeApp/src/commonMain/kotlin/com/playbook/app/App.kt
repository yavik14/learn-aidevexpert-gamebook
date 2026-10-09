package com.playbook.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft
import com.playbook.core.repository.NoteRepository

/** Owner único del MVP: lo provee la UI al construir cada [NoteDraft]. */
private const val LOCAL_OWNER_ID = "local"

/** Destino de navegación local (sin librería de navegación). */
sealed interface NotesDestination {
    data object List : NotesDestination
    data object Create : NotesDestination
    data class Edit(val noteId: String) : NotesDestination
}

/**
 * Raíz de la UI compartida y dueña del estado de la lista.
 *
 * La lectura vive en un [LaunchedEffect], nunca en el cuerpo de composición.
 * Tras cada mutación se incrementa `refreshKey` (`reload()`), lo que dispara una
 * relectura y refleja los cambios sin reiniciar la app. Sigue sin DI,
 * `ViewModel`/`Lifecycle`, coroutines propias ni Flow: sólo estado de Compose y
 * el `NoteRepository` síncrono recibido por parámetro.
 */
@Composable
fun App(noteRepository: NoteRepository) {
    MaterialTheme {
        var notes by remember { mutableStateOf(emptyList<Note>()) }
        var destination by remember { mutableStateOf<NotesDestination>(NotesDestination.List) }
        var refreshKey by remember { mutableStateOf(0) }

        LaunchedEffect(refreshKey) {
            notes = noteRepository.getAll()
        }

        fun reload() {
            refreshKey++
        }

        fun goToList() {
            destination = NotesDestination.List
        }

        when (val current = destination) {
            NotesDestination.List -> NotesListScreen(
                notes = notes,
                onCreate = { destination = NotesDestination.Create },
                onEdit = { noteId -> destination = NotesDestination.Edit(noteId) },
            )

            NotesDestination.Create -> NoteEditorScreen(
                initialBody = "",
                initialTrack = null,
                initialTags = emptyList(),
                isEditing = false,
                onSave = { body, track, tags ->
                    noteRepository.create(
                        NoteDraft(
                            owner = LOCAL_OWNER_ID,
                            body = body.trim(),
                            track = track,
                            tags = tags,
                        ),
                    )
                    goToList()
                    reload()
                },
                onDelete = null,
                onCancel = ::goToList,
            )

            is NotesDestination.Edit -> {
                val note = notes.firstOrNull { it.id == current.noteId }
                if (note == null) {
                    // La nota ya no existe (p. ej. se borró): volver a la lista.
                    LaunchedEffect(current.noteId) { goToList() }
                } else {
                    NoteEditorScreen(
                        initialBody = note.body,
                        initialTrack = note.track,
                        initialTags = note.tags,
                        isEditing = true,
                        onSave = { body, track, tags ->
                            noteRepository.update(
                                note.copy(body = body.trim(), track = track, tags = tags),
                            )
                            goToList()
                            reload()
                        },
                        onDelete = {
                            noteRepository.delete(note.id)
                            goToList()
                            reload()
                        },
                        onCancel = ::goToList,
                    )
                }
            }
        }
    }
}
