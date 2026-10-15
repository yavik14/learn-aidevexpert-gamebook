package com.playbook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.playbook.app.voice.AndroidVoiceTranscriber
import com.playbook.core.ai.FakeAiClient
import com.playbook.core.ai.NoteIndexingOrchestrator
import com.playbook.core.ai.NoteIndexingService
import com.playbook.core.ai.PendingIndexingRetry
import com.playbook.core.ai.RagQueryService
import com.playbook.core.db.AndroidDatabaseDriverFactory
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
import java.io.File

class MainActivity : ComponentActivity() {
    // Se instancia al arrancar para aplicar el esquema local (v5) y alimentar los
    // repositorios de notas, adjuntos, embeddings y enlaces que consume la UI.
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
        val embeddingRepository: EmbeddingRepository = SqlDelightEmbeddingRepository(
            database = database,
            clock = ::currentTimeMillis,
        )
        val noteLinkRepository: NoteLinkRepository = SqlDelightNoteLinkRepository(
            database = database,
            clock = ::currentTimeMillis,
        )
        // Runtime de IA por defecto: fake determinista y sin red (el adaptador real
        // es follow-up del ADR 0001). `generate` materializa el RAG.
        val aiClient = FakeAiClient()
        // Sink de diagnóstico local acotado (`filesDir/diagnostics.log`): registra
        // los fallos de IA y de las integraciones nativas sin telemetría remota.
        val diagnostics = FileDiagnostics(
            diagnosticStorage(File(filesDir, "diagnostics.log").absolutePath),
        )
        val noteIndexingService = NoteIndexingService(aiClient, embeddingRepository, diagnostics)
        // Orquestador de indexado: envuelve el servicio y persiste la transición
        // real de `NoteStatus` (la UI lo usa al guardar y al re-indexar a mano).
        val noteIndexingOrchestrator = NoteIndexingOrchestrator(noteIndexingService, noteRepository)
        // Reintento de indexado pendiente (`offline-pending-retry`): al arrancar la
        // app reintenta las Notas `PENDING` y promueve a `FAILED` las irrecuperables.
        val pendingIndexingRetry = PendingIndexingRetry(noteIndexingOrchestrator, noteRepository)
        val ragQueryService = RagQueryService(
            aiClient,
            noteRepository,
            embeddingRepository,
            diagnostics = diagnostics,
        )
        val semanticLinkingService = SemanticLinkingService(embeddingRepository, noteLinkRepository)
        // El adaptador registra el permission launcher, por eso se construye aquí
        // (onCreate) y no dentro del composable.
        val voiceTranscriber = AndroidVoiceTranscriber(this)
        setContent {
            App(
                noteRepository = noteRepository,
                attachmentRepository = attachmentRepository,
                voiceTranscriber = voiceTranscriber,
                noteIndexingOrchestrator = noteIndexingOrchestrator,
                pendingIndexingRetry = pendingIndexingRetry,
                ragQueryService = ragQueryService,
                noteLinkRepository = noteLinkRepository,
                semanticLinkingService = semanticLinkingService,
                diagnostics = diagnostics,
            )
        }
    }
}
