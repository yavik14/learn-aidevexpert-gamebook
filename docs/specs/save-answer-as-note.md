# Feature Implementation Spec: Guardar una respuesta del Q&A como Nota

## Source Feature

- `id`: `save-answer-as-note` · `area`: `ui` · `status`: `not_started` (al planificar)
- `depends_on`: `["rag-answer-citations", "brainstorming-wizard", "create-text-note"]` (las tres `accepted`)
- `source`: `feature_list.json`
- **Alcance efectivo (decisión del autor, 2026-10-20):** la entrada menciona Q&A **y** Brainstorming, pero el guardado por paso ya lo entrega `brainstorming-note-per-step` (`accepted`). Esta feature cubre **sólo Q&A** y **no toca** el wizard; al implementar/validar, la entrada de `feature_list.json` debe reflejar ese alcance.

## Goal

Desde una respuesta del Q&A (`Preguntar`), ofrecer **"Guardar como Nota"** para
persistirla como Nota final, editable y visible en lista/GDD, con confirmación,
bloqueo de duplicados exactos y sin persistir citas/fuentes. Es **UI + wiring**:
reutiliza `NoteRepository`, `indexBestEffort`, `NoteSaveResult` y el predicado de
duplicados; no agrega modelos, tablas, migraciones ni IA.

## Scope Decisions (vinculantes)

1. **Sólo Q&A.** No se modifica ni duplica el flujo del wizard de Brainstorming.
2. **`body` = respuesta sola.** `body = answer.text.trim()`, sin anteponer la pregunta; si queda en blanco no se guarda. No se persisten citas, fuentes, ranks ni enlaces (el retrieval sigue efímero).
3. **`track = null` ("Sin track").** Una respuesta RAG puede abarcar varias fuentes/tracks; no hay un Track único correcto y asignarlo sería inventar clasificación. La Nota queda editable.
4. **Géneros = `emptyList()`.** Derivarlos de las fuentes exigiría clasificación no confirmada por el usuario y las fuentes pueden tener Géneros heterogéneos. Sin IA nueva; el editor permite agregarlos luego.
5. **Acción explícita.** "Guardar como Nota" **sólo** en `AskUiState.Answered`, `enabled = answer.text.isNotBlank()`. Sin acción en `Idle`/`Asking`/`NoIndexedNotes`/`Error`. Nunca autoguardado.
6. **Confirmación siempre.** `AlertDialog` "Guardar como Nota" con preview truncado (5 líneas / 200 caracteres + `…`, criterio del wizard), `Track: Sin track` y `Géneros: Ninguno`; "Guardar" crea y "Cancelar" no crea.
7. **Duplicados exactos bloqueados.** Identidad = `body` exacto + `track` + Géneros normalizados (`isDuplicateBrainstormingNote`), sin importar `status`. Pre-chequeo en UI y chequeo autoritativo en `App` contra `noteRepository.getAll()`. Si coincide: "Nota duplicada", sin confirmación y sin segunda Nota; sin override.
8. **Feedback inline** `Nota guardada.` al confirmar; se limpia al iniciar otra pregunta/intento. Error de creación: diálogo `No se pudo guardar la Nota.` sin crear. La acción es síncrona (sin estado de carga nuevo).
9. **Persistencia.** `NoteDraft(owner = LOCAL_OWNER_ID, body = body, track = null, genres = emptyList())`; `status = CAPTURED` (default); `indexBestEffort(created)` y `reload()`. Con IA fake termina `INDEXED`; un fallo de indexado no deshace la Nota. Sin citas/enlaces.
10. **Reutilización, sin `:core`.** `NoteSaveResult` (enum de paquete en `BrainstormingWizardScreen.kt`) e `isDuplicateBrainstormingNote` (identidad genérica). Sin modelos/tablas/migraciones ni cambios de esquema. Se acepta un helper privado de preview en `AskNotesScreen.kt`; no se toca el wizard.
11. **Sin IA/retrieval/esquema.** Sin llamadas IA nuevas, cambios en `RagQueryService`/`RagAnswer`/citas/`AiClient`, deps, `init.sh` ni `iosApp`; iOS sólo compila.
12. **Sin navegación automática.** Tras guardar, el usuario sigue en Q&A; ve la Nota en lista/GDD y la edita con el editor existente.

