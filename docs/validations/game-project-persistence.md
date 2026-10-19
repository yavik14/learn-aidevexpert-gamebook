# Validación independiente: Proyecto de Juego (`game-project-persistence`)

- Fecha: 2026-10-19
- Validador: `feature-validator` (rol independiente del implementer)
- Feature: `game-project-persistence` — `feature_list.json` (status `passing`)
- Spec: `docs/specs/game-project-persistence.md`
- Rama: `feature/game-project-persistence` (worktree), árbol de trabajo sin commit
- Veredicto: **accept** (tras aplicar el repair descrito abajo)

## 1. Alcance validado

Dominio + persistencia del Proyecto de Juego singleton en `:core`, esquema **v8**
(`7.sqm` aditivo), sin UI, wiring, wizard, onboarding, GDD, export, IA,
multi-proyecto ni `note.project_id`:

- `GameProject`/`GameProjectDraft`/`KeyMechanic`/`MechanicCategory` y
  normalización (`trim`, blanks→`null`, listas con límites 3/3/5, Géneros con
  `normalizeGenres`).
- `GameProjectRepository { get(); save(draft) }` +
  `SqlDelightGameProjectRepository`: upsert del singleton, `owner`/`createdAt`
  preservados, `updatedAt` por `clock()`, owner distinto falla, hijas reemplazadas
  en la transacción.
- `GameProject.sq` + `7.sqm` con DDL espejado, migración v7→v8 que preserva datos
  y crea las tablas del Proyecto vacías.
- Tests compartidos + runners Android/JVM e iOS.

## 2. Comandos re-ejecutados

