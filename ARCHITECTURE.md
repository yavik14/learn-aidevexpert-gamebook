# Architecture

Mapa de la base KMP. `local-persistence-sqldelight` agregó la capa de
persistencia local (SQLDelight), `note-model-crud-core` el modelo de dominio
`Note` y su CRUD, `notes-list-ui` la primera pantalla real (lista de notas) y
`create-text-note` el CRUD de notas de texto desde la UI (crear/editar/borrar +
refresco).

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

## UI

- **`commonMain`** (`com.playbook.app`): `App(noteRepository: NoteRepository)` es
  la raíz de la UI y dueña del estado. Hoistea `notes` (`mutableStateOf`),
  `destination` (`NotesDestination`: `List`/`Create`/`Edit(noteId)`) y `refreshKey`.
  La lectura ocurre en un `LaunchedEffect(refreshKey)`, **nunca** en el cuerpo de
  composición; tras cada mutación se incrementa `refreshKey` (`reload()`), lo que
  dispara la relectura y refleja los cambios sin reiniciar la app. Sin DI, sin
  `ViewModel`/`Lifecycle`, sin coroutines fuera de Compose y sin Flow ni librería
  de navegación.
- **Owner del MVP:** `LOCAL_OWNER_ID = "local"` (`private const val` en `App.kt`);
  la UI lo provee al construir el `NoteDraft` de creación. `:core` no cambia.
- **`NotesListScreen`**: `Scaffold` con `ExtendedFloatingActionButton` "Nueva
  nota"; estado vacío ("Todavía no hay notas") con CTA "Crear primera nota"; y
  estado con notas (título "Notas" + `LazyColumn` de `Card`s clickeables que
  llaman `onEdit(note.id)`). Cada tarjeta muestra `body` (máx. 2 líneas, ellipsis)
  y una fila de chips de texto con `track.code` (o "Sin track" si es `null`) y
  `status.code`. El `track`/`status` se comunican con texto, no sólo color
  (`DESIGN.md`).
- **`NoteEditorScreen`** (nuevo): editor de texto para crear (`isEditing = false`)
  y editar (`isEditing = true`). `OutlinedTextField` multilínea para `body`;
  selector de `track` ("Sin track" + `Track.entries`) que comunica la selección
  con marca textual "✓", borde y color; `Guardar` deshabilitado si `body.isBlank()`;
  `Cancelar`; y en edición `Borrar` con `AlertDialog` de confirmación. La pantalla
  mantiene el borrador local y emite callbacks; la persistencia y el refresco los
  maneja `App`.
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

- **Android:** `MainActivity` (`com.playbook.app`) monta `App(noteRepository)`,
  aplica el esquema local con `createDatabase(AndroidDatabaseDriverFactory(...))`
  y construye el `SqlDelightNoteRepository`.
- **iOS:** `MainViewController()` (Kotlin, `composeApp/src/iosMain`) crea la base
  local, construye el repositorio y envuelve `App(noteRepository)`; lo llama
  `ComposeView` (SwiftUI) en `iosApp`.

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

- Etiquetas, adjuntos, enlaces, embeddings e IA (features posteriores).
- Agrupado por `track` / vista GDD (`gdd-view`) y detalle de nota de sólo lectura.
- Manejo del back físico Android/iOS en el editor (hoy sólo "Cancelar").
- Formalización de `DESIGN.md` (sigue `provisional`; sin entrega de UI/UX).
