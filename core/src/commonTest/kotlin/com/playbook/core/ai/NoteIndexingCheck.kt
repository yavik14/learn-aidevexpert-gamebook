package com.playbook.core.ai

import app.cash.sqldelight.db.SqlDriver
import com.playbook.core.db.DatabaseDriverFactory
import com.playbook.core.db.createDatabase
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.Track
import com.playbook.core.repository.SqlDelightEmbeddingRepository
import com.playbook.core.repository.SqlDelightNoteRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** [AiClient] que registra los textos recibidos y delega en [FakeAiClient]. */
private class RecordingAiClient(private val delegate: AiClient = FakeAiClient()) : AiClient {
    val recordedTexts = mutableListOf<List<String>>()

    override suspend fun embed(texts: List<String>): List<Embedding> {
        recordedTexts += texts
        return delegate.embed(texts)
    }

    override suspend fun generate(request: GenerateRequest): String = delegate.generate(request)
}

/** [AiClient] que siempre lanza; para el Scenario 4. */
private class FailingAiClient : AiClient {
    val failure = AiClientException("runtime no disponible")

    override suspend fun embed(texts: List<String>): List<Embedding> = throw failure

    override suspend fun generate(request: GenerateRequest): String = throw failure
}

/** [AiClient] que devuelve una respuesta fija; para el Scenario 5. */
private class StubAiClient(private val result: List<Embedding>) : AiClient {
    override suspend fun embed(texts: List<String>): List<Embedding> = result

    override suspend fun generate(request: GenerateRequest): String = "stub"
}

/**
 * Ejercita [NoteIndexingService] contra un [SqlDriver] con el esquema v2. Cubre
 * el contrato del primer consumidor de [AiClient]: generación, overwrite
 * idempotente, fallo que no corrompe y respuesta inválida que no persiste.
 */
suspend fun verifyNoteIndexing(driver: SqlDriver) {
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
    val embeddingRepository = SqlDelightEmbeddingRepository(database = database, clock = { now })

    val note = noteRepository.create(NoteDraft("owner-1", "Diseñar el jefe final", Track.MECHANICS))

    // --- Scenario 2: generar vía AiClient (primer consumidor real) --------
    val recording = RecordingAiClient()
    val service = NoteIndexingService(recording, embeddingRepository)
    val result = service.index(note)
    assertTrue(result is IndexingResult.Indexed, "indexar una nota válida debe devolver Indexed")
    val indexed = (result as IndexingResult.Indexed).embedding
    val expectedValues = FakeAiClient().embed(listOf(note.body)).single().values
    assertEquals(expectedValues, indexed.values)
    assertEquals(
        listOf(note.body),
        recording.recordedTexts.single(),
        "el servicio llama embed con un único texto (el body de la Nota)",
    )
    val persisted = embeddingRepository.getByNoteId(note.id)
    assertNotNull(persisted)
    assertEquals(note.id, persisted.noteId)
    assertEquals(expectedValues, persisted.values)
    assertEquals(
        NoteStatus.CAPTURED,
        noteRepository.getById(note.id)?.status,
        "indexar no transiciona el NoteStatus (decisión de scope)",
    )

    // --- Scenario 3: re-generación idempotente (overwrite) ----------------
    now += 1_000
    val changed = note.copy(body = "Diseñar el jefe final (revisado)")
    val reindexed = service.index(changed)
    assertTrue(reindexed is IndexingResult.Indexed)
    val reindexedEmbedding = (reindexed as IndexingResult.Indexed).embedding
    val newValues = FakeAiClient().embed(listOf(changed.body)).single().values
    assertEquals(newValues, reindexedEmbedding.values)
    assertEquals(now, reindexedEmbedding.indexedAt, "re-indexar re-sella indexedAt con clock()")
    assertEquals(
        1,
        embeddingRepository.getAll().count { it.noteId == note.id },
        "re-indexar no debe duplicar filas",
    )
    assertEquals(newValues, embeddingRepository.getByNoteId(note.id)?.values)

    val beforeFailure = embeddingRepository.getByNoteId(note.id)

    // --- Scenario 4: fallo del runtime no corrompe el estado --------------
    val failing = FailingAiClient()
    val failingService = NoteIndexingService(failing, embeddingRepository)
    val failedExisting = failingService.index(changed)
    assertTrue(failedExisting is IndexingResult.Failed, "un AiClientException debe dar Failed")
    assertSame(
        failing.failure,
        (failedExisting as IndexingResult.Failed).cause,
        "Failed debe propagar la causa del runtime",
    )
    assertEquals(
        beforeFailure,
        embeddingRepository.getByNoteId(note.id),
        "un fallo no debe pisar el embedding previo",
    )

    val noteWithoutEmbedding = noteRepository.create(NoteDraft("owner-1", "otra nota", null))
    val failedMissing = failingService.index(noteWithoutEmbedding)
    assertTrue(failedMissing is IndexingResult.Failed)
    assertNull(
        embeddingRepository.getByNoteId(noteWithoutEmbedding.id),
        "un fallo sobre una Nota sin embedding no debe crear fila",
    )

    // --- Scenario 5: respuesta inválida del runtime (control de contrato) --
    val invalidClients = listOf(
        "0 vectores" to StubAiClient(emptyList()),
        ">1 vector" to StubAiClient(listOf(Embedding(listOf(1f)), Embedding(listOf(2f)))),
        "dimensión 0" to StubAiClient(listOf(Embedding(emptyList()))),
    )
    for ((label, client) in invalidClients) {
        val invalidService = NoteIndexingService(client, embeddingRepository)
        val outcome = invalidService.index(changed)
        assertTrue(outcome is IndexingResult.Failed, "$label debe devolver Failed")
        assertNotNull(
            (outcome as IndexingResult.Failed).cause,
            "$label debe reportar un AiClientException",
        )
        assertEquals(
            beforeFailure,
            embeddingRepository.getByNoteId(note.id),
            "$label no debe pisar el embedding previo",
        )
    }
    assertNull(
        embeddingRepository.getByNoteId(noteWithoutEmbedding.id),
        "una respuesta inválida no debe persistir ninguna fila",
    )
}
