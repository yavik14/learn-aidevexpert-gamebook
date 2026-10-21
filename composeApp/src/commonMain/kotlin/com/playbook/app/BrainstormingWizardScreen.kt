package com.playbook.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.playbook.core.ai.BrainstormingSuggestion
import com.playbook.core.ai.BrainstormingSuggestionResult
import com.playbook.core.ai.BrainstormingSuggestionService
import com.playbook.core.ai.BrainstormingStep
import com.playbook.core.brainstorming.BrainstormingStepNote
import com.playbook.core.brainstorming.buildBrainstormingStepNote
import com.playbook.core.brainstorming.isDuplicateBrainstormingNote
import com.playbook.core.model.GameProject
import com.playbook.core.model.GameProjectDraft
import com.playbook.core.model.KeyMechanic
import com.playbook.core.model.MechanicCategory
import com.playbook.core.model.Note
import com.playbook.core.model.Track
import com.playbook.core.model.normalizeGenres
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TOTAL_STEPS = 9
private const val MAX_INSPIRATIONS = 3
private const val MAX_OUT_OF_SCOPE = 3
private const val MAX_KEY_MECHANICS = 5
private const val SUGGESTION_ERROR = "No se pudieron obtener sugerencias. Prueba de nuevo."
private const val SUGGESTION_INVALID =
    "Playbook no devolvió sugerencias nuevas o válidas. Prueba de nuevo."

private val STEP_TITLES = listOf(
    "Título",
    "Género",
    "Core Loop",
    "Audiencia",
    "Concepto",
    "Inspiraciones",
    "Fantasía",
    "Mecánicas clave",
    "Fuera de alcance",
)

private val STEP_KINDS = listOf(
    BrainstormingStep.TITLE,
    BrainstormingStep.GENRE,
    BrainstormingStep.CORE_LOOP,
    BrainstormingStep.AUDIENCE,
    BrainstormingStep.CONCEPT,
    BrainstormingStep.INSPIRATIONS,
    BrainstormingStep.FANTASY,
    BrainstormingStep.KEY_MECHANICS,
    BrainstormingStep.OUT_OF_SCOPE,
)

/** Resultado de intentar guardar una Nota desde el wizard. */
enum class NoteSaveResult {
    SAVED,
    DUPLICATE,
    FAILED,
}

/** Borrador local de una mecánica; la categoría puede quedar sin elegir. */
private data class MechanicForm(
    val category: MechanicCategory? = null,
    val description: String = "",
)

/** Estado efímero de las sugerencias del paso actual. */
private sealed interface SuggestUiState {
    data object Idle : SuggestUiState
    data object Loading : SuggestUiState
    data class Loaded(
        val suggestions: List<BrainstormingSuggestion>,
        val partial: Boolean,
    ) : SuggestUiState

    data class Error(val message: String) : SuggestUiState
}

/**
 * Wizard de 9 pasos para crear o iterar la ficha del Proyecto único.
 *
 * Todos los pasos son opcionales. El estado vive acá y sólo se persiste al tocar
 * "Guardar": se construye un [GameProjectDraft] **completo** (nunca un draft
 * parcial, porque el repositorio reemplaza la ficha entera). "Cancelar" descarta
 * los cambios locales. Al reabrirse, el estado se inicializa desde
 * [initialProject] y se re-inicializa si el Proyecto termina de cargar
 * (`updatedAt` como clave).
 *
 * El paso Género reutiliza [GenrePicker] (catálogo + personalizados). La
 * creación de Notas por paso queda fuera de esta feature.
 */
