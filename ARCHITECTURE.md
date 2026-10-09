# Architecture

Mapa de la base KMP. `local-persistence-sqldelight` agregó la capa de
persistencia local (SQLDelight), `note-model-crud-core` el modelo de dominio
`Note` y su CRUD, `notes-list-ui` la primera pantalla real (lista de notas) con
el repositorio cableado a la app, `create-text-note` el CRUD de notas de texto
desde la UI (crear/editar/borrar + refresco), `ai-client-interface` el puerto
`AiClient` hacia el runtime de IA más un adaptador fake por defecto, y
`voice-capture-stt` la captura por voz nativa (puerto `VoiceTranscriber` +
adaptadores Android/iOS) que crea una Nota con el texto transcripto.

## Modules

- **`:core`** — KMP library compartida (sin UI). Targets: `androidTarget`,
  `iosX64`, `iosArm64`, `iosSimulatorArm64`. Namespace Android
  `com.playbook.core`. Incluye la capa de persistencia local (ver abajo).
- **`:composeApp`** — UI en Compose Multiplatform + app Android + framework iOS.
  Es el único módulo que produce artefactos de aplicación: APK Android y
  `ComposeApp.framework` (static).
- **`iosApp/`** — host Xcode (SwiftUI) que embebe `ComposeApp.framework` y
  arranca la UI compartida vía `MainViewController()`.

## Dependency direction

```
iosApp (Swift) ──▶ ComposeApp.framework (${composeApp iosMain})
composeApp (Android app / commonMain) ──▶ core
```

- `core` no depende de `composeApp` ni de ninguna UI.
- `composeApp` contiene la UI y consume el core; nunca al revés.
- Regla durable: la lógica de dominio vive en `:core`; la UI y el wiring de
  plataforma viven en `:composeApp`.

## Persistence

- **SQLDelight 2.1.0** en `:core`; base `PlaybookDatabase` (paquete
  `com.playbook.core.db`) generada desde
  `core/src/commonMain/sqldelight/com/playbook/core/db/Note.sq`.
- Esquema v1: tabla `note` (`id`, `owner`, `body`, `track` nullable, `status`,
  `created_at`, `updated_at`) y queries `insertNote`, `selectAllNotes`,
  `selectNoteById`, `updateNote`, `deleteNoteById`. Sigue en v1 (sin `.sqm`):
  `note-model-crud-core` sólo agregó queries, no cambió la tabla. Las tablas
  vecinas (etiquetas, adjuntos, enlaces, embeddings) llegan en features
  posteriores.
- **Drivers:** `DatabaseDriverFactory` (commonMain) + `AndroidDatabaseDriverFactory`
  (`AndroidSqliteDriver`) e `IosDatabaseDriverFactory` (`NativeSqliteDriver`).
- **Entry point:** `createDatabase(factory)` en `:core` construye la base y fuerza
  la apertura de la conexión con `executeQuery("PRAGMA user_version", ...)` para
  aplicar el esquema al arrancar. Esto es necesario en iOS: `NativeSqliteDriver`
  abre la conexión de forma perezosa, por lo que sólo instanciar el driver no crea
  el archivo ni el esquema. El `PRAGMA` retorna filas: debe ir con `executeQuery`,
  no con `execute` (que sólo admite statements sin resultado).
- **Wiring:** `MainActivity` (Android) y `MainViewController` (iOS, en
  `:composeApp`) llaman a `createDatabase(...)` al arrancar y construyen
  `SqlDelightNoteRepository(database, ::randomNoteId, ::currentTimeMillis)`, que
  pasan a `App(noteRepository)`.
- **iOS host:** la app Xcode enlaza `libsqlite3` (`OTHER_LDFLAGS = -lsqlite3`)
  porque el driver nativo referencia los símbolos de SQLite del sistema.

## Domain

- **Modelo** (`com.playbook.core.model`): `Note` (id, owner, body, `track`
  nullable, `status` no nulo, `createdAt`, `updatedAt`), `NoteDraft` (entrada de
  `create`, `status` default `CAPTURED`) y los enums `Track`
  (`MECHANICS`/`CHARACTERS`/`STORY`) y `NoteStatus`
  (`CAPTURED`/`PENDING`/`INDEXED`/`FAILED`) con `code`/`fromCode`. Los
  identificadores Kotlin están en inglés; el `code` persistido usa el valor de
  dominio canónico en español (`"mecánicas"`, `"personajes"`, `"historia"`,
  `"capturada"`, `"pendiente"`, `"indexada"`, `"fallida"`).
