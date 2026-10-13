# Progress Log

## Current Verified State

- Repository root: `/Users/javierrodriguez/Alt10/course/ai/devexpert/learn-aidevexpert-gamebook`
- Standard startup path: `./init.sh`
- Standard verification path: `./init.sh` corre `:composeApp:assembleDebug`, `:core:testDebugUnitTest`, `:composeApp:linkDebugFrameworkIosSimulatorArm64` y (en macOS) `:core:iosSimulatorArm64Test`, sin levantar dev servers.
- Última feature `accepted`: `semantic-linking` (2026-10-13; Session 034; enlazado por similitud coseno, esquema v5 + `4.sqm`; validación independiente `accept`); antes `ai-enrichment-classify-tags-type` (Session 032).
- `ai-client-interface` `accepted`: puerto `AiClient` + `FakeAiClient` en `:core` (runtime sustituible, sin wiring a la app todavía).
- `ai-runtime-decision` `accepted` (2026-10-09): ADR 0001 (híbrido por fases, cloud-first, detrás de `AiClient`) + spike test-only sin red + docs; validada de forma independiente.
- `android-release-pipeline` `accepted`: signing de release Android condicional (precedencia env `PLAYBOOK_*` > `keystore.properties`, keystore fuera del repo) y runbook `docs/release/android.md`.
- `embeddings-generation` `accepted`: tabla `embedding` (esquema v2 con `1.sqm`, FK cascade) + `EmbeddingRepository` + `NoteIndexingService` como primer consumidor de `AiClient`.
- `voice-capture-stt` `accepted`: puerto `VoiceTranscriber` + adaptadores nativos (`SpeechRecognizer` en Android; `SFSpeechRecognizer`+`AVAudioEngine` en iOS) en `:composeApp`; el dictado crea una `Note` transcripta vía `create(NoteDraft(...))`. Sin cambios en `:core`.
- `note-category-and-tags` `accepted` (2026-10-09): Etiquetas libres, múltiples y editables en la Nota; esquema v3 (`note_tag`, migración `2.sqm` v2→v3); clasificación fija por `Track` ya existente.
- `image-capture-camera` `accepted` (2026-10-09): captura de imagen (cámara/galería) → Nota con Adjunto; tabla `attachment` (esquema **v4**, migración `3.sqm` v3→v4) + `AttachmentRepository` + integración nativa Android/iOS.
- `image-ocr-optional` `accepted` (2026-10-13): feature **opcional/min** resuelta como **decisión + documentación** — **DISCARD del OCR para el MVP** (validación independiente `accept`). Entregable: `docs/adr/0002-ocr-decision.md` (MADR-lite, promovido a `accepted`) + docs durables actualizados; sin código/deps/esquema.
- `gdd-view` `accepted` (2026-10-13): primera vista de GDD — proyección pura de las Notas por `track` (`buildGdd`/`GddSection` en `:core`) + pantalla `GddScreen` (secciones por track, conteo global/por sección, chips de Etiquetas/`status`/"Imagen", abrir nota y "Volver"), sin tabla ni esquema nuevos (v4). Validada de forma independiente.
- `ios-release-pipeline-scaffolding` `passing`: artefactos repo-side de release iOS verificados (scripts, scheme compartido, firma/identidad, versionado, `.gitignore`, runbook; Scenarios 1/2/7/8). Requiere validación independiente para `accepted`.
- `ios-release-pipeline-first-upload` `blocked`: archive firmado + export `.ipa` + upload a TestFlight + device real (Scenarios 3–6) requieren Apple Developer Program + `TEAM_ID` + credenciales, ausentes en el entorno. Probado con `scripts/ios-release/preflight.sh` → exit 1. Ver `docs/release/ios-testflight.md`.
- `ai-enrichment-classify-tags-type` `accepted` (2026-10-13; validación independiente `accept` en `docs/validations/ai-enrichment-classify-tags-type.md`): indexado asíncrono + transiciones de `NoteStatus`. `NoteIndexingOrchestrator.index(note)` reutiliza `NoteIndexingService` y persiste: éxito→`INDEXED`; fallo→`PENDING` salvo Nota ya `INDEXED` (no se degrada); **nunca** escribe `FAILED`. `NoteRepository.updateStatus` (query `updateNoteStatus`, sólo `status`, sin re-sellar `updated_at`) sin cambio de esquema (sigue v4). Alcance re-derivado: la IA no clasifica ni etiqueta.
- `semantic-linking` `accepted` (2026-10-13; validación independiente `accept`): Enlaces Nota↔Nota por similitud coseno (esquema **v5**, migración `4.sqm` v4→v5, tabla `note_link` con PK canónica `note_a_id < note_b_id` + FK cascade); `CosineSimilarity`, `NoteLink`/`NoteLinkMappers`/`otherNoteId`, `NoteLinkRepository`/`SqlDelightNoteLinkRepository` y `SemanticLinkingService.recomputeAll()` + `LinkingSummary` (umbral `0.8` inclusivo e inyectable). Core puro, sin UI ni wiring.
- Current next ready features: `note-links-ui` (depende de `semantic-linking`, ya `accepted`), `voice-attachment-storage` y `rag-query` (dependencias ya `accepted`); requieren spec. `note-enrichment-status-ui` y `offline-pending-retry` quedan dependency-ready (`ai-enrichment-classify-tags-type` ya `accepted`); requieren spec.
- Current blocker: `ios-release-pipeline-first-upload` (account-gated).
- Last verified at: 2026-10-13.

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
- Goal: implementar `ai-runtime-decision` (spec `docs/specs/ai-runtime-decision.md`).
- Completed:
  - ADR `docs/adr/0001-ai-runtime-decision.md` (formato MADR-lite): contexto, 7 criterios, 3 alternativas (cloud-only / on-device-only / híbrido por fases) con trade-offs puntuados, decisión **híbrida por fases, cloud-first** detrás de `AiClient`, consecuencias, evidencia y preguntas abiertas; política durable de no versionar keys y sonda manual key-gated (`AI_RUNTIME_PROBE_KEY`, fuera del gate).
  - Harness test-only en `:core`: `core/src/commonTest/kotlin/com/playbook/core/ai/spike/AiRuntimeSpike.kt` con `LocalSpikeAiClient` (on-device-like), `RemoteSpikeAiClient` (cloud-like sin red, `failMode`), `runAiRuntimeSpike(): String` (contrato, offline, mapeo de fallo a `AiClientException` + degradación, sustituibilidad, control negativo y latencia local informativa) y `verifyContractRejectsBrokenClient()`.
  - Runners `@Test` con `runTest` en `core/src/androidUnitTest/.../spike/AiRuntimeSpikeAndroidTest.kt` y `core/src/iosTest/.../spike/AiRuntimeSpikeIosTest.kt` (imprimen el reporte).
  - Reporte `docs/spikes/ai-runtime-spike.md`: metodología, cómo reproducir, stdout capturado (Android/JVM e iOS), medido vs estimado (con fuentes fechadas), control negativo y límites.
  - `ARCHITECTURE.md` (sección AI: runtime decidido + política de secrets + evidencia), `docs/technical-discovery.md` (nueva "AI Decisions (ai-runtime-decision)"), `docs/risks-and-open-questions.md` (riesgo principal movido a "Resolved"; research task del spike marcado hecho) y `AGENTS.md` (estado/próxima feature) actualizados; tasks de la spec marcadas.
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL in 1m45s (66 tasks executed); Android/JVM `AiRuntimeSpikeAndroidTest` 2/2 + AiClient 3/3 + NotePersistence 2/2 + NoteRepository 1/1 + Greeting 1/1 (0 failures); iOS `AiRuntimeSpikeIosTest` 2/2 + AiClient 3/3 + NotePersistence 2/2 + NoteRepository 1/1 + Greeting 1/1 (0 failures).
  - Reporte del spike capturado de `core/build/test-results/*/TEST-*.xml`: contrato OK en ambos arquetipos, offline/determinismo OK, mapeo de fallo + degradación OK, control negativo OK, sustituibilidad OK; latencia local informativa Android/JVM `elapsed_us=683 / ns_per_note=1138`, iOS `elapsed_us=798 / ns_per_note=1330` (medida del fake, no de un modelo real).
  - Control negativo manual: forzar `LocalSpikeAiClient.embed` a `Embedding(emptyList())` hace fallar `AiRuntimeSpikeAndroidTest.reportsRuntimeArchetypes` (`tests=2 failures=1`, `AssertionError: la dimensión del embedding debe ser > 0`); restaurado y re-ejecutado en verde.
  - `./init.sh` → exit 0, sin dev servers/simuladores/red; `git diff -- init.sh` vacío.
  - Sin deps nuevas (`git diff` vacío en build files/`libs.versions.toml`); `git status` sólo documentación + harness en source sets de test; sin keys/secrets; `:composeApp`/`iosApp`/`core/src/commonMain`/esquema SQLDelight/puerto/UI sin cambios.
- Evidence captured: arreglo `evidence` de `ai-runtime-decision` en `feature_list.json`; reporte en `docs/spikes/ai-runtime-spike.md`; salida XML en `core/build/test-results/`.
- Files or artifacts updated: `docs/adr/0001-ai-runtime-decision.md` (nuevo), `docs/spikes/ai-runtime-spike.md` (nuevo), `core/src/commonTest/kotlin/com/playbook/core/ai/spike/AiRuntimeSpike.kt` (nuevo), `core/src/androidUnitTest/kotlin/com/playbook/core/ai/spike/AiRuntimeSpikeAndroidTest.kt` (nuevo), `core/src/iosTest/kotlin/com/playbook/core/ai/spike/AiRuntimeSpikeIosTest.kt` (nuevo), `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `AGENTS.md`, `docs/specs/ai-runtime-decision.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: la latencia local mide el fake determinista, no un modelo on-device real; latencia/calidad/costo cloud reales no son ejecutables en el gate (documentados como estimación, no medición). Proveedor/modelo/dimensión, gestión de keys y runtime on-device concreto quedan como follow-ups del ADR. ADR en `status: propuesto` hasta la validación independiente.
- Next best step: validación independiente de `ai-runtime-decision` con `$feature-validator`; tras el `accept`, crear spec de `note-category-and-tags` con `$feature-spec`.

