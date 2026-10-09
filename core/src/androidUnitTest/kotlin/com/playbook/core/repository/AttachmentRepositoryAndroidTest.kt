package com.playbook.core.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.playbook.core.db.PlaybookDatabase
import kotlin.test.Test

class AttachmentRepositoryAndroidTest {
    @Test
    fun attachmentCrudAndCascade() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyAttachmentCrud(driver)
        } finally {
            driver.close()
        }
    }
}
