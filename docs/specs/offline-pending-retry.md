# Feature Implementation Spec: Reintento de indexado pendiente y promoción a `fallida`

## Source Feature

- `id`: `offline-pending-retry`
- `area`: `ai`
- `depends_on`: `["ai-enrichment-classify-tags-type"]` (`accepted` 2026-10-13)
- `status`: `not_started` (al planificar); `source`: `feature_list.json`

> **Re-derivación de alcance.** Se descarta la terminología obsoleta
> ("enriquecimiento" = clasificación IA). En `CONTEXT.md`, *Indexado (IA)* = generar
> el embedding (no clasifica ni etiqueta). El alcance real: **reintentar el indexado
> de las Notas `pendiente` cuando la IA vuelve a estar disponible** y, tras agotar un
> presupuesto de intentos, **promover `pendiente → fallida`**.
> `ai-enrichment-classify-tags-type` (ya `accepted`) escribe sólo `INDEXED`/`PENDING`;
> `FAILED`, los reintentos y la disponibilidad son de esta feature (`CONSTRAINTS.md`).

## Goal

Cerrar el ciclo offline/online: al abrir/reabrir la app, las Notas `PENDING` se
reintentan contra el runtime; las exitosas pasan a `INDEXED` y las que fallan
persistentemente, tras `budget` intentos fallidos (default `3`), pasan a `FAILED`. El
presupuesto se persiste en `note.index_attempts` (esquema **v6**, `5.sqm`) para que la
promoción sea durable. Además, el indexado de la app debe usar
`NoteIndexingOrchestrator` (hoy usa `NoteIndexingService`, que **no** transiciona
`NoteStatus`), para que existan `PENDING` reales. Entregable: `:core` (columna + servicio
+ tests) + wiring mínimo en `:composeApp`. Sin UI ni runtime real.

**Decisión de "IA disponible" (límites honestos).** No hay red ni runtime real
(`FakeAiClient`, ADR 0001): la disponibilidad es un puerto inyectable `AiAvailability`
(`suspend fun isAvailable(): Boolean`) con default `AlwaysAvailableAiAvailability`; si es
`false`, el reintento es no-op (no consume presupuesto ni cambia estados). La sonda real de
conectividad es follow-up del ADR y la "vuelta de la IA" se aproxima con el **arranque de la app**.

## Non-Goals

- Conectividad real ni adaptadores cloud/on-device (ADR 0001).
- Scheduling en background (`WorkManager`/`BGTaskScheduler`) o reintento continuo.
- UI de estado/corrección (`note-enrichment-status-ui`) y logging (`enrichment-observability-logging`).
- Clasificación IA/categoría/tipo/etiquetas IA; Enlaces (`semantic-linking`) y RAG.
- Cambiar `AiClient`, `NoteIndexingService` ni la regla del orquestador; deps nuevas; red en el gate.

## Job Story

When mis Notas quedaron `pendiente` por falta de IA y vuelvo a abrir la app, I want
que se reintente su indexado automáticamente y que las irrecuperables queden
`fallida`, so I can recuperar las indexables sin intervención y detectar las que no
lo son, sin bloquear nunca la captura.

## Users And Permissions

- Desarrollador (autor): único actor; inyecta `AiClient`/`AiAvailability`, corre
  Gradle/tests. MVP single-user local-first; sin auth ni permisos nuevos.

## Acceptance Scenarios

