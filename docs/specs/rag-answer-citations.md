# Feature Implementation Spec: La respuesta RAG referencia las notas usadas

## Source Feature

- `id`: `rag-answer-citations`
- `area`: `rag`
- `depends_on`: `["rag-query"]` (`accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Cada respuesta debe **indicar de qué Notas proviene** y permitir **abrir la Nota
fuente**. El retrieval ya devuelve `RagAnswer.Answer(text,
sources: List<RetrievedNote>)` ordenado por score (`rag-query`, `accepted`), pero
la UI descarta `sources` y muestra sólo el texto. Esta feature lo surfacea como
**citas numeradas** (rank + fragmento + `track`) con **navegación a la Nota**; el
retrieval sigue **efímero** (no se persiste).

## Non-Goals

- **Persistir citas/enlaces** (`link`): eso es `semantic-linking`/`note-links-ui`;
  la cita vive sólo en el resultado.
- **Tocar retrieval/runtime**: sin cambios en `cosineSimilarity`, top-K,
  `maxSources`, umbral, scoring ni `RagQueryService`/`RagAnswer`; sin adaptador
  real (ADR 0001), streaming ni multi-turno.
- **`NoteStatus`/indexado**: sin cambios (`note-enrichment-status-ui`/
  `offline-pending-retry`).
- **Navegación enriquecida**: sin "Notas Relacionadas", grafo ni resaltado; sólo
  abrir la Nota en el editor.
- **Score crudo** en la UI (no calibrado con `FakeAiClient`).
- **Adjuntos sin texto/OCR/clasificación IA/GDD**, cambios de esquema,
  dependencias, `init.sh`, `:core` e `iosApp`.

## Job Story

When leo una respuesta armada a partir de mis Notas, I want ver de qué Notas salió
y poder abrirlas, so I can confiar en la respuesta y volver a la idea original.

## Acceptance Scenarios

### Scenario 1: la respuesta lista sus fuentes en orden (happy path)
Given varias Notas indexadas y una consulta que produce `RagAnswer.Answer`,
When la UI muestra `AskUiState.Answered`,
Then muestra el texto **y** "Fuentes" con exactamente `sources` (mismo orden,
1..N, top-K), cada cita con fragmento del `body` y su `track` (o "Sin track"),
coincidiendo con `answer.sources.map { it.note.id }`.

### Scenario 2: tocar una cita abre esa Nota
Given una respuesta con fuentes visibles, When se toca la cita de la Nota X,
Then se abre su editor (`NotesDestination.Edit(X)`) con body y `track` reales.

### Scenario 3: cuerpo vacío o body largo
Given una fuente con `body` en blanco → muestra "Sin texto" (como la lista); con
`body` largo → truncado a 2 líneas con ellipsis.

### Scenario 4: fuente borrada entre la respuesta y el toque
Given una Nota fuente borrada antes de tocarla, When se toca esa cita,
Then no hay crash: el editor detecta la Nota inexistente y vuelve a la lista
(comportamiento existente de `Edit`).

### Scenario 5: sin fuentes no hay citas inventadas
Given `NoIndexedNotes` o `Error`, Then **no** aparece "Fuentes"; sólo el mensaje y
"Reintentar"/"Volver".

### Scenario 6: accesibilidad "no sólo color"
Given una cita, Then comunica con **texto** ("Fuente N" + fragmento + `track`), no
sólo color, y es objetivo táctil con label/rol ("Abrir nota fuente") (`DESIGN.md`).

### Scenario 7: contrato del core intacto + gate honesto
Given `:core` sin cambios, When se corren `:core:testDebugUnitTest`,
`:core:iosSimulatorArm64Test`, `:composeApp:assembleDebug`,
`:composeApp:linkDebugFrameworkIosSimulatorArm64` y `./init.sh`,
Then todo pasa en Android/JVM e iOS, el esquema sigue en **v4** (sin `.sqm`) y
`./init.sh` termina en 0 sin dev servers.

## Repository Research

### Files Inspected

- `feature_list.json` — entry de la feature (listar/navegar fuentes; `rag-query`
  declara las citas como no-goal).
- `core/.../ai/RagQueryService.kt` — `RagAnswer.Answer(text,
  sources: List<RetrievedNote>)`, `RetrievedNote(note, score)`; `sources` ya viene
  ordenado (score desc, desempate por `note.id`, top-K). **No requiere cambio.**
- `core/.../commonTest/ai/RagQueryCheck.kt` — `verifyRagAnswer` aserta el orden de
  `sources`: contrato a no romper.
- `composeApp/.../app/AskNotesScreen.kt` — `AskUiState` y `Card` "Respuesta" (sólo
  texto): punto exacto de cambio.
- `composeApp/.../app/App.kt` — `ask()` descarta `sources` (`Answered(text)`);
  `NotesDestination.Ask`/`Edit`.
- `composeApp/.../app/NotesListScreen.kt`/`LabelChip.kt` — chips de `track`, body a
  2 líneas con ellipsis, "Sin texto": patrón visual a reutilizar.
- `composeApp/src/{androidMain,iosMain}` — roots ya construyen `RagQueryService`:
  sin cambios.
- `DESIGN.md` (provisional) — Core Screen 9 ("citas a notas"), "Referencia de Nota
  (cita)", "no sólo color".
- `ARCHITECTURE.md`/`docs/technical-discovery.md` — afirman que la UI **no**
  renderiza `sources`: a actualizar.
- `composeApp/src/**` — sin source set de test (UI por smoke + tests de `:core`).

### Existing Patterns To Follow

- Pantallas "tontas": estado + callbacks; navegación por `sealed interface
  NotesDestination`; sin DI/`ViewModel`/Flow.
- `LabelChip`, body truncado, `clickable(onClickLabel=..., role=Role.Button)`;
  asincronía con `rememberCoroutineScope()` (ya justificado por `rag-query`).

### Current Gaps

- `AskUiState.Answered` sólo lleva `text`; `App.ask()` descarta `sources`.
- `AskNotesScreen` no tiene sección de fuentes ni callback para abrir una Nota.
- No hay tests de UI en `:composeApp`.

## Technical Approach

1. **Fuentes sin romper el contrato (`:core` sin cambios).**
   `RagAnswer.Answer.sources` ya es la lista ordenada de `RetrievedNote(note,
   score)`; la UI la consume tal cual (sin ids sintéticos ni API nueva).
2. **Estado de UI (`AskNotesScreen.kt`).** `AskUiState.Answered(text,
   sources: List<RetrievedNote>)` (import de `com.playbook.core.ai.RetrievedNote`);
   `AskNotesScreen` suma `onOpenSource: (String) -> Unit`. En `AskResult`, tras la
   `Card` "Respuesta", si `sources.isNotEmpty()` se renderiza **"Fuentes"**: por
   cada `source` (índice `i`), fila clickeable con `"Fuente ${i + 1}"`, el body
   (máx. 2 líneas + ellipsis, o "Sin texto") y un `LabelChip` con
   `note.track?.code ?: "Sin track"`; `clickable(onClickLabel = "Abrir nota
   fuente", role = Role.Button)`. Sin fuentes → sin sección.
3. **Mapeo y navegación (`App.kt`).** `ask()`:
   `is RagAnswer.Answer -> Answered(result.text, result.sources)`; en
   `NotesDestination.Ask`, `onOpenSource = { noteId -> destination =
   NotesDestination.Edit(noteId) }`. Se reusa el editor; su "Cancelar"/guardado
   vuelven a la lista. `askState` vive en `App`, no se pierde al navegar.
4. **Sin wiring ni esquema nuevos.** Los roots ya pasan `RagQueryService`; sin
   cambios de esquema (v4), dependencias, `init.sh` ni `iosApp`.

## Expected File Changes

- `composeApp/.../app/AskNotesScreen.kt` — modificar; `Answered(text, sources)`,
  `onOpenSource`, sección "Fuentes".
- `composeApp/.../app/App.kt` — modificar; mapear `sources` y cablear
  `onOpenSource` a `Edit(noteId)`.
- `ARCHITECTURE.md` — modificar; "RAG" (UI renderiza `sources` y navega).
- `docs/technical-discovery.md` — modificar; "RAG Decisions" (citas/navegación).
- `AGENTS.md` — modificar sólo "Próxima feature en cola" al cierre.
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar (estado/evidencia).

No se esperan cambios en `core/**`, esquema (`.sq`/`.sqm`), `init.sh`,
`gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, `iosApp/**` ni
`MainActivity`/`MainViewController`.

## Visual Design Impact

- UI involved: yes. Design source: `DESIGN.md` (provisional) — Core Screen 9 y
  "Referencia de Nota (cita)"; regla "no sólo color".
- States affected: `AskNotesScreen` `Answered` (nueva sección "Fuentes") y
  transición a `NotesDestination.Edit`.
- New design artifact required: no — Material 3, `Card`, `LabelChip` y patrones
  existentes; sin tokens nuevos.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — "RAG" deja de afirmar que la UI no renderiza
  `sources`; describir "Fuentes" y navegación.
- `CONSTRAINTS.md`: **not needed** — sin MUST/MUST NOT nuevo (la cita no persiste).
- `AGENTS.md`: **update (acotado)** — sólo "Próxima feature en cola".
- Other docs: `docs/technical-discovery.md` — update ("RAG Decisions": citas);
  `docs/domain-model.md`/`CONTEXT.md`/`DESIGN.md` — not needed (alineados);
  `PROGRESS.md`/`feature_list.json` — update al cerrar.

## Implementation Plan

1. Modificar `AskUiState.Answered` + `onOpenSource` y la sección "Fuentes" en
   `AskNotesScreen.kt`.
2. Mapear `sources` en `App.ask()` y cablear `onOpenSource` a `Edit`.
3. Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`.
4. Correr verificación, smoke en ambas plataformas y registrar evidencia.

## Implementation Tasks

- [ ] `AskUiState.Answered` a `(text, sources: List<RetrievedNote>)`.
- [ ] Agregar `onOpenSource: (String) -> Unit` a `AskNotesScreen`.
- [ ] Renderizar "Fuentes" (rank + body truncado/placeholder + chip `track`),
      clickeable/accesible; sin sección si `sources` está vacía.
- [ ] Mapear `RagAnswer.Answer -> Answered(text, sources)` en `App.ask()`.
- [ ] Cablear `onOpenSource` a `NotesDestination.Edit(noteId)`.
- [ ] Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`.
- [ ] Correr verificación + smoke (Android/iOS) y registrar evidencia.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → suite intacta (incluye
  `RagQuery` 6/6 con orden de `sources`), 0 failures; `:core:iosSimulatorArm64Test
  --rerun-tasks` → idem en iOS.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL;
  `:composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL;
  `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug
  -sdk iphonesimulator build` → BUILD SUCCEEDED.
- Confirmar esquema **v4** (`PlaybookDatabase.Schema.version == 4`), sin `.sqm`
  nuevos y sin diffs en `.sq`/`init.sh`/build files.
- **Control negativo** (restaurar): forzar `App.ask()` a
  `Answered(result.text, emptyList())` deja el Scenario 1 sin fuentes.
- **Smoke Android** (emulador): sembrar Notas con embedding, preguntar → respuesta
  + "Fuentes" 1..N; tocar "Fuente 1" → abre la Nota correcta; body largo truncado.
- **Smoke iOS** (simulador): build/install/launch + OCR de la respuesta con
  "Fuentes". Si el tecleo no es automatizable (sin `idb`/`cliclick`, `osascript`
  bloqueado), declararlo honestamente y ejercer `commonMain` vía Android.
- `./init.sh` → exit 0; gate no bloqueante, sin dev servers ni simuladores.
- E2E persistente: **no aplica** (sin harness de UI/E2E, `init.sh` no levanta
  simuladores y `:composeApp` no tiene source set de test). El contrato de datos ya
  está cubierto por tests de `:core`; render/navegación por smoke, como
  `notes-list-ui`/`create-text-note`.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest`/`:core:iosSimulatorArm64Test` (suite intacta,
  `RagQuery` 6/6 incl. orden de `sources`) y de `assembleDebug` + link iOS +
  `xcodebuild`.
- `./init.sh` → exit 0; `Schema.version == 4`, sin `.sqm`; diffs vacíos de
  `:core`/`.sq`/`init.sh`/build files/`iosApp`.
- Smoke (Android/iOS): respuesta + "Fuentes" 1..N en orden, navegación a la Nota
  correcta, truncado/placeholder, ausencia de sección sin fuentes.
- Control negativo reproducido y restaurado; limitación de tooling iOS declarada
  si aplica.

## Validator Checklist

- [ ] Alcance respetado: la UI lista y navega a las Notas fuente; **sin** persistir
      citas/enlaces, sin tocar retrieval/runtime, sin score crudo, sin `NoteStatus`.
- [ ] Escenarios 1–7 pasan.
- [ ] `RagAnswer`/`RagQueryService`/`cosineSimilarity` **sin cambios**; el orden de
      las citas coincide con `answer.sources`.
- [ ] Tocar una fuente abre `Edit(noteId)`; fuente borrada → vuelve a la lista;
      `NoIndexedNotes`/`Error` no muestran "Fuentes".
- [ ] Accesibilidad: citas con texto (no sólo color) y con label/rol.
- [ ] Evidencia en `feature_list.json`/`PROGRESS.md`; `ARCHITECTURE.md`/
      `docs/technical-discovery.md` actualizados; esquema v4; `init.sh` sin cambios.
- [ ] No hay E2E persistente y la spec justifica por qué.

## Open Questions

- **Score visible**: ¿mostrar la similitud con un adaptador real (ADR 0001)? Hoy no
  (no calibrada con `FakeAiClient`).
- **Retorno de navegación**: abrir una fuente y "Cancelar" vuelve a la lista (no a
  la respuesta); re-entrar a "Preguntar" resetea a `Idle`. No bloquea el MVP.
- **Adjuntos sin texto**: siguen fuera del RAG (`note.body` es la única fuente
  indexada); coherente con `image-ocr-optional`.
