# Feature Implementation Spec: Logging local de fallos de IA e integraciones nativas

## Source Feature

- `id`: `enrichment-observability-logging`, `area`: `observability`, `source`: `feature_list.json`
- `depends_on`: `["ai-enrichment-classify-tags-type"]` (`accepted`)
- `status`: `not_started` (al planificar)

> **Re-derivación de alcance.** El `id` conserva la palabra "enriquecimiento", pero
> el alcance es **observabilidad mínima del MVP** (`docs/technical-discovery.md` →
> *Observability*): registrar **localmente** los **fallos** de IA (indexado/consulta)
> y de **integraciones nativas** (voz, imagen, adjuntos) para diagnóstico. No hay
> clasificación IA (descartada en `CONTEXT.md`); "enriquecimiento" = el pipeline de
> IA ya existente. Sin UI de logs ni telemetría remota.

## Goal

Introducir un **puerto `Diagnostics`** en `:core` detrás del cual los fallos se
registran de forma **best-effort** (nunca lanza ni bloquea), con una implementación
**en memoria** (tests) y una **local persistente en archivo acotado** (dispositivo).
Los servicios de IA (`NoteIndexingService`, `RagQueryService`) y los fallos nativos
en `App` (voz, imagen, persistencia de adjuntos) reportan sus caminos de fallo.
Entregable de `:core` + `:composeApp`. **Sin** dependencias nuevas, **sin** cambio
de esquema (sigue v5), **sin** UI.

## Non-Goals

- UI/pantalla de diagnóstico, serialización del log, export o compartir.
- Telemetría remota / crash reporting (Crashlytics, Sentry, red): prohibida en el MVP.
- Librería de logging (Timber, Kermit, `os_log` wrapper) ni dependencias nuevas.
- Cambio de esquema SQLDelight (sin tabla `log`/`diagnostic` ni `.sqm`).
- Niveles/structured logging, sampling, rotación multi-archivo, compresión, subida.
- Instrumentar caminos de éxito/informativos; corregir los fallos registrados.
- Reintentos/transiciones de `NoteStatus` (`offline-pending-retry`).
- Garantizar thread-safety/transaccionalidad: el registro es best-effort.

## Job Story

When algo falla en el pipeline de IA o en una integración nativa,
I want una entrada local recuperable con fuente y motivo,
so I can diagnosticar en dispositivo sin telemetría remota ni UI.

## Users And Permissions

- Desarrollador (autor): único actor; corre tests, inspecciona el log local e
  inyecta `Diagnostics`. MVP single-user local-first, sin auth ni permisos.

## Acceptance Scenarios

1. **Fallo de IA en el indexado → entrada recuperable.** Given un
   `NoteIndexingService` con `InMemoryDiagnostics` y un `AiClient` que lanza
   `AiClientException`; When `index(note)`; Then devuelve `Failed`, `snapshot()` tiene
   una entrada `AI_INDEXING` con el motivo, el fallo **no** se propaga y **no** se
   escribe embedding.
2. **Indexado exitoso → sin entradas.** Given el mismo servicio con un `AiClient`
   válido; When `index(note)` sobre una Nota con `body` no vacío; Then devuelve
   `Indexed` y `snapshot()` queda **vacío**.
3. **Respuesta inválida del runtime → entrada.** Given un `AiClient` que devuelve
   `0` vectores, `>1` vectores o dimensión `0`; When `index(note)`; Then `Failed` y se
   registra `AI_INDEXING`.
4. **Fallo de RAG → entrada; "sin notas indexadas" no registra.** Given un
   `RagQueryService` con `InMemoryDiagnostics`; When `ask(question)` falla en
   `embed`/`generate` (o respuesta inválida); Then `RagAnswer.Failed` y entrada
   `AI_QUERY`; sin Notas indexadas, `NoIndexedNotes` **sin** invocar el runtime y
   **sin** entrada.
5. **El registro nunca lanza ni bloquea.** Given un `Diagnostics` cuya
   implementación subyacente falla al escribir; When `recordFailure(...)`; Then la
   excepción **no** se propaga y el llamador continúa.
6. **Sink de archivo local, acotado y recuperable.** Given un `FileDiagnostics` con
   `DiagnosticStorage` de test, `clock` y `maxEntries`; When se registran más fallos
   que `maxEntries`; Then `rawLog()` tiene a lo sumo `maxEntries` líneas (las más
   recientes), cada una con timestamp, `source`, `summary` y `detail`, recuperables
   tras registros sucesivos.
7. **Privacidad — no se registran datos sensibles.** Given un fallo de IA y uno
   nativo; When se registra; Then el log **no** contiene `body`, pregunta, rutas de
   adjuntos, IDs ni secrets; sólo fuente, resumen estable y mensaje de excepción
   (recortado).
