# Architecture

Mapa de la base KMP. `local-persistence-sqldelight` agregó la capa de
persistencia local (SQLDelight), `note-model-crud-core` el modelo de dominio
`Note` y su CRUD, `notes-list-ui` la primera pantalla real (lista de notas) con
el repositorio cableado a la app, `create-text-note` el CRUD de notas de texto
desde la UI (crear/editar/borrar + refresco), `ai-client-interface` el puerto
`AiClient` hacia el runtime de IA más un adaptador fake por defecto, y
`image-capture-camera` el primer Adjunto: tabla `attachment` (esquema v2 +
migración), su repositorio y la captura nativa de imagen (cámara/galería) con
almacenamiento de archivos y permisos.

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
  `core/src/commonMain/sqldelight/com/playbook/core/db/{Note,Attachment}.sq`.
- **Esquema v2** (primera migración del repo):
  - v1: tabla `note` (`id`, `owner`, `body`, `track` nullable, `status`,
    `created_at`, `updated_at`) y queries `insertNote`, `selectAllNotes`,
    `selectNoteById`, `updateNote`, `deleteNoteById`.
  - v2 (`image-capture-camera`): tabla `attachment` (`id`, `note_id`, `kind`,
    `mime_type`, `file_path`, `byte_size`, `created_at`) + índice
    `attachment_note_id`. Queries `insertAttachment`,
    `selectAttachmentsByNoteId`, `selectAllAttachments`,
    `deleteAttachmentsByNoteId`.
  - **Migración `1.sqm`** (`CREATE TABLE attachment` + índice): SQLDelight deriva
    `PlaybookDatabase.Schema.version = 2` del archivo de migración; los drivers
    aplican create/migrate al abrir. No se toca `note`, así que los datos v1 se
    conservan intactos (cubierto por `verifyMigrationV1ToV2` en ambos targets y
    verificado en el iOS simulator real).
  - Los bytes **no** se guardan en la base: sólo metadatos + ruta relativa al
    contenedor (ver "Attachments y captura nativa").
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
  `SqlDelightNoteRepository(database, ::randomNoteId, ::currentTimeMillis)` y
  `SqlDelightAttachmentRepository(database, ::randomNoteId, ::currentTimeMillis)`,
  que pasan a `App(noteRepository, attachmentRepository)`.
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
  `delete(id)` corre en una transacción (`transactionWithResult`) que borra
  primero las filas `attachment` de la nota y después la `note` (cascade sin
  huérfanos); la semántica pública no cambia.
- **Adjunto** (`com.playbook.core.model`): `Attachment` (id, noteId, kind,
  mimeType, filePath, byteSize, createdAt) + enum `AttachmentKind` (`IMAGE` →
  `"imagen"`; `AUDIO` llegará con `voice-attachment-storage` sin cambiar el
  esquema) con `code`/`fromCode` fail-fast. `AttachmentMappers.kt` aliasa
  `com.playbook.core.db.Attachment as AttachmentRow`. `AttachmentRepository`
  (`create`/`getByNoteId`/`getAll`/`deleteByNoteId`) +
  `SqlDelightAttachmentRepository(database, idFactory, clock)`. `filePath` es
  relativo al contenedor de la app.
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

## Attachments y captura nativa

- **Contrato común** (`composeApp/src/commonMain/.../ImageCapture.kt`):
  `ImageSource` (`CAMERA`/`GALLERY`), `PickedImage` (bytes + mimeType, en
  memoria), `ImageCaptureError` (`PERMISSION_DENIED`, `CAMERA_UNAVAILABLE`,
  `READ_FAILED`, `UNKNOWN`), `ImagePicker` + `rememberImagePicker(...)`,
  `StoredImage`/`AttachmentFileStore` + `rememberAttachmentFileStore()`, y
  `decodeImageBitmap(bytes)`. Son `expect`; las implementaciones viven en
  `androidMain`/`iosMain`.