- `Track.fromCode(null | desconocido)` → `null` (edge case "Nota sin `track`").
  `NoteStatus.fromCode(desconocido)` → `IllegalArgumentException` fail-fast: la
  columna es `NOT NULL` y sólo la escribe este código.
- **Repositorio** (`com.playbook.core.repository`): interfaz `NoteRepository`
  (`create`/`getAll`/`getById`/`update`/`delete`) e implementación
  `SqlDelightNoteRepository(database, idFactory, clock)`. `update` re-sella
  `updated_at` con `clock()` (ignora el del argumento) y preserva
  `owner`/`created_at`; `update`/`delete` devuelven `rowsAffected > 0`.
- **Mapper** (`NoteMappers.kt`): importa `com.playbook.core.db.Note as NoteRow`
  para evitar la colisión con el modelo de dominio.
- **IDs y tiempo** (`com.playbook.core.platform`): `expect`/`actual`
  `randomNoteId()`/`currentTimeMillis()` (Android `UUID`/`System`; iOS
  `NSUUID`/`NSDate`). En tests se inyectan fakes deterministas; no se usa
  `expect`/`actual` en source sets de test.
- **Orden de lectura:** `selectAllNotes` ordena por `updated_at DESC, id DESC`
  (cambio de query, no de esquema: sigue v1, sin `.sqm`). El `id` desempata de
  forma estable los `updated_at` iguales; `getAll()` es determinista y está
  cubierto por `verifyNoteCrud`.
- El repositorio se instancia en los entry points de plataforma y se pasa a la
  UI; no hay DI.

## AI

- **Puerto** (`com.playbook.core.ai`): `AiClient` con
  `suspend fun embed(texts: List<String>): List<Embedding>` y
  `suspend fun generate(request: GenerateRequest): String`. Tipos: `Embedding`
  (`values: List<Float>`), `GenerateRequest` (`prompt`, `context` default
  `emptyList()`) y `AiClientException` para fallos del runtime (red, cuota,
  modelo). `suspend` porque las llamadas de IA son asíncronas y cancelables.
- **Dirección de dependencia (regla durable):** la lógica de dominio consume
  **sólo el puerto** `AiClient`; los adaptadores concretos se inyectan desde
  afuera. `:core` expone el puerto y un adaptador fake por defecto; los
  adaptadores reales (cloud/on-device) vivirán fuera de `:core` y se inyectarán
  sin tocar la lógica de dominio. Elegir el runtime es `ai-runtime-decision`.
- **Fake por defecto** (`commonMain`): `FakeAiClient(dimension = 8)` es
  determinista y sin red. Los vectores derivan de `String.hashCode()` (estable
  entre plataformas): mismo texto → mismo vector; textos distintos → vectores
  distintos salvo colisión de hash. `embed(emptyList()) == emptyList()`,
  `require(dimension > 0)` y `generate` devuelve una respuesta no vacía. Al
  vivir en `commonMain` (no en test) es inyectable desde el composition root.
- **Sin wiring todavía:** no hay consumidor de IA (`embeddings-generation` será
  el primero), por lo que `AiClient` **no** se cablea a `App` ni a los entry
  points; sería una dependencia muerta. La inyección se materializa en los tests
  del contrato (dos implementaciones por el mismo puerto).
- **Dependencias:** `kotlinx-coroutines-core` (commonMain) por el contrato
  `suspend`; `kotlinx-coroutines-test` (commonTest) para `runTest`. Versión
  **1.10.1**, compatible con Kotlin 2.1.21.

## Voice capture (STT)

- **Puerto** (`com.playbook.app.voice`, en `:composeApp/commonMain`):
  `VoiceTranscriber` con `start(onPartialResult, onFinalResult, onError)`,
  `stop()` y `cancel()`, más `VoiceCaptureError`
  (`PERMISSION_DENIED`/`UNAVAILABLE`/`NO_MATCH`/`NETWORK`/`BUSY`/`UNKNOWN`). Vive
  en `:composeApp` (no en `:core`): es una integración nativa que sólo consume la
  UI y necesita contexto de plataforma; `:core` no cambia.
