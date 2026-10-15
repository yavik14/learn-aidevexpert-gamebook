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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [AiClient] que siempre lanza [AiClientException]; simula el runtime caído. */
private class AlwaysThrowingAiClient : AiClient {
    val failure = AiClientException("runtime no disponible")

    override suspend fun embed(texts: List<String>): List<Embedding> = throw failure

    override suspend fun generate(request: GenerateRequest): String = throw failure
}

/** [AiClient] que delega en [FakeAiClient] y cuenta las llamadas a `embed`. */
private class CountingAiClient : AiClient {
    var embedCalls = 0
    private val delegate = FakeAiClient()

    override suspend fun embed(texts: List<String>): List<Embedding> {
        embedCalls++
        return delegate.embed(texts)
    }

    override suspend fun generate(request: GenerateRequest): String = delegate.generate(request)
}

/** [AiAvailability] fijo; permite forzar `false` sin runtime real. */
private class FixedAiAvailability(private val available: Boolean) : AiAvailability {
    override suspend fun isAvailable(): Boolean = available
}

/**
 * Ejercita [PendingIndexingRetry] sobre un [SqlDriver] con el esquema v6 (`note`
 * con `index_attempts`). Cubre los escenarios 1–5 de la spec: reintento exitoso,
 * fallo bajo presupuesto, agotamiento del presupuesto (`FAILED`), degradación sin
 * efectos cuando la IA no está disponible y aislamiento de las no-`PENDING`.
 *
 * Reutiliza [NoteIndexingOrchestrator]/[NoteIndexingService] (sin modificarlos)
 * con stubs deterministas de [AiClient]; el estilo espeja
 * `NoteIndexingOrchestratorCheck`.
 */