### Scenario 1: reintento exitoso `pendiente → indexada`
Given una Nota `PENDING` (`index_attempts = 1`) y disponibilidad true, When
`PendingIndexingRetry.retryPending()` corre con `FakeAiClient`, Then queda `INDEXED`, el
embedding se persiste, `index_attempts` vuelve a `0` y el resumen da `indexed = 1`.
### Scenario 2: fallo bajo presupuesto → sigue `pendiente`
Given una Nota `PENDING` (`index_attempts = 0`) y un `AiClient` que lanza
`AiClientException`, When corre `retryPending()`, Then sigue `PENDING` (nunca `FAILED`
antes del presupuesto), `index_attempts = 1` y el resumen da `stillPending = 1`, `failed = 0`.
### Scenario 3: agotar el presupuesto → `pendiente → fallida`
Given `budget = 3` y una Nota `PENDING` con `index_attempts = 2`, When un reintento
vuelve a fallar, Then `index_attempts = 3` y la Nota queda `FAILED` (`failed = 1`); una
`FAILED` no se reintenta hasta que el usuario la edite (la edición la reindexa).
### Scenario 4: IA no disponible → degradación sin efectos
Given `isAvailable() == false` y Notas `PENDING`, When corre `retryPending()`, Then no se
consume presupuesto, ningún estado cambia, no se llama a `AiClient` y el resumen da
`available = false`, `attempted = 0`.
### Scenario 5: sólo se procesan `PENDING`
Given Notas `CAPTURED`, `INDEXED` y `FAILED`, When corre `retryPending()`, Then ninguna se
toca; sólo las `PENDING` participan.
### Scenario 6: migración v5 → v6 preserva Notas y agrega `index_attempts`
Given una base v5 con Notas, When se migra a v6, Then las Notas se conservan intactas y
`note.index_attempts` existe con default `0`; `Schema.version == 6` y sólo aparece `5.sqm`.
### Scenario 7: el indexado de la app usa el orquestador
Given la app guarda/edita una Nota con `body` no vacío y un `AiClient` que falla, When
termina el guardado, Then la Nota queda `PENDING` en la base (transición real); con éxito,
queda `INDEXED`.
### Scenario 8: ambas plataformas y gate honesto
Given los tests de los escenarios 1–7, When corren `:core:testDebugUnitTest`,
`:core:iosSimulatorArm64Test`, `:composeApp:assembleDebug`,
`:composeApp:linkDebugFrameworkIosSimulatorArm64` y `./init.sh`, Then todo pasa en
Android/JVM e iOS, sin `.sqm` extra ni deps nuevas, y `./init.sh` termina en `0`.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json` — dependency-ready; terminología del
  entry obsoleta. `CONSTRAINTS.md` — *Indexado de IA*: el indexado nunca escribe
  `FAILED` ni bloquea la captura; reintentos/`fallida` son de esta feature.
- `CONTEXT.md`, `docs/domain-model.md` — *Indexado* = embedding; lifecycle
  `capturada → pendiente → indexada | fallida`; "editar dispara re-indexado".
- `docs/technical-discovery.md` (§Indexing/RAG), `docs/adr/0001-ai-runtime-decision.md`
  — el indexado no escribe `FAILED`; el `App` indexa best-effort con
  `noteIndexingService.index` (sin `NoteStatus`); `AiClientException` genérico.
- `core/.../ai/{NoteIndexingOrchestrator,NoteIndexingService,AiClient}.kt`,
  `core/.../model/{Note,NoteStatus,NoteMappers}.kt`,
  `core/.../repository/{NoteRepository,SqlDelightNoteRepository}.kt`,
  `core/.../sqldelight/.../{Note.sq,1..4.sqm}` — orquestador éxito→`INDEXED`/fallo→
  `PENDING` (nunca `FAILED`); `Note` sin contador; `updateStatus` sólo estado; esquema v5.
- `core/src/commonTest/.../{db/MigrationCheck,db/DatabaseMigrationCheck,
  ai/NoteIndexingOrchestratorCheck,repository/NoteRepositoryCheck}.kt` — helpers con
  `SqlDriver` + stubs de `AiClient` + `runTest`; migración v1→v5 a subir a v6.
- `composeApp/.../App.kt` (`indexBestEffort`, `LaunchedEffect`, `reload()`) y
  `composeApp/.../{MainActivity,MainViewController}.kt` (composition roots).

### Existing Patterns To Follow

- `:core` sin DI (dependencias por constructor); servicios `suspend`.
- Tests compartidos en `commonTest` sobre `SqlDriver` + `@Test` con `runTest` en
  `androidUnitTest` (`JdbcSqliteDriver.IN_MEMORY`) e `iosTest` (`inMemoryDriver`);
  `.sqm` con DDL espejado por `Schema.create` (tests comparan columnas entre caminos).
- Mutaciones devuelven `rowsAffected > 0`; no re-sellar `updated_at`.
- UI: estado de Compose + `rememberCoroutineScope()`; sin `ViewModel`/Flow/DI.

### Current Gaps

- No hay columna `index_attempts` ni lectura de Notas por `status`.
- No existe servicio de reintento ni puerto de disponibilidad.
- El `App` indexa con `NoteIndexingService` (sin `NoteStatus`): no genera `PENDING`.

## Technical Approach

### 1. Esquema v6: presupuesto durable (`5.sqm`)
- `Note.sq`: agregar `index_attempts INTEGER NOT NULL DEFAULT 0` a `CREATE TABLE note`
  y las queries (sólo queries):
  ```sql
  selectNotesByStatus:  SELECT * FROM note WHERE status = ? ORDER BY updated_at DESC, id DESC;
  updateNoteIndexAttempts: UPDATE note SET index_attempts = ? WHERE id = ?;
  ```
  `insertNote`/`updateNote`/`updateNoteStatus` sin cambios (`insertNote` usa default `0`).
- `5.sqm` (v5→v6): `ALTER TABLE note ADD COLUMN index_attempts INTEGER NOT NULL DEFAULT 0;`.
- `Note.indexAttempts: Int = 0` + `NoteMappers` mapea `index_attempts`. `NoteDraft` no cambia.

### 2. Repositorio
```kotlin
fun getByStatus(status: NoteStatus): List<Note>          // sólo lectura
fun setIndexAttempts(id: String, attempts: Int): Boolean // sólo index_attempts
```
`SqlDelightNoteRepository` → `rowsAffected > 0`; ninguna re-sella `updated_at`.
`getByStatus` agrupa etiquetas como `getAll`.

### 3. Disponibilidad (seam)
```kotlin
interface AiAvailability { suspend fun isAvailable(): Boolean }
object AlwaysAvailableAiAvailability : AiAvailability { override suspend fun isAvailable() = true }
```

### 4. Servicio de reintento (`:core`)
```kotlin
data class RetrySummary(val available: Boolean, val attempted: Int,
    val indexed: Int, val stillPending: Int, val failed: Int)
