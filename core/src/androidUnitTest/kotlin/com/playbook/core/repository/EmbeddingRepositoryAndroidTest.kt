package com.playbook.core.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.playbook.core.db.PlaybookDatabase
import kotlin.test.Test

class EmbeddingRepositoryAndroidTest {
    @Test
    fun roundTripCodecOverwriteAndCascade() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyEmbeddingRepository(driver)
        } finally {
            driver.close()
        }
    }
}
