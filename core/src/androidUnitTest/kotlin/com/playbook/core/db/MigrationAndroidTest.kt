package com.playbook.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test

class MigrationAndroidTest {
    @Test
    fun migratesV1ToV6PreservingNotes() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            verifyMigrationV1ToV6(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun migratesV5ToV6AddingIndexAttempts() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            verifyMigrationV5ToV6(driver)
        } finally {
            driver.close()
        }
    }
}
