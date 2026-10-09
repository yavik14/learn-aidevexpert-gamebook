package com.playbook.app

import androidx.compose.ui.window.ComposeUIViewController
import com.playbook.core.db.IosDatabaseDriverFactory
import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.db.createDatabase
import com.playbook.core.platform.currentTimeMillis
import com.playbook.core.platform.randomNoteId
import com.playbook.core.repository.AttachmentRepository
import com.playbook.core.repository.NoteRepository
import com.playbook.core.repository.SqlDelightAttachmentRepository
import com.playbook.core.repository.SqlDelightNoteRepository
import platform.UIKit.UIViewController

// Base local y repositorios (notas + adjuntos) en iOS: se crean al arrancar y
// aplican el esquema v2. La UI compartida los consume vía App(...).
private var playbookDatabase: PlaybookDatabase? = null
private var noteRepository: NoteRepository? = null
private var attachmentRepository: AttachmentRepository? = null

fun MainViewController(): UIViewController {
    val notes = noteRepository ?: run {
        val database = playbookDatabase ?: createDatabase(IosDatabaseDriverFactory()).also {
            playbookDatabase = it
        }
        SqlDelightNoteRepository(
            database = database,
            idFactory = ::randomNoteId,
            clock = ::currentTimeMillis,
        ).also { noteRepository = it }
    }
    val attachments = attachmentRepository ?: run {
        val database = playbookDatabase ?: createDatabase(IosDatabaseDriverFactory()).also {
            playbookDatabase = it
        }
        SqlDelightAttachmentRepository(
            database = database,
            idFactory = ::randomNoteId,
            clock = ::currentTimeMillis,
        ).also { attachmentRepository = it }
    }
    return ComposeUIViewController { App(notes, attachments) }
}
