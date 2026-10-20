# Validación independiente: Guardar cada paso del Brainstorming como Nota (`brainstorming-note-per-step`)

- Fecha: 2026-10-20
- Validador: `feature-validator` (rol independiente del implementer)
- Feature: `brainstorming-note-per-step` — `feature_list.json` (status `passing`)
- Spec: `docs/specs/brainstorming-note-per-step.md`
- Rama: `feature/brainstorming-note-per-step` (worktree), árbol de trabajo sin commit
- Dependencias verificadas: `brainstorming-wizard` y `create-text-note` (`accepted`)
- Veredicto: **accept** (re-validación 2026-10-20; el `revise` original y su
  reparación quedan documentados en §5–§10)

## 1. Alcance validado

- Mapper puro `buildBrainstormingStepNote(step, draft)` en
  `core/.../brainstorming/BrainstormingNoteMapper.kt`, con `require(0..8)`,
  `normalizeGenres`, listas con `\n`, Mecánicas `Categoría: descripción` y
  Track (`MECHANICS` para Core Loop/Mecánicas, `STORY` para Concepto/Fantasía,
  `null` para el resto).
- Acción `Guardar como Nota` en los 9 pasos de
  `BrainstormingWizardScreen.kt`: confirmación con preview/Track/Géneros,
  `enabled = canSaveNote`, feedback inline, error de mecánica incompleta y aviso
  de duplicado.
- Wiring en `App.kt`: `existingNotes = notes`, creación con
  `NoteDraft(owner = LOCAL_OWNER_ID, ...)`, `indexBestEffort(note)` y
  `reload()`; chequeo autoritativo de duplicado con `noteRepository.getAll()`.
- Sin `note.project_id`, sin IA generativa, sin cambios en modelos, SQLDelight
  (`.sqm`), repositorios, build files, `init.sh`, `iosApp/**`, `GenrePicker.kt`,
  `NoteEditorScreen.kt`, `NotesListScreen.kt` ni roots.

## 2. Comandos re-ejecutados

> El worktree no tiene `local.properties` (está fuera de git); los comandos se
> ejecutaron con `ANDROID_HOME="$HOME/Library/Android/sdk"` sin modificar el
> repo.

| Comando | Resultado |
| --- | --- |
| `./gradlew :core:testDebugUnitTest --rerun-tasks` | **BUILD SUCCESSFUL** — 27 suites / 80 tests / 0 failures / 0 errors / 0 skipped; `BrainstormingNoteMapperAndroidTest.mapsEveryStep` ejecutado |
| `./gradlew :composeApp:assembleDebug --rerun-tasks` | **BUILD SUCCESSFUL** (56 tasks) |
| `CI=true ./init.sh` | **exit 0** (Android-only, IA fake, sin red ni dev servers) |
| `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` | **BUILD SUCCESSFUL** (iOS congelado) |
| `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` | **BUILD SUCCESSFUL** — 27 suites / 80 tests / 0 failures / 0 errors / 0 skipped; `BrainstormingNoteMapperIosTest.mapsEveryStep` ejecutado |

## 3. Esquema y paths protegidos

- `PlaybookDatabaseImpl.Schema.version` = **8** (generado por SQLDelight).
- `.sqm` presentes: `1.sqm`..`7.sqm`; **sin** `.sqm` nuevo.
- `git diff --stat` vacío en `core/src/commonMain/sqldelight`,
  `core/src/commonMain/kotlin/com/playbook/core/{model,repository,ai}`,
  `composeApp/build.gradle.kts`, `core/build.gradle.kts`,
  `gradle/libs.versions.toml`, `init.sh`, `iosApp/**`,
  `composeApp/src/androidMain`, `composeApp/src/iosMain`,
  `GenrePicker.kt`, `NoteEditorScreen.kt` y `NotesListScreen.kt`.
- `git status` sólo muestra archivos de la feature y de su documentación; no
  hay binarios, secrets ni artefactos de build versionados.

## 4. Controles negativos

