package com.playbook.core.ai

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
 */
class NoteIndexingService(
    private val aiClient: AiClient,
    private val embeddingRepository: EmbeddingRepository,
) {
    suspend fun index(note: Note): IndexingResult {
        val embeddings = try {
            aiClient.embed(listOf(note.body))
        } catch (cause: AiClientException) {
            return IndexingResult.Failed(cause)
        }

        if (embeddings.size != 1) {
            return IndexingResult.Failed(
                AiClientException(
                    "Se esperaba exactamente 1 embedding, se recibieron ${embeddings.size}",
                ),
            )
        }

        val values = embeddings.single().values
        if (values.isEmpty()) {
            return IndexingResult.Failed(
                AiClientException("El runtime devolvió un embedding de dimensión 0"),
            )
        }

        return IndexingResult.Indexed(embeddingRepository.upsert(note.id, values))
    }
}
