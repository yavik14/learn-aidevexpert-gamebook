package com.playbook.core.ai

/**
 * Seam de disponibilidad de la IA ("¿hay runtime utilizable ahora?").
 *
 * No hay red ni runtime real en el MVP (`FakeAiClient`, ADR 0001), así que la
 * "vuelta de la IA" no se puede observar de verdad. Este puerto desacopla esa
 * decisión: [PendingIndexingRetry] consulta [isAvailable] y, si es `false`, es
 * un no-op (no consume presupuesto ni cambia estados). La sonda real de
 * conectividad es follow-up del ADR 0001.
 *
 * La disponibilidad se inyecta por constructor; el default
 * [AlwaysAvailableAiAvailability] preserva el comportamiento del MVP y permite
 * tests con una implementación que devuelva `false`.
 */
interface AiAvailability {
    /** `true` si el runtime de IA puede intentarse ahora mismo. */
    suspend fun isAvailable(): Boolean
}

/** Implementación por defecto: la IA siempre se considera disponible. */
object AlwaysAvailableAiAvailability : AiAvailability {
    override suspend fun isAvailable(): Boolean = true
}
