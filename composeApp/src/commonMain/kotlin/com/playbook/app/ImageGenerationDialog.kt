package com.playbook.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Estado visible del diálogo de generación de imagen desde texto. */
sealed interface ImageGenerationUiState {
    data object Idle : ImageGenerationUiState
    data object Generating : ImageGenerationUiState
    data class Error(val message: String) : ImageGenerationUiState
}

/**
 * Diálogo para describir y generar una imagen con IA.
 *
 * "Generar" queda deshabilitado con prompt vacío o mientras corre la llamada;
 * "Cancelar" (y el cierre del diálogo) delega en [onCancel], que cancela el job
 * en [App]. El error se muestra con texto, no sólo color.
 */
@Composable
fun ImageGenerationDialog(
    prompt: String,
    onPromptChange: (String) -> Unit,
    state: ImageGenerationUiState,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
) {
    val generating = state is ImageGenerationUiState.Generating
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = "Generar imagen con IA") },
        text = {
            Column {
                Text(
                    text = "Describí la imagen que querés crear.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    label = { Text(text = "Descripción") },
                    placeholder = { Text(text = "Un jefe final con armadura de cristal…") },
                    enabled = !generating,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                when (state) {
                    ImageGenerationUiState.Idle -> Unit
                    ImageGenerationUiState.Generating -> Text(
                        text = "Generando…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is ImageGenerationUiState.Error -> Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onGenerate,
                enabled = prompt.isNotBlank() && !generating,
            ) {
                Text(text = if (generating) "Generando…" else "Generar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = "Cancelar")
            }
        },
    )
}
