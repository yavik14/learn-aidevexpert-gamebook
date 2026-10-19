package com.playbook.core.model

// SQLDelight genera `com.playbook.core.db.Game_project` para la tabla
// `game_project`; se aliasa para evitar la colisión con el modelo de dominio.
import com.playbook.core.db.Game_project as GameProjectRow

/** Mapea una fila SQLDelight (`GameProjectRow`) al modelo [GameProject]. */
fun GameProjectRow.toDomain(
    genres: List<String>,
    inspirations: List<String>,
    keyMechanics: List<KeyMechanic>,
    outOfScope: List<String>,
): GameProject = GameProject(
    id = id,
    owner = owner,
    title = title,
    genres = genres,
    coreLoop = core_loop,
    audience = audience,
    concept = concept,
    inspirations = inspirations,
    fantasy = fantasy,
    keyMechanics = keyMechanics,
    outOfScope = outOfScope,
    createdAt = created_at,
    updatedAt = updated_at,
)