- **(b) ejecutado.** Se mutó `2, 7 -> Track.MECHANICS` a
  `7 -> Track.MECHANICS` en el mapper; `BrainstormingNoteMapperAndroidTest`
  falló con `AssertionError`. Se restauró el archivo byte-idéntico
  (`sha256 85aaf4b0d84ac91c5aa81d64bddb81a6576cea178cdf0618c4197ab073dc85ab`;
  blob git `4124a0f3839740681b9e0499e1bf3b455b993d76`) y la suite volvió a verde.
- **(a), (c) y (d) no se ejecutaron end-to-end.** No hay harness de UI/E2E y los
  emuladores están ocupados por otras sesiones (el APK instalado en `5556` y
  `5558` no es el de este worktree). Se revisó el código: `App.kt:335-350` es
  el único camino de creación; `BrainstormingWizardScreen.kt:160-165` y
  `:186-188` contienen las guardas de vacío; `:193-198` y `App.kt:327-332`
  contienen el bloqueo de duplicado. No se sobreescribió el APK de los
  emuladores.
- **Sonda adicional de duplicado (temporal, retirada).** Se agregó un test
  temporal en `core/src/androidUnitTest` que crea una Nota con Géneros
  personalizados `["Zeta", "omega"]` y compara `getAll().genres` con
  `normalizeGenres(payload)`. Resultado:
  `normalized=[omega, Zeta]` vs `stored=[Zeta, omega]` (AssertionError). El test
  temporal se eliminó y `:core:testDebugUnitTest --rerun-tasks` volvió a verde.
  Esto motiva el hallazgo High 1.

## 5. Hallazgos

### Finding 1: El bloqueo de duplicados compara Géneros almacenados sin normalizar (escenario 6)

- Severity: **High**
- Evidence:
  - `composeApp/src/commonMain/kotlin/com/playbook/app/BrainstormingWizardScreen.kt:193-197`
    compara `note.genres == payload.genres`.
  - `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt:327-331` compara
    `note.genres == normalized`.
  - `SqlDelightNoteRepository.getAll()` devuelve los Géneros en el orden de
    `SELECT ... ORDER BY note_id, genre` (`Genre.sq:19-20`), que es orden
    binario de SQLite, no el orden de `normalizeGenres` (orden
    case-insensitive). La sonda temporal reprodujo:
    `normalizeGenres(["Zeta","omega"]) = ["omega","Zeta"]` pero
    `getAll().genres = ["Zeta","omega"]`.
  - También afecta a Notas migradas desde `note_tag` (`6.sqm` copia
    **verbatim**), donde un Género puede conservar otra grafía (`"jrpg"` frente
    a `"JRPG"`).
- Why it matters: La identidad del duplicado es "body exacto + Track + Géneros
  **normalizados**" (spec, Scope Decision 8 y Scenario 6). Con Géneros
  personalizados en distinto orden/grafía, el segundo guardado no detecta el
  duplicado y crea otra Nota, incluso desde el mismo paso. El smoke del
  implementer usó Título sin Géneros, por lo que no cubrió este caso.
- Required change: comparar los Géneros **normalizados** de la Nota existente
  contra el payload normalizado, tanto en la UI como en el callback autoritativo
  de `App`.
- Suggested implementation:
  1. En `BrainstormingWizardScreen.kt:193-197`, usar
     `normalizeGenres(note.genres) == payload.genres`.
  2. En `App.kt:327-331`, usar `normalizeGenres(note.genres) == normalized`.
  3. Agregar cobertura de regresión: si se extrae el predicado a una función
     pura de `:composeApp`, un test JVM en `composeApp/src/androidUnitTest` con
     `["omega","Zeta"]`; si no, un escenario de smoke explícito con esos
     Géneros y evidencia en `smoke-evidence/brainstorming-note-per-step/`.
- Verification after fix:
  - `:core:testDebugUnitTest --rerun-tasks` y `:composeApp:assembleDebug
    --rerun-tasks` verdes.
  - Guardar dos veces el mismo paso con Géneros personalizados
    `["omega","Zeta"]`: el segundo intento muestra `Nota duplicada` y no crea
    una segunda fila en `note`.

