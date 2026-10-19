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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.playbook.app.voice.RecordedAudio
import com.playbook.app.voice.VoiceCaptureError
import com.playbook.app.voice.VoiceTranscriber
import com.playbook.core.ai.AiClient
import com.playbook.core.ai.BrainstormingSuggestionService
import com.playbook.core.ai.NoteIndexingOrchestrator
import com.playbook.core.ai.PendingIndexingRetry
import com.playbook.core.ai.RagAnswer
import com.playbook.core.ai.RagQueryService
import com.playbook.core.diagnostics.DiagnosticSource
import com.playbook.core.diagnostics.Diagnostics
import com.playbook.core.diagnostics.NoOpDiagnostics
import com.playbook.core.linking.RelatedNote
import com.playbook.core.linking.SemanticLinkingService
import com.playbook.core.linking.buildRelatedNotes
import com.playbook.core.model.Attachment
import com.playbook.core.model.AttachmentKind
import com.playbook.core.model.GameProject
import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.Track
import com.playbook.core.platform.currentTimeMillis
import com.playbook.core.repository.AttachmentRepository
import com.playbook.core.repository.GameProjectRepository
import com.playbook.core.repository.NoteLinkRepository
import com.playbook.core.repository.NoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owner único del MVP: lo provee la UI al construir cada [NoteDraft]. */
private const val LOCAL_OWNER_ID = "local"

// Resúmenes estables (sin datos del usuario) para el diagnóstico de fallos
// nativos. El `detail` nunca lleva rutas ni contenido.
private const val VOICE_FAILED = "voice_failed"
private const val IMAGE_FAILED = "image_failed"
private const val STORAGE_FAILED = "storage_failed"

/** Destino de navegación local (sin librería de navegación). */
sealed interface NotesDestination {
    data object List : NotesDestination
    data object Create : NotesDestination
    data class Edit(val noteId: String) : NotesDestination
    data object Ask : NotesDestination

    /** Vista de GDD: proyección de las Notas agrupadas por `track`. */
    data object Gdd : NotesDestination

    /** Wizard de 9 pasos para crear o iterar la ficha del Proyecto. */
    data object Brainstorming : NotesDestination

    /** Creación con una imagen recién capturada/elegida, todavía en memoria. */
    data class CreateWithImage(val image: PickedImage) : NotesDestination

