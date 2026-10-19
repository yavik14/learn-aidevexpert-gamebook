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
 * base sin que el esquema actual (v8) se aplique automáticamente, para que el test
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
 * Prueba la migración v1 → v8 (todas las del repo: `1.sqm`..`7.sqm`). Asume un
 * driver vacío: construye el esquema v1 (`note` + una fila + `user_version = 1`),
 * aplica `PlaybookDatabase.Schema.migrate(1, 8)` y verifica que las notas v1 se
 * conservan intactas, que `note_link` (v5) y `note_genre` (v7) quedan
 * disponibles y que las tablas del Proyecto (v8) se crean vacías.
 *
 * No se usa `PlaybookDatabase.Schema.create` para la v1 porque eso aplicaría el
 * esquema v8; el DDL v1 se replica aquí a propósito.
 */
fun verifyMigrationV1ToV8(driver: SqlDriver) {
    // Given: base v1 existente con una fila.
    driver.execute(null, CREATE_NOTE_V1, 0)
    driver.execute(null, "PRAGMA user_version = 1", 0)
    driver.execute(null, INSERT_NOTE_V1, 0)

    assertEquals(8L, PlaybookDatabase.Schema.version, "con 1.sqm..7.sqm el esquema debe ser v8")

    // La tabla `note_link` todavía no existe en v1.
    assertEquals(0L, tableCount(driver, "note_link"), "la tabla note_link no debe existir en v1")

    // When: se aplica la migración v1 → v8.
    PlaybookDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 8).value

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

    // Then: las tablas del Proyecto (v8) existen y arrancan vacías.
    assertEquals(1L, tableCount(driver, "game_project"), "game_project debe existir en v8")
    assertEquals(1L, tableCount(driver, "game_project_genre"))
    assertEquals(1L, tableCount(driver, "game_project_inspiration"))
    assertEquals(1L, tableCount(driver, "game_project_key_mechanic"))
    assertEquals(1L, tableCount(driver, "game_project_out_of_scope"))
    assertEquals(
        0L,
        database.gameProjectQueries.countGameProjects().executeAsOne(),
        "la migración no crea un Proyecto",
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
 * Scenario 6 de la spec de Géneros: migración aislada v6 → v7 (`6.sqm`).
 * Construye una base **v6** con `note` (con `index_attempts`) y `note_tag` con
 * filas, corre `migrate(6, 7)` y comprueba que `note_genre` contiene
 * **exactamente** esos valores (copia verbatim, sin canonización), que
 * `note_tag` ya no existe y que la Nota no se pierde.
 *
 * El esquema global ya es v8, pero esta migración aislada se detiene en v7.
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

    assertEquals(8L, PlaybookDatabase.Schema.version, "el esquema global debe ser v8")

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

/**
 * Scenario 10: migración aislada v7 → v8 (`7.sqm`). Construye una base v1,
 * corre la migración real hasta v7, siembra una Nota enlazada, un Género, un
 * Adjunto, un Enlace y un Embedding, y migra a v8. Verifica que todos esos datos
 * sobreviven, que las tablas del Proyecto se crean vacías y que `game_project`
 * no existía en v7.
 */
fun verifyMigrationV7ToV8(driver: SqlDriver) {
    // Given: una base v1 con una Nota, migrada hasta v7.
    driver.execute(null, CREATE_NOTE_V1, 0)
    driver.execute(null, "PRAGMA user_version = 1", 0)
    driver.execute(null, INSERT_NOTE_V1, 0)
    PlaybookDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 7).value

    val v7Database = PlaybookDatabase(driver)
    assertEquals(0L, tableCount(driver, "game_project"), "game_project no existe en v7")
    v7Database.noteQueries.insertNote(
        id = "note-v7b",
        owner = "local",
        body = "nota enlazada antes de v8",
        track = null,
        status = "capturada",
        created_at = 2_000L,
        updated_at = 2_000L,
    )
    v7Database.genreQueries.insertNoteGenre(note_id = "note-v1", genre = "Puzzle")
    v7Database.attachmentQueries.insertAttachment(
        id = "attachment-v7",
        note_id = "note-v1",
        kind = "imagen",
        mime_type = "image/png",
        file_path = "files/v7.png",
        byte_size = 12L,
        created_at = 2_000L,
    )
    v7Database.noteLinkQueries.upsertNoteLink(
        note_a_id = "note-v1",
        note_b_id = "note-v7b",
        score = 0.5,
        updated_at = 2_000L,
    )
    v7Database.embeddingQueries.upsertEmbedding(
        note_id = "note-v1",
        dimension = 1L,
        vector = byteArrayOf(0x3F, 0x80.toByte(), 0x00, 0x00),
        indexed_at = 2_000L,
    )

    // When: se aplica sólo la migración v7 → v8.
    PlaybookDatabase.Schema.migrate(driver, oldVersion = 7, newVersion = 8).value

    // Then: la Nota y su Género sobreviven intactos.
    val database = PlaybookDatabase(driver)
    val note = database.noteQueries.selectNoteById("note-v1").executeAsOneOrNull()
    assertEquals("note-v1", note?.id)
    assertEquals("nota antes de migrar", note?.body)
    assertEquals(
        listOf("Puzzle"),
        database.genreQueries.selectGenresByNoteId("note-v1").executeAsList(),
        "los Géneros de la Nota sobreviven a v8",
    )

    // Then: Adjuntos, Enlaces y Embeddings sobreviven intactos.
    assertEquals(
        1,
        database.attachmentQueries.selectAttachmentsByNoteId("note-v1").executeAsList().size,
        "el Adjunto sobrevive a v8",
    )
    assertEquals(
        1,
        database.noteLinkQueries.selectAllLinks().executeAsList().size,
        "el Enlace sobrevive a v8",
    )
    assertEquals(
        1,
        database.embeddingQueries.selectAllEmbeddings().executeAsList().size,
        "el Embedding sobrevive a v8",
    )

    // Then: las tablas del Proyecto existen y están vacías.
    assertEquals(1L, tableCount(driver, "game_project"))
    assertEquals(1L, tableCount(driver, "game_project_genre"))
    assertEquals(1L, tableCount(driver, "game_project_inspiration"))
    assertEquals(1L, tableCount(driver, "game_project_key_mechanic"))
    assertEquals(1L, tableCount(driver, "game_project_out_of_scope"))
    assertEquals(0L, database.gameProjectQueries.countGameProjects().executeAsOne())
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
