package com.playbook.core.repository

import app.cash.sqldelight.db.SqlDriver
import com.playbook.core.db.DatabaseDriverFactory
import com.playbook.core.db.createDatabase
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.Track
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ejercita [EmbeddingBlobCodec] y [SqlDelightEmbeddingRepository] sobre un
 * [SqlDriver] ya inicializado con el esquema v2. Vive en `commonTest` para correr
 * el mismo test en Android/JVM e iOS con el driver in-memory de cada plataforma.
 *
 * `createDatabase(...)` se usa a propósito: además de abrir la base, habilita
 * `PRAGMA foreign_keys = ON`, que es lo que hace posible el cascade del
 * Scenario 8.
 */
fun verifyEmbeddingRepository(driver: SqlDriver) {
    val database = createDatabase(
        object : DatabaseDriverFactory {
            override fun createDriver(): SqlDriver = driver
        },
    )
    var sequence = 0
    var now = 1_700_000_000_000L
    val noteRepository = SqlDelightNoteRepository(
        database = database,
        idFactory = { "note-${sequence++}" },
        clock = { now },
    )
    val embeddings = SqlDelightEmbeddingRepository(database = database, clock = { now })

    // --- Scenario 6: codec BLOB round-trip y entrada inválida --------------
    val codecValues = listOf(
        0f,
        -0.0f,
        1.5f,
        -3.25f,
        Float.MAX_VALUE,
        Float.MIN_VALUE,
        Float.NaN,
        Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY,
    )
    val encoded = EmbeddingBlobCodec.encode(codecValues)
    assertEquals(codecValues.size * 4, encoded.size, "encode debe producir 4 bytes por valor")
    assertEquals(
        codecValues.map { it.toBits() },
        EmbeddingBlobCodec.decode(encoded).map { it.toBits() },
        "el round-trip del codec debe ser bit-exacto",
    )
    assertEquals(
        listOf(0x3F, 0x80, 0x00, 0x00),
        EmbeddingBlobCodec.encode(listOf(1.0f)).map { it.toInt() and 0xFF },
        "1.0f debe codificarse en big-endian explícito (3F 80 00 00)",
    )
    assertFailsWith<IllegalArgumentException>("un BLOB de 3 bytes no es múltiplo de 4") {
        EmbeddingBlobCodec.decode(ByteArray(3))
    }
    assertFailsWith<IllegalArgumentException>("un BLOB de 5 bytes no es múltiplo de 4") {
        EmbeddingBlobCodec.decode(ByteArray(5))
    }
    assertEquals(emptyList(), EmbeddingBlobCodec.decode(ByteArray(0)))

    // --- Scenario 1: persistir y recuperar el vector intacto --------------
    val noteA = noteRepository.create(NoteDraft("owner-1", "cuerpo A", Track.MECHANICS))
    val noteB = noteRepository.create(NoteDraft("owner-1", "cuerpo B", null))
    val valuesA = listOf(0.25f, -1.5f, 3.0f)
    val stored = embeddings.upsert(noteA.id, valuesA)
    assertEquals(noteA.id, stored.noteId)
    assertEquals(valuesA, stored.values)
    assertEquals(now, stored.indexedAt)
    assertEquals(stored, embeddings.getByNoteId(noteA.id), "getByNoteId recupera el embedding")
    assertEquals(
        valuesA.size.toLong(),
        database.embeddingQueries.selectEmbeddingByNoteId(noteA.id).executeAsOne().dimension,
        "la dimension almacenada coincide con values.size",
    )
    assertNull(embeddings.getByNoteId(noteB.id), "otro noteId no ve el embedding")

    val valuesB = listOf(7f)
    embeddings.upsert(noteB.id, valuesB)
    assertEquals(
        listOf(noteA.id, noteB.id).sorted(),
        embeddings.getAll().map { it.noteId },
        "getAll devuelve todos los embeddings ordenados por note_id",
    )

    // --- Scenario 3: re-generación idempotente (overwrite) ----------------
    now += 1_000
    val newValuesA = listOf(9f, 8f)
    val overwritten = embeddings.upsert(noteA.id, newValuesA)
    assertEquals(now, overwritten.indexedAt, "re-indexar re-sella indexedAt con clock()")
    assertEquals(
        1,
        embeddings.getAll().count { it.noteId == noteA.id },
        "re-indexar no debe duplicar filas",
    )
    assertEquals(newValuesA, embeddings.getByNoteId(noteA.id)?.values)
    assertEquals(
        newValuesA.size.toLong(),
        database.embeddingQueries.selectEmbeddingByNoteId(noteA.id).executeAsOne().dimension,
    )

    // `values` vacío es un error de programación (el servicio lo valida antes).
    assertFailsWith<IllegalArgumentException>("upsert no admite values vacío") {
        embeddings.upsert(noteA.id, emptyList())
    }

    // --- Scenario 8: ciclo de vida por FK ---------------------------------
    val noteC = noteRepository.create(NoteDraft("owner-1", "cuerpo C", null))
    embeddings.upsert(noteC.id, listOf(1f))
    assertTrue(embeddings.deleteByNoteId(noteC.id), "deleteByNoteId de un embedding existente")
    assertNull(embeddings.getByNoteId(noteC.id), "tras deleteByNoteId el embedding no existe")
    assertFalse(embeddings.deleteByNoteId(noteC.id), "deleteByNoteId de un id inexistente")

    val noteD = noteRepository.create(NoteDraft("owner-1", "cuerpo D", null))
    embeddings.upsert(noteD.id, listOf(2f))
    assertTrue(noteRepository.delete(noteD.id), "delete de la Nota existente")
    assertNull(
        embeddings.getByNoteId(noteD.id),
        "borrar la Nota debe cascadear el embedding (PRAGMA foreign_keys = ON)",
    )
    assertFalse(
        embeddings.getAll().any { it.noteId == noteD.id },
        "no quedan filas huérfanas",
    )

    // --- Mapper: fail-fast ante corrupción --------------------------------
    database.embeddingQueries.upsertEmbedding(
        note_id = noteA.id,
        dimension = 99L,
        vector = EmbeddingBlobCodec.encode(listOf(1f)),
        indexed_at = now,
    )
    assertFailsWith<IllegalStateException>("dimension desalineada debe fallar al mapear") {
        embeddings.getByNoteId(noteA.id)
    }
}
