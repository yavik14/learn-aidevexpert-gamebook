package com.playbook.core.repository

import app.cash.sqldelight.db.SqlDriver
import com.playbook.core.db.DatabaseDriverFactory
import com.playbook.core.db.createDatabase
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.otherNoteId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Ejercita [SqlDelightNoteLinkRepository] sobre un [SqlDriver] ya inicializado
 * con el esquema v5 (tablas `note` + `note_link`). Vive en `commonTest` para
 * correr el mismo test en Android/JVM e iOS con el driver in-memory de cada
 * plataforma.
 *
 * `createDatabase(...)` se usa a propósito: además de abrir la base, habilita
 * `PRAGMA foreign_keys = ON`, que es lo que hace posible el cascade del
 * Scenario 5.
 */
fun verifyNoteLinkRepository(driver: SqlDriver) {
    val database = createDatabase(
        object : DatabaseDriverFactory {
            override fun createDriver(): SqlDriver = driver
        },
    )
    var sequence = 0
    var now = 1_700_000_000_000L
    val notes = SqlDelightNoteRepository(
        database = database,
        idFactory = { "note-${sequence++}" },
        clock = { now },
    )
    val links = SqlDelightNoteLinkRepository(database = database, clock = { now })

    // Las FK exigen que ambas Notas existan. Los ids son note-0 < note-1 < note-2.
    val noteA = notes.create(NoteDraft("owner-1", "A", null))
    val noteB = notes.create(NoteDraft("owner-1", "B", null))
    val noteC = notes.create(NoteDraft("owner-1", "C", null))
    assertTrue(noteA.id < noteB.id, "precondición: noteA.id < noteB.id")

    // --- Scenario 5: alta canónica con el par invertido --------------------
    val link = links.upsert(noteB.id, noteA.id, 0.9)
    assertEquals(noteA.id, link.noteAId, "el orden se canoniza: noteAId < noteBId")
    assertEquals(noteB.id, link.noteBId)
    assertEquals(0.9, link.score)
    assertEquals(now, link.updatedAt, "updatedAt se sella con clock()")

    // Una sola fila para el par (PK compuesta).
    assertEquals(1, links.getAll().size, "un par se guarda una sola vez")
    assertEquals(
        link,
        links.getAll().single(),
        "la fila persistida es canónica",
    )
    assertEquals(
        noteA.id,
        database.noteLinkQueries.selectLinkByPair(noteA.id, noteB.id).executeAsOne().note_a_id,
    )

    // La lectura resuelve "el otro" id desde ambos lados.
    assertEquals(listOf(link), links.getLinksForNote(noteA.id))
    assertEquals(listOf(link), links.getLinksForNote(noteB.id))
    assertEquals(emptyList(), links.getLinksForNote(noteC.id), "una Nota sin enlaces devuelve vacío")

    // `otherNoteId` resuelve el otro extremo y falla rápido con un id ajeno.
    assertEquals(noteB.id, link.otherNoteId(noteA.id))
    assertEquals(noteA.id, link.otherNoteId(noteB.id))
    assertFailsWith<IllegalArgumentException>("un id ajeno no pertenece al enlace") {
        link.otherNoteId("no-existe")
    }

    // --- upsert idempotente: mismo par, score y updatedAt nuevos -----------
    now += 1_000
    val updated = links.upsert(noteA.id, noteB.id, 0.95)
    assertEquals(1, links.getAll().size, "re-upsert no duplica filas")
    assertEquals(0.95, updated.score)
    assertEquals(now, updated.updatedAt, "re-upsert re-sella updatedAt")
    assertEquals(
        listOf(updated),
        links.getLinksForNote(noteB.id),
        "la lectura ve el score actualizado desde el lado B",
    )

    // --- Validación de entradas --------------------------------------------
    assertFailsWith<IllegalArgumentException>("un enlace no admite el mismo id dos veces") {
        links.upsert(noteA.id, noteA.id, 0.5)
    }
    assertFailsWith<IllegalArgumentException>("score NaN es inválido") {
        links.upsert(noteA.id, noteC.id, Double.NaN)
    }
    assertFailsWith<IllegalArgumentException>("score infinito es inválido") {
        links.upsert(noteA.id, noteC.id, Double.POSITIVE_INFINITY)
    }

    // --- deleteAll ---------------------------------------------------------
    links.upsert(noteA.id, noteC.id, 0.85)
    assertEquals(2, links.getAll().size)
    links.deleteAll()
    assertEquals(emptyList(), links.getAll(), "deleteAll vacía la tabla")
    assertEquals(emptyList(), links.getLinksForNote(noteA.id))

    // --- Scenario 5 (cont.): borrar una Nota cascadea sus note_link ---------
    links.upsert(noteA.id, noteC.id, 0.8)
    links.upsert(noteB.id, noteC.id, 0.7)
    assertEquals(2, links.getAll().size)
    assertTrue(notes.delete(noteC.id), "delete de la Nota existente")
    assertEquals(
        emptyList(),
        links.getAll(),
        "borrar la Nota cascadea sus enlaces (PRAGMA foreign_keys = ON)",
    )
    assertEquals(emptyList(), links.getLinksForNote(noteA.id), "sin huérfanos")
    assertEquals(emptyList(), links.getLinksForNote(noteB.id), "sin huérfanos")
}
