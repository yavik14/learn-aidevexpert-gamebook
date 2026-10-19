# Feature Implementation Spec: Persistencia del Proyecto de Juego (singleton)

## Source Feature

- `id`: `game-project-persistence`
- `area`: `persistence`
- `depends_on`: `["local-persistence-sqldelight", "genres-catalog-persistence"]`
  (ambas `accepted`)
- `status`: `not_started` (al planificar)
- `source`: `feature_list.json`

## Goal

Que `:core` pueda **guardar y leer el Proyecto de Juego único** (singleton por
instalación) con su ficha: título, Géneros, Core Loop, Audiencia, Concepto,
Inspiraciones (≤3), Fantasía, Mecánicas clave (≤5, con categoría y descripción) y
Fuera de alcance (≤3). Se usa una tabla `game_project` + tablas hijas y el esquema
**v8** (`7.sqm` v7→v8).

La feature es **sólo dominio + persistencia**: sin UI, wizard, onboarding, GDD
híbrido ni wiring. Los Géneros reutilizan `GenreCatalog`/`normalizeGenres`; no
hay `note.project_id` ni duplicación de Notas.

## Non-Goals

- UI del Proyecto, wizard de Brainstorming, onboarding, GDD híbrido o export.
- Wiring de `GameProjectRepository` en `App`/`MainActivity`/
  `MainViewController`.
- Múltiples Proyectos, Banco de ideas, borrado/reset (`delete()`).
- `note.project_id` o FK Nota→Proyecto: pertenencia implícita al singleton.
- Estado persistido `vacío`/`esbozado`: se deriva aguas abajo.
- IA, gestión global de Géneros o materializar el catálogo en la base.
- Migración de datos hacia una fila de Proyecto.
- Dependencias nuevas o cambios en `init.sh`, `composeApp`, `iosApp` o build
  files.

## Job Story

When empiezo o itero el diseño de mi juego,
I want que el core guarde y lea la ficha completa del Proyecto en un único lugar,
so I can reutilizarla desde el Brainstorming, el GDD y el export sin duplicar
datos.

## Users And Permissions

- **Autor** (single-user local-first): dueño del Proyecto y sus Notas. Sin auth.
- **Desarrollador**: corre Gradle/tests. Sin secrets ni red.

## Scope Decisions

> Resueltas con el autor (2026-10-19) antes de implementar.

1. **Esquema v8 aditivo.** `7.sqm` v7→v8 crea las tablas del Proyecto; el
   esquema v7 (Géneros) no cambia. `Schema.version` pasa a **8**.
2. **Modelo relacional, no JSON.** `game_project` + `game_project_genre`,
   `game_project_inspiration`, `game_project_key_mechanic` y
   `game_project_out_of_scope`. Conserva orden y permite constraints.
3. **Singleton.** `game_project.id` es siempre `"singleton"` con
   `CHECK (id = 'singleton')`; como máximo una fila. `owner` se persiste y se
   preserva en updates.
4. **Ficha y cardinalidad.** Todos los campos son opcionales: escalares
   `String?`; Géneros múltiples; inspiraciones `0..3`; fuera de alcance `0..3`;
   mecánicas `0..5`. Se guardan sólo las entradas completas.
5. **Géneros reutilizan el dominio.** `GameProject.genres` usa
   `normalizeGenres` (trim, vacíos, canonización contra `GenreCatalog`, dedupe
   case-insensitive y orden).
6. **Owner.** Obligatorio y con `trim`. Se usa al crear; en updates, otro owner
   lanza `IllegalArgumentException` sin escribir. `createdAt` se preserva.
7. **API mínima.** `GameProjectRepository { get(): GameProject?;
   save(draft): GameProject }`; `save` es un **upsert** del singleton. Sin
   `delete()`.
8. **Sin estado persistido.** `get() == null` = sin Proyecto; ficha sin campos =
   `vacío`; con al menos un campo = `esbozado`.
