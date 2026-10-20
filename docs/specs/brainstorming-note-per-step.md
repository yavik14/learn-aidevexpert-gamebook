# Feature Implementation Spec: Guardar cada paso del Brainstorming como Nota

## Source Feature

- `id`: `brainstorming-note-per-step` · `area`: `ui`
- `status`: `not_started` al planificar; implementada 2026-10-19 (`passing`, pendiente de validación independiente)
- `depends_on`: `["brainstorming-wizard", "create-text-note"]` (ambas `accepted`)
- `source`: `feature_list.json`

## Goal

Desde cualquier paso del wizard de Brainstorming, guardar el contenido de ese paso como una **Nota** ya clasificada con el `Track` y los Géneros del Proyecto, sin salir del flujo. La acción es explícita, confirmada y no depende de que la ficha esté guardada. Es **UI + wiring + un mapper puro en `:core`**: reutiliza `NoteRepository`, `indexBestEffort` y el wizard; no cambia modelos, esquema, IA ni el guardado del Proyecto.

## Scope Decisions

> **Vinculantes para el implementer.**

1. **Acción por paso, nunca automática.** Cada paso muestra `Guardar como Nota`; navegar, guardar el Proyecto o cancelar **no** crean Notas.
2. **Los 9 pasos generan Nota.** El paso Género usa como `body` la lista normalizada de Géneros unida por `", "`.
3. **Contexto = draft actual.** El contenido sale del estado local del wizard (aunque la ficha no esté guardada). Los Géneros de **todas** las Notas son los seleccionados actualmente; si están vacíos, la Nota queda sin Géneros.
4. **Nota final, no borrador.** No existe borrador de Nota en el dominio: se persiste con `status = CAPTURED` y se indexa best-effort con `indexBestEffort` (nunca bloquea; IA fake determinista en el gate). Un fallo de indexado deja la Nota persistida (`PENDING`/`CAPTURED`), sin error visible.
5. **Owner.** La Nota usa `LOCAL_OWNER_ID` (`"local"`), como el resto de Notas; el `owner` del Proyecto no se propaga.
6. **Confirmación siempre.** Antes de crear se abre un `AlertDialog` con preview del `body`, `Track` y Géneros; `Cancelar` no crea nada.
7. **Vacío no se guarda.** La acción se deshabilita si el `body` calculado queda en blanco; nunca se crean Notas con `body` vacío.
8. **Duplicados exactos: bloqueo.** La identidad es el contenido (`body` exacto + `Track` + Géneros **normalizados en ambos lados**), sin importar el paso de origen ni el `status` de la Nota existente. Si coincide, no se crea otra: `Nota duplicada`, sin confirmación. No hay override; para una copia, cambiá el contenido o editá la Nota existente. El chequeo autoritativo se repite al guardar.
9. **Mecánica incompleta.** Sólo en el paso Mecánicas: descripción no vacía + categoría nula → error (igual que el guardado del Proyecto), sin confirmación ni Nota, **aunque haya filas completas**. Filas vacías se ignoran.
10. **Sin salir del flujo.** Tras guardar, el wizard permanece en el mismo paso, conserva su estado y muestra `Nota guardada.` inline; no navega.
11. **Cancelar el wizard conserva las Notas ya creadas.** Descarta sólo los cambios locales de la ficha; las Notas confirmadas son independientes.
12. **Independencia del Proyecto.** Guardar una Nota no modifica ni guarda `game_project`, no avanza el paso y no toca el `GameProjectDraft`. Sin FK/`project_id` Nota→Proyecto.
13. **Sin IA generativa.** El mapeo es determinista; la única IA es el indexado best-effort existente (fake en el gate).

## Mapeo paso → Nota

`genres = normalizeGenres(géneros actuales del wizard)` para **todos** los pasos.

| # | Paso | `body` | `Track` |
|---|------|--------|---------|
| 1 | Título | `title.trim()` | `null` |
| 2 | Género | `normalizeGenres(genres).joinToString(", ")` | `null` |
| 3 | Core Loop | `coreLoop.trim()` | `MECHANICS` (`mecánicas`) |
| 4 | Audiencia | `audience.trim()` | `null` |
| 5 | Concepto | `concept.trim()` | `STORY` (`historia`) |
| 6 | Inspiraciones | ítems no vacíos, `trim`, unidos por `"\n"` | `null` |
| 7 | Fantasía | `fantasy.trim()` | `STORY` (`historia`) |
| 8 | Mecánicas clave | mecánicas completas `"<Categoría>: <descripción>"`, unidas por `"\n"` | `MECHANICS` (`mecánicas`) |
| 9 | Fuera de alcance | ítems no vacíos, `trim`, unidos por `"\n"` | `null` |

