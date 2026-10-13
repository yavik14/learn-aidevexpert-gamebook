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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** [AiClient] que siempre lanza [AiClientException]; simula el runtime no disponible. */
private class ThrowingAiClient : AiClient {
    val failure = AiClientException("runtime no disponible")

    override suspend fun embed(texts: List<String>): List<Embedding> = throw failure

    override suspend fun generate(request: GenerateRequest): String = throw failure
}

/** [AiClient] que devuelve una respuesta fija; para las respuestas inválidas. */
private class FixedResponseAiClient(private val result: List<Embedding>) : AiClient {
    override suspend fun embed(texts: List<String>): List<Embedding> = result

    override suspend fun generate(request: GenerateRequest): String = "stub"
}

/**
 * Ejercita [NoteIndexingOrchestrator] sobre un [SqlDriver] con el esquema v4.
 * Cubre los escenarios 1–7 de la spec: transición de [NoteStatus] derivada del
 * resultado del indexado, sin bloquear la captura, sin degradar lo ya indexado y
 * sin escribir `FAILED`.
 *
 * Reutiliza `NoteIndexingService` (sin modificarlo) con stubs deterministas de
 * [AiClient]; el estilo de los stubs espeja `NoteIndexingCheck`.
 */
suspend fun verifyNoteIndexingOrchestrator(driver: SqlDriver) {
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

    val successOrchestrator = NoteIndexingOrchestrator(
        indexingService = NoteIndexingService(FakeAiClient(), embeddingRepository),
        noteRepository = noteRepository,
    )
    val throwing = ThrowingAiClient()
    val failingOrchestrator = NoteIndexingOrchestrator(
        indexingService = NoteIndexingService(throwing, embeddingRepository),
        noteRepository = noteRepository,
    )

    // --- Scenario 1: `capturada` + éxito → `indexada` ----------------------
    val captured = noteRepository.create(
        NoteDraft(owner = "owner-1", body = "Diseñar el jefe final", track = Track.MECHANICS),
    )
    assertEquals(NoteStatus.CAPTURED, captured.status)
    val scenario1 = successOrchestrator.index(captured)
    assertEquals(NoteStatus.INDEXED, scenario1, "éxito debe devolver INDEXED")
    assertEquals(
        NoteStatus.INDEXED,
        noteRepository.getById(captured.id)?.status,
        "éxito debe persistir INDEXED",
    )
    val expected1 = FakeAiClient().embed(listOf(captured.body)).single().values
    assertEquals(
        expected1,
        embeddingRepository.getByNoteId(captured.id)?.values,
        "éxito debe persistir el embedding de la Nota",
    )

    // --- Scenario 2: fallo de IA no bloquea la captura → `pendiente` --------
    val captured2 = noteRepository.create(
        NoteDraft(owner = "owner-1", body = "Nota cuyo runtime falla", track = null),
    )
    val scenario2 = failingOrchestrator.index(captured2) // no debe lanzar
    assertEquals(NoteStatus.PENDING, scenario2, "un fallo de IA debe devolver PENDING")
    assertEquals(
        NoteStatus.PENDING,
        noteRepository.getById(captured2.id)?.status,
        "un fallo de IA debe persistir PENDING",
    )
    assertNull(
        embeddingRepository.getByNoteId(captured2.id),
        "un fallo de IA no debe crear fila en embedding",
    )

    // --- Scenario 3: respuesta inválida del runtime → `pendiente` ----------
    val invalidClients = listOf(
        "0 vectores" to FixedResponseAiClient(emptyList()),
        ">1 vector" to FixedResponseAiClient(listOf(Embedding(listOf(1f)), Embedding(listOf(2f)))),
        "dimensión 0" to FixedResponseAiClient(listOf(Embedding(emptyList()))),
    )
    for ((label, client) in invalidClients) {
        val note = noteRepository.create(
            NoteDraft(owner = "owner-1", body = "respuesta inválida: $label", track = null),
        )
        assertEquals(NoteStatus.CAPTURED, note.status, "$label parte de CAPTURED")
        val orchestrator = NoteIndexingOrchestrator(
            indexingService = NoteIndexingService(client, embeddingRepository),
            noteRepository = noteRepository,
        )
        val outcome = orchestrator.index(note)
        assertEquals(NoteStatus.PENDING, outcome, "$label debe devolver PENDING")
        assertEquals(
            NoteStatus.PENDING,
            noteRepository.getById(note.id)?.status,
            "$label debe persistir PENDING",
        )
        assertNull(
            embeddingRepository.getByNoteId(note.id),
            "$label no debe persistir ninguna fila",
        )
    }

    // --- Scenario 4: `pendiente` + éxito → `indexada` (reintento) ----------
    val pending = noteRepository.create(
        NoteDraft(
            owner = "owner-1",
            body = "Nota pendiente que se reintenta",
            track = Track.STORY,
            status = NoteStatus.PENDING,
        ),
    )
    assertEquals(NoteStatus.PENDING, pending.status)
    now += 1_000
    val scenario4 = successOrchestrator.index(pending)
    assertEquals(NoteStatus.INDEXED, scenario4, "un reintento exitoso debe dar INDEXED")
    assertEquals(
        NoteStatus.INDEXED,
        noteRepository.getById(pending.id)?.status,
        "un reintento exitoso debe persistir INDEXED",
    )
    val expected4 = FakeAiClient().embed(listOf(pending.body)).single().values
    assertEquals(
        expected4,
        embeddingRepository.getByNoteId(pending.id)?.values,
        "un reintento exitoso debe persistir el embedding",
    )

    // --- Scenario 5: `pendiente` + fallo → sigue `pendiente` (sin `FAILED`) --
    val pending2 = noteRepository.create(
        NoteDraft(
            owner = "owner-1",
            body = "Nota pendiente que vuelve a fallar",
            track = null,
            status = NoteStatus.PENDING,
        ),
    )
    val scenario5 = failingOrchestrator.index(pending2)
    assertEquals(NoteStatus.PENDING, scenario5, "un fallo sobre PENDING debe seguir PENDING")
    val reread5 = noteRepository.getById(pending2.id)
    assertEquals(NoteStatus.PENDING, reread5?.status, "sigue PENDING persistido")
    assertTrue(
        reread5?.status != NoteStatus.FAILED,
        "esta feature nunca escribe FAILED (es de offline-pending-retry)",
    )

    // --- Scenario 6: re-indexado exitoso de `indexada` → sigue `indexada` ---
    val indexed = noteRepository.create(
        NoteDraft(owner = "owner-1", body = "Nota que se indexa primero", track = Track.CHARACTERS),
    )
    assertEquals(NoteStatus.INDEXED, successOrchestrator.index(indexed))
    val changed = noteRepository.getById(indexed.id)!!.copy(body = "Nota re-indexada")
    now += 1_000
    val scenario6 = successOrchestrator.index(changed)
    assertEquals(NoteStatus.INDEXED, scenario6, "re-indexar con éxito sigue INDEXED")
    val expected6 = FakeAiClient().embed(listOf(changed.body)).single().values
    assertEquals(
        expected6,
        embeddingRepository.getByNoteId(indexed.id)?.values,
        "re-indexar con éxito actualiza los values",
    )
    assertEquals(
        1,
        embeddingRepository.getAll().count { it.noteId == indexed.id },
        "re-indexar no duplica filas (una sola por note.id)",
    )

    // --- Scenario 7: re-indexado fallido de `indexada` → no se degrada ------
    val before7 = embeddingRepository.getByNoteId(indexed.id)
    val scenario7 = failingOrchestrator.index(changed)
    assertEquals(NoteStatus.INDEXED, scenario7, "un fallo de re-indexado no degrada INDEXED")
    assertEquals(
        NoteStatus.INDEXED,
        noteRepository.getById(indexed.id)?.status,
        "una Nota ya INDEXED permanece INDEXED ante un fallo",
    )
    assertEquals(
        before7,
        embeddingRepository.getByNoteId(indexed.id),
        "un fallo de re-indexado deja el embedding previo intacto",
    )
    assertSame(
        throwing.failure,
        (NoteIndexingService(throwing, embeddingRepository).index(changed) as IndexingResult.Failed).cause,
        "el fallo del runtime se propaga como causa de IndexingResult.Failed",
    )

    // --- Guardrail: `index` nunca propaga el fallo de IA -------------------
    assertFalse(
        listOf(scenario2, scenario4, scenario5, scenario6, scenario7).contains(NoteStatus.FAILED),
        "el orquestador no escribe FAILED",
    )
}
