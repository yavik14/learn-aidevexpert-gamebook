# Feature Implementation Spec: Embeddings reales en el indexado

## Source Feature

- `id`: `ai-real-embeddings`
- `area`: `ai`
- `depends_on`: `["ai-fake-real-switch", "offline-pending-retry"]` (ambas `accepted`)
- `status`: `not_started` (al planificar)
- `source`: `feature_list.json`

> **Hallazgo de alcance (verificado).** `ai-fake-real-switch` **ya** entregó el build
> flag, `AiRuntime`/`openAiAiRuntime`, `OpenAiAiClient.embed`,
> `UrlConnectionHttpTransport`, el wiring en `MainActivity`/`MainViewController`
> (`NoteIndexingService`/`RagQueryService`/`PendingIndexingRetry` usan el runtime
> activo) y `EmbeddingDimensionReconciler`. **Esta feature NO duplica** flag,
> adaptadores OpenAI, transporte ni wiring. El gap es: **(a)** no se valida la
> dimensión devuelta por el proveedor antes de persistir; **(b)** no hay cobertura de
> integración del camino real con `HttpTransport` falso; **(c)** el smoke real no
> ejercita el indexado.

## Goal

Que el indexado con runtime real produzca embeddings de la dimensión configurada
(`1536`), los persista de forma segura y alimente Enlaces/RAG, sin romper la máquina
`PENDING`/reintento de `offline-pending-retry`. Un embedding de dimensión inesperada
no se persiste: la Nota queda `PENDING` y el reintento consume presupuesto hasta
`FAILED` según las reglas existentes. `fake` sigue siendo el default y el único modo
de tests/gate.

## Non-Goals

- Reimplementar flag, `AiRuntime`, `HttpTransport`, adaptadores de otros modelos ni
  wiring ya existentes.
- `ai-real-chat`, transcribe, tts, image, `ai-suggest-classification` o UI nueva.
- Cambiar `PENDING`/`FAILED`/presupuesto de `offline-pending-retry`.
- Sonda de conectividad, backoff, batching, cacheo o scheduling.
- Recomputo automático de Enlaces (se verifica el consumo, no el trigger).
- Esquema/`.sqm`, `init.sh`, `App.kt`, `iosApp/**`, IA real en iOS u otros worktrees.

## Job Story

When corro la app en modo `real` y guardo o reintento una Nota,
I want que el indexado use el embedding real y valide su dimensión antes de persistir,
so I can tener Enlaces/RAG reales sin corromper la base ni romper el ciclo
offline/reintento.

## Users And Permissions

- Desarrollador (autor): configura `playbook.ai.mode=real` y la key fuera del repo.
- Tests/gate: actor implícito; siempre fake, sin red ni key.

## Acceptance Scenarios

### Scenario 1: fake default y gate intactos

Given el repo sin flags de IA
When se corre `CI=true ./init.sh`
Then usa `fakeAiRuntime()` (dimensión `8`), sin red ni key, y termina en `0`.

### Scenario 2: indexado real exitoso con transporte falso

Given `AiRuntimeConfig(embeddingDimension = 1536)` y un `HttpTransport` falso que
responde `POST /embeddings` con un vector de 1536 valores
When `NoteIndexingOrchestrator.index(note)` indexa una Nota `CAPTURED`
Then queda `INDEXED`, se persiste un embedding de 1536 valores y no se escribe `FAILED`.

### Scenario 3: dimensión inesperada no se persiste

Given el runtime real a 1536 y un transporte que devuelve 8 valores
When se indexa una Nota
Then `IndexingResult.Failed` con `AiClientException` sanitizada, la Nota queda
`PENDING`, no hay fila en `embedding` y el mensaje no contiene key ni body.

### Scenario 4: re-indexado fallido no degrada una Nota indexada

Given una Nota `INDEXED` con embedding real de 1536
When se re-indexa y el proveedor falla (red/HTTP 500) o devuelve dimensión incorrecta
Then sigue `INDEXED` y el embedding previo queda intacto.