- **Adaptadores por plataforma** (mismo patrón que `DatabaseDriverFactory`, **sin**
  `expect`/`actual`):
  - `AndroidVoiceTranscriber(ComponentActivity)` (`androidMain`): `SpeechRecognizer`
    + `RecognizerIntent` con resultados parciales; pide `RECORD_AUDIO` con
    `registerForActivityResult(RequestPermission())` en el constructor (debe
    construirse en `onCreate`). Mapea `onResults`/`onError` del
    `RecognitionListener` a los callbacks del puerto y recrea el recognizer por
    sesión.
  - `IosVoiceTranscriber()` (`iosMain`): `SFSpeechRecognizer` +
    `SFSpeechAudioBufferRecognitionRequest` + `AVAudioEngine`; pide
    `requestAuthorization` y `requestRecordPermission` y, como los callbacks de
    `Speech`/AVFoundation llegan en colas secundarias, los despacha al main queue
    (`dispatch_async(dispatch_get_main_queue())`). `cancel()` cancela la tarea,
    quita el tap, detiene el engine y desactiva la `AVAudioSession`.
- **Wiring sin DI:** `MainActivity` construye `AndroidVoiceTranscriber(this)` y
  `MainViewController` construye `IosVoiceTranscriber()`; ambos se pasan a
  `App(noteRepository, voiceTranscriber)`.
- **Permisos:** `AndroidManifest.xml` declara `android.permission.RECORD_AUDIO`;
  `iosApp/iosApp/Info.plist` declara `NSMicrophoneUsageDescription` y
  `NSSpeechRecognitionUsageDescription` (sin la segunda, iOS termina el proceso al
  primer uso).
- **Resultado:** el texto final no vacío se persiste como `body` de una Nota con
  `track = null`/`status = CAPTURED` por el mismo `noteRepository.create(...)` del
  texto; el audio original **no** se guarda (lo hará `voice-attachment-storage`).
- **Dependencias:** ninguna nueva (`SpeechRecognizer`/`RecognizerIntent` son
  framework Android; `activity-compose` ya estaba; `Speech`/`AVFAudio`/`dispatch`
  vienen de Kotlin/Native). No se agregan coroutines ni Flow.

## UI

- **`commonMain`** (`com.playbook.app`): `App(noteRepository: NoteRepository,
  voiceTranscriber: VoiceTranscriber)` es la raíz de la UI y dueña del estado.
  Hoistea `notes` (`mutableStateOf`), `destination` (`NotesDestination`:
  `List`/`Create`/`Edit(noteId)`/`VoiceCapture`), `refreshKey` y `voiceState`
  (`VoiceUiState`: `Idle`/`Listening(partial)`/`Processing`/`Error`).
  La lectura ocurre en un `LaunchedEffect(refreshKey)`, **nunca** en el cuerpo de
  composición; tras cada mutación se incrementa `refreshKey` (`reload()`), lo que
  dispara la relectura y refleja los cambios sin reiniciar la app. Sin DI, sin
  `ViewModel`/`Lifecycle`, sin coroutines fuera de Compose y sin Flow ni librería
  de navegación.
- **Owner del MVP:** `LOCAL_OWNER_ID = "local"` (`private const val` en `App.kt`);
  la UI lo provee al construir el `NoteDraft` de creación. `:core` no cambia.
