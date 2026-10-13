package com.playbook.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Estado visible de la pantalla de consulta (RAG).
 *
 * La pantalla es "tonta": recibe este estado y emite intenciones; la invocación
 * `suspend` de `RagQueryService` la maneja [App].
 */
sealed interface AskUiState {
    /** Aún no se preguntó (o se reinició): invita a escribir una pregunta. */
    data object Idle : AskUiState

    /** Consulta en curso: se muestra el estado de carga. */
    data object Asking : AskUiState

    /** Respuesta generada; la UI muestra **sólo** el texto (sin citas). */
    data class Answered(val text: String) : AskUiState

    /** No hay Notas con embedding: el runtime no se invocó. */
    data object NoIndexedNotes : AskUiState

    /** El runtime falló: se muestra un error con "Reintentar". */
    data object Error : AskUiState
}

/**
 * Pantalla de consulta en lenguaje natural sobre las Notas propias.
 *
 * - `Idle`: invita a escribir y habilita "Preguntar".
 * - `Asking`: muestra "Pensando…" y deshabilita la acción.
 * - `Answered`: muestra el texto de la respuesta (sin listar ni navegar a fuentes).
 * - `NoIndexedNotes`: mensaje explícito.
 * - `Error`: mensaje de error con "Reintentar".
 *
 * Todos los estados se comunican con texto (nunca sólo color), según la regla de
 * accesibilidad de `DESIGN.md`.
 */
@Composable
fun AskNotesScreen(
    state: AskUiState,
    onAsk: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var question by remember { mutableStateOf("") }
    val canSubmit = question.isNotBlank() && state != AskUiState.Asking

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(text = "Preguntar", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "La respuesta se arma sólo con tus notas.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text(text = "Pregunta") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        AskResult(state = state, modifier = Modifier.fillMaxWidth().weight(1f))
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(text = "Volver")
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { onAsk(question) },
                enabled = canSubmit,
            ) {
                Text(text = primaryActionLabel(state))
            }
        }
    }
}

@Composable
private fun AskResult(state: AskUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        when (state) {
            AskUiState.Idle -> Text(
                text = "Escribí una pregunta sobre tus notas.",
                style = MaterialTheme.typography.bodyLarge,
            )

            AskUiState.Asking -> Text(
                text = "Pensando…",
                style = MaterialTheme.typography.titleMedium,
            )

            is AskUiState.Answered -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(text = "Respuesta", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = state.text, style = MaterialTheme.typography.bodyLarge)
                }
            }

            AskUiState.NoIndexedNotes -> Text(
                text = "Todavía no hay notas indexadas. Guardá una nota de texto y volvé a preguntar.",
                style = MaterialTheme.typography.bodyLarge,
            )

            AskUiState.Error -> Text(
                text = "No se pudo responder. Revisá el runtime de IA e intentá de nuevo.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun primaryActionLabel(state: AskUiState): String = when (state) {
    AskUiState.Asking -> "Pensando…"
    AskUiState.Error -> "Reintentar"
    else -> "Preguntar"
}
