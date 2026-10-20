package com.playbook.core.ai

import com.playbook.core.diagnostics.DiagnosticSource
import com.playbook.core.diagnostics.Diagnostics
import com.playbook.core.diagnostics.NoOpDiagnostics

/**
 * Resultado de generar una imagen desde un prompt.
 *
 * El caso de uso **no persiste nada**: la UI decide navegar al editor con la
 * imagen en memoria y el guardado existente crea la Nota/Adjunto.
 */
sealed interface ImageGenerationResult {
    /** Imagen generada, con bytes y mime ya validados. */
    data class Generated(val image: GeneratedImage) : ImageGenerationResult

    /** El prompt está vacío o sólo contiene espacios; no se llamó al cliente. */
    data object EmptyPrompt : ImageGenerationResult

    /** El cliente devolvió bytes o mime inválidos (no es una imagen). */
    data object InvalidImage : ImageGenerationResult

    /** El runtime de IA falló (red, cuota, modelo, etc.). */
    data class Failed(val cause: AiClientException) : ImageGenerationResult
}

/**
 * Genera una imagen desde texto con [ImageClient] y valida el resultado antes de
 * exponerlo a la UI.
 *
 * Privacidad: un fallo se registra con [DiagnosticSource.IMAGE] y un resumen
 * estable; nunca se registran el prompt, los bytes, rutas ni ids.
 */
class TextImageGenerationService(
    private val imageClient: ImageClient,
    private val diagnostics: Diagnostics = NoOpDiagnostics,
) {
    suspend fun generate(prompt: String): ImageGenerationResult {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return ImageGenerationResult.EmptyPrompt

        val image = try {
            imageClient.generateImage(trimmed)
        } catch (cause: AiClientException) {
            diagnostics.recordFailure(DiagnosticSource.IMAGE, SUMMARY_FAILED)
            return ImageGenerationResult.Failed(cause)
        }

        val mimeType = image.mimeType.trim()
        if (image.bytes.isEmpty() || !mimeType.isImageMimeType()) {
            diagnostics.recordFailure(DiagnosticSource.IMAGE, SUMMARY_INVALID)
            return ImageGenerationResult.InvalidImage
        }

        return ImageGenerationResult.Generated(GeneratedImage(image.bytes, mimeType))
    }

    companion object {
        /** Resumen estable del diagnóstico de fallo de generación. */
        const val SUMMARY_FAILED: String = "image_generation_failed"

        /** Resumen estable del diagnóstico de imagen inválida. */
        const val SUMMARY_INVALID: String = "image_generation_invalid"
    }
}

private fun String.isImageMimeType(): Boolean =
    isNotEmpty() && startsWith("image/", ignoreCase = true)
