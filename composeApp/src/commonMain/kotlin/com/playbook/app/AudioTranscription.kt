package com.playbook.app

/**
 * Estado visible de la transcripción diferida del Adjunto `AUDIO` de una Nota.
 *
 * [App] es dueña del job y del último texto; el editor sólo recibe este estado y
 * emite intenciones. `Idle` cubre "aún no se transcribió" y el éxito (el texto ya
 * se aplicó al borrador). La transcripción **nunca** se guarda sola: el usuario
 * confirma con "Guardar".
 */
sealed interface AudioTranscriptionUiState {
    /** Sin transcripción en curso ni error pendiente; la acción está disponible. */
    data object Idle : AudioTranscriptionUiState

    /** Se pidió la transcripción y se espera al transcriptor. */
    data object Transcribing : AudioTranscriptionUiState

    /** El transcriptor devolvió texto vacío o sólo espacios. */
    data object Empty : AudioTranscriptionUiState

    /** El Adjunto AUDIO ya no se puede leer (archivo ausente/ilegible). */
    data object Unavailable : AudioTranscriptionUiState

    /** Falló la IA (red/cuota/modelo) o hubo un fallo inesperado. */
    data object Failed : AudioTranscriptionUiState
}

/** Aviso tras aplicar el texto al borrador; recuerda que hay que Guardar. */
const val TRANSCRIPTION_APPLIED_NOTICE: String =
    "Transcripción aplicada. Guardá para conservarla."

/** Mensaje textual del estado (o `null` si no hay nada que mostrar). */
fun AudioTranscriptionUiState.message(): String? = when (this) {
    AudioTranscriptionUiState.Idle,
    AudioTranscriptionUiState.Transcribing,
    -> null

    AudioTranscriptionUiState.Empty -> "La transcripción quedó vacía"
    AudioTranscriptionUiState.Unavailable -> "No se pudo leer el audio adjunto"
    AudioTranscriptionUiState.Failed ->
        "No se pudo transcribir. Revisá la conexión e intentá de nuevo"
}

/** Etiqueta del botón: invita a transcribir o a reintentar tras un error. */
fun AudioTranscriptionUiState.actionLabel(): String = when (this) {
    AudioTranscriptionUiState.Transcribing -> "Transcribiendo…"
    AudioTranscriptionUiState.Empty,
    AudioTranscriptionUiState.Unavailable,
    AudioTranscriptionUiState.Failed,
    -> "Reintentar"

    AudioTranscriptionUiState.Idle -> "Transcribir"
}

/**
 * Etiqueta determinista de un audio: ordinal por orden del repositorio
 * (`created_at ASC, id ASC`) + tamaño legible. No hay playback ni metadatos de
 * origen; el ordinal es sólo para elegir.
 */
fun audioAttachmentLabel(index: Int, byteSize: Long): String =
    "Audio ${index + 1} · ${formatAudioByteSize(byteSize)}"

/** Tamaño legible sin depender de `String.format` (común a Android/iOS). */
fun formatAudioByteSize(byteSize: Long): String {
    if (byteSize < KIB) return "$byteSize B"
    if (byteSize < MIB) return "${(byteSize + KIB / 2) / KIB} KB"
    val tenths = (byteSize * 10 + MIB / 2) / MIB
    return "${tenths / 10}.${tenths % 10} MB"
}

private const val KIB: Long = 1024L
private const val MIB: Long = 1024L * 1024L
