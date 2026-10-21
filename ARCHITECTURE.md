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
Etiquetas libres (esquema v3 con `note_tag` y la migración `2.sqm`; reemplazadas
luego por Géneros),
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
`semantic-linking` agrega los Enlaces Nota↔Nota por similitud coseno (esquema v5
con `4.sqm`, tabla `note_link`, `NoteLinkRepository` y `SemanticLinkingService`).
`voice-attachment-storage` agrega el audio original del dictado como Adjunto
`AUDIO` (grabado en la misma sesión de voz, sin cambio de esquema).
`rag-query` agrega el retrieval por similitud coseno, `RagQueryService` y el primer
wiring del runtime de IA a la app (indexado best-effort al guardar + pantalla de
consulta). `rag-answer-citations` surfacea las Notas fuente como **citas
numeradas** con navegación a la Nota, sin persistirlas.
`offline-pending-retry` cierra el ciclo offline/online: reintenta las Notas
`PENDING` al arrancar (`PendingIndexingRetry`) y promueve a `FAILED` las que
agotan el presupuesto (`index_attempts`, esquema v6 con `5.sqm`); la
disponibilidad de la IA es un puerto inyectable (`AiAvailability`).
`gdd-export` cierra el GDD como documento compartible: proyección pura a Markdown
(`buildGddMarkdown`) + el puerto de share `GddSharer` (expect/actual, texto en
memoria).
`genres-catalog-persistence` reemplaza las Etiquetas por **Géneros** (esquema v7
con `6.sqm`, tabla `note_genre`): catálogo fijo `GenreCatalog` (12) +
personalizados por Nota, `normalizeGenres` con canonización case-insensitive; la
columna/relación `note_tag`→`note_genre` se migra **verbatim**.
`game-project-persistence` agrega el **Proyecto de Juego** singleton (esquema v8
con `7.sqm`): ficha estructurada en `game_project` + tablas hijas y
`GameProjectRepository` (`get`/`save` upsert); `brainstorming-wizard` cablea ese
repositorio a la UI mediante un wizard de 9 pasos. `gdd-document-hybrid` convierte
el GDD en documento del Proyecto (ficha + Notas por Track) y `gdd-export-hybrid`
incluye esa ficha en el Markdown compartido reutilizando la misma proyección.

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
- **Esquema v8:** tabla `note` (`id`, `owner`, `body`, `track` nullable, `status`,
  `created_at`, `updated_at`, `index_attempts` INTEGER NOT NULL DEFAULT 0; queries
  `insertNote`, `selectAllNotes`, `selectNoteById`, `selectNotesByStatus`,
  `updateNote`, `updateNoteStatus`, `updateNoteIndexAttempts`, `deleteNoteById`),
  tabla
  `embedding` (`note_id`
  PK y FK → `note(id)` `ON DELETE CASCADE`, `dimension`, `vector` BLOB,
  `indexed_at`), tabla `note_genre` (`note_id`, `genre`, PK `(note_id, genre)`; N—N
  sin FK, limpieza explícita en el repositorio), tabla `attachment` (`id`,
  `note_id`, `kind`, `mime_type`, `file_path`, `byte_size`, `created_at` + índice
  `attachment_note_id`), tabla `note_link` (`note_a_id`/`note_b_id` FK → `note(id)`
  `ON DELETE CASCADE`, `score`, `updated_at`, PK `(note_a_id, note_b_id)`,
  `CHECK (note_a_id < note_b_id)` + índice `note_link_note_b_id`) y las tablas del
  **Proyecto**: `game_project` (`id` fijo `"singleton"` con
  `CHECK (id = 'singleton')`, `owner`, `title`, `core_loop`, `audience`, `concept`,
  `fantasy`, `created_at`, `updated_at`) +
  `game_project_genre`/`game_project_inspiration`/`game_project_key_mechanic`/
  `game_project_out_of_scope` (FK → `game_project(id)` `ON DELETE CASCADE`; PKs
  `(project_id, genre)` o `(project_id, position)`). La columna `index_attempts`
  la agrega `offline-pending-retry`; la tabla N—N `note_tag` (Etiquetas) es
  reemplazada por `note_genre` (Géneros) en v7; los bytes de la imagen **no** se
  guardan en la base (sólo metadatos + ruta relativa).
- **Migraciones (v1 → v2 → v3 → v4 → v5 → v6 → v7 → v8):**
  `embeddings-generation` introdujo `1.sqm` (v1 → v2, `embedding`),
  `note-category-and-tags` `2.sqm` (v2 → v3, `note_tag`), `image-capture-camera`
  `3.sqm` (v3 → v4, `attachment`), `semantic-linking` `4.sqm` (v4 → v5,
  `note_link` + índice), `offline-pending-retry` `5.sqm` (v5 → v6,
  `note.index_attempts`), `genres-catalog-persistence` `6.sqm` (v6 → v7,
  `note_tag` → `note_genre` con copia verbatim + `DROP TABLE note_tag`) y
  `game-project-persistence` `7.sqm` (v7 → v8, tablas del Proyecto, aditivo sin
  datos). Los `.sq`
  describen el esquema más reciente y los `.sqm` hacen upgrade; los drivers
  (`AndroidSqliteDriver`/`NativeSqliteDriver` con `PlaybookDatabase.Schema`)
  detectan `user_version` y ejecutan `Schema.migrate`.
  `PlaybookDatabase.Schema.version = 8`. `INSERT OR REPLACE` en lugar de
  `ON CONFLICT DO UPDATE` porque el UPSERT de SQLite exige 3.24 (Android 11+) y
  `minSdk = 24`.
- **Migraciones sin binario:** el repo no usa `schemaOutputDirectory` ni
  `verifySqlDelightMigration`; la equivalencia entre los `.sqm` y los `CREATE TABLE`
  de los `.sq` se cubre con tests portables (`DatabaseMigrationCheck`,
  `GenreCheck.verifyGenreMigration`, `verifyMigrationV1ToV8`,
  `verifyMigrationV5ToV6`, `verifyMigrationV6ToV7` y `verifyMigrationV7ToV8`) que
  corren en Android/JVM e
  iOS.
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
  nullable, `status` no nulo, `createdAt`, `updatedAt`, `genres`), `NoteDraft`
  (entrada de `create`, `status` default `CAPTURED`, `genres` default vacío) y los
  enums `Track` (`MECHANICS`/`CHARACTERS`/`STORY`) y `NoteStatus`
  (`CAPTURED`/`PENDING`/`INDEXED`/`FAILED`) con `code`/`fromCode`. Los
  identificadores Kotlin están en inglés; el `code` persistido usa el valor de
  dominio canónico en español (`"mecánicas"`, `"personajes"`, `"historia"`,
  `"capturada"`, `"pendiente"`, `"indexada"`, `"fallida"`).
- **Géneros** (`Genres.kt`, `genres-catalog-persistence`): `GenreCatalog.GENRES`
  es el catálogo **fijo** de 12 (`Platformer`, `Metroidvania`, `JRPG`, `ActionRPG`,
  `Roguelike`, `Shooter`, `Puzzle`, `Adventure`, `Towerdefense`, `Novel`,
  `Farming`, `Arcade`) y `GenreCatalog.canonicalize(raw)` devuelve la grafía
  canónica del catálogo si `raw` coincide case-insensitive (si no, `raw`).
  `normalizeGenres(raw: List<String>)` aplica `trim`, descarta vacíos, **canoniza**
  contra el catálogo, deduplica sin distinguir mayúsculas/minúsculas conservando la
  **primera** grafía resultante y ordena case-insensitive. Los Géneros
  **personalizados** (no del catálogo) viven por Nota y no engrosan el catálogo
  global. `genres` se agrega con `emptyList()` por defecto; `Track` (fijo y
  único, por sección) y `Género` (descriptivo y múltiple) **no** son sinónimos
  (`CONTEXT.md`).
- **Proyecto de Juego** (`GameProject.kt`, `game-project-persistence`):
  `GameProject` (id fijo `"singleton"`, owner, title, genres, coreLoop, audience,
  concept, inspirations, fantasy, `keyMechanics`, outOfScope, createdAt,
  updatedAt) es la ficha estructurada del único Proyecto por instalación.
  `GameProjectDraft` es la entrada de escritura; `KeyMechanic(category,
  description)` y `MechanicCategory` (`MOVEMENT`/`INTERACTION`/`CONFLICT` con
  `code` en español) modelan las mecánicas. Todos los campos de contenido son
  opcionales; escalares con `trim`/vacío→`null` y listas con `trim`, descarte de
  vacíos y límites `0..3` (inspiraciones/fuera de alcance) y `0..5` (mecánicas).
  `GameProjectRepository { get(); save(draft) }` +
  `SqlDelightGameProjectRepository(database, clock)`: `save` es un upsert del
  singleton que preserva `owner`/`createdAt` (un owner distinto falla), avanza
  `updatedAt` y reemplaza las hijas en la transacción. Sin estado
  `vacío`/`esbozado` persistido (se deriva) y sin FK Nota→Proyecto (pertenencia
  implícita). Cableado a la UI por `brainstorming-wizard` (wizard de 9 pasos;
  guardado explícito de la ficha completa).
- **Mapper de Brainstorming** (`com.playbook.core.brainstorming`,
  `brainstorming-note-per-step`): `BrainstormingStepNote(body, track, genres)` y
  `buildBrainstormingStepNote(step, draft)` proyectan el estado de un paso
  (índice `0..8`) a una Nota; `isDuplicateBrainstormingNote` normaliza ambos
  lados de los Géneros para bloquear duplicados. Core Loop/Mecánicas →
  `MECHANICS`, Concepto/Fantasía → `STORY`, el resto `null`; puro y determinista.
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
    `body`/`track`/`genres`/`owner`/`created_at`. Devuelve `rowsAffected > 0`.
  - Géneros: `create` normaliza (con canonización de catálogo) y persiste
    `insertNote` + `insertNoteGenre` en una transacción; `getById` lee
    `selectGenresByNoteId`; `getAll` usa
    `selectAllNoteGenres` agrupado en memoria (evita N+1); `update` reemplaza el
    conjunto completo (delete + insert) en la misma transacción que la nota y
    sólo si la fila existía; `delete` limpia `note_genre` y las filas `attachment`
    de la nota en la misma transacción (`transactionWithResult`), sin huérfanos.