- **Regla durable:** los bytes de imagen **nunca** se guardan en la base; se
  escribe el archivo en almacenamiento privado y la fila `attachment` guarda la
  **ruta relativa** (`images/<uuid>.jpg`). En iOS la ruta absoluta del sandbox
  cambia entre instalaciones/updates; por eso es relativa.
- **Android:** cámara con permiso `CAMERA` en runtime + intent
  `ACTION_IMAGE_CAPTURE` (`StartActivityForResult`) sobre un `Uri` de
  `FileProvider` en `cacheDir/images/`. No se usa
  `ActivityResultContracts.TakePicture` porque no agrega los flags de grant de
  URI y la app de cámara falla al escribir el output; el intent se arma con
  `FLAG_GRANT_READ/WRITE_URI_PERMISSION` + `clipData`. Galería con
  `PickVisualMedia` (sin permiso). `AndroidAttachmentFileStore` usa
  `filesDir/images/`; `decodeImageBitmap` usa `BitmapFactory`.
  `AndroidManifest.xml` declara `CAMERA`, `uses-feature camera required=false` y
  el `<provider>` `FileProvider` (`${applicationId}.fileprovider` +
  `res/xml/file_paths.xml`).
- **iOS:** cámara con `AVCaptureDevice` (availability + permission) y
  `UIImagePickerController` presentado desde el controller superior; si el
  simulador no expone cámara → `CAMERA_UNAVAILABLE`. Galería con
  `PHPickerViewController` (sin permiso). La imagen se re-encodea a JPEG
  (`UIImageJPEGRepresentation`, calidad 0.9). `IosAttachmentFileStore` usa
  `Application Support/images/`; `decodeImageBitmap` usa
  `org.jetbrains.skia.Image`. `Info.plist` incluye `NSCameraUsageDescription`.
  Los delegates de UIKit son weak: se retienen en un holder para no liberarlos
  mientras el picker está presentado.
- **Ciclo de vida del archivo:** al guardar se escribe el archivo **antes** de
  crear la nota y el adjunto; si algo falla después, se borra el archivo
  recién guardado (sin huérfanos). Al borrar la nota, las filas se borran en
  cascade dentro del repositorio y los archivos con el `AttachmentFileStore`
  desde la UI.

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

## UI

- **`commonMain`** (`com.playbook.app`): `App(noteRepository: NoteRepository,
  attachmentRepository: AttachmentRepository)` es la raíz de la UI y dueña del
  estado. Hoistea `notes` y `attachments` (`mutableStateOf`), `destination`
  (`NotesDestination`: `List`/`Create`/`Edit(noteId)`/`CreateWithImage(image)`) y
  `refreshKey`. La lectura ocurre en un `LaunchedEffect(refreshKey)`, **nunca** en
  el cuerpo de composición; tras cada mutación se incrementa `refreshKey`
  (`reload()`), lo que dispara la relectura y refleja los cambios sin reiniciar
  la app. Sin DI, sin `ViewModel`/`Lifecycle`, sin coroutines fuera de Compose y
  sin Flow ni librería de navegación. Obtiene el `ImagePicker` y el
  `AttachmentFileStore` por `remember`; la acción de imagen abre un `AlertDialog`
  "Agregar imagen" (Cámara/Galería/Cancelar), `onResult` navega a
  `CreateWithImage` y `onError` muestra un `AlertDialog` accionable.
- **Owner del MVP:** `LOCAL_OWNER_ID = "local"` (`private const val` en `App.kt`);
  la UI lo provee al construir el `NoteDraft` de creación. `:core` no cambia.
