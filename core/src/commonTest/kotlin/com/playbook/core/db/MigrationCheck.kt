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

/** DDL de `note` en v6 (idéntico al de v1 + `index_attempts`). */
private const val CREATE_NOTE_V6 = """
CREATE TABLE note (
  id TEXT NOT NULL PRIMARY KEY,
  owner TEXT NOT NULL,
  body TEXT NOT NULL,
  track TEXT,
  status TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  index_attempts INTEGER NOT NULL DEFAULT 0
)
"""

/** DDL de `note_tag` en v6 (el que creó `2.sqm`). */
private const val CREATE_NOTE_TAG_V6 = """
CREATE TABLE note_tag (
  note_id TEXT NOT NULL,
  label TEXT NOT NULL,
  PRIMARY KEY (note_id, label)
)
"""

/**
 * Esquema "vacío" (sin tablas) con versión 1. Permite obtener un driver en estado
 * base sin que el esquema actual (v7) se aplique automáticamente, para que el test
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
 * Prueba la migración v1 → v7 (todas las del repo: `1.sqm`..`6.sqm`). Asume un
 * driver vacío: construye el esquema v1 (`note` + una fila + `user_version = 1`),
 * aplica `PlaybookDatabase.Schema.migrate(1, 7)` y verifica que las notas v1 se
 * conservan intactas, que la columna `index_attempts` (v6) queda con default `0`,
 * que la tabla `note_link` (v5) queda disponible y que `note_tag` (v2) fue
 * reemplazada por `note_genre` (v7).
 *
 * No se usa `PlaybookDatabase.Schema.create` para la v1 porque eso aplicaría el
 * esquema v7; el DDL v1 se replica aquí a propósito.
 */
fun verifyMigrationV1ToV7(driver: SqlDriver) {
    // Given: base v1 existente con una fila.
    driver.execute(null, CREATE_NOTE_V1, 0)
    driver.execute(null, "PRAGMA user_version = 1", 0)
    driver.execute(null, INSERT_NOTE_V1, 0)

    assertEquals(7L, PlaybookDatabase.Schema.version, "con 1.sqm..6.sqm el esquema debe ser v7")

    // La tabla `note_link` todavía no existe en v1.
    val noteLinkTables = driver.executeQuery(
        identifier = null,
        sql = "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'note_link'",
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: 0L)
        },
        parameters = 0,
    ).value
    assertEquals(0L, noteLinkTables, "la tabla note_link no debe existir en v1")

    // When: se aplica la migración v1 → v7.
    PlaybookDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 7).value

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
    assertEquals(0L, note.index_attempts, "index_attempts (v6) debe existir con default 0")

    // Then: la tabla `note_link` queda disponible y usable (FK exige 2 notas).
    database.noteQueries.insertNote(
        id = "note-v1b",
        owner = "local",
        body = "otra nota",
        track = null,
        status = "capturada",
        created_at = 2000L,
        updated_at = 2000L,
    )
    database.noteLinkQueries.upsertNoteLink(
        note_a_id = "note-v1",
        note_b_id = "note-v1b",
        score = 0.75,
        updated_at = 3000L,
    )
    val links = database.noteLinkQueries.selectAllLinks().executeAsList()
    assertEquals(1, links.size, "note_link debe estar disponible tras migrar")
    assertEquals("note-v1", links.first().note_a_id)
    assertEquals("note-v1b", links.first().note_b_id)
    assertEquals(0.75, links.first().score)

    // Then: `note_genre` (v7) existe y `note_tag` fue eliminada.
    assertEquals(0L, tableCount(driver, "note_tag"), "note_tag debe eliminarse al migrar a v7")
    assertEquals(1L, tableCount(driver, "note_genre"), "note_genre debe existir tras migrar a v7")
    database.genreQueries.insertNoteGenre(note_id = "note-v1", genre = "Puzzle")
    assertEquals(
        listOf("Puzzle"),
        database.genreQueries.selectGenresByNoteId("note-v1").executeAsList(),
        "note_genre debe quedar operativa tras migrar",
    )
}

/**
 * Prueba el paso aislado v5 → v6 (`5.sqm`): agrega `index_attempts` a `note` sin
 * perder las Notas. Como la tabla `note` no cambió entre v1 y v5, su DDL v5 es el
 * de v1; `migrate(5, 6)` corre sólo `5.sqm`.
 */
