package com.playbook.core.ai

import app.cash.sqldelight.db.SqlDriver
import com.playbook.core.db.DatabaseDriverFactory
import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.db.createDatabase
import com.playbook.core.diagnostics.DiagnosticSource
import com.playbook.core.diagnostics.InMemoryDiagnostics
import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft
import com.playbook.core.repository.SqlDelightEmbeddingRepository
import com.playbook.core.repository.SqlDelightNoteRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val FIXED_NOW = 1_700_000_000_000L
private const val COSINE_TOLERANCE = 1e-5f

/** [AiClient] que registra los textos de `embed` y los `GenerateRequest`, delegando en [FakeAiClient]. */
private class RecordingRagAiClient(private val delegate: AiClient = FakeAiClient()) : AiClient {
    val embeddedTexts = mutableListOf<List<String>>()
    val generateRequests = mutableListOf<GenerateRequest>()

    override suspend fun embed(texts: List<String>): List<Embedding> {
        embeddedTexts += texts
        return delegate.embed(texts)
    }

    override suspend fun generate(request: GenerateRequest): String {
        generateRequests += request
        return delegate.generate(request)
    }
}

/** Falla siempre; para el Escenario 3 (fallo en `embed`). */
private class FailingEmbedAiClient : AiClient {
    val failure = AiClientException("runtime no disponible (embed)")

    override suspend fun embed(texts: List<String>): List<Embedding> = throw failure

    override suspend fun generate(request: GenerateRequest): String = throw failure
}

/** Embebe con [FakeAiClient] pero falla en `generate`; para el Escenario 3. */
private class FailingGenerateAiClient : AiClient {
    val failure = AiClientException("runtime no disponible (generate)")
    var generateCalls = 0
    private val delegate = FakeAiClient()

    override suspend fun embed(texts: List<String>): List<Embedding> = delegate.embed(texts)

    override suspend fun generate(request: GenerateRequest): String {
        generateCalls++
        throw failure
    }
}

/** Devuelve la respuesta de `embed` indicada y cuenta las llamadas a `generate`; Escenario 4. */
private class StubEmbeddingAiClient(private val embeddings: List<Embedding>) : AiClient {
    var generateCalls = 0

    override suspend fun embed(texts: List<String>): List<Embedding> = embeddings

    override suspend fun generate(request: GenerateRequest): String {
        generateCalls++
        return "stub"
    }
}

/** Devuelve una respuesta de chat fija y registra el último `GenerateRequest`. */
private class FixedAnswerAiClient(private val answer: String) : AiClient {
    var lastRequest: GenerateRequest? = null
    private val delegate = FakeAiClient()

    override suspend fun embed(texts: List<String>): List<Embedding> = delegate.embed(texts)

    override suspend fun generate(request: GenerateRequest): String {
        lastRequest = request
        return answer
    }
}

/** Devuelve un vector de pregunta fijo y registra el último `GenerateRequest`; Escenario 6. */
private class FixedEmbedAiClient(private val questionVector: List<Float>) : AiClient {
    var lastRequest: GenerateRequest? = null

    override suspend fun embed(texts: List<String>): List<Embedding> =
        listOf(Embedding(questionVector))

    override suspend fun generate(request: GenerateRequest): String {
        lastRequest = request
        return "respuesta fija"
    }
}

private fun databaseFrom(driver: SqlDriver): PlaybookDatabase = createDatabase(
    object : DatabaseDriverFactory {
        override fun createDriver(): SqlDriver = driver
    },
)

/** Escenario 5: contrato de [cosineSimilarity] (control negativo del validador). */
fun verifyCosineSimilarity() {
    val a = listOf(1f, 2f, 3f)
    assertEquals(1f, cosineSimilarity(a, a), COSINE_TOLERANCE, "el mismo vector debe dar 1f")
    assertEquals(
        0f,
        cosineSimilarity(listOf(1f, 0f), listOf(0f, 1f)),
        COSINE_TOLERANCE,
        "vectores ortogonales deben dar 0f",
    )
    assertEquals(
        -1f,
        cosineSimilarity(listOf(1f, 0f), listOf(-1f, 0f)),
        COSINE_TOLERANCE,
        "vectores opuestos deben dar -1f",
    )

    val nullVector = cosineSimilarity(listOf(0f, 0f), listOf(1f, 2f))
    assertEquals(0f, nullVector, "un vector nulo debe dar 0f")
    assertFalse(nullVector.isNaN(), "un vector nulo no debe producir NaN")

    assertEquals(
        cosineSimilarity(a, listOf(0.5f, 1.5f, 2.5f)),
        cosineSimilarity(listOf(0.5f, 1.5f, 2.5f), a),
        COSINE_TOLERANCE,
        "cos(a, b) debe ser igual a cos(b, a)",
    )

    assertFailsWith<IllegalArgumentException>("tamaños distintos deben lanzar") {
        cosineSimilarity(listOf(1f), listOf(1f, 2f))
    }
}

