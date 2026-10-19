# Feature Implementation Spec: Selección fake/real por build flag

## Source Feature

- `id`: `ai-fake-real-switch`
- `area`: `ai`
- `depends_on`: `["ai-client-ports"]` (satisfecha: `accepted`)
- `status`: `not_started` (al planificar)
- `source`: `feature_list.json`

> **Alcance.** Habilitar la **IA real** del MVP (proveedor propio OpenAI-compatible,
> `https://inference.devexpert.io/v1`, 6 modelos) **detrás de los puertos ya
> existentes**, con un **build flag** que elige `fake` (determinista, default y
> siempre en tests/gate) o `real` (requiere API key, nunca versionada). Esta feature
> entrega el **flag + adaptadores reales de los 6 modelos + selección/inyección +
> reconciliación de dimensión de embeddings**. **No** cablea casos de uso de IA
> (transcribir, tts, imagen, sugerencias, Q&A real, indexado real) ni UI: eso es de
> `transcribe-audio-attachment`, `tts-read-aloud`, `image-generation-from-text`,
> `ai-suggest-classification`, `ai-real-chat` y `ai-real-embeddings`.

## Goal

Que el build y el composition root elijan el runtime de IA (fake o real) sin
exponer la key y sin tocar la lógica de dominio: `:core` expone los adaptadores
OpenAI-compatible detrás del puerto `HttpTransport`, `:composeApp` provee la
implementación HTTP de plataforma y arma el `AiRuntime` desde `BuildConfig`. En
modo `real`, re-indexar automáticamente los embeddings cuya dimensión no coincide
con la del modelo real, para que Enlaces/RAG sigan funcionando.

## Non-Goals

- **Casos de uso** de IA y su UI: transcribir el Adjunto AUDIO, reproducción tts,
  generar/adjuntar imagen, sugerir clasificación con confirmación, chat del
  Brainstorming/Q&A, indexado real con el proveedor (`ai-real-embeddings`) y
  `ai-real-chat`.
- **Indicador visual fake/real** (`DESIGN.md`); pertenece a la UI.
- Cambiar `AiClient`/`FakeAiClient`/`AiPorts`/`FakeAiPorts`/`AiAvailability` ni la
  semántica de `NoteIndexingService`/`RagQueryService`/`NoteIndexingOrchestrator`.
- Esquema y migraciones (la v8 es `game-project-persistence`).
- **IA real en iOS** (congelado, ADR 0003): iOS sigue con `fakeAiRuntime()`.
- Sonda de conectividad real, reintentos/timeouts con backoff, cacheo, batching,
  observabilidad de costos, multi-proveedor.

## Scope Decisions

> Validadas con el autor (2026-10-19).

1. **Los 6 adaptadores reales entran en esta feature** (chat/embedding vía
   `AiClient`; `TranscribeClient`/`TtsClient`/`ImageClient`/`JevClient`).
2. **Ubicación:** `:core`, detrás de un puerto `HttpTransport`. **Excepción
   explícita** (decidida por el autor) a la regla “adaptadores reales fuera de
   `:core`”: el adaptador vive en `:core` porque el transporte HTTP se inyecta y
   los tests corren offline en el gate.
3. **Modelos:** defaults por nombre (`chat`, `embedding`, `transcribe`, `tts`,
   `image`, `jev`), override por propiedad/env.
4. **`real` sin key (o sin base URL/modelos/dimensión):** el build **falla**.
5. **Key:** canal elegido: `local.properties` (`playbook.ai.apiKey`, ignorado por
   git); también se aceptan env/`-P`. Se inyecta en `BuildConfig` del APK local;
   nunca versionada, logueada ni mostrada. En modo fake el campo queda **vacío**.
   `real` por defecto sólo en **debug**; en release requiere opt-in por propiedad
   Gradle (`-Pplaybook.ai.allowReleaseKey=true`; `findProperty` también resuelve
   `gradle.properties`/`ORG_GRADLE_PROJECT_*`, hardening estricto pendiente).
6. **Smoke real de los 6 modelos** ahora, key-gated y **fuera del gate**,
   ejecutable con un test JVM habilitado por env.
7. **Gate:** sin cambios de alcance (`:composeApp:assembleDebug` +
   `:core:testDebugUnitTest`); los tests de `:composeApp` son verificación
   dirigida.