- **`NotesListScreen`**: `Scaffold` con `ExtendedFloatingActionButton` "Nueva
  nota"; acción secundaria `FilledTonalButton` "Dictar nota" (`onDictate`) visible
  en el estado vacío y con notas; estado vacío ("Todavía no hay notas") con CTA
  "Crear primera nota"; y estado con notas (título "Notas" + `LazyColumn` de
  `Card`s clickeables que llaman `onEdit(note.id)`). Cada tarjeta muestra `body`
  (máx. 2 líneas, ellipsis) y una fila de chips de texto con `track.code` (o "Sin
  track" si es `null`) y `status.code`. El `track`/`status` se comunican con texto,
  no sólo color (`DESIGN.md`).
- **`NoteEditorScreen`**: editor de texto para crear (`isEditing = false`)
  y editar (`isEditing = true`). `OutlinedTextField` multilínea para `body`;
  selector de `track` ("Sin track" + `Track.entries`) que comunica la selección
  con marca textual "✓", borde y color; `Guardar` deshabilitado si `body.isBlank()`;
  `Cancelar`; y en edición `Borrar` con `AlertDialog` de confirmación. La pantalla
  mantiene el borrador local y emite callbacks; la persistencia y el refresco los
  maneja `App`.
- **`VoiceCaptureScreen`** (nuevo): pantalla "tonta" de dictado. Recibe
  `VoiceUiState` (`Idle`/`Listening(partial)`/`Processing`/`Error`) y emite
  `onStart`/`onStop`/`onCancel`. La acción primaria es "Grabar" (Idle),
  "Detener" (Listening) o "Reintentar" (Error); "Cancelar" vuelve a la lista.
  Los estados y los errores se comunican con texto (nunca sólo color). No mantiene
  el `VoiceTranscriber`.
- **Orquestación de voz (`App`):** la sesión se inicia sólo al tocar "Grabar"
  (para que el prompt de permiso sea explícito); `onPartialResult` actualiza el
  parcial; `onError` pasa a `Error`; `onFinalResult` no vacío crea la Nota
  (`NoteDraft(LOCAL_OWNER_ID, text.trim(), track = null)`) y vuelve a la lista con
  `reload()`; un final vacío se trata como `NO_MATCH`. Al salir del destino un
  `DisposableEffect` llama `voiceTranscriber.cancel()` (libera el micrófono).
- **Flujo de datos:** crear → `create(NoteDraft(LOCAL_OWNER_ID, body.trim(),
  track))` (status default `CAPTURED`); editar → `update(note.copy(body =
  body.trim(), track = ...))` (re-sella `updatedAt`, preserva
  `owner`/`createdAt`/`status`); borrar → `delete(id)`; siempre `reload()` y
  vuelta a la lista. El `status` no se edita desde la UI. Si el `id` en
  `Edit(noteId)` ya no existe en `notes`, se vuelve a la lista.
- **Deuda resuelta:** `notes-list-ui` leía la lista en el hilo de composición y no
  refrescaba tras mutaciones; `create-text-note` lo resolvió con `LaunchedEffect` +
  `refreshKey`. No hay crear/editar/borrar de tags, adjuntos, IA ni GDD en esta
  superficie.

## Runtime surfaces

- **Android:** `MainActivity` (`com.playbook.app`) monta `App(noteRepository,
  AndroidVoiceTranscriber(this))`, aplica el esquema local con
  `createDatabase(AndroidDatabaseDriverFactory(...))` y construye el
  `SqlDelightNoteRepository`.
- **iOS:** `MainViewController()` (Kotlin, `composeApp/src/iosMain`) crea la base
  local, construye el repositorio y `IosVoiceTranscriber()`, y envuelve
  `App(repository, transcriber)`; lo llama `ComposeView` (SwiftUI) en `iosApp`.

## Identifiers

- Kotlin base package: `com.playbook`
- Android `namespace` / `applicationId`: `com.playbook.app`
- iOS bundle id: `com.playbook.app`
- iOS framework `baseName`: `ComposeApp`
- Xcode scheme/target: `iosApp`; app display name `Playbook`

## Build and versions

- Gradle wrapper **8.14.5**, Kotlin **2.1.21**, Compose Multiplatform **1.8.2**,
  AGP **8.10.1**, SQLDelight **2.1.0**.
- compileSdk **36**, minSdk **24**, targetSdk **36**, JVM target **11**, JDK **21**.
- Versiones centralizadas en `gradle/libs.versions.toml`.
- Android SDK en `local.properties` (`sdk.dir=...`), no versionado.

## Verification

Gate no bloqueante: `./init.sh` (Android build + tests del core en Android/JVM
+ link del framework iOS + tests del core en iOS; no levanta dev servers).
Comandos manuales equivalentes:

- `./gradlew :composeApp:assembleDebug`
- `./gradlew :core:testDebugUnitTest` (o `:core:allTests`)
- `./gradlew :core:iosSimulatorArm64Test`
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`
- iOS app: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator build`

## Deferred

- Etiquetas, adjuntos, enlaces y embeddings (features posteriores). El runtime
  de IA concreto (`ai-runtime-decision`) y el wiring de `AiClient` a su primer
  consumidor (`embeddings-generation`) también quedan pendientes.
- Guardar el audio original del dictado como `Adjunto`
  (`voice-attachment-storage`): `voice-capture-stt` sólo persiste la transcripción.
- Agrupado por `track` / vista GDD (`gdd-view`) y detalle de nota de sólo lectura.
- Manejo del back físico Android/iOS en el editor y en la captura de voz (hoy sólo
  "Cancelar").
- Formalización de `DESIGN.md` (sigue `provisional`; sin entrega de UI/UX).