- **Adjunto** (`com.playbook.core.model`): `Attachment` (id, noteId, kind,
  mimeType, filePath, byteSize, createdAt) + enum `AttachmentKind` (`IMAGE` →
  `"imagen"`; `AUDIO` → `"audio"`, el audio original de un dictado) con
  `code`/`fromCode` fail-fast. `AttachmentMappers.kt` aliasa
  `com.playbook.core.db.Attachment as AttachmentRow`. `AttachmentRepository`
  (`create`/`getByNoteId`/`getAll`/`deleteByNoteId`) +
  `SqlDelightAttachmentRepository(database, idFactory, clock)`. `filePath` es
  relativo al contenedor de la app (`images/<uuid>.jpg` o `audio/<uuid>.m4a`);
  el `kind` se persiste como texto y agregar `AUDIO` no cambió el esquema.
- **Mapper** (`NoteMappers.kt`): importa `com.playbook.core.db.Note as NoteRow`
  para evitar la colisión con el modelo de dominio;
  `NoteRow.toDomain(genres = emptyList())` recibe los Géneros ya resueltos.
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
- **Enlace / Relación** (`com.playbook.core.model`): `NoteLink(noteAId, noteBId,
  score, updatedAt)` es **siempre canónico** (`noteAId < noteBId`); la extensión
  `NoteLink.otherNoteId(of)` resuelve "la otra Nota" y falla rápido si `of` no es
  un extremo. `NoteLinkMappers.kt` aliasa `com.playbook.core.db.Note_link as
  NoteLinkRow`. `NoteLinkRepository` (`upsert`/`getLinksForNote`/`getAll`/
  `deleteAll`) + `SqlDelightNoteLinkRepository(database, clock)`: `upsert` valida
  `noteAId != noteBId` y `score.isFinite()`, canoniza el orden y hace
  `INSERT OR REPLACE`; `getLinksForNote` consulta ambos lados
  (`note_a_id = :id OR note_b_id = :id`). El borrado de la Nota cascadea las filas
  `note_link` por FK (no se toca `SqlDelightNoteRepository.delete`).
- **Similitud y servicio de enlazado** (`com.playbook.core.linking`):
  `CosineSimilarity.score(a, b): Double?` (puro common) devuelve `null` si las
  dimensiones difieren o alguna norma es `0`; si no, `dot / (‖a‖·‖b‖)`.
  `SemanticLinkingService(embeddingRepository, noteLinkRepository, threshold =
  DEFAULT_SIMILARITY_THRESHOLD = 0.8)` expone `recomputeAll(): LinkingSummary`
  (`notesConsidered`, `linksWritten`): lee `EmbeddingRepository.getAll()`, borra
  todos los enlaces y reconstruye los pares con `score >= threshold` (inclusivo,
  inyectable, `require(threshold in -1.0..1.0)`). Determinista e idempotente.
  **No** toca `NoteStatus` ni los embeddings. Ya se cablea a la app: se construye
  en los composition roots y se inyecta en `App(...)`; la UI de lectura es
  `RelatedNotesScreen` y el **único** trigger es un recomputo best-effort al abrir
  "Notas relacionadas" (ver "Enlaces (notas relacionadas)"); el trigger de ciclo de
  vida sigue como follow-up.
- **IDs y tiempo** (`com.playbook.core.platform`): `expect`/`actual`
  `randomNoteId()`/`currentTimeMillis()` (Android `UUID`/`System`; iOS
  `NSUUID`/`NSDate`). En tests se inyectan fakes deterministas; no se usa
  `expect`/`actual` en source sets de test.
- **Orden de lectura:** `selectAllNotes` ordena por `updated_at DESC, id DESC`
  (cambio de query en `notes-list-ui`, sin cambio de esquema). El `id` desempata
  de forma estable los `updated_at` iguales; `getAll()` es determinista y está
  cubierto por `verifyNoteCrud`.
- El repositorio se instancia en los entry points de plataforma y se pasa a la
  UI; no hay DI.

## Attachments y captura nativa

- **Contrato de captura** (`composeApp/src/commonMain/.../ImageCapture.kt`):
  `ImageSource` (`CAMERA`/`GALLERY`), `PickedImage` (bytes + mimeType, en
  memoria), `ImageCaptureError` (`PERMISSION_DENIED`, `CAMERA_UNAVAILABLE`,
  `READ_FAILED`, `UNKNOWN`), `ImagePicker` + `rememberImagePicker(...)` y
  `decodeImageBitmap(bytes)` (`expect`; actuals Android/iOS).
  `rememberAttachmentFileStore()` sigue siendo `expect` en `:composeApp`, pero
  sólo calcula el `baseDir` de la plataforma y delega en el store de `:core`.
- **Store de Adjuntos en `:core`** (`com.playbook.core.storage`):
  `StoredImage(relativePath, byteSize)`, `AttachmentFileStore`
  (`saveImage`/`read`/`delete`) + `expect fun attachmentFileStore(baseDir)`.
  `read(relativePath)` devuelve los bytes **exactos** o `null` si el archivo no
  existe/no se puede leer; `read`/`delete` normalizan/canonicalizan la ruta y
  devuelven `null`/`false` si escapa de `baseDir` (guard de path traversal).
  Vive en `:core` para poder testear el IO cross-platform (precedente:
  `DiagnosticStorage`), sin que la UI dependa de APIs de plataforma.
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
  `PickVisualMedia` (sin permiso). El `actual` Android del store de `:core`
  (`java.io.File`) usa `filesDir/images/`; `decodeImageBitmap` usa
  `BitmapFactory`.
  `AndroidManifest.xml` declara `CAMERA`, `uses-feature camera required=false` y
  el `<provider>` `FileProvider` (`${applicationId}.fileprovider` +
  `res/xml/file_paths.xml`).
- **iOS:** cámara con `AVCaptureDevice` (availability + permission) y
  `UIImagePickerController` presentado desde el controller superior; si el
  simulador no expone cámara → `CAMERA_UNAVAILABLE`. Galería con
  `PHPickerViewController` (sin permiso). La imagen se re-encodea a JPEG
  (`UIImageJPEGRepresentation`, calidad 0.9). El `actual` iOS del store de
  `:core` (`NSFileManager`/`NSData`) usa `Application Support/images/`;
  `decodeImageBitmap` usa `org.jetbrains.skia.Image`. `Info.plist` incluye
  `NSCameraUsageDescription`.
  Los delegates de UIKit son weak: se retienen en un holder para no liberarlos
  mientras el picker está presentado.
- **Ciclo de vida del archivo:** al guardar se escribe el archivo **antes** de
  crear la nota y el adjunto; si algo falla después, se borra el archivo
  recién guardado (sin huérfanos). Al borrar la nota, las filas se borran en
  cascade dentro del repositorio y los archivos con el `AttachmentFileStore`
  desde la UI.
- **Audio del dictado (`voice-attachment-storage`):** el audio original se graba
  en la **misma sesión de micrófono** del STT y se entrega en `onFinalResult`
  como `RecordedAudio(relativePath, byteSize, mimeType)` (AAC en MPEG-4,
  `audio/<uuid>.m4a`, `audio/mp4`). Usa el mismo root privado que las imágenes
  (`filesDir/audio/` en Android, `Application Support/audio/` en iOS), así que el
  `AttachmentFileStore.delete(relativePath)` ya cubre el audio al borrar la Nota.
  `App` persiste el Adjunto `AUDIO`; si la persistencia falla, borra el archivo y
  conserva la Nota (degradación tolerante). No hay cambio de esquema (sigue v4).

## AI

- **Puerto** (`com.playbook.core.ai`): `AiClient` con
  `suspend fun embed(texts: List<String>): List<Embedding>` y
  `suspend fun generate(request: GenerateRequest): String`. Tipos: `Embedding`
  (`values: List<Float>`), `GenerateRequest` (`prompt`, `context` default
  `emptyList()` y, desde `ai-real-chat`, `instructions`, `temperature` y
  `maxTokens` opcionales con `null` por defecto) y `AiClientException` para
  fallos del runtime (red, cuota, modelo). `suspend` porque las llamadas de IA
  son asíncronas y cancelables.
- **Dirección de dependencia (regla durable):** la lógica de dominio consume
  **sólo el puerto** `AiClient`; los adaptadores concretos se inyectan desde
  afuera. `:core` expone el puerto y un adaptador fake por defecto. Excepción
  decidida en `ai-fake-real-switch`: los adaptadores reales OpenAI-compatible
  viven en `:core` (`com.playbook.core.ai.openai`) **detrás de `HttpTransport`**,
  y el transporte de plataforma (Android) se inyecta desde `:composeApp`; así el
  dominio no se acopla a red ni a un cliente HTTP y los tests corren offline.
- **Runtime decidido (`ai-runtime-decision`, 2026-10-09):** estrategia
  **híbrida por fases, cloud-first**, detrás del puerto `AiClient` sin cambiarlo
  (ver `docs/adr/0001-ai-runtime-decision.md`). Orden: (1) primer adaptador real
  en la **nube** (`embeddings-generation` para `embed`; `rag-query` para
  `generate`) con indexado asíncrono tolerante a fallos
  (`capturada → pendiente → indexada/fallida`, `offline-pending-retry`); (2)
  adaptador **on-device** (empezando por embeddings) inyectado por el mismo
  puerto cuando se elija el runtime nativo; (3) `FakeAiClient` como default
  offline/determinista. `:core` no gana un SDK de IA: el adaptador real
  OpenAI-compatible vive en `:core` detrás de `HttpTransport`, y la
  implementación de red se inyecta desde `:composeApp`.
- **Puertos por modelo (ADR 0004, `ai-client-ports`, 2026-10-16):** el core
  expone un contrato por modelo de IA. `chat` y `embedding` siguen en `AiClient`
  (`generate`/`embed`); los demás viven como interfaces independientes en
  `AiPorts.kt`: `TranscribeClient.transcribe(audio, mimeType): String`,
  `TtsClient.synthesize(text): SynthesizedAudio`,
  `ImageClient.generateImage(prompt): GeneratedImage` y
  `JevClient.suggest(text): ClassificationSuggestion` (`track: Track?`,
  `genres: List<String>`; **sólo propone**, nunca auto-asigna). Tipos de salida
  `SynthesizedAudio`/`GeneratedImage` (`bytes`+`mimeType`; `class`, no
  `data class`, porque contienen `ByteArray`). Todos `suspend` y con fallos vía
  `AiClientException`. Los fakes deterministas y sin red por defecto viven en
  `FakeAiPorts.kt` (`FakeTranscribeClient`/`FakeTtsClient`/`FakeImageClient`/
  `FakeJevClient`). El bundle `AiRuntime` y la selección fake/real son de
  `ai-fake-real-switch`; las features de cada caso de uso consumen los puertos.