8. **JSON:** `kotlinx-serialization-json` en `:core` (runtime, sin plugin de
   compilador), con `buildJsonObject`/`parseToJsonElement`.
9. **Errores:** cualquier no-2xx (y timeout/IO/JSON inválido) → `AiClientException`
   con **mensaje genérico por status**; sin leer el body.
10. **jev:** sólo `type: "choice"` (sin `multi_choice`) ⇒ `genres` devuelve 0 o 1.
11. **Dimensión de embeddings:** **1536**, observada en el smoke real (el valor
    inicial de `3` era incorrecto). Se expone como
    `playbook.ai.embeddingDimension` (default `1536`, override por propiedad) y
    un servicio de `:core` reconcilia los embeddings incompatibles (los borra y
    los deja `PENDING`). El smoke verifica que el vector devuelto mida 1536.

## Job Story

When construya y pruebe las superficies de IA del MVP,
I want que un build flag elija entre un fake determinista y el proveedor real
OpenAI-compatible, sin versionar la key,
so I can desarrollar y testear offline con fake y, cuando quiera, usar la IA real
con una sola configuración, sin tocar la lógica de dominio.

## Users And Permissions

- Desarrollador (autor): único actor. Configura el flag/key por env,
  `local.properties` o `-P`; corre Gradle/tests y el smoke key-gated. MVP
  single-user local-first; sin auth ni permisos de app.

## Model → Port → Endpoint Mapping

| modelo ADR 0004 | puerto / operación | endpoint (base `/v1`) | default | parseo |
|---|---|---|---|---|
| `chat` | `AiClient.generate(GenerateRequest)` | `POST /chat/completions` | `chat` | `choices[0].message.content` |
| `embedding` | `AiClient.embed(List<String>)` | `POST /embeddings` | `embedding` | `data[i].embedding` ordenado por `index` |
| `transcribe` | `TranscribeClient.transcribe(audio, mimeType)` | `POST /audio/transcriptions` | `transcribe` | `text` (ignora `languages`) |
| `tts` | `TtsClient.synthesize(text)` | `POST /audio/speech` | `tts` | bytes de audio (mp3) |
| `image` | `ImageClient.generateImage(prompt)` | `POST /images/generations` | `image` | `data[0].b64_json` → bytes con mime detectado |
| `jev` | `JevClient.suggest(text)` | `POST /decisions` | `jev` | `answers.<q>.choice` |

Parámetros confirmados con el proveedor:

- **transcribe:** mp3 y m4a; ≤ 30 s; ≤ 25 MB; `response_format=json`.
- **tts:** voces OpenAI (default `alloy`); ≤ 4096 caracteres.
- **image:** el proveedor real **sólo acepta `1024x1024`**; `512x512`/`256x256`
  se rechazan con HTTP 400 (observado en el smoke). Se envían `quality=low`,
  `background=transparent` y `output_format=png`, pero el proveedor puede
  devolver **JPEG** y `background:null`; el adaptador detecta el mime real por la
  firma de los bytes. Default `1024x1024`.
- **jev:** `state` + `questions` con `type: "choice"`, `instructions`, `criteria`.

## Acceptance Scenarios

### Scenario 1: build/gate por defecto fake

Given el repo sin flags de IA
When se corre `./init.sh`
Then usa `fakeAiRuntime()` (sin red ni key), `:core:testDebugUnitTest` pasa y
`./init.sh` termina en `0`.

### Scenario 2: el gate ignora un entorno real

Given `PLAYBOOK_AI_MODE=real` y una key en el entorno
When se corre `./init.sh`
Then `init.sh` fuerza `-Pplaybook.ai.mode=fake`, el `BuildConfig.AI_MODE` generado
es `fake`, `AI_API_KEY` está vacío y no hay red.

### Scenario 3: build real (debug) con key

Given modo `real` con key, base URL, los 6 modelos y `embeddingDimension`
When se corre `./gradlew :composeApp:assembleDebug -Pplaybook.ai.mode=real …`
Then compila, `BuildConfig.AI_MODE == "real"` y el runtime real se selecciona.

### Scenario 4: `real` sin datos obligatorios falla

Given modo `real` sin key (o sin base URL/modelos/dimensión) en debug
When se corre el build
Then Gradle falla con un mensaje accionable que **no** incluye la key.

### Scenario 5: release real protegido

