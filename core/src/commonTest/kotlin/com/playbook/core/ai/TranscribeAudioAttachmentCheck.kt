package com.playbook.core.ai

import app.cash.sqldelight.db.SqlDriver
import com.playbook.core.db.DatabaseDriverFactory
import com.playbook.core.db.createDatabase
import com.playbook.core.diagnostics.DiagnosticSource
import com.playbook.core.diagnostics.InMemoryDiagnostics
import com.playbook.core.model.AttachmentKind
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.NoteStatus
import com.playbook.core.repository.SqlDelightAttachmentRepository
import com.playbook.core.repository.SqlDelightEmbeddingRepository
import com.playbook.core.repository.SqlDelightNoteRepository
import com.playbook.core.storage.AttachmentFileStore
import com.playbook.core.storage.StoredImage
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Store en memoria: los tests siembran rutas relativas arbitrarias (`audio/...`). */
private class InMemoryAudioFileStore : AttachmentFileStore {
    private val files = mutableMapOf<String, ByteArray>()
    private var sequence = 0

    fun put(relativePath: String, bytes: ByteArray) {
        files[relativePath] = bytes.copyOf()
    }

    override fun saveImage(bytes: ByteArray, mimeType: String): StoredImage {
        val path = "images/fake-${sequence++}.jpg"
        files[path] = bytes.copyOf()
        return StoredImage(path, bytes.size.toLong())
    }

    override fun read(relativePath: String): ByteArray? = files[relativePath]?.copyOf()

    override fun delete(relativePath: String): Boolean = files.remove(relativePath) != null
}

/** [TranscribeClient] que registra bytes/mime exactos y responde con [output]. */
private class RecordingTranscribeClient(
    private val output: (ByteArray, String) -> String,
) : TranscribeClient {
    val audios = mutableListOf<ByteArray>()
    val mimeTypes = mutableListOf<String>()

    override suspend fun transcribe(audio: ByteArray, mimeType: String): String {
        audios += audio.copyOf()
        mimeTypes += mimeType
        return output(audio, mimeType)
    }
}

/** [TranscribeClient] que siempre lanza [failure]; para el Scenario 7. */
private class FailingTranscribeClient(val failure: AiClientException) : TranscribeClient {
    var calls = 0

    override suspend fun transcribe(audio: ByteArray, mimeType: String): String {
        calls++
        throw failure
    }
}

/**
 * Escenarios 1, 3–7 y 9 de `transcribe-audio-attachment` contra un [SqlDriver]
 * ya inicializado (esquema v8). Corre en Android/JVM e iOS con el driver
 * in-memory de cada plataforma; el transcriptor y el store son deterministas y
 * sin red (IA fake).
 *
 * El caso de uso no persiste: el Scenario 3 simula el guardado del editor
 * (`noteRepository.update` + `NoteIndexingOrchestrator.index`) para comprobar
 * que el `body` transcripto pasa por el indexado existente y queda `INDEXED`.
 */
