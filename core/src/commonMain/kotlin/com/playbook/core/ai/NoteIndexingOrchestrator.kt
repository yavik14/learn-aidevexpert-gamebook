package com.playbook.core.ai

import com.playbook.core.model.Note
import com.playbook.core.model.NoteStatus
import com.playbook.core.repository.NoteRepository

/**
 * Orquesta el indexado asíncrono de una [Note]: reutiliza [NoteIndexingService]
 * (genera y persiste el embedding vía `AiClient`) y persiste la **transición de
 * [NoteStatus]** derivada del resultado.
 *
 * Regla de transición (la captura nunca se bloquea por IA):
 * 1. Éxito ([IndexingResult.Indexed]) → `INDEXED`.
 * 2. Fallo y la Nota **no** estaba `INDEXED` → `PENDING` (reintentable).
 * 3. Fallo y la Nota **ya estaba `INDEXED`** (re-indexado) → se mantiene
 *    `INDEXED`; el embedding previo queda intacto y un fallo no degrada una Nota
 *    ya indexada.
 *
 * **No escribe `FAILED`**: la promoción `pendiente → fallida` exige un
 * presupuesto de reintentos/detección de conectividad que pertenece a
 * `offline-pending-retry` (fuera de alcance). Tampoco clasifica ni etiqueta la
 * Nota ni crea Enlaces (`semantic-linking`).
 *
 * Es `suspend` y no agenda nada: el llamador decide cuándo invocarlo (fuera del
 * camino de captura). El scheduling/reintento es de `offline-pending-retry`.
 */
class NoteIndexingOrchestrator(
    private val indexingService: NoteIndexingService,
    private val noteRepository: NoteRepository,
) {
    /** Indexa [note] y persiste la transición de [NoteStatus]. Devuelve el estado resultante. */
    suspend fun index(note: Note): NoteStatus {
        val result = indexingService.index(note) // nunca lanza por fallo de IA
        val target = when {
            result is IndexingResult.Indexed -> NoteStatus.INDEXED
            // No degradar una Nota ya indexada ante un fallo de re-indexado.
            note.status == NoteStatus.INDEXED -> NoteStatus.INDEXED
            else -> NoteStatus.PENDING
        }
        noteRepository.updateStatus(note.id, target)
        return target
    }
}