9. **Normalización de texto.** Escalares: `trim`; vacío → `null`. Listas:
   `trim`, descartar vacíos y conservar orden (sin dedupe, salvo Géneros).
   Límites validados **después** de normalizar.
10. **Sin FK Nota→Proyecto y sin UI/wiring.** El repositorio queda listo para
    `brainstorming-wizard`, `first-run-project-onboarding`,
    `gdd-document-hybrid` y `gdd-export-hybrid`.

## Acceptance Scenarios

### Scenario 1: instalación sin Proyecto

Given una base v8 recién creada, When se llama `get()`, Then devuelve `null` y
`countGameProjects() == 0`; las tablas del Proyecto existen.

### Scenario 2: crear un Proyecto vacío

Given una base sin Proyecto, When `save(GameProjectDraft(owner = " local "))`,
Then se crea el singleton con `id = "singleton"`, `owner = "local"`, escalares
`null`, listas vacías y `createdAt == updatedAt`; `get()` lo recupera y sigue
habiendo una sola fila.

### Scenario 3: round-trip de la ficha completa

Given una ficha con todos los campos (5 mecánicas incluidas), When se guarda y
relee, Then todo coincide; las listas conservan su orden y cada mecánica
conserva categoría + descripción.

### Scenario 4: Géneros de catálogo + personalizados

Given `genres = [" platformer ", "jrpg", "JRPG", "Zelda-like"]`, When se guarda,
Then `get().genres == ["JRPG", "Platformer", "Zelda-like"]` (mismo contrato que
en las Notas).

### Scenario 5: update reemplaza las colecciones y preserva identidad

Given un Proyecto guardado, When se hace un segundo `save` con otras listas,
Then las filas hijas anteriores se reemplazan sin huérfanos, `owner`/`createdAt`
se preservan, `updatedAt` avanza y sigue existiendo una sola fila.

### Scenario 6: opcionales y blanks

Given un draft con título `"   "`, listas con entradas vacías y una mecánica con
descripción en blanco, When se guarda, Then el título queda `null`, se descartan
los vacíos, la mecánica incompleta no se persiste y se permiten menos de 3/5
elementos.

### Scenario 7: límites de cardinalidad

Given listas normalizadas con más de 3 inspiraciones, más de 3 “fuera de
alcance” o más de 5 mecánicas, When se llama `save`, Then lanza
`IllegalArgumentException` y el estado persistido no cambia.

### Scenario 8: categorías de mecánica

Given mecánicas con `Movimiento`, `Interacción` y `Conflicto`, When se guardan y
releen, Then cada categoría round-tripea por su `code`. Un `code` desconocido
insertado directamente en la base hace fallar la lectura (`fromCode` fail-fast).

### Scenario 9: invariante de owner

Given un Proyecto con `owner = "local"`, When se intenta `save` con otro owner,
Then lanza `IllegalArgumentException` y la ficha persistida no cambia.

### Scenario 10: migración v7 → v8

Given una base v7 con Notas, Géneros, Adjuntos, Enlaces y Embeddings, When se
migra a v8, Then los datos sobreviven intactos, las tablas del Proyecto se crean
**vacías**, `Schema.version == 8` y sólo aparece `7.sqm` nuevo.

### Scenario 11: bootstrap fresco = esquema migrado

Given una base creada con `Schema.create` (v8) y otra migrada v1→v8, When se
comparan columnas, PKs y FKs de `game_project` y sus hijas, Then coinciden.

### Scenario 12: gate honesto