/** Escenario 1: recupera las Notas por similitud y genera la respuesta anclada en ellas. */
suspend fun verifyRagAnswer(driver: SqlDriver) {
    val database = databaseFrom(driver)
    var sequence = 0
    val noteRepository = SqlDelightNoteRepository(database, { "note-${sequence++}" }, { FIXED_NOW })
    val embeddingRepository = SqlDelightEmbeddingRepository(database, { FIXED_NOW })

    val notes = listOf(
        noteRepository.create(NoteDraft("owner-1", "Diseñar el jefe final con tres fases", null)),
        noteRepository.create(NoteDraft("owner-1", "Curva de dificultad del nivel tutorial", null)),
        noteRepository.create(NoteDraft("owner-1", "Economía de recursos y monedas", null)),
    )

    // Indexa con el mismo AiClient que luego consulta (Escenario 1).
    val client = RecordingRagAiClient()
    val indexingService = NoteIndexingService(client, embeddingRepository)
    notes.forEach { note ->
        assertTrue(
            indexingService.index(note) is IndexingResult.Indexed,
            "la Nota de setup debe indexarse",
        )
    }
    assertEquals(notes.size, embeddingRepository.getAll().size, "cada Nota debe quedar indexada")
    client.embeddedTexts.clear()

    val service = RagQueryService(client, noteRepository, embeddingRepository)
    val question = "  ¿cómo diseño al jefe final?  "
    val result = service.ask(question)

    assertTrue(result is RagAnswer.Answer, "con Notas indexadas debe devolver Answer")
    val answer = result as RagAnswer.Answer

    // embed una vez con la pregunta (trim) y generate una vez.
    assertEquals(
        listOf(listOf(question.trim())),
        client.embeddedTexts,
        "embed debe llamarse una sola vez con la pregunta",
    )
    assertEquals(1, client.generateRequests.size, "generate debe llamarse una sola vez")
    val request = client.generateRequests.single()
    assertEquals(question.trim(), request.prompt, "el prompt es la pregunta normalizada")
    assertEquals(
        answer.sources.map { it.note.body },
        request.context,
        "el context son los cuerpos de las sources, en orden",
    )

    // sources ordenadas por score descendente y a lo sumo maxSources.
    assertEquals(
        answer.sources.map { it.score }.sortedDescending(),
        answer.sources.map { it.score },
        "sources debe venir ordenado por score descendente",
    )
    assertTrue(
        answer.sources.size <= RagQueryService.DEFAULT_MAX_SOURCES,
        "sources no supera maxSources",
    )

    // La respuesta es la que devolvió el runtime (FakeAiClient).
    assertEquals(FakeAiClient().generate(request), answer.text, "text es la respuesta del runtime")

    // Ranking esperado (mismo cálculo coseno + desempate por note.id).
    val questionVector = FakeAiClient().embed(listOf(question.trim())).single().values
    val expected = notes
        .map { note ->
            RetrievedNote(
                note = note,
                score = cosineSimilarity(
                    questionVector,
                    FakeAiClient().embed(listOf(note.body)).single().values,
                ),
            )
        }
        .sortedWith(compareByDescending<RetrievedNote> { it.score }.thenBy { it.note.id })
        .take(RagQueryService.DEFAULT_MAX_SOURCES)
    assertEquals(
        expected.map { it.note.id },
        answer.sources.map { it.note.id },
        "la Nota más cercana primero",
    )
    expected.zip(answer.sources).forEach { (expectedNote, actualNote) ->
        assertEquals(expectedNote.score, actualNote.score, COSINE_TOLERANCE)
    }
}

/** Escenario 2: sin embeddings no se llama al runtime. */
suspend fun verifyRagNoIndexedNotes(driver: SqlDriver) {
    val database = databaseFrom(driver)
    var sequence = 0
    val noteRepository = SqlDelightNoteRepository(database, { "note-${sequence++}" }, { FIXED_NOW })
    val embeddingRepository = SqlDelightEmbeddingRepository(database, { FIXED_NOW })
    noteRepository.create(NoteDraft("owner-1", "nota persistida pero sin embedding", null))

    val client = RecordingRagAiClient()
    val result = RagQueryService(client, noteRepository, embeddingRepository).ask("¿qué diseñé?")

    assertEquals(RagAnswer.NoIndexedNotes, result, "sin embeddings debe devolver NoIndexedNotes")
    assertTrue(client.embeddedTexts.isEmpty(), "sin Notas indexadas no se llama embed")
    assertTrue(client.generateRequests.isEmpty(), "sin Notas indexadas no se llama generate")
}

