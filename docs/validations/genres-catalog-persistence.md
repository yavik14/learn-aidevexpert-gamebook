# Validación independiente: Persistencia de Géneros (`genres-catalog-persistence`)

- Fecha: 2026-10-16
- Validador: `feature-validator` (rol independiente del implementer)
- Feature: `genres-catalog-persistence` — `feature_list.json` (status `passing`)
- Spec: `docs/specs/genres-catalog-persistence.md` (incluye Scope Decisions validadas v7)
- Rama/commit base: `feature/genres-catalog-persistence` @ `43ce627` (+ árbol de trabajo sin commit)
- Veredicto: **accept**

## 1. Alcance validado

Dominio + persistencia (esquema v7) + renombre mecánico mínimo en la UI, según las
Scope Decisions validadas:

- v7 = Géneros (`6.sqm`, renombre N—N `note_tag`→`note_genre`, copia **verbatim** + DROP);
- `GenreCatalog`/`normalizeGenres` en `:core`;
- UI renombre mecánico + rótulos; export GDD con prefijo `Géneros:`;
- migración verbatim.

## 2. Comandos re-ejecutados (no se confió en el resumen)

| Comando | Resultado |
| --- | --- |
| `./gradlew :core:testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL — **47 tests, 0 failures, 0 errors, 0 skipped** (`core/build/test-results/testDebugUnitTest/`) |
| `./gradlew :composeApp:assembleDebug` | BUILD SUCCESSFUL (54 tasks) |
| `./init.sh` | **exit 0**; assembleDebug + testDebugUnitTest; sin dev servers ni red |
| `PlaybookDatabaseImpl.Schema.version` (código generado) | `get() = 7` |
| `ls core/.../db/*.sqm` | `1.sqm`..`6.sqm` (sólo `6.sqm` nuevo; `Genre.sq` nuevo) |
| `git diff -- 2.sqm gradle/ *.gradle.kts init.sh iosApp/` | vacío/no aplica — `2.sqm`, build files, deps e `init.sh` sin cambios |

Tests nuevos/renombrados observados en el XML (extracto): `NoteRepositoryAndroidTest`
4/4 (`crudRoundTrip`, `genresRoundTrip`, `migrationV1ToV7PreservesNotes`,
`retryQueriesRoundTrip`), `MigrationAndroidTest` 3/3
(`migratesV1ToV7PreservingNotes`, `migratesV5ToV6AddingIndexAttempts`,
`migratesV6ToV7RenamingTagsToGenres`), `DatabaseMigrationAndroidTest` 1/1,
`GddMarkdownAndroidTest` 1/1, `NoteIndexingOrchestratorAndroidTest` 1/1.

## 3. Cobertura de escenarios (1–9)

- **1 catálogo fijo + canonización case-insensitive:** `GenreCheck.verifyGenres` —
  `GenreCatalog.GENRES` == 12; `normalizeGenres([" platformer ","JRPG","jrpg","Mi género"])`
  == `["JRPG","Mi género","Platformer"]` (trim, canonización, dedupe case-insensitive,
  orden). OK.
- **2 crear/leer con catálogo + personalizados:** `create`/`getById`/`getAll` con
  `["Metroidvania","Roguelike","Zelda-like"]` desde `note_genre`. OK.
- **3 update reemplaza el conjunto; `Track` intacto:** `update(copy(genres=["Puzzle"]))`
  → conjunto exacto, `updatedAt` avanza, `track`/`status` sin cambios. OK.
- **4 N—N por Nota; personalizados no globalizan:** quitar en A no afecta B;
  `selectAllNoteGenres` expone por Nota. OK.
- **5 borrar limpia Géneros:** `delete` → `getById == null` y
  `selectGenresByNoteId == []`. OK.
- **6 migración v6→v7:** `verifyMigrationV6ToV7` — `note_genre` contiene los valores
  verbatim (`" JRPG "`,`"JRPG"`,`"jefe"`, sin canonizar), `note_tag` ausente, Nota
  intacta, `Schema.version == 7`. OK.
- **7 bootstrap == migrado:** `verifyDatabaseMigration` compara columnas/FKs de
  `note`/`note_genre`/`embedding`/`note_link` entre `migrate(1,7)` y `Schema.create`;
  ausencia de `note_tag`; PK compuesta `(note_id, genre)`. OK.
- **8 app compila y sin vocabulario "Etiqueta" de UI:** `assembleDebug` OK; UI usa
  `note.genres` con rótulos "Géneros"/"Nuevo género" y `onClickLabel` "Quitar género";
  GDD emite `Géneros:`. Las únicas apariciones de "etiqueta" son comentarios de KDoc
  sobre "etiqueta textual"/"label" del Adjunto/Status (no el dominio Nota). OK.
- **9 gate honesto:** `init.sh` exit 0, Android-only, sin red, sin `.sqm` extra, sin
  deps nuevas. OK.

## 4. Controles negativos (propios, distintos de los del implementer)

Se registraron hashes base (`6.sqm` `ce548807…`, `Genres.kt` `8033fe70…`,
`SqlDelightNoteRepository.kt` `0c44ab58…`; los dos primeros coinciden con los del
implementer). Se restauró **byte-idéntico** (hash re-verificado) y se re-corrió en verde.

- **Control A (propio): quitar `DROP TABLE note_tag;` de `6.sqm`.**
  Re-ejecutado `:core:testDebugUnitTest --rerun-tasks --tests "*Migration*"` →
  **BUILD FAILED**: `MigrationAndroidTest.migratesV6ToV7RenamingTagsToGenres`,
  `MigrationAndroidTest.migratesV1ToV7PreservingNotes` y
  `DatabaseMigrationAndroidTest.migratesV1ToV7AndMatchesCreate` fallan (3/4). El
  implementer había roto el `INSERT … SELECT`; este control prueba el `DROP`, que no
  estaba cubierto por el control del implementer.
- **Control B (propio): quitar `deleteGenresByNoteId(id)` de `delete` en el repositorio.**
  Re-ejecutado `--tests "*NoteRepositoryAndroidTest*"` → **BUILD FAILED**:
  `NoteRepositoryAndroidTest.genresRoundTrip` falla (assert de huérfanos del Scenario 5).
- **Restauración:** `6.sqm` → `ce548807…` (idéntico) y
  `SqlDelightNoteRepository.kt` → `0c44ab58…` (idéntico). `:core:testDebugUnitTest
  --rerun-tasks` → **BUILD SUCCESSFUL** de nuevo. Sin archivos de backup residuales
  (`git status` sin `.bak`/`.valbak`).

## 5. Scope discipline y no-goals

- Sin picker de catálogo/UX nueva (`genres-catalog-ui`), sin Proyecto (v8), sin tabla
  canónica `genre`, sin IA de clasificación, sin 7.sqm. Confirmado por
  `grep -rin "CREATE TABLE genre\b|7.sqm" core composeApp` → none.
- Sin cambios en `gradle/`, `*.gradle.kts`, `init.sh`, `iosApp/**`, `2.sqm` ni deps.
- Archivos extra fuera de *Expected File Changes*: `EnrichmentStatus.kt` y
  `LabelChip.kt` — **sólo comentarios KDoc** ("etiqueta"→"Género"); coherentes con el
  objetivo de "no dejar vocabulario obsoleto". No es scope creep (Low).
- Vocabulario: no quedan `noteTagQueries`/`normalizeTags`/`Note.tags`/`initialTags` en
  código de producción; `note_tag` sólo aparece en `2.sqm` (historia), en tests de
  migración que simulan la v6 y en comentarios. Correcto.

## 6. Arquitectura y seguridad

- Patrones respetados: `:core` sin DI (dependencias por constructor), mutaciones en
  `database.transaction { }`, agrupación anti-N+1 en `getAll`/`getByStatus`, orden
  determinista. `Genre.sq` genera `genreQueries`; DDL espejado por `6.sqm` y verificado
  por `DatabaseMigrationCheck`.
- Seguridad (checklist feature-scoped): sin secrets/keys/keystores en el diff
  (`grep -iE "api[_-]?key|secret|password|token"` sobre el diff → ninguno); single-user
  local sin auth/red; entrada de usuario `genres` normalizada (`normalizeGenres`) y
  persistida mediante **queries SQLDelight parametrizadas** (sin concatenación SQL en
  producción); el export escapa Markdown del contenido de usuario. Sin nuevos servicios
  externos ni dependencias. **Sin hallazgos de seguridad.**

## 7. Documentación durable

- `ARCHITECTURE.md`: Etiquetas→Géneros, `Tags.kt`→`Genres.kt`, `note_tag`→`note_genre`,
  esquema v7, `Schema.version = 7`, prefijo `Géneros:`. OK.
- `docs/technical-discovery.md`: v7 = Géneros (`6.sqm`, verbatim, `note_genre`,
  canonización al escribir) y v8 = Proyecto (`7.sqm`). Corrige `:31`. OK.
- `docs/risks-and-open-questions.md`: migración N—N "resuelto" con `6.sqm`; normalización
  "resuelto" remitiendo a la spec; v8 = Proyecto. OK.
- `docs/specs/gdd-export.md`: nota de supersesión del prefijo `Etiquetas:`→`Géneros:`
  (sin reescribir la spec aceptada). OK.
- `AGENTS.md`: sólo la línea "Próxima feature en cola". OK.
- `feature_list.json`/`PROGRESS.md`: evidencia registrada; status permanece `passing`
  (el orchestrator persiste `accepted`). OK.
- Nota (Low, informativa): `docs/technical-discovery.md` conserva en su sección
  histórica de la feature v3 referencias a `normalizeTags`/`note_tag`; es narrative
  histórica, permitida por el no-goal de la spec (no reescribir docs fuera de los
  durables listados) y no contradice el estado actual.

## 8. E2E / smoke

No hay harness E2E persistente y `init.sh` no levanta simuladores; la spec lo justifica.
`adb` **no** está disponible en el entorno (no hay emulador), por lo que el smoke real
v6→v7 sobre `playbook.db` **no** se ejecutó; queda cubierto por los tests del core
sobre el esquema real (`verifyMigrationV6ToV7`, `verifyGenreMigration`,
`verifyDatabaseMigration`). El implementer lo declaró honestamente.

## 9. Hallazgos

Ninguno que bloquee. Sólo notas no bloqueantes:

- **Low — archivos extra de comentarios.** `EnrichmentStatus.kt`/`LabelChip.kt`
  modificados (sólo KDoc) fuera de la lista de la spec; alineados con el objetivo de
  vocabulario. No accionable.
- **Low — smoke en emulador no ejecutado.** `adb`/emulador ausentes; cobertura por
  tests del core. Declarado.
- **Low — historia técnica.** Referencias v3 (`normalizeTags`) permanecen en la
  sección histórica de `docs/technical-discovery.md`; permitido.

## 10. Handoff

- `./init.sh` corre en verde tras la validación; el árbol no contiene artefactos de
  backup ni cambios de control. La feature puede promoverse a `accepted` por el
  orchestrator. No se modificó `feature_list.json`.

**Veredicto: `accept`.**