Given los escenarios 1–11, When corren `:core:testDebugUnitTest`,
`:composeApp:assembleDebug` y `./init.sh`, Then todo pasa en Android/JVM sin red
ni deps nuevas, y `./init.sh` termina en `0`.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`,
  `docs/mvp-execution-plan.md` — Android-first, iOS congelado, gate
  `./init.sh`; esquema v8 reservado a esta feature.
- `CONTEXT.md`, `docs/domain-model.md`, `docs/build-brief.md`,
  `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`,
  `CONSTRAINTS.md`, `DESIGN.md`, `docs/adr/0003-ios-congelado-mvp.md`.
- `docs/specs/genres-catalog-persistence.md` — precedente de esquema v7,
  migración espejada, repositorio, tests y docs durables.
- `core/.../sqldelight/.../{Note.sq, Genre.sq, 1.sqm..6.sqm, Attachment.sq,
  NoteLink.sq, embedding.sq}` — esquema actual v7 y patrones DDL/queries.
- `core/.../model/{Note,NoteDraft,Genres,NoteMappers,Track,NoteStatus}.kt` —
  modelo, normalización de Géneros y enums con `code`/`fromCode`.
- `core/.../repository/{NoteRepository,SqlDelightNoteRepository,
  AttachmentRepository,SqlDelightAttachmentRepository}.kt` — contratos,
  transacciones y reemplazo de relaciones.
- `core/src/commonTest/.../{repository/GenreCheck, repository/NoteRepositoryCheck,
  db/MigrationCheck, db/DatabaseMigrationCheck}.kt` y runners — helpers sobre
  `SqlDriver`, migración v1→v7, comparación bootstrap/migración.
- `composeApp/.../App.kt` (`LOCAL_OWNER_ID = "local"` privado, sin repositorio de
  Proyecto) e `init.sh` (gate Android-only, sin dev servers ni simuladores).

### Existing Patterns To Follow

- `:core` sin DI: dependencias por constructor; `clock` inyectado; mutaciones en
  `database.transaction { }` / `transactionWithResult { }`.
- `.sq` describe el esquema más reciente; `.sqm` hace upgrade con DDL
  **espejado**. La equivalencia se prueba con `DatabaseMigrationCheck` (el repo
  no usa `verifySqlDelightMigration`).
- Tests compartidos en `commonTest` sobre `SqlDriver`; runners Android/JVM
  (`JdbcSqliteDriver.IN_MEMORY` + `Schema.create`) e iOS
  (`inMemoryDriver(PlaybookDatabase.Schema)`).
- `createDatabase(...)` ejecuta `PRAGMA foreign_keys = ON`; las FKs de las hijas
  cascadean en la app real.
- Mappers en archivo aparte con alias de la fila generada
  (`Game_project as GameProjectRow`). Lecturas ordenadas por `genre` o
  `position`.

### Current Gaps

- No existe modelo, repositorio, tabla ni spec del Proyecto.
- `PlaybookDatabase.Schema.version` es **7**; no existe `7.sqm`.
- Varios tests afirman `Schema.version == 7` y migran hasta 7; deben actualizarse
  a 8 (`GenreCheck`, `MigrationCheck`, `DatabaseMigrationCheck` y runners).
- El dominio ya define la ficha y el singleton, pero no su persistencia.

## Technical Approach

### 1. Dominio (`:core`)

`core/src/commonMain/kotlin/com/playbook/core/model/GameProject.kt`:

```kotlin
const val GAME_PROJECT_SINGLETON_ID = "singleton"

data class GameProject(
    val id: String = GAME_PROJECT_SINGLETON_ID,
    val owner: String,
    val title: String? = null,
    val genres: List<String> = emptyList(),
    val coreLoop: String? = null,
    val audience: String? = null,
    val concept: String? = null,
    val inspirations: List<String> = emptyList(),
    val fantasy: String? = null,
    val keyMechanics: List<KeyMechanic> = emptyList(),
    val outOfScope: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)

data class GameProjectDraft(
    val owner: String,
    val title: String? = null,
    val genres: List<String> = emptyList(),
    val coreLoop: String? = null,
    val audience: String? = null,
    val concept: String? = null,
    val inspirations: List<String> = emptyList(),
    val fantasy: String? = null,
    val keyMechanics: List<KeyMechanic> = emptyList(),
    val outOfScope: List<String> = emptyList(),
)