@Composable
fun BrainstormingWizardScreen(
    initialProject: GameProject?,
    owner: String,
    suggestionService: BrainstormingSuggestionService,
    existingNotes: List<Note>,
    onSave: (GameProjectDraft) -> Boolean,
    onSaveNote: (body: String, track: Track?, genres: List<String>) -> NoteSaveResult,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val projectKey = initialProject?.updatedAt
    var step by remember { mutableStateOf(0) }
    var title by remember(projectKey) { mutableStateOf(initialProject?.title.orEmpty()) }
    var genres by remember(projectKey) { mutableStateOf(initialProject?.genres.orEmpty()) }
    var coreLoop by remember(projectKey) { mutableStateOf(initialProject?.coreLoop.orEmpty()) }
    var audience by remember(projectKey) { mutableStateOf(initialProject?.audience.orEmpty()) }
    var concept by remember(projectKey) { mutableStateOf(initialProject?.concept.orEmpty()) }
    var fantasy by remember(projectKey) { mutableStateOf(initialProject?.fantasy.orEmpty()) }
    val inspirations = remember(projectKey) {
        mutableStateListOf<String>().apply {
            addAll(initialProject?.inspirations.orEmpty().ifEmpty { listOf("") })
        }
    }
    val keyMechanics = remember(projectKey) {
        mutableStateListOf<MechanicForm>().apply {
            addAll(
                initialProject?.keyMechanics.orEmpty()
                    .map { MechanicForm(category = it.category, description = it.description) }
                    .ifEmpty { listOf(MechanicForm()) },
            )
        }
    }
    val outOfScope = remember(projectKey) {
        mutableStateListOf<String>().apply {
            addAll(initialProject?.outOfScope.orEmpty().ifEmpty { listOf("") })
        }
    }
    var saveError by remember { mutableStateOf<String?>(null) }
    var suggestState by remember { mutableStateOf<SuggestUiState>(SuggestUiState.Idle) }
    var suggestionJob by remember { mutableStateOf<Job?>(null) }
    var suggestionRequestId by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var noteFeedback by remember { mutableStateOf<String?>(null) }
    var noteError by remember { mutableStateOf<String?>(null) }
    var duplicateMessage by remember { mutableStateOf<String?>(null) }
    var pendingNote by remember { mutableStateOf<BrainstormingStepNote?>(null) }

    LaunchedEffect(step) {
        noteFeedback = null
    }

    fun buildDraft(): GameProjectDraft = GameProjectDraft(
        owner = owner,
        title = title.trim().ifEmpty { null },
        genres = normalizeGenres(genres),
        coreLoop = coreLoop.trim().ifEmpty { null },
        audience = audience.trim().ifEmpty { null },
        concept = concept.trim().ifEmpty { null },
        inspirations = inspirations.map { it.trim() }.filter { it.isNotEmpty() },
        fantasy = fantasy.trim().ifEmpty { null },
        keyMechanics = keyMechanics
            .filter { it.description.isNotBlank() }
            .mapNotNull { form ->
                form.category?.let { category ->
                    KeyMechanic(category = category, description = form.description.trim())
                }
            },
        outOfScope = outOfScope.map { it.trim() }.filter { it.isNotEmpty() },
    )

    val canSaveNote = if (step == 7) {
        keyMechanics.any { it.description.isNotBlank() }
    } else {
        buildBrainstormingStepNote(step, buildDraft()).body.isNotBlank()
    }

    fun resetSuggestions() {
        suggestionRequestId++
        suggestionJob?.cancel()
        suggestionJob = null
        suggestState = SuggestUiState.Idle
    }

    fun save() {
        val incompleteMechanic = keyMechanics.any {
            it.description.isNotBlank() && it.category == null
        }
        if (incompleteMechanic) {
            saveError = "Elige una categoría para cada mecánica con descripción."
            return
        }

        resetSuggestions()
        if (!onSave(buildDraft())) {
            saveError = "No se pudo guardar el Proyecto. Inténtalo de nuevo."
        }
    }

    fun startNoteSave() {
        noteFeedback = null
        val incompleteMechanic = step == 7 && keyMechanics.any {
            it.description.isNotBlank() && it.category == null
        }
        if (incompleteMechanic) {
            noteError = "Elige una categoría para cada mecánica con descripción."
            return
        }

        val payload = buildBrainstormingStepNote(step, buildDraft())
        if (payload.body.isBlank()) return

        val duplicate = existingNotes.any { note ->
            isDuplicateBrainstormingNote(
                note = note,
                body = payload.body,
                track = payload.track,
                genres = payload.genres,
            )
        }
        if (duplicate) {
            duplicateMessage = "Ya existe una Nota con el mismo contenido, Track y Géneros."
            return
        }

        pendingNote = payload
    }

    fun confirmNoteSave() {
        val payload = pendingNote ?: return
        when (onSaveNote(payload.body, payload.track, payload.genres)) {
            NoteSaveResult.SAVED -> {
                pendingNote = null
                noteFeedback = "Nota guardada."
            }
            NoteSaveResult.DUPLICATE -> {
                pendingNote = null
                duplicateMessage = "Ya existe una Nota con el mismo contenido, Track y Géneros."
            }
            NoteSaveResult.FAILED -> {
                pendingNote = null
                noteError = "No se pudo guardar la Nota."
            }
        }
    }

    fun goToStep(newStep: Int) {
        resetSuggestions()
        step = newStep
    }

    fun requestSuggestions() {
        if (suggestState is SuggestUiState.Loading) return
        suggestionJob?.cancel()
        val requestId = ++suggestionRequestId
        val requestedStep = step
        val stepKind = STEP_KINDS[requestedStep]
        val draftSnapshot = buildDraft()
        suggestState = SuggestUiState.Loading
        suggestionJob = scope.launch {
            val result = try {
                suggestionService.suggest(stepKind, draftSnapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (requestId == suggestionRequestId && requestedStep == step) {
                    suggestState = SuggestUiState.Error(SUGGESTION_ERROR)
                }
                return@launch
            }
            if (requestId != suggestionRequestId || requestedStep != step) return@launch
            suggestState = when (result) {
                is BrainstormingSuggestionResult.Success ->
                    SuggestUiState.Loaded(result.suggestions, result.partial)

                is BrainstormingSuggestionResult.Failed ->
                    SuggestUiState.Error(SUGGESTION_ERROR)

                BrainstormingSuggestionResult.InvalidResponse ->
                    SuggestUiState.Error(SUGGESTION_INVALID)
            }
        }
    }

    fun fillStringList(values: SnapshotStateList<String>, value: String, max: Int) {
        val blankIndex = values.indexOfFirst { it.isBlank() }
        when {
            blankIndex >= 0 -> values[blankIndex] = value
            values.size < max -> values.add(value)
        }
    }

    fun addMechanic(suggestion: BrainstormingSuggestion) {
        val category = suggestion.category ?: return
        val form = MechanicForm(category = category, description = suggestion.text)
        val blankIndex = keyMechanics.indexOfFirst {
            it.category == null && it.description.isBlank()
        }
        when {
            blankIndex >= 0 -> keyMechanics[blankIndex] = form
            keyMechanics.size < MAX_KEY_MECHANICS -> keyMechanics.add(form)
        }
    }

    fun canAcceptSuggestion(suggestion: BrainstormingSuggestion): Boolean =
        when (STEP_KINDS[step]) {
            BrainstormingStep.TITLE -> !suggestion.text.equals(title.trim(), ignoreCase = true)
            BrainstormingStep.GENRE -> genres.none { it.equals(suggestion.text, ignoreCase = true) }

            BrainstormingStep.CORE_LOOP ->
                !suggestion.text.equals(coreLoop.trim(), ignoreCase = true)

            BrainstormingStep.AUDIENCE ->
                !suggestion.text.equals(audience.trim(), ignoreCase = true)

            BrainstormingStep.CONCEPT ->
                !suggestion.text.equals(concept.trim(), ignoreCase = true)

            BrainstormingStep.INSPIRATIONS ->
                inspirations.none { it.equals(suggestion.text, ignoreCase = true) } &&
                    (inspirations.size < MAX_INSPIRATIONS || inspirations.any { it.isBlank() })

            BrainstormingStep.FANTASY ->
                !suggestion.text.equals(fantasy.trim(), ignoreCase = true)

            BrainstormingStep.KEY_MECHANICS -> suggestion.category != null &&
                keyMechanics.none {
                    it.category == suggestion.category &&
                        it.description.equals(suggestion.text, ignoreCase = true)
                } &&
                (keyMechanics.size < MAX_KEY_MECHANICS ||
                    keyMechanics.any { it.category == null && it.description.isBlank() })

            BrainstormingStep.OUT_OF_SCOPE ->
                outOfScope.none { it.equals(suggestion.text, ignoreCase = true) } &&
                    (outOfScope.size < MAX_OUT_OF_SCOPE || outOfScope.any { it.isBlank() })
        }

    fun dismissSuggestion(suggestion: BrainstormingSuggestion) {
        val current = suggestState as? SuggestUiState.Loaded ?: return
        val remaining = current.suggestions.filterNot { it == suggestion }
        suggestState = if (remaining.isEmpty()) {
            SuggestUiState.Idle
        } else {
            current.copy(suggestions = remaining)
        }
    }

    fun acceptSuggestion(suggestion: BrainstormingSuggestion) {
        when (STEP_KINDS[step]) {
            BrainstormingStep.TITLE -> title = suggestion.text
            BrainstormingStep.GENRE -> genres = normalizeGenres(genres + suggestion.text)
            BrainstormingStep.CORE_LOOP -> coreLoop = suggestion.text
            BrainstormingStep.AUDIENCE -> audience = suggestion.text
            BrainstormingStep.CONCEPT -> concept = suggestion.text
            BrainstormingStep.INSPIRATIONS ->
                fillStringList(inspirations, suggestion.text, MAX_INSPIRATIONS)

            BrainstormingStep.FANTASY -> fantasy = suggestion.text
            BrainstormingStep.KEY_MECHANICS -> addMechanic(suggestion)
            BrainstormingStep.OUT_OF_SCOPE ->
                fillStringList(outOfScope, suggestion.text, MAX_OUT_OF_SCOPE)
        }
        dismissSuggestion(suggestion)
    }

    LaunchedEffect(projectKey) {
        resetSuggestions()
    }
    DisposableEffect(Unit) {
        onDispose {
            suggestionJob?.cancel()
            suggestionJob = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .imePadding(),
    ) {
        Text(text = "Brainstorming", style = MaterialTheme.typography.headlineMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    resetSuggestions()
                    onCancel()
                },
            ) {
                Text(text = "Cancelar")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Paso ${step + 1}/$TOTAL_STEPS",
                style = MaterialTheme.typography.titleSmall,
            )
            if (step < TOTAL_STEPS - 1) {
                TextButton(onClick = { save() }) {
                    Text(text = "Guardar")
                }
            }
        }
        LinearProgressIndicator(
            progress = { (step + 1) / TOTAL_STEPS.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Todos los pasos son opcionales.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = STEP_TITLES[step],
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            when (step) {
                0 -> TitleStep(title = title, onTitleChange = { title = it })
                1 -> GenrePicker(selected = genres, onChange = { genres = it })
                2 -> TextStep(
                    value = coreLoop,
                    onValueChange = { coreLoop = it },
                    label = "Core Loop",
                    placeholder = "¿Qué acción central repite el jugador?",
                    minLines = 3,
                )
                3 -> TextStep(
                    value = audience,
                    onValueChange = { audience = it },
                    label = "Audiencia",
                    placeholder = "¿A quién va dirigido el juego?",
                    minLines = 2,
                )
                4 -> TextStep(
                    value = concept,
                    onValueChange = { concept = it },
                    label = "Concepto",
                    placeholder = "Describe la idea central del juego.",
                    minLines = 4,
                )
                5 -> StringListStep(
                    values = inspirations,
                    label = "Inspiración",
                    addLabel = "Añadir inspiración",
                    max = MAX_INSPIRATIONS,
                )
                6 -> TextStep(
                    value = fantasy,
                    onValueChange = { fantasy = it },
                    label = "Fantasía",
                    placeholder = "¿Qué experiencia o poder imagina vivir el jugador?",
                    minLines = 3,
                )
                7 -> MechanicsStep(mechanics = keyMechanics)
                8 -> StringListStep(
                    values = outOfScope,
                    label = "Fuera de alcance",
                    addLabel = "Añadir fuera de alcance",
                    max = MAX_OUT_OF_SCOPE,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            SuggestionSection(
                stepKind = STEP_KINDS[step],
                state = suggestState,
                canAccept = { canAcceptSuggestion(it) },
                onSuggest = { requestSuggestions() },
                onAccept = { acceptSuggestion(it) },
                onIgnore = { dismissSuggestion(it) },
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { startNoteSave() },
                enabled = canSaveNote,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Guardar como Nota")
            }
            noteFeedback?.let { feedback ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { if (step > 0) goToStep(step - 1) },
                enabled = step > 0,
            ) {
                Text(text = "Atrás")
            }
            Spacer(modifier = Modifier.weight(1f))
            if (step < TOTAL_STEPS - 1) {
                Button(onClick = { goToStep(step + 1) }) {
                    Text(text = "Siguiente")
                }
            } else {
                Button(onClick = { save() }) {
                    Text(text = "Guardar")
                }
            }
        }
    }

    saveError?.let { message ->
        AlertDialog(
            onDismissRequest = { saveError = null },
            title = { Text(text = "No se pudo guardar") },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = { saveError = null }) {
                    Text(text = "Aceptar")
                }
            },
        )
    }

    pendingNote?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingNote = null },
            title = { Text(text = "Guardar como Nota") },
            text = {
                Column {
                    Text(text = previewBody(payload.body))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Track: ${payload.track?.code ?: "Sin track"}")
                    Text(
                        text = "Géneros: " +
                            payload.genres.joinToString(", ").ifEmpty { "Ninguno" },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmNoteSave() }) {
                    Text(text = "Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingNote = null }) {
                    Text(text = "Cancelar")
                }
            },
        )
    }

    duplicateMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { duplicateMessage = null },
            title = { Text(text = "Nota duplicada") },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = { duplicateMessage = null }) {
                    Text(text = "Aceptar")
                }
            },
        )
    }

    noteError?.let { message ->
        AlertDialog(
            onDismissRequest = { noteError = null },
            title = { Text(text = "No se pudo guardar la Nota") },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = { noteError = null }) {
                    Text(text = "Aceptar")
                }
            },
        )
    }
}