## Resolved Questions (2026-10-20)

- **Duplicado parcial:** mismo `body` con otro `track`/Géneros **no** es duplicado; se permite (identidad completa).
- **Preview:** helper privado en `AskNotesScreen`; no se toca el wizard.
- **Cobertura:** sin tests nuevos; compilación + smoke Android + controles negativos.
- **Doble toque:** se confía en el flujo síncrono; sin `isSaving` ni estado de carga.

## Non-Goals

- Guardado por paso del Brainstorming, TTS, transcripción, generación de imagen, sugerencias de clasificación o cualquier IA nueva.
- Persistir `RetrievedNote`/citas/enlaces, asociar la Nota a fuentes o al Proyecto (`project_id`) o clasificarla automáticamente.
- Editar la respuesta antes de guardar, elegir Track/Géneros en el diálogo, edición inline, autosave o historial.
- Cambios en `:core`, SQLDelight/`.sqm`, repositorios, `NoteEditorScreen`, `NotesListScreen`, `BrainstormingWizardScreen`, build files, `init.sh` o `iosApp/**`; tests de UI/E2E nuevos (no hay harness).

## Job Story And Users

When obtengo una respuesta útil del Q&A, I want guardarla como Nota sin copiar y
pegar, so I can conservarla y editarla después como parte de mi GDD.
**Autor** (single-user local-first): guarda Notas; sin auth ni permisos nuevos.

## Acceptance Scenarios

### Scenario 1: guardar una respuesta (happy path)

Given una consulta con `AskUiState.Answered(text)` y Notas indexadas,
When se toca "Guardar como Nota" y se confirma "Guardar",
Then se crea **una** Nota con `body = text.trim()`, `track = null`,
`genres = emptyList()`, `status` inicial `CAPTURED`, timestamps del reloj; se
muestra `Nota guardada.` inline y no se navega.

### Scenario 2: cancelar no crea nada

Given el diálogo de confirmación abierto, When se toca "Cancelar" (o se descarta),
Then no se crea ninguna Nota y el Q&A queda igual.

### Scenario 3: respuesta en blanco

Given `Answered` con `text` vacío o sólo espacios, When se observa la acción,
Then "Guardar como Nota" está deshabilitada y no se abre diálogo ni se crea Nota.

### Scenario 4: estados sin respuesta

Given `Idle`, `Asking`, `NoIndexedNotes` o `Error`, Then no existe la acción de
guardar en ninguno de esos estados.

### Scenario 5: duplicado exacto

Given una Nota existente con el mismo `body`, `track = null` y sin Géneros
(cualquier `status`), When se intenta guardar la misma respuesta,
Then la UI muestra "Nota duplicada" sin abrir la confirmación; si se fuerza el
callback, `App` devuelve `DUPLICATE` y no crea una segunda Nota. Given una Nota
con el mismo `body` pero otro `track` o Géneros, When se guarda, Then **no** es
duplicada y se crea la Nota nueva (identidad completa).

### Scenario 6: fallo de creación

Given el diálogo confirmado, When `noteRepository.create`/la lectura falla,
Then se cierra la confirmación, se muestra `No se pudo guardar la Nota.` y no
queda ninguna Nota nueva.

### Scenario 7: indexado best-effort y refresco

Given una respuesta guardada con IA fake, Then la Nota se crea antes de indexar y
pasa `CAPTURED → INDEXED` sin bloquear; si el indexado falla, persiste
(`PENDING`/`CAPTURED`) y no se revierte. Lista y GDD se refrescan sin reiniciar
(`reload()`), con la Nota bajo "Sin track" y sin citas.

### Scenario 8: sin persistencia de citas

Given una respuesta con "Fuentes" visibles, When se guarda, Then la Nota contiene
sólo el texto; no se crean filas de citas/enlaces ni se guardan scores o
`RetrievedNote`.

### Scenario 9: gate Android e iOS congelado

Given la feature implementada, When se corre `CI=true ./init.sh`, `assembleDebug`
y `:core:testDebugUnitTest`, Then todo pasa con IA fake y sin red; el esquema sigue
en v8 (sin `.sqm` nuevo) y el framework iOS compila. Sin smoke ni paridad iOS.

### Scenario 10: smoke Android honesto, sin E2E persistente

