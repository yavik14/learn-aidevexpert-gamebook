# Architecture

Mapa de la base KMP. `local-persistence-sqldelight` agregó la capa de
persistencia local (SQLDelight), `note-model-crud-core` el modelo de dominio
`Note` y su CRUD, `notes-list-ui` la primera pantalla real (lista de notas) con
el repositorio cableado a la app, `create-text-note` el CRUD de notas de texto
desde la UI (crear/editar/borrar + refresco), `ai-client-interface` el puerto
`AiClient` hacia el runtime de IA más un adaptador fake por defecto,
`ai-runtime-decision` la estrategia de runtime decidida (híbrida por fases,
cloud-first) junto con su spike y ADR, `android-release-pipeline` la configuración
de release/signing de Android (keystore externo) y el runbook de publicación,
`embeddings-generation` la tabla `embedding` (esquema v2 con `1.sqm`),
`EmbeddingRepository` y `NoteIndexingService` como primer consumidor real de
`AiClient`, `voice-capture-stt` la captura por voz nativa (puerto
`VoiceTranscriber` + adaptadores Android/iOS), `note-category-and-tags` las
Etiquetas libres (esquema v3 con `note_tag` y la migración `2.sqm`),
`image-capture-camera` el primer Adjunto: tabla `attachment` (esquema v4 con
`3.sqm`), su repositorio y la captura nativa de imagen (cámara/galería),
`image-ocr-optional` la decisión de viabilidad del OCR del boceto (descartado del
MVP; ver `docs/adr/0002-ocr-decision.md`), `ios-release-pipeline` el pipeline de
release de iOS (archive/export/upload a TestFlight) con firma, versionado y reglas
de secretos, y `gdd-view` la primera vista de GDD (proyección pura de las Notas
por `track` + pantalla navegable).
`ai-enrichment-classify-tags-type` agrega el orquestador de indexado asíncrono
(`NoteIndexingOrchestrator`) y las transiciones de `NoteStatus` derivadas del
resultado del embedding, sin cambio de esquema (sigue v4).

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
  `com.playbook.core.db`) generada desde los `.sq` de
  `core/src/commonMain/sqldelight/com/playbook/core/db/`.
- **Esquema v4:** tabla `note` (`id`, `owner`, `body`, `track` nullable, `status`,
  `created_at`, `updated_at`; queries `insertNote`, `selectAllNotes`,
  `selectNoteById`, `updateNote`, `updateNoteStatus`, `deleteNoteById`), tabla
  `embedding` (`note_id`
  PK y FK → `note(id)` `ON DELETE CASCADE`, `dimension`, `vector` BLOB,
  `indexed_at`), tabla `note_tag` (`note_id`, `label`, PK `(note_id, label)`; N—N
  sin FK, limpieza explícita en el repositorio) y tabla `attachment` (`id`,
  `note_id`, `kind`, `mime_type`, `file_path`, `byte_size`, `created_at` + índice
  `attachment_note_id`). La tabla `note` no cambió en estas features; los bytes de
  la imagen **no** se guardan en la base (sólo metadatos + ruta relativa).
- **Migraciones (v1 → v2 → v3 → v4):** `embeddings-generation` introdujo `1.sqm`
  (v1 → v2, `embedding`), `note-category-and-tags` `2.sqm` (v2 → v3, `note_tag`) e
  `image-capture-camera` `3.sqm` (v3 → v4, `attachment`). Los `.sq` describen el
  esquema más reciente y los `.sqm` hacen upgrade; los drivers
  (`AndroidSqliteDriver`/`NativeSqliteDriver` con `PlaybookDatabase.Schema`)
  detectan `user_version` y ejecutan `Schema.migrate`.
  `PlaybookDatabase.Schema.version = 4`. `INSERT OR REPLACE` en lugar de
  `ON CONFLICT DO UPDATE` porque el UPSERT de SQLite exige 3.24 (Android 11+) y
  `minSdk = 24`. Las tablas vecinas restantes (enlaces) llegan en features
  posteriores.