### Scenario 5: RAG alimentado por embeddings reales

Given dos Notas indexadas con vectores reales de 1536 y un transporte que responde
también `/chat/completions`
When `RagQueryService.ask(question)` corre
Then devuelve `RagAnswer.Answer` con `sources` no vacías; si la pregunta no puede
embeberse, devuelve `Failed` sin inventar respuesta.

### Scenario 6: Enlaces alimentados por embeddings reales

Given las mismas Notas con vectores reales idénticos
When `SemanticLinkingService.recomputeAll()` corre
Then escribe un `note_link` (score `1.0`, umbral `0.8`) sin tocar estados ni
embeddings.

### Scenario 7: fallo de red conserva `PENDING` y presupuesto

Given un transporte que lanza timeout/IO o responde 500
When se indexa y luego corre `PendingIndexingRetry.retryPending(budget = 2)`
Then el indexado directo da `PENDING` sin lanzar; el primer reintento deja `PENDING`
con `index_attempts = 1`; el segundo promueve a `FAILED` con `index_attempts = 2`;
nunca queda un embedding huérfano.

### Scenario 8: fallo transitorio se recupera en el reintento

Given un transporte que falla la primera llamada a `/embeddings` y luego responde bien
When corre un reintento de la Nota `PENDING`
Then queda `INDEXED`, `index_attempts = 0` y el embedding de 1536 se persiste.

### Scenario 9: reconciliación fake (8) → real (1536)

Given una Nota `INDEXED` con embedding de 8 (fake) y otra con embedding de 1536
When `EmbeddingDimensionReconciler.reconcile(1536)` corre y el reintento indexa
Then borra sólo el de 8, deja esa Nota `PENDING`/`index_attempts = 0`, no toca la
compatible, y el reintento la deja `INDEXED` con 1536; RAG vuelve a responder.

### Scenario 10: smoke real key-gated (fuera del gate)

Given key real en `local.properties` (gitignored) y `PLAYBOOK_AI_SMOKE=1`
When corre el smoke manual con build real
Then el indexado persiste dimensión = `runtime.embeddingDimension` (1536), la Nota
queda `INDEXED`, RAG devuelve fuentes y la salida sólo registra longitudes/estado.

### Scenario 11: no-regresión

Given el diff de la feature
Then no cambian `AiClient`/`FakeAiClient`/`AiRuntime`/`HttpTransport`, adaptadores de
otros modelos, esquema/`.sqm`, `init.sh`, `App.kt`, `iosApp/**` ni las reglas de
`offline-pending-retry`; el link iOS compila (iOS sigue fake).

## Repository Research

### Files Inspected

- `core/.../ai/openai/OpenAiAdapters.kt` — `OpenAiAiClient.embed` ordena por `index`
  y exige 1 vector por texto/no vacío, pero **no** valida longitud contra
  `config.embeddingDimension`.
- `core/.../ai/NoteIndexingService.kt` — exige 1 vector no vacío, persiste y mapea
  `AiClientException` a `Failed`; no conoce dimensión esperada.
- `core/.../ai/{NoteIndexingOrchestrator,PendingIndexingRetry,
  EmbeddingDimensionReconciler,RagQueryService}.kt` — máquina de estados, presupuesto,
  reconciliación y RAG ya implementados; no se reescriben.
- `core/.../ai/openai/{AiRuntime,AiRuntimeConfig,OpenAiHttp,HttpTransport}.kt` —
  runtime real, `embeddingDimension = 1536` y transporte inyectable; `OpenAiHttp` ya
  mapea red/HTTP/JSON a `AiClientException`.
- `core/src/commonTest/.../ai/openai/OpenAiAdaptersCheck.kt` y
  `.../ai/{NoteIndexing*,PendingIndexingRetry*,EmbeddingDimensionReconciler,RagQuery}Check.kt`
  — cubren adaptador y máquina con stubs/fake; no ejecutan el adaptador real en el
  pipeline.
