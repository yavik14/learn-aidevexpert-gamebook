package com.playbook.core.repository

import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.model.NoteEmbedding
import com.playbook.core.model.toDomain

/**
 * Implementación SQLDelight de [EmbeddingRepository].
 *
 * `clock` se inyecta para que los tests sean deterministas y para no acoplar el
 * dominio a una fuente de tiempo concreta. En producción se usa
 * `::currentTimeMillis`.
 */
class SqlDelightEmbeddingRepository(
    private val database: PlaybookDatabase,
    private val clock: () -> Long,
) : EmbeddingRepository {

    override fun upsert(noteId: String, values: List<Float>): NoteEmbedding {
        require(values.isNotEmpty()) { "values no puede estar vacío" }
        val indexedAt = clock()
        database.embeddingQueries.upsertEmbedding(
            note_id = noteId,
            dimension = values.size.toLong(),
            vector = EmbeddingBlobCodec.encode(values),
            indexed_at = indexedAt,
        )
        return NoteEmbedding(noteId = noteId, values = values, indexedAt = indexedAt)
    }

    override fun getByNoteId(noteId: String): NoteEmbedding? =
        database.embeddingQueries.selectEmbeddingByNoteId(noteId).executeAsOneOrNull()?.toDomain()

    override fun getAll(): List<NoteEmbedding> =
        database.embeddingQueries.selectAllEmbeddings().executeAsList().map { it.toDomain() }

    override fun deleteByNoteId(noteId: String): Boolean {
        val rowsAffected = database.embeddingQueries.deleteEmbeddingByNoteId(noteId).value
        return rowsAffected > 0
    }
}
