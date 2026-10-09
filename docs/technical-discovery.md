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
- **IA:** interfaz `AiClient` como abstracción (puerto ya implementado en
  `:core`; ver "AI Decisions"). Runtime **decidido** en
  `ai-runtime-decision`: estrategia **híbrida por fases, cloud-first** detrás del
  puerto (ver "AI Decisions (ai-runtime-decision)" y `docs/adr/0001-ai-runtime-decision.md`).
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
- **Proveedor de IA:** embeddings + LLM detrás de `AiClient` — puerto ya
  implementado; runtime pendiente de elección (ver riesgos).

## Authentication and Authorization
- Ninguna en el MVP (single-user local-first).
- Se reserva la noción de `Owner` para multi-usuario futuro.

## Deployment and Operations
- Distribución en Android (Play / internal testing) e iOS (TestFlight) para
  ejercitar "lanzar a producción".
- Sin operación de servidores en el MVP.

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