- `composeApp/src/{androidMain,iosMain}/.../MainActivity.kt`/`MainViewController.kt`
  — roots ya usan el runtime activo y reconcilian; falta pasar la dimensión al servicio.
- `composeApp/src/androidUnitTest/.../ai/RealAiSmokeTest.kt` — smoke key-gated de los 6
  modelos; mide dimensión, pero no indexa ni consulta RAG.
- `init.sh`, `ARCHITECTURE.md`, `CONSTRAINTS.md`, `docs/technical-discovery.md`
  (§ AI Real Switch Decisions), `docs/adr/0004-*`.

### Existing Patterns To Follow

- Puertos/adaptadores en `:core`; red sólo detrás de `HttpTransport`; tests con
  transporte falso y `runTest`, sin red.
- Tests de pipeline en `commonTest` sobre `SqlDriver` in-memory, con runners
  Android/JVM e iOS.
- `NoteIndexingService` reporta fallos a `Diagnostics` sin body ni datos sensibles.
- Gate Android-only: `assembleDebug` + `:core:testDebugUnitTest`; `init.sh` fuerza fake.
- Smoke real key-gated (`PLAYBOOK_AI_SMOKE=1`), fuera del gate, salida sanitizada.

### Current Gaps

- Falta el contrato de dimensión en adaptador y en el límite de persistencia.
- No hay test que conecte adaptador real + orquestador + retry + reconciliación +
  Enlaces/RAG sin red.
- El smoke real no prueba el indexado ni la persistencia.

## Technical Approach

1. **Adaptador:** en `OpenAiAiClient.embed`, tras ordenar y validar cantidad, exigir
   que cada vector mida `config.embeddingDimension`; si no, lanzar
   `AiClientException("embedding devolvió dimensión <n>, se esperaba <m>")` (sin key,
   headers ni body). Esto cubre también la pregunta de RAG.
2. **Persistencia:** agregar a `NoteIndexingService` `expectedDimension: Int? = null`
   (default conserva comportamiento; `require` > 0). Si no es `null` y no coincide,
   devolver `IndexingResult.Failed`, registrar `AI_INDEXING` y **no persistir**. Los
   roots pasan `aiRuntime.embeddingDimension` (1536 real / 8 fake).
3. **Integración:** nuevo check `commonTest` con `openAiAiRuntime(config, fakeTransport)`
   conectado a repos SQLDelight in-memory, orquestador, retry, reconciler,
   `SemanticLinkingService` y `RagQueryService`. El transporte falso responde
   `/embeddings` (1536) y `/chat/completions`, y permite timeout, 500, dimensión
   incorrecta y fallo transitorio.
4. **Smoke:** extender `RealAiSmokeTest` con indexado real key-gated sobre una base
   in-memory (dim persistida, `INDEXED`, RAG con fuentes) y agregar
   `sqldelight.sqlite.driver` como dependencia **test-only** de `composeApp`.

## Expected File Changes

- `core/.../ai/openai/OpenAiAdapters.kt` — modify; guard de dimensión en `embed`.
- `core/.../ai/NoteIndexingService.kt` — modify; `expectedDimension` opcional.
- `composeApp/src/androidMain/.../MainActivity.kt` — modify; pass-through de dimensión.
- `composeApp/src/iosMain/.../MainViewController.kt` — modify; mismo pass-through.
- `core/src/commonTest/.../ai/openai/OpenAiAdaptersCheck.kt` — modify; mismatch.
- `core/src/commonTest/.../ai/NoteIndexingCheck.kt` — modify; guard/default.
- `core/src/commonTest/.../ai/RealEmbeddingIndexingCheck.kt` — create; integración.
- `core/src/androidUnitTest/.../ai/RealEmbeddingIndexingAndroidTest.kt` — create.
- `core/src/iosTest/.../ai/RealEmbeddingIndexingIosTest.kt` — create.
- `composeApp/src/androidUnitTest/.../ai/RealAiSmokeTest.kt` — modify; smoke indexado.
- `composeApp/build.gradle.kts` — modify **sólo** `androidUnitTest` con
  `libs.sqldelight.sqlite.driver`; sin tocar el bloque de IA.
