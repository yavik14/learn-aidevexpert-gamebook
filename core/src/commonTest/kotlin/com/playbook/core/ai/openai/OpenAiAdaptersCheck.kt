package com.playbook.core.ai.openai

import com.playbook.core.ai.AiClientException
import com.playbook.core.ai.FakeAiClient
import com.playbook.core.ai.GenerateRequest
import com.playbook.core.model.Track
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private class RecordingTransport(
    private val responder: (HttpRequest) -> HttpResponse,
) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return responder(request)
    }
}

private fun realConfig(): AiRuntimeConfig = AiRuntimeConfig(
    baseUrl = "https://inference.test/v1",
    apiKey = "test-key",
    embeddingDimension = 3,
)

private fun jsonResponse(json: String): HttpResponse =
    HttpResponse(status = 200, body = json.encodeToByteArray())

class OpenAiAdaptersCheck {

    @Test
    fun fakeRuntimeIsDefaultAndOffline() = runTest {
        val runtime = fakeAiRuntime()

        assertEquals(AiMode.FAKE, runtime.mode)
        assertIs<FakeAiClient>(runtime.aiClient)
        assertTrue(runtime.availability.isAvailable())
        assertEquals(FakeAiClient.DEFAULT_DIMENSION, runtime.embeddingDimension)
    }

    @Test
    fun realRuntimeRequiresCompleteConfig() {
        assertFailsWith<IllegalArgumentException> {
            openAiAiRuntime(
                config = AiRuntimeConfig(apiKey = ""),
                transport = RecordingTransport { jsonResponse("{}") },
            )
        }
    }

    @Test
    fun configRedactsApiKey() {
        assertFalse(realConfig().toString().contains("test-key"))
        assertTrue(realConfig().toString().contains("apiKey=***"))
    }

