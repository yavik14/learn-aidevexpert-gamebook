package com.playbook.core.repository

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.playbook.core.db.PlaybookDatabase
import kotlin.test.Test

class AttachmentRepositoryIosTest {
    @Test
    fun attachmentCrudAndCascade() {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyAttachmentCrud(driver)
        } finally {
            driver.close()
        }
    }
}
