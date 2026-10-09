package com.playbook.core.repository

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.playbook.core.db.PlaybookDatabase
import kotlin.test.Test

class EmbeddingRepositoryIosTest {
    @Test
    fun roundTripCodecOverwriteAndCascade() {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyEmbeddingRepository(driver)
        } finally {
            driver.close()
        }
    }
}
