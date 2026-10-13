package com.playbook.core.repository

import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.model.NoteLink
import com.playbook.core.model.toDomain

/**
 * Implementación SQLDelight de [NoteLinkRepository].
 *
 * `clock` se inyecta para que los tests sean deterministas; en producción se usa
 * `::currentTimeMillis`.
 */
class SqlDelightNoteLinkRepository(
    private val database: PlaybookDatabase,
    private val clock: () -> Long,
) : NoteLinkRepository {

    override fun upsert(noteAId: String, noteBId: String, score: Double): NoteLink {
        require(noteAId != noteBId) { "un enlace necesita dos Notas distintas" }
        require(score.isFinite()) { "el score del enlace debe ser finito" }

        val (a, b) = if (noteAId < noteBId) noteAId to noteBId else noteBId to noteAId
        val updatedAt = clock()
        database.noteLinkQueries.upsertNoteLink(
            note_a_id = a,
            note_b_id = b,
            score = score,
            updated_at = updatedAt,
        )
        return NoteLink(noteAId = a, noteBId = b, score = score, updatedAt = updatedAt)
    }

    override fun getLinksForNote(noteId: String): List<NoteLink> =
        database.noteLinkQueries.selectLinksForNote(noteId, noteId).executeAsList().map { it.toDomain() }

    override fun getAll(): List<NoteLink> =
        database.noteLinkQueries.selectAllLinks().executeAsList().map { it.toDomain() }

    override fun deleteAll() {
        database.noteLinkQueries.deleteAllLinks()
    }
}
