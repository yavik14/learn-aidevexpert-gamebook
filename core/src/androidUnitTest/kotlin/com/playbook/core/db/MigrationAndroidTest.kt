package com.playbook.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test

class MigrationAndroidTest {
    @Test
    fun migratesV1ToV4PreservingNotes() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            verifyMigrationV1ToV4(driver)
        } finally {
            driver.close()
        }
    }
}
