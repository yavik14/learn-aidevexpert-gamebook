package com.playbook.core.db

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.assertEquals

/**
 * Regresión del bug que rompía el arranque: `createDatabase(...)` ejecutaba el
 * `PRAGMA user_version` de apertura con `execute(...)`, y SQLite rechaza
 * `execute` sobre una sentencia que retorna filas. El test anterior
 * (`verifyNotePersistence`) sólo instanciaba `PlaybookDatabase` directamente y
 * no cubría este helper, por eso el bug no se detectó.
 *
 * Ejercita exactamente el path cableado al arrancar la app: `createDatabase`
 * con un [SqlDriver] ya inicializado. Verifica que no lanza y que la DB
 * responde con la tabla `note` vacía.
 */
fun verifyCreateDatabaseOpensConnection(driver: SqlDriver) {
    val database = createDatabase(
        object : DatabaseDriverFactory {
            override fun createDriver(): SqlDriver = driver
        },
    )
    assertEquals(
        0,
        database.noteQueries.selectAllNotes().executeAsList().size,
        "la DB debe responder con la tabla note vacía",
    )
}