/** Escenario 3: fallo del runtime → Failed con la causa propagada. */
suspend fun verifyRagRuntimeFailure(driver: SqlDriver) {
    val database = databaseFrom(driver)
    var sequence = 0
    val noteRepository = SqlDelightNoteRepository(database, { "note-${sequence++}" }, { FIXED_NOW })
    val embeddingRepository = SqlDelightEmbeddingRepository(database, { FIXED_NOW })
    val note = noteRepository.create(NoteDraft("owner-1", "nota indexada", null))
    embeddingRepository.upsert(note.id, FakeAiClient().embed(listOf(note.body)).single().values)

    val failingEmbed = FailingEmbedAiClient()
    val embedFailure = RagQueryService(failingEmbed, noteRepository, embeddingRepository)
        .ask("pregunta")
    assertTrue(embedFailure is RagAnswer.Failed, "un fallo en embed debe dar Failed")
    assertSame(
        failingEmbed.failure,
        (embedFailure as RagAnswer.Failed).cause,
        "Failed debe propagar la causa de embed",
    )

    val failingGenerate = FailingGenerateAiClient()
    val generateFailure = RagQueryService(failingGenerate, noteRepository, embeddingRepository)
        .ask("pregunta")
    assertTrue(generateFailure is RagAnswer.Failed, "un fallo en generate debe dar Failed")
    assertSame(
        failingGenerate.failure,
        (generateFailure as RagAnswer.Failed).cause,
        "Failed debe propagar la causa de generate",
    )
    assertEquals(1, failingGenerate.generateCalls, "generate se intentó antes de fallar")
}

/** Escenario 4: respuesta inválida de `embed` no produce respuesta ni llama a `generate`. */
suspend fun verifyRagInvalidEmbedding(driver: SqlDriver) {
    val database = databaseFrom(driver)
    var sequence = 0
    val noteRepository = SqlDelightNoteRepository(database, { "note-${sequence++}" }, { FIXED_NOW })
    val embeddingRepository = SqlDelightEmbeddingRepository(database, { FIXED_NOW })
    val note = noteRepository.create(NoteDraft("owner-1", "nota indexada", null))
    embeddingRepository.upsert(note.id, FakeAiClient().embed(listOf(note.body)).single().values)

    val invalidClients = listOf(
        "0 vectores" to StubEmbeddingAiClient(emptyList()),
        ">1 vector" to StubEmbeddingAiClient(
            listOf(Embedding(listOf(1f)), Embedding(listOf(2f))),
        ),
        "dimensión 0" to StubEmbeddingAiClient(listOf(Embedding(emptyList()))),
    )
    for ((label, client) in invalidClients) {
        val result = RagQueryService(client, noteRepository, embeddingRepository).ask("pregunta")
        assertTrue(result is RagAnswer.Failed, "$label debe devolver Failed")
        assertNotNull(
            (result as RagAnswer.Failed).cause,
            "$label debe reportar un AiClientException",
        )
        assertEquals(0, client.generateCalls, "$label no debe llamar generate")
    }
}

