# Progress Log

## Current Verified State

- Repository root: `/Users/javierrodriguez/Alt10/course/ai/devexpert/learn-aidevexpert-gamebook`
- Standard startup path: `./init.sh`
- Standard verification path: `./init.sh` corre `:composeApp:assembleDebug`, `:core:testDebugUnitTest`, `:composeApp:linkDebugFrameworkIosSimulatorArm64` y (en macOS) `:core:iosSimulatorArm64Test`, sin levantar dev servers.
- Última feature `accepted`: `android-release-pipeline` (2026-10-09; Sessions 015-016; validación independiente `accept`).
- `android-release-pipeline` `accepted`: signing de release Android condicional (precedencia env `PLAYBOOK_*` > `keystore.properties`, keystore fuera del repo), modo degradado sin secrets, override de `versionCode`/`versionName` y runbook `docs/release/android.md`; `init.sh` sin cambios. Subida real a Play internal testing (Scenario 6) queda como paso manual/externo.
- Current next ready feature: `note-category-and-tags` (depende de `create-text-note`, ya `accepted`) e `ios-release-pipeline` (depende de `create-text-note`, ya `accepted`); `ai-runtime-decision` (depende de `ai-client-interface`, ya `accepted`); requieren spec.
- Current blocker: none.
- Last verified at: 2026-10-09.

## Session Log

### Session 001

- Date: 2026-09-21
- Goal: Create the minimal startup harness.
- Completed: `AGENTS.md`, `init.sh`, `PROGRESS.md` and `feature_list.json` created.
- Verification run: JSON validation of `feature_list.json` and `chmod +x init.sh`.
- Evidence captured: deterministic resolution of first ready feature.
- Files or artifacts updated: `AGENTS.md`, `init.sh`, `PROGRESS.md`, `feature_list.json`.
- Known risk or unresolved issue: app not bootstrapped yet; feature-level verification commands were provisional until bootstrap.
- Next best step: run `$feature-spec` for `kmp-project-bootstrap` (technical bootstrap of the KMP + Compose Multiplatform project).

### Session 002

- Date: 2026-10-01
- Goal: Implement `kmp-project-bootstrap` (spec `docs/specs/kmp-project-bootstrap.md`).
- Completed:
  - Toolchain Gradle: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, wrapper 8.14.5, `local.properties` (no versionado).
  - `:core` KMP library (targets `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`) con `Greeting` + test trivial.
  - `:composeApp` Compose Multiplatform: `App()` placeholder en `commonMain`, `MainActivity` + `AndroidManifest.xml` + `themes.xml` en `androidMain`, `MainViewController` + framework `ComposeApp` en `iosMain`.
  - Host `iosApp` (Xcode, SwiftUI) enlazado al framework vía `:composeApp:embedAndSignAppleFrameworkForXcode`.
  - `init.sh` bootstrapeado (gate no bloqueante), `.gitignore`, `ARCHITECTURE.md` (nuevo), `AGENTS.md` y `docs/technical-discovery.md` actualizados.
- Verification run:
  - `./gradlew :composeApp:assembleDebug :core:testDebugUnitTest` → BUILD SUCCESSFUL (APK 10.0 MB, 1 test OK).
  - `./gradlew :core:iosSimulatorArm64Test` → 1 test OK, 0 failures.
  - `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator` → BUILD SUCCEEDED; `Playbook.app` instalada y lanzada en iPhone 15 (iOS 17.2).
  - App lanzada en Android (emulator-5554, Pixel_3A_API_34): MainActivity en foco, sin excepciones.
  - `./init.sh` → exit 0 sin dev servers; simulación sin JDK/Xcode → exit 1 con mensaje accionable.
- Evidence captured: ver arreglo `evidence` de `kmp-project-bootstrap` en `feature_list.json`; capturas de pantalla locales (Android/iOS).
- Files or artifacts updated: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `core/**`, `composeApp/**`, `iosApp/**`, `init.sh`, `.gitignore`, `ARCHITECTURE.md`, `AGENTS.md`, `docs/technical-discovery.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el proyecto depende de macOS + Xcode para el target iOS (documentado). `local.properties` no se versiona, cada entorno debe fijar `sdk.dir` o `ANDROID_HOME`.
- Next best step: validación independiente de `kmp-project-bootstrap` con `$feature-validator`; tras `accept`, arrancar `local-persistence-sqldelight`.

### Session 003

- Date: 2026-10-05
- Goal: Validación independiente de `kmp-project-bootstrap` (spec `docs/specs/kmp-project-bootstrap.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `kmp-project-bootstrap` promovida a `accepted` en `feature_list.json` con evidencia de validación.
- Verification run:
  - `./init.sh` → exit 0 (Android build + tests core + link framework iOS).
  - Build limpio `:composeApp:assembleDebug :core:testDebugUnitTest :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test --rerun-tasks` → GreetingTest 1/1, 0 failures.
  - `xcodebuild` scheme iosApp → BUILD SUCCEEDED; app instalada y lanzada en iPhone 15 (iOS 17.2) sin crashes.
  - `init.sh` con entorno incompleto → exit 1 con mensaje accionable.
