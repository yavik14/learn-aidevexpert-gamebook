# Architecture

Mapa de la base KMP. `local-persistence-sqldelight` agregó la capa de
persistencia local (SQLDelight), `note-model-crud-core` el modelo de dominio
`Note` y su CRUD, `notes-list-ui` la primera pantalla real (lista de notas) con
el repositorio cableado a la app, `create-text-note` el CRUD de notas de texto
desde la UI (crear/editar/borrar + refresco), `ai-client-interface` el puerto
`AiClient` hacia el runtime de IA más un adaptador fake por defecto, y
`note-category-and-tags` las Etiquetas libres (esquema v2 con `note_tag` y su
primera migración v1→v2).

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
- Esquema **v2**: tabla `note` (`id`, `owner`, `body`, `track` nullable,
  `status`, `created_at`, `updated_at`) y tabla `note_tag` (`note_id`, `label`,
  PK compuesta `(note_id, label)`) para las Etiquetas (relación N—N). La tabla
  `note` no cambió en esta feature. `note-category-and-tags` agregó la primera
  migración `core/src/commonMain/sqldelight/migrations/1.sqm` (v1→v2, crea
  `note_tag`); `PlaybookDatabase.Schema.version = 2`. Las tablas vecinas
  (adjuntos, enlaces, embeddings) llegan en features posteriores.
- **Migraciones:** como se agregó una tabla, el esquema pasó de v1 a v2 con un
  archivo `.sqm` por transición (`1.sqm` migra v1→v2). El `.sq` describe el
  esquema más reciente; los `.sqm` hacen upgrade. `AndroidSqliteDriver` (con
  `PlaybookDatabase.Schema` en su constructor) y `NativeSqliteDriver` detectan
  `user_version` y ejecutan `Schema.migrate` sobre bases existentes. Sin
  `schemaOutputDirectory` (no se usa `verifyMigrations`).
- Sin FK ni `ON DELETE CASCADE` en `note_tag`: la limpieza de etiquetas es
  explícita en el repositorio.
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
  (`create`/`getAll`/`getById`/`update`/`delete`) e implementación
  `SqlDelightNoteRepository(database, idFactory, clock)`. `update` re-sella
  `updated_at` con `clock()` (ignora el del argumento) y preserva
  `owner`/`created_at`; `update`/`delete` devuelven `rowsAffected > 0`.
  - Etiquetas: `create` normaliza y persiste `insertNote` + `insertNoteTag` en
    una transacción; `getById` lee `selectTagsByNoteId`; `getAll` usa
    `selectAllNoteTags` agrupado en memoria (evita N+1); `update` reemplaza el
    conjunto completo (delete + insert) en la misma transacción que la nota y
    sólo si la fila existía; `delete` limpia `note_tag` junto con la nota.
- **Mapper** (`NoteMappers.kt`): importa `com.playbook.core.db.Note as NoteRow`
  para evitar la colisión con el modelo de dominio;
  `NoteRow.toDomain(tags = emptyList())` recibe las etiquetas ya resueltas.
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
  `status.code`; si `note.tags` no está vacía, agrega una fila de chips de
  etiqueta con la misma `LabelChip` (en `Row` con `horizontalScroll`). El
  `track`/`status`/etiquetas se comunican con texto, no sólo color (`DESIGN.md`).
- **`NoteEditorScreen`**: editor de texto para crear (`isEditing = false`)
  y editar (`isEditing = true`). `OutlinedTextField` multilínea para `body`;
  selector de `track` ("Sin track" + `Track.entries`) que comunica la selección
  con marca textual "✓", borde y color; campo "Nueva etiqueta" + botón "Agregar"
  (habilitado con texto no vacío) que aplica `normalizeTags` y limpia el campo;
  chips de etiqueta con control de quitar "×" (`clickable` con
  `onClickLabel = "Quitar etiqueta <label>"`, `role = Role.Button`, sin íconos
  nuevos); `Guardar` deshabilitado si `body.isBlank()`; `Cancelar`; y en edición
  `Borrar` con `AlertDialog` de confirmación. La pantalla mantiene el borrador
  local (`body`/`track`/`tags`) y emite
  `onSave(body, track, tags)`/`onDelete`/`onCancel`; la persistencia y el
  refresco los maneja `App`. No hay control de Categoría/Tipo/Nivel: el `track`
  ya cubre la clasificación fija.
- **Flujo de datos:** crear → `create(NoteDraft(LOCAL_OWNER_ID, body.trim(),
  track, tags = tags))` (status default `CAPTURED`); editar → `update(note.copy(
  body = body.trim(), track = ..., tags = tags))` (re-sella `updatedAt`, y
  reemplaza el conjunto de etiquetas, preservando `owner`/`createdAt`/`status`);
  borrar → `delete(id)`; siempre `reload()` y vuelta a la lista. El `status` no
  se edita desde la UI. Si el `id` en `Edit(noteId)` ya no existe en `notes`, se
  vuelve a la lista.
- **Deuda resuelta:** `notes-list-ui` leía la lista en el hilo de composición y no
  refrescaba tras mutaciones; `create-text-note` lo resolvió con `LaunchedEffect` +
  `refreshKey`. `note-category-and-tags` agregó la edición de etiquetas; siguen
  fuera de esta superficie los adjuntos, la IA y el GDD.

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

- Adjuntos, enlaces y embeddings (features posteriores). El runtime
  de IA concreto (`ai-runtime-decision`) y el wiring de `AiClient` a su primer
  consumidor (`embeddings-generation`) también quedan pendientes.
- Agrupado por `track` / vista GDD (`gdd-view`) y detalle de nota de sólo lectura.
- Manejo del back físico Android/iOS en el editor (hoy sólo "Cancelar").
- Formalización de `DESIGN.md` (sigue `provisional`; sin entrega de UI/UX).
