package com.playbook.core.db

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.assertEquals

/**
 * Ejercita el plumbing del esquema v1 sobre un [SqlDriver] ya inicializado:
 * inserta una nota y la vuelve a leer verificando el contenido.
 *
 * Se mantiene en `commonTest` para que el mismo test corra en Android/JVM y en
 * iOS con el driver in-memory de cada plataforma.
 */
fun verifyNotePersistence(driver: SqlDriver) {
    val database = PlaybookDatabase(driver)
    val id = "note-1"
    val createdAt = 1_700_000_000_000L

    database.noteQueries.insertNote(
        id = id,
        owner = "owner-1",
        body = "Diseñar el jefe final",
        track = "mecánicas",
        status = "capturada",
        created_at = createdAt,
        updated_at = createdAt,
    )

    val all = database.noteQueries.selectAllNotes().executeAsList()
    assertEquals(1, all.size, "debe existir exactamente una nota persistida")

    val note = database.noteQueries.selectNoteById(id).executeAsOne()
    assertEquals(id, note.id)
    assertEquals("owner-1", note.owner)
    assertEquals("Diseñar el jefe final", note.body)
    assertEquals("mecánicas", note.track)
    assertEquals("capturada", note.status)
    assertEquals(createdAt, note.created_at)
    assertEquals(createdAt, note.updated_at)
}
