package com.playbook.core.ai

import kotlin.math.sqrt

/**
 * Similitud coseno entre dos vectores de la misma dimensión.
 *
 * `cos(a, b) = dot(a, b) / (||a|| * ||b||)`, con `||x|| = sqrt(dot(x, x))`.
 *
 * El caller garantiza dimensiones compatibles: si `a.size != b.size` lanza
 * [IllegalArgumentException] (fail-fast). Cuando alguno de los vectores es nulo
 * (norma `0f`) el denominador también lo es y se devuelve `0f` en lugar de `NaN`.
 *
 * Es una utilidad pura de Kotlin common. El RAG la usa para recuperar las Notas
 * más cercanas a la pregunta; `semantic-linking` la reutilizará para los Enlaces.
 */
fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
    require(a.size == b.size) {
        "los vectores deben tener la misma dimensión: ${a.size} != ${b.size}"
    }

    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (index in a.indices) {
        dot += a[index] * b[index]
        normA += a[index] * a[index]
        normB += b[index] * b[index]
    }

    val denominator = sqrt(normA) * sqrt(normB)
    if (denominator == 0f) return 0f
    return dot / denominator
}