- **Migraciones sin binario:** el repo no usa `schemaOutputDirectory` ni
  `verifySqlDelightMigration`; la equivalencia entre los `.sqm` y los `CREATE TABLE`
  de los `.sq` se cubre con tests portables (`DatabaseMigrationCheck`,
  `NoteTagCheck.verifyNoteTagMigration` y `verifyMigrationV1ToV4`) que corren en
  Android/JVM e iOS.
- **Drivers:** `DatabaseDriverFactory` (commonMain) + `AndroidDatabaseDriverFactory`
  (`AndroidSqliteDriver`) e `IosDatabaseDriverFactory` (`NativeSqliteDriver`).
- **Entry point:** `createDatabase(factory)` en `:core` construye la base y fuerza
  la apertura de la conexión con `executeQuery("PRAGMA user_version", ...)` para
  aplicar el esquema al arrancar. Esto es necesario en iOS: `NativeSqliteDriver`
  abre la conexión de forma perezosa, por lo que sólo instanciar el driver no crea
  el archivo ni el esquema. El `PRAGMA` retorna filas: debe ir con `executeQuery`,
  no con `execute` (que sólo admite statements sin resultado). Después ejecuta
  `PRAGMA foreign_keys = ON` (sin filas → `execute`): SQLite trae las foreign keys
  **OFF** por defecto y el `ON DELETE CASCADE` de `embedding` no aplicaría sin
  esto. Es por conexión e idempotente.
- **Wiring:** `MainActivity` (Android) y `MainViewController` (iOS, en
  `:composeApp`) llaman a `createDatabase(...)` al arrancar y construyen
  `SqlDelightNoteRepository(database, ::randomNoteId, ::currentTimeMillis)` y
  `SqlDelightAttachmentRepository(database, ::randomNoteId, ::currentTimeMillis)`,
  que pasan a `App(noteRepository, attachmentRepository)`.
- **iOS host:** la app Xcode enlaza `libsqlite3` (`OTHER_LDFLAGS = -lsqlite3`)
  porque el driver nativo referencia los símbolos de SQLite del sistema.

## Domain

- **Modelo** (`com.playbook.core.model`): `Note` (id, owner, body, `track`
  nullable, `status` no nulo, `createdAt`, `updatedAt`, `tags`), `NoteDraft`
  (entrada de `create`, `status` default `CAPTURED`, `tags` default vacío) y los
  enums `Track` (`MECHANICS`/`CHARACTERS`/`STORY`) y `NoteStatus`
  (`CAPTURED`/`PENDING`/`INDEXED`/`FAILED`) con `code`/`fromCode`. Los
  identificadores Kotlin están en inglés; el `code` persistido usa el valor de
  dominio canónico en español (`"mecánicas"`, `"personajes"`, `"historia"`,
  `"capturada"`, `"pendiente"`, `"indexada"`, `"fallida"`).
- **Etiquetas** (`Tags.kt`): `normalizeTags(raw: List<String>)` aplica `trim`,
  descarta vacías, deduplica sin distinguir mayúsculas/minúsculas conservando la
  **primera** grafía y ordena case-insensitive. `tags` se agrega al final con
  `emptyList()` por defecto (no rompe el código/tests previos). `Track` (fijo y
  único) y `Etiqueta` (libre y múltiple) **no** son sinónimos (`CONTEXT.md`).
- `Track.fromCode(null | desconocido)` → `null` (edge case "Nota sin `track`").
  `NoteStatus.fromCode(desconocido)` → `IllegalArgumentException` fail-fast: la
  columna es `NOT NULL` y sólo la escribe este código.
