# Feature Implementation Spec: Persistencia de Géneros (catálogo fijo + personalizados)

## Source Feature

- `id`: `genres-catalog-persistence`
- `area`: `persistence`
- `depends_on`: `["note-category-and-tags"]` (`accepted` 2026-10-09)
- `status`: `not_started` (al planificar); `source`: `feature_list.json`

> **Re-derivación de alcance (2026-10-15).** Las **Etiquetas libres** se reemplazan
> por **Géneros**: clasificación descriptiva y múltiple, elegida de un **catálogo
> fijo de 12** o escrita como **personalizada** por Nota (`CONTEXT.md`,
> `docs/domain-model.md`, `CONSTRAINTS.md` §"Géneros y clasificación asistida"). Esta
> feature cubre el **modelo de dominio + la persistencia (esquema v7)**: renombre de
> la relación N—N `note_tag`→`note_genre`, `GenreCatalog` y normalización
> canónica en `:core`, y la migración `Etiquetas → Géneros personalizados`. La **UI
> de selección por catálogo** (picker de chips) es `genres-catalog-ui`.

## Goal

Que `:core` pueda **guardar y leer los Géneros** de una Nota: cada Género proviene
del **catálogo fijo** (canonización case-insensitive) o es un **personalizado**
(el texto escrito por el usuario, guardado por Nota, sin engrosar el catálogo
global). Las Notas existentes con Etiquetas libres **migran** a Géneros sin pérdida
de datos (esquema **v7**, `6.sqm`). Entregable: `:core` (dominio + SQLDelight +
repositorio + tests) y el **renombre mecánico mínimo** en `:composeApp` para que el
gate siga compilando. Sin UI nueva de catálogo, sin IA, sin Proyecto.

## Non-Goals

- **Picker de catálogo / UX de selección** de Géneros en el editor (chips del
  catálogo, deduplicación visual): es `genres-catalog-ui`.
- **Proyecto de Juego** (ficha singleton) y sus tablas: es `game-project-persistence`
  (esquema **v8**, `7.sqm`).
- Gestión global de Géneros (renombrar/autocompletar/fusionar), tabla `genre`
  canónica o límite de cantidad/largo: no en el MVP.
- IA: **sugerencia** de clasificación (`ai-suggest-classification`); el indexado
  sigue **sin** clasificar (`CONSTRAINTS.md`).
- Reescritura completa de la documentación que aún dice "Etiqueta" fuera del código
  (sólo se actualizan los docs durables listados abajo).

## Job Story

When capturo o edito una Nota, I want clasificarla con los **Géneros** del catálogo
(o con uno propio si no está), so I can describir a qué tipo de juego se refiere sin
perder las Etiquetas que ya había escrito.

## Users And Permissions

- Autor (único usuario, single-user local-first): crea/edita Notas y sus Géneros.
- Desarrollador: corre Gradle/tests. Sin auth, permisos ni secrets.

## Scope Decisions

> **Validadas con el autor (2026-10-16)** antes de implementar. Resuelven el
> conflicto documental v7/v8 y el solapamiento de catálogo/normalización.

