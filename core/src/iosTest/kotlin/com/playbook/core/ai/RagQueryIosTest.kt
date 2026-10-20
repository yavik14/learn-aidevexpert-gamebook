package com.playbook.core.ai

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import com.playbook.core.db.PlaybookDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RagQueryIosTest {
    @Test
    fun cosineSimilarityContract() = verifyCosineSimilarity()

    @Test
    fun retrievesAnswerAnchoredInNotes() = runTest {
        withInMemoryDb { verifyRagAnswer(it) }
    }

    @Test
    fun noIndexedNotesSkipsRuntime() = runTest {
        withInMemoryDb { verifyRagNoIndexedNotes(it) }
    }

    @Test
    fun runtimeFailureMapsToFailed() = runTest {
        withInMemoryDb { verifyRagRuntimeFailure(it) }
    }

    @Test
    fun invalidEmbeddingDoesNotGenerate() = runTest {
        withInMemoryDb { verifyRagInvalidEmbedding(it) }
    }

    @Test
    fun rankingIsDeterministicAndTopK() = runTest {
        withInMemoryDb { verifyRagRanking(it) }
    }

    @Test
    fun realChatContractAndNormalization() = runTest {
        withInMemoryDb { verifyRagRealChatContract(it) }
    }

    private suspend fun withInMemoryDb(block: suspend (SqlDriver) -> Unit) {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            block(driver)
        } finally {
            driver.close()
        }
    }
}
