package com.playbook.core.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/** Driver SQLDelight respaldado por SQLite nativo en iOS. */
class IosDatabaseDriverFactory : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver =
        NativeSqliteDriver(PlaybookDatabase.Schema, "playbook.db")
}
