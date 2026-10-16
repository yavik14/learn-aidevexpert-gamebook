package com.playbook.core.repository

import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.normalizeGenres
import com.playbook.core.model.toDomain

/**
 * Implementación SQLDelight de [NoteRepository].
 *
 * `idFactory` y `clock` se inyectan para que los tests sean deterministas y para
 * no acoplar el dominio a una fuente de tiempo/identidad concreta. En producción
 * se usan los defaults de plataforma (`randomNoteId()` / `currentTimeMillis()`).
 *
 * Los Géneros (`note_genre`) se persisten en la misma transacción que la nota y
 * se normalizan con [normalizeGenres] (canonización contra el catálogo incluida).
 * `update` reemplaza el conjunto completo de Géneros; `delete` limpia las filas
 * de Géneros y de adjuntos de la nota.
 */
class SqlDelightNoteRepository(
    private val database: PlaybookDatabase,
    private val idFactory: () -> String,
    private val clock: () -> Long,
) : NoteRepository {

    override fun create(draft: NoteDraft): Note {
        val now = clock()
        val genres = normalizeGenres(draft.genres)
        val note = Note(
            id = idFactory(),
            owner = draft.owner,
            body = draft.body,
            track = draft.track,
            status = draft.status,
            createdAt = now,
            updatedAt = now,
            genres = genres,
        )
        database.transaction {
            insertNoteRow(note)
            replaceGenres(note.id, genres)
        }
        return note
    }

    override fun getAll(): List<Note> {
        // Una sola consulta de Géneros agrupada en memoria evita el N+1.
        val genresByNote = database.genreQueries.selectAllNoteGenres()
            .executeAsList()
            .groupBy({ it.note_id }, { it.genre })
        return database.noteQueries.selectAllNotes().executeAsList().map { row ->
            row.toDomain(genresByNote[row.id].orEmpty())
        }
    }

    override fun getById(id: String): Note? {
        val row = database.noteQueries.selectNoteById(id).executeAsOneOrNull() ?: return null
        val genres = database.genreQueries.selectGenresByNoteId(id).executeAsList()
        return row.toDomain(genres)
    }

    override fun getByStatus(status: NoteStatus): List<Note> {
        // Misma estrategia que `getAll`: una sola consulta de Géneros agrupada
        // en memoria para evitar el N+1.
        val genresByNote = database.genreQueries.selectAllNoteGenres()
            .executeAsList()
            .groupBy({ it.note_id }, { it.genre })
        return database.noteQueries.selectNotesByStatus(status.code)
            .executeAsList()
            .map { row -> row.toDomain(genresByNote[row.id].orEmpty()) }
    }

    override fun update(note: Note): Boolean {
        val genres = normalizeGenres(note.genres)
        return database.transactionWithResult {
            val rowsAffected = database.noteQueries.updateNote(
                body = note.body,
                track = note.track?.code,
                status = note.status.code,
                updated_at = clock(),
                id = note.id,
            ).value
            if (rowsAffected > 0) {
                replaceGenres(note.id, genres)
            }
            rowsAffected > 0
        }
    }

    override fun delete(id: String): Boolean = database.transactionWithResult {
        database.attachmentQueries.deleteAttachmentsByNoteId(id)
        val rowsAffected = database.noteQueries.deleteNoteById(id).value
        database.genreQueries.deleteGenresByNoteId(id)
        rowsAffected > 0
    }

    override fun updateStatus(id: String, status: NoteStatus): Boolean {
        val rowsAffected = database.noteQueries.updateNoteStatus(
            status = status.code,
            id = id,
        ).value
        return rowsAffected > 0
    }

    override fun setIndexAttempts(id: String, attempts: Int): Boolean {
        val rowsAffected = database.noteQueries.updateNoteIndexAttempts(
            index_attempts = attempts.toLong(),
            id = id,
        ).value
        return rowsAffected > 0
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

    private fun replaceGenres(noteId: String, genres: List<String>) {
        database.genreQueries.deleteGenresByNoteId(noteId)
        genres.forEach { genre ->
            database.genreQueries.insertNoteGenre(note_id = noteId, genre = genre)
        }
    }
}
