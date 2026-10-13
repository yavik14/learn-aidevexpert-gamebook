package com.playbook.core.linking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EPSILON = 1e-9

/**
 * Scenario 1: cálculo de similitud coseno (puro). Corre como `@Test` directo en
 * `commonTest` (Android/JVM e iOS) sin driver ni base de datos.
 */
class CosineSimilarityTest {

    @Test
    fun identicalVectorsScoreOne() {
        assertEquals(1.0, CosineSimilarity.score(listOf(1f, 0f, 0f), listOf(1f, 0f, 0f))!!, EPSILON)
        assertEquals(1.0, CosineSimilarity.score(listOf(2f, -3f), listOf(2f, -3f))!!, EPSILON)
    }

    @Test
    fun orthogonalVectorsScoreZero() {
        assertEquals(0.0, CosineSimilarity.score(listOf(1f, 0f), listOf(0f, 1f))!!, EPSILON)
    }

    @Test
    fun oppositeVectorsScoreMinusOne() {
        assertEquals(-1.0, CosineSimilarity.score(listOf(1f, 0f), listOf(-1f, 0f))!!, EPSILON)
    }

    @Test
    fun knownAngleScoresExpectedCosine() {
        // cos(60°) = 0.5 entre (1, 0) y (1, √3).
        val score = CosineSimilarity.score(listOf(1f, 0f), listOf(1f, 1.7320508f))!!
        assertEquals(0.5, score, 1e-6)
    }

    @Test
    fun mismatchedDimensionsReturnNull() {
        assertNull(CosineSimilarity.score(listOf(1f, 0f), listOf(1f, 0f, 0f)))
        assertNull(CosineSimilarity.score(emptyList(), listOf(1f)))
    }

    @Test
    fun zeroNormVectorsReturnNullAndDoNotThrow() {
        assertNull(CosineSimilarity.score(listOf(0f, 0f), listOf(1f, 0f)), "vector a de norma 0")
        assertNull(CosineSimilarity.score(listOf(1f, 0f), listOf(0f, 0f)), "vector b de norma 0")
        assertNull(CosineSimilarity.score(listOf(0f, 0f), listOf(0f, 0f)), "ambos de norma 0")
        assertNull(CosineSimilarity.score(emptyList(), emptyList()), "vectores vacíos")
    }

    @Test
    fun resultIsAlwaysWithinUnitRange() {
        val score = CosineSimilarity.score(listOf(3f, 4f), listOf(-4f, 3f))!!
        assertTrue(score in -1.0..1.0, "el coseno debe quedar en [-1, 1], fue $score")
    }
}