Given modo `real` y `assembleRelease` sin `-Pplaybook.ai.allowReleaseKey=true`
When se corre el build
Then usa `fake` con warning (no embebe key). Con `allowReleaseKey=true` y key
completa, compila real.

### Scenario 6: adaptadores reales correctos (transporte falso)

Given un `HttpTransport` de prueba con respuestas canónicas por modelo
When se invocan los 6 adaptadores
Then se construyen requests/parsea respuestas correctamente (orden de embeddings,
`text`, bytes mp3, `b64_json`→imagen (mime detectado), `answers.*.choice`) y los tipos de dominio
resultan no vacíos.

### Scenario 7: errores mapeados y sanitizados

Given un transporte que responde 401/500, timeout o JSON inválido
When se invoca cualquier adaptador
Then lanza `AiClientException` con un mensaje genérico por status que **no**
contiene la key, los headers ni el body del usuario.

### Scenario 8: jev sugiere sin auto-asignar

Given `JevClient.suggest(text)`
When el proveedor responde `track`/`genre` (o `none`)
Then devuelve `ClassificationSuggestion` con `track` por `Track.fromCode` y
`genres` de 0 o 1 elemento; **no** persiste ni modifica ninguna Nota.

### Scenario 9: reconciliación de dimensión de embeddings

Given embeddings persistidos con dimensión distinta a `embeddingDimension`
When el composition root llama a `EmbeddingDimensionReconciler.reconcile`
Then borra los embeddings incompatibles, deja sus Notas `PENDING` con
`index_attempts=0` (reintento los re-indexa con la dimensión nueva) y no toca los
compatibles. Con `expectedDimension = null` es no-op.

### Scenario 10: `AiAvailability` acorde

Given el runtime fake
When se consulta `isAvailable()`
Then es `true`. Given el runtime real con configuración incompleta (unidad),
Then `ConfiguredAiAvailability` devuelve `false` y `PendingIndexingRetry` es
no-op.

### Scenario 11: no-regresión

Given esta feature
When se inspecciona el diff
Then `AiClient`/`FakeAiClient`/`AiPorts`/`FakeAiPorts` y el esquema SQLDelight no
cambian; no hay UI nueva; `:composeApp:linkDebugFrameworkIosSimulatorArm64`
compila (iOS usa fake).

## Repository Research

### Files Inspected

- `docs/adr/0004-ai-real-integration.md` — proveedor propio OpenAI-compatible,
  mapeo modelo→caso de uso, fake/real por build flag, key nunca versionada.
- `docs/specs/ai-client-ports.md` — contratos ya definidos; esta feature
  implementa sus adaptadores reales.
- `core/src/commonMain/kotlin/com/playbook/core/ai/{AiClient,FakeAiClient,
  AiPorts,FakeAiPorts,AiAvailability}.kt` — puertos y fakes vigentes.
- `core/src/commonMain/kotlin/com/playbook/core/ai/{NoteIndexingService,
  NoteIndexingOrchestrator,RagQueryService,PendingIndexingRetry}.kt` — consumidores
  y máquina de estados que no se modifican.
- `core/src/commonMain/kotlin/com/playbook/core/repository/{EmbeddingRepository,
  SqlDelightEmbeddingRepository,NoteRepository}.kt` y
  `core/src/commonMain/sqldelight/.../embedding.sq` — `deleteByNoteId`,
  `updateStatus`, `setIndexAttempts` para la reconciliación (sin `.sqm` nuevo).
- `core/src/commonMain/kotlin/com/playbook/core/model/Genres.kt` —
  `GenreCatalog.GENRES` para los criterios de `jev`.
- `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt` y
  `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt` —
  composition roots (fake hardcodeado hoy).
- `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`,
  `init.sh`, `CONSTRAINTS.md` (§IA real), `ARCHITECTURE.md` (§AI).

### Existing Patterns To Follow

- Puertos como `interface` en `commonMain`; inyección desde el composition root.
- Fakes deterministas y sin red en `commonMain`; contratos en `commonTest`
  corridos desde runners Android/JVM e iOS.
- `AiClientException` para fallos del runtime; métodos `suspend`.
- Config externa por env/`local.properties` (patrón de `keystore.properties`),
  nunca hardcodeada ni versionada.