- Docs: `ARCHITECTURE.md`, `CONSTRAINTS.md`, `docs/technical-discovery.md`,
  `AGENTS.md` (sólo próxima feature), `feature_list.json`, `PROGRESS.md`.

**Protegidos (NO cambiar):** `init.sh`, `commonMain/sqldelight/**` y `.sqm`,
`AiClient.kt`, `FakeAiClient.kt`, `AiRuntime.kt`, `HttpTransport.kt`, `OpenAiAdapters.kt`
fuera de `embed`, `AiRuntimeConfig.kt`, `OpenAiHttp.kt`, `NoteIndexingOrchestrator.kt`,
`PendingIndexingRetry.kt`, `EmbeddingDimensionReconciler.kt`, `RagQueryService.kt`,
`App.kt`, `iosApp/**`, bloque `playbook.ai.*` de `composeApp/build.gradle.kts` y otros
worktrees.

## Visual Design Impact

- UI involved: no. Design source: not applicable. Screens/states: ninguno (badges ya
  existen). New design artifact: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — contrato de dimensión y camino real indexado/RAG/Enlaces.
- `CONSTRAINTS.md`: **update (acotado)** — un embedding real **MUST** medir
  `playbook.ai.embeddingDimension`; un mismatch **MUST NOT** persistirse y **MUST**
  seguir `PENDING`/reintento.
- `AGENTS.md`: **update (acotado)** — sólo la línea de próxima feature al integrar.
- `docs/technical-discovery.md`: **update** — sección “AI Real Embeddings Decisions”.
- `docs/adr/0004-*`, `CONTEXT.md`, `docs/domain-model.md`: **not needed**.
- `feature_list.json`/`PROGRESS.md`: **update** al cerrar.

**Duración/impacto:** ~1 sesión. Diff de producción pequeño (2 guards + pass-through);
el volumen es test de integración/smoke. Impacto visible: ninguno; verificación:
medio-alto.

## Implementation Plan

1. Guard de dimensión en `OpenAiAiClient.embed` + tests de adaptador.
2. `expectedDimension` en `NoteIndexingService`, pass-through en roots y tests.
3. Check de integración con transporte falso (éxito, RAG, Enlaces, red, retry,
   transitorio, mismatch, reconciliación 8→1536) + runners.
4. Smoke real key-gated + dep test-only.
5. Verificación/controles negativos y docs/estado.

## Implementation Tasks

- [ ] Validar dimensión por vector en `OpenAiAiClient.embed` (mensaje sanitizado).
- [ ] Agregar `expectedDimension` opcional a `NoteIndexingService` (`require` > 0).
- [ ] Pasar `aiRuntime.embeddingDimension` en Android/iOS roots.
- [ ] Tests de mismatch en `OpenAiAdaptersCheck` y guard/default en `NoteIndexingCheck`.
- [ ] Check de integración real+fake transport + runners Android/iOS.
- [ ] Extender `RealAiSmokeTest` con indexado real y dep test-only.
- [ ] Correr gate, tests dirigidos, link iOS y controles negativos.
- [ ] Actualizar docs durables, `feature_list.json` y `PROGRESS.md`.

## Verification Plan

- `CI=true ./init.sh` → exit 0, fake, sin red/key.
- `./gradlew :core:testDebugUnitTest --rerun-tasks` → nuevos tests en verde.
- `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` (opcional) → runner iOS verde.
- `./gradlew :composeApp:assembleDebug` y `:composeApp:linkDebugFrameworkIosSimulatorArm64`
  → BUILD SUCCESSFUL.
