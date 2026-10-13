package com.playbook.core.linking

import com.playbook.core.repository.EmbeddingRepository
import com.playbook.core.repository.NoteLinkRepository

/** Resumen de un recomputo completo de enlaces. */
data class LinkingSummary(
    val notesConsidered: Int,
    val linksWritten: Int,
)

/**
 * Segundo consumidor del storage de embeddings: calcula la similitud coseno de
 * los pares de Notas con embedding y persiste los Enlaces por encima del umbral.
 *
 * Es una operación **explícita, determinista e idempotente** ([recomputeAll]):
 * borra todos los enlaces y los reconstruye desde los embeddings actuales. El
 * trigger (al indexar, al editar, offline) se difiere a features posteriores.
 *
 * No toca [com.playbook.core.model.NoteStatus] ni los embeddings.
 *
 * @param threshold umbral inclusivo `score >= threshold`; inyectable para poder
 * afinarlo cuando llegue el runtime real. Debe estar en `[-1, 1]`.
 */
class SemanticLinkingService(
    private val embeddingRepository: EmbeddingRepository,
    private val noteLinkRepository: NoteLinkRepository,
    private val threshold: Double = DEFAULT_SIMILARITY_THRESHOLD,
) {

    fun recomputeAll(): LinkingSummary {
        require(threshold in -1.0..1.0) { "threshold debe estar en [-1, 1]" }

        // `getAll()` está ordenado por `note_id`: el recomputo es determinista.
        val embeddings = embeddingRepository.getAll()

        // Borrado + reconstrucción: si el proceso muere a mitad, los enlaces se
        // regeneran en el próximo recomputo (biblioteca personal, sin duplicados
        // ni filas viejas).
        noteLinkRepository.deleteAll()

        var linksWritten = 0
        for (i in embeddings.indices) {
            for (j in i + 1 until embeddings.size) {
                val a = embeddings[i]
                val b = embeddings[j]
                // `null` (dimensión distinta o norma 0) o por debajo del umbral → se omite.
                val score = CosineSimilarity.score(a.values, b.values) ?: continue
                if (score >= threshold) {
                    noteLinkRepository.upsert(a.noteId, b.noteId, score)
                    linksWritten++
                }
            }
        }

        return LinkingSummary(notesConsidered = embeddings.size, linksWritten = linksWritten)
    }

    companion object {
        /** Heurística de "alta similitud" para coseno en `[-1, 1]`. */
        const val DEFAULT_SIMILARITY_THRESHOLD: Double = 0.8
    }
}
