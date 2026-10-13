package com.playbook.core.linking

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.playbook.core.db.PlaybookDatabase
import kotlin.test.Test

class SemanticLinkingAndroidTest {
    @Test
    fun thresholdInclusiveUndefinedIdempotentAndCascade() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifySemanticLinking(driver)
        } finally {
            driver.close()
        }
    }
}