suspend fun verifyAudioAttachmentTranscription(driver: SqlDriver) {
    val database = createDatabase(
        object : DatabaseDriverFactory {
            override fun createDriver(): SqlDriver = driver
        },
    )
    var noteSequence = 0
    var attachmentSequence = 0
    var now = 1_700_000_000_000L
    val noteRepository = SqlDelightNoteRepository(
        database = database,
        idFactory = { "note-${noteSequence++}" },
        clock = { now },
    )
    val attachmentRepository = SqlDelightAttachmentRepository(
        database = database,
        idFactory = { "attachment-${attachmentSequence++}" },
        clock = { now },
    )
    val embeddingRepository = SqlDelightEmbeddingRepository(database = database, clock = { now })
    val store = InMemoryAudioFileStore()
    val diagnostics = InMemoryDiagnostics(clock = { now })

    fun attachment(
        noteId: String,
        filePath: String,
        byteSize: Long,
        kind: AttachmentKind = AttachmentKind.AUDIO,
        mimeType: String = "audio/mp4",
    ) = attachmentRepository.create(
        noteId = noteId,
        kind = kind,
        mimeType = mimeType,
        filePath = filePath,
        byteSize = byteSize,
    )

    // --- Scenario 1/9: cuerpo vacío, bytes exactos y determinismo ----------
    val note = noteRepository.create(NoteDraft(owner = "local", body = "", track = null))
    val firstBytes = ByteArray(64) { index -> (index % 251).toByte() }
    store.put("audio/first.m4a", firstBytes)
    val firstAudio = attachment(note.id, "audio/first.m4a", firstBytes.size.toLong())

    val recorder = RecordingTranscribeClient { audio, mimeType ->
        "[fake] transcripción (${audio.size} bytes, $mimeType)"
    }
    val service = AudioAttachmentTranscriptionService(
        transcribeClient = recorder,
        attachmentRepository = attachmentRepository,
        fileStore = store,
        diagnostics = diagnostics,
    )

    val first = service.transcribe(note.id, firstAudio.id)
    assertIs<AudioTranscriptionResult.Transcribed>(first, "un audio legible debe transcribirse")
    assertEquals(
        "[fake] transcripción (64 bytes, audio/mp4)",
        first.text,
        "el texto debe ser el del fake determinista",
    )
    assertEquals(1, recorder.audios.size, "se llama al transcriptor una sola vez")
    assertContentEquals(firstBytes, recorder.audios.single(), "se leen los bytes exactos")
    assertEquals("audio/mp4", recorder.mimeTypes.single())

    // El caso de uso no persiste ni modifica la Nota/Adjuntos.
    assertEquals("", noteRepository.getById(note.id)?.body, "el servicio no escribe el body")
    assertEquals(
        listOf(firstAudio),
        attachmentRepository.getByNoteId(note.id),
        "el servicio no crea ni borra Adjuntos",
    )

    // Determinismo (Scenario 9): misma entrada → mismo texto, sin red.
    val repeated = service.transcribe(note.id, firstAudio.id)
    assertIs<AudioTranscriptionResult.Transcribed>(repeated)
    assertEquals(first.text, repeated.text, "dos transcripciones del mismo archivo son idénticas")
    assertEquals(2, recorder.audios.size)
    assertTrue(diagnostics.snapshot().isEmpty(), "el camino exitoso no registra diagnósticos")

    // --- Scenario 3: guardar re-indexa con el camino existente ------------
    val edited = noteRepository.getById(note.id)!!.copy(body = first.text)
    assertTrue(noteRepository.update(edited), "el editor persiste el body transcripto")
    val orchestrator = NoteIndexingOrchestrator(
        indexingService = NoteIndexingService(FakeAiClient(), embeddingRepository),
        noteRepository = noteRepository,
    )
    val status = orchestrator.index(noteRepository.getById(note.id)!!)
    assertEquals(NoteStatus.INDEXED, status, "con IA fake la Nota transcripta queda INDEXED")
    assertEquals(NoteStatus.INDEXED, noteRepository.getById(note.id)?.status)
    assertNotNull(
        embeddingRepository.getByNoteId(note.id),
        "el guardado re-indexa y persiste el embedding",
    )

    // --- Scenario 4: varios AUDIO, orden determinista y elección ----------
    // Mismo `created_at` a propósito: el desempate es `id ASC`.
    val secondBytes = ByteArray(128) { index -> (index % 239).toByte() }
    store.put("audio/second.m4a", secondBytes)
    val secondAudio = attachment(
        noteId = note.id,
        filePath = "audio/second.m4a",
        byteSize = secondBytes.size.toLong(),
        mimeType = "audio/x-m4a",
    )
    assertEquals(
        listOf(firstAudio, secondAudio),
        attachmentRepository.getByNoteId(note.id),
        "los AUDIO se listan por created_at ASC, id ASC",
    )
    val recorderCallsBeforeChoice = recorder.audios.size
    val chosen = service.transcribe(note.id, secondAudio.id)
    assertIs<AudioTranscriptionResult.Transcribed>(chosen)
    assertEquals(
        "[fake] transcripción (128 bytes, audio/x-m4a)",
        chosen.text,
        "Transcribir usa sólo el Adjunto elegido",
    )
    assertEquals(
        recorderCallsBeforeChoice + 1,
        recorder.audios.size,
        "elegir un audio no dispara llamadas extra",
    )
    assertContentEquals(secondBytes, recorder.audios.last(), "se leen los bytes del audio elegido")

    // Un Adjunto de otra Nota no se puede transcribir desde esta Nota.
    val otherNote = noteRepository.create(NoteDraft(owner = "local", body = "otra", track = null))
    store.put("audio/other.m4a", ByteArray(8))
    val otherAudio = attachment(otherNote.id, "audio/other.m4a", 8L)
    val callsBeforeOther = recorder.audios.size
    assertEquals(
        AudioTranscriptionResult.AudioNotFound,
        service.transcribe(note.id, otherAudio.id),
        "un Adjunto de otra Nota no pertenece a la Nota pedida",
    )
    assertEquals(
        AudioTranscriptionResult.AudioNotFound,
        service.transcribe(note.id, "attachment-inexistente"),
        "un attachmentId inexistente es AudioNotFound",
    )

    // Un Adjunto IMAGE tampoco es transcribible.
    val image = attachment(
        noteId = note.id,
        filePath = "images/boceto.jpg",
        byteSize = 16L,
        kind = AttachmentKind.IMAGE,
        mimeType = "image/jpeg",
    )
    assertEquals(
        AudioTranscriptionResult.AudioNotFound,
        service.transcribe(note.id, image.id),
        "sólo los Adjuntos AUDIO se transcriben",
    )
    assertEquals(callsBeforeOther, recorder.audios.size, "AudioNotFound no llama al transcriptor")

    // --- Scenario 5: archivo ausente/ilegible ------------------------------
    val missingAudio = attachment(note.id, "audio/missing.m4a", 40L)
    assertEquals(
        AudioTranscriptionResult.AudioUnavailable,
        service.transcribe(note.id, missingAudio.id),
        "sin archivo legible el resultado es AudioUnavailable",
    )
    assertEquals(
        callsBeforeOther,
        recorder.audios.size,
        "un archivo ausente no llama al transcriptor",
    )
    assertEquals(edited.body, noteRepository.getById(note.id)?.body, "el body no cambia")

    // --- Scenario 6: transcripción vacía/blanca ----------------------------
    val blankService = AudioAttachmentTranscriptionService(
        transcribeClient = RecordingTranscribeClient { _, _ -> "   \n  " },
        attachmentRepository = attachmentRepository,
        fileStore = store,
        diagnostics = diagnostics,
    )
    assertEquals(
        AudioTranscriptionResult.EmptyTranscription,
        blankService.transcribe(note.id, firstAudio.id),
        "texto sólo con espacios es EmptyTranscription",
    )
    assertEquals(edited.body, noteRepository.getById(note.id)?.body, "vacío no toca el body")
    assertTrue(diagnostics.snapshot().isEmpty(), "vacío no registra diagnóstico")

    // --- Scenario 7: AiClientException / red -------------------------------
    val failure = AiClientException("red no disponible")
    val failing = FailingTranscribeClient(failure)
    val failingService = AudioAttachmentTranscriptionService(
        transcribeClient = failing,
        attachmentRepository = attachmentRepository,
        fileStore = store,
        diagnostics = diagnostics,
    )
    val bodyBeforeFailure = noteRepository.getById(note.id)?.body
    val failed = failingService.transcribe(note.id, firstAudio.id)
    assertIs<AudioTranscriptionResult.Failed>(failed)
    assertSame(failure, failed.cause, "Failed propaga la causa del runtime")
    assertEquals(1, failing.calls)
    assertEquals(bodyBeforeFailure, noteRepository.getById(note.id)?.body, "el body no cambia")
    val diagnostic = diagnostics.snapshot().single()
    assertEquals(DiagnosticSource.AI_TRANSCRIBE, diagnostic.source)
    assertEquals(AudioAttachmentTranscriptionService.SUMMARY_FAILED, diagnostic.summary)
    assertNull(diagnostic.detail, "el diagnóstico no lleva mensaje/texto/datos del archivo")
    assertEquals(
        "transcription_failed",
        diagnostic.summary,
        "el resumen estable no contiene datos del usuario",
    )
    assertTrue(!diagnostic.summary.contains(note.id), "el resumen no incluye ids")
    assertTrue(!diagnostic.summary.contains(firstAudio.id), "el resumen no incluye ids de Adjunto")
}