8. **Fallos de integraciones nativas en `App`.** Given un fallo de voz (`onError`),
   imagen (`onError`) o persistencia de adjunto (`catch (Throwable)`); Then `App`
   registra `VOICE`/`IMAGE`/`STORAGE` sin cambiar el comportamiento visible.
9. **Sin cambio de esquema, ambas plataformas y gate honesto.** Given los tests 1–8;
   When `:core:testDebugUnitTest`, `:core:iosSimulatorArm64Test`,
   `:composeApp:assembleDebug`, `:composeApp:linkDebugFrameworkIosSimulatorArm64` y
   `./init.sh`; Then todo pasa en Android/JVM e iOS, `Schema.version == 5`, **sin**
   `.sqm` nuevo y `./init.sh` en 0 sin dev servers ni simuladores.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json` — SDD/WIP=1; dependencia
  `accepted`; verificación provisional ("un fallo simulado genera una entrada de
  log local recuperable").
- `docs/technical-discovery.md` — *Observability*: logging local de fallos de
  indexado e integraciones nativas; sin telemetría remota.
- `CONTEXT.md`, `docs/domain-model.md`, `CONSTRAINTS.md` — no hay entidad de
  dominio `Log`; reglas del indexado a ampliar con privacidad del log.
- `core/.../ai/NoteIndexingService.kt` — 3 caminos `Failed`: `AiClientException`,
  tamaño ≠ 1, dimensión 0.
- `core/.../ai/RagQueryService.kt` — `embed`/`generate`/respuesta inválida →
  `RagAnswer.Failed`; `NoIndexedNotes` sin invocar el runtime.
- `core/.../ai/NoteIndexingOrchestrator.kt` — sólo lee `IndexingResult` (no cambia).
- `core/.../platform/PlatformDefaults.kt` — patrón `expect`/`actual` reutilizable.
- `composeApp/.../App.kt` — fallos nativos: `onError` de voz (`VoiceCaptureError`),
  `onError` de imagen (`ImageCaptureError`) y dos `catch (_: Throwable)` de
  persistencia de Adjunto. El indexado best-effort y `ask` ya pasan por los
  servicios (no re-registrar).
- `composeApp/.../ImageCapture.kt` + actuals — patrón de "file store" privado
  (`filesDir` Android / `Application Support` iOS, ruta relativa) para el sink.
- `composeApp/{androidMain,iosMain}/.../MainActivity.kt|MainViewController.kt` —
  composition roots que construyen repos, `FakeAiClient`, servicios y `App(...)`.
- `init.sh` y tests de `:core` (`commonTest` con stubs de `AiClient` + `runTest`).

### Existing Patterns To Follow

- Puertos + adaptadores inyectados por constructor; `expect`/`actual` con fakes
  deterministas en tests; tests compartidos en `commonTest` + `@Test` Android/iOS
  con `runTest`; documentos en español, términos técnicos en inglés.

### Current Gaps

- No existe puerto ni implementación de logging/observabilidad.
- Los fallos de IA se descartan (`Failed`) y los `catch (Throwable)` nativos en
  `App` son silenciosos. No hay sink local ni retención definida.

## Technical Approach

### 1. Puerto y tipos en `:core` (`com.playbook.core.diagnostics`)

```kotlin
enum class DiagnosticSource { AI_INDEXING, AI_QUERY, VOICE, IMAGE, STORAGE }

data class DiagnosticEntry(
    val timestampMillis: Long, val source: DiagnosticSource,
    val summary: String, val detail: String? = null,
)

/** Best-effort: NUNCA lanza ni bloquea al llamador. */
interface Diagnostics { fun recordFailure(source: DiagnosticSource, summary: String, detail: String? = null) }

