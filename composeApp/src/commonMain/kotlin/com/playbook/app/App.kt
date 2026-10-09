package com.playbook.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    /** Creación con una imagen recién capturada/elegida, todavía en memoria. */
    data class CreateWithImage(val image: PickedImage) : NotesDestination
}

/**
 * Raíz de la UI compartida y dueña del estado de la lista y de las acciones de
 * captura de imagen.
 *
 * La lectura vive en un [LaunchedEffect], nunca en el cuerpo de composición.
 * Tras cada mutación se incrementa `refreshKey` (`reload()`), lo que dispara una
 * relectura y refleja los cambios sin reiniciar la app. Sigue sin DI,
 * `ViewModel`/`Lifecycle`, coroutines propias ni Flow: sólo estado de Compose y
 * los repositorios síncronos recibidos por parámetro.
 */
@Composable
fun App(
    noteRepository: NoteRepository,
    attachmentRepository: AttachmentRepository,
) {
    MaterialTheme {
        var notes by remember { mutableStateOf(emptyList<Note>()) }
        var attachments by remember { mutableStateOf(emptyList<Attachment>()) }
        var destination by remember { mutableStateOf<NotesDestination>(NotesDestination.List) }
        var refreshKey by remember { mutableStateOf(0) }
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

        /** Guarda el archivo, luego la nota y el adjunto; limpia si algo falla. */
        fun saveNoteWithImage(image: PickedImage, body: String, track: Track?) {
            val stored = fileStore.saveImage(image.bytes, image.mimeType)
            try {
                val note = noteRepository.create(
                    NoteDraft(owner = LOCAL_OWNER_ID, body = body.trim(), track = track),
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

        val attachmentCounts = attachments.groupingBy { it.noteId }.eachCount()

        when (val current = destination) {
            NotesDestination.List -> NotesListScreen(
                notes = notes,
                attachmentCounts = attachmentCounts,
                onCreate = { destination = NotesDestination.Create },
                onCaptureImage = { showImageSourceDialog = true },
                onEdit = { noteId -> destination = NotesDestination.Edit(noteId) },
            )

            NotesDestination.Create -> NoteEditorScreen(
                initialBody = "",
                initialTrack = null,
                initialImage = null,
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
                onRemoveImage = null,
            )

            is NotesDestination.CreateWithImage -> NoteEditorScreen(
                initialBody = "",
                initialTrack = null,
                initialImage = current.image,
                isEditing = false,
                onSave = { body, track -> saveNoteWithImage(current.image, body, track) },
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
                        initialImage = null,
                        isEditing = true,
                        onSave = { body, track ->
                            noteRepository.update(note.copy(body = body.trim(), track = track))
                            goToList()
                            reload()
                        },
                        onDelete = {
                            // Las filas `attachment` se borran en cascade dentro
                            // del repositorio; los archivos se borran acá.
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
