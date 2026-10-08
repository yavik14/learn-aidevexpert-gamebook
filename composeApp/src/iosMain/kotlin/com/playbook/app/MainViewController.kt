package com.playbook.app

import androidx.compose.ui.window.ComposeUIViewController
import com.playbook.core.db.IosDatabaseDriverFactory
import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.db.createDatabase
import com.playbook.core.platform.currentTimeMillis
import com.playbook.core.platform.randomNoteId
import com.playbook.core.repository.NoteRepository
import com.playbook.core.repository.SqlDelightNoteRepository
import platform.UIKit.UIViewController

// Base local y repositorio de notas en iOS: se crean al arrancar y aplican el
// esquema v1. La UI compartida los consume vía App(noteRepository).
private var playbookDatabase: PlaybookDatabase? = null
private var noteRepository: NoteRepository? = null

fun MainViewController(): UIViewController {
    val repository = noteRepository ?: run {
        val database = playbookDatabase ?: createDatabase(IosDatabaseDriverFactory()).also {
            playbookDatabase = it
        }
        SqlDelightNoteRepository(
            database = database,
            idFactory = ::randomNoteId,
            clock = ::currentTimeMillis,
        ).also { noteRepository = it }
    }
    return ComposeUIViewController { App(repository) }
}
