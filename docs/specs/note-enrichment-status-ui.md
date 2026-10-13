# Feature Implementation Spec: Estado de enriquecimiento visible y re-indexado manual

## Source Feature

- `id`: `note-enrichment-status-ui`
- `area`: `ui`
- `depends_on`: `["ai-enrichment-classify-tags-type"]` (`accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json` (el `id` se conserva; el alcance se re-deriva)

> **Re-derivación de alcance (decidida por el planner).** El entry original habla
> de "corregir categoría/etiquetas/tipo"; la **clasificación IA** de
> `categoría`/`tipo` y las **etiquetas IA** están **descartadas** (`CONTEXT.md` →
> *Rejected / Ambiguous Terms*). El `track`/`Etiquetas` son **manuales** y ya se
> editan en `NoteEditorScreen` (`note-category-and-tags`, `create-text-note`,
> `accepted`): la corrección del usuario **ya está entregada** y la IA **nunca**
> escribe `track`/`tags`. Lo que falta y es coherente: (1) el `NoteStatus` nunca
> transiciona porque `App` llama `NoteIndexingService.index` best-effort (saltea
> `NoteIndexingOrchestrator`) → el chip queda en `capturada`; (2) el estado no se
> muestra con etiqueta legible ni en el editor; (3) no hay forma de **re-indexar**
> manualmente una Nota no indexada (`DESIGN.md` → pantalla "Estado de Indexado:
> pendiente/fallida + reintento"). Esta feature cierra esos huecos **sin**
> clasificación IA, **sin** reintento automático (`offline-pending-retry`) y
> **sin** cambios de esquema.

## Goal

Que la UI refleje de forma **verdadera y legible** el estado de indexado de cada
Nota y permita al usuario **disparar manualmente** el (re)indexado de una Nota no
indexada, garantizando que ese enriquecimiento **nunca pise** la clasificación
manual (`track`/`Etiquetas`) ni el `body`.

Concretamente: `App` deja de usar `NoteIndexingService.index` directo y pasa a
usar `NoteIndexingOrchestrator.index`, de modo que un guardado con `body` no
vacío transiciona `CAPTURED → INDEXED` (éxito) o `→ PENDING` (fallo) y refresca
la UI. La lista y el editor muestran un **badge de estado** textual ("no sólo
color"). El editor ofrece un botón **"Re-indexar"** para Notas `PENDING`/
`CAPTURED`/`FAILED` con `body` no vacío. Es una feature de `:composeApp` (+ un
test de regresión en `:core`); **sin** tabla, esquema ni migración nuevos
(esquema v5).

## Non-Goals

- **Clasificación IA** de `categoría`/`tipo` ni `Etiquetas` asignadas por IA
  (`CONTEXT.md`). La IA no clasifica ni etiqueta.
- **Reintento automático**, colas, scheduling, detección de conectividad y la
  transición `pendiente → fallida` (`offline-pending-retry`). Aquí sólo hay un
  re-indexado **manual**, disparado por el usuario.
- **Enlaces** persistentes / `note-links-ui` (`semantic-linking`) y citas de RAG
  (`rag-answer-citations`).
- Logging local de fallos (`enrichment-observability-logging`) y runtime real
  (`ai-runtime-decision`); se sigue usando `FakeAiClient`.
- Cambios en `:core` **más allá** del test de regresión del orquestador (no se
  toca `NoteIndexingService`/`NoteIndexingOrchestrator`/`NoteRepository` ni
  `GddScreen`), y cambios de esquema/migraciones (sigue v5, sin `.sqm` nuevo).

## Job Story

When reviso una Nota que capturé,
I want ver si su embedding está listo (o pendiente) y poder re-indexarla a mano,
so I can confiar en que aparece en el RAG/enlaces sin que el sistema toque el
`track` ni las Etiquetas que yo elegí.

## Users And Permissions

- Autor único (single-user local-first, MVP). Sin auth ni permisos nuevos.
- El `owner` lo provee la UI; el usuario es dueño total de `track`/`tags`/`body`.

## Acceptance Scenarios

### Scenario 1: guardar una Nota con texto la indexa y lo refleja

Given la app con `NoteIndexingOrchestrator` + `FakeAiClient`,
When el usuario crea una Nota con `body` no vacío y vuelve a la lista,
Then el chip de estado de esa Nota muestra **"indexada"** (no "capturada"), y la
fila `embedding` existe para esa Nota.

### Scenario 2: un fallo de IA deja la Nota "pendiente" y visible

Given una Nota persistida con `status = pendiente` (p. ej. sembrada) y `body` no
vacío,
When la lista/el editor la muestran,
Then el badge dice **"pendiente"** y el editor habilita **"Re-indexar"**.

### Scenario 3: re-indexado manual exitoso sin pisar la clasificación

Given una Nota `pendiente` con `track = personajes` y `tags = ["jefe"]`,
When el usuario toca "Re-indexar" y el runtime responde,
Then el estado pasa a **"indexada"** y `track`, `tags` y `body` quedan
**idénticos** (la corrección manual no se pisa).

### Scenario 4: una Nota ya indexada no ofrece re-indexar y no se degrada

Given una Nota `indexada`,
When el usuario abre el editor,
Then el badge dice **"indexada"** y **no** se muestra el botón "Re-indexar" (o
queda deshabilitado); un fallo hipotético de re-indexado no la degradaría (regla
del orquestador, ya vigente).

### Scenario 5: Nota sin texto (sólo imagen) no se indexa

Given una Nota con `body` en blanco (p. ej. sólo imagen),
When se guarda y el usuario abre el editor,
Then el estado sigue **"capturada"** y "Re-indexar" queda **deshabilitado** (no
hay texto que embeber).

### Scenario 6: sin cambio de esquema, ambas plataformas y gate honesto

Given los tests y la app,
When se corren `:core:testDebugUnitTest`, `:core:iosSimulatorArm64Test`,
`:composeApp:assembleDebug`, `:composeApp:linkDebugFrameworkIosSimulatorArm64` y
`./init.sh`,
Then todo pasa en Android/JVM e iOS, `PlaybookDatabase.Schema.version == 5`,
**no** aparece un `.sqm` nuevo y `./init.sh` termina en 0 sin dev servers ni
simuladores.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — SDD/WIP=1; `rag-query` dejó el indexado
  best-effort **sin** transicionar `NoteStatus` y remitió estados/reintento a
  esta feature y a `offline-pending-retry`.
- `feature_list.json` — metadata de esta feature (título/`user_visible_behavior`
  obsoletos: "categoría/etiquetas/tipo"); `depends_on` satisfecho; es edge case
  de `docs/domain-model.md`.
- `CONTEXT.md`, `docs/domain-model.md` — *Indexado* = embedding (+ Enlaces);
  "no clasifica ni etiqueta"; lifecycle `capturada → indexada/pendiente`; edge
  case "re-indexar no debe perder el `track` ni las etiquetas manuales".
- `CONSTRAINTS.md` — el pipeline **MUST NOT** clasificar/etiquetar ni bloquear la
  captura; `pendiente → fallida`/reintentos son de `offline-pending-retry`.
- `DESIGN.md` — componentes "Tarjeta de Nota"/"Badge de Estado"
  (`capturada`/`pendiente`/`indexada`/`fallida`); pantalla 7 "Estado de Indexado:
  pendiente/fallida + reintento"; regla "estados no comunicados sólo por color".
- `composeApp/.../app/App.kt` — hoistea estado y `NotesDestination`;
  `indexBestEffort(note)` llama `noteIndexingService.index` directo (best-effort,
  sin tocar `NoteStatus`, sin `reload()` al terminar).
- `composeApp/.../app/NotesListScreen.kt` — `NoteCard` ya muestra
  `LabelChip(text = note.status.code)` (texto crudo, sin badge designado).
- `composeApp/.../app/NoteEditorScreen.kt` — no muestra estado; edita
  `body`/`track`/`tags`; sin acción de re-indexado.
- `composeApp/src/{androidMain,iosMain}/.../{MainActivity,MainViewController}.kt`
  — composition roots: construyen `NoteIndexingService` + `RagQueryService` +
  `FakeAiClient` y los pasan a `App(...)`.
- `core/src/commonMain/.../ai/NoteIndexingOrchestrator.kt` — `index(note)` sólo
  llama `updateStatus` (no toca `body`/`track`/`tags`); ya `accepted`.
- `core/src/commonMain/.../model/NoteStatus.kt` — enum con `code` legible;
  `fromCode` fail-fast.
- `core/src/commonTest/.../ai/NoteIndexingOrchestratorCheck.kt` — escenarios 1–7
  del orquestador; patrón de stubs y de `createDatabase`.
- Esquema actual: `1..4.sqm`; `Schema.version == 5`. `init.sh` — gate no
  bloqueante, sin dev servers ni simuladores.

### Existing Patterns To Follow

- UI "tonta": estado/navegación hoisteado en `App`; pantallas reciben datos y
  emiten intenciones; sin DI/`ViewModel`/Flow/navegación.
- Refresco: `LaunchedEffect(refreshKey)` + `reload()` (incremento de
  `refreshKey`) tras cada mutación.
- Accesibilidad: chips/estados **textuales** ("no sólo color"); `clickable` con
  `onClickLabel` y `role = Role.Button` cuando corresponde.
- Asincronía en la UI con `rememberCoroutineScope()` (patrón de `rag-query`),
  sin introducir `ViewModel`/Flow.
- Dominio/persistencia/servicios en `:core`; tests compartidos en `commonTest`
  con un `SqlDriver` y `@Test` por plataforma con `runTest`.
- Documentos en español, términos técnicos en inglés.

### Current Gaps

- `App` saltea `NoteIndexingOrchestrator`: el `NoteStatus` queda siempre en
  `capturada` aunque exista embedding; el chip miente.
- No hay una etiqueta legible/`Badge de Estado` compartido; se usa `status.code`
  crudo en una sola pantalla y no aparece en el editor.
- No existe acción de UI para re-indexar una Nota no indexada (el servicio
  `suspend` sólo se dispara desde el guardado).
- No hay cobertura de regresión explícita de "re-indexar preserva
  `track`/`tags`/`body`" a nivel del orquestador.

## Technical Approach

### 1. Cablear el orquestador en la app (reemplaza el best-effort directo)

En `App`, reemplazar la dependencia `noteIndexingService: NoteIndexingService`
por `noteIndexingOrchestrator: NoteIndexingOrchestrator`. Reescribir
`indexBestEffort(note)` para llamar `noteIndexingOrchestrator.index(note)` y
disparar `reload()` **después** de que el `suspend` resuelva (hoy no refresca).
Se mantiene el contrato: `body` en blanco no se indexa; los fallos del runtime
no lanzan ni bloquean el guardado. Este es el único cambio de comportamiento de
datos y hace que el `NoteStatus` sea verdadero.

### 2. Estado legible y re-indexado manual en la UI

- Nuevo `composeApp/src/commonMain/kotlin/com/playbook/app/EnrichmentStatus.kt`:
  - `statusLabel(NoteStatus): String` → etiqueta en español
    (`CAPTURED`→"capturada", etc., alineado con `NoteStatus.code`).
  - `@Composable StatusBadge(status)` (un `Surface`/`LabelChip` con texto y
    forma, **nunca sólo color**), reutilizable por lista y editor.
- `NotesListScreen`: reemplazar el `LabelChip(text = note.status.code)` por
  `StatusBadge(note.status)` (mismo lugar, semántica textual).
- `NoteEditorScreen`: nuevos parámetros `initialStatus: NoteStatus?` y
  `onReindex: (() -> Unit)?`; en modo edición, mostrar `StatusBadge` + botón
  **"Re-indexar"** habilitado sólo si `status != INDEXED && body.isNotBlank()`.
  El estado es de sólo lectura; `track`/`tags` siguen editándose igual (la
  "corrección" ya existente).
- `App`: nueva función `reindex(note)` que llama al orquestador y hace
  `reload()`; pasa `initialStatus = note.status` y `onReindex` al editor en
  `NotesDestination.Edit`.

Re-indexado manual queda **deliberadamente** separado del automático: es un tap
del usuario, sin scheduling, sin conectividad, sin `FAILED` (frontera con
`offline-pending-retry`).

### 3. Composition roots y test de regresión

- `MainActivity`/`MainViewController`: construir
  `NoteIndexingOrchestrator(noteIndexingService, noteRepository)` y pasarlo a
  `App(...)` (además de seguir construyendo `NoteIndexingService` para el RAG).
- `NoteIndexingOrchestratorCheck` (`:core`, `commonTest`): agregar un escenario
  que indexe una Nota `PENDING` con `track`/`tags`/`body` conocidos y verifique
  que al quedar `INDEXED` esos campos (y `owner`/timestamps) quedan **idénticos**.

### 4. Sin esquema ni dependencias

Se reusa `NoteStatus` existente (esquema v5). No hay tablas, `.sqm`, ni
dependencias Gradle nuevas. Compose y `kotlinx-coroutines` ya están.

## Expected File Changes

- `composeApp/src/commonMain/kotlin/com/playbook/app/EnrichmentStatus.kt` —
  crear; `statusLabel` + `StatusBadge` (texto, no sólo color).
- `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt` —
  modificar; usar `StatusBadge(note.status)`.
- `composeApp/src/commonMain/kotlin/com/playbook/app/NoteEditorScreen.kt` —
  modificar; `initialStatus`/`onReindex` + badge + botón "Re-indexar".
- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt` — modificar;
  dependencia `NoteIndexingOrchestrator`, `indexBestEffort` con `reload()`,
  `reindex(note)`, props nuevas del editor.
- `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt` —
  modificar; construir y pasar el orquestador.
- `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt` —
  modificar; idem (campo singleton del orquestador).
- `core/src/commonTest/kotlin/com/playbook/core/ai/NoteIndexingOrchestratorCheck.kt`
  — modificar; escenario de regresión "re-indexar preserva `track`/`tags`/`body`".
- `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md` —
  modificar (ver abajo).
- `AGENTS.md` — modificar sólo la línea "Próxima feature en cola" al cierre.
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado, evidencia y
  limpieza de terminología obsoleta de esta feature.

No se esperan cambios en `:core` de producción (`NoteIndexingService`,
`NoteIndexingOrchestrator`, `NoteRepository`, esquema, `.sqm`), ni en `iosApp/**`,
`init.sh` ni `gradle/libs.versions.toml`. `GddScreen.kt` fuera de alcance.

## Visual Design Impact

- UI involved: yes.
- Design source: `DESIGN.md` (sección Components → "Badge de Estado"; Core
  Screens #7 "Estado de Indexado: pendiente/fallida + reintento").
- Screens or states affected: lista de Notas (badge de estado por tarjeta) y
  editor de Nota en modo edición (badge + acción "Re-indexar").
- Nuevo artefacto de diseño: **no** — se usan los tokens y componentes
  provisionales de `DESIGN.md` (chips `Surface`, tipografía `label`), sin
  inventar estilo. La regla "no sólo color" se cumple con texto/forma.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — la sección de indexado/UI: `App` ahora usa
  `NoteIndexingOrchestrator` (no el servicio directo), el `reload()` tras indexar,
  el `StatusBadge` y el re-indexado manual (frontera con `offline-pending-retry`).
- `CONSTRAINTS.md`: **update (acotado)** — regla durable: la UI de estado **MUST
  NOT** reintentar automáticamente, agendar ni detectar conectividad (manual
  sólo) y el enriquecimiento **MUST NOT** pisar `track`/`tags`/`body`; los estados
  visibles provienen de `NoteStatus`, no de clasificación IA.
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola".
- Other docs:
  - `docs/technical-discovery.md`: **update** — "Enrichment Status UI Decisions
    (note-enrichment-status-ui)": wiring del orquestador, `reload()`, badge,
    re-indexado manual, sin esquema nuevo.
  - `CONTEXT.md` / `docs/domain-model.md`: **not needed** — se apoya en el
    lifecycle y en *Indexado* ya documentados; no cambia la fuente de verdad.
  - `docs/risks-and-open-questions.md`: **not needed** (reintento/`fallida`
    siguen abiertos en `offline-pending-retry`).
  - `DESIGN.md`: **not needed** — el "Badge de Estado" y la pantalla #7 ya están
    descritos; no se definen tokens nuevos.
  - `feature_list.json` / `PROGRESS.md`: **update** al cerrar.
- **Limpieza de terminología:** al cerrar, actualizar el `title`,
  `user_visible_behavior` y `notes` de esta feature a "estado de enriquecimiento
  visible + re-indexado manual" (quitar "categoría/tipo" y "corrige etiquetas
  IA"), y actualizar su `verification` a la de esta spec, sin tocar otras
  entradas (WIP=1).

## Implementation Plan

1. Crear `EnrichmentStatus.kt` (`statusLabel` + `StatusBadge`).
2. Cablear `NoteIndexingOrchestrator` en `App` (reemplazar el servicio directo),
   `reload()` tras indexar y `reindex(note)`; ajustar los composition roots.
3. Aplicar `StatusBadge` en la lista y el editor; agregar el botón "Re-indexar"
   con su condición de habilitación.
4. Extender `NoteIndexingOrchestratorCheck` con la regresión de
   `track`/`tags`/`body`.
5. Actualizar docs durables; correr verificación + smoke + control negativo.
6. Registrar evidencia y limpiar la terminología de esta feature en
   `feature_list.json`/`PROGRESS.md`.

## Implementation Tasks

- [ ] Crear `EnrichmentStatus.kt` con `statusLabel` y `StatusBadge` (texto, no sólo color).
- [ ] Reemplazar `noteIndexingService` por `noteIndexingOrchestrator` en `App` y actualizar `indexBestEffort` (+ `reload()`).
- [ ] Agregar `reindex(note)` en `App` y pasarlo al editor.
- [ ] Construir y pasar el orquestador en `MainActivity` y `MainViewController`.
- [ ] Aplicar `StatusBadge` en `NotesListScreen` y en `NoteEditorScreen`.
- [ ] Agregar estado read-only + botón "Re-indexar" (habilitado si `status != INDEXED && body.isNotBlank()`) en el editor.
- [ ] Extender `NoteIndexingOrchestratorCheck` con la regresión de `track`/`tags`/`body`.
- [ ] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`, `AGENTS.md`.
- [ ] Correr verificación + smoke + control negativo y registrar evidencia.
- [ ] Actualizar `feature_list.json` (título/`user_visible_behavior`/`verification`/`notes`) y `PROGRESS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks`
  → BUILD SUCCESSFUL; el `NoteIndexingOrchestrator*Test` incluye la regresión de
  `track`/`tags`/`body`; 0 failures en ambas plataformas.
- `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks`
  → BUILD SUCCESSFUL (UI compartida compila en Android e iOS).
- Confirmar `PlaybookDatabase.Schema.version == 5` y que **no** aparece `.sqm`
  nuevo (`find core/src -name '*.sqm'` → `1..4.sqm`).
- `./init.sh` → exit 0; gate no bloqueante, sin dev servers ni simuladores, sin
  cambios en `init.sh`.
- **Smoke Android** (emulador): crear Nota con texto → la lista muestra
  "indexada" (y la fila `embedding` existe, verificable con `sqlite3`); sembrar
  una Nota con `status='pendiente'` → la lista/editor muestran "pendiente" y el
  editor habilita "Re-indexar"; tocar "Re-indexar" → pasa a "indexada"
  conservando `track`/`tags`; nota sin texto → "capturada" y "Re-indexar"
  deshabilitado; una Nota `indexada` no ofrece re-indexar.
- **Smoke iOS** (simulador): build/install/launch + verificación por OCR de los
  badges "indexada"/"pendiente" y del botón "Re-indexar"; la interacción CRUD no
  es automatizable en este entorno (limitación declarada, sin evidencia
  fabricada; igual que features previas).
- **Control negativo**: (a) hacer que `indexBestEffort` no llame al orquestador
  (volver al servicio directo) debe impedir que el estado pase a "indexada" en el
  smoke; (b) romper temporalmente la regresión de `track`/`tags` en el test
  (p. ej. pisar `track`) debe hacerla fallar; restaurar ambos y re-verificar en
  verde.
- E2E persistente: **no aplica** — el repo no tiene harness de UI/E2E, `init.sh`
  no levanta simuladores y `FakeAiClient` es determinista; la cobertura es de
  tests del core en dos plataformas + compilación + smoke manual. Se justifica
  explícitamente.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest`/`:core:iosSimulatorArm64Test` (nombres y
  conteos por plataforma, incluida la regresión nueva).
- Salida de `:composeApp:assembleDebug` y del link del framework iOS.
- `./init.sh` → exit 0; `Schema.version == 5`; `git diff` vacío en esquema/`.sqm`/
  `NoteIndexingService`/`NoteIndexingOrchestrator`/`iosApp`/`init.sh`.
- Smoke Android con capturas/UI dumps de: estado "indexada", "pendiente" +
  "Re-indexar" habilitado, resultado del re-indexado preservando `track`/`tags`,
  nota sin texto con acción deshabilitada.
- Smoke iOS con OCR de los badges/botón (y la limitación declarada).
- Control negativo reproducible y restaurado (wiring directo; regresión rota).

## Validator Checklist

- [ ] Alcance respetado: UI de estado + re-indexado **manual**; **sin**
      clasificación IA, categoría/tipo/etiquetas IA, reintento automático,
      scheduling, conectividad, enlaces ni citas.
- [ ] Escenarios de aceptación 1–6 pasan (smoke + tests).
- [ ] `App` usa `NoteIndexingOrchestrator` y `reload()` tras indexar; el estado
      mostrado es el real (`indexada` tras guardar texto).
- [ ] El re-indexado manual preserva `body`/`track`/`tags` (`owner`/timestamps) y
      `updateStatus` no re-sella `updated_at`.
- [ ] El estado se comunica con texto/forma (no sólo color) en lista y editor;
      "Re-indexar" sólo con `status != INDEXED && body` no vacío.
- [ ] Sin cambio de esquema: `Schema.version == 5`, sin `.sqm` nuevo; sin
      dependencias Gradle nuevas.
- [ ] `:core` de producción (`NoteIndexingService`/`Orchestrator`/`Repository`),
      `iosApp/**` e `init.sh` sin cambios.
- [ ] `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`
      actualizados; `AGENTS.md` sólo la línea de próxima feature.
- [ ] `feature_list.json` (título/`user_visible_behavior`/`verification`/`notes`)
      y `PROGRESS.md` actualizados; terminología "categoría/tipo" retirada de esta
      entrada.
- [ ] No hay E2E persistente y la spec justifica por qué.
- [ ] `init.sh` sin cambios, no bloqueante y sin procesos de larga duración.
