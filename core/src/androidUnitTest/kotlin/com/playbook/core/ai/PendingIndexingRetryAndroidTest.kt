package com.playbook.core.ai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.playbook.core.db.PlaybookDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PendingIndexingRetryAndroidTest {
    @Test
    fun retriesPendingAndPromotesToFailed() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyPendingIndexingRetry(driver)
        } finally {
            driver.close()
        }
    }
}
