package com.playbook.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test

class MigrationAndroidTest {
    @Test
    fun migratesV1ToV2PreservingNotes() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            verifyMigrationV1ToV2(driver)
        } finally {
            driver.close()
        }
    }
}