    /** Notas relacionadas por similitud, desde una Nota de origen. */
    data class Related(val noteId: String) : NotesDestination
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
 * El dictado es **inline en Nueva nota**: la sesión y el cap de 30s viven acá,
 * el editor muestra el progreso/parcial y anexa el texto final al `body`, y el
 * audio original queda pendiente hasta Guardar (Adjunto `AUDIO`). El cap arranca
 * con `onStarted` (permiso concedido), no al tocar "Dictar". Al cancelar o salir
 * de la composición se corta la sesión y se borra el audio pendiente sin
 * huérfanos.
 *
 * El enriquecimiento (embedding + `NoteStatus`) pasa por
 * [NoteIndexingOrchestrator]: al guardar una Nota con `body` no vacío se indexa
 * best-effort (`indexBestEffort`, nunca bloquea el guardado) y el estado real se
 * refleja al refrescar. El usuario puede **re-indexar manualmente** una Nota no
 * indexada desde el editor ([reindex]); el reintento automático de las `PENDING`
 * ocurre al arrancar (ver más abajo), no en el guardado.
 *
 * El ciclo offline/online lo cierra [PendingIndexingRetry]: al abrir/reabrir la
 * app se reintentan las Notas `PENDING` contra la IA; las exitosas quedan
 * `INDEXED` y las irrecuperables (tras agotar el presupuesto) pasan a `FAILED`.
 * Es un efecto de arranque (una vez); si la IA no está disponible es un no-op.
 */
@Composable
fun App(
    noteRepository: NoteRepository,
    gameProjectRepository: GameProjectRepository,
    attachmentRepository: AttachmentRepository,
    voiceTranscriber: VoiceTranscriber,
    noteIndexingOrchestrator: NoteIndexingOrchestrator,
    pendingIndexingRetry: PendingIndexingRetry,
    ragQueryService: RagQueryService,
    noteLinkRepository: NoteLinkRepository,
    semanticLinkingService: SemanticLinkingService,
    aiClient: AiClient,
    diagnostics: Diagnostics = NoOpDiagnostics,
) {
    MaterialTheme {
        var notes by remember { mutableStateOf(emptyList<Note>()) }
        var project by remember { mutableStateOf<GameProject?>(null) }
        var attachments by remember { mutableStateOf(emptyList<Attachment>()) }
        var destination by remember { mutableStateOf<NotesDestination>(NotesDestination.List) }
        var refreshKey by remember { mutableStateOf(0) }
        var relatedNotes by remember { mutableStateOf(emptyList<RelatedNote>()) }
        var dictation by remember { mutableStateOf<DictationUiState>(DictationUiState.Idle) }
        var dictatedText by remember { mutableStateOf<String?>(null) }
        var pendingAudio by remember { mutableStateOf<RecordedAudio?>(null) }
        var dictationJob by remember { mutableStateOf<Job?>(null) }
        var askState by remember { mutableStateOf<AskUiState>(AskUiState.Idle) }
        var showImageSourceDialog by remember { mutableStateOf(false) }
        var imageError by remember { mutableStateOf<ImageCaptureError?>(null) }

        // Las llamadas de IA son asíncronas: `rememberCoroutineScope` permite
        // invocar los servicios `suspend` sin introducir `ViewModel`/Flow.
        val scope = rememberCoroutineScope()
        val suggestionService = remember(aiClient) {
            BrainstormingSuggestionService(aiClient)
        }

        val imagePicker = rememberImagePicker(
            onResult = { picked -> destination = NotesDestination.CreateWithImage(picked) },
            onError = { error ->
                // Fallo de integración nativa: registrar sin cambiar la UI.
                diagnostics.recordFailure(DiagnosticSource.IMAGE, IMAGE_FAILED, error.name)
                imageError = error
            },
        )
        val fileStore = rememberAttachmentFileStore()

        LaunchedEffect(refreshKey) {
            notes = noteRepository.getAll()
            attachments = attachmentRepository.getAll()
            project = gameProjectRepository.get()
        }

        fun reload() {
            refreshKey++
        }

        // Al abrir/reabrir la app se reintentan las Notas `PENDING`
        // (`offline-pending-retry`): las exitosas quedan `INDEXED` y las
        // irrecuperables `FAILED`. Es un efecto de arranque (una vez), no
        // polling/scheduling; `retryPending` es no-op si la IA no está
        // disponible y nunca bloquea la UI.
        LaunchedEffect(Unit) {
            pendingIndexingRetry.retryPending()
            reload()
        }

        fun goToList() {
            destination = NotesDestination.List
        }

        /**
         * Indexado best-effort al guardar: corre [NoteIndexingOrchestrator.index]
         * (embedding + transición real de `NoteStatus`) sin bloquear ni fallar el
         * guardado, y refresca la lista cuando el `suspend` resuelve para que el
         * badge muestre el estado verdadero. Una Nota con `body` en blanco (p. ej.
         * sólo imagen) no se indexa.
         */
        fun indexBestEffort(note: Note) {
            if (note.body.isBlank()) return
            scope.launch {
                noteIndexingOrchestrator.index(note)
                reload()
            }
        }

        /**
         * Re-indexado **manual** disparado por el usuario desde el editor. Reusa
         * el mismo orquestador y refresca la UI; no agenda nada. El reintento
         * automático de las `PENDING` ocurre al arrancar ([PendingIndexingRetry]);
         * esto es sólo el tap del usuario.
         */
        fun reindex(note: Note) {
            scope.launch {
                noteIndexingOrchestrator.index(note)
                reload()
            }
        }

        /** Consulta RAG: pasa el estado visible por [AskUiState] mientras corre. */
        fun ask(question: String) {
            if (question.isBlank()) return
            askState = AskUiState.Asking
            scope.launch {
                askState = when (val result = ragQueryService.ask(question)) {
                    is RagAnswer.Answer -> AskUiState.Answered(result.text, result.sources)
                    RagAnswer.NoIndexedNotes -> AskUiState.NoIndexedNotes
                    is RagAnswer.Failed -> AskUiState.Error
                }
            }
        }

        /** Borra el audio pendiente (si lo hay) y lo limpia; idempotente. */
        fun discardPendingAudio() {
            val audio = pendingAudio ?: return
            pendingAudio = null
            fileStore.delete(audio.relativePath)
        }

        /**
         * Persiste el audio pendiente como Adjunto `AUDIO` de [noteId]
         * (best-effort: si falla se registra `STORAGE` y se borra el archivo) y
         * siempre limpia `pendingAudio` para no dejarlo huérfano.
         */
        fun attachAudio(noteId: String) {
            val audio = pendingAudio ?: return
            pendingAudio = null
            try {
                attachmentRepository.create(
                    noteId = noteId,
                    kind = AttachmentKind.AUDIO,
                    mimeType = audio.mimeType,
                    filePath = audio.relativePath,
                    byteSize = audio.byteSize,
                )
            } catch (_: Throwable) {
                // Degradación tolerante: conservar la Nota sin Adjunto y no dejar
                // el archivo huérfano.
                diagnostics.recordFailure(DiagnosticSource.STORAGE, STORAGE_FAILED)
                fileStore.delete(audio.relativePath)
            }
        }

        /** Corta la sesión de dictado y descarta el parcial; no toca el audio pendiente. */
        fun cancelDictation() {
            dictationJob?.cancel()
            dictationJob = null
            voiceTranscriber.cancel()
            dictation = DictationUiState.Idle
        }

        /**
         * Detiene una sesión `Listening` (idempotente) y espera el final. Si ya
         * está en `Processing` o `Idle`/`Error`, no hace nada.
         */
        fun stopDictation() {
            if (dictation !is DictationUiState.Listening) return
            dictationJob?.cancel()
            dictationJob = null
            dictation = DictationUiState.Processing
            voiceTranscriber.stop()
        }

        /**
         * Lanza el timer del cap de 30s. Arranca recién con `onStarted` (sesión
         * real iniciada), no al tocar "Dictar": el diálogo de permiso no consume
         * el cap. Al llegar al máximo llama [stopDictation] una sola vez.
         */
        fun launchDictationTimer() {
            dictationJob?.cancel()
            dictationJob = scope.launch {
                val startedAt = currentTimeMillis()
                while (true) {
                    delay(DICTATION_TICK_MILLIS)
                    val current = dictation
                    if (current !is DictationUiState.Listening) return@launch
                    val elapsed = (currentTimeMillis() - startedAt)
                        .coerceIn(0L, DICTATION_MAX_MILLIS)
                    dictation = current.copy(elapsedMillis = elapsed)
                    if (elapsed >= DICTATION_MAX_MILLIS) {
                        stopDictation()
                        return@launch
                    }
                }
            }
        }

        /**
         * Arranca una sesión inline: corta la anterior, conserva el
         * `pendingAudio` finalizado hasta que un nuevo dictado traiga audio, y
         * deja el estado en `Listening`. El timer espera a `onStarted`.
         */
        fun startDictation() {
            dictationJob?.cancel()
            dictationJob = null
            voiceTranscriber.cancel()
            dictation = DictationUiState.Listening(partial = "", elapsedMillis = 0L)
            voiceTranscriber.start(
                onPartialResult = { partial ->
                    val current = dictation
                    if (current is DictationUiState.Listening) {
                        dictation = current.copy(partial = partial)
                    }
                },
                onFinalResult = { text, audio ->
                    dictationJob?.cancel()
                    dictationJob = null
                    val trimmed = text.trim()
                    if (trimmed.isBlank()) {
                        // Sin transcripción no se anexa nada: descartar sólo el
                        // audio nuevo y conservar el pendiente anterior.
                        audio?.let { fileStore.delete(it.relativePath) }
                        dictation = DictationUiState.Error(VoiceCaptureError.NO_MATCH)
                    } else {
                        if (audio != null) {
                            // Reemplazo exitoso: borra el pendiente anterior.
                            discardPendingAudio()
                            pendingAudio = audio
                        }
                        dictatedText = trimmed
                        dictation = DictationUiState.Idle
                    }
                },
                onError = { error ->
                    // Fallo de integración nativa: registrar y conservar el
                    // audio pendiente anterior.
                    dictationJob?.cancel()
                    dictationJob = null
                    diagnostics.recordFailure(DiagnosticSource.VOICE, VOICE_FAILED, error.name)
                    dictation = DictationUiState.Error(error)
                },
                onStarted = {
                    when (dictation) {
                        // El usuario detuvo antes de que la sesión arrancara.
                        DictationUiState.Processing -> voiceTranscriber.stop()
                        is DictationUiState.Listening -> launchDictationTimer()
                        DictationUiState.Idle,
                        is DictationUiState.Error,
                        -> Unit
                    }
                },
            )
        }

        /** `onDictatedTextConsumed` del editor: limpia sólo si coincide. */
        fun consumeDictatedText(text: String) {
            if (dictatedText == text) dictatedText = null
        }

        /** Cancela una Nueva nota sin guardar: corta el dictado y borra su audio. */
        fun cancelNewNote() {
            cancelDictation()
            discardPendingAudio()
            dictatedText = null
            goToList()
        }

        /**
         * Guarda una Nueva nota de texto: corta cualquier dictado en curso y
         * adjunta el audio pendiente. Si la Nota no se crea, descarta el audio
         * pendiente (sin huérfanos) y deja el editor abierto para reintentar.
         */
        fun saveTextNote(body: String, track: Track?, genres: List<String>) {
            cancelDictation()
            val note = try {
                noteRepository.create(
                    NoteDraft(
                        owner = LOCAL_OWNER_ID,
                        body = body.trim(),
                        track = track,
                        genres = genres,
                    ),
                )
            } catch (_: Throwable) {
                diagnostics.recordFailure(DiagnosticSource.STORAGE, STORAGE_FAILED)
                discardPendingAudio()
                return
            }
            attachAudio(note.id)
            indexBestEffort(note)
            goToList()
            reload()
        }

        /**
         * Guarda el archivo, luego la Nota y los Adjuntos; limpia si algo falla.
         * La Nota se crea primero; el Adjunto de imagen es best-effort (si falla
         * se conserva la Nota y se borra la imagen) y el audio pendiente se
         * adjunta después con [attachAudio]. Si la Nota no se crea, se borran la
         * imagen recién guardada y el audio pendiente.
         */
        fun saveNoteWithImage(image: PickedImage, body: String, track: Track?, genres: List<String>) {
            cancelDictation()
            val stored = try {
                fileStore.saveImage(image.bytes, image.mimeType)
            } catch (_: Throwable) {
                diagnostics.recordFailure(DiagnosticSource.STORAGE, STORAGE_FAILED)
                discardPendingAudio()
                return
            }
            val note = try {
                noteRepository.create(
                    NoteDraft(
                        owner = LOCAL_OWNER_ID,
                        body = body.trim(),
                        track = track,
                        genres = genres,
                    ),
                )
            } catch (_: Throwable) {
                diagnostics.recordFailure(DiagnosticSource.STORAGE, STORAGE_FAILED)
                fileStore.delete(stored.relativePath)
                discardPendingAudio()
                return
            }
            try {
                attachmentRepository.create(
                    noteId = note.id,
                    kind = AttachmentKind.IMAGE,
                    mimeType = image.mimeType,
                    filePath = stored.relativePath,
                    byteSize = stored.byteSize,
                )
            } catch (_: Throwable) {
                // La Nota se conserva; no dejar la imagen recién guardada huérfana.
                diagnostics.recordFailure(DiagnosticSource.STORAGE, STORAGE_FAILED)
                fileStore.delete(stored.relativePath)
            }
            attachAudio(note.id)
            indexBestEffort(note)
            goToList()
            reload()
        }

        // Limpieza a nivel App: al salir de composición se corta el micrófono y
        // se borra el audio pendiente. No se pone un DisposableEffect por destino
        // Create/CreateWithImage: alternar la imagen no debe cortar el dictado.
        DisposableEffect(Unit) {
            onDispose {
                dictationJob?.cancel()
                dictationJob = null
                voiceTranscriber.cancel()
                discardPendingAudio()
            }
        }

        val attachmentKinds = attachments
            .groupBy({ it.noteId }, { it.kind })
            .mapValues { (_, kinds) -> kinds.toSet() }
        val attachmentCounts = attachmentKinds.mapValues { (_, kinds) -> kinds.size }

        // `attachments` llega ordenado por `created_at ASC, id ASC`: `distinctBy`
        // conserva el primer Adjunto IMAGE por Nota, de forma determinista.
        val imagePaths = attachments
            .filter { it.kind == AttachmentKind.IMAGE }
            .distinctBy { it.noteId }
            .associate { it.noteId to it.filePath }
        val loadThumbnail: suspend (String) -> ImageBitmap? = { path ->
            fileStore.read(path)?.let(::decodeImageBitmap)
        }

        when (val current = destination) {
            NotesDestination.List -> NotesListScreen(
                notes = notes,
                attachmentKinds = attachmentKinds,
                imagePaths = imagePaths,
                loadThumbnail = loadThumbnail,
                onCreate = { destination = NotesDestination.Create },
                onCaptureImage = { showImageSourceDialog = true },
                onEdit = { noteId -> destination = NotesDestination.Edit(noteId) },
                onDictate = {
                    // El dictado ahora vive dentro de Nueva nota: abrir el editor
                    // y arrancar la sesión (el cap espera al onStarted real).
                    destination = NotesDestination.Create
                    startDictation()
                },
                onOpenGdd = { destination = NotesDestination.Gdd },
                onAsk = {
                    askState = AskUiState.Idle
                    destination = NotesDestination.Ask
                },
                onOpenBrainstorming = { destination = NotesDestination.Brainstorming },
            )

            NotesDestination.Brainstorming -> BrainstormingWizardScreen(
                initialProject = project,
                owner = project?.owner ?: LOCAL_OWNER_ID,
                suggestionService = suggestionService,
                onSave = { draft ->
                    val saved = runCatching { gameProjectRepository.save(draft) }.isSuccess
                    if (saved) {
                        reload()
                        goToList()
                    }
                    saved
                },
                onCancel = ::goToList,
            )

            NotesDestination.Gdd -> GddScreen(
                notes = notes,
                attachmentCounts = attachmentCounts,
                imagePaths = imagePaths,
                loadThumbnail = loadThumbnail,
                onOpenNote = { noteId -> destination = NotesDestination.Edit(noteId) },
                onBack = ::goToList,
            )

            is NotesDestination.Related -> {
                val sourceNote = notes.firstOrNull { it.id == current.noteId }
                if (sourceNote == null) {
                    // La Nota de origen ya no existe (p. ej. se borró): volver a la lista.
                    LaunchedEffect(current.noteId) { goToList() }
                } else {
                    // Recomputo best-effort al abrir: materializa los enlaces desde los
                    // embeddings actuales; un fallo se ignora y se muestra lo persistido.
                    LaunchedEffect(current.noteId, refreshKey) {
                        runCatching { semanticLinkingService.recomputeAll() }
                        relatedNotes = buildRelatedNotes(
                            noteId = current.noteId,
                            links = noteLinkRepository.getLinksForNote(current.noteId),
                            notes = notes,
                        )
                    }
                    RelatedNotesScreen(
                        note = sourceNote,
                        related = relatedNotes,
                        onOpenNote = { noteId -> destination = NotesDestination.Edit(noteId) },
                        onBack = ::goToList,
                    )
                }
            }

            NotesDestination.Create -> NoteEditorScreen(
                initialBody = "",
                initialTrack = null,
                initialGenres = emptyList(),
                initialImage = null,
                isEditing = false,
                onSave = { body, track, genres ->
                    saveTextNote(body, track, genres)
                },
                onDelete = null,
                onCancel = ::cancelNewNote,
                onRemoveImage = null,
                onOpenRelated = null,
                dictationState = dictation,
                dictatedText = dictatedText,
                onStartDictation = ::startDictation,
                onStopDictation = ::stopDictation,
                onDictatedTextConsumed = ::consumeDictatedText,
            )

            is NotesDestination.CreateWithImage -> NoteEditorScreen(
                initialBody = "",
                initialTrack = null,
                initialGenres = emptyList(),
                initialImage = current.image,
                isEditing = false,
                onSave = { body, track, genres ->
                    saveNoteWithImage(current.image, body, track, genres)
                },
                onDelete = null,
                onCancel = ::cancelNewNote,
                onRemoveImage = { destination = NotesDestination.Create },
                onOpenRelated = null,
                dictationState = dictation,
                dictatedText = dictatedText,
                onStartDictation = ::startDictation,
                onStopDictation = ::stopDictation,
                onDictatedTextConsumed = ::consumeDictatedText,
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
                        initialGenres = note.genres,
                        initialImage = null,
                        isEditing = true,
                        onSave = { body, track, genres ->
                            val edited = note.copy(
                                body = body.trim(),
                                track = track,
                                genres = genres,
                            )
                            noteRepository.update(edited)
                            indexBestEffort(edited)
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
                        onOpenRelated = { destination = NotesDestination.Related(note.id) },
                        initialStatus = note.status,
                        onReindex = { reindex(note) },
                    )
                }
            }

            NotesDestination.Ask -> AskNotesScreen(
                state = askState,
                onAsk = ::ask,
                onOpenSource = { noteId -> destination = NotesDestination.Edit(noteId) },
                onBack = ::goToList,
            )
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