- **Repositorio** (`com.playbook.core.repository`): interfaz `NoteRepository`
  (`create`/`getAll`/`getById`/`update`/`updateStatus`/`delete`) e implementación
  `SqlDelightNoteRepository(database, idFactory, clock)`. `update` re-sella
  `updated_at` con `clock()` (ignora el del argumento) y preserva
  `owner`/`created_at`; `update`/`delete` devuelven `rowsAffected > 0`.
  - `updateStatus(id, status)` es la transición **aislada** de estado que usa el
    pipeline de indexado: escribe sólo `status` y **no** re-sella `updated_at`
    (efecto de sistema, no edición del usuario; así una indexación en segundo
    plano no reordena la lista `updated_at DESC`), ni toca
    `body`/`track`/`tags`/`owner`/`created_at`. Devuelve `rowsAffected > 0`.
  - Etiquetas: `create` normaliza y persiste `insertNote` + `insertNoteTag` en
    una transacción; `getById` lee `selectTagsByNoteId`; `getAll` usa
    `selectAllNoteTags` agrupado en memoria (evita N+1); `update` reemplaza el
    conjunto completo (delete + insert) en la misma transacción que la nota y
    sólo si la fila existía; `delete` limpia `note_tag` y las filas `attachment`
    de la nota en la misma transacción (`transactionWithResult`), sin huérfanos.
- **Adjunto** (`com.playbook.core.model`): `Attachment` (id, noteId, kind,
  mimeType, filePath, byteSize, createdAt) + enum `AttachmentKind` (`IMAGE` →
  `"imagen"`; `AUDIO` llegará con `voice-attachment-storage` sin cambiar el
  esquema) con `code`/`fromCode` fail-fast. `AttachmentMappers.kt` aliasa
  `com.playbook.core.db.Attachment as AttachmentRow`. `AttachmentRepository`
  (`create`/`getByNoteId`/`getAll`/`deleteByNoteId`) +
  `SqlDelightAttachmentRepository(database, idFactory, clock)`. `filePath` es
  relativo al contenedor de la app.
- **Mapper** (`NoteMappers.kt`): importa `com.playbook.core.db.Note as NoteRow`
  para evitar la colisión con el modelo de dominio;
  `NoteRow.toDomain(tags = emptyList())` recibe las etiquetas ya resueltas.
- **Embedding** (`com.playbook.core.model`): `NoteEmbedding(noteId, values,
  indexedAt)` es la entidad persistida (relación 1:0..1 con `Note`).
  `NoteEmbeddingMappers.kt` importa `com.playbook.core.db.Embedding as
  EmbeddingRow`, decodifica el BLOB y hace `check` de que `dimension ==
  values.size` (fail-fast ante corrupción).
- **Codec BLOB** (`com.playbook.core.repository.EmbeddingBlobCodec`): `internal
  object` que serializa `List<Float>` a `ByteArray` con **big-endian explícito**
  (`Float.toBits()`/`fromBits()`, 4 bytes por valor, puro Kotlin common y estable
  entre plataformas). `decode` exige longitud múltiplo de 4.
- **Repositorio de embeddings** (`com.playbook.core.repository`): interfaz
  `EmbeddingRepository` (`upsert`/`getByNoteId`/`getAll`/`deleteByNoteId`) e
  implementación `SqlDelightEmbeddingRepository(database, clock)`. `upsert`
  `require(values.isNotEmpty())`, sella `indexedAt` con `clock()` y hace
  `INSERT OR REPLACE`; `deleteByNoteId` devuelve `rowsAffected > 0`.
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
  sin tocar la lógica de dominio.
- **Runtime decidido (`ai-runtime-decision`, 2026-10-09):** estrategia
  **híbrida por fases, cloud-first**, detrás del puerto `AiClient` sin cambiarlo
  (ver `docs/adr/0001-ai-runtime-decision.md`). Orden: (1) primer adaptador real
  en la **nube** (`embeddings-generation` para `embed`; `rag-query` para
  `generate`) con indexado asíncrono tolerante a fallos
  (`capturada → pendiente → indexada/fallida`, `offline-pending-retry`); (2)
  adaptador **on-device** (empezando por embeddings) inyectado por el mismo
  puerto cuando se elija el runtime nativo; (3) `FakeAiClient` como default
  offline/determinista. `:core` no gana dependencias de IA: los adaptadores
  reales se inyectan desde afuera.
