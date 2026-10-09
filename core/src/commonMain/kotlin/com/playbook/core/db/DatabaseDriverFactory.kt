package com.playbook.core.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * Crea el [SqlDriver] adecuado para cada plataforma. La implementación real vive
 * en `androidMain` / `iosMain`; el core sólo conoce la abstracción.
 */
interface DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

/**
 * Construye la [PlaybookDatabase] a partir del driver de la plataforma y fuerza
 * la apertura de la conexión para que el esquema local quede aplicado al arrancar.
 *
 * El driver nativo de SQLDelight (`NativeSqliteDriver`) abre la conexión de forma
 * perezosa: crear el driver sólo crea el directorio de la base, no el archivo ni
 * el esquema. Un `PRAGMA` inocuo fuerza esa apertura; en Android es idempotente
 * porque `AndroidSqliteDriver` ya abre la base en su constructor.
 *
 * El `PRAGMA user_version` retorna una fila, por lo que debe ejecutarse con
 * `executeQuery(...)` y no con `execute(...)`: SQLite (Jdbc y nativo) rechaza
 * `execute` sobre una sentencia que devuelve resultados.
 */
fun createDatabase(driverFactory: DatabaseDriverFactory): PlaybookDatabase {
    val driver = driverFactory.createDriver()
    driver.executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor ->
            cursor.next()
            QueryResult.Unit
        },
        parameters = 0,
    )
    return PlaybookDatabase(driver)
}
