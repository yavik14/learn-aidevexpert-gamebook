package com.playbook.core.db

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * `SqlSchema` vacío para crear un driver in-memory **sin** esquema en iOS. A
 * diferencia de `JdbcSqliteDriver` en JVM, el helper nativo `inMemoryDriver`
 * exige una `SqlSchema`; con `version = 1` y `create` no-op queda una base vacía
 * sobre la que el test puede aplicar el esquema v1 a mano.
 */
internal object EmptySqlSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1L

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> = QueryResult.Value(Unit)

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Value(Unit)
}

/**
 * Scenario 7 de la spec: migración v1 → v7 y bootstrap en ambos caminos.
 *
 * [migratedDriver] debe ser una base **vacía** (sin esquema) sobre la que se
 * aplica el v1 a mano; [freshDriver] debe tener ya el esquema v7 creado desde
 * cero (`Schema.create` en JVM o `inMemoryDriver(Schema)` en iOS). El esquema
 * final (columnas de `note`/`embedding`/`note_link`/`note_genre`, foreign keys y
 * la ausencia de `note_tag`) debe coincidir entre ambos caminos.
 */
fun verifyDatabaseMigration(migratedDriver: SqlDriver, freshDriver: SqlDriver) {
    // Esquema v1 aplicado a mano + una fila legacy.
    migratedDriver.execute(null, V1_NOTE_CREATE, 0)
    val migratedDatabase = PlaybookDatabase(migratedDriver)
    migratedDatabase.noteQueries.insertNote(
        id = "legacy-1",
        owner = "owner-1",
        body = "nota legacy",
        track = "mecánicas",
        status = "capturada",
        created_at = 1_700_000_000_000L,
        updated_at = 1_700_000_000_000L,
    )

    PlaybookDatabase.Schema.migrate(migratedDriver, oldVersion = 1L, newVersion = 7L)

    assertEquals(7L, PlaybookDatabase.Schema.version, "PlaybookDatabase.Schema.version debe ser 7")

    // La fila v1 sobrevive a la migración.
    val legacy = migratedDatabase.noteQueries.selectNoteById("legacy-1").executeAsOneOrNull()
    assertNotNull(legacy, "la fila v1 debe sobrevivir a la migración")
    assertEquals("nota legacy", legacy.body)
    assertEquals(0L, legacy.index_attempts, "index_attempts (v6) arranca en 0")

    // La tabla `embedding` existe y es usable.
    migratedDatabase.embeddingQueries.upsertEmbedding(
        note_id = "legacy-1",
        dimension = 2L,
        vector = byteArrayOf(0x3F, 0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        indexed_at = 42L,
    )
    assertEquals(
        1,
        migratedDatabase.embeddingQueries.selectAllEmbeddings().executeAsList().size,
        "la tabla embedding debe existir y aceptar filas tras migrar",
    )

    // La tabla `note_link` (nueva en v5) existe y es usable (FK exige 2 notas).
    migratedDatabase.noteQueries.insertNote(
        id = "legacy-2",
        owner = "owner-1",
        body = "otra legacy",
        track = null,
        status = "capturada",
        created_at = 1_700_000_000_000L,
        updated_at = 1_700_000_000_000L,
    )
    migratedDatabase.noteLinkQueries.upsertNoteLink(
        note_a_id = "legacy-1",
        note_b_id = "legacy-2",
        score = 0.9,
        updated_at = 43L,
    )
    assertEquals(
        1,
        migratedDatabase.noteLinkQueries.selectAllLinks().executeAsList().size,
        "la tabla note_link debe existir y aceptar filas tras migrar",
    )

    // La tabla `note_genre` (v7) existe y es usable.
    migratedDatabase.genreQueries.insertNoteGenre(note_id = "legacy-1", genre = "Puzzle")
    assertEquals(
        listOf("Puzzle"),
        migratedDatabase.genreQueries.selectGenresByNoteId("legacy-1").executeAsList(),
        "la tabla note_genre debe existir y aceptar filas tras migrar",
    )
    assertEquals(
        0L,
        tableCount(migratedDriver, "note_tag"),
        "note_tag debe eliminarse al migrar a v7",
    )

    // Bootstrap desde cero produce el mismo esquema final (v7).
    assertEquals(
        noteColumns(migratedDriver),
        noteColumns(freshDriver),
        "las columnas de note deben coincidir entre migración y Schema.create",
    )
    assertEquals(
        noteGenreColumns(migratedDriver),
        noteGenreColumns(freshDriver),
        "las columnas de note_genre deben coincidir entre migración y Schema.create",
    )
    assertEquals(
        embeddingColumns(migratedDriver),
        embeddingColumns(freshDriver),
        "las columnas de embedding deben coincidir entre migración y Schema.create",
    )
    assertEquals(
        embeddingForeignKeys(migratedDriver),
        embeddingForeignKeys(freshDriver),
        "las foreign keys de embedding deben coincidir entre migración y Schema.create",
    )
    assertEquals(
        noteLinkColumns(migratedDriver),
        noteLinkColumns(freshDriver),
        "las columnas de note_link deben coincidir entre migración y Schema.create",
    )
    assertEquals(
        noteLinkForeignKeys(migratedDriver),
        noteLinkForeignKeys(freshDriver),
        "las foreign keys de note_link deben coincidir entre migración y Schema.create",
    )

    // Esquema de `note`: la columna v6 `index_attempts` está presente con default 0.
    val noteColumnsByName = noteColumns(migratedDriver).associateBy { it[1] }
    val indexAttempts = noteColumnsByName["index_attempts"]
    assertNotNull(indexAttempts, "index_attempts debe existir en note tras migrar a v6")
    assertEquals("INTEGER", indexAttempts[2], "index_attempts debe ser INTEGER")
    assertEquals("1", indexAttempts[3], "index_attempts debe ser NOT NULL")
    assertEquals("0", indexAttempts[4], "index_attempts debe tener default 0")

    // Esquema de `note_genre`: PK compuesta `(note_id, genre)`, ambos TEXT.
    val genreColumns = noteGenreColumns(migratedDriver)
    assertEquals(
        listOf("note_id", "genre"),
        genreColumns.map { it[1] },
        "orden y nombres de columnas de note_genre",
    )
    val genreByName = genreColumns.associateBy { it[1] }
    assertEquals("TEXT", genreByName.getValue("note_id")[2], "note_id debe ser TEXT")
    assertEquals("TEXT", genreByName.getValue("genre")[2], "genre debe ser TEXT")
    assertEquals("1", genreByName.getValue("note_id")[5], "note_id es parte de la PK")
    assertEquals("2", genreByName.getValue("genre")[5], "genre es parte de la PK")

    val columns = embeddingColumns(migratedDriver)
    assertEquals(
        listOf("note_id", "dimension", "vector", "indexed_at"),
        columns.map { it[1] },
        "orden y nombres de columnas de embedding",
    )
    val byName = columns.associateBy { it[1] }
    assertEquals("BLOB", byName.getValue("vector")[2], "vector debe ser BLOB")
    assertEquals("1", byName.getValue("note_id")[5], "note_id debe ser PRIMARY KEY")
    assertEquals(
        listOf("note", "note_id", "id", "NO ACTION", "CASCADE"),
        embeddingForeignKeys(migratedDriver).single().let {
            listOf(it[2], it[3], it[4], it[5], it[6])
        },
        "embedding debe referenciar note(id) ON DELETE CASCADE",
    )

    // Esquema de `note_link`: par canónico + PK compuesta + dos FK cascade.
    val linkColumns = noteLinkColumns(migratedDriver)
    assertEquals(
        listOf("note_a_id", "note_b_id", "score", "updated_at"),
        linkColumns.map { it[1] },
        "orden y nombres de columnas de note_link",
    )
    val linkByName = linkColumns.associateBy { it[1] }
    assertEquals("TEXT", linkByName.getValue("note_a_id")[2], "note_a_id debe ser TEXT")
    assertEquals("TEXT", linkByName.getValue("note_b_id")[2], "note_b_id debe ser TEXT")
    assertEquals("REAL", linkByName.getValue("score")[2], "score debe ser REAL")
    assertEquals("1", linkByName.getValue("note_a_id")[5], "note_a_id es parte de la PK")
    assertEquals("2", linkByName.getValue("note_b_id")[5], "note_b_id es parte de la PK")
    assertEquals(
        setOf(
            listOf("note_a_id", "note", "id", "CASCADE"),
            listOf("note_b_id", "note", "id", "CASCADE"),
        ),
        noteLinkForeignKeys(migratedDriver).map { listOf(it[3], it[2], it[4], it[6]) }.toSet(),
        "note_link debe referenciar note(id) ON DELETE CASCADE por ambos lados",
    )
}

private fun noteColumns(driver: SqlDriver): List<List<String?>> =
    pragmaRows(driver, "PRAGMA table_info(note)", columnCount = 6)

private fun noteGenreColumns(driver: SqlDriver): List<List<String?>> =
    pragmaRows(driver, "PRAGMA table_info(note_genre)", columnCount = 6)

private fun tableCount(driver: SqlDriver, name: String): Long = driver.executeQuery(
    identifier = null,
    sql = "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = '$name'",
    mapper = { cursor ->
        cursor.next()
        QueryResult.Value(cursor.getLong(0) ?: 0L)
    },
    parameters = 0,
).value

private fun embeddingColumns(driver: SqlDriver): List<List<String?>> =
    pragmaRows(driver, "PRAGMA table_info(embedding)", columnCount = 6)

private fun embeddingForeignKeys(driver: SqlDriver): List<List<String?>> =
    pragmaRows(driver, "PRAGMA foreign_key_list(embedding)", columnCount = 8)

private fun noteLinkColumns(driver: SqlDriver): List<List<String?>> =
    pragmaRows(driver, "PRAGMA table_info(note_link)", columnCount = 6)

private fun noteLinkForeignKeys(driver: SqlDriver): List<List<String?>> =
    pragmaRows(driver, "PRAGMA foreign_key_list(note_link)", columnCount = 8)

private fun pragmaRows(driver: SqlDriver, sql: String, columnCount: Int): List<List<String?>> =
    driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            val rows = mutableListOf<List<String?>>()
            while (cursor.next().value) {
                rows += List(columnCount) { cursor.getString(it) }
            }
            QueryResult.Value(rows)
        },
        parameters = 0,
    ).value

private const val V1_NOTE_CREATE = """
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