data class KeyMechanic(val category: MechanicCategory, val description: String)

enum class MechanicCategory(val code: String) {
    MOVEMENT("movimiento"),
    INTERACTION("interacción"),
    CONFLICT("conflicto"),
    ;
    companion object {
        fun fromCode(code: String): MechanicCategory =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("MechanicCategory desconocido: '$code'")
    }
}
```

Helpers internos: `normalizeProjectText` (trim, vacío→`null`),
`normalizeProjectList(raw, max, field)` (trim, descartar vacíos, orden, luego
`require(size <= max)`) y `normalizeKeyMechanics` (descripción trim, descartar
incompletas, luego límite 5). Géneros con `normalizeGenres`.

### 2. SQLDelight (esquema v8)

`core/src/commonMain/sqldelight/.../GameProject.sq`:

```sql
CREATE TABLE game_project (
  id TEXT NOT NULL PRIMARY KEY CHECK (id = 'singleton'),
  owner TEXT NOT NULL,
  title TEXT,
  core_loop TEXT,
  audience TEXT,
  concept TEXT,
  fantasy TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE game_project_genre (
  project_id TEXT NOT NULL REFERENCES game_project(id) ON DELETE CASCADE,
  genre TEXT NOT NULL,
  PRIMARY KEY (project_id, genre)
);

CREATE TABLE game_project_inspiration (
  project_id TEXT NOT NULL REFERENCES game_project(id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  value TEXT NOT NULL,
  PRIMARY KEY (project_id, position),
  CHECK (position >= 0 AND position < 3)
);

CREATE TABLE game_project_key_mechanic (
  project_id TEXT NOT NULL REFERENCES game_project(id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  category TEXT NOT NULL,
  description TEXT NOT NULL,
  PRIMARY KEY (project_id, position),
  CHECK (position >= 0 AND position < 5)
);

CREATE TABLE game_project_out_of_scope (
  project_id TEXT NOT NULL REFERENCES game_project(id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  value TEXT NOT NULL,
  PRIMARY KEY (project_id, position),
  CHECK (position >= 0 AND position < 3)
);
```

Queries: `selectGameProject`, `countGameProjects`, `insertGameProject`,
`updateGameProject` y select/insert/delete por tabla hija. `7.sqm` replica
**exactamente** este DDL, sin datos.

### 3. Repositorio

```kotlin
interface GameProjectRepository {
    /** Proyecto singleton o `null` si todavía no existe. */
    fun get(): GameProject?

    /** Crea el singleton o reemplaza la ficha; preserva owner/createdAt. */
    fun save(draft: GameProjectDraft): GameProject
}
```

`SqlDelightGameProjectRepository(database, clock)`:

1. Normaliza el draft **antes** de la transacción (un límite u owner inválido no
   deja escritura parcial).
2. En `transactionWithResult`, lee el Proyecto existente.
3. Si no existe, inserta con `id = "singleton"` y
   `created_at = updated_at = clock()`.
4. Si existe, `require(existing.owner == draft.owner)`; actualiza campos +
   `updated_at`, preservando `owner`/`created_at`.
5. Reemplaza las hijas (delete + insert por `position`) en la misma transacción y
   devuelve el `GameProject` persistido.

`GameProjectMappers.kt` usa alias de filas (`Game_project as GameProjectRow`,
etc.); `MechanicCategory.fromCode` fail-fast.

### 4. Migración y tests de esquema

- `7.sqm` v7→v8 sólo agrega tablas.
- `MigrationCheck`: `verifyMigrationV1ToV7`→`verifyMigrationV1ToV8` (migrar 1→8,
  assert 8, Proyecto vacío) + `verifyMigrationV7ToV8` (v1→v7, sembrar Nota y
  Género, migrar 7→8, datos intactos, Proyecto vacío). Ajustar
  `verifyMigrationV6ToV7` al `Schema.version` global 8.
- `DatabaseMigrationCheck`: migrar 1→8 y comparar `game_project` + hijas contra
  `Schema.create` (columnas, PKs, FKs).
- `GenreCheck.verifyGenreMigration`: migrar 1→8 y assert 8.
- Runners: `migratesV7ToV8AddingProjectTables`, renombres `migratesV1ToV8…` y
  `migrationV1ToV8PreservesNotes`.

## Expected File Changes

Crear:

- `core/.../model/GameProject.kt` — modelo, draft, `KeyMechanic`,
  `MechanicCategory`, constante y normalización.
- `core/.../model/GameProjectMappers.kt` — filas → dominio.
- `core/.../repository/GameProjectRepository.kt` — contrato `get`/`save`.
- `core/.../repository/SqlDelightGameProjectRepository.kt` — upsert + hijas.
- `core/.../sqldelight/.../GameProject.sq` — DDL y queries.
- `core/.../sqldelight/.../7.sqm` — migración v7→v8.
- `core/src/commonTest/.../repository/GameProjectCheck.kt` — escenarios 1–9.
- `core/src/androidUnitTest/.../repository/GameProjectAndroidTest.kt`.
- `core/src/iosTest/.../repository/GameProjectIosTest.kt`.

Modificar:

- `core/src/commonTest/.../db/MigrationCheck.kt` — v1→v8 + v7→v8.
- `core/src/commonTest/.../db/DatabaseMigrationCheck.kt` — v8 y tablas nuevas.
- `core/src/commonTest/.../repository/GenreCheck.kt` — versión 8.
- Runners `Migration*Test.kt`, `DatabaseMigration*Test.kt`,
  `NoteRepository*Test.kt` — renombres y test v7→v8.
- `ARCHITECTURE.md`, `docs/technical-discovery.md`,
  `docs/risks-and-open-questions.md`, `AGENTS.md` (sólo cola),
  `feature_list.json` y `PROGRESS.md`.

Sin cambios: `composeApp/**`, `iosApp/**`, `init.sh`,
`gradle/libs.versions.toml`, build files, `Note.sq`, `Genre.sq` ni
`1.sqm`..`6.sqm`.

## Visual Design Impact

- UI involved: **no**.
- Design source: no aplica. New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — v8, `game_project` + hijas, migración
  `7.sqm`, `GameProjectRepository`, singleton/owner/sin FK Nota.
- `CONSTRAINTS.md`: **not needed** — las reglas ya están en `CONTEXT.md`/
  `docs/domain-model.md`; esta feature las implementa.
- `AGENTS.md`: **update (acotado)** — sólo “Próxima feature en cola”.
- `docs/technical-discovery.md`: **update** — v8 implementado y forma de la
  ficha.
- `docs/risks-and-open-questions.md`: **update** — cerrar la pregunta “Esquema
  v8” remitiendo a esta spec; registrar que `vacío/esbozado` se deriva y que no
  hay FK Nota→Proyecto.
- `CONTEXT.md`/`docs/domain-model.md`: **not needed**.
- `feature_list.json`/`PROGRESS.md`: **update** al implementar/validar.

## Implementation Plan

1. Crear modelo, `MechanicCategory` y normalización.
2. Crear `GameProject.sq` y `7.sqm` con DDL espejado.
3. Implementar mapper, contrato y repositorio upsert.
4. Agregar `GameProjectCheck` + runners Android/JVM e iOS.
5. Actualizar migraciones/tests de esquema a v8 (incluye v7→v8).
6. Correr verificación y controles negativos.
7. Actualizar docs durables y cerrar `feature_list.json`/`PROGRESS.md`.

## Implementation Tasks

- [ ] `GameProject.kt` con modelo, draft, mecánicas, categoría y helpers.
- [ ] `GameProject.sq` con 5 tablas y queries.
- [ ] `7.sqm` idéntico al DDL, sin datos.
- [ ] `GameProjectMappers.kt` + `MechanicCategory.fromCode`.
- [ ] `GameProjectRepository` + `SqlDelightGameProjectRepository`.
- [ ] `GameProjectCheck` (escenarios 1–9) + runners.
- [ ] Migraciones/tests a v8 + v7→v8 + `DatabaseMigrationCheck`.
- [ ] Docs durables, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- [ ] Verificación + controles negativos + evidencia.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → `GameProjectAndroidTest`
  (escenarios 1–9), `MigrationAndroidTest` (v1→v8, v5→v6, v6→v7, v7→v8),
  `DatabaseMigrationAndroidTest` y suite existente, 0 failures.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL.
- `CI=true ./init.sh` → exit 0; gate Android-only, sin dev servers ni red.
- `PlaybookDatabase.Schema.version == 8`; `.sqm` = `1.sqm`..`7.sqm`; sólo
  `7.sqm` nuevo; `git diff` vacío en `1.sqm`..`6.sqm`.
- Revisión: no queda ninguna aserción de `Schema.version == 7` ni migración de
  test sin contemplar v8.
- **Controles negativos** (restaurar byte-idéntico y re-verificar):
  (a) omitir una tabla hija en `7.sqm` → falla migración;
  (b) no borrar las hijas antes de insertar → falla el escenario 5;
  (c) quitar `require(size <= max)` → falla el escenario 7.
- **iOS (no bloqueante):** `:core:iosSimulatorArm64Test --rerun-tasks` y
  `:composeApp:linkDebugFrameworkIosSimulatorArm64` compilan.
- **E2E persistente:** no aplica (feature de core sin flujo de UI ni harness
  E2E; `init.sh` no levanta simuladores). Cobertura: tests sobre el esquema real
  en ambas plataformas + compilación de la app.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` con nombres/conteos de
  `GameProjectAndroidTest` y tests de migración.
- `:composeApp:assembleDebug` OK y `./init.sh` exit 0.
- `Schema.version == 8`; `.sqm` = `1`..`7`; `1`..`6` sin cambios.
- Escenarios 1–9 por `verifyGameProject(driver)`; migración v7→v8 y bootstrap vs
  migración por `MigrationCheck`/`DatabaseMigrationCheck`.
- Controles negativos (a)/(b)/(c) reproducidos, restaurados y re-verificados.
- `git status` sólo con archivos de la feature; sin deps, secrets ni cambios en
  `composeApp`/`iosApp`/`init.sh`/build files; sin smoke real en emulador si no
  hay `adb` (declarado).

## Validator Checklist

- [ ] Alcance respetado: sólo modelo + persistencia v8 (sin UI, wiring, wizard,
  onboarding, GDD, export, IA, multi-proyecto ni `note.project_id`).
- [ ] Escenarios 1–12 pasan.
- [ ] Singleton real (`CHECK` + upsert); `get()` `null` sin Proyecto y una sola
  fila tras varios `save`.
- [ ] Ficha round-tripea; Géneros usan `normalizeGenres`; listas respetan orden
  y límites 3/3/5; blanks se descartan.
- [ ] Update preserva `owner`/`createdAt`, avanza `updatedAt`, reemplaza hijas
  sin huérfanos y falla con otro owner.
- [ ] Migración v7→v8 preserva datos y crea tablas vacías; `Schema.version == 8`;
  sólo `7.sqm` nuevo; `1.sqm`..`6.sqm` sin cambios.
- [ ] Bootstrap v8 y migración v1→v8 coinciden en columnas/PKs/FKs.
- [ ] Sin deps nuevas, secrets, red ni cambios en
  `init.sh`/`composeApp`/`iosApp`/build files.
- [ ] Docs durables actualizados; `feature_list.json`/`PROGRESS.md` con
  evidencia; `AGENTS.md` sólo la cola.
- [ ] E2E persistente no aplica y la spec lo justifica.