    @Test
    fun chatParsesContentAndSendsContext() = runTest {
        val transport = RecordingTransport { request ->
            assertEquals("https://inference.test/v1/chat/completions", request.url)
            assertEquals("Bearer test-key", request.headers["Authorization"])
            val body = Json.parseToJsonElement(request.body!!.decodeToString()).jsonObject
            assertEquals("chat", body["model"]!!.jsonPrimitive.content)
            val messages = body["messages"]!!.jsonArray
            assertTrue(messages[0].jsonObject["content"]!!.jsonPrimitive.content.contains("Contexto"))
            assertEquals("pregunta", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
            jsonResponse("""{"choices":[{"message":{"content":"respuesta"}}]}""")
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        val answer = runtime.aiClient.generate(
            GenerateRequest(prompt = "pregunta", context = listOf("nota")),
        )

        assertEquals("respuesta", answer)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun embeddingParsesByIndexInOrder() = runTest {
        val transport = RecordingTransport {
            jsonResponse(
                """{"data":[
                    {"index":1,"embedding":[4.0,5.0,6.0]},
                    {"index":0,"embedding":[1.0,2.0,3.0]}
                ]}""",
            )
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        val embeddings = runtime.aiClient.embed(listOf("a", "b"))

        assertEquals(2, embeddings.size)
        assertContentEquals(listOf(1f, 2f, 3f), embeddings[0].values)
        assertContentEquals(listOf(4f, 5f, 6f), embeddings[1].values)
        val body = Json.parseToJsonElement(
            transport.requests.single().body!!.decodeToString(),
        ).jsonObject
        assertEquals("embedding", body["model"]!!.jsonPrimitive.content)
        assertEquals(2, body["input"]!!.jsonArray.size)
    }

    @Test
    fun embeddingWrongDimensionIsRejectedAndSanitized() = runTest {
        val transport = RecordingTransport {
            jsonResponse("""{"data":[{"index":0,"embedding":[1.0,2.0]}]}""")
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        val cause = assertFailsWith<AiClientException> {
            runtime.aiClient.embed(listOf("body secreto de la nota"))
        }

        assertEquals("embedding devolvió dimensión 2, se esperaba 3", cause.message)
        assertFalse(cause.message!!.contains("test-key"))
        assertFalse(cause.message!!.contains("body secreto"))
    }

    @Test
    fun embeddingValidatesEveryVectorDimension() = runTest {
        val transport = RecordingTransport {
            jsonResponse(
                """{"data":[
                    {"index":0,"embedding":[1.0,2.0,3.0]},
                    {"index":1,"embedding":[4.0,5.0]}
                ]}""",
            )
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        val cause = assertFailsWith<AiClientException> {
            runtime.aiClient.embed(listOf("a", "b"))
        }

        assertEquals("embedding devolvió dimensión 2, se esperaba 3", cause.message)
    }

    @Test
    fun embeddingEmptyListDoesNotCallTransport() = runTest {
        val transport = RecordingTransport { jsonResponse("{}") }
        val runtime = openAiAiRuntime(realConfig(), transport)

        assertTrue(runtime.aiClient.embed(emptyList()).isEmpty())
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun transcribeParsesTextAndBuildsMultipart() = runTest {
        val transport = RecordingTransport { request ->
            assertTrue(request.headers["Content-Type"]!!.startsWith("multipart/form-data; boundary="))
            val body = request.body!!.decodeToString()
            assertTrue(body.contains("name=\"model\""))
            assertTrue(body.contains("name=\"file\"; filename=\"audio.m4a\""))
            assertTrue(body.contains("Content-Type: audio/mp4"))
            jsonResponse("""{"text":"hola mundo","languages":[{"code":"fr"}]}""")
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        val text = runtime.transcribeClient.transcribe(
            audio = "audio".encodeToByteArray(),
            mimeType = "audio/mp4",
        )

        assertEquals("hola mundo", text)
    }

    @Test
    fun ttsReturnsBytesAndMime() = runTest {
        val transport = RecordingTransport {
            HttpResponse(
                status = 200,
                body = "mp3-bytes".encodeToByteArray(),
                headers = mapOf("Content-Type" to "audio/mpeg; charset=binary"),
            )
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        val audio = runtime.ttsClient.synthesize("hola")

        assertContentEquals("mp3-bytes".encodeToByteArray(), audio.bytes)
        assertEquals("audio/mpeg", audio.mimeType)
        val body = Json.parseToJsonElement(
            transport.requests.single().body!!.decodeToString(),
        ).jsonObject
        assertEquals("tts", body["model"]!!.jsonPrimitive.content)
        assertEquals("alloy", body["voice"]!!.jsonPrimitive.content)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun imageDecodesBase64Json() = runTest {
        val pngSignature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        val pngBytes = pngSignature + "png-bytes".encodeToByteArray()
        val encoded = Base64.encode(pngBytes)
        val transport = RecordingTransport {
            jsonResponse("""{"created":1,"data":[{"b64_json":"$encoded"}]}""")
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        val image = runtime.imageClient.generateImage("un jefe final")

        assertContentEquals(pngBytes, image.bytes)
        assertEquals("image/png", image.mimeType)
        val body = Json.parseToJsonElement(
            transport.requests.single().body!!.decodeToString(),
        ).jsonObject
        assertEquals("1024x1024", body["size"]!!.jsonPrimitive.content)
        assertEquals("low", body["quality"]!!.jsonPrimitive.content)
        assertEquals("transparent", body["background"]!!.jsonPrimitive.content)
        assertEquals("png", body["output_format"]!!.jsonPrimitive.content)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun imageReportsActualJpegMime() = runTest {
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
        val encoded = Base64.encode(jpegBytes)
        val transport = RecordingTransport {
            jsonResponse("""{"created":1,"data":[{"b64_json":"$encoded"}]}""")
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        assertEquals("image/jpeg", runtime.imageClient.generateImage("un jefe final").mimeType)
    }

    @Test
    fun jevMapsChoiceAndNeverAssigns() = runTest {
        val transport = RecordingTransport {
            jsonResponse(
                """{"answers":{
                    "track":{"type":"choice","choice":"mecánicas"},
                    "genre":{"type":"choice","choice":"RPG"}
                }}""",
            )
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        val suggestion = runtime.jevClient.suggest("un RPG por turnos")

        assertEquals(Track.MECHANICS, suggestion.track)
        assertEquals(listOf("RPG"), suggestion.genres)
        val body = Json.parseToJsonElement(
            transport.requests.single().body!!.decodeToString(),
        ).jsonObject
        assertEquals("jev", body["model"]!!.jsonPrimitive.content)
        assertTrue(body["questions"]!!.jsonObject.containsKey("track"))
        assertTrue(body["questions"]!!.jsonObject.containsKey("genre"))
    }

    @Test
    fun jevNoneMapsToNullAndEmpty() = runTest {
        val transport = RecordingTransport {
            jsonResponse(
                """{"answers":{
                    "track":{"type":"choice","choice":"none"},
                    "genre":{"type":"choice","choice":"none"}
                }}""",
            )
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        val suggestion = runtime.jevClient.suggest("sin clasificación")

        assertNull(suggestion.track)
        assertTrue(suggestion.genres.isEmpty())
    }

    @Test
    fun httpErrorIsGenericAndSanitized() = runTest {
        val transport = RecordingTransport {
            HttpResponse(status = 401, body = """{"error":"secret detail"}""".encodeToByteArray())
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        val cause = assertFailsWith<AiClientException> {
            runtime.aiClient.generate(GenerateRequest("pregunta"))
        }

        assertEquals("chat falló con HTTP 401", cause.message)
        assertFalse(cause.message!!.contains("test-key"))
        assertFalse(cause.message!!.contains("secret detail"))
    }

    @Test
    fun http500IsGenericAndSanitized() = runTest {
        val transport = RecordingTransport {
            HttpResponse(status = 500, body = """{"error":"secret detail"}""".encodeToByteArray())
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        val cause = assertFailsWith<AiClientException> {
            runtime.aiClient.generate(GenerateRequest("pregunta"))
        }

        assertEquals("chat falló con HTTP 500", cause.message)
        assertFalse(cause.message!!.contains("test-key"))
        assertFalse(cause.message!!.contains("secret detail"))
    }

    @Test
    fun networkFailureIsGenericAndSanitized() = runTest {
        val transport = RecordingTransport {
            throw RuntimeException("timeout test-key secret body")
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        val cause = assertFailsWith<AiClientException> {
            runtime.aiClient.generate(GenerateRequest("pregunta"))
        }

        assertEquals("chat falló por error de red", cause.message)
        assertFalse(cause.message!!.contains("test-key"))
        assertFalse(cause.message!!.contains("secret body"))
    }

    @Test
    fun invalidJsonIsMappedToAiClientException() = runTest {
        val transport = RecordingTransport {
            HttpResponse(status = 200, body = "no-json".encodeToByteArray())
        }
        val runtime = openAiAiRuntime(realConfig(), transport)

        assertFailsWith<AiClientException> {
            runtime.aiClient.generate(GenerateRequest("pregunta"))
        }
    }

    @Test
    fun configuredAvailabilityReflectsConfig() = runTest {
        val complete = openAiAiRuntime(realConfig(), RecordingTransport { jsonResponse("{}") })
        assertTrue(complete.availability.isAvailable())

        val incomplete = ConfiguredAiAvailability(AiRuntimeConfig(apiKey = ""))
        assertFalse(incomplete.isAvailable())
    }
}
