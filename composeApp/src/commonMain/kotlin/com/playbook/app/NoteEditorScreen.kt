package com.playbook.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.playbook.core.ai.ClassificationSuggestion
import com.playbook.core.ai.ClassificationSuggestionResult
import com.playbook.core.ai.ClassificationSuggestionService
import com.playbook.core.ai.applyClassificationSuggestion
import com.playbook.core.model.Attachment
import com.playbook.core.model.GenreCatalog
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.Track
import com.playbook.core.model.normalizeGenres
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Estado efímero de la sugerencia de clasificación en el editor. */
private sealed interface SuggestionUiState {
    data object Idle : SuggestionUiState
    data object Loading : SuggestionUiState
    data class Ready(val suggestion: ClassificationSuggestion) : SuggestionUiState
    data object Empty : SuggestionUiState
    data object Error : SuggestionUiState
}

/**
 * Editor de nota de texto. Sirve para crear (modo `isEditing = false`) y editar
 * (modo `isEditing = true`).
 *
 * - El `body` es multilínea. Guardar se deshabilita si el `body` está vacío **y**
 *   no hay imagen: una nota sólo-imagen es válida. También se deshabilita
 *   mientras hay un texto dictado pendiente de anexarse (`dictatedText != null`),
 *   para no guardar el audio sin su texto.
 * - En **Nueva nota** (`!isEditing`) se muestra un panel de dictado inline
 *   (estado [DictationUiState], progreso de 30s y parcial en vivo). El texto
 *   final llega en [dictatedText], se anexa una sola vez con [appendDictation] y
 *   se avisa con [onDictatedTextConsumed]; la sesión y el audio pendiente viven
 *   en [App]. En edición el panel no existe.
 * - El `track` es opcional ("Sin track" por defecto); la selección se comunica
 *   con más que color (borde + marca textual), según `DESIGN.md`.
 * - Los Géneros se eligen del [GenreCatalog] fijo (toggle de chips) o se escriben
 *   como personalizados en [GenrePicker]; el conjunto inicial y toda alta/remoción
 *   pasan por [normalizeGenres]. No hay Categoría/Tipo/Nivel: el `track` ya cubre
 *   la clasificación fija.
 * - El contenido es scrollable y la fila Borrar/Cancelar/Guardar queda fija al
 *   pie: el picker puede crecer sin ocultar las acciones y el cuerpo conserva una
 *   altura mínima usable.
 * - Si hay una [initialImage] se muestra una previsualización y "Quitar imagen".
 * - En modo edición se ofrece "Borrar", con `AlertDialog` de confirmación. La
 *   acción no destructiva es explícita: "Cancelar". Si [onOpenRelated] no es null
 *   (modo edición) también se ofrece "Ver relacionadas".
 * - En modo edición, si llega [initialStatus], se muestra un `StatusBadge`
 *   (sólo lectura, nunca sólo color) y —cuando [onReindex] no es null— el botón
 *   "Re-indexar", habilitado sólo si el estado no es `INDEXED` y el `body` del
 *   borrador no está en blanco (sin texto no hay nada que embeber). Re-indexar
 *   **no** reescribe `track`/`genres`/`body`: sólo dispara el enriquecimiento.
 * - En modo edición, si la Nota tiene Adjuntos `AUDIO` ([audioAttachments]) se
 *   muestra "Audio adjunto": lista determinista (ordinal + tamaño) para elegir
 *   uno y la acción "Transcribir" con su estado ([transcriptionState]). El job y
 *   el texto transcripto viven en [App]; llegan por [transcribedText] y se
 *   consumen una sola vez con [onTranscribedTextConsumed]. Con `body` vacío el
 *   texto se aplica directo; con texto existente se abre un diálogo con vista
 *   previa y "Anexar"/"Reemplazar"/"Cancelar". Nunca hay auto-guardado.
 * - "Sugerir clasificación" (debajo del cuerpo/dictado y antes de `Track`) pide
 *   una propuesta efímera a [classificationSuggestionService]: sólo acción
 *   explícita, deshabilitada con `body` en blanco o mientras carga. "Confirmar"
 *   aplica al borrador local (`Track` sugerido reemplaza, Géneros se fusionan
 *   normalizados) y "Descartar" no toca nada; ninguno persiste. Cambiar a mano
 *   el `body`, el `Track` o los Géneros cancela/descarta la propuesta, y Guardar
 *   nunca espera a la IA.
 *
 * La pantalla es "tonta": mantiene el borrador local y emite `onSave`/`onDelete`/
 * `onCancel`/`onRemoveImage`/`onOpenRelated`/`onReindex`/`onTranscribe`; la
 * persistencia, el refresco y la navegación los maneja [App].
 */
