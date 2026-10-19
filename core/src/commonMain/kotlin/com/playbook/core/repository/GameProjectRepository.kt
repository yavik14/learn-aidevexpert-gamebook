package com.playbook.core.repository

import com.playbook.core.model.GameProject
import com.playbook.core.model.GameProjectDraft

/**
 * Persistencia del Proyecto de Juego único (singleton por instalación). La UI y
 * los casos de uso consumen esta abstracción; la implementación concreta hoy es
 * SQLDelight.
 */
interface GameProjectRepository {
    /**
     * Devuelve el Proyecto singleton o `null` si todavía no existe. `null`
     * significa "sin Proyecto" (el onboarding puede crear uno vacío).
     */
    fun get(): GameProject?

    /**
     * Crea el singleton si no existe o reemplaza la ficha si existe. En updates
     * preserva `owner`/`createdAt` y avanza `updatedAt`; `draft.owner` debe
     * coincidir con el persistido. Las listas se normalizan y reemplazan
     * completas.
     */
    fun save(draft: GameProjectDraft): GameProject
}
