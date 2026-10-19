package com.playbook.core.ai.openai

import com.playbook.core.ai.AiClientException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 * Helper común de los adaptadores reales. Construye la URL/headers, ejecuta el
 * transporte y mapea cualquier fallo a [AiClientException] con un mensaje
 * **genérico por status** (sin body, sin headers y sin key).
 */
internal class OpenAiHttp(
    private val config: AiRuntimeConfig,
    private val transport: HttpTransport,
) {
    suspend fun postJson(path: String, body: ByteArray, model: String): JsonObject {
        val response = postRaw(path, body, "application/json", model, accept = "application/json")
        return parseJsonObject(response.body)
    }

    suspend fun postRaw(
        path: String,
        body: ByteArray,
        contentType: String,
        model: String,
        accept: String = "*/*",
    ): HttpResponse {
        val request = HttpRequest(
            method = "POST",
            url = config.endpoint(path),
            headers = mapOf(
                "Authorization" to "Bearer ${config.apiKey}",
                "Content-Type" to contentType,
                "Accept" to accept,
            ),
            body = body,
        )
        val response = try {
            transport.execute(request)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: AiClientException) {
            throw cause
        } catch (cause: Exception) {
            throw AiClientException("$model falló por error de red", cause)
        }
        if (!response.isSuccessful) {
            throw AiClientException("$model falló con HTTP ${response.status}")
        }
        return response
    }
}
