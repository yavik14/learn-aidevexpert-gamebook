package com.playbook.core.ai

import com.playbook.core.model.NoteStatus
import com.playbook.core.repository.NoteRepository

/**
 * Resumen de una corrida de [PendingIndexingRetry.retryPending].
 *
 * @property available si la IA estaba disponible (`false` ⇒ la corrida fue no-op).
 * @property attempted cuántas Notas `PENDING` se intentaron indexar.
 * @property indexed cuántas terminaron `INDEXED`.
 * @property stillPending cuántas siguen `PENDING` (fallo bajo el presupuesto).
 * @property failed cuántas se promovieron a `FAILED` (presupuesto agotado).
 */
data class RetrySummary(
    val available: Boolean,
    val attempted: Int,
    val indexed: Int,
    val stillPending: Int,
    val failed: Int,
)

/**
 * Cierra el ciclo offline/online del indexado: reintenta las Notas `PENDING`
 * cuando la IA vuelve a estar disponible y promueve a `FAILED` las que fallan
 * persistentemente tras agotar [budget] intentos.
 *
 * Reglas:
 * 1. Si [AiAvailability.isAvailable] es `false`: no-op (no toca la base, no llama
 *    a `AiClient`); el resumen devuelve `available = false` y contadores en `0`.
 * 2. Si está disponible: por cada Nota `PENDING` (`NoteRepository.getByStatus`),
 *    corre `NoteIndexingOrchestrator.index` (que fija `INDEXED`/`PENDING` y nunca
 *    escribe `FAILED`). Si queda `INDEXED` → reset del presupuesto
 *    (`setIndexAttempts(id, 0)`). Si sigue `PENDING` → `index_attempts + 1`; al
 *    alcanzar [budget] se promueve a `FAILED` (terminal hasta que una edición la
 *    reindexe, `NoteIndexingOrchestrator`).
 * 3. Sólo participan las `PENDING`: `CAPTURED`/`INDEXED`/`FAILED` no se tocan.
 *
 * `suspend` porque la disponibilidad y el indexado son asíncronos. No agenda ni
 * detecta conectividad real: el llamador decide cuándo invocarlo (hoy, el
 * arranque de la app).
 *
 * @property budget presupuesto de intentos fallidos antes de `FAILED` (`> 0`).
 */
class PendingIndexingRetry(
    private val orchestrator: NoteIndexingOrchestrator,
    private val noteRepository: NoteRepository,
    private val availability: AiAvailability = AlwaysAvailableAiAvailability,
    private val budget: Int = DEFAULT_RETRY_BUDGET,
) {
    init {
        require(budget > 0) { "budget debe ser > 0 (se recibió $budget)" }
    }

    suspend fun retryPending(): RetrySummary {
        if (!availability.isAvailable()) {
            return RetrySummary(
                available = false,
                attempted = 0,
                indexed = 0,
                stillPending = 0,
                failed = 0,
            )
        }

        var attempted = 0
        var indexed = 0
        var stillPending = 0
        var failed = 0

        for (note in noteRepository.getByStatus(NoteStatus.PENDING)) {
            attempted++
            val outcome = orchestrator.index(note)
            if (outcome == NoteStatus.INDEXED) {
                noteRepository.setIndexAttempts(note.id, 0)
                indexed++
            } else {
                val next = note.indexAttempts + 1
                noteRepository.setIndexAttempts(note.id, next)
                if (next >= budget) {
                    noteRepository.updateStatus(note.id, NoteStatus.FAILED)
                    failed++
                } else {
                    stillPending++
                }
            }
        }

        return RetrySummary(
            available = true,
            attempted = attempted,
            indexed = indexed,
            stillPending = stillPending,
            failed = failed,
        )
    }

    companion object {
        /** Presupuesto por defecto de intentos fallidos antes de `FAILED`. */
        const val DEFAULT_RETRY_BUDGET = 3
    }
}
