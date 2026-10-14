package com.playbook.core.diagnostics

import app.cash.sqldelight.db.SqlDriver
import com.playbook.core.ai.AiClient
import com.playbook.core.ai.AiClientException
import com.playbook.core.ai.Embedding
import com.playbook.core.ai.FakeAiClient
import com.playbook.core.ai.GenerateRequest
import com.playbook.core.ai.IndexingResult
import com.playbook.core.ai.NoteIndexingService
import com.playbook.core.ai.RagAnswer
import com.playbook.core.ai.RagQueryService
import com.playbook.core.db.DatabaseDriverFactory
import com.playbook.core.db.createDatabase
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.Track
import com.playbook.core.repository.EmbeddingRepository
import com.playbook.core.repository.NoteRepository
import com.playbook.core.repository.SqlDelightEmbeddingRepository
import com.playbook.core.repository.SqlDelightNoteRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val FIXED_NOW = 1_700_000_000_000L

/** [AiClient] que siempre falla en `embed`/`generate`; Scenarios 1/4/7. */
private class FailingDiagnosticsAiClient(
    message: String = "runtime no disponible",
) : AiClient {
    val failure = AiClientException(message)

    override suspend fun embed(texts: List<String>): List<Embedding> = throw failure

    override suspend fun generate(request: GenerateRequest): String = throw failure
}

/** Embebe con [FakeAiClient] pero falla en `generate`; Scenario 4. */
private class FailingGenerateDiagnosticsAiClient : AiClient {
    val failure = AiClientException("runtime no disponible (generate)")
    private val delegate = FakeAiClient()

    override suspend fun embed(texts: List<String>): List<Embedding> = delegate.embed(texts)

    override suspend fun generate(request: GenerateRequest): String = throw failure
}

/** Devuelve una respuesta de `embed` fija y cuenta `generate`; Scenario 4. */
private class StubEmbeddingDiagnosticsAiClient(private val embeddings: List<Embedding>) : AiClient {
    var generateCalls = 0

    override suspend fun embed(texts: List<String>): List<Embedding> = embeddings

    override suspend fun generate(request: GenerateRequest): String {
        generateCalls++
        return "stub"
    }
}

/** Registra las llamadas al runtime sin fallar; Scenario 4 (`NoIndexedNotes`). */
private class RecordingDiagnosticsAiClient : AiClient {
    val embeddedTexts = mutableListOf<List<String>>()
    val generateRequests = mutableListOf<GenerateRequest>()
    private val delegate = FakeAiClient()

    override suspend fun embed(texts: List<String>): List<Embedding> {
        embeddedTexts += texts
        return delegate.embed(texts)
    }

    override suspend fun generate(request: GenerateRequest): String {
        generateRequests += request
        return delegate.generate(request)
    }
}

/** Storage en memoria para `FileDiagnostics`; Scenarios 6/7. */
private class FakeDiagnosticStorage : DiagnosticStorage {
    private var content = ""

    override fun append(line: String) {
        content += "$line\n"
    }

    override fun read(): String = content

    override fun overwrite(text: String) {
        content = text
    }
}

/** Storage que falla en toda operación; Scenario 5 (best-effort). */
private class ThrowingDiagnosticStorage : DiagnosticStorage {
    override fun append(line: String): Unit = throw IllegalStateException("append falló")

    override fun read(): String = throw IllegalStateException("read falló")

    override fun overwrite(text: String): Unit = throw IllegalStateException("overwrite falló")
}

/**
 * Escenarios 1–7 de `enrichment-observability-logging`: registro de fallos de IA
 * (indexado/consulta), best-effort del sink, retención del archivo local y
 * privacidad.
 */
suspend fun verifyDiagnostics(driver: SqlDriver) {
    val database = createDatabase(
        object : DatabaseDriverFactory {
            override fun createDriver(): SqlDriver = driver
        },
    )
    var sequence = 0
    val now = FIXED_NOW
    val noteRepository = SqlDelightNoteRepository(
        database = database,
        idFactory = { "note-${sequence++}" },
        clock = { now },
    )
    val embeddingRepository = SqlDelightEmbeddingRepository(database = database, clock = { now })

    verifyIndexingDiagnostics(noteRepository, embeddingRepository)
    verifyQueryDiagnostics(noteRepository, embeddingRepository)
    verifyBestEffort()
    verifyFileDiagnosticsRetentionAndRecovery()
    verifyPrivacy(noteRepository, embeddingRepository)
}

