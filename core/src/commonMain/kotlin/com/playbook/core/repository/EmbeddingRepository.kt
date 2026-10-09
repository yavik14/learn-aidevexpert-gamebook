package com.playbook.core.repository

import com.playbook.core.model.NoteEmbedding

/**
 * Persistencia de embeddings por Nota (1:0..1). La implementación concreta hoy
 * es SQLDelight; `semantic-linking`/`rag-query` consumirán [getAll]/[getByNoteId].
 */
interface EmbeddingRepository {
    /**
     * Inserta o reemplaza el embedding de [noteId] y sella `indexedAt` con el
     * reloj actual. Devuelve la entidad persistida.
     */
    fun upsert(noteId: String, values: List<Float>): NoteEmbedding

    /** Devuelve el embedding de [noteId] o `null` si no existe. */
    fun getByNoteId(noteId: String): NoteEmbedding?

    /** Devuelve todos los embeddings persistidos, ordenados por `noteId`. */
    fun getAll(): List<NoteEmbedding>

    /** Borra el embedding de [noteId]. Devuelve `true` si la fila existía. */
    fun deleteByNoteId(noteId: String): Boolean
}
