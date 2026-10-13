package com.playbook.core.linking

import app.cash.sqldelight.db.SqlDriver
import com.playbook.core.db.DatabaseDriverFactory
import com.playbook.core.db.createDatabase
import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft
import com.playbook.core.repository.SqlDelightEmbeddingRepository
import com.playbook.core.repository.SqlDelightNoteLinkRepository
import com.playbook.core.repository.SqlDelightNoteRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val EPSILON = 1e-9

/**
 * Ejercita [SemanticLinkingService] sobre un [SqlDriver] ya inicializado con el
 * esquema v5. Vive en `commonTest` para correr el mismo test en Android/JVM e iOS
 * con el driver in-memory de cada plataforma.
 *
 * `createDatabase(...)` habilita `PRAGMA foreign_keys = ON`, necesario para el
 * cascade del Scenario 7. `idFactory`/`clock` son fakes deterministas; los
 * embeddings se insertan directamente para controlar el coseno.
 */
fun verifySemanticLinking(driver: SqlDriver) {
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
    val embeddings = SqlDelightEmbeddingRepository(database = database, clock = { now })
    val links = SqlDelightNoteLinkRepository(database = database, clock = { now })

    fun newNote(body: String): Note = notes.create(NoteDraft("owner-1", body, null))

    // Borra las Notas de la fase (cascadea embeddings y enlaces) para aislar
    // cada escenario. También limpia la tabla de enlaces.
    fun reset() {
        notes.getAll().forEach { notes.delete(it.id) }
        assertEquals(emptyList(), links.getAll(), "reset deja note_link vacía")
        assertEquals(emptyList(), embeddings.getAll(), "reset cascadea los embeddings")
    }

    // --- Configuración -----------------------------------------------------
    assertEquals(0.8, SemanticLinkingService.DEFAULT_SIMILARITY_THRESHOLD)
    assertFailsWith<IllegalArgumentException>("threshold fuera de [-1, 1]") {
        SemanticLinkingService(embeddings, links, threshold = 2.0).recomputeAll()
    }
    assertFailsWith<IllegalArgumentException>("threshold fuera de [-1, 1]") {
        SemanticLinkingService(embeddings, links, threshold = -1.5).recomputeAll()
    }

    val service = SemanticLinkingService(embeddings, links) // threshold default 0.8

    // --- Scenario 2: generar enlaces por encima del umbral -----------------
    run {
        val n1 = newNote("uno")
        val n2 = newNote("dos")
        val n3 = newNote("tres")
        embeddings.upsert(n1.id, listOf(1f, 0f))
        embeddings.upsert(n2.id, listOf(1f, 0f)) // idéntico a n1 → 1.0
        embeddings.upsert(n3.id, listOf(0f, 1f)) // ortogonal → 0.0

        now += 1_000
        val summary = service.recomputeAll()
        assertEquals(3, summary.notesConsidered)
        assertEquals(1, summary.linksWritten, "sólo el par n1-n2 supera 0.8")

        val link = links.getAll().single()
        assertEquals(n1.id, link.noteAId)
        assertEquals(n2.id, link.noteBId)
        assertEquals(1.0, link.score, EPSILON)
        assertEquals(now, link.updatedAt, "updatedAt = clock() del recomputo")
        assertEquals(listOf(link), links.getLinksForNote(n1.id))
        assertEquals(listOf(link), links.getLinksForNote(n2.id))
        assertEquals(emptyList(), links.getLinksForNote(n3.id))
        reset()
    }

    // --- Scenario 3: frontera del umbral (inclusivo) -----------------------
    run {
        val n4 = newNote("cuatro")
        val n5 = newNote("cinco")
        val n6 = newNote("seis")
        embeddings.upsert(n4.id, listOf(1f, 0f))
        embeddings.upsert(n5.id, listOf(1f, 0f)) // coseno exactamente 1.0
        embeddings.upsert(n6.id, listOf(1f, 0.5f)) // coseno ≈ 0.894 < 1.0

        val boundary = SemanticLinkingService(embeddings, links, threshold = 1.0)
        val summary = boundary.recomputeAll()
        assertEquals(3, summary.notesConsidered)
        assertEquals(1, summary.linksWritten, "el par con coseno == umbral se incluye (>=)")

        val link = links.getAll().single()
        assertEquals(n4.id, link.noteAId)
        assertEquals(n5.id, link.noteBId)
        assertEquals(1.0, link.score, EPSILON)
        assertFalse(
            links.getAll().any { it.noteAId == n6.id || it.noteBId == n6.id },
            "un par justo por debajo del umbral no crea fila",
        )
        reset()
    }

    // --- Scenario 4: similitud indefinida no crea enlace -------------------
    run {
        val n7 = newNote("siete")
        val n8 = newNote("ocho")
        val n9 = newNote("nueve")
        val n10 = newNote("diez")
        embeddings.upsert(n7.id, listOf(1f, 0f))
        embeddings.upsert(n8.id, listOf(1f, 0f, 0f)) // dimensión distinta → null
        embeddings.upsert(n9.id, listOf(0f, 0f)) // norma 0 → null
        embeddings.upsert(n10.id, listOf(1f, 0f)) // idéntico a n7 → 1.0

        val summary = service.recomputeAll() // no debe lanzar
        assertEquals(4, summary.notesConsidered)
        assertEquals(1, summary.linksWritten, "los pares indefinidos se omiten, el resto se procesa")
        val link = links.getAll().single()
        assertEquals(n7.id, link.noteAId)
        assertEquals(n10.id, link.noteBId)
        reset()
    }

    // --- Scenario 6: recomputo idempotente y recalculo tras re-indexar -----
    run {
        val n11 = newNote("once")
        val n12 = newNote("doce")
        val n13 = newNote("trece")
        embeddings.upsert(n11.id, listOf(1f, 0f))
        embeddings.upsert(n12.id, listOf(1f, 0f))
        embeddings.upsert(n13.id, listOf(0f, 1f))

        service.recomputeAll()
        val first = links.getAll()
        assertEquals(1, first.size, "n11-n12 enlazados")

        service.recomputeAll()
        assertEquals(first, links.getAll(), "recomputo idempotente: mismo conjunto y scores")

        // Re-indexar n12: ya no se parece a n11 y ahora sí a n13.
        now += 1_000
        embeddings.upsert(n12.id, listOf(0f, 1f))
        val summary = service.recomputeAll()
        assertEquals(1, summary.linksWritten)
        val after = links.getAll().single()
        assertFalse(
            links.getAll().any { it.noteAId == n11.id || it.noteBId == n11.id },
            "el enlace obsoleto n11-n12 se elimina (borra y reconstruye)",
        )
        assertEquals(n12.id, after.noteAId, "n12 < n13 canónico")
        assertEquals(n13.id, after.noteBId)
        assertEquals(1.0, after.score, EPSILON)
        assertEquals(now, after.updatedAt)
        reset()
    }

    // --- Scenario 7: sin embedding no participa; borrado cascadea ----------
    run {
        val n14 = newNote("catorce")
        val n15 = newNote("quince")
        val n16 = newNote("sin embedding") // indexado fallido: no hay fila embedding
        embeddings.upsert(n14.id, listOf(1f, 0f))
        embeddings.upsert(n15.id, listOf(1f, 0f))

        val summary = service.recomputeAll()
        assertEquals(2, summary.notesConsidered, "la Nota sin embedding no se considera")
        assertEquals(1, summary.linksWritten)
        assertEquals(emptyList(), links.getLinksForNote(n16.id), "sin embedding no aparece en enlaces")
        assertTrue(links.getAll().none { it.noteAId == n16.id || it.noteBId == n16.id })

        assertTrue(notes.delete(n15.id))
        assertEquals(emptyList(), links.getAll(), "borrar la Nota cascadea sus note_link")
        assertEquals(emptyList(), links.getLinksForNote(n14.id), "sin huérfanos")
        reset()
    }
}