fun verifyMigrationV5ToV6(driver: SqlDriver) {
    // Given: base v5 con la tabla `note` (sin `index_attempts`) y una fila.
    driver.execute(null, CREATE_NOTE_V1, 0)
    driver.execute(null, "PRAGMA user_version = 5", 0)
    driver.execute(null, INSERT_NOTE_V1, 0)

    // When: se aplica sólo la migración v5 → v6.
    PlaybookDatabase.Schema.migrate(driver, oldVersion = 5, newVersion = 6).value

    // Then: la Nota se conserva y `index_attempts` existe con default 0.
    val database = PlaybookDatabase(driver)
    val notes = database.noteQueries.selectAllNotes().executeAsList()
    assertEquals(1, notes.size, "la migración v5 → v6 no debe perder notas")
    val note = notes.first()
    assertEquals("note-v1", note.id)
    assertEquals("nota antes de migrar", note.body)
    assertEquals("mecánicas", note.track)
    assertEquals("capturada", note.status)
    assertEquals(1000L, note.updated_at)
    assertEquals(0L, note.index_attempts, "index_attempts debe existir con default 0")

    // Then: la columna nueva es operable.
    database.noteQueries.updateNoteIndexAttempts(index_attempts = 2L, id = "note-v1")
    assertEquals(
        2L,
        database.noteQueries.selectNoteById("note-v1").executeAsOne().index_attempts,
        "updateNoteIndexAttempts debe operar tras la migración",
    )
}

/**
 * Scenario 6: migración aislada v6 → v7 (`6.sqm`). Construye una base **v6** con
 * `note` (con `index_attempts`) y `note_tag` con filas, corre `migrate(6, 7)` y
 * comprueba que `note_genre` contiene **exactamente** esos valores (copia
 * verbatim, sin canonización), que `note_tag` ya no existe y que la Nota no se
 * pierde.
 */
fun verifyMigrationV6ToV7(driver: SqlDriver) {
    // Given: base v6 con `note` + `note_tag` y filas.
    driver.execute(null, CREATE_NOTE_V6, 0)
    driver.execute(null, CREATE_NOTE_TAG_V6, 0)
    driver.execute(null, "PRAGMA user_version = 6", 0)
    driver.execute(
        null,
        "INSERT INTO note (id, owner, body, track, status, created_at, updated_at, index_attempts) " +
            "VALUES ('note-v6', 'local', 'nota con etiquetas', 'historia', 'capturada', 1000, 1000, 0)",
        0,
    )
    driver.execute(null, "INSERT INTO note_tag (note_id, label) VALUES ('note-v6', 'jefe')", 0)
    driver.execute(null, "INSERT INTO note_tag (note_id, label) VALUES ('note-v6', ' JRPG ')", 0)
    driver.execute(null, "INSERT INTO note_tag (note_id, label) VALUES ('note-v6', 'JRPG')", 0)

    assertEquals(7L, PlaybookDatabase.Schema.version, "el esquema debe ser v7")

    // When: se aplica sólo la migración v6 → v7.
    PlaybookDatabase.Schema.migrate(driver, oldVersion = 6, newVersion = 7).value

    // Then: `note_genre` contiene exactamente las etiquetas (verbatim, sin canonizar).
    val database = PlaybookDatabase(driver)
    assertEquals(
        listOf(" JRPG ", "JRPG", "jefe"),
        database.genreQueries.selectGenresByNoteId("note-v6").executeAsList(),
        "note_genre copia las etiquetas verbatim (sin canonización)",
    )
    // Then: `note_tag` ya no existe.
    assertEquals(0L, tableCount(driver, "note_tag"), "note_tag debe eliminarse en v6 → v7")
    // Then: la Nota sobrevive intacta.
    val migrated = database.noteQueries.selectNoteById("note-v6").executeAsOneOrNull()
    assertEquals("note-v6", migrated?.id, "la Nota v6 sobrevive a la migración")
    assertEquals("nota con etiquetas", migrated?.body)
    assertEquals("historia", migrated?.track)
    assertEquals(0L, migrated?.index_attempts)
}

/** Cuenta cuántas tablas con [name] existen (0 = no existe). */
private fun tableCount(driver: SqlDriver, name: String): Long = driver.executeQuery(
    identifier = null,
    sql = "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = '$name'",
    mapper = { cursor ->
        cursor.next()
        QueryResult.Value(cursor.getLong(0) ?: 0L)
    },
    parameters = 0,
).value
