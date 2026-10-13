package com.playbook.core.linking

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.playbook.core.db.PlaybookDatabase
import kotlin.test.Test

class SemanticLinkingIosTest {
    @Test
    fun thresholdInclusiveUndefinedIdempotentAndCascade() {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifySemanticLinking(driver)
        } finally {
            driver.close()
        }
    }
}
