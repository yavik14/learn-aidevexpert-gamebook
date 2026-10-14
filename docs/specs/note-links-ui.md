# Feature Implementation Spec: Ver notas relacionadas

## Source Feature

- `id`: `note-links-ui`
- `area`: `ui`
- `depends_on`: `["semantic-linking"]` (ya `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Dar **superficie de lectura** a los Enlaces Nota↔Nota que `semantic-linking` ya
calcula y persiste. Desde una Nota, el usuario abre "Notas relacionadas" y ve las
Notas enlazadas por similitud coseno (con su `score`), y navega a cada una. Hoy
`NoteLinkRepository`/`SemanticLinkingService` **no están cableados** a la app y no
existe UI de lectura: esta feature agrega la proyección pura, la pantalla y el
wiring en Android/iOS.

### Decisión de alcance: incluye un trigger mínimo (recomputo al abrir)

Ningún camino del código materializa filas `note_link` en runtime
(`semantic-linking` difirió el trigger). Una UI de sólo lectura quedaría siempre
vacía y **no observable**, así que esta feature incluye un trigger **mínimo y
explícito**: al abrir la pantalla corre `SemanticLinkingService.recomputeAll()`
**best-effort** (fallos ignorados) y luego lee los enlaces. No es el trigger del
ciclo de vida; eso queda en no-goals.

## Non-Goals

- Trigger automático del recomputo al indexar/editar/guardar, scheduling,
  background, debounce o recomputo incremental por Nota
  (`note-enrichment-status-ui`, `offline-pending-retry`). Acá el recomputo es
  **al abrir la pantalla**, único y explícito.
- Transición de `NoteStatus`, reintentos, colas o conectividad.
- Crear/editar/borrar Enlaces a mano, enlaces manuales vs semánticos, umbral en
  la UI, top-K o calibración del umbral.
- Pantalla de detalle de sólo lectura completa, render de Adjuntos, reproducción
  de audio o citas del RAG (`rag-answer-citations`).
- Cambios de esquema (sigue **v5**), migraciones, dependencias nuevas o `AiClient`
  real (sigue `FakeAiClient`).

## Job Story

When ya indexé varias Notas y quiero descubrir conexiones entre ideas,
I want abrir una Nota y ver sus Notas relacionadas con su grado de similitud,
so I can saltar entre ideas cercanas sin buscarlas a mano.

## Users And Permissions

- Usuario único (single-user, local-first): navega y abre Notas relacionadas; sin
  auth ni permisos. `owner` no interviene (los enlaces se vinculan por `note.id`).
- Desarrollador/autor: ejecuta Gradle/tests y los smokes; inyecta los repos por
  constructor (sin DI).

## Acceptance Scenarios

### Scenario 1: Proyección pura de Notas relacionadas

Given `buildRelatedNotes(noteId, links, notes)` con enlaces de una Nota a otras,
When se proyecta,
Then devuelve sólo las Notas del "otro extremo" (`NoteLink.otherNoteId`), **sin**
la propia, ordenadas por `score` **desc** con desempate por `note.id` asc
(determinista); los ids ausentes en `notes` se omiten; sin enlaces → lista vacía;
no muta las entradas.

### Scenario 2: Ver relacionadas (camino feliz)

Given una Nota con `embedding` y otra Nota con similitud `score >= 0.8`,
When el usuario abre "Ver relacionadas" desde esa Nota,
Then la pantalla muestra el cuerpo de la Nota origen y tarjetas de las
relacionadas con el `score` como **texto** (p. ej. `0.92`) y chips de `track`/
`status`/Etiquetas; tocar una tarjeta abre esa Nota (edición).

### Scenario 3: Estado vacío

Given una Nota sin enlaces (sin embedding, o ninguna otra supera el umbral),
When se abre "Notas relacionadas",
Then se muestra un estado vacío explícito ("No hay notas relacionadas") con una
línea de apoyo, sin crash.

### Scenario 4: Recomputo best-effort al abrir

Given embeddings persistidos cuyos pares superan el umbral,
When se abre la pantalla (aunque no existan filas `note_link` todavía),
Then `recomputeAll()` corre y se materializan los enlaces (relacionadas reflejan
los embeddings actuales). Si el recomputo falla, la pantalla muestra lo
persistido (o el estado vacío), sin propagar la excepción ni bloquear la
navegación.

### Scenario 5: Navegación y "Volver"

Given la pantalla de relacionadas abierta,
When el usuario toca una relacionada, Then `App` abre esa Nota (edición); When
toca "Volver", Then vuelve a la lista. Una Nota origen inexistente (borrada)
redirige a la lista.

### Scenario 6: Ambas plataformas + gate

Given los escenarios 1–5,
When se corren `:core:testDebugUnitTest`, `:core:iosSimulatorArm64Test`,
`:composeApp:assembleDebug`, `:composeApp:linkDebugFrameworkIosSimulatorArm64`,
`xcodebuild` (scheme `iosApp`) y `./init.sh`,
Then todo pasa en Android/JVM e iOS; el esquema sigue en **v5** sin `.sqm` nuevo;
sin dependencias nuevas; `./init.sh` termina en 0 sin dev servers ni simuladores.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json` — flujo SDD/WIP=1;
  `semantic-linking` es la dependencia `accepted`; `note-links-ui` es la primera
  ready y quedó como deuda explícita ("el **trigger** del recomputo y la UI de
  lectura siguen pendientes").
- `docs/specs/semantic-linking.md` — contrato de la dependencia: tabla `note_link`,
  `NoteLink`/`otherNoteId`, `NoteLinkRepository` (`getLinksForNote`/`getAll`),
  `SemanticLinkingService.recomputeAll()` + `DEFAULT_SIMILARITY_THRESHOLD = 0.8`
  inclusivo; trigger diferido.
- `core/src/commonMain/.../model/NoteLink.kt`, `.../repository/{NoteLinkRepository,
  SqlDelightNoteLinkRepository}.kt`, `.../linking/SemanticLinkingService.kt` —
  `NoteLink` canónico + `otherNoteId(of)` fail-fast; lectura por
  `getLinksForNote(noteId)`; `recomputeAll()` no-`suspend` (borra y reconstruye).
- `core/src/commonMain/.../gdd/GddProjection.kt` — precedente de **proyección pura
  en `:core`** (`buildGdd`) testeada cross-platform y renderizada por una pantalla
  "tonta".
- `core/src/commonMain/.../repository/NoteRepository.kt` — `getAll()` (orden
  `updated_at DESC, id DESC`) e `getById`.
- `composeApp/src/commonMain/.../app/App.kt` — hoistea `notes`/`destination`/
  `refreshKey` con `mutableStateOf`; `NotesDestination` sellado;
  `LaunchedEffect(refreshKey)` + `reload()`; `rememberCoroutineScope()` para
  servicios `suspend`; `indexBestEffort`; sin DI/`ViewModel`/Flow.
- `composeApp/src/commonMain/.../app/{NotesListScreen,GddScreen,NoteEditorScreen,
  LabelChip,AskNotesScreen}.kt` — patrón de pantallas "tontas", `LabelChip`
  reutilizable, `TextOverflow.Ellipsis`, textos en español y regla "no sólo color".
- `composeApp/src/{androidMain,iosMain}/.../{MainActivity,MainViewController}.kt` —
  composition roots: construyen repos + `FakeAiClient` + `NoteIndexingService` +
  `RagQueryService` y los pasan a `App(...)` (iOS cachea en `var` de nivel superior).
- `DESIGN.md` (provisional) — dirección "cuaderno cálido", accesibilidad (AA, touch
  ≥48dp, estados no sólo color) y Core Screen 8 "Notas Relacionadas".
- `docs/domain-model.md`, `CONTEXT.md` — `Enlace / Relación` = conexión Nota↔Nota
  por similitud con `score`; `indexada` = embedding **y** enlaces.
- `docs/technical-discovery.md` ("Linking Decisions"), `ARCHITECTURE.md` (Deferred),
  `docs/risks-and-open-questions.md` (follow-up del trigger).

### Existing Patterns To Follow

- `:core` no depende de la UI: la lógica de orden/resolución vive en una función
  pura; la pantalla sólo renderiza y emite intenciones (patrón `buildGdd`).
- Sin DI: dependencias por constructor; estado y navegación en `App` con
  `mutableStateOf` + `LaunchedEffect` + `reload()`; sin `ViewModel`/Flow.
- Textos en español, `LabelChip` para chips, `role`/`onClickLabel` semánticos,
  información nunca sólo por color.
- Tests compartidos puros (`@Test` en `commonTest`) sin driver; los corren
  Android/JVM e iOS.

### Current Gaps

- No existe pantalla ni proyección de relacionadas; `NoteLinkRepository` y
  `SemanticLinkingService` no se construyen ni pasan a `App(...)` en ningún root.
- `NotesDestination` no tiene destino de relacionadas ni entrada desde el editor.
- El único origen de filas `note_link` es `recomputeAll()`, hoy sin disparador.

## Technical Approach

1. **Proyección pura en `:core`** (`com.playbook.core.linking`, `RelatedNotes.kt`):

   ```kotlin
   data class RelatedNote(val note: Note, val score: Double)
   fun buildRelatedNotes(noteId: String, links: List<NoteLink>, notes: List<Note>): List<RelatedNote>
   ```

   Resuelve el otro extremo con `otherNoteId(noteId)`, lo busca en `notes` (omite
   si falta), excluye la propia Nota y ordena por `score` desc / `note.id` asc.
   Pura, determinista, sin SQLDelight ni `AiClient`.

2. **`RelatedNotesScreen.kt`** (`:composeApp/commonMain`), "tonta": props
   `note: Note`, `related: List<RelatedNote>`, `onOpenNote: (String) -> Unit`,
   `onBack: () -> Unit`. Header "Notas relacionadas" + cuerpo de la Nota origen
   (máx. 2 líneas) + "Volver"; estado vacío explícito; `LazyColumn` de tarjetas
   (cuerpo máx. 2 líneas, `score` como texto, chips `track`/`status`/Etiquetas),
   cada tarjeta `clickable(onClickLabel = "Abrir nota")`.

3. **`App.kt`**: params `noteLinkRepository`/`semanticLinkingService`; destino
   `NotesDestination.Related(noteId)`; estado `relatedNotes`; en la rama `Related`
   un `LaunchedEffect(noteId)` corre `runCatching { semanticLinkingService
   .recomputeAll() }`, luego `buildRelatedNotes(noteId, noteLinkRepository
   .getLinksForNote(noteId), notes)`; `onOpenNote` navega a `Edit(id)`. La rama
   `Edit` pasa `onOpenRelated` al editor.

4. **`NoteEditorScreen.kt`**: param opcional `onOpenRelated: (() -> Unit)?` y un
   `TextButton("Ver relacionadas")` sólo en modo edición.

5. **Composition roots**: `MainActivity`/`MainViewController` construyen
   `SqlDelightNoteLinkRepository(database, ::currentTimeMillis)` y
   `SemanticLinkingService(embeddingRepository, noteLinkRepository)` y los pasan a
   `App(...)` (iOS cachea el service en `var`).

Límite aceptado: `recomputeAll()` es **síncrono** y O(n²) sobre embeddings; corre
en el dispatcher principal dentro de `LaunchedEffect` (igual que `getAll()`).
Aceptable para una biblioteca personal; el recomputo incremental/background queda
como follow-up. El umbral `0.8` no se toca desde la UI.

## Expected File Changes

- `core/src/commonMain/.../linking/RelatedNotes.kt` — create; `RelatedNote` +
  `buildRelatedNotes`.
- `core/src/commonTest/.../linking/RelatedNotesTest.kt` — create; `@Test` puro.
- `composeApp/src/commonMain/.../app/RelatedNotesScreen.kt` — create; pantalla y
  estados.
- `composeApp/src/commonMain/.../app/App.kt` — modify; destino, params, estado,
  recomputo best-effort y navegación.
- `composeApp/src/commonMain/.../app/NoteEditorScreen.kt` — modify;
  `onOpenRelated` + botón.
- `composeApp/src/androidMain/.../MainActivity.kt` y
  `composeApp/src/iosMain/.../MainViewController.kt` — modify; construir y pasar
  `SqlDelightNoteLinkRepository` + `SemanticLinkingService`.
- `ARCHITECTURE.md`, `docs/technical-discovery.md`,
  `docs/risks-and-open-questions.md` — modify; UI + trigger mínimo.
- `AGENTS.md` — modify sólo la línea "Próxima feature en cola" al cierre.
- `feature_list.json`, `PROGRESS.md` — modify al cerrar; estado y evidencia.

No se esperan cambios en `core/**/sqldelight/**`, `NoteLink.sq`/`embedding.sq`/
`.sqm`, repositorios del core, `gradle/libs.versions.toml`,
`composeApp/build.gradle.kts` ni `init.sh`. Esquema sigue **v5**.

## Visual Design Impact

- UI involved: yes.
- Design source: `DESIGN.md` (`provisional`) — dirección estructural y baseline de
  accesibilidad; sin tokens nuevos.
- Screens or states affected: nueva "Notas relacionadas" (Core Screen 8) con estado
  vacío y con resultados; botón de entrada en el editor.
- New design artifact required: no — se reutilizan `LabelChip`, `Card`, tipografía
  y espaciados. Se respeta "no sólo color" (el `score` y los chips son texto) y
  touch ≥48dp; el `score` se muestra como texto (p. ej. `0.92`), sin semáforo.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — superficie de lectura de Enlaces, wiring de
  `NoteLinkRepository`/`SemanticLinkingService` en los roots y política del trigger
  mínimo (recomputo al abrir).
- `CONSTRAINTS.md`: **not needed** — no introduce un MUST/MUST NOT nuevo; la
  política de trigger vive en `ARCHITECTURE.md`/`docs/technical-discovery.md`.
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola".
- Other docs: `docs/technical-discovery.md` — update (append "Linking Decisions":
  trigger best-effort al abrir + UI); `docs/risks-and-open-questions.md` — update
  (trigger parcialmente resuelto; queda el de ciclo de vida);
  `docs/domain-model.md`/`CONTEXT.md` — not needed (ya describen Enlaces);
  `PROGRESS.md`/`feature_list.json` — update al cerrar.

## Implementation Plan

1. Crear `RelatedNotes.kt` (`RelatedNote` + `buildRelatedNotes`) y su `@Test` puro.
2. Crear `RelatedNotesScreen.kt` (estado vacío + lista).
3. Extender `App.kt`: params, `NotesDestination.Related`, estado, `LaunchedEffect`
   con recomputo best-effort y navegación; pasar `onOpenRelated` desde el editor.
4. Agregar `onOpenRelated` + botón en `NoteEditorScreen.kt`.
5. Cablear los roots Android/iOS (`SqlDelightNoteLinkRepository` + service).
6. Actualizar docs durables; correr verificación, control negativo y smoke real de
   relacionadas; registrar evidencia.

## Implementation Tasks

- [x] Crear `RelatedNote` + `buildRelatedNotes` en `:core` (orden/desempate,
      exclusión, omisión, no-mutación).
- [x] Crear `RelatedNotesTest` (puro, `@Test` en `commonTest`).
- [x] Crear `RelatedNotesScreen` (header, estado vacío, tarjetas con score textual,
      navegación/Volver).
- [x] Extender `App.kt` (params, `NotesDestination.Related`, estado, recomputo
      best-effort, navegación).
- [x] Agregar `onOpenRelated` + botón en `NoteEditorScreen` (en fila propia; ver
      desvío en `docs/technical-discovery.md`).
- [x] Cablear `MainActivity` y `MainViewController` con `SqlDelightNoteLinkRepository`
      + `SemanticLinkingService`.
- [x] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md`,
      `docs/risks-and-open-questions.md`, `AGENTS.md`.
- [x] Correr verificación + smoke real (Android; iOS arranque) y registrar evidencia.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → `RelatedNotesTest` en verde +
  los previos (`NoteLinkRepository`, `SemanticLinking`, `CosineSimilarity`,
  `RagQuery`, etc.), 0 failures.
- `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` (macOS) → idem en iOS.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (UI + wiring Android).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL;
  `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug
  -sdk iphonesimulator build` → BUILD SUCCEEDED.
- Confirmar `Schema.version == 5` y sin `.sqm` nuevo; sin dependencias nuevas;
  `init.sh` sin cambios; `./init.sh` → exit 0, sin dev servers ni simuladores.
- **Control negativo**: invertir el orden (`score` asc) o incluir la propia Nota
  debe hacer fallar `RelatedNotesTest`; restaurar.
- **Smoke real Android** (emulador): sembrar Notas + filas `embedding` con vectores
  controlados (p. ej. idénticos → coseno 1.0); abrir una Nota → "Ver relacionadas"
  → ver la relacionada con su `score`, tocar y abrir, "Volver"; con una Nota sin
  embedding → estado vacío. (Emuladores pueden estar compartidos; declarar la
  limitación sin fabricar evidencia.)
- **Smoke iOS** (simulador): mismo camino verificado por OCR cuando no haya input
  automation; el mismo `commonMain` se ejercita en Android.
- E2E persistente: **no aplica** (no hay harness de UI/E2E y `init.sh` no levanta
  simuladores; la proyección se cubre con tests del core en dos plataformas y la UI
  con smoke manual). Se justifica explícitamente.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest`/`:core:iosSimulatorArm64Test` con
  `RelatedNotesTest` en verde (conteos por plataforma).
- Salida de `:composeApp:assembleDebug`, link del framework iOS y `xcodebuild`.
- `./init.sh` → exit 0, sin procesos de larga duración; `init.sh` sin cambios.
- `Schema.version == 5`, sin `.sqm` nuevo; build files/`libs.versions.toml` sin
  cambios.
- Control negativo reproducido y restaurado.
- Smoke real Android/iOS: relacionadas con score, navegación a la Nota y "Volver",
  estado vacío sin embedding; limitaciones declaradas honestamente.

## Validator Checklist

- [ ] Alcance respetado: sólo proyección + UI + wiring + recomputo best-effort al
      abrir; sin trigger de ciclo de vida, `NoteStatus`, RAG ni esquema.
- [ ] Los escenarios 1–6 pasan.
- [ ] `buildRelatedNotes` resuelve el otro extremo, excluye la propia Nota, ordena
      por `score` desc con desempate por `id`, omite ids ausentes y no muta.
- [ ] La pantalla muestra estado vacío y estado con resultados; comunica con texto
      (score/chips), no sólo color; touch ≥48dp; navegación y "Volver" funcionan.
- [ ] El recomputo al abrir es best-effort (un fallo no propaga ni bloquea).
- [ ] Esquema v5, sin `.sqm` nuevo ni dependencias nuevas; `init.sh` sin cambios.
- [ ] Evidencia en `feature_list.json`/`PROGRESS.md`; smoke declarado honestamente;
      no hay E2E persistente y la spec justifica por qué.
- [ ] `ARCHITECTURE.md` y `docs/technical-discovery.md` actualizados.
