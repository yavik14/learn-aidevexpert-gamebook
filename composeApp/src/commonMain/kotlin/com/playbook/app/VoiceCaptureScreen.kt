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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.playbook.app.voice.VoiceCaptureError

/**
 * Estado visible de la pantalla de captura por voz.
 *
 * La pantalla es "tonta": recibe este estado y emite intenciones; la sesión del
 * [com.playbook.app.voice.VoiceTranscriber] y la creación de la Nota las maneja
 * [App].
 */
sealed interface VoiceUiState {
    /** Aún no se empezó (o se canceló): invita a grabar. */
    data object Idle : VoiceUiState

    /** Dictado en curso; [partial] es la transcripción parcial (puede estar vacía). */
    data class Listening(val partial: String) : VoiceUiState

    /** Se pidió el resultado final y el motor está cerrando. */
    data object Processing : VoiceUiState

    /** Fallo explícito; [error] se traduce a un mensaje en texto. */
    data class Error(val error: VoiceCaptureError) : VoiceUiState
}

/** Mensaje en texto del error: la UI nunca comunica el estado sólo con color. */
fun VoiceCaptureError.message(): String = when (this) {
    VoiceCaptureError.PERMISSION_DENIED ->
        "Permiso de micrófono denegado. Habilitalo en los ajustes del sistema y reintentá."
    VoiceCaptureError.UNAVAILABLE ->
        "El dictado no está disponible en este dispositivo."
    VoiceCaptureError.NO_MATCH ->
        "No se escuchó nada. Probá de nuevo."
    VoiceCaptureError.NETWORK ->
        "No hay conexión para transcribir. Revisá tu red e intentá otra vez."
    VoiceCaptureError.BUSY ->
        "El reconocimiento está ocupado. Reintentá en un momento."
    VoiceCaptureError.UNKNOWN ->
        "No se pudo transcribir. Probá de nuevo."
}

/**
 * Pantalla de captura por voz (dictado → Nota).
 *
 * - `Idle`/`Error` ofrecen "Grabar"/"Reintentar".
 * - `Listening` muestra "Escuchando…" + transcripción parcial y ofrece "Detener".
 * - `Processing` muestra "Transcribiendo…" con la acción primaria deshabilitada.
 * - "Cancelar" siempre está disponible y vuelve a la lista liberando el micrófono.
 */
@Composable
fun VoiceCaptureScreen(
    state: VoiceUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(text = "Dictar nota", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        VoiceStatus(state = state, modifier = Modifier.fillMaxWidth().weight(1f))
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(text = "Cancelar")
            }
            Spacer(modifier = Modifier.weight(1f))
            PrimaryVoiceAction(state = state, onStart = onStart, onStop = onStop)
        }
    }
}

@Composable
private fun VoiceStatus(state: VoiceUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            VoiceUiState.Idle -> {
                Text(
                    text = "Tocá Grabar y dictá tu idea.",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            is VoiceUiState.Listening -> {
                Text(text = "Escuchando…", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.partial.ifBlank { "Esperando voz…" },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            VoiceUiState.Processing -> {
                Text(text = "Transcribiendo…", style = MaterialTheme.typography.titleMedium)
            }

            is VoiceUiState.Error -> {
                Text(
                    text = "No se pudo dictar",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.error.message(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun PrimaryVoiceAction(
    state: VoiceUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    when (state) {
        is VoiceUiState.Listening -> Button(onClick = onStop) {
            Text(text = "Detener")
        }

        VoiceUiState.Processing -> Button(onClick = {}, enabled = false) {
            Text(text = "Transcribiendo…")
        }

        VoiceUiState.Idle -> Button(onClick = onStart) {
            Text(text = "Grabar")
        }

        is VoiceUiState.Error -> Button(onClick = onStart) {
            Text(text = "Reintentar")
        }
    }
}
