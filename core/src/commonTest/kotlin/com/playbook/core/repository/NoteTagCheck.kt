package com.playbook.core.repository

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.model.NoteDraft
import com.playbook.core.model.Track
import com.playbook.core.model.normalizeTags
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Esquema sintético v1 (sólo la tabla `note`, sin `note_tag`) para simular una
 * base existente anterior a esta feature. El `create` replica exactamente el
 * `Note.sq` de `v1`.
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
 * Ejercita las Etiquetas libres de [SqlDelightNoteRepository] sobre un [SqlDriver]
 * ya inicializado con el esquema v2 (tablas `note` y `note_tag`).
 *
 * Vive en `commonTest` para correr el mismo test en Android/JVM e iOS con el
 * driver in-memory de cada plataforma. `idFactory`/`clock` son fakes
 * deterministas.
 */
fun verifyNoteTags(driver: SqlDriver) {
    val database = PlaybookDatabase(driver)
    var sequence = 0
    var now = 1_700_000_000_000L
    val repository = SqlDelightNoteRepository(
        database = database,
        idFactory = { "note-${sequence++}" },
        clock = { now },
    )

    // --- normalizeTags: trim, vacías, dedupe case-insensitive y orden ------
    assertEquals(emptyList(), normalizeTags(emptyList()), "sin etiquetas")
    assertEquals(
        emptyList(),
        normalizeTags(listOf("", "   ", "\t")),
        "las etiquetas vacías o sólo con espacios se descartan",
    )
    assertEquals(
        listOf("Arte", "jefe"),
        normalizeTags(listOf(" jefe ", "Arte", "JEFE", "arte")),
        "trim + dedupe case-insensitive conserva la primera grafía + orden case-insensitive",
    )
    assertEquals(
        listOf("boss", "Nivel 2"),
        normalizeTags(listOf("boss", "Nivel 2", "BOSS")),
        "dedupe case-insensitive y orden determinista",
    )

    // --- Scenario 1: crear con etiquetas normalizadas ----------------------
    val withTags = repository.create(
        NoteDraft(
            owner = "owner-1",
            body = "Diseñar el jefe final",
            track = Track.CHARACTERS,
            tags = listOf(" jefe ", "Boss", "JEFE"),
        ),
    )
    assertEquals(listOf("Boss", "jefe"), withTags.tags, "create normaliza las etiquetas")
    assertEquals(withTags, repository.getById(withTags.id), "getById recupera la nota con sus etiquetas")
    assertEquals(
        withTags.tags,
        repository.getAll().first { it.id == withTags.id }.tags,
        "getAll expone las etiquetas agrupadas",
    )

    // --- Scenario 2: update reemplaza el conjunto completo -----------------
    val frozenUpdatedAt = withTags.updatedAt
    now += 1_000
    assertTrue(repository.update(withTags.copy(tags = listOf("nuevo", "jefe"))))
    val updated = repository.getById(withTags.id)
    assertEquals(listOf("jefe", "nuevo"), updated?.tags, "update reemplaza el conjunto de etiquetas")
    assertTrue(updated!!.updatedAt > frozenUpdatedAt, "update avanza updatedAt")
    assertEquals(Track.CHARACTERS, updated.track, "el track no cambia al editar etiquetas")

    // --- Scenario 3: N—N; quitar en una nota no afecta a la otra ----------
    val noteA = repository.create(NoteDraft("owner-1", "A", null, tags = listOf("jefe")))
    val noteB = repository.create(NoteDraft("owner-1", "B", null, tags = listOf("jefe")))
    assertEquals(listOf("jefe"), repository.getById(noteA.id)?.tags)
    assertEquals(listOf("jefe"), repository.getById(noteB.id)?.tags)
    now += 1_000
    repository.update(noteA.copy(tags = listOf("solo-a")))
    assertEquals(listOf("solo-a"), repository.getById(noteA.id)?.tags)
    assertEquals(listOf("jefe"), repository.getById(noteB.id)?.tags, "quitar en A no afecta a B")

    // --- Scenario 4: borrar la nota limpia sus etiquetas -------------------
    assertTrue(repository.delete(noteB.id))
    assertNull(repository.getById(noteB.id))
    assertEquals(
        0,
        database.noteTagQueries.selectTagsByNoteId(noteB.id).executeAsList().size,
        "no quedan etiquetas huérfanas tras borrar la nota",
    )

    // --- update de un id inexistente no crea etiquetas huérfanas ----------
    assertFalse(
        repository.update(withTags.copy(id = "no-existe", tags = listOf("fantasma"))),
    )
    assertEquals(
        0,
        database.noteTagQueries.selectTagsByNoteId("no-existe").executeAsList().size,
        "un update inexistente no persiste etiquetas",
    )

    // --- getAll agrupa las etiquetas por nota -----------------------------
    val byId = repository.getAll().associateBy { it.id }
    assertEquals(listOf("jefe", "nuevo"), byId[withTags.id]?.tags)
    assertEquals(listOf("solo-a"), byId[noteA.id]?.tags)
}

/**
 * Verifica la migración real v1 -> v2 (primer `.sqm` del repo). El [driver] debe
 * representar una base **v1** ya existente (tabla `note` sin `note_tag`); los
 * tests de plataforma la crean con [NoteV1Schema]:
 *
 * - Android/JVM: `JdbcSqliteDriver.IN_MEMORY` + `NoteV1Schema.create(driver)`.
 * - iOS: `inMemoryDriver(NoteV1Schema)`.
 *
 * Siembra una fila v1, corre `Schema.migrate(driver, 1, 2)` y comprueba que la
 * nota sobrevive y que la tabla nueva `note_tag` queda operable.
 */
fun verifyNoteTagMigration(driver: SqlDriver) {
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

    // Corre la migración real v1 -> v2.
    PlaybookDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2)

    val database = PlaybookDatabase(driver)
    val migrated = database.noteQueries.selectNoteById("note-v1").executeAsOneOrNull()
    assertEquals("note-v1", migrated?.id, "la nota v1 sobrevive a la migración")
    assertEquals("owner-1", migrated?.owner)
    assertEquals("nota previa", migrated?.body, "el body v1 se preserva")
    assertEquals("historia", migrated?.track, "el track v1 se preserva")
    assertEquals("capturada", migrated?.status, "el status v1 se preserva")
    assertEquals(100L, migrated?.created_at)
    assertEquals(100L, migrated?.updated_at)

    // La tabla nueva existe, arranca vacía para los datos viejos y es operable.
    assertTrue(
        database.noteTagQueries.selectTagsByNoteId("note-v1").executeAsList().isEmpty(),
        "una nota v1 arranca sin etiquetas",
    )
    database.noteTagQueries.insertNoteTag(note_id = "note-v1", label = "migrado")
    assertEquals(
        listOf("migrado"),
        database.noteTagQueries.selectTagsByNoteId("note-v1").executeAsList(),
        "la tabla note_tag queda operativa tras migrar",
    )
}