- **Política de secrets (durable):** ninguna API key/token/secret se versiona ni
  se commitea; el adaptador cloud lee su credencial de configuración fuera del
  repo (p. ej. `local.properties` ignorado o variable de entorno de build). El
  gate (`init.sh`) no hace red.
- **Evidencia del spike:** `docs/spikes/ai-runtime-spike.md` (test-only, sin red)
  verifica contrato sobre dos arquetipos, offline, mapeo de fallo a
  `AiClientException` y sustituibilidad, con latencia local informativa del fake
  (no un modelo real).
- **Fake por defecto** (`commonMain`): `FakeAiClient(dimension = 8)` es
  determinista y sin red. Los vectores derivan de `String.hashCode()` (estable
  entre plataformas): mismo texto → mismo vector; textos distintos → vectores
  distintos salvo colisión de hash. `embed(emptyList()) == emptyList()`,
  `require(dimension > 0)` y `generate` devuelve una respuesta no vacía. Al
  vivir en `commonMain` (no en test) es inyectable desde el composition root.
- **Primer consumidor real** (`com.playbook.core.ai.NoteIndexingService`):
  `index(note: Note): IndexingResult` (`suspend`) llama
  `aiClient.embed(listOf(note.body))`; si es válido (exactamente 1 vector no
  vacío) hace `embeddingRepository.upsert(note.id, values)`. Un `AiClientException`
  o una respuesta inválida (0 vectores, >1 vector, dimensión 0) devuelve
  `IndexingResult.Failed` **sin escribir ni pisar** el embedding previo. **No
  toca `NoteStatus`**: un fallo de IA no degrada la Nota (la captura nunca se
  bloquea por IA). La fuente de verdad de "tiene embedding" es la fila en
  `embedding`.
- **Orquestador de indexado asíncrono**
  (`com.playbook.core.ai.NoteIndexingOrchestrator`, `ai-enrichment-classify-tags-type`):
  `index(note: Note): NoteStatus` (`suspend`) reutiliza `NoteIndexingService` **sin
  modificarlo** y persiste la transición de `NoteStatus` derivada del resultado:
  éxito (`IndexingResult.Indexed`) → `INDEXED`; fallo (`IndexingResult.Failed`) →
  `PENDING` **salvo** que la Nota **ya** estuviera `INDEXED`, en cuyo caso se queda
  `INDEXED` (un re-indexado fallido no degrada lo ya indexado y deja el embedding
  previo intacto). **No escribe `FAILED`**: la promoción `pendiente → fallida` y
  los reintentos son de `offline-pending-retry`. El fallo de IA nunca se propaga
  (`NoteIndexingService` lo convierte en `IndexingResult.Failed`), así la captura
  nunca se bloquea. No clasifica ni etiqueta la Nota y **no** crea Enlaces
  (`semantic-linking`). Es `suspend` y no agenda nada: el llamador decide cuándo
  invocarlo, fuera del camino de captura. La transición usa
  `NoteRepository.updateStatus` (sólo `status`, sin re-sellar `updated_at`).
