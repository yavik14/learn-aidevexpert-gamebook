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
- **IA:** interfaz `AiClient` como abstracción. Runtime (cloud / on-device /
  híbrido) todavía **sin decidir**.
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
- **Proveedor de IA:** embeddings + LLM detrás de `AiClient` — pendiente de
  elección (ver riesgos).

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
