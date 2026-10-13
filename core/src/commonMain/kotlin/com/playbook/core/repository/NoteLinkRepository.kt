package com.playbook.core.repository

import com.playbook.core.model.NoteLink

/**
 * Persistencia de Enlaces Nota↔Nota (similitud coseno). Los enlaces son
 * bidireccionales y se guardan **una sola vez** por par con orden canónico
 * `noteAId < noteBId`; la implementación concreta canoniza el orden.
 */
interface NoteLinkRepository {
    /**
     * Inserta o reemplaza el enlace del par; canoniza el orden y sella
     * `updatedAt` con `clock()`. Devuelve la entidad persistida.
     *
     * `IllegalArgumentException` si [noteAId] == [noteBId] o si [score] no es
     * finito.
     */
    fun upsert(noteAId: String, noteBId: String, score: Double): NoteLink

    /** Devuelve los enlaces de [noteId] (por cualquiera de los dos lados). */
    fun getLinksForNote(noteId: String): List<NoteLink>

    /** Devuelve todos los enlaces, ordenados por (`noteAId`, `noteBId`). */
    fun getAll(): List<NoteLink>

    /** Borra todos los enlaces. */
    fun deleteAll()
}
