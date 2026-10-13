package com.playbook.core.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.playbook.core.db.PlaybookDatabase
import kotlin.test.Test

class NoteLinkRepositoryAndroidTest {
    @Test
    fun canonicalUpsertReadsBothSidesAndCascade() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyNoteLinkRepository(driver)
        } finally {
            driver.close()
        }
    }
}