/** Scenario 1/2/3: indexado registra sólo en fallo y nunca escribe embedding. */
private suspend fun verifyIndexingDiagnostics(
    noteRepository: NoteRepository,
    embeddingRepository: EmbeddingRepository,
) {
    // Scenario 1: fallo del runtime → Failed + entrada AI_INDEXING, sin embedding.
    val failureDiagnostics = InMemoryDiagnostics(clock = { FIXED_NOW })
    val note = noteRepository.create(
        NoteDraft("owner-1", "Diseñar el jefe final", Track.MECHANICS),
    )
    val failing = FailingDiagnosticsAiClient()
    val failed = NoteIndexingService(failing, embeddingRepository, failureDiagnostics).index(note)
    assertTrue(failed is IndexingResult.Failed, "un fallo de IA debe dar Failed")
    assertEquals(
        listOf(DiagnosticSource.AI_INDEXING),
        failureDiagnostics.snapshot().map { it.source },
        "el fallo de indexado debe registrar exactamente una entrada AI_INDEXING",
    )
    assertTrue(
        failureDiagnostics.snapshot().single().detail?.isNotBlank() == true,
        "la entrada debe llevar el motivo del fallo",
    )
    assertNull(
        embeddingRepository.getByNoteId(note.id),
        "un fallo de indexado no debe escribir embedding",
    )

    // Scenario 2: indexado exitoso → Indexed y snapshot vacío.
    val successDiagnostics = InMemoryDiagnostics(clock = { FIXED_NOW })
    val successNote = noteRepository.create(NoteDraft("owner-1", "Otra idea", null))
    val indexed = NoteIndexingService(FakeAiClient(), embeddingRepository, successDiagnostics)
        .index(successNote)
    assertTrue(indexed is IndexingResult.Indexed, "un runtime válido debe indexar")
    assertTrue(
        successDiagnostics.snapshot().isEmpty(),
        "el camino de éxito no debe registrar entradas",
    )

    // Scenario 3: respuesta inválida (0 vectores / >1 vector / dimensión 0).
    val invalidResponses = listOf(
        "0 vectores" to emptyList(),
        ">1 vector" to listOf(Embedding(listOf(1f)), Embedding(listOf(2f))),
        "dimensión 0" to listOf(Embedding(emptyList())),
    )
    for ((label, response) in invalidResponses) {
        val diagnostics = InMemoryDiagnostics(clock = { FIXED_NOW })
        val invalidNote = noteRepository.create(NoteDraft("owner-1", "Nota $label", null))
        val outcome = NoteIndexingService(
            StubEmbeddingDiagnosticsAiClient(response),
            embeddingRepository,
            diagnostics,
        ).index(invalidNote)
        assertTrue(outcome is IndexingResult.Failed, "$label debe devolver Failed")
        assertEquals(
            listOf(DiagnosticSource.AI_INDEXING),
            diagnostics.snapshot().map { it.source },
            "$label debe registrar AI_INDEXING",
        )
        assertNull(
            embeddingRepository.getByNoteId(invalidNote.id),
            "$label no debe persistir embedding",
        )
    }
}

