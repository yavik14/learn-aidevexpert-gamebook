package com.playbook.app

import androidx.compose.ui.window.ComposeUIViewController
import com.playbook.app.voice.IosVoiceTranscriber
import com.playbook.core.ai.FakeAiClient
import com.playbook.core.ai.NoteIndexingService
import com.playbook.core.ai.RagQueryService
import com.playbook.core.db.IosDatabaseDriverFactory
import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.db.createDatabase
import com.playbook.core.platform.currentTimeMillis
import com.playbook.core.platform.randomNoteId
import com.playbook.core.repository.AttachmentRepository
import com.playbook.core.repository.EmbeddingRepository
import com.playbook.core.repository.NoteRepository
import com.playbook.core.repository.SqlDelightAttachmentRepository
import com.playbook.core.repository.SqlDelightEmbeddingRepository
import com.playbook.core.repository.SqlDelightNoteRepository
import platform.UIKit.UIViewController

// Base local, repositorios (notas, adjuntos, embeddings) y runtime de IA fake en
// iOS: se crean una sola vez al arrancar y aplican el esquema v4. La UI
// compartida los consume vía App(...).
private var playbookDatabase: PlaybookDatabase? = null
private var noteRepository: NoteRepository? = null
private var attachmentRepository: AttachmentRepository? = null
private var embeddingRepository: EmbeddingRepository? = null
private var aiClient: FakeAiClient? = null
private var noteIndexingService: NoteIndexingService? = null
private var ragQueryService: RagQueryService? = null
private var voiceTranscriber: IosVoiceTranscriber? = null

private fun resolveDatabase(): PlaybookDatabase =
    playbookDatabase ?: createDatabase(IosDatabaseDriverFactory()).also { playbookDatabase = it }

fun MainViewController(): UIViewController {
    val database = resolveDatabase()
    val notes = noteRepository ?: SqlDelightNoteRepository(
        database = database,
        idFactory = ::randomNoteId,
        clock = ::currentTimeMillis,
    ).also { noteRepository = it }
    val attachments = attachmentRepository ?: SqlDelightAttachmentRepository(
        database = database,
        idFactory = ::randomNoteId,
        clock = ::currentTimeMillis,
    ).also { attachmentRepository = it }
    val embeddings = embeddingRepository ?: SqlDelightEmbeddingRepository(
        database = database,
        clock = ::currentTimeMillis,
    ).also { embeddingRepository = it }
    // Runtime de IA por defecto: fake determinista y sin red (el adaptador real es
    // follow-up del ADR 0001). `generate` materializa el RAG.
    val ai = aiClient ?: FakeAiClient().also { aiClient = it }
    val indexing = noteIndexingService ?: NoteIndexingService(ai, embeddings).also {
        noteIndexingService = it
    }
    val rag = ragQueryService ?: RagQueryService(ai, notes, embeddings).also {
        ragQueryService = it
    }
    val transcriber = voiceTranscriber ?: IosVoiceTranscriber().also { voiceTranscriber = it }
    return ComposeUIViewController { App(notes, attachments, transcriber, indexing, rag) }
}
