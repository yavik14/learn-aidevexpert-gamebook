package com.playbook.core.repository

import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft

/**
 * CRUD de [Note] sobre la persistencia local. La UI y los casos de uso consumen
 * esta abstracción; la implementación concreta hoy es SQLDelight.
 */
interface NoteRepository {
    /** Crea una Nota a partir del [draft] y devuelve la entidad persistida. */
    fun create(draft: NoteDraft): Note

    /** Devuelve todas las Notas persistidas. */
    fun getAll(): List<Note>

    /** Devuelve la Nota con [id] o `null` si no existe. */
    fun getById(id: String): Note?

    /**
     * Actualiza `body`/`track`/`status` de la Nota y re-sella `updatedAt` con el
     * reloj actual (el `updatedAt` del argumento se ignora). Devuelve `true` si la
     * fila existía.
     */
    fun update(note: Note): Boolean

    /** Borra la Nota con [id]. Devuelve `true` si la fila existía. */
    fun delete(id: String): Boolean
}