Given un emulador Android con la app instalada, When se siembran Notas indexadas,
se pregunta, se guarda, se repite (duplicado), se prueba vacío/error y se abre
lista/GDD, Then se observa lo de los Scenarios 1–8. No hay comando E2E persistente, harness
de UI ni tests de UI en `:composeApp` (`androidUnitTest` sólo cubre smoke de IA
real) y `init.sh` no levanta simuladores: la UI se cubre por smoke manual y el
path de datos por `:core`.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `CONTEXT.md`, `DESIGN.md`.
- `docs/specs/{rag-answer-citations,brainstorming-note-per-step,create-text-note,brainstorming-wizard}.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`.
- `composeApp/.../AskNotesScreen.kt` — `Answered(text, sources)`, "Fuentes" y scroll; punto de la acción/diálogos.
- `composeApp/.../App.kt` — `notes`, `indexBestEffort`, `saveBrainstormingNote`, `reload()`, `LOCAL_OWNER_ID`, wiring de `NotesDestination.Ask`.
- `composeApp/.../BrainstormingWizardScreen.kt` — `NoteSaveResult`, `pendingNote`, diálogos, `previewBody` privado y uso del predicado.
- `core/.../model/{Note,Track,NoteStatus}.kt`, `NoteRepository.kt`, `ai/{RagQueryService,FakeAiClient}.kt`, `brainstorming/BrainstormingNoteDuplicate.kt`.

### Patterns And Gaps

- Pantallas "tontas" con estado + callbacks; persistencia/refresco en `App`; sin DI/ViewModel/Flow. Diálogos Material 3 y preview 5 líneas/200 chars + `…`.
- `NoteSaveResult { SAVED, DUPLICATE, FAILED }` ya es de paquete en `com.playbook.app`; `isDuplicateBrainstormingNote` normaliza ambos lados de Géneros y tiene tests en `:core`.
- `AskNotesScreen` no recibe Notas ni callback de guardado; `App` no expone `saveQaAnswer`; `previewBody` es privado del wizard; no hay harness de UI/E2E ni tests de UI en `:composeApp`.

## Technical Approach

### 1. `AskNotesScreen.kt` (modificar)

Firma nueva: `existingNotes: List<Note>` y `onSaveNote: (String) -> NoteSaveResult`.
Estado local: `pendingBody: String?`, `duplicateMessage: String?`, `saveError: String?`,
`saveFeedback: String?`.

- En `Answered`, debajo de "Respuesta" y antes de "Fuentes": `OutlinedButton("Guardar
  como Nota", enabled = state.text.isNotBlank())`; debajo, el feedback inline.
- `startSave()`: limpia feedback; `body = state.text.trim()`; si está en blanco no
  hace nada; si `existingNotes.any { isDuplicateBrainstormingNote(it, body, null,
  emptyList()) }` → "Ya existe una Nota con el mismo contenido, Track y Géneros.";
  si no, `pendingBody = body`.
- `confirmSave()`: `onSaveNote(body)`; `SAVED` → cerrar + `Nota guardada.`;
  `DUPLICATE` → cerrar + diálogo; `FAILED` → cerrar + `saveError`.
- Diálogos: confirmación (preview privado, `Track: Sin track`, `Géneros: Ninguno`,
  Guardar/Cancelar), "Nota duplicada" (Aceptar) y error con título
  `No se pudo guardar la Nota.` (Aceptar). Limpiar feedback al Preguntar/Reintentar
  y en `startSave()`.
- No cambia `AskUiState`, "Fuentes" ni navegación.

### 2. `App.kt` (modificar)

`saveQaAnswer(answerText: String): NoteSaveResult`: `body = answerText.trim()`; si
`body.isBlank()` → `FAILED` sin escribir (defensivo). Dentro de `runCatching`:
duplicado en `getAll()` → `DUPLICATE`; si no, `create(NoteDraft(owner =
LOCAL_OWNER_ID, body = body, track = null, genres = emptyList()))`; excepción →
`FAILED`. Sólo si la creación fue exitosa: `indexBestEffort(created)` + `reload()`
fuera del bloque → `SAVED` (un fallo de indexado no convierte la creación en
`FAILED`). En `NotesDestination.Ask`: `existingNotes = notes`, `onSaveNote =
::saveQaAnswer`. No se toca `ask()`, el retrieval ni otros destinos.

