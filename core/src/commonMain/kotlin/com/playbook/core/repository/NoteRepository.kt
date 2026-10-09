package com.playbook.core.repository

import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft

/**
 * CRUD de [Note] sobre la persistencia local. La UI y los casos de uso consumen
 * esta abstracción; la implementación concreta hoy es SQLDelight.
 */
interface NoteRepository {
    /**
     * Crea una Nota a partir del [draft] y devuelve la entidad persistida. Las
     * etiquetas del draft se normalizan (trim, sin vacías, dedupe
     * case-insensitive, ordenadas) antes de persistirse.
     */
    fun create(draft: NoteDraft): Note

    /** Devuelve todas las Notas persistidas, cada una con sus etiquetas. */
    fun getAll(): List<Note>

    /** Devuelve la Nota con [id] (con sus etiquetas) o `null` si no existe. */
    fun getById(id: String): Note?

    /**
     * Actualiza `body`/`track`/`status`/`tags` de la Nota y re-sella `updatedAt`
     * con el reloj actual (el `updatedAt` del argumento se ignora). Las
     * etiquetas se normalizan y **reemplazan** el conjunto anterior. Devuelve
     * `true` si la fila existía.
     */
    fun update(note: Note): Boolean

    /** Borra la Nota con [id] y limpia sus etiquetas. Devuelve `true` si la fila existía. */
    fun delete(id: String): Boolean
}
