package com.playbook.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.playbook.core.repository.NoteRepository

/**
 * Raíz de la UI compartida.
 *
 * La lectura de la lista es sincrónica y se hace una vez por composición con
 * `remember`: el repositorio es síncrono y todavía no hay mutaciones. No se
 * introduce DI/ViewModel/Flow. **No hay refresco tras mutaciones**: cuando
 * llegue `create-text-note` habrá que re-leer o introducir estado observable.
 */
@Composable
fun App(noteRepository: NoteRepository) {
    MaterialTheme {
        val notes = remember(noteRepository) { noteRepository.getAll() }
        NotesListScreen(notes = notes)
    }
}
