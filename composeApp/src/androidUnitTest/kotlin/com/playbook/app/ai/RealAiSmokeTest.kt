package com.playbook.app.ai

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.playbook.core.ai.GenerateRequest
import com.playbook.core.ai.NoteIndexingOrchestrator
import com.playbook.core.ai.NoteIndexingService
import com.playbook.core.ai.RagAnswer
import com.playbook.core.ai.RagQueryService
import com.playbook.core.ai.openai.AiMode
import com.playbook.core.db.DatabaseDriverFactory
import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.db.createDatabase
import com.playbook.core.diagnostics.InMemoryDiagnostics
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.NoteStatus
import com.playbook.core.repository.SqlDelightEmbeddingRepository
import com.playbook.core.repository.SqlDelightNoteRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Smoke manual key-gated, **fuera del gate**. Sólo corre con
 * `PLAYBOOK_AI_SMOKE=1` y un build real (key en `local.properties`). No imprime
 * la key ni bodies: sólo dimensiones/longitudes.
 */
class RealAiSmokeTest {

    private fun smokeEnabled(): Boolean =
        System.getProperty("playbook.ai.smoke") == "1" ||
            System.getenv("PLAYBOOK_AI_SMOKE") == "1"

    @Test
    fun smokeAllSixModels() {
        if (!smokeEnabled()) return

        val runtime = createAndroidAiRuntime()
        if (runtime.mode != AiMode.REAL) {
            println("AI smoke: omitido (build fake)")
            return
        }

        runBlocking {
            val embedding = runtime.aiClient.embed(listOf("Nota de humo para embedding"))
            assertEquals(1, embedding.size)
            assertEquals(
                runtime.embeddingDimension,
                embedding.single().values.size,
                "La dimensión real del embedding no coincide con la configurada",
            )

            val chat = runtime.aiClient.generate(
                GenerateRequest("Respondé con una sola palabra: ok"),
            )
            assertTrue(chat.isNotBlank(), "chat devolvió una respuesta vacía")

            val audio = runtime.ttsClient.synthesize("Prueba de voz de Playbook.")
            assertTrue(audio.bytes.isNotEmpty(), "tts devolvió audio vacío")
            assertTrue(audio.mimeType.startsWith("audio/"), "tts devolvió un mime inesperado")

            val transcription = runtime.transcribeClient.transcribe(
                audio = audio.bytes,
                mimeType = audio.mimeType,
            )
            assertTrue(transcription.isNotBlank(), "transcribe devolvió texto vacío")

            val image = runtime.imageClient.generateImage(
                "Un cubo azul minimalista sobre fondo transparente",
            )
            assertTrue(image.bytes.isNotEmpty(), "image devolvió bytes vacíos")
            assertTrue(image.mimeType.startsWith("image/"), "image devolvió un mime inesperado")

            val suggestion = runtime.jevClient.suggest(
                "Un RPG por turnos con combates tácticos y personajes complejos",
            )
            assertTrue(
                suggestion.track != null || suggestion.genres.isNotEmpty(),
                "jev no propuso Track ni Género",
            )

            println(
                "AI smoke OK: embedding=${embedding.single().values.size}, " +
                    "chat=${chat.length}, tts=${audio.bytes.size}, " +
                    "transcribe=${transcription.length}, " +
                    "image=${image.bytes.size} (${image.mimeType}), " +
                    "track=${suggestion.track}, genres=${suggestion.genres.size}",
            )
        }
    }

    /**
     * Indexado real sobre una base in-memory: valida dimensión persistida,
     * transición `INDEXED` y RAG con fuentes. Salida sanitizada (sin key ni
     * bodies).
     */
    @Test
    fun smokeRealIndexingFeedsRag() {
        if (!smokeEnabled()) return

        val runtime = createAndroidAiRuntime()
        if (runtime.mode != AiMode.REAL) {
            println("AI smoke indexing: omitido (build fake)")
            return
        }

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            val database = createDatabase(
                object : DatabaseDriverFactory {
                    override fun createDriver(): SqlDriver = driver
                },
            )
            val noteRepository = SqlDelightNoteRepository(
                database = database,
                idFactory = { "smoke-note" },
                clock = { System.currentTimeMillis() },
            )
            val embeddingRepository = SqlDelightEmbeddingRepository(
                database = database,
                clock = { System.currentTimeMillis() },
            )
            val diagnostics = InMemoryDiagnostics()
            val orchestrator = NoteIndexingOrchestrator(
                NoteIndexingService(
                    aiClient = runtime.aiClient,
                    embeddingRepository = embeddingRepository,
                    diagnostics = diagnostics,
                    expectedDimension = runtime.embeddingDimension,
                ),
                noteRepository,
            )

            runBlocking {
                val note = noteRepository.create(
                    NoteDraft("smoke", "Nota de humo para validar el indexado real", null),
                )
                val status = orchestrator.index(note)
                assertEquals(NoteStatus.INDEXED, status, "el indexado real debe dejar INDEXED")

                val persisted = assertNotNull(
                    embeddingRepository.getByNoteId(note.id),
                    "el indexado real debe persistir el embedding",
                )
                assertEquals(
                    runtime.embeddingDimension,
                    persisted.values.size,
                    "la dimensión persistida no coincide con el runtime",
                )

                val answer = RagQueryService(
                    aiClient = runtime.aiClient,
                    noteRepository = noteRepository,
                    embeddingRepository = embeddingRepository,
                    diagnostics = diagnostics,
                ).ask("¿De qué trata la nota de humo?")
                val answered = assertIs<RagAnswer.Answer>(
                    answer,
                    "RAG debe responder con el runtime real",
                )
                assertTrue(answered.sources.isNotEmpty(), "RAG debe devolver fuentes")
                assertTrue(answered.text.isNotBlank(), "RAG devolvió una respuesta vacía")

                println(
                    "AI smoke indexing OK: dim=${persisted.values.size}, " +
                        "status=$status, sources=${answered.sources.size}, " +
                        "answerLen=${answered.text.length}",
                )
            }
        } finally {
            driver.close()
        }
    }
}
