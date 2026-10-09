# Feature Implementation Spec: Captura de foto de boceto (cámara/galería)

## Source Feature

- `id`: `image-capture-camera`
- `area`: `capture-image`
- `depends_on`: `["create-text-note"]` (ya `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Habilitar la **captura de una foto de boceto** (cámara o galería) que crea una
**Nota con esa imagen como Adjunto**, integrando APIs nativas en Android e iOS
desde la UI compartida de Compose Multiplatform. Es el tercer modo de captura del
MVP (`docs/build-brief.md`, slice 3) y la primera feature que introduce el
concepto de **Adjunto** en persistencia y UI.

La feature agrega un modelo `Attachment` y su repositorio en `:core`, **la primera
migración de esquema del repo** (v1 → v2: nueva tabla `attachment`), la integración
nativa de cámara/galería con manejo de permisos, y un flujo de captura que abre el
editor existente con una previsualización de la imagen y guarda la nota con el
adjunto. Conserva la arquitectura vigente: repositorios síncronos pasados a
`App(...)` sin DI, sin `ViewModel`/Flow y sin librería de navegación.

## Non-Goals

- **Editar adjuntos de una nota existente** (agregar/quitar/reemplazar imagen en
  modo edición). Esta feature sólo adjunta una imagen al **crear** desde captura.
- Adjuntos de audio (`voice-attachment-storage`), transcripción/OCR
  (`image-ocr-optional`), texto derivado del Adjunto.
- Renderizar la imagen en la tarjeta de la lista o una pantalla de detalle/visor;
  en la lista sólo se muestra un chip textual "Imagen". El render completo llega
  con una feature de detalle/visor.
- Compresión, redimensionado, corrección de orientación EXIF, miniaturas
  cacheadas o multi-adjunto por nota desde la UI (la tabla es 1—N, pero la UI crea
  un adjunto por captura).
- IA/embeddings/enlaces/GDD/RAG; estados de enriquecimiento (`NoteStatus` no se
  edita).
- Bibliotecas de carga de imágenes (p. ej. Coil), DI, `ViewModel`/`Lifecycle`,
  coroutines fuera de Compose, Flow o librería de navegación.
- Formalizar `DESIGN.md` (sigue `status: provisional`).
- Formato de audio/otros `AttachmentKind`: sólo `IMAGE` en esta feature.

## Job Story

When tengo un boceto en papel o una referencia visual en la cabeza,
I want fotografiarlo o elegir la imagen y que quede guardada como Adjunto de una
Nota,
so I can conservar la idea visual junto al resto de mi GDD sin volver a dibujarla.

## Users And Permissions

- Autor/usuario único del MVP (local-first): todas las notas y archivos del
  dispositivo. Sin auth.
- **Cámara (Android):** se declara `android.permission.CAMERA` y se solicita en
  runtime antes de abrir la cámara; si se deniega, se informa y no se abre.
- **Galería (Android):** se usa el **photo picker** del sistema
  (`ActivityResultContracts.PickVisualMedia`), que **no requiere permiso** de
  almacenamiento (compatible con scoped storage / targetSdk 36).
- **Galería (iOS):** se usa `PHPickerViewController`, que **no requiere permiso**
  de fototeca. **Cámara (iOS):** requiere `NSCameraUsageDescription` en
  `Info.plist`; si el usuario deniega, se informa y no se abre.
- El `owner` de la Nota sigue siendo `LOCAL_OWNER_ID = "local"` (UI).

## Acceptance Scenarios

### Scenario 1: Capturar desde la cámara y crear la nota

Given la app abierta en la lista y el permiso de cámara otorgado (Android) o
`NSCameraUsageDescription` presente (iOS),
When el usuario elige la fuente "Cámara", captura una foto y confirma en el editor,
Then se crea una Nota con `body` vacío permitido, `status = capturada` y el
`track` elegido (o "Sin track"), se guarda el archivo en almacenamiento privado de
la app, se persiste una fila `attachment` que la referencia, la lista se refresca y
la tarjeta muestra el chip "Imagen".

### Scenario 2: Elegir una imagen de la galería

Given la app abierta en la lista,
When el usuario elige la fuente "Galería" y selecciona una imagen (sin pedir
permisos de almacenamiento/fototeca),
Then se sigue el mismo flujo y resultado que Scenario 1 con la imagen elegida.

### Scenario 3: Previsualización y guardado con imagen

Given una imagen recién capturada/elegida en memoria (todavía no persistida),
When se abre el editor de creación con la imagen,
Then se muestra una **previsualización** de la imagen, se ofrece "Quitar imagen", y
"Guardar" está habilitado aunque el `body` esté vacío; al guardar se persiste la
Nota y el Adjunto. "Cancelar" descarta la imagen sin crear fila ni archivo.

### Scenario 4: Permiso denegado o fuente no disponible

Given que el usuario deniega el permiso de cámara (Android/iOS) o la cámara no está
disponible (p. ej. simulador iOS),
When se intenta capturar desde la cámara,
Then no se abre la fuente, se muestra un mensaje de error accionable, no se crea
ninguna fila ni archivo, y la app no crashea.

### Scenario 5: Persistencia y metadatos del Adjunto

Given una Nota con Adjunto creada desde la UI,
When se cierra y reabre la app,
Then la Nota y su fila `attachment` persisten, la tarjeta sigue mostrando el chip
"Imagen", y la ruta guardada es **relativa** al contenedor de la app (no una ruta
absoluta), de modo que sobrevive a reinstalaciones/actualizaciones de iOS.

### Scenario 6: Borrar la Nota borra el Adjunto

Given una Nota con Adjunto,
When se borra la Nota desde la UI (con confirmación),
Then se eliminan las filas `attachment` de esa nota (cascade de la base) y los
archivos asociados del almacenamiento privado; no quedan huérfanos.

### Scenario 7: Migración v1 → v2 sin pérdida de datos

Given una base v1 existente (tabla `note` con filas, `user_version = 1`),
When se abre la app con el esquema v2 (SQLDelight ejecuta `1.sqm`),
Then las notas existentes se conservan intactas y la tabla `attachment` queda
disponible; `PlaybookDatabase.Schema.version == 2`.

### Scenario 8: Gate honesto

Given la feature implementada,
When se ejecuta `./init.sh`,
Then sigue terminando en 0 sin dev servers ni simuladores; los tests del core
(Android/JVM e iOS) corren verdes incluidos los de adjuntos y migración, la app
Android compila y el framework iOS enlaza.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `create-text-note` `accepted`
  (Sessions 011–013); próxima feature `image-capture-camera` sin spec.
- `feature_list.json` — metadata de `image-capture-camera` (verificación
  provisional: "captura real en Android e iOS que produce una nota con Adjunto de
  imagen"; nota: "Integración nativa. Manejo de permisos de cámara/galería.").
- `CONTEXT.md`, `docs/domain-model.md` — `Nota` 1—N `Adjunto`; `Adjunto` = archivo
  original (imagen de boceto); el texto derivado (OCR) vive en la Nota; estados del
  Adjunto `subido → procesado | fallido`; edge case "Adjunto sin texto".
- `docs/build-brief.md` — slice 3: "Captura por imagen de boceto (cámara) → Nota
  con Adjunto"; la captura no debe bloquearse.
- `docs/technical-discovery.md` — local-first; SQLDelight única fuente de verdad;
  "Cámara / galería: captura de bocetos; el OCR (si existe) es texto derivado";
  duplicación Android+iOS de permisos/cámara.
- `docs/risks-and-open-questions.md` — "Modelo de STT y cámara por plataforma, más
  manejo de permisos" como pregunta de implementación.
- `ARCHITECTURE.md` — `:composeApp → :core`; dominio/repositorio en `:core`; UI y
  wiring en `:composeApp`; esquema v1 (`note`); "Deferred: adjuntos".
- `docs/specs/create-text-note.md` — contrato precedente: `App(noteRepository)`,
  `NotesDestination` (`List`/`Create`/`Edit`), `NOTE`/`refreshKey`, editor con
  `Guardar` deshabilitado con `body` vacío, borrado con `AlertDialog`, smoke iOS
  limitado por falta de input automation.
- `docs/specs/notes-list-ui.md`, `docs/specs/note-model-crud-core.md`,
  `docs/specs/local-persistence-sqldelight.md` — patrones de repositorio, tests
  `commonTest` + `@Test` por plataforma, `Note.sq`, esquema v1 y regla
  "`schema.version` sigue en 1, sin `.sqm`" (esta feature la cambia).
- `composeApp/src/commonMain/.../{App,NotesListScreen,NoteEditorScreen}.kt` — estado
  de lista con `LaunchedEffect(refreshKey)`, navegación local, FAB "Nueva nota",
  CTA de estado vacío, tarjetas clickeables, editor con preview de `track` y
  validación de `body`.
- `composeApp/src/androidMain/.../MainActivity.kt` — `createDatabase(...)` +
  `SqlDelightNoteRepository(database, ::randomNoteId, ::currentTimeMillis)` →
  `App(...)`.
- `composeApp/src/iosMain/.../MainViewController.kt` — ídem iOS (singletons a
  nivel módulo).
- `composeApp/src/androidMain/AndroidManifest.xml` — sin permisos ni `provider`;
  `theme` mínimo. `composeApp/src/androidMain/res/values/themes.xml` — único res.
- `composeApp/src/androidMain/.../db/AndroidDatabaseDriverFactory.kt`,
  `core/.../db/IosDatabaseDriverFactory.kt`, `core/.../db/DatabaseDriverFactory.kt` —
  drivers y `createDatabase(...)` con `PRAGMA user_version`/`executeQuery`.
- `core/src/commonMain/sqldelight/.../Note.sq` — tabla `note` + queries; sin
  `attachment`.
- `core/.../model/{Note,Track,NoteStatus,NoteMappers}.kt`,
  `core/.../repository/{NoteRepository,SqlDelightNoteRepository}.kt` — modelo y
  CRUD síncronos, mapper con alias `Note as NoteRow`.
- `core/.../platform/PlatformDefaults*.kt` — `randomNoteId()`/`currentTimeMillis()`
  `expect`/`actual`.
- `core/src/commonTest/.../repository/NoteRepositoryCheck.kt` — `verifyNoteCrud`
  (incluye orden `updated_at DESC, id DESC`).
- `core/build.gradle.kts` — SQLDelight sin carpeta de migraciones; `commonTest`
  (`kotlin-test`, `coroutines-test`), `androidUnitTest` (`sqlite-driver`).
- `composeApp/build.gradle.kts` — Compose MP + `:core` +
  `androidx.activity:activity-compose`; sin source set de test.
- `iosApp/iosApp/Info.plist` — sin claves de privacidad; `ContentView.swift`
  host SwiftUI; deployment target iOS 15.3 (`project.pbxproj`).
- `gradle/libs.versions.toml` — Kotlin 2.1.21, CMP 1.8.2, AGP 8.10.1, SQLDelight
  2.1.0, `androidx-activity` 1.10.1.
- `init.sh` — gate no bloqueante (assembleDebug + tests core Android/JVM e iOS +
  link framework iOS), sin dev servers ni simuladores.
- `DESIGN.md` — "Captura de Imagen: cámara / galería"; "Acción de Captura";
  regla "estados no sólo por color"; tokens provisionales.

### Existing Patterns To Follow

- **UI → core**: la UI consume repositorios; la lógica de dominio no se mueve a
  `:composeApp`.
- **Sin DI**: las dependencias se pasan como parámetros (`App(repo, attachmentRepo)`).
- **Tests de datos en `:core`**: helper en `commonTest` sobre un `SqlDriver` +
  `@Test` por plataforma con drivers in-memory; la UI/nativo se verifica con smoke
  manual.
- **`expect`/`actual`** sólo para plataforma (`PlatformDefaults`, `DatabaseDriverFactory`);
  no se usa `expect/actual` en source sets de test.
- **Códigos canónicos en español** para valores persistidos; identificadores Kotlin
  en inglés.
- **SQLDelight**: `.sq` current schema + `.sqm` de migración; versión derivada de
  las migraciones.
- `init.sh` no bloqueante, sin dev servers ni simuladores.
- Documentos en español, términos técnicos en inglés.

### Current Gaps

- No existe el concepto de Adjunto en `:core` (ni modelo, ni tabla, ni repositorio).
- **Nunca se hizo una migración de esquema**: no hay carpeta/archivo `.sqm`, ni
  criterio documentado de versionado v1 → v2.
- No hay integración nativa de cámara/galería, ni permisos en el manifest/plist, ni
  `FileProvider`/photo picker en Android, ni pickers en iOS.
- No hay almacenamiento de archivos de imagen ni abstracción de filesystem.
- La lista no distingue notas con adjunto; el editor no previsualiza imágenes ni
  permite guardar sin `body`.
- `:composeApp` no tiene source set de test ni harness de UI/nativo.

## Technical Approach

### Modelo y persistencia del Adjunto (primera migración de esquema)

El `Adjunto` se modela como entidad hija de `Nota` (1—N). **No** se guardan los
bytes en la base: se guarda el archivo en almacenamiento privado de la app y la
base sólo conserva **metadatos + ruta relativa**. Decisión explícita:

- Guardar imágenes como BLOB en SQLite es riesgoso en Android: el `CursorWindow`
  del framework limita la fila (~2 MB) y una foto puede superarlo, lanzando
  `SQLiteBlobTooBigException`. Además, cargar BLOBs en la lista encarece cada
  lectura. Los archivos en disco evitan ambos problemas y son el enfoque estándar
  mobile.
- La ruta se guarda **relativa** al contenedor de la app (p. ej. `images/<uuid>.jpg`)
  porque en iOS el path absoluto del sandbox cambia entre instalaciones/updates.

Esquema v2 (nuevo `Attachment.sq`):

```sql
CREATE TABLE attachment (
  id TEXT NOT NULL PRIMARY KEY,
  note_id TEXT NOT NULL,
  kind TEXT NOT NULL,          -- sólo "imagen" en esta feature
  mime_type TEXT NOT NULL,
  file_path TEXT NOT NULL,     -- ruta relativa dentro del contenedor de la app
  byte_size INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX attachment_note_id ON attachment (note_id);
```

**Migración v1 → v2 (primera del repo):** crear
`core/src/commonMain/sqldelight/com/playbook/core/db/1.sqm` con el `CREATE TABLE
attachment` + el índice. SQLDelight deriva `Schema.version = 2` de la migración
más alta (la `1.sqm`) y los drivers existentes (`AndroidSqliteDriver`,
`NativeSqliteDriver`) aplican `create`/`migrate` automáticamente al abrir la
conexión; `createDatabase(...)` sigue forzando la apertura con el `PRAGMA`. No se
toca `Note.sq` salvo si se decide mover el índice (no necesario).

- `AttachmentKind` (enum) con `code`; sólo `IMAGE` → `"imagen"`. `fromCode`
  fail-fast (columna `NOT NULL`, sólo la escribe este código), consistente con
  `NoteStatus`. `AUDIO` llegará con `voice-attachment-storage` sin cambiar el
  esquema (es un valor de la misma columna).
- `Attachment` de dominio: `id`, `noteId`, `kind`, `mimeType`, `filePath`,
  `byteSize`, `createdAt`. Mapper con alias `Attachment as AttachmentRow`.

### Repositorios en `:core`

```kotlin
interface AttachmentRepository {
    fun create(noteId: String, kind: AttachmentKind, mimeType: String,
               filePath: String, byteSize: Long): Attachment
    fun getByNoteId(noteId: String): List<Attachment>
    fun getAll(): List<Attachment>          // metadatos, sin bytes
    fun deleteByNoteId(noteId: String): Boolean
}
```

`SqlDelightAttachmentRepository(database, idFactory, clock)` reutiliza los defaults
`::randomNoteId`/`::currentTimeMillis` (el id es un UUID genérico). Queries en
`Attachment.sq`: `insertAttachment`, `selectAttachmentsByNoteId`,
`selectAllAttachments`, `deleteAttachmentsByNoteId`.

**Cascade en el borrado de la Nota:** `SqlDelightNoteRepository.delete(id)` pasa a
ejecutar, dentro de `database.transaction { }`, primero
`deleteAttachmentsByNoteId(id)` y luego `deleteNoteById(id)`, devolviendo
`rowsAffected > 0` de la nota. Así no quedan filas huérfanas y el comportamiento
queda cubierto por tests del core. La semántica pública (`delete` devuelve si existía)
no cambia.

### Almacenamiento de archivos y pickers nativos (`:composeApp`)

Se declaran en `commonMain` (nuevo `ImageCapture.kt`) y se implementan por
plataforma con `expect`/`actual`:

```kotlin
enum class ImageSource { CAMERA, GALLERY }
class PickedImage(val bytes: ByteArray, val mimeType: String)
enum class ImageCaptureError { PERMISSION_DENIED, CAMERA_UNAVAILABLE, READ_FAILED, UNKNOWN }

interface ImagePicker { fun launch(source: ImageSource) }
@Composable expect fun rememberImagePicker(
    onResult: (PickedImage) -> Unit,
    onError: (ImageCaptureError) -> Unit,
): ImagePicker

data class StoredImage(val relativePath: String, val byteSize: Long)
interface AttachmentFileStore {
    fun saveImage(bytes: ByteArray, mimeType: String): StoredImage
    fun delete(relativePath: String): Boolean
}
@Composable expect fun rememberAttachmentFileStore(): AttachmentFileStore

expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
```

- **Android (`ImageCapture.android.kt`)**
  - Cámara: `ActivityResultContracts.RequestPermission` (si `CAMERA` no está
    concedido) → **intent `ACTION_IMAGE_CAPTURE` con `StartActivityForResult`**
    (no `ActivityResultContracts.TakePicture`) con un `Uri` de `FileProvider`
    sobre un archivo temporal en `cacheDir/images/`. **Finding de implementación:**
    `TakePicture` sólo pone `EXTRA_OUTPUT` y **no** agrega
    `FLAG_GRANT_READ/WRITE_URI_PERMISSION`; la app de cámara falla al guardar con
    `SecurityException`/`RemoteException` en `ContentResolver.openOutputStream`
    (`checkAssociationAndPermissionLocked`). El intent se arma a mano con los flags
    de grant + `clipData`. `FileProvider` requiere `<provider>` en el manifest y
    `res/xml/file_paths.xml` (`<cache-path name="images" path="images/" />`).
    `FileProvider` viene de `androidx.core:core` (transitivo vía
    `activity-compose`; resuelve sin declararlo).
  - Galería: `ActivityResultContracts.PickVisualMedia` (sin permiso; cancelar →
    no-op, no error).
  - Lectura: `contentResolver.openInputStream(uri).readBytes()`, mime
    `contentResolver.getType(uri) ?: "image/jpeg"`.
  - `decodeImageBitmap`: `BitmapFactory.decodeByteArray(...)?.asImageBitmap()`.
  - `AndroidAttachmentFileStore(context)`: `filesDir/images/<uuid>.jpg`; `saveImage`
    crea el dir, escribe bytes y devuelve la ruta relativa; `delete` resuelve
    `filesDir` + ruta y borra.
- **iOS (`ImageCapture.ios.kt`)**
  - Cámara: `UIImagePickerController` (`sourceType = camera`) presentado desde el
    `UIViewController` superior (`UIApplication.keyWindow.rootViewController` →
    topmost). En simulador la cámara no está disponible → `CAMERA_UNAVAILABLE`.
  - Galería: `PHPickerViewController` (iOS 14+; deployment 15.3) con
    `PHPickerViewControllerDelegate`; carga la imagen vía `NSItemProvider`
    (`loadDataRepresentationForTypeIdentifier("public.image")` → `NSData` →
    `UIImage.imageWithData`). **Finding de implementación:** el binding de
    `loadObjectOfClass(UIImage)` no resuelve (espera `NSItemProviderReadingProtocol`,
    no la clase), por eso se usa la carga por representación de datos.
  - Conversión: re-encode a JPEG `NSData` (`UIImageJPEGRepresentation`, calidad
    ~0.9) y a `ByteArray` (interop `NSData` ↔ `ByteArray`); mime `"image/jpeg"`.
  - `decodeImageBitmap`: `org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()`.
  - `IosAttachmentFileStore`: base `NSApplicationSupportDirectory` + `images/`
    (`NSFileManager`), ruta relativa; crea el dir si falta.
- **Permisos:** Android declara `CAMERA` y lo pide en runtime; iOS agrega
  `NSCameraUsageDescription` en `Info.plist`. Galería sin permiso en ambas
  plataformas (photo picker / `PHPicker`). La denegación se mapea a
  `PERMISSION_DENIED`.

### Flujo de UI (reutiliza `App`/editor existentes)

- `NotesListScreen` suma una acción de captura de imagen junto a "Nueva nota"
  (`Row` de FABs en el slot de `Scaffold`, con etiqueta textual) y un botón
  "Agregar foto" en el estado vacío. Cada tarjeta muestra además un chip "Imagen"
  si la nota tiene adjuntos, y usa `note.body.ifBlank { "Imagen adjunta" }` como
  texto del cuerpo (una nota sólo-imagen tiene `body` vacío).
- `App(noteRepository, attachmentRepository)`: obtiene `rememberImagePicker(...)` y
  `rememberAttachmentFileStore()`, y mantiene `attachments` junto a `notes`
  (recargados en `LaunchedEffect(refreshKey)`); calcula el conteo por nota para la
  lista. La acción de imagen abre un `AlertDialog` "Agregar imagen"
  ("Cámara"/"Galería"/"Cancelar"); `onResult` navega a
  `NotesDestination.CreateWithImage(picked)`; `onError` muestra un `AlertDialog` de
  error.
- `NotesDestination` agrega `data class CreateWithImage(val image: PickedImage)`.
- `NoteEditorScreen` gana parámetros opcionales `image: PickedImage?` y
  `onRemoveImage: (() -> Unit)?`; cuando hay imagen muestra la previsualización
  (decodificada) + "Quitar imagen", y `canSave = body.isNotBlank() || image != null`.
- **Guardado con imagen:** `fileStore.saveImage(bytes, mime)` (ruta/byteSize) →
  `noteRepository.create(NoteDraft(LOCAL_OWNER_ID, body.trim(), track))` →
  `attachmentRepository.create(note.id, IMAGE, mime, path, size)` → `reload()` →
  lista. El archivo se guarda **antes** de crear la nota; si algo falla después, se
  borra el archivo recién guardado (evita huérfanos). "Cancelar"/"Quitar imagen"
  descartan la imagen en memoria (nada persistido).
- **Borrado:** antes de borrar, `App` obtiene los adjuntos de la nota, borra la nota
  (cascade de filas en el repositorio) y luego borra los archivos con el
  `AttachmentFileStore`; `reload()`. Si el borrado físico falla, se registra y se
  continúa (huérfano de archivo tolerado; se documenta).

### Diseño visual

- Sigue la dirección provisional de `DESIGN.md` ("Captura de Imagen: cámara /
  galería", "Acción de Captura", "Tarjeta de Nota"); sin tokens formales. El chip
  "Imagen" comunica el adjunto con texto (no sólo color).

### Dependencias, esquema e `init.sh`

- **Sin dependencias Gradle nuevas esperadas**: `PickVisualMedia`/Activity Result
  vienen de `activity-compose`; `FileProvider` y `asImageBitmap` de
  `androidx.core`/`compose.ui`; `Image` de `compose.foundation`. Si `FileProvider`
  no resuelve transitivamente, agregar `androidx.core:core` al catálogo.
- **Cambio de esquema:** `schema.version` pasa de **1 a 2**; se agrega `1.sqm` +
  `Attachment.sq`. Primera migración del repo.
- **`init.sh` no cambia**: sigue no bloqueante y sin procesos de larga duración.

Aclaraciones y riesgos abiertos se detallan en "Open Questions / Bloqueos".

## Expected File Changes

### `:core`

- `core/src/commonMain/sqldelight/com/playbook/core/db/Attachment.sq` — crear; tabla
  `attachment` + queries.
- `core/src/commonMain/sqldelight/com/playbook/core/db/1.sqm` — crear; migración v1 → v2.
- `core/src/commonMain/kotlin/com/playbook/core/model/Attachment.kt` — crear;
  `Attachment` + `AttachmentKind` con `code`/`fromCode`.
- `core/src/commonMain/kotlin/com/playbook/core/model/AttachmentMappers.kt` — crear;
  fila → dominio (alias `Attachment as AttachmentRow`).
- `core/src/commonMain/kotlin/com/playbook/core/repository/AttachmentRepository.kt` —
  crear; interfaz.
- `core/src/commonMain/kotlin/com/playbook/core/repository/SqlDelightAttachmentRepository.kt`
  — crear; implementación.
- `core/src/commonMain/kotlin/com/playbook/core/repository/SqlDelightNoteRepository.kt`
  — modificar; cascade de adjuntos en `delete` (transacción).
- `core/src/commonTest/kotlin/com/playbook/core/repository/AttachmentRepositoryCheck.kt`
  — crear; `verifyAttachmentCrud(driver)` (incluye cascade).
- `core/src/commonTest/kotlin/com/playbook/core/db/MigrationCheck.kt` — crear;
  `verifyMigrationV1ToV2(driver)`.
- `core/src/androidUnitTest/kotlin/com/playbook/core/repository/AttachmentRepositoryAndroidTest.kt`
  — crear; `@Test` JVM.
- `core/src/iosTest/kotlin/com/playbook/core/repository/AttachmentRepositoryIosTest.kt`
  — crear; `@Test` iOS.
- `core/src/androidUnitTest/kotlin/com/playbook/core/db/MigrationAndroidTest.kt` — crear.
- `core/src/iosTest/kotlin/com/playbook/core/db/MigrationIosTest.kt` — crear.

### `:composeApp`

- `composeApp/src/commonMain/kotlin/com/playbook/app/ImageCapture.kt` — crear;
  `ImageSource`/`PickedImage`/`ImageCaptureError`/`ImagePicker`/`AttachmentFileStore`
  y los `expect` (`rememberImagePicker`, `rememberAttachmentFileStore`,
  `decodeImageBitmap`).
- `composeApp/src/androidMain/kotlin/com/playbook/app/ImageCapture.android.kt` —
  crear; `actual` Android (photo picker, `TakePicture` + `FileProvider`, decode,
  file store).
- `composeApp/src/iosMain/kotlin/com/playbook/app/ImageCapture.ios.kt` — crear;
  `actual` iOS (`UIImagePickerController`, `PHPickerViewController`, decode, file
  store).
- `composeApp/src/androidMain/AndroidManifest.xml` — modificar; `CAMERA` +
  `<provider>` `FileProvider`.
- `composeApp/src/androidMain/res/xml/file_paths.xml` — crear; `cache-path` de
  imágenes.
- `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt` — modificar;
  construir `SqlDelightAttachmentRepository` y pasarlo a `App(...)`.
- `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt` —
  modificar; ídem iOS.
- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt` — modificar; segundo
  repositorio, picker/file store, `CreateWithImage`, carga de adjuntos, diálogo de
  fuente/errores y guardado/borrado con archivos.
- `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt` —
  modificar; acción/CTA de captura de imagen y chip "Imagen".
- `composeApp/src/commonMain/kotlin/com/playbook/app/NoteEditorScreen.kt` —
  modificar; previsualización de imagen, "Quitar imagen" y guardado con imagen.
- `iosApp/iosApp/Info.plist` — modificar; `NSCameraUsageDescription`.

### Docs y cierre

- `ARCHITECTURE.md` — modificar; capa de adjuntos, esquema v2/migración y
  integración nativa de captura.
- `docs/technical-discovery.md` — modificar; decisiones de almacenamiento de
  adjuntos (archivo + ruta relativa), migración y pickers/permisos.
- `docs/risks-and-open-questions.md` — modificar; registrar cámara/permisos
  resueltos y la limitación de cámara iOS en simulador.
- `AGENTS.md` — modificar al cerrar; línea "Próxima feature en cola".
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado y evidencia.

Si algún path provisional del nativo cambia (p. ej. nombre del archivo de actuals),
mantener el contrato de `commonMain`.

## Visual Design Impact

- UI involved: yes.
- Design source: `DESIGN.md` (provisional). Se sigue su dirección estructural
  ("Captura de Imagen: cámara / galería", "Acción de Captura", "Tarjeta de Nota") y
  la regla "estados no sólo por color"; no se fijan tokens.
- Screens or states affected: "Lista de Notas" (acción/FAB de imagen, CTA de estado
  vacío, chip "Imagen" y placeholder de cuerpo vacío) y "Editor de Nota de Texto"
  (modo creación con imagen: previsualización, quitar imagen, guardar con body
  vacío permitido).
- New design artifact required: no — la entrega de UI/UX sigue pendiente.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — aparece la entidad `Adjunto`, el esquema v2 con su
  primera migración y la integración nativa (pickers, permisos, file store).
- `CONSTRAINTS.md`: not needed — no surge una regla MUST/MUST NOT durable nueva
  (las reglas de `:core` vs `:composeApp` ya están en `ARCHITECTURE.md`/`AGENTS.md`).
- `AGENTS.md`: **update** — la línea "Próxima feature en cola" cambia; no cambian
  workflow ni gate.
- Other docs: `docs/technical-discovery.md` y `docs/risks-and-open-questions.md` —
  update; `feature_list.json`/`PROGRESS.md` — update al cerrar; `CONTEXT.md`/
  `docs/domain-model.md` — not needed (la spec se alinea con ellos).

## Implementation Plan

1. **Core/persistencia:** crear `Attachment.sq`, `1.sqm` y el modelo/repositorio;
   confirmar `Schema.version = 2` y que `:core:testDebugUnitTest` compila (validar
   la migración temprano).
2. **Tests del core:** `verifyAttachmentCrud` (CRUD + cascade) y
   `verifyMigrationV1ToV2` en `commonTest`; `@Test` por plataforma Android/JVM e iOS.
3. **Cascade** en `SqlDelightNoteRepository.delete` (transacción).
4. **Abstracción de captura:** `ImageCapture.kt` (commonMain) + actuals Android/iOS
   (pickers, decode, file store) y `MainActivity`/`MainViewController` con el
   segundo repositorio.
5. **Permisos y manifest/plist:** `CAMERA` + `FileProvider`/`file_paths.xml` en
   Android; `NSCameraUsageDescription` en iOS.
6. **UI:** `App` (destino `CreateWithImage`, diálogo de fuente/errores, carga de
   adjuntos, guardado/borrado), `NotesListScreen` (acción/CTA + chip) y
   `NoteEditorScreen` (preview/quitar/guardar con imagen).
7. **Docs durables** y verificación; smoke manual Android/iOS y evidencia; cierre en
   `feature_list.json`/`PROGRESS.md`/`AGENTS.md`.

## Implementation Tasks

- [x] Crear `Attachment.sq` (tabla + queries) y `1.sqm` (migración v1 → v2).
- [x] Confirmar `PlaybookDatabase.Schema.version == 2` y codegen correcto.
- [x] Crear `Attachment`/`AttachmentKind` y el mapper (alias `Attachment as AttachmentRow`).
- [x] Definir `AttachmentRepository` + `SqlDelightAttachmentRepository`.
- [x] Agregar el cascade de adjuntos en `SqlDelightNoteRepository.delete` (transacción).
- [x] Escribir `verifyAttachmentCrud` (CRUD + cascade) y `verifyMigrationV1ToV2` en `commonTest`.
- [x] Escribir los `@Test` por plataforma (Android/JVM con `JdbcSqliteDriver.IN_MEMORY`, iOS con `inMemoryDriver`).
- [x] Crear `ImageCapture.kt` en `commonMain` con los contratos y `expect`.
- [x] Implementar el `actual` Android (permiso + intent de cámara con grant de URI + `PickVisualMedia`, decode, file store).
- [x] Implementar el `actual` iOS (`UIImagePickerController` + `PHPickerViewController`, decode, file store).
- [x] Agregar `CAMERA`, `FileProvider` y `res/xml/file_paths.xml` en Android; `NSCameraUsageDescription` en iOS.
- [x] Construir el `AttachmentRepository` y pasarlo a `App(...)` en `MainActivity`/`MainViewController`.
- [x] Agregar `NotesDestination.CreateWithImage`, el diálogo de fuente/errores y la carga de adjuntos en `App`.
- [x] Actualizar `NotesListScreen` (acción/CTA de captura + chip "Imagen" + placeholder de cuerpo).
- [x] Actualizar `NoteEditorScreen` (previsualización, quitar imagen, guardar con body vacío permitido).
- [x] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md` y `docs/risks-and-open-questions.md`.
- [x] Correr `./init.sh` y los comandos de verificación; smoke manual Android/iOS; registrar evidencia.
- [x] Actualizar `feature_list.json`, `PROGRESS.md` y `AGENTS.md` al cerrar.

## Verification Plan

### Verificable en el harness (`init.sh` / Gradle)

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL;
  `verifyAttachmentCrud` (create/list/delete + cascade al borrar la nota),
  `verifyMigrationV1ToV2` y los tests previos en verde; `Schema.version == 2` y
  `1.sqm` presente.
- `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → los mismos helpers verdes
  en iOS.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (UI + actual Android +
  manifest/FileProvider).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL
  (UI + actual iOS); `xcodebuild` del scheme `iosApp` → BUILD SUCCEEDED.
- `./init.sh` → exit 0; ejecuta el gate no bloqueante actual, **no** inicia dev
  servers ni simuladores y puede imprimir los comandos manuales.
- Confirmar que no se agregaron dependencias inesperadas y que
  `PlaybookDatabaseImpl.Schema.version = 2`.

### Smoke manual (no automatizable en el harness)

- **Android (emulador/Pixel_3A_API_34):**
  - Cámara con cámara virtual del emulador: elegir "Cámara" → conceder permiso →
    capturar → previsualización en el editor → guardar → la nota aparece con chip
    "Imagen"; reabrir la app y verificar persistencia.
  - Galería: photo picker con una imagen sembrada vía `adb push` a
    `/sdcard/Pictures/` (+ media scan) → elegir "Galería" → seleccionar → guardar.
  - Denegar permiso de cámara → mensaje de error, sin crash ni filas creadas.
  - Borrar la nota → confirmar en `databases/playbook.db` (`sqlite3`) que no quedan
    filas en `attachment` y que el archivo se eliminó.
- **iOS (simulador iPhone 15 / iOS 17.2):**
  - `xcodebuild` + instalar/lanzar; sembrar la fototeca con
    `xcrun simctl addmedia booted <imagen>`; elegir "Galería" (`PHPicker`) →
    seleccionar → previsualización → guardar → nota con chip "Imagen"; verificar
    persistencia y `Library/Application Support/databases/playbook.db`
    (`user_version = 2`, fila en `attachment`).
  - Cámara en simulador → `CAMERA_UNAVAILABLE` con mensaje, sin crash (la captura
    real de cámara iOS requiere dispositivo físico; **no** se fabrica evidencia).
- E2E persistente: **no aplica** — no existe harness de UI/nativo y `init.sh` no
  levanta simuladores. Se justifica: el path de datos (adjuntos + migración +
  cascade) queda cubierto por tests del core en dos plataformas; la integración
  nativa (pickers, permisos, archivos) se verifica con smoke manual en ambas
  plataformas, como en las features previas de UI.

### `init.sh`

- Debe ejecutar el gate estándar no bloqueante del estado actual; **no** debe
  levantar procesos de larga duración (sin simuladores ni dev servers); puede
  imprimir los comandos manuales tras los checks. No requiere cambios.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` con los tests
  de adjuntos y migración en verde (nombres y conteos).
- Salida de `:composeApp:assembleDebug`, `:composeApp:linkDebugFrameworkIosSimulatorArm64`
  y `xcodebuild` del scheme `iosApp`.
- `PlaybookDatabase.Schema.version == 2` y presencia de `1.sqm`; `find core/src -name '*.sqm'`.
- Salida de `./init.sh` (exit 0) sin procesos de larga duración.
- Smoke Android: captura por cámara y por galería, previsualización, guardado,
  persistencia, denegación de permiso y borrado sin huérfanos (capturas/UI dumps +
  inspección `sqlite3`).
- Smoke iOS: galería (`PHPicker`) con imagen sembrada, persistencia y
  `user_version = 2`; cámara en simulador → `CAMERA_UNAVAILABLE` (o dispositivo si
  hay); capturas/OCR cuando aplique. Honestidad si algo no pudo ejecutarse.
- Confirmación de archivos en almacenamiento privado con ruta relativa guardada en
  la base.

## Validator Checklist

- [ ] Alcance respetado: captura de imagen (cámara/galería) → Nota con Adjunto;
      sin audio/OCR/render en lista, sin editar adjuntos existentes, sin IA/GDD.
- [ ] Escenarios de aceptación 1–8 pasan (los nativos con smoke honesto).
- [ ] La migración v1 → v2 existe (`1.sqm` + `Attachment.sq`), `Schema.version == 2`
      y el test de migración preserva las notas existentes.
- [ ] El Adjunto se guarda como archivo en almacenamiento privado con **ruta
      relativa**; no se usan BLOBs para las imágenes.
- [ ] Borrar una Nota elimina las filas `attachment` (cascade) y los archivos.
- [ ] Permisos: `CAMERA` en Android solicitado en runtime; `NSCameraUsageDescription`
      en iOS; galería sin permiso (photo picker / `PHPicker`); denegación sin crash.
- [ ] Guardar con imagen y `body` vacío permitido; "Cancelar"/"Quitar imagen" no
      dejan filas ni archivos.
- [ ] Sin DI/`ViewModel`/Flow/librería de navegación; `App` recibe los repositorios
      por parámetro.
- [ ] No hay E2E persistente y la spec lo justifica; límites de tooling declarados
      (cámara iOS en simulador, sin input automation iOS).
- [ ] Evidencia en `feature_list.json` / `PROGRESS.md`; `ARCHITECTURE.md`,
      `docs/technical-discovery.md`, `docs/risks-and-open-questions.md` y
      `AGENTS.md` actualizados.
- [ ] `init.sh` sin cambios, no bloqueante y sin procesos de larga duración.

## Open Questions / Bloqueos

- **Cámara iOS fuera del harness:** el simulador no expone cámara; la captura real
  de cámara en iOS sólo se valida en dispositivo físico. Se documenta y no se
  fabrica evidencia.
- **Decode multiplataforma de la previsualización:** confirmar
  `toComposeImageBitmap()` (skiko/iOS) y `asImageBitmap()` (Android) al primer
  build; si falla, usar el placeholder textual documentado y reportar el bloqueo.
- **`FileProvider` transitivo:** confirmar que `androidx.core:core` resuelve desde
  `activity-compose`; si no, agregarlo al catálogo.
- **`body` vacío para notas sólo-imagen:** decisión tomada (permitido); revisar si
  el producto lo quiere no vacío y, de ser así, cambiarlo en una feature de editor.
- **Migración sin precedentes:** validar temprano que `1.sqm` + `Attachment.sq`
  generan `Schema.version = 2` y que `:core:testDebugUnitTest` compila; habilitar
  `verifyMigrations` es opcional (red de seguridad de build-time), no un requisito.
- **`UIViewController` topmost en iOS:** presentar los pickers nativos desde el host
  de Compose puede depender de la jerarquía de controllers; verificación manual.
- **Cascade de archivos:** las filas se borran en el repositorio (testeable) y los
  archivos en la UI (nativo); un fallo de borrado físico deja huérfanos tolerados
  (se documentan).