/** Preview corto del body; la Nota persistida conserva el contenido completo. */
private fun previewBody(body: String): String {
    val byLines = body.lines().take(5).joinToString("\n")
    val clipped = if (byLines.length > 200) byLines.take(200) else byLines
    return if (clipped.length < body.length) "$clipped…" else clipped
}

@Composable
private fun TitleStep(
    title: String,
    onTitleChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text(text = "Título del Proyecto") },
        placeholder = { Text(text = "Ej. Proyecto Aurora") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextStep(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    minLines: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        placeholder = { Text(text = placeholder) },
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StringListStep(
    values: SnapshotStateList<String>,
    label: String,
    addLabel: String,
    max: Int,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        values.forEachIndexed { index, value ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { values[index] = it },
                    label = { Text(text = "$label ${index + 1}") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { values.removeAt(index) }) {
                    Text(text = "Quitar")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = { values.add("") },
            enabled = values.size < max,
        ) {
            Text(text = addLabel)
        }
        Text(
            text = "${values.size}/$max",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun MechanicsStep(
    mechanics: SnapshotStateList<MechanicForm>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        mechanics.forEachIndexed { index, mechanic ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Mecánica ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MechanicCategory.entries.forEach { category ->
                        CategoryChip(
                            label = categoryLabel(category),
                            selected = mechanic.category == category,
                            onClick = {
                                mechanics[index] = mechanic.copy(category = category)
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = mechanic.description,
                    onValueChange = {
                        mechanics[index] = mechanic.copy(description = it)
                    },
                    label = { Text(text = "Descripción") },
                    placeholder = { Text(text = "Describe la mecánica en una frase.") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { mechanics.removeAt(index) }) {
                    Text(text = "Quitar")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = { mechanics.add(MechanicForm()) },
            enabled = mechanics.size < MAX_KEY_MECHANICS,
        ) {
            Text(text = "Añadir mecánica")
        }
        Text(
            text = "${mechanics.size}/$MAX_KEY_MECHANICS",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SuggestionSection(
    stepKind: BrainstormingStep,
    state: SuggestUiState,
    canAccept: (BrainstormingSuggestion) -> Boolean,
    onSuggest: () -> Unit,
    onAccept: (BrainstormingSuggestion) -> Unit,
    onIgnore: (BrainstormingSuggestion) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onSuggest, enabled = state !is SuggestUiState.Loading) {
                Text(text = if (state is SuggestUiState.Loading) "Sugiriendo…" else "Sugerir")
            }
            if (state is SuggestUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        when (state) {
            SuggestUiState.Idle -> Unit
            SuggestUiState.Loading -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Playbook está pensando ideas…",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            is SuggestUiState.Error -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onSuggest) {
                    Text(text = "Reintentar")
                }
            }

            is SuggestUiState.Loaded -> {
                if (state.partial) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Playbook devolvió una sola idea. Puedes pedir de nuevo.",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                state.suggestions.forEach { suggestion ->
                    Spacer(modifier = Modifier.height(8.dp))
                    SuggestionCard(
                        stepKind = stepKind,
                        suggestion = suggestion,
                        enabled = canAccept(suggestion),
                        onAccept = onAccept,
                        onIgnore = onIgnore,
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    stepKind: BrainstormingStep,
    suggestion: BrainstormingSuggestion,
    enabled: Boolean,
    onAccept: (BrainstormingSuggestion) -> Unit,
    onIgnore: (BrainstormingSuggestion) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            suggestion.category?.let { category ->
                Text(
                    text = categoryLabel(category),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(text = suggestion.text, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onAccept(suggestion) },
                    enabled = enabled,
                ) {
                    Text(text = if (isScalarStep(stepKind)) "Usar" else "Añadir")
                }
                TextButton(onClick = { onIgnore(suggestion) }) {
                    Text(text = "Ignorar")
                }
            }
        }
    }
}

private fun isScalarStep(step: BrainstormingStep): Boolean = when (step) {
    BrainstormingStep.TITLE,
    BrainstormingStep.CORE_LOOP,
    BrainstormingStep.AUDIENCE,
    BrainstormingStep.CONCEPT,
    BrainstormingStep.FANTASY,
    -> true

    BrainstormingStep.GENRE,
    BrainstormingStep.INSPIRATIONS,
    BrainstormingStep.KEY_MECHANICS,
    BrainstormingStep.OUT_OF_SCOPE,
    -> false
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
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
        modifier = Modifier
            .semantics { this.selected = selected }
            .clickable(
                onClickLabel = if (selected) {
                    "Quitar categoría $label"
                } else {
                    "Elegir categoría $label"
                },
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

private fun categoryLabel(category: MechanicCategory): String =
    category.code.replaceFirstChar { it.uppercase() }
