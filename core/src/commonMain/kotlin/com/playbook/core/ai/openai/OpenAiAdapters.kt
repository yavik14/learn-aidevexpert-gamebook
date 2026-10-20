package com.playbook.core.ai.openai

import com.playbook.core.ai.AiClient
import com.playbook.core.ai.AiClientException
import com.playbook.core.ai.ClassificationSuggestion
import com.playbook.core.ai.Embedding
import com.playbook.core.ai.GenerateRequest
import com.playbook.core.ai.GeneratedImage
import com.playbook.core.ai.ImageClient
import com.playbook.core.ai.JevClient
import com.playbook.core.ai.SynthesizedAudio
import com.playbook.core.ai.TranscribeClient
import com.playbook.core.ai.TtsClient
import com.playbook.core.model.GenreCatalog
import com.playbook.core.model.Track
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** `chat` + `embedding` de OpenAI-compatible. */
internal class OpenAiAiClient(
    private val config: AiRuntimeConfig,
    private val http: OpenAiHttp,
) : AiClient {

    override suspend fun embed(texts: List<String>): List<Embedding> {
        if (texts.isEmpty()) return emptyList()

        val body = jsonBody {
            put("model", config.modelEmbedding)
            putJsonArray("input") { texts.forEach { add(JsonPrimitive(it)) } }
        }
        val response = http.postJson("/embeddings", body, "embedding")
        val data = response.arrayOrNull("data")
            ?: throw AiClientException("embedding no devolvió 'data'")

        val ordered = data.mapIndexed { fallbackIndex, element ->
            val item = element as? JsonObject
                ?: throw AiClientException("embedding devolvió un item inválido")
            val index = item.intOrNull("index") ?: fallbackIndex
            val vector = item.arrayOrNull("embedding")
                ?: throw AiClientException("embedding no devolvió 'embedding'")
            val values = vector.map { value ->
                (value as? JsonPrimitive)?.floatOrNull
                    ?: throw AiClientException("embedding devolvió un valor no numérico")
            }
            if (values.isEmpty()) throw AiClientException("embedding devolvió un vector vacío")
            index to values
        }.sortedBy { it.first }

        if (ordered.size != texts.size) {
            throw AiClientException(
                "embedding devolvió ${ordered.size} vectores para ${texts.size} textos",
            )
        }
        // Contrato de dimensión: un vector de tamaño distinto al configurado
        // corrompería Enlaces/RAG y nunca debe persistirse. El mensaje sólo
        // incluye dimensiones (sin key, headers ni body).
        ordered.forEach { (_, values) ->
            if (values.size != config.embeddingDimension) {
                throw AiClientException(
                    "embedding devolvió dimensión ${values.size}, " +
                        "se esperaba ${config.embeddingDimension}",
                )
            }
        }
        return ordered.map { Embedding(it.second) }
    }

    override suspend fun generate(request: GenerateRequest): String {
        val system = buildString {
            append(SYSTEM_PROMPT)
            request.instructions
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { instructions ->
                    append("\n\n").append(instructions)
                }
            if (request.context.isNotEmpty()) {
                append("\n\nContexto provisto:\n")
                request.context.forEachIndexed { index, text ->
                    append("${index + 1}. ").append(text).append('\n')
                }
            }
        }
        val body = jsonBody {
            put("model", config.modelChat)
            request.temperature?.let { put("temperature", it) }
            request.maxTokens?.let { put("max_tokens", it) }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", system)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", request.prompt)
                }
            }
        }
        val response = http.postJson("/chat/completions", body, "chat")
        val content = response.arrayOrNull("choices")
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.objOrNull("message")
            ?.stringOrNull("content")
        return content?.takeIf { it.isNotBlank() }
            ?: throw AiClientException("chat devolvió una respuesta vacía")
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "Sos un asistente de diseño de videojuegos. Respondé en español, " +
                "de forma concreta y práctica."
    }
}

