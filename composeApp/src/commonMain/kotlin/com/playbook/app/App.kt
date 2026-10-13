package com.playbook.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.playbook.app.voice.VoiceCaptureError
import com.playbook.app.voice.VoiceTranscriber
import com.playbook.core.model.Attachment
import com.playbook.core.model.AttachmentKind
import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.Track
import com.playbook.core.repository.AttachmentRepository
import com.playbook.core.repository.NoteRepository

/** Owner único del MVP: lo provee la UI al construir cada [NoteDraft]. */
private const val LOCAL_OWNER_ID = "local"

/** Destino de navegación local (sin librería de navegación). */
sealed interface NotesDestination {
    data object List : NotesDestination
    data object Create : NotesDestination
    data class Edit(val noteId: String) : NotesDestination
    data object VoiceCapture : NotesDestination

    /** Vista de GDD: proyección de las Notas agrupadas por `track`. */
    data object Gdd : NotesDestination

    /** Creación con una imagen recién capturada/elegida, todavía en memoria. */
    data class CreateWithImage(val image: PickedImage) : NotesDestination
}

/**
 * Raíz de la UI compartida y dueña del estado de la lista, del dictado y de las
 * acciones de captura de imagen.
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
fun App(
    noteRepository: NoteRepository,
    attachmentRepository: AttachmentRepository,
    voiceTranscriber: VoiceTranscriber,
) {
    MaterialTheme {
        var notes by remember { mutableStateOf(emptyList<Note>()) }
        var attachments by remember { mutableStateOf(emptyList<Attachment>()) }
        var destination by remember { mutableStateOf<NotesDestination>(NotesDestination.List) }
        var refreshKey by remember { mutableStateOf(0) }
        var voiceState by remember { mutableStateOf<VoiceUiState>(VoiceUiState.Idle) }
        var showImageSourceDialog by remember { mutableStateOf(false) }
        var imageError by remember { mutableStateOf<ImageCaptureError?>(null) }

        val imagePicker = rememberImagePicker(
            onResult = { picked -> destination = NotesDestination.CreateWithImage(picked) },
            onError = { error -> imageError = error },
        )
        val fileStore = rememberAttachmentFileStore()

        LaunchedEffect(refreshKey) {
            notes = noteRepository.getAll()
            attachments = attachmentRepository.getAll()
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
                onFinalResult = { text, audio ->
                    val trimmed = text.trim()
                    if (trimmed.isBlank()) {
                        // Sin transcripción no hay Nota: descartar el audio grabado.
                        audio?.let { fileStore.delete(it.relativePath) }
                        voiceState = VoiceUiState.Error(VoiceCaptureError.NO_MATCH)
                    } else {
                        val note = noteRepository.create(
                            NoteDraft(owner = LOCAL_OWNER_ID, body = trimmed, track = null),
                        )
                        if (audio != null) {
                            try {
                                attachmentRepository.create(
                                    noteId = note.id,
                                    kind = AttachmentKind.AUDIO,
                                    mimeType = audio.mimeType,
                                    filePath = audio.relativePath,
                                    byteSize = audio.byteSize,
                                )
                            } catch (_: Throwable) {
                                // Degradación tolerante: conservar la Nota sin
                                // Adjunto y no dejar el archivo huérfano.
                                fileStore.delete(audio.relativePath)
                            }
                        }
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

        /** Guarda el archivo, luego la nota y el adjunto; limpia si algo falla. */
        fun saveNoteWithImage(image: PickedImage, body: String, track: Track?, tags: List<String>) {
            val stored = fileStore.saveImage(image.bytes, image.mimeType)
            try {
                val note = noteRepository.create(
                    NoteDraft(
                        owner = LOCAL_OWNER_ID,
                        body = body.trim(),
                        track = track,
                        tags = tags,
                    ),
                )
                attachmentRepository.create(
                    noteId = note.id,
                    kind = AttachmentKind.IMAGE,
                    mimeType = image.mimeType,
                    filePath = stored.relativePath,
                    byteSize = stored.byteSize,
                )
            } catch (_: Throwable) {
                // Nada quedó persistido (o la nota se creó sin adjunto): no dejar
                // el archivo recién guardado huérfano.
                fileStore.delete(stored.relativePath)
            }
            goToList()
            reload()
        }

        val attachmentKinds = attachments
            .groupBy({ it.noteId }, { it.kind })
            .mapValues { (_, kinds) -> kinds.toSet() }
        val attachmentCounts = attachmentKinds.mapValues { (_, kinds) -> kinds.size }

        when (val current = destination) {
            NotesDestination.List -> NotesListScreen(
                notes = notes,
                attachmentKinds = attachmentKinds,
                onCreate = { destination = NotesDestination.Create },
                onCaptureImage = { showImageSourceDialog = true },
                onEdit = { noteId -> destination = NotesDestination.Edit(noteId) },
                onDictate = {
                    voiceState = VoiceUiState.Idle
                    destination = NotesDestination.VoiceCapture
                },
                onOpenGdd = { destination = NotesDestination.Gdd },
            )

            NotesDestination.Gdd -> GddScreen(
                notes = notes,
                attachmentCounts = attachmentCounts,
                onOpenNote = { noteId -> destination = NotesDestination.Edit(noteId) },
                onBack = ::goToList,
            )

            NotesDestination.Create -> NoteEditorScreen(
                initialBody = "",
                initialTrack = null,
                initialTags = emptyList(),
                initialImage = null,
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
                onRemoveImage = null,
            )

            is NotesDestination.CreateWithImage -> NoteEditorScreen(
                initialBody = "",
                initialTrack = null,
                initialTags = emptyList(),
                initialImage = current.image,
                isEditing = false,
                onSave = { body, track, tags ->
                    saveNoteWithImage(current.image, body, track, tags)
                },
                onDelete = null,
                onCancel = ::goToList,
                onRemoveImage = { destination = NotesDestination.Create },
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
                        initialImage = null,
                        isEditing = true,
                        onSave = { body, track, tags ->
                            noteRepository.update(
                                note.copy(body = body.trim(), track = track, tags = tags),
                            )
                            goToList()
                            reload()
                        },
                        onDelete = {
                            // Las filas `attachment` se borran dentro del
                            // repositorio de notas; los archivos se borran acá.
                            val noteAttachments = attachmentRepository.getByNoteId(note.id)
                            noteRepository.delete(note.id)
                            noteAttachments.forEach { fileStore.delete(it.filePath) }
                            goToList()
                            reload()
                        },
                        onCancel = ::goToList,
                        onRemoveImage = null,
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

        if (showImageSourceDialog) {
            AlertDialog(
                onDismissRequest = { showImageSourceDialog = false },
                title = { Text(text = "Agregar imagen") },
                text = {
                    Column {
                        TextButton(
                            onClick = {
                                showImageSourceDialog = false
                                imagePicker.launch(ImageSource.CAMERA)
                            },
                        ) {
                            Text(text = "Cámara")
                        }
                        TextButton(
                            onClick = {
                                showImageSourceDialog = false
                                imagePicker.launch(ImageSource.GALLERY)
                            },
                        ) {
                            Text(text = "Galería")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showImageSourceDialog = false }) {
                        Text(text = "Cancelar")
                    }
                },
            )
        }

        imageError?.let { error ->
            AlertDialog(
                onDismissRequest = { imageError = null },
                title = { Text(text = "No se pudo agregar la imagen") },
                text = { Text(text = imageErrorMessage(error)) },
                confirmButton = {
                    TextButton(onClick = { imageError = null }) {
                        Text(text = "Aceptar")
                    }
                },
            )
        }
    }
}

private fun imageErrorMessage(error: ImageCaptureError): String = when (error) {
    ImageCaptureError.PERMISSION_DENIED ->
        "Permiso de cámara denegado. Habilitalo en los ajustes del sistema para usarla."
    ImageCaptureError.CAMERA_UNAVAILABLE ->
        "La cámara no está disponible en este dispositivo."
    ImageCaptureError.READ_FAILED ->
        "No se pudo leer la imagen elegida."
    ImageCaptureError.UNKNOWN ->
        "Ocurrió un error al agregar la imagen."
}
