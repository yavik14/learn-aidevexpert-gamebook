package com.playbook.core.repository

import app.cash.sqldelight.db.SqlDriver
import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.model.AttachmentKind
import com.playbook.core.model.NoteDraft
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ejercita el CRUD de [SqlDelightAttachmentRepository] y el cascade de adjuntos
 * al borrar la Nota, sobre un [SqlDriver] ya inicializado (esquema v2). Vive en
 * `commonTest` para correr en Android/JVM e iOS con el driver in-memory de cada
 * plataforma; `idFactory`/`clock` son fakes deterministas (sin `expect`/`actual`
 * en source sets de test).
 */
fun verifyAttachmentCrud(driver: SqlDriver) {
    val database = PlaybookDatabase(driver)
    var noteSequence = 0
    var attachmentSequence = 0
    var now = 1_700_000_000_000L
    val noteRepository = SqlDelightNoteRepository(
        database = database,
        idFactory = { "note-${noteSequence++}" },
        clock = { now },
    )
    val attachmentRepository = SqlDelightAttachmentRepository(
        database = database,
        idFactory = { "attachment-${attachmentSequence++}" },
        clock = { now },
    )

    // Mapeo de códigos fail-fast (columna NOT NULL escrita sólo por este código).
    assertEquals(AttachmentKind.IMAGE, AttachmentKind.fromCode("imagen"))
    assertFailsWith<IllegalArgumentException>("AttachmentKind desconocido debe fallar") {
        AttachmentKind.fromCode("desconocido")
    }

    // --- Scenario: crear adjunto y leerlo por nota / global ----------------
    val note = noteRepository.create(NoteDraft(owner = "local", body = "", track = null))
    assertTrue(attachmentRepository.getAll().isEmpty(), "sin adjuntos al inicio")
    assertTrue(
        attachmentRepository.getByNoteId(note.id).isEmpty(),
        "una nota recién creada no tiene adjuntos",
    )

    val first = attachmentRepository.create(
        noteId = note.id,
        kind = AttachmentKind.IMAGE,
        mimeType = "image/jpeg",
        filePath = "images/attachment-0.jpg",
        byteSize = 1_234L,
    )
    assertEquals("attachment-0", first.id)
    assertEquals(note.id, first.noteId)
    assertEquals(AttachmentKind.IMAGE, first.kind)
    assertEquals("image/jpeg", first.mimeType)
    assertEquals("images/attachment-0.jpg", first.filePath)
    assertEquals(1_234L, first.byteSize)
    assertEquals(now, first.createdAt)
    assertEquals(listOf(first), attachmentRepository.getByNoteId(note.id))
    assertEquals(listOf(first), attachmentRepository.getAll())

    // Una segunda nota no ve los adjuntos de la primera.
    val otherNote = noteRepository.create(NoteDraft(owner = "local", body = "otra", track = null))
    assertTrue(attachmentRepository.getByNoteId(otherNote.id).isEmpty())

    // Varios adjuntos de la misma nota: orden determinista (created_at, id).
    now += 1_000
    val second = attachmentRepository.create(
        noteId = note.id,
        kind = AttachmentKind.IMAGE,
        mimeType = "image/png",
        filePath = "images/attachment-1.png",
        byteSize = 2_048L,
    )
    assertEquals(
        listOf(first, second),
        attachmentRepository.getByNoteId(note.id),
        "getByNoteId ordena por created_at ASC, id ASC",
    )

    // --- Scenario: borrar adjuntos por nota -------------------------------
    assertTrue(
        attachmentRepository.deleteByNoteId(note.id),
        "deleteByNoteId de una nota con adjuntos devuelve true",
    )
    assertTrue(attachmentRepository.getByNoteId(note.id).isEmpty())
    assertFalse(
        attachmentRepository.deleteByNoteId(note.id),
        "deleteByNoteId sin adjuntos devuelve false",
    )

    // --- Scenario 6: borrar la Nota borra sus adjuntos (cascade) ----------
    val cascadeNote = noteRepository.create(
        NoteDraft(owner = "local", body = "nota con adjunto", track = null),
    )
    val cascadeAttachment = attachmentRepository.create(
        noteId = cascadeNote.id,
        kind = AttachmentKind.IMAGE,
        mimeType = "image/jpeg",
        filePath = "images/cascade.jpg",
        byteSize = 512L,
    )
    assertEquals(
        listOf(cascadeAttachment),
        attachmentRepository.getByNoteId(cascadeNote.id),
        "el adjunto existe antes del borrado",
    )

    assertTrue(noteRepository.delete(cascadeNote.id), "delete de la nota devuelve true")
    assertTrue(
        attachmentRepository.getByNoteId(cascadeNote.id).isEmpty(),
        "el cascade borra las filas attachment de la nota",
    )
    assertFalse(
        attachmentRepository.getAll().any { it.id == cascadeAttachment.id },
        "el adjunto borrado no queda en getAll",
    )

    // La conversión de una fila persistida conserva el kind.
    val persistedNote = noteRepository.create(NoteDraft(owner = "local", body = "", track = null))
    attachmentRepository.create(
        noteId = persistedNote.id,
        kind = AttachmentKind.IMAGE,
        mimeType = "image/jpeg",
        filePath = "images/round-trip.jpg",
        byteSize = 10L,
    )
    assertEquals(
        AttachmentKind.IMAGE,
        attachmentRepository.getByNoteId(persistedNote.id).single().kind,
    )
}