- Evidence captured: ver líneas de validación en el arreglo `evidence` de `kmp-project-bootstrap` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: cambios sin commitear en `.opencode/agents/{planner,implementer,validator}.md` (observación Low del validador); pendiente decidir commit o descarte. Hallazgos Low no bloqueantes: tasks de la spec sin marcar, `:core` incluye `Greeting` pese a "vacío" en la spec, `init.sh` no corre `:core:iosSimulatorArm64Test`.
- Next best step: crear spec de `local-persistence-sqldelight` con `$feature-spec` e implementarla con `$feature-implementer`.

### Session 004

- Date: 2026-10-05
- Goal: implementar `local-persistence-sqldelight` (spec `docs/specs/local-persistence-sqldelight.md`).
- Completed:
  - SQLDelight 2.1.0 en `:core` (`libs.versions.toml`, raíz, `core/build.gradle.kts`) con base `PlaybookDatabase` (`com.playbook.core.db`).
  - Esquema v1 `Note.sq` (tabla `note` con `track` nullable) y queries `insertNote`/`selectAllNotes`/`selectNoteById`.
  - `DatabaseDriverFactory` + `createDatabase` (commonMain), `AndroidDatabaseDriverFactory` (androidMain), `IosDatabaseDriverFactory` (iosMain).
  - Test común `NotePersistenceCheck` + `NotePersistenceAndroidTest` (`JdbcSqliteDriver.IN_MEMORY`) y `NotePersistenceIosTest` (`inMemoryDriver`).
  - Wiring de arranque en `MainActivity`/`MainViewController`; host Xcode enlaza `-lsqlite3`.
  - `init.sh` corre `:core:iosSimulatorArm64Test`; `ARCHITECTURE.md` y `docs/technical-discovery.md` actualizados.
