package com.playbook.core.repository

import com.playbook.core.model.Attachment
import com.playbook.core.model.AttachmentKind

/**
 * Persistencia de los Adjuntos de una Nota. La UI/nativo guarda el archivo en
 * almacenamiento privado y aquí sólo se persisten metadatos + ruta relativa.
 */
interface AttachmentRepository {
    /** Crea y persiste un Adjunto para [noteId]; devuelve la entidad persistida. */
    fun create(
        noteId: String,
        kind: AttachmentKind,
        mimeType: String,
        filePath: String,
        byteSize: Long,
    ): Attachment

    /** Adjuntos de la Nota [noteId], ordenados de forma determinista. */
    fun getByNoteId(noteId: String): List<Attachment>

    /** Todos los Adjuntos (sólo metadatos, sin bytes). */
    fun getAll(): List<Attachment>

    /** Borra las filas de la Nota [noteId]. Devuelve `true` si borró alguna. */
    fun deleteByNoteId(noteId: String): Boolean
}