- `PLAYBOOK_AI_MODE=real PLAYBOOK_AI_API_KEY=dummy CI=true ./init.sh` → sigue fake y
  `AI_API_KEY` vacío.
- **Smoke real (fuera del gate):** con key en `local.properties`,
  `PLAYBOOK_AI_SMOKE=1 ./gradlew :composeApp:testDebugUnitTest --tests
  "com.playbook.app.ai.RealAiSmokeTest" -Pplaybook.ai.mode=real
  -Pplaybook.ai.smoke=1`; debe reportar dimensión 1536, `INDEXED`, `sources >= 1`, sin
  key ni bodies. Sin key, declararlo y no fabricar evidencia.
- **Controles negativos** (restaurar byte-idéntico): (a) quitar el guard del adaptador
  → falla el test de mismatch; (b) no pasar `expectedDimension` → falla el límite de
  persistencia; (c) forzar 500 → el test verifica `PENDING`/presupuesto.
- E2E persistente: **no aplica** (no hay harness E2E de UI; el gate no levanta
  simuladores). Cobertura = pipeline `:core` + smoke real key-gated.

## Evidence To Capture

- `CI=true ./init.sh` exit 0 y salida de `:core:testDebugUnitTest` con tests nuevos.
- Mismatch: `Failed`, `PENDING`, sin fila `embedding`.
- Red/500: `RetrySummary(stillPending = 1)` y luego `failed = 1` con `index_attempts`;
  diagnóstico sin key/body.
- Reconciliación: `ReconcileSummary(cleared = 1)` y reindexado a 1536.
- RAG/Enlaces: `RagAnswer.Answer` con fuentes y `LinkingSummary.linksWritten`.
- Smoke: línea sanitizada (1536, `INDEXED`, fuentes); key sólo en `local.properties`.
- `git diff --stat` sin paths protegidos; `schema.version = 8` sin `.sqm`.

## Known Risks

- **Config/modelo desalineado:** el indexado real fallará y consumirá presupuesto
  hasta `FAILED`; el smoke detecta la dimensión real. Corregir config/alias.
- **Offline real:** `ConfiguredAiAvailability` no prueba conectividad; arranques
  offline pueden agotar presupuesto y promover a `FAILED`. Sonda = follow-up del ADR.
- **Enlaces obsoletos:** la reconciliación no recomputa `note_link`; el trigger sigue
  explícito.
- **Tests con transporte falso:** validan contrato/máquina, no calidad semántica ni
  disponibilidad del proveedor.
- **Smoke real:** red/cuota/key; nunca en el gate.
- **API de `NoteIndexingService`:** default `null`, sin ruptura de llamadores.

## Open Questions

- ¿La sonda de conectividad va en una feature futura o follow-up del ADR? No bloquea.
- ¿Recomputar Enlaces tras reconciliar/reintentar? Hoy explícito; pertenece a
  `semantic-linking`/UI.
- ¿Exponer la dimensión activa en diagnóstico/UI? Fuera de alcance.

## Validator Checklist

- [ ] Alcance: no se duplican flag/runtime/adaptadores/wiring; sólo guards, pass-through
      y verificación.
- [ ] Scenarios 1–11 pasan (fake gate, mismatch, red, retry, transitorio, 8→1536, RAG,
      Enlaces).
- [ ] Un embedding de dimensión incorrecta nunca se persiste; `INDEXED` no se degrada.
- [ ] `PENDING`/`FAILED`/presupuesto siguen `offline-pending-retry`; el indexado directo
      no escribe `FAILED`.
- [ ] Gate fake, sin red/key; `init.sh` sin cambios; iOS compila.
- [ ] Sin cambios de esquema, UI, `App.kt`, `iosApp/**` ni secrets.
- [ ] Evidencia de tests + smoke sanitizado; `feature_list.json`/`PROGRESS.md` y docs
      durables actualizados.
- [ ] No hay E2E persistente y la spec lo justifica.