- Gate Android-only (`init.sh`): `:composeApp:assembleDebug` +
  `:core:testDebugUnitTest`, sin dev servers.

### Current Gaps

- No hay adaptadores reales, ni `HttpTransport`, ni selección fake/real.
- No hay lectura de key/modo/base URL/modelos desde build.
- `AiRuntimeConfig`/dimensión de embedding no existen; los embeddings del fake
  usan 8 dimensiones y quedarían incompatibles con el real.
- `:core` no tiene dependencia de JSON.

## Technical Approach

### 1. Dependencias y version catalog

- `gradle/libs.versions.toml`: agregar `kotlinx-serialization-json` (compatible
  con Kotlin 2.1.21, p. ej. `1.8.1`).
- `core/build.gradle.kts`: `implementation(libs.kotlinx.serialization.json)` en
  `commonMain`. Sin plugin de serialización (se usa `JsonElement`).

### 2. Build flag (`composeApp/build.gradle.kts`)

- Leer `local.properties` (ignorado) + `findProperty` + env, con precedencia
  **`-P` > env > `local.properties`**:
  - `playbook.ai.mode` / `PLAYBOOK_AI_MODE` (default `fake`).
  - `playbook.ai.baseUrl` / `PLAYBOOK_AI_BASE_URL` (default
    `https://inference.devexpert.io/v1`).
  - `playbook.ai.apiKey` / `PLAYBOOK_AI_API_KEY` / `local.properties`.
  - `playbook.ai.embeddingDimension` / `PLAYBOOK_AI_EMBEDDING_DIMENSION`
    (default `1536`, observado en el smoke real).
  - `playbook.ai.model.{chat,embedding,transcribe,tts,image,jev}` /
    `PLAYBOOK_AI_MODEL_{CHAT,EMBEDDING,TRANSCRIBE,TTS,IMAGE,JEV}`.
  - `playbook.ai.tts.voice` (default `alloy`); `playbook.ai.image.size`
    (default `1024x1024`), `.quality` (`low`), `.background` (`transparent`),
    `.outputFormat` (`png`); timeouts HTTP opcionales.
- `buildFeatures.buildConfig = true`.
- **Efectivo por variante:** debug usa `aiMode`; release usa `real` sólo con
  `-Pplaybook.ai.allowReleaseKey=true` (propiedad Gradle; env/local no cuentan;
  `findProperty` también resuelve `gradle.properties`/`ORG_GRADLE_PROJECT_*`, por
  lo que el hardening estricto a sólo `-P` queda pendiente; si no, `fake` +
  warning).
- Si el modo efectivo es `real`: exigir key y base URL, y usar los 6 modelos y
  `embeddingDimension` (default `1536`); si falta key/base URL o un modelo,
  `error(...)` sin imprimir la key.
- Si el modo efectivo es `fake`: `AI_API_KEY` = `""` (aunque haya key en el
  entorno), para que el gate nunca la embeba.
- `init.sh`: pasar `-Pplaybook.ai.mode=fake` en la invocación de Gradle; el resto
  del script no cambia.

### 3. Adaptadores reales en `:core` (`com.playbook.core.ai.openai`)

- `HttpTransport.kt`: `HttpRequest(method, url, headers, body)` /
  `HttpResponse(status, body, headers)` / `interface HttpTransport { suspend fun
  execute(request): HttpResponse }` (sin red en `:core`; se inyecta).
- `AiRuntimeConfig.kt`: config validada; `toString` **redacta** la key.
- `OpenAiJson.kt`: helpers con `kotlinx-serialization-json`
  (`buildJsonObject`/`parseToJsonElement`).