### Session 016

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

### Session 017

- Date: 2026-10-09
- Goal: validación independiente de `ai-runtime-decision` (spec `docs/specs/ai-runtime-decision.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `ai-runtime-decision` promovida a `accepted` en `feature_list.json` con evidencia de validación; ADR 0001 promovido de `propuesto` a `accepted`; commit `feature: complete ai-runtime-decision` creado por el orquestador.
- Verification run:
  - `./init.sh` → exit 0, sin dev servers/simuladores/red; `init.sh` sin cambios.
  - `:core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL (66 tasks); AiRuntimeSpike 2/2 + AiClient 3/3 + NotePersistence 2/2 + NoteRepository 1/1 + Greeting 1/1 en cada plataforma, 0 failures.
  - Reporte del spike reproducido: contrato OK en ambos arquetipos, offline/determinismo OK, mapeo de fallo + degradación OK, control negativo OK, sustituibilidad OK; latencia local informativa (varía por corrida, no asertada).
  - Control negativo propio del validador (forzar `LocalSpikeAiClient.embed` a `emptyList()`) hace fallar `reportsRuntimeArchetypes` (tests=2 failures=1); restaurado (hash idéntico) y re-verificado en verde.
  - Confirmado el ADR (MADR-lite, 3 alternativas, trade-offs, decisión, consecuencias, evidencia, preguntas abiertas) y la separación medido vs estimado; sin wiring de IA en la app, sin deps de producción de IA, sin keys/secrets; `:composeApp`/`iosApp`/`commonMain`/`androidMain`/`iosMain`/esquema/puerto/UI sin cambios.
- Evidence captured: línea de validación en el arreglo `evidence` de `ai-runtime-decision` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `docs/adr/0001-ai-runtime-decision.md`, `AGENTS.md`.
- Known risk or unresolved issue: findings Low no bloqueantes — las citas de lo estimado usan categoría/fecha pero sin URLs concretas, y el `status` del ADR se promovió al persistir la aceptación. El runtime real (proveedor/modelo/dimensión, gestión de keys, on-device de fase 2) queda como follow-up de `embeddings-generation`/`rag-query`.
- Next best step: la primera feature lista es `note-category-and-tags` (depende de `create-text-note`); crear su spec con `$feature-spec` e implementarla con `$feature-implementer`. `embeddings-generation` también está lista (depende de `ai-client-interface` y `note-model-crud-core`, ambas `accepted`).

### Session 018

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

### Session 019

- Date: 2026-10-09
- Goal: implementar `embeddings-generation` (spec `docs/specs/embeddings-generation.md`).
- Completed:
  - Esquema **v2**: nueva tabla `embedding` 1:0..1 con FK `note(id) ON DELETE CASCADE` en `core/src/commonMain/sqldelight/com/playbook/core/db/embedding.sq` y primera migración `1.sqm` (v1 → v2). `PlaybookDatabase.Schema.version` pasa a 2; los drivers migran automáticamente por `user_version`.
  - `NoteEmbedding` + `NoteEmbeddingMappers` (alias `Embedding as EmbeddingRow`, `check` de dimensión) y `EmbeddingBlobCodec` (internal, big-endian explícito con `Float.toBits()`/`fromBits()`, 4 bytes/valor, `decode` exige múltiplo de 4).
  - `EmbeddingRepository` + `SqlDelightEmbeddingRepository(database, clock)` (upsert/select/getAll/deleteByNoteId; `INSERT OR REPLACE`, `rowsAffected > 0`).
  - `NoteIndexingService` + `IndexingResult` (primer consumidor de `AiClient`): valida 1 vector no vacío, persiste vía `EmbeddingRepository`; fallo/respuesta inválida → `Failed` sin escribir ni pisar; no toca `NoteStatus`.
  - `createDatabase` agrega `PRAGMA foreign_keys = ON` (con `execute`, después del `PRAGMA user_version`) para habilitar el cascade.
  - Tests compartidos `commonTest`: `verifyEmbeddingRepository`, `verifyNoteIndexing`, `verifyDatabaseMigration` (+ `EmptySqlSchema` para el driver nativo vacío) con `@Test` Android/JVM (`JdbcSqliteDriver.IN_MEMORY`) e iOS (`inMemoryDriver`).
  - `ARCHITECTURE.md`, `docs/technical-discovery.md` y `docs/risks-and-open-questions.md` actualizados; tasks de la spec marcadas; `AGENTS.md` (línea "Próxima feature en cola").
- Verification run:
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; nuevos EmbeddingRepository 1/1 + NoteIndexing 1/1 + DatabaseMigration 1/1 + previos NotePersistence 2/2, NoteRepository 1/1, AiClient 3/3, Greeting 1/1; 0 failures.
  - `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; mismos 10 tests en iOS, 0 failures.
  - `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (63 tasks junto al test Android con `--rerun-tasks`).
  - `Schema.version = 2`; `1.sqm` presente; `Note.sq` sin cambios; sin cambios en `:composeApp`/`iosApp`/`init.sh`/`libs.versions.toml`.
  - Control negativo reproducido y restaurado: endianness invertido en `EmbeddingBlobCodec.encode` → `EmbeddingRepositoryAndroidTest` falla con `AssertionError` de round-trip bit-exacto; restaurado a big-endian → verde 1/1.
  - Smoke real de migración v1 → v2: iOS (iPhone 15, iOS 17.2) e Android (emulator-5556) con `user_version=2`, tabla `embedding` creada y Nota legacy preservada, sin crashes/FATAL.
  - `./init.sh` → exit 0, sin dev servers ni simuladores.
- Evidence captured: arreglo `evidence` y `verification` de `embeddings-generation` en `feature_list.json` (9 líneas).
- Files or artifacts updated: `core/src/commonMain/sqldelight/com/playbook/core/db/{embedding.sq,1.sqm}`, `core/src/commonMain/kotlin/com/playbook/core/model/{NoteEmbedding,NoteEmbeddingMappers}.kt`, `core/src/commonMain/kotlin/com/playbook/core/repository/{EmbeddingBlobCodec,EmbeddingRepository,SqlDelightEmbeddingRepository}.kt`, `core/src/commonMain/kotlin/com/playbook/core/ai/NoteIndexingService.kt`, `core/src/commonMain/kotlin/com/playbook/core/db/DatabaseDriverFactory.kt`, `core/src/commonTest/kotlin/com/playbook/core/{repository/EmbeddingRepositoryCheck,ai/NoteIndexingCheck,db/DatabaseMigrationCheck}.kt`, `core/src/{androidUnitTest,iosTest}/kotlin/com/playbook/core/{repository/EmbeddingRepository*,ai/NoteIndexing*,db/DatabaseMigration*}.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `AGENTS.md`, `docs/specs/embeddings-generation.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: sin UI ni wiring de `AiClient`/`NoteIndexingService` a la app (intencional; no hay disparador de ciclo de vida ni superficie observable todavía, llega con `note-enrichment-status-ui`/`offline-pending-retry`). No se transiciona `NoteStatus` (decisión de scope: `indexada` exige también los Enlaces de `semantic-linking`). El runtime/dimensión reales del embedding siguen abiertos (`ai-runtime-decision`); se usa `FakeAiClient`. No hay E2E persistente (la spec lo justifica). Los emuladores Android son compartidos con otras sesiones paralelas: el primer intento en `emulator-5554` fue interrumpido por reinstalaciones concurrentes; el smoke se completó en `emulator-5556` con el APK de esta feature.
- Next best step: validación independiente de `embeddings-generation` con `$feature-validator`; tras el `accept`, crear spec de `semantic-linking` (depende de esta feature) o de `note-category-and-tags`/`ai-runtime-decision` (ya listas, requieren spec) con `$feature-spec`.

### Session 020

- Date: 2026-10-09
- Goal: validación independiente de `embeddings-generation` (spec `docs/specs/embeddings-generation.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `embeddings-generation` promovida a `accepted` en `feature_list.json` con evidencia de validación y registro en `docs/validations/embeddings-generation.md`.
- Verification run:
  - `./init.sh` → exit 0 con el gate real, sin dev servers.
  - `:core:testDebugUnitTest --rerun-tasks` y `:core:iosSimulatorArm64Test --rerun-tasks` → 10 tests / 0 failures en cada plataforma (conteos de `core/build/test-results/*/TEST-*.xml`).
  - `:composeApp:assembleDebug` + link del framework iOS con `--rerun-tasks` → BUILD SUCCESSFUL.
  - `Schema.version == 2` con `1.sqm`; `CREATE TABLE embedding` idéntico entre `embedding.sq`, `1.sqm` y `Schema.create`; `Note.sq`/`init.sh`/`libs.versions.toml`/`:composeApp`/`iosApp` sin cambios; sin hallazgos de seguridad.
  - Controles negativos propios del validador, cada uno reproducido y restaurado: persistir antes de validar (falla `NoteIndexingAndroidTest`), quitar `PRAGMA foreign_keys = ON` (falla el cascade), endianness invertido (falla el round-trip del codec); suite re-ejecutada en verde.
  - Smoke iOS real corroborado sobre el `playbook.db` del simulador: `user_version=2`, tablas `note`+`embedding` con FK cascade, fila legacy `legacy-ios` preservada.
- Evidence captured: línea de validación en el arreglo `evidence` de `embeddings-generation` en `feature_list.json`; `docs/validations/embeddings-generation.md`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `docs/validations/embeddings-generation.md`.
- Known risk or unresolved issue: hallazgo Low no bloqueante — el smoke de migración real en Android no se reprodujo de forma independiente por contaminación del emulador compartido (`emulator-5556` con el esquema de otra feature en paralelo); la migración Android queda cubierta por `DatabaseMigrationAndroidTest` en JVM. Persisten los scope decisions: sin UI/wiring, sin transición de `NoteStatus`, sin runtime real.
- Next best step: `semantic-linking` (depende de `embeddings-generation`, ya `accepted`) es la próxima feature ready; crear su spec con `$feature-spec`. Alternativas ready que requieren spec: `note-category-and-tags` y `ai-runtime-decision`.

### Session 021

- Date: 2026-10-09
- Goal: implementar `voice-capture-stt` (spec `docs/specs/voice-capture-stt.md`), primera integración nativa (STT).
- Completed:
  - `VoiceTranscriber` + `VoiceCaptureError` en `composeApp/commonMain` (`com.playbook.app.voice`): puerto con `start`/`stop`/`cancel` y errores normalizados.
  - `AndroidVoiceTranscriber` (`androidMain`): `SpeechRecognizer` + `RecognizerIntent` (parciales), permiso `RECORD_AUDIO` con `registerForActivityResult(RequestPermission())` registrado en `onCreate`, mapeo de errores del `RecognitionListener`; `RECORD_AUDIO` agregado al manifest.
  - `IosVoiceTranscriber` (`iosMain`): `SFSpeechRecognizer` + `AVAudioEngine` + `SFSpeechAudioBufferRecognitionRequest`, autorizaciones de micrófono/reconocimiento, despacho a main queue, limpieza de tap/engine/sesión en `cancel()`; `NSMicrophoneUsageDescription` + `NSSpeechRecognitionUsageDescription` agregadas al `Info.plist`.
  - `VoiceCaptureScreen` (commonMain) con `VoiceUiState` `Idle`/`Listening(partial)`/`Processing`/`Error`, acciones Grabar/Detener/Reintentar/Cancelar y mensajes de error en texto.
  - `App(noteRepository, voiceTranscriber)`: `NotesDestination.VoiceCapture`, inicio manual de la sesión, parciales, error, creación de la Nota con `create(NoteDraft(LOCAL_OWNER_ID, text.trim(), track = null))` + `goToList()` + `reload()`, final vacío → `NO_MATCH`, y `cancel()` (micrófono liberado) en `DisposableEffect` al salir.
  - `NotesListScreen` suma `onDictate` y `FilledTonalButton` "Dictar nota"; `MainActivity`/`MainViewController` construyen y pasan el adaptador.
  - `ARCHITECTURE.md`, `docs/technical-discovery.md` y `docs/risks-and-open-questions.md` actualizados; `AGENTS.md` (próxima feature) corregido; tasks de la spec marcadas.
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL (66 tasks executed); Android/JVM: GreetingTest 1/1, AiClientAndroidTest 3/3, NotePersistenceAndroidTest 2/2, NoteRepositoryAndroidTest 1/1; iOS: idem, 0 failures.
  - `xcodebuild` scheme iosApp (iPhone 15, iOS 17.2) → BUILD SUCCEEDED; `simctl install`/`launch` → PID vivo; sin crash reports.
  - Smoke Android (emulador `sdk_gphone64_arm64`, Google APIs): lista con "Dictar nota"; pantalla de captura Idle; permiso denegado → mensaje explícito, sin Nota ni crash; "Cancelar" → lista; con permiso concedido el reconocedor arranca ("Escuchando…"); "Detener" con silencio → "No se escuchó nada" (`NO_MATCH`), sin Nota ni crash. Capturas/UI dumps en `composeApp/build/smoke-evidence/android-voice-*`.
  - `./init.sh` → exit 0, sin procesos de larga duración.
  - `Schema.version = 1`, sin `.sqm`; `git status` sólo con archivos de la feature; sin cambios en `core/**`/`libs.versions.toml`/`composeApp/build.gradle.kts`/`init.sh`.
- Evidence captured: arreglo `evidence` de `voice-capture-stt` en `feature_list.json` (7 líneas); capturas/UI dumps en `composeApp/build/smoke-evidence/`.
- Files or artifacts updated: `composeApp/src/commonMain/kotlin/com/playbook/app/voice/VoiceTranscriber.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/VoiceCaptureScreen.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt`, `composeApp/src/androidMain/kotlin/com/playbook/app/voice/AndroidVoiceTranscriber.kt`, `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt`, `composeApp/src/androidMain/AndroidManifest.xml`, `composeApp/src/iosMain/kotlin/com/playbook/app/voice/IosVoiceTranscriber.kt`, `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt`, `iosApp/iosApp/Info.plist`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `docs/specs/voice-capture-stt.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el happy path de audio real → Nota no pudo ejecutarse en emulador (no hay forma determinista de inyectar voz); el reconocedor sí arrancó y los flujos de permiso denegado/cancelar/sin habla quedaron verificados. El emulador Android está compartido con otras sesiones paralelas: durante el smoke aparecieron ANR de una app ajena (`es.alt10.kotlin.midgt.ne.des`), un `pm clear` externo de `com.playbook.app` y, al final, el APK instalado fue reemplazado por un build de otra feature (UI de "Etiquetas", inexistente en este código), por eso el smoke se detuvo tras capturar la evidencia. La persistencia de la Nota está cubierta por tests de `:core`. No hay harness E2E (justificado en la spec).
- Next best step: validación independiente de `voice-capture-stt` con `$feature-validator`; tras el `accept`, crear spec de `note-category-and-tags` con `$feature-spec`.

### Session 022

- Date: 2026-10-09
- Goal: validación independiente de `voice-capture-stt` (spec `docs/specs/voice-capture-stt.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `voice-capture-stt` promovida a `accepted` en `feature_list.json` con evidencia de validación; commit `feature: completa voice-capture-stt` creado por el orquestador.
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL (66 tasks, 0 failures en Android/JVM e iOS).
  - `./init.sh` → exit 0 con el gate real y `init.sh` sin cambios (`git diff` vacío); `xcodebuild` scheme iosApp → BUILD SUCCEEDED; `simctl install`/`launch` → PID vivo, sin crash reports.
  - Scope: sin cambios en `core/**`, `Note.sq`, `libs.versions.toml`, `composeApp/build.gradle.kts`, `init.sh` ni `.pbxproj`; `Schema.version = 1`, sin `.sqm`; `RECORD_AUDIO` en el manifest empaquetado y ambas usage descriptions en el `Info.plist` del producto.
  - Smoke Android independiente (emulador API 34, Google APIs): Escenario 2 (permiso denegado → mensaje explícito, sin Nota ni crash) y Escenario 4 (Cancelar → lista) reproducidos; con permiso concedido el reconocedor arranca ("Escuchando…"). El happy path de audio real no es ejecutable determinísticamente (limitación de entorno, corroborada por interferencia de otra sesión en el emulador compartido).
- Evidence captured: línea de validación en el arreglo `evidence` de `voice-capture-stt` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: hallazgos Low no bloqueantes — happy path de audio→Nota no ejecutable en emulador/simulador (declarado honestamente), artefacto no citado `android-voice-10-happy-listening.xml` en el build dir ignorado (build ajeno) y `setCategory(..., error = null)` que ignora el error en iOS. Sin hallazgos de seguridad.
- Next best step: crear spec de `note-category-and-tags` con `$feature-spec` e implementarla con `$feature-implementer`; alternativamente `ai-runtime-decision` o `voice-attachment-storage` (ya dependency-ready).

### Session 023

- Date: 2026-10-09
- Goal: implementar `note-category-and-tags` (spec `docs/specs/note-category-and-tags.md`): Etiquetas libres, múltiples y editables en la Nota.
- Completed:
  - Modelo: `tags: List<String> = emptyList()` al final de `Note`/`NoteDraft` (no rompe código previo) y `normalizeTags` en `com.playbook.core.model.Tags` (trim, descarta vacías, dedupe case-insensitive conservando la primera grafía, orden case-insensitive).
  - Persistencia: `NoteTag.sq` (tabla `note_tag` con PK `(note_id, label)` + queries `insertNoteTag`/`selectTagsByNoteId`/`selectAllNoteTags`/`deleteTagsByNoteId`) y `2.sqm` (v2→v3, tras el `1.sqm` de embeddings). `Schema.version = 3`; `Note.sq` sin cambios.
  - Repositorio: `SqlDelightNoteRepository` persiste/lee etiquetas en transacción (`create`, `getById`, `getAll` sin N+1, `update` reemplaza el conjunto, `delete` limpia) y `NoteMappers.toDomain(tags)`.
  - UI (`:composeApp`): `NoteEditorScreen` con campo "Nueva etiqueta" + "Agregar" y chips con "×" (`onClickLabel`/`Role.Button`, sin íconos); `NotesListScreen` con fila de chips de etiqueta; `App` pasa `tags` a `NoteDraft`/`copy`.
  - Docs: `ARCHITECTURE.md` (esquema/migración, dominio, UI) y `docs/technical-discovery.md` (nueva sección "Tags & Migration Decisions"); `AGENTS.md` (próxima feature); `feature_list.json` reconciliado (título/comportamiento/verificación) y `passing`.
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL (66 tareas). Android/JVM: NoteRepository 3/3, NotePersistence 2/2, AiClient 3/3, Greeting 1/1; iOS: NoteRepository 3/3, NotePersistence 2/2, AiClient 3/3, Greeting 1/1; 0 failures.
  - Control negativo: `2.sqm` creando `note_tag_broken` hace fallar `migrationV1ToV3PreservesNotes` en Android/JVM con `SQLiteException: ... no such table: note_tag`; restaurado y verde.
  - `Schema.version = 3`; existe `2.sqm`; `Note.sq`, build files, `init.sh`, `iosApp/**`, `MainActivity`/`MainViewController` sin cambios.
  - `xcodebuild` scheme iosApp (iPhone 15, iOS 17.2) → BUILD SUCCEEDED.
  - Smoke Android (Pixel_3A_API_34/emulator-5556; APK md5 verificado): estado vacío; crear con `jefe`/`arte` + duplicado `JEFE` ignorado → chips; guardar → lista + DB con exactamente `arte`/`jefe`; editar (quitar `arte`, agregar `nivel`) → lista y DB con `jefe`/`nivel`; relanzar persiste; `track`/`status` intactos. Migración real v1→v3 sobre `playbook.db` v1 (`user_version` 1→3, `note`+`embedding`+`note_tag`, fila v1 intacta) y chips de etiqueta renderizados.
  - Smoke iOS (iPhone 15, iOS 17.2): install/launch OK; OCR estado vacío y lista con chips `arte`/`jefe`; migración real v1→v3 sobre `playbook.db` v1 (`user_version` 1→3, fila intacta, `note_tag` creada). CRUD interactivo iOS **no** ejecutado: sin `idb`/`cliclick`/`applesimutils` y `osascript`/System Events no disponible; no se fabricó evidencia.
  - `./init.sh` → exit 0, sin procesos de larga duración.
- Evidence captured: arreglo `evidence` de `note-category-and-tags` en `feature_list.json` (10 líneas); capturas/UI dumps/DBs en el directorio ignorado `composeApp/build/smoke-evidence/`; datos QA limpiados de emulador y simulador.
- Files or artifacts updated: `core/src/commonMain/sqldelight/com/playbook/core/db/NoteTag.sq` (nuevo), `core/src/commonMain/sqldelight/com/playbook/core/db/2.sqm` (nuevo), `core/src/commonMain/kotlin/com/playbook/core/model/Tags.kt` (nuevo), `core/src/commonMain/kotlin/com/playbook/core/model/{Note,NoteMappers}.kt`, `core/src/commonMain/kotlin/com/playbook/core/repository/{NoteRepository,SqlDelightNoteRepository}.kt`, `core/src/commonMain/kotlin/com/playbook/core/db/DatabaseDriverFactory.kt` (KDoc), `core/src/commonTest/kotlin/com/playbook/core/repository/NoteTagCheck.kt` (nuevo), `core/src/androidUnitTest/kotlin/com/playbook/core/repository/NoteRepositoryAndroidTest.kt`, `core/src/iosTest/kotlin/com/playbook/core/repository/NoteRepositoryIosTest.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/{App,NotesListScreen,NoteEditorScreen}.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: la PK `(note_id, label)` no normaliza globalmente etiquetas (renombrar/autocompletar exigiría `tag` + `note_tag`, documentado); sin tope de cantidad/largo de etiquetas ni orden de ingreso (se devuelven ordenadas por `label`). El emulador Android está compartido con otros worktrees que usan el mismo `applicationId`: una instalación paralela pisó el APK en `emulator-5554`; el smoke se hizo en `emulator-5556` verificando el md5 del APK instalado. CRUD interactivo iOS no automatizable (limitación de tooling).
- Next best step: validación independiente de `note-category-and-tags` con `$feature-validator`; tras el `accept`, crear spec de la próxima feature (p. ej. `ai-runtime-decision` o captura por voz/imagen) con `$feature-spec`.

### Session 024

- Date: 2026-10-09
- Goal: validación independiente de `note-category-and-tags` (spec `docs/specs/note-category-and-tags.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `note-category-and-tags` promovida a `accepted` en `feature_list.json` con evidencia de validación; commit `feature: complete note-category-and-tags` creado por el orquestador.
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL in 43s (66 tareas); NoteRepository 3/3 + NotePersistence 2/2 + AiClient 3/3 + Greeting 1/1 por plataforma, 0 failures; `./init.sh` → exit 0.
  - `Schema.version = 2`; migración compilada (`create()`/`migrateInternal` emiten `note_tag`); `Note.sq` sin cambios (`git diff` vacío); sin cambios en build files/`init.sh`/`iosApp`/`Track.kt`.
  - Control negativo propio del validador: renombrar la tabla en `migrations/1.sqm` a `note_tag_broken` reproduce `SQLiteException: ... no such table: note_tag`; restaurado byte-idéntico y re-ejecutado en verde.
  - Corroboración con `sqlite3` de los artefactos de smoke: `android-before-migration.db` (v1, sólo `note`), `android-after-migration.db` (v2, `note`+`note_tag`, fila v1 intacta), `android-06-interactive.db` (`arte`/`jefe`, `JEFE` deduplicado), `android-08-after-edit.db` (`jefe`/`nivel`, `track`/`status` intactos). UI dumps sin control Categoría/Tipo/Nivel.
- Evidence captured: línea de validación en el arreglo `evidence` de `note-category-and-tags` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: hallazgos Low no bloqueantes — CRUD interactivo iOS no automatizado (limitación de tooling, declarada honestamente) y orden de etiquetas case-insensitive en memoria vs `ORDER BY label` (BINARY) en SQL (respeta el contrato de la spec; determinista en cada lectura). Términos obsoletos "categoría/tipo" persisten en entradas fuera de alcance (`ai-enrichment-classify-tags-type`, `note-enrichment-status-ui`, `gdd-view`), señalados para re-derivar.
- Next best step: crear spec de la próxima feature dependency-ready con `$feature-spec` (p. ej. `ai-runtime-decision` o captura por voz/imagen).

### Session 025

- Date: 2026-10-09
- Goal: implementar `image-capture-camera` (spec `docs/specs/image-capture-camera.md`).
- Completed:
  - `:core`: `Attachment.sq` (tabla `attachment` + índice `attachment_note_id`) y migración `3.sqm` (v3 → v4); `PlaybookDatabase.Schema.version == 4`. Modelo `Attachment`/`AttachmentKind` (`IMAGE` → `"imagen"`, `fromCode` fail-fast) + `AttachmentMappers` (alias `Attachment as AttachmentRow`); `AttachmentRepository` + `SqlDelightAttachmentRepository(database, idFactory, clock)`.
  - `SqlDelightNoteRepository.delete(id)` pasa a borrar las filas `attachment` (y las `note_tag`) y la `note` en una transacción (`transactionWithResult`), sin huérfanos; la semántica pública no cambia.
  - Tests nuevos en `commonTest`: `verifyAttachmentCrud` (CRUD + `AttachmentKind.fromCode` + cascade) y `verifyMigrationV1ToV4` (construye v1, aplica `Schema.migrate(1,4)`, verifica notas preservadas y `attachment` usable); `@Test` por plataforma (`AttachmentRepositoryAndroidTest`, `MigrationAndroidTest`, `AttachmentRepositoryIosTest`, `MigrationIosTest`).
  - `:composeApp`: contrato `ImageCapture.kt` (`ImageSource`/`PickedImage`/`ImageCaptureError`/`ImagePicker`/`AttachmentFileStore`/`decodeImageBitmap`) con `expect`; `actual` Android (permiso `CAMERA` runtime + intent `ACTION_IMAGE_CAPTURE` con grant de URI + `FileProvider` + `PickVisualMedia`, `BitmapFactory`, `filesDir/images/`) y iOS (`AVCaptureDevice` + `UIImagePickerController` + `PHPickerViewController`, re-encode JPEG, Skia, `Application Support/images/`). Permisos: `CAMERA` + `<provider>` + `res/xml/file_paths.xml` (Android), `NSCameraUsageDescription` (iOS). `MainActivity`/`MainViewController` construyen y pasan el segundo repositorio.
  - UI: `App(noteRepository, attachmentRepository, voiceTranscriber)` carga adjuntos, resuelve `NotesDestination.CreateWithImage`, diálogo de fuente (Cámara/Galería/Cancelar) y diálogo de error; `NotesListScreen` con FABs "Nueva nota"+"Foto", botón "Dictar nota", CTA "Agregar foto", chip "Imagen" y placeholder "Imagen adjunta"; `NoteEditorScreen` con previsualización, "Quitar imagen", etiquetas y `canSave = body.isNotBlank() || image != null`. Guardado con archivo antes de la nota (borra el archivo si falla después); borrado de nota borra adjuntos y archivos.
  - Docs: `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `AGENTS.md`; spec con findings de implementación y tasks marcadas.
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL in 1m34s (66 tareas). Android/JVM: AttachmentRepository 1/1, Migration 1/1, NotePersistence 2/2, NoteRepository 3/3, AiClient 3/3, Greeting 1/1; iOS idem; 0 failures.
  - `PlaybookDatabase.Schema.version = 4`; `find core/src -name '*.sqm'` → `1.sqm`, `2.sqm`, `3.sqm`; sin dependencias Gradle nuevas.
  - `./init.sh` → exit 0, sin procesos de larga duración.
  - `xcodebuild` scheme `iosApp` (iPhone 15, iOS 17.2) → BUILD SUCCEEDED; `NSCameraUsageDescription` presente en el Info.plist construido.
  - Smoke Android (emulator-5556 API 35): DB on-device `user_version=4` + `note`+`embedding`+`note_tag`+`attachment`+índice; **galería** → preview → guardar con body vacío → lista con chip "Imagen"; fila `attachment` con ruta relativa `images/<uuid>.jpg` y archivo en `files/images/`; persistencia tras force-stop/relaunch; **borrado** con `AlertDialog` → `notes=0`/`attachments=0` y archivo eliminado; **cámara** (grant + FileProvider) → captura/guardar → adjunto `image/jpeg`; **permiso denegado** → diálogo accionable, 0 filas, sin crash.
  - Smoke iOS (simulador iPhone 15, iOS 17.2): install/launch con PID vivo; DB `user_version=4` + tablas; **migración real v1→v4** (seed v1 → relaunch) preserva la nota y agrega `attachment`. Galería/cámara interactivas iOS no automatizables; cámara iOS en simulador → `CAMERA_UNAVAILABLE`.
  - **Finding reproducible:** `ActivityResultContracts.TakePicture` no agrega los flags de grant de URI y la app de cámara fallaba al guardar (`RemoteException`); se reemplazó por `ACTION_IMAGE_CAPTURE` con `FLAG_GRANT_READ/WRITE_URI_PERMISSION` + `clipData`, tras lo cual funciona.
- Evidence captured: arreglo `evidence` de `image-capture-camera` en `feature_list.json` (10 líneas); UI dumps en `composeApp/build/smoke-evidence/android-*`.
- Files or artifacts updated: `core/src/commonMain/sqldelight/com/playbook/core/db/{Attachment.sq,3.sqm}`, `core/src/commonMain/kotlin/com/playbook/core/model/{Attachment,AttachmentMappers}.kt`, `core/src/commonMain/kotlin/com/playbook/core/repository/{AttachmentRepository,SqlDelightAttachmentRepository,SqlDelightNoteRepository}.kt`, `core/src/commonTest/kotlin/com/playbook/core/{repository/AttachmentRepositoryCheck,db/MigrationCheck}.kt`, `core/src/{androidUnitTest,iosTest}/kotlin/com/playbook/core/{repository/AttachmentRepository*,db/Migration*}.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/{ImageCapture,App,NotesListScreen,NoteEditorScreen}.kt`, `composeApp/src/{androidMain,iosMain}/kotlin/com/playbook/app/ImageCapture.*.kt`, `composeApp/src/androidMain/AndroidManifest.xml`, `composeApp/src/androidMain/res/xml/file_paths.xml`, `composeApp/src/{androidMain,iosMain}/kotlin/com/playbook/app/Main*.kt`, `iosApp/iosApp/Info.plist`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `docs/specs/image-capture-camera.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: la cámara iOS real sólo se valida en dispositivo físico (simulador → `CAMERA_UNAVAILABLE`) y no hubo input automation iOS; se declara honestamente. "Quitar imagen" vuelve a `Create` (descarta el borrador del editor). Los adjuntos de una nota existente no se editan (fuera de alcance). El archivo físico se borra desde la UI: si falla el borrado queda un huérfano tolerado y documentado.
- Next best step: validación independiente de `image-capture-camera` con `$feature-validator`; tras el `accept`, crear spec de `note-category-and-tags` con `$feature-spec`.

### Session 026

- Date: 2026-10-09
- Goal: validación independiente de `image-capture-camera` (spec `docs/specs/image-capture-camera.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `image-capture-camera` promovida a `accepted` en `feature_list.json` con evidencia de validación; commit `feature: complete image-capture-camera` creado por el orquestador.
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL (66 tasks); 9/9 Android/JVM y 9/9 iOS, 0 failures; `./init.sh` → exit 0 con `init.sh` sin cambios.
  - `PlaybookDatabase.Schema.version == 2`; `1.sqm` presente; sin dependencias nuevas; sin cambios en `init.sh`/`Note.sq`/build files.
  - Controles negativos propios del validador (restaurados al byte original): comentar `deleteAttachmentsByNoteId` en `SqlDelightNoteRepository.delete` hace fallar `attachmentCrudAndCascade`; romper `1.sqm` (`attachment_broken`) hace fallar `migratesV1ToV2PreservingNotes`.
  - Verificación independiente de artefactos: DB on-device `user_version=2` + `note`+`attachment`, fila con `file_path=images/<uuid>.jpg` (relativa) y post-borrado `note=0`/`attachment=0`; `NSCameraUsageDescription` en el Info.plist construido; UI dumps de diálogo de fuente, preview/quitar/guardar y chip "Imagen".
  - Alcance/arquitectura: sin `ViewModel`/Flow/DI/navegación/`BLOB`, sin editar adjuntos existentes, sin audio/OCR/render/IA/GDD. Límites de tooling iOS (sin `idb`/`cliclick`, `osascript` bloqueado, cámara iOS en simulador) confirmados reales y declarados honestamente.
- Evidence captured: línea de validación (accept) en el arreglo `evidence` de `image-capture-camera` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: hallazgos Low no bloqueantes (L1–L5): comentarios obsoletos sobre `TakePicture` en `ImageCapture.android.kt`/`AndroidManifest.xml`, KDoc de `createDatabase` que dice 'v1', fallo parcial improbable en `saveNoteWithImage`, temporal de cámara en `cacheDir/images/` y artefacto de smoke de permiso denegado no guardado. La cámara iOS real sólo se valida en dispositivo físico.
- Next best step: crear spec de `note-category-and-tags` con `$feature-spec` e implementarla con `$feature-implementer` (primera feature no-`accepted` dependency-ready en orden).

### Session 027

- Date: 2026-10-09
- Goal: implementar `ios-release-pipeline` (spec `docs/specs/ios-release-pipeline.md`): pipeline de release de iOS a TestFlight (artefactos repo-side + ejecución account-gated).
- Completed:
  - Firma/identidad: `PRODUCT_BUNDLE_IDENTIFIER` pasa de `${BUNDLE_ID}${TEAM_ID}` a `${BUNDLE_ID}` (bundle id estable `com.playbook.app`) y `Release` usa `CODE_SIGN_IDENTITY = "Apple Distribution"` (Debug conserva `Apple Development`).
  - Versionado: `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` como build settings (default `1.0`/`1`); `Info.plist` usa `$(MARKETING_VERSION)`/`$(CURRENT_PROJECT_VERSION)`.
  - `Config.xcconfig` versiona `TEAM_ID=` vacío y `#include? "Local.xcconfig"` (gitignored); `Local.xcconfig.example` creado.
  - Scheme compartido `iosApp.xcscheme` creado para archive/export CLI deterministas.
  - `scripts/ios-release/{preflight,archive,export-upload}.sh` + `ExportOptions.plist.template`; `preflight` no archiva, `archive` soporta `--unsigned`, `export-upload` renderiza el plist y sube con `xcrun altool --upload-app`.
  - `.gitignore` con credenciales/artefactos de firma/release; runbook `docs/release/ios-testflight.md`; `CONSTRAINTS.md`; `ARCHITECTURE.md` (sección Release / iOS distribution + identifiers); `docs/technical-discovery.md` (Deployment + Release Decisions); `AGENTS.md`.
- Verification run:
  - `scripts/ios-release/preflight.sh` (sin credenciales) → exit 1; `[FAIL] TEAM_ID no definido` y `[FAIL] Sin credenciales de firma: 0 identidades de codesigning y sin App Store Connect API key`; `[OK]` macOS/Xcode/scheme/altool. No archivó ni subió (Scenario 1).
  - `./gradlew :composeApp:linkReleaseFrameworkIosArm64` → BUILD SUCCESSFUL in 14m 54s (Scenario 2).
  - `scripts/ios-release/archive.sh --unsigned --version 1.0 --build 1` → `xcodebuild ... CODE_SIGNING_ALLOWED=NO archive` → `** ARCHIVE SUCCEEDED **`; `build/ios/Playbook.xcarchive` con `CFBundleIdentifier=com.playbook.app`, `1.0`/`1`; binario arm64 de 27.4 MB con Compose enlazado (Scenario 2).
  - `xcodebuild -list` → scheme `iosApp` visible.
  - `scripts/ios-release/export-upload.sh` (sin `TEAM_ID`) → exit 1 con `ERROR: falta TEAM_ID.` (no exporta ni sube).
  - Scenario 7: `git status` sólo con archivos de la feature; `git check-ignore` confirma `build/ios/**` e `iosApp/Configuration/Local.xcconfig`; `find` de artefactos de firma y `git grep` de claves privadas sin resultados.
  - `./init.sh` → exit 0 (28s+41s+8s), sin dev servers ni procesos de larga duración; no archiva ni sube (Scenario 8). `init.sh` sin cambios.
  - Scenarios 3–6 (archive firmado, export `.ipa`, upload, device real) **no ejecutados**: sin Apple Developer Program + credenciales. Bloqueo probado por preflight. No se fabricó evidencia de TestFlight.
- Evidence captured: arreglo `evidence` de `ios-release-pipeline` en `feature_list.json` (7 líneas); logs en `/tmp/ios-archive.log` (1093 líneas) e `/tmp/ios-init.log`.
- Files or artifacts updated: `iosApp/iosApp.xcodeproj/project.pbxproj`, `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` (nuevo), `iosApp/iosApp/Info.plist`, `iosApp/Configuration/Config.xcconfig`, `iosApp/Configuration/Local.xcconfig.example` (nuevo), `scripts/ios-release/*` (nuevos), `.gitignore`, `docs/release/ios-testflight.md` (nuevo), `CONSTRAINTS.md` (nuevo), `ARCHITECTURE.md`, `docs/technical-discovery.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: la feature queda `blocked` (no `passing`) porque su `user_visible_behavior` exige subir a TestFlight e instalar en device real, imposible sin cuenta. Recomendación honesta: dividir en `ios-release-pipeline-scaffolding` (verificable, ya cumplido) + `ios-release-pipeline-first-upload` (account-gated), en vez de declarar `accepted` sin subida. `altool` está deprecado por Apple, aunque funciona en Xcode 26.4/27.0 (27.0.5 (2.1)); fallback declarado: `iTMSTransporter`, y `fastlane pilot` como último recurso (no adoptado).
- Next best step: decisión del owner sobre la división de la feature; en paralelo, crear spec de `note-category-and-tags` con `$feature-spec` para continuar el MVP. Cuando haya cuenta Apple Developer Program, completar Scenarios 3–6 y `accept`.

### Session 028

- Date: 2026-10-13
- Goal: implementar `image-ocr-optional` (spec `docs/specs/image-ocr-optional.md`): feature **opcional/min** resuelta como decisión de viabilidad + documentación.
- Completed:
  - `docs/adr/0002-ocr-decision.md` (nuevo, MADR-lite consistente con `docs/adr/0001-ai-runtime-decision.md`): contexto, 7 criterios, **5 alternativas** (ML Kit Text Recognition v2, iOS Vision `VNRecognizeTextRequest`, `AiClient`/visión, OCR cloud dedicado, descartar) con tabla de trade-offs por criterio, decisión **DISCARD para el MVP** con fundamento, consecuencias (positivas/negativas/neutras), **disparadores de re-evaluación**, evidencia y límites honestos (no se ejecutó OCR ni se midió calidad).
  - `docs/technical-discovery.md`: Integrations marca el OCR como descartado + nueva sección "OCR Decision (image-ocr-optional)".
  - `docs/risks-and-open-questions.md`: nueva entrada en "Resolved" (OCR descartado del MVP) + pregunta de RAG de adjuntos sin texto **precisada**.
  - `ARCHITECTURE.md`: línea introductoria + sección "Deferred" declaran el descarte con puntero al ADR y disparador de re-evaluación.
  - Tasks de la spec marcadas (`docs/specs/image-ocr-optional.md`).
- Verification run:
  - `./init.sh` → exit 0 (assembleDebug + testDebugUnitTest + link framework iOS + iosSimulatorArm64Test), sin procesos de larga duración; `init.sh` sin cambios.
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks --console=plain` → BUILD SUCCESSFUL in 2m 51s (66 actionable tasks: 66 executed). 16 tests Android/JVM y 16 iOS, 0 failures / 0 errors (Greeting 1, AiClient 3, NoteIndexing 1, AiRuntimeSpike 2, DatabaseMigration 1, Migration 1, NotePersistence 2, AttachmentRepository 1, EmbeddingRepository 1, NoteRepository 3 por plataforma).
  - Esquema sin cambios: `PlaybookDatabaseImpl.Schema.version = 4`; `find core/src -name '*.sqm'` → `1.sqm`/`2.sqm`/`3.sqm` (sin `.sqm` nuevo).
  - Sin deps nuevas ni cambios de producto: `git diff` vacío en `gradle/libs.versions.toml`, `build.gradle.kts`, `core/build.gradle.kts`, `composeApp/build.gradle.kts`, `core/src/**`, `composeApp/src/**`, `iosApp/**` e `init.sh`.
  - Control negativo: `grep -riE "mlkit|VNRecognizeTextRequest|TextRecognizer" core/src composeApp/src` → sin resultados; contrato de `AiClient` sin cambios (`git diff` vacío).
- Evidence captured: arreglo `evidence` de `image-ocr-optional` en `feature_list.json`; ADR `docs/adr/0002-ocr-decision.md`; diff de los docs durables.
- Files or artifacts updated: `docs/adr/0002-ocr-decision.md` (nuevo), `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `ARCHITECTURE.md`, `docs/specs/image-ocr-optional.md`, `feature_list.json`, `PROGRESS.md`, `AGENTS.md`.
- Known risk or unresolved issue: la calidad de OCR sobre **manuscrito** no se midió (no se ejecutó OCR); el descarte se fundamenta en la orientación de los motores a texto impreso y en las restricciones del repo, declarado como incertidumbre. La pregunta "¿los Adjuntos sin texto entran al RAG?" sigue abierta (precisada) para la feature de indexado/RAG. Convención de estado final de una feature descartada (`passing → accepted` con el descarte documentado) propuesta en la spec; si el validador/orquestador prefiere otra, registrarla.
- Next best step: validación independiente de `image-ocr-optional` con `$feature-validator`; tras el `accept`, elegir la próxima feature lista (`semantic-linking` / `voice-attachment-storage`) y crear su spec con `$feature-spec`.

### Session 029

- Date: 2026-10-13
- Goal: validación independiente de `image-ocr-optional` (spec `docs/specs/image-ocr-optional.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `image-ocr-optional` promovida a `accepted` en `feature_list.json` con evidencia de validación; ADR `docs/adr/0002-ocr-decision.md` promovido de `propuesto` a `accepted`; commit `docs: complete image-ocr-optional` creado por el orquestador y PR draft contra `develop`.
- Verification run:
  - `./init.sh` → exit 0, sin dev servers ni simuladores; `git diff -- init.sh` vacío.
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL (66 tasks, 6m49s); 16 tests Android/JVM y 16 iOS, 0 failures/0 errors (conteos de `core/build/test-results/*/TEST-*.xml`).
  - Esquema confirmado por fuente generada: `PlaybookDatabaseImpl.Schema.version == 4`; `find core/src -name '*.sqm'` → `1.sqm`/`2.sqm`/`3.sqm` (sin `.sqm` nuevo).
  - `git diff` vacío en `core/src composeApp/src iosApp gradle/libs.versions.toml build.gradle.kts core/build.gradle.kts composeApp/build.gradle.kts init.sh`, incluido `AiClient.kt` (contrato sin cambios).
  - Control negativo propio del validador: `grep -riE "mlkit|VNRecognizeTextRequest|TextRecognizer|com.google.mlkit" core/src composeApp/src` → sin resultados (exit 1).
  - Escenarios 1–5 verificados (ADR MADR-lite con 5 alternativas/trade-offs/decisión DISCARD/disparadores; docs durables con puntero al ADR; entry + `PROGRESS.md`/`AGENTS.md`; gate honesto; control negativo). Honestidad corroborada: "No se ejecutó OCR ni se midió calidad", sin medición fabricada; sin secrets.
- Evidence captured: línea de validación en el arreglo `evidence` de `image-ocr-optional` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `AGENTS.md`, `docs/adr/0002-ocr-decision.md`.
- Known risk or unresolved issue: hallazgos Low no bloqueantes — `Status: propuesto` del ADR (promovido a `accepted` al persistir la aceptación) y estimaciones de peso/calidad sin URLs fechadas (declaradas en los límites honestos del ADR). La calidad de OCR sobre manuscrito sigue sin medirse; la pregunta de RAG de adjuntos sin texto sigue abierta para la feature de indexado/RAG.
- Next best step: la próxima feature ready es `semantic-linking` (depende de `embeddings-generation`, ya `accepted`); crear su spec con `$feature-spec`. Alternativa ready: `voice-attachment-storage` (depende de `voice-capture-stt`).

### Session 030

- Date: 2026-10-13
- Goal: implementar `gdd-view` (spec `docs/specs/gdd-view.md`): primera vista de GDD que agrupa y resume las Notas por `track`, sin tabla propia.
- Completed:
  - `:core`: `com.playbook.core.gdd.GddSection(track, notes)` + `buildGdd(notes)` (proyección pura; orden fijo `Track.entries` + "Sin track" sólo con notas sin track; conserva orden de entrada; sin mutar ni duplicar). Sin SQLDelight ni `AiClient`.
  - Tests: `verifyGddProjection()` en `commonTest` + `GddProjectionAndroidTest`/`GddProjectionIosTest` (agrupación, orden fijo, sin track, sin duplicados, determinismo, no-mutación).
  - `:composeApp`: nuevo `GddScreen` (estado vacío "Todavía no hay notas"; encabezado "GDD" + total; secciones por track con conteo y "Sin notas en este track"; notas resumidas a 2 líneas con chips de `status`/Etiquetas/"Imagen"; "Volver"; abrir nota → editor). `LabelChip` extraído a archivo común reutilizado por lista y GDD.
  - `App.kt`: nuevo `NotesDestination.Gdd` y wiring (`List → Gdd → Edit`); `NotesListScreen` suma la acción "Ver GDD".
  - `ARCHITECTURE.md` (intro, destino `Gdd`, sección GDD, Deferred), `docs/technical-discovery.md` (nueva "GDD Decisions (gdd-view)") y `AGENTS.md` actualizados; terminología del entry de `feature_list.json` reconciliada ("por categoría/etiqueta" → "por track"); tasks de la spec implícitas.
- Verification run:
  - `./gradlew :core:testDebugUnitTest` → BUILD SUCCESSFUL; GddProjectionAndroidTest 1/1 + previos (Greeting 1/1, AiClient 3/3, NoteIndexing 1/1, AiRuntimeSpike 2/2, DatabaseMigration 1/1, Migration 1/1, NotePersistence 2/2, AttachmentRepository 1/1, EmbeddingRepository 1/1, NoteRepository 3/3), 0 failures.
  - `./gradlew :core:iosSimulatorArm64Test` → BUILD SUCCESSFUL; GddProjectionIosTest 1/1, 0 failures.
  - `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (60 tareas); `xcodebuild` scheme `iosApp` → `** BUILD SUCCEEDED **`.
  - Control negativo propio: omitir la sección "Sin track" en `buildGdd` hace fallar `GddProjectionAndroidTest.projectionGroupsByTrack` (`AssertionError: orden fijo de secciones y 'Sin track' al final... expected:<[MECHANICS, CHARACTERS, STORY, null]> but was:<[MECHANICS, CHARACTERS, STORY]>`); restaurado y verde 1/1.
  - Esquema sin cambios: `Schema.version = 4`, sin `.sqm` nuevos (`1.sqm`/`2.sqm`/`3.sqm` ya existentes); `getAll()` sin cambios; sin dependencias nuevas; `init.sh` sin cambios.
  - Smoke Android (emulator-5556, APK md5 `d29d8470eccff4b540aa4ea2af483ae6` = build local): GDD vacío → `GDD`/`0 notas`/`Todavía no hay notas`/`Volver`; `Volver` → lista. Base sembrada (5 notas: mecánicas×2, personajes, historia, sin track; 2 etiquetas; 1 adjunto) → secciones en orden con conteos (`mecánicas` 2, `personajes` 1, `historia` 1, `Sin track` 1), encabezado `5 notas`; nota sin track + chip `fallida` tras scroll; chips `Imagen`/etiquetas/`status`; tocar nota → editor `Editar nota`; `Cancelar` → lista; crear nota (`historia`) desde la lista y reabrir GDD → `6 notas` y `historia` `2 notas` (refresco). Datos QA limpiados con `pm clear`.
  - Smoke iOS (iPhone 15 Pro, iOS 17.2): instalado/lanzado (PID vivo); OCR (`macOS Vision`) de la captura muestra `Notas`/`Dictar nota`/`Ver GDD`/`Todavía no hay notas`/`Crear primera nota`. La navegación al GDD no pudo automatizarse (sin `idb`/`cliclick`/`applesimutils`; `osascript`/System Events bloqueado). No se fabricó evidencia.
  - `./init.sh` → exit 0, sin procesos de larga duración; `./gradlew tasks` → BUILD SUCCESSFUL.
- Evidence captured: arreglo `evidence` de `gdd-view` en `feature_list.json` (10 líneas); capturas/UI dumps en el directorio ignorado `composeApp/build/smoke-evidence/gdd-view/` (`android-*.xml`, `ios-01-launch.png`).
- Files or artifacts updated: `core/src/commonMain/kotlin/com/playbook/core/gdd/GddProjection.kt` (nuevo), `core/src/commonTest/kotlin/com/playbook/core/gdd/GddProjectionCheck.kt` (nuevo), `core/src/androidUnitTest/kotlin/com/playbook/core/gdd/GddProjectionAndroidTest.kt` (nuevo), `core/src/iosTest/kotlin/com/playbook/core/gdd/GddProjectionIosTest.kt` (nuevo), `composeApp/src/commonMain/kotlin/com/playbook/app/GddScreen.kt` (nuevo), `composeApp/src/commonMain/kotlin/com/playbook/app/LabelChip.kt` (nuevo), `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: al guardar/cancelar en el editor abierto desde el GDD se vuelve a la lista, no al GDD (limitación aceptada; `previousDestination` llega con una feature de navegación/detalle). No hay harness de UI/E2E (la spec lo justifica): la proyección se cubre con tests cross-platform y la UI con smoke manual Android; el GDD en iOS no se pudo navegar por falta de input automation (build/link/launch/OCR del "Ver GDD" sí). Sin detalle de sólo lectura ni edición de adjuntos.
- Next best step: validación independiente de `gdd-view` con `$feature-validator`; tras el `accept`, crear spec de `semantic-linking` o `voice-attachment-storage` con `$feature-spec`.

### Session 031

- Date: 2026-10-13
- Goal: validación independiente de `gdd-view` (spec `docs/specs/gdd-view.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `gdd-view` promovida a `accepted` en `feature_list.json` con evidencia de validación; commit `feature: complete gdd-view` creado por el orquestador y PR draft contra `develop` (push pedido explícitamente por el owner).
- Verification run:
  - `./init.sh` → exit 0 con el gate real, sin cambios y sin dev servers/simuladores.
  - `:core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL (66 tareas, 3m27s); GddProjectionAndroidTest/GddProjectionIosTest 1/1 y los tests previos verdes (11 clases, 0 failures por plataforma, `core/build/test-results/*/TEST-*.xml`).
  - `xcodebuild` scheme `iosApp` (iPhone 15, iOS 17.2) → BUILD SUCCEEDED.
  - Esquema: `PlaybookDatabaseImpl.Schema.version = 4`; `.sqm` `1/2/3` (ninguno nuevo); `git diff -- Note.sq` vacío; sin dependencias nuevas (`libs.versions.toml`/build files sin cambios); `init.sh` sin cambios.
  - Escenarios 1–8 verificados: agrupación por track con orden fijo y preservación de orden intra-sección, nota sin track en "Sin track", resumen global/por sección con chips textuales, navegación `List → Gdd → Edit → List`, estado vacío, determinismo del `buildGdd` puro, refresco por `refreshKey` y gate honesto.
  - Control negativo propio del validador (preservación del orden intra-sección: `buildGdd.reversed()`) hace fallar `GddProjectionAndroidTest.projectionGroupsByTrack` (`mecánicas conserva el orden`); restaurado byte-idéntico y re-verificado en verde.
  - Sin DI/`ViewModel`/Flow/librería de navegación; dirección `UI → core` respetada; sin hallazgos de seguridad (sin red/auth/secretos/permisos ni cambios de manifest/`Info.plist`).
- Evidence captured: línea de validación en el arreglo `evidence` de `gdd-view` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md` (por el orquestador al persistir la aceptación).
- Known risk or unresolved issue: hallazgos Low no bloqueantes del validador — F1 línea en blanco al final de `docs/technical-discovery.md`; F2 comentario de `LabelChip.kt` que menciona el editor (que usa su propio `TagChip`); F3 aserción tautológica de no-mutación en `GddProjectionCheck.kt`; F4 smoke iOS del GDD no automatizado (limitación de tooling, declarada honestamente). Al guardar/cancelar en el editor abierto desde el GDD se vuelve a la lista, no al GDD (limitación aceptada; `previousDestination` en una feature de navegación/detalle posterior).
- Next best step: crear spec de `semantic-linking` o `voice-attachment-storage` con `$feature-spec` (ambas con dependencias ya `accepted`).
### Session 032

- Date: 2026-10-13

- Goal: implementar `ai-enrichment-classify-tags-type` (spec `docs/specs/ai-enrichment-classify-tags-type.md`): indexado asíncrono + transiciones de `NoteStatus`.
- Completed:
  - Alcance re-derivado por el planner: la feature es el pipeline de **indexado asíncrono** + transición de `NoteStatus`; **no** clasifica ni etiqueta (clasificación IA descartada en `CONTEXT.md`), no crea Enlaces (`semantic-linking`) y no escribe `FAILED`/reintenta (`offline-pending-retry`).
  - `:core`: query `updateNoteStatus` en `Note.sq` (sólo query; **sin cambio de esquema**, sigue v4) y `NoteRepository.updateStatus(id, status)` + implementación en `SqlDelightNoteRepository` (escribe sólo `status`, **no** re-sella `updated_at`; `rowsAffected > 0`).
  - `NoteIndexingOrchestrator(indexingService, noteRepository)` (`com.playbook.core.ai`): `suspend index(note): NoteStatus` reutiliza `NoteIndexingService` sin modificarlo; éxito→`INDEXED`; fallo→`PENDING` salvo Nota ya `INDEXED` (se queda `INDEXED`, embedding previo intacto); nunca propaga el fallo de IA ni escribe `FAILED`.
  - Tests: `verifyNoteIndexingOrchestrator(driver)` (escenarios 1–7) en `commonTest` + `@Test` Android (`JdbcSqliteDriver.IN_MEMORY`) e iOS (`inMemoryDriver`); `verifyNoteCrud` extendido con el Scenario 8 (`updateStatus` sólo toca `status`, id inexistente→false).
  - Docs: `ARCHITECTURE.md` (AI, dominio/repositorio y query), `docs/technical-discovery.md` (nueva "Indexing Decisions"), `CONSTRAINTS.md` (MUST NOT durable de indexado) y `AGENTS.md` (línea "Próxima feature en cola").
- Verification run:
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; Android/JVM **17/17** (NoteIndexingOrchestratorAndroidTest 1/1 nuevo, NoteRepositoryAndroidTest 3/3 con Scenario 8, NoteIndexing 1/1, EmbeddingRepository 1/1, AttachmentRepository 1/1, NotePersistence 2/2, DatabaseMigration 1/1, Migration 1/1, AiClient 3/3, AiRuntimeSpike 2/2, Greeting 1/1), 0 failures.
  - `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; iOS **17/17** (NoteIndexingOrchestratorIosTest 1/1 nuevo), 0 failures.
  - `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (57 tasks).
  - `PlaybookDatabaseImpl.Schema.version = 4`; `find core/src -name '*.sqm'` → `1.sqm`/`2.sqm`/`3.sqm` (sin `.sqm` nuevo); `Note.sq` sólo suma la query.
  - `./init.sh` → exit 0, sin procesos de larga duración; `init.sh` sin cambios.
  - Controles negativos reproducidos y restaurados byte-idénticos: (1) orquestador forzado a `INDEXED` siempre → falla `NoteIndexingOrchestratorAndroidTest` (aserciones de fallo→`PENDING`: escenarios 2/3/5); (2) `updateNoteStatus` con `updated_at = updated_at + 1` → falla `NoteRepositoryAndroidTest.crudRoundTrip` (Scenario 8). Suites re-ejecutadas en verde tras restaurar.
  - Alcance verificado: `git diff` vacío para `NoteIndexingService.kt`, `AiClient.kt`, `embedding.sq`, `1.sqm`, `:composeApp`, `iosApp`, `init.sh` y `libs.versions.toml`; sin clasificación IA/etiquetas IA ni entidad `enlace` en el diff.
- Evidence captured: arreglo `evidence` de `ai-enrichment-classify-tags-type` en `feature_list.json` (11 líneas) + `docs/validations/ai-enrichment-classify-tags-type.md`.
- Files or artifacts updated: `core/src/commonMain/sqldelight/com/playbook/core/db/Note.sq`, `core/src/commonMain/kotlin/com/playbook/core/repository/{NoteRepository,SqlDelightNoteRepository}.kt`, `core/src/commonMain/kotlin/com/playbook/core/ai/NoteIndexingOrchestrator.kt` (nuevo), `core/src/commonTest/kotlin/com/playbook/core/ai/NoteIndexingOrchestratorCheck.kt` (nuevo), `core/src/commonTest/kotlin/com/playbook/core/repository/NoteRepositoryCheck.kt`, `core/src/androidUnitTest/kotlin/com/playbook/core/ai/NoteIndexingOrchestratorAndroidTest.kt` (nuevo), `core/src/iosTest/kotlin/com/playbook/core/ai/NoteIndexingOrchestratorIosTest.kt` (nuevo), `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: sin wiring a la app ni UI (intencional; no hay disparador de ciclo de vida ni superficie observable, llega con `note-enrichment-status-ui`/`offline-pending-retry`). `FAILED` sigue sin escribirse (decisión de alcance). No hay E2E persistente (la spec lo justifica). La terminología obsoleta "categoría/tipo/etiquetas IA" de features vecinas (`note-enrichment-status-ui`, `gdd-view`, `offline-pending-retry`, `enrichment-observability-logging`) sigue registrada para re-derivarse.
- Validation: validación independiente (`docs/validations/ai-enrichment-classify-tags-type.md`) → **accept** (2026-10-13). El validador re-ejecutó `./init.sh` (exit 0), los tests del core en Android/JVM e iOS (17/0 en cada plataforma), `assembleDebug` + link del framework iOS y confirmó esquema v4 sin `.sqm` nuevo; reprodujo y restauró sus propios controles negativos y no halló findings Critical/High/Medium (dos Low no bloqueantes: cita de escenarios corregida a 2/3/5 y el contrato del llamador sobre `note.status`). Feature promovida a `accepted`; commit `feature: complete ai-enrichment-classify-tags-type`.
- Next best step: crear spec de `semantic-linking` (o `note-enrichment-status-ui`/`offline-pending-retry`, ya dependency-ready) con `$feature-spec`.

### Session 033

- Date: 2026-10-13

- Goal: implementar `semantic-linking` (spec `docs/specs/semantic-linking.md`): enlazado por similitud coseno en el core (dominio + persistencia + servicio), esquema v5 con `4.sqm`.
- Completed:
  - Esquema **v5**: nueva tabla `note_link` (`note_a_id`/`note_b_id` FK → `note(id) ON DELETE CASCADE`, `score`, `updated_at`, PK `(note_a_id, note_b_id)`, `CHECK (note_a_id < note_b_id)` + índice `note_link_note_b_id`) y cuarta migración `4.sqm` (v4 → v5). `PlaybookDatabase.Schema.version` pasa a 5.
  - Dominio: `NoteLink` (canónico) + `NoteLink.otherNoteId(of)` (fail-fast) + `NoteLinkMappers` (`Note_link as NoteLinkRow`); `CosineSimilarity.score(a, b): Double?` (puro common; `null` para dimensión distinta o norma 0).
  - Persistencia: `NoteLinkRepository` + `SqlDelightNoteLinkRepository(database, clock)` (`upsert` canoniza y valida, `getLinksForNote` consulta ambos lados, `getAll`, `deleteAll`).
  - Servicio: `SemanticLinkingService` + `LinkingSummary` con `DEFAULT_SIMILARITY_THRESHOLD = 0.8` inclusivo e inyectable; `recomputeAll()` borra y reconstruye (determinista e idempotente); no toca `NoteStatus` ni se cablea a la app.
  - Tests nuevos: `CosineSimilarityTest` (7), `verifyNoteLinkRepository` + runners Android/iOS, `verifySemanticLinking` + runners Android/iOS. Actualizados a v5: `DatabaseMigrationCheck` (columnas/FKs de `note_link`), `MigrationCheck.verifyMigrationV1ToV5`, `NoteTagCheck.verifyNoteTagMigration` y los nombres de los `@Test` en los runners de migración.
  - Docs durables: `ARCHITECTURE.md` (esquema v5, tabla/repos/servicio), `docs/technical-discovery.md` ("Linking Decisions (semantic-linking)"), `docs/risks-and-open-questions.md` (direccionalidad/umbral resueltos; trigger como follow-up), `AGENTS.md` (estado/próxima feature) y tasks de la spec.
  - **Desvío menor documentado:** el archivo `.sq` se llamó `NoteLink.sq` (no `note_link.sq`) porque SQLDelight deriva el accessor del nombre del archivo (`NoteLink.sq` → `database.noteLinkQueries`, convención `NoteTag.sq` → `noteTagQueries`); la tabla sigue siendo `note_link` y la data class generada es `Note_link`. Registrado en la spec.
- Verification run:
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL in 43s (16 tasks); 25 tests, 0 failures (core/build/test-results/testDebugUnitTest/).
  - `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; 25 tests, 0 failures (core/build/test-results/iosSimulatorArm64Test/).
  - `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL in 2m39s (57 tasks).
  - `PlaybookDatabase.Schema.version` generado `get() = 5`; `4.sqm` presente; `Note.sq`/`embedding.sq`/`.sqm` previos sin cambios (git diff vacío).
  - `./init.sh` → exit 0, sin dev servers ni procesos de larga duración; `init.sh` sin cambios.
  - Control negativo 1: `>=` → `>` en `SemanticLinkingService` hace fallar `SemanticLinkingAndroidTest` (`AssertionError: el par con coseno == umbral se incluye (>=) expected:<1> but was:<0>`); restaurado (SHA-256 coincide) y verde.
  - Control negativo 2: renombrar `note_link`→`note_link_broken` en `4.sqm` hace fallar `MigrationAndroidTest`/`DatabaseMigrationAndroidTest` con `SQLiteException: ... no such table: note_link`; restaurado (SHA-256 coincide) y verde.
  - Smoke real de migración v4 → v5: Android (emulator-5556) `user_version` 4→5, `note_link` + índice creados, Notas/embedding preservados, sin crashes; iOS (iPhone 15, iOS 17.2) idem con la app viva y sin crash reports. Datos de smoke limpiados.
  - E2E persistente: no aplica (sin UI ni wiring; justificado en la spec).
- Evidence captured: arreglo `evidence` de `semantic-linking` en `feature_list.json` (13 líneas).
- Files or artifacts updated: `core/src/commonMain/sqldelight/com/playbook/core/db/NoteLink.sq` (nuevo), `.../db/4.sqm` (nuevo), `core/src/commonMain/kotlin/com/playbook/core/model/{NoteLink,NoteLinkMappers}.kt` (nuevos), `.../linking/{CosineSimilarity,SemanticLinkingService}.kt` (nuevos), `.../repository/{NoteLinkRepository,SqlDelightNoteLinkRepository}.kt` (nuevos), tests nuevos (`CosineSimilarityTest`, `NoteLinkCheck`, `SemanticLinkingCheck` y runners Android/iOS), `DatabaseMigrationCheck`/`MigrationCheck`/`NoteTagCheck` + runners de migración actualizados a v5, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `AGENTS.md`, `docs/specs/semantic-linking.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el archivo `NoteLink.sq` difiere del nombre planeado (documentado); la tabla `note_link` usa FK cascade (no se toca `SqlDelightNoteRepository.delete`); el **trigger** del recomputo y la UI de lectura (`note-links-ui`) siguen pendientes; sin wiring en la app. Emulator-5554 estaba roto (sin servicio `package`), el smoke Android se hizo en emulator-5556.
- Next best step: validación independiente de `semantic-linking` con `$feature-validator`; tras el `accept`, crear spec de `note-links-ui` (depende de esta feature) o de otro candidato ready (`voice-attachment-storage`, `rag-query`, `gdd-view`) con `$feature-spec`.

### Session 034

- Date: 2026-10-13

- Goal: validación independiente de `semantic-linking` (spec `docs/specs/semantic-linking.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `semantic-linking` promovida a `accepted` en `feature_list.json` con evidencia de validación; commit `feature: complete semantic-linking` creado por el orquestador.
- Verification run:
  - `./init.sh` → exit 0, sin dev servers/simuladores.
  - `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test --rerun-tasks` → 25 tests / 0 failures en cada plataforma (conteos de `core/build/test-results/*/TEST-*.xml`); `:composeApp:assembleDebug` + link del framework iOS → BUILD SUCCESSFUL (57 tasks); `xcodebuild` scheme iosApp (iPhone 15, iOS 17.2) → BUILD SUCCEEDED.
  - Esquema: `Schema.version == 5`; `4.sqm` byte-equivalente a `NoteLink.sq` y a `Schema.create` (PK `(note_a_id, note_b_id)`, `CHECK`, 2 FK `ON DELETE CASCADE`, índice). Escenarios 1–9 cubiertos.
  - Controles negativos propios del validador, reproducidos y restaurados byte-idénticos: umbral `>=`→`>` falla `SemanticLinkingAndroidTest`; `note_link`→`note_link_broken` en `4.sqm` falla `MigrationAndroidTest`/`DatabaseMigrationAndroidTest`.
  - Smoke iOS real v4→v5 reproducido de forma independiente (`user_version=5`, `note_link` + índice, fila legacy preservada, sin crashes). Smoke Android no re-ejecutado por el validador (sin `adb` en su entorno; cubierto por tests portables).
  - Alcance: `git diff` vacío en `:composeApp`/`iosApp/**`/`init.sh`/`gradle/libs.versions.toml`/`Note.sq`/`embedding.sq`/`1-3.sqm`; sin transición de `NoteStatus`; sin wiring. Sin hallazgos de seguridad.
- Evidence captured: línea de validación en el arreglo `evidence` de `semantic-linking` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: findings Low no bloqueantes — smoke Android de migración no re-ejecutado por el validador (sin `adb`), `linksWritten` cuenta upserts (correcto porque `deleteAll()` precede) y `CosineSimilarity` devuelve `null` para dos vectores vacíos (conforme a la spec). El trigger del recomputo y la UI de lectura (`note-links-ui`) siguen pendientes; `note-links-ui` queda desbloqueada.
- Next best step: crear spec de `note-links-ui` con `$feature-spec` e implementarla con `$feature-implementer` (primera ready en orden), o de otro candidato ready (`voice-attachment-storage`, `gdd-view`, `rag-query`).
