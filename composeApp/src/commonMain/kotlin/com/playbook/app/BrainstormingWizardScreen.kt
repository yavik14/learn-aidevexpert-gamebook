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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.playbook.core.model.GameProject
import com.playbook.core.model.GameProjectDraft
import com.playbook.core.model.KeyMechanic
import com.playbook.core.model.MechanicCategory
import com.playbook.core.model.normalizeGenres

private const val TOTAL_STEPS = 9
private const val MAX_INSPIRATIONS = 3
private const val MAX_OUT_OF_SCOPE = 3
private const val MAX_KEY_MECHANICS = 5

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

/** Borrador local de una mecánica; la categoría puede quedar sin elegir. */
private data class MechanicForm(
    val category: MechanicCategory? = null,
    val description: String = "",
)

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
    onSave: (GameProjectDraft) -> Boolean,
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

    fun save() {
        val incompleteMechanic = keyMechanics.any {
            it.description.isNotBlank() && it.category == null
        }
        if (incompleteMechanic) {
            saveError = "Elegí una categoría para cada mecánica con descripción."
            return
        }

        val draft = GameProjectDraft(
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

        if (!onSave(draft)) {
            saveError = "No se pudo guardar el Proyecto. Intentá de nuevo."
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
            TextButton(onClick = onCancel) {
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
                    placeholder = "Describí la idea central del juego.",
                    minLines = 4,
                )
                5 -> StringListStep(
                    values = inspirations,
                    label = "Inspiración",
                    addLabel = "Agregar inspiración",
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
                    addLabel = "Agregar fuera de alcance",
                    max = MAX_OUT_OF_SCOPE,
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
                onClick = { if (step > 0) step-- },
                enabled = step > 0,
            ) {
                Text(text = "Atrás")
            }
            Spacer(modifier = Modifier.weight(1f))
            if (step < TOTAL_STEPS - 1) {
                Button(onClick = { step++ }) {
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
                    placeholder = { Text(text = "Describí la mecánica en una frase.") },
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
            Text(text = "Agregar mecánica")
        }
        Text(
            text = "${mechanics.size}/$MAX_KEY_MECHANICS",
            style = MaterialTheme.typography.labelMedium,
        )
    }
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