suspend fun verifyPendingIndexingRetry(driver: SqlDriver) {
    val database = createDatabase(
        object : DatabaseDriverFactory {
            override fun createDriver(): SqlDriver = driver
        },
    )
    var sequence = 0
    var now = 1_700_000_000_000L
    val noteRepository = SqlDelightNoteRepository(database, { "note-${sequence++}" }, { now })
    val embeddingRepository = SqlDelightEmbeddingRepository(database = database, clock = { now })

    fun orchestratorFor(client: AiClient): NoteIndexingOrchestrator =
        NoteIndexingOrchestrator(NoteIndexingService(client, embeddingRepository), noteRepository)

    val successOrchestrator = orchestratorFor(FakeAiClient())
    val failingOrchestrator = orchestratorFor(AlwaysThrowingAiClient())

    // Presupuesto inválido: fail-fast.
    assertFailsWith<IllegalArgumentException>("budget = 0 debe fallar") {
        PendingIndexingRetry(successOrchestrator, noteRepository, budget = 0)
    }

    // --- Scenario 1: reintento exitoso `pendiente -> indexada` -------------
    val s1 = noteRepository.create(
        NoteDraft("owner-1", "Diseñar el jefe final", Track.MECHANICS, status = NoteStatus.PENDING),
    )
    noteRepository.setIndexAttempts(s1.id, 1)
    // Sin `availability` explícita: usa `AlwaysAvailableAiAvailability` (default);
    // así el control negativo (b), que la fuerza a `false`, rompe este Scenario.
    val retryWithSuccess = PendingIndexingRetry(successOrchestrator, noteRepository)
    val summary1 = retryWithSuccess.retryPending()
    assertTrue(summary1.available)
    assertEquals(1, summary1.attempted)
    assertEquals(1, summary1.indexed)
    assertEquals(0, summary1.stillPending)
    assertEquals(0, summary1.failed)
    val reloaded1 = noteRepository.getById(s1.id)!!
    assertEquals(NoteStatus.INDEXED, reloaded1.status, "el reintento exitoso deja INDEXED")
    assertEquals(0, reloaded1.indexAttempts, "el éxito resetea el presupuesto a 0")
    assertEquals(
        FakeAiClient().embed(listOf(s1.body)).single().values,
        embeddingRepository.getByNoteId(s1.id)?.values,
        "el reintento exitoso persiste el embedding",
    )

    // --- Scenario 2: fallo bajo presupuesto -> sigue `pendiente` -----------
    val s2 = noteRepository.create(
        NoteDraft("owner-1", "Nota cuyo runtime falla", null, status = NoteStatus.PENDING),
    )
    val summary2 = PendingIndexingRetry(failingOrchestrator, noteRepository).retryPending()
    assertEquals(1, summary2.attempted)
    assertEquals(0, summary2.indexed)
    assertEquals(1, summary2.stillPending)
    assertEquals(0, summary2.failed, "no se promueve a FAILED antes del presupuesto")
    val reloaded2 = noteRepository.getById(s2.id)!!
    assertEquals(NoteStatus.PENDING, reloaded2.status)
    assertEquals(1, reloaded2.indexAttempts, "el fallo bajo presupuesto incrementa el contador")
    assertNull(
        embeddingRepository.getByNoteId(s2.id),
        "un fallo no debe crear fila en embedding",
    )
    // Limpieza para aislar el Scenario 3: si `s2` siguiera `PENDING` participaría
    // en la corrida siguiente y desestabilizaría los conteos del resumen.
    noteRepository.delete(s2.id)

    // --- Scenario 3: agotar el presupuesto -> `pendiente -> fallida` -------
    val s3 = noteRepository.create(
        NoteDraft("owner-1", "Nota que agota el presupuesto", null, status = NoteStatus.PENDING),
    )
    noteRepository.setIndexAttempts(s3.id, PendingIndexingRetry.DEFAULT_RETRY_BUDGET - 1)
    val summary3 = PendingIndexingRetry(failingOrchestrator, noteRepository).retryPending()
    assertEquals(1, summary3.attempted)
    assertEquals(0, summary3.stillPending)
    assertEquals(1, summary3.failed)
    val reloaded3 = noteRepository.getById(s3.id)!!
    assertEquals(PendingIndexingRetry.DEFAULT_RETRY_BUDGET, reloaded3.indexAttempts)
    assertEquals(NoteStatus.FAILED, reloaded3.status, "agotar el presupuesto promueve a FAILED")
    // Terminal para el reintento: `FAILED` no vuelve a aparecer en `PENDING`.
    assertTrue(
        noteRepository.getByStatus(NoteStatus.PENDING).none { it.id == s3.id },
        "una FAILED no se reintenta automáticamente",
    )

    // --- Scenario 4: IA no disponible -> degradación sin efectos -----------
    val s4 = noteRepository.create(
        NoteDraft("owner-1", "Nota pendiente con IA caída", null, status = NoteStatus.PENDING),
    )
    val recording = CountingAiClient()
    val retryUnavailable = PendingIndexingRetry(
        orchestrator = orchestratorFor(recording),
        noteRepository = noteRepository,
        availability = FixedAiAvailability(false),
    )
    val summary4 = retryUnavailable.retryPending()
    assertEquals(false, summary4.available, "sin IA el resumen marca available = false")
    assertEquals(0, summary4.attempted)
    assertEquals(0, summary4.indexed)
    assertEquals(0, summary4.stillPending)
    assertEquals(0, summary4.failed)
    assertEquals(0, recording.embedCalls, "sin IA no se llama a AiClient")
    val reloaded4 = noteRepository.getById(s4.id)!!
    assertEquals(NoteStatus.PENDING, reloaded4.status, "sin IA ningún estado cambia")
    assertEquals(0, reloaded4.indexAttempts, "sin IA no se consume presupuesto")
    assertTrue(
        noteRepository.getByStatus(NoteStatus.PENDING).any { it.id == s4.id },
        "la Nota sigue PENDING (no se degrada a FAILED)",
    )
    noteRepository.delete(s4.id)

    // --- Scenario 5: sólo se procesan `PENDING` ----------------------------
    val captured5 = noteRepository.create(
        NoteDraft("owner-1", "Nota capturada intacta", null, status = NoteStatus.CAPTURED),
    )
    val pending5 = noteRepository.create(
        NoteDraft("owner-1", "Nota pendiente reintentada", Track.STORY, status = NoteStatus.PENDING),
    )
    // Estado previo: s1 INDEXED, s3 FAILED, captured5 CAPTURED, pending5 PENDING.
    val summary5 = retryWithSuccess.retryPending()
    assertEquals(1, summary5.attempted, "sólo la PENDING participa")
    assertEquals(1, summary5.indexed)
    assertEquals(0, summary5.failed)
    assertEquals(
        NoteStatus.CAPTURED,
        noteRepository.getById(captured5.id)?.status,
        "una CAPTURED no se toca",
    )
    assertEquals(
        NoteStatus.INDEXED,
        noteRepository.getById(s1.id)?.status,
        "una INDEXED no se toca",
    )
    val stillFailed = noteRepository.getById(s3.id)!!
    assertEquals(NoteStatus.FAILED, stillFailed.status, "una FAILED no se reintenta")
    assertEquals(PendingIndexingRetry.DEFAULT_RETRY_BUDGET, stillFailed.indexAttempts)
    assertEquals(
        NoteStatus.INDEXED,
        noteRepository.getById(pending5.id)?.status,
        "la PENDING sí se reintenta y queda INDEXED",
    )

    // Una edición (re-indexado explícito con el orquestador) recupera una FAILED.
    assertEquals(
        NoteStatus.INDEXED,
        successOrchestrator.index(stillFailed),
        "reindexar una FAILED la recupera (vía edición/manual)",
    )
    assertEquals(NoteStatus.INDEXED, noteRepository.getById(s3.id)?.status)
}