### 3. Invariantes

`:core`, SQLDelight/`.sqm` (v8), repositorios, `RagQueryService`, citas,
`BrainstormingWizardScreen`, roots, build files, `init.sh` e `iosApp/**` intactos.
La Nota no navega ni se abre automáticamente.

## Expected File Changes

- `composeApp/.../AskNotesScreen.kt` — **modify**; acción, confirmación, feedback, duplicado UI, preview.
- `composeApp/.../App.kt` — **modify**; `saveQaAnswer` y wiring de `Ask`.
- `docs/specs/save-answer-as-note.md` — **create** (este spec).
- Al implementar: `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`; al cerrar: `AGENTS.md` (cola), `feature_list.json`, `PROGRESS.md`.
- **Sin cambios:** `core/**`, `.sq`/`.sqm`, `BrainstormingWizardScreen.kt`, `NoteEditorScreen.kt`, `NotesListScreen.kt`, build files, `init.sh`, `iosApp/**`.

## Visual Design And Durable Docs

- UI: **sí**; `DESIGN.md` (provisional), Core Screen 6 ("Guardar como Nota"), Material 3, sin tokens/artefacto nuevos. "No sólo color": deshabilitado y feedback/errores se comunican por texto. iOS congelado.
- `ARCHITECTURE.md`: **update** — superficie de creación de Notas desde Q&A (callback en `App`, UI tonta, sin persistir citas).
- `CONSTRAINTS.md`: **not needed** — sin MUST/MUST NOT durable nuevo. `AGENTS.md`: **update acotado** al cerrar (sólo cola).
- `docs/technical-discovery.md`: **update** — body solo, sin Track/Géneros, duplicados exactos, indexado best-effort. `docs/risks-and-open-questions.md`: **update** — cerrar pre-clasificación de Q&A (sin Track único).
- `CONTEXT.md`, `docs/domain-model.md`, `DESIGN.md`: **not needed** (alineados).

## Implementation Plan And Tasks

1. Extender la firma de `AskNotesScreen` y agregar estado/acciones.
2. Implementar confirmación, preview, feedback, duplicado y error.
3. Implementar `saveQaAnswer` en `App` y cablear `NotesDestination.Ask`.
4. Actualizar docs durables y correr verificación/smoke; registrar evidencia.

- [ ] Agregar `existingNotes`/`onSaveNote` a `AskNotesScreen`.
- [ ] Mostrar "Guardar como Nota" sólo en `Answered`, deshabilitado con texto en blanco.
- [ ] Implementar confirmación con preview y `Track: Sin track` / `Géneros: Ninguno`.
- [ ] Implementar pre-chequeo de duplicado y diálogo "Nota duplicada".
- [ ] Implementar `saveQaAnswer` con chequeo autoritativo, `NoteDraft` sin Track/Géneros, `indexBestEffort` y `reload()`.
- [ ] Implementar feedback `Nota guardada.` y error `No se pudo guardar la Nota.`; limpiar feedback.
- [ ] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md` y `docs/risks-and-open-questions.md`.
- [ ] Correr gate/verificación, controles negativos y smoke Android; registrar evidencia.

## Verification Plan

- `ANDROID_HOME=<ruta-al-Android-SDK> CI=true ./init.sh` → **exit 0** (Android-only, IA fake, sin red; el baseline del worktree ya pasó). En este worktree no hay `local.properties`: exportar `ANDROID_HOME` (o crear un `local.properties` local **no versionado**). **No** copiar/versionar el `local.properties` del checkout principal.
- `./gradlew :composeApp:assembleDebug -Pplaybook.ai.mode=fake` → **BUILD SUCCESSFUL**.
- `./gradlew :core:testDebugUnitTest -Pplaybook.ai.mode=fake` → **BUILD SUCCESSFUL**, suite intacta (incluye `BrainstormingNoteDuplicateCheck` y `RagQuery`), 0 failures.
- iOS (no bloqueante): `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → **BUILD SUCCESSFUL**; sin paridad ni smoke iOS.
- Esquema v8; `.sqm` 1..7 sin archivo nuevo; `git diff --stat` vacío en `core/**`, `.sq`, build files, `init.sh`, `iosApp/**`, `BrainstormingWizardScreen.kt` y roots.
- **Controles negativos** (restaurar byte-idéntico y re-verificar): (a) quitar la acción/`enabled` de texto no blanco; (b) quitar el duplicado en UI/`App` → segunda Nota; (c) forzar `answeredText = " "` en `App.ask` → acción deshabilitada; (d) quitar `reload()` → lista/GDD sin la Nota; (e) forzar `track = STORY` → sección equivocada del GDD; (f) `saveQaAnswer` → `FAILED` → error sin Nota.
- **Smoke Android** (emulador, IA fake; evidencia en `composeApp/build/smoke-evidence/save-answer-as-note/`): crear 2–3 Notas hasta `indexada` → Preguntar → `Answered` + Fuentes → dialog → Cancelar sin fila → Guardar → `Nota guardada.`, Nota al tope con body esperado, `Sin track`, `Ninguno`, luego `indexada`; abrir GDD → "Sin track"; repetir guardado → "Nota duplicada" y conteo de `note` sin cambios; vacío/error por inspección/control negativo; SQL de `note`/`note_genre` sin citas, `track IS NULL` y sin Géneros.
- **E2E persistente: no aplica** — no hay harness de UI/E2E ni tests de UI en `:composeApp` (`androidUnitTest` sólo cubre smoke de IA real) y `init.sh` no levanta simuladores. Cobertura: tests de `:core` (datos/duplicados) + compilación + smoke manual Android.