`<Categoría>` = `Movimiento`/`Interacción`/`Conflicto` (capitaliza `MechanicCategory.code`). Se conserva el orden de ingreso y se descartan vacíos. El `body` persistido es completo; el preview puede truncarse (5 líneas / 200 caracteres + `…`).

## Non-Goals

- IA real/sugerencias (`brainstorming-ai-suggestions`), chat, TTS o JEV; cambios en SQLDelight/`.sqm`, modelos, repositorios, `NoteRepository`, build files, `gradle/libs.versions.toml` o dependencias. En `:core` sólo se agrega el mapper puro y sus tests.
- `note.project_id`, FK Nota→Proyecto, sincronización Nota↔Proyecto, guardado masivo, autosave o Notas al navegar/guardar/cancelar.
- Rediseño del wizard, topes 3/3/5, `GenrePicker`, `NoteEditorScreen`; abrir, editar o borrar la Nota creada desde el wizard.
- Actualizar una Nota existente, versiones o historial de cambios.
- Cambios en `init.sh`, `iosApp/**`, `MainActivity`/`MainViewController`; iOS sólo debe seguir compilando.
- Tests de UI/E2E nuevos (no hay harness persistente).

## Job Story And Users

When estoy elaborando un paso del Brainstorming y quiero conservar esa idea suelta, I want guardarla como Nota con el Track y los Géneros del Proyecto sin salir del wizard, so I can seguir completando la ficha y tener después esa idea en la lista/GDD.

- **Autor** (single-user local-first): crea Notas desde el wizard; sin auth ni permisos nuevos.
- **Desarrollador**: Gradle/tests y smoke Android. Sin red ni secrets.

## Acceptance Scenarios

### Scenario 1: Título / Género

Given Título `Proyecto Aurora` y Géneros `[" platformer ", "JRPG", "JRPG"]`, When se guarda cada paso, Then se crean Notas con `body = "Proyecto Aurora"` y `body = "JRPG, Platformer"`, `track = null` y `genres = ["JRPG", "Platformer"]`; el wizard sigue en el mismo paso y muestra `Nota guardada.`.

### Scenario 2: Track mecánicas

Given Core Loop y Mecánicas con contenido, When se guardan, Then ambas tienen `track = MECHANICS` y los Géneros del Proyecto. Mecánicas produce, por ejemplo, `"Movimiento: Dash\nInteracción: Abrir puertas"`.

### Scenario 3: Track historia y sin Track

Given Concepto/Fantasía, When se guardan, Then ambas tienen `track = STORY`. Audiencia, Inspiraciones y Fuera de alcance generan Notas con `track = null`.

### Scenario 4: listas

Given Inspiraciones `["Hollow Knight", " ", "Celeste"]` y Fuera de alcance `["Multijugador"]`, When se guardan, Then los `body` son `"Hollow Knight\nCeleste"` y `"Multijugador"`; se descartan los vacíos.

### Scenario 5: vacío y mecánica incompleta

Given un paso con `body` en blanco (texto vacío, lista sin ítems, Géneros vacíos o Mecánicas sin descripciones), When se observa la acción, Then `Guardar como Nota` está deshabilitada y no se crea nada. Given una fila con descripción y sin categoría, When se toca `Guardar como Nota`, Then aparece el error de categoría, no se abre la confirmación y no se escribe ninguna Nota.

### Scenario 6: duplicados bloqueados

Given una Nota existente con el mismo `body`/`Track`/Géneros (aunque venga de otro paso o esté `FAILED`/`INDEXED`), When se guarda otra vez, Then se muestra `Nota duplicada`, no se abre la confirmación y no se crea una segunda Nota. El chequeo se repite en el callback autoritativo de `App`.

### Scenario 7: cancelar y errores

Given el diálogo abierto, When se toca `Cancelar`, Then no se crea Nota y el wizard no cambia. Given Notas ya guardadas, When se cancela el wizard, Then la ficha no se modifica y las Notas siguen existiendo. Given un fallo al crear, Then se muestra `No se pudo guardar la Nota.` y se permanece en el paso.