- Adaptadores:
  - `OpenAiAiClient(config, transport)`: `embed` (lista o vacío; body
    `{model,input}`; orden por `index`; 1 vector por input, no vacío) y
    `generate` (`{model,messages}`; system prompt + contexto cuando
    `request.context` no está vacío; parsear `choices[0].message.content`).
  - `OpenAiTranscribeClient`: multipart `model` + `file` (filename/ext según
    `mimeType`: `audio/mpeg`→`.mp3`, `audio/mp4`→`.m4a`);
    `response_format=json`; parsear `text`.
  - `OpenAiTtsClient`: `{model,voice,input}`; bytes de respuesta;
    `mimeType` del `Content-Type` o `audio/mpeg`.
  - `OpenAiImageClient`: `{model,prompt,size,quality,background,output_format}`;
    `data[0].b64_json` → Base64 (`kotlin.io.encoding.Base64`, opt-in experimental)
    → `GeneratedImage(bytes, detectImageMime(bytes))` (`image/png`/`image/jpeg`).
  - `OpenAiJevClient`: `state={text}` + preguntas `choice`:
    - `track`: criterios `mecánicas`/`personajes`/`historia`/`none`.
    - `genre`: criterios desde `GenreCatalog.GENRES` (+ `none`).
    Parsear `answers.track.choice`/`answers.genre.choice`; `none`→`null`/vacío;
    **nunca** persiste.
  - Errores: no-2xx/timeout/IO/JSON inválido → `AiClientException("<modelo> falló
    con HTTP <status>")` (mensaje genérico por status; sin key/headers/body).
- `AiRuntime.kt`: `enum AiMode { FAKE, REAL }`; `AiRuntime(mode, aiClient,
  transcribeClient, ttsClient, imageClient, jevClient, availability,
  embeddingDimension)`; `fakeAiRuntime()` (fakes + `AlwaysAvailableAiAvailability`
  + `embeddingDimension = FakeAiClient.DEFAULT_DIMENSION`); `openAiAiRuntime(config,
  transport)` (adaptadores reales + `ConfiguredAiAvailability(config)` +
  `embeddingDimension` de la config).
- `AiAvailability`: `ConfiguredAiAvailability(config)` devuelve `true` sólo si la
  configuración está completa (unidad; sin sonda de red).

### 4. Reconciliación de dimensión (`:core`)

- `EmbeddingDimensionReconciler(embeddingRepository, noteRepository)`:
  `fun reconcile(expectedDimension: Int?): ReconcileSummary`.
  - `null` → no-op.
  - Por cada `NoteEmbedding` con `values.size != expectedDimension`:
    `deleteByNoteId`, `updateStatus(noteId, PENDING)`, `setIndexAttempts(noteId,
    0)`; cuenta `cleared`.
  - Sin esquema nuevo; usa repos existentes.

### 5. Wiring

- `MainActivity` (Android): leer `BuildConfig` → `AiRuntimeConfig` →
  `openAiAiRuntime(config, UrlConnectionHttpTransport(...))` (o `fakeAiRuntime()`
  si el modo efectivo es fake); usar `runtime.aiClient` en
  `NoteIndexingService`/`RagQueryService`, `runtime.availability` en
  `PendingIndexingRetry`; correr `EmbeddingDimensionReconciler.reconcile(...)`
  **antes** de `setContent` (el `retryPending()` de `App` indexa lo reconciliado).
- `MainViewController` (iOS): `fakeAiRuntime()`; sin transporte ni key.
- `App.kt` sin cambios.

### 6. Transporte Android

- `UrlConnectionHttpTransport` (`composeApp/androidMain`): `HttpURLConnection`,
  timeouts configurables, headers, body bytes, status + bytes + `Content-Type`;
  **no sigue redirects** (`instanceFollowRedirects=false`); un 3xx queda como
  no-2xx y se mapea a `AiClientException` genérica, sin reenviar key/body/headers
  a otro host; el I/O bloqueante corre en `Dispatchers.IO` (dispatcher
  inyectable), nunca en el hilo llamador/Main; sin loguear key ni bodies.

## Expected File Changes

- `gradle/libs.versions.toml` — modify; `kotlinx-serialization-json`.
- `core/build.gradle.kts` — modify; dependencia JSON en `commonMain`.
- `composeApp/build.gradle.kts` — modify; flag/config, `buildConfig`, guard
  debug/release, key vacía en fake, deps de test.
- `init.sh` — modify; forzar `-Pplaybook.ai.mode=fake`.
- `core/src/commonMain/kotlin/com/playbook/core/ai/openai/*` — create;
  `HttpTransport`, `AiRuntimeConfig`, `OpenAiJson`, adaptadores, `AiRuntime`.
- `core/src/commonMain/kotlin/com/playbook/core/ai/EmbeddingDimensionReconciler.kt`
  — create.
- `composeApp/src/androidMain/kotlin/com/playbook/app/ai/UrlConnectionHttpTransport.kt`
  — create.
- `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt` — modify;
  wiring del runtime + reconciler.
