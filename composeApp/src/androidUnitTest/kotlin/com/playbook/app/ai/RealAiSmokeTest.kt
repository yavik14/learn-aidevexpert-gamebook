package com.playbook.app.ai

import com.playbook.core.ai.GenerateRequest
import com.playbook.core.ai.openai.AiMode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoke manual key-gated, **fuera del gate**. Sólo corre con
 * `PLAYBOOK_AI_SMOKE=1` y un build real (key en `local.properties`). No imprime
 * la key ni bodies: sólo dimensiones/longitudes.
 */
class RealAiSmokeTest {

    @Test
    fun smokeAllSixModels() {
        val enabled = System.getProperty("playbook.ai.smoke") == "1" ||
            System.getenv("PLAYBOOK_AI_SMOKE") == "1"
        if (!enabled) return

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
}