### Finding 2: Evidencia incompleta de los controles (a)/(c)/(d) y de las capturas pedidas

- Severity: **Medium**
- Evidence:
  - La spec pide los controles (a)-(d) (`docs/specs/brainstorming-note-per-step.md:222`)
    y capturas/UI dumps de acción, confirmación, `Nota guardada`, `Nota
    duplicada`, vacío deshabilitado, error de mecánica y SQL de `note`/
    `note_genre` (`:229-231`).
  - `feature_list.json` marca "Correr gate, smoke Android y controles negativos"
    como completado, pero la evidencia sólo registra el control del mapper (b).
  - En `composeApp/build/smoke-evidence/brainstorming-note-per-step/` sólo hay
    `list-after-notes.png/xml`, `wizard-step1-with-title.xml` y
    `smoke-notes.txt`; no hay capturas de confirmación/duplicado/vacío/error ni
    SQL de `note_genre`.
  - Esta validación sólo pudo ejecutar (b) por el estado compartido de los
    emuladores; (a)/(c)/(d) quedaron revisados estáticamente.
- Why it matters: El plan de verificación no está demostrado de forma
  reproducible y la evidencia visual prometida es parcial; eso dificulta la
  aceptación y el handoff.
- Required change: ejecutar los controles restantes en un emulador seguro o
  documentar explícitamente por qué no aplican; completar la evidencia de smoke
  (o declarar la limitación de forma inequívoca).
- Suggested implementation:
  1. Correr (a)/(c)/(d) con el APK de esta rama en un emulador propio o
     coordinado; restaurar la app/DB al terminar.
  2. Guardar capturas/UI dumps de confirmación, `Nota guardada.`, `Nota
     duplicada`, vacío deshabilitado y error de mecánica.
  3. Adjuntar el SQL de `note`/`note_genre` y la prueba de cancelación del
     wizard (Notas conservadas, `game_project` sin cambios).
- Verification after fix:
  - Evidencia nueva bajo `composeApp/build/smoke-evidence/brainstorming-note-per-step/`
    y `feature_list.json`/`PROGRESS.md` actualizados con resultados reales.

### Finding 3: Hash de restauración del control negativo no reproducible

- Severity: **Low**
- Evidence: `feature_list.json:999,1009` y `PROGRESS.md:1408` registran
  `b266c5d9d21c85abf24888b133d4600fbd7b2d83` como hash restaurado. Ese objeto no
  existe en el repo (`git cat-file -t` falla) y no coincide con el blob actual
  del mapper (`4124a0f3839740681b9e0499e1bf3b455b993d76`) ni con su sha256
  (`85aaf4b0…`). El control (b) sí se reprodujo en esta validación.
- Why it matters: La evidencia durable debe ser reproducible; un hash incorrecto
  resta confianza al resto del registro.
- Required change: reemplazar el hash por el real o quitar la referencia.
- Suggested implementation:
  1. Actualizar las tres ocurrencias con el hash verificado.
  2. Mantener el resultado FAILED/restaurado/verde tal como se observó.
- Verification after fix:
  - `git hash-object core/.../BrainstormingNoteMapper.kt` coincide con el hash
    registrado.

### Finding 4: La lectura de Notas para el chequeo autoritativo queda fuera del manejo de fallos

- Severity: **Low**
- Evidence: `App.kt:327` ejecuta `noteRepository.getAll()` antes del
  `runCatching` que envuelve `noteRepository.create` (`:335-341`). Un fallo de
  lectura/DB escaparía del callback en lugar de devolver `FAILED`.
- Why it matters: El contrato de `onSaveNote` es devolver `SAVED`/`DUPLICATE`/
  `FAILED`; una excepción inesperada rompería el flujo en vez de mostrar
  `No se pudo guardar la Nota.` (Scenario 7).
- Required change: envolver también el chequeo de duplicado (o todo el callback)
  en `runCatching` y mapear el fallo a `NoteSaveResult.FAILED`.
