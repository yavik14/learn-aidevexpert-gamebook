package com.playbook.core.repository

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.playbook.core.db.PlaybookDatabase
import kotlin.test.Test

class GameProjectIosTest {
    @Test
    fun projectRoundTrip() {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyGameProject(driver)
        } finally {
            driver.close()
        }
    }
}