- **`NotesListScreen`**: `Scaffold` con una fila de `ExtendedFloatingActionButton`
  ("Nueva nota" + "Foto"); estado vacío ("Todavía no hay notas") con CTA "Crear
  primera nota" y "Agregar foto"; y estado con notas (título "Notas" + `LazyColumn`
  de `Card`s clickeables que llaman `onEdit(note.id)`). Cada tarjeta muestra
  `body.ifBlank { "Imagen adjunta" }` (máx. 2 líneas, ellipsis), un chip textual
  "Imagen" si la nota tiene adjuntos (`attachmentCounts`), y chips de `track.code`
  (o "Sin track") y `status.code`. Estados y adjuntos se comunican con texto, no
  sólo color (`DESIGN.md`).
- **`NoteEditorScreen`**: editor de texto para crear (`isEditing = false`) y editar
  (`isEditing = true`). `OutlinedTextField` multilínea para `body`; selector de
  `track` ("Sin track" + `Track.entries`) que comunica la selección con marca
  textual "✓", borde y color; `Guardar` deshabilitado si `body.isBlank()` **y** no
  hay `initialImage` (una nota sólo-imagen es válida); `Cancelar`; y en edición
  `Borrar` con `AlertDialog` de confirmación. Cuando llega `initialImage` muestra
  la previsualización decodificada (`decodeImageBitmap`), "Quitar imagen" y el
  placeholder de fallo si no decodifica. La pantalla mantiene el borrador local y
  emite callbacks; la persistencia y el refresco los
  maneja `App`.
- **Flujo de datos:** crear → `create(NoteDraft(LOCAL_OWNER_ID, body.trim(),
  track))` (status default `CAPTURED`); **crear con imagen** →
  `fileStore.saveImage(bytes, mime)` → `create(NoteDraft(...))` →
  `attachmentRepository.create(note.id, IMAGE, mime, relativePath, byteSize)` →
  `reload()`; si algo falla después de guardar el archivo, se borra el archivo
  (sin huérfanos). Editar → `update(note.copy(body = body.trim(), track = ...))`
  (re-sella `updatedAt`, preserva `owner`/`createdAt`/`status`); borrar → se leen
  los adjuntos de la nota, `delete(id)` (cascade de filas) y se borran los
  archivos con el `AttachmentFileStore`; siempre `reload()` y vuelta a la lista.
  El `status` no se edita desde la UI. Si el `id` en `Edit(noteId)` ya no existe
  en `notes`, se vuelve a la lista.
- **Deuda resuelta:** `notes-list-ui` leía la lista en el hilo de composición y no
  refrescaba tras mutaciones; `create-text-note` lo resolvió con `LaunchedEffect` +
  `refreshKey`. `image-capture-camera` sólo adjunta imagen al **crear**; no hay
  edición de adjuntos existentes, tags, IA ni GDD en esta superficie.

## Runtime surfaces

- **Android:** `MainActivity` (`com.playbook.app`) monta
  `App(noteRepository, attachmentRepository)`, aplica el esquema local v2 con
  `createDatabase(AndroidDatabaseDriverFactory(...))` y construye
  `SqlDelightNoteRepository` + `SqlDelightAttachmentRepository`.
- **iOS:** `MainViewController()` (Kotlin, `composeApp/src/iosMain`) crea la base
  local, construye los dos repositorios y envuelve
  `App(noteRepository, attachmentRepository)`; lo llama `ComposeView` (SwiftUI) en
  `iosApp`.

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

- Adjuntos de audio (`voice-attachment-storage`), OCR del boceto
  (`image-ocr-optional`), edición de adjuntos de una nota existente y render de la
  imagen en la lista/detalle (hoy sólo chip "Imagen" + previsualización al crear).
- Etiquetas, enlaces y embeddings (features posteriores). El runtime de IA
  concreto (`ai-runtime-decision`) y el wiring de `AiClient` a su primer
  consumidor (`embeddings-generation`) también quedan pendientes.
- Agrupado por `track` / vista GDD (`gdd-view`) y detalle de nota de sólo lectura.
- Manejo del back físico Android/iOS en el editor (hoy sólo "Cancelar").
- Formalización de `DESIGN.md` (sigue `provisional`; sin entrega de UI/UX).
