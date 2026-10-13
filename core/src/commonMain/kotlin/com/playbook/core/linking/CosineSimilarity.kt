package com.playbook.core.linking

import kotlin.math.sqrt

/**
 * Similitud coseno pura entre dos vectores de embedding. Puro Kotlin common,
 * determinista y sin dependencias.
 *
 * Devuelve `null` para una similitud **indefinida**: dimensiones distintas o
 * algún vector de norma `0` (p. ej. todo ceros). No lanza para esas entradas
 * inválidas; el llamador decide omitirlas.
 *
 * Para vectores idénticos devuelve exactamente `1.0`; ortogonales `0.0`;
 * opuestos `-1.0` (comparación con epsilon en los tests).
 */
internal object CosineSimilarity {

    fun score(a: List<Float>, b: List<Float>): Double? {
        if (a.size != b.size) return null

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            normA += x * x
            normB += y * y
        }
        if (normA == 0.0 || normB == 0.0) return null

        return dot / (sqrt(normA) * sqrt(normB))
    }
}
