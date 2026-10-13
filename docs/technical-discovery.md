# Technical Discovery

## Product Surface
App móvil para **Android + iOS en paralelo**. UI en Compose Multiplatform y
lógica de dominio en un core Kotlin Multiplatform (KMP).

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
  `RagQueryService` (`rag-query`, ver "RAG Decisions").
- **Integraciones nativas:** speech-to-text (voz) y cámara (foto de bocetos).

## Data and Storage
- **Local-first**, sin backend en el MVP.
- SQLDelight en ambas plataformas como única fuente de verdad local.
- Vectores de embeddings como BLOB en SQLDelight; similitud coseno en memoria
  (suficiente para una biblioteca personal).
- `Owner` presente en el modelo aunque el MVP sea single-user.

## Integrations
- **Speech-to-text nativo:** Android `SpeechRecognizer` / iOS `Speech`.
- **Cámara / galería:** captura de bocetos; el OCR (si existe) es texto derivado.
  **OCR descartado del MVP** (`image-ocr-optional`): ver "OCR Decision
  (image-ocr-optional)" y `docs/adr/0002-ocr-decision.md`.
- **Proveedor de IA:** embeddings + LLM detrás de `AiClient` — puerto
  implementado y runtime decidido (híbrido, cloud-first); `FakeAiClient` es el
  adaptador inyectado hoy y el adaptador real es follow-up del ADR 0001.

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
- Prueba manual end-to-end en Android y iOS.

## Observability
- Mínimo: logging local de fallos de indexado e integraciones nativas.
- Sin telemetría remota en el MVP.

## Constraints
- **API keys y costos:** a resolver si se elige cloud; nunca commitear keys.
- **Offline:** la captura siempre debe funcionar; el indexado tolera fallos y se
  reintenta.
- **Costo de KMP × 2 plataformas:** Android + iOS duplica el trabajo nativo
  (permisos, STT, cámara) y requiere entorno macOS/Xcode.

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
  `AskUiState` (`Idle`/`Asking`/`Answered`/`NoIndexedNotes`/`Error`). La pantalla
  `AskNotesScreen` muestra sólo el **texto** de la respuesta (las citas son
  `rag-answer-citations`) y comunica los estados con texto.
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
  `attachments`. `VoiceCaptureScreen` agrega una línea informativa
  ("Se guarda también el audio original como Adjunto.").
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
  o API key) no se puede firmar/exportar/subir. La feature queda `blocked` (no
  `passing`) hasta ejecutar Scenarios 3–6 en un entorno con cuenta.

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

