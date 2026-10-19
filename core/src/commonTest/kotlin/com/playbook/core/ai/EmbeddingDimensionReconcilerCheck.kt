package com.playbook.core.ai

import com.playbook.core.model.Note
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.NoteEmbedding
import com.playbook.core.model.NoteStatus
import com.playbook.core.repository.EmbeddingRepository
import com.playbook.core.repository.NoteRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeEmbeddingRepository(
    initial: List<NoteEmbedding> = emptyList(),
) : EmbeddingRepository {
    private val embeddings = initial.associateBy { it.noteId }.toMutableMap()

    override fun upsert(noteId: String, values: List<Float>): NoteEmbedding =
        NoteEmbedding(noteId = noteId, values = values, indexedAt = 0L)
            .also { embeddings[noteId] = it }

    override fun getByNoteId(noteId: String): NoteEmbedding? = embeddings[noteId]

    override fun getAll(): List<NoteEmbedding> = embeddings.values.sortedBy { it.noteId }

    override fun deleteByNoteId(noteId: String): Boolean = embeddings.remove(noteId) != null
}

private class FakeNoteRepository(
    private val statuses: MutableMap<String, NoteStatus> = mutableMapOf(),
) : NoteRepository {
    val attempts: MutableMap<String, Int> = mutableMapOf()

    override fun create(draft: NoteDraft): Note = error("no usado en este test")

    override fun getAll(): List<Note> = error("no usado en este test")

    override fun getById(id: String): Note? = null

    override fun getByStatus(status: NoteStatus): List<Note> = emptyList()

    override fun update(note: Note): Boolean = false

    override fun updateStatus(id: String, status: NoteStatus): Boolean {
        if (id !in statuses) return false
        statuses[id] = status
        return true
    }

    override fun setIndexAttempts(id: String, attempts: Int): Boolean {
        if (id !in statuses) return false
        this.attempts[id] = attempts
        return true
    }

    override fun delete(id: String): Boolean = false

    fun statusOf(id: String): NoteStatus? = statuses[id]
}

private fun embedding(noteId: String, dimension: Int): NoteEmbedding =
    NoteEmbedding(noteId = noteId, values = List(dimension) { 0.5f }, indexedAt = 1L)

class EmbeddingDimensionReconcilerCheck {

    @Test
    fun clearsOnlyMismatchedAndMarksPending() {
        val embeddings = FakeEmbeddingRepository(
            listOf(
                embedding("note-8", dimension = 8),
                embedding("note-3", dimension = 3),
            ),
        )
        val notes = FakeNoteRepository(
            mutableMapOf(
                "note-8" to NoteStatus.INDEXED,
                "note-3" to NoteStatus.INDEXED,
            ),
        )
        val reconciler = EmbeddingDimensionReconciler(embeddings, notes)

        val summary = reconciler.reconcile(expectedDimension = 3)

        assertEquals(ReconcileSummary(expectedDimension = 3, cleared = 1), summary)
        assertNull(embeddings.getByNoteId("note-8"))
        assertEquals(NoteStatus.PENDING, notes.statusOf("note-8"))
        assertEquals(0, notes.attempts["note-8"])
        assertEquals(3, embeddings.getByNoteId("note-3")!!.values.size)
        assertEquals(NoteStatus.INDEXED, notes.statusOf("note-3"))
    }

    @Test
    fun nullDimensionIsNoOp() {
        val embeddings = FakeEmbeddingRepository(listOf(embedding("note-8", dimension = 8)))
        val notes = FakeNoteRepository(mutableMapOf("note-8" to NoteStatus.INDEXED))
        val reconciler = EmbeddingDimensionReconciler(embeddings, notes)

        val summary = reconciler.reconcile(expectedDimension = null)

        assertEquals(ReconcileSummary(expectedDimension = null, cleared = 0), summary)
        assertTrue(embeddings.getAll().isNotEmpty())
        assertEquals(NoteStatus.INDEXED, notes.statusOf("note-8"))
    }

    @Test
    fun matchingDimensionsAreUntouched() {
        val embeddings = FakeEmbeddingRepository(listOf(embedding("note-3", dimension = 3)))
        val notes = FakeNoteRepository(mutableMapOf("note-3" to NoteStatus.INDEXED))
        val reconciler = EmbeddingDimensionReconciler(embeddings, notes)

        val summary = reconciler.reconcile(expectedDimension = 3)

        assertEquals(0, summary.cleared)
        assertTrue(embeddings.getAll().isNotEmpty())
        assertEquals(NoteStatus.INDEXED, notes.statusOf("note-3"))
        assertTrue(notes.attempts.isEmpty())
    }

    @Test
    fun rejectsNonPositiveDimension() {
        val reconciler = EmbeddingDimensionReconciler(
            FakeEmbeddingRepository(),
            FakeNoteRepository(),
        )

        assertFailsWith<IllegalArgumentException> {
            reconciler.reconcile(expectedDimension = 0)
        }
    }
}
