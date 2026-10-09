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
 * Escenarios 7 de la spec: migración v1 → v2 y bootstrap en ambos caminos.
 *
 * [migratedDriver] debe ser una base **vacía** (sin esquema) sobre la que se
 * aplica el v1 a mano; [freshDriver] debe tener ya el esquema v2 creado desde
 * cero (`Schema.create` en JVM o `inMemoryDriver(Schema)` en iOS). El esquema
 * final (columnas y foreign keys de `embedding`) debe coincidir entre ambos
 * caminos.
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

    PlaybookDatabase.Schema.migrate(migratedDriver, oldVersion = 1L, newVersion = 2L)

    assertEquals(2L, PlaybookDatabase.Schema.version, "PlaybookDatabase.Schema.version debe ser 2")

    // La fila v1 sobrevive a la migración.
    val legacy = migratedDatabase.noteQueries.selectNoteById("legacy-1").executeAsOneOrNull()
    assertNotNull(legacy, "la fila v1 debe sobrevivir a la migración")
    assertEquals("nota legacy", legacy.body)

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

    // Bootstrap desde cero produce el mismo esquema final (v2).
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
}

private fun embeddingColumns(driver: SqlDriver): List<List<String?>> =
    pragmaRows(driver, "PRAGMA table_info(embedding)", columnCount = 6)

private fun embeddingForeignKeys(driver: SqlDriver): List<List<String?>> =
    pragmaRows(driver, "PRAGMA foreign_key_list(embedding)", columnCount = 8)

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
