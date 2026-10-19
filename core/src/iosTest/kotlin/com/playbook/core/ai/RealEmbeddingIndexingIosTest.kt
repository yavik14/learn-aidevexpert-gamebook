package com.playbook.core.ai

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.playbook.core.db.PlaybookDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RealEmbeddingIndexingIosTest {
    @Test
    fun realRuntimeWithFakeTransportAcrossIndexingPipeline() = runTest {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyRealEmbeddingIndexing(driver)
        } finally {
            driver.close()
        }
    }
}