- **Sugerencia de clasificación (`ai-suggest-classification`, 2026-10-20):**
  `ClassificationSuggestionService(jevClient, timeoutMillis = 20_000)` en
  `ClassificationSuggestions.kt`. `suggest(text)` recorta el texto y, si queda
  vacío, devuelve `Empty` **sin** llamar al puerto; si no, usa
  `withTimeout` + `JevClient.suggest`, normaliza los Géneros con
  `normalizeGenres` y devuelve `Success`/`Empty` (`Failed` para
  `AiClientException` o timeout; la cancelación del llamador se propaga). La
  función pura `applyClassificationSuggestion` reemplaza el `Track` sólo si la
  sugerencia lo trae (un `null` conserva el actual) y **fusiona** los Géneros
  normalizados sin duplicados ni pérdidas. Nada se persiste, ni se toca
  `NoteStatus`/indexado/esquema; el usuario confirma o descarta en el editor.
  `FakeJevClient` devuelve un Género de `GenreCatalog.GENRES` determinista por
  hash. `App` recibe `jevClient` por separado de `aiClient` y crea el servicio
  recordado para los tres editores (`Create`, `CreateWithImage`, `Edit`); los
  roots pasan `aiRuntime.jevClient` (iOS sigue fake).
- **Selección fake/real y runtime OpenAI-compatible (`ai-fake-real-switch`,
  2026-10-19):** `AiRuntime` (`com.playbook.core.ai.openai`) agrupa `aiClient`
  (`AiClient`), los 4 puertos, `AiAvailability` y `embeddingDimension`.
  `fakeAiRuntime()` es el default (fakes + `AlwaysAvailableAiAvailability` +
  8 dims); `openAiAiRuntime(config, transport)` construye los 6 adaptadores
  reales y `ConfiguredAiAvailability`. El flag de build
  `playbook.ai.mode` (`PLAYBOOK_AI_MODE`, default `fake`) se resuelve con
  precedencia `-P` > env > `local.properties`; `init.sh` fuerza `fake`. La key
  (`playbook.ai.apiKey` / `PLAYBOOK_AI_API_KEY` / `local.properties`) se inyecta
  en `BuildConfig` **sólo** en modo real y **nunca** se versiona, loguea ni
  muestra; `real` es el default sólo en debug (release real exige opt-in por
  propiedad Gradle: `-Pplaybook.ai.allowReleaseKey=true`; env/local no cuentan;
  `findProperty` también resuelve `gradle.properties`/`ORG_GRADLE_PROJECT_*`, por
  lo que el hardening estricto a sólo `-P` queda pendiente) y un real incompleto
  falla el build. `HttpTransport` es el borde de red:
  `UrlConnectionHttpTransport` vive en `:composeApp/androidMain`, **no sigue
  redirects** (`instanceFollowRedirects=false`; un 3xx se mapea a
  `AiClientException` genérica), ejecuta el I/O en `Dispatchers.IO` (nunca en
  Main; dispatcher inyectable) e iOS congelado usa `fakeAiRuntime()`. El
  adaptador de imagen usa 1024x1024 (el proveedor rechaza 512/256) y detecta el
  mime real (PNG/JPEG) por la firma de los bytes.
- **Transcripción diferida del audio (`transcribe-audio-attachment`, 2026-10-20):**
  `AudioAttachmentTranscriptionService` (`:core`) recibe `TranscribeClient`,
  `AttachmentRepository`, `AttachmentFileStore` y (opcional) `Diagnostics`.
  `transcribe(noteId, attachmentId)` exige un Adjunto `AUDIO` **de** esa Nota
  (si no → `AudioNotFound`, sin llamar al puerto), lee los bytes exactos con
  `read` (si `null` → `AudioUnavailable`), llama `transcribe(bytes, mimeType)`
  una vez y devuelve `Transcribed(text.trim())`, `EmptyTranscription` o
  `Failed(AiClientException)`. **No persiste ni indexa**: el editor aplica el
  texto al borrador y el Guardar existente (`update` + `indexBestEffort`) lo
  re-indexa. El wiring pasa `aiRuntime.transcribeClient` a `App(...)`; no cambia
  `AiRuntime`/`TranscribeClient` ni el esquema.
- **Generación de imagen desde texto (`image-generation-from-text`, 2026-10-20):**
  `TextImageGenerationService` (`:core`) recibe `ImageClient` y `Diagnostics`.
  `generate(prompt)` normaliza el prompt (vacío → `EmptyPrompt` **sin** llamar al
  puerto), llama `generateImage(trimmed)` una vez y valida el `GeneratedImage`
  (bytes no vacíos, `mimeType` no vacío e `image/*`; inválido → `InvalidImage`).
  `AiClientException` → `Failed` con diagnóstico `IMAGE`/`image_generation_failed`;
  la imagen inválida registra `image_generation_invalid`. No persiste ni indexa.
  La UI (`App` + `ImageGenerationDialog`) ofrece "Generar con Playbook" desde el
  diálogo "Añadir imagen" de la lista, muestra carga/error/cancelación y al
  éxito navega a `CreateWithImage(PickedImage)`; el Guardar existente
  (`saveNoteWithImage`) persiste el Adjunto `IMAGE` con ruta relativa/mime real,
  sin reindexar por la imagen. `FakeImageClient` devuelve un PNG 1×1 decodificable
  con chunk `tEXt` determinista (Base64 + CRC32) antes de `IEND`, para que el
  smoke fake muestre preview/miniatura sin red.
- **Lectura en voz alta (`tts-read-aloud`, 2026-10-20):**
  `ReadAloudService` (`:core`) normaliza el texto (vacío → `EmptyText` sin llamar
  al puerto; recorte exacto a 4096) y devuelve `Ready(audio)`/`Failed` desde
  `TtsClient.synthesize`; `buildGddNarration` construye la narración determinista
  del GDD (título, ficha y cuerpos no vacíos por sección). `App` posee un único
  `ReadAloudUiState` (`Detenido`/`Preparando`/`Reproduciendo`/`Error`), un único
  `ReadAloudPlayer` y un request-id que descarta resultados tardíos; detiene al
  navegar/cerrar. El player es `expect`/`actual`: Android usa `MediaPlayer` +
  `MediaDataSource` sobre bytes en memoria (sin archivos); iOS congelado aporta un
  actual mínimo sin playback. `FakeTtsClient` devuelve un WAV PCM determinista
  (`audio/wav`) para el smoke; el adaptador real (MP3) no cambia. Sin Adjuntos,
  caché, audio focus, timeout propio ni cambios de esquema.
- **Sugerencias de Brainstorming (`brainstorming-ai-suggestions`, 2026-10-19):**
  `BrainstormingSuggestionService` (`:core`) arma un `GenerateRequest` por paso
  (`BrainstormingStep`; prompt con marcador + `STEP=<enum>` y contexto acotado del
  `GameProjectDraft`, incluidos Géneros), llama `AiClient.generate` y parsea hasta
  **2 ideas únicas**, descartando las que ya están en el borrador. Mecánicas
  exigen categoría (`Movimiento`/`Interacción`/`Conflicto`) + descripción; el
  resultado es `Success(partial)` / `Failed` / `InvalidResponse`. No persiste ni
  crea Notas. `FakeAiClient` devuelve 2 ideas parseables cuando el prompt contiene
  el marcador (determinista, sin red). `BrainstormingWizardScreen` mantiene el
  estado efímero por paso y sólo muta el borrador con Usar/Añadir; las
  sugerencias se descartan al navegar.
- **Chat real de Brainstorming/Q&A (`ai-real-chat`, 2026-10-20):** sin duplicar
  `ai-fake-real-switch`, el adaptador `OpenAiAiClient.generate` acepta
  `instructions` (se agregan al `system`), `temperature` y `maxTokens` (se
  serializan como `temperature`/`max_tokens` sólo si no son `null`) y rotula el
  contexto como `Contexto provisto:`. Brainstorming usa `temperature=0.7`/
  `maxTokens=400`, refuerza el prompt contra preámbulos/markdown y parsea hasta 2
  ideas tolerando encabezados, `**`/`__`/backticks, viñetas, numeración, comillas,
  preámbulos y separadores; el dedupe/filtro contra el borrador normaliza
  espacios y mayúsculas. Q&A conserva `prompt=question.trim()` y
  `context=cuerpos de sources`, agrega `RAG_INSTRUCTIONS` de grounding y usa
  `temperature=0.2`/`maxTokens=600`; normaliza fences/encabezados/`**`/`__` y un
  vacío/sin sustancia se mapea a `Failed` + `AI_QUERY` (un refusal textual sigue
  siendo `Answer`). No persiste, no cae a fake, no toca UI/esquema y se verifica
  con smoke real key-gated fuera del gate.
- **Reconciliación de dimensión de embeddings (`ai-fake-real-switch`):**
  `com.playbook.core.ai.EmbeddingDimensionReconciler` recibe la dimensión del
  runtime activo (1536 en real, 8 en fake). Si un embedding persistido no coincide,
  lo borra (`deleteByNoteId`), deja la Nota `PENDING` y resetea
  `index_attempts=0`; el reintento de arranque la re-indexa. No cambia el
  esquema ni llama a la IA. Se ejecuta en los composition roots antes de
  `retryPending()`.