class PendingIndexingRetry(
    private val orchestrator: NoteIndexingOrchestrator,
    private val noteRepository: NoteRepository,
    private val availability: AiAvailability = AlwaysAvailableAiAvailability,
    private val budget: Int = DEFAULT_RETRY_BUDGET, // 3, require > 0
) { suspend fun retryPending(): RetrySummary }
```
Si `!isAvailable()` → resumen `available = false` sin efectos. Si disponible: por cada
`getByStatus(PENDING)`, `orchestrator.index(note)` (fija `INDEXED`/`PENDING`); si `INDEXED`
→ `setIndexAttempts(id, 0)`; si sigue `PENDING` → `next = note.indexAttempts + 1`,
`setIndexAttempts(id, next)` y, si `next >= budget`, `updateStatus(id, FAILED)`. Una
`FAILED` deja de aparecer en `getByStatus(PENDING)`: terminal salvo re-indexado por edición.

### 5. Wiring en `:composeApp`
- Roots (`MainActivity`/`MainViewController`): construir `NoteIndexingOrchestrator(
  noteIndexingService, noteRepository)` y `PendingIndexingRetry(orchestrator,
  noteRepository)`; pasarlos a `App(...)`.
- `App`: reemplazar `indexBestEffort` por el orquestador; en el arranque,
  `LaunchedEffect(Unit) { pendingRetry.retryPending(); reload() }`. El guardado indexa; el
  reintento de otras `PENDING` ocurre al reabrir (no reintento inmediato inútil). Sin trigger
  manual (sin superficie de UI; es `note-enrichment-status-ui`).

## Expected File Changes

- `core/src/commonMain/sqldelight/.../{Note.sq, 5.sqm}` — modificar/crear; columna
  `index_attempts` + queries `selectNotesByStatus`/`updateNoteIndexAttempts`; migración v5→v6.
- `core/src/commonMain/kotlin/.../model/{Note,NoteMappers}.kt` — modificar; `indexAttempts`.
- `core/src/commonMain/kotlin/.../repository/{NoteRepository,SqlDelightNoteRepository}.kt`
  — modificar; `getByStatus`/`setIndexAttempts`.
- `core/src/commonMain/kotlin/.../ai/{AiAvailability,PendingIndexingRetry}.kt` — **crear**.
- `core/src/commonTest/.../{ai/PendingIndexingRetryCheck (crear), repository/NoteRepositoryCheck,
  db/MigrationCheck, db/DatabaseMigrationCheck}.kt` + runners `androidUnitTest`/`iosTest`
  (`PendingIndexingRetry*Test`, crear) — escenarios 1–5, nuevos métodos y migración v6.
- `composeApp/.../{App,MainActivity,MainViewController}.kt` — modificar; wiring del
  orquestador + reintento al arranque.
- `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md` — modificar (ver abajo).
- `AGENTS.md` — modificar sólo la línea "Próxima feature en cola" al cierre.
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar.

No se esperan cambios en `AiClient.kt`, `NoteIndexingService.kt`,
`NoteIndexingOrchestrator.kt`, `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`,
`iosApp/**` ni `init.sh`.

## Visual Design Impact

- UI involved: yes (mínimo). Design source: `DESIGN.md` (provisional); sin dirección nueva.
- Screens or states affected: `NotesListScreen` (el chip de `status` existente puede
  pasar de `pendiente` a `indexada`/`fallida` tras reintento + `reload()`).
- New design artifact required: no — sin pantallas, componentes ni tokens nuevos.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — sección AI: reintento/`AiAvailability`, columna
  `index_attempts` (esquema v6) y wiring del orquestador desde la app.
- `CONSTRAINTS.md`: **update (acotado)** — `FAILED` sólo lo escribe el reintento tras
  agotar el presupuesto; el indexado directo nunca lo escribe ni bloquea la captura.
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola".
- Other docs: `docs/technical-discovery.md` **update** ("Retry Decisions
  (offline-pending-retry)": presupuesto, seam, trigger, límites honestos);
  `docs/domain-model.md`/`CONTEXT.md` **not needed** (lifecycle ya documentado);
  `docs/risks-and-open-questions.md` **not needed** (no cambia la matriz de riesgos);
  `feature_list.json`/`PROGRESS.md` **update** al cerrar.

## Implementation Plan

1. Agregar `index_attempts` a `Note.sq`, crear `5.sqm` y actualizar `Note`/mapper;
   confirmar `Schema.version == 6`.
2. Agregar `getByStatus`/`setIndexAttempts` a `NoteRepository` + implementación.
3. Crear `AiAvailability` y `PendingIndexingRetry` (presupuesto + resumen).
4. Escribir `PendingIndexingRetryCheck` (1–5) + runners; extender `NoteRepositoryCheck`
   y migraciones a v6.
5. Cablear `App`/roots y actualizar docs.
6. Correr verificación + controles negativos, registrar evidencia y cerrar.

## Implementation Tasks

- [ ] Agregar columna/query en `Note.sq` y crear `5.sqm` (v5→v6).
- [ ] Extender `Note`/`NoteMappers` con `indexAttempts`.
- [ ] Agregar `getByStatus`/`setIndexAttempts` a `NoteRepository` e implementarlos.
- [ ] Crear `AiAvailability` y `PendingIndexingRetry`.
- [ ] Crear `PendingIndexingRetryCheck` + runners Android/iOS (escenarios 1–5).
- [ ] Extender `NoteRepositoryCheck` y migraciones a v6.
- [ ] Cablear `App` + `MainActivity`/`MainViewController`.
- [ ] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`, `AGENTS.md`.
- [ ] Correr verificación + controles negativos y registrar evidencia.
- [ ] Re-derivar el entry en `feature_list.json` y actualizar `PROGRESS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → `PendingIndexingRetryAndroidTest`
  1/1 + `NoteRepositoryAndroidTest` (nuevos métodos + migración v1→v6) +
  `MigrationAndroidTest`/`DatabaseMigrationAndroidTest` +
  `NoteIndexingOrchestratorAndroidTest` 1/1 + resto en verde, 0 failures.
- `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` (macOS) → equivalentes iOS.
- `./gradlew :composeApp:assembleDebug` y
  `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
- `PlaybookDatabase.Schema.version == 6`; `find core/src -name '*.sqm'` = `1.sqm`..`5.sqm`.
- `./init.sh` → exit 0; gate no bloqueante, sin dev servers/simuladores/red.
- **Controles negativos** (restaurar byte-idéntico y re-verificar en verde):
  (a) no promover a `FAILED` al alcanzar `budget` → falla el Scenario 3;
  (b) `AiAvailability` forzado a `false` → falla el Scenario 1;
  (c) renombrar la columna en `5.sqm` (`index_attempts_broken`) → falla el test de migración.
- E2E persistente: **no aplica** (sin harness de UI/E2E, `init.sh` no levanta
  simuladores, runtime `FakeAiClient`). Cobertura: tests del core en dos plataformas +
  compilación de la app + smoke manual de migración.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest`/`:core:iosSimulatorArm64Test` con los nuevos tests
  en verde (nombres/conteos) y de `:composeApp` (build/link).
- `Schema.version == 6`; `.sqm` = `1`..`5`; `AiClient.kt`/`NoteIndexingService.kt`/
  `NoteIndexingOrchestrator.kt` sin cambios (`git diff`).
- Controles negativos (a)/(b)/(c) reproducidos y restaurados, con el test que falla.
- Smoke real de migración v5→v6 (Android emulador, iOS simulador): Notas preservadas,
  `user_version = 6`, `note.index_attempts` presente.
- `./init.sh` → exit 0, sin procesos de larga duración ni red.
- Confirmación de que no se tocaron `iosApp/**`, `init.sh`, `libs.versions.toml` ni
  `composeApp/build.gradle.kts`; sin deps nuevas ni secrets.

## Validator Checklist

- [ ] Alcance respetado: reintento de `PENDING` + `FAILED` + wiring mínimo; sin
      conectividad real, scheduling, UI de estado, RAG ni Enlaces.
- [ ] Escenarios de aceptación 1–8 pasan.
- [ ] `FAILED` sólo tras agotar `budget`; `CAPTURED`/`INDEXED` no se tocan; el indexado
      directo sigue sin escribir `FAILED`.
- [ ] `AiAvailability=false` no consume presupuesto ni cambia estados.
- [ ] `NoteIndexingOrchestrator`/`NoteIndexingService`/`AiClient` sin modificar; `budget > 0`.
- [ ] `Schema.version == 6`, `5.sqm` correcto, `index_attempts` default `0`, migración
      v5→v6 sin pérdida de Notas; sin `.sqm` extra.
- [ ] `ARCHITECTURE.md`, `docs/technical-discovery.md` y `CONSTRAINTS.md` actualizados;
      `AGENTS.md` sólo la línea de próxima feature.
- [ ] `feature_list.json` y `PROGRESS.md` actualizados con la evidencia.
- [ ] No hay E2E persistente y la spec lo justifica; `init.sh` sin cambios.
