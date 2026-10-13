package com.playbook.core.ai

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.playbook.core.db.PlaybookDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RagQueryAndroidTest {
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

    private suspend fun withInMemoryDb(block: suspend (SqlDriver) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            block(driver)
        } finally {
            driver.close()
        }
    }
}