/** Escenario 6: ranking determinista, top-K y dimensiones incompatibles. */
suspend fun verifyRagRanking(driver: SqlDriver) {
    val database = databaseFrom(driver)
    var sequence = 0
    val noteRepository = SqlDelightNoteRepository(database, { "note-${sequence++}" }, { FIXED_NOW })
    val embeddingRepository = SqlDelightEmbeddingRepository(database, { FIXED_NOW })

    fun note(body: String, values: List<Float>): Note {
        val created = noteRepository.create(NoteDraft("owner-1", body, null))
        embeddingRepository.upsert(created.id, values)
        return created
    }

    val exact = note("exacto", listOf(1f, 0f)) // score 1
    note("diagonal", listOf(1f, 1f)) // ~0.707
    note("ortogonal", listOf(0f, 1f)) // 0
    note("opuesto", listOf(-1f, 0f)) // -1
    note("incompatible", listOf(1f, 0f, 0f)) // dimensión 3 → se descarta
    val tieA = note("empate A", listOf(1f, 0f)) // score 1 (desempata por id)
    val tieB = note("empate B", listOf(1f, 0f)) // score 1 (desempata por id)

    val client = FixedEmbedAiClient(listOf(1f, 0f))
    val result = RagQueryService(client, noteRepository, embeddingRepository, maxSources = 3)
        .ask("  pregunta  ")

    assertTrue(result is RagAnswer.Answer, "con candidatos válidos debe devolver Answer")
    val answer = result as RagAnswer.Answer
    assertEquals(3, answer.sources.size, "sources se limita a maxSources")
    assertEquals(
        listOf(exact.id, tieA.id, tieB.id),
        answer.sources.map { it.note.id },
        "top-K por score descendente con desempate estable por note.id",
    )
    assertTrue(
        answer.sources.none { it.note.body == "incompatible" },
        "los embeddings de dimensión incompatible se descartan",
    )
    assertNotNull(client.lastRequest)
    assertEquals("pregunta", client.lastRequest!!.prompt)
    assertEquals(
        listOf("exacto", "empate A", "empate B"),
        client.lastRequest!!.context,
        "el context sigue el orden del ranking",
    )

    // maxSources = 0 → no se recupera ninguna fuente.
    assertEquals(
        RagAnswer.NoIndexedNotes,
        RagQueryService(client, noteRepository, embeddingRepository, maxSources = 0)
            .ask("pregunta"),
        "con maxSources = 0 no hay fuentes",
    )

    // Todos los candidatos con dimensión incompatible → sin fuentes.
    assertEquals(
        RagAnswer.NoIndexedNotes,
        RagQueryService(
            aiClient = FixedEmbedAiClient(listOf(1f, 0f, 0f, 0f, 0f)),
            noteRepository = noteRepository,
            embeddingRepository = embeddingRepository,
        ).ask("pregunta"),
        "si todos los candidatos se descartan no hay fuentes",
    )
}


/** Escenario 4: contrato de chat real y normalización del Q&A. */
suspend fun verifyRagRealChatContract(driver: SqlDriver) {
    val database = databaseFrom(driver)
    var sequence = 0
    val noteRepository = SqlDelightNoteRepository(database, { "note-${sequence++}" }, { FIXED_NOW })
    val embeddingRepository = SqlDelightEmbeddingRepository(database, { FIXED_NOW })
    val note = noteRepository.create(NoteDraft("owner-1", "Nota fuente sobre jefes finales", null))
    embeddingRepository.upsert(note.id, FakeAiClient().embed(listOf(note.body)).single().values)

    val raw = "  ```markdown\n# Respuesta\n\n**Contenido**\n\n\nFin\n```  "
    val client = FixedAnswerAiClient(raw)
    val diagnostics = InMemoryDiagnostics(clock = { FIXED_NOW })
    val result = RagQueryService(
        client,
        noteRepository,
        embeddingRepository,
        diagnostics = diagnostics,
    ).ask("  pregunta  ")
    assertTrue(result is RagAnswer.Answer, "con respuesta válida debe devolver Answer")
    val answer = result as RagAnswer.Answer
    assertEquals("Respuesta\n\nContenido\n\nFin", answer.text)
    assertEquals(listOf(note.id), answer.sources.map { it.note.id })
    val request = assertNotNull(client.lastRequest)
    assertEquals("pregunta", request.prompt)
    assertEquals(listOf(note.body), request.context)
    assertEquals(RAG_INSTRUCTIONS, request.instructions)
    assertEquals(0.2, request.temperature)
    assertEquals(600, request.maxTokens)
    assertTrue(diagnostics.snapshot().isEmpty(), "una respuesta válida no registra fallo")

    val refusal = RagQueryService(
        FixedAnswerAiClient("No tengo información suficiente en las notas."),
        noteRepository,
        embeddingRepository,
    ).ask("pregunta")
    assertTrue(refusal is RagAnswer.Answer, "un refusal textual es una Answer")
    assertEquals(
        "No tengo información suficiente en las notas.",
        (refusal as RagAnswer.Answer).text,
    )

    val emptyDiagnostics = InMemoryDiagnostics(clock = { FIXED_NOW })
    val empty = RagQueryService(
        FixedAnswerAiClient("```\n\n```"),
        noteRepository,
        embeddingRepository,
        diagnostics = emptyDiagnostics,
    ).ask("pregunta")
    assertTrue(empty is RagAnswer.Failed, "una respuesta vacía debe ser Failed")
    assertEquals(1, emptyDiagnostics.snapshot().size)
    assertEquals(DiagnosticSource.AI_QUERY, emptyDiagnostics.snapshot().single().source)
    assertFalse(emptyDiagnostics.snapshot().single().detail?.contains("pregunta") == true)
}