- **Sin wiring a la UI todavía:** `NoteIndexingService`/`NoteIndexingOrchestrator`/
  `EmbeddingRepository` existen y están cubiertos por tests, pero `AiClient` **no**
  se cablea a `App` ni a los entry points; no hay superficie observable ni token de
  ciclo de vida que lo dispare (llegará con
  `note-enrichment-status-ui`/`offline-pending-retry`).
  La inyección se materializa en los tests del contrato (dos implementaciones por
  el mismo puerto) y en `NoteIndexingService`.
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
  attachmentRepository: AttachmentRepository, voiceTranscriber: VoiceTranscriber)`
  es la raíz de la UI y dueña del estado. Hoistea `notes` y `attachments`
  (`mutableStateOf`), `destination` (`NotesDestination`:
  `List`/`Create`/`Edit(noteId)`/`VoiceCapture`/`CreateWithImage(image)`/`Gdd`),
  `refreshKey`, `voiceState` (`VoiceUiState`) y el estado de captura de imagen
  (`showImageSourceDialog`/`imageError`). La lectura ocurre en un
  `LaunchedEffect(refreshKey)`, **nunca** en el cuerpo de composición; tras cada
  mutación se incrementa `refreshKey` (`reload()`). Sin DI, sin
  `ViewModel`/`Lifecycle`, sin coroutines fuera de Compose y sin Flow ni librería
  de navegación. Obtiene el `ImagePicker` y el `AttachmentFileStore` por
  `remember`; la acción de imagen abre un `AlertDialog` "Agregar imagen"
  (Cámara/Galería/Cancelar), `onResult` navega a `CreateWithImage` y `onError`
  muestra un `AlertDialog` accionable.
- **Owner del MVP:** `LOCAL_OWNER_ID = "local"` (`private const val` en `App.kt`);
  la UI lo provee al construir el `NoteDraft` de creación. `:core` no cambia.
- **`NotesListScreen`**: `Scaffold` con una fila de `ExtendedFloatingActionButton`
  ("Nueva nota" + "Foto"); acciones secundarias `FilledTonalButton` "Dictar nota"
  (`onDictate`) y "Ver GDD" (`onOpenGdd`); estado vacío ("Todavía no hay notas") con CTA "Crear primera nota"
  + "Agregar foto"; y estado con notas (título "Notas" + `LazyColumn` de `Card`s
  clickeables que llaman `onEdit(note.id)`). Cada tarjeta muestra
  `body.ifBlank { "Imagen adjunta" }` (máx. 2 líneas, ellipsis), un chip textual
  "Imagen" si la nota tiene adjuntos (`attachmentCounts`), chips de `track.code`
  (o "Sin track") y `status.code`, y —si `note.tags` no está vacía— una fila de
  chips de etiqueta (misma `LabelChip`, en `Row` con `horizontalScroll`). Todo se
  comunica con texto, no sólo color (`DESIGN.md`).
- **`NoteEditorScreen`**: editor de texto para crear (`isEditing = false`) y editar
  (`isEditing = true`). `OutlinedTextField` multilínea para `body`; selector de
  `track` ("Sin track" + `Track.entries`) que comunica la selección con marca
  textual "✓", borde y color; campo "Nueva etiqueta" + botón "Agregar" (aplica
  `normalizeTags`) y chips de etiqueta con control de quitar "×" (`clickable` con
  `onClickLabel`/`role = Role.Button`); `Guardar` deshabilitado si `body.isBlank()`
  **y** no hay `initialImage` (una nota sólo-imagen es válida); `Cancelar`; y en
  edición `Borrar` con `AlertDialog`. Cuando llega `initialImage` muestra la
  previsualización decodificada (`decodeImageBitmap`), "Quitar imagen" y el
  placeholder de fallo si no decodifica. Mantiene el borrador local
  (`body`/`track`/`tags`) y emite
  `onSave(body, track, tags)`/`onDelete`/`onCancel`/`onRemoveImage`; la
  persistencia y el refresco los maneja `App`. No hay control de
  Categoría/Tipo/Nivel: el `track` ya cubre la clasificación fija.
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
  track, tags = tags))` (status default `CAPTURED`); **crear con imagen** →
  `fileStore.saveImage(bytes, mime)` → `create(NoteDraft(...))` →
  `attachmentRepository.create(note.id, IMAGE, mime, relativePath, byteSize)` →
  `reload()`; si algo falla después de guardar el archivo, se borra (sin
  huérfanos). Editar → `update(note.copy(body = body.trim(), track = ...,
  tags = tags))` (re-sella `updatedAt`, reemplaza el conjunto de etiquetas y
  preserva `owner`/`createdAt`/`status`); borrar → se leen los adjuntos de la
  nota, `delete(id)` (limpia `note_tag` y las filas `attachment` en la misma
  transacción) y se borran los archivos con el `AttachmentFileStore`; siempre
  `reload()` y vuelta a la lista. El `status` no se edita desde la UI. Si el `id`
  en `Edit(noteId)` ya no existe en `notes`, se vuelve a la lista.