- `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt` —
  modify; `fakeAiRuntime()`.
- `core/src/commonTest/kotlin/com/playbook/core/ai/openai/*` — create; contratos
  de adaptadores con `FakeHttpTransport`, config, errores, `AiRuntime`.
- `core/src/commonTest/kotlin/com/playbook/core/ai/EmbeddingDimensionReconcilerCheck.kt`
  — create.
- `composeApp/src/androidUnitTest/kotlin/com/playbook/app/ai/*` — create;
  selección por `BuildConfig`/config y smoke key-gated.
- `docs/specs/ai-fake-real-switch.md` — este spec.
- `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`,
  `AGENTS.md`, `docs/adr/0004-ai-real-integration.md`, `feature_list.json`,
  `PROGRESS.md` — update al cerrar.

No se esperan cambios en `AiClient.kt`, `FakeAiClient.kt`, `AiPorts.kt`,
`FakeAiPorts.kt`, `AiAvailability.kt`, `NoteIndexingService.kt`,
`NoteIndexingOrchestrator.kt`, `RagQueryService.kt`, `PendingIndexingRetry.kt`,
esquema/`.sqm`, `App.kt` ni `iosApp/**`.

## Visual Design Impact

- UI involved: no.
- Design source: not applicable.
- Screens or states affected: ninguno (el indicador fake/real de `DESIGN.md` es
  de otra feature).
- New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — selección fake/real, `HttpTransport`, runtime y
  la excepción explícita “adaptadores reales en `:core` detrás de transporte”.
- `CONSTRAINTS.md`: **update** — nombres exactos del build flag/key y el guard
  debug/release.
- `AGENTS.md`: **update (acotado)** — sólo la línea “Próxima feature en cola”.
- Other docs: `docs/technical-discovery.md` **update** (nueva sección
  “AI Real Switch Decisions”); `docs/adr/0004-ai-real-integration.md`
  **promoción a `accepted`** tras la validación; `feature_list.json`/`PROGRESS.md`
  **update** al cerrar. `CONTEXT.md`/`docs/domain-model.md` **not needed**.

## Implementation Plan

1. Version catalog + `:core` JSON; adaptadores y `HttpTransport` con tests de
   contrato (transporte falso).
2. `AiRuntimeConfig`/`AiRuntime`/`ConfiguredAiAvailability` + reconciliador y sus
   tests.
3. Build flag/guard en `composeApp` + `init.sh`; transporte Android y wiring en
   los composition roots.
4. Smoke key-gated de los 6 modelos + controles negativos; docs y cierre.

## Implementation Tasks

- [x] Agregar `kotlinx-serialization-json` al catalog y a `:core`.
- [x] `HttpTransport` + `AiRuntimeConfig` (key redactada) + `AiRuntime`/factory.
- [x] Adaptadores reales de los 6 modelos con `AiClientException` genérica.
- [x] `EmbeddingDimensionReconciler` + tests (mismatch, `null`, compatibles).
- [x] `ConfiguredAiAvailability` + tests.
- [x] Build flag/guard/config en `composeApp/build.gradle.kts`; key vacía en fake.
- [x] `UrlConnectionHttpTransport` + wiring en `MainActivity`/`MainViewController`.
- [x] `init.sh` fuerza `fake`.
- [x] Tests de `:core` (adaptadores/config/reconciler) y de `:composeApp`
      (selección + smoke key-gated).
- [x] Correr verificación + controles negativos; smoke real de los 6.
- [x] Actualizar docs durables, `feature_list.json` y `PROGRESS.md`.

## Verification Plan

- `./init.sh` → exit 0, fake, sin red; `:core:testDebugUnitTest` en verde con los
  tests nuevos.
- `PLAYBOOK_AI_MODE=real PLAYBOOK_AI_API_KEY=dummy ./init.sh` → sigue fake;
  `BuildConfig.AI_MODE` `fake` y `AI_API_KEY` vacío.
- `./gradlew :composeApp:testDebugUnitTest` → tests de selección/config en verde.
- `./gradlew :composeApp:assembleDebug -Pplaybook.ai.mode=real
  -Pplaybook.ai.apiKey=… -Pplaybook.ai.embeddingDimension=…` → BUILD SUCCESSFUL.
- `./gradlew :composeApp:assembleDebug -Pplaybook.ai.mode=real` (sin key) → falla
  con mensaje accionable.
