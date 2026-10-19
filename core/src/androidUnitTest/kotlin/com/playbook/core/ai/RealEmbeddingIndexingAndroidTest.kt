package com.playbook.core.ai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.playbook.core.db.PlaybookDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RealEmbeddingIndexingAndroidTest {
    @Test
    fun realRuntimeWithFakeTransportAcrossIndexingPipeline() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyRealEmbeddingIndexing(driver)
        } finally {
            driver.close()
        }
    }
}
