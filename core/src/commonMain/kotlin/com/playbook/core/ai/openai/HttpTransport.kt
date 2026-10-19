package com.playbook.core.ai.openai

/**
 * Petición HTTP mínima del runtime de IA real. `body` son bytes para no acoplar
 * `:core` a un cliente HTTP concreto (Android usa `HttpURLConnection`).
 */
class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

/** Respuesta HTTP mínima: status + bytes + headers. */
class HttpResponse(
    val status: Int,
    val body: ByteArray,
    val headers: Map<String, String> = emptyMap(),
) {
    val isSuccessful: Boolean
        get() = status in 200..299

    /** Busca un header case-insensitive. */
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

/**
 * Frontera de transporte del runtime real. La implementación concreta vive en la
 * plataforma (`:composeApp/androidMain`) y se inyecta en los adaptadores; los
 * tests usan un transporte falso, sin red.
 */
interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}