- **Embeddings reales en el indexado (`ai-real-embeddings`, 2026-10-19):**
  contrato de dimensión en dos límites. (1) `OpenAiAiClient.embed` exige que
  **cada** vector mida `config.embeddingDimension` (1536 real) y, si no, lanza
  `AiClientException("embedding devolvió dimensión N, se esperaba M")` sin key,
  headers ni body. (2) `NoteIndexingService` acepta
  `expectedDimension: Int? = null` (`require` > 0 o `null`); con `null` conserva
  el comportamiento anterior y con un valor rechaza el mismatch **antes** de
  `embeddingRepository.upsert` (`IndexingResult.Failed` + `AI_INDEXING`). Los
  composition roots pasan `aiRuntime.embeddingDimension` (8 fake / 1536 real).
  Un mismatch deja la Nota `PENDING` y sigue el presupuesto de
  `offline-pending-retry`; nunca se persiste un vector de dimensión inesperada
  ni se degrada una Nota ya `INDEXED` en un re-indexado fallido. La cobertura de
  integración conecta `openAiAiRuntime(config, HttpTransport falso)` con
  SQLDelight in-memory, orquestador, retry, reconciliación, Enlaces y RAG, sin
  red. Esto valida contrato/máquina de estados, **no** calidad semántica ni
  disponibilidad del proveedor (eso lo cubre el smoke real key-gated).
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
  `embedding`. Cada `Failed` se reporta a `Diagnostics` (`AI_INDEXING`) de forma
  best-effort (ver "Observability").
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
- **Reintento de pendientes (`offline-pending-retry`, 2026-10-15):**
  `com.playbook.core.ai.PendingIndexingRetry`
  (`retryPending(): RetrySummary`, `suspend`) cierra el ciclo offline/online. Si
  `AiAvailability.isAvailable()` es `false` es un **no-op** (no consume
  presupuesto, no cambia estados, no llama a `AiClient`). Si está disponible,
  recorre `NoteRepository.getByStatus(PENDING)` (sólo lectura) y por cada Nota
  llama a `NoteIndexingOrchestrator.index`: éxito → `setIndexAttempts(id, 0)`;
  fallo → `index_attempts + 1` y, al alcanzar `budget` (default
  `DEFAULT_RETRY_BUDGET = 3`, `require(budget > 0)`),
  `updateStatus(id, FAILED)`. `setIndexAttempts`/`updateStatus` no re-sellan
  `updated_at`. Una `FAILED` es terminal para el reintento (no aparece en
  `getByStatus(PENDING)`) hasta que una edición la reindexe con el orquestador.
  El presupuesto es durable (`note.index_attempts`, esquema v6 con `5.sqm`).
- **Disponibilidad de la IA (`AiAvailability`):** puerto en
  `com.playbook.core.ai` (`suspend fun isAvailable(): Boolean`) con default
  `AlwaysAvailableAiAvailability` (siempre `true`). No hay red ni runtime real en
  el MVP (ADR 0001), así que la "vuelta de la IA" se aproxima con el arranque de
  la app; la sonda real de conectividad es follow-up del ADR. Se inyecta por
  constructor para poder degradar y testear sin runtime real.
- **Wiring a la UI (`rag-query` 2026-10-13; `note-enrichment-status-ui` 2026-10-14;
  `offline-pending-retry` 2026-10-15):**
  los composition roots construyen `SqlDelightEmbeddingRepository`, `FakeAiClient`,
  `NoteIndexingService`, `NoteIndexingOrchestrator`, `PendingIndexingRetry` y
  `RagQueryService` y los pasan a `App(...)`. `App` indexa best-effort al
  guardar/editar una Nota con `body` no vacío vía `NoteIndexingOrchestrator`
  (transiciona `NoteStatus` y refresca el badge) y la pantalla de consulta invoca
  `RagQueryService.ask`. El editor permite re-indexar a mano una Nota no indexada.
  En el arranque (`LaunchedEffect(Unit)`) se corre `PendingIndexingRetry.retryPending()`
  y se refresca: las `PENDING` se reintentan y las irrecuperables pasan a `FAILED`.
  No hay scheduling ni detección de conectividad real.
- **Dependencias:** `kotlinx-coroutines-core` (commonMain) por el contrato
  `suspend`; `kotlinx-coroutines-test` (commonTest) para `runTest`. Versión
  **1.10.1**, compatible con Kotlin 2.1.21.

## RAG (consulta en lenguaje natural)

- **Utilidad de similitud** (`com.playbook.core.ai.cosineSimilarity`): pura de
  Kotlin common (`kotlin.math.sqrt`). Exige vectores de igual tamaño
  (`IllegalArgumentException` fail-fast) y devuelve `0f` cuando el denominador es
  `0f` (vector nulo, sin `NaN`). Es reusable (la consumirá `semantic-linking`).
- **`RagQueryService`** (`com.playbook.core.ai`): `ask(question)` con
  `require(question.isNotBlank())` y `maxSources` (default
  `DEFAULT_MAX_SOURCES = 5`). Orden: (1) `embeddingRepository.getAll()`; vacío →
  `NoIndexedNotes` **sin** llamar al runtime; (2) asocia las Notas por `id` y
  descarta embeddings sin Nota; (3) `aiClient.embed(listOf(question.trim()))`
  (try/catch `AiClientException` → `Failed`); si no devuelve exactamente 1 vector
  no vacío → `Failed`; (4) score coseno contra cada candidato de dimensión
  compatible (los incompatibles se descartan), orden descendente con desempate
  estable por `note.id`, top-K; (5) `context = top.map { it.note.body }` y
  `aiClient.generate(GenerateRequest(prompt = question.trim(), context,
  instructions = RAG_INSTRUCTIONS, temperature = 0.2, maxTokens = 600))`
  (try/catch → `Failed`); (6) normaliza fences/encabezados/`**`/`__` y, si no
  queda contenido, `Failed` + `AI_QUERY`; (7) `Answer(text, sources)`. Los fallos
  del runtime se mapean a `Failed` sin propagar la excepción ni inventar
  respuesta. Cada `Failed` se reporta a `Diagnostics` (`AI_QUERY`) de forma
  best-effort y sin registrar la pregunta (ver "Observability").
- **`RagAnswer`** (`Answer(text, sources)` / `NoIndexedNotes` / `Failed(cause)`) y
  **`RetrievedNote(note, score)`**. `sources` viene ordenado por score
  descendente; la UI lo renderiza como **citas numeradas** ("Fuentes", rank 1..N,
  fragmento + `track`) y permite abrir la Nota fuente (`rag-answer-citations`).
- **Sin umbral ni vector DB:** el retrieval es top-K **sin umbral** (la
  calibración es pregunta abierta de `semantic-linking`) y en memoria sobre los
  BLOBs. Es **efímero** (por consulta): no crea filas `link`.
- **Sin conocimiento externo:** sin Notas indexadas no se llama al runtime (las
  respuestas provienen sólo de las Notas). El `generate` ancla la respuesta en el
  `context` recuperado y `RAG_INSTRUCTIONS` refuerza el grounding (responder sólo
  con el contexto, no inventar, decir si no alcanza, no enumerar fuentes).
- **Runtime real:** el composition root inyecta `FakeAiClient` (default) o el
  `AiRuntime` real; `RagQueryService` y `BrainstormingSuggestionService` no
  conocen el proveedor. La verificación fake prueba el **pipeline**; la calidad
  real se cubre con el smoke key-gated de `ai-real-chat` (2 ideas parseables y
  respuesta + fuentes), fuera del gate.
- **Indexado best-effort al guardar** (glue del RAG): tras crear/editar una Nota
  (texto/voz/imagen) con `body` no vacío se dispara
  `scope.launch { noteIndexingService.index(note) }`; es silencioso ante fallo,
  no bloquea el guardado y **no** cambia `NoteStatus`. Las Notas con `body` en
  blanco (p. ej. sólo imagen) no se indexan. Sin esto, ninguna Nota del usuario
  tendría embedding y el RAG siempre vería `NoIndexedNotes`.
- **UI (`AskNotesScreen`)**: `OutlinedTextField` + botón "Preguntar"
  (deshabilitado con la pregunta en blanco o mientras corre), estado de carga
  ("Pensando…"), `Card` con el **texto** de la respuesta y, debajo, la sección
  **"Fuentes"** (`rag-answer-citations`): una cita por cada `RetrievedNote` de
  `sources` (rank `"Fuente N"` 1..N, fragmento del `body` a 2 líneas con ellipsis
  o "Sin texto", chip de `track` o "Sin track"), clickeable con
  `onClickLabel = "Abrir nota fuente"`/`Role.Button`; sin fuentes no hay sección.
  Mensaje explícito de "sin notas indexadas", error con "Reintentar" y "Volver".
  Estados `AskUiState` (`Idle`/`Asking`/`Answered(text, sources)`/`NoIndexedNotes`/`Error`)
  comunicados con texto. `NotesListScreen` suma la acción "Preguntar". `App` usa
  `rememberCoroutineScope()` (único patrón nuevo; sin `ViewModel`/Flow) para
  invocar los servicios `suspend`.

## Voice capture (STT)

- **Puerto** (`com.playbook.app.voice`, en `:composeApp/commonMain`):
  `VoiceTranscriber` con `start(onPartialResult, onFinalResult, onError, onStarted = {})`,
  `stop()` y `cancel()`, más `VoiceCaptureError`
  (`PERMISSION_DENIED`/`UNAVAILABLE`/`NO_MATCH`/`NETWORK`/`BUSY`/`UNKNOWN`) y
  `RecordedAudio(relativePath, byteSize, mimeType)`. `onFinalResult` recibe
  `(String, RecordedAudio?)`: el texto transcripto y el audio original ya cerrado
  (o `null` si no se pudo grabar), en un único callback terminal. `onStarted` se
  invoca **una vez por sesión** cuando el reconocimiento/grabación ya arrancó
  (permiso concedido), no durante el diálogo de permiso ni en errores de
  permiso/disponibilidad/arranque; `cancel()` lo descarta. Vive en
  `:composeApp` (no en `:core`): es una integración nativa que sólo consume la UI
  y necesita contexto de plataforma; `:core` no cambia.