| Comando | Resultado |
| --- | --- |
| `./gradlew :core:testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL — **55 tests, 0 failures, 0 errors** (`core/build/test-results/testDebugUnitTest/`) |
| `CI=true ./init.sh` | **exit 0**; `:composeApp:assembleDebug` + `:core:testDebugUnitTest`; sin dev servers ni red |
| `PlaybookDatabase.Schema.version` (generado) | `get() = 8` (`PlaybookDatabaseImpl.kt:43`) |
| `find core/src -name '*.sqm'` | `1.sqm`..`7.sqm` (sólo `7.sqm` nuevo) |
| `git status`/`git diff` en paths protegidos | vacío: `composeApp/**`, `iosApp/**`, `init.sh`, `gradle/libs.versions.toml`, `core/build.gradle.kts`, `Note.sq`, `Genre.sq`, `1.sqm`..`6.sqm` |
| DDL `GameProject.sq` vs `7.sqm` | **5/5** bloques `CREATE TABLE` idénticos textualmente |
| `:core:iosSimulatorArm64Test --rerun-tasks` | BUILD SUCCESSFUL — **55 tests, 0 failures** (`GameProjectIosTest` 1/1, `MigrationIosTest` 4/4) |
| `:composeApp:linkDebugFrameworkIosSimulatorArm64` | BUILD SUCCESSFUL |

Tests Android observados: `GameProjectAndroidTest` 1/1,
`MigrationAndroidTest` 4/4 (v1→v8, v5→v6, v6→v7, v7→v8),
`DatabaseMigrationAndroidTest` 1/1, `NoteRepositoryAndroidTest` 4/4.

## 3. Hallazgos y repair aplicado

### Medium — orden de Géneros no round-tripeaba

`normalizeGenres` ordena case-insensitive (`sortedBy { it.lowercase() }`), pero
`get()` leía con `ORDER BY genre` (colación BINARY de SQLite). Caso
`["apple", "Banana"]`: `save()` devolvía `["apple", "Banana"]` y `get()`
`["Banana", "apple"]`.

Repair:
- `SqlDelightGameProjectRepository.get()` aplica
  `.sortedBy { it.lowercase() }` tras leer.
- `GameProjectCheck` agrega la regresión mixed-case (`mixedCase`) y verifica
  `saved == repository.get()`.

### Low — `docs/specs/audio.mp3` fuera de alcance

Archivo untracked de 973 KB, ajeno a la feature y ausente del repo principal.
Repair: eliminado; `git status` sólo muestra archivos de la feature.

### Low — Scenario 10 cubría sólo Nota + Género

Se reforzó `verifyMigrationV7ToV8`: antes de migrar v7→v8 siembra una segunda
Nota, un Adjunto, un Enlace y un Embedding; después verifica que las cuatro
entidades sobreviven y que las tablas del Proyecto siguen vacías.

### Decisión — `updatedAt`

Se mantiene `clock()` directo (patrón del repo). Si dos saves caen en el mismo
milisegundo, `updatedAt` puede quedar igual; se acepta como comportamiento del
contrato actual. No es un hallazgo bloqueante.

## 4. Controles negativos (propios, con restauración byte-idéntica)

Se registraron hashes base, se mutó, se re-ejecutó el test focalizado y se
restauró verificando `sha256`:

- **Control A — renombrar `game_project_inspiration` en `7.sqm`.**
  Hash base `7.sqm` `006d68bc…`; mutado `8fc76855…`.
  Resultado: **BUILD FAILED**; falla
  `MigrationAndroidTest.migratesV7ToV8AddingProjectTables` (y v1→v8 por el
  esquema). Restaurado a `006d68bc…` (idéntico).
- **Control B — omitir el borrado de inspiraciones en `save`.**
  Hash base `SqlDelightGameProjectRepository.kt` `fe7265a7…` (post-fix); mutado
  `a6c1c6ab…`. Resultado: **BUILD FAILED**;
  `GameProjectAndroidTest.projectRoundTrip` falla con `SQLiteException` (PK).
  Restaurado a `fe7265a7…` (idéntico).
- **Control C — quitar `require(size <= max)` de `normalizeProjectList`.**
  Hash base `GameProject.kt` `efb29c94…`; mutado `a577384c…`.
  Resultado: **BUILD FAILED**; `projectRoundTrip` falla por el `CHECK` de
  posición de SQLite. Restaurado a `efb29c94…` (idéntico).

Tras restaurar los tres archivos se re-ejecutó la suite completa:
**BUILD SUCCESSFUL, 55/0**.

## 5. Cobertura de escenarios

- **1–9:** `GameProjectCheck.verifyGameProject` (singleton, vacío, round-trip,
  Géneros, update, blanks, límites, categorías, owner) + regresión de orden
  mixed-case.
- **10:** `verifyMigrationV7ToV8` con Nota, Género, Adjunto, Enlace y Embedding.
- **11:** `verifyDatabaseMigration` compara columnas/PKs/FKs de `game_project` y
  sus 4 hijas entre `Schema.create` y `migrate(1, 8)`.
- **12:** `:core:testDebugUnitTest` + `:composeApp:assembleDebug` vía
  `CI=true ./init.sh`, exit 0, sin red ni deps nuevas.

## 6. Scope discipline y seguridad

- Sin cambios en `composeApp/**`, `iosApp/**`, `init.sh`, build files, `Note.sq`,
  `Genre.sq` ni `1.sqm`..`6.sqm`.
- Sin UI, wiring, wizard, onboarding, GDD, export, IA, multi-proyecto ni
  `note.project_id`; sin `delete()`.
- Sin secrets, API keys, red ni dependencias nuevas. Queries SQLDelight
  parametrizadas. Invariante de fila única por `CHECK (id = 'singleton')` + upsert
  con id fijo; el `UPDATE` no toca `owner`.
- **Sin hallazgos de seguridad.**

## 7. Documentación durable

- `ARCHITECTURE.md`: v8, tablas del Proyecto, `GameProjectRepository`, singleton,
  sin FK Nota→Proyecto y estado derivado. Coherente.
- `docs/technical-discovery.md` y `docs/risks-and-open-questions.md`: v8
  implementado y pregunta cerrada. Coherente.
- `AGENTS.md`: sólo la línea de cola. Correcto.
- `feature_list.json`/`PROGRESS.md`: evidencia actualizada con el repair y los
  checks re-ejecutados; status se mantiene `passing` hasta que la sesión
  integradora persista `accepted`.

## 8. E2E / smoke

No hay harness E2E persistente y `init.sh` no levanta simuladores; la spec lo
justifica. No hay `adb`/emulador en el entorno, por lo que no se ejecutó smoke
real sobre `playbook.db`; la cobertura es por tests del core sobre el esquema
real y compilación de la app.

## 9. Handoff

- Worktree sin `audio.mp3` ni artefactos temporales; los tres archivos mutados en
  controles negativos quedaron byte-idénticos.
- `./init.sh` corre en verde y iOS compila.
- Recomendación: la sesión integradora puede persistir `accepted` para
  `game-project-persistence`. El validador no commitea ni cambia el estado.
