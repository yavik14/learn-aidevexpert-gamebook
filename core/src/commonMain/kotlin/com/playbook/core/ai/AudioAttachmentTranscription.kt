package com.playbook.core.ai

import com.playbook.core.diagnostics.DiagnosticSource
import com.playbook.core.diagnostics.Diagnostics
import com.playbook.core.diagnostics.NoOpDiagnostics
import com.playbook.core.model.AttachmentKind
import com.playbook.core.repository.AttachmentRepository
import com.playbook.core.storage.AttachmentFileStore

/**
 * Resultado de transcribir el Adjunto `AUDIO` de una Nota.
 *
 * El caso de uso **no persiste nada**: el editor decide cómo aplicar el texto al
 * borrador y el guardado existente re-indexa la Nota. Los estados distinguen
 * ausencia del Adjunto, archivo ilegible, transcripción vacía y fallo de IA.
 */
sealed interface AudioTranscriptionResult {
    /** Texto transcripto, ya normalizado con `trim`. */
    data class Transcribed(val text: String) : AudioTranscriptionResult

    /** El Adjunto no existe, no pertenece a la Nota o no es `AUDIO`. */
    data object AudioNotFound : AudioTranscriptionResult

    /** La fila existe pero los bytes no se pueden leer (archivo ausente/ilegible). */
    data object AudioUnavailable : AudioTranscriptionResult

    /** El transcriptor devolvió texto vacío o sólo espacios. */
    data object EmptyTranscription : AudioTranscriptionResult

    /** El runtime de IA falló (red, cuota, modelo, etc.). */
    data class Failed(val cause: AiClientException) : AudioTranscriptionResult
}

/**
 * Transcripción **diferida** del audio original de un dictado.
 *
 * Valida que [attachmentId] sea un Adjunto `AUDIO` **de** [noteId], lee los bytes
 * exactos con [AttachmentFileStore.read] y llama
 * [TranscribeClient.transcribe] una sola vez. No escribe repositorios ni indexa:
 * esas responsabilidades siguen en el editor/[App].
 *
 * Privacidad: un fallo de IA se registra con [DiagnosticSource.AI_TRANSCRIBE] y
 * el resumen estable [SUMMARY_FAILED]; nunca se registran texto, bytes, rutas ni
 * ids. Se omite el `detail` a propósito para no filtrar datos del archivo/Nota.
 */
class AudioAttachmentTranscriptionService(
    private val transcribeClient: TranscribeClient,
    private val attachmentRepository: AttachmentRepository,
    private val fileStore: AttachmentFileStore,
    private val diagnostics: Diagnostics = NoOpDiagnostics,
) {
    suspend fun transcribe(noteId: String, attachmentId: String): AudioTranscriptionResult {
        val attachment = attachmentRepository.getByNoteId(noteId)
            .firstOrNull { it.id == attachmentId && it.kind == AttachmentKind.AUDIO }
            ?: return AudioTranscriptionResult.AudioNotFound

        val bytes = fileStore.read(attachment.filePath)
            ?: return AudioTranscriptionResult.AudioUnavailable

        val text = try {
            transcribeClient.transcribe(bytes, attachment.mimeType)
        } catch (cause: AiClientException) {
            diagnostics.recordFailure(DiagnosticSource.AI_TRANSCRIBE, SUMMARY_FAILED)
            return AudioTranscriptionResult.Failed(cause)
        }

        val trimmed = text.trim()
        return if (trimmed.isEmpty()) {
            AudioTranscriptionResult.EmptyTranscription
        } else {
            AudioTranscriptionResult.Transcribed(trimmed)
        }
    }

    companion object {
        /** Resumen estable del diagnóstico de fallo de transcripción. */
        const val SUMMARY_FAILED: String = "transcription_failed"
    }
}
