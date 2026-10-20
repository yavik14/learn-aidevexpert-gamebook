package com.playbook.core.brainstorming

import com.playbook.core.model.Note
import com.playbook.core.model.Track
import com.playbook.core.model.normalizeGenres

/**
 * `true` si [note] es un duplicado exacto del contenido propuesto.
 *
 * La Nota existente puede venir de SQLite con los Géneros en otro orden/grafía
 * (`ORDER BY genre` binario o migración verbatim), así que **ambos lados** se
 * normalizan con [normalizeGenres] antes de comparar. El `body` se compara
 * exacto y el `Track` por igualdad.
 */
fun isDuplicateBrainstormingNote(
    note: Note,
    body: String,
    track: Track?,
    genres: List<String>,
): Boolean =
    note.body == body &&
        note.track == track &&
        normalizeGenres(note.genres) == normalizeGenres(genres)
