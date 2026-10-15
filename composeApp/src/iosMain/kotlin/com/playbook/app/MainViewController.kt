package com.playbook.app

import androidx.compose.ui.window.ComposeUIViewController
import com.playbook.app.voice.IosVoiceTranscriber
import com.playbook.core.ai.FakeAiClient
import com.playbook.core.ai.NoteIndexingOrchestrator
import com.playbook.core.ai.NoteIndexingService
import com.playbook.core.ai.PendingIndexingRetry
import com.playbook.core.ai.RagQueryService
import com.playbook.core.db.IosDatabaseDriverFactory
import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.db.createDatabase
import com.playbook.core.diagnostics.FileDiagnostics
import com.playbook.core.diagnostics.diagnosticStorage
import com.playbook.core.linking.SemanticLinkingService
import com.playbook.core.platform.currentTimeMillis
import com.playbook.core.platform.randomNoteId
import com.playbook.core.repository.AttachmentRepository
import com.playbook.core.repository.EmbeddingRepository
import com.playbook.core.repository.NoteLinkRepository
import com.playbook.core.repository.NoteRepository
import com.playbook.core.repository.SqlDelightAttachmentRepository
import com.playbook.core.repository.SqlDelightEmbeddingRepository
import com.playbook.core.repository.SqlDelightNoteLinkRepository
import com.playbook.core.repository.SqlDelightNoteRepository
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

// Base local, repositorios (notas, adjuntos, embeddings, enlaces) y runtime de IA
// fake en iOS: se crean una sola vez al arrancar y aplican el esquema v5. La UI
// compartida los consume vía App(...).
private var playbookDatabase: PlaybookDatabase? = null
private var noteRepository: NoteRepository? = null
private var attachmentRepository: AttachmentRepository? = null
private var embeddingRepository: EmbeddingRepository? = null
private var noteLinkRepository: NoteLinkRepository? = null
private var aiClient: FakeAiClient? = null
private var noteIndexingService: NoteIndexingService? = null
private var noteIndexingOrchestrator: NoteIndexingOrchestrator? = null
private var pendingIndexingRetry: PendingIndexingRetry? = null
private var ragQueryService: RagQueryService? = null
private var semanticLinkingService: SemanticLinkingService? = null
private var voiceTranscriber: IosVoiceTranscriber? = null
private var diagnostics: FileDiagnostics? = null

private fun resolveDatabase(): PlaybookDatabase =
    playbookDatabase ?: createDatabase(IosDatabaseDriverFactory()).also { playbookDatabase = it }

/** Sink de diagnóstico local en `Application Support/diagnostics.log`. */
private fun diagnosticsFilePath(): String {
    val base = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String ?: error("No se encontró Application Support")
    return "$base/diagnostics.log"
}

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
    val links = noteLinkRepository ?: SqlDelightNoteLinkRepository(
        database = database,
        clock = ::currentTimeMillis,
    ).also { noteLinkRepository = it }
    // Runtime de IA por defecto: fake determinista y sin red (el adaptador real es
    // follow-up del ADR 0001). `generate` materializa el RAG.
    val ai = aiClient ?: FakeAiClient().also { aiClient = it }
    // Sink de diagnóstico local acotado en Application Support.
    val diag = diagnostics ?: FileDiagnostics(
        diagnosticStorage(diagnosticsFilePath()),
    ).also { diagnostics = it }
    val indexing = noteIndexingService ?: NoteIndexingService(ai, embeddings, diag).also {
        noteIndexingService = it
    }
    // Orquestador de indexado: envuelve el servicio y persiste la transición
    // real de `NoteStatus` (la UI lo usa al guardar y al re-indexar a mano).
    val orchestrator = noteIndexingOrchestrator ?: NoteIndexingOrchestrator(indexing, notes).also {
        noteIndexingOrchestrator = it
    }
    // Reintento de indexado pendiente (`offline-pending-retry`): al arrancar la
    // app reintenta las Notas `PENDING` y promueve a `FAILED` las irrecuperables.
    val retry = pendingIndexingRetry ?: PendingIndexingRetry(orchestrator, notes).also {
        pendingIndexingRetry = it
    }
    val rag = ragQueryService ?: RagQueryService(
        ai,
        notes,
        embeddings,
        diagnostics = diag,
    ).also { ragQueryService = it }
    val linking = semanticLinkingService ?: SemanticLinkingService(embeddings, links).also {
        semanticLinkingService = it
    }
    val transcriber = voiceTranscriber ?: IosVoiceTranscriber().also { voiceTranscriber = it }
    return ComposeUIViewController {
        App(notes, attachments, transcriber, orchestrator, retry, rag, links, linking, diag)
    }
}