@Composable
fun NoteEditorScreen(
    initialBody: String,
    initialTrack: Track?,
    initialGenres: List<String>,
    initialImage: PickedImage?,
    isEditing: Boolean,
    onSave: (body: String, track: Track?, genres: List<String>) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
    onRemoveImage: (() -> Unit)?,
    onOpenRelated: (() -> Unit)? = null,
    initialStatus: NoteStatus? = null,
    onReindex: (() -> Unit)? = null,
    dictationState: DictationUiState = DictationUiState.Idle,
    dictatedText: String? = null,
    onStartDictation: (() -> Unit)? = null,
    onStopDictation: (() -> Unit)? = null,
    onDictatedTextConsumed: ((String) -> Unit)? = null,
    audioAttachments: List<Attachment> = emptyList(),
    transcriptionState: AudioTranscriptionUiState = AudioTranscriptionUiState.Idle,
    transcribedText: String? = null,
    onTranscribe: ((String) -> Unit)? = null,
    onTranscribedTextConsumed: ((String) -> Unit)? = null,
    classificationSuggestionService: ClassificationSuggestionService,
    readAloudState: ReadAloudUiState = ReadAloudUiState.Detenido,
    onReadAloud: ((String) -> Unit)? = null,
    onStopReadAloud: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var body by remember { mutableStateOf(initialBody) }
    var selectedTrack by remember { mutableStateOf(initialTrack) }
    var genres by remember { mutableStateOf(normalizeGenres(initialGenres)) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    // Selección determinista: el primero del orden del repositorio queda elegido.
    // Se conserva mientras los ids no cambien (un `refresh` no debe perderla).
    val audioIds = audioAttachments.map { it.id }
    var selectedAudioId by remember(audioIds) { mutableStateOf(audioIds.firstOrNull()) }
    // Texto pendiente de confirmación ("Anexar"/"Reemplazar") y aviso tras aplicar.
    var pendingTranscription by remember { mutableStateOf<String?>(null) }
    var transcriptionNotice by remember { mutableStateOf<String?>(null) }

    // La sugerencia es estado de UI efímero: no se persiste. El contador de
    // request descarta resultados tardíos aunque la cancelación del `Job` llegue
    // después de que el servicio ya respondió.
    val suggestionScope = rememberCoroutineScope()
    var suggestionState by remember { mutableStateOf<SuggestionUiState>(SuggestionUiState.Idle) }
    var suggestionJob by remember { mutableStateOf<Job?>(null) }
    var suggestionRequest by remember { mutableStateOf(0) }

    fun discardSuggestion() {
        suggestionRequest++
        suggestionJob?.cancel()
        suggestionJob = null
        suggestionState = SuggestionUiState.Idle
    }

    fun requestSuggestion() {
        val text = body.trim()
        if (text.isEmpty()) return
        suggestionJob?.cancel()
        val request = ++suggestionRequest
        suggestionState = SuggestionUiState.Loading
        suggestionJob = suggestionScope.launch {
            val result = classificationSuggestionService.suggest(text)
            if (request != suggestionRequest) return@launch
            suggestionState = when (result) {
                is ClassificationSuggestionResult.Success ->
                    SuggestionUiState.Ready(result.suggestion)

                ClassificationSuggestionResult.Empty -> SuggestionUiState.Empty
                is ClassificationSuggestionResult.Failed -> SuggestionUiState.Error
            }
            suggestionJob = null
        }
    }

    fun confirmSuggestion(suggestion: ClassificationSuggestion) {
        val applied = applyClassificationSuggestion(selectedTrack, genres, suggestion)
        selectedTrack = applied.track
        genres = applied.genres
        discardSuggestion()
    }

    // El texto final del dictado se anexa una sola vez. Mientras esté pendiente,
    // Guardar queda deshabilitado para no persistir el audio sin su texto.
    LaunchedEffect(dictatedText) {
        val dictated = dictatedText
        if (!dictated.isNullOrBlank()) {
            discardSuggestion()
            body = appendDictation(body, dictated)
            onDictatedTextConsumed?.invoke(dictated)
        }
    }

    // La transcripción se aplica una sola vez: con `body` vacío directo y con
    // texto existente mediante confirmación explícita (nunca en silencio).
    LaunchedEffect(transcribedText) {
        val incoming = transcribedText ?: return@LaunchedEffect
        val trimmed = incoming.trim()
        if (trimmed.isEmpty()) {
            onTranscribedTextConsumed?.invoke(incoming)
            return@LaunchedEffect
        }
        transcriptionNotice = null
        if (body.isBlank()) {
            body = trimmed
            transcriptionNotice = TRANSCRIPTION_APPLIED_NOTICE
        } else {
            pendingTranscription = trimmed
        }
        // Una sola notificación por resultado; el texto queda en estado local.
        onTranscribedTextConsumed?.invoke(incoming)
    }

    val canSave =
        (body.isNotBlank() || initialImage != null) && dictatedText == null
    val dictationActive =
        dictationState is DictationUiState.Listening ||
            dictationState is DictationUiState.Processing
    val canTranscribe = isEditing &&
        onTranscribe != null &&
        audioAttachments.isNotEmpty() &&
        selectedAudioId != null &&
        !dictationActive &&
        transcriptionState != AudioTranscriptionUiState.Transcribing

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = if (isEditing) "Editar nota" else "Nueva nota",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (isEditing && initialStatus != null) {
                // Estado de indexado de sólo lectura + re-indexado manual. El badge
                // comunica con texto/forma y el botón sólo se habilita si hay algo
                // que embeber (body no vacío) y la Nota no está ya indexada.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(status = initialStatus)
                    Spacer(modifier = Modifier.weight(1f))
                    if (onReindex != null) {
                        OutlinedButton(
                            onClick = onReindex,
                            enabled = initialStatus != NoteStatus.INDEXED && body.isNotBlank(),
                        ) {
                            Text(text = "Re-indexar")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (initialImage != null) {
                val bitmap = remember(initialImage) { decodeImageBitmap(initialImage.bytes) }
                Text(text = "Imagen adjunta", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Previsualización de la imagen adjunta",
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        text = "No se pudo previsualizar la imagen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = { onRemoveImage?.invoke() }) {
                    Text(text = "Quitar imagen")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (isEditing && audioAttachments.isNotEmpty() && onTranscribe != null) {
                AudioAttachmentSection(
                    attachments = audioAttachments,
                    selectedId = selectedAudioId,
                    state = transcriptionState,
                    notice = transcriptionNotice,
                    enabled = canTranscribe,
                    onSelect = { selectedAudioId = it },
                    onTranscribe = {
                        selectedAudioId?.let { selected ->
                            transcriptionNotice = null
                            onTranscribe(selected)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            OutlinedTextField(
                value = body,
                onValueChange = {
                    if (it != body) {
                        body = it
                        discardSuggestion()
                    }
                },
                label = { Text(text = "Cuerpo") },
                placeholder = { Text(text = "Escribe tu idea…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 320.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (onReadAloud != null && onStopReadAloud != null) {
                ReadAloudButton(
                    surface = ReadAloudSurface.NOTE,
                    surfaceLabel = "nota",
                    state = readAloudState,
                    enabled = body.isNotBlank(),
                    onRead = { onReadAloud(body) },
                    onStop = onStopReadAloud,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (!isEditing && onStartDictation != null && onStopDictation != null) {
                DictationPanel(
                    state = dictationState,
                    onStart = onStartDictation,
                    onStop = onStopDictation,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            ClassificationSuggestionSection(
                state = suggestionState,
                enabled = body.isNotBlank(),
                onSuggest = ::requestSuggestion,
                onConfirm = ::confirmSuggestion,
                onDiscard = ::discardSuggestion,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Track", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrackOption(
                    label = "Sin track",
                    selected = selectedTrack == null,
                    onClick = {
                        selectedTrack = null
                        discardSuggestion()
                    },
                )
                Track.entries.forEach { track ->
                    TrackOption(
                        label = track.code,
                        selected = selectedTrack == track,
                        onClick = {
                            selectedTrack = track
                            discardSuggestion()
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            GenrePicker(
                selected = genres,
                onChange = {
                    genres = it
                    discardSuggestion()
                },
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (isEditing && onOpenRelated != null) {
                // Fila propia: en pantallas angostas, "Ver relacionadas" junto a
                // Borrar/Cancelar/Guardar empujaba "Guardar" fuera del borde visible.
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onOpenRelated) {
                        Text(text = "Ver relacionadas")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isEditing && onDelete != null) {
                OutlinedButton(onClick = { showDeleteDialog = true }) {
                    Text(text = "Borrar")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) {
                Text(text = "Cancelar")
            }
            Button(
                onClick = { onSave(body, selectedTrack, genres) },
                enabled = canSave,
            ) {
                Text(text = "Guardar")
            }
        }
    }

    pendingTranscription?.let { preview ->
        AlertDialog(
            onDismissRequest = { pendingTranscription = null },
            title = { Text(text = "Aplicar transcripción") },
            text = {
                Column {
                    Text(
                        text = "El cuerpo ya tiene texto. Elige cómo integrar la transcripción:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Vista previa", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            body = appendDictation(body, preview)
                            transcriptionNotice = TRANSCRIPTION_APPLIED_NOTICE
                            pendingTranscription = null
                        },
                    ) {
                        Text(text = "Anexar")
                    }
                    TextButton(
                        onClick = {
                            body = preview.trim()
                            transcriptionNotice = TRANSCRIPTION_APPLIED_NOTICE
                            pendingTranscription = null
                        },
                    ) {
                        Text(text = "Reemplazar")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTranscription = null }) {
                    Text(text = "Cancelar")
                }
            },
        )
    }

    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = "¿Borrar esta nota?") },
            text = { Text(text = "Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text(text = "Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = "Cancelar")
                }
            },
        )
    }
}

/**
 * Sección "Audio adjunto" del modo edición: lista determinista de los Adjuntos
 * `AUDIO` (ordinal + tamaño), selector cuando hay más de uno y la acción
 * "Transcribir" con su estado textual y el aviso de aplicación. No hay playback:
 * elegir un audio no lee el archivo ni llama al transcriptor.
 */
@Composable
private fun AudioAttachmentSection(
    attachments: List<Attachment>,
    selectedId: String?,
    state: AudioTranscriptionUiState,
    notice: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onTranscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "Audio adjunto", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        attachments.forEachIndexed { index, attachment ->
            val label = audioAttachmentLabel(index, attachment.byteSize)
            if (attachments.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(attachment.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = attachment.id == selectedId,
                        onClick = { onSelect(attachment.id) },
                    )
                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onTranscribe, enabled = enabled) {
            Text(text = state.actionLabel())
        }
        state.message()?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
        notice?.let { applied ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = applied, style = MaterialTheme.typography.bodyMedium)
        }
    }
}


/**
 * Sección de sugerencia de clasificación. `Idle`/`Loading` muestran la acción
 * explícita (con "Sugiriendo…" deshabilitado mientras corre); `Ready` muestra la
 * tarjeta con `Track` y chips de Género; `Empty`/`Error` explican sin detalles
 * técnicos. Confirmar/Descartar/Reintentar son las únicas acciones; la IA nunca
 * guarda ni auto-asigna.
 */
@Composable
private fun ClassificationSuggestionSection(
    state: SuggestionUiState,
    enabled: Boolean,
    onSuggest: () -> Unit,
    onConfirm: (ClassificationSuggestion) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (state) {
            SuggestionUiState.Idle -> {
                OutlinedButton(
                    onClick = onSuggest,
                    enabled = enabled,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(text = "Sugerir clasificación")
                }
            }

            SuggestionUiState.Loading -> {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(text = "Sugiriendo…")
                }
            }

            is SuggestionUiState.Ready -> {
                val suggestion = state.suggestion
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Sugerencia de Playbook",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Track:",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            LabelChip(text = suggestion.track?.code ?: "Sin track")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (suggestion.genres.isEmpty()) {
                            Text(
                                text = "Sin géneros sugeridos",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                suggestion.genres.forEach { genre ->
                                    GenreLabelChip(text = genre)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onConfirm(suggestion) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            ) {
                                Text(text = "Confirmar")
                            }
                            OutlinedButton(
                                onClick = onDiscard,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            ) {
                                Text(text = "Descartar")
                            }
                        }
                    }
                }
            }

            SuggestionUiState.Empty -> {
                Text(
                    text = "Playbook no propuso una clasificación.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(text = "Descartar")
                }
            }

            SuggestionUiState.Error -> {
                Text(
                    text = "No se pudo obtener la sugerencia.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSuggest,
                        enabled = enabled,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(text = "Reintentar")
                    }
                    OutlinedButton(
                        onClick = onDiscard,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(text = "Descartar")
                    }
                }
            }
        }
    }
}


/**
 * Panel de dictado inline de Nueva nota: `Idle` invita a dictar; `Listening`
 * muestra la barra de progreso (`elapsed / 30s`), el parcial en vivo y "Detener";
 * `Processing` deja la acción deshabilitada; `Error` muestra el mensaje textual y
 * "Reintentar". Todos los estados se comunican con texto, no sólo color.
 */
@Composable
private fun DictationPanel(
    state: DictationUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "Dictado", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        when (state) {
            DictationUiState.Idle -> {
                Button(onClick = onStart) {
                    Text(text = "Dictar")
                }
            }

            is DictationUiState.Listening -> {
                LinearProgressIndicator(
                    progress = {
                        (state.elapsedMillis.toFloat() / DICTATION_MAX_MILLIS)
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Escuchando… (${state.elapsedMillis / 1000}s / 30s)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.partial.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.partial,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onStop) {
                    Text(text = "Detener")
                }
            }

            DictationUiState.Processing -> {
                Button(onClick = {}, enabled = false) {
                    Text(text = "Transcribiendo…")
                }
            }

            is DictationUiState.Error -> {
                Text(
                    text = state.error.message(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onStart) {
                    Text(text = "Reintentar")
                }
            }
        }
    }
}

/**
 * Opción de `track` seleccionable. La selección se comunica con texto (marca
 * "✓"), grosor de borde y color, no sólo color (`DESIGN.md`).
 */
@Composable
private fun TrackOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
