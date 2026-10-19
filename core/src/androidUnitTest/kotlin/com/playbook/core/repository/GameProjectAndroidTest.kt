package com.playbook.core.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.playbook.core.db.PlaybookDatabase
import kotlin.test.Test

class GameProjectAndroidTest {
    @Test
    fun projectRoundTrip() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyGameProject(driver)
        } finally {
            driver.close()
        }
    }
}
