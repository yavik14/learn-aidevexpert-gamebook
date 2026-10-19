package com.playbook.core.repository

import app.cash.sqldelight.db.SqlDriver
import com.playbook.core.db.PlaybookDatabase
import com.playbook.core.model.GAME_PROJECT_SINGLETON_ID
import com.playbook.core.model.GameProjectDraft
import com.playbook.core.model.KeyMechanic
import com.playbook.core.model.MechanicCategory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Ejercita [SqlDelightGameProjectRepository] sobre un [SqlDriver] ya
 * inicializado con el esquema v8. Cubre los escenarios 1–9 de la spec y vive en
 * `commonTest` para correr igual en Android/JVM e iOS.
 */
fun verifyGameProject(driver: SqlDriver) {
    val database = PlaybookDatabase(driver)
    var now = 1_700_000_000_000L
    val repository = SqlDelightGameProjectRepository(database) { now }

    // --- Scenario 1: instalación sin Proyecto ------------------------------
    assertNull(repository.get(), "sin Proyecto, get() devuelve null")
    assertEquals(
        0L,
        database.gameProjectQueries.countGameProjects().executeAsOne(),
        "no hay filas de Proyecto al inicio",
    )

    // --- Scenario 2: crear un Proyecto vacío -------------------------------
    val empty = repository.save(GameProjectDraft(owner = " local "))
    assertEquals(GAME_PROJECT_SINGLETON_ID, empty.id)
    assertEquals("local", empty.owner, "owner se normaliza con trim")
    assertNull(empty.title)
    assertEquals(emptyList(), empty.genres)
    assertNull(empty.coreLoop)
    assertNull(empty.audience)
    assertNull(empty.concept)
    assertEquals(emptyList(), empty.inspirations)
    assertNull(empty.fantasy)
    assertEquals(emptyList(), empty.keyMechanics)
    assertEquals(emptyList(), empty.outOfScope)
    assertEquals(now, empty.createdAt)
    assertEquals(now, empty.updatedAt, "createdAt y updatedAt arrancan iguales")
    assertEquals(empty, repository.get(), "get() recupera el Proyecto vacío")
    assertEquals(
        1L,
        database.gameProjectQueries.countGameProjects().executeAsOne(),
        "el singleton tiene una sola fila",
    )

    // --- Scenario 3 + 4: ficha completa y normalización de Géneros ---------
    now += 1_000
    val fullDraft = GameProjectDraft(
        owner = "local",
        title = " Proyecto Aurora ",
        genres = listOf(" platformer ", "jrpg", "JRPG", "Zelda-like"),
        coreLoop = " Explorar y resolver ",
        audience = " Jugadores de aventura ",
        concept = " Metroidvania narrativo ",
        inspirations = listOf("Hollow Knight", " Celeste "),
        fantasy = " Sentirse explorador ",
        keyMechanics = listOf(
            KeyMechanic(MechanicCategory.MOVEMENT, " Dash "),
            KeyMechanic(MechanicCategory.MOVEMENT, "Wall jump"),
            KeyMechanic(MechanicCategory.INTERACTION, "Abrir puertas"),
            KeyMechanic(MechanicCategory.INTERACTION, "Activar palancas"),
            KeyMechanic(MechanicCategory.CONFLICT, "Combatir jefes"),
        ),
        outOfScope = listOf("Multijugador", " Editor de niveles "),
    )
    val saved = repository.save(fullDraft)
    assertEquals("Proyecto Aurora", saved.title)
    assertEquals(listOf("JRPG", "Platformer", "Zelda-like"), saved.genres)
    assertEquals("Explorar y resolver", saved.coreLoop)
    assertEquals("Jugadores de aventura", saved.audience)
    assertEquals("Metroidvania narrativo", saved.concept)
    assertEquals(listOf("Hollow Knight", "Celeste"), saved.inspirations)
    assertEquals("Sentirse explorador", saved.fantasy)
    assertEquals(
        listOf(
            KeyMechanic(MechanicCategory.MOVEMENT, "Dash"),
            KeyMechanic(MechanicCategory.MOVEMENT, "Wall jump"),
            KeyMechanic(MechanicCategory.INTERACTION, "Abrir puertas"),
            KeyMechanic(MechanicCategory.INTERACTION, "Activar palancas"),
            KeyMechanic(MechanicCategory.CONFLICT, "Combatir jefes"),
        ),
        saved.keyMechanics,
    )
    assertEquals(listOf("Multijugador", "Editor de niveles"), saved.outOfScope)
    assertEquals("local", saved.owner)
    assertEquals(empty.createdAt, saved.createdAt, "el upsert preserva createdAt")
    assertEquals(now, saved.updatedAt)
    assertEquals(saved, repository.get(), "la ficha completa round-tripea")

    // --- Scenario 5: update reemplaza las colecciones ----------------------
    now += 1_000
    val updated = repository.save(
        GameProjectDraft(
            owner = "local",
            title = "Proyecto Aurora 2",
            genres = listOf("Puzzle"),
            inspirations = listOf("Una"),
            keyMechanics = listOf(KeyMechanic(MechanicCategory.CONFLICT, "Puzzle final")),
            outOfScope = emptyList(),
        ),
    )
    assertEquals(saved.createdAt, updated.createdAt, "update preserva createdAt")
    assertEquals(now, updated.updatedAt, "update avanza updatedAt")
    assertEquals(listOf("Puzzle"), updated.genres)
    assertEquals(listOf("Una"), updated.inspirations)
    assertEquals(listOf(KeyMechanic(MechanicCategory.CONFLICT, "Puzzle final")), updated.keyMechanics)
    assertEquals(emptyList(), updated.outOfScope)
    assertEquals(updated, repository.get(), "get() refleja el reemplazo completo")
    assertEquals(
        1L,
        database.gameProjectQueries.countGameProjects().executeAsOne(),
        "el update no duplica el singleton",
    )
    assertEquals(
        1,
        database.gameProjectQueries.selectGameProjectInspirations(GAME_PROJECT_SINGLETON_ID)
            .executeAsList().size,
        "no quedan inspiraciones huérfanas",
    )
    assertEquals(
        1,
        database.gameProjectQueries.selectGameProjectKeyMechanics(GAME_PROJECT_SINGLETON_ID)
            .executeAsList().size,
        "no quedan mecánicas huérfanas",
    )
    assertEquals(
        0,
        database.gameProjectQueries.selectGameProjectOutOfScope(GAME_PROJECT_SINGLETON_ID)
            .executeAsList().size,
        "el fuera de alcance vacío limpia las filas previas",
    )

    // --- Scenario 6: opcionales y blanks -----------------------------------
    now += 1_000
    val blanks = repository.save(
        GameProjectDraft(
            owner = "local",
            title = "   ",
            genres = listOf("", "   "),
            coreLoop = "  ",
            audience = "\t",
            concept = "",
            inspirations = listOf(" Uno ", "", "Dos"),
            fantasy = " ",
            keyMechanics = listOf(
                KeyMechanic(MechanicCategory.MOVEMENT, "  "),
                KeyMechanic(MechanicCategory.CONFLICT, " Golpe "),
            ),
            outOfScope = listOf("", "Fuera"),
        ),
    )
    assertNull(blanks.title)
    assertEquals(emptyList(), blanks.genres)
    assertNull(blanks.coreLoop)
    assertNull(blanks.audience)
    assertNull(blanks.concept)
    assertEquals(listOf("Uno", "Dos"), blanks.inspirations)
    assertNull(blanks.fantasy)
    assertEquals(listOf(KeyMechanic(MechanicCategory.CONFLICT, "Golpe")), blanks.keyMechanics)
    assertEquals(listOf("Fuera"), blanks.outOfScope)

    // --- Scenario 7: límites de cardinalidad -------------------------------
    val beforeLimits = repository.get()
    assertFailsWith<IllegalArgumentException> {
        repository.save(
            GameProjectDraft(owner = "local", inspirations = listOf("a", "b", "c", "d")),
        )
    }
    assertEquals(beforeLimits, repository.get(), "un límite inválido no escribe nada")
    assertFailsWith<IllegalArgumentException> {
        repository.save(
            GameProjectDraft(owner = "local", outOfScope = listOf("a", "b", "c", "d")),
        )
    }
    assertEquals(beforeLimits, repository.get(), "outOfScope inválido no escribe nada")
    assertFailsWith<IllegalArgumentException> {
        repository.save(
            GameProjectDraft(
                owner = "local",
                keyMechanics = (0..5).map {
                    KeyMechanic(MechanicCategory.MOVEMENT, "mecánica $it")
                },
            ),
        )
    }
    assertEquals(beforeLimits, repository.get(), "keyMechanics inválidas no escriben nada")

    // --- Scenario 8: categorías de mecánica --------------------------------
    now += 1_000
    val mechanics = listOf(
        KeyMechanic(MechanicCategory.MOVEMENT, "Mover"),
        KeyMechanic(MechanicCategory.INTERACTION, "Interactuar"),
        KeyMechanic(MechanicCategory.CONFLICT, "Combatir"),
    )
    val categorized = repository.save(
        GameProjectDraft(owner = "local", keyMechanics = mechanics),
    )
    assertEquals(mechanics, categorized.keyMechanics, "las tres categorías round-tripean")

    // --- Scenario 9: invariante de owner -----------------------------------
    val beforeOwner = repository.get()
    assertFailsWith<IllegalArgumentException> {
        repository.save(GameProjectDraft(owner = "otro", title = "no debe persistir"))
    }
    assertEquals(beforeOwner, repository.get(), "un owner distinto no cambia el Proyecto")

    // --- Orden de Géneros personalizados con mayúsculas mezcladas ----------
    now += 1_000
    val mixedCase = repository.save(
        GameProjectDraft(owner = "local", genres = listOf("apple", "Banana")),
    )
    assertEquals(listOf("apple", "Banana"), mixedCase.genres)
    assertEquals(mixedCase, repository.get(), "el orden de Géneros round-tripea")

    // Lectura fail-fast de una categoría desconocida insertada directamente.
    database.gameProjectQueries.deleteGameProjectKeyMechanics(GAME_PROJECT_SINGLETON_ID)
    database.gameProjectQueries.insertGameProjectKeyMechanic(
        project_id = GAME_PROJECT_SINGLETON_ID,
        position = 0,
        category = "desconocido",
        description = "corrupta",
    )
    assertFailsWith<IllegalArgumentException> {
        repository.get()
    }
    database.gameProjectQueries.deleteGameProjectKeyMechanics(GAME_PROJECT_SINGLETON_ID)
}
