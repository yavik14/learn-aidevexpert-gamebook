package com.playbook.core.repository

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.model.GenreCatalog
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.Track
import com.playbook.core.model.normalizeGenres
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Esquema sintético v1 (sólo la tabla `note`, sin `note_tag`/`note_genre`) para
 * simular una base existente anterior a esta feature. El `create` replica
 * exactamente el `Note.sq` de `v1`.
 */
object NoteV1Schema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE note (
                  id TEXT NOT NULL PRIMARY KEY,
                  owner TEXT NOT NULL,
                  body TEXT NOT NULL,
                  track TEXT,
                  status TEXT NOT NULL,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL
                )
            """.trimIndent(),
            parameters = 0,
        )
        return QueryResult.Unit
    }

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Unit
}

/**
 * Ejercita los Géneros de [SqlDelightNoteRepository] sobre un [SqlDriver] ya
 * inicializado con el esquema v7 (tablas `note` y `note_genre`).
 *
 * Cubre los escenarios 1–5 de la spec: catálogo fijo + canonización
 * case-insensitive, crear/leer con Géneros de catálogo y personalizados, update
 * que reemplaza el conjunto sin tocar `track`/`status`, N—N por Nota
 * (personalizados no globalizados) y borrado sin Géneros huérfanos.
 *
 * Vive en `commonTest` para correr el mismo test en Android/JVM e iOS con el
 * driver in-memory de cada plataforma. `idFactory`/`clock` son fakes
 * deterministas.
 */
fun verifyGenres(driver: SqlDriver) {
    val database = PlaybookDatabase(driver)
    var sequence = 0
    var now = 1_700_000_000_000L
    val repository = SqlDelightNoteRepository(
        database = database,
        idFactory = { "note-${sequence++}" },
        clock = { now },
    )

    // --- Scenario 1: catálogo fijo + canonización case-insensitive ---------
    assertEquals(
        listOf(
            "Platformer", "Metroidvania", "JRPG", "ActionRPG", "Roguelike", "Shooter",
            "Puzzle", "Adventure", "Towerdefense", "Novel", "Farming", "Arcade",
        ),
        GenreCatalog.GENRES,
        "el catálogo fijo tiene exactamente 12 Géneros",
    )
    assertEquals(
        listOf("JRPG", "Mi género", "Platformer"),
        normalizeGenres(listOf(" platformer ", "JRPG", "jrpg", "Mi género")),
        "trim, canoniza platformer→Platformer, deduplica jrpg/JRPG conservando la grafía canónica, mantiene el personalizado y ordena case-insensitive",
    )
    assertEquals(emptyList(), normalizeGenres(emptyList()), "sin Géneros")
    assertEquals(
        emptyList(),
        normalizeGenres(listOf("", "   ", "\t")),
        "los Géneros vacíos o sólo con espacios se descartan",
    )
    assertEquals(
        listOf("Metroidvania", "Roguelike"),
        normalizeGenres(listOf(" metroidvania ", "ROGUELIKE", "Roguelike")),
        "canoniza la grafía del catálogo y deduplica case-insensitive",
    )
    assertEquals(
        listOf("Mi género", "Zelda-like"),
        normalizeGenres(listOf("Zelda-like", "Mi género", " zelda-like ")),
        "un personalizado conserva su grafía y deduplica case-insensitive",
    )

    // --- Scenario 2: crear/leer una Nota con Géneros -----------------------
    val withGenres = repository.create(
        NoteDraft(
            owner = "owner-1",
            body = "Diseñar el jefe final",
            track = Track.CHARACTERS,
            genres = listOf("Metroidvania", "roguelike", "Zelda-like"),
        ),
    )
    assertEquals(
        listOf("Metroidvania", "Roguelike", "Zelda-like"),
        withGenres.genres,
        "create canoniza el de catálogo y conserva el personalizado",
    )
    assertEquals(withGenres, repository.getById(withGenres.id), "getById recupera la Nota con sus Géneros")
    assertEquals(
        withGenres.genres,
        repository.getAll().first { it.id == withGenres.id }.genres,
        "getAll expone los Géneros agrupados",
    )

    // --- Scenario 3: update reemplaza el conjunto; `Track` intacto --------
    val frozenUpdatedAt = withGenres.updatedAt
    now += 1_000
    assertTrue(repository.update(withGenres.copy(genres = listOf("Puzzle"))))
    val updated = repository.getById(withGenres.id)
    assertEquals(listOf("Puzzle"), updated?.genres, "update reemplaza el conjunto de Géneros")
    assertTrue(updated!!.updatedAt > frozenUpdatedAt, "update avanza updatedAt")
    assertEquals(Track.CHARACTERS, updated.track, "el track no cambia al editar Géneros")
    assertEquals(NoteStatus.CAPTURED, updated.status, "el status no cambia al editar Géneros")

    // --- Scenario 4: N—N por Nota; personalizados no se globalizan ---------
    val noteA = repository.create(NoteDraft("owner-1", "A", null, genres = listOf("Zelda-like")))
    val noteB = repository.create(NoteDraft("owner-1", "B", null, genres = listOf("Zelda-like")))
    assertEquals(listOf("Zelda-like"), repository.getById(noteA.id)?.genres)
    assertEquals(listOf("Zelda-like"), repository.getById(noteB.id)?.genres)
    now += 1_000
    repository.update(noteA.copy(genres = listOf("solo-a")))
    assertEquals(listOf("solo-a"), repository.getById(noteA.id)?.genres)
    assertEquals(listOf("Zelda-like"), repository.getById(noteB.id)?.genres, "quitar en A no afecta a B")
    assertEquals(
        setOf("solo-a", "Zelda-like"),
        database.genreQueries.selectAllNoteGenres().executeAsList()
            .filter { it.note_id == noteA.id || it.note_id == noteB.id }
            .map { it.genre }
            .toSet(),
        "selectAllNoteGenres expone los Géneros por Nota sin materializar un catálogo global",
    )

    // --- Scenario 5: borrar la Nota limpia sus Géneros ---------------------
    assertTrue(repository.delete(noteB.id))
    assertNull(repository.getById(noteB.id))
    assertEquals(
        0,
        database.genreQueries.selectGenresByNoteId(noteB.id).executeAsList().size,
        "no quedan Géneros huérfanos tras borrar la Nota",
    )

    // --- update de un id inexistente no crea Géneros huérfanos -------------
    assertFalse(
        repository.update(withGenres.copy(id = "no-existe", genres = listOf("fantasma"))),
    )
    assertEquals(
        0,
        database.genreQueries.selectGenresByNoteId("no-existe").executeAsList().size,
        "un update inexistente no persiste Géneros",
    )

    // --- getAll agrupa los Géneros por Nota --------------------------------
    val byId = repository.getAll().associateBy { it.id }
    assertEquals(listOf("Puzzle"), byId[withGenres.id]?.genres)
    assertEquals(listOf("solo-a"), byId[noteA.id]?.genres)
}

/**
 * Verifica la migración real v1 -> v8 (aplica todos los `.sqm` del repo). El
 * [driver] debe representar una base **v1** ya existente (tabla `note` sin
 * `note_tag`); los tests de plataforma la crean con [NoteV1Schema]:
 *
 * - Android/JVM: `JdbcSqliteDriver.IN_MEMORY` + `NoteV1Schema.create(driver)`.
 * - iOS: `inMemoryDriver(NoteV1Schema)`.
 *
 * Siembra una fila v1, corre `Schema.migrate(driver, 1, 8)` y comprueba que la
 * Nota sobrevive, que la tabla `note_genre` queda operable y que `note_tag` ya no
 * existe. La v8 (Proyecto) es aditiva y no afecta a esta verificación.
 */
fun verifyGenreMigration(driver: SqlDriver) {
    val v1Database = PlaybookDatabase(driver)
    v1Database.noteQueries.insertNote(
        id = "note-v1",
        owner = "owner-1",
        body = "nota previa",
        track = "historia",
        status = "capturada",
        created_at = 100L,
        updated_at = 100L,
    )
    driver.execute(identifier = null, sql = "PRAGMA user_version = 1", parameters = 0).value

    assertEquals(8L, PlaybookDatabase.Schema.version, "con 1.sqm..7.sqm el esquema debe ser v8")

    // Corre la migración real v1 -> v8 (aplica 1.sqm..7.sqm).
    PlaybookDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 8)

    val database = PlaybookDatabase(driver)
    val migrated = database.noteQueries.selectNoteById("note-v1").executeAsOneOrNull()
    assertEquals("note-v1", migrated?.id, "la Nota v1 sobrevive a la migración")
    assertEquals("owner-1", migrated?.owner)
    assertEquals("nota previa", migrated?.body, "el body v1 se preserva")
    assertEquals("historia", migrated?.track, "el track v1 se preserva")
    assertEquals("capturada", migrated?.status, "el status v1 se preserva")
    assertEquals(100L, migrated?.created_at)
    assertEquals(100L, migrated?.updated_at)

    // `note_tag` (v2) fue reemplazada por `note_genre` (v7).
    assertEquals(
        0L,
        tableCount(driver, "note_tag"),
        "note_tag debe eliminarse al migrar a v7",
    )

    // La tabla nueva existe, arranca vacía para los datos viejos y es operable.
    assertTrue(
        database.genreQueries.selectGenresByNoteId("note-v1").executeAsList().isEmpty(),
        "una Nota v1 arranca sin Géneros",
    )
    database.genreQueries.insertNoteGenre(note_id = "note-v1", genre = "migrado")
    assertEquals(
        listOf("migrado"),
        database.genreQueries.selectGenresByNoteId("note-v1").executeAsList(),
        "la tabla note_genre queda operativa tras migrar",
    )
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
