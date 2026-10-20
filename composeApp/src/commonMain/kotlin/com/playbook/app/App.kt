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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.playbook.app.voice.RecordedAudio
import com.playbook.app.voice.VoiceCaptureError
import com.playbook.app.voice.VoiceTranscriber
import com.playbook.core.ai.AiClient
import com.playbook.core.ai.AudioAttachmentTranscriptionService
import com.playbook.core.ai.AudioTranscriptionResult
import com.playbook.core.ai.BrainstormingSuggestionService
import com.playbook.core.ai.ClassificationSuggestionService
import com.playbook.core.ai.ImageClient
import com.playbook.core.ai.ImageGenerationResult
import com.playbook.core.ai.JevClient
import com.playbook.core.ai.NoteIndexingOrchestrator
import com.playbook.core.brainstorming.isDuplicateBrainstormingNote
import com.playbook.core.ai.PendingIndexingRetry
import com.playbook.core.ai.RagAnswer
import com.playbook.core.ai.RagQueryService
import com.playbook.core.ai.ReadAloudResult
import com.playbook.core.ai.ReadAloudService
import com.playbook.core.ai.TextImageGenerationService
import com.playbook.core.ai.TranscribeClient
import com.playbook.core.ai.TtsClient
import com.playbook.core.ai.normalizeReadAloudText
import com.playbook.core.diagnostics.DiagnosticSource
import com.playbook.core.diagnostics.Diagnostics
import com.playbook.core.diagnostics.NoOpDiagnostics
import com.playbook.core.linking.RelatedNote
import com.playbook.core.linking.SemanticLinkingService
import com.playbook.core.linking.buildRelatedNotes
import com.playbook.core.model.Attachment
import com.playbook.core.model.AttachmentKind
import com.playbook.core.model.GameProject
import com.playbook.core.model.GameProjectDraft
import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.Track
import com.playbook.core.model.normalizeGenres
import com.playbook.core.platform.currentTimeMillis
import com.playbook.core.repository.AttachmentRepository
import com.playbook.core.repository.GameProjectRepository
import com.playbook.core.repository.NoteLinkRepository
import com.playbook.core.repository.NoteRepository
import kotlinx.coroutines.CancellationException
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