1. **Esquema v7 = Géneros; v8 = Proyecto (opción A).** `feature_list.json` es la
   fuente: `genres-catalog-persistence` → **renombre** N—N `note_tag`→`note_genre` +
   v7 (`6.sqm`), `game-project-persistence` → v8 (`7.sqm`). Contradicen esto y se
   **corrigen** al actualizar docs: `docs/technical-discovery.md:31` (agrupa Géneros
   y Proyecto en v7) y `docs/risks-and-open-questions.md:44-46` ("se reutiliza la
   tabla N—N") y `:63-64` (v7 = Proyecto). Esta feature **no** crea el Proyecto.
2. **El catálogo y la normalización viven en `:core`.** `GenreCatalog` +
   `normalizeGenres` son dominio, no UI. `genres-catalog-ui` consume ese helper para
   poblar el picker; no redefine el catálogo.
3. **La UI se toca sólo para compilar y no dejar vocabulario obsoleto.** Renombre
   mecánico `tags`→`genres` y rótulos "Etiquetas/Nueva etiqueta"→"Géneros/Nuevo
   género". El comportamiento del editor sigue siendo **texto libre** hasta
   `genres-catalog-ui`.
4. **El export del GDD cambia su prefijo** `Etiquetas:`→`Géneros:` (consecuencia del
   renombre de dominio). **Supersede** la línea del "Format Contract" de
   `docs/specs/gdd-export.md`; se actualizan `GddMarkdown` y `GddMarkdownCheck`.
5. **La migración copia las Etiquetas verbatim** (sin canonización): todas pasan a
   Géneros **personalizados**. La canonización contra el catálogo ocurre al
   **escribir** (create/update). Determinista y sin pérdida.

## Acceptance Scenarios

### Scenario 1: catálogo fijo + canonización case-insensitive
Given `GenreCatalog.GENRES` (12 valores), When `normalizeGenres(listOf(" platformer ",
"JRPG", "jrpg", "Mi género"))` corre, Then devuelve `["JRPG", "Mi género",
"Platformer"]`: trim, canoniza `platformer`→`Platformer`, deduplica `jrpg`/`JRPG`
conservando la grafía canónica, mantiene `Mi género` (personalizado) y ordena
case-insensitivo.

### Scenario 2: crear/leer una Nota con Géneros de catálogo y personalizados
Given una Nota nueva, When `create(NoteDraft(..., genres = listOf("Metroidvania",
"roguelike", "Zelda-like")))`, Then `getById`/`getAll` devuelven
`["Metroidvania", "Roguelike", "Zelda-like"]` (canonizado + personalizado) desde
`note_genre`.

### Scenario 3: editar reemplaza el conjunto; `Track` intacto
Given una Nota con Géneros, When `update(note.copy(genres = listOf("Puzzle")))`, Then
el conjunto exacto es `["Puzzle"]`, `updatedAt` avanza y `track`/`status` no cambian.

### Scenario 4: N—N por Nota; personalizados no se globalizan
Given dos Notas con el Género personalizado `"Zelda-like"`, When se quita en una,
Then la otra lo conserva; `selectAllNoteGenres` no materializa un catálogo global
(el personalizado vive por Nota).

### Scenario 5: borrar la Nota limpia sus Géneros
Given una Nota con Géneros, When `delete(id)`, Then `getById` es `null` y
`genreQueries.selectGenresByNoteId(id)` queda vacío (sin huérfanos).

### Scenario 6: migración v6 → v7 preserva Etiquetas como Géneros
Given una base **v6** con `note_tag` (`note_id`,`label`) y filas, When se migra a v7,
Then `note_genre` contiene exactamente esos valores, `note_tag` ya no existe,
`Schema.version == 7` y sólo aparece `6.sqm` nuevo.

### Scenario 7: bootstrap fresco = esquema migrado
Given una base creada con `Schema.create` (v7) y otra migrada v6→v7, When se comparan
las columnas de `note`, `note_genre`, `note_link`, `attachment` y `embedding`, Then
coinciden (mismo esquema final por ambos caminos).

### Scenario 8: la app sigue compilando y no queda vocabulario "Etiqueta"
Given el renombre de dominio, When corre `:composeApp:assembleDebug` y se buscan
rótulos de UI, Then compila y la UI usa `note.genres` con rótulos "Géneros"/"Nuevo
género"; el export del GDD emite `Géneros:`.

### Scenario 9: gate honesto
Given los escenarios 1–8, When corren `./init.sh` (Android-only) y los tests del core,
Then todo pasa en Android/JVM sin red, sin `.sqm` extra, sin dependencias nuevas y
`./init.sh` termina en `0`.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json` — Android-first, iOS congelado,
  gate `./init.sh` Android-only; esquema previsto v7 (Géneros) / v8 (Proyecto).
- `CONTEXT.md` (§Género, §Etiqueta, §Track vs Género), `docs/domain-model.md`
  (§Género, edge cases de canonización y migración Etiquetas→Géneros),
  `CONSTRAINTS.md` (§Géneros y clasificación asistida), `docs/technical-discovery.md`
  (§Data and Storage, §Scope Review), `docs/adr/0003-ios-congelado-mvp.md`.
- `core/src/commonMain/sqldelight/.../{Note.sq, NoteTag.sq, 1.sqm..5.sqm}` — esquema
  actual v6; `note_tag(note_id, label)` sin FK; patrón de `.sqm` espejado por `.sq`
  (el repo **no** usa `verifySqlDelightMigration`).
- `core/src/commonMain/kotlin/.../model/{Note,NoteMappers,Tags,Track}.kt` —
  `Note.tags: List<String>`, `normalizeTags`, `Track` enum.
- `core/src/commonMain/kotlin/.../repository/{NoteRepository,SqlDelightNoteRepository}.kt`
  — `tags` persistidas en transacción; `noteTagQueries`; `getAll`/`getByStatus`
  agrupan etiquetas para evitar N+1.
- `core/src/commonMain/kotlin/.../gdd/GddMarkdown.kt` — línea de metadatos
  `Etiquetas: … — Estado: …`.
- `core/src/commonTest/.../{repository/NoteTagCheck,repository/NoteRepositoryCheck,
  db/MigrationCheck,db/DatabaseMigrationCheck,ai/NoteIndexingOrchestratorCheck,
  gdd/GddMarkdownCheck}.kt` y runners
  `core/src/{androidUnitTest,iosTest}/.../repository/NoteRepository*Test.kt` —
  helpers sobre `SqlDriver`; migración v1→v6; asserts sobre `.tags`.
- `composeApp/.../{App,NoteEditorScreen,NotesListScreen,GddScreen,RelatedNotesScreen}.kt`
  — uso de `note.tags`/`initialTags`/`tags` en el flujo de UI.

### Existing Patterns To Follow

- `:core` sin DI: dependencias por constructor; mutaciones devuelven
  `rowsAffected > 0`; `create`/`update`/`delete` agrupan nota + relación en
  `database.transaction { }`.
- Tests compartidos en `commonTest` sobre `SqlDriver`; runners Android/JVM
  (`JdbcSqliteDriver.IN_MEMORY`) e iOS (`inMemoryDriver`). Migraciones probadas
  simulando el esquema viejo (`NoteV1Schema`, `BlankTestSchema`), corriendo
  `Schema.migrate(driver, old, new)` y comparando con `Schema.create`.
- `.sqm` con DDL **espejado** por el `.sq` más reciente (equivalencia cubierta por
  `DatabaseMigrationCheck`, no por `verifySqlDelightMigration`).
- Orden determinista en lecturas (`ORDER BY … , id DESC`); el listado de relación se
  ordena por la etiqueta/género.

### Current Gaps

- No existe `GenreCatalog` ni `normalizeGenres`; el dominio sólo tiene `tags`.
- La tabla se llama `note_tag` (columna `label`); no hay migración v6→v7.
- La UI y el export del GDD usan el vocabulario "Etiqueta".

## Technical Approach

### 1. Dominio (`:core`)

`core/src/commonMain/kotlin/com/playbook/core/model/Genres.kt` (reemplaza `Tags.kt`):

```kotlin
object GenreCatalog {
    val GENRES: List<String> = listOf(
        "Platformer", "Metroidvania", "JRPG", "ActionRPG", "Roguelike", "Shooter",
        "Puzzle", "Adventure", "Towerdefense", "Novel", "Farming", "Arcade",
    )
    /** Grafía canónica del catálogo si [raw] coincide case-insensitive; si no, [raw]. */
    fun canonicalize(raw: String): String =
        GENRES.firstOrNull { it.equals(raw, ignoreCase = true) } ?: raw
}

fun normalizeGenres(raw: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    return raw
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map(GenreCatalog::canonicalize)
        .filter { seen.add(it.lowercase()) } // dedupe case-insensitive, conserva la 1ª grafía
        .sortedBy { it.lowercase() }
}
```

(Reemplaza 1:1 a `normalizeTags`: mismo contrato —trim, descarte de vacías, dedupe
case-insensitive, orden— más la canonización contra el catálogo.)

- `Note.tags` → `Note.genres: List<String> = emptyList()` (mismo lugar/default).
- `NoteDraft.tags` → `NoteDraft.genres: List<String> = emptyList()`.
- `NoteMappers.toDomain(genres: List<String> = emptyList())`.

### 2. Persistencia (esquema v7, `6.sqm`)

`core/src/commonMain/sqldelight/.../Genre.sq` (reemplaza `NoteTag.sq`; genera
`genreQueries`):

```sql
CREATE TABLE note_genre (
  note_id TEXT NOT NULL,
  genre TEXT NOT NULL,
  PRIMARY KEY (note_id, genre)
);
insertNoteGenre:      INSERT OR IGNORE INTO note_genre (note_id, genre) VALUES (?, ?);
selectGenresByNoteId: SELECT genre FROM note_genre WHERE note_id = ? ORDER BY genre;
selectAllNoteGenres:  SELECT note_id, genre FROM note_genre ORDER BY note_id, genre;
deleteGenresByNoteId: DELETE FROM note_genre WHERE note_id = ?;
```

`6.sqm` (v6→v7), espejando el DDL anterior:

```sql
CREATE TABLE note_genre (
  note_id TEXT NOT NULL,
  genre TEXT NOT NULL,
  PRIMARY KEY (note_id, genre)
);
INSERT INTO note_genre (note_id, genre) SELECT note_id, label FROM note_tag;
DROP TABLE note_tag;
```

`2.sqm` (que crea `note_tag`) **no se modifica**: es historia de la base.

### 3. Repositorio

`SqlDelightNoteRepository`: `replaceTags`→`replaceGenres` (usa `genreQueries`);
`create`/`update` normalizan con `normalizeGenres` y reemplazan el conjunto;
`getAll`/`getByStatus` agrupan `selectAllNoteGenres`; `getById` usa
`selectGenresByNoteId`; `delete` limpia `deleteGenresByNoteId`. Contrato y KDoc de
`NoteRepository` actualizados (`genres`).

### 4. UI mínima (`:composeApp`) + GDD

- `NoteEditorScreen`: `initialTags`→`initialGenres`, estado `tags`→`genres`,
  `onSave: (body, track, genres) -> Unit`; rótulos "Géneros" / "Nuevo género" /
  `onClickLabel = "Quitar género …"`. Sin picker de catálogo.
- `NotesListScreen`/`GddScreen`/`RelatedNotesScreen`: `note.genres`.
- `App.kt`: `initialTags = note.genres`, `note.copy(..., genres = genres)`,
  `NoteDraft(..., genres = genres)`.
- `GddMarkdown.metadataLine`: `"Géneros: $genres — $status"`.

## Expected File Changes

- `core/.../model/Tags.kt` → **`Genres.kt`** (renombrar); `GenreCatalog` +
  `normalizeGenres`.
- `core/.../model/{Note,NoteMappers}.kt` — modificar; `tags`→`genres`.
- `core/.../sqldelight/.../NoteTag.sq` → **`Genre.sq`** (renombrar); tabla/columnas/
  queries. Nuevo `6.sqm`.
- `core/.../repository/{NoteRepository,SqlDelightNoteRepository}.kt` — modificar;
  `genres`/`genreQueries`.
- `core/.../gdd/GddMarkdown.kt` — modificar; prefijo `Géneros:`.
- `core/src/commonTest/.../{repository/NoteTagCheck→GenreCheck, repository/NoteRepositoryCheck,
  db/MigrationCheck, db/DatabaseMigrationCheck, ai/NoteIndexingOrchestratorCheck,
  gdd/GddMarkdownCheck}.kt` — modificar (renombres + migración a v7 + escenarios 1–7).
- `core/src/{androidUnitTest,iosTest}/.../repository/NoteRepository*Test.kt` —
  modificar; runners `genresRoundTrip` + `migrationV1ToV7PreservesNotes`.
- `composeApp/.../{App,NoteEditorScreen,NotesListScreen,GddScreen,RelatedNotesScreen}.kt`
  — modificar; renombre mecánico + rótulos.
- `ARCHITECTURE.md`, `docs/technical-discovery.md` — **update** (ver abajo).
- `AGENTS.md` — update **acotado** (sólo "próxima feature en cola" al cerrar).
- `feature_list.json`, `PROGRESS.md` — update al cerrar.

No se esperan cambios en `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`,
`core/build.gradle.kts`, `init.sh`, `iosApp/**` ni `2.sqm`.

## Visual Design Impact

- UI involved: yes (mecánico). Design source: `DESIGN.md` (provisional), sin
  dirección nueva.
- Screens or states affected: `NoteEditorScreen` (rótulo "Géneros"/"Nuevo género"),
  `NotesListScreen`/`GddScreen`/`RelatedNotesScreen` (chips de Género, misma
  `LabelChip`). El chip visual de Género del catálogo (color `chip-genre`) llega con
  `genres-catalog-ui`.
- New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — Etiquetas→Géneros, `Tags.kt`→`Genres.kt`,
  `note_tag`→`note_genre`, esquema v7 y prefijo `Géneros:` del export.
- `CONSTRAINTS.md`: **not needed** — la regla de Géneros ya existe (§"Géneros y
  clasificación asistida"); esta feature la implementa.
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola".
- Other docs: `docs/technical-discovery.md` **update** (fijar v7 = **renombre a
  Géneros**, v8 = Proyecto; `note_genre`; canonización en escritura; migración
  verbatim; corregir `:31`). `docs/risks-and-open-questions.md` **update** —
  corregir `:44-46` (Géneros **sí** renombra la N—N con migración `6.sqm`) y
  `:63-64` (v7 = Géneros; el Proyecto es v8/`7.sqm`); cerrar la pregunta abierta de
  normalización contra el catálogo (`:60-62`) remitiendo a esta spec.
  `docs/specs/gdd-export.md` — **nota de supersesión** del prefijo `Etiquetas:`
  (no se reescribe la spec aceptada). `CONTEXT.md`/`docs/domain-model.md` **not
  needed** (ya definen Género). `feature_list.json`/`PROGRESS.md` **update** al
  cerrar.

## Implementation Plan

1. Crear `Genres.kt` (`GenreCatalog` + `normalizeGenres`) y borrar `Tags.kt`.
2. Renombrar el modelo (`Note`/`NoteDraft`/`NoteMappers`) a `genres`.
3. Renombrar `NoteTag.sq`→`Genre.sq` (tabla `note_genre`, queries) y crear `6.sqm`.
4. Actualizar `NoteRepository`/`SqlDelightNoteRepository`.
5. Renombre mecánico en `:composeApp` + `GddMarkdown`.
6. Actualizar/renombrar tests del core (escenarios 1–7) y migraciones a v7.
7. Correr verificación + controles negativos, actualizar docs y cerrar.

## Implementation Tasks

- [ ] `Genres.kt`: `GenreCatalog` (12) + `normalizeGenres`; eliminar `Tags.kt`.
- [ ] `Note`/`NoteDraft`/`NoteMappers`: `tags`→`genres`.
- [ ] `Genre.sq` (renombre) + `6.sqm` (v6→v7 con copia + drop de `note_tag`).
- [ ] `NoteRepository`/`SqlDelightNoteRepository`: `genres` + `genreQueries`.
- [ ] `GddMarkdown`: prefijo `Géneros:`.
- [ ] `:composeApp`: renombre mecánico + rótulos ("Géneros"/"Nuevo género").
- [ ] Tests: `GenreCheck` (escenarios 1–5), migración v6→v7 (Scenario 6),
      bootstrap (Scenario 7), renombre en `NoteRepositoryCheck`/
      `NoteIndexingOrchestratorCheck`/`GddMarkdownCheck`, runners Android/iOS.
- [ ] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md` y la nota en
      `docs/specs/gdd-export.md`; `AGENTS.md` (cola).
- [ ] Correr verificación + controles negativos y registrar evidencia.
- [ ] Actualizar `feature_list.json` y `PROGRESS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → `NoteRepositoryAndroidTest`
  (`crudRoundTrip`, `genresRoundTrip`, `migrationV1ToV7PreservesNotes`,
  `retryQueriesRoundTrip`) + `GenreCheck` + `MigrationAndroidTest`
  (`verifyMigrationV6ToV7`) + `DatabaseMigrationAndroidTest` + `GddMarkdownAndroidTest`
  + `NoteIndexingOrchestrator…` en verde, 0 failures.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL.
- `PlaybookDatabase.Schema.version == 7`; `find core/src -name '*.sqm'` = `1`..`6`.
- `grep -ri etiqueta composeApp/src` → sin rótulos de UI (sólo comentarios si los hay).
- `./init.sh` → exit 0; gate Android-only, sin dev servers ni red.
- **iOS (no bloqueante, macOS):** `./gradlew :core:iosSimulatorArm64Test
  --rerun-tasks` y `:composeApp:linkDebugFrameworkIosSimulatorArm64` compilan; iOS se
  conserva (CONSTRAINTS) pero **no** es requisito del gate.
- **Controles negativos** (restaurar byte-idéntico y re-verificar en verde):
  (a) `6.sqm` sin el `INSERT … SELECT` (o renombrando `note_genre` a
  `note_genre_broken`) → falla el Scenario 6;
  (b) `GenreCatalog.canonicalize` devolviendo `raw` → falla el Scenario 1;
  (c) dedupe por igualdad exacta en vez de case-insensitive → falla el Scenario 1.
- E2E persistente: **no aplica** (sin harness E2E, `init.sh` no levanta
  simuladores). Cobertura: tests del core sobre el esquema real + compilación de la
  app + smoke manual de migración opcional en emulador Android.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` con los tests nuevos/renombrados en verde
  (nombres y conteos) y de `:composeApp:assembleDebug`.
- `Schema.version == 7`; `.sqm` = `1`..`6`; `git status` sólo con los archivos de la
  feature; `2.sqm` sin cambios.
- Migración real v6→v7 sobre `playbook.db` v6 (emulador Android, con 2–3 Notas con
  Etiquetas): `user_version` 6→7, `note_genre` con los valores de `note_tag`,
  `note_tag` ausente, Notas intactas.
- Controles negativos (a)/(b)/(c) reproducidos y restaurados, con el test que falla.
- `./init.sh` → exit 0, sin procesos de larga duración ni red.
- Sin dependencias nuevas ni secrets; `init.sh`/`iosApp/**`/build files sin cambios.

## Validator Checklist

- [ ] Alcance respetado: dominio + persistencia (v7) + renombre mecánico; **sin**
      picker de catálogo, Proyecto (v8), IA, gestión global de Géneros ni tabla
      `genre`.
- [ ] Escenarios de aceptación 1–9 pasan.
- [ ] `note_tag`→`note_genre` con copia verbatim; `note_tag` eliminada; migración
      v6→v7 sin pérdida; `Schema.version == 7`; sólo `6.sqm` nuevo.
- [ ] `GenreCatalog` (12) y `normalizeGenres` canonizan case-insensitive y
      deduplican; personalizados viven por Nota.
- [ ] `:composeApp` compila y ya no usa el vocabulario de UI "Etiqueta"; GDD emite
      `Géneros:`.
- [ ] `2.sqm` sin cambios; sin deps nuevas; sin `.sqm` extra.
- [ ] `ARCHITECTURE.md`, `docs/technical-discovery.md` y
      `docs/risks-and-open-questions.md` actualizados (v7 = Géneros, v8 = Proyecto);
      nota de supersesión del prefijo en `docs/specs/gdd-export.md`; `AGENTS.md`
      sólo la línea de cola.
- [ ] `feature_list.json` y `PROGRESS.md` actualizados con la evidencia.
- [ ] No hay E2E persistente y la spec lo justifica; `init.sh` sin cambios.
