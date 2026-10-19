package com.playbook.app.ai

import com.playbook.core.ai.openai.HttpRequest
import com.playbook.core.ai.openai.HttpResponse
import com.playbook.core.ai.openai.HttpTransport
import java.net.HttpRetryException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación Android/JVM del transporte HTTP con `HttpURLConnection`.
 * No loguea headers ni bodies (la key viaja sólo en el header de autorización).
 *
 * **No sigue redirects** (`instanceFollowRedirects = false`): un 3xx se reporta
 * como respuesta no-2xx y el adaptador lo mapea a `AiClientException` genérica,
 * sin reenviar el body ni headers sensibles a un host no validado.
 *
 * El I/O bloqueante corre en [dispatcher] (por defecto `Dispatchers.IO`), nunca
 * en el hilo que invoca (en Android, el Main): evita
 * `NetworkOnMainThreadException` y ANR en el indexado/RAG reales.
 */
internal class UrlConnectionHttpTransport(
    private val connectTimeoutMs: Int = 30_000,
    private val readTimeoutMs: Int = 60_000,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HttpTransport {

    override suspend fun execute(request: HttpRequest): HttpResponse =
        withContext(dispatcher) {
            executeBlocking(request)
        }

    private fun executeBlocking(request: HttpRequest): HttpResponse {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (request.body != null) doOutput = true
        }

        try {
            request.body?.let { body ->
                connection.outputStream.use { output -> output.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytes() } ?: ByteArray(0)
            val headers = connection.headerFields.entries
                .filter { it.key != null }
                .associate { (name, values) -> name to values.joinToString(",") }
            return HttpResponse(status = status, body = body, headers = headers)
        } catch (cause: HttpRetryException) {
            // `HttpURLConnection` puede lanzar esto para 307/308 en POST aunque
            // los redirects estén deshabilitados; se reporta el 3xx sin seguirlo.
            return HttpResponse(status = cause.responseCode(), body = ByteArray(0))
        } finally {
            connection.disconnect()
        }
    }
}
