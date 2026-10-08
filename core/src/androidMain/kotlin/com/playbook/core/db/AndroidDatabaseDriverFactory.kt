package com.playbook.core.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/** Driver SQLDelight respaldado por SQLite en Android. */
class AndroidDatabaseDriverFactory(private val context: Context) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver =
        AndroidSqliteDriver(PlaybookDatabase.Schema, context, "playbook.db")
}