- **Adaptadores por plataforma** (mismo patrón que `DatabaseDriverFactory`, **sin**
  `expect`/`actual`):
  - `AndroidVoiceTranscriber(ComponentActivity)` (`androidMain`): `SpeechRecognizer`
    + `RecognizerIntent` con resultados parciales; pide `RECORD_AUDIO` con
    `registerForActivityResult(RequestPermission())` en el constructor (debe
    construirse en `onCreate`). Mapea `onResults`/`onError` del
    `RecognitionListener` a los callbacks del puerto y recrea el recognizer por
    sesión. Graba el audio original con `MediaRecorder` (MIC, MPEG_4, AAC) a
    `filesDir/audio/<uuid>.m4a`; si no puede iniciar/escribir, entrega `null` y
    sigue la transcripción. Emite `onStarted` después de `startListening(...)`
    (permiso ya concedido y grabación lanzada). `cancel()` descarta el parcial y
    limpia `onStarted`.
  - `IosVoiceTranscriber()` (`iosMain`): `SFSpeechRecognizer` +
    `SFSpeechAudioBufferRecognitionRequest` + `AVAudioEngine`; pide
    `requestAuthorization` y `requestRecordPermission` y, como los callbacks de
    `Speech`/AVFoundation llegan en colas secundarias, los despacha al main queue
    (`dispatch_async(dispatch_get_main_queue())`). El tap del `inputNode` alimenta
    al reconocedor **y** escribe el audio original a un `AVAudioFile` AAC en
    `Application Support/audio/<uuid>.m4a`. Emite `onStarted` sólo si
    `engine.startAndReturnError(...)` devuelve `true`; si el engine no arranca,
    limpia y reporta `UNKNOWN` sin emitirlo. Un **guard de generación** (contador
    que `start()` captura y `cancel()` incrementa) invalida los callbacks tardíos
    de autorización/reconocimiento, de modo que una sesión cancelada no arranca
    el micrófono, no emite `onStarted` ni entrega resultados. `cancel()` además
    cancela la tarea, quita el tap, detiene el engine, descarta el parcial y
    desactiva la `AVAudioSession`.
- **Wiring sin DI:** `MainActivity` construye `AndroidVoiceTranscriber(this)` y
  `MainViewController` construye `IosVoiceTranscriber()`; ambos se pasan a
  `App(noteRepository, attachmentRepository, voiceTranscriber)`.
- **Permisos:** `AndroidManifest.xml` declara `android.permission.RECORD_AUDIO`;
  `iosApp/iosApp/Info.plist` declara `NSMicrophoneUsageDescription` y
  `NSSpeechRecognitionUsageDescription` (sin la segunda, iOS termina el proceso al
  primer uso). Sin permisos nuevos (grabar usa la misma concesión del STT).
- **Resultado:** el texto final no vacío se persiste como `body` de una Nota con
  `track = null`/`status = CAPTURED` y, si hay `RecordedAudio`, se persiste un
  Adjunto `AUDIO` (`audio/mp4`, ruta relativa) con el mismo
  `attachmentRepository.create(...)` que la imagen. Texto vacío → `NO_MATCH` y se
  borra el audio; Nota primero, Adjunto después (con limpieza si falla).
- **Dependencias:** ninguna nueva (`SpeechRecognizer`/`RecognizerIntent`/
  `MediaRecorder` son framework Android; `activity-compose` ya estaba;
  `Speech`/`AVFAudio`/`AVFoundation`/`CoreAudioTypes`/`dispatch` vienen de
  Kotlin/Native). No se agregan coroutines ni Flow.

## UI

- **`commonMain`** (`com.playbook.app`): `App(noteRepository: NoteRepository,
  gameProjectRepository: GameProjectRepository,
  attachmentRepository: AttachmentRepository, voiceTranscriber: VoiceTranscriber,
  noteIndexingOrchestrator: NoteIndexingOrchestrator, ragQueryService: RagQueryService,
  noteLinkRepository: NoteLinkRepository, semanticLinkingService: SemanticLinkingService)`
  es la raíz de la UI y dueña del estado. Hoistea `notes`, `projectState`
  (`ProjectLoadState`: `Loading`/`Missing`/`Loaded(project)`) y `attachments`
  (`mutableStateOf`), `destination`
  (`NotesDestination`:
  `List`/`Create`/`Edit(noteId)`/`CreateWithImage(image)`/`Gdd`/`Ask`/
  `Brainstorming`/`Related(noteId)`),
  `refreshKey`, `relatedNotes`, `dictation` (`DictationUiState`), `dictatedText`
  (texto final pendiente de anexar), `pendingAudio` (`RecordedAudio?`),
  `dictationJob`, `askState` (`AskUiState`), `readAloudState`
  (`ReadAloudUiState`) + player/request-id de lectura en voz alta, el estado de
  captura de imagen (`showImageSourceDialog`/`imageError`) y el estado del
  onboarding (`onboardingBusy`/`onboardingError`). Recibe `aiClient` y crea
  `BrainstormingSuggestionService` con `remember(aiClient)` para el wizard. La
  lectura
  ocurre en un `LaunchedEffect(refreshKey)`, **nunca** en el cuerpo de
  composición; tras cada mutación se incrementa `refreshKey` (`reload()`). Sin DI,
  sin `ViewModel`/`Lifecycle` y sin Flow ni librería de navegación; las llamadas
  `suspend` de IA se invocan con `rememberCoroutineScope()`. Obtiene el
  `ImagePicker` y el `AttachmentFileStore` por
  `remember`; la acción de imagen abre un `AlertDialog` "Añadir imagen"
  (Cámara/Galería/Cancelar), `onResult` navega a `CreateWithImage` y `onError`
  muestra un `AlertDialog` accionable.
- **Onboarding del Proyecto (`first-run-project-onboarding`):** `App` lee el
  singleton en el mismo `LaunchedEffect(refreshKey)` y lo representa con
  `ProjectLoadState` (`Loading` inicial; **no** vuelve a `Loading` en cada
  `reload()`). Con `Missing` renderiza `FirstRunOnboardingScreen` full-screen
  ("Iniciar Brainstorming"/"Saltar") y no la lista; con `Loaded` corre el
  `when(destination)` normal. Ambas acciones crean primero el singleton vacío
  (`GameProjectDraft(owner = LOCAL_OWNER_ID)`) con guard `onboardingBusy`;
  aceptar navega a `Brainstorming` y saltar a `List`. Un fallo registra
  `STORAGE`/`STORAGE_FAILED`, muestra un `AlertDialog` y no navega. No hay flag
  persistido: la existencia del Proyecto es la única señal, así que no se
  reofrece cuando ya existe.
- **Owner del MVP:** `LOCAL_OWNER_ID = "local"` (`private const val` en `App.kt`);
  la UI lo provee al construir el `NoteDraft` de creación. `:core` no cambia.
- **`NotesListScreen` (`main-screen-playbook-shell`)**: shell principal con
  `Scaffold(topBar = PlaybookTopBar, floatingActionButton = CaptureSpeedDial)`.
  `PlaybookTopBar` usa `TopAppBar` con “Playbook” y tres acciones
  (`GDD`/`✦`/`?` como fallback textual sin `material-icons`, cada una con un único
  `contentDescription`: “Ver GDD”, “Brainstorming”, “Preguntar”). El speed-dial
  alterna `+`/`×` y apila “Nota de texto”, “Galería” (`contentDescription`
  “Galería y generar imagen con Playbook”) y “Cámara”; un scrim dentro del contenido
  cierra al tocar y un `PlaybookBackHandler` (`expect`/`actual`; Android usa
  `androidx.activity.compose.BackHandler`, iOS no-op) cierra con Back. “Galería”
  abre el diálogo “Añadir imagen” existente (conserva Cámara/Galería/“Generar
  con Playbook”); “Cámara” lanza `ImageSource.CAMERA` directo. Estado vacío (“Todavía
  no hay notas”) con CTA “Crear primera nota” + “Añadir imagen”; y estado con
  notas (`LazyColumn` de `Card`s clickeables que llaman `onEdit(note.id)`).
  `App` cablea `onOpenImageSource`/`onCaptureCamera` y ya no pasa `onDictate`.
  Cada tarjeta muestra
  `body.ifBlank { "Imagen adjunta" }` (máx. 2 líneas, ellipsis), un chip textual
  "Imagen" si la nota tiene adjuntos (`attachmentCounts`), chips de `track.code`
  (o "Sin track") y un `StatusBadge` con la etiqueta legible del `status`, y —si
  `note.genres` no está vacía— una fila de
  chips de Género (`GenreLabelChip`, en `Row` con `horizontalScroll`). Si la
  Nota tiene un Adjunto `IMAGE`, la tarjeta suma su **miniatura**
  (`NoteThumbnail`) arriba del cuerpo; el resto de la tarjeta no cambia. Todo se
  comunica con texto, no sólo color (`DESIGN.md`).
- **Miniaturas de bocetos (`sketch-thumbnails`):** `App` deriva
  `imagePaths: Map<noteId, relativePath>` sólo de Adjuntos `IMAGE` (el primero
  por Nota en el orden determinista `created_at ASC, id ASC` de
  `AttachmentRepository.getAll()`) y `loadThumbnail: suspend (String) ->
  ImageBitmap? = { path -> fileStore.read(path)?.let(::decodeImageBitmap) }`.
  Ambos se pasan **además** de `attachmentKinds`/`attachmentCounts`;
  `NotesListScreen` y `GddScreen` los reenvían a sus tarjetas. `NoteThumbnail`
  carga con `produceState` (sólo al componerse, es decir, al ser visible) y
  decodifica en `Dispatchers.Default`; muestra `Image` de 72 dp con recorte
  `rounded` de 16 dp (`rounded.md` de `DESIGN.md`) y `ContentScale.Crop`. El
  espaciado inferior vive dentro de `NoteThumbnail`; si el archivo falta o no
  decodifica, no emite `Image` ni espaciado (sin hueco ni placeholder) y se
  conservan cuerpo/chips. Sin
  `ViewModel`/Flow/DI/ImageLoader, sin cacheo ni redimensionado (diferidos).
