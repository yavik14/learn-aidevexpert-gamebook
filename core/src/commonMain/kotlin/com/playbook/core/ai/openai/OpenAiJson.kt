package com.playbook.core.ai.openai

import com.playbook.core.ai.AiClientException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

/** Serializa un objeto JSON a bytes UTF-8. */
internal fun jsonBody(block: JsonObjectBuilder.() -> Unit): ByteArray =
    buildJsonObject(block).toString().encodeToByteArray()

/** Parsea bytes UTF-8 a un objeto JSON; cualquier error se mapea a `AiClientException`. */
internal fun parseJsonObject(bytes: ByteArray): JsonObject =
    try {
        Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
            ?: throw AiClientException("El proveedor devolvió un JSON que no es un objeto")
    } catch (cause: AiClientException) {
        throw cause
    } catch (cause: Exception) {
        throw AiClientException("El proveedor devolvió JSON inválido", cause)
    }

internal fun JsonObject.requireString(key: String): String =
    stringOrNull(key)?.takeIf { it.isNotBlank() }
        ?: throw AiClientException("El proveedor no devolvió '$key'")

internal fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.floatOrNull(key: String): Float? =
    (this[key] as? JsonPrimitive)?.floatOrNull

internal fun JsonObject.objOrNull(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray
