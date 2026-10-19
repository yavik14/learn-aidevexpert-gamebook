package com.playbook.app

import com.playbook.app.voice.VoiceCaptureError

/**
 * Estado visible del dictado inline de Nueva nota.
 *
 * La sesión, el cap de 30s y el audio pendiente los maneja [App]; el editor sólo
 * recibe este estado y emite intenciones.
 */
sealed interface DictationUiState {
    /** No hay sesión (aún no se empezó o se canceló): invita a dictar. */
    data object Idle : DictationUiState

    /**
     * Sesión en curso. [partial] es la transcripción parcial (puede estar vacía)
     * y [elapsedMillis] el tiempo transcurrido desde que arrancó la sesión real
     * (`onStarted`), acotado al cap.
     */
    data class Listening(val partial: String, val elapsedMillis: Long) : DictationUiState

    /** Se pidió el resultado final y el motor está cerrando. */
    data object Processing : DictationUiState

    /** Fallo explícito; [error] se traduce a un mensaje en texto. */
    data class Error(val error: VoiceCaptureError) : DictationUiState
}

/** Cap del dictado inline: a los 30s se detiene automáticamente una sola vez. */
const val DICTATION_MAX_MILLIS: Long = 30_000L

/** Cadencia con la que se actualiza el progreso visible (elapsed / cap). */
const val DICTATION_TICK_MILLIS: Long = 100L

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
 * Anexa el texto dictado al cuerpo con un salto de línea, omitiendo vacíos y sin
 * duplicar espacios en los extremos (el `body` previo se conserva).
 */
fun appendDictation(body: String, dictated: String): String =
    listOf(body.trim(), dictated.trim())
        .filter { it.isNotEmpty() }
        .joinToString("\n")