- **Deuda resuelta:** `notes-list-ui` leía la lista en el hilo de composición y no
  refrescaba tras mutaciones; `create-text-note` lo resolvió con `LaunchedEffect` +
  `refreshKey`. `note-category-and-tags` agregó la edición de etiquetas e
  `image-capture-camera` la captura/adjunto de imagen (sólo al **crear**; sin
  edición de adjuntos existentes ni render en la lista/detalle). `gdd-view` agregó
  la pantalla de GDD (ver abajo). Sigue fuera de esta superficie la IA.

## GDD

- **Proyección pura en `:core`** (`com.playbook.core.gdd`): `GddSection(track,
  notes)` y `buildGdd(notes): List<GddSection>`. Agrupa por `track` en orden fijo
  (`Track.entries`: `mecánicas`, `personajes`, `historia`) y añade una sección
  "Sin track" (`track = null`) al final **sólo** si hay notas sin `track`. Las
  tres secciones de `track` siempre están presentes (estructura estable); dentro
  de cada sección se conserva el orden de entrada (`getAll()`:
  `updated_at DESC, id DESC`). No muta la lista ni duplica notas. No depende de
  SQLDelight ni de `AiClient`: el GDD **no es una entidad persistida**, es una
  vista agregada sobre las Notas (`docs/domain-model.md`), sin tabla, query ni
  migración propias.
- **Pantalla `GddScreen`** (`:composeApp/commonMain`): render fino sobre
  `buildGdd`. Encabezado "GDD" + resumen del total ("N notas"); un botón "Volver";
  una sección por `track` (o "Sin track") con su conteo ("N notas") y el texto
  "Sin notas en este track" cuando está vacía; y las notas en un `LazyColumn`
  (`key = { it.id }`). Cada nota resume el `body` (máx. 2 líneas, ellipsis) y
  muestra chips textuales de `status`, Etiquetas (si tiene) y "Imagen" (si tiene
  adjuntos). Todo se comunica con texto, no sólo color (`DESIGN.md`).
- **Estado vacío:** si `notes.isEmpty()`, mensaje explícito "Todavía no hay notas"
  (con "0 notas" en el encabezado) sin secciones ni errores.
- **Navegación (sin librería):** `NotesListScreen` "Ver GDD" →
  `NotesDestination.Gdd`; tocar una nota → `NotesDestination.Edit(noteId)` (mismo
  editor que desde la lista); "Volver" desde el GDD → `List`. **Limitación
  aceptada:** al guardar/cancelar en el editor se vuelve a la lista, no al GDD (un
  `previousDestination` llegará con una feature de navegación/detalle).
- **Refresco:** `GddScreen` recibe `notes`/`attachmentCounts` ya hoisteados por
  `App` (recargados por `refreshKey`); no lee la base dentro del cuerpo de
  composición. No hay DI/`ViewModel`/Flow ni esquema/dependencias nuevos.

## Runtime surfaces

- **Android:** `MainActivity` (`com.playbook.app`) monta `App(noteRepository,
  attachmentRepository, AndroidVoiceTranscriber(this))`, aplica el esquema local v4
  con `createDatabase(AndroidDatabaseDriverFactory(...))` y construye
  `SqlDelightNoteRepository` + `SqlDelightAttachmentRepository`.