- **`BrainstormingWizardScreen`** (`brainstorming-wizard`): wizard de 9 pasos
  (Título, Género, Core Loop, Audiencia, Concepto, Inspiraciones, Fantasía,
  Mecánicas clave, Fuera de alcance) para crear/iterar la ficha del Proyecto.
  Todos los pasos son opcionales; `LinearProgressIndicator` + `Paso X/9`;
  "Atrás"/"Siguiente" y "Guardar" (explícito) o "Cancelar" (descarta). El estado
  local se inicializa desde `initialProject` y se re-inicializa con
  `updatedAt` como clave cuando `App` termina de cargar. El paso Género reutiliza
  `GenrePicker`; Inspiraciones/Fuera de alcance topan en 3 y Mecánicas en 5 (con
  categoría + descripción). "Guardar" construye un `GameProjectDraft` **completo**
  (nunca parcial) y llama `GameProjectRepository.save`; si falla, muestra un
  `AlertDialog` y no navega. `brainstorming-ai-suggestions` agrega un botón
  "Sugerir" por paso: pide 2 ideas a `BrainstormingSuggestionService`, las muestra
  como tarjetas efímeras y el usuario decide con Usar/Añadir o Ignorar; nunca
  auto-rellena ni guarda, y las sugerencias se descartan al cambiar de paso,
  cancelar o guardar. `brainstorming-note-per-step` agrega "Guardar como Nota"
  en cada paso: usa el mismo `buildDraft()` + el mapper puro, muestra un
  `AlertDialog` con preview/Track/Géneros, bloquea duplicados exactos por
  contenido (Géneros normalizados en ambos lados) y crea la Nota vía
  `onSaveNote` (`NoteRepository.create` + `indexBestEffort`), sin salir del
  wizard ni tocar la ficha. `Cancelar` del wizard conserva las Notas ya creadas.
- **`NoteEditorScreen`**: editor de texto para crear (`isEditing = false`) y editar
  (`isEditing = true`). `OutlinedTextField` multilínea para `body`; selector de
  `track` ("Sin track" + `Track.entries`) que comunica la selección con marca
  textual "✓", borde y color; campo "Nuevo género" + botón "Añadir" (aplica
  `normalizeGenres`) y chips de Género con control de quitar "×" (`clickable` con
  `onClickLabel`/`role = Role.Button`); `Guardar` deshabilitado si `body.isBlank()`
  **y** no hay `initialImage` (una nota sólo-imagen es válida); `Cancelar`; y en
  edición `Borrar` con `AlertDialog`. Cuando llega `initialImage` muestra la
  previsualización decodificada (`decodeImageBitmap`), "Quitar imagen" y el
  placeholder de fallo si no decodifica. En edición, si llega `onOpenRelated` no
  nulo, ofrece "Ver relacionadas" en una **fila propia** (en pantallas angostas no
  cabe junto a Borrar/Cancelar/Guardar y empujaría "Guardar" fuera del borde).
  En edición, si llega `initialStatus` (no null) muestra un `StatusBadge` de sólo
  lectura y —si `onReindex` no es null— el botón "Re-indexar", habilitado sólo si
  el estado no es `INDEXED` y el `body` del borrador no está en blanco; re-indexar
  **no** reescribe `track`/`genres`/`body` (sólo dispara el enriquecimiento).
  En edición, con Adjuntos `AUDIO`, muestra la sección **"Audio adjunto"**:
  lista determinista `Audio 1 · <tamaño>`, `RadioButton` cuando hay más de uno
  (default el primero), y `Transcribir`/`Transcribiendo…`/`Reintentar` con
  mensajes textuales para vacío, archivo ilegible y fallo de IA. El texto llega
  por `transcribedText` (job en `App`) y se aplica una sola vez: `body` vacío →
  directo + aviso "Transcripción aplicada. Guarda para conservarla."; `body` con
  texto → `AlertDialog` con vista previa y "Anexar" (misma semántica que
  `appendDictation`)/"Reemplazar" (sólo el texto)/"Cancelar" (sin cambios).
  `onTranscribedTextConsumed` limpia el texto de `App`; nunca hay auto-guardado.
  Entre el cuerpo/dictado y `Track` ofrece "Sugerir clasificación" (sólo con
  `body` no vacío y fuera de carga); la propuesta es estado local efímero
  (`Idle`/`Loading`/`Ready`/`Empty`/`Error`) con una tarjeta de `Track` + chips
  `GenreLabelChip`, y acciones Confirmar/Descartar/Reintentar. Confirmar aplica
  con `applyClassificationSuggestion` al borrador (sin guardar); cambiar a mano
  `body`/`track`/Géneros cancela el `Job`, ignora resultados tardíos y vuelve a
  `Idle`. `Guardar` nunca espera ni se bloquea por la IA.
  Mantiene el borrador local
  (`body`/`track`/`genres`) y emite
  `onSave(body, track, genres)`/`onDelete`/`onCancel`/`onRemoveImage`/`onOpenRelated`/`onReindex`/`onTranscribe`;
  la persistencia, el refresco y la navegación los maneja `App`. No hay control de
  Categoría/Tipo/Nivel: el `track` ya cubre la clasificación fija.
- **`EnrichmentStatus`** (`commonMain`, `note-enrichment-status-ui`):
  `statusLabel(NoteStatus)` (etiqueta en español, única fuente del texto mostrado
  al usuario) y `StatusBadge(status)`, un `Surface` con borde y texto que **nunca**
  comunica sólo con color; es el "Badge de Estado" de `DESIGN.md` y lo reutilizan
  la lista de Notas y el editor.
- **`Dictation` + panel inline (`inline-dictation-30s`)**: `Dictation.kt`
  (`commonMain`) define `DictationUiState` (`Idle`/`Listening(partial,
  elapsedMillis)`/`Processing`/`Error`), `DICTATION_MAX_MILLIS = 30_000`,
  `DICTATION_TICK_MILLIS = 100`, `VoiceCaptureError.message()` y
  `appendDictation(body, dictated)` (`trim` de ambos, omite vacíos y une con
  `\n`). El dictado **no** tiene pantalla propia: en **Nueva nota**
  (`Create`/`CreateWithImage`, nunca en edición) `NoteEditorScreen` muestra un
  panel después de "Cuerpo" y antes de "Track": "Dictar" (Idle), barra
  `LinearProgressIndicator` + "Escuchando… (Ns / 30s)" + parcial + "Detener"
  (Listening), acción deshabilitada "Transcribiendo…" (Processing) o mensaje
  textual + "Reintentar" (Error). El editor es tonto: recibe `dictationState`,
  `dictatedText` y callbacks; anexa el texto final una sola vez con
  `appendDictation` y avisa `onDictatedTextConsumed(text)` (limpia sólo si
  coincide). `canSave` además exige `dictatedText == null` para no guardar el
  audio sin su texto. El texto final se anexa al `body` previo sin duplicar
  espacios.
- **`AskNotesScreen`** (nuevo): pantalla "tonta" de consulta RAG. Recibe
  `AskUiState` (`Idle`/`Asking`/`Answered(text, sources)`/`NoIndexedNotes`/`Error`)
  y emite `onAsk(question)`/`onOpenSource(noteId)`/`onBack`. La acción primaria es
  "Preguntar" (deshabilitada con la pregunta en blanco o mientras `Asking`),
  muestra "Pensando…" durante la consulta, la **respuesta** (texto) en un `Card` y,
  si hay `sources`, la sección **"Fuentes"** con una cita numerada clickeable por
  Nota (abre `NotesDestination.Edit(noteId)`); un mensaje explícito si no hay Notas
  indexadas, y un error con "Reintentar"; "Volver" siempre vuelve a la lista. Todos
  los estados se comunican con texto (nunca sólo color).
- **Guardar respuesta Q&A como Nota (`save-answer-as-note`, 2026-10-20):** en
  `Answered`, debajo de "Respuesta" y antes de "Fuentes", aparece "Guardar como
  Nota" (`enabled = text.isNotBlank()`); en el resto de los estados no existe. La
  confirmación es un `AlertDialog` con preview truncado (5 líneas/200 caracteres +
  `…`), `Track: Sin track` y `Géneros: Ninguno`; `Cancelar`/dismiss no crea. La UI
  pre-chequea duplicados con `existingNotes` e `isDuplicateBrainstormingNote` y
  `App.saveQaAnswer(answerText)` repite el chequeo de forma autoritativa con
  `noteRepository.getAll()`: la Nota se crea con
  `NoteDraft(owner = LOCAL_OWNER_ID, body = answerText.trim(), track = null,
  genres = emptyList())`, `status` inicial `CAPTURED`, y sólo en el camino
  exitoso corre `indexBestEffort(created)` + `reload()`. No se persisten citas,
  fuentes, scores ni `RetrievedNote`; tras guardar se queda en Q&A con feedback
  inline `Nota guardada.` (sin navegación). Es el mismo patrón de UI tonta +
  callback que el wizard, reutilizando `NoteSaveResult`; no agrega modelos, tablas
  ni migraciones.
- **Orquestación de dictado (`App`):** el dictado vive **sólo** en el editor de
  Nueva nota (`main-screen-playbook-shell` retiró `onDictate` de la principal);
  el botón "Dictar" llama `startDictation()`. `startDictation()` cancela el job
  y la sesión previa (descarta su audio parcial), **conserva** el `pendingAudio`
  finalizado y deja `Listening("", 0)`; el timer de 30s **no** arranca al tocar
  "Dictar" sino en `onStarted`, con `currentTimeMillis()` como base y tick de
  100ms, acotado a `DICTATION_MAX_MILLIS`; al llegar al cap llama
  `stopDictation()` una sola vez. `stopDictation()` es idempotente: sólo desde
  `Listening` pasa a `Processing` y llama `voiceTranscriber.stop()`.
  `onPartialResult` actualiza el parcial preservando el elapsed; `onError` pasa
  a `Error` conservando el audio pendiente anterior; `onFinalResult(text,
  audio)` cancela el timer; si el texto queda vacío borra **sólo** el audio nuevo
  y fija `Error(NO_MATCH)` (conserva el pendiente anterior); si hay texto y
  `audio != null` borra el pendiente anterior y guarda el nuevo (si `audio ==
  null` conserva el anterior), fija `dictatedText = text.trim()` y `Idle`.
  `onStarted` que llega con `Processing` (el usuario detuvo antes) sólo llama
  `voiceTranscriber.stop()`; con `Idle`/`Error` se ignora (sesión cancelada).
  Guardar una Nueva nota (texto o texto+imagen) primero `cancelDictation()`,
  crea la Nota y luego `attachAudio(note.id)` (Adjunto `AUDIO` best-effort; si
  falla se registra `STORAGE` y se borra el archivo, siempre limpiando
  `pendingAudio`). Si la Nota no se crea, se descarta el audio pendiente y —con
  imagen— también el archivo recién guardado. "Cancelar" del editor ejecuta
  `cancelDictation()` + `discardPendingAudio()` y vuelve a la lista. Un
  `DisposableEffect(Unit)` **a nivel App** cancela el job, `cancel()` del
  transcriber y borra el audio pendiente al salir de composición; **no** hay
  `DisposableEffect` por destino, por lo que alternar imagen
  (`Create ↔ CreateWithImage`) no corta el dictado.