- Suggested implementation:
  1. Mover `getAll()` dentro del `runCatching` o usar
     `runCatching { ... }.getOrElse { return NoteSaveResult.FAILED }`.
  2. Mantener el `indexBestEffort`/`reload` sólo en el camino exitoso.
- Verification after fix:
  - Revisión del camino de error y, si hay harness, un test de repositorio que
    lance; el callback debe devolver `FAILED` sin propagar la excepción.

## 6. Seguridad

- El diff no introduce secrets, API keys, keystores ni permisos nuevos.
- Todo el flujo es local: DB SQLDelight y `NoteRepository` existentes; sin red
  ni IA generativa (sólo `indexBestEffort` con la IA fake del gate).
- `local.properties` (ignorado por git) existe en el entorno con una key, pero
  no está versionado ni forma parte del diff de la feature.
- Sin hallazgos de seguridad bloqueantes.

## 7. Documentación y harness

- `ARCHITECTURE.md`, `docs/technical-discovery.md`,
  `docs/risks-and-open-questions.md` y `AGENTS.md` describen el mapper, el
  flujo de Nota por paso y la independencia del Proyecto de forma coherente con
  el diff.
- `feature_list.json`/`PROGRESS.md` registran `passing` y la evidencia del
  implementer; esta validación agrega el veredicto `revise` y no cambia el
  status a `accepted`.
- `CONSTRAINTS.md` y `DESIGN.md` no requerían cambios y no se tocaron.
- `init.sh` sigue siendo el gate Android-only no bloqueante y no arranca
  procesos de larga duración.

## 8. Veredicto

**revise.** La feature cumple el goal, el mapper y los 9 pasos, el wiring de
`App`, la independencia del Proyecto y el alcance (sin modelos/esquema/build
files/`init.sh`/`iosApp`). Sin embargo, el bloqueo de duplicados no respeta la
identidad por **Géneros normalizados** para Géneros personalizados cuya grafía u
orden difiere al leerlos de SQLite; esto puede crear una segunda Nota y
contradice el escenario 6. Además, la evidencia de los controles (a)/(c)/(d) y
de las capturas pedidas es incompleta. La feature permanece `passing` hasta
corregir y re-validar.

## 9. Implementation Repair Brief

Goal: hacer que el duplicado exacto se detecte por `body` + `Track` + Géneros
normalizados en UI y `App`, y cerrar la evidencia de verificación.

Do:
1. En `BrainstormingWizardScreen.kt`, normalizar los Géneros de cada Nota
   existente antes de compararlos: `normalizeGenres(note.genres) == payload.genres`.
2. En `App.kt`, hacer lo mismo en el chequeo autoritativo:
   `normalizeGenres(note.genres) == normalized`.
3. Agregar una regresión para Géneros personalizados con orden/grafía distinta
   (por ejemplo `["omega","Zeta"]`): test JVM/puro si se extrae el predicado o
   escenario de smoke con evidencia.
4. Ejecutar (a)/(c)/(d) en un emulador seguro o documentar la limitación;
   completar capturas y SQL de `note`/`note_genre`.
5. Corregir el hash `b266c5d9…` en `feature_list.json`/`PROGRESS.md` y,
   opcionalmente, envolver `getAll()` en el manejo de fallos de `App`.

Do not:
- tocar modelos, SQLDelight/`.sqm`, repositorios, `NoteRepository`,
  `NoteIndexingOrchestrator`, build files, `init.sh`, `iosApp/**`,
  `GenrePicker.kt`, `NoteEditorScreen.kt` ni `NotesListScreen.kt`;
- agregar `note.project_id`, FK Nota→Proyecto, IA generativa, autosave ni
  cambios de alcance.