## Evidence To Capture

- Salida de `CI=true ./init.sh` (exit 0), `assembleDebug`, `:core:testDebugUnitTest` y link iOS; esquema v8 y diffs vacíos en paths protegidos.
- Smoke: capturas/UI dumps de acción, confirmación, `Nota guardada.`, lista/GDD, `Nota duplicada`, vacío deshabilitado y error; SQL de `note`/`note_genre`.
- Controles negativos (a)–(f) reproducidos y restaurados; `git status` sólo con archivos de la feature; sin secrets, deps ni `.sqm`.
- Límites honestos: en blanco/error/indexado por inspección + control negativo; sin E2E de UI ni smoke iOS.

## Validator Checklist

- [ ] Alcance: sólo Q&A; el wizard de Brainstorming no se modificó ni duplicó.
- [ ] `body = answer.text.trim()`, `track = null`, `genres = emptyList()`; sin citas, fuentes, scores, enlaces ni `project_id` persistidos.
- [ ] Acción exclusiva de `Answered`, deshabilitada con texto en blanco; sin autoguardado ni acción en `Idle`/`Asking`/`NoIndexedNotes`/`Error`.
- [ ] Confirmación con preview truncado, `Sin track` y `Ninguno`; Cancelar no crea.
- [ ] Duplicados exactos bloqueados en UI **y** en `App` (sin importar `status`), una sola Nota; mismo body con otro Track/Géneros permitido; sin override.
- [ ] `SAVED` → feedback inline; `FAILED` → error sin Nota; feedback se limpia.
- [ ] `indexBestEffort` no bloquea ni revierte; `reload()` refresca lista/GDD; IA fake deja `INDEXED`.
- [ ] Sin tests nuevos; cobertura por compilación, smoke Android y controles negativos.
- [ ] Escenarios 1–10 verificados y con evidencia; sin E2E persistente y justificado.
- [ ] Sin cambios en `:core`/esquema/deps/`init.sh`/`iosApp`; iOS compila.
- [ ] Docs durables y `feature_list.json`/`PROGRESS.md` actualizados al implementar/validar.

## Risks And Assumptions

- **Nombre del predicado:** `isDuplicateBrainstormingNote` vive en `:core.brainstorming`; se reutiliza por contrato aunque el nombre no sea genérico. Moverlo sería refactor fuera de alcance.
- **Body largo:** el preview se trunca (5 líneas/200 chars) pero la Nota conserva el texto completo; diferencias de espacios internos o formato no se consideran duplicado (identidad exacta tras `trim`).
- **Cobertura honesta:** `FakeAiClient` nunca devuelve respuesta vacía y el fallo de creación/indexado no es inyectable desde UI sin harness; Scenario 3 y los fallos se verifican por inspección/control negativo.
