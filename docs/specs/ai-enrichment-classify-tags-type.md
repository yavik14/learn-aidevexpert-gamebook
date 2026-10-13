# Feature Implementation Spec: Indexado asíncrono y transiciones de `NoteStatus`

## Source Feature

- `id`: `ai-enrichment-classify-tags-type`
- `area`: `ai`
- `depends_on`: `["ai-client-interface", "ai-runtime-decision", "note-category-and-tags"]` (las tres `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json` (el `id` se conserva; el alcance se re-deriva)

> **Re-derivación de alcance (decidida por el planner).** El planeamiento original
> de esta feature asumía una **clasificación IA** de "categoría / tipo / tipo de
> nota" y etiquetas asignadas por IA. Eso está **descartado** del MVP
> (`CONTEXT.md` → *Rejected / Ambiguous Terms* → "Categoría / Tipo de nota";
> `docs/domain-model.md`; `docs/build-brief.md`). La Nota se clasifica **sólo**
> por un `Track` fijo elegido por el usuario y por `Etiquetas` libres manuales.
> La IA **no clasifica y no etiqueta**. Esta feature se re-encuadra al pipeline
> real: **indexado asíncrono** (generar/persistir el embedding) y las
> **transiciones de `NoteStatus`** que dependen de su resultado.

## Goal

Orquestar el **indexado asíncrono de una Nota** sobre lo ya construido —
`NoteIndexingService` (genera y persiste el embedding vía `AiClient`) y
`EmbeddingRepository`— y persistir la **transición de `NoteStatus`** derivada del
resultado: `capturada → indexada` con éxito, `capturada → pendiente` ante fallo
(la captura nunca se bloquea por IA), y `pendiente → indexada` en un intento
posterior exitoso. Es el eslabón que `embeddings-generation` deliberadamente no
cubrió (esa feature persistía el embedding **sin** tocar el estado de la Nota).

El entregable es de `:core` (servicio + un método de repositorio + tests). No
crea entidades nuevas, **no cambia el esquema** (`status` ya existe en `note`,
esquema v4) y **no** incluye UI ni wiring a la app.

### Decisión de alcance sobre `NoteStatus` (regla elegida y por qué)

El puerto `AiClient` sólo expone `AiClientException` **genérico** (sin
clasificación transitorio/permanente; ver `docs/adr/0001-ai-runtime-decision.md`)
y `IndexingResult.Failed` tampoco distingue la causa. Por lo tanto el
orquestador **no puede** separar "no disponible/transitorio" de "permanente".
Regla crisp y testeable que se adopta:

1. **Éxito** (`IndexingResult.Indexed`) → la Nota queda `INDEXED`.
2. **Fallo** (`IndexingResult.Failed`) y la Nota **no** estaba `INDEXED` → queda
   `PENDING` (reintentable).
3. **Fallo** y la Nota **ya estaba `INDEXED`** (re-indexado) → se queda
   `INDEXED`; el embedding previo permanece intacto y un re-indexado fallido no
   degrada una Nota ya indexada.

Consecuencia honesta: esta feature **no produce `FAILED`**. La promoción
`pendiente → fallida` requiere un presupuesto de reintentos/detección de
conectividad que pertenece a `offline-pending-retry` (fuera de alcance). El
`NoteStatus.FAILED` sigue existiendo en el enum pero no lo escribe este código.

**Los enlaces quedan FUERA de alcance.** El enlazado por similitud coseno y la
creación/actualización de filas `Enlace` los posee `semantic-linking` (ya
`not_started`, `depends_on: [embeddings-generation]`); **no existe** tabla ni
entidad `enlace`/`link` en el repo (verificado). Duplicarlos aquí violaría WIP=1 y
crearía una dependencia muerta. En consecuencia, `INDEXED` aquí significa
"embedding generado y persistido", alineado con el lifecycle de
`docs/domain-model.md` (`capturada → indexada: el embedding se generó con
éxito`); la parte "y sus Enlaces" de la definición de *Indexado* se materializa
en `semantic-linking`.

## Non-Goals

- **Clasificación IA**, `categoría`, `tipo`/`tipo de nota`, o etiquetas
  asignadas por IA. La IA no clasifica ni etiqueta (`CONTEXT.md`).
- Enlaces/relaciones por similitud coseno, umbrales, score o grafo
  (`semantic-linking`, `note-links-ui`) y RAG (`rag-query`).
- Reintentos, colas, scheduling, detección de conectividad y la transición
  `pendiente → fallida` (`offline-pending-retry`).
- UI del estado de indexado / correcciones (`note-enrichment-status-ui`) y
  logging local de fallos (`enrichment-observability-logging`).
- Wiring del orquestador a `App`/`MainActivity`/`MainViewController`, dispatch
  asíncrono o `CoroutineScope` de ciclo de vida (no hay superficie observable; la
  variante `suspend` del servicio ya permite correrlo fuera del camino de captura).
- Elegir runtime/proveedor/modelo/dimensión (`ai-runtime-decision`); se consume el
  `FakeAiClient` por defecto.
- Cambios de esquema o migraciones: `note.status` ya existe (esquema v4, sin
  `.sqm` nuevo).

## Job Story

When capturo o edito una Nota,
I want que se genere su embedding en segundo plano y que su estado refleje el
resultado,
so I can saber qué Notas están listas para enlazarse/consultarse sin que la
captura dependa nunca de la IA.

## Users And Permissions

- Desarrollador (autor): único actor; ejecuta Gradle/tests e inyecta
  `AiClient`/`NoteIndexingService`/`NoteRepository`. Sin usuarios finales ni
  permisos.
- MVP single-user local-first; `owner` lo provee el llamador. Sin auth.

## Acceptance Scenarios

### Scenario 1: `capturada` + éxito → `indexada`

Given una Nota persistida con `status = CAPTURED` y un `NoteIndexingOrchestrator`
con `FakeAiClient`,
When se corre `index(note)`,
Then el embedding queda persistido en `embedding` (fila para `note.id`), la Nota
en la base queda `INDEXED` y `index(note)` devuelve `INDEXED`.

### Scenario 2: fallo de IA no bloquea la captura → `pendiente`

Given una Nota `CAPTURED` y un `AiClient` de test que lanza `AiClientException`,
When se corre `index(note)`,
Then `index(note)` **no lanza**, devuelve `PENDING`, la Nota queda `PENDING` en la
base, y no se crea fila en `embedding`.

### Scenario 3: respuesta inválida del runtime → `pendiente`

Given un `AiClient` de test que devuelve `0` vectores, `>1` vectores o un vector
de dimensión `0`,
When se corre `index(note)` sobre una Nota `CAPTURED`,
Then devuelve `PENDING`, la Nota queda `PENDING` y no se persiste ninguna fila
(la respuesta inválida la detecta `NoteIndexingService` y se mapea a fallo).

### Scenario 4: `pendiente` + éxito → `indexada` (reintento)

Given una Nota persistida con `status = PENDING`,
When se corre `index(note)` con un `AiClient` que responde válidamente,
Then el embedding queda persistido y la Nota pasa a `INDEXED`.

### Scenario 5: `pendiente` + fallo → sigue `pendiente` (sin `FAILED`)

Given una Nota `PENDING`,
When se corre `index(note)` y el runtime vuelve a fallar,
Then la Nota permanece `PENDING` (idempotente) y **nunca** pasa a `FAILED` en esta
feature.

### Scenario 6: re-indexado exitoso de una Nota `indexada` → sigue `indexada`

Given una Nota `INDEXED` con embedding previo y `body` cambiado,
When se corre `index(note)` con éxito,
Then la Nota sigue `INDEXED`, sigue habiendo **una sola** fila por `note.id` y sus
`values` son los nuevos.

### Scenario 7: re-indexado fallido de una Nota `indexada` → no se degrada

Given una Nota `INDEXED` con embedding previo,
When se corre `index(note)` con un runtime que falla,
Then la Nota permanece `INDEXED`, el embedding previo queda **intacto** y no se
crea ni pisa fila alguna.

### Scenario 8: `updateStatus` toca sólo el estado

Given una Nota persistida con `body`/`track`/`tags`/`owner` y timestamps
conocidos,
When se llama `noteRepository.updateStatus(id, INDEXED)`,
Then cambia sólo `status`; `body`/`track`/`tags`/`owner`/`createdAt`/`updatedAt`
quedan **idénticos**; devuelve `true`. Un `id` inexistente devuelve `false` sin
lanzar.

### Scenario 9: sin cambio de esquema, ambas plataformas y gate honesto

Given los tests compartidos de los escenarios 1–8,
When se corren `:core:testDebugUnitTest`, `:core:iosSimulatorArm64Test`,
`:composeApp:assembleDebug`, `:composeApp:linkDebugFrameworkIosSimulatorArm64` y
`./init.sh`,
Then todo pasa en Android/JVM e iOS, `PlaybookDatabase.Schema.version == 4`, **no**
aparece un `.sqm` nuevo y `./init.sh` termina en 0 sin dev servers ni simuladores.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `embeddings-generation` es
  `accepted` y dejó `NoteStatus` fuera a propósito; `note-category-and-tags` y
  `image-capture-camera` `accepted` (esquema v4).
- `feature_list.json` — metadata de esta feature (título/`user_visible_behavior`
  obsoletos, con "categoría/tipo"); `depends_on` satisfecho; verificación
  provisional.
- `CONTEXT.md`, `docs/domain-model.md` — *Indexado* = embedding (+ Enlaces),
  "no clasifica ni etiqueta"; lifecycle de Nota
  (`capturada → indexada/pendiente`, `pendiente → indexada/fallida`); edge case
  "re-indexar no pierde track ni etiquetas".
- `docs/build-brief.md`, `docs/risks-and-open-questions.md`,
  `docs/technical-discovery.md` — offline-first, la captura nunca se bloquea por
  IA; `pendiente`/`fallida` y reintento pendientes; sin clasificación IA.
- `docs/adr/0001-ai-runtime-decision.md` — híbrido cloud-first detrás del puerto;
  `AiClientException` genérico; índice asíncrono tolerante a fallos.
- `docs/specs/embeddings-generation.md`, `docs/validations/embeddings-generation.md`
  — `NoteIndexingService`/`IndexingResult` ya existen y **no** tocan `NoteStatus`
  (decisión de scope documentada); la fuente de verdad de "tiene embedding" es la
  fila en `embedding`.
- `core/src/commonMain/kotlin/com/playbook/core/ai/NoteIndexingService.kt` —
  `suspend index(note): IndexingResult`; catch de `AiClientException` → `Failed`;
  respuesta inválida → `Failed`; nunca escribe si falla.
- `core/src/commonMain/kotlin/com/playbook/core/ai/AiClient.kt` — puerto con
  `AiClientException` genérico.
- `core/src/commonMain/kotlin/com/playbook/core/model/NoteStatus.kt` — enum
  `CAPTURED`/`PENDING`/`INDEXED`/`FAILED` con `code`/`fromCode`.
- `core/src/commonMain/kotlin/com/playbook/core/model/Note.kt` — `Note` incluye
  `status` (no nulo) y `tags`; `NoteDraft` default `CAPTURED`.
- `core/src/commonMain/kotlin/com/playbook/core/repository/NoteRepository.kt` +
  `SqlDelightNoteRepository.kt` — `update(note)` re-sella `updatedAt` y reemplaza
  `body`/`track`/`status`/`tags` (no sirve para una transición de estado sola).
- `core/src/commonMain/sqldelight/.../Note.sq` — tabla `note` (con `status`) y
  queries; sin `updateNoteStatus`.
- `core/src/commonTest/kotlin/com/playbook/core/ai/NoteIndexingCheck.kt` — patrón
  de stubs de `AiClient` (`RecordingAiClient`/`FailingAiClient`/`StubAiClient`) y
  runner con `createDatabase` + `runTest`.
- `core/src/{commonMain,...,androidUnitTest,iosTest}` — no hay otra
  implementación de `NoteRepository` fuera de `SqlDelightNoteRepository`.
- Esquema actual: `1.sqm` (v1→v2 `embedding`), `2.sqm` (v2→v3 `note_tag`),
  `3.sqm` (v3→v4 `attachment`); `Schema.version == 4`.
- `init.sh` — gate no bloqueante (`assembleDebug` + `testDebugUnitTest` + link iOS
  + `iosSimulatorArm64Test`); sin dev servers.

### Existing Patterns To Follow

- Dominio/persistencia/servicios en `:core`; sin DI, dependencias por constructor.
- Servicios `suspend`, tests compartidos en `commonTest` con un `SqlDriver` y
  `@Test` por plataforma con `runTest`.
- `NoteRepository` devuelve `rowsAffected > 0` en mutaciones; queries SQLDelight
  con `INTEGER`→`Long` y snake_case.
- Tests con `AiClient` stubs deterministas y sin red; `FakeAiClient` por defecto.
- Documentos en español, términos técnicos en inglés.

### Current Gaps

- No existe orquestación que derive `NoteStatus` del resultado del indexado;
  `NoteIndexingService` lo omite por diseño.
- `NoteRepository` no tiene una transición de estado aislada (sólo `update(note)`,
  que pisa contenido y `updatedAt`).
- No hay query `updateNoteStatus`.
- La terminología de esta feature en `feature_list.json` (y de features vecinas)
  quedó anclada a la clasificación IA descartada.

## Technical Approach

### 1. Transición de estado aislada en el repositorio (sin cambio de esquema)

Nueva query en `Note.sq` (sólo query; **no** cambia el esquema):

```sql
updateNoteStatus:
UPDATE note SET status = ? WHERE id = ?;
```

Nuevo método en `NoteRepository`:

```kotlin
/** Actualiza sólo el `status` de la Nota (no toca body/track/tags/updated_at). */
fun updateStatus(id: String, status: NoteStatus): Boolean
```

`SqlDelightNoteRepository.updateStatus` mapea a
`database.noteQueries.updateNoteStatus(status.code, id).value > 0`. **No**
re-sella `updated_at`: el indexado es un efecto de sistema, no una edición del
usuario; además así una indexación en segundo plano no reordena la lista
(`selectAllNotes` ordena por `updated_at DESC`). No toca `body`/`track`/`tags`/
`owner`/`created_at`.

### 2. Orquestador de indexado asíncrono

Nuevo `com.playbook.core.ai.NoteIndexingOrchestrator`:

```kotlin
class NoteIndexingOrchestrator(
    private val indexingService: NoteIndexingService,
    private val noteRepository: NoteRepository,
) {
    /** Indexa [note] y persiste la transición de `NoteStatus`. Devuelve el estado resultante. */
    suspend fun index(note: Note): NoteStatus {
        val result = indexingService.index(note)               // nunca lanza por fallo de IA
        val target = when {
            result is IndexingResult.Indexed -> NoteStatus.INDEXED
            note.status == NoteStatus.INDEXED -> NoteStatus.INDEXED // no degradar lo ya indexado
            else -> NoteStatus.PENDING
        }
        noteRepository.updateStatus(note.id, target)
        return target
    }
}
```

- Reutiliza `NoteIndexingService` **sin modificarlo** (código `accepted`); el
  orquestador sólo lee su `IndexingResult` y añade la transición.
- No escribe `FAILED` (ver "Decisión de alcance").
- `index` no propaga fallos de IA: `NoteIndexingService` ya los convierte en
  `Failed`; el orquestador los mapea a `PENDING`. Depende del contrato del puerto
  (los adaptadores mapean sus fallos a `AiClientException`).
- Es `suspend` y no agenda nada: el llamador decide cuándo invocarlo (fuera del
  camino de captura). El scheduling/reintento es `offline-pending-retry`.

### 3. Tests

- `NoteIndexingOrchestratorCheck.kt` (`commonTest`): helper que recibe un
  `SqlDriver`, arma `createDatabase` + `SqlDelightNoteRepository` +
  `SqlDelightEmbeddingRepository`, y ejerce los escenarios 1–7 con stubs de
  `AiClient` (exitoso con `FakeAiClient`, fallo con `AiClientException`, y
  respuestas inválidas), reutilizando el estilo de `NoteIndexingCheck`.
- Extender `NoteRepositoryCheck.kt` con el Scenario 8 (`updateStatus`).
- Runners `@Test` en `androidUnitTest` (`JdbcSqliteDriver.IN_MEMORY`) e `iosTest`
  (`inMemoryDriver`), con `runTest`.

## Expected File Changes

- `core/src/commonMain/sqldelight/com/playbook/core/db/Note.sq` — modificar;
  agregar la query `updateNoteStatus` (sin cambio de esquema).
- `core/src/commonMain/kotlin/com/playbook/core/repository/NoteRepository.kt` —
  modificar; agregar `updateStatus(id, status)`.
- `core/src/commonMain/kotlin/com/playbook/core/repository/SqlDelightNoteRepository.kt`
  — modificar; implementar `updateStatus` (sólo `status`).
- `core/src/commonMain/kotlin/com/playbook/core/ai/NoteIndexingOrchestrator.kt` —
  crear; orquestador + transición de estado.
- `core/src/commonTest/kotlin/com/playbook/core/ai/NoteIndexingOrchestratorCheck.kt`
  — crear; helper de escenarios 1–7.
- `core/src/commonTest/kotlin/com/playbook/core/repository/NoteRepositoryCheck.kt`
  — modificar; cubrir `updateStatus` (Scenario 8).
- `core/src/androidUnitTest/kotlin/com/playbook/core/ai/NoteIndexingOrchestratorAndroidTest.kt`
  — crear; `@Test` JVM (`runTest`).
- `core/src/iosTest/kotlin/com/playbook/core/ai/NoteIndexingOrchestratorIosTest.kt`
  — crear; `@Test` iOS (`runTest`).
- `ARCHITECTURE.md`, `docs/technical-discovery.md` — modificar; decisiones y
  estado.
- `CONSTRAINTS.md` — modificar (acotado); regla durable MUST NOT (ver abajo).
- `AGENTS.md` — modificar sólo la línea "Próxima feature en cola" al cierre.
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado, evidencia y
  limpieza de terminología de esta feature.

No se esperan cambios en `:composeApp`, `iosApp/**`, `init.sh`,
`gradle/libs.versions.toml`, `NoteIndexingService.kt`, `AiClient.kt`,
`embedding.sq` ni en ningún `.sqm`.

## Visual Design Impact

- UI involved: no.
- Design source: not applicable.
- Screens or states affected: ninguna (la UI de estado es
  `note-enrichment-status-ui`, fuera de alcance).
- New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — sección AI: nuevo `NoteIndexingOrchestrator` y
  la regla de transición de `NoteStatus` (éxito→`INDEXED`; fallo→`PENDING` salvo
  Nota ya `INDEXED`; sin `FAILED` aquí), `NoteRepository.updateStatus` y que no
  cambia el esquema (v4).
- `CONSTRAINTS.md`: **update (acotado)** — agregar un MUST NOT durable: el
  pipeline de indexado **no** clasifica ni etiqueta la Nota y **no** bloquea la
  captura; la transición `pendiente → fallida` y los reintentos son de
  `offline-pending-retry`. Aplica a features que dependen de esta
  (`offline-pending-retry`, `note-enrichment-status-ui`).
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola"
  (práctica del repo); no cambian workflow, arranque ni gate.
- Other docs:
  - `docs/technical-discovery.md`: **update** — "Indexing Decisions
    (ai-enrichment-classify-tags-type)": regla de estado, `updateStatus` sin
    `updated_at`.
  - `docs/domain-model.md` / `CONTEXT.md`: **not needed** — la spec se apoya en el
    lifecycle (`capturada → indexada: el embedding se generó con éxito`); los
    Enlaces se materializan en `semantic-linking`.
  - `docs/risks-and-open-questions.md`: **not needed** (no cambia un riesgo:
    reintento/`fallida` siguen abiertos en `offline-pending-retry`).
  - `feature_list.json` / `PROGRESS.md`: **update** al cerrar (estado y evidencia).
- **Limpieza de terminología (durable, fuera del diff de código):** el `id` de
  esta feature conserva el nombre obsoleto, pero el título y
  `user_visible_behavior` en `feature_list.json` se actualizan en esta corrida de
  planning a "indexado asíncrono + estado". Quedan ancladas a la clasificación IA
  descartada —y deben re-derivarse al planificarse— las entradas:
  `note-enrichment-status-ui` ("corregir categoría/etiquetas/tipo"),
  `gdd-view` ("agrupa y resume por categoría/etiqueta"), `offline-pending-retry`
  ("notas pendientes se enriquecen") y `enrichment-observability-logging`
  ("fallos de enriquecimiento"). No se tocan en esta corrida (WIP=1); se registran
  aquí para no perderlas.

## Implementation Plan

1. Agregar `updateNoteStatus` a `Note.sq` y `updateStatus` a `NoteRepository` +
   `SqlDelightNoteRepository`; confirmar que el esquema sigue en v4 y sin `.sqm`.
2. Crear `NoteIndexingOrchestrator` con la regla de transición decidida.
3. Escribir el helper `NoteIndexingOrchestratorCheck` (escenarios 1–7) y extender
   `NoteRepositoryCheck` (Scenario 8); agregar los `@Test` Android/iOS con
   `runTest`.
4. Actualizar docs durables (`ARCHITECTURE.md`, `docs/technical-discovery.md`,
   `CONSTRAINTS.md`, `AGENTS.md`).
5. Correr la verificación + control negativo, registrar evidencia y limpiar la
   terminología de esta feature en `feature_list.json`/`PROGRESS.md`.

## Implementation Tasks

- [x] Agregar la query `updateNoteStatus` en `Note.sq` (sin cambio de esquema).
- [x] Agregar `updateStatus` a `NoteRepository` e implementarlo en `SqlDelightNoteRepository`.
- [x] Crear `NoteIndexingOrchestrator` con la regla de transición (sin `FAILED`).
- [x] Escribir `NoteIndexingOrchestratorCheck` (escenarios 1–7) y sus `@Test` Android/iOS.
- [x] Extender `NoteRepositoryCheck` con el Scenario 8 (`updateStatus`).
- [x] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`, `AGENTS.md`.
- [x] Correr la verificación + control negativo y registrar evidencia.
- [x] Actualizar el título/`user_visible_behavior`/notas de esta feature en `feature_list.json` y `PROGRESS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → nuevos tests
  (`NoteIndexingOrchestratorAndroidTest`, `updateStatus` en
  `NoteRepositoryAndroidTest`) en verde + los previos (`NoteIndexing` 1/1,
  `EmbeddingRepository` 1/1, `NotePersistence` 2/2, `NoteRepository` 1/1,
  `AiClient` 3/3, `Greeting` 1/1), 0 failures.
- `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` (macOS) → mismos tests en
  verde en iOS, 0 failures.
- `./gradlew :composeApp:assembleDebug` y
  `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
- Confirmar `PlaybookDatabase.Schema.version == 4` y que **no** aparece un `.sqm`
  nuevo (`find core/src -name '*.sqm'`).
- `./init.sh` → exit 0; gate no bloqueante, sin dev servers ni simuladores, sin
  cambios en `init.sh`.
- **Control negativo**: forzar al orquestador a marcar siempre `INDEXED` (ignorar
  `Failed`) debe hacer fallar los escenarios 2/3/7; restaurar. Un cambio que
  re-selle `updated_at` dentro de `updateStatus` debe hacer fallar el Scenario 8.
- E2E persistente: **no aplica**. No hay flujo de usuario/API observable (sin UI
  ni wiring) ni harness E2E; `init.sh` no levanta simuladores. La cobertura es de
  tests del core en dos plataformas + compilación de la app. Se justifica
  explícitamente.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` con los
  nuevos tests en verde (nombres y conteos por plataforma).
- Salida de `:composeApp:assembleDebug` y
  `:composeApp:linkDebugFrameworkIosSimulatorArm64`.
- `./init.sh` → exit 0, sin procesos de larga duración.
- `PlaybookDatabase.Schema.version == 4`; sin `.sqm` nuevo; `NoteIndexingService.kt`
  sin cambios (`git diff` vacío).
- Control negativo reproducido y restaurado (orquestador marca `INDEXED` siempre;
  `updateStatus` re-sella `updated_at`).
- Confirmación de que no se tocaron `:composeApp`/`iosApp`/`init.sh`/
  `libs.versions.toml` ni `embedding.sq`/`1.sqm`.
- Confirmación de que no hay clasificación IA/etiquetas IA ni entidades de enlace
  nuevas en el diff.

## Validator Checklist

- [ ] Alcance respetado: orquestación + transiciones de `NoteStatus`; **sin**
      clasificación IA, categoría/tipo, etiquetas IA, enlaces, UI, reintentos ni
      runtime real.
- [ ] Escenarios de aceptación 1–9 pasan.
- [ ] La regla de estado es la de la spec: éxito→`INDEXED`; fallo→`PENDING` salvo
      Nota ya `INDEXED` (que no se degrada); **no** se escribe `FAILED`.
- [ ] `updateStatus` cambia sólo `status` (no `body`/`track`/`tags`/`owner`/
      `created_at`/`updated_at`) y devuelve `false` ante `id` inexistente.
- [ ] `NoteIndexingService`/`AiClient`/`EmbeddingRepository` reutilizados sin
      modificar; `index` no propaga fallos de IA.
- [ ] `Schema.version == 4` y sin `.sqm` nuevo; no hay tabla/entidad `enlace`.
- [ ] `ARCHITECTURE.md`, `docs/technical-discovery.md` y `CONSTRAINTS.md`
      actualizados; `AGENTS.md` sólo cambia la línea de próxima feature.
- [ ] `feature_list.json` (título/`user_visible_behavior`/notas) y `PROGRESS.md`
      actualizados; la terminología obsoleta de las features vecinas queda
      registrada para re-derivarse.
- [ ] No hay E2E persistente y la spec justifica por qué.
- [ ] `init.sh` sin cambios, no bloqueante y sin procesos de larga duración.