private sealed interface ProjectLoadState {
    data object Loading : ProjectLoadState
    data object Missing : ProjectLoadState
    data class Loaded(val project: GameProject) : ProjectLoadState
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
    transcribeClient: TranscribeClient,
    ttsClient: TtsClient,
    jevClient: JevClient,
    imageClient: ImageClient,
    diagnostics: Diagnostics = NoOpDiagnostics,
) {
    MaterialTheme {
        var notes by remember { mutableStateOf(emptyList<Note>()) }
        var projectState by remember { mutableStateOf<ProjectLoadState>(ProjectLoadState.Loading) }
        var onboardingBusy by remember { mutableStateOf(false) }
        var onboardingError by remember { mutableStateOf(false) }
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
        var showGenerateImageDialog by remember { mutableStateOf(false) }
        var imagePrompt by remember { mutableStateOf("") }
        var imageGenerationState by remember {
            mutableStateOf<ImageGenerationUiState>(ImageGenerationUiState.Idle)
        }
        var generationJob by remember { mutableStateOf<Job?>(null) }
        // Transcripción diferida del Adjunto AUDIO: el job y el último texto
        // viven acá; el editor sólo aplica/confirma y avisa que lo consumió.
        var transcriptionState by remember {
            mutableStateOf<AudioTranscriptionUiState>(AudioTranscriptionUiState.Idle)
        }
        var transcribedText by remember { mutableStateOf<String?>(null) }
        var transcriptionNoteId by remember { mutableStateOf<String?>(null) }
        var transcriptionJob by remember { mutableStateOf<Job?>(null) }
        // Lectura en voz alta: estado único y un solo reproductor para las 3
        // superficies. El request-id descarta resultados viejos.
        var readAloudState by remember { mutableStateOf<ReadAloudUiState>(ReadAloudUiState.Detenido) }
        var readAloudJob by remember { mutableStateOf<Job?>(null) }
        var readAloudRequest by remember { mutableStateOf(0) }

        // Las llamadas de IA son asíncronas: `rememberCoroutineScope` permite
        // invocar los servicios `suspend` sin introducir `ViewModel`/Flow.
        val scope = rememberCoroutineScope()
        val suggestionService = remember(aiClient) {
            BrainstormingSuggestionService(aiClient)
        }
        // Sugerencia de clasificación (Track/Géneros) con `jev`: efímera y sólo
        // por acción explícita del editor; nunca persiste ni auto-asigna.
        val classificationSuggestionService = remember(jevClient) {
            ClassificationSuggestionService(jevClient)
        }
        val readAloudService = remember(ttsClient) { ReadAloudService(ttsClient) }
        val readAloudPlayer = rememberReadAloudPlayer()

        val imagePicker = rememberImagePicker(
            onResult = { picked -> destination = NotesDestination.CreateWithImage(picked) },
            onError = { error ->
                // Fallo de integración nativa: registrar sin cambiar la UI.
                diagnostics.recordFailure(DiagnosticSource.IMAGE, IMAGE_FAILED, error.name)
                imageError = error
            },
        )
        val fileStore = rememberAttachmentFileStore()
        // Caso de uso puro del core: valida Nota/AUDIO, lee bytes y transcribe.
        // No persiste ni indexa; eso queda en el Guardar existente.
        val audioTranscriptionService = remember(
            transcribeClient,
            attachmentRepository,
            fileStore,
            diagnostics,
        ) {
            AudioAttachmentTranscriptionService(
                transcribeClient = transcribeClient,
                attachmentRepository = attachmentRepository,
                fileStore = fileStore,
                diagnostics = diagnostics,
            )
        }
        // Genera y valida la imagen; la persistencia sigue en saveNoteWithImage.
        val imageGenerationService = remember(imageClient, diagnostics) {
            TextImageGenerationService(imageClient, diagnostics)
        }

        LaunchedEffect(refreshKey) {
            notes = noteRepository.getAll()
            attachments = attachmentRepository.getAll()
            val loaded = gameProjectRepository.get()
            projectState = if (loaded == null) {
                ProjectLoadState.Missing
            } else {
                ProjectLoadState.Loaded(loaded)
            }
        }

        val project = (projectState as? ProjectLoadState.Loaded)?.project

        fun reload() {
            refreshKey++
        }

        /** Corta la lectura actual y descarta cualquier resultado en vuelo. */
        fun stopReadAloud() {
            readAloudRequest++
            readAloudJob?.cancel()
            readAloudJob = null
            readAloudPlayer.stop()
            readAloudState = ReadAloudUiState.Detenido
        }

        /**
         * Inicia la lectura de [surface]. Detiene la anterior, sintetiza con
         * [ReadAloudService] y sólo arranca el player si el request sigue vigente.
         */
        fun readAloud(surface: ReadAloudSurface, rawText: String) {
            val text = normalizeReadAloudText(rawText) ?: return
            readAloudJob?.cancel()
            readAloudPlayer.stop()
            val request = ++readAloudRequest
            readAloudState = ReadAloudUiState.Preparando(surface)
            readAloudJob = scope.launch {
                try {
                    when (val result = readAloudService.synthesize(text)) {
                        is ReadAloudResult.Ready -> {
                            if (request != readAloudRequest) return@launch
                            val started = readAloudPlayer.play(
                                audio = result.audio,
                                onCompleted = {
                                    if (request == readAloudRequest) {
                                        readAloudJob = null
                                        readAloudState = ReadAloudUiState.Detenido
                                    }
                                },
                                onError = {
                                    if (request == readAloudRequest) {
                                        readAloudJob = null
                                        readAloudState = ReadAloudUiState.Error(surface)
                                    }
                                },
                            )
                            if (request == readAloudRequest) {
                                readAloudState = when {
                                    !started -> ReadAloudUiState.Error(surface)
                                    readAloudState is ReadAloudUiState.Preparando ->
                                        ReadAloudUiState.Reproduciendo(surface)
                                    else -> readAloudState
                                }
                            }
                        }

                        ReadAloudResult.EmptyText ->
                            if (request == readAloudRequest) {
                                readAloudState = ReadAloudUiState.Detenido
                            }

                        is ReadAloudResult.Failed ->
                            if (request == readAloudRequest) {
                                readAloudState = ReadAloudUiState.Error(surface)
                            }
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Throwable) {
                    if (request == readAloudRequest) {
                        readAloudState = ReadAloudUiState.Error(surface)
                    }
                } finally {
                    if (request == readAloudRequest) readAloudJob = null
                }
            }
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

        // Cambiar de destino (Nota, lista, etc.) cancela el job y descarta el
        // resultado: nunca se aplica texto de una Nota a otra. La selección de
        // audio vive en el editor y se reinicia al entrar/salir de Edit.
        LaunchedEffect(destination) {
            stopReadAloud()
            transcriptionJob?.cancel()
            transcriptionJob = null
            transcriptionState = AudioTranscriptionUiState.Idle
            transcribedText = null
            transcriptionNoteId = null
        }

        fun goToList() {
            destination = NotesDestination.List
        }

        fun createProjectFromOnboarding(action: (GameProject) -> Unit) {
            if (onboardingBusy) return
            onboardingBusy = true
            try {
                val saved = runCatching {
                    gameProjectRepository.save(GameProjectDraft(owner = LOCAL_OWNER_ID))
                }
                saved.onSuccess { savedProject ->
                    projectState = ProjectLoadState.Loaded(savedProject)
                    reload()
                    action(savedProject)
                }.onFailure {
                    diagnostics.recordFailure(DiagnosticSource.STORAGE, STORAGE_FAILED)
                    onboardingError = true
                }
            } finally {
                onboardingBusy = false
            }
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
         * Crea una Nota desde el wizard. El chequeo de duplicado y la creación
         * corren dentro de [runCatching]: un fallo de lectura/escritura devuelve
         * [NoteSaveResult.FAILED] sin propagar. El indexado y el refresh sólo
         * ocurren en el camino exitoso.
         */
        fun saveBrainstormingNote(
            body: String,
            track: Track?,
            genres: List<String>,
        ): NoteSaveResult {
            val normalized = normalizeGenres(genres)
            val created = runCatching {
                val duplicate = noteRepository.getAll().any { note ->
                    isDuplicateBrainstormingNote(note, body, track, normalized)
                }
                if (duplicate) {
                    null
                } else {
                    noteRepository.create(
                        NoteDraft(
                            owner = LOCAL_OWNER_ID,
                            body = body,
                            track = track,
                            genres = normalized,
                        ),
                    )
                }
            }.getOrElse { return NoteSaveResult.FAILED }

            if (created == null) return NoteSaveResult.DUPLICATE
            indexBestEffort(created)
            reload()
            return NoteSaveResult.SAVED
        }

        /**
         * Crea una Nota desde una respuesta del Q&A. Sólo persiste la respuesta
         * (`body`), sin Track ni Géneros y sin citas/fuentes: el retrieval es
         * efímero. El chequeo de duplicado y la creación corren dentro de
         * [runCatching]; un fallo de lectura/escritura devuelve
         * [NoteSaveResult.FAILED] sin propagar. El indexado y el refresh sólo
         * ocurren en el camino exitoso (best-effort: un fallo de indexado no
         * revierte la Nota).
         */
        fun saveQaAnswer(answerText: String): NoteSaveResult {
            val body = answerText.trim()
            if (body.isBlank()) return NoteSaveResult.FAILED
            val created = runCatching {
                val duplicate = noteRepository.getAll().any { note ->
                    isDuplicateBrainstormingNote(note, body, null, emptyList())
                }
                if (duplicate) {
                    null
                } else {
                    noteRepository.create(
                        NoteDraft(
                            owner = LOCAL_OWNER_ID,
                            body = body,
                            track = null,
                            genres = emptyList(),
                        ),
                    )
                }
            }.getOrElse { return NoteSaveResult.FAILED }

            if (created == null) return NoteSaveResult.DUPLICATE
            indexBestEffort(created)
            reload()
            return NoteSaveResult.SAVED
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

        /**
         * Transcribe el Adjunto `AUDIO` elegido de [noteId]. El job es cancelable
         * y su resultado se descarta si el destino ya no es esa Nota. El texto
         * sólo se entrega al editor ([transcribedText]); no se guarda ni indexa
         * hasta que el usuario toque "Guardar" (camino existente).
         */
        fun transcribeAttachment(noteId: String, attachmentId: String) {
            val current = destination
            if (current !is NotesDestination.Edit || current.noteId != noteId) return
            transcriptionJob?.cancel()
            transcriptionJob = scope.launch {
                transcriptionNoteId = noteId
                transcribedText = null
                transcriptionState = AudioTranscriptionUiState.Transcribing
                val result: AudioTranscriptionResult? = try {
                    audioTranscriptionService.transcribe(noteId, attachmentId)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Throwable) {
                    // Fallo inesperado (no del puerto): mismo mensaje/retry, sin
                    // datos del usuario en el diagnóstico.
                    diagnostics.recordFailure(
                        DiagnosticSource.AI_TRANSCRIBE,
                        AudioAttachmentTranscriptionService.SUMMARY_FAILED,
                    )
                    null
                }

                // La Nota pudo cambiar durante la llamada: descartar el texto.
                if ((destination as? NotesDestination.Edit)?.noteId != noteId) {
                    transcriptionJob = null
                    return@launch
                }

                transcriptionState = when (result) {
                    is AudioTranscriptionResult.Transcribed -> {
                        transcribedText = result.text
                        AudioTranscriptionUiState.Idle
                    }

                    AudioTranscriptionResult.EmptyTranscription -> AudioTranscriptionUiState.Empty
                    // Incluye AudioNotFound: para la UI el Adjunto ya no está legible.
                    AudioTranscriptionResult.AudioNotFound,
                    AudioTranscriptionResult.AudioUnavailable,
                    -> AudioTranscriptionUiState.Unavailable

                    is AudioTranscriptionResult.Failed,
                    null,
                    -> AudioTranscriptionUiState.Failed
                }
                transcriptionJob = null
            }
        }

        /** `onTranscribedTextConsumed` del editor: limpia sólo si coincide. */
        fun consumeTranscribedText(text: String) {
            if (transcribedText == text) transcribedText = null
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

        /** Cierra el diálogo de generación y descarta cualquier resultado en vuelo. */
        fun cancelImageGeneration() {
            generationJob?.cancel()
            generationJob = null
            showGenerateImageDialog = false
            imagePrompt = ""
            imageGenerationState = ImageGenerationUiState.Idle
        }

        /**
         * Genera una imagen desde el prompt y, si es válida, navega al editor con
         * la imagen en memoria. Nada se persiste hasta Guardar. Los errores se
         * muestran en el diálogo y permiten reintentar con el mismo prompt.
         */
        fun generateImage() {
            val prompt = imagePrompt
            if (prompt.isBlank() || imageGenerationState is ImageGenerationUiState.Generating) return
            generationJob?.cancel()
            generationJob = scope.launch {
                imageGenerationState = ImageGenerationUiState.Generating
                try {
                    when (val result = imageGenerationService.generate(prompt)) {
                        is ImageGenerationResult.Generated -> {
                            showGenerateImageDialog = false
                            imagePrompt = ""
                            imageGenerationState = ImageGenerationUiState.Idle
                            destination = NotesDestination.CreateWithImage(
                                PickedImage(result.image.bytes, result.image.mimeType),
                            )
                        }

                        ImageGenerationResult.EmptyPrompt ->
                            imageGenerationState = ImageGenerationUiState.Error(
                                "Escribí una descripción para generar la imagen.",
                            )

                        ImageGenerationResult.InvalidImage ->
                            imageGenerationState = ImageGenerationUiState.Error(
                                "La IA devolvió una imagen inválida. Intentá de nuevo.",
                            )

                        is ImageGenerationResult.Failed ->
                            imageGenerationState = ImageGenerationUiState.Error(
                                "No se pudo generar la imagen. Revisá la conexión e intentá de nuevo.",
                            )
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Throwable) {
                    diagnostics.recordFailure(
                        DiagnosticSource.IMAGE,
                        TextImageGenerationService.SUMMARY_FAILED,
                    )
                    imageGenerationState = ImageGenerationUiState.Error(
                        "No se pudo generar la imagen. Intentá de nuevo.",
                    )
                } finally {
                    generationJob = null
                }
            }
        }

        // Limpieza a nivel App: al salir de composición se corta el micrófono y
        // se borra el audio pendiente. No se pone un DisposableEffect por destino
        // Create/CreateWithImage: alternar la imagen no debe cortar el dictado.
        DisposableEffect(Unit) {
            onDispose {
                dictationJob?.cancel()
                dictationJob = null
                transcriptionJob?.cancel()
                transcriptionJob = null
                generationJob?.cancel()
                generationJob = null
                readAloudJob?.cancel()
                readAloudJob = null
                readAloudPlayer.stop()
                readAloudPlayer.release()
                readAloudState = ReadAloudUiState.Detenido
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

        when (projectState) {
            ProjectLoadState.Loading -> FirstRunLoading()
            ProjectLoadState.Missing -> FirstRunOnboardingScreen(
                onStartBrainstorming = {
                    createProjectFromOnboarding {
                        destination = NotesDestination.Brainstorming
                    }
                },
                onSkip = {
                    createProjectFromOnboarding { goToList() }
                },
            )

            is ProjectLoadState.Loaded -> Unit
        }

        if (projectState is ProjectLoadState.Loaded) when (val current = destination) {
            NotesDestination.List -> NotesListScreen(
                notes = notes,
                attachmentKinds = attachmentKinds,
                imagePaths = imagePaths,
                loadThumbnail = loadThumbnail,
                onCreate = { destination = NotesDestination.Create },
                onOpenImageSource = { showImageSourceDialog = true },
                onCaptureCamera = { imagePicker.launch(ImageSource.CAMERA) },
                onEdit = { noteId -> destination = NotesDestination.Edit(noteId) },
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
                existingNotes = notes,
                onSave = { draft ->
                    val saved = runCatching { gameProjectRepository.save(draft) }.isSuccess
                    if (saved) {
                        reload()
                        goToList()
                    }
                    saved
                },
                onSaveNote = ::saveBrainstormingNote,
                onCancel = ::goToList,
            )

            NotesDestination.Gdd -> GddScreen(
                notes = notes,
                project = project,
                attachmentCounts = attachmentCounts,
                imagePaths = imagePaths,
                loadThumbnail = loadThumbnail,
                onOpenNote = { noteId -> destination = NotesDestination.Edit(noteId) },
                onBack = ::goToList,
                readAloudState = readAloudState,
                onReadAloud = { text -> readAloud(ReadAloudSurface.GDD, text) },
                onStopReadAloud = ::stopReadAloud,
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
                classificationSuggestionService = classificationSuggestionService,
                readAloudState = readAloudState,
                onReadAloud = { text -> readAloud(ReadAloudSurface.NOTE, text) },
                onStopReadAloud = ::stopReadAloud,
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
                classificationSuggestionService = classificationSuggestionService,
                readAloudState = readAloudState,
                onReadAloud = { text -> readAloud(ReadAloudSurface.NOTE, text) },
                onStopReadAloud = ::stopReadAloud,
            )

            is NotesDestination.Edit -> {
                val note = notes.firstOrNull { it.id == current.noteId }
                if (note == null) {
                    // La nota ya no existe (p. ej. se borró): volver a la lista.
                    LaunchedEffect(current.noteId) { goToList() }
                } else {
                    // Los Adjuntos llegan ordenados `created_at ASC, id ASC`; el
                    // filtro conserva ese orden para el selector determinista.
                    val noteAudio = attachments.filter {
                        it.noteId == note.id && it.kind == AttachmentKind.AUDIO
                    }
                    // El estado/texto transcripto sólo es válido para esta Nota:
                    // un resultado viejo nunca se aplica al cambiar de Nota.
                    val noteTranscriptionState =
                        if (transcriptionNoteId == note.id) {
                            transcriptionState
                        } else {
                            AudioTranscriptionUiState.Idle
                        }
                    val noteTranscribedText =
                        if (transcriptionNoteId == note.id) transcribedText else null
                    // `key(note.id)` reinicia borrador y selección al cambiar de Nota.
                    key(note.id) {
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
                            dictationState = dictation,
                            audioAttachments = noteAudio,
                            transcriptionState = noteTranscriptionState,
                            transcribedText = noteTranscribedText,
                            onTranscribe = { attachmentId ->
                                transcribeAttachment(note.id, attachmentId)
                            },
                            onTranscribedTextConsumed = ::consumeTranscribedText,
                            classificationSuggestionService = classificationSuggestionService,
                            readAloudState = readAloudState,
                            onReadAloud = { text -> readAloud(ReadAloudSurface.NOTE, text) },
                            onStopReadAloud = ::stopReadAloud,
                        )
                    }
                }
            }

            NotesDestination.Ask -> AskNotesScreen(
                state = askState,
                existingNotes = notes,
                onSaveNote = ::saveQaAnswer,
                onAsk = ::ask,
                onOpenSource = { noteId -> destination = NotesDestination.Edit(noteId) },
                onBack = ::goToList,
                readAloudState = readAloudState,
                onReadAloud = { text -> readAloud(ReadAloudSurface.QA_ANSWER, text) },
                onStopReadAloud = ::stopReadAloud,
            )
        }

        if (onboardingError) {
            AlertDialog(
                onDismissRequest = { onboardingError = false },
                title = { Text(text = "No se pudo crear el Proyecto") },
                text = { Text(text = "Intentá de nuevo.") },
                confirmButton = {
                    TextButton(onClick = { onboardingError = false }) {
                        Text(text = "Aceptar")
                    }
                },
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
                        TextButton(
                            onClick = {
                                showImageSourceDialog = false
                                imagePrompt = ""
                                imageGenerationState = ImageGenerationUiState.Idle
                                showGenerateImageDialog = true
                            },
                        ) {
                            Text(text = "Generar con IA")
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

        if (showGenerateImageDialog) {
            ImageGenerationDialog(
                prompt = imagePrompt,
                onPromptChange = { imagePrompt = it },
                state = imageGenerationState,
                onGenerate = ::generateImage,
                onCancel = ::cancelImageGeneration,
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
