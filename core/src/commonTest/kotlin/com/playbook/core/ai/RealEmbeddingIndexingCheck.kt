package com.playbook.core.ai

import app.cash.sqldelight.db.SqlDriver
import com.playbook.core.ai.openai.AiRuntimeConfig
import com.playbook.core.ai.openai.HttpRequest
import com.playbook.core.ai.openai.HttpResponse
import com.playbook.core.ai.openai.HttpTransport
import com.playbook.core.ai.openai.openAiAiRuntime
import com.playbook.core.db.DatabaseDriverFactory
import com.playbook.core.db.createDatabase
import com.playbook.core.diagnostics.DiagnosticSource
import com.playbook.core.diagnostics.InMemoryDiagnostics
import com.playbook.core.linking.SemanticLinkingService
import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.Track
import com.playbook.core.repository.SqlDelightEmbeddingRepository
import com.playbook.core.repository.SqlDelightNoteLinkRepository
import com.playbook.core.repository.SqlDelightNoteRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private const val REAL_DIMENSION = 1536
private const val FAKE_DIMENSION = 8
private const val FIXED_NOW = 1_700_000_000_000L
private const val BASE_URL = "https://inference.test/v1"
private const val TEST_KEY = "test-key"
private const val SECRET_BODY = "secret body"
private const val COSINE_EPSILON = 1e-6

/**
 * Transporte falso del runtime real. Responde `/embeddings` con vectores
 * deterministas de [dimension] valores y `/chat/completions` con una respuesta
 * fija; [mode] permite simular red/500/dimensión incorrecta/fallo transitorio.
 * No usa red ni key real (la `apiKey` del config es un dummy de test).
 */
private class ScriptedTransport(
    var mode: Mode = Mode.SUCCESS,
    private val dimension: Int = REAL_DIMENSION,
) : HttpTransport {

    enum class Mode { SUCCESS, WRONG_DIMENSION, HTTP_500, NETWORK_ERROR, FAIL_FIRST }

    var embeddingCalls = 0
    val paths = mutableListOf<String>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        paths += request.url.removePrefix(BASE_URL)
        return when {
            request.url.endsWith("/chat/completions") -> jsonResponse(CHAT_RESPONSE)
            request.url.endsWith("/embeddings") -> {
                embeddingCalls++
                when (mode) {
                    Mode.SUCCESS -> embeddingsResponse(request, dimension)
                    Mode.WRONG_DIMENSION -> embeddingsResponse(request, FAKE_DIMENSION)
                    Mode.HTTP_500 -> httpError500()
                    Mode.NETWORK_ERROR -> throw RuntimeException("timeout $TEST_KEY $SECRET_BODY")
                    Mode.FAIL_FIRST -> if (embeddingCalls == 1) {
                        httpError500()
                    } else {
                        embeddingsResponse(request, dimension)
                    }
                }
            }
            else -> HttpResponse(status = 404, body = ByteArray(0))
        }
    }

    private fun embeddingsResponse(request: HttpRequest, values: Int): HttpResponse {
        val input = Json.parseToJsonElement(request.body!!.decodeToString())
            .jsonObject["input"]!!
            .jsonArray
        val vector = (0 until values).joinToString(",") { "1.0" }
        val data = input.indices.joinToString(",") { index ->
            """{"index":$index,"embedding":[$vector]}"""
        }
        return jsonResponse("""{"data":[$data]}""")
    }

    private fun jsonResponse(body: String): HttpResponse =
        HttpResponse(status = 200, body = body.encodeToByteArray())

    private fun httpError500(): HttpResponse =
        HttpResponse(status = 500, body = """{"error":"$SECRET_BODY"}""".encodeToByteArray())

    private companion object {
        const val CHAT_RESPONSE = """{"choices":[{"message":{"content":"respuesta real"}}]}"""
    }
}

private fun realConfig(dimension: Int = REAL_DIMENSION): AiRuntimeConfig = AiRuntimeConfig(
    baseUrl = BASE_URL,
    apiKey = TEST_KEY,
    embeddingDimension = dimension,
)

/**
 * Integración del camino real sin red: `openAiAiRuntime(config, transport)` +
 * repositorios SQLDelight in-memory + orquestador + retry + reconciliación +
 * Enlaces + RAG. Cubre los Scenarios 2–9 de la spec de `ai-real-embeddings`.
 *
 * No valida calidad semántica ni disponibilidad del proveedor (el transporte es
 * falso); valida contrato de dimensión y la máquina de estados existente.
 */