/** `transcribe`: multipart con `model` + `file` + `response_format`. */
internal class OpenAiTranscribeClient(
    private val config: AiRuntimeConfig,
    private val http: OpenAiHttp,
) : TranscribeClient {

    override suspend fun transcribe(audio: ByteArray, mimeType: String): String {
        val (filename, contentType) = audioFileFor(mimeType)
        val boundary = MULTIPART_BOUNDARY
        val body = multipartBody(
            parts = listOf(
                FieldPart("model", config.modelTranscribe),
                FieldPart("response_format", config.transcribeResponseFormat),
                FilePart("file", filename, contentType, audio),
            ),
            boundary = boundary,
        )
        val response = http.postRaw(
            path = "/audio/transcriptions",
            body = body,
            contentType = "multipart/form-data; boundary=$boundary",
            model = "transcribe",
            accept = "application/json",
        )
        return parseJsonObject(response.body).requireString("text")
    }
}

/** `tts`: devuelve los bytes de audio tal cual y el mime de la respuesta. */
internal class OpenAiTtsClient(
    private val config: AiRuntimeConfig,
    private val http: OpenAiHttp,
) : TtsClient {

    override suspend fun synthesize(text: String): SynthesizedAudio {
        val body = jsonBody {
            put("model", config.modelTts)
            put("voice", config.ttsVoice)
            put("input", text)
        }
        val response = http.postRaw(
            path = "/audio/speech",
            body = body,
            contentType = "application/json",
            model = "tts",
            accept = "audio/mpeg",
        )
        if (response.body.isEmpty()) throw AiClientException("tts devolvió audio vacío")
        val mimeType = response.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.startsWith("audio/") }
            ?: "audio/mpeg"
        return SynthesizedAudio(bytes = response.body, mimeType = mimeType)
    }
}

/** `image`: decodifica `data[0].b64_json` a bytes png. */
@OptIn(ExperimentalEncodingApi::class)
internal class OpenAiImageClient(
    private val config: AiRuntimeConfig,
    private val http: OpenAiHttp,
) : ImageClient {

    override suspend fun generateImage(prompt: String): GeneratedImage {
        val body = jsonBody {
            put("model", config.modelImage)
            put("prompt", prompt)
            put("size", config.imageSize)
            put("quality", config.imageQuality)
            put("background", config.imageBackground)
            put("output_format", config.imageOutputFormat)
        }
        val response = http.postJson("/images/generations", body, "image")
        val encoded = response.arrayOrNull("data")
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.stringOrNull("b64_json")
            ?.takeIf { it.isNotBlank() }
            ?: throw AiClientException("image no devolvió b64_json")

        val bytes = try {
            Base64.decode(encoded)
        } catch (cause: Exception) {
            throw AiClientException("image devolvió base64 inválido", cause)
        }
        if (bytes.isEmpty()) throw AiClientException("image devolvió una imagen vacía")
        return GeneratedImage(bytes = bytes, mimeType = detectImageMime(bytes))
    }
}