object NoOpDiagnostics : Diagnostics                 // default inyectable
class InMemoryDiagnostics(maxEntries = 100, clock = ::currentTimeMillis) : Diagnostics {
    fun snapshot(): List<DiagnosticEntry>; fun clear()
}
```

### 2. Sink local persistente (archivo acotado, sin dependencias)

```kotlin
interface DiagnosticStorage { fun append(line: String); fun read(): String; fun overwrite(text: String) }
expect fun diagnosticStorage(path: String): DiagnosticStorage                 // Android/JVM e iOS
class FileDiagnostics(storage, clock = ::currentTimeMillis, maxEntries = 200) : Diagnostics {
    override fun recordFailure(...) { /* append; recorta a maxEntries; todo en try/catch */ }
    fun rawLog(): String = storage.read()
}
```

- Línea: `<timestampMillis>\t<source>\t<summary>\t<detail|->`; `detail` recortado a
  ~200 chars. **Retención:** cap por cantidad (se reescribe con las últimas
  `maxEntries`). Path privado: `filesDir/diagnostics.log` (Android),
  `Application Support/diagnostics.log` (iOS).
- `androidMain` usa `java.io.File`; `iosMain` `NSFileManager`/`NSString` (append =
  read + overwrite).
- **Privacidad:** sólo `source`, `summary` estable y mensaje de la excepción. Nunca
  `body`, pregunta, rutas, IDs ni secrets.

### 3. Instrumentación

- `:core`: agregar `diagnostics: Diagnostics = NoOpDiagnostics` (default, no rompe
  callers) y registrar `AI_INDEXING` en cada `Failed` de `NoteIndexingService` y
  `AI_QUERY` en cada `Failed` de `RagQueryService`. `NoteIndexingOrchestrator` no
  cambia.
- `:composeApp`: `App(..., diagnostics)` registra `VOICE` (`onError` voz), `IMAGE`
  (`onError` imagen) y `STORAGE` (los dos `catch (Throwable)` de adjuntos). Los
  composition roots construyen `FileDiagnostics(diagnosticStorage(path))` y lo pasan
  a los dos servicios y a `App(...)`.

## Expected File Changes

- `core/.../diagnostics/Diagnostics.kt` — crear; tipos, puerto, `NoOp`, `InMemory`.
- `core/.../diagnostics/FileDiagnostics.kt` — crear; `DiagnosticStorage`, `expect`, `FileDiagnostics`.
- `core/src/{androidMain,iosMain}/.../diagnostics/Diagnostics.*.kt` — crear; actuals (`File`/Foundation).
- `core/.../ai/NoteIndexingService.kt`, `core/.../ai/RagQueryService.kt` — modificar; parámetro + registro.
- `core/src/commonTest/.../diagnostics/DiagnosticsCheck.kt` — crear; helpers 1–7 + test de `FileDiagnostics` con storage fake.
- `core/src/{androidUnitTest,iosTest}/.../diagnostics/Diagnostics*Test.kt` — crear; `@Test` con `runTest`.
- `composeApp/.../app/App.kt` — modificar; recibir `diagnostics` y registrar `VOICE`/`IMAGE`/`STORAGE`.
- `composeApp/src/{androidMain,iosMain}/.../MainActivity.kt|MainViewController.kt` — modificar; crear y cablear el sink.
- `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md` — modificar en el cierre.

Sin cambios en `init.sh`, `gradle/libs.versions.toml`, `iosApp/**`, `.sq`/`.sqm`,
`AiClient.kt`, `FakeAiClient.kt` ni el esquema (v5).

## Visual Design Impact

- UI involved: no (se instrumenta `App` sin cambiar su superficie).
- Design source: not applicable. Screens/states affected: ninguna.
- New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — nueva sección Observability: puerto `Diagnostics`
  (+ `NoOp`/`InMemory`/`File`), fuentes, sink acotado y privacidad.
- `CONSTRAINTS.md`: **update** — MUST NOT registrar secrets ni datos de contenido
  (`body`/pregunta/rutas); `Diagnostics` MUST ser best-effort; MUST NOT telemetría
  remota.
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola".
- `docs/technical-discovery.md`: **update** — "Observability Decisions".
- `CONTEXT.md`/`docs/domain-model.md`: **not needed** — el log es infra, no modelo.
- `docs/risks-and-open-questions.md`: **not needed** (no cambia un riesgo).
- `feature_list.json`/`PROGRESS.md`: **update** al cerrar (estado/evidencia).

## Implementation Plan

1. Crear el paquete `diagnostics` en `:core` (tipos/puerto/`NoOp`/`InMemory`) +
   `FileDiagnostics` con `expect diagnosticStorage` y actuals Android/iOS.
2. Instrumentar `NoteIndexingService` y `RagQueryService` (`diagnostics = NoOpDiagnostics`).
3. Instrumentar `App` (VOICE/IMAGE/STORAGE) y cablear `FileDiagnostics` en los roots.
4. Escribir `DiagnosticsCheck` (1–7) + runners Android/iOS.
5. Actualizar docs durables, correr verificación + control negativo, registrar evidencia.

## Implementation Tasks

- [x] Crear `Diagnostics`/`DiagnosticSource`/`DiagnosticEntry`/`NoOpDiagnostics`/`InMemoryDiagnostics`.
- [x] Crear `DiagnosticStorage` + `expect diagnosticStorage(path)` + actuals y `FileDiagnostics` (cap + try/catch + `rawLog`).
- [x] Registrar `AI_INDEXING` en `NoteIndexingService` y `AI_QUERY` en `RagQueryService` (default `NoOpDiagnostics`).
- [x] Instrumentar `App` (VOICE/IMAGE/STORAGE) y cablear el sink en los composition roots.
- [x] Escribir `DiagnosticsCheck` + `DiagnosticsAndroidTest`/`DiagnosticsIosTest` (+ tests del `actual` real por plataforma).
- [x] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`, `AGENTS.md`.
- [x] Correr verificación + control negativo y registrar evidencia.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` y `:core:iosSimulatorArm64Test
  --rerun-tasks` → `Diagnostics*Test` en verde (fallo IA→entrada, éxito→sin entradas,
  `NoIndexedNotes`→sin entradas, best-effort con sink que falla, `FileDiagnostics`
  cap/recuperación) + los tests previos de IA (con `NoOpDiagnostics`) intactos, 0 failures.
- `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64`
  → BUILD SUCCESSFUL.
- Confirmar `Schema.version == 5` y sin `.sqm` nuevo (`find core/src -name '*.sqm'`).
- `./init.sh` → exit 0, sin dev servers ni simuladores.
- **Control negativo**: (a) no registrar en `NoteIndexingService` ante fallo hace
  fallar el Scenario 1; (b) quitar el cap de `FileDiagnostics` hace fallar el
  Scenario 6. Ambos se restauran.
- **Smoke (log local)**: Android → denegar permiso de cámara y capturar imagen,
  leer `adb shell run-as com.playbook.app cat files/diagnostics.log` y ver una línea
  `IMAGE`; iOS → `Application Support/diagnostics.log` en el contenedor del
  simulador tras un fallo de permiso. Registro crudo, sin fabricar.
- E2E persistente: **no aplica** (sin harness E2E; `init.sh` no levanta simuladores;
  se cubre con tests del core en dos plataformas + smoke manual del archivo).

## Evidence To Capture

- `:core:testDebugUnitTest`/`:core:iosSimulatorArm64Test` con `Diagnostics*Test` en
  verde (conteos por plataforma) y tests previos de IA intactos.
- `:composeApp:assembleDebug` + link del framework iOS; `./init.sh` exit 0.
- `Schema.version == 5`; sin `.sqm` nuevo; `git diff` vacío en `init.sh`/
  `libs.versions.toml`/`iosApp`/`.sq`/`.sqm`.
- Control negativo reproducido y restaurado (ausencia de registro; cap removido).
- Contenido crudo del archivo local tras un fallo nativo (`IMAGE`/`VOICE`/`STORAGE`)
  y **ausencia** de `body`/pregunta/rutas. Sin dependencias nuevas ni telemetría.

## Validator Checklist

- [ ] Alcance respetado: sólo fallos; sin UI de logs, telemetría remota, librería de
      logging, cambio de esquema ni reintentos.
- [ ] Escenarios 1–9 pasan.
- [ ] `recordFailure` best-effort (Scenario 5) y `FileDiagnostics` acota por
      cantidad (Scenario 6).
- [ ] Privacidad: sin `body`/pregunta/rutas/IDs/secrets (Scenario 7); MUST NOT en
      `CONSTRAINTS.md`.
- [ ] `NoteIndexingService`/`RagQueryService` retrocompatibles (`NoOpDiagnostics`) y
      siguen sin propagar fallos de IA; `App` registra VOICE/IMAGE/STORAGE sin
      cambiar el comportamiento visible.
- [ ] `Schema.version == 5` y sin `.sqm` nuevo; sin dependencias nuevas.
- [ ] `ARCHITECTURE.md`, `docs/technical-discovery.md` y `CONSTRAINTS.md`
      actualizados; `AGENTS.md` sólo la línea de próxima feature.
- [ ] `feature_list.json` y `PROGRESS.md` actualizados con evidencia.
- [ ] No hay E2E persistente y la spec lo justifica.
- [ ] `init.sh` sin cambios, no bloqueante y sin procesos de larga duración.

## Implementation Notes (2026-10-14)

Desvíos menores, todos dentro del alcance y documentados:

- **`rawLog()` normaliza el `\n` final** (`storage.read().trimEnd('\n')`) para que
  el conteo de líneas del Scenario 6 sea determinista; el formato por línea no
  cambia.
- **Best-effort en el borde:** las implementaciones provistas
  (`NoOp`/`InMemory`/`File`) nunca lanzan y `FileDiagnostics` envuelve el storage
  en try/catch (Scenario 5 se verifica con un `DiagnosticStorage` que lanza). Los
  servicios llaman a `recordFailure` directo confiando en el contrato del puerto.
- **`App(..., diagnostics = NoOpDiagnostics)`** agrega default para no romper
  callers existentes; los composition roots pasan el `FileDiagnostics` real.
- **Tests extra** `DiagnosticsStorage{Android,Ios}Test` ejercitan el `actual` real
  de cada plataforma (el `Diagnostics*Test` compartido usa un storage fake).
- **Smoke iOS interactivo no ejecutado** por falta de input automation
  (`idb`/`cliclick`); el `actual` iOS se cubre en runtime por su test y los
  escenarios 1–7 corren en `:core:iosSimulatorArm64Test`.