- **Transcripción de audio (`App`):** `App` recibe `transcribeClient`, construye
  `AudioAttachmentTranscriptionService` y posee `transcriptionState`,
  `transcribedText`, `transcriptionNoteId` y el `Job`. `onTranscribe(attachmentId)`
  sólo corre en `Edit`; el resultado se descarta si el destino ya no es esa Nota
  y un `LaunchedEffect(destination)` cancela el job y limpia el estado al cambiar
  de pantalla. El editor consume el texto una sola vez
  (`onTranscribedTextConsumed`) y el Guardar existente persiste + re-indexa. No
  crea Adjuntos ni reproduce audio.
- **Flujo de datos:** crear → `create(NoteDraft(LOCAL_OWNER_ID, body.trim(),
  track, genres = genres))` (status default `CAPTURED`); **crear con imagen** →
  `fileStore.saveImage(bytes, mime)` → `create(NoteDraft(...))` →
  `attachmentRepository.create(note.id, IMAGE, mime, relativePath, byteSize)` →
  `reload()`; si algo falla después de guardar el archivo, se borra (sin
  huérfanos). **Dictar inline** → el texto final se anexa al `body` del editor y
  el audio queda en `pendingAudio`; al Guardar → `create(NoteDraft(...))` →
  `attachAudio(note.id)` (`attachmentRepository.create(note.id, AUDIO,
  audio.mimeType, audio.relativePath, audio.byteSize)`) → `reload()` (misma
  limpieza ante fallo; cancelar borra el pendiente). Editar → `update(note.copy(body =
  body.trim(), track = ..., genres = genres))` (re-sella `updatedAt`, reemplaza el
  conjunto de Géneros y preserva `owner`/`createdAt`/`status`); borrar → se leen
  los adjuntos de la nota, `delete(id)` (limpia `note_genre` y las filas `attachment`
  en la misma transacción) y se borran los archivos con el `AttachmentFileStore`
  (cubre imágenes y audio por la ruta relativa); siempre `reload()` y vuelta a la
  lista. El `status` no se edita desde la UI. Si el `id` en `Edit(noteId)` ya no
  existe en `notes`, se vuelve a la lista. Tras crear o editar (texto/voz/imagen)
  con `body` no vacío se dispara el indexado best-effort (`indexBestEffort`), que
  llama `NoteIndexingOrchestrator.index` y —al resolver el `suspend`— `reload()`,
  de modo que el badge muestra el estado **real** (antes el `status` quedaba en
  `capturada`). Una Nota de sólo imagen no se indexa. El editor ofrece además el
  **re-indexado manual** (`reindex(note)`, mismo orquestador + `reload()`),
  habilitado sólo si el estado no es `INDEXED` y hay texto; no agenda nada ni
  detecta conectividad (`offline-pending-retry`). La consulta RAG llama
  `ragQueryService.ask(question)` y muestra el `Answered(text, sources)` con la
  respuesta y sus **citas** (tocar una cita abre la Nota fuente con `Edit`).
- **Deuda resuelta:** `notes-list-ui` leía la lista en el hilo de composición y no
  refrescaba tras mutaciones; `create-text-note` lo resolvió con `LaunchedEffect` +
  `refreshKey`. `note-category-and-tags` agregó la edición de Etiquetas (hoy
  Géneros, `genres-catalog-persistence`),
  `image-capture-camera` la captura/adjunto de imagen y `voice-attachment-storage`
  el audio del dictado como Adjunto `AUDIO` (chips por tipo en la lista), sólo al
  **crear** (sin edición de adjuntos existentes ni render/reproducción).
  `gdd-view` agregó la pantalla de GDD (ver abajo) y `rag-query` el indexado
  best-effort al guardar/editar y la pantalla de consulta.

## GDD

- **Proyección pura de Notas en `:core`** (`com.playbook.core.gdd`): `GddSection(track,
  notes)` y `buildGdd(notes): List<GddSection>`. Agrupa por `track` en orden fijo
  (`Track.entries`: `mecánicas`, `personajes`, `historia`) y añade una sección
  "Sin track" (`track = null`) al final **sólo** si hay notas sin `track`. Las
  tres secciones de `track` siempre están presentes (estructura estable); dentro
  de cada sección se conserva el orden de entrada (`getAll()`:
  `updated_at DESC, id DESC`). No muta la lista ni duplica notas. No depende de
  SQLDelight ni de `AiClient`: el GDD **no es una entidad persistida**, es una
  vista agregada sobre las Notas (`docs/domain-model.md`), sin tabla, query ni
  migración propias.
- **Documento híbrido (`gdd-document-hybrid`):** `buildGddDocument(project,
  notes): GddDocument` vive en `:core` y es puro/determinista. Arriba proyecta la
  ficha del `GameProject` (`GddProjectBlock`) con los campos actuales en orden
  fijo (`Géneros`, `Core Loop`, `Audiencia`, `Concepto`, `Inspiraciones`,
  `Fantasía`, `Mecánicas clave`, `Fuera de alcance`), omitiendo vacíos y
  normalizando listas con `trim` (mecánicas como `Categoría · descripción` en
  líneas). El `title` queda en la cabecera y **no** se repite como campo;
  `project == null` produce `project = null`. Debajo mapea `buildGdd(notes)` a
  `GddDocumentSection` con títulos `mecánicas`/`personajes`/`historia`/`Sin
  track`, conservando orden, agrupación y Notas. Historia/Lore y Personajes son
  las Notas de `Track.STORY`/`Track.CHARACTERS` (no hay campos fabricados).
