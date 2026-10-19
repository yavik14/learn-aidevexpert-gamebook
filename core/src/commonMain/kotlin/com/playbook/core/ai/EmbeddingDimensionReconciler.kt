package com.playbook.core.ai

import com.playbook.core.model.NoteStatus
import com.playbook.core.repository.EmbeddingRepository
import com.playbook.core.repository.NoteRepository

/** Resultado de reconciliar embeddings contra la dimensión del runtime activo. */
data class ReconcileSummary(
    val expectedDimension: Int?,
    val cleared: Int,
)

/**
 * Borra los embeddings cuya dimensión no coincide con la del runtime activo y
 * deja sus Notas `PENDING` con el presupuesto de reintentos en `0`, para que el
 * reintento de arranque las re-indexe con el modelo actual.
 *
 * Es un efecto de sistema local (sin IA): no llama al runtime. `null` es no-op
 * (modo sin dimensión conocida).
 */
class EmbeddingDimensionReconciler(
    private val embeddingRepository: EmbeddingRepository,
    private val noteRepository: NoteRepository,
) {
    fun reconcile(expectedDimension: Int?): ReconcileSummary {
        if (expectedDimension == null) return ReconcileSummary(null, 0)
        require(expectedDimension > 0) { "expectedDimension debe ser > 0" }

        var cleared = 0
        embeddingRepository.getAll()
            .filter { it.values.size != expectedDimension }
            .forEach { embedding ->
                embeddingRepository.deleteByNoteId(embedding.noteId)
                noteRepository.updateStatus(embedding.noteId, NoteStatus.PENDING)
                noteRepository.setIndexAttempts(embedding.noteId, 0)
                cleared++
            }

        return ReconcileSummary(expectedDimension = expectedDimension, cleared = cleared)
    }
}