/** `jev`: decisiones tipo `choice` para Track y Género. Nunca auto-asigna. */
internal class OpenAiJevClient(
    private val config: AiRuntimeConfig,
    private val http: OpenAiHttp,
) : JevClient {

    override suspend fun suggest(text: String): ClassificationSuggestion {
        val body = jsonBody {
            put("model", config.modelJev)
            putJsonObject("state") { put("text", text) }
            putJsonObject("questions") {
                putJsonObject("track") {
                    put("type", "choice")
                    put("instructions", TRACK_INSTRUCTIONS)
                    putJsonObject("criteria") {
                        put("mecánicas", "Reglas, sistemas y mecánicas de juego")
                        put("personajes", "Personajes, enemigos y NPCs")
                        put("historia", "Historia, lore y narrativa")
                        put("none", "Ninguno de los anteriores")
                    }
                }
                putJsonObject("genre") {
                    put("type", "choice")
                    put("instructions", GENRE_INSTRUCTIONS)
                    putJsonObject("criteria") {
                        GenreCatalog.GENRES.forEach { genre -> put(genre, genre) }
                        put("none", "Ninguno de los anteriores")
                    }
                }
            }
        }
        val response = http.postJson("/decisions", body, "jev")
        val answers = response.objOrNull("answers")
            ?: throw AiClientException("jev no devolvió 'answers'")
        val trackChoice = answers.objOrNull("track")?.stringOrNull("choice")
        val genreChoice = answers.objOrNull("genre")?.stringOrNull("choice")

        return ClassificationSuggestion(
            track = trackChoice?.takeUnless { it == NONE }?.let(Track::fromCode),
            genres = genreChoice?.takeUnless { it == NONE }?.let { listOf(it) } ?: emptyList(),
        )
    }

    private companion object {
        const val NONE = "none"
        const val TRACK_INSTRUCTIONS = "¿A qué Track de diseño pertenece este texto?"
        const val GENRE_INSTRUCTIONS = "¿Qué género describe mejor este texto?"
    }
}

internal sealed interface MultipartPart
internal class FieldPart(val name: String, val value: String) : MultipartPart
internal class FilePart(
    val name: String,
    val filename: String,
    val contentType: String,
    val bytes: ByteArray,
) : MultipartPart

internal const val MULTIPART_BOUNDARY = "----PlaybookBoundary7MA4YWxkTrZu0gW"

internal fun multipartBody(parts: List<MultipartPart>, boundary: String): ByteArray {
    val chunks = mutableListOf<ByteArray>()
    for (part in parts) {
        chunks.add("--$boundary\r\n".encodeToByteArray())
        when (part) {
            is FieldPart -> {
                chunks.add(
                    "Content-Disposition: form-data; name=\"${part.name}\"\r\n\r\n"
                        .encodeToByteArray(),
                )
                chunks.add(part.value.encodeToByteArray())
                chunks.add("\r\n".encodeToByteArray())
            }
            is FilePart -> {
                chunks.add(
                    (
                        "Content-Disposition: form-data; name=\"${part.name}\"; " +
                            "filename=\"${part.filename}\"\r\n"
                        ).encodeToByteArray(),
                )
                chunks.add("Content-Type: ${part.contentType}\r\n\r\n".encodeToByteArray())
                chunks.add(part.bytes)
                chunks.add("\r\n".encodeToByteArray())
            }
        }
    }
    chunks.add("--$boundary--\r\n".encodeToByteArray())
    return concat(chunks)
}

internal fun audioFileFor(mimeType: String): Pair<String, String> =
    when (mimeType.trim().lowercase()) {
        "audio/mpeg", "audio/mp3" -> "audio.mp3" to "audio/mpeg"
        "audio/mp4", "audio/m4a", "audio/x-m4a" -> "audio.m4a" to "audio/mp4"
        else -> "audio.bin" to mimeType.ifBlank { "application/octet-stream" }
    }

/**
 * El proveedor puede devolver PNG o JPEG aunque se pida `output_format=png`;
 * se reporta el mime real según la firma del archivo.
 */
internal fun detectImageMime(bytes: ByteArray): String = when {
    bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> "image/png"
    bytes.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
    bytes.startsWith(0x47, 0x49, 0x46, 0x38) -> "image/gif"
    bytes.size >= 12 &&
        bytes.startsWith(0x52, 0x49, 0x46, 0x46) &&
        bytes.copyOfRange(8, 12).startsWith(0x57, 0x45, 0x42, 0x50) -> "image/webp"
    else -> "application/octet-stream"
}

private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }

private fun concat(chunks: List<ByteArray>): ByteArray {
    val size = chunks.sumOf { it.size }
    val result = ByteArray(size)
    var offset = 0
    for (chunk in chunks) {
        chunk.copyInto(result, destinationOffset = offset)
        offset += chunk.size
    }
    return result
}