- **Pantalla `GddScreen`** (`:composeApp/commonMain`): render fino sobre
  `buildGddDocument(project, notes)` con `remember(project, notes)`. Cabecera
  **fija** `GDD — <título>` (o "GDD") + total de Notas y los botones "Volver" y
  **"Compartir"** (habilitado con Notas o con ficha con título/campos; deshabilitado
  sólo si no hay contenido exportable). Debajo, un `LazyColumn`
  con la ficha en una `Card` destacada (encabezado "Proyecto", label
  `labelMedium` arriba y valor `bodyMedium` debajo, multilínea) y las secciones
  de Notas, que scrollean juntas. Cada sección muestra título/conteo ("N
  notas"/"1 nota") y "Sin notas en este track" cuando está vacía; las Notas
  conservan cuerpo a 2 líneas, chips de `status`/Géneros/"Imagen", miniatura
  (`NoteThumbnail`, misma degradación tolerante que la lista) y apertura de la
  Nota. Todo se comunica con texto, no sólo color (`DESIGN.md`).
- **Estados vacíos:** `project == null` → "Todavía no hay Proyecto" +
  "Complétalo desde Brainstorming." (sin CTA) y las Notas siguen visibles;
  Proyecto sin datos → "Proyecto sin datos adicionales"; `notes.isEmpty()` →
  ficha (si existe) + "Todavía no hay notas", sin secciones de Track vacías. Sin
  Proyecto ni Notas ambos placeholders conviven sin crash. `App` pasa `project`
  (ya cargado por `refreshKey`); no hay estado, repositorios ni navegación
  nuevos.
- **Navegación (sin librería):** `NotesListScreen` "Ver GDD" →
  `NotesDestination.Gdd`; tocar una nota → `NotesDestination.Edit(noteId)` (mismo
  editor que desde la lista); "Volver" desde el GDD → `List`. **Limitación
  aceptada:** al guardar/cancelar en el editor se vuelve a la lista, no al GDD (un
  `previousDestination` llegará con una feature de navegación/detalle).
- **Refresco:** `GddScreen` recibe `notes`/`attachmentCounts`/`imagePaths` y
  `loadThumbnail` ya hoisteados por `App` (recargados por `refreshKey`); no lee la
  base dentro del cuerpo de composición. No hay DI/`ViewModel`/Flow ni
  esquema/dependencias nuevos.
- **Export híbrido a Markdown (`gdd-export-hybrid`; proyección pura en `:core`):**
  `com.playbook.core.gdd.buildGddMarkdown(project, notes, now): String` reusa
  `buildGddDocument`/`buildGdd` y **omite** secciones vacías sin duplicar Notas.
  El overload `buildGddMarkdown(notes, now)` delega con `project = null` y queda
  byte-idéntico al contrato de `gdd-export`. Documento determinista: cabecera
  `# GDD — <título>` (título escapado) o `# GDD — Playbook`, `Total: N notas`
  (singular `Total: 1 nota`) y, si la ficha tiene campos, `## Proyecto` con
  `- **<Label>:** <valor escapado>` en el orden de `GddDocument` y mecánicas como
  sub-viñetas `  - Categoría · descripción`. Debajo van las secciones
  `## <title>` en orden fijo, por Nota `### <título>` (primera línea del `body`
  trim, escapada; fallback `Nota <now>` con `body` vacío), el **resto** del
  `body` (escapado, sin repetir la primera línea) y `Géneros: a, b — Estado:
  <status.code>` (Géneros escapados, `status.code` no). Los valores de la ficha
  se colapsan a una línea y se escapan con el set
  `` \ ` * _ { } [ ] ( ) # + - . ! | > ``; labels/prefijos generados no se
  escapan. Bloques separados por una línea en blanco y documento terminado en
  `\n`. `now` es un `String` inyectado (lo formatea la plataforma) para que
  `:core` no dependa de APIs de fecha. Sin tabla, query, migración ni
  dependencias.
- **Puerto de share `GddSharer` (`:composeApp`, expect/actual):** `interface
  GddSharer { fun share(markdown: String): Boolean }` +
  `@Composable expect fun rememberGddSharer()` y
  `expect fun currentTimestampLabel()`. Android usa `Intent(ACTION_SEND)` con
  `text/plain` + `EXTRA_TEXT` y `Intent.createChooser` (devuelve `false` si no hay
  app); iOS presenta un `UIActivityViewController` desde el `topViewController()`
  compartido (`IosViewControllers.kt`, reusado por `ImageCapture.ios.kt`).
  `currentTimestampLabel()` es `SimpleDateFormat("yyyy-MM-dd HH:mm")` en Android y
  `NSDateFormatter` en iOS. Todo **en memoria**: sin archivos, `FileProvider`,
  permisos, persistencia ni backend. "Compartir" construye el Markdown, llama
  `share` y muestra un **snackbar** ("Listo para compartir"/"No disponible") con un
  `Scaffold`/`SnackbarHostState` local a la pantalla. `GddScreen` invoca el export
  híbrido con `project`/`notes`; `App` sólo pasa `project` y el mecanismo de share
  no cambia.

## Enlaces (notas relacionadas)

- **Proyección pura en `:core`** (`com.playbook.core.linking`): `RelatedNote(note,
  score)` y `buildRelatedNotes(noteId, links, notes): List<RelatedNote>`. Resuelve
  el otro extremo del enlace con `NoteLink.otherNoteId(noteId)` (el enlace es no
  dirigido: sirve abrirlo desde cualquiera de los dos lados), lo busca en `notes`
  (**omite** si falta), nunca devuelve la propia Nota, y ordena por `score` **desc**
  con desempate por `note.id` **asc** (determinista). Sin enlaces → lista vacía. No
  muta entradas; no depende de SQLDelight ni de `AiClient`.
- **Pantalla `RelatedNotesScreen`** (`:composeApp/commonMain`): render fino sobre
  `RelatedNote`. Encabezado "Notas relacionadas", el `body` de la Nota de origen
  (máx. 2 líneas, ellipsis) y "Volver"; estado vacío explícito ("No hay notas
  relacionadas" + línea de apoyo); con resultados, un `LazyColumn` de tarjetas
  (`key = { it.note.id }`) con el `body` de la relacionada (máx. 2 líneas), el
  `score` como **texto** ("Similitud 0.92", formateado sin depender del locale) y
  chips de `track`/`status`/Géneros; cada tarjeta es `clickable(onClickLabel =
  "Abrir nota", role = Role.Button)`. Todo se comunica con texto, no sólo color.
- **Navegación:** en `NoteEditorScreen` (modo edición) "Ver relacionadas" →
  `NotesDestination.Related(noteId)`; tocar una tarjeta → `NotesDestination.Edit(noteId)`;
  "Volver" → `List`. Si el `id` de origen ya no existe, se vuelve a la lista. No hay
  librería de navegación.
- **Trigger mínimo (recomputo best-effort al abrir):** al entrar a
  `NotesDestination.Related(noteId)`, un `LaunchedEffect(noteId, refreshKey)` corre
  `runCatching { semanticLinkingService.recomputeAll() }` (materializa los enlaces
  desde los embeddings actuales) y luego proyecta con
  `buildRelatedNotes(noteId, noteLinkRepository.getLinksForNote(noteId), notes)`.
  Un fallo del recomputo **se ignora** (no propaga ni bloquea la navegación) y se
  muestra lo persistido (o el estado vacío). Es el **único** disparador del
  recomputo; el trigger de ciclo de vida (al indexar/editar, offline) sigue como
  follow-up. `recomputeAll()` es síncrono y O(n²) sobre los embeddings: corre en el
  dispatcher principal dentro del `LaunchedEffect`, aceptable para una biblioteca
  personal.
- **Sin cambios de esquema:** sigue **v5** (sin `.sqm` nuevo), sin dependencias
  nuevas. `NoteLinkRepository`/`SemanticLinkingService` se construyen en los
  composition roots y se inyectan por constructor (sin DI).

## Observability

- **Puerto `Diagnostics`** (`com.playbook.core.diagnostics`): única superficie de
  observabilidad del MVP. `recordFailure(source, summary, detail?)` es
  **best-effort**: **nunca** lanza ni bloquea al llamador (una implementación que
  falle al escribir se traga el error). No es un logger general: sólo registra
  **fallos**, no caminos de éxito. Sin telemetría remota ni librería de logging.
- **Tipos:** `DiagnosticSource` (`AI_INDEXING`, `AI_QUERY`, `AI_TRANSCRIBE`,
  `VOICE`, `IMAGE`, `STORAGE`) y `DiagnosticEntry(timestampMillis, source,
  summary, detail?)`.
  `summary` es un **resumen estable** (sin datos del usuario).
- **Implementaciones:** `NoOpDiagnostics` (**default inyectable**, no rompe a los
  callers que no cablean sink), `InMemoryDiagnostics(maxEntries = 100, clock)`
  (tests/sink efímero; `snapshot()`/`clear()`; retiene las últimas `maxEntries`) y
  `FileDiagnostics(storage, clock, maxEntries = 200)` (sink local persistente).
- **Sink local acotado:** `DiagnosticStorage` (interfaz) + `expect fun
  diagnosticStorage(path)` con `actual` Android/JVM (`java.io.File`) e iOS
  (`NSFileManager`/`NSString`, con append = read + overwrite). Cada línea es
  `<timestampMillis>\t<source>\t<summary>\t<detail|->` (`detail` recortado a ~200
  chars); la retención es **por cantidad** (al superar `maxEntries` reescribe con
  las últimas). `rawLog()` expone el contenido crudo para diagnóstico.
- **Cableado:** los composition roots construyen
  `FileDiagnostics(diagnosticStorage("<private>/diagnostics.log"))`
  (`filesDir/diagnostics.log` en Android, `Application Support/diagnostics.log` en
  iOS) y lo inyectan en `NoteIndexingService`/`RagQueryService` y `App(...)`.
  `:core` usa `NoOpDiagnostics` por defecto (retrocompatible).
- **Instrumentación:** `NoteIndexingService` registra `AI_INDEXING` en cada
  `Failed` (excepción del runtime o respuesta inválida); `RagQueryService` registra
  `AI_QUERY` en cada `Failed` (`embed`/`generate`/respuesta inválida);
  `NoIndexedNotes` **no** registra. `App` registra `VOICE`/`IMAGE` (los `onError`
  de las integraciones nativas, con el nombre del error como `detail`) y `STORAGE`
  (los `catch (Throwable)` de persistencia de adjuntos, sin `detail`).
  `AudioAttachmentTranscriptionService` registra `AI_TRANSCRIBE` con el resumen
  estable `transcription_failed` ante `AiClientException`, **sin `detail`**, para
  no filtrar texto, bytes, rutas ni ids; un fallo inesperado se registra igual
  desde `App` sin `detail`.
- **Privacidad (durable):** el log sólo guarda fuente, resumen estable y mensaje
  de excepción recortado. **Nunca** `body`, preguntas, rutas de adjuntos, IDs ni
  secrets (ver `CONSTRAINTS.md`). El `detail` de `STORAGE` se omite a propósito
  porque las excepciones de IO/SQL pueden contener rutas.
- **Sin esquema ni deps:** el log es infra, no modelo de dominio (no hay entidad
  `Log`, ni tabla, ni `.sqm`): no cambia el esquema, sin dependencias nuevas.

## Runtime surfaces

- **Android:** `MainActivity` (`com.playbook.app`) aplica el esquema local v7 con
  `createDatabase(AndroidDatabaseDriverFactory(...))`, construye
  `SqlDelightNoteRepository` + `SqlDelightAttachmentRepository` +
  `SqlDelightEmbeddingRepository` + `SqlDelightNoteLinkRepository`, un
  `FakeAiClient`, un `FileDiagnostics` sobre `filesDir/diagnostics.log`,
  `NoteIndexingService`, `NoteIndexingOrchestrator`, `PendingIndexingRetry`,
  `RagQueryService` y `SemanticLinkingService`, y monta
  `App(..., AndroidVoiceTranscriber(this), noteLinkRepository,
  semanticLinkingService, aiClient, aiRuntime.transcribeClient, diagnostics)`.
- **iOS:** `MainViewController()` (Kotlin, `composeApp/src/iosMain`) crea la base
  local (singletons perezosos), construye los cuatro repositorios, el
  `FakeAiClient`, un `FileDiagnostics` sobre `Application Support/diagnostics.log`,
  `NoteIndexingService`, `NoteIndexingOrchestrator`, `PendingIndexingRetry`,
  `RagQueryService` y
  `SemanticLinkingService`, y envuelve
  `App(notes, attachments, transcriber, orchestrator, retry, rag, links, linking,
  ai, runtime.transcribeClient, diag)`; lo llama `ComposeView` (SwiftUI) en
  `iosApp`. iOS sigue congelado y usa `fakeAiRuntime()`.

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
- Edición de adjuntos de una Nota existente, reproducción del audio y render de la
  imagen/audio en la lista o detalle (hoy sólo chips de texto "Imagen"/"Audio" +
  previsualización de la imagen al crear).
- Enlaces: la generación (`semantic-linking`) y su **UI** de lectura
  (`note-links-ui`: `RelatedNotesScreen`) ya existen; el recomputo es
  **best-effort al abrir "Notas relacionadas"**. Queda pendiente el **trigger de
  ciclo de vida** (al indexar/editar, offline) y el recomputo incremental.
  `rag-query` expone `cosineSimilarity` y hace retrieval **efímero** (no crea
  filas `link` ni define umbral); `rag-answer-citations` renderiza esas fuentes
  como citas y navega a la Nota, sin persistir el enlace. El estado de indexado ya
  es visible (badge textual) y re-indexable a mano (`note-enrichment-status-ui`);
  el reintento automático/`FAILED` (`offline-pending-retry`) sigue pendiente. El
  proveedor/modelo/dimensión y la gestión de keys son follow-ups del ADR 0001
  (`docs/adr/0001-ai-runtime-decision.md`).
- Adaptador on-device concreto (fase 2 de la estrategia híbrida).
- Reproducción del audio original del dictado (playback/controles/waveform); el
  Adjunto `AUDIO` ya se persiste y es visible (`voice-attachment-storage`).
- Detalle de nota de sólo lectura (posterior). La vista de GDD (`gdd-view`) y la
  de Notas relacionadas (`note-links-ui`) ya están implementadas; volver al GDD (o
  a las relacionadas) tras editar desde allí queda fuera de alcance (ver secciones
  GDD y Enlaces).
- Manejo del back físico Android/iOS en el editor y en la captura de voz (hoy sólo
  "Cancelar").
- Observabilidad: UI/pantalla de diagnóstico, serialización/export del log local y
  telemetría remota/crash reporting quedan **fuera del MVP** (el puerto
  `Diagnostics` sólo registra fallos localmente; ver "Observability").
- Formalización de `DESIGN.md` (sigue `provisional`; sin entrega de UI/UX).