suspend fun verifyRealEmbeddingIndexing(driver: SqlDriver) {
    val database = createDatabase(
        object : DatabaseDriverFactory {
            override fun createDriver(): SqlDriver = driver
        },
    )
    var sequence = 0
    var now = FIXED_NOW
    val noteRepository = SqlDelightNoteRepository(
        database = database,
        idFactory = { "note-${sequence++}" },
        clock = { now },
    )
    val embeddingRepository = SqlDelightEmbeddingRepository(database = database, clock = { now })
    val linkRepository = SqlDelightNoteLinkRepository(database = database, clock = { now })
    val diagnostics = InMemoryDiagnostics(clock = { now })

    fun newNote(body: String, status: NoteStatus = NoteStatus.CAPTURED): Note =
        noteRepository.create(NoteDraft("owner-1", body, Track.MECHANICS, status = status))

    fun reset() {
        noteRepository.getAll().forEach { noteRepository.delete(it.id) }
        linkRepository.deleteAll()
    }

    fun serviceFor(runtime: com.playbook.core.ai.openai.AiRuntime): NoteIndexingService =
        NoteIndexingService(
            aiClient = runtime.aiClient,
            embeddingRepository = embeddingRepository,
            diagnostics = diagnostics,
            expectedDimension = runtime.embeddingDimension,
        )

    fun orchestratorFor(runtime: com.playbook.core.ai.openai.AiRuntime): NoteIndexingOrchestrator =
        NoteIndexingOrchestrator(serviceFor(runtime), noteRepository)

    // --- Scenario 2: indexado real exitoso con transporte falso -------------
    run {
        val transport = ScriptedTransport()
        val runtime = openAiAiRuntime(realConfig(), transport)
        assertEquals(REAL_DIMENSION, runtime.embeddingDimension)

        val note = newNote("Diseñar el jefe final en tres fases")
        val status = orchestratorFor(runtime).index(note)

        assertEquals(NoteStatus.INDEXED, status, "un embedding real válido deja INDEXED")
        val persisted = assertNotNull(
            embeddingRepository.getByNoteId(note.id),
            "el indexado real debe persistir el embedding",
        )
        assertEquals(REAL_DIMENSION, persisted.values.size)
        assertEquals(NoteStatus.INDEXED, noteRepository.getById(note.id)?.status)
        assertEquals(0, noteRepository.getById(note.id)?.indexAttempts)
        assertTrue(
            transport.paths.isNotEmpty() && transport.paths.all { it == "/embeddings" },
            "el indexado sólo llama /embeddings",
        )
    }

    // --- Scenario 3: dimensión inesperada no se persiste --------------------
    run {
        reset()
        val transport = ScriptedTransport(mode = ScriptedTransport.Mode.WRONG_DIMENSION)
        val runtime = openAiAiRuntime(realConfig(), transport)
        val note = newNote("Nota con proveedor desalineado")
        diagnostics.clear()

        val result = serviceFor(runtime).index(note)

        val failed = assertIs<IndexingResult.Failed>(
            result,
            "un vector de 8 con runtime de 1536 falla",
        )
        val message = failed.cause.message.orEmpty()
        assertEquals("embedding devolvió dimensión 8, se esperaba 1536", message)
        assertFalse(message.contains(TEST_KEY), "el mensaje no debe incluir la key")
        assertFalse(message.contains(note.body), "el mensaje no debe incluir el body")
        assertNull(
            embeddingRepository.getByNoteId(note.id),
            "un mismatch no debe crear fila embedding",
        )

        // El orquestador deja PENDING (nunca FAILED) y el diagnóstico es AI_INDEXING.
        val status = orchestratorFor(runtime).index(note)
        assertEquals(NoteStatus.PENDING, status)
        assertEquals(NoteStatus.PENDING, noteRepository.getById(note.id)?.status)
        val entry = diagnostics.snapshot().last()
        assertEquals(DiagnosticSource.AI_INDEXING, entry.source)
        assertEquals("indexing_failed", entry.summary)
        assertFalse(entry.detail.orEmpty().contains(TEST_KEY))
        assertFalse(entry.detail.orEmpty().contains(note.body))
    }

    // --- Scenario 4: re-indexado fallido no degrada una Nota INDEXED --------
    run {
        reset()
        val transport = ScriptedTransport()
        val runtime = openAiAiRuntime(realConfig(), transport)
        val orchestrator = orchestratorFor(runtime)
        val note = newNote("Nota ya indexada con embedding real")
        assertEquals(NoteStatus.INDEXED, orchestrator.index(note))
        val before = assertNotNull(embeddingRepository.getByNoteId(note.id))

        transport.mode = ScriptedTransport.Mode.HTTP_500
        assertEquals(
            NoteStatus.INDEXED,
            orchestrator.index(noteRepository.getById(note.id)!!),
            "un HTTP 500 en re-indexado no degrada INDEXED",
        )
        assertEquals(before, embeddingRepository.getByNoteId(note.id))

        transport.mode = ScriptedTransport.Mode.WRONG_DIMENSION
        assertEquals(
            NoteStatus.INDEXED,
            orchestrator.index(noteRepository.getById(note.id)!!),
            "un mismatch en re-indexado no degrada INDEXED",
        )
        assertEquals(before, embeddingRepository.getByNoteId(note.id))
    }

    // --- Scenario 5: RAG alimentado por embeddings reales -------------------
    run {
        reset()
        val transport = ScriptedTransport()
        val runtime = openAiAiRuntime(realConfig(), transport)
        val orchestrator = orchestratorFor(runtime)
        val first = newNote("Diseñar el jefe final")
        val second = newNote("Curva de dificultad del tutorial")
        assertEquals(NoteStatus.INDEXED, orchestrator.index(first))
        assertEquals(NoteStatus.INDEXED, orchestrator.index(second))

        val rag = RagQueryService(
            aiClient = runtime.aiClient,
            noteRepository = noteRepository,
            embeddingRepository = embeddingRepository,
            diagnostics = diagnostics,
        )
        val answer = rag.ask("¿cómo diseño al jefe final?")
        val answered = assertIs<RagAnswer.Answer>(
            answer,
            "con embeddings reales RAG debe responder",
        )
        assertTrue(answered.sources.isNotEmpty(), "RAG debe devolver fuentes")
        assertEquals("respuesta real", answered.text)
        assertEquals(
            setOf(first.id, second.id),
            answered.sources.map { it.note.id }.toSet(),
            "las fuentes son las Notas indexadas con el runtime real",
        )

        // Una pregunta que no puede embeberse devuelve Failed sin inventar respuesta.
        transport.mode = ScriptedTransport.Mode.NETWORK_ERROR
        assertIs<RagAnswer.Failed>(
            rag.ask("pregunta sin red"),
            "un fallo de embed en RAG no debe inventar respuesta",
        )
    }

    // --- Scenario 6: Enlaces alimentados por embeddings reales --------------
    run {
        reset()
        now += 1_000
        val transport = ScriptedTransport()
        val runtime = openAiAiRuntime(realConfig(), transport)
        val orchestrator = orchestratorFor(runtime)
        val first = newNote("Nota A sobre economía")
        val second = newNote("Nota B sobre economía")
        assertEquals(NoteStatus.INDEXED, orchestrator.index(first))
        assertEquals(NoteStatus.INDEXED, orchestrator.index(second))
        val statusesBefore = noteRepository.getAll().map { it.id to it.status }
        val embeddingsBefore = embeddingRepository.getAll()

        val summary = SemanticLinkingService(embeddingRepository, linkRepository).recomputeAll()

        assertEquals(2, summary.notesConsidered)
        assertEquals(1, summary.linksWritten, "dos vectores idénticos generan un Enlace")
        val link = linkRepository.getAll().single()
        assertEquals(1.0, link.score, COSINE_EPSILON)
        assertEquals(
            statusesBefore,
            noteRepository.getAll().map { it.id to it.status },
            "recomponer Enlaces no toca estados",
        )
        assertEquals(
            embeddingsBefore,
            embeddingRepository.getAll(),
            "recomponer Enlaces no toca embeddings",
        )
    }

    // --- Scenario 7: fallo de red conserva PENDING y consume presupuesto ----
    run {
        reset()
        val transport = ScriptedTransport(mode = ScriptedTransport.Mode.NETWORK_ERROR)
        val runtime = openAiAiRuntime(realConfig(), transport)
        val orchestrator = orchestratorFor(runtime)
        val note = newNote("Nota con red caída")
        diagnostics.clear()

        // El indexado directo nunca lanza y deja PENDING (no escribe FAILED).
        assertEquals(NoteStatus.PENDING, orchestrator.index(note))
        assertEquals(NoteStatus.PENDING, noteRepository.getById(note.id)?.status)
        assertEquals(0, noteRepository.getById(note.id)?.indexAttempts)
        assertNull(embeddingRepository.getByNoteId(note.id))

        val retry = PendingIndexingRetry(
            orchestrator = orchestrator,
            noteRepository = noteRepository,
            availability = runtime.availability,
            budget = 2,
        )

        assertEquals(
            RetrySummary(available = true, attempted = 1, indexed = 0, stillPending = 1, failed = 0),
            retry.retryPending(),
            "el primer reintento falla pero sigue PENDING",
        )
        assertEquals(1, noteRepository.getById(note.id)?.indexAttempts)

        transport.mode = ScriptedTransport.Mode.HTTP_500
        assertEquals(
            RetrySummary(available = true, attempted = 1, indexed = 0, stillPending = 0, failed = 1),
            retry.retryPending(),
            "el segundo reintento agota el presupuesto y promueve a FAILED",
        )
        assertEquals(2, noteRepository.getById(note.id)?.indexAttempts)
        assertEquals(NoteStatus.FAILED, noteRepository.getById(note.id)?.status)
        assertNull(
            embeddingRepository.getByNoteId(note.id),
            "nunca queda un embedding huérfano",
        )
        assertTrue(
            diagnostics.snapshot().none { entry ->
                val text = entry.detail.orEmpty()
                text.contains(TEST_KEY) || text.contains(SECRET_BODY) || text.contains("tiempo")
            },
            "el diagnóstico no debe incluir key, body ni detalles del transporte",
        )
    }

    // --- Scenario 8: fallo transitorio se recupera en el reintento ----------
    run {
        reset()
        val transport = ScriptedTransport(mode = ScriptedTransport.Mode.FAIL_FIRST)
        val runtime = openAiAiRuntime(realConfig(), transport)
        val orchestrator = orchestratorFor(runtime)
        val note = newNote("Nota con fallo transitorio")

        // Primera llamada a `/embeddings`: falla y deja la Nota PENDING.
        assertEquals(NoteStatus.PENDING, orchestrator.index(note))
        assertEquals(1, transport.embeddingCalls)

        val summary = PendingIndexingRetry(
            orchestrator = orchestrator,
            noteRepository = noteRepository,
            availability = runtime.availability,
            budget = 2,
        ).retryPending()

        assertEquals(
            RetrySummary(available = true, attempted = 1, indexed = 1, stillPending = 0, failed = 0),
            summary,
        )
        assertEquals(2, transport.embeddingCalls, "falla la primera llamada y la segunda responde")
        assertEquals(NoteStatus.INDEXED, noteRepository.getById(note.id)?.status)
        assertEquals(0, noteRepository.getById(note.id)?.indexAttempts)
        val persisted = assertNotNull(embeddingRepository.getByNoteId(note.id))
        assertEquals(REAL_DIMENSION, persisted.values.size)
    }

    // --- Scenario 9: reconciliación fake (8) → real (1536) -------------------
    run {
        reset()
        now += 1_000
        val transport = ScriptedTransport()
        val runtime = openAiAiRuntime(realConfig(), transport)

        val fakeNote = newNote("Nota indexada por el fake")
        embeddingRepository.upsert(fakeNote.id, List(FAKE_DIMENSION) { 1f })
        noteRepository.updateStatus(fakeNote.id, NoteStatus.INDEXED)
        val realNote = newNote("Nota ya indexada por el real")
        embeddingRepository.upsert(realNote.id, List(REAL_DIMENSION) { 1f })
        noteRepository.updateStatus(realNote.id, NoteStatus.INDEXED)

        val reconcile = EmbeddingDimensionReconciler(embeddingRepository, noteRepository)
            .reconcile(runtime.embeddingDimension)

        assertEquals(ReconcileSummary(expectedDimension = REAL_DIMENSION, cleared = 1), reconcile)
        assertNull(embeddingRepository.getByNoteId(fakeNote.id), "sólo se borra el embedding de 8")
        assertEquals(NoteStatus.PENDING, noteRepository.getById(fakeNote.id)?.status)
        assertEquals(0, noteRepository.getById(fakeNote.id)?.indexAttempts)
        assertEquals(
            REAL_DIMENSION,
            embeddingRepository.getByNoteId(realNote.id)?.values?.size,
            "el embedding compatible no se toca",
        )
        assertEquals(NoteStatus.INDEXED, noteRepository.getById(realNote.id)?.status)

        val retrySummary = PendingIndexingRetry(
            orchestrator = orchestratorFor(runtime),
            noteRepository = noteRepository,
            availability = runtime.availability,
        ).retryPending()
        assertEquals(1, retrySummary.indexed)
        assertEquals(NoteStatus.INDEXED, noteRepository.getById(fakeNote.id)?.status)
        assertEquals(
            REAL_DIMENSION,
            embeddingRepository.getByNoteId(fakeNote.id)?.values?.size,
            "el reintento re-indexa con 1536",
        )

        val answer = RagQueryService(
            aiClient = runtime.aiClient,
            noteRepository = noteRepository,
            embeddingRepository = embeddingRepository,
        ).ask("¿qué notas tengo?")
        val answered = assertIs<RagAnswer.Answer>(
            answer,
            "RAG vuelve a responder tras reconciliar",
        )
        assertTrue(answered.sources.isNotEmpty())
    }
}
