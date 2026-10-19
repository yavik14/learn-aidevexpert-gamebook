package com.playbook.core.repository

import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.model.GAME_PROJECT_SINGLETON_ID
import com.playbook.core.model.GameProject
import com.playbook.core.model.GameProjectDraft
import com.playbook.core.model.KeyMechanic
import com.playbook.core.model.MAX_PROJECT_INSPIRATIONS
import com.playbook.core.model.MAX_PROJECT_OUT_OF_SCOPE
import com.playbook.core.model.MechanicCategory
import com.playbook.core.model.normalizeGenres
import com.playbook.core.model.normalizeKeyMechanics
import com.playbook.core.model.normalizeProjectList
import com.playbook.core.model.normalizeProjectText
import com.playbook.core.model.toDomain

/**
 * Implementación SQLDelight de [GameProjectRepository].
 *
 * `clock` se inyecta para que los tests sean deterministas; en producción se usa
 * `::currentTimeMillis`. El Proyecto es un singleton: `save` hace upsert de una
 * única fila (`id = "singleton"`) y reemplaza todas sus filas hijas en la misma
 * transacción.
 */
class SqlDelightGameProjectRepository(
    private val database: PlaybookDatabase,
    private val clock: () -> Long,
) : GameProjectRepository {

    override fun get(): GameProject? {
        val row = database.gameProjectQueries
            .selectGameProject(GAME_PROJECT_SINGLETON_ID)
            .executeAsOneOrNull()
            ?: return null

        val genres = database.gameProjectQueries
            .selectGameProjectGenres(GAME_PROJECT_SINGLETON_ID)
            .executeAsList()
            .sortedBy { it.lowercase() }
        val inspirations = database.gameProjectQueries
            .selectGameProjectInspirations(GAME_PROJECT_SINGLETON_ID)
            .executeAsList()
        val keyMechanics = database.gameProjectQueries
            .selectGameProjectKeyMechanics(GAME_PROJECT_SINGLETON_ID)
            .executeAsList()
            .map { KeyMechanic(MechanicCategory.fromCode(it.category), it.description) }
        val outOfScope = database.gameProjectQueries
            .selectGameProjectOutOfScope(GAME_PROJECT_SINGLETON_ID)
            .executeAsList()

        return row.toDomain(
            genres = genres,
            inspirations = inspirations,
            keyMechanics = keyMechanics,
            outOfScope = outOfScope,
        )
    }

    override fun save(draft: GameProjectDraft): GameProject {
        val owner = draft.owner.trim()
        require(owner.isNotEmpty()) { "el owner del Proyecto no puede estar vacío" }

        val title = normalizeProjectText(draft.title)
        val coreLoop = normalizeProjectText(draft.coreLoop)
        val audience = normalizeProjectText(draft.audience)
        val concept = normalizeProjectText(draft.concept)
        val fantasy = normalizeProjectText(draft.fantasy)
        val genres = normalizeGenres(draft.genres)
        val inspirations = normalizeProjectList(
            raw = draft.inspirations,
            max = MAX_PROJECT_INSPIRATIONS,
            field = "inspirations",
        )
        val keyMechanics = normalizeKeyMechanics(draft.keyMechanics)
        val outOfScope = normalizeProjectList(
            raw = draft.outOfScope,
            max = MAX_PROJECT_OUT_OF_SCOPE,
            field = "outOfScope",
        )

        return database.transactionWithResult {
            val existing = database.gameProjectQueries
                .selectGameProject(GAME_PROJECT_SINGLETON_ID)
                .executeAsOneOrNull()
            val now = clock()
            val createdAt = existing?.created_at ?: now

            if (existing == null) {
                database.gameProjectQueries.insertGameProject(
                    id = GAME_PROJECT_SINGLETON_ID,
                    owner = owner,
                    title = title,
                    core_loop = coreLoop,
                    audience = audience,
                    concept = concept,
                    fantasy = fantasy,
                    created_at = now,
                    updated_at = now,
                )
            } else {
                require(existing.owner == owner) {
                    "el owner del Proyecto no coincide con el persistido"
                }
                database.gameProjectQueries.updateGameProject(
                    title = title,
                    core_loop = coreLoop,
                    audience = audience,
                    concept = concept,
                    fantasy = fantasy,
                    updated_at = now,
                    id = GAME_PROJECT_SINGLETON_ID,
                )
            }

            replaceGenres(genres)
            replaceInspirations(inspirations)
            replaceKeyMechanics(keyMechanics)
            replaceOutOfScope(outOfScope)

            GameProject(
                id = GAME_PROJECT_SINGLETON_ID,
                owner = owner,
                title = title,
                genres = genres,
                coreLoop = coreLoop,
                audience = audience,
                concept = concept,
                inspirations = inspirations,
                fantasy = fantasy,
                keyMechanics = keyMechanics,
                outOfScope = outOfScope,
                createdAt = createdAt,
                updatedAt = now,
            )
        }
    }

    private fun replaceGenres(genres: List<String>) {
        database.gameProjectQueries.deleteGameProjectGenres(GAME_PROJECT_SINGLETON_ID)
        genres.forEach { genre ->
            database.gameProjectQueries.insertGameProjectGenre(
                project_id = GAME_PROJECT_SINGLETON_ID,
                genre = genre,
            )
        }
    }

    private fun replaceInspirations(inspirations: List<String>) {
        database.gameProjectQueries.deleteGameProjectInspirations(GAME_PROJECT_SINGLETON_ID)
        inspirations.forEachIndexed { index, value ->
            database.gameProjectQueries.insertGameProjectInspiration(
                project_id = GAME_PROJECT_SINGLETON_ID,
                position = index.toLong(),
                value_ = value,
            )
        }
    }

    private fun replaceKeyMechanics(keyMechanics: List<KeyMechanic>) {
        database.gameProjectQueries.deleteGameProjectKeyMechanics(GAME_PROJECT_SINGLETON_ID)
        keyMechanics.forEachIndexed { index, mechanic ->
            database.gameProjectQueries.insertGameProjectKeyMechanic(
                project_id = GAME_PROJECT_SINGLETON_ID,
                position = index.toLong(),
                category = mechanic.category.code,
                description = mechanic.description,
            )
        }
    }

    private fun replaceOutOfScope(outOfScope: List<String>) {
        database.gameProjectQueries.deleteGameProjectOutOfScope(GAME_PROJECT_SINGLETON_ID)
        outOfScope.forEachIndexed { index, value ->
            database.gameProjectQueries.insertGameProjectOutOfScope(
                project_id = GAME_PROJECT_SINGLETON_ID,
                position = index.toLong(),
                value_ = value,
            )
        }
    }
}