### Scenario 8: indexado y gate

Given una Nota creada con `body` no vacío, When se crea, Then se dispara `indexBestEffort` sin bloquear; con IA fake termina `INDEXED` y un fallo no impide la creación. When corren `:composeApp:assembleDebug`, `:core:testDebugUnitTest`, `CI=true ./init.sh` y el link iOS, Then todo pasa sin red, sin deps nuevas, esquema v8 y sin `.sqm` nuevo.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json` — Android-first, iOS congelado, gate Android-only, WIP=1, IA fake en tests/gate.
- `docs/specs/brainstorming-wizard.md` — 9 pasos, estado local, guardado del Proyecto, `GenrePicker`, topes y validación de mecánica.
- `docs/specs/create-text-note.md` — creación de Notas, `NoteDraft`, `indexBestEffort` y refresco.
- `docs/specs/game-project-persistence.md` — `GameProjectDraft` y normalización; `docs/risks-and-open-questions.md` — mapeo propuesto paso→Track.
- `composeApp/.../BrainstormingWizardScreen.kt` — estado, `save()` del Proyecto, `AlertDialog` y helpers de UI.
- `composeApp/.../App.kt` — `notes`/`project`, `indexBestEffort`, destino `Brainstorming`, callback `onSave` y `LOCAL_OWNER_ID`.
- `composeApp/.../NoteEditorScreen.kt`, `GenrePicker.kt` — patrones de Notas y Géneros.
- `core/.../model/{Note,Track,Genres,GameProject}.kt` — `NoteDraft(owner, body, track, status, genres)`, `Track` nullable y `normalizeGenres`.
- `core/.../repository/NoteRepository.kt` — `create(draft): Note`, `getAll()`; `core/.../ai/NoteIndexingOrchestrator.kt` — transición sin bloquear.
- `init.sh` — gate `assembleDebug + :core:testDebugUnitTest` con `-Pplaybook.ai.mode=fake`.

### Existing Patterns And Gaps

- Pantalla "tonta" con estado local; persistencia/refresco en `App`; sin DI/ViewModel/Flow. `App` lee en `LaunchedEffect(refreshKey)` y refresca con `reload()`.
- Crear Nota = `noteRepository.create(NoteDraft(...))` + `indexBestEffort(note)`; `normalizeGenres`; `Track` nullable; `status` default `CAPTURED`.
- UI con texto/forma, no sólo color; Material 3. Verificación: `:core` tests, `assembleDebug`, `CI=true ./init.sh` y smoke Android.
- Gaps: el wizard no tiene acción de Nota ni callback de creación; `App` no pasa las Notas existentes; no existe el mapper puro ni el mapeo en código; no hay harness de UI/E2E.

## Technical Approach

### 1. `:core` — mapper puro (crear)

`core/src/commonMain/kotlin/com/playbook/core/brainstorming/BrainstormingNoteMapper.kt`:

```kotlin
data class BrainstormingStepNote(val body: String, val track: Track?, val genres: List<String>)
fun buildBrainstormingStepNote(step: Int, draft: GameProjectDraft): BrainstormingStepNote
```

- `step` es el índice 0..8 del wizard (`require(step in 0..8)`); usa `draft.title`, `draft.genres`, `draft.coreLoop`, `draft.audience`, `draft.concept`, `draft.inspirations`, `draft.fantasy`, `draft.keyMechanics` y `draft.outOfScope`.
- Aplica la tabla: `trim`, descarta vacíos, `normalizeGenres` para todos, listas unidas por `\n` y Mecánicas como `"<Categoría>: <descripción>"`. No toca `owner`, persistencia ni IA.
- `BrainstormingNoteDuplicate.kt`: predicado puro `isDuplicateBrainstormingNote(note, body, track, genres)` que normaliza **ambos lados** con `normalizeGenres` antes de comparar (tolera el orden binario de SQLite y grafías migradas). Lo usan la UI y `App`.
- Tests: `BrainstormingNoteMapperCheck.kt` (helper `verifyBrainstormingNoteMapper()`) + `BrainstormingNoteDuplicateCheck.kt` (Géneros personalizados/orden/grafía, body/Track distintos y sin Géneros) + wrappers `androidUnitTest`/`iosTest` con `@Test`. Cubren los 9 pasos, listas/vacíos, mecánicas (incluida descripción vacía omitida), normalización de Géneros, `body` vacío, determinismo y `require` fuera de rango.

### 2. `BrainstormingWizardScreen.kt` (modificar)

Agregar `onSaveNote: (body: String, track: Track?, genres: List<String>) -> NoteSaveResult` y `existingNotes: List<Note>`.

- `NoteSaveResult` (enum en el archivo): `SAVED`, `DUPLICATE`, `FAILED`.
- Extraer `buildDraft()` del `save()` actual y reutilizarlo: el guardado del Proyecto y la Nota usan el mismo `GameProjectDraft` normalizado; el smoke revalida guardar/reabrir la ficha y la validación de mecánica. El mapper se invoca con `buildBrainstormingStepNote(step, draft)`.
- `canSaveNote`: texto `body.isNotBlank()`; Género `normalizeGenres(genres).isNotEmpty()`; listas con al menos un ítem no vacío; Mecánicas con al menos una descripción no vacía (las incompletas se validan al tocar).
- `OutlinedButton("Guardar como Nota")` debajo del contenido, `enabled = canSaveNote`.
- Al tocar: (1) mecánica incompleta → `noteError` con el mensaje del Proyecto y sin diálogo; (2) `body` en blanco → no hacer nada; (3) duplicado exacto contra `existingNotes` (`body`, `track`, `genres` normalizados) → `noteInfo = "Ya existe una Nota con el mismo contenido, Track y Géneros."`, diálogo `Nota duplicada`, sin confirmación; (4) abrir `AlertDialog` "Guardar como Nota" con preview, Track (`code` o `Sin track`) y Géneros (`joinToString(", ")` o `Ninguno`); (5) `Guardar` → `onSaveNote(...)`: `SAVED` → cerrar y `noteFeedback = "Nota guardada."`; `DUPLICATE` → cerrar y mostrar `Nota duplicada`; `FAILED` → cerrar y `noteError = "No se pudo guardar la Nota."`.
- `noteFeedback` inline se limpia al cambiar de paso y al iniciar un nuevo intento de guardado. `noteInfo`/`noteError` usan diálogos separados del de guardado del Proyecto.
- La acción no cambia `step`, no llama `onSave` del Proyecto y no navega. `Cancelar` del diálogo sólo lo cierra; `Cancelar` del wizard sigue llamando `onCancel()` (descarta la ficha; las Notas creadas quedan).

### 3. `App.kt` (modificar)

En `NotesDestination.Brainstorming`:

- `existingNotes = notes`.
- `onSaveNote`: normaliza Géneros; si `noteRepository.getAll()` ya tiene una Nota con el mismo `body`/`Track`/Géneros, devuelve `DUPLICATE`; si no, crea con `NoteDraft(owner = LOCAL_OWNER_ID, body = body, track = track, genres = normalized)` + `indexBestEffort`, llama `reload()` y devuelve `SAVED`/`FAILED`.

`noteRepository.getAll()` en el callback es el chequeo autoritativo (cubre `existingNotes` desactualizado y cualquier `status`); se recorre la tabla local sin query nueva. `reload()` refresca la lista. No se toca el guardado del Proyecto, `LOCAL_OWNER_ID` ni la navegación.

### 4. Sin cambios fuera de lo anterior

No se tocan modelos, SQLDelight/`.sqm`, repositorios, `NoteRepository`, `NoteIndexingOrchestrator`, `GenrePicker.kt`, `NoteEditorScreen.kt`, `NotesListScreen.kt`, build files, `init.sh`, `iosApp/**` ni los roots.

## Expected File Changes

- `docs/specs/brainstorming-note-per-step.md` — **create** (este spec).
- `core/.../brainstorming/BrainstormingNoteMapper.kt` — **create**; mapper puro y `BrainstormingStepNote`.
- `core/.../brainstorming/BrainstormingNoteDuplicate.kt` — **create**; predicado de duplicado con Géneros normalizados.
- `core/src/commonTest/.../brainstorming/BrainstormingNoteMapperCheck.kt` — **create**; helper de los 9 pasos.
- `core/src/commonTest/.../brainstorming/BrainstormingNoteDuplicateCheck.kt` — **create**; regresión de Géneros personalizados/orden/grafía.
- `core/src/androidUnitTest/.../brainstorming/BrainstormingNoteMapperAndroidTest.kt` y
  `core/src/iosTest/.../brainstorming/BrainstormingNoteMapperIosTest.kt` — **create**; wrappers `@Test`.
- `composeApp/.../BrainstormingWizardScreen.kt` — **modify**; acción, `buildDraft`, `NoteSaveResult`, duplicados bloqueados, feedback y errores.
- `composeApp/.../App.kt` — **modify**; `existingNotes`, `onSaveNote` con chequeo autoritativo de duplicado, `create` + `indexBestEffort` + `reload`.
- `ARCHITECTURE.md` — **update**; mapper en `:core` y creación de Notas desde el wizard.
- `docs/technical-discovery.md` — **update**; Nota final, mapeo e indexado.
- `docs/risks-and-open-questions.md` — **update**; cerrar mapeo e indexado.
- `AGENTS.md` — **update acotado** al cerrar (cola de features); `feature_list.json`/`PROGRESS.md` — **update** al implementar/validar.

Sin cambios: modelos, SQLDelight/`.sqm`, repositorios, `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`, `init.sh`, `iosApp/**`, `GenrePicker.kt`, `NoteEditorScreen.kt`, `NotesListScreen.kt`, `MainActivity.kt`, `MainViewController.kt`.

## Visual Design And Durable Docs

- UI involved: **yes**; fuente `DESIGN.md` (provisional), Material 3 y regla "no sólo color"; sin tokens nuevos. Afecta `BrainstormingWizardScreen`: acción secundaria, diálogo con preview, aviso de duplicado, feedback inline y error. New design artifact: **no**; iOS congelado: sin paridad visual ni smoke iOS.
- `ARCHITECTURE.md`: **update** — nueva superficie de creación de Notas desde el wizard y callback en `App`.
- `CONSTRAINTS.md`: **not needed** — no surge una regla MUST/MUST NOT nueva. `AGENTS.md`: **update acotado** — sólo la cola de features al cerrar.
- `docs/technical-discovery.md`: **update** — mapeo determinista, Nota final, duplicados bloqueados, indexado best-effort e independencia del Proyecto.
- `docs/risks-and-open-questions.md`: **update** — cerrar pre-clasificación e indexado. `DESIGN.md`, `CONTEXT.md`, `docs/domain-model.md`: **not needed**.
- `feature_list.json`/`PROGRESS.md`: **update** al implementar/validar.

## Implementation Plan And Tasks

1. Crear `BrainstormingNoteMapper` en `:core` y sus tests (common/Android/iOS).
2. Extraer `buildDraft()` y usar el mapper en el wizard; agregar `canSaveNote`.
3. Agregar la acción, confirmación, bloqueo de duplicados, feedback y errores.
4. Extender la firma con `existingNotes`/`onSaveNote` y cablear `App`.
5. Correr verificación, smoke Android y controles negativos; actualizar docs/evidencia.

- [x] Implementar `buildBrainstormingStepNote` con la tabla exacta y `require(0..8)`.
- [x] Implementar `isDuplicateBrainstormingNote` normalizando ambos lados de los Géneros.
- [x] Agregar tests de los 9 pasos, listas, mecánicas, Géneros, vacío, determinismo y duplicados personalizados.
- [x] Extraer `buildDraft()` y agregar `Guardar como Nota` con `enabled = canSaveNote`.
- [x] Validar mecánica incompleta y `body` vacío antes de confirmar.
- [x] Implementar confirmación con preview, Track y Géneros.
- [x] Bloquear duplicados exactos en UI y en el callback autoritativo de `App` usando el predicado normalizado.
- [x] Implementar `onSaveNote` con `NoteDraft`, `LOCAL_OWNER_ID`, `indexBestEffort` y `reload`.
- [x] Verificar cancelar del diálogo/wizard y persistencia de Notas.
- [x] Correr gate, smoke Android y controles negativos.
- [x] Actualizar docs, `feature_list.json` y `PROGRESS.md`.

## Verification Plan

- `./gradlew :composeApp:assembleDebug --rerun-tasks` → **BUILD SUCCESSFUL**.
- `./gradlew :core:testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL**, con la suite del mapper (9 pasos, listas, mecánicas, Géneros, vacío, determinismo) y la regresión de duplicado con Géneros personalizados; post-rebase 29 suites / 89 tests / 0 failures (incluye las demás features de `develop`).
- `CI=true ./init.sh` → **exit 0**; Android-only, IA fake, sin red ni dev servers. No bloqueante: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` y `./gradlew :core:iosSimulatorArm64Test` → **BUILD SUCCESSFUL**.
- `PlaybookDatabase.Schema.version == 8`; `.sqm` 1..7; sin `.sqm` nuevo; `git diff --stat` vacío en modelos, SQLDelight/`.sqm`, repositorios, build files, `init.sh`, `iosApp`, `GenrePicker.kt`, `NoteEditorScreen.kt`, `NotesListScreen.kt` y roots.
- **Smoke manual Android** (sin dispositivo, declararlo honestamente):
  1. Abrir Brainstorming; Título + Géneros (`Platformer`, `JRPG`).
  2. Paso 1: guardar → confirmar → `Nota guardada.`; en la lista aparece con el `body` del Título, `Sin track` y chips `JRPG`/`Platformer`.
  3. Paso 2: guardar → `body = "JRPG, Platformer"` y los mismos Géneros.
  4. Pasos 3–9: verificar `Track` (`mecánicas`/`historia`/`Sin track`) y `body` (listas con `\n`; Mecánicas con `Categoría: descripción`).
  5. Paso vacío → acción deshabilitada; no se crea fila.
  6. Mecánica con descripción y sin categoría → error, sin confirmación ni Nota.
  7. Guardar dos veces el mismo paso → `Nota duplicada`, sin confirmación y sin segunda Nota.
  8. Guardar Notas y cancelar el wizard → `game_project` no cambia y las Notas siguen en la lista; revalidar que `Guardar` del Proyecto y la reapertura siguen funcionando.
  9. Consultar `note`/`note_genre`: `body`, `track`, `status` y Géneros; con IA fake, tras el indexado queda `indexada`.
- **Controles negativos** (restaurar byte-idéntico y re-verificar): (a) quitar `noteRepository.create` → confirmar no crea Nota; (b) forzar `track = null` en Core Loop → sin `mecánicas`; (c) quitar la guarda de vacío → un paso vacío permite confirmar y crear una Nota en blanco; (d) quitar el bloqueo de duplicado → el segundo guardado crea una copia.
- **E2E persistente: no aplica** — no hay harness de UI/E2E y `init.sh` no levanta simuladores. Cobertura: tests del mapper en `:core`, path de datos de Notas, compilación y smoke manual Android.

## Evidence To Capture

- Salida de `:composeApp:assembleDebug`, `:core:testDebugUnitTest` (nueva suite del mapper), `:core:iosSimulatorArm64Test` (no bloqueante), `CI=true ./init.sh` (exit 0) y link iOS.
- Esquema v8, `.sqm` 1..7 y diff vacío en modelos/repositorios/build files/paths protegidos.
- Smoke: capturas/UI dumps de la acción, confirmación, `Nota guardada.`, `Nota duplicada`, vacío deshabilitado y error de mecánica.
- SQL de `note`/`note_genre`; prueba de que cancelar el wizard no toca `game_project` y no borra Notas.
- Controles negativos (a)–(d) reproducidos y restaurados; `git status` sólo con archivos de la feature; sin deps, secrets, `.sqm` ni cambios de esquema.

## Validator Checklist

- [ ] Alcance respetado: sólo Notas por paso desde el wizard; sin IA, onboarding, GDD híbrido, rediseño, esquema ni deps; en `:core` sólo el mapper y sus tests.
- [ ] Los 9 pasos generan la Nota con el `body`/`Track`/Géneros exactos de la tabla, cubiertos por tests del mapper.
- [ ] Nota final (`CAPTURED`) + `indexBestEffort`; no bloquea ni falla el guardado.
- [ ] Vacío deshabilitado; mecánica incompleta bloquea con error.
- [ ] Confirmación siempre; duplicados bloqueados por contenido (sin importar paso/status) en UI y en `App`; `Cancelar` no crea.
- [ ] El wizard permanece en el paso y muestra feedback; cancelar conserva las Notas y no modifica `game_project`.
- [ ] `init.sh` sin cambios y exit 0; iOS compila; esquema v8 sin `.sqm` nuevo.
- [ ] Docs durables y evidencia en `feature_list.json`/`PROGRESS.md`; `AGENTS.md` sólo la cola.
- [ ] No hay E2E persistente y la spec lo justifica.