/** Scenario 4: fallos de RAG registran AI_QUERY; `NoIndexedNotes` no registra. */
private suspend fun verifyQueryDiagnostics(
    noteRepository: NoteRepository,
    embeddingRepository: EmbeddingRepository,
) {
    val question = "¿cómo diseño al jefe final?"

    suspend fun seedEmbedding(): String {
        val note = noteRepository.create(NoteDraft("owner-1", "Nota indexada", null))
        embeddingRepository.upsert(note.id, FakeAiClient().embed(listOf(note.body)).single().values)
        return note.id
    }

    // Fallo en embed.
    val embedNoteId = seedEmbedding()
    val embedDiagnostics = InMemoryDiagnostics(clock = { FIXED_NOW })
    val embedFailure = RagQueryService(
        FailingDiagnosticsAiClient(),
        noteRepository,
        embeddingRepository,
        diagnostics = embedDiagnostics,
    ).ask(question)
    assertTrue(embedFailure is RagAnswer.Failed, "un fallo en embed debe dar Failed")
    assertEquals(
        listOf(DiagnosticSource.AI_QUERY),
        embedDiagnostics.snapshot().map { it.source },
        "el fallo de embed debe registrar AI_QUERY",
    )
    embeddingRepository.deleteByNoteId(embedNoteId)

    // Fallo en generate.
    val generateNoteId = seedEmbedding()
    val generateDiagnostics = InMemoryDiagnostics(clock = { FIXED_NOW })
    val generateFailure = RagQueryService(
        FailingGenerateDiagnosticsAiClient(),
        noteRepository,
        embeddingRepository,
        diagnostics = generateDiagnostics,
    ).ask(question)
    assertTrue(generateFailure is RagAnswer.Failed, "un fallo en generate debe dar Failed")
    assertEquals(
        listOf(DiagnosticSource.AI_QUERY),
        generateDiagnostics.snapshot().map { it.source },
        "el fallo de generate debe registrar AI_QUERY",
    )
    embeddingRepository.deleteByNoteId(generateNoteId)

    // Respuesta de embed inválida.
    val invalidNoteId = seedEmbedding()
    val invalidResponses = listOf(
        "0 vectores" to emptyList(),
        ">1 vector" to listOf(Embedding(listOf(1f)), Embedding(listOf(2f))),
        "dimensión 0" to listOf(Embedding(emptyList())),
    )
    for ((label, response) in invalidResponses) {
        val diagnostics = InMemoryDiagnostics(clock = { FIXED_NOW })
        val result = RagQueryService(
            StubEmbeddingDiagnosticsAiClient(response),
            noteRepository,
            embeddingRepository,
            diagnostics = diagnostics,
        ).ask(question)
        assertTrue(result is RagAnswer.Failed, "$label debe devolver Failed")
        assertEquals(
            listOf(DiagnosticSource.AI_QUERY),
            diagnostics.snapshot().map { it.source },
            "$label debe registrar AI_QUERY",
        )
    }
    embeddingRepository.deleteByNoteId(invalidNoteId)

    // Sin Notas indexadas → NoIndexedNotes sin invocar el runtime ni registrar.
    embeddingRepository.getAll().forEach { embeddingRepository.deleteByNoteId(it.noteId) }
    val noIndexedDiagnostics = InMemoryDiagnostics(clock = { FIXED_NOW })
    val recording = RecordingDiagnosticsAiClient()
    val noIndexed = RagQueryService(
        recording,
        noteRepository,
        embeddingRepository,
        diagnostics = noIndexedDiagnostics,
    ).ask(question)
    assertEquals(RagAnswer.NoIndexedNotes, noIndexed, "sin embeddings debe dar NoIndexedNotes")
    assertTrue(recording.embeddedTexts.isEmpty(), "sin Notas indexadas no se llama embed")
    assertTrue(recording.generateRequests.isEmpty(), "sin Notas indexadas no se llama generate")
    assertTrue(
        noIndexedDiagnostics.snapshot().isEmpty(),
        "NoIndexedNotes no es un fallo y no debe registrar entradas",
    )
}

/** Scenario 5: `recordFailure` nunca lanza aunque el storage falle. */
private fun verifyBestEffort() {
    val diagnostics = FileDiagnostics(ThrowingDiagnosticStorage(), clock = { FIXED_NOW })
    // Si lanzara, el test fallaría acá.
    diagnostics.recordFailure(DiagnosticSource.VOICE, "voice_failed", "detalle")
    assertEquals("", diagnostics.rawLog(), "un storage que falla no debe romper ni exponer contenido")
}

/** Scenario 6: `FileDiagnostics` acota por cantidad y es recuperable. */
private fun verifyFileDiagnosticsRetentionAndRecovery() {
    var tick = 1_000L
    val storage = FakeDiagnosticStorage()
    val diagnostics = FileDiagnostics(
        storage = storage,
        clock = { tick++ },
        maxEntries = 3,
    )

    diagnostics.recordFailure(DiagnosticSource.AI_INDEXING, "indexing_failed", "fallo 1")
    diagnostics.recordFailure(DiagnosticSource.AI_INDEXING, "indexing_failed", "fallo 2")
    diagnostics.recordFailure(DiagnosticSource.AI_INDEXING, "indexing_failed", "fallo 3")
    assertEquals(3, diagnostics.rawLog().lines().size, "debe retener las entradas registradas")

    // Supera la retención: sólo quedan las más recientes.
    diagnostics.recordFailure(DiagnosticSource.AI_INDEXING, "indexing_failed", "fallo 4")
    val lines = diagnostics.rawLog().lines()
    assertEquals(3, lines.size, "rawLog() no debe superar maxEntries")
    assertTrue(lines.none { it.contains("fallo 1") }, "la entrada más vieja se descarta")
    assertTrue(lines.last().contains("fallo 4"), "la entrada más reciente se conserva")

    lines.forEach { line ->
        val fields = line.split('\t')
        assertEquals(4, fields.size, "cada línea debe tener timestamp/source/summary/detail")
        assertTrue(fields[0].toLong() > 0, "el timestamp debe ser positivo")
        assertEquals("AI_INDEXING", fields[1], "la fuente se registra por su nombre")
        assertEquals("indexing_failed", fields[2], "el resumen es estable")
    }

    // Recuperable tras registros sucesivos (el archivo se reescribe, no se pierde).
    diagnostics.recordFailure(DiagnosticSource.AI_INDEXING, "indexing_failed", "fallo 5")
    assertEquals(
        3,
        diagnostics.rawLog().lines().size,
        "rawLog() sigue acotado tras nuevos registros",
    )
}

