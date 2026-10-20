# Technical Discovery

## Product Surface
App móvil **Android-first**. UI en Compose Multiplatform y lógica de dominio en un
core Kotlin Multiplatform (KMP). iOS queda **congelado** desde 2026-10-15 (ver
"Platform Scope Decisions" y `docs/adr/0003-ios-congelado-mvp.md`).

## Candidate Stack
- **Core:** Kotlin Multiplatform (lógica compartida real, sin duplicar por
  plataforma).
- **UI:** Compose Multiplatform.
- **Persistencia:** SQLDelight.
- **Búsqueda semántica:** embeddings almacenados como BLOB + similitud coseno
  calculada en memoria.
- **IA:** interfaz `AiClient` como abstracción (puerto implementado en `:core`;
  ver "AI Decisions"). Runtime **decidido** en `ai-runtime-decision`: estrategia
  **híbrida por fases, cloud-first** detrás del puerto (ver "AI Decisions
  (ai-runtime-decision)" y `docs/adr/0001-ai-runtime-decision.md`). `embed` lo
  consume `NoteIndexingService` (`embeddings-generation`) y `generate` el
  `RagQueryService` (`rag-query`, ver "RAG Decisions"). `rag-answer-citations`
  renderiza las Notas fuente de la respuesta como citas navegables. El
  **proveedor real** del MVP (propio, OpenAI-compatible) y los 6 modelos/casos de
  uso están en `docs/adr/0004-ai-real-integration.md`.
- **Integraciones nativas:** speech-to-text (voz) y cámara (foto de bocetos).

## Data and Storage
- **Local-first**, sin backend en el MVP.
- SQLDelight en ambas plataformas como única fuente de verdad local.
- Vectores de embeddings como BLOB en SQLDelight; similitud coseno en memoria
  (suficiente para una biblioteca personal).
- **Esquema v7 (Géneros, 2026-10-16):** renombre de la N—N de Etiquetas a
  **Géneros** (catálogo fijo + personalizados) con `6.sqm` v6→v7 conservando los
  datos (etiquetas libres → Géneros personalizados, copiadas **verbatim**);
  `note_tag`→`note_genre`; la canonización case-insensitive ocurre al **escribir**.
- **Esquema v8 (Proyecto, 2026-10-19):** tabla `game_project` (singleton
  `id = "singleton"`, `owner` y ficha estructurada) + `game_project_genre`,
  `game_project_inspiration`, `game_project_key_mechanic` y
  `game_project_out_of_scope` con `7.sqm` v7→v8 **aditivo** (sin copiar datos).
  `GameProjectRepository` (`get`/`save` upsert) en `:core`, sin UI ni wiring;
  los campos son opcionales y las listas respetan `0..3`/`0..5`.
- `Owner` presente en el modelo aunque el MVP sea single-user.

## Integrations
- **Speech-to-text nativo:** Android `SpeechRecognizer` / iOS `Speech`.
- **Cámara / galería:** captura de bocetos; el OCR (si existe) es texto derivado.
  **OCR descartado del MVP** (`image-ocr-optional`): ver "OCR Decision
  (image-ocr-optional)" y `docs/adr/0002-ocr-decision.md`.
- **Proveedor de IA (real, MVP):** proveedor **propio** compatible con la API de
  OpenAI (`https://inference.devexpert.io/v1`) detrás de `AiClient`/puertos;
  modelos **chat** (generación: Q&A/Brainstorming), **embedding** (indexado),
  **transcribe** (Adjunto AUDIO), **tts** (lectura en voz alta), **image**
  (generación desde texto) y **jev** (sugerencia de clasificación Track/Género).
  Alternancia **fake/real** por **build flag** (key nunca versionada; tests y
  gate siempre fake). Decisión en `docs/adr/0004-ai-real-integration.md`.

## Authentication and Authorization
- Ninguna en el MVP (single-user local-first).
- Se reserva la noción de `Owner` para multi-usuario futuro.

## Deployment and Operations
- Distribución en Android (Play / internal testing) e iOS (TestFlight) para
  ejercitar "lanzar a producción".
- Sin operación de servidores en el MVP.
- **iOS release (`ios-release-pipeline`):** pipeline shell + Xcode CLI sin
  `fastlane` en `scripts/ios-release/` (preflight → archive → export → upload).
  Firma automática con `Apple Distribution` en Release; bundle id estable
  `com.playbook.app`; versionado por build settings; subida con
  `xcrun altool --upload-app` y App Store Connect API key fuera del repo. Detalle
  en `ARCHITECTURE.md` y runbook `docs/release/ios-testflight.md`. La ejecución
  firmada a TestFlight requiere Apple Developer Program y credenciales
  (account-gated).

## Testing and Verification
- Tests unitarios del core KMP con un `AiClient` fake (deterministas, sin red).
- Tests de persistencia SQLDelight.
- Gate `init.sh` **Android-only** (desde 2026-10-15): `:composeApp:assembleDebug` +
  `:core:testDebugUnitTest` (Android/JVM). iOS sale del gate con el congelamiento.
- Prueba manual end-to-end en **Android** (emulador/dispositivo).

## Observability
- Mínimo: **logging local de fallos** de indexado/consulta de IA e integraciones
  nativas vía el puerto `Diagnostics` (best-effort, sin telemetría remota ni
  librería de logging). Sink acotado por cantidad en `diagnostics.log` (privado).
  Ver "Observability Decisions (enrichment-observability-logging)".

## Constraints
- **API keys y costos:** a resolver si se elige cloud; nunca commitear keys.
- **Offline:** la captura siempre debe funcionar; el indexado tolera fallos y se
  reintenta.
- **Costo de KMP × 2 plataformas:** Android + iOS duplica el trabajo nativo
  (permisos, STT, cámara) y requiere entorno macOS/Xcode.
- **iOS congelado (2026-10-15):** no se exige paridad ni verificación iOS; el
  código se conserva y compila. Los nuevos `expect` requieren un `actual` iOS
  mínimo para no romper el build iOS. El gate deja de construir/linkear iOS.

## Bootstrap Decisions (kmp-project-bootstrap)

Implementado 2026-10-01. Estructura y versiones fijadas:

- **Estructura:** `:core` (KMP library, sin UI) + `:composeApp` (Compose MP UI +
  app Android + framework iOS `ComposeApp`) + `iosApp` (host Xcode SwiftUI).
  Detalle y dirección de dependencias en `ARCHITECTURE.md`.
- **Identidad de paquete:** base `com.playbook`; `applicationId` Android y bundle
  id iOS `com.playbook.app`; framework iOS `ComposeApp`.
- **Versiones (combinación compatible, centralizada en
  `gradle/libs.versions.toml`):** Gradle wrapper 8.14.5, Kotlin 2.1.21, Compose
  Multiplatform 1.8.2, AGP 8.10.1, compileSdk 36, minSdk 24, targetSdk 36, JVM
  target 11.
- **Motor de UI Android:** Jetpack Compose (via Compose Multiplatform) con
  `activity-compose` 1.10.1; sin Views/XML de UI (solo `themes.xml` mínimo).
- **iOS:** framework estático `ComposeApp` enlazado por el host Xcode; el script
  phase `Compile Kotlin Framework` invoca
  `:composeApp:embedAndSignAppleFrameworkForXcode`.

## Persistence Decisions (local-persistence-sqldelight)

Implementado 2026-10-05. Detalles en `ARCHITECTURE.md`.

- **SQLDelight 2.1.0** como única fuente de verdad local. Se eligió 2.1.0
  (contemporánea de Kotlin 2.1.21) para no arrastrar KGP 2.2.x/2.3.x de
  SQLDelight 2.2.1+ y pisar la Kotlin fijada del bootstrap.
- **Codegen con AGP 8.10.1:** el riesgo de que los fuentes generados no se
  compilaran con AGP 8.9–8.11 (corregido recién en 2.4.0) **no se materializó**:
  `generateCommonMainPlaybookDatabaseInterface` + `:core:testDebugUnitTest`
  compilaron sin mitigaciones extra.
- **Esquema v1:** tabla `note` con `track` nullable (edge case "nota sin track")
  y `schema.version = 1`; sin archivos de migración. Queries de plumbing:
  `insertNote`, `selectAllNotes`, `selectNoteById`.
- **Drivers:** `AndroidSqliteDriver` (`playbook.db` en el directorio de bases de
  Android) y `NativeSqliteDriver` (iOS). En iOS el archivo queda en
  `Library/Application Support/databases/playbook.db`.
- **Native driver perezoso:** `NativeSqliteDriver` no abre la conexión (ni crea el
  archivo/esquema) hasta el primer statement. `createDatabase(...)` en `:core`
  ejecuta un `PRAGMA user_version` inocuo para forzar la apertura y aplicar el
  esquema al arrancar. Como `PRAGMA user_version` retorna filas, debe ejecutarse
  con `driver.executeQuery(...)`; usar `driver.execute(...)` lanza
  `SQLiteException: Queries can be performed using SQLiteDatabase query or
  rawQuery methods only` y rompía el arranque en Android e iOS (corregido
  2026-10-05).
- **Link de SQLite en iOS:** el host Xcode necesita enlazar `libsqlite3`
  (`OTHER_LDFLAGS = -lsqlite3`); sin eso el link de la app falla con símbolos
  `sqlite3_*` no encontrados.
- **Tests:** helper compartido en `commonTest` que recibe un `SqlDriver`; se
  corre en Android/JVM con `JdbcSqliteDriver.IN_MEMORY` y en iOS con
  `inMemoryDriver`. Incluye `createDatabaseOpensConnection`, que ejercita el
  helper real `createDatabase(...)` (path de arranque) y cubre la regresión del
  `PRAGMA` de apertura. `init.sh` corre también `:core:iosSimulatorArm64Test` en
  macOS.

## Domain Decisions (note-model-crud-core)

Implementado 2026-10-05. Detalles en `ARCHITECTURE.md`.

- **Modelo:** `Note`/`NoteDraft` en `com.playbook.core.model` con `track`
  nullable y `status` no nulo. `Track` (`MECHANICS`/`CHARACTERS`/`STORY`) y
  `NoteStatus` (`CAPTURED`/`PENDING`/`INDEXED`/`FAILED`) guardan un `code`
  canónico en español (el valor de dominio de `CONTEXT.md`); los identificadores
  Kotlin quedan en inglés.
- **Mapeo tolerante vs fail-fast:** `Track.fromCode(null | desconocido)` → `null`
  (preserva el edge case "Nota sin `track`" ante datos migrados);
  `NoteStatus.fromCode(desconocido)` lanza `IllegalArgumentException` porque la
  columna es `NOT NULL` y sólo la escribe este código.
- **Repositorio:** `NoteRepository` + `SqlDelightNoteRepository(database,
  idFactory, clock)`. `create` genera `id`/timestamps; `update` re-sella
  `updated_at` con `clock()` y preserva `owner`/`created_at`; `update`/`delete`
  devuelven `rowsAffected > 0` (los mutators SQLDelight 2.x retornan
  `QueryResult<Long>`, leído sincrónicamente con `.value`).
- **IDs y tiempo por plataforma:** `expect/actual` `randomNoteId()` /
  `currentTimeMillis()` en `commonMain`/`androidMain`/`iosMain`. Los tests
  inyectan fakes deterministas; no se usa `expect/actual` en source sets de test.
- **Queries nuevas sin migración:** `updateNote`/`deleteNoteById` en `Note.sq`;
  `schema.version` sigue en 1, sin `.sqm`.
- **Tests compartidos del CRUD:** `verifyNoteCrud(driver)` en `commonTest`
  (create/read, avance de `updatedAt`, ids inexistentes, delete, round-trip de
  todos los `Track`/`NoteStatus`, `track = null`, track desconocido y relectura
  con una segunda instancia). Corre en `NoteRepositoryAndroidTest`
  (`JdbcSqliteDriver.IN_MEMORY` + `Schema.create`) y `NoteRepositoryIosTest`
  (`inMemoryDriver`).

## UI Decisions (notes-list-ui)

Implementado 2026-10-05. Detalles en `ARCHITECTURE.md`.

- **Wiring sin DI:** `App(noteRepository: NoteRepository)` recibe la dependencia
  como parámetro; no se introduce framework DI, `ViewModel`/`Lifecycle` ni
  `Flow`. `MainActivity`/`MainViewController` construyen
  `SqlDelightNoteRepository(database, ::randomNoteId, ::currentTimeMillis)` y lo
  pasan a `App(...)`.
- **Lectura estática:** la lista se lee una vez por composición con
  `remember(noteRepository) { noteRepository.getAll() }`. Es aceptable para una
  tabla local pequeña; no hay reactividad ni refresco tras mutaciones (llega con
  `create-text-note`). No se expone el `PlaybookDatabase` a la UI. **(Superado por
  `create-text-note`: la lectura pasó a `LaunchedEffect` + `refreshKey`.)**
- **Orden determinista en SQL:** `selectAllNotes` pasa a
  `ORDER BY updated_at DESC, id DESC`; el `id` desempata de forma estable. El
  cambio es de query, no de esquema (`schema.version` sigue en 1, sin `.sqm`).
  Se extiende `verifyNoteCrud` con la aserción de orden (incluye empates de
  `updated_at` y compara con re-ordenar en Kotlin con el mismo criterio).
- **UI sólo lectura:** `NotesListScreen` renderiza estado vacío ("Todavía no hay
  notas", sin CTA) y estado con notas (título "Notas", `LazyColumn` de `Card`s).
  Cada tarjeta muestra `body` a máx. 2 líneas con ellipsis y `track.code` (o "Sin
  track") + `status.code` como **texto**, no sólo color (accesibilidad de
  `DESIGN.md`). Se evitó `TopAppBar` experimental: un `Text` de encabezado basta.
- **Sin deps ni migraciones:** `compose.foundation`/`compose.material3` ya
  estaban; `init.sh` no cambió. El estado no vacío se sembró sólo para QA
  (`sqlite3` sobre el `playbook.db` del emulador/simulador); no se commiteó
  código de seed.

## UI Decisions (create-text-note)

Implementado 2026-10-08. Detalles en `ARCHITECTURE.md`.

- **Estado de lista sin DI/ViewModel/Flow:** `App(noteRepository)` hoistea
  `notes` con `mutableStateOf` y un `refreshKey`; la lectura vive en
  `LaunchedEffect(refreshKey)` y **no** en el cuerpo de composición (resuelve la
  deuda de `notes-list-ui`). `reload()` incrementa la clave tras cada mutación
  (crear/editar/borrar) para releer y reflejar los cambios sin reiniciar.
- **Navegación local:** `sealed interface NotesDestination`
  (`List`/`Create`/`Edit(noteId)`) resuelve lista ↔ editor sin librería de
  navegación ni `ViewModel`. Si el `noteId` de `Edit` ya no existe en `notes`, se
  vuelve a `List`.
- **Owner del MVP:** `LOCAL_OWNER_ID = "local"` como `private const val` en la UI;
  `:core` no se toca. Si una feature futura necesita el owner fuera de
  `:composeApp`, se puede promover a `:core` sin cambiar el contrato funcional.
- **Sin cambio de esquema ni deps:** los mutators `create`/`update`/`delete` del
  repositorio ya existían; `schema.version` sigue en 1, sin `.sqm`. `Scaffold`,
  `ExtendedFloatingActionButton`, `OutlinedTextField`, `AlertDialog` y `Surface`
  (chips de `track`) vienen de `compose.material3`/`compose.foundation`; no se
  agregó `kotlinx-coroutines-core`.
- **Validación y borrado:** `Guardar` se deshabilita con `body.isBlank()`; el
  borrado sólo existe en modo edición y pasa por un `AlertDialog` de
  confirmación. El `status` no se edita (una nota nueva queda `capturada`; editar
  el cuerpo no lo revierte).
- **Accesibilidad:** la selección de `track` se comunica con marca textual "✓",
  borde y color (no sólo color); las tarjetas usan
  `clickable(onClickLabel = "Editar nota", role = Role.Button)`.
- **Verificación:** el path de datos del CRUD y el orden `updated_at DESC,
  id DESC` siguen cubiertos por `verifyNoteCrud` en `:core` (Android/JVM e iOS);
  la UI se verificó con smoke manual. En iOS no hubo input automation disponible
  (`idb`/`cliclick` ausentes; `osascript`/System Events bloqueado), así que el
  CRUD interactivo se ejercitó en Android sobre el mismo `commonMain` y en iOS se
  verificaron build, launch y render (OCR).

## AI Decisions (ai-client-interface)

Implementado 2026-10-08. Detalles en `ARCHITECTURE.md`.

- **Puerto `AiClient`** en `com.playbook.core.ai` con `suspend fun
  embed(texts): List<Embedding>` y `suspend fun generate(request):
  GenerateRequest → String`; tipos `Embedding`, `GenerateRequest` y
  `AiClientException`. `suspend` porque las llamadas de IA son asíncronas y
  cancelables; un contrato síncrono forzaría a bloquear a los runtimes de red.
  Se definen ya embeddings **y** generación (el concept-prompt pide ambos detrás
  de `AiClient`) para no reabrir el puerto al llegar el RAG; `generate` queda
  como seam sin consumidor actual.
- **Agnóstico de runtime/dimensión:** el puerto no fija el proveedor
  (cloud/on-device/híbrido) ni la dimensión del embedding; los adaptadores los
  deciden (`ai-runtime-decision`, `risks-and-open-questions.md`).
- **`FakeAiClient` (commonMain, default):** determinista y sin red, con
  `dimension` configurable (default 8) y `require(dimension > 0)`. Los vectores
  derivan de `String.hashCode()` (estable entre plataformas) mediante un LCG de
  32 bits: mismo texto → mismo vector, textos distintos → vectores distintos
  salvo colisión de hash. `embed(emptyList()) == emptyList()` y `generate`
  devuelve una respuesta no vacía. Vive en `commonMain` porque es el runtime por
  defecto inyectable hasta que se decida el real; también lo usan los tests.
- **Inyección / wiring:** no hay consumidor de IA todavía, así que **no** se
  cablea `AiClient` a `App`/entry points (sería una dependencia muerta). La
  inversión de dependencia se demuestra con tests de contrato que corren la misma
  interfaz contra `FakeAiClient` y una segunda implementación test-only
  (`ConstantAiClient`), sin tocar archivos de producción de `:core`.
- **Dependencia `kotlinx-coroutines`:** `core` no tenía coroutines. Se agrega
  **1.10.1** (compatible con Kotlin 2.1.21; resuelta en
  `androidDebugCompileClasspath`): `kotlinx-coroutines-core` en `commonMain` y
  `kotlinx-coroutines-test` en `commonTest` para `runTest`. `init.sh` no cambió.
- **Tests:** `verifyAiClientContract(client)` (contrato genérico) y
  `verifyFakeAiClient()` (comportamiento del fake) en `commonTest`; se ejecutan
  en Android/JVM (`AiClientAndroidTest`, 3) e iOS (`AiClientIosTest`, 3).

## AI Decisions (ai-runtime-decision)

Implementado 2026-10-09. Decisión en `docs/adr/0001-ai-runtime-decision.md`;
evidencia en `docs/spikes/ai-runtime-spike.md`.

- **Decisión:** estrategia **híbrida por fases, cloud-first**, siempre detrás del
  puerto `AiClient` (que no cambia). (1) MVP: primer adaptador real **cloud**
  (`embeddings-generation` para `embed`; `rag-query` para `generate`) con indexado
  asíncrono tolerante a fallos; (2) luego: adaptador **on-device** (empezando por
  embeddings) por el mismo puerto; (3) `FakeAiClient` como default
  offline/determinista.
- **Por qué no cloud-only ni on-device-only:** cloud-only es el camino más rápido
  a un flujo real y comparte todo el código KMP, pero obliga a keys/costo y baja
  privacidad; on-device-only da privacidad/offline pero exige deps nativas
  pesadas por plataforma (poco compartido), app más grande y calidad/latencia de
  generación riesgosa. La híbrida conserva lo mejor de cada una y el puerto ya
  permite hacerlo sin refactor. Detalle y puntajes por criterio en el ADR.
- **Política de secrets (durable):** ninguna API key/token/secret se versiona. El
  adaptador cloud leerá su credencial de configuración de build/entorno fuera del
  repo (`local.properties` ignorado o variable de entorno). `init.sh` no hace red.
- **Spike (test-only, sin red):** en `core/src/commonTest/.../ai/spike/`,
  `LocalSpikeAiClient` (on-device-like) y `RemoteSpikeAiClient` (cloud-like sin
  red, con `failMode`) demuestran contrato, offline, mapeo de fallo →
  `AiClientException` con degradación, sustituibilidad y un control negativo que
  rechaza un embedding vacío. Latencia local informativa con `TimeSource.Monotonic`
  (µs/nota), **no asertada** y medida sobre el fake, no sobre un modelo real.
- **Sin deps de producción:** no se agregó SDK cloud ni runtime on-device; el
  spike vive sólo en source sets de test. Sin cambios en `:composeApp`, `iosApp`,
  esquema SQLDelight, puerto, UI ni `init.sh`.
- **Follow-ups:** proveedor/modelo/dimensión; gestión de keys (usuario vs build);
  sonda manual key-gated (`AI_RUNTIME_PROBE_KEY`, fuera del gate) descrita en el
  ADR; elección del runtime on-device concreto.

## Deployment Decisions (android-release-pipeline)

Implementado 2026-10-09. Detalles en `ARCHITECTURE.md` y runbook en
`docs/release/android.md`.

- **Signing de release condicional:** `composeApp/build.gradle.kts` resuelve las
  credenciales con precedencia **env > `keystore.properties`**. Sólo con las
  cuatro presentes se registra `signingConfigs.create("release")` y se asigna al
  build type `release`; si falta alguna, no se registra y `assembleRelease`
  produce un APK **sin firmar** (modo degradado) sin fallar. Decisión clave: el
  build de release nunca exige secrets, por lo que `init.sh` y los entornos
  limpios siguen funcionando.
- **Secrets fuera del repo:** `keystore.properties`, `*.jks` y `*.keystore`
  quedan ignorados por git; `keystore.properties.example` documenta la plantilla
  sin secrets. El keystore de release es la *upload key* (Play App Signing
  custodia la *app signing key*).
- **Artefactos:** `assembleRelease` → APK instalable (`composeApp-release.apk`;
  `composeApp-release-unsigned.apk` en degradado); `bundleRelease` →
  `composeApp-release.aab` para Play. Verificación con `apksigner` (APK) y
  `jarsigner -verify` (AAB, firma JAR).
- **Versión overrideable:** `-Pplaybook.versionCode=N` y
  `-Pplaybook.versionName=X` con defaults `1` / `"1.0"`; Play exige incrementar
  `versionCode` por subida sin editar el script.
- **Sin R8/minificación:** `isMinifyEnabled = false` en release para no arriesgar
  Compose; la optimización de tamaño queda fuera de alcance.
- **Límite honesto:** la subida real al track *internal testing* de Play Console
  requiere cuenta/app/testers y **no** es verificable en el repo; se documenta
  como paso manual y su evidencia es externa (o declarada como no ejecutada). No
  se agregan plugins de Play/fastlane (evitan service-account JSON) ni CI.
- **`init.sh` sin cambios:** no corre tareas ni requiere secrets; sigue siendo el
  gate no bloqueante de debug.

## Embedding Decisions (embeddings-generation)

Implementado 2026-10-09. Detalles en `ARCHITECTURE.md`.

- **Tabla separada `embedding` (1:0..1) + esquema v2.** Se descartó una columna
  `embedding BLOB` en `note`: habría obligado a tocar la tabla y el CRUD
  aceptados, y `selectAllNotes()` cargaría todos los BLOBs al pintar la lista.
  La tabla separada mantiene las lecturas de Notas livianas y permite que
  `semantic-linking` pida sólo `embedding.getAll()`.
- **Primera migración `1.sqm` (v1 → v2).** Al crear la primera tabla vecina, el
  esquema cambia: `PlaybookDatabase.Schema.version` pasa a **2**. Los drivers
  (Android/Native) ya construidos con `Schema` migran automáticamente por
  `user_version`; `Schema.create` en instalación nueva crea el esquema final. No
  se cambió v1 "en el lugar" para no romper instalaciones existentes. Sin
  `schemaOutputDirectory` ni `.db` versionado: el repo no usa
  `verifySqlDelightMigration`; la equivalencia entre `1.sqm` y el `CREATE TABLE`
  de `embedding.sq` (y la preservación de datos) se cubre con un test portable
  (`DatabaseMigrationCheck`) que corre en Android/JVM e iOS. Limitación
  documentada: `1.sqm` debe replicar el `CREATE TABLE` de `.sq`.
- **FK con cascade.** `note_id ... REFERENCES note(id) ON DELETE CASCADE` evita
  huérfanos. Como SQLite trae las foreign keys **OFF**, `createDatabase(...)`
  agrega `PRAGMA foreign_keys = ON` después del `PRAGMA user_version` existente
  (statement sin filas → `execute`). Sin esto, `ON DELETE CASCADE` no se aplica.
- **`INSERT OR REPLACE` en vez de `ON CONFLICT DO UPDATE`:** el UPSERT de SQLite
  requiere 3.24 (Android 11+) y `minSdk = 24` (SQLite 3.9). Se guarda un único
  `indexed_at` (re-indexar lo actualiza); no hace falta `created_at` para datos
  derivados.
- **Codec BLOB big-endian explícito.** `EmbeddingBlobCodec` (puro Kotlin common,
  sin dependencias) serializa `List<Float>` con `Float.toBits()`/`fromBits()` y
  4 bytes por valor; `decode` exige longitud múltiplo de 4. Un cambio de
  endianness rompe el round-trip (control negativo reproducible).
- **Primer consumidor real de `AiClient`:** `NoteIndexingService` genera el
  embedding de `note.body` y lo persiste vía `EmbeddingRepository`. Es `suspend`
  para correrlo fuera del camino de captura; un fallo (`AiClientException`) o una
  respuesta inválida (0 vectores, >1 vector, dimensión 0) devuelve
  `IndexingResult.Failed` **sin escribir ni pisar** datos. **No transiciona
  `NoteStatus`**: solo el embedding y sus Enlaces definen `indexada`, y los
  Enlaces llegan con `semantic-linking`; la política de `pendiente`/`fallida` es
  `offline-pending-retry`. La fuente de verdad de "tiene embedding" es la fila en
  `embedding`.
- **Tests:** `verifyEmbeddingRepository` (codec, persistencia, overwrite,
  cascade, corrupción), `verifyNoteIndexing` (generación, overwrite, fallo,
  respuesta inválida) y `verifyDatabaseMigration` (v1 → v2 + equivalencia con
  `Schema.create`) en `commonTest`, corridos en Android/JVM
  (`JdbcSqliteDriver.IN_MEMORY`) e iOS (`inMemoryDriver`). Smoke real de
  migración v1 → v2 en Android (emulador) e iOS (simulador): `user_version = 2`,
  tabla `embedding` creada y Notas preservadas, sin crashes.

## Indexing Decisions (ai-enrichment-classify-tags-type)

Implementado 2026-10-13. Detalles en `ARCHITECTURE.md`.

- **Alcance re-derivado:** la feature se re-encuadró al **indexado asíncrono** +
  **transiciones de `NoteStatus`**. La clasificación IA de categoría/tipo y las
  etiquetas IA están **descartadas** (`CONTEXT.md`); los Enlaces por similitud son
  de `semantic-linking`; el reintento y `pendiente → fallida` son de
  `offline-pending-retry`. No se toca UI ni wiring.
- **Transición de estado aislada (sin cambio de esquema):** nueva query
  `updateNoteStatus` (`UPDATE note SET status = ? WHERE id = ?`) en `Note.sq` y
  `NoteRepository.updateStatus(id, status)`.
  `SqlDelightNoteRepository.updateStatus` mapea a `rowsAffected > 0`. **No**
  re-sella `updated_at`: indexar es un efecto de sistema, no una edición del
  usuario, así una indexación en segundo plano no reordena la lista
  (`selectAllNotes` ordena por `updated_at DESC`). `PlaybookDatabase.Schema.version`
  sigue en **4**, sin `.sqm` nuevo.
- **Regla de transición (`NoteIndexingOrchestrator`):** éxito → `INDEXED`; fallo →
  `PENDING` **salvo** que la Nota ya estuviera `INDEXED` (no se degrada lo ya
  indexado; el embedding previo queda intacto). **No escribe `FAILED`**: el puerto
  `AiClient` sólo expone `AiClientException` genérico (sin distinción
  transitorio/permanente) e `IndexingResult.Failed` tampoco distingue causa, así
  que la regla es crisp y testeable sin reintentos. La promoción
  `pendiente → fallida` pertenece a `offline-pending-retry`.
- **Reutilización sin modificar:** el orquestador sólo lee `IndexingResult` de
  `NoteIndexingService` (código `accepted`) y añade la transición; no propaga
  fallos de IA (el servicio ya los convierte en `Failed`). Es `suspend` y no
  agenda nada: el llamador decide cuándo invocarlo, fuera del camino de captura.
- **Tests:** `verifyNoteIndexingOrchestrator(driver)` (escenarios 1–7) en
  `commonTest`, corrido en Android/JVM
  (`JdbcSqliteDriver.IN_MEMORY` → `NoteIndexingOrchestratorAndroidTest`) e iOS
  (`inMemoryDriver` → `NoteIndexingOrchestratorIosTest`). `verifyNoteCrud` se
  extendió con el Scenario 8 (`updateStatus` cambia sólo `status`, preserva
  `body`/`track`/`tags`/`owner`/`created_at`/`updated_at` y devuelve `false` con
  `id` inexistente). Sin E2E persistente (no hay flujo observable; la spec lo
  justifica).

## Linking Decisions (semantic-linking)

Implementado 2026-10-13. Detalles en `ARCHITECTURE.md`.

- **Enlaces bidireccionales guardados una sola vez (par canónico).** La similitud
  coseno es simétrica (`sim(a,b) == sim(b,a)`), así que persistir dos filas por
  par duplicaría el dato y podría desincronizarse. Se guarda **una fila por par**
  con orden canónico `note_a_id < note_b_id` (PK compuesta + `CHECK`); la lectura
  "enlaces de una Nota" consulta ambos lados
  (`note_a_id = :id OR note_b_id = :id`) y un índice sobre `note_b_id` la hace
  eficiente. `NoteLink.otherNoteId(of)` resuelve "la otra Nota" (fail-fast).
- **Umbral inclusivo y configurable.** `SemanticLinkingService.
  DEFAULT_SIMILARITY_THRESHOLD = 0.8`, inyectable por constructor
  (`threshold in -1.0..1.0`); se enlaza cuando `score >= threshold`. Coseno vive
  en `[-1, 1]`; `0.8` es una heurística de "alta similitud" a afinar cuando llegue
  el runtime real. El control negativo (`>=` → `>`) hace fallar la frontera.
- **Similitud indefinida → `null`.** `CosineSimilarity.score` devuelve `null`
  (sin lanzar) si las dimensiones difieren o alguna norma es `0` (vector cero); el
  servicio omite esas parejas y sigue con el resto. Vectores idénticos → `1.0`
  exacto; ortogonales → `0.0`; opuestos → `-1.0`.
- **Recomputo explícito, determinista e idempotente.** El único punto de entrada
  es `SemanticLinkingService.recomputeAll()`: lee `EmbeddingRepository.getAll()`
  (ordenado por `note_id`), borra todos los enlaces y los reconstruye. Un
  borrado + reconstrucción es aceptable para una biblioteca personal (si el
  proceso muere a mitad, se regeneran en el próximo recomputo). Elimina enlaces
  obsoletos tras re-indexar y no deja duplicados.
- **Sin transición de `NoteStatus` ni wiring.** `indexada` = "embedding **y**
  Enlaces" en el dominio, pero la transición de estado y su trigger/scheduling
  son de `note-enrichment-status-ui`/`offline-pending-retry`; además hoy no hay
  disparador, así que el servicio es puro core y **no** se cablea a `App`. Esta
  feature no cambia `embeddings-generation` ni `NoteStatus`.
- **Esquema v5 + `4.sqm` (v4 → v5).** Nueva tabla `note_link` con FK
  `note(id) ON DELETE CASCADE` por ambos lados (borrar una Nota no deja enlaces
  huérfanos; `createDatabase` ya habilita `PRAGMA foreign_keys = ON`). El
  `CREATE TABLE`/`CREATE INDEX` de `4.sqm` replica exactamente el de `NoteLink.sq`.
- **Nombre de archivo `.sq` vs símbolos generados:** SQLDelight deriva el
  accessor del **nombre del archivo** (`NoteLink.sq` → `database.noteLinkQueries`)
  y la data class del **nombre de la tabla** (`note_link` → `Note_link`). Se usó
  `NoteLink.sq` (igual que `NoteTag.sq` / `note_tag`) para respetar la convención
  del repo; el mapper aliasa `Note_link as NoteLinkRow`.
- **Tests:** `CosineSimilarityTest` (puro, `@Test` en `commonTest`),
  `verifyNoteLinkRepository` (canónico, upsert idempotente, lectura por ambos
  lados, validaciones, cascade, `deleteAll`) y `verifySemanticLinking` (umbral,
  frontera inclusiva, similitud indefinida, idempotencia, re-indexado, Nota sin
  embedding, cascade) en Android/JVM (`JdbcSqliteDriver.IN_MEMORY`) e iOS
  (`inMemoryDriver`). Smoke real de migración v4 → v5 en Android (emulador) e iOS
  (simulador): `user_version` 4 → 5, tabla `note_link` + índice creados y Notas
  preservadas, sin crashes.

## Linking UI Decisions (note-links-ui)

Implementado 2026-10-14. Detalles en `ARCHITECTURE.md` ("Enlaces (notas
relacionadas)").

- **Proyección pura en `:core`, pantalla "tonta".** La resolución del otro extremo
  (`NoteLink.otherNoteId`), la exclusión de la propia Nota y el orden
  (`score` desc / `note.id` asc) viven en `buildRelatedNotes(noteId, links, notes)`
  (`com.playbook.core.linking`), testeada cross-platform; `RelatedNotesScreen` sólo
  renderiza y emite `onOpenNote`/`onBack` (mismo patrón que `buildGdd`/`GddScreen`).
  Evita que la UI dependa de SQLDelight y deja el orden determinista y verificable.
- **Trigger mínimo: recomputo best-effort al abrir.** `semantic-linking` no tenía
  disparador, así que una UI de sólo lectura habría quedado siempre vacía. Al
  entrar a `NotesDestination.Related(noteId)`, un `LaunchedEffect` corre
  `runCatching { SemanticLinkingService.recomputeAll() }` y luego lee los enlaces.
  `recomputeAll()` es síncrono y O(n²) sobre los embeddings; corre en el dispatcher
  principal (igual que `getAll()`), aceptable para una biblioteca personal. Un
  fallo **no** propaga ni bloquea: se muestra lo persistido o el estado vacío. El
  trigger de ciclo de vida (al indexar/editar, offline) y el recomputo incremental
  permanecen como follow-ups.
- **`score` como texto, sin semáforo.** La tarjeta muestra "Similitud 0.92" (dos
  decimales, formateados sin depender del locale) más chips de `track`/`status`/
  Etiquetas; la información nunca depende sólo del color (`DESIGN.md`). No se
  expone el umbral ni top-K en la UI.
- **Layout del editor.** "Ver relacionadas" va en una **fila propia** sobre la fila
  de acciones: en edición, ponerlo junto a Borrar/Cancelar/Guardar desbordaba una
  pantalla de 1080px y empujaba "Guardar" fuera del borde (detectado en el smoke
  real Android y corregido).
- **Sin cambios de esquema ni dependencias.** Sigue **v5** (sin `.sqm` nuevo),
  `gradle/libs.versions.toml`/`build.gradle.kts`/`init.sh` sin cambios. Los
  composition roots construyen `SqlDelightNoteLinkRepository` +
  `SemanticLinkingService` y los inyectan en `App(...)` (sin DI).
- **Tests y smoke.** `RelatedNotesTest` (7 casos puros en `commonTest`: otro
  extremo desde ambos lados, orden `score` desc, desempate por `id`, omisión de
  ausentes, exclusión de la propia Nota/enlaces ajenos, sin enlaces, no-mutación y
  determinismo) corre en Android/JVM e iOS; control negativo (orden asc) rompe
  `ordersByScoreDescending`. Smoke real Android (emulador, `adb shell input` +
  `uiautomator`): una Nota sin enlaces → estado vacío; dos Notas con el mismo
  cuerpo → embeddings idénticos (`FakeAiClient`) → coseno 1.0 → tarjeta
  "Similitud 1.00"; tocar la tarjeta abre la Nota y "Volver" regresa. **Limitación:**
  no se pudo ejecutar el mismo camino interactivo en iOS (sin input automation ni
  OCR); se verificó el arranque de la app en simulador (wiring + esquema v5) y el
  `commonMain` compartido se ejercita en Android.

## Enrichment Status UI Decisions (note-enrichment-status-ui)

Implementado 2026-10-14. Detalles en `ARCHITECTURE.md` ("UI", "AI").

- **`App` usa el orquestador, no el servicio directo.** `indexBestEffort(note)`
  llama `NoteIndexingOrchestrator.index` y hace `reload()` al resolver el
  `suspend`; así el `NoteStatus` transiciona de verdad (`CAPTURED → INDEXED` o
  `→ PENDING`) y el badge deja de mentir. Se mantiene el contrato: `body` en
  blanco no se indexa y un fallo del runtime no bloquea el guardado.
- **Badge textual, no sólo color.** `EnrichmentStatus.kt` expone
  `statusLabel`/`StatusBadge` (borde + texto), reutilizado por la lista y el
  editor; reemplaza el `status.code` crudo. La etiqueta coincide con
  `NoteStatus.code` ("capturada"/"pendiente"/"indexada"/"fallida") pero queda
  desacoplada del valor persistido.
- **Re-indexado manual, separado del automático.** El editor muestra el badge
  (sólo lectura) y un botón "Re-indexar" habilitado sólo si `status != INDEXED` y
  el `body` no está en blanco. Es un tap del usuario (`App.reindex(note)` →
  orquestador + `reload()`): **sin** scheduling, conectividad ni `FAILED` (frontera
  con `offline-pending-retry`). Re-indexar sólo llama `updateStatus`; **no** pisa
  `track`/`tags`/`body`/`owner`/timestamps (regresión cubierta en
  `NoteIndexingOrchestratorCheck`).
- **Sin esquema ni dependencias.** Sigue **v5** (sin `.sqm` nuevo); Compose y
  `kotlinx-coroutines` ya estaban. Los composition roots construyen
  `NoteIndexingOrchestrator(noteIndexingService, noteRepository)` y lo inyectan en
  `App(...)` (el servicio se sigue construyendo para el orquestador; sin DI).
- **Tests y smoke.** `:core` Android/JVM e iOS 40/0 (incluye la regresión de
  re-indexado). Smoke real Android (emulador dedicado, `adb shell input` +
  `uiautomator` + sembrado de DB por `run-as`): guardar texto → "indexada" + fila
  `embedding`; sembrar `pendiente` → lista/editor "pendiente" + "Re-indexar"
  habilitado; tocarlo → "indexada" conservando `track=mecánicas`/tag `jefe`
  (verificado en DB); nota sin texto → "capturada" + "Re-indexar" deshabilitado;
  nota indexada → "Re-indexar" deshabilitado. **Limitación iOS:** sólo
  install/launch + lista renderizada; sin input automation/OCR y con el container
  del simulador no accesible, no se pudieron verificar los badges/el botón (el
  `commonMain` se ejercita en Android).

## Retry Decisions (offline-pending-retry)

Implementado 2026-10-15. Detalles en `ARCHITECTURE.md` y `CONSTRAINTS.md`.

- **Presupuesto durable (esquema v6, `5.sqm`).** `note.index_attempts INTEGER NOT
  NULL DEFAULT 0` (`ALTER TABLE note ADD COLUMN`); las Notas v5 existentes se
  conservan y arrancan en `0`. El contador persiste los fallos entre arranques, así
  la promoción a `FAILED` es durable. `setIndexAttempts`/`updateStatus` **no**
  re-sellan `updated_at` (efecto de sistema; no reordena la lista).
- **Disponibilidad como seam (`AiAvailability`).** No hay red ni runtime real
  (ADR 0001), así que "la IA volvió" no se puede observar: un puerto
  `suspend fun isAvailable(): Boolean` con default `AlwaysAvailableAiAvailability`
  desacopla la decisión. Si es `false`, `retryPending()` es **no-op** (no consume
  presupuesto, no cambia estados, no llama a `AiClient`). La sonda real de
  conectividad es follow-up del ADR.
- **Trigger: arranque de la app.** `App` corre
  `LaunchedEffect(Unit) { pendingRetry.retryPending(); reload() }`. Se aproxima la
  "vuelta de la IA" con reabrir la app; **no** hay `WorkManager`/`BGTaskScheduler`
  ni reintento continuo (fuera de alcance). El guardado ya indexa con el
  orquestador; el reintento cubre las `PENDING` de sesiones previas.
- **Sólo `PENDING` participan.** `NoteRepository.getByStatus(PENDING)` (sólo
  lectura, orden `updated_at DESC, id DESC`, etiquetas agrupadas) acota el trabajo;
  `CAPTURED`/`INDEXED`/`FAILED` no se tocan. Una `FAILED` es terminal para el
  reintento hasta que una edición la reindexe (`NoteIndexingOrchestrator`).
- **`FAILED` sólo lo escribe el reintento.** El indexado directo
  (`NoteIndexingService`/`NoteIndexingOrchestrator`) nunca escribe `FAILED` ni
  bloquea la captura; la promoción ocurre en `PendingIndexingRetry` sólo tras
  agotar `budget` (`DEFAULT_RETRY_BUDGET = 3`, `require(budget > 0)`).
- **Tests.** `verifyPendingIndexingRetry(driver)` cubre los escenarios 1–5
  (éxito→`INDEXED`+reset, fallo bajo presupuesto, agotar→`FAILED`,
  `AiAvailability=false` no-op sin llamar al runtime, sólo `PENDING`), corrido en
  Android/JVM (`PendingIndexingRetryAndroidTest`) e iOS
  (`PendingIndexingRetryIosTest`). `verifyNoteRetryQueries` cubre
  `getByStatus`/`setIndexAttempts`; `verifyMigrationV1ToV6`/`verifyMigrationV5ToV6`
  y `DatabaseMigrationCheck` la migración a v6 (`index_attempts` default `0`, sin
  pérdida de Notas). Control negativo (c): renombrar la columna en `5.sqm` rompe
  la migración con `no such column: note.index_attempts`.

## RAG Decisions (rag-query)

Implementado 2026-10-13. Detalles en `ARCHITECTURE.md`.

- **Similitud coseno en memoria, sin vector DB ni umbral:** `cosineSimilarity`
  (pura Kotlin common) puntúa la pregunta contra cada embedding persistido; el
  retrieval es **top-K** (default `maxSources = 5`) sin umbral. El umbral y los
  enlaces persistidos son de `semantic-linking`; el retrieval del RAG es
  **efímero** (por consulta) y no crea filas `link`. El denominador `0f` devuelve
  `0f` (evita `NaN` con vectores nulos).
- **`RagQueryService` con el puerto `AiClient`:** `ask(question)` embebe la
  pregunta (`embed`), recupera por coseno y genera con
  `GenerateRequest(prompt = pregunta, context = cuerpos)` (`generate`). El core no
  conoce proveedor/modelo/dimensión: sólo el puerto; la app inyecta `FakeAiClient`.
- **Sin conocimiento externo:** si no hay filas `embedding` (o ningún candidato
  queda tras descartar embeddings sin Nota o de dimensión incompatible) devuelve
  `NoIndexedNotes` **sin** invocar el runtime. Los fallos de `embed`/`generate` se
  mapean a `Failed(cause)` sin propagar ni inventar respuesta; una respuesta de
  `embed` inválida (0 vectores, >1 vector, dimensión 0) → `Failed` sin llamar a
  `generate`.
- **Indexado best-effort al guardar:** `App` dispara
  `noteIndexingService.index(note)` en un `scope.launch` tras crear/editar una
  Nota con `body` no vacío; es silencioso ante fallo, no bloquea el guardado y no
  transiciona `NoteStatus`. Una Nota de sólo imagen no se indexa. Este glue es el
  mínimo para que el RAG vea las Notas del usuario; los estados/reintento son de
  `note-enrichment-status-ui`/`offline-pending-retry`.
- **Asincronía en la UI sin `ViewModel`/Flow:** la llamada `suspend` se invoca con
  `rememberCoroutineScope()` (único patrón nuevo); `App` mapea el `RagAnswer` a
  `AskUiState` (`Idle`/`Asking`/`Answered(text, sources)`/`NoIndexedNotes`/`Error`).
  La pantalla `AskNotesScreen` muestra el **texto** de la respuesta y, debajo, la
  sección **"Fuentes"** con una cita numerada por `RetrievedNote` (rank, fragmento
  del `body`, chip de `track`), clickeable para abrir la Nota (`Edit`); sin fuentes
  no hay sección. Comunica los estados con texto (`rag-answer-citations`).
- **Citas de la respuesta (`rag-answer-citations`):** la UI no descarta más
  `sources`: `AskUiState.Answered` lleva `List<RetrievedNote>` tal cual lo devuelve
  `RagQueryService` (mismo orden/top-K), y `App` cablea `onOpenSource` a
  `NotesDestination.Edit(noteId)`. La cita es sólo de presentación/navegación: **no**
  se persiste (eso es `semantic-linking`/`note-links-ui`) y no hay score crudo en la
  UI (no calibrado). Si la Nota fuente se borró, `Edit` detecta la ausencia y vuelve
  a la lista (comportamiento existente).
- **Sin esquema ni deps:** se reusan `note`/`embedding`/`note_tag`/`attachment`
  (esquema v4, sin `.sqm` nuevos); Compose y `kotlinx-coroutines` ya estaban.
- **Límite honesto (runtime real):** no hay adaptador cloud/on-device; la
  respuesta es la de `FakeAiClient`. La verificación prueba el **pipeline**
  (retrieval real + petición de generación anclada en las Notas), no la calidad de
  la respuesta, que depende del ADR 0001. Sin red ni keys.

## Voice / STT Decisions (voice-capture-stt)

Implementado 2026-10-09. Detalles en `ARCHITECTURE.md`.

- **Puerto + adaptadores, no `expect`/`actual`:** `VoiceTranscriber` vive en
  `:composeApp/commonMain` porque la integración es nativa y sólo la consume la UI
  (con contexto de plataforma en el constructor), igual que `DatabaseDriverFactory`
  o `AiClient`. `:core` no se toca.
- **Android (`SpeechRecognizer`):** se usa el servicio del sistema con
  `RecognizerIntent` (`LANGUAGE_MODEL_FREE_FORM`, resultados parciales) y el
  idioma por defecto; `RECORD_AUDIO` se pide con
  `registerForActivityResult(RequestPermission())` registrado en el constructor
  del adaptador (por eso se construye en `onCreate`). `isRecognitionAvailable`
  falso → `UNAVAILABLE`. Los errores del `RecognitionListener` se mapean por
  código (`NO_MATCH`/`SPEECH_TIMEOUT` → `NO_MATCH`, `NETWORK*` → `NETWORK`,
  `RECOGNIZER_BUSY` → `BUSY`, `INSUFFICIENT_PERMISSIONS` → `PERMISSION_DENIED`,
  resto → `UNKNOWN`).
- **iOS (`SFSpeechRecognizer` + `AVAudioEngine`):** se piden ambas autorizaciones
  (`SFSpeechRecognizer.requestAuthorization` y `AVAudioSession.requestRecordPermission`);
  si el recognizer no existe o `isAvailable()` es falso → `UNAVAILABLE`. Los
  callbacks de `Speech`/AVFoundation llegan en colas secundarias y se despachan al
  main queue con `dispatch_async(dispatch_get_main_queue())` antes de tocar el
  estado de Compose. `cancel()` cancela la tarea, quita el tap, detiene el engine
  y desactiva la sesión (evita "remove tap" sobre un nodo sin tap usando un flag
  `tapInstalled`). `AVAudioSession.setActive(...)` es una **función de extensión**
  de `platform.AVFAudio` y requiere import explícito (`import platform.AVFAudio.setActive`).
- **`Info.plist`:** `NSMicrophoneUsageDescription` y
  `NSSpeechRecognitionUsageDescription` son obligatorias; sin la segunda, iOS
  termina el proceso al primer uso del reconocimiento.
- **Sin dependencias nuevas:** `SpeechRecognizer`/`RecognizerIntent` son framework
  Android; `activity-compose` ya estaba para el permission launcher;
  `Speech`/`AVFAudio`/`dispatch` son platform libs de Kotlin/Native. No se agregan
  coroutines ni Flow a `:composeApp`; `schema.version` sigue en 1, sin `.sqm`.
- **Reconocimiento en la nube vs. on-device:** no se fija; `SpeechRecognizer`
  puede usar el servicio del sistema y `SFSpeechRecognizer` por defecto usa red.
  No afecta el contrato (la Nota se crea igual).
- **Verificación:** no hay forma determinista de simular el motor de voz nativo ni
  de inyectar audio en el emulador/simulador, así que la integración se verifica
  con smoke manual. En Android (emulador `sdk_gphone64_arm64`, Google APIs) se
  verificaron el lanzamiento del reconocedor ("Escuchando…"), el permiso denegado
  y el "sin habla" (`NO_MATCH`); el happy path de audio real → Nota no pudo
  ejecutarse por falta de entrada de audio inyectable. El path de datos (crear
  Nota) está cubierto por los tests de `:core`.

## Tags & Migration Decisions (note-category-and-tags)

Implementado 2026-10-09. Detalles en `ARCHITECTURE.md`.

- **Etiquetas vs `Track`:** una `Etiqueta` es vocabulario libre, múltiple y del
  usuario (relación N—N `Nota N—N Etiqueta`); el `track` es clasificación fija,
  única y ya existente. No se agrega Categoría/Tipo/Nivel.
- **Esquema v3 + migración `2.sqm`:** la tabla `note_tag` (`note_id`, `label`, PK
  `(note_id, label)`) exige un cambio de esquema. Como `embeddings-generation` ya
  había introducido `1.sqm` (v1→v2, `embedding`), esta feature agrega
  `core/src/commonMain/sqldelight/com/playbook/core/db/2.sqm` (v2→v3) y
  `PlaybookDatabase.Schema.version = 3`. Los `.sqm` viven junto a los `.sq` en
  `com/playbook/core/db/`; SQLDelight los detecta y `Schema.migrate` los aplica.
  La tabla `note` no cambió.
- **Sin FK ni cascade:** la etiqueta se identifica por su `label` (sin tabla
  `tag` ni id sintético) y la limpieza es explícita en el repositorio. Si una
  feature futura necesita renombrar/autocompletar etiquetas globalmente, se
  normaliza a `tag` + `note_tag` sin cambiar el contrato `Note.tags`.
- **Normalización:** `normalizeTags` recorta, descarta vacías, deduplica
  case-insensitive conservando la primera grafía y ordena case-insensitive. La
  UI reutiliza el helper; el repositorio lo aplica en `create`/`update`. Sin
  tope de cantidad/largo (decisión explícita) y sin orden de ingreso (se
  devuelven ordenadas por `label`).
- **Transacciones:** `create`/`update`/`delete` agrupan nota + etiquetas en
  `database.transaction`. `update` reemplaza el conjunto (delete + insert) sólo
  si la nota existía; `getAll` agrupa `selectAllNoteTags` en memoria para evitar
  N+1.
- **Migración verificada:** test compartido `verifyNoteTagMigration(driver)` en
  `commonTest` simula una base v1 con `NoteV1Schema` (SqlSchema sintético v1),
  corre `Schema.migrate(driver, 1, 3)` y valida que la nota sobrevive y
  `note_tag` queda operable. Corre en Android/JVM e iOS. Además se verificó la
  migración real sobre `playbook.db` v1 en emulador Android y simulador iOS
  (`user_version` 1→3, notas y `track`/`status` intactos).
- **UI:** el editor suma campo "Nueva etiqueta" + "Agregar" y chips con "×";
  la lista muestra una fila de chips de etiqueta. Sin dependencias nuevas
  (texto, no íconos) y sin cambio de `init.sh`.

## Attachment & Image Capture Decisions (image-capture-camera)

Implementado 2026-10-09. Detalles en `ARCHITECTURE.md`.

- **Archivo en disco + ruta relativa (no BLOB):** los bytes de la imagen no se
  guardan en SQLite. Un BLOB puede superar el límite del `CursorWindow` de Android
  (~2 MB) y lanzar `SQLiteBlobTooBigException`, y cargar BLOBs encarece cada
  lectura de la lista. Se escribe el archivo en almacenamiento privado y la fila
  `attachment` guarda **metadatos + ruta relativa** (`images/<uuid>.jpg`). La ruta
  es relativa porque en iOS el path absoluto del sandbox cambia entre
  instalaciones/updates.
- **Esquema v4 + migración `3.sqm`:** `Attachment.sq` (tabla `attachment`
  + índice `attachment_note_id`) y `3.sqm` (v3 → v4, `CREATE TABLE attachment` +
  índice). Como `embeddings-generation` (`1.sqm`, v1→v2) y `note-category-and-tags`
  (`2.sqm`, v2→v3) ya ocupaban las versiones previas, esta feature es la cuarta:
  `PlaybookDatabase.Schema.version = 4`. `AndroidSqliteDriver`/`NativeSqliteDriver`
  aplican create/migrate al abrir; `createDatabase(...)` sigue forzando la apertura
  con el `PRAGMA`. No se toca `note`, así que los datos previos se conservan.
- **Cascade sin huérfanos:** `SqlDelightNoteRepository.delete(id)` corre una
  transacción (`transactionWithResult`) que borra `attachment` de la nota y luego
  la `note`; devuelve `rowsAffected > 0`. Los archivos físicos los borra la UI con
  el `AttachmentFileStore` (si el borrado físico falla, queda un huérfano de
  archivo tolerado y documentado).
- **Android cámara (finding):** `ActivityResultContracts.TakePicture` **no** agrega
  `FLAG_GRANT_READ/WRITE_URI_PERMISSION`; la app de cámara fallaba al escribir el
  output con `RemoteException` en `ContentResolver.openOutputStream`
  (`checkAssociationAndPermissionLocked`). Se reemplaza por un intent
  `ACTION_IMAGE_CAPTURE` lanzado con `StartActivityForResult` que agrega ambos
  flags + `clipData`. `FileProvider` (`androidx.core:core`, transitivo por
  `activity-compose`) requiere `<provider>` con `${applicationId}.fileprovider` y
  `res/xml/file_paths.xml` (`<cache-path path="images/" />`). `CAMERA` se pide en
  runtime; la galería usa `PickVisualMedia` (sin permiso).
- **iOS pickers:** cámara con `AVCaptureDevice` (chequeo de availability +
  `authorizationStatusForMediaType`/`requestAccessForMediaType`) y
  `UIImagePickerController` presentado desde el controller superior; en simulador
  no hay cámara → `CAMERA_UNAVAILABLE`. Galería con `PHPickerViewController`
  (sin permiso). `loadObjectOfClass(UIImage)` no resuelve en el binding (espera
  `NSItemProviderReadingProtocol`), así que la carga se hace con
  `loadDataRepresentationForTypeIdentifier("public.image")` → `NSData` →
  `UIImage.imageWithData`. La imagen se re-encodea a JPEG
  (`UIImageJPEGRepresentation`, 0.9). Delegates de UIKit son weak: se retienen en
  un holder. `decodeImageBitmap` usa `org.jetbrains.skia.Image`; el file store usa
  `Application Support/images/`.
- **Tests nuevos:** `verifyAttachmentCrud(driver)` (create/getByNoteId/getAll/
  deleteByNoteId + `AttachmentKind.fromCode` + cascade al borrar la nota) y
  `verifyMigrationV1ToV4(driver)` (construye v1, aplica `Schema.migrate(1, 4)`,
  verifica que las notas v1 se conservan y `attachment` queda usable) en
  `commonTest`; corren en Android/JVM (`AttachmentRepositoryAndroidTest`,
  `MigrationAndroidTest`) e iOS (`AttachmentRepositoryIosTest`, `MigrationIosTest`).

## OCR Decision (image-ocr-optional)

Implementado 2026-10-13. Decisión en `docs/adr/0002-ocr-decision.md`.

- **Decisión (feature opcional/min):** **DISCARD** del OCR para el MVP. No se
  integró ningún motor, no se agregaron dependencias ni se tocó `:core`/UI/esquema
  (`Schema.version == 4`, sin `.sqm` nuevo). El entregable es el registro de
  decisión + la actualización de docs durables + el estado de la feature.
- **Por qué no integrar:** (1) el objetivo es **texto manuscrito** y los motores
  on-device de bajo costo (ML Kit Text Recognition, iOS Vision) están orientados a
  **texto impreso**, con calidad incierta sobre manuscrito; (2) Android exigiría
  una dependencia nativa pesada (ML Kit) con **poca superficie compartida KMP**,
  creando asimetría con iOS; (3) el puerto `AiClient` es **text-only** y no acepta
  imágenes (extenderlo a visión está fuera de alcance); (4) el OCR cloud viola
  offline-first y la política de secrets del ADR 0001; (5) el OCR no es requisito
  del MVP (`docs/build-brief.md`). Trade-offs por alternativa en el ADR.
- **Patrón futuro (no creado hoy):** si se reabre, el encaje natural es un puerto
  `TextRecognizer` + adaptadores por plataforma (análogo a `VoiceTranscriber`),
  consumiendo la imagen ya capturada por `image-capture-camera` y persistiendo el
  texto derivado en la **Nota** (no en el `Adjunto`).
- **Disparadores de re-evaluación:** adaptador `AiClient` con visión disponible;
  necesidad de texto derivado de `Adjunto` para RAG; evidencia de calidad de OCR
  sobre **manuscrito** en una plataforma objetivo sin dependencia pesada.
- **Límite honesto:** no se ejecutó OCR ni se midió calidad; el descarte se
  fundamenta en la evaluación de alternativas y las restricciones del repo, no en
  mediciones. Sin cambios de producto, dependencias ni esquema.

## Voice Attachment / Audio Recording Decisions (voice-attachment-storage)

Implementado 2026-10-13. Detalles en `ARCHITECTURE.md`.

- **Misma sesión de micrófono, un solo puerto:** el audio original se graba en la
  misma sesión del STT (no se abre un segundo cliente de micrófono, que competiría
  por el recurso). Se cambia la firma de `VoiceTranscriber.onFinalResult` de
  `(String) -> Unit` a `(String, RecordedAudio?) -> Unit`: la sesión entrega el
  texto **y** el Adjunto ya cerrado en un único callback terminal, evitando la
  carrera entre el resultado del reconocedor y el cierre del archivo.
  `RecordedAudio(relativePath, byteSize, mimeType)` es un `data class` en
  `:composeApp/commonMain/voice`.
- **Valor de enum sin cambio de esquema:** `AttachmentKind.AUDIO("audio")` se suma
  a `:core`; el mapper y `AttachmentRepository.create(...)` ya aceptaban cualquier
  `kind`, así que `PlaybookDatabase.Schema.version` sigue en **4**, no hay `.sqm`
  nuevos y `Attachment.sq` no cambió. `fromCode("audio")` pasa; un código
  desconocido sigue fallando fail-fast.
- **Formato y ubicación:** AAC en contenedor MPEG-4 (`.m4a`), `mimeType =
  "audio/mp4"`, ruta relativa `audio/<uuid>.m4a` bajo el mismo root privado que las
  imágenes (`filesDir/audio/` en Android, `Application Support/audio/` en iOS). El
  root coincide con el que resuelve `AttachmentFileStore.delete(relativePath)`, así
  que el borrado al borrar la Nota cubre el audio sin cambios.
- **Android (`MediaRecorder`):** en `startRecognition()` se arranca
  `MediaRecorder` (`AudioSource.MIC`, `OutputFormat.MPEG_4`, `AudioEncoder.AAC`)
  con salida a `filesDir/audio/<uuid>.m4a`, en paralelo con `SpeechRecognizer`.
  `MediaRecorder(context)` requiere API 31+; en API < 31 se usa el constructor sin
  args (deprecado, `@Suppress("DEPRECATION")`). `stop()` lanza si no hay datos
  válidos de audio: se captura y se devuelve `null`. Cualquier fallo al
  iniciar/escribir se traga y se sigue sólo con la transcripción (degradación
  tolerante).
- **iOS (`AVAudioFile` AAC):** el tap de `AVAudioEngine.inputNode` ya recibía cada
  `AVAudioPCMBuffer` para el reconocedor; ahora además se escribe al
  `AVAudioFile`, creado **perezosamente** con el formato del primer buffer:
  settings `AVFormatIDKey = kAudioFormatMPEG4AAC` (constante `UInt` de
  `platform.CoreAudioTypes`, envuelta con `NSNumber.numberWithUnsignedInt`),
  `AVSampleRateKey`/`AVNumberOfChannelsKey` del buffer y `AVEncoderBitRateKey =
  64_000`. Si `AVAudioFile` no se crea o `writeFromBuffer` devuelve `false`, se
  marca fallo y se degrada a Nota sin Adjunto. `AVAudioFile(forWriting:settings:error:)`
  es un inicializador designado mapeado a constructor Kotlin; se envuelve en
  `try/catch`. El tamaño se lee con `NSFileManager.attributesOfItemAtPath` +
  `NSFileSize`.
- **Ciclo de vida y limpieza (sin huérfanos):** en `App.onFinalResult(text, audio)`
  si el texto queda vacío → `NO_MATCH` y se borra el audio; si hay texto → se crea
  la Nota y, si hay audio, se persiste el Adjunto (`AttachmentKind.AUDIO`); si la
  persistencia del Adjunto falla se borra el archivo y se conserva la Nota
  (degradación tolerante, Nota primero). `cancel()` descarta el parcial y `onError`
  también (no hay Nota en esos caminos).
- **UI por tipo:** `NotesListScreen` deja `attachmentCounts: Map<String, Int>` y
  pasa a `attachmentKinds: Map<String, Set<AttachmentKind>>`; renderiza chips
  textuales por tipo en orden estable (`AttachmentKind.entries` → "Imagen", luego
  "Audio"), sin comunicar el estado sólo con color. `App` calcula el mapa desde
  `attachments`. La pantalla separada `VoiceCaptureScreen` de esta feature fue
  **retirada** por `inline-dictation-30s`: hoy el dictado vive en el editor de
  Nueva nota (ver "Inline Dictation Decisions" al final).
- **Sin dependencias ni permisos nuevos:** `MediaRecorder` es framework Android;
  `AVAudioFile`/`AVFoundation`/`CoreAudioTypes` son platform libs de Kotlin/Native.
  El micrófono es el mismo `RECORD_AUDIO`/`NSMicrophoneUsageDescription` de
  `voice-capture-stt`; no se agrega permiso ni dependencia Gradle.
- **Verificación:** el happy path de voz real no es inyectable de forma
  determinista, así que se verifica con smoke manual. Android (emulador
  `sdk_gphone64_arm64`/API 35): un archivo parcial `audio/<uuid>.m4a` sí se crea
  (confirmado con `run-as` + logcat de `MediaRecorder`/`AudioRecord`); con silencio
  el motor devuelve `NO_MATCH` y el archivo se borra sin crear Nota; "Grabar" +
  "Cancelar" en vivo borra el parcial, vuelve a la lista y no deja fila; borrar la
  Nota elimina la fila `attachment` y el archivo de audio (la imagen queda). El
  chip "Audio" y su coexistencia con "Imagen" se verificaron sembrando la base en
  Android e iOS. El round-trip del Adjunto `AUDIO` está cubierto por tests de
  `:core` en Android/JVM e iOS. Límite de entorno: los emuladores están
  compartidos con otras sesiones paralelas, que reinstalan/limpian el paquete y
  roban el foco; el happy path con voz real queda pendiente de dispositivo físico.

## Release Decisions (ios-release-pipeline)

Implementado 2026-10-09 (artefactos repo-side; upload account-gated). Detalle en
`ARCHITECTURE.md` y runbook `docs/release/ios-testflight.md`.

- **Herramienta:** shell + Xcode CLI, sin agregar `fastlane`/Ruby al repo (se
  mantiene la decisión de no sumar dependencias sin justificación). El CLI de
  subida confirmado en la Xcode instalada es `xcrun altool` 27.0.5 (2.1); el
  fallback declarado es `iTMSTransporter` y, como último recurso, `fastlane
  pilot` (requeriría Gemfile, no adoptado).
- **Firma:** `Release` cambia `CODE_SIGN_IDENTITY` de `Apple Development` a
  `Apple Distribution`; `Debug` queda igual. `PRODUCT_BUNDLE_IDENTIFIER` deja de
  concatenar `${TEAM_ID}` y pasa a `${BUNDLE_ID}` (`com.playbook.app`) para
  registrar un App ID estable. El `TEAM_ID` real vive en `Local.xcconfig`
  (gitignored) o env, no en el repo.
- **Versionado:** `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` como build
  settings; `Info.plist` deja de hardcodear `1.0`/`1` y usa `$(...)`. Los
  overrides de `archive.sh` permiten un build number único por subida
  (TestFlight rechaza repetidos).
- **Verificación sin credenciales:** `archive.sh --unsigned`
  (`CODE_SIGNING_ALLOWED=NO`) produce `build/ios/Playbook.xcarchive` y prueba que
  la app compila/enlaza en Release. `preflight.sh` falla con exit != 0 y
  `[FAIL]` accionables por prerequisito. Los artefactos de firma/release están en
  `.gitignore`.
- **Bloqueo:** sin Apple Developer Program + credenciales (`TEAM_ID`, identidad
  o API key) no se puede firmar/exportar/subir; los Scenarios 3–6 quedan
  account-gated en `ios-release-pipeline-first-upload` (`blocked`). El
  **scaffolding** repo-side está verificado y `accepted`.

## GDD Decisions (gdd-view)

Implementado 2026-10-13. Detalle en `ARCHITECTURE.md`.

- **GDD = proyección, no entidad:** el GDD agrupa/resume las Notas por `track` y
  **no** tiene tabla, query ni migración propias (`docs/domain-model.md`). Se
  reutiliza `NoteRepository.getAll()` (ya ordenado `updated_at DESC, id DESC` y
  con etiquetas); el esquema sigue en **v4** sin `.sqm` nuevos.
- **Terminología reconciliada:** el entry de `feature_list.json` decía "por
  categoría/etiqueta" (obsoleto). No hay agrupación por Etiqueta ni
  Categoría/Tipo/Nivel: el `track` (enum fijo) estructura el GDD y las Etiquetas
  se muestran como chips. `docs/specs/note-category-and-tags.md` ya lo había
  señalado.
- **Agregación en `:core` (testeable):** `com.playbook.core.gdd.buildGdd(notes):
  List<GddSection>` es una función pura sobre el modelo (sin SQLDelight ni
  `AiClient`). Orden fijo `Track.entries` (`mecánicas`/`personajes`/`historia`)
  + "Sin track" (`track = null`) al final **sólo** si hay notas sin `track`; las
  tres secciones de `track` siempre están presentes. Conserva el orden de entrada
  y no muta ni duplica notas. Se cubre con `verifyGddProjection()` en `commonTest`
  corrido en Android/JVM e iOS (sin harness de UI no se puede testear la pantalla;
  la lógica sí queda con tests cross-platform deterministas).
- **UI fina sin DI/ViewModel/Flow:** `GddScreen` recibe `notes`/`attachmentCounts`
  y sólo emite `onOpenNote`/`onBack`. Encabezado "GDD" + total ("N notas"), un
  `LazyColumn` con una sección por `track` (conteo "N notas" y texto "Sin notas en
  este track" si vacía) y las notas resumidas (cuerpo a 2 líneas + chips de
  `status`/Etiquetas/"Imagen"). Todo con texto, no sólo color (`DESIGN.md`).
- **Navegación local:** nuevo `NotesDestination.Gdd`; "Ver GDD" en
  `NotesListScreen` abre el GDD; tocar una nota abre el editor existente
  (`NotesDestination.Edit`); "Volver" regresa a `List`. Sin librería de
  navegación. **Limitación aceptada:** al guardar/cancelar en el editor se vuelve a
  la lista, no al GDD (un `previousDestination` llegará con una feature de
  navegación/detalle).
- **Refresco:** `GddScreen` consume el estado hoisteado por `App` (recargado por
  `refreshKey`); no lee la base en el cuerpo de composición. Editar/crear/borrar
  desde otra pantalla se refleja al reabrir el GDD.
- **Sin deps ni esquema:** `Scaffold`/`Card`/`LazyColumn`/`Surface` ya estaban;
  no se agregaron dependencias y `init.sh` no cambió. `LOCAL_OWNER_ID`/`:core`
  sin cambios de contrato.

## GDD Export Decisions (gdd-export)

Implementado 2026-10-15. Detalle en `ARCHITECTURE.md`.

- **Export = proyección pura, no entidad ni archivo:** el GDD se comparte como
  **texto Markdown en memoria**, sin `FileProvider`, sin guardar archivos, sin
  permisos, sin PDF y sin backend. La construcción vive en `:core`
  (`buildGddMarkdown(notes, now)`), es determinista y reusa `buildGdd` (misma
  agrupación/orden por `track`, omitiendo secciones vacías). Sin tabla, query ni
  migración: el esquema sigue en **v5** (sin `.sqm` nuevo). Nada de Adjuntos ni
  Notas relacionadas en el documento (sólo Notas, Etiquetas y `status`).
- **`now` inyectado como `String`:** el timestamp se formatea en la plataforma
  (Android `SimpleDateFormat("yyyy-MM-dd HH:mm")`, iOS `NSDateFormatter`) y llega
  a `:core` como `String`; así `:core` no agrega dependencia de fecha y los tests
  fijan `now` → salida determinista. `now` sólo se usa en el fallback
  `Nota <now>` de un `body` vacío.
- **Escapado del contenido de usuario:** backslash-escape del set
  `` \ ` * _ { } [ ] ( ) # + - . ! | > `` en título, cuerpo (resto) y Etiquetas.
  Los valores generados (labels de sección `track.code`/`Sin track`, `status.code`
  y el fallback `Nota <now>`) **no** se escapan. Cubierto por el escenario 6 en
  `:core` (Android/JVM e iOS).
- **Puerto de share `GddSharer` (expect/actual), sin DI:** `interface GddSharer
  { fun share(markdown: String): Boolean }` + `@Composable expect fun
  rememberGddSharer()` y `expect fun currentTimestampLabel()`. Android:
  `ACTION_SEND` `text/plain` + `createChooser` (devuelve `false` sin app que lo
  reciba); iOS: `UIActivityViewController` desde el `topViewController()` extraído
  a un helper **interno compartido** (`IosViewControllers.kt`) y reusado por
  `ImageCapture.ios.kt` (sin cambiar su lógica de captura). `share` devuelve un
  `Boolean` honesto: la UI muestra "Listo para compartir"/"No disponible" en un
  **snackbar**.
- **Snackbar autocontenido en `GddScreen`:** se agregó un `Scaffold` con
  `SnackbarHostState` + `rememberCoroutineScope()` **dentro** de la pantalla y un
  `TextButton` "Compartir" (`enabled = notes.isNotEmpty()`). No se agregaron
  parámetros a `GddScreen`, por lo que **`App.kt` no cambió** (evita el conflicto
  con `offline-pending-retry`, que sí toca `App.kt`).
- **Sin dependencias nuevas:** `Scaffold`/`SnackbarHost`/`rememberCoroutineScope`
  ya estaban (`compose.material3` + coroutines); el timestamp usa APIs nativas.
  `init.sh` no cambió. **Límite honesto:** el gesto de compartir en iOS no es
  automatizable en este entorno (sin `idb`/`cliclick`/`applesimutils`) y no había
  `adb`/emulador Android para el smoke interactivo; la lógica del documento queda
  cubierta por tests del core en dos plataformas y la app compila/enlaza en ambas.

## GDD Document Decisions (gdd-document-hybrid)

Implementado 2026-10-20. Detalle en `ARCHITECTURE.md` y `docs/specs/gdd-document-hybrid.md`.

- **Documento híbrido, sin entidad nueva:** `buildGddDocument(project, notes)` en
  `:core` compone la ficha del `GameProject` (`GddProjectBlock`) y las secciones
  de Notas (`GddDocumentSection`) reutilizando `buildGdd`. Sigue sin tabla,
  query, migración ni duplicación de Notas: el esquema queda en **v8** con
  `1.sqm`..`7.sqm` y sin `.sqm` nuevos.
- **Ficha = campos actuales del Proyecto:** orden fijo `Géneros`, `Core Loop`,
  `Audiencia`, `Concepto`, `Inspiraciones`, `Fantasía`, `Mecánicas clave`, `Fuera
  de alcance`; sólo campos no vacíos, listas con `trim` y orden preservado, y
  mecánicas `"Categoría · descripción"` (categoría capitalizada; el `code`
  persistido no cambia). El `Título` se normaliza y vive en la cabecera `GDD —
  <título>`, no como campo.
- **Historia/Lore y Personajes salen de las Notas:** no hay campos dedicados en
  `GameProject`; esas secciones son las Notas de `Track.STORY`/`Track.CHARACTERS`.
  `concept`/`fantasy` quedan sólo en la ficha para no duplicar contenido.
- **UI fina y estados explícitos:** `GddScreen` recibe `project` desde `App`,
  calcula el documento con `remember(project, notes)` y renderiza la ficha en una
  `Card` destacada + secciones en un único `LazyColumn`. `project == null` →
  "Todavía no hay Proyecto"/"Completalo desde Brainstorming."; Proyecto vacío →
  "Proyecto sin datos adicionales"; sin Notas → ficha + "Todavía no hay notas"
  (sin secciones vacías). Sin DI, `ViewModel`, Flow ni navegación nuevos.
- **Export intacto:** "Compartir" sigue usando `buildGddMarkdown(notes, now)` y
  `GddSharer` **sólo con Notas** (deshabilitado sin Notas). Incluir la ficha en
  el Markdown es `gdd-export-hybrid`, que depende de esta feature.
- **Verificación:** `verifyGddDocument()` cubre escenarios 1–7 en Android/JVM e
  iOS; smoke Android del documento completo, sin Proyecto con Notas, Proyecto
  vacío y sin Notas. Sin dependencias nuevas.

## Observability Decisions (enrichment-observability-logging)

Implementado 2026-10-14. Detalles en `ARCHITECTURE.md` y `CONSTRAINTS.md`.

- **Puerto `Diagnostics` best-effort, sin librería ni telemetría:** un único
  método `recordFailure(source, summary, detail?)` que **nunca** lanza ni bloquea.
  No se adoptó Timber/Kermit/`os_log` ni crash reporting remoto (no permitido en el
  MVP). Sólo se registran **fallos**, no caminos de éxito; sin niveles/sampling/
  rotación multi-archivo.
- **Default no-op retrocompatible:** `NoteIndexingService`/`RagQueryService` y
  `App` reciben `diagnostics: Diagnostics = NoOpDiagnostics`, así los callers y
  tests existentes siguen intactos; los composition roots inyectan el sink real.
- **Sink local acotado, sin esquema:** `FileDiagnostics` escribe líneas
  `<ts>\t<source>\t<summary>\t<detail|->` en un archivo privado; retención **por
  cantidad** (`maxEntries`, default 200) y `detail` recortado a ~200 chars. El log
  es **infra**, no modelo: no hay entidad `Log`, ni tabla, ni `.sqm` (esquema v5).
  `DiagnosticStorage` es `expect`/`actual` (`java.io.File` en Android/JVM;
  `NSFileManager`/`NSString` con append = read + overwrite en iOS).
- **Path por plataforma:** `filesDir/diagnostics.log` (Android) y
  `Application Support/diagnostics.log` (iOS), provisto por cada composition root.
- **Privacidad (durable):** sólo fuente, resumen estable y mensaje de excepción
  recortado. **Nunca** `body`, pregunta, rutas, IDs ni secrets. El `detail` de
  `STORAGE` se omite a propósito (las excepciones de IO/SQL pueden traer rutas).
- **Instrumentación:** `AI_INDEXING` en cada `Failed` de indexado (excepción o
  respuesta inválida); `AI_QUERY` en cada `Failed` de RAG; `NoIndexedNotes` no
  registra. `VOICE`/`IMAGE` en los `onError` nativos (con el nombre del error) y
  `STORAGE` en los `catch (Throwable)` de persistencia de adjuntos.
- **Verificación:** `verifyDiagnostics(driver)` cubre los escenarios 1–7 en
  `commonTest` (Android/JVM e iOS) y `verifyDiagnosticStorage(...)` ejercita el
  `actual` real de cada plataforma; smoke Android real con el log
  `IMAGE image_failed PERMISSION_DENIED` tras denegar el permiso de cámara.
  Control negativo reproducido y restaurado (sin registro de `AI_INDEXING`; sin
  retención).

## Platform Scope Decisions (Android-first / iOS congelado)

Revisión de alcance 2026-10-15. Decisión en `docs/adr/0003-ios-congelado-mvp.md`.

- **Android-first; iOS congelado:** iOS sale del alcance del MVP y de la
  verificación. Se conservan `iosApp/` y los `actual` de `iosMain`; el proyecto
  sigue **compilando** iOS, pero no se exige paridad ni se ejecuta en el gate.
- **Gate `init.sh` Android-only:** deja de correr
  `:composeApp:linkDebugFrameworkIosSimulatorArm64` y `:core:iosSimulatorArm64Test`.
  Pasa a: `:composeApp:assembleDebug` + `:core:testDebugUnitTest`.
- **Nuevos `expect`:** deben seguir aportando un `actual` iOS mínimo (aunque no se
  verifique) para no romper el build compartido.
- **Sugerencias de Brainstorming:** vía `AiClient.generate` (2 por paso, a
  demanda); hoy `FakeAiClient`.
- **Miniaturas de imagen:** implementadas en `sketch-thumbnails` (2026-10-19).
  El store de Adjuntos se reubicó en `:core` (`com.playbook.core.storage`) y se
  extendió con `read(relativePath): ByteArray?`; la UI decodifica con
  `decodeImageBitmap()` (sin deps nuevas). El cacheo/redimensionado sigue
  diferido. Ver "Thumbnail Decisions".
- **Esquema v7 (Géneros):** renombre N—N a **Géneros** (catálogo fijo +
  personalizados) con `6.sqm` v6→v7, copiando las etiquetas libres a Géneros
  personalizados (verbatim).
- **Esquema v8 (Proyecto, 2026-10-19):** entidad **Proyecto** (ficha + hasta 5
  mecánicas) con `7.sqm` v7→v8 aditivo; `GameProjectRepository` en `:core`, sin
  UI ni wiring.
- **Catálogo de Géneros:** constante de la app (Platformer, Metroidvania, JRPG,
  ActionRPG, Roguelike, Shooter, Puzzle, Adventure, Towerdefense, Novel, Farming,
  Arcade); los personalizados se guardan por Nota y no engordan el catálogo global.

## AI Real Integration Decisions (proveedor propio)

Revisión de alcance 2026-10-15. Decisión en `docs/adr/0004-ai-real-integration.md`.

- **Proveedor propio, OpenAI-compatible:** base `https://inference.devexpert.io/v1`.
  Un solo proveedor para los 6 modelos; `AiClient` (hoy text-only: `embed`+
  `generate`) se extiende con puertos para los demás (transcribe/tts/image/jev) —
  diseño en `ai-client-ports`.
- **Un caso de uso por modelo (mínimo):** chat → Q&A/Brainstorming; embedding →
  indexado/enlaces/RAG; transcribe → Adjunto AUDIO; tts → lectura en voz alta
  (Q&A/Nota/GDD); image → generación desde texto; jev → sugerencia de
  clasificación (Track/Género, con confirmación).
- **Fake/real por build flag:** `fake` (determinista, **default y siempre en
  tests/gate**) o `real` (requiere **API key**). La key **nunca** se versiona
  (env/gradle property, gitignored). `init.sh` **no** usa la API real (sin red).
- **Manejo de fallos/offline:** los usos de IA respetan la tolerancia a fallos ya
  existente (indexado → `PENDING`/reintento; respuestas no bloquean la captura).
- **Sugerencia de clasificación (non-goal revisado):** la IA **sugiere** `Track`/
  `Género`; el usuario **confirma o edita**; **nunca** auto-asigna. El indexado
  sigue sin clasificar (`CONSTRAINTS.md`).

## App Icon Decisions (app-icon-configuration)

Feature de configuración de recursos (2026-10-16), sin código, deps ni esquema.

- **Fuente de verdad:** `docs/design/icon.png` (1024×1024, **RGB sin alfa**, fondo
  crema medido `#F7EFE2`); no se crea arte nuevo.
- **Adaptive icon (API 26+):** `mipmap-anydpi-v26/ic_launcher.xml` con
  `background = @color/ic_launcher_background` (`#F7EFE2`) y
  `foreground = @drawable/ic_launcher_foreground`; `android:roundIcon` apunta al
  **mismo** `@mipmap/ic_launcher`.
- **PNG legacy (API < 26):** `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
  (48/72/96/144/192 px).
- **Generación con `sips`** (macOS, **sin deps**): legacy `-z`; foreground
  center-crop `-c 800 800` (quita el marco redondeado del arte) → `-z 264 264`
  (safe zone ~66 % de 108 dp) → `-p 432 432 --padColor F7EFE2`. El padding del
  foreground == color de `background` ⇒ **sin costura** dentro del mask. Los PNG
  derivados se **commitean**; el repo no regenera arte en build.
- **Foreground en `drawable-nodpi`** (un único bitmap 432 px; el sistema escala).
- **Manifest:** `<application>` declara `android:icon` y `android:roundIcon`
  (sin tocar `label`, permisos ni `FileProvider`).
- **iOS intacto:** iOS congelado (`docs/adr/0003-ios-congelado-mvp.md`); `AppIcon`/
  assets sin cambios. Sin `monochrome`/themed icon, splash ni store assets.

## AI Ports Decisions (ai-client-ports)

Implementado 2026-10-16. Contratos en `AiPorts.kt`; fakes en `FakeAiPorts.kt`.

- **Mapeo modelo → puerto (ADR 0004):** `chat` → `AiClient.generate`; `embedding`
  → `AiClient.embed` (ambos ya existentes, no se duplican); `transcribe` →
  `TranscribeClient.transcribe(audio, mimeType): String`; `tts` →
  `TtsClient.synthesize(text): SynthesizedAudio`; `image` →
  `ImageClient.generateImage(prompt): GeneratedImage`; `jev` →
  `JevClient.suggest(text): ClassificationSuggestion`.
- **Puertos independientes, sin bundle:** los 4 puertos nuevos son `interface`
  sueltas (sin agregado `AiRuntime`); la inyección conjunta y el build flag
  fake/real son de `ai-fake-real-switch`.
- **Tipos:** `SynthesizedAudio`/`GeneratedImage` son `class` (no `data class`)
  porque contienen `ByteArray` (su `equals` sería por identidad); los tests
  comparan `bytes`/`mimeType`. `ClassificationSuggestion` es `data class`
  (`track: Track?`, `genres: List<String>`) y **sólo propone** —el usuario
  confirma—; la canonización de Géneros contra el catálogo es del consumidor
  (`genres-catalog-persistence`), el fake sólo devuelve un género ilustrativo.
- **Fakes deterministas y sin red** (`commonMain`, inyectables): texto derivado
  de tamaño+mime (`FakeTranscribeClient`, tolera `audio` vacío), audio = bytes
  UTF-8 del texto (`FakeTtsClient`, `audio/mpeg`), PNG simbólico derivado del
  prompt (`FakeImageClient`, `image/png`), `Track` por hash sobre `Track.entries`
  + género ilustrativo (`FakeJevClient`). Sin `Random`, sin red ni key.
- **Tests:** contratos `verify{Transcribe,Tts,Image,Jev}ClientContract` +
  comportamiento de fakes (`verifyFakeAiPorts`, Scenario 6) y una implementación
  test-only `ConstantTtsClient` que demuestra sustituibilidad sin tocar archivos
  de producción; corren en Android/JVM (`AiPortsAndroidTest`, 6) e iOS
  (`AiPortsIosTest`, 6).
- **Sin cambios** en `AiClient`/`FakeAiClient`/`AiAvailability`/servicios,
  esquema SQLDelight (v6, sin `.sqm` nuevo), dependencias ni `init.sh`. Los
  adaptadores reales, el build flag y los casos de uso son features posteriores.

## Géneros UI Decisions (genres-catalog-ui)

Implementado 2026-10-19. Sólo `:composeApp` + docs; sin cambios de esta feature
en `:core`/esquema (el global es **v8** con `7.sqm` por
`game-project-persistence`), `App.kt`, build files, dependencias ni `init.sh`.

- **Picker (`GenrePicker.kt`, `internal`):** "Géneros" con tres zonas — (1) si
  hay elegidos, "Seleccionados" con chips removibles `×`; (2) "Catálogo" con los
  12 `GenreCatalog.GENRES` **siempre visibles** en un `FlowRow` (estable en
  Compose MP 1.8.2, sin opt-in experimental); (3) "Género personalizado"
  (`OutlinedTextField` con placeholder "Escribí uno nuevo…") + "Agregar"
  habilitado sólo con texto no blanco.
- **Toggle:** tocar un chip del catálogo lo agrega si no está y lo quita si está
  (comparación case-insensitive). **Toda** alta/remoción pasa por
  `normalizeGenres` de `:core` (trim, canonización contra el catálogo, dedupe y
  orden), así que `jrpg` deviene `JRPG` sin duplicar. Sin límites de cantidad ni
  largo; el truncado es sólo visual (`maxLines = 1` + ellipsis).
- **Selección ≠ sólo color:** el chip de catálogo seleccionado muestra `✓`, borde
  de 2.dp y `semantics { selected = true }`; los controles usan
  `Modifier.clickable(onClickLabel = "Agregar género X"/"Quitar género X", role =
  Role.Button)` y `defaultMinSize(minHeight = 48.dp)`. En Android, Compose mapea
  `SemanticsProperties.Selected` con `Role.Button` a `checkable/checked` (no a
  `isSelected`), verificado en el UI dump del smoke.
- **Chip de lectura (`GenreLabelChip`):** en `LabelChip.kt`; detecta el catálogo
  case-insensitive y usa `primaryContainer`/`onPrimaryContainer` para catálogo y
  `secondaryContainer`/`onSecondaryContainer` para personalizado, con texto a una
  línea y ellipsis. `LabelChip` queda igual para `track`/`status`/adjuntos. Se usa
  en las filas de Géneros de tarjetas, GDD y Notas relacionadas.
- **Integración mínima:** `NoteEditorScreen` delega en
  `GenrePicker(selected = genres, onChange = { genres = it })`, conserva
  `genres = normalizeGenres(initialGenres)` y el contrato
  `onSave(body, track, genres)`; se elimina el `GenreChip` privado de texto libre.
  `App.kt` no cambia.
- **Layout del editor (fix 2026-10-19):** el contenido es scrollable
  (`verticalScroll(rememberScrollState())`) y la fila `Borrar/Cancelar/Guardar`
  queda fija fuera del scroll; el cuerpo usa `heightIn(min = 200.dp, max =
  320.dp)`. Motivo: con el picker siempre visible y "Seleccionados", en modo
  edición el cuerpo quedaba en una línea y las acciones se recortaban fuera del
  viewport. El picker no se colapsa ni se limita; `App.kt`/`:core` no cambian.
- **Verificación:** `assembleDebug` (60 tasks en la base rebasada),
  `:core:testDebugUnitTest` (55/55 en la base rebasada con `game-project-persistence`;
  53/53 en la base pre-integración), `./init.sh` exit 0, link iOS no
  bloqueante y smoke Android (12 chips en orden, toggle, `" jrpg "` → `JRPG`,
  personalizado, tarjeta/GDD/relacionadas, reapertura y persistencia tras
  relanzar; fix de layout verificado con 3 y 13 Géneros, GDD con personalizado y
  `jrpg` sembrado → `JRPG`). Evidencia en
  `composeApp/build/smoke-evidence/genres-catalog-ui/` (`29`–`41`).

## AI Real Switch Decisions (ai-fake-real-switch)

Implementado 2026-10-19. Proveedor propio OpenAI-compatible
(`https://inference.devexpert.io/v1`) detrás de los puertos existentes.

- **Flag/config:** `playbook.ai.mode` (`PLAYBOOK_AI_MODE`, default `fake`) con
  precedencia `-P` > env > `local.properties`; `init.sh` fuerza
  `-Pplaybook.ai.mode=fake`. Base URL, 6 modelos (`chat`, `embedding`,
  `transcribe`, `tts`, `image`, `jev`), voz (`alloy`), tamaño de imagen
  (`1024x1024`, quality low; el proveedor rechaza 512/256 y puede devolver JPEG)
  y dimensión de embedding (`1536`) configurables por propiedad.
- **Runtime:** `AiRuntime` (`com.playbook.core.ai.openai`) agrupa `AiClient` +
  `TranscribeClient`/`TtsClient`/`ImageClient`/`JevClient` + `AiAvailability` +
  `embeddingDimension`. `fakeAiRuntime()` es el default; `openAiAiRuntime(config,
  transport)` arma los adaptadores reales. El transporte de red es un puerto
  (`HttpTransport`); `UrlConnectionHttpTransport` vive en `:composeApp/androidMain`
  y los tests usan un transporte falso.
- **Adaptadores:** `POST /chat/completions` (`choices[0].message.content`),
  `POST /embeddings` (`data[i].embedding`, orden por `index`),
  `POST /audio/transcriptions` (multipart `model`+`file`+`response_format=json`,
  parsea `text`; mp3/m4a, ≤30 s, ≤25 MB), `POST /audio/speech` (bytes mp3),
  `POST /images/generations` (`data[0].b64_json` → bytes con mime detectado,
  PNG/JPEG) y `POST /decisions`
  (`answers.<q>.choice`; Track por código + un Género, sin auto-asignar).
- **Errores:** cualquier no-2xx, timeout, IO o JSON inválido →
  `AiClientException` con mensaje genérico por status; sin body, headers ni key.
  El transporte **no sigue redirects** (`instanceFollowRedirects=false`): un 3xx
  se reporta como no-2xx y el adaptador lo mapea a `AiClientException`, sin
  reenviar key/body/headers a otro host. El I/O bloqueante corre en
  `Dispatchers.IO` (dispatcher inyectable), nunca en el hilo Main; así el
  indexado/RAG reales no lanzan `NetworkOnMainThreadException` ni bloquean la UI.
- **Seguridad:** la key (`playbook.ai.apiKey` / `PLAYBOOK_AI_API_KEY` /
  `local.properties`) se inyecta en `BuildConfig` sólo en modo real; en fake el
  campo queda vacío. `real` es el default sólo en debug; release real exige
  opt-in por propiedad Gradle (`-Pplaybook.ai.allowReleaseKey=true`; env/local se
  ignoran; `findProperty` también resuelve `gradle.properties`/`ORG_GRADLE_PROJECT_*`,
  hardening estricto pendiente). Un real incompleto falla el build.
- **Embeddings:** `EmbeddingDimensionReconciler` borra los vectores cuya
  dimensión no coincide con el runtime activo y deja la Nota `PENDING` con
  `index_attempts=0`; el reintento de arranque la re-indexa. Sin `.sqm`.
- **Tests:** contractos de adaptadores con `HttpTransport` falso, JSON/base64,
  errores sanitizados, selección fake/real, `AiAvailability`, reconciler y
  transporte Android (servidor HTTP local). El smoke de los 6 modelos es
  key-gated (`PLAYBOOK_AI_SMOKE=1`), fuera del gate.
- **iOS:** congelado; usa `fakeAiRuntime()`, sin transporte real.

## Brainstorming Wizard Decisions (brainstorming-wizard)

Implementado 2026-10-19. Detalles en `ARCHITECTURE.md` y esta spec.

- **Sólo ficha del Proyecto.** El wizard de 9 pasos escribe únicamente
  `GameProject` con `GameProjectRepository.save(draft)`; no crea Notas,
  Adjuntos, embeddings ni llamadas de IA. La creación de Notas por paso se
  separó a `brainstorming-note-per-step` (`not_started`).
- **Guardado explícito.** "Guardar" envía un `GameProjectDraft` **completo**
  construido con todo el formulario; el repositorio reemplaza la ficha y sus
  hijas. Nunca se guarda sólo el paso actual (un draft parcial borraría campos).
  "Cancelar" descarta los cambios locales; "Atrás" no guarda. Reabrir precarga
  desde `get()` y se re-inicializa si el Proyecto termina de cargar (clave
  `updatedAt`).
- **Todos opcionales.** Se permite avanzar con campos vacíos y guardar un
  Proyecto vacío; el singleton se crea con `owner = "local"` y campos
  `null`/listas vacías. `owner`/`createdAt` se preservan en updates.
- **Géneros compartidos.** El paso Género reutiliza `GenrePicker` de
  `genres-catalog-ui` (catálogo + personalizado, `normalizeGenres`); no se
  duplica el picker ni se toca `NoteEditorScreen`.
- **Límites.** Inspiraciones ≤3, Fuera de alcance ≤3, Mecánicas ≤5. La UI
  deshabilita "Agregar" al tope; el repositorio valida después de normalizar.
  Las filas vacías se descartan. Una mecánica con descripción y sin categoría
  bloquea el guardado con un `AlertDialog`; una fila totalmente vacía se
  descarta.
- **Entrada temporal.** `NotesListScreen` suma un botón "Brainstorming" en una
  fila propia; `main-screen-playbook-shell` lo reemplazará por el icono de top
  bar. La apertura automática del onboarding del primer arranque se agregó en
  `first-run-project-onboarding`.
- **Sin IA ni rediseño.** No se muestran sugerencias; `brainstorming-ai-suggestions`
  las agrega después. Progreso con `LinearProgressIndicator` + `Paso X/9`, sin
  artefacto de diseño nuevo.
- **Verificación:** `assembleDebug` OK, `:core:testDebugUnitTest` 23 suites/55
  tests/0 failures con `--rerun-tasks`, `./init.sh` exit 0, link iOS no
  bloqueante y smoke Android (entrada, 9 pasos, guardado vacío, ficha completa,
  géneros catálogo + personalizado, topes 3/3/5, reapertura, cancelación,
  iteración/reemplazo y error de mecánica incompleta). Evidencia en
  `composeApp/build/smoke-evidence/brainstorming-wizard/`.

## Brainstorming AI Suggestions Decisions (brainstorming-ai-suggestions)

Implementado 2026-10-19. Detalles en `docs/specs/brainstorming-ai-suggestions.md`.

- **Servicio en `:core`:** `BrainstormingSuggestionService.suggest(step, draft)`
  arma el `GenerateRequest` (prompt con marcador
  `PLAYBOOK_BRAINSTORMING_SUGGESTIONS_V1` + `STEP=<enum>`) y un contexto acotado
  con campos no vacíos del `GameProjectDraft`; en Género incluye el catálogo. No
  envía `owner`, ids ni timestamps.
- **Parsing:** acepta líneas con numeración/viñetas, limpia y colapsa espacios,
  deduplica case-insensitive, descarta valores ya presentes en el borrador y
  trunca a 280 caracteres. Mecánicas exigen
  `Movimiento|Interacción|Conflicto: descripción`; una línea sin categoría válida
  se descarta. Máximo 2 ideas; 0 → `InvalidResponse`; 1 → `Success(partial)`.
- **Fake determinista:** `FakeAiClient.generate` devuelve 2 líneas parseables
  cuando el prompt contiene el marcador; el resto del fake (RAG/Q&A) conserva su
  respuesta anterior. Sin red ni key.
- **UI efímera:** `BrainstormingWizardScreen` recibe el servicio, muestra
  "Sugerir"/"Sugiriendo…", hasta 2 tarjetas y Usar/Agregar/Ignorar. Aceptar aplica
  al control existente (campo, chip, fila o mecánica); nunca auto-rellena ni
  guarda. Las sugerencias se descartan al cambiar de paso, cancelar, guardar,
  recargar el Proyecto o salir; los resultados tardíos se ignoran por paso.
- **Sin persistencia:** no hay tabla/entidad/migración (esquema v8 intacto) ni
  creación de Notas. `App` recibe `aiClient` y los roots pasan
  `aiRuntime.aiClient` (iOS fake).
- **Verificación:** `:core:testDebugUnitTest --rerun-tasks` 28 suites/87 tests/0
  failures post-rebase (7 nuevos); `:core:iosSimulatorArm64Test --rerun-tasks`
  28/87/0; `assembleDebug` OK; `CI=true ./init.sh` exit 0; link iOS OK; smoke Android con
  2 tarjetas, parcial por dedupe, Género/Inspiraciones/Mecánicas, limpieza al
  navegar y guardado v8. Evidencia en
  `composeApp/build/smoke-evidence/brainstorming-ai-suggestions/`.
## First-Run Project Onboarding Decisions (first-run-project-onboarding)

Implementado 2026-10-19. Detalles en `ARCHITECTURE.md` y esta spec.

- **Detección por estado, no por flag.** `App` usa `ProjectLoadState`
  (`Loading`/`Missing`/`Loaded(project)`) y lee `GameProjectRepository.get()` en
  el `LaunchedEffect(refreshKey)`. `Loading` sólo es el estado inicial; los
  `reload()` no lo reintroducen. `Missing` muestra la oferta full-screen y
  `Loaded` el destino normal.
- **Crear al aceptar o saltar.** Ambas acciones persisten primero
  `GameProjectDraft(owner = "local")` (singleton vacío, escalares `null`,
  listas/hijas vacías, `createdAt == updatedAt`, topes 3/3/5) y luego navegan:
  aceptar a `Brainstorming`, saltar a la lista. Cancelar el wizard deja el
  Proyecto vacío y no reabre el onboarding.
- **Idempotencia y error.** `GameProjectRepository.save` hace upsert y la UI
  bloquea dobles taps (`onboardingBusy`); un fallo registra
  `STORAGE`/`STORAGE_FAILED`, muestra `AlertDialog` y no navega. La existencia
  del singleton es la única señal: no hay flag "onboarding visto".
- **Verificación:** `assembleDebug` OK, `:core:testDebugUnitTest` 28 suites/87
  tests/0 failures (post-rebase), `CI=true ./init.sh` exit 0, link iOS no
  bloqueante y smoke Android real (oferta, saltar, aceptar+cancelar, relanzar sin
  oferta, aceptar+Guardar y error forzado) documentado en `PROGRESS.md`.

## Brainstorming Note per Step Decisions (brainstorming-note-per-step)

Implementado 2026-10-20. Detalles en `docs/specs/brainstorming-note-per-step.md`.

- **Mapper puro en `:core`.** `buildBrainstormingStepNote(step, draft)` (índice
  `0..8`) devuelve `BrainstormingStepNote(body, track, genres)`; usa el draft
  actual, normaliza Géneros, trimea/descarta vacíos, une listas con `\n` y
  formatea Mecánicas como `Categoría: descripción`. Track: Core Loop/Mecánicas →
  `MECHANICS`; Concepto/Fantasía → `STORY`; el resto `null`.
- **Duplicados.** `isDuplicateBrainstormingNote` normaliza **ambos** lados de los
  Géneros (tolera el orden binario de SQLite y grafías migradas); UI y `App` lo
  usan. Regresión con `["Zeta","omega"]`/`["omega","Zeta"]` y
  `"jrpg"/"Platformer"`/`"JRPG"/"platformer"`.
- **Nota final.** Acción explícita por paso, confirmación con preview, `status =
  CAPTURED` + `indexBestEffort`; cancelar el wizard conserva las Notas creadas y
  no toca `game_project`. El callback de `App` envuelve `getAll()` + `create` en
  `runCatching` y devuelve `FAILED` ante error.
- **Verificación:** `:core` 27 suites/81 tests/0 failures, `assembleDebug`,
  `CI=true ./init.sh` exit 0, link/tests iOS y smoke/SQL con Géneros
  personalizados; evidencia en
  `composeApp/build/smoke-evidence/brainstorming-note-per-step/`.

## AI Real Embeddings Decisions (ai-real-embeddings)

Implementado 2026-10-19. No duplica el flag/runtime/adaptadores/wiring de
`ai-fake-real-switch`: el diff de producción son dos límites de dimensión y el
pass-through de la dimensión a `NoteIndexingService`.

- **Contrato en el adaptador:** `OpenAiAiClient.embed` valida que **cada** vector
  mida `config.embeddingDimension` después de ordenar por `index` y validar
  cantidad. Un mismatch lanza
  `AiClientException("embedding devolvió dimensión N, se esperaba M")`; el
  mensaje sólo contiene dimensiones (sin key, headers ni body). Esto también
  cubre el `embed` de la pregunta en RAG.
- **Límite de persistencia:** `NoteIndexingService` recibe
  `expectedDimension: Int? = null` (`require` > 0 o `null`). Con `null` conserva
  el comportamiento previo; con valor, un mismatch devuelve
  `IndexingResult.Failed`, registra `AI_INDEXING` (`indexing_failed` con detalle
  sanitizado) y **no** llama a `embeddingRepository.upsert`. Los roots Android e
  iOS pasan `aiRuntime.embeddingDimension` (`1536` real / `8` fake), por lo que
  un cambio de modo no puede persistir vectores incompatibles.
- **Máquina de estados intacta:** el mismatch se comporta como cualquier fallo de
  IA: el indexado directo deja `PENDING` (nunca `FAILED`), el reintento consume
  presupuesto y promueve a `FAILED` al agotarlo, y un re-indexado fallido de una
  Nota `INDEXED` no la degrada ni pisa el embedding previo. La reconciliación
  fake(8)→real(1536) sigue borrando sólo los incompatibles y dejando
  `index_attempts=0`.
- **Verificación de integración:** `RealEmbeddingIndexingCheck` (commonTest +
  runners Android/iOS) conecta `openAiAiRuntime(config, HttpTransport falso)` con
  SQLDelight in-memory, `NoteIndexingOrchestrator`, `PendingIndexingRetry`,
  `EmbeddingDimensionReconciler`, `SemanticLinkingService` y `RagQueryService`.
  Cubre éxito 1536, mismatch no persistido, re-indexado fallido sin degradación,
  RAG con fuentes, Enlaces (score 1.0), red/500 con `PENDING`/presupuesto,
  fallo transitorio recuperado y reconciliación 8→1536. El transporte falso no
  valida calidad semántica ni disponibilidad del proveedor.
- **Smoke real key-gated:** `RealAiSmokeTest.smokeRealIndexingFeedsRag` (fuera del
  gate, `PLAYBOOK_AI_SMOKE=1`) indexa una Nota sobre una base in-memory y
  verifica dimensión = `runtime.embeddingDimension`, estado `INDEXED` y RAG con
  fuentes; imprime sólo longitudes/estado. Dependencia test-only
  `sqldelight.sqlite.driver` en `composeApp` (bloque `androidUnitTest`).
- **Riesgos aceptados:** el runtime real no sondea conectividad (arranques
  offline pueden agotar presupuesto y terminar en `FAILED`; sonda = follow-up);
  la reconciliación no recomputa `note_link` (Relacionadas se recomputa al
  abrir); la dimensión activa no se expone en UI/diagnóstico. Sin cambios de
  esquema (v8), `init.sh`, `App.kt`, `iosApp/**` ni dependencias de producción.

## Inline Dictation Decisions (inline-dictation-30s)

Implementado 2026-10-19. Detalles en `ARCHITECTURE.md`.

- **Se retira la pantalla separada:** desaparecen `VoiceCaptureScreen.kt`,
  `VoiceUiState` y `NotesDestination.VoiceCapture`. El dictado se integra en
  `NoteEditorScreen` **sólo en Nueva nota** (`Create` y `CreateWithImage`), dentro
  del contenido scrollable después de "Cuerpo" y antes de "Track"; editar una
  Nota existente no muestra el panel. "Dictar nota" en la lista abre `Create` y
  arranca la sesión.
- **Estado y dueño:** `Dictation.kt` (commonMain) define
  `DictationUiState` (`Idle`/`Listening(partial, elapsedMillis)`/`Processing`/
  `Error`), `DICTATION_MAX_MILLIS = 30_000`, `DICTATION_TICK_MILLIS = 100`,
  `VoiceCaptureError.message()` (movido desde la pantalla vieja) y
  `appendDictation(body, dictated)` (`trim`, omite vacíos, une con `\n`). `App`
  es dueña de la sesión, del timer, de `dictatedText` y de `pendingAudio`; el
  editor sólo renderiza y anexa.
- **Cap desde la sesión real (`onStarted`):** el puerto suma
  `onStarted: () -> Unit = {}` (aditivo). Android lo emite después de
  `startListening(...)` (permiso concedido y `MediaRecorder` lanzado); iOS sólo si
  `engine.startAndReturnError(...)` es `true` (si no, limpia y reporta `UNKNOWN`).
  No se emite en permiso denegado, `UNAVAILABLE` ni error de arranque. El timer de
  30s arranca en ese callback, así que el diálogo de permiso no consume el cap.
  `cancel()` limpia `onStarted` en ambos adaptadores.
- **Guard de generación en iOS:** `start()` incrementa un contador y captura el
  valor; los callbacks de autorización (speech y micrófono) y de reconocimiento
  verifican que siga vigente antes de tocar estado. `cancel()` lo incrementa, de
  modo que una sesión cancelada no arranca el engine, no emite `onStarted` ni
  entrega resultados tardíos.
- **Auto-stop y detener:** el timer mide `currentTimeMillis() - startedAt` (no se
  reinicia con cada parcial), actualiza cada 100ms acotado a 30s y al llegar al cap
  llama `stopDictation()` una sola vez. `stopDictation()` es idempotente (sólo
  desde `Listening`) y pasa a `Processing` + `voiceTranscriber.stop()`.
  `onStarted` tardío con estado `Processing` (el usuario detuvo antes) llama
  `stop()`; con `Idle`/`Error` se ignora.
- **Audio pendiente y reemplazo:** `onFinalResult(text, audio)` vacío borra
  **sólo** el audio nuevo, fija `Error(NO_MATCH)` y conserva el `pendingAudio`
  anterior; con texto y `audio != null` borra el pendiente anterior y guarda el
  nuevo; con texto y `audio == null` conserva el anterior. `onError` también
  conserva el pendiente. `startDictation()` cancela la sesión previa (y su parcial)
  pero no toca `pendingAudio`.
- **Guardado y limpieza:** Guardar (texto o texto+imagen) primero
  `cancelDictation()`, crea la Nota y luego `attachAudio(note.id)` (Adjunto `AUDIO`
  best-effort; ante fallo registra `STORAGE` y borra el archivo, siempre limpia
  `pendingAudio`). Si la Nota no se crea se descarta el audio pendiente y, con
  imagen, también el archivo recién guardado. Cancelar el editor corta la sesión,
  borra el pendiente y vuelve a la lista. Un `DisposableEffect(Unit)` a nivel
  `App` (no por destino) cubre la salida de composición; alternar imagen no corta
  el dictado.
- **Editor:** el panel muestra "Dictar" (Idle), barra de progreso
  `elapsed / 30s` + "Escuchando… (Ns / 30s)" + parcial + "Detener" (Listening),
  "Transcribiendo…" deshabilitado (Processing) o mensaje textual + "Reintentar"
  (Error). El texto final se anexa con `LaunchedEffect(dictatedText)` y
  `canSave` exige `dictatedText == null` para no guardar el audio sin su texto.
- **Sin watchdog:** si tras `stop()` nunca llega el final, la UI queda en
  `Processing`; "Cancelar" sigue disponible y limpia. Límite aceptado.
- **Sin deps, esquema ni `:core`:** se reutilizan `VoiceTranscriber`,
  `AttachmentRepository`, `AttachmentKind.AUDIO` y `currentTimeMillis()`; sin
  `.sqm`, sin build files, sin `init.sh` y sin permisos nuevos. iOS congelado sólo
  se verifica que compile.

## Thumbnail Decisions (sketch-thumbnails)

Implementado 2026-10-19. Detalles en `ARCHITECTURE.md` y
`docs/specs/sketch-thumbnails.md`.

- **Store en `:core` por testabilidad.** `StoredImage` y
  `AttachmentFileStore` (`saveImage`/`read`/`delete`) se movieron de
  `:composeApp` a `com.playbook.core.storage` como `interface` + `expect fun
  attachmentFileStore(baseDir: String)`, siguiendo el precedente de
  `DiagnosticStorage`. `read` es nuevo: devuelve los bytes exactos o `null` si
  el archivo no existe/no se puede leer.
- **Actuals por plataforma.** Android/JVM usa `java.io.File` y
  `canonicalFile` para el guard; iOS usa `NSFileManager`/`NSData` y normaliza
  `.`/`..`/separadores antes del chequeo de prefijo (sin APIs de
  canonicalización exóticas). En ambos, `read`/`delete` devuelven `null`/`false`
  si la ruta escapa de `baseDir` (path traversal).
- **Rutas y contrato preservados.** `saveImage` sigue escribiendo
  `images/<uuid>.jpg` y devolviendo la ruta **relativa**; no hay migración ni
  cambio de la tabla `attachment`. `rememberAttachmentFileStore()` en
  `:composeApp` sólo calcula el `baseDir` (`filesDir` Android, `Application
  Support` iOS) y delega en `:core`.
- **Render perezoso y tolerante.** `App` calcula `imagePaths` sólo con Adjuntos
  `IMAGE` (el primero por Nota según `created_at ASC, id ASC`) y un
  `loadThumbnail` que lee + decodifica. `NoteThumbnail` usa
  `produceState` (carga al componerse) y `Dispatchers.Default`; `Image` de
  72 dp con recorte `rounded.md` de 16 dp y `ContentScale.Crop`. El espaciado
  inferior se emite dentro de `NoteThumbnail`, junto con la imagen. Si el archivo
  falta o no decodifica, no hay `Image` ni espaciado (sin placeholder) y la
  tarjeta conserva cuerpo y chips (incluido "Imagen" en el GDD).
- **Cacheo diferido.** Se decodifica a tamaño completo y se escala en pantalla;
  no hay cache en memoria/disco ni redimensionado en esta feature. Límite
  declarado en `docs/risks-and-open-questions.md`.
- **Verificación:** `:core:testDebugUnitTest` 26 suites/77 tests/0 failures
  (incluye `AttachmentFileStoreAndroidTest`), `:core:iosSimulatorArm64Test`
  26/77/0 (incluye `AttachmentFileStoreIosTest`), `:composeApp:assembleDebug`
  OK, `./init.sh` exit 0 y link iOS OK. Controles negativos (a/b/c)
  reproducidos y restaurados. Smoke Android manual no ejecutado (sin
  `adb`/emulador en el entorno).