- **iOS:** `MainViewController()` (Kotlin, `composeApp/src/iosMain`) crea la base
  local, construye los dos repositorios y `IosVoiceTranscriber()`, y envuelve
  `App(notes, attachments, transcriber)`; lo llama `ComposeView` (SwiftUI) en
  `iosApp`.

## Identifiers

- Kotlin base package: `com.playbook`
- Android `namespace` / `applicationId`: `com.playbook.app`
- iOS bundle id: `com.playbook.app` (estable, sin sufijo de Team ID)
- iOS framework `baseName`: `ComposeApp` (static)
- Xcode scheme/target: `iosApp`; app display name `Playbook`
- Scheme compartido: `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme`
- iOS marketing version: `MARKETING_VERSION` (default `1.0`); build number:
  `CURRENT_PROJECT_VERSION` (default `1`). `Info.plist` usa
  `$(MARKETING_VERSION)`/`$(CURRENT_PROJECT_VERSION)`.

## Build and versions

- Gradle wrapper **8.14.5**, Kotlin **2.1.21**, Compose Multiplatform **1.8.2**,
  AGP **8.10.1**, SQLDelight **2.1.0**.
- compileSdk **36**, minSdk **24**, targetSdk **36**, JVM target **11**, JDK **21**.
- Versiones centralizadas en `gradle/libs.versions.toml`.
- Android SDK en `local.properties` (`sdk.dir=...`), no versionado.

## Android release

- **Superficie:** `:composeApp` es el único módulo que produce artefactos de
  release. `assembleRelease` genera `composeApp-release.apk` (o
  `composeApp-release-unsigned.apk` sin credenciales) y `bundleRelease` genera
  `composeApp-release.aab` (artefacto de subida a Play).
- **Signing config condicional:** `composeApp/build.gradle.kts` carga las
  credenciales con precedencia **variables de entorno > `keystore.properties`**
  (raíz, gitignored). Sólo si las cuatro (`storeFile`/`storePassword`/`keyAlias`/
  `keyPassword`, o sus equivalentes `PLAYBOOK_*`) están presentes se registra
  `signingConfigs.create("release")` y se asigna al build type `release`. Si
  falta alguna, no se registra el signing config y el release queda **sin firmar**
  (modo degradado) en vez de fallar: así `init.sh` y cualquier entorno sin
  secretos siguen funcionando.
- **Keystore fuera del repo:** el keystore de release es la *upload key* del
  desarrollador (Play App Signing custodia la *app signing key*). `.gitignore`
  cubre `keystore.properties`, `*.jks` y `*.keystore`; **nunca** se versionan
  secrets ni keystores.
- **Versión overrideable:** `versionCode`/`versionName` se pueden pasar como
  `-Pplaybook.versionCode=N` / `-Pplaybook.versionName=X` con los defaults
  actuales (`1` / `"1.0"`); no requiere editar el script.
- **R8/ProGuard:** `release` mantiene `isMinifyEnabled = false` (sin minificación
  ni shrinking) para no arriesgar Compose en release; la optimización de tamaño
  es trabajo posterior.
- **Runbook:** `docs/release/android.md` (generación de keystore, build firmado,
  verificación con `apksigner`/`jarsigner` e subida manual a Play internal
  testing). La subida real requiere Play Console y no es parte de `init.sh`.

## Verification

Gate no bloqueante: `./init.sh` (Android build + tests del core en Android/JVM
+ link del framework iOS + tests del core en iOS; no levanta dev servers).
Comandos manuales equivalentes:

- `./gradlew :composeApp:assembleDebug`
- `./gradlew :core:testDebugUnitTest` (o `:core:allTests`)
- `./gradlew :core:iosSimulatorArm64Test`
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`
- iOS app: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator build`

## Release / iOS distribution

