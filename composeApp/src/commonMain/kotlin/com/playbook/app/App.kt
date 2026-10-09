package com.playbook.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.playbook.app.voice.VoiceCaptureError
import com.playbook.app.voice.VoiceTranscriber
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
    data object VoiceCapture : NotesDestination
}

/**
 * Raíz de la UI compartida y dueña del estado de la lista y del dictado.
 *
 * La lectura vive en un [LaunchedEffect], nunca en el cuerpo de composición.
 * Tras cada mutación se incrementa `refreshKey` (`reload()`), lo que dispara una
 * relectura y refleja los cambios sin reiniciar la app. Sigue sin DI,
 * `ViewModel`/`Lifecycle`, coroutines propias ni Flow: sólo estado de Compose y
 * las dependencias recibidas por parámetro.
 *
 * La captura por voz usa el puerto [VoiceTranscriber]: la sesión se inicia sólo
 * al tocar "Grabar" (para que el permiso sea explícito), el texto final se
 * persiste como `body` de una Nota (`track = null`, `status = CAPTURED`) por el
 * mismo camino que el texto, y al salir del destino se libera el micrófono.
 */
@Composable
fun App(noteRepository: NoteRepository, voiceTranscriber: VoiceTranscriber) {
    MaterialTheme {
        var notes by remember { mutableStateOf(emptyList<Note>()) }
        var destination by remember { mutableStateOf<NotesDestination>(NotesDestination.List) }
        var refreshKey by remember { mutableStateOf(0) }
        var voiceState by remember { mutableStateOf<VoiceUiState>(VoiceUiState.Idle) }

        LaunchedEffect(refreshKey) {
            notes = noteRepository.getAll()
        }

        fun reload() {
            refreshKey++
        }

        fun goToList() {
            destination = NotesDestination.List
        }

        fun startVoiceCapture() {
            voiceState = VoiceUiState.Listening(partial = "")
            voiceTranscriber.start(
                onPartialResult = { partial ->
                    voiceState = VoiceUiState.Listening(partial = partial)
                },
                onFinalResult = { text ->
                    val trimmed = text.trim()
                    if (trimmed.isBlank()) {
                        voiceState = VoiceUiState.Error(VoiceCaptureError.NO_MATCH)
                    } else {
                        noteRepository.create(
                            NoteDraft(owner = LOCAL_OWNER_ID, body = trimmed, track = null),
                        )
                        voiceState = VoiceUiState.Idle
                        goToList()
                        reload()
                    }
                },
                onError = { error ->
                    voiceState = VoiceUiState.Error(error)
                },
            )
        }

        fun stopVoiceCapture() {
            voiceState = VoiceUiState.Processing
            voiceTranscriber.stop()
        }

        fun cancelVoiceCapture() {
            voiceTranscriber.cancel()
            voiceState = VoiceUiState.Idle
            goToList()
        }

        when (val current = destination) {
            NotesDestination.List -> NotesListScreen(
                notes = notes,
                onCreate = { destination = NotesDestination.Create },
                onEdit = { noteId -> destination = NotesDestination.Edit(noteId) },
                onDictate = {
                    voiceState = VoiceUiState.Idle
                    destination = NotesDestination.VoiceCapture
                },
            )

            NotesDestination.Create -> NoteEditorScreen(
                initialBody = "",
                initialTrack = null,
                isEditing = false,
                onSave = { body, track ->
                    noteRepository.create(
                        NoteDraft(
                            owner = LOCAL_OWNER_ID,
                            body = body.trim(),
                            track = track,
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
                        isEditing = true,
                        onSave = { body, track ->
                            noteRepository.update(note.copy(body = body.trim(), track = track))
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

            NotesDestination.VoiceCapture -> {
                // Al salir del destino (cancelar o tras crear la Nota) se libera el
                // micrófono. Es idempotente respecto de `cancelVoiceCapture()`.
                DisposableEffect(Unit) {
                    onDispose { voiceTranscriber.cancel() }
                }
                VoiceCaptureScreen(
                    state = voiceState,
                    onStart = ::startVoiceCapture,
                    onStop = ::stopVoiceCapture,
                    onCancel = ::cancelVoiceCapture,
                )
            }
        }
    }
}