- Verification run:
  - `./gradlew :core:testDebugUnitTest` → BUILD SUCCESSFUL; NotePersistenceAndroidTest 1/1 + GreetingTest 1/1.
  - `./gradlew :core:iosSimulatorArm64Test` → BUILD SUCCESSFUL; NotePersistenceIosTest 1/1 + GreetingTest 1/1.
  - `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - `xcodebuild` scheme iosApp (iPhone 15, iOS 17.2) → BUILD SUCCEEDED. (Claim original "sin excepciones / sobrevive al relanzar" **retirado**: no correspondía; el arranque real crasheaba por el bug de `createDatabase` corregido en Session 005.)
  - `./init.sh` → exit 0 sin procesos de larga duración.
- Evidence captured: ver arreglo `evidence` de `local-persistence-sqldelight` en `feature_list.json`.
- Files or artifacts updated: `gradle/libs.versions.toml`, `build.gradle.kts`, `core/build.gradle.kts`, `core/src/**`, `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt`, `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt`, `iosApp/iosApp.xcodeproj/project.pbxproj`, `init.sh`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/specs/local-persistence-sqldelight.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el path real de arranque (`createDatabase`) no estaba cubierto por un test y el claim de iOS no se sostuvo; el validador reprodujo crashes. Resuelto en Session 005. El riesgo AGP 8.10.1 + SQLDelight 2.1.0 quedó descartado. Hallazgos: driver nativo perezoso y `-lsqlite3` en el host Xcode.
- Next best step: validación independiente de `local-persistence-sqldelight` con `$feature-validator`; tras `accept`, crear spec e implementar `note-model-crud-core`.

### Session 005

- Date: 2026-10-05
- Goal: reparar `local-persistence-sqldelight` tras veredicto `revise` (Implementer Repair Brief): arranque real, test del path cableado y evidencia honesta.
- Completed:
  - `createDatabase(...)` ahora ejecuta el `PRAGMA user_version` de apertura con `executeQuery(...)` (antes usaba `execute(...)`, inválido para sentencias que retornan filas); import de `app.cash.sqldelight.db.QueryResult`.
  - Nuevo test de regresión `createDatabaseOpensConnection` en `androidUnitTest` (`JdbcSqliteDriver.IN_MEMORY` + `Schema.create`) e `iosTest` (`inMemoryDriver`), sobre el helper `verifyCreateDatabaseOpensConnection` en `commonTest`. Ejercita el path real cableado al arranque, que antes no se cubría.
  - Evidencia corregida en `feature_list.json`: se retiró "sin excepciones / sobrevive al relanzar".
  - Terminología obsoleta de `note-model-crud-core` corregida ("categoría/tipo" → `track`); `AGENTS.md` actualizado (próxima feature en cola).
  - `docs/technical-discovery.md`, `ARCHITECTURE.md` y la spec documentan que el statement de apertura debe usar `executeQuery`.
- Verification run:
  - Regresión reproducible: con `execute(...)` el test `createDatabaseOpensConnection` falla en iOS con `SQLiteException: Queries can be performed using SQLiteDatabase query or rawQuery methods only.` (mismo error del validador); con `executeQuery(...)` pasa en Android/JVM y en iOS.
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; Android `NotePersistenceAndroidTest` 2/2 (createDatabaseOpensConnection + writesAndReadsNote) + `GreetingTest` 1/1; iOS `NotePersistenceIosTest` 2/2 + `GreetingTest` 1/1.
  - `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - `xcodebuild` scheme iosApp (iPhone 15, iOS 17.2) → BUILD SUCCEEDED; iOS real: app instalada/lanzada con PID vivo y relanzada sin crash nuevo; `playbook.db` con tabla `note` y `user_version=1`.
  - Android real: emulador Pixel_3A_API_34 (emulator-5554), `pm clear com.playbook.app` + launch; PID vivo, sin `FATAL EXCEPTION`/`SQLiteException`; `databases/playbook.db` con tabla `note` y `user_version=1`.
  - `./init.sh` → exit 0, sin procesos de larga duración.
- Evidence captured: arreglo `evidence` de `local-persistence-sqldelight` en `feature_list.json`; crash reports de la versión rota en `~/Library/Logs/DiagnosticReports/` (21:11–21:16), sin crashes nuevos post-fix.
- Files or artifacts updated: `core/src/commonMain/kotlin/com/playbook/core/db/DatabaseDriverFactory.kt`, `core/src/commonTest/kotlin/com/playbook/core/db/DatabaseCreationCheck.kt`, `core/src/androidUnitTest/kotlin/com/playbook/core/db/NotePersistenceAndroidTest.kt`, `core/src/iosTest/kotlin/com/playbook/core/db/NotePersistenceIosTest.kt`, `docs/technical-discovery.md`, `ARCHITECTURE.md`, `docs/specs/local-persistence-sqldelight.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el emulador Android con GPU por software tarda mucho en bootear; el arranque real queda verificado manualmente y no forma parte de `init.sh` (por diseño, sin simuladores).
- Next best step: re-validación independiente de `local-persistence-sqldelight` con `$feature-validator`.

### Session 006

- Date: 2026-10-05
- Goal: re-validación independiente de `local-persistence-sqldelight` tras la reparación (spec `docs/specs/local-persistence-sqldelight.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `local-persistence-sqldelight` promovida a `accepted` en `feature_list.json` con evidencia de validación.
- Verification run:
  - `./init.sh` → exit 0.
  - `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test --rerun-tasks` → 2/2 + 2/2, 0 failures.
  - `:composeApp:assembleDebug` + link del framework iOS → BUILD SUCCESSFUL; `xcodebuild` scheme iosApp → BUILD SUCCEEDED.
  - Control negativo propio del validador (revertir `executeQuery`→`execute`) reproduce en iOS el `SQLiteException` exacto y luego se restaura.
  - Arranque real iOS (iPhone 15, iOS 17.2) y Android (Pixel_3A_API_34): PID vivo, sin crashes, `playbook.db` con tabla `note` y `user_version=1`.
- Evidence captured: línea de validación en el arreglo `evidence` de `local-persistence-sqldelight` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: terminología residual "categoría/tipo" en otras entradas de `feature_list.json` (`note-category-and-tags`, `gdd-view`, enriquecimiento IA), a normalizar a `track` cuando se re-deriven. Vocabulario de ejemplo de `track` a unificar en `note-model-crud-core`.
- Next best step: crear spec de `note-model-crud-core` con `$feature-spec` e implementarla con `$feature-implementer`.

### Session 007

- Date: 2026-10-05
- Goal: implementar `note-model-crud-core` (spec `docs/specs/note-model-crud-core.md`).
- Completed:
  - Modelo de dominio en `com.playbook.core.model`: `Note`, `NoteDraft`, enums `Track` (`MECHANICS`/`CHARACTERS`/`STORY`, códigos `mecánicas`/`personajes`/`historia`) y `NoteStatus` (`CAPTURED`/`PENDING`/`INDEXED`/`FAILED`, códigos `capturada`/`pendiente`/`indexada`/`fallida`) con `code`/`fromCode`; mapper `NoteRow.toDomain()` (import alias `com.playbook.core.db.Note as NoteRow`).
  - Repositorio `NoteRepository` + `SqlDelightNoteRepository(database, idFactory, clock)` con create/getAll/getById/update/delete; `update` re-sella `updated_at` con `clock()`; update/delete devuelven `rowsAffected > 0`.
  - `expect`/`actual` `randomNoteId()`/`currentTimeMillis()` en commonMain/androidMain (`UUID`/`System`)/iosMain (`NSUUID`/`NSDate`).
  - Queries `updateNote`/`deleteNoteById` agregadas a `Note.sq` sin cambio de esquema (sigue v1, sin `.sqm`).
  - Tests: `verifyNoteCrud(driver)` en `commonTest` + `NoteRepositoryAndroidTest` (`JdbcSqliteDriver.IN_MEMORY` + `Schema.create`) e `NoteRepositoryIosTest` (`inMemoryDriver`). Se unificó el vocabulario de `NotePersistenceCheck` a los códigos canónicos.
  - `ARCHITECTURE.md` (capa de dominio/repositorio) y `docs/technical-discovery.md` (Domain Decisions) actualizados; tasks de la spec marcadas.
- Verification run:
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; NoteRepositoryAndroidTest 1/1, NotePersistenceAndroidTest 2/2, GreetingTest 1/1, 0 failures.
  - `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; NoteRepositoryIosTest 1/1, NotePersistenceIosTest 2/2, GreetingTest 1/1, 0 failures.
  - `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - `PlaybookDatabaseImpl.Schema.version = 1`; sin `.sqm` en `core/src`.
  - `./init.sh` → exit 0, sin dev servers ni simuladores.
- Evidence captured: arreglo `evidence` de `note-model-crud-core` en `feature_list.json`.
- Files or artifacts updated: `core/src/commonMain/sqldelight/com/playbook/core/db/Note.sq`, `core/src/commonMain/kotlin/com/playbook/core/model/{Track,NoteStatus,Note,NoteMappers}.kt`, `core/src/commonMain/kotlin/com/playbook/core/repository/{NoteRepository,SqlDelightNoteRepository}.kt`, `core/src/commonMain/kotlin/com/playbook/core/platform/PlatformDefaults.kt`, `core/src/androidMain/kotlin/com/playbook/core/platform/PlatformDefaults.android.kt`, `core/src/iosMain/kotlin/com/playbook/core/platform/PlatformDefaults.ios.kt`, `core/src/commonTest/kotlin/com/playbook/core/repository/NoteRepositoryCheck.kt`, `core/src/androidUnitTest/kotlin/com/playbook/core/repository/NoteRepositoryAndroidTest.kt`, `core/src/iosTest/kotlin/com/playbook/core/repository/NoteRepositoryIosTest.kt`, `core/src/commonTest/kotlin/com/playbook/core/db/NotePersistenceCheck.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/specs/note-model-crud-core.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: sin wiring del repositorio en la app (intencional; `notes-list-ui` lo instanciará con los defaults de plataforma). La verificación de arranque real (Android/iOS) no forma parte de esta feature porque no toca el path de arranque; `init.sh` sigue sin simuladores por diseño. Terminología residual "categoría/tipo" persiste en otras entradas de `feature_list.json` ajenas a esta feature.
- Next best step: validación independiente de `note-model-crud-core` con `$feature-validator`; tras el `accept`, crear spec de `notes-list-ui` con `$feature-spec`.

### Session 008

- Date: 2026-10-05
- Goal: validación independiente de `note-model-crud-core` (spec `docs/specs/note-model-crud-core.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `note-model-crud-core` promovida a `accepted` en `feature_list.json` con evidencia de validación.
- Verification run:
  - `./init.sh` → exit 0.
  - `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test --rerun-tasks` → NoteRepository 1/1 + NotePersistence 2/2 + Greeting 1/1 en cada plataforma, 0 failures.
  - `:composeApp:assembleDebug` + link del framework iOS con `--rerun-tasks` → BUILD SUCCESSFUL.
  - Esquema: `Schema.version = 1`, sin `.sqm`; solo queries `updateNote`/`deleteNoteById`.
  - Control negativo propio del validador (romper `updated_at = clock()` en `update`) hace fallar `crudRoundTrip`; restaurado.
  - Verificado: `Track.fromCode(null|desconocido) -> null`; `NoteStatus.fromCode(desconocido)` fail-fast; `update` preserva `owner`/`created_at`; sin rastros de tags/etiquetas/categoría en `core/src`.
- Evidence captured: línea de validación en el arreglo `evidence` de `note-model-crud-core` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: `getAll()` sin `ORDER BY` (fuera de alcance; considerar orden determinista en `notes-list-ui`). Terminología residual "categoría/tipo/etiquetas" en otras entradas de `feature_list.json`, a normalizar cuando se re-deriven.
- Next best step: crear spec de `notes-list-ui` con `$feature-spec` e implementarla con `$feature-implementer`.

### Session 009

- Date: 2026-10-05
- Goal: implementar `notes-list-ui` (spec `docs/specs/notes-list-ui.md`).
- Completed:
  - `selectAllNotes` en `Note.sq` pasa a `ORDER BY updated_at DESC, id DESC` (cambio de query, no de esquema: sigue v1, sin `.sqm`).
  - `verifyNoteCrud` extendido con la aserción de orden determinista (incluye empates de `updated_at` desempatados por `id` y comparación con re-ordenar en Kotlin).
  - `NotesListScreen.kt` (commonMain) con estado vacío ("Todavía no hay notas", sin CTA) y estado con notas (título "Notas" + `LazyColumn` de `Card`s; `body` a máx. 2 líneas con ellipsis; chips de texto `track.code`/`status.code` y `Sin track` para `track == null`).
  - `App(noteRepository)` recibe el repositorio, lee con `remember(noteRepository) { noteRepository.getAll() }` y delega en `NotesListScreen`; sin DI/ViewModel/Flow.
  - Wiring en `MainActivity`/`MainViewController`: `SqlDelightNoteRepository(database, ::randomNoteId, ::currentTimeMillis)` tras `createDatabase(...)` y pasado a `App(...)`.
  - `ARCHITECTURE.md` (nueva sección de UI y wiring) y `docs/technical-discovery.md` (UI Decisions) actualizados; `AGENTS.md` (línea "Próxima feature en cola") corregido; nota obsoleta de `DESIGN.md` en `feature_list.json` corregida; tasks de la spec marcadas.
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL; Android: NoteRepository 1/1 (con aserción de orden), NotePersistence 2/2, Greeting 1/1; iOS: idem, 0 failures.
  - Control negativo: `selectAllNotes` con `ORDER BY id` hace fallar `crudRoundTrip` en Android/JVM (AssertionError); restaurado a `ORDER BY updated_at DESC, id DESC`.
  - `xcodebuild` scheme iosApp (iPhone 15, iOS 17.2) → BUILD SUCCEEDED.
  - `schema.version = 1`, sin `.sqm`; sin dependencias nuevas; `init.sh` sin cambios.
  - `./init.sh` → exit 0, sin procesos de larga duración.
  - Smoke Android (Pixel_3A_API_34/emulator-5554): `pm clear` + launch → UI dump vacío correcto; base sembrada por `sqlite3` → lista en orden `seed-1, seed-3, seed-2, seed-4`, body truncado, chips de texto y `Sin track`.
  - Smoke iOS (simulador iPhone 15, iOS 17.2): idem vacío y sembrado, verificado por OCR (macOS Vision) sobre las capturas.
- Evidence captured: arreglo `evidence` de `notes-list-ui` en `feature_list.json` (8 líneas); capturas en el directorio ignorado `composeApp/build/smoke-evidence/` y en el temp de QA.
- Files or artifacts updated: `core/src/commonMain/sqldelight/com/playbook/core/db/Note.sq`, `core/src/commonTest/kotlin/com/playbook/core/repository/NoteRepositoryCheck.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt` (nuevo), `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt`, `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `AGENTS.md`, `docs/specs/notes-list-ui.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: la lectura de `getAll()` ocurre en el hilo de composición y no hay refresco tras mutaciones (por diseño; lo resolverá `create-text-note`). No hay harness de UI/E2E persistente: la UI se verificó manualmente con OCR en ambas plataformas y el path de datos con tests del core. Las bases sembradas para QA se limpiaron de emulador/simulador; no se commiteó código de seed.
- Next best step: validación independiente de `notes-list-ui` con `$feature-validator`; tras el `accept`, crear spec de `create-text-note` con `$feature-spec`.

### Session 010

- Date: 2026-10-05
- Goal: validación independiente de `notes-list-ui` (spec `docs/specs/notes-list-ui.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `notes-list-ui` promovida a `accepted` en `feature_list.json` con evidencia de validación.
- Verification run:
  - `./init.sh` → exit 0.
  - `:core:testDebugUnitTest` + `:core:iosSimulatorArm64Test` + `:composeApp:assembleDebug` + link del framework iOS con `--rerun-tasks` → BUILD SUCCESSFUL (66 tareas), 0 failures.
  - Control negativo propio del validador (cambiar `ORDER BY updated_at DESC, id DESC` → `ORDER BY id`) hace fallar `crudRoundTrip`; restaurado.
  - `Schema.version = 1`, sin `.sqm`; sin cambios de Gradle/init.sh/iosApp; sin código de seed.
  - Smoke iOS independiente (iPhone 15, iOS 17.2): estado vacío y lista ordenada `seed-1, seed-3, seed-2, seed-4` con chips de texto. Smoke Android no re-ejecutado (sin emulador/adb en el entorno del validador); evidencia textual consistente.
- Evidence captured: línea de validación en el arreglo `evidence` de `notes-list-ui` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: lectura en el hilo de composición sin refresco tras mutaciones (deuda aceptada, la aborda `create-text-note`). Smoke Android no reproducido por el validador (Low). Terminología residual "categoría/tipo/etiquetas" en otras entradas de `feature_list.json`.
- Next best step: crear spec de `create-text-note` con `$feature-spec` e implementarla con `$feature-implementer`.

### Session 011

- Date: 2026-10-08
- Goal: implementar `create-text-note` (spec `docs/specs/create-text-note.md`).
- Completed:
  - `App.kt` reescrito: hoistea `notes`/`destination`/`refreshKey` con `mutableStateOf`, carga con `LaunchedEffect` (sin lectura en el cuerpo de composición), define `NotesDestination` (`List`/`Create`/`Edit(noteId)`) y `LOCAL_OWNER_ID = "local"`, y hace `reload()` (incremento de `refreshKey`) tras crear/editar/borrar. Sin DI/ViewModel/Flow/coroutines propias ni librería de navegación.
  - `NotesListScreen.kt`: `Scaffold` con `ExtendedFloatingActionButton` "Nueva nota", CTA "Crear primera nota" en el estado vacío y `Card`s clickeables (`clickable(onClickLabel = "Editar nota", role = Role.Button)`).
  - `NoteEditorScreen.kt` (nuevo): body multilínea (`OutlinedTextField`), selector de `track` ("Sin track" + `Track.entries`, selección con marca textual "✓", borde y color), `Guardar` deshabilitado si `body.isNotBlank()` es falso, `Cancelar`, y en modo edición `Borrar` con `AlertDialog` "¿Borrar esta nota?".
  - `ARCHITECTURE.md` (sección UI/refresco/navegación local) y `docs/technical-discovery.md` (nueva "UI Decisions (create-text-note)") actualizados; `AGENTS.md` (línea "Próxima feature en cola"); tasks de la spec marcadas.
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL (66 tareas, 19s); Android/JVM 1/2/1 tests, iOS 1/2/1 tests, 0 failures.
  - `xcodebuild` scheme iosApp (iPhone 15, iOS 17.2) → BUILD SUCCEEDED; app instalada/lanzada con PID vivo, sin crash reports; `playbook.db` con tabla `note` y `user_version=1`.
  - Smoke Android (Pixel_3A_API_34/emulator-5554): estado vacío con CTA/FAB; crear con `track`; validación (Guardar `enabled=false` con body blank); editar con reordenamiento al tope y `track`/`status` preservados; borrar con confirmación (Cancelar no borra; Borrar sí); borrar la última → estado vacío; persistencia tras force-stop/relaunch. Capturas y UI dumps en `composeApp/build/smoke-evidence/`.
  - Smoke iOS limitado: OCR del estado vacío y de una fila sembrada (luego eliminada). El CRUD interactivo en iOS **no** pudo ejecutarse: sin `idb`/`cliclick` y `osascript`/System Events bloqueado por permisos. No se fabricó evidencia.
  - `schema.version = 1`, sin `.sqm`; sin dependencias nuevas; `git status` sólo con archivos de la feature.
  - `./init.sh` → exit 0, sin procesos de larga duración.
- Evidence captured: arreglo `evidence` de `create-text-note` en `feature_list.json`; capturas/UI dumps en `composeApp/build/smoke-evidence/android-*` e `ios-*`.
- Files or artifacts updated: `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/NoteEditorScreen.kt` (nuevo), `ARCHITECTURE.md`, `docs/technical-discovery.md`, `AGENTS.md`, `docs/specs/create-text-note.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: la lectura de `getAll()` sigue en el main thread (dentro de `LaunchedEffect`); aceptable para tabla local pequeña. El back físico Android/iOS no está manejado (sólo "Cancelar"). Smoke de CRUD iOS no automatizable en este entorno (limitación de tooling, no del código). No hay E2E persistente (la spec lo justifica).
- Next best step: validación independiente de `create-text-note` con `$feature-validator`; tras el `accept`, crear spec de `note-category-and-tags` con `$feature-spec`.

### Session 012

- Date: 2026-10-08
- Goal: implementar `ai-client-interface` (spec `docs/specs/ai-client-interface.md`).
- Completed:
  - Puerto `AiClient` en `com.playbook.core.ai` (`AiClient`, `Embedding`, `GenerateRequest`, `AiClientException`); `embed`/`generate` son `suspend`.
  - `FakeAiClient(dimension = 8)` determinista y sin red en `commonMain`: vectores derivados de `String.hashCode()` vía un LCG de 32 bits, `embed(emptyList()) == emptyList()`, `require(dimension > 0)`, `generate` no vacío. Es el adaptador por defecto inyectable.
  - Tests compartidos en `commonTest`: `verifyAiClientContract(client)`, `verifyFakeAiClient()` y `ConstantAiClient` (segunda implementación test-only para demostrar sustitución sin tocar producción).
  - `AiClientAndroidTest` (JVM, `runTest`) e `AiClientIosTest` (iOS, `runTest`) corren el contrato contra `FakeAiClient` y `ConstantAiClient` más el comportamiento del fake.
  - `gradle/libs.versions.toml` + `core/build.gradle.kts`: `kotlinx-coroutines` 1.10.1 (`-core` commonMain, `-test` commonTest). `init.sh` sin cambios.
  - `ARCHITECTURE.md` (nueva sección AI + dirección de dependencia), `docs/technical-discovery.md` (AI Decisions) y `docs/risks-and-open-questions.md` (el puerto ya existe) actualizados; tasks de la spec marcadas.
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL (20 tasks executed); Android/JVM AiClientAndroidTest 3/3 + NotePersistence 2/2 + NoteRepository 1/1 + Greeting 1/1; iOS AiClientIosTest 3/3 + NotePersistence 2/2 + NoteRepository 1/1 + Greeting 1/1; 0 failures.
  - `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (57 tasks).
  - `kotlinx-coroutines` resuelto en 1.10.1 (compatible con Kotlin 2.1.21); `Schema.version = 1`, sin `.sqm`; sin cambios en `:composeApp`/`iosApp`/`init.sh`/`Note.sq`.
  - `./init.sh` → exit 0, sin procesos de larga duración.
  - Control de sustitución: el contrato pasa con `ConstantAiClient` además de `FakeAiClient`, sin modificar archivos de producción de `:core`.
  - Control negativo propio: forzar temporalmente `FakeAiClient.embed` a `Embedding(emptyList())` hace fallar `fakeSatisfiesContract` y `fakeBehaviourIsDeterministic` (2 failed); restaurado y re-ejecutado en verde 3/3.
- Evidence captured: arreglo `evidence` de `ai-client-interface` en `feature_list.json` (7 líneas).
- Files or artifacts updated: `core/src/commonMain/kotlin/com/playbook/core/ai/{AiClient,FakeAiClient}.kt`, `core/src/commonTest/kotlin/com/playbook/core/ai/AiClientContract.kt`, `core/src/androidUnitTest/kotlin/com/playbook/core/ai/AiClientAndroidTest.kt`, `core/src/iosTest/kotlin/com/playbook/core/ai/AiClientIosTest.kt`, `gradle/libs.versions.toml`, `core/build.gradle.kts`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `docs/specs/ai-client-interface.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: sin wiring de `AiClient` a la app (intencional; no hay consumidor de IA todavía). El runtime real y la dimensión concreta del embedding siguen abiertos para `ai-runtime-decision`. No hay harness E2E persistente y no aplica aquí (sin flujo observable).
- Next best step: validación independiente de `ai-client-interface` con `$feature-validator`; tras el `accept`, crear spec de `create-text-note` con `$feature-spec`.

### Session 013

- Date: 2026-10-08
- Goal: validación independiente de `create-text-note` (spec `docs/specs/create-text-note.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `create-text-note` promovida a `accepted` en `feature_list.json` con evidencia de validación; commit `feature: complete create-text-note` creado por el orquestador.
- Verification run:
  - `./init.sh` → exit 0, sin dev servers ni simuladores.
  - `:core:testDebugUnitTest --rerun-tasks` y `:core:iosSimulatorArm64Test --rerun-tasks` → 1/2/1 en cada plataforma, 0 failures; `assembleDebug` + link del framework iOS `--rerun-tasks` → BUILD SUCCESSFUL.
  - `Schema.version = 1`, sin `.sqm`; sin dependencias nuevas; `git status` sólo con los archivos de la feature; `core/**`/build files/`init.sh`/`iosApp/**`/`MainActivity`/`MainViewController` sin cambios.
  - Checks dirigidos del validador: `getAll()` sólo dentro de `LaunchedEffect(refreshKey)` (`App.kt:41`) y `reload()` tras create/update/delete; `Guardar` `enabled=false` con body blank en `android-02-editor-create-empty.xml`; `AlertDialog` de borrado y estado vacío tras borrar la última; orden `updated_at DESC, id DESC` cubierto por `verifyNoteCrud`.
  - Findings Low no bloqueantes: CRUD interactivo iOS no automatizado (limitación de tooling, declarada honestamente) y `remember` del editor sin key (seguro por el flujo actual).
- Evidence captured: línea de validación en el arreglo `evidence` de `create-text-note` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: la lectura de `getAll()` sigue en el main thread (dentro de `LaunchedEffect`) y el back físico Android/iOS no está manejado (sólo "Cancelar"); ambos aceptados en la spec. Smoke de CRUD iOS sigue sin automatización en este entorno.
- Next best step: crear spec de `note-category-and-tags` con `$feature-spec` e implementarla con `$feature-implementer`.

### Session 014

- Date: 2026-10-08
- Goal: validación independiente de `ai-client-interface` (spec `docs/specs/ai-client-interface.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `ai-client-interface` promovida a `accepted` en `feature_list.json` con evidencia de validación.
- Verification run:
  - `./init.sh` → exit 0 con el gate real; `init.sh` sin cambios (`git diff` vacío).
  - `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; AiClient 3/3 + NotePersistence 2/2 + NoteRepository 1/1 + Greeting 1/1 en cada plataforma, 0 failures (conteos de `core/build/test-results/*/TEST-*.xml`).
  - `:composeApp:assembleDebug` + link del framework iOS con `--rerun-tasks` → BUILD SUCCESSFUL (57 tasks).
  - `kotlinx-coroutines-core/-core-jvm:1.10.1` resuelto (`androidDebugCompileClasspath`), compatible con Kotlin 2.1.21.
  - Control negativo propio del validador (cambiar `ConstantAiClient.embed` a `emptyList()`) hace fallar `constantSatisfiesContract` y se restaura; re-ejecutado en verde.
  - `Schema.version = 1`, sin `.sqm`; sin cambios en `:composeApp`/`iosApp`/`init.sh`/`Note.sq`; sin hallazgos de seguridad.
- Evidence captured: línea de validación en el arreglo `evidence` de `ai-client-interface` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: hallazgos Low no bloqueantes — el contrato no aserta el mapeo posicional `result[i] ↔ texts[i]` y `AiClientException` aún no se lanza (seam para adaptadores reales). Sin wiring de `AiClient` a la app (intencional; no hay consumidor todavía). El runtime real/dimensión del embedding siguen abiertos para `ai-runtime-decision`.
- Next best step: crear spec de `create-text-note` con `$feature-spec` e implementarla con `$feature-implementer`.

### Session 015

- Date: 2026-10-09
- Goal: implementar `android-release-pipeline` (spec `docs/specs/android-release-pipeline.md`).
- Completed:
  - `composeApp/build.gradle.kts`: carga de credenciales con precedencia **env > `keystore.properties`** (`PLAYBOOK_KEYSTORE_FILE`/`_PASSWORD`, `PLAYBOOK_KEY_ALIAS`/`_PASSWORD`), `hasReleaseSigning` y registro condicional de `signingConfigs.create("release")` asignado al build type `release` (`isMinifyEnabled = false`). Si falta alguna de las 4, no se registra el signing config y el release queda **sin firmar** (modo degradado) sin fallar.
  - Override de `versionCode`/`versionName` vía `-Pplaybook.versionCode`/`-Pplaybook.versionName` con defaults `1`/`"1.0"`.
  - `.gitignore`: ignora `keystore.properties`, `*.jks`, `*.keystore`. Nuevo `keystore.properties.example` (plantilla sin secrets).
  - Nuevo runbook `docs/release/android.md` (generación de keystore, config por archivo/env, build, verificación de firma, Play App Signing y subida manual a internal testing; deja explícito local vs Play Console).
  - `ARCHITECTURE.md` (nueva sección "Android release"), `docs/technical-discovery.md` ("Deployment Decisions (android-release-pipeline)") y `AGENTS.md` (estado + puntero al runbook) actualizados; tasks de la spec marcadas.
- Verification run:
  - Modo degradado (sin secrets): `./gradlew :composeApp:assembleRelease` → BUILD SUCCESSFUL; `composeApp-release-unsigned.apk` (6.88 MB). Re-confirmado desde estado limpio.
  - Firma por env (keystore descartable): `assembleRelease`+`bundleRelease` BUILD SUCCESSFUL; `composeApp-release.apk` y `composeApp-release.aab`. `apksigner verify --print-certs` → exit 0, cert `CN=Playbook Verify...` SHA-256 `e1187c9d…`; `jarsigner -verify` AAB → `jar verified.`
  - Firma por `keystore.properties` (storeFile relativo, resuelto contra la raíz): mismo resultado firmado; `apksigner` cert SHA-256 `2a7069bd…` (coincide con el keystore descartable; distinto del de debug) y AAB `jar verified.`
  - Precedencia env > archivo: con env apuntando a un keystore (`CN=Playbook ENV`, SHA-256 `53fede45…`) y `keystore.properties` a otro distinto (`CN=Playbook FILE`, SHA-256 `71e592a0…`), el APK queda firmado con el de env (`53fede45…`).
  - Instalabilidad (opcional): `adb -s emulator-5554 install -r` del APK release → Success; launch → PID vivo, sin `FATAL EXCEPTION`.
  - Secretos: `git check-ignore -v` matchea `keystore.properties`/`*.jks`/`*.keystore`; `git status --porcelain` sin secrets.
  - Versionado: default `1`/`"1.0"`; con `-Pplaybook.versionCode=7 -Pplaybook.versionName=1.2.3` → `output-metadata.json` 7 / `"1.2.3"`.
  - No regresión: `./init.sh` → exit 0; tests del core 7/7 en Android/JVM e iOS, 0 failures; sin cambios en `core/**`, `gradle/libs.versions.toml`, `iosApp/**`, `init.sh` ni `Note.sq`; esquema v1, sin `.sqm`.
  - Limpieza: se borraron `keystore.properties`, el keystore copiado a `composeApp/build/verify-release/` y el keystore descartable externo; no queda ningún keystore en el repo.
- Evidence captured: arreglo `evidence` de `android-release-pipeline` en `feature_list.json` (10 líneas).
- Files or artifacts updated: `composeApp/build.gradle.kts`, `.gitignore`, `keystore.properties.example` (nuevo), `docs/release/android.md` (nuevo), `ARCHITECTURE.md`, `docs/technical-discovery.md`, `AGENTS.md`, `docs/specs/android-release-pipeline.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: Scenario 6 (subida real al track internal testing de Play Console e instalación desde el canal) **no ejecutado**: requiere cuenta de Play Console y no es verificable en el repo; documentado como paso manual. `apksigner` vive en `build-tools` (presente 37.0.0) y `adb` no está en `PATH` (se usó la ruta del SDK). iOS/TestFlight fuera de alcance (`ios-release-pipeline`).
- Next best step: validación independiente de `android-release-pipeline` con `$feature-validator`; tras el `accept`, crear spec de `note-category-and-tags` con `$feature-spec`.

### Session 016

- Date: 2026-10-09
- Goal: validación independiente de `android-release-pipeline` (spec `docs/specs/android-release-pipeline.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `android-release-pipeline` promovida a `accepted` en `feature_list.json` con evidencia de validación; commit `build: complete android-release-pipeline` creado por el orquestador y PR draft contra `develop`.
- Verification run:
  - Alcance: `git diff HEAD` de `init.sh`/`core/**`/`gradle/libs.versions.toml`/`iosApp/**`/`Note.sq` vacío; `Note.sq`/esquema intactos.
  - Scenario 1 (degradado): sin secrets, `./gradlew :composeApp:assembleRelease` → BUILD SUCCESSFUL (84 tasks); `composeApp-release-unsigned.apk` (6.88 MB); `apksigner` → `DOES NOT VERIFY: Missing META-INF/MANIFEST.MF`.
  - Scenario 3 (env, keystore propio del validador `CN=Playbook ENV`): `assembleRelease`+`bundleRelease` BUILD SUCCESSFUL; `apksigner --print-certs` exit 0 con el cert del keystore; `jarsigner -verify` del AAB → `jar verified.`
  - Scenario 2 (file, keystore `CN=Playbook FILE`, `storeFile` relativo): BUILD SUCCESSFUL; cert = keystore del archivo; AAB `jar verified.`
  - Precedencia env > archivo reproducida (cert del APK = keystore de env). Certificados ≠ debug (`~/.android/debug.keystore`).
  - Versionado: `-Pplaybook.versionCode=7 -Pplaybook.versionName=1.2.3` → `7`/`1.2.3`; default `1`/`"1.0"`; inválido `abc` → `1`.
  - Scenario 5 (secretos): `git check-ignore -v` matchea `.gitignore:21/22/23`; `git status --porcelain` sin secrets; `keystore.properties.example` sólo placeholders y no ignorado.
  - Scenario 7 (gate): `./init.sh` exit 0 sin procesos de larga duración; `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test --rerun-tasks` 7/7 en cada plataforma, 0 failures.
  - Scenario 6 (Play Console): verificado que está marcado honestamente como **no ejecutado/manual** en spec, `feature_list.json` y runbook; sin evidencia fabricada.
  - Limpieza: keystores descartables y `keystore.properties` eliminados; `git status --porcelain` idéntico al baseline.
- Evidence captured: línea de validación en el arreglo `evidence` de `android-release-pipeline` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md` (por el orquestador al persistir la aceptación).
- Known risk or unresolved issue: hallazgos Low no bloqueantes — los hashes exactos de certificados de keystores descartables ya borrados no son replays exactos (el mecanismo sí es reproducible); el runbook deriva `APKSIGNER` de `$ANDROID_HOME` (adaptar si sólo hay `sdk.dir` en `local.properties`); `jarsigner` emite warnings benignos de self-signed/no-timestamp. La subida real a Play internal testing sigue siendo un paso externo manual.
- Next best step: crear spec de `note-category-and-tags` (o `ios-release-pipeline`/`ai-runtime-decision`) con `$feature-spec` e implementarla con `$feature-implementer`.