- `./gradlew :composeApp:assembleRelease -Pplaybook.ai.mode=real` → usa fake +
  warning; con `allowReleaseKey=true` y datos completos, real.
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
- **Smoke real (fuera del gate):** con la key en `local.properties`
  (`playbook.ai.apiKey`, ignorado), correr
  `PLAYBOOK_AI_SMOKE=1 ./gradlew :composeApp:testDebugUnitTest --tests
  "com.playbook.app.ai.RealAiSmokeTest" -Pplaybook.ai.mode=real
  -Pplaybook.ai.smoke=1`, que ejercita los 6 adaptadores contra el proveedor.
  Debe registrar resultados sanitizados (sin key) y **verificar que la dimensión
  observada del embedding sea 1536**; si no, detenerse y revisar el valor antes
  de cerrar.
- **Controles negativos** (restaurar byte-idéntico y re-verificar en verde):
  (a) romper el parseo de `choices[0]` → falla el test de chat; (b) hacer que un
  adaptador incluya la key en el mensaje de error → falla el test de seguridad;
  (c) reconciliar con dimensión coincidente → no borra nada.
- E2E persistente: **no aplica** (sin flujo de usuario nuevo; `init.sh` no levanta
  simuladores). La cobertura real es `:core` + los tests dirigidos de `:composeApp`
  + el smoke key-gated.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` (nombres y conteos) y de
  `:composeApp:testDebugUnitTest`.
- `./init.sh` exit 0 y, con env real, `BuildConfig` fake con key vacía.
- Build real debug OK y fallo de `real` sin key (mensaje).
- Smoke de los 6 modelos: resultado sanitizado (modelo, status, forma/latencia,
  dimensión de embedding observada = 1536) sin key ni bodies; confirmar que la key
  vive sólo en `local.properties` (ignorado) y no se commiteó.
- Controles negativos reproducidos y restaurados.
- `git diff` sin cambios en puertos/fakes/esquema/UI; iOS compila.

## Known Risks

- **Key en el APK real:** el guard restringe `real` a debug (release con opt-in
  explícito), pero un APK real contiene la key; no debe distribuirse.
- **Dimensión de embedding = 1536:** el smoke real observó 1536; el valor inicial
  de `3` era incorrecto. El test verifica la longitud real; si el proveedor
  cambia de modelo/dimensión, se ajusta la config y se revisa la reconciliación.
- **Imagen del proveedor:** sólo acepta `1024x1024` (512/256 dan HTTP 400) y
  puede devolver JPEG aunque se pida PNG transparente; el mime se detecta por
  bytes. La transparencia no está garantizada por el proveedor.
- **Redirects:** el transporte no los sigue; un 3xx se mapea a error genérico
  para no reenviar key/body/headers a un host no validado.
- **`allowReleaseKey`:** se acepta por propiedad Gradle (`-P`); env/local no
  habilitan el embebido de key en release. `findProperty` también resuelve
  `gradle.properties`/`ORG_GRADLE_PROJECT_*`; el hardening estricto a sólo `-P`
  queda pendiente (deuda conocida).
- **Aliases de modelo:** los defaults son los nombres del proveedor; si un alias
  difiere, se ajusta por propiedad (sin cambio de código).
- **Errores del proveedor:** no se parsea el body; el mensaje es genérico por
  status (aceptado).
- **iOS:** sigue fake por el congelamiento (ADR 0003); deuda si se descongela.
- **Smoke real:** depende de red, cuota y key; se corre fuera del gate y nunca se
  versiona la key.

## Validator Checklist

- [ ] Alcance respetado: flag + 6 adaptadores + selección + reconciliación; **sin**
      casos de uso, UI, esquema ni cambios de puertos/fakes.
- [ ] Scenarios 1–11 pasan.
- [ ] La key nunca está versionada, logueada ni mostrada; en fake el campo vacío.
- [ ] `real` sin datos obligatorios falla; release real protegido.
- [ ] Reconciliación borra incompatibles y deja `PENDING`/`attempts=0`; no toca
      compatibles.
- [ ] `init.sh` sin cambios de alcance y forzando fake; iOS compila.
- [ ] Docs durables actualizados; `feature_list.json`/`PROGRESS.md` con evidencia.
- [ ] No hay E2E persistente y la spec lo justifica.
