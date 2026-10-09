package com.playbook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.playbook.core.db.AndroidDatabaseDriverFactory
import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.db.createDatabase
import com.playbook.core.platform.currentTimeMillis
import com.playbook.core.platform.randomNoteId
import com.playbook.core.repository.AttachmentRepository
import com.playbook.core.repository.NoteRepository
import com.playbook.core.repository.SqlDelightAttachmentRepository
import com.playbook.core.repository.SqlDelightNoteRepository

class MainActivity : ComponentActivity() {
    // Se instancia al arrancar para aplicar el esquema local (v2) y alimentar los
    // repositorios de notas y adjuntos que consume la UI.
    private lateinit var database: PlaybookDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = createDatabase(AndroidDatabaseDriverFactory(applicationContext))
        val noteRepository: NoteRepository = SqlDelightNoteRepository(
            database = database,
            idFactory = ::randomNoteId,
            clock = ::currentTimeMillis,
        )
        val attachmentRepository: AttachmentRepository = SqlDelightAttachmentRepository(
            database = database,
            idFactory = ::randomNoteId,
            clock = ::currentTimeMillis,
        )
        setContent {
            App(noteRepository, attachmentRepository)
        }
    }
}
