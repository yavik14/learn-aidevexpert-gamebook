package com.playbook.core.brainstorming

import com.playbook.core.model.GameProjectDraft
import com.playbook.core.model.MechanicCategory
import com.playbook.core.model.Track
import com.playbook.core.model.normalizeGenres

/** Cantidad de pasos del wizard de Brainstorming. */
const val BRAINSTORMING_STEP_COUNT: Int = 9

/** Contenido de una Nota derivado de un paso del wizard. */
data class BrainstormingStepNote(
    val body: String,
    val track: Track?,
    val genres: List<String>,
)

/**
 * Proyecta el estado de un paso del wizard a una Nota.
 *
 * `step` es el índice 0..8 del wizard. El `body` usa el contenido actual del
 * [draft] (aunque la ficha no esté guardada); los Géneros se normalizan para
 * todas las Notas. El mapeo es determinista y no toca `owner` ni persistencia.
 *
 * Pasos: 0 Título, 1 Género, 2 Core Loop, 3 Audiencia, 4 Concepto,
 * 5 Inspiraciones, 6 Fantasía, 7 Mecánicas clave, 8 Fuera de alcance.
 */
fun buildBrainstormingStepNote(step: Int, draft: GameProjectDraft): BrainstormingStepNote {
    require(step in 0 until BRAINSTORMING_STEP_COUNT) {
        "step debe estar entre 0 y ${BRAINSTORMING_STEP_COUNT - 1} (se recibió $step)"
    }

    val genres = normalizeGenres(draft.genres)
    val body = when (step) {
        0 -> draft.title.orEmpty().trim()
        1 -> genres.joinToString(", ")
        2 -> draft.coreLoop.orEmpty().trim()
        3 -> draft.audience.orEmpty().trim()
        4 -> draft.concept.orEmpty().trim()
        5 -> draft.inspirations.normalizedLines()
        6 -> draft.fantasy.orEmpty().trim()
        7 -> draft.keyMechanics
            .map { it.copy(description = it.description.trim()) }
            .filter { it.description.isNotEmpty() }
            .joinToString("\n") { "${categoryLabel(it.category)}: ${it.description}" }
        8 -> draft.outOfScope.normalizedLines()
        else -> error("Paso de Brainstorming no soportado: $step")
    }

    val track = when (step) {
        2, 7 -> Track.MECHANICS
        4, 6 -> Track.STORY
        else -> null
    }

    return BrainstormingStepNote(body = body, track = track, genres = genres)
}

private fun List<String>.normalizedLines(): String =
    map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

private fun categoryLabel(category: MechanicCategory): String =
    category.code.replaceFirstChar { it.uppercase() }
