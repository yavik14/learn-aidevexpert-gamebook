package com.playbook.core.db

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import kotlin.test.assertEquals

private const val CREATE_NOTE_V1 = """
CREATE TABLE note (
  id TEXT NOT NULL PRIMARY KEY,
  owner TEXT NOT NULL,
  body TEXT NOT NULL,
  track TEXT,
  status TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
)
"""

private const val INSERT_NOTE_V1 =
    "INSERT INTO note (id, owner, body, track, status, created_at, updated_at) " +
        "VALUES ('note-v1', 'local', 'nota antes de migrar', 'mecánicas', 'capturada', 1000, 1000)"

/**
 * Esquema "vacío" (sin tablas) con versión 1. Permite obtener un driver en estado
 * base sin que el esquema actual (v2) se aplique automáticamente, para que el test
 * de migración construya la v1 con SQL explícito. Android/JVM usa directamente
 * `JdbcSqliteDriver.IN_MEMORY`; iOS usa `inMemoryDriver(BlankTestSchema)`.
 */
object BlankTestSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> = QueryResult.Unit

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Unit
}

/**
 * Prueba la primera migración del repo (v1 → v2). Asume un driver vacío:
 * construye el esquema v1 (`note` + una fila + `user_version = 1`), aplica
 * `PlaybookDatabase.Schema.migrate(1, 2)` y verifica que las notas v1 se
 * conservan intactas y la tabla `attachment` queda disponible.
 *
 * No se usa `PlaybookDatabase.Schema.create` para la v1 porque eso aplicaría el
 * esquema v2; el DDL v1 se replica aquí a propósito.
 */
fun verifyMigrationV1ToV2(driver: SqlDriver) {
    // Given: base v1 existente con una fila.
    driver.execute(null, CREATE_NOTE_V1, 0)
    driver.execute(null, "PRAGMA user_version = 1", 0)
    driver.execute(null, INSERT_NOTE_V1, 0)

    assertEquals(2L, PlaybookDatabase.Schema.version, "con 1.sqm el esquema debe ser v2")

    // La tabla `attachment` todavía no existe en v1.
    val attachmentTables = driver.executeQuery(
        identifier = null,
        sql = "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'attachment'",
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: 0L)
        },
        parameters = 0,
    ).value
    assertEquals(0L, attachmentTables, "la tabla attachment no debe existir en v1")

    // When: se aplica la migración v1 → v2.
    PlaybookDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2).value

    // Then: las notas previas se conservan intactas.
    val database = PlaybookDatabase(driver)
    val notes = database.noteQueries.selectAllNotes().executeAsList()
    assertEquals(1, notes.size, "la migración no debe perder notas")
    val note = notes.first()
    assertEquals("note-v1", note.id)
    assertEquals("local", note.owner)
    assertEquals("nota antes de migrar", note.body)
    assertEquals("mecánicas", note.track)
    assertEquals("capturada", note.status)
    assertEquals(1000L, note.created_at)
    assertEquals(1000L, note.updated_at)

    // Then: la tabla `attachment` queda disponible y usable.
    database.attachmentQueries.insertAttachment(
        id = "att-1",
        note_id = "note-v1",
        kind = "imagen",
        mime_type = "image/jpeg",
        file_path = "images/x.jpg",
        byte_size = 42L,
        created_at = 2000L,
    )
    val attachments = database.attachmentQueries
        .selectAttachmentsByNoteId("note-v1")
        .executeAsList()
    assertEquals(1, attachments.size, "attachment debe estar disponible tras migrar")
    assertEquals("note-v1", attachments.first().note_id)
    assertEquals("imagen", attachments.first().kind)
}
