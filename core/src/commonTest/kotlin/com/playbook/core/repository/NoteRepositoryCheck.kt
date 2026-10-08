package com.playbook.core.repository

import app.cash.sqldelight.db.SqlDriver
import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.Track
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ejercita el CRUD de [SqlDelightNoteRepository] sobre un [SqlDriver] ya
 * inicializado (esquema v1 aplicado). Vive en `commonTest` para correr el mismo
 * test en Android/JVM e iOS con el driver in-memory de cada plataforma.
 *
 * `idFactory`/`clock` son fakes deterministas: no se usa `expect`/`actual` en
 * los source sets de test.
 */
fun verifyNoteCrud(driver: SqlDriver) {
    val database = PlaybookDatabase(driver)
    var sequence = 0
    var now = 1_700_000_000_000L
    val repository = SqlDelightNoteRepository(
        database = database,
        idFactory = { "note-${sequence++}" },
        clock = { now },
    )

    // Mapeo de códigos: tolerante para `track`, fail-fast para `status`.
    assertNull(Track.fromCode(null), "track null se mapea a null")
    assertNull(Track.fromCode("desconocido"), "track desconocido se mapea a null")
    assertFailsWith<IllegalArgumentException>("NoteStatus desconocido debe fallar") {
        NoteStatus.fromCode("desconocido")
    }

    // --- Scenario 1: crear nota -------------------------------------------
    val created = repository.create(
        NoteDraft(owner = "owner-1", body = "Diseñar el jefe final", track = Track.MECHANICS),
    )
    assertEquals("note-0", created.id)
    assertEquals("owner-1", created.owner)
    assertEquals("Diseñar el jefe final", created.body)
    assertEquals(Track.MECHANICS, created.track)
    assertEquals(NoteStatus.CAPTURED, created.status, "status default es CAPTURED")
    assertEquals(now, created.createdAt)
    assertEquals(now, created.updatedAt, "createdAt y updatedAt arrancan iguales")
    assertEquals(created, repository.getById(created.id), "getById recupera la nota creada")
    assertEquals(listOf(created), repository.getAll(), "getAll contiene la nota creada")

    // --- Scenario 2: actualizar nota --------------------------------------
    val frozenUpdatedAt = created.updatedAt
    now += 1_000
    val wasUpdated = repository.update(
        created.copy(
            body = "Diseñar el jefe final (revisado)",
            track = Track.STORY,
            status = NoteStatus.INDEXED,
        ),
    )
    assertTrue(wasUpdated, "update de una nota existente devuelve true")
    val reloaded = repository.getById(created.id)
    assertEquals("Diseñar el jefe final (revisado)", reloaded?.body)
    assertEquals(Track.STORY, reloaded?.track)
    assertEquals(NoteStatus.INDEXED, reloaded?.status)
    assertEquals(created.createdAt, reloaded?.createdAt, "update preserva createdAt")
    assertEquals(now, reloaded?.updatedAt, "update re-sella updatedAt con clock()")
    assertTrue(
        reloaded?.updatedAt != frozenUpdatedAt,
        "update ignora el updatedAt del argumento y avanza el timestamp",
    )
    assertFalse(
        repository.update(created.copy(id = "no-existe", body = "x")),
        "update de un id inexistente devuelve false y no lanza",
    )

    // --- Scenario 3: borrar nota ------------------------------------------
    assertTrue(repository.delete(created.id), "delete de una nota existente devuelve true")
    assertNull(repository.getById(created.id), "getById devuelve null tras borrar")
    assertFalse(
        repository.getAll().any { it.id == created.id },
        "getAll ya no contiene la nota borrada",
    )
    assertFalse(repository.delete("no-existe"), "delete de un id inexistente devuelve false")

    // --- Scenario 4: round-trip de Track y NoteStatus ---------------------
    for (track in Track.entries) {
        val note = repository.create(
            NoteDraft(owner = "owner-1", body = "track ${track.code}", track = track),
        )
        assertEquals(track, repository.getById(note.id)?.track, "round-trip de $track")
    }

    for (status in NoteStatus.entries) {
        val note = repository.create(
            NoteDraft(
                owner = "owner-1",
                body = "status ${status.code}",
                track = null,
                status = status,
            ),
        )
        val reread = repository.getById(note.id)
        assertEquals(status, reread?.status, "round-trip de $status")
        assertNull(reread?.track, "track null se persiste y relee como null")
    }

    // Un `track` desconocido ya almacenado no rompe la lectura: se mapea a null.
    database.noteQueries.insertNote(
        id = "note-legacy",
        owner = "owner-1",
        body = "fila migrada",
        track = "desconocido",
        status = NoteStatus.CAPTURED.code,
        created_at = now,
        updated_at = now,
    )
    val legacy = repository.getById("note-legacy")
    assertNull(legacy?.track, "un track desconocido se mapea a null")
    assertEquals(NoteStatus.CAPTURED, legacy?.status)

    // --- Scenario 5: orden determinista (updated_at DESC, id DESC) --------
    // Filas con timestamps controlados, incluidos empates de `updated_at` que se
    // desempatan por `id` descendente. Los `updated_at` son chicos para no
    // intercalarse con las filas previas (que usan ~1.7e12): el subconjunto
    // "order-*" conserva su orden relativo dentro de `getAll()`.
    val orderingNotes = listOf(
        "order-a" to 400L,
        "order-b" to 200L,
        "order-c" to 200L,
        "order-d" to 100L,
    )
    orderingNotes.forEach { (id, updatedAt) ->
        database.noteQueries.insertNote(
            id = id,
            owner = "owner-order",
            body = "orden $id",
            track = null,
            status = NoteStatus.CAPTURED.code,
            created_at = updatedAt,
            updated_at = updatedAt,
        )
    }
    val orderedIds = repository.getAll().map { it.id }.filter { it.startsWith("order-") }
    assertEquals(
        listOf("order-a", "order-c", "order-b", "order-d"),
        orderedIds,
        "getAll respeta updated_at DESC, id DESC (id desempata los empates)",
    )

    // El orden que devuelve el repositorio coincide con re-ordenar en Kotlin con
    // el mismo criterio.
    val allNotes = repository.getAll()
    val resorted = allNotes.sortedWith(
        compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id },
    )
    assertEquals(resorted, allNotes, "getAll ya viene ordenado por updated_at DESC, id DESC")

    // --- Scenario: persistencia visible desde otra instancia --------------
    val secondRepository = SqlDelightNoteRepository(
        database = database,
        idFactory = { "note-second" },
        clock = { now },
    )
    assertEquals(
        repository.getAll(),
        secondRepository.getAll(),
        "una segunda instancia sobre el mismo driver relee lo persistido",
    )
}
