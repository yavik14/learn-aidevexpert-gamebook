package com.playbook.core.ai

import kotlinx.coroutines.CancellationException

/** Límite de caracteres que acepta el modelo `tts` del proveedor. */
const val READ_ALOUD_MAX_CHARS: Int = 4096

/**
 * Normaliza un texto para lectura en voz alta: `trim`, vacío → `null` y recorte
 * determinista a [READ_ALOUD_MAX_CHARS] caracteres. Aplica a Q&A, Nota y GDD.
 */
fun normalizeReadAloudText(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    return if (trimmed.length > READ_ALOUD_MAX_CHARS) {
        trimmed.take(READ_ALOUD_MAX_CHARS)
    } else {
        trimmed
    }
}

/** Resultado de sintetizar un texto para lectura en voz alta. */
sealed interface ReadAloudResult {
    /** Audio listo para reproducir en memoria. */
    data class Ready(val audio: SynthesizedAudio) : ReadAloudResult

    /** El texto estaba vacío o sólo tenía espacios: no se llamó al proveedor. */
    data object EmptyText : ReadAloudResult

    /** Fallo del runtime o audio inválido; la UI puede reintentar. */
    data class Failed(val cause: Throwable?) : ReadAloudResult
}

/**
 * Caso de uso de lectura en voz alta sobre [TtsClient].
 *
 * Normaliza el texto, no llama al proveedor si queda vacío y valida que el audio
 * tenga bytes y mime. No persiste, no diagnostica contenido y no conoce UI ni
 * plataforma. Los fallos se devuelven como [ReadAloudResult.Failed]; una
 * cancelación del llamador se propaga.
 */
class ReadAloudService(
    private val ttsClient: TtsClient,
) {
    suspend fun synthesize(rawText: String): ReadAloudResult {
        val text = normalizeReadAloudText(rawText) ?: return ReadAloudResult.EmptyText
        return try {
            val audio = ttsClient.synthesize(text)
            if (audio.bytes.isEmpty() || audio.mimeType.isBlank()) {
                ReadAloudResult.Failed(cause = null)
            } else {
                ReadAloudResult.Ready(audio)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Throwable) {
            ReadAloudResult.Failed(cause = failure)
        }
    }
}