/** Scenario 7: el log no contiene body, pregunta, rutas, IDs ni secrets. */
private suspend fun verifyPrivacy(
    noteRepository: NoteRepository,
    embeddingRepository: EmbeddingRepository,
) {
    val secretBody = "SECRET_BODY_DO_NOT_LOG_12345"
    val secretQuestion = "SECRET_QUESTION_DO_NOT_LOG_67890"

    val indexingDiagnostics = InMemoryDiagnostics(clock = { FIXED_NOW })
    val note = noteRepository.create(NoteDraft("owner-1", secretBody, null))
    val failed = NoteIndexingService(
        FailingDiagnosticsAiClient(),
        embeddingRepository,
        indexingDiagnostics,
    ).index(note)
    assertTrue(failed is IndexingResult.Failed)
    val indexingEntry = indexingDiagnostics.snapshot().single()
    assertFalse(indexingEntry.summary.contains(secretBody))
    assertFalse(indexingEntry.detail.orEmpty().contains(secretBody))
    assertFalse(indexingEntry.detail.orEmpty().contains(note.id))

    embeddingRepository.upsert(note.id, FakeAiClient().embed(listOf(secretBody)).single().values)
    val queryDiagnostics = InMemoryDiagnostics(clock = { FIXED_NOW })
    val queryFailed = RagQueryService(
        FailingDiagnosticsAiClient(),
        noteRepository,
        embeddingRepository,
        diagnostics = queryDiagnostics,
    ).ask(secretQuestion)
    assertTrue(queryFailed is RagAnswer.Failed)
    val queryEntry = queryDiagnostics.snapshot().single()
    assertFalse(queryEntry.summary.contains(secretQuestion))
    assertFalse(queryEntry.detail.orEmpty().contains(secretQuestion))
    embeddingRepository.deleteByNoteId(note.id)

    // El sink de archivo no inventa datos sensibles y usa "-" cuando no hay detail.
    val fileDiagnostics = FileDiagnostics(FakeDiagnosticStorage(), clock = { FIXED_NOW })
    fileDiagnostics.recordFailure(DiagnosticSource.STORAGE, "storage_failed")
    val raw = fileDiagnostics.rawLog()
    assertFalse(raw.contains(secretBody), "el log no debe contener el body")
    assertFalse(raw.contains(secretQuestion), "el log no debe contener la pregunta")
    assertFalse(raw.contains(note.id), "el log no debe contener IDs")
    assertFalse(raw.contains("SECRET"), "el log no debe contener datos sensibles")
    assertTrue(raw.endsWith("\t-"), "sin detail se registra el placeholder '-'")
}

/**
 * Ejercita el [DiagnosticStorage] **real de la plataforma** (el `actual`) con
 * `FileDiagnostics`: round-trip, formato de línea, detalle y retención.
 */
fun verifyDiagnosticStorage(storage: DiagnosticStorage) {
    var tick = 5_000L
    val diagnostics = FileDiagnostics(storage = storage, clock = { tick++ }, maxEntries = 2)

    diagnostics.recordFailure(DiagnosticSource.STORAGE, "storage_failed")
    diagnostics.recordFailure(DiagnosticSource.VOICE, "voice_failed", "detalle de voz")
    diagnostics.recordFailure(DiagnosticSource.IMAGE, "image_failed", "PERMISSION_DENIED")

    val lines = diagnostics.rawLog().lines()
    assertEquals(2, lines.size, "el storage real acota a maxEntries")
    assertTrue(lines.last().contains("PERMISSION_DENIED"), "la última entrada se conserva")
    assertFalse(lines.last().contains("storage_failed"), "la más vieja se descarta")
    lines.forEach { line ->
        val fields = line.split('\t')
        assertEquals(4, fields.size, "cada línea debe tener timestamp/source/summary/detail")
        assertTrue(fields[0].toLong() > 0, "el timestamp debe ser positivo")
    }
}

