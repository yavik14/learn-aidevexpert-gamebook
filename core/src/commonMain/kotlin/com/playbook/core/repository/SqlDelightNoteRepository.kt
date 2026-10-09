package com.playbook.core.repository

import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.normalizeTags
import com.playbook.core.model.toDomain

/**
 * Implementación SQLDelight de [NoteRepository].
 *
 * `idFactory` y `clock` se inyectan para que los tests sean deterministas y para
 * no acoplar el dominio a una fuente de tiempo/identidad concreta. En producción
 * se usan los defaults de plataforma (`randomNoteId()` / `currentTimeMillis()`).
 *
 * Las etiquetas (`note_tag`) se persisten en la misma transacción que la nota y
 * se normalizan con [normalizeTags]. `update` reemplaza el conjunto completo de
 * etiquetas; `delete` limpia las filas de etiquetas de la nota.
 */
class SqlDelightNoteRepository(
    private val database: PlaybookDatabase,
    private val idFactory: () -> String,
    private val clock: () -> Long,
) : NoteRepository {

    override fun create(draft: NoteDraft): Note {
        val now = clock()
        val tags = normalizeTags(draft.tags)
        val note = Note(
            id = idFactory(),
            owner = draft.owner,
            body = draft.body,
            track = draft.track,
            status = draft.status,
            createdAt = now,
            updatedAt = now,
            tags = tags,
        )
        database.transaction {
            insertNoteRow(note)
            replaceTags(note.id, tags)
        }
        return note
    }

    override fun getAll(): List<Note> {
        // Una sola consulta de etiquetas agrupada en memoria evita el N+1.
        val tagsByNote = database.noteTagQueries.selectAllNoteTags()
            .executeAsList()
            .groupBy({ it.note_id }, { it.label })
        return database.noteQueries.selectAllNotes().executeAsList().map { row ->
            row.toDomain(tagsByNote[row.id].orEmpty())
        }
    }

    override fun getById(id: String): Note? {
        val row = database.noteQueries.selectNoteById(id).executeAsOneOrNull() ?: return null
        val tags = database.noteTagQueries.selectTagsByNoteId(id).executeAsList()
        return row.toDomain(tags)
    }

    override fun update(note: Note): Boolean {
        val tags = normalizeTags(note.tags)
        return database.transactionWithResult {
            val rowsAffected = database.noteQueries.updateNote(
                body = note.body,
                track = note.track?.code,
                status = note.status.code,
                updated_at = clock(),
                id = note.id,
            ).value
            if (rowsAffected > 0) {
                replaceTags(note.id, tags)
            }
            rowsAffected > 0
        }
    }

    override fun delete(id: String): Boolean = database.transactionWithResult {
        val rowsAffected = database.noteQueries.deleteNoteById(id).value
        database.noteTagQueries.deleteTagsByNoteId(id)
        rowsAffected > 0
    }

    private fun insertNoteRow(note: Note) {
        database.noteQueries.insertNote(
            id = note.id,
            owner = note.owner,
            body = note.body,
            track = note.track?.code,
            status = note.status.code,
            created_at = note.createdAt,
            updated_at = note.updatedAt,
        )
    }

    private fun replaceTags(noteId: String, tags: List<String>) {
        database.noteTagQueries.deleteTagsByNoteId(noteId)
        tags.forEach { label ->
            database.noteTagQueries.insertNoteTag(note_id = noteId, label = label)
        }
    }
}