- **Superficie:** `scripts/ios-release/` (shell + Xcode CLI, sin `fastlane`).
  `preflight.sh` valida prerequisitos (macOS/Xcode, scheme, `TEAM_ID`,
  credenciales, herramienta de subida) y falla sin archivar; `archive.sh` produce
  `build/ios/Playbook.xcarchive` en Release (firmado o con
  `CODE_SIGNING_ALLOWED=NO` para verificación local); `export-upload.sh`
  renderiza `ExportOptions.plist` desde
  `scripts/ios-release/ExportOptions.plist.template`, exporta el `.ipa` y lo
  sube con `xcrun altool --upload-app` (CLI confirmado en la Xcode instalada).
- **Firma e identidad:** bundle id estable `${BUNDLE_ID}` = `com.playbook.app`
  (sin `${TEAM_ID}`); `Release` usa `CODE_SIGN_IDENTITY = "Apple Distribution"`
  con `CODE_SIGN_STYLE = Automatic`; `Debug` conserva `Apple Development`. El
  `TEAM_ID` real no vive en el repo: `Config.xcconfig` versiona `TEAM_ID=` vacío
  y hace `#include? "Local.xcconfig"` (gitignored); los scripts también aceptan
  la env `TEAM_ID`.
- **Versionado:** `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` como build
  settings (default `1.0`/`1`); `archive.sh` los recibe por parámetro y los pasa
  como overrides. Cada subida exige un build number nuevo (TestFlight).
- **Gate:** `init.sh` **no** archiva ni sube (ver `CONSTRAINTS.md`). El release
  es explícito y separado del gate.
- **Estado:** los artefactos repo-side están verificados; el archive firmado /
  export / upload a TestFlight queda `blocked` hasta contar con Apple Developer
  Program + credenciales. Runbook: `docs/release/ios-testflight.md`.

## Deferred

- **OCR del boceto (texto derivado): descartado del MVP** (`image-ocr-optional`,
  2026-10-13). No se integra ningún motor: los on-device de bajo costo están
  orientados a texto impreso (calidad incierta sobre manuscrito), Android exigiría
  una dependencia pesada (ML Kit) poco compartida y `AiClient` es text-only.
  Decisión y **disparadores de re-evaluación** (adaptador `AiClient` con visión,
  necesidad de texto derivado para RAG, evidencia de calidad sobre manuscrito) en
  `docs/adr/0002-ocr-decision.md`. Si se reabre, el patrón sería un puerto
  `TextRecognizer` + adaptadores nativos (patrón `VoiceTranscriber`), guardando el
  texto derivado en la Nota (no en el `Adjunto`).
- Adjuntos de audio (`voice-attachment-storage`), edición de adjuntos de una nota
  existente y render de la imagen en la lista/detalle (hoy sólo chip "Imagen" +
  previsualización al crear).
- Enlaces (features posteriores). El wiring de `AiClient`/`NoteIndexingService` a
  la app (`note-enrichment-status-ui`, `offline-pending-retry`) y la similitud
  coseno / enlaces (`semantic-linking`) siguen pendientes; los embeddings ya se
  generan y persisten (`embeddings-generation`). El proveedor/modelo/dimensión y
  la gestión de keys son follow-ups del ADR 0001
  (`docs/adr/0001-ai-runtime-decision.md`).
- Adaptador on-device concreto (fase 2 de la estrategia híbrida).
- Guardar el audio original del dictado como `Adjunto`
  (`voice-attachment-storage`): `voice-capture-stt` sólo persiste la transcripción.
- Detalle de nota de sólo lectura (`note-links-ui`/posterior). La vista de GDD
  (`gdd-view`) ya está implementada; volver al GDD tras editar desde él queda
  fuera de alcance (ver sección GDD).
- Manejo del back físico Android/iOS en el editor y en la captura de voz (hoy sólo
  "Cancelar").
- Formalización de `DESIGN.md` (sigue `provisional`; sin entrega de UI/UX).
