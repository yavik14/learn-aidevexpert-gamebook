package com.playbook.app.ai

import com.playbook.core.ai.AiClientException
import com.playbook.core.ai.GenerateRequest
import com.playbook.core.ai.openai.AiRuntimeConfig
import com.playbook.core.ai.openai.HttpRequest
import com.playbook.core.ai.openai.openAiAiRuntime
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class RecordingDispatcher(
    private val delegate: CoroutineDispatcher = Dispatchers.Default,
) : CoroutineDispatcher() {
    var dispatched = false

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatched = true
        delegate.dispatch(context, block)
    }
}

class UrlConnectionHttpTransportTest {

    @Test
    fun dispatchesBlockingIoOffTheCallerThread() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { exchange ->
            val response = "ok".encodeToByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        try {
            val dispatcher = RecordingDispatcher()
            val transport = UrlConnectionHttpTransport(
                connectTimeoutMs = 5_000,
                readTimeoutMs = 5_000,
                dispatcher = dispatcher,
            )

            val response = transport.execute(
                HttpRequest(
                    method = "GET",
                    url = "http://127.0.0.1:${server.address.port}/ok",
                ),
            )

            assertEquals(200, response.status)
            assertTrue(dispatcher.dispatched, "el I/O debe despacharse vía el dispatcher inyectado")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun sendsRequestAndReadsResponse() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var receivedMethod: String? = null
        var receivedAuth: String? = null
        var receivedBody: String? = null

        server.createContext("/echo") { exchange ->
            receivedMethod = exchange.requestMethod
            receivedAuth = exchange.requestHeaders.getFirst("Authorization")
            receivedBody = exchange.requestBody.use { it.readBytes().decodeToString() }
            val response = "respuesta".encodeToByteArray()
            exchange.responseHeaders.add("X-Test", "yes")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        try {
            val port = server.address.port
            val transport = UrlConnectionHttpTransport(
                connectTimeoutMs = 5_000,
                readTimeoutMs = 5_000,
            )

            val response = transport.execute(
                HttpRequest(
                    method = "POST",
                    url = "http://127.0.0.1:$port/echo",
                    headers = mapOf(
                        "Authorization" to "Bearer test-key",
                        "Content-Type" to "application/json",
                    ),
                    body = "cuerpo".encodeToByteArray(),
                ),
            )

            assertEquals(200, response.status)
            assertContentEquals("respuesta".encodeToByteArray(), response.body)
            assertEquals("yes", response.header("X-Test"))
            assertEquals("POST", receivedMethod)
            assertEquals("Bearer test-key", receivedAuth)
            assertEquals("cuerpo", receivedBody)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun doesNotFollowRedirectsAndDoesNotLeakRequest() = runBlocking {
        val targetHits = AtomicInteger(0)
        val target = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        target.createContext("/target") { exchange ->
            targetHits.incrementAndGet()
            val response = "target".encodeToByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        target.start()

        val redirect = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val targetUrl = "http://127.0.0.1:${target.address.port}/target"
        redirect.createContext("/") { exchange ->
            exchange.responseHeaders.add("Location", targetUrl)
            exchange.sendResponseHeaders(307, -1)
            exchange.close()
        }
        redirect.start()

        try {
            val redirectUrl = "http://127.0.0.1:${redirect.address.port}/chat/completions"
            val transport = UrlConnectionHttpTransport(
                connectTimeoutMs = 5_000,
                readTimeoutMs = 5_000,
            )
            val response = transport.execute(
                HttpRequest(
                    method = "POST",
                    url = redirectUrl,
                    headers = mapOf(
                        "Authorization" to "Bearer test-key",
                        "Content-Type" to "application/json",
                    ),
                    body = """{"secret":"body"}""".encodeToByteArray(),
                ),
            )

            assertEquals(307, response.status)
            assertEquals(0, targetHits.get(), "el redirect no debe reenviar la petición")

            val runtime = openAiAiRuntime(
                config = AiRuntimeConfig(
                    baseUrl = "http://127.0.0.1:${redirect.address.port}",
                    apiKey = "test-key",
                    embeddingDimension = 1536,
                ),
                transport = transport,
            )
            val cause = assertFailsWith<AiClientException> {
                runtime.aiClient.generate(GenerateRequest("pregunta"))
            }

            assertEquals("chat falló con HTTP 307", cause.message)
            assertFalse(cause.message!!.contains("test-key"))
            assertEquals(0, targetHits.get(), "el destino no debe recibir key ni body")
        } finally {
            redirect.stop(0)
            target.stop(0)
        }
    }
}
