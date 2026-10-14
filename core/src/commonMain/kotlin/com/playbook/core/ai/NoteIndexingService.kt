package com.playbook.core.ai

import com.playbook.core.diagnostics.Diagnostics
import com.playbook.core.diagnostics.DiagnosticSource
import com.playbook.core.diagnostics.NoOpDiagnostics
import com.playbook.core.model.Note
import com.playbook.core.model.NoteEmbedding
import com.playbook.core.repository.EmbeddingRepository

/** Resultado de indexar una Nota: éxito con el embedding persistido o fallo. */
sealed interface IndexingResult {
    data class Indexed(val embedding: NoteEmbedding) : IndexingResult
    data class Failed(val cause: AiClientException) : IndexingResult
}

/**
 * Primer consumidor real de [AiClient]: convierte el `body` de una [Note] en un
 * embedding y lo persiste vía [EmbeddingRepository]. `suspend` para que el
 * llamador pueda correrlo fuera del camino de captura (la captura nunca se
 * bloquea por la IA).
 *
 * No toca el `NoteStatus`: un fallo de IA no degrada la Nota. La fuente de verdad
 * de "tiene embedding" es la fila en `embedding`.
 *
 * Los fallos se reportan a [Diagnostics] (`AI_INDEXING`) de forma best-effort,
 * sin propagar la excepción del sink ni registrar el `body` de la Nota.
 *
 * @param diagnostics sink de diagnóstico (default [NoOpDiagnostics]).
 */
class NoteIndexingService(
    private val aiClient: AiClient,
    private val embeddingRepository: EmbeddingRepository,
    private val diagnostics: Diagnostics = NoOpDiagnostics,
) {
    suspend fun index(note: Note): IndexingResult {
        val embeddings = try {
            aiClient.embed(listOf(note.body))
        } catch (cause: AiClientException) {
            diagnostics.recordFailure(DiagnosticSource.AI_INDEXING, SUMMARY_FAILED, cause.message)
            return IndexingResult.Failed(cause)
        }

        if (embeddings.size != 1) {
            val cause = AiClientException(
                "Se esperaba exactamente 1 embedding, se recibieron ${embeddings.size}",
            )
            diagnostics.recordFailure(DiagnosticSource.AI_INDEXING, SUMMARY_FAILED, cause.message)
            return IndexingResult.Failed(cause)
        }

        val values = embeddings.single().values
        if (values.isEmpty()) {
            val cause = AiClientException("El runtime devolvió un embedding de dimensión 0")
            diagnostics.recordFailure(DiagnosticSource.AI_INDEXING, SUMMARY_FAILED, cause.message)
            return IndexingResult.Failed(cause)
        }

        return IndexingResult.Indexed(embeddingRepository.upsert(note.id, values))
    }

    private companion object {
        /** Resumen estable (sin datos del usuario) para los fallos de indexado. */
        const val SUMMARY_FAILED = "indexing_failed"
    }
}
