package com.playbook.core.repository

import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.model.Attachment
import com.playbook.core.model.AttachmentKind
import com.playbook.core.model.toDomain

/**
 * Implementación SQLDelight de [AttachmentRepository].
 *
 * `idFactory` y `clock` se inyectan para determinismo en tests; en producción se
 * reutilizan los defaults de plataforma (`randomNoteId()`/`currentTimeMillis()`).
 */
class SqlDelightAttachmentRepository(
    private val database: PlaybookDatabase,
    private val idFactory: () -> String,
    private val clock: () -> Long,
) : AttachmentRepository {

    override fun create(
        noteId: String,
        kind: AttachmentKind,
        mimeType: String,
        filePath: String,
        byteSize: Long,
    ): Attachment {
        val attachment = Attachment(
            id = idFactory(),
            noteId = noteId,
            kind = kind,
            mimeType = mimeType,
            filePath = filePath,
            byteSize = byteSize,
            createdAt = clock(),
        )
        database.attachmentQueries.insertAttachment(
            id = attachment.id,
            note_id = attachment.noteId,
            kind = attachment.kind.code,
            mime_type = attachment.mimeType,
            file_path = attachment.filePath,
            byte_size = attachment.byteSize,
            created_at = attachment.createdAt,
        )
        return attachment
    }

    override fun getByNoteId(noteId: String): List<Attachment> =
        database.attachmentQueries.selectAttachmentsByNoteId(noteId)
            .executeAsList()
            .map { it.toDomain() }

    override fun getAll(): List<Attachment> =
        database.attachmentQueries.selectAllAttachments()
            .executeAsList()
            .map { it.toDomain() }

    override fun deleteByNoteId(noteId: String): Boolean =
        database.attachmentQueries.deleteAttachmentsByNoteId(noteId).value > 0
}
