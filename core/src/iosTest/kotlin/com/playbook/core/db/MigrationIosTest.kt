package com.playbook.core.db

import app.cash.sqldelight.driver.native.inMemoryDriver
import kotlin.test.Test

class MigrationIosTest {
    @Test
    fun migratesV1ToV5PreservingNotes() {
        val driver = inMemoryDriver(BlankTestSchema)
        try {
            verifyMigrationV1ToV5(driver)
        } finally {
            driver.close()
        }
    }
}
