package com.playbook.core.db

import app.cash.sqldelight.driver.native.inMemoryDriver
import kotlin.test.Test

class MigrationIosTest {
    @Test
    fun migratesV1ToV6PreservingNotes() {
        val driver = inMemoryDriver(BlankTestSchema)
        try {
            verifyMigrationV1ToV6(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun migratesV5ToV6AddingIndexAttempts() {
        val driver = inMemoryDriver(BlankTestSchema)
        try {
            verifyMigrationV5ToV6(driver)
        } finally {
            driver.close()
        }
    }
}
