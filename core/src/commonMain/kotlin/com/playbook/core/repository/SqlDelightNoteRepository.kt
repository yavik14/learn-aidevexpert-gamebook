package com.playbook.core.repository

import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.toDomain

/**
 * Implementación SQLDelight de [NoteRepository].
 *
 * `idFactory` y `clock` se inyectan para que los tests sean deterministas y para
 * no acoplar el dominio a una fuente de tiempo/identidad concreta. En producción
 * se usan los defaults de plataforma (`randomNoteId()` / `currentTimeMillis()`).
 */
class SqlDelightNoteRepository(
    private val database: PlaybookDatabase,
    private val idFactory: () -> String,
    private val clock: () -> Long,
) : NoteRepository {

    override fun create(draft: NoteDraft): Note {
        val now = clock()
        val note = Note(
            id = idFactory(),
            owner = draft.owner,
            body = draft.body,
            track = draft.track,
            status = draft.status,
            createdAt = now,
            updatedAt = now,
        )
        database.noteQueries.insertNote(
            id = note.id,
            owner = note.owner,
            body = note.body,
            track = note.track?.code,
            status = note.status.code,
            created_at = note.createdAt,
            updated_at = note.updatedAt,
        )
        return note
    }

    override fun getAll(): List<Note> =
        database.noteQueries.selectAllNotes().executeAsList().map { it.toDomain() }

    override fun getById(id: String): Note? =
        database.noteQueries.selectNoteById(id).executeAsOneOrNull()?.toDomain()

    override fun update(note: Note): Boolean {
        val rowsAffected = database.noteQueries.updateNote(
            body = note.body,
            track = note.track?.code,
            status = note.status.code,
            updated_at = clock(),
            id = note.id,
        ).value
        return rowsAffected > 0
    }

    /**
     * Borra la Nota y, en la misma transacción, sus filas `attachment` para no
     * dejar huérfanas. La semántica pública no cambia: devuelve si la Nota
     * existía (`rowsAffected > 0`).
     */
    override fun delete(id: String): Boolean =
        database.transactionWithResult {
            database.attachmentQueries.deleteAttachmentsByNoteId(id)
            database.noteQueries.deleteNoteById(id).value > 0
        }
}