Run:
- `./gradlew :core:testDebugUnitTest --rerun-tasks`
- `./gradlew :composeApp:assembleDebug --rerun-tasks`
- `CI=true ./init.sh`
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`
- `./gradlew :core:iosSimulatorArm64Test`
- Smoke: guardar dos veces el mismo paso con Géneros `["omega","Zeta"]` →
  `Nota duplicada` y una sola fila en `note`.

Update:
- `docs/validations/brainstorming-note-per-step.md` (re-validación).
- `feature_list.json`/`PROGRESS.md` con la evidencia corregida.

## 10. Reparación del implementer (2026-10-20)

Estado: reparación aplicada; la feature sigue `passing`, pendiente de
re-validación independiente.

### Cambios

- **Finding 1 (High):** nuevo predicado puro
  `isDuplicateBrainstormingNote(note, body, track, genres)` en
  `core/.../brainstorming/BrainstormingNoteDuplicate.kt`; normaliza **ambos**
  lados con `normalizeGenres`, por lo que tolera el orden binario de SQLite y
  grafías migradas. `BrainstormingWizardScreen` y `App.saveBrainstormingNote`
  usan el predicado.
- **Regresión:** `BrainstormingNoteDuplicateCheck` cubre
  `["Zeta","omega"]` vs `["omega","Zeta"]`, `"jrpg"`/`"Platformer"` vs
  `"JRPG"`/`"platformer"`, body/Track distintos y ausencia de Géneros; métodos
  Android/iOS en `BrainstormingNoteMapper*Test`.
- **Finding 4 (Low):** `App.saveBrainstormingNote` envuelve `getAll()` + `create`
  en `runCatching`; un fallo devuelve `FAILED` sin propagar. `indexBestEffort` y
  `reload()` sólo corren en el camino exitoso.
- **Finding 3 (Low):** hashes corregidos a los valores reproducibles:
  mapper git blob `4124a0f3839740681b9e0499e1bf3b455b993d76`, sha256
  `85aaf4b0d84ac91c5aa81d64bddb81a6576cea178cdf0618c4197ab073dc85ab`.

### Verificación

- `:core:testDebugUnitTest --rerun-tasks` → **27 suites / 81 tests / 0 failures**
  (incluye la regresión de duplicado).
- `:composeApp:assembleDebug --rerun-tasks` → **BUILD SUCCESSFUL**;
  `CI=true ./init.sh` → **exit 0**.
- `:composeApp:linkDebugFrameworkIosSimulatorArm64` +
  `:core:iosSimulatorArm64Test --rerun-tasks` → **BUILD SUCCESSFUL** (27/81/0).
- **Finding 1 reproducido en emulator-5558 (APK de este worktree, md5
  `ed9a6d8fdf5c91a0cdb7c1bdf1d74ce7`):** paso Género con `["omega","Zeta"]` →
  `note_genre` `omega, Zeta`; segundo `Guardar como Nota` → `Nota duplicada`,
  `SELECT count(*) FROM note = 1`. Evidencia en
  `composeApp/build/smoke-evidence/brainstorming-note-per-step/`
  (`confirm-genre-custom.xml`, `duplicate-genre-custom.xml/png`,
  `list-genre-custom.xml/png`, `sql-note.txt`, `sql-note_genre.txt`).
- **Controles negativos (Finding 2), ejecutados y restaurados
  byte-idénticos:**
  - (a) sin `create` → `SELECT count(*) = 0`.
  - (b) Core Loop sin `MECHANICS` → `mapsEveryStep` FAILED; mapper restaurado
    (blob `4124a0f…`, sha256 `85aaf4b0…`).
  - (c) sin guarda de vacío → botón enabled y Nota con body vacío.
  - (d) sin bloqueo de duplicado → 2 Notas `DupTest`.
  - Hashes de restauración: App `0a6f59e4c1069788fcab936840e96fcf51ebbfd6`
    (sha256 `6ce46ac7…`), Wizard
    `fee8bfbd6a95a899371f04e72df2dc0f19c764d7` (sha256 `ba0b90ca…`), Duplicate
    `97c3d3e3732847e4ea292e668885ca682d2d1754` (sha256 `c91f63d5…`). Detalle en
    `negative-controls.txt`.

### Pendiente

- Re-validación independiente. No quedan hallazgos funcionales conocidos; no se
  commiteó ni pusheó.

## 11. Re-validación independiente (2026-10-20)

- Validador: `feature-validator` (sesión independiente del repair).
- Alcance: verificar la reparación del veredicto `revise`, re-ejecutar la
  verificación completa, reproducir el caso High en emulador y revisar alcance,
  seguridad y docs. Sin cambios de implementación, commit ni push.

### 11.1 Comandos re-ejecutados

> `ANDROID_HOME="$HOME/Library/Android/sdk"`; el worktree no tiene
> `local.properties` y no se modificó el repo.

| Comando | Resultado |
| --- | --- |
| `./gradlew :core:testDebugUnitTest --rerun-tasks` | **BUILD SUCCESSFUL** — 27 suites / 81 tests / 0 failures / 0 errors / 0 skipped; `BrainstormingNoteMapperAndroidTest.mapsEveryStep` y `detectsDuplicatesWithNormalizedGenres` ejecutados |
| `./gradlew :composeApp:assembleDebug --rerun-tasks` | **BUILD SUCCESSFUL** (56 tasks); APK md5 `ed9a6d8fdf5c91a0cdb7c1bdf1d74ce7` |
| `CI=true ./init.sh` | **exit 0** (Android-only, IA fake, sin red ni dev servers) |
| `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` | **BUILD SUCCESSFUL** (iOS congelado) |
| `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` | **BUILD SUCCESSFUL** — 27 suites / 81 tests / 0 failures / 0 errors / 0 skipped |

### 11.2 Hallazgos anteriores

- **Finding 1 (High) — resuelto.** `isDuplicateBrainstormingNote` normaliza
  **ambos** lados con `normalizeGenres`; UI
  (`BrainstormingWizardScreen.startNoteSave`) y
  `App.saveBrainstormingNote` lo usan. La regresión
  `verifyBrainstormingNoteDuplicate` cubre orden (`["Zeta","omega"]` vs
  `["omega","Zeta"]`), grafía (`"jrpg"/"Platformer"` vs
  `"JRPG"/"platformer"`), body/Track distintos y ausencia de Géneros; corre en
  Android e iOS.
- **Finding 2 (Medium) — resuelto con evidencia.** `negative-controls.txt` y
  los XML/PNG/SQL bajo `composeApp/build/smoke-evidence/brainstorming-note-per-step/`
  documentan (a) sin `create` → 0 Notas; (b) sin `MECHANICS` en Core Loop → test
  FAILED; (c) sin guarda de vacío → Nota vacía; (d) sin bloqueo → 2 Notas. Todos
  restaurados byte-idénticos.
- **Finding 3 (Low) — resuelto.** Hashes verificados en esta sesión:
  mapper blob `4124a0f3839740681b9e0499e1bf3b455b993d76` / sha256
  `85aaf4b0d84ac91c5aa81d64bddb81a6576cea178cdf0618c4197ab073dc85ab`; App
  `0a6f59e4…` / `6ce46ac7…`; Wizard `fee8bfbd…` / `ba0b90ca…`; Duplicate
  `97c3d3e3…` / `c91f63d5…` (todos reproducidos con `git hash-object` y
  `shasum -a 256`).
- **Finding 4 (Low) — resuelto.** `getAll()` + `create` están dentro de
  `runCatching`; un fallo devuelve `NoteSaveResult.FAILED` sin propagar.
  `indexBestEffort` y `reload()` sólo corren en el camino exitoso.

### 11.3 Reproducción del caso High (emulator-5558)

- APK instalado verificado por md5 `ed9a6d8fdf5c91a0cdb7c1bdf1d74ce7`
  (idéntico al APK recién construido en esta sesión).
- Paso Género con personalizados `["omega","Zeta"]`: la confirmación mostró
  body `omega, Zeta`, `Track: Sin track`, `Géneros: omega, Zeta`. `note_genre`
  quedó en orden binario (`Zeta`, `omega`).
- Primer guardado: `SELECT COUNT(*) FROM note = 1`, `status = indexada`;
  "Nota guardada." visible tras desplazar el contenido.
- Segundo guardado del mismo paso: `AlertDialog` **Nota duplicada** / "Ya existe
  una Nota con el mismo contenido, Track y Géneros."; `COUNT(*)` siguió en 1.
- Prueba adicional con body distinto (`omega, Platformer, Zeta`) guardó sin
  bloquear y mostró "Nota guardada."; confirma que no hay falso positivo.
- La app se limpió (`pm clear`) al terminar: la DB del emulador quedó como
  estaba (vacía) y no se dejaron artefactos.

### 11.4 Esquema, paths protegidos y alcance

- `PlaybookDatabaseImpl.Schema.version` = **8**; `.sqm` `1.sqm`..`7.sqm`; sin
  `.sqm` nuevo.
- `git diff --stat` vacío en `core/src/commonMain/sqldelight`,
  `core/.../{model,repository,ai}`, `composeApp/build.gradle.kts`,
  `core/build.gradle.kts`, `gradle/libs.versions.toml`, `init.sh`, `iosApp/**`,
  `composeApp/src/{androidMain,iosMain}`, `GenrePicker.kt`,
  `NoteEditorScreen.kt` y `NotesListScreen.kt`.
- Sin `note.project_id`, FK Nota→Proyecto, IA generativa, autosave ni cambios de
  alcance. Sólo se agregan el mapper, el predicado puro y sus tests en `:core`;
  UI/wiring en `BrainstormingWizardScreen.kt` y `App.kt`.
- `git diff --check` limpio. Sin secrets, API keys, keystores ni permisos nuevos.

### 11.5 Documentación

- `ARCHITECTURE.md`, `docs/technical-discovery.md` y
  `docs/risks-and-open-questions.md` describen el mapper, el predicado
  normalizado, la Nota final con `indexBestEffort`, la independencia del
  Proyecto y la cancelación de forma coherente con el código.
- `AGENTS.md` sólo actualiza la cola/estado; `CONSTRAINTS.md` y `DESIGN.md` no
  requerían cambios.
- `feature_list.json`/`PROGRESS.md` registran `passing` y la evidencia; esta
  re-validación agrega el veredicto `accept`.

### 11.6 Veredicto

**accept.** La reparación cierra el hallazgo High (duplicados por `body` +
`Track` + Géneros normalizados en UI y `App`), la regresión corre en Android e
iOS, el caso High se reprodujo con el APK de la rama, los controles negativos
(a)–(d) están ejecutados y restaurados, los hashes son reproducibles y el
callback mapea fallos a `FAILED`. Gate Android, `init.sh`, link iOS y tests iOS
en verde; esquema v8 sin `.sqm` nuevo; sin cambios fuera de alcance ni secrets.

Queda para el orquestador persistir el estado `accepted` en
`feature_list.json` (este validador no lo cambia) y actualizar la cola en
`AGENTS.md`/`PROGRESS.md` al integrar. No se commiteó ni pusheó.

### 11.7 Verificación post-rebase (cierre 2026-10-20)

- Rama rebasada sobre `origin/develop` `66611ec` (incluye
  `brainstorming-ai-suggestions`); conflictos resueltos preservando Sugerencias
  y Nota por paso.
- `:core:testDebugUnitTest --rerun-tasks` → **29 suites / 89 tests / 0 failures**.
- `:composeApp:assembleDebug --rerun-tasks` → **BUILD SUCCESSFUL**;
  `CI=true ./init.sh` → **exit 0**.
- `:composeApp:linkDebugFrameworkIosSimulatorArm64` y
  `:core:iosSimulatorArm64Test --rerun-tasks` → **BUILD SUCCESSFUL**.
- Esquema v8 (`1.sqm`..`7.sqm`), sin `.sqm` nuevo; `git diff --stat` vacío en
  paths protegidos; sin secrets ni cambios de alcance.
- Estado persistido: `brainstorming-note-per-step` `accepted` en
  `feature_list.json`; PR abierto contra `develop` (el merge lo hace la sesión
  integradora).
