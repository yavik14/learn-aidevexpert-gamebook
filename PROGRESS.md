# Progress Log

## Current Verified State

- Repository root: `/Users/javierrodriguez/Alt10/course/ai/devexpert/learn-aidevexpert-gamebook`
- Standard startup path: `./init.sh`
- Standard verification path: `./init.sh` (gate no bloqueante **Android-only**) corre `:composeApp:assembleDebug` + `:core:testDebugUnitTest`, sin levantar dev servers. iOS está congelado (ADR 0003).
- Última feature `accepted`: `ai-suggest-classification` (2026-10-20; Sessions 079/080; validación independiente `accept` en `docs/validations/ai-suggest-classification.md`); antes `transcribe-audio-attachment` (Sessions 076/077), `gdd-document-hybrid` (Sessions 074/075), `first-run-project-onboarding` (Sessions 071/072), `brainstorming-note-per-step`, `brainstorming-ai-suggestions` (Sessions 069/070), `sketch-thumbnails`, `inline-dictation-30s`, `ai-real-embeddings`, `brainstorming-wizard`, `ai-fake-real-switch`, `game-project-persistence` y `genres-catalog-ui`.
- `ai-suggest-classification` `accepted` (2026-10-20; Session 079 implementación + Session 080 cierre; validación independiente `accept` en `docs/validations/ai-suggest-classification.md`): sugerencia efímera de `Track`/Géneros con `JevClient` — `ClassificationSuggestionService` en `:core` (timeout 20 s, normalización con `normalizeGenres`, `applyClassificationSuggestion`), `FakeJevClient` con Género del catálogo determinista por hash, UI `Idle`/`Loading`/`Ready`/`Empty`/`Error` en `NoteEditorScreen` (Confirmar/Descartar/Reintentar, cambios manuales descartan) y wiring de `jevClient` en `App`/roots. Sin persistir la propuesta ni cambios de esquema (v8)/runtime/puertos/build files.
- `ai-client-interface` `accepted`: puerto `AiClient` + `FakeAiClient` en `:core` (runtime sustituible, sin wiring a la app todavía).
- `ai-runtime-decision` `accepted` (2026-10-09): ADR 0001 (híbrido por fases, cloud-first, detrás de `AiClient`) + spike test-only sin red + docs; validada de forma independiente.
- `android-release-pipeline` `accepted`: signing de release Android condicional (precedencia env `PLAYBOOK_*` > `keystore.properties`, keystore fuera del repo) y runbook `docs/release/android.md`.
- `embeddings-generation` `accepted`: tabla `embedding` (esquema v2 con `1.sqm`, FK cascade) + `EmbeddingRepository` + `NoteIndexingService` como primer consumidor de `AiClient`.
- `voice-capture-stt` `accepted`: puerto `VoiceTranscriber` + adaptadores nativos (`SpeechRecognizer` en Android; `SFSpeechRecognizer`+`AVAudioEngine` en iOS) en `:composeApp`; el dictado crea una `Note` transcripta vía `create(NoteDraft(...))`. Sin cambios en `:core`.
- `note-category-and-tags` `accepted` (2026-10-09): Etiquetas libres, múltiples y editables en la Nota; esquema v3 (`note_tag`, migración `2.sqm` v2→v3); clasificación fija por `Track` ya existente.
- `image-capture-camera` `accepted` (2026-10-09): captura de imagen (cámara/galería) → Nota con Adjunto; tabla `attachment` (esquema **v4**, migración `3.sqm` v3→v4) + `AttachmentRepository` + integración nativa Android/iOS.
- `image-ocr-optional` `accepted` (2026-10-13): feature **opcional/min** resuelta como **decisión + documentación** — **DISCARD del OCR para el MVP** (validación independiente `accept`). Entregable: `docs/adr/0002-ocr-decision.md` (MADR-lite, promovido a `accepted`) + docs durables actualizados; sin código/deps/esquema.
- `gdd-view` `accepted` (2026-10-13): primera vista de GDD — proyección pura de las Notas por `track` (`buildGdd`/`GddSection` en `:core`) + pantalla `GddScreen` (secciones por track, conteo global/por sección, chips de Etiquetas/`status`/"Imagen", abrir nota y "Volver"), sin tabla ni esquema nuevos (v4). Validada de forma independiente.
- `ios-release-pipeline-scaffolding` `accepted` (2026-10-14; validación independiente `accept` en `docs/validations/ios-release-pipeline-scaffolding.md`): artefactos repo-side de release iOS verificados (scripts, scheme compartido, firma/identidad, versionado, `.gitignore`, runbook; Scenarios 1/2/7/8). El upload firmado queda en `ios-release-pipeline-first-upload` (`blocked`, account-gated).
- `ios-release-pipeline-first-upload` `blocked`: archive firmado + export `.ipa` + upload a TestFlight + device real (Scenarios 3–6) requieren Apple Developer Program + `TEAM_ID` + credenciales, ausentes en el entorno. Probado con `scripts/ios-release/preflight.sh` → exit 1. Ver `docs/release/ios-testflight.md`.
- `ai-enrichment-classify-tags-type` `accepted` (2026-10-13; validación independiente `accept` en `docs/validations/ai-enrichment-classify-tags-type.md`): indexado asíncrono + transiciones de `NoteStatus`. `NoteIndexingOrchestrator.index(note)` reutiliza `NoteIndexingService` y persiste: éxito→`INDEXED`; fallo→`PENDING` salvo Nota ya `INDEXED` (no se degrada); **nunca** escribe `FAILED`. `NoteRepository.updateStatus` (query `updateNoteStatus`, sólo `status`, sin re-sellar `updated_at`) sin cambio de esquema (sigue v4). Alcance re-derivado: la IA no clasifica ni etiqueta.
- `semantic-linking` `accepted` (2026-10-13; validación independiente `accept`): Enlaces Nota↔Nota por similitud coseno (esquema **v5**, migración `4.sqm` v4→v5, tabla `note_link` con PK canónica `note_a_id < note_b_id` + FK cascade); `CosineSimilarity`, `NoteLink`/`NoteLinkMappers`/`otherNoteId`, `NoteLinkRepository`/`SqlDelightNoteLinkRepository` y `SemanticLinkingService.recomputeAll()` + `LinkingSummary` (umbral `0.8` inclusivo e inyectable). Core puro, sin UI ni wiring.
- `voice-attachment-storage` `accepted` (2026-10-13; validación independiente `accept`): el audio original del dictado se graba en la misma sesión de micrófono y se persiste como Adjunto `AUDIO` (`AttachmentKind.AUDIO("audio")`, `audio/<uuid>.m4a`, `audio/mp4`) separado de la transcripción; sin cambio de esquema (sigue v4, sin `.sqm`), sin dependencias ni permisos nuevos. Chips de texto "Imagen"/"Audio" en la lista.
- `rag-query` `accepted` (2026-10-13; validación independiente `accept`): consulta en lenguaje natural sobre las Notas — `cosineSimilarity` + `RagQueryService.ask(question)` en `:core` (retrieval top-K por similitud coseno en memoria + `generate(GenerateRequest(prompt, context))`), `AskNotesScreen`/`AskUiState` + acción "Preguntar", y el primer wiring del runtime de IA a la app (`FakeAiClient` + `NoteIndexingService` index-on-save best-effort + `RagQueryService`).
- `rag-answer-citations` `accepted` (2026-10-14; validación independiente `accept` en `docs/validations/rag-answer-citations.md`): la respuesta RAG surfacea sus Notas fuente como **citas numeradas** ("Fuentes", rank 1..N + fragmento + `track`) y permite abrir la Nota (`NotesDestination.Edit`); sólo UI (`AskNotesScreen.kt` + `App.kt`) + docs, `:core` y esquema (v5) sin cambios.
- `note-links-ui` `accepted` (2026-10-14; validación independiente `accept` en `docs/validations/note-links-ui.md`): superficie de lectura de los Enlaces — proyección pura `buildRelatedNotes`/`RelatedNote` en `:core` + `RelatedNotesScreen` (estado vacío explícito, tarjetas con `score` textual y chips) + `NotesDestination.Related(noteId)` + botón "Ver relacionadas" en el editor + wiring de `SqlDelightNoteLinkRepository`/`SemanticLinkingService` en los composition roots; **trigger mínimo** best-effort al abrir. Esquema **v5**, sin `.sqm` ni dependencias nuevos.
- `note-enrichment-status-ui` `accepted` (2026-10-14): el estado de indexado es visible y verdadero — `EnrichmentStatus.kt` (`statusLabel`/`StatusBadge`, texto + forma, no sólo color) en la lista y el editor; `App` usa `NoteIndexingOrchestrator` (reemplaza el best-effort directo con `NoteIndexingService`) y hace `reload()` tras indexar; el editor ofrece **re-indexado manual** (botón "Re-indexar" habilitado si `status != INDEXED` y `body` no vacío) que preserva `track`/`tags`/`body`. Regresión del orquestador en `:core`. Esquema **v5**, sin `.sqm` ni dependencias nuevas. `accepted` (validación independiente `accept`, 2026-10-14).
- `enrichment-observability-logging` `accepted` (2026-10-14; Session 042; validación independiente `accept`): observabilidad mínima del MVP — puerto `Diagnostics` best-effort (`NoOpDiagnostics` default + `InMemoryDiagnostics` + `FileDiagnostics` con `DiagnosticStorage` expect/actual) en `:core`; instrumenta `AI_INDEXING`/`AI_QUERY` (`NoteIndexingService`/`RagQueryService`, retrocompatibles con `NoOpDiagnostics`) y `VOICE`/`IMAGE`/`STORAGE` (`App`); sink `FileDiagnostics` cableado en los composition roots (`filesDir/diagnostics.log` Android, `Application Support/diagnostics.log` iOS). Sin esquema (sigue v5), sin `.sqm`, sin dependencias nuevas ni UI/telemetría remota.
- `offline-pending-retry` `accepted` (2026-10-15; Session 044; validación independiente `accept` en `docs/validations/offline-pending-retry.md`): cierra el ciclo offline/online — reintenta las Notas `PENDING` al arrancar y promueve a `FAILED` las que agotan el presupuesto. Esquema **v6** con `5.sqm` (`note.index_attempts INTEGER NOT NULL DEFAULT 0`); `Note.indexAttempts`; `NoteRepository.getByStatus`/`setIndexAttempts`; `PendingIndexingRetry` + `AiAvailability` (`AlwaysAvailableAiAvailability` default; `false` = no-op); wiring en `App` (`LaunchedEffect(Unit) { retryPending(); reload() }`) + roots. `FAILED` sólo lo escribe el reintento; sin conectividad real ni scheduling. Ver `feature_list.json` y `docs/technical-discovery.md`.
- `gdd-export` `accepted` (2026-10-15; Session 045; validación independiente `accept` en `docs/validations/gdd-export.md`): export del GDD a Markdown (`buildGddMarkdown(notes, now)` en `:core`, proyección pura con el "Format Contract") + puerto `GddSharer` (expect/actual; `ACTION_SEND` en Android, `UIActivityViewController` en iOS; share **en memoria**, sin archivos/permisos) + botón "Compartir" (deshabilitado sin Notas) y snackbar en `GddScreen` (`Scaffold`/`SnackbarHostState` local, sin nuevos parámetros, por lo que **`App.kt` no cambió**); `topViewController()` extraído a `IosViewControllers.kt` compartido con `ImageCapture.ios.kt`. No agrega `.sqm` ni dependencias nuevas (el esquema global queda en **v6** por `offline-pending-retry`).
- **Revisión de alcance 2026-10-15 (Android-first):** iOS **congelado** (ADR 0003); Etiquetas→**Géneros** (catálogo fijo + personalizados); **Proyecto de Juego** (ficha singleton) + **Brainstorming**; GDD **documento híbrido** (ficha + secciones por Track); principal rediseñada (top bar "Playbook" + iconos + FAB desplegable), bocetos con miniatura, dictado inline 30s, Q&A "guardar como Nota"; e **IA real** con proveedor propio OpenAI-compatible (`https://inference.devexpert.io/v1`; modelos chat/embedding/transcribe/tts/image/jev; alternancia fake/real por build flag; la IA **sugiere** y el usuario **confirma**). Diseño en `DESIGN.md` (paleta `docs/design/paleta.png`). Esquema previsto **v7** (Géneros) y **v8** (Proyecto). Backlog re-derivado: **21 features `not_started`** (incluye `app-icon-configuration`).
- `app-icon-configuration` `accepted` (2026-10-16; validación independiente `accept` en `docs/validations/app-icon-configuration.md`): icono de launcher Android desde `docs/design/icon.png` (**RGB sin alfa**, fondo `#F7EFE2`) — adaptive icon (`mipmap-anydpi-v26/ic_launcher.xml` = `@color/ic_launcher_background` + `@drawable/ic_launcher_foreground`) + PNG legacy por densidad (48/72/96/144/192) y `android:icon`/`android:roundIcon` en el manifest. Generado con `sips` (sin deps); foreground en `drawable-nodpi` (432 px, safe zone ~66 %), sin costura. Sin cambios en `:core`/`iosApp`/`init.sh`/build files/Kotlin/esquema. Ver `docs/technical-discovery.md`.
- `ai-client-ports` `accepted` (2026-10-16; Session 049; validación independiente `accept` en `docs/validations/ai-client-ports.md`): puertos de IA `TranscribeClient`/`TtsClient`/`ImageClient`/`JevClient` + fakes deterministas en `:core`; chat/embedding siguen en `AiClient`; sin adaptadores reales ni build flag (feature posterior). Ver `docs/technical-discovery.md`.
- `ai-fake-real-switch` `accepted` (2026-10-19; Sessions 055/056; validación independiente `accept`): build flag `playbook.ai.mode` (`fake` default / `real`), 6 adaptadores reales OpenAI-compatible en `:core` detrás de `HttpTransport`, `UrlConnectionHttpTransport` Android con I/O en `Dispatchers.IO` y sin redirects, `AiRuntime`/`ConfiguredAiAvailability`, reconciliación de dimensión de embeddings (`1536` real / `8` fake), smoke real key-gated de los 6 modelos (fuera del gate), guard release y key nunca versionada. ADR 0004 promovido a `accepted`. Ver `docs/specs/ai-fake-real-switch.md` y `docs/technical-discovery.md`.
- `genres-catalog-persistence` `accepted` (2026-10-16; Session 050; validación independiente `accept` en `docs/validations/genres-catalog-persistence.md`): Etiquetas→**Géneros** en `:core` — `GenreCatalog` (12 fijos) + `normalizeGenres` (canonización case-insensitive) y esquema **v7** (`6.sqm`: `note_tag`→`note_genre` con copia verbatim + `DROP TABLE note_tag`); `Note.tags`→`Note.genres`, `noteTagQueries`→`genreQueries`, `normalizeTags`→`normalizeGenres`, `replaceTags`→`replaceGenres`; GDD emite `Géneros:`; renombre mecánico + rótulos ("Géneros"/"Nuevo género") en `:composeApp` (sin picker de catálogo). `:core:testDebugUnitTest` 47/0, `:composeApp:assembleDebug` OK, `./init.sh` exit 0; iOS no bloqueante OK. Ver `feature_list.json`.
- `game-project-persistence` `accepted` (2026-10-19; Session 052 + validación independiente `accept` el 2026-10-19, persistida en Session 054): Proyecto de Juego singleton en `:core` — esquema **v8** (`7.sqm` aditivo) con `game_project` (`id = 'singleton'` + CHECK) y `game_project_genre`/`_inspiration`/`_key_mechanic`/`_out_of_scope`; `GameProject`/`GameProjectDraft`/`KeyMechanic`/`MechanicCategory` + `GameProjectRepository`/`SqlDelightGameProjectRepository` (`get`/`save` upsert; preserva `owner`/`createdAt`, límites 3/3/5, Géneros con `normalizeGenres`). Repair de validación: orden de Géneros case-insensitive en `get()`, regresión mixed-case y Scenario 10 con Adjunto/Enlace/Embedding. `:core:testDebugUnitTest` 55/0, `:composeApp:assembleDebug` OK, `./init.sh` exit 0; iOS no bloqueante 55/0. Ver `docs/specs/game-project-persistence.md`, `docs/validations/game-project-persistence.md` y `feature_list.json`.
- `ai-real-embeddings` `accepted` (2026-10-19; Sessions 061/062; validación independiente `accept`): embeddings reales en el indexado sin duplicar `ai-fake-real-switch` — guard de dimensión en `OpenAiAiClient.embed` (`1536`) y `NoteIndexingService.expectedDimension` (mismatch no persiste, `AI_INDEXING`, sigue `PENDING`/reintento), pass-through de `aiRuntime.embeddingDimension` en roots Android/iOS, integración `openAiAiRuntime + HttpTransport falso + SQLDelight in-memory` (éxito, RAG, Enlaces, red/500, transitorio, 8→1536) y smoke real key-gated de indexado (`dim=1536`, `INDEXED`, RAG con fuentes). Post-rebase sobre `develop` `65adc62`: gate 0, `:core` 79/0, `:composeApp` 7/0, iOS 79/0 y link OK; esquema v8 sin `.sqm` nuevos. Ver `docs/specs/ai-real-embeddings.md` y `docs/technical-discovery.md`.
- `genres-catalog-ui` `accepted` (2026-10-19; Sessions 052/053; validación independiente `accept` en `docs/validations/genres-catalog-ui.md`; rebasada sobre `develop` con `game-project-persistence` v8): picker de **Géneros** en `:composeApp` — 12 chips de catálogo en `FlowRow` con toggle, "Seleccionados" con `×`, alta de personalizado y toda alta/remoción vía `normalizeGenres`; `GenreLabelChip` distingue catálogo (`primaryContainer`) vs personalizado (`secondaryContainer`) en tarjetas, GDD y relacionadas. Incluye el **fix de layout** tras la validación `revise`: `NoteEditorScreen` con contenido scrollable y fila Borrar/Cancelar/Guardar fija (cuerpo `heightIn(200..320dp)`). No toca `:core`/esquema (el global es v8 por `game-project-persistence`)/`App.kt`/build files/deps. Gate en la base rebasada: `assembleDebug` OK, `:core:testDebugUnitTest` 23 suites/55 tests/0 failures, `./init.sh` exit 0 e iOS link OK. Smoke Android completo (toggle, canonización, dedupe, tarjeta, reapertura, edición, 13 Géneros, GDD con personalizado y persistencia tras relanzar). Ver `feature_list.json`, `docs/validations/genres-catalog-ui.md` y `composeApp/build/smoke-evidence/genres-catalog-ui/`.
- `brainstorming-wizard` `accepted` (2026-10-19; Session 059 implementación + Session 060 cierre; validación independiente `accept` en `docs/validations/brainstorming-wizard.md`): wizard de 9 pasos para crear/iterar la **ficha del Proyecto** (`GameProject`), todos los pasos opcionales, progreso `Paso X/9`, guardado explícito de un `GameProjectDraft` completo, reapertura con precarga y Cancelar sin escritura. Reutiliza `GenrePicker`; topes 3/3/5; mecánica con descripción sin categoría bloquea con error; entrada temporal "Brainstorming" en la lista. `App` recibe `GameProjectRepository`, carga `project` y agrega `NotesDestination.Brainstorming`; roots cableados con `SqlDelightGameProjectRepository`. Sin IA, sin Notas por paso (`brainstorming-note-per-step`), sin cambios en `:core`/esquema (v8)/build files/`init.sh`/`iosApp`. `assembleDebug` OK, `:core` 25 suites/76 tests/0 failures en la base rebasada (23/55 en la validación), `./init.sh` exit 0 e iOS link OK; smoke Android completo con evidencia en `composeApp/build/smoke-evidence/brainstorming-wizard/`.
- `sketch-thumbnails` `passing` (2026-10-19; Session 061; pendiente de validación independiente): miniatura del boceto en las tarjetas de la **lista** y del **GDD**. El store de Adjuntos se reubicó en `:core` (`com.playbook.core.storage`: `StoredImage` + `AttachmentFileStore.saveImage/read/delete` + `expect fun attachmentFileStore(baseDir)`) con actuals Android (`java.io.File`, `canonicalFile`) e iOS (`NSFileManager`/`NSData`, normalización) y **guard de path traversal**; `read` devuelve los bytes exactos o `null`. `:composeApp` conserva `rememberAttachmentFileStore()` (sólo calcula `baseDir`) y `decodeImageBitmap`; `App` deriva `imagePaths` (sólo `IMAGE`, primero por Nota en orden `created_at ASC, id ASC`) + `loadThumbnail`, y `NoteThumbnail` (`produceState` + `Dispatchers.Default`, 72 dp/16 dp/`ContentScale.Crop`) degrada sin imagen ni hueco (el espaciado vive dentro del composable). Tests `AttachmentFileStoreCheck` + runners Android/iOS (Escenarios 1–4). Sin deps, esquema (v8), `.sqm`, `init.sh`, `iosApp` ni cacheo/redimensionado. `:core:testDebugUnitTest` 26 suites/77 tests/0 failures; iOS 26/77/0; `assembleDebug` y `init.sh` OK. Smoke Android no ejecutado (sin `adb`/emulador en el entorno). **Reparación post-validación (dos Low):** recorte a `rounded.md` de 16 dp y espaciado de la miniatura dentro de `NoteThumbnail` (sin hueco en degradación); guard iOS léxico documentado como riesgo residual aceptado. Re-verificado en verde. Ver `feature_list.json`.
- **Plan de ejecución del MVP restante** (2026-10-16): `docs/mvp-execution-plan.md` define 6 olas para las 18 features `not_started`, hasta **3 sesiones en paralelo**, una feature por sesión/worktree/rama, con paradas tras planner/implementer/validator y una sola cola de merge. Cierra con validación end-to-end y un `README.md` bitácora (`readme-project-documentation`, al final). Excepción controlada al WIP=1 documentada en `AGENTS.md`.
- `inline-dictation-30s` `accepted` (2026-10-19; Session 063 implementación + Session 064 cierre/integración; validación independiente `accept` en `docs/validations/inline-dictation-30s.md`): el dictado pasa a ser **inline en Nueva nota** (panel con progreso de 30s + parcial + Detener/Processing/Error), el cap arranca con `onStarted` (no cuenta el diálogo de permiso), auto-stop a los 30s, audio pendiente adjuntado al guardar y limpieza sin huérfanos; se retira `VoiceCaptureScreen`/`VoiceUiState`/`NotesDestination.VoiceCapture`. Sin deps, esquema ni `:core`. Detalle en `docs/specs/inline-dictation-30s.md`, `ARCHITECTURE.md` y `docs/technical-discovery.md`.
- `brainstorming-ai-suggestions` `accepted` (2026-10-19; Session 069 implementación + Session 070 cierre/integración; validación independiente `accept` en `docs/validations/brainstorming-ai-suggestions.md`): `BrainstormingSuggestionService` en `:core` (prompt/contexto por paso, parsing/dedupe, parcial/error y máximo 2) + `FakeAiClient` determinista + UI efímera en el wizard (Sugerir/Usar/Agregar/Ignorar) + wiring de `aiClient` en `App`/roots. Sin persistencia, Notas, esquema ni IA real. `:core` 28 suites/87 tests/0 failures en Android e iOS (post-rebase), `assembleDebug` OK, `./init.sh` exit 0 e iOS link OK; smoke Android con evidencia.
- `first-run-project-onboarding` `accepted` (2026-10-20; Session 071 implementación + Session 072 cierre; validación independiente `accept`): onboarding saltable del Proyecto en el primer arranque. `App` usa `ProjectLoadState` (`Loading`/`Missing`/`Loaded`) y, sin Proyecto, muestra `FirstRunOnboardingScreen` full-screen; “Iniciar Brainstorming” y “Saltar” crean primero el singleton vacío (`owner = "local"`, `id = "singleton"`, escalares `null`, listas/hijas vacías, `createdAt == updatedAt`, topes 3/3/5) y luego navegan al wizard o a la lista. Guard `onboardingBusy`, error con `AlertDialog` y sin flag persistido (la existencia del Proyecto evita reofrecer). Sin cambios en `:core`, esquema v8, raíces, build files, `init.sh` ni `iosApp`. Detalle en `docs/specs/first-run-project-onboarding.md`, `ARCHITECTURE.md` y `PROGRESS.md` (Sessions 071/072).
- `brainstorming-note-per-step` `accepted` (2026-10-20; Sessions 063/064 implementación+repair y Session 065 cierre; re-validación independiente `accept` en `docs/validations/brainstorming-note-per-step.md` §11): mapper puro `buildBrainstormingStepNote` + predicado `isDuplicateBrainstormingNote` (normaliza ambos lados de los Géneros) en `:core`, con tests Android/iOS; acción `Guardar como Nota` en los 9 pasos con confirmación, bloqueo de duplicados exactos por contenido, feedback inline y errores; `App` crea con `LOCAL_OWNER_ID` + `indexBestEffort` + `reload` y envuelve el chequeo/creación en `runCatching`. Sin `note.project_id`, IA generativa, cambios de esquema/build files/`init.sh`/`iosApp`. Gate: `:core` 29 suites/89 tests/0 failures (post-rebase), `assembleDebug` OK, `./init.sh` exit 0, link y test iOS OK; smoke/SQL en `composeApp/build/smoke-evidence/brainstorming-note-per-step/`.
- `gdd-document-hybrid` `accepted` (2026-10-20; Session 074 implementación + Session 075 cierre; validación independiente `accept`): GDD híbrido — `buildGddDocument(project, notes)` en `:core` (ficha con orden fijo, `Título` en cabecera, categorías capitalizadas y secciones `mecánicas`/`personajes`/`historia`/`Sin track` reutilizando `buildGdd`) + `GddScreen` con cabecera fija, ficha en `Card`, estados sin Proyecto/Proyecto vacío/sin Notas y `App` pasando `project`. Sin cambios de esquema (v8), repositorios, `GddProjection`/`GddMarkdown` ni export; “Compartir” sigue sólo Notas. Tests 29/88 pre-rebase y 30/90 post-rebase, 0 failures en Android e iOS; smoke Android y gate post-rebase. Ver `docs/specs/gdd-document-hybrid.md`.
- `transcribe-audio-attachment` `accepted` (2026-10-20; Session 076 implementación + Session 077 cierre; validación independiente `accept` en `docs/validations/transcribe-audio-attachment.md`): transcripción **diferida** del Adjunto `AUDIO` de una Nota. `AudioAttachmentTranscriptionService` en `:core` (valida Nota/AUDIO, lee bytes con `AttachmentFileStore.read`, llama `TranscribeClient` una vez; `AudioNotFound`/`AudioUnavailable`/`EmptyTranscription`/`Failed`; no persiste ni indexa) + `AudioTranscriptionUiState` y sección "Audio adjunto" en `NoteEditorScreen` (selector determinista `Audio N · tamaño`, default primero, `Transcribir`/`Transcribiendo…`/`Reintentar`, `body` vacío directo, texto existente con preview y Anexar/Reemplazar/Cancelar) + job cancelable y wiring de `aiRuntime.transcribeClient` en `App`/raíces + `DiagnosticSource.AI_TRANSCRIBE`/`transcription_failed`. Sin deps, esquema (v8), `.sqm`, `init.sh` ni cambios en `AiRuntime`/`TranscribeClient`. Post-rebase sobre `9c75be4`: `:core` 31 suites/91 tests/0 failures en Android e iOS; gate 0; Android + link iOS OK; smoke post-rebase en emulator-5558. Ver `docs/specs/transcribe-audio-attachment.md`.
- `gdd-export-hybrid` `passing` (2026-10-20; Session 078; validación independiente `accept`, pendiente de persistencia de `accepted` por el orquestador): el Markdown del GDD incluye la ficha del Proyecto (campos/orden de `GddDocument`) además de las Notas. `buildGddMarkdown(project, notes, now)` reusa `buildGddDocument`/`buildGdd`; el overload `(notes, now)` delega con `project = null` y queda byte-idéntico. “Compartir” se habilita con Notas o ficha con título/campos; `GddSharer`, `App.kt`, esquema v8 e `init.sh` sin cambios. Post-rebase sobre `origin/develop` `dc74959`: tests 32 suites/92 tests/0 failures en Android/JVM e iOS; `assembleDebug`, link iOS y `CI=true ./init.sh` verdes. Smoke del implementer no ejecutado; el validador cubrió parcialmente “Notas + Proyecto vacío” en `emulator-5556` (chooser con la ficha/Notas), sin “Proyecto con campos sin Notas” ni “sin contenido → deshabilitado”. Ver `docs/specs/gdd-export-hybrid.md`.
- Current state: **41 `accepted`, 6 `not_started`, 1 `passing` y 1 `blocked`** (49 total). `ai-suggest-classification` quedó `accepted`; `gdd-export-hybrid` está validada (`accept`) y pendiente de persistencia de `accepted` por el orquestador; después siguen las demás `not_started` de las olas 4–6. `readme-project-documentation` espera el cierre del MVP. Ver `feature_list.json`.
- Current blocker: `ios-release-pipeline-first-upload` (account-gated).
- Last verified at: 2026-10-20.

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

### Session 035

- Date: 2026-10-13

- Goal: implementar `voice-attachment-storage` (spec `docs/specs/voice-attachment-storage.md`): grabar el audio original del dictado y conservarlo como Adjunto `AUDIO`, además de la transcripción.
- Completed:
  - `:core`: `AttachmentKind.AUDIO("audio")` (sin cambio de esquema; el mapper y `AttachmentRepository.create(...)` ya aceptaban cualquier `kind`). `verifyAttachmentCrud` suma el round-trip de un Adjunto `AUDIO` (`audio/round-trip.m4a`, `audio/mp4`) + `AttachmentKind.fromCode("audio")`.
  - Puerto de voz: `RecordedAudio(relativePath, byteSize, mimeType)` y `VoiceTranscriber.onFinalResult` pasa a `(String, RecordedAudio?)`; el texto y el audio cerrado llegan en un único callback terminal (sin carrera STT/cierre de archivo).
  - Android (`AndroidVoiceTranscriber`): `MediaRecorder` (MIC, MPEG_4, AAC) en paralelo con el `SpeechRecognizer`, salida a `filesDir/audio/<uuid>.m4a`; `finishAudioRecording()`/`discardAudioRecording()` cierran o borran el archivo; degradación tolerante si no puede iniciar/escribir.
  - iOS (`IosVoiceTranscriber`): el tap de `AVAudioEngine.inputNode` además escribe cada `AVAudioPCMBuffer` a un `AVAudioFile` AAC creado perezosamente en `Application Support/audio/<uuid>.m4a` (settings `kAudioFormatMPEG4AAC` + sampleRate/channels del buffer + bitrate 64k); `closeAudioRecording()`/`discardAudioRecording()` cierran o borran el parcial.
  - `App.onFinalResult(text, audio)`: texto vacío → `NO_MATCH` + borra el audio; con texto → crea la Nota y persiste el Adjunto `AUDIO`; si el Adjunto falla se borra el archivo y se conserva la Nota. `NotesListScreen` cambia `attachmentCounts` por `attachmentKinds: Map<String, Set<AttachmentKind>>` y renderiza chips textuales por tipo ("Imagen"/"Audio"). `VoiceCaptureScreen` suma una línea informativa.
  - Docs: `ARCHITECTURE.md` (AUDIO, grabación en la sesión de voz, UI por tipo, Deferred), `docs/technical-discovery.md` (nueva sección "Voice Attachment / Audio Recording Decisions"), `docs/risks-and-open-questions.md` (doble cliente de micrófono, verificación de audio real) y `AGENTS.md`; tasks de la spec marcadas.
- Verification run:
  - `./gradlew :core:testDebugUnitTest :composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64 --console=plain` → BUILD SUCCESSFUL in 1m 55s (62 tasks); `AttachmentRepositoryAndroidTest` 1/1 con el round-trip `AUDIO` en verde + el resto (3/3/2/3/2/1/1/1/1/1), 0 failures.
  - `./gradlew :core:iosSimulatorArm64Test --rerun-tasks --console=plain` → BUILD SUCCESSFUL in 1m 13s; `AttachmentRepositoryIosTest` 1/1 con el round-trip `AUDIO` + el resto en verde, 0 failures.
  - Control negativo propio: cambiar la aserción a `AttachmentKind.IMAGE` hace fallar `AttachmentRepositoryAndroidTest` con `AssertionError: expected:<IMAGE> but was:<AUDIO>` (`failures="1"`); restaurado y re-ejecutado en verde.
  - `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL; `xcodebuild` scheme `iosApp` → ** BUILD SUCCEEDED **.
  - `PlaybookDatabaseImpl.Schema.version = 4`; `find core/src -name '*.sqm'` = `1.sqm`/`2.sqm`/`3.sqm` (sin nuevos); `git diff -- Attachment.sq` vacío; sin cambios en build files/`init.sh`/`AndroidManifest.xml`/`Info.plist`/`iosApp`.
  - `./init.sh` → exit 0, sin procesos de larga duración.
  - Smoke Android (emulador `sdk_gphone64_arm64`/API 35): al 'Grabar' se crea un archivo parcial `files/audio/<uuid>.m4a` (confirmado con `run-as` + logcat de `MediaRecorder`/`AudioRecord`); con silencio → `NO_MATCH` y el archivo se borra sin Nota; 'Reintentar' + 'Cancelar' en vivo borra el parcial, vuelve a la lista sin filas; chips 'Audio'/'Imagen'/ambos verificados por UI dump con base sembrada; borrar la Nota elimina la fila `attachment` y el archivo de audio (la imagen de otra nota queda intacta); `user_version=4`.
  - Smoke iOS (iPhone 15, iOS 17.2): install/launch con PID vivo; `playbook.db` `user_version=4` con `note`/`attachment`/`note_tag`/`embedding`; OCR de la lista sembrada confirma chips 'Audio', 'Imagen'+'Audio' y ausencia de chips sin Adjuntos.
  - Limitación honesta: el happy path con voz real (dictar → Nota con Adjunto `AUDIO` con contenido) no es ejecutable de forma determinista (no se puede inyectar voz en emulador/simulador); los emuladores Android están compartidos con otras sesiones paralelas (se observó desinstalación externa del paquete y el foco robado por otra app). No se fabricó evidencia.
- Evidence captured: arreglo `evidence` de `voice-attachment-storage` en `feature_list.json` (11 líneas); capturas/UI dumps/DBs y helper OCR en el directorio ignorado `composeApp/build/smoke-evidence/`.
- Files or artifacts updated: `core/src/commonMain/kotlin/com/playbook/core/model/Attachment.kt`, `core/src/commonTest/kotlin/com/playbook/core/repository/AttachmentRepositoryCheck.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/voice/VoiceTranscriber.kt`, `composeApp/src/androidMain/kotlin/com/playbook/app/voice/AndroidVoiceTranscriber.kt`, `composeApp/src/iosMain/kotlin/com/playbook/app/voice/IosVoiceTranscriber.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/VoiceCaptureScreen.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `AGENTS.md`, `docs/specs/voice-attachment-storage.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: `MediaRecorder` + `SpeechRecognizer` comparten micrófono y algunos dispositivos pueden rechazarlo (mitigado con degradación tolerante; el emulador sí graba). La conversión a AAC en iOS con `AVAudioFile` no se validó con voz real (sólo compila/enlaza y el ciclo de vida está cubierto por smoke Android). El playback del audio queda diferido (no-goal). Los smoke tests Android fueron intermitentes por emuladores compartidos con otras sesiones paralelas.
- Next best step: validación independiente de `voice-attachment-storage` con `$feature-validator`; tras el `accept`, crear spec de `semantic-linking` con `$feature-spec`.

### Session 036

- Date: 2026-10-13

- Goal: validación independiente de `voice-attachment-storage` (spec `docs/specs/voice-attachment-storage.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `voice-attachment-storage` promovida a `accepted` en `feature_list.json` con evidencia de validación; commit y PR draft contra `develop` creados por el orquestador.
- Verification run:
  - `./init.sh` → exit 0, gate real sin procesos de larga duración.
  - `:core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL (55s); `AttachmentRepositoryAndroidTest` 1/1 e `AttachmentRepositoryIosTest` 1/1 con el round-trip `AUDIO` + resto de suites 0 failures en ambas plataformas (conteos de `core/build/test-results/*/TEST-*.xml`).
  - `:composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL (56 tasks); confirmado que la ruta Android `MediaRecorder` y la iOS `AVAudioFile` compilan frescas.
  - Esquema: `Schema.version = 4`; `.sqm` = `1/2/3.sqm` (sin nuevos); `git diff -- Attachment.sq` vacío; `git diff` vacío para `iosApp/`, `AndroidManifest.xml`, `Info.plist`, `composeApp/build.gradle.kts`, `gradle/libs.versions.toml` e `init.sh`.
  - Permisos/arquitectura: manifest sólo `CAMERA`+`RECORD_AUDIO` e `Info.plist` sin cambios; grep sin `ViewModel`/`androidx.lifecycle`/Flow/`androidx.navigation`/playback (`MediaPlayer`/`AVAudioPlayer`).
  - Control negativo propio del validador (cambiar `assertEquals(AUDIO, reread.kind)`→`IMAGE` en `AttachmentRepositoryCheck.kt`) hace fallar `AttachmentRepositoryAndroidTest` con `AssertionError`; restaurado byte-idéntico y re-verificado en verde.
  - Corroboración independiente de evidencia: OCR re-ejecutado sobre `ios-voice-att-02-list-seeded.png` (chips `Audio`, `Imagen`, `Imagen`+`Audio`, y sin chips) y lectura de `ios-db/playbook.db` (`user_version=4`, tablas `note`/`attachment`/`note_tag`/`embedding`); consistencia de root de archivos (`filesDir`/`Application Support`) con `AttachmentFileStore.delete`.
- Evidence captured: línea de validación (accept) en el arreglo `evidence` de `voice-attachment-storage` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: findings Low no bloqueantes — redacción de `AGENTS.md` (resuelta al persistir `accepted`), comentario obsoleto en `Attachment.sq` (correcto por contrato: la spec exige no tocarlo), escritura AAC en iOS sin voz real y `catch (_: Throwable)` amplio en `App.onFinalResult` (consistente con el patrón existente). Sin hallazgos de seguridad.
- Next best step: crear spec de `semantic-linking` con `$feature-spec` e implementarla con `$feature-implementer`.

### Session 037

- Date: 2026-10-13

- Goal: implementar `rag-query` (spec `docs/specs/rag-query.md`): consulta en lenguaje natural sobre las Notas (retrieval por similitud coseno + `RagQueryService` + primer wiring del runtime de IA a la app).
- Completed:
  - `:core`: `cosineSimilarity(a, b)` (Kotlin common, `kotlin.math.sqrt`; `require` de igual tamaño y `0f` para denominador nulo) y `RagQueryService(aiClient, noteRepository, embeddingRepository, maxSources = 5)` con `RagAnswer` (`Answer`/`NoIndexedNotes`/`Failed`) y `RetrievedNote`. `ask(question)`: `require(isNotBlank)`, sin embeddings → `NoIndexedNotes` **sin** runtime, embebe la pregunta (try/catch), valida 1 vector no vacío, puntúa coseno descartando dimensiones incompatibles, ordena desc con desempate por `note.id`, top-K, y genera con `GenerateRequest(prompt, context = cuerpos)`.
  - Tests compartidos `commonTest` `RagQueryCheck.kt` (helpers + stubs recording/failing/stub/fixed) con `@Test` por plataforma (`RagQueryAndroidTest`, `RagQueryIosTest`, `runTest`): similitud, retrieval, no-indexed, fallo, respuesta inválida y ranking.
  - `:composeApp`: `AskNotesScreen` + `AskUiState`; acción "Preguntar" en `NotesListScreen`; `NotesDestination.Ask`, `askState` y `rememberCoroutineScope()` en `App` (sin `ViewModel`/Flow); indexado **best-effort** al guardar/editar (`noteIndexingService.index` en `scope.launch`, silencioso, sin tocar `NoteStatus`, sólo con `body` no vacío).
  - Composition roots `MainActivity`/`MainViewController`: `SqlDelightEmbeddingRepository` + `FakeAiClient` + `NoteIndexingService` + `RagQueryService` pasados a `App(...)` (primer wiring de IA a la app).
  - Docs: `ARCHITECTURE.md` (nueva sección RAG, wiring, surfaces; "Deferred" corregido), `docs/technical-discovery.md` (nueva "RAG Decisions"), `docs/risks-and-open-questions.md` (respuesta desde las Notas; runtime real/calidad siguen abiertos), `AGENTS.md` (próxima feature).
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL in 2m 3s (66 tareas); Android/JVM 22/22 (RagQuery 6/6) y iOS 22/22 (RagQuery 6/6), 0 failures.
  - `xcodebuild` scheme `iosApp` (iPhone 15, iOS 17.2) → BUILD SUCCEEDED.
  - `PlaybookDatabase.Schema.version == 4`; sin `.sqm` nuevos ni cambios en `Note.sq`/`embedding.sq`; sin cambios en `init.sh`/`libs.versions.toml`/`composeApp/build.gradle.kts`.
  - `./init.sh` → exit 0, sin procesos de larga duración.
  - Controles negativos reproducidos y restaurados: `dot -=` rompe `cosineSimilarityContract` (+`retrievesAnswerAnchoredInNotes`); `ask` generando sin fuentes rompe `noIndexedNotesSkipsRuntime`.
  - Smoke iOS (iPhone 15): lista con "Preguntar"; pantalla de consulta; respuesta real `[fake] ¿cómo diseño al jefe final? (context=1)` mostrada sólo como texto (OCR de capturas). Smoke Android interactivo **no** ejecutado: `emulator-5554`/`emulator-5556` contaminados por worktrees paralelos que reinstalan su APK (md5 distinto) y una instancia adicional no arrancó por contención; no se fabricó evidencia Android.
- Evidence captured: arreglo `verification`/`evidence` de `rag-query` en `feature_list.json` (9/8 líneas); capturas OCR en `composeApp/build/smoke-evidence/ios-rag-0{1,2,3}-*.png`; la siembra de QA se eliminó del simulador.
- Files or artifacts updated: `core/src/commonMain/kotlin/com/playbook/core/ai/{CosineSimilarity,RagQueryService}.kt` (nuevos), `core/src/commonTest/kotlin/com/playbook/core/ai/RagQueryCheck.kt` (nuevo), `core/src/{androidUnitTest,iosTest}/kotlin/com/playbook/core/ai/RagQuery{Android,Ios}Test.kt` (nuevos), `composeApp/src/commonMain/kotlin/com/playbook/app/{AskNotesScreen,App,NotesListScreen}.kt`, `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt`, `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: la "respuesta generada" es la de `FakeAiClient` (el adaptador cloud/on-device real es follow-up del ADR 0001), así que la verificación prueba el **pipeline** (retrieval real + petición anclada), no la calidad. El indexado best-effort no transiciona `NoteStatus` ni reintenta (deuda de `note-enrichment-status-ui`/`offline-pending-retry`). Top-K sin umbral (calibración pendiente de `semantic-linking`). Smoke Android bloqueado por emuladores compartidos (limitación de entorno, declarada honestamente).
- Next best step: validación independiente de `rag-query` con `$feature-validator`; tras el `accept`, crear spec de `rag-answer-citations` o `semantic-linking` con `$feature-spec`.

### Session 038

- Date: 2026-10-13

- Goal: validación independiente de `rag-query` (spec `docs/specs/rag-query.md`).
- Completed: validación por agente validador independiente; veredicto `accept`; `rag-query` promovida a `accepted` en `feature_list.json` con evidencia de validación; commit `feature: complete rag-query` creado por el orquestador y PR draft contra `develop`.
- Verification run:
  - `./init.sh` → exit 0, sin dev servers ni simuladores; `init.sh` sin cambios.
  - `:core:testDebugUnitTest --rerun-tasks` y `:core:iosSimulatorArm64Test --rerun-tasks` → RagQuery 6/6 + previos, 22/22 por plataforma, 0 failures; `assembleDebug` + link del framework iOS `--rerun-tasks` BUILD SUCCESSFUL; `xcodebuild` scheme iosApp (iPhone 15, iOS 17.2) → BUILD SUCCEEDED.
  - Esquema `Schema.version == 4`; sólo `1.sqm`/`2.sqm`/`3.sqm`; diffs de `Note.sq`/`embedding.sq`/`init.sh`/`libs.versions.toml`/`composeApp/build.gradle.kts` vacíos; sin dependencias nuevas ni hallazgos de seguridad.
  - Controles negativos propios del validador, restaurados byte-idénticos (sha256 antes/después): invertir el signo del `dot product` rompe `cosineSimilarityContract` + `rankingIsDeterministicAndTopK`; forzar `generate` en la rama sin embeddings rompe `noIndexedNotesSkipsRuntime`.
  - Smoke Android independiente (Scenario 7/8): el validador creó una Nota de texto por UI → fila `embedding` persistida (`dimension=8`, 32 bytes) con `note.status` en `capturada`; consultó por UI → respuesta `[fake] … (context=1)` mostrada sólo como texto; datos del emulador limpiados con `pm clear`. OCR independiente de las capturas iOS reproduce lista/consulta/respuesta.
  - Scenario iOS/y Android 7–8 cerrados; Scenarios 1–6 y 9 por tests del core en dos plataformas.
- Evidence captured: línea de validación en el arreglo `evidence` de `rag-query` en `feature_list.json`; nombre del test del control negativo corregido (`rankingIsDeterministicAndTopK`).
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md` (por el orquestador al persistir la aceptación).
- Known risk or unresolved issue: la respuesta es la de `FakeAiClient` (el adaptador cloud/on-device real y la calidad son follow-up del ADR 0001); indexado best-effort sin `NoteStatus`/reintento (`note-enrichment-status-ui`/`offline-pending-retry`); top-K sin umbral (`semantic-linking`). Findings Low no bloqueantes: dumpos Android obsoletos en el directorio ignorado `composeApp/build/smoke-evidence/`.
- Next best step: crear spec de `rag-answer-citations` (depende de `rag-query`, ya `accepted`) con `$feature-spec` e implementarla con `$feature-implementer`.

### Session 039

- Date: 2026-10-13

- Goal: implementar `rag-answer-citations` (spec `docs/specs/rag-answer-citations.md`): la respuesta RAG lista sus Notas fuente como citas numeradas y permite abrirlas. Sólo UI.
- Completed:
  - `AskNotesScreen.kt`: `AskUiState.Answered(text, sources: List<RetrievedNote>)`; nuevo parámetro `onOpenSource: (String) -> Unit`; sección **"Fuentes"** dentro de `AskResult` (una cita por `source`: `"Fuente ${i+1}"`, body a 2 líneas con ellipsis o `"Sin texto"`, `LabelChip` con `track?.code ?: "Sin track"`), toda la tarjeta `clickable(onClickLabel = "Abrir nota fuente", role = Role.Button)`; sin fuentes → sin sección; `AskResult` con `verticalScroll` para que respuesta + hasta 5 citas sean alcanzables.
  - `App.kt`: `is RagAnswer.Answer -> AskUiState.Answered(result.text, result.sources)`; `NotesDestination.Ask` cablea `onOpenSource = { noteId -> destination = NotesDestination.Edit(noteId) }` (reusa el editor; si la Nota se borró, el guard existente vuelve a la lista).
  - Docs: `ARCHITECTURE.md` y `docs/technical-discovery.md` dejan de afirmar que la UI no renderiza `sources`; describen "Fuentes", la navegación y la cita efímera. Spec corregida (v4 → v5 por `semantic-linking`) + "Implementation Findings".
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL in 3m 15s (66 tareas); 33/33 Android/JVM y 33/33 iOS, 0 failures (RagQuery 6/6 en ambas, orden de `sources` intacto).
  - `xcodebuild ... -sdk iphonesimulator ... build` → `** BUILD SUCCEEDED **`; `./init.sh` → exit 0, sin procesos de larga duración; `init.sh` sin cambios.
  - Esquema v5 (`Schema.version == 5`, `1.sqm`..`4.sqm`, sin `.sqm` nuevo); diffs de `core/**`/`libs.versions.toml`/`composeApp/build.gradle.kts`/`init.sh`/`iosApp/**` vacíos; sin dependencias nuevas.
  - Smoke Android (emulator-5556, APK md5 `1358c4be8d3c333b7195d7c37771db11` = build local): 3 Notas sembradas con embeddings 8-dim; consulta ASCII → `[fake] como diseno al jefe final (context=3)` + "Fuentes" con `Fuente 1` (Sin texto/Sin track), `Fuente 2` (Diseño del jefe final…/mecánicas), `Fuente 3` (La historia…/historia), orden coincidente con el ranking recomputado offline (0.855/0.804/0.773); tocar `Fuente 2` abrió `Editar nota` con body y `✓ mecánicas` de la Nota correcta. Scenario 5: 0 embeddings → "Todavía no hay notas indexadas" y sin "Fuentes".
  - Control negativo reproducido y restaurado: `App.ask()` a `Answered(result.text, emptyList())` deja la respuesta pero sin "Fuentes"; `App.kt` restaurado y recompilado.
  - Smoke iOS (iPhone 15 iOS 17.2): install/launch + OCR de la lista sembrada (acciones y chips); la navegación/tap **no** es automatizable (simuladores *headless*, sin `idb`/`cliclick`), declarado honestamente; `commonMain` ejercido vía Android.
- Evidence captured: arreglo `verification` (8)/`evidence` (9) de `rag-answer-citations` en `feature_list.json`; UI dumps y capturas en `composeApp/build/smoke-evidence/rag-answer-citations/` (`android-01-answer.png`, `android-answer.xml`, `android-02-open-source.*`, `android-03-no-indexed.*`, `android-04-negative-control.xml`, `ios-01-list-empty.png`, `ios-02-list-seeded.png`, `ocr.swift`).
- Files or artifacts updated: `composeApp/src/commonMain/kotlin/com/playbook/app/AskNotesScreen.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/specs/rag-answer-citations.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el score crudo no se muestra (no calibrado con `FakeAiClient`); abrir una cita y "Cancelar" vuelve a la lista y re-entrar a "Preguntar" resetea a `Idle` (no hay retorno a la respuesta ni `previousDestination`); Scenario 4 (fuente borrada entre respuesta y toque) no se pudo reproducir por UI (re-entrar a `Ask` resetea `askState`), pero el callback es un `Edit(noteId)` directo y el guard de Nota inexistente es preexistente; smoke iOS interactivo no automatizable (limitación de entorno). Sin hallazgos de seguridad.
- Next best step: validación independiente de `rag-answer-citations` con `$feature-validator`; tras el `accept`, crear spec de `note-links-ui` (depende de `semantic-linking`, ya `accepted`) con `$feature-spec`.
- Validation: validación independiente (`docs/validations/rag-answer-citations.md`) → **accept** (2026-10-14). El validador re-ejecutó `CI=true ./init.sh` (exit 0) y `--rerun-tasks` (66 tasks, 33/33 en cada plataforma, RagQuery 6/6), confirmó esquema v5 sin `.sqm` y diffs vacíos en paths protegidos, y reprodujo la feature con smoke propio en emulator-5554 (md5 `1358c4be8d3c333b7195d7c37771db11`) con "Fuentes" 1..3 en orden idéntico al ranking recalculado + control negativo propio (0 embeddings → sin "Fuentes"). Hallazgos Low no bloqueantes: "esquema v4" desactualizado en `docs/technical-discovery.md` (preexistente) y la spec editada por "Implementation Findings". Feature promovida a `accepted`; commit `feature: complete rag-answer-citations` + PR draft (orquestador).

### Session 040

- Date: 2026-10-14
- Goal: implementar (`$feature-implementer`) la feature `note-links-ui` (spec `docs/specs/note-links-ui.md`), retomando cambios sin verificar de una corrida previa interrumpida.
- Completed: revisada y completada la implementación — `RelatedNote`/`buildRelatedNotes` (proyección pura en `:core`), `RelatedNotesTest`, `RelatedNotesScreen` (estado vacío + tarjetas con `score` textual), `NotesDestination.Related(noteId)` + recomputo best-effort al abrir en `App.kt`, `onOpenRelated`/"Ver relacionadas" en `NoteEditorScreen` (en **fila propia**, ver desvío), wiring de `SqlDelightNoteLinkRepository`/`SemanticLinkingService` en `MainActivity`/`MainViewController`. Docs durables actualizados (`ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `AGENTS.md` línea de próxima feature, spec tasks); `feature_list.json` `in_progress` → `passing` con evidencia.
- Verification run:
  - `:core:testDebugUnitTest --rerun-tasks` → `RelatedNotesTest` 7/7 + previos, 40 tests / 0 failures; `:core:iosSimulatorArm64Test --rerun-tasks` → ídem 40/0.
  - `:composeApp:assembleDebug` BUILD SUCCESSFUL; `:composeApp:linkDebugFrameworkIosSimulatorArm64` recompiló Kotlin/native del framework; `xcodebuild` scheme iosApp → ** BUILD SUCCEEDED **.
  - `CI=true ./init.sh` → exit 0, sin dev servers ni simuladores; `init.sh` sin cambios.
  - Esquema `Schema.version == 5`; `1.sqm`–`4.sqm` (sin `.sqm` nuevo); sin cambios en `libs.versions.toml`/`build.gradle.kts` ni dependencias nuevas.
  - Control negativo (reproducido y restaurado): orden `score` asc rompe `RelatedNotesTest.ordersByScoreDescending`.
  - Smoke real Android (emulador-5556, `adb shell input` + `uiautomator`): estado vacío (Scenario 3); dos Notas con el mismo cuerpo → `Similitud 1.00` (Scenarios 2/4); abrir relacionada y `Volver` (Scenario 5). Smoke iOS: app instalada/lanzada en simulador sin crash; navegación interactiva iOS no automatizable (sin `idb`/`cliclick`/OCR), declarada honestamente.
- Evidence captured: 10 líneas en el arreglo `evidence` de `note-links-ui` en `feature_list.json`; capturas en `build/smoke/relnotes-*.png` y script `build/smoke/relnotes_smoke.py` (ambos en `build/`, ignorado por git).
- Files or artifacts updated: `core/src/commonMain/.../linking/RelatedNotes.kt`, `core/src/commonTest/.../linking/RelatedNotesTest.kt`, `composeApp/src/commonMain/.../app/RelatedNotesScreen.kt`, `App.kt`, `NoteEditorScreen.kt`, `composeApp/src/androidMain/.../MainActivity.kt`, `composeApp/src/iosMain/.../MainViewController.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `AGENTS.md`, `docs/specs/note-links-ui.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el trigger de ciclo de vida del recomputo (al indexar/editar, offline) y el recomputo incremental/background siguen pendientes; `recomputeAll()` es síncrono O(n²) sobre los embeddings y corre en el dispatcher principal al abrir la pantalla. Desvío detectado por el smoke: el botón "Ver relacionadas" en la fila de acciones del editor desbordaba 1080px y ocultaba "Guardar"; movido a fila propia. Smoke iOS interactivo no reproducible en este entorno. Pendiente validación independiente.
- Next best step: validación independiente de `note-links-ui` con `$feature-validator` (spec `docs/specs/note-links-ui.md`); tras el `accept`, crear spec de `rag-answer-citations` (depende de `rag-query`, ya `accepted`).
- Validation: validación independiente (`docs/validations/note-links-ui.md`) → **accept** (2026-10-14). El validador re-ejecutó `CI=true ./init.sh` (exit 0) y `--rerun-tasks` (40/0 en cada plataforma; `RelatedNotesTest` 7/7), confirmó `Schema.version == 5` sin `.sqm` nuevo y `git status` sólo con archivos de la feature, verificó que el recomputo `recomputeAll()` best-effort al abrir está dentro del alcance de la spec, y corrió control negativo propio (orden `score` asc → falla `ordersByScoreDescending`, restaurado byte-idéntico). Hallazgos Low no bloqueantes: posible frame transitorio de `relatedNotes` al cambiar de Nota origen; smoke iOS limitado al arranque. Feature promovida a `accepted`; commit `feature: complete note-links-ui` + PR draft (orquestador).

### Session 041

- Date: 2026-10-14
- Goal: implementar (`$feature-implementer`) la feature `note-enrichment-status-ui` (spec `docs/specs/note-enrichment-status-ui.md`): estado de enriquecimiento visible + re-indexado manual.
- Completed: `EnrichmentStatus.kt` (`statusLabel`/`StatusBadge`); `App` usa `NoteIndexingOrchestrator` (el param reemplaza `NoteIndexingService`), `indexBestEffort` con `reload()` y `reindex(note)`; `NotesListScreen` usa `StatusBadge`; `NoteEditorScreen` con `initialStatus`/`onReindex` + badge read-only + botón "Re-indexar"; composition roots Android/iOS construyen y pasan el orquestador; regresión de re-indexado en `NoteIndexingOrchestratorCheck`. Docs durables (`ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`, `AGENTS.md` línea de próxima feature) y tasks de la spec marcadas; `feature_list.json` `in_progress` → `passing` con `verification`/`evidence`/`notes` re-derivados.
- Verification run:
  - `:core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; 40/0 en cada plataforma (incluye la regresión nueva).
  - `:composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL; `xcodebuild` scheme iosApp → `** BUILD SUCCEEDED **`.
  - `CI=true ./init.sh` → exit 0, sin dev servers ni simuladores; `init.sh` sin cambios.
  - Esquema `Schema.version == 5`; `1.sqm`–`4.sqm` (sin `.sqm` nuevo); sin dependencias nuevas.
  - Smoke Android (emulador dedicado, `adb shell input` + `uiautomator` + sembrado de DB por `run-as`): Scenarios 1–5 verdes (indexada + fila `embedding`; pendiente + "Re-indexar" habilitado; re-indexado → indexada preservando `track`/`tags`/`body` en DB; sin texto → capturada + botón deshabilitado; indexada → botón deshabilitado).
  - Smoke iOS: install/launch + lista renderizada; badges/"Re-indexar" por OCR e interacción no automatizables (sin `idb`/`cliclick`/OCR y container del simulador denegado), declarado honestamente.
  - Controles negativos reproducidos y restaurados: (a) `indexBestEffort` sin orquestador → lista "capturada"; (b) re-indexado que pisa `track` → falla `NoteIndexingOrchestratorAndroidTest`.
- Evidence captured: 9 líneas en el arreglo `evidence` de `note-enrichment-status-ui` en `feature_list.json`; capturas `build/smoke/enrich-{1..6}-*.png` y `build/smoke/enrich-ios-1-list.png` + script `build/smoke/enrich_smoke.py` (en `build/`, ignorado por git).
- Files or artifacts updated: `composeApp/src/commonMain/.../app/EnrichmentStatus.kt` (nuevo), `App.kt`, `NotesListScreen.kt`, `NoteEditorScreen.kt`, `composeApp/src/androidMain/.../MainActivity.kt`, `composeApp/src/iosMain/.../MainViewController.kt`, `core/src/commonTest/.../ai/NoteIndexingOrchestratorCheck.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`, `AGENTS.md`, `docs/specs/note-enrichment-status-ui.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el botón "Re-indexar" queda deshabilitado (no oculto) en Notas `INDEXED`/sin texto (la spec lo permite); no hay estado visual de "re-indexando" (taps repetidos son idempotentes por `upsert`); `reindex` usa la Nota persistida (no el borrador sin guardar); los emuladores compartidos con sesiones paralelas obligaron a un emulador dedicado; smoke iOS interactivo/OCR no reproducible (limitación de entorno). Pendiente validación independiente.
- Next best step: validación independiente de `note-enrichment-status-ui` con `$feature-validator` (spec `docs/specs/note-enrichment-status-ui.md`).
- Validation: validación independiente (`docs/validations/note-enrichment-status-ui.md`) → **accept** (2026-10-14). El validador re-ejecutó `CI=true ./init.sh` (exit 0), `:core` tests `--rerun-tasks` (40/0 por plataforma, `NoteIndexingOrchestrator{Android,Ios}Test` 1/1) y `:composeApp:assembleDebug` + link iOS (57 tareas); confirmó `Schema.version == 5` sin `.sqm` y `git diff` vacío en paths protegidos; verificó por código `App` con `NoteIndexingOrchestrator`+`reload()`, que `reindex` no pisa `track`/`tags`/`body`, `StatusBadge` textual y la reconciliación de `note-links-ui` intacta. Control negativo propio (orquestador pisando `body` → falla `NoteIndexingOrchestratorAndroidTest`, restaurado byte-idéntico). Hallazgos Low no bloqueantes. Feature promovida a `accepted`; commit `feature: complete note-enrichment-status-ui` + PR draft (orquestador).

### Session 042

- Date: 2026-10-14
- Goal: implementar (`$feature-implementer`) la feature `enrichment-observability-logging` (spec `docs/specs/enrichment-observability-logging.md`): observabilidad mínima del MVP (puerto `Diagnostics` + sink local + instrumentación de fallos).
- Completed:
  - `:core` gana `com.playbook.core.diagnostics`: `DiagnosticSource`/`DiagnosticEntry`/`Diagnostics` (best-effort), `NoOpDiagnostics` (default), `InMemoryDiagnostics` (cap + `snapshot`/`clear`), `DiagnosticStorage` + `expect diagnosticStorage(path)` con `actual` Android/JVM (`java.io.File`) e iOS (`NSFileManager`/`NSString`), y `FileDiagnostics` (línea `<ts>\t<source>\t<summary>\t<detail|->`, retención por cantidad `maxEntries`, `detail` ~200 chars, todo en try/catch, `rawLog()`).
  - `NoteIndexingService` y `RagQueryService` aceptan `diagnostics: Diagnostics = NoOpDiagnostics` (retrocompatible) y registran `AI_INDEXING`/`AI_QUERY` en cada `Failed` (excepción o respuesta inválida); `NoIndexedNotes` no registra.
  - `App.kt` recibe `diagnostics` y registra `VOICE`/`IMAGE` (`onError` nativos con el nombre del error) y `STORAGE` (los dos `catch (Throwable)` de adjuntos, sin `detail`); `MainActivity`/`MainViewController` construyen `FileDiagnostics(diagnosticStorage("<privado>/diagnostics.log"))` y lo cablean a servicios y `App`.
  - Tests: `DiagnosticsCheck.kt` (escenarios 1–7) + `DiagnosticsAndroidTest`/`DiagnosticsIosTest` + `DiagnosticsStorageAndroidTest`/`DiagnosticsStorageIosTest` (ejercitan el `actual` real por plataforma).
  - Docs durables: `ARCHITECTURE.md` (sección Observability + runtime surfaces + Deferred), `docs/technical-discovery.md` (Observability + "Observability Decisions"), `CONSTRAINTS.md` (MUST/MUST NOT de diagnóstico), `AGENTS.md` (sólo la línea de próxima feature); spec tasks marcadas; `feature_list.json` `in_progress` → `passing`.
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; 42/42 en Android/JVM y 42/42 en iOS, 0 failures. Diagnóstico 2/2 por plataforma; tests previos de IA intactos con `NoOpDiagnostics` (AiClient 3, NoteIndexing 1, Orchestrator 1, RagQuery 6).
  - `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL (57 tasks).
  - `CI=true ./init.sh` → exit 0, sin dev servers ni simuladores; `init.sh` sin cambios.
  - `PlaybookDatabaseImpl.Schema.version = 5`; `1.sqm`–`4.sqm` (sin `.sqm` nuevo); `git diff` vacío en `init.sh`/`libs.versions.toml`/`iosApp`/`sqldelight`/`AiClient.kt`/`FakeAiClient.kt`.
  - Control negativo (a) reproducido y restaurado: sin `recordFailure(AI_INDEXING, ...)` en el catch de `embed`, `DiagnosticsAndroidTest` falla (Scenario 1). Control negativo (b): sin `enforceRetention()` en `FileDiagnostics`, `DiagnosticsAndroidTest` falla (Scenario 6).
  - Smoke Android real (emulator-5554, `adb shell input` + `uiautomator`): build instalado, `pm clear` + launch, 'Foto' → 'Cámara' con permiso denegado → `files/diagnostics.log` contiene `1791991973203	IMAGE	image_failed	PERMISSION_DENIED` (sin `body`/pregunta/rutas/IDs/secrets). Datos QA limpiados.
- Evidence captured: 9 líneas en el arreglo `evidence` de `enrichment-observability-logging` en `feature_list.json`; smoke crudo del archivo transcrito en la evidencia. Sin capturas persistentes.
- Files or artifacts updated: `core/src/commonMain/.../diagnostics/{Diagnostics.kt,FileDiagnostics.kt}`, `core/src/androidMain/.../diagnostics/Diagnostics.android.kt`, `core/src/iosMain/.../diagnostics/Diagnostics.ios.kt`, `core/src/commonMain/.../ai/{NoteIndexingService.kt,RagQueryService.kt}`, `core/src/commonTest/.../diagnostics/DiagnosticsCheck.kt`, `core/src/androidUnitTest/.../diagnostics/{DiagnosticsAndroidTest.kt,DiagnosticsStorageAndroidTest.kt}`, `core/src/iosTest/.../diagnostics/{DiagnosticsIosTest.kt,DiagnosticsStorageIosTest.kt}`, `composeApp/src/commonMain/.../app/App.kt`, `composeApp/src/androidMain/.../MainActivity.kt`, `composeApp/src/iosMain/.../MainViewController.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`, `AGENTS.md`, `docs/specs/enrichment-observability-logging.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el log no es thread-safe (el registro es best-effort; declarado en la KDoc) y no hay UI/export del log ni telemetría (fuera de alcance del MVP). Smoke iOS interactivo no ejecutado por falta de input automation (`idb`/`cliclick`); el `actual` iOS se ejercita en runtime por `DiagnosticsStorageIosTest` y los escenarios 1–7 corren en iOS. `FileDiagnostics` no tiene rotación por tamaño ni timestamp legible (sólo epoch millis), por diseño del MVP. Pendiente validación independiente.
- Next best step: validación independiente de `enrichment-observability-logging` con `$feature-validator` (spec `docs/specs/enrichment-observability-logging.md`); tras el `accept`, elegir la siguiente feature en cola (`note-enrichment-status-ui` u `offline-pending-retry`).
- Validation: validación independiente (`docs/validations/enrichment-observability-logging.md`) → **accept** (2026-10-14). El validador re-ejecutó `CI=true ./init.sh` (exit 0) y `:core` tests `--rerun-tasks` (42/42 por plataforma, `Diagnostics*Test` 1/1), `:composeApp:assembleDebug`/link iOS; confirmó `Schema.version == 5` sin `.sqm` ni deps; corrió controles negativos propios (sin `recordFailure(AI_INDEXING)` → falla Scenario 1; sin `enforceRetention()` → falla Scenario 6), restaurados byte-idénticos. Hallazgos Low no bloqueantes (typo cosmético de `ARCHITECTURE.md` corregido; VOICE/STORAGE por inspección; vigilar `detail`=mensaje de excepción en el adaptador cloud). Feature promovida a `accepted`; commit `feature: complete enrichment-observability-logging` + PR draft (orquestador).

### Session 043

- Date: 2026-10-14
- Goal: validación independiente de `ios-release-pipeline-scaffolding` (spec `docs/specs/ios-release-pipeline.md`, Scenarios 1/2/7/8 repo-side).
- Completed: validación por agente validador independiente; veredicto `accept`; feature promovida a `accepted` con evidencia de validación. El validador re-ejecutó `CI=true ./init.sh` (exit 0, sin archivar/subir), `preflight.sh` sin credenciales → exit 1 accionable, fail-fast de `archive.sh`/`export-upload.sh` sin `TEAM_ID`/keys/`.p8`, `archive.sh --unsigned` → `** ARCHIVE SUCCEEDED **` + `.xcarchive` (bundle `com.playbook.app`, 1.0/1, arm64 con `ComposeApp`), `-showBuildSettings` (Release `Apple Distribution` + overrides aplicados) y Scenario 7 (sin artefactos de firma ni secrets versionados).
- Verification run: `CI=true ./init.sh` exit 0; smokes repo-side sin cuenta; Scenarios 3–6 account-gated (no fabricados). Registro en `docs/validations/ios-release-pipeline-scaffolding.md`.
- Evidence captured: línea de validación en el arreglo `evidence` de `ios-release-pipeline-scaffolding` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `AGENTS.md`, `docs/technical-discovery.md`, `docs/validations/ios-release-pipeline-scaffolding.md`.
- Known risk or unresolved issue: hallazgos Low no bloqueantes — `docs/technical-discovery.md` decía "blocked (no passing)" (corregido al persistir) e inconsistencia menor `preflight` (`iTMSTransporter`) vs `export-upload` (`altool`, fallback declarado en el runbook). Scenarios 3–6 siguen account-gated en `ios-release-pipeline-first-upload` (`blocked`).
- Next best step: continuar con `offline-pending-retry` (depende de `ai-enrichment-classify-tags-type`, ya `accepted`; última por el esquema v6).

### Session 044

- Date: 2026-10-15
- Goal: implementar `offline-pending-retry` (spec `docs/specs/offline-pending-retry.md`): reintento de indexado de las Notas `PENDING` y promoción a `FAILED` al agotar el presupuesto, con disponibilidad de IA inyectable. Esquema v6.
- Completed:
  - Esquema **v6**: `note.index_attempts INTEGER NOT NULL DEFAULT 0` en `Note.sq`; nuevo `5.sqm` (`ALTER TABLE note ADD COLUMN index_attempts ...`); queries `selectNotesByStatus`/`updateNoteIndexAttempts` (sólo queries; `insertNote`/`updateNote`/`updateNoteStatus` sin cambios). `Note.indexAttempts: Int = 0` + `NoteMappers` (`index_attempts.toInt()`).
  - Repositorio: `NoteRepository.getByStatus(status)` (sólo lectura, orden `updated_at DESC, id DESC`, etiquetas agrupadas como `getAll`) y `setIndexAttempts(id, attempts)` (sólo el contador; sin re-sellar `updated_at`), implementados en `SqlDelightNoteRepository` con `rowsAffected > 0`.
  - `:core`: puerto `AiAvailability` + `AlwaysAvailableAiAvailability`; `PendingIndexingRetry` (`retryPending(): RetrySummary`, `suspend`) con `budget` default `DEFAULT_RETRY_BUDGET = 3` (`require(budget > 0)`): `!isAvailable()` → no-op; si disponible, por cada `PENDING` corre `NoteIndexingOrchestrator.index`; éxito → `setIndexAttempts(id, 0)`; fallo → `index_attempts + 1` y `FAILED` al alcanzar `budget`. Reutiliza `NoteIndexingService`/`NoteIndexingOrchestrator`/`AiClient` **sin modificarlos**.
  - Tests: `PendingIndexingRetryCheck.kt` (escenarios 1–5) + runners `PendingIndexingRetryAndroidTest`/`PendingIndexingRetryIosTest`; `verifyNoteRetryQueries` en `NoteRepositoryCheck` (+ runner); migraciones a v6 (`verifyMigrationV1ToV6`, nuevo `verifyMigrationV5ToV6`, `DatabaseMigrationCheck` con comparación de columnas de `note`); `NoteTagCheck.verifyNoteTagMigration` y runners renombrados a v6.
  - Wiring: `PendingIndexingRetry` en los composition roots (`MainActivity`/`MainViewController`); `App(...)` recibe `pendingIndexingRetry` y corre `LaunchedEffect(Unit) { pendingIndexingRetry.retryPending(); reload() }` al arrancar.
  - Docs durables: `ARCHITECTURE.md` (esquema v6, migraciones, sección AI con reintento/`AiAvailability`/wiring, Runtime surfaces), `docs/technical-discovery.md` ("Retry Decisions (offline-pending-retry)"), `CONSTRAINTS.md` (`FAILED` sólo lo escribe el reintento tras agotar `budget`; `AiAvailability=false` no-op), `AGENTS.md` (línea de próxima feature).
- Verification run:
  - `CI=true ./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL (10s, 16 tasks); 45/45 Android/JVM, 0 failures: PendingIndexingRetryAndroidTest 1/1 (nuevo), NoteRepositoryAndroidTest 4/4 (retryQueriesRoundTrip + migrationV1ToV6PreservesNotes), MigrationAndroidTest 2/2, DatabaseMigrationAndroidTest 1/1, NoteIndexingOrchestratorAndroidTest 1/1 + resto (conteos de `core/build/test-results/testDebugUnitTest/`).
  - `CI=true ./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL (16s); 45/45 iOS, 0 failures con los equivalentes `IosTest`.
  - `CI=true ./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (57 tasks).
  - `PlaybookDatabaseImpl.Schema.version` generado = 6; `find core/src -name '*.sqm'` = `1.sqm`..`5.sqm` (sin extra).
  - `CI=true ./init.sh` → exit 0; sin dev servers/simuladores/red; `git diff -- init.sh` vacío.
  - Controles negativos reproducidos, con el fallo esperado, restaurados byte-idénticos (SHA-256 coincide) y re-verificados en verde: (a) `next >= budget` → `next > budget` falla el Scenario 3 (`expected:<0> but was:<1>`); (b) `AlwaysAvailableAiAvailability` → `false` falla el Scenario 1 (`Expected value to be true.`); (c) columna renombrada en `5.sqm` falla la migración con `no such column: note.index_attempts`.
  - Alcance: `git diff` vacío en `AiClient.kt`/`NoteIndexingService.kt`/`NoteIndexingOrchestrator.kt`/`gradle/libs.versions.toml`/`composeApp/build.gradle.kts`/`iosApp/**`/`init.sh`; `GddScreen.kt`/`GddMarkdown`/`GddSharer` sin tocar; sin deps nuevas ni secrets.
- Evidence captured: arreglo `verification`/`evidence` de `offline-pending-retry` en `feature_list.json`.
- Files or artifacts updated: `core/src/commonMain/sqldelight/com/playbook/core/db/{Note.sq,5.sqm}`, `core/src/commonMain/kotlin/com/playbook/core/model/{Note,NoteMappers}.kt`, `core/src/commonMain/kotlin/com/playbook/core/repository/{NoteRepository,SqlDelightNoteRepository}.kt`, `core/src/commonMain/kotlin/com/playbook/core/ai/{AiAvailability,PendingIndexingRetry}.kt` (nuevos), `core/src/commonTest/kotlin/com/playbook/core/ai/PendingIndexingRetryCheck.kt` (nuevo), `core/src/commonTest/kotlin/com/playbook/core/{db/{MigrationCheck,DatabaseMigrationCheck},repository/{NoteRepositoryCheck,NoteTagCheck}}.kt`, runners `core/src/{androidUnitTest,iosTest}/kotlin/com/playbook/core/ai/PendingIndexingRetry*Test.kt` (nuevos) y `{db,repository}/*Test.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt`, `composeApp/src/{androidMain,iosMain}/kotlin/com/playbook/app/{MainActivity,MainViewController}.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: sin conectividad real (`AiAvailability` default `true`; la sonda es follow-up del ADR 0001) y sin scheduling/reintento continuo (el trigger es el arranque; `WorkManager`/`BGTaskScheduler` fuera de alcance). El runtime es `FakeAiClient` (la verificación prueba el pipeline, no una IA real). No hay E2E persistente (la spec lo justifica).
- Validation: validación independiente (`docs/validations/offline-pending-retry.md`) → **accept** (2026-10-15). El validador re-ejecutó `CI=true ./init.sh` (exit 0) y `:core:testDebugUnitTest`/`:core:iosSimulatorArm64Test --rerun-tasks` (45/45 en cada plataforma, incl. `PendingIndexingRetry*Test` 1/1), `:composeApp:assembleDebug` + link framework iOS (57 tasks); confirmó `Schema.version == 6` y `.sqm` = `1..5`, y los invariantes (`FAILED` sólo tras `budget`, `AiAvailability=false` no-op con `embedCalls==0`, `budget > 0`). Control negativo propio (romper el reseteo del presupuesto a `0` en el éxito) reproduce el fallo del Scenario 1 y se restaura byte-idéntico. Hallazgo Low no bloqueante: `NoteTagCheck.kt`/`docs/risks-and-open-questions.md` cambiaron como arrastres del bump a v6 (no scope creep). Feature promovida a `accepted`; commit `feature: complete offline-pending-retry`.
- Next best step: crear spec/implementar `gdd-export` (depende de `gdd-view`, ya `accepted`) con `$feature-spec`.

### Session 045

- Date: 2026-10-15
- Goal: implementar (`$feature-implementer`) la feature `gdd-export` (spec `docs/specs/gdd-export.md`): exportar/compartir el GDD como texto Markdown desde `GddScreen`.
- Completed:
  - `:core` gana `com.playbook.core.gdd.buildGddMarkdown(notes, now)` (+ helpers `title`/`bodyRest`/`metadataLine`/`escapeMarkdown`): reusa `buildGdd`, omite secciones vacías, cumple el "Format Contract" (encabezado sin fecha, total singular/plural, orden fijo, `Sin track`, título = 1ra línea, cuerpo = resto, fallback `Nota <now>`, metadatos y backslash-escape del set `` \ ` * _ { } [ ] ( ) # + - . ! | > `` en contenido de usuario). Sin fecha/SQLDelight/`AiClient`: `now` se inyecta como `String`.
  - Tests: `core/src/commonTest/.../gdd/GddMarkdownCheck.kt` (`verifyGddMarkdown()`, escenarios 1–8 + assert del documento canónico) + runners `GddMarkdownAndroidTest`/`GddMarkdownIosTest` (patrón de `GddProjection{Android,Ios}Test`).
  - `:composeApp` gana el puerto `GddSharer` (`commonMain`): `interface GddSharer { fun share(markdown): Boolean }` + `@Composable expect fun rememberGddSharer()` + `expect fun currentTimestampLabel()`; `actual` Android (`ACTION_SEND` `text/plain` + `createChooser`, `SimpleDateFormat`) e iOS (`UIActivityViewController` + `NSDateFormatter`).
  - `IosViewControllers.kt` (nuevo) extrae `internal fun topViewController()` de `ImageCapture.ios.kt` (que lo reusa, sin tocar la lógica de captura; se quitaron imports huérfanos).
  - `GddScreen.kt` envuelve el contenido en `Scaffold(snackbarHost = ...)` con `rememberCoroutineScope()` local, agrega `TextButton` "Compartir" (`enabled = notes.isNotEmpty()`) que construye el Markdown y llama `sharer.share`, y muestra snackbar "Listo para compartir"/"No disponible"; sin nuevos parámetros → **`App.kt` sin cambios**.
  - Docs durables: `ARCHITECTURE.md` (intro + sección GDD con export/puerto de share) y `docs/technical-discovery.md` (nueva sección "GDD Export Decisions (gdd-export)"); `AGENTS.md` (sólo la línea de próxima feature); tasks de la spec; `feature_list.json` `in_progress` → `passing`.
- Verification run:
  - `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; 43/43 en Android/JVM y 43/43 en iOS, 0 failures (incluye `GddMarkdown*Test` 1/1 y `GddProjection*Test` 1/1).
  - `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (66 tareas).
  - `CI=true ./init.sh` → exit 0, sin dev servers ni simuladores; `init.sh` sin cambios.
  - Esquema: `PlaybookDatabaseImpl.Schema.version = 5`; `1.sqm`–`4.sqm` (sin `.sqm` nuevo); sin dependencias nuevas. **Discrepancia con la spec registrada honestamente**: la spec asumía `== 6` por `offline-pending-retry` (paralela), que no está en este worktree.
  - Controles negativos reproducidos y restaurados byte-idénticos (sha256 `GddMarkdown.kt` `e165dae1…`): (a) sin `.filter { it.notes.isNotEmpty() }` → `ComparisonFailure` (sección vacía presente); (b) `bodyRest` con el body completo → `ComparisonFailure` (1ra línea repetida); (c) `escapeMarkdown` identidad → `AssertionError`. Verde tras restaurar.
  - Paths protegidos: `git diff --stat` vacío para `Note.sq`, `Note.kt`, `repository/**`, `App.kt`, `MainActivity.kt`, `MainViewController.kt`, `init.sh`, `iosApp/**`, `libs.versions.toml`, build files.
- Evidence captured: 9 líneas en el arreglo `evidence` de `gdd-export` en `feature_list.json`; salida de tests/build en `core/build/test-results/`.
- Files or artifacts updated: `core/src/commonMain/.../gdd/GddMarkdown.kt` (nuevo), `core/src/commonTest/.../gdd/GddMarkdownCheck.kt` (nuevo), `core/src/androidUnitTest/.../gdd/GddMarkdownAndroidTest.kt` (nuevo), `core/src/iosTest/.../gdd/GddMarkdownIosTest.kt` (nuevo), `composeApp/src/commonMain/.../app/GddSharer.kt` (nuevo), `composeApp/src/androidMain/.../app/GddSharer.android.kt` (nuevo), `composeApp/src/iosMain/.../app/GddSharer.ios.kt` (nuevo), `composeApp/src/iosMain/.../app/IosViewControllers.kt` (nuevo), `composeApp/src/iosMain/.../app/ImageCapture.ios.kt`, `composeApp/src/commonMain/.../app/GddScreen.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: smoke interactivo **no ejecutado** — no hay `adb`/emulador Android y el gesto de compartir iOS no es automatizable (`idb`/`cliclick`/`applesimutils` ausentes); declarado honestamente, sin evidencia fabricada. La lógica del documento queda cubierta por tests del core en dos plataformas y la app compila/enlaza en Android e iOS. Pendiente validación independiente.
- Next best step: validación independiente de `gdd-export` con `$feature-validator` (spec `docs/specs/gdd-export.md`); tras el `accept`, continuar con `offline-pending-retry`.

### Session 046

- Date: 2026-10-15
- Goal: validación independiente de `gdd-export` (spec `docs/specs/gdd-export.md`) y persistencia de la aceptación.
- Completed: validación por agente validador independiente; veredicto `accept`; `gdd-export` promovida a `accepted` en `feature_list.json` con evidencia; registro en `docs/validations/gdd-export.md`; commit `feature: complete gdd-export` creado por el orquestador sobre `feature/gdd-export`.
- Verification run:
  - `CI=true ./init.sh` → exit 0, sin dev servers ni simuladores; `init.sh` sin cambios.
  - `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; 43/0 en cada plataforma (incluye `GddMarkdownAndroidTest`/`GddMarkdownIosTest` 1/1 nuevos).
  - `:composeApp:assembleDebug` + link del framework iOS `--rerun-tasks` → BUILD SUCCESSFUL (57 tareas).
  - Esquema: `PlaybookDatabaseImpl.Schema.version = 5`, sin `.sqm` nuevo (`1.sqm`–`4.sqm`); sin dependencias nuevas; paths protegidos (`Note.sq`, `Note.kt`, `repository/**`, `App.kt`, `MainActivity.kt`, `MainViewController.kt`, `init.sh`, `iosApp/**`, build files) byte-idénticos (`git diff` vacío).
  - Control negativo propio del validador (total sólo en plural) hace fallar `GddMarkdownAndroidTest.buildsDeterministicMarkdown`; restaurado byte-idéntico (sha256 `e165dae1…`) y re-verificado en verde.
- Evidence captured: línea de validación en el arreglo `evidence` de `gdd-export` en `feature_list.json`; `docs/validations/gdd-export.md`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `docs/validations/gdd-export.md` (nuevo).
- Known risk or unresolved issue: hallazgos Low no bloqueantes — la cobertura del test de escapado omite `{ } +` (la implementación sí los incluye), sin smoke interactivo Android/iOS (sin `adb`/input automation, declarado honestamente) y nit de checkbox de smoke en la spec. La spec aún dice `Schema.version == 6` (asunción de `offline-pending-retry`, no integrada en este worktree); el valor real v5 quedó registrado honestamente.
- Next best step: continuar con `offline-pending-retry` (depende de `ai-enrichment-classify-tags-type`, ya `accepted`; última por el esquema v6).

### Session 047

- Date: 2026-10-15
- Goal: revisión de descubrimiento (build-brief) del MVP Android-first y re-derivación del harness/backlog (sin implementar producto).
- Completed:
  - Discovery (rama `docs/mvp-android-root-discovery`): `CONTEXT.md`, `docs/build-brief.md`, `docs/domain-model.md`, `docs/risks-and-open-questions.md`, `docs/technical-discovery.md`, `DESIGN.md` y `docs/adr/0003-ios-congelado-mvp.md` (iOS congelado).
  - Re-derivación del harness: `init.sh` pasa a gate **Android-only** (`:composeApp:assembleDebug` + `:core:testDebugUnitTest`; sin Xcode ni tareas iOS); `feature_list.json` conserva las 26 entradas (anotando las reemplazadas) y suma **20 features `not_started`** (12 producto + 8 IA; `ai-client-ports`, `ai-suggest-classification`); `AGENTS.md` (Android-first + IA real + backlog); `CONSTRAINTS.md` (Género ≥ Etiqueta; Android-first + `actual` iOS mínimo; key nunca versionada; tests/gate siempre fake; clasificación IA sugerida con confirmación; GDD híbrido).
  - IA: docs de descubrimiento actualizados (non-goal revisado: la IA sugiere, el usuario confirma) + `docs/adr/0004-ai-real-integration.md` (proveedor propio OpenAI-compatible; mapeo modelo→caso de uso; puertos; keys por build flag).
  - `DESIGN.md` iterado con la paleta del autor (`docs/design/paleta.png`) y las superficies de IA (tts, generación de imagen, transcripción, sugerencia de clasificación, indicador fake/real).
- Verification run:
  - `python3` (json.load + chequeo de `depends_on`/ciclos/`priority`) → `feature_list.json` válido; 46 entradas (25 `accepted`, 1 `blocked`, 20 `not_started`), 0 problemas.
  - `bash init.sh` → exit 0 con el gate Android-only, sin dev servers ni red.
  - `git diff --stat` sin cambios en código de producto ni esquema.
- Evidence captured: `feature_list.json` (20 nuevas + anotaciones), `bash init.sh` exit 0, y los documentos actualizados listados arriba.
- Files or artifacts updated: `init.sh`, `feature_list.json`, `AGENTS.md`, `PROGRESS.md`, `CONSTRAINTS.md`, `CONTEXT.md`, `docs/build-brief.md`, `docs/domain-model.md`, `docs/risks-and-open-questions.md`, `docs/technical-discovery.md`, `docs/adr/0004-ai-real-integration.md` (nuevo), `DESIGN.md`, `docs/design/paleta.png` (nuevo), `docs/design/icon.png` (nuevo).
- Known risk or unresolved issue: iOS congelado (se conserva y debe seguir compilando; los nuevos `expect` necesitan `actual` iOS mínimo, ya no forzado por el gate); IA real con clave por build flag (nunca versionada) y costos/offline abiertos (ver `docs/risks-and-open-questions.md`); esquema v7/v8 previstos.
- Next best step: al mergear el PR #32 a `develop`, derivar la spec de `genres-catalog-persistence` con `$feature-spec` y avanzar por el backlog con `$feature-flow` (una feature a la vez, WIP=1).

### Session 048

- Date: 2026-10-16
- Goal: implementar `app-icon-configuration` (spec `docs/specs/app-icon-configuration.md`): icono de launcher Android desde `docs/design/icon.png` (adaptive + legacy) y declararlo en el manifest.
- Completed:
  - Generados con `sips` (macOS, sin deps): `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` (48/72/96/144/192) y `drawable-nodpi/ic_launcher_foreground.png` (center-crop `-c 800 800` → `-z 264 264` → `-p 432 432 --padColor F7EFE2`).
  - `values/colors.xml` (`ic_launcher_background = #F7EFE2`) y `mipmap-anydpi-v26/ic_launcher.xml` (`background` color + `foreground` bitmap).
  - `AndroidManifest.xml`: `android:icon` + `android:roundIcon` = `@mipmap/ic_launcher` (sin otros cambios).
  - Docs durables: `docs/technical-discovery.md` ("App Icon Decisions (app-icon-configuration)") y `DESIGN.md` (icono marcado como configurado en el build Android).
- Verification run:
  - `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (54 actionable tasks).
  - `aapt dump badging` (build-tools 36.0.0) → `application: label='Playbook' icon='res/mipmap-anydpi-v26/ic_launcher.xml'`; `aapt list` → 5 densidades + anydpi-v26 + `drawable-nodpi-v4/ic_launcher_foreground.png`; `aapt dump --values resources` → `color/ic_launcher_background #fff7efe2`.
  - Smoke visual (emulador Pixel_3A_API_35/emulator-5556, API 35 ⇒ adaptive): icono de Playbook visible en el cajón; sin costura ni recorte del arte (capturas `composeApp/build/smoke-evidence/icon-*.png`, ignorado por git).
  - Controles negativos: (a) manifest sin icono → badging `icon=''` y robot por defecto en el launcher; (b) `@drawable/ic_launcher_foreground_missing` → `assembleDebug` exit 1 (`AAPT: resource ... not found`). Ambos restaurados y re-verificados en verde.
  - `./init.sh` (baseline y final) → exit 0; `git diff` de `core`/`iosApp`/`init.sh`/build files/`*.kt` vacío; sin deps nuevas.
- Evidence captured: entradas en `feature_list.json` (status `passing`, 9 líneas de evidencia) y esta sesión; salidas de `assembleDebug`/`aapt`/`init.sh` y capturas en `composeApp/build/smoke-evidence/`.
- Files or artifacts updated: `composeApp/src/androidMain/AndroidManifest.xml`, `composeApp/src/androidMain/res/**` (nuevos), `docs/technical-discovery.md`, `DESIGN.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el arte fuente es **provisional** (`DESIGN.md`); si el autor entrega uno nuevo hay que regenerar los PNG. `AGENTS.md` ("próxima feature en cola") revisado: sigue siendo `genres-catalog-persistence`, así que no se editó. Validación independiente pendiente (`feature-validator`).
- Next best step: `feature-validator` valida `app-icon-configuration` contra la spec; al `accept`, seguir con `genres-catalog-persistence` según el backlog.

### Session 049

- Date: 2026-10-16
- Goal: implementar (`$feature-implementer`) la feature `ai-client-ports` (spec `docs/specs/ai-client-ports.md`): contratos (puertos) de IA para los 4 modelos que `AiClient` no cubre (transcribe/tts/image/jev) + fakes deterministas, en `:core`.
- Completed:
  - `core/src/commonMain/.../ai/AiPorts.kt` (nuevo): interfaces `TranscribeClient` (`transcribe(audio, mimeType): String`), `TtsClient` (`synthesize(text): SynthesizedAudio`), `ImageClient` (`generateImage(prompt): GeneratedImage`) y `JevClient` (`suggest(text): ClassificationSuggestion`); tipos `SynthesizedAudio`/`GeneratedImage` (`class`, bytes+mime, porque contienen `ByteArray`) y `ClassificationSuggestion` (`data class`, `track: Track?` + `genres: List<String>`; sólo propone). Todos `suspend`; fallos vía `AiClientException`.
  - `core/src/commonMain/.../ai/FakeAiPorts.kt` (nuevo): `FakeTranscribeClient` (texto por tamaño+mime), `FakeTtsClient` (bytes UTF-8, `audio/mpeg`), `FakeImageClient` (PNG simbólico por prompt, `image/png`) y `FakeJevClient` (`Track` por hash + género ilustrativo). Deterministas, sin red ni `Random`.
  - `core/src/commonTest/.../ai/AiPortsContract.kt` (nuevo): `verify{Transcribe,Tts,Image,Jev}ClientContract` (Scenarios 1–4) + `verifyFakeAiPorts` (Scenario 6) + `ConstantTtsClient` test-only (Scenario 5, sustituibilidad sin tocar producción).
  - Runners `AiPortsAndroidTest`/`AiPortsIosTest` (nuevos; `@Test` + `runTest`).
  - Docs durables: `ARCHITECTURE.md` (sección AI: puertos por modelo + fakes), `docs/technical-discovery.md` (nueva "AI Ports Decisions (ai-client-ports)"), `AGENTS.md` (sólo la línea de cola); tasks de la spec; `feature_list.json` `in_progress` → `passing`.
- Verification run:
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; 52/52, 0 failures, con `AiPortsAndroidTest` 6/6 (nuevo).
  - `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (54 tasks).
  - iOS (no bloqueante): `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; `AiPortsIosTest` 6/6, 52/52, 0 failures.
  - `./init.sh` → exit 0, sin dev servers ni red; `git diff -- init.sh` vacío.
  - Esquema: `Schema.version = 6`; `find core/src -name '*.sqm'` → `1.sqm`–`5.sqm` (sin nuevo); sin deps nuevas.
  - Controles negativos reproducidos y restaurados byte-idénticos (sha256 `FakeAiPorts.kt` `c31e098e…`): (a) `FakeImageClient` con `Random.nextInt()` → `fakesAreDeterministicAndOffline` FAILED (Scenario 6); (b) `FakeImageClient` con `GeneratedImage(ByteArray(0), "")` → `fakeImageSatisfiesContract` FAILED (Scenario 3); (c) `FakeTranscribeClient` devolviendo `""` → `fakeTranscribeSatisfiesContract` FAILED (Scenario 1). Verde tras restaurar.
  - Paths protegidos: `git diff` vacío para `AiClient.kt`, `FakeAiClient.kt`, `AiAvailability.kt`, `NoteIndexingService.kt`, `RagQueryService.kt`, `core/src/commonMain/sqldelight/**`, `gradle/libs.versions.toml`, `core/build.gradle.kts`, `composeApp/**`, `iosApp/**`, `init.sh`.
- Evidence captured: 10 líneas en el arreglo `evidence` de `ai-client-ports` en `feature_list.json`; salida de tests/build en `core/build/test-results/`.
- Files or artifacts updated: `core/src/commonMain/kotlin/com/playbook/core/ai/AiPorts.kt` (nuevo), `core/src/commonMain/kotlin/com/playbook/core/ai/FakeAiPorts.kt` (nuevo), `core/src/commonTest/kotlin/com/playbook/core/ai/AiPortsContract.kt` (nuevo), `core/src/androidUnitTest/kotlin/com/playbook/core/ai/AiPortsAndroidTest.kt` (nuevo), `core/src/iosTest/kotlin/com/playbook/core/ai/AiPortsIosTest.kt` (nuevo), `ARCHITECTURE.md`, `docs/technical-discovery.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: la canonización de Géneros contra el catálogo no la hace el fake (`FakeJevClient` devuelve un género ilustrativo): es del consumidor (`genres-catalog-persistence`); la sugerencia `jev` sólo propone y nunca auto-asigna. Sin adaptadores reales ni build flag (llegan con `ai-fake-real-switch` y los casos de uso). Sin E2E persistente (la spec lo justifica: sin consumidor ni flujo observable). Pendiente validación independiente.
- Next best step: validación independiente de `ai-client-ports` con `$feature-validator` (spec `docs/specs/ai-client-ports.md`); tras el `accept`, continuar con `genres-catalog-persistence` (primera ready del backlog) o `ai-fake-real-switch`.

### Session 050

- Date: 2026-10-16
- Goal: implementar `genres-catalog-persistence` (spec `docs/specs/genres-catalog-persistence.md`): dominio + persistencia (esquema v7) + renombre mecánico en la UI.
- Completed:
  - Dominio (`:core`): `Tags.kt`→`Genres.kt` con `GenreCatalog` (12 Géneros fijos) + `normalizeGenres` (trim, descarte de vacíos, canonización case-insensitive contra el catálogo, dedupe case-insensitive, orden); `Note.tags`/`NoteDraft.tags`→`genres`; `NoteMappers.toDomain(genres)`.
  - Persistencia: `NoteTag.sq`→`Genre.sq` (tabla `note_genre(note_id, genre)` PK `(note_id, genre)`; queries `insertNoteGenre`/`selectGenresByNoteId`/`selectAllNoteGenres`/`deleteGenresByNoteId`; interfaz `genreQueries`) y nuevo `6.sqm` (v6→v7: `CREATE TABLE note_genre` + `INSERT … SELECT note_id, label FROM note_tag` + `DROP TABLE note_tag`). `2.sqm` **sin cambios**.
  - Repositorio: `NoteRepository`/`SqlDelightNoteRepository` con `genres` + `genreQueries` (`replaceTags`→`replaceGenres`), mismos patrones de transacción y agrupación anti-N+1.
  - GDD: `GddMarkdown.metadataLine` emite `Géneros: … — Estado: …`.
  - UI mínima (`:composeApp`): renombre mecánico `tags`→`genres` en `App`/`NoteEditorScreen`/`NotesListScreen`/`GddScreen`/`RelatedNotesScreen` y rótulos "Géneros"/"Nuevo género" + `onClickLabel` "Quitar género …". El editor sigue siendo de **texto libre** (el picker de catálogo es `genres-catalog-ui`).
  - Tests: `NoteTagCheck`→`GenreCheck` (`verifyGenres` escenarios 1–5 + `verifyGenreMigration` v1→v7); `MigrationCheck` (`verifyMigrationV1ToV7`, `verifyMigrationV6ToV7` para Scenario 6) + runners; `DatabaseMigrationCheck` a v7 con columnas de `note_genre` y ausencia de `note_tag`; `NoteRepositoryCheck`/`NoteIndexingOrchestratorCheck`/`GddMarkdownCheck` renombrados a `genres`.
  - Docs durables: `ARCHITECTURE.md` (Géneros, `Genres.kt`, `note_genre`, v7, prefijo `Géneros:`), `docs/technical-discovery.md` (v7 = Géneros, v8 = Proyecto), `docs/risks-and-open-questions.md` (migración N—N renombrada, v8 = Proyecto, normalización resuelta), nota de supersesión en `docs/specs/gdd-export.md`, `AGENTS.md` (sólo la línea de cola).
- Verification run:
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; Android/JVM **47 tests, 0 failures** (NoteRepositoryAndroidTest 4/4, MigrationAndroidTest 3/3, DatabaseMigrationAndroidTest 1/1, GddMarkdown 1/1, NoteIndexingOrchestrator 1/1, etc.).
  - `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (54 tasks).
  - `./init.sh` → exit 0 (Android-only, sin dev servers ni red).
  - `PlaybookDatabase.Schema.version == 7`; `.sqm` = `1.sqm`..`6.sqm` (sólo `6.sqm` nuevo); `git diff 2.sqm` vacío.
  - iOS (no bloqueante): `:core:iosSimulatorArm64Test --rerun-tasks` y `:composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - Controles negativos reproducidos y restaurados byte-idénticos (`6.sqm` sha256 `ce548807…`, `Genres.kt` sha256 `8033fe70…`): (a) `6.sqm` sin `INSERT … SELECT` → falla `migratesV6ToV7RenamingTagsToGenres`; (b) `canonicalize` devolviendo `raw` → falla `genresRoundTrip`; (c) dedupe por igualdad exacta → falla `genresRoundTrip`. Re-verificado en verde tras restaurar.
- Evidence captured: arreglo `evidence` de `genres-catalog-persistence` en `feature_list.json` (11 líneas).
- Files or artifacts updated: `core/src/commonMain/kotlin/com/playbook/core/model/{Genres.kt (nuevo), Note.kt, NoteMappers.kt}`, `core/src/commonMain/sqldelight/com/playbook/core/db/{Genre.sq (nuevo), 6.sqm (nuevo), NoteTag.sq (eliminado)}`, `core/src/commonMain/kotlin/com/playbook/core/repository/{NoteRepository.kt, SqlDelightNoteRepository.kt}`, `core/src/commonMain/kotlin/com/playbook/core/gdd/GddMarkdown.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/{App.kt, NoteEditorScreen.kt, NotesListScreen.kt, GddScreen.kt, RelatedNotesScreen.kt, LabelChip.kt, EnrichmentStatus.kt}`, `core/src/{commonTest,androidUnitTest,iosTest}/**`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `docs/specs/gdd-export.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: sin emulador Android disponible (`adb` ausente), el smoke real v6→v7 sobre `playbook.db` no se ejecutó; la migración se cubre con los tests del core sobre el esquema real. El picker de catálogo en la UI queda para `genres-catalog-ui`. La feature queda `passing` hasta la validación independiente.
- Next best step: validación independiente de `genres-catalog-persistence` con `$feature-validator`; tras el `accept`, derivar/implementar `genres-catalog-ui` (picker de chips) o `game-project-persistence` (v8).

### Session 051

- Date: 2026-10-16
- Goal: preparar la ejecución del MVP restante (plan, excepción de paralelismo y tarea de README-bitácora).
- Completed:
  - `docs/mvp-execution-plan.md` (nuevo): alcance de las 18 features `not_started`, modelo de sesión/worktree/rama, paradas planner → implementer → validator, 6 olas con orden de merge, protocolo de coordinación paralela y criterio de cierre del MVP.
  - `feature_list.json`: nueva tarea `readme-project-documentation` (`area: docs`) con `depends_on` = las 18 features del MVP, para que se seleccione recién al final; es documentación de cierre, no feature de producto.
  - `AGENTS.md`: excepción controlada al WIP=1 (hasta 3 features en vuelo, una por sesión, una sola cola de merge) y referencia al plan vivo.
  - `PROGRESS.md`: estado actual y esta sesión.
- Verification run:
  - `python3 -c "import json; json.load(open('feature_list.json'))"` → JSON válido; 19 `not_started` (18 features + README) y 28 `accepted`.
  - `git diff --check` → sin errores de whitespace.
  - `CI=true ./init.sh` → exit 0 (Android-only, sin red ni dev servers).
- Evidence captured: este bloque de sesión; `feature_list.json` y `docs/mvp-execution-plan.md`.
- Files or artifacts updated: `docs/mvp-execution-plan.md` (nuevo), `feature_list.json`, `AGENTS.md`, `PROGRESS.md`.
- Known risk or unresolved issue: la ejecución paralela introduce conflictos esperables en `feature_list.json`/`PROGRESS.md` y en los hotspots de UI; se mitiga con el orden de merge y la cola única.
- Next best step: crear los worktrees de la Ola 1 y abrir una sesión por feature para correr sólo el rol `planner`: `game-project-persistence`, `genres-catalog-ui` y `ai-fake-real-switch`.

### Session 052

- Date: 2026-10-19
- Goal: implementar `game-project-persistence` (spec `docs/specs/game-project-persistence.md`): dominio + persistencia del Proyecto de Juego singleton (esquema v8).
- Completed:
  - Dominio (`:core`): `GameProject.kt` con `GAME_PROJECT_SINGLETON_ID`, `GameProject`, `GameProjectDraft`, `KeyMechanic`, `MechanicCategory` (`movimiento`/`interacción`/`conflicto`, `fromCode` fail-fast) y helpers `normalizeProjectText`/`normalizeProjectList`/`normalizeKeyMechanics` (trim, descarte de vacíos, límites 3/3/5 después de normalizar); `GameProjectMappers.kt` (`Game_project` → dominio).
  - Persistencia: `GameProject.sq` (tabla `game_project` con `CHECK (id = 'singleton')` + 4 tablas hijas con FK cascade y PKs compuestas/CHECK de posición) y `7.sqm` (v7→v8 aditivo, DDL espejado); `GameProjectRepository`/`SqlDelightGameProjectRepository` (`get`/`save` upsert; preserva `owner`/`createdAt`, un owner distinto falla, reemplaza las hijas en la transacción).
  - Tests: `GameProjectCheck.verifyGameProject` (escenarios 1–9) + runners `GameProjectAndroidTest`/`GameProjectIosTest`; `MigrationCheck` a v1→v8 + `verifyMigrationV7ToV8`; `DatabaseMigrationCheck` a v8 con columnas/PKs/FKs de las tablas del Proyecto; `GenreCheck` y runners renombrados/actualizados.
  - Docs durables: `ARCHITECTURE.md` (v8, tablas del Proyecto, dominio/repositorio), `docs/technical-discovery.md` (v8 implementado, dos menciones), `docs/risks-and-open-questions.md` (pregunta v8 cerrada), `AGENTS.md` (sólo la línea de cola), `feature_list.json` `in_progress` → `passing`.
- Verification run:
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; Android/JVM **55 tests, 0 failures** (GameProjectAndroidTest 1/1, MigrationAndroidTest 4/4, DatabaseMigrationAndroidTest 1/1, NoteRepositoryAndroidTest 4/4, etc.).
  - `CI=true ./init.sh` → exit 0 (Android-only, sin dev servers ni red); `:composeApp:assembleDebug` BUILD SUCCESSFUL.
  - `PlaybookDatabase.Schema.version` = 8; `find core/src -name '*.sqm'` = `1.sqm`..`7.sqm` (sólo `7.sqm` nuevo); `1.sqm`..`6.sqm` sin cambios.
  - iOS (no bloqueante): `:core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; 55/55, 0 failures (GameProjectIosTest 1/1, MigrationIosTest 4/4); `:composeApp:linkDebugFrameworkIosSimulatorArm64` BUILD SUCCESSFUL.
  - Controles negativos reproducidos y restaurados byte-idénticos (hashes `7.sqm` `006d68bc…`, `SqlDelightGameProjectRepository.kt` `06ab3322…`, `GameProject.kt` `efb29c94…`): (a) `7.sqm` sin `game_project_inspiration` → falla `migratesV7ToV8AddingProjectTables`; (b) `save` sin borrar inspiraciones previas → `projectRoundTrip` falla con `SQLiteException` (PK); (c) sin `require(size <= max)` → `projectRoundTrip` falla (el CHECK de SQLite atrapa la posición fuera del límite).
  - Paths protegidos: `git diff` vacío para `composeApp/**`, `iosApp/**`, `init.sh`, `gradle/libs.versions.toml`, `core/build.gradle.kts`, `Note.sq`, `Genre.sq`, `1.sqm`..`6.sqm`.
- Evidence captured: arreglo `evidence` de `game-project-persistence` en `feature_list.json` (9 líneas); salida de tests/build en `core/build/test-results/`.
- Files or artifacts updated: `core/src/commonMain/kotlin/com/playbook/core/model/{GameProject.kt (nuevo), GameProjectMappers.kt (nuevo)}`, `core/src/commonMain/kotlin/com/playbook/core/repository/{GameProjectRepository.kt (nuevo), SqlDelightGameProjectRepository.kt (nuevo)}`, `core/src/commonMain/sqldelight/com/playbook/core/db/{GameProject.sq (nuevo), 7.sqm (nuevo)}`, `core/src/{commonTest,androidUnitTest,iosTest}/**`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: la lectura fail-fast de una categoría desconocida sólo se cubre insertando directo en la base (camino de corrupción, no de escritura normal). El estado `vacío`/`esbozado` no se persiste (se deriva aguas abajo) y no hay FK Nota→Proyecto. Sin emulador Android disponible (`adb` ausente), no se ejecutó smoke real de la migración v7→v8 sobre `playbook.db`; se cubre con tests del core sobre el esquema real. La feature queda `passing` hasta la validación independiente.
- Next best step: validación independiente de `game-project-persistence` con `$feature-validator`; tras el `accept`, continuar con `genres-catalog-ui` (picker de chips) o `ai-fake-real-switch`.

### Session 053

- Date: 2026-10-19
- Goal: validación independiente de `game-project-persistence` y repair de los hallazgos (sin cambiar el estado a `accepted`).
- Completed:
  - Auditoría independiente contra `docs/specs/game-project-persistence.md`: escenarios 1–12, migración v7→v8, bootstrap vs migración, scope/non-goals, seguridad y docs.
  - Hallazgo **Medium**: el orden de Géneros no round-tripeaba para personalizados con mayúsculas mezcladas (`normalizeGenres` ordena case-insensitive; `get()` leía con `ORDER BY genre` BINARY). Repair: `.sortedBy { it.lowercase() }` en `get()` + regresión `["apple", "Banana"]` en `GameProjectCheck`.
  - Hallazgo **Low**: `docs/specs/audio.mp3` untracked y ajeno a la feature. Repair: eliminado.
  - Hallazgo **Low**: Scenario 10 sólo cubría Nota + Género. Repair: `verifyMigrationV7ToV8` ahora siembra Adjunto, Enlace y Embedding antes de migrar y verifica su supervivencia.
  - Decisión: `updatedAt` se mantiene con `clock()` directo (patrón del repo), sin forzar monotonía estricta.
  - `docs/validations/game-project-persistence.md` creado con veredicto `accept`, checks, hallazgos, repair y controles negativos.
- Verification run:
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; Android/JVM **55 tests, 0 failures, 0 errors** (`GameProjectAndroidTest` 1/1, `MigrationAndroidTest` 4/4, `DatabaseMigrationAndroidTest` 1/1, `NoteRepositoryAndroidTest` 4/4).
  - `CI=true ./init.sh` → **exit 0**; `:composeApp:assembleDebug` OK; gate Android-only.
  - `PlaybookDatabase.Schema.version` getter = 8; `.sqm` = `1.sqm`..`7.sqm`; DDL `.sq`/`7.sqm` espejado 5/5; paths protegidos sin cambios.
  - iOS (no bloqueante): `:core:iosSimulatorArm64Test --rerun-tasks` 55/0 y `:composeApp:linkDebugFrameworkIosSimulatorArm64` BUILD SUCCESSFUL.
  - Controles negativos propios (a/b/c) con hashes post-fix y restauración byte-idéntica: (a) `7.sqm` mutado `8fc76855…` → falla `migratesV7ToV8AddingProjectTables`; (b) repositorio mutado `a6c1c6ab…` → falla `projectRoundTrip` (PK); (c) `GameProject.kt` mutado `a577384c…` → falla `projectRoundTrip` (CHECK). Restaurados a `006d68bc…`, `fe7265a7…` y `efb29c94…`; suite completa verde después.
- Evidence captured: `feature_list.json` (evidencia de validación/repair) y `docs/validations/game-project-persistence.md`.
- Files or artifacts updated: `SqlDelightGameProjectRepository.kt`, `GameProjectCheck.kt`, `MigrationCheck.kt`, `docs/validations/game-project-persistence.md` (nuevo), `feature_list.json`, `PROGRESS.md`; eliminado `docs/specs/audio.mp3`.
- Known risk or unresolved issue: sin emulador Android (`adb` ausente) no se ejecutó smoke real de la migración sobre `playbook.db`; cobertura por tests del core sobre el esquema real. La lectura fail-fast de categoría desconocida sólo se cubre por inserción directa (camino de corrupción).
- Next best step: la sesión integradora persiste `accepted` para `game-project-persistence`; luego continuar con `genres-catalog-ui` (picker de chips) o `ai-fake-real-switch`.

### Session 052 (genres-catalog-ui)

- Date: 2026-10-19
- Goal: implementar `genres-catalog-ui` (spec `docs/specs/genres-catalog-ui.md`): picker de Géneros con catálogo + personalizado y chip de lectura con variante catálogo/personalizado.
- Completed:
  - `composeApp/src/commonMain/kotlin/com/playbook/app/GenrePicker.kt` (nuevo): "Géneros"; si hay elegidos, "Seleccionados" con chips removibles `×`; "Catálogo" con los 12 `GenreCatalog.GENRES` en orden en un `FlowRow`; "Género personalizado" + placeholder "Escribí uno nuevo…" + "Agregar" habilitado sólo con texto no blanco.
  - Toggle de catálogo (agrega/quita) y toda alta/remoción pasan por `normalizeGenres`; dedupe/canonización case-insensitive (`jrpg` → `JRPG`). Sin límites de cantidad ni largo; etiquetas a una línea con ellipsis.
  - Accesibilidad: `onClickLabel` "Agregar género X"/"Quitar género X", `role = Role.Button`, `defaultMinSize(minHeight = 48.dp)`, `✓` + borde de 2.dp y `semantics { selected }` en el chip del catálogo.
  - `NoteEditorScreen.kt`: usa `GenrePicker(selected = genres, onChange = { genres = it })`, conserva `genres = normalizeGenres(initialGenres)` y `onSave(..., genres)`, y elimina el `GenreChip` privado y el texto libre. `App.kt` sin cambios.
  - `LabelChip.kt`: nuevo `GenreLabelChip(text, modifier)` con detección de catálogo case-insensitive, `primaryContainer` vs `secondaryContainer` y una línea con ellipsis; `LabelChip` intacto. Usado sólo en las filas de Géneros de `NotesListScreen.kt`, `GddScreen.kt` y `RelatedNotesScreen.kt`.
  - Docs/evidencia: `docs/technical-discovery.md` (decisión de UI), `feature_list.json` (`passing` + evidencia), `AGENTS.md` (sólo la línea de próxima feature), esta sesión y capturas/UI dumps en `composeApp/build/smoke-evidence/genres-catalog-ui/` (ignorado por git).
- Verification run (base `6ba979f`, pre `game-project-persistence`):
  - `./gradlew :composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL (54 tasks).
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; 22 suites, **53 tests, 0 failures, 0 errors**. Nota honesta: el spec citaba 47 como expectativa; el branch ya incluía tests de features integradas posteriores y `:core` no se tocó.
  - `./init.sh` → exit 0 (Android-only, sin dev servers ni red).
  - iOS (no bloqueante): `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - `PlaybookDatabase.Schema.version == 7`; `.sqm` = `1.sqm`..`6.sqm`; `git diff --stat` vacío en `:core`, build files, `gradle/libs.versions.toml`, `init.sh`, `iosApp`, `App.kt`, `MainActivity`/`MainViewController`; sin dependencias ni secrets.
  - Smoke manual Android (emulator-5556, Pixel_3A_API_35, 1080x2220/440 dpi): 12 chips de catálogo en orden y sin "Seleccionados" al inicio; toggle Platformer/JRPG; tocar JRPG de nuevo lo quita; `" jrpg "` + Agregar → `JRPG`; alta de `Zelda-like`; tarjeta, reapertura, remoción/alta, GDD y Notas relacionadas; persistencia tras force-stop + relanzar. UI dumps/capturas en `composeApp/build/smoke-evidence/genres-catalog-ui/` (`02`, `05`–`08`, `10`, `12`–`18`, `27`).
- Evidence captured: arreglo `evidence` de `genres-catalog-ui` en `feature_list.json` + capturas/UI dumps.
- Files or artifacts updated: `GenrePicker.kt` (nuevo), `NoteEditorScreen.kt`, `LabelChip.kt`, `NotesListScreen.kt`, `GddScreen.kt`, `RelatedNotesScreen.kt`, `docs/specs/genres-catalog-ui.md`, `docs/technical-discovery.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el editor seguía siendo una `Column` no scrollable; el cuerpo perdía peso y los botones podían quedar al borde (resuelto en Session 053). Sin tests de UI/E2E persistentes (la spec lo justifica).
- Next best step: validación independiente de `genres-catalog-ui`.

### Session 053 (genres-catalog-ui)

- Date: 2026-10-19
- Goal: corregir el hallazgo High de la validación independiente de `genres-catalog-ui`: en modo edición el editor no era scrollable, el cuerpo quedaba en una línea y la fila `Borrar/Cancelar/Guardar` se recortaba fuera del viewport.
- Completed:
  - `NoteEditorScreen.kt`: el contenido (título, estado, imagen, cuerpo, Track, `GenrePicker`, "Ver relacionadas") vive en una `Column` con `verticalScroll(rememberScrollState())` y `weight(1f)`; la fila `Borrar/Cancelar/Guardar` queda **fija** fuera del scroll. El cuerpo usa `heightIn(min = 200.dp, max = 320.dp)`. KDoc actualizado.
  - `GenrePicker.kt`/`GenreLabelChip`/`LabelChip` intactos: la reparación es sólo de layout del editor.
  - Docs: `feature_list.json` (evidencia del fix), `docs/technical-discovery.md` (decisión durable de layout), `docs/specs/genres-catalog-ui.md` (tareas marcadas y conteo actualizado). `AGENTS.md` sin cambios en esta sesión.
- Verification run:
  - `./gradlew :composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL (54 tasks).
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; 22 suites / **53 tests / 0 failures / 0 errors / 0 skipped** (base pre-integración).
  - `./init.sh` → exit 0; iOS link no bloqueante → BUILD SUCCESSFUL.
  - Smoke Android (emulator-5556): `29` edición con 3 Géneros → cuerpo 550 px (200 dp) y acciones completas en `[2061,2116]`; `31` 12 catálogo + `Zelda-like`; `33`/`39` persistencia; `35` `" jrpg "` → `JRPG` sin duplicado; `36` GDD con personalizado; `37` creación; `40`/`41` `jrpg` sembrado → `JRPG`. Evidencia `29`–`41` en `composeApp/build/smoke-evidence/genres-catalog-ui/`.
- Validation: validación independiente (`docs/validations/genres-catalog-ui.md`) → **accept** (2026-10-19). El validador re-ejecutó `assembleDebug` (54 tasks), `:core` (22 suites / 53 tests / 0 failures), `init.sh` (exit 0) y el link iOS; verificó alcance vacío y schema v7 en la base validada; re-dirigió el smoke en emulator-5556 (3 y 13 Géneros, toggle/dedupe, GDD con personalizado, `jrpg` sembrado → `JRPG`) y restauró la DB. Notas Low no bloqueantes: casing legacy de lectura, ellipsis con scroll horizontal, tamaños extremos y altura acotada del cuerpo.
- Integración (rebase final sobre `develop` @ `a404f09`, con `game-project-persistence` v8): la base incluye esquema **v8** (`7.sqm`), 23 suites / **55 tests / 0 failures**, `assembleDebug` 60 tasks y `./init.sh` exit 0; `genres-catalog-ui` no modifica `:core` ni el esquema. La validación `accept` se mantiene porque el diff de la feature es el mismo.
- Next best step: PR listo para revisión; la sesión integradora puede mergear cuando el autor lo indique.

### Session 054

- Date: 2026-10-19
- Goal: persistir la aceptación de `game-project-persistence` y dejar `develop` sincronizado como base de la Ola 1/2.
- Completed:
  - `feature_list.json`: `game-project-persistence` `passing` → `accepted`; evidencia de validación independiente y de la integración; notas actualizadas.
  - `PROGRESS.md`: estado actual y próxima cola actualizados; sesión de integración registrada.
  - `AGENTS.md`: cola de features actualizada (Proyecto aceptado; siguientes `genres-catalog-ui` y `ai-fake-real-switch`).
- Verification run:
  - `CI=true ./init.sh` → exit 0 en `develop` (Android-only, sin red ni dev servers).
  - Validación independiente `accept` registrada en `docs/validations/game-project-persistence.md` (55 tests/0 fallos en Android/JVM e iOS; migración v7→v8).
  - `git diff` de esta sesión acotado a `feature_list.json`, `PROGRESS.md` y `AGENTS.md`; sin cambios de código ni de esquema.
- Evidence captured: este bloque de sesión y la entrada `accepted` en `feature_list.json`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `AGENTS.md`.
- Known risk or unresolved issue: `genres-catalog-ui` y `ai-fake-real-switch` están en validación en otras sesiones; hay que rebasearlas sobre este `develop` después de cada accept/merge.
- Next best step: cerrar la validación de `genres-catalog-ui` y `ai-fake-real-switch`; al aceptarlas, integrarlas en ese orden y luego abrir `brainstorming-wizard`, `inline-dictation-30s` y `ai-real-embeddings` (Ola 2).

### Session 055 (ai-fake-real-switch)

- Date: 2026-10-19
- Goal: implementar `ai-fake-real-switch` (spec `docs/specs/ai-fake-real-switch.md`): selección fake/real por build flag + adaptadores reales de los 6 modelos + reconciliación de dimensión de embeddings.
- Completed:
  - `:core`: `com.playbook.core.ai.openai` con `HttpTransport`/`HttpRequest`/`HttpResponse`, `AiRuntimeConfig` (key redactada), `OpenAiHttp`, adaptadores `OpenAiAiClient` (chat/embedding), `OpenAiTranscribeClient`, `OpenAiTtsClient`, `OpenAiImageClient`, `OpenAiJevClient`, `AiRuntime`/`fakeAiRuntime()`/`openAiAiRuntime()` y `ConfiguredAiAvailability`.
  - `:core`: `EmbeddingDimensionReconciler` (borra embeddings de dimensión distinta, deja la Nota `PENDING` y `index_attempts=0`; `null` = no-op).
  - `:core`: dependencia `kotlinx-serialization-json` 1.8.1 (runtime, sin plugin); Base64 con `kotlin.io.encoding.Base64`.
  - `:composeApp`: flag/config por `-P` > env > `local.properties`, `BuildConfig`, guard debug/release (real sólo debug; release real con `allowReleaseKey`), key vacía en fake; `UrlConnectionHttpTransport`; `createAndroidAiRuntime()`; wiring en `MainActivity` y `MainViewController` (iOS fake); `init.sh` fuerza `-Pplaybook.ai.mode=fake`.
  - Tests: `OpenAiAdaptersCheck` (requests/parseo/errores/seguridad), `EmbeddingDimensionReconcilerCheck`, `UrlConnectionHttpTransportTest` (servidor local), `AndroidAiRuntimeTest`, smoke key-gated `RealAiSmokeTest` (6 modelos; el transcribe usa el audio de tts).
  - Docs: `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`, `AGENTS.md` (cola), `feature_list.json` (evidencia).
- Verification run:
  - `./init.sh` → exit 0 (fake, sin red).
  - `:core:testDebugUnitTest` → BUILD SUCCESSFUL (adaptadores + reconciler incluidos).
  - `:composeApp:testDebugUnitTest -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL (transporte + selección).
  - Build real debug con dummy key → BUILD SUCCESSFUL (`AI_MODE=real`, `AI_EMBEDDING_DIMENSION=1536`); real sin key → exit 1 con "Modo IA real incompleto"; release real sin opt-in → exit 0 con `AI_MODE=fake` y key vacía.
  - Gate con `PLAYBOOK_AI_MODE=real PLAYBOOK_AI_API_KEY=dummy` → exit 0; BuildConfig debug `AI_MODE=fake`, `AI_API_KEY` vacío.
  - `:composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - Controles negativos: romper `choices` → falla `chatParsesContentAndSendsContext`; incluir la key en el error → falla `httpErrorIsGenericAndSanitized`. Ambos restaurados y re-verificados en verde.
  - Smoke real key-gated de los 6 modelos (`-Pplaybook.ai.mode=real -Pplaybook.ai.smoke=1`, key fuera del repo) → BUILD SUCCESSFUL: `embedding=1536`, `chat=2`, `tts=47232` bytes, `transcribe=26`, `image=601619` bytes (`image/jpeg`), `track=MECHANICS`, `genres=1`.
  - Hallazgos del smoke: la dimensión real del embedding es **1536** (el valor inicial `3` era incorrecto, se ajustó el default); el proveedor de imagen **sólo acepta 1024x1024** (512/256 → HTTP 400) y devolvió **JPEG** aunque se pidió PNG transparente, por lo que el adaptador detecta el mime real por bytes.
- Evidence captured: arreglo `evidence` de `ai-fake-real-switch` en `feature_list.json`; logs de verificación; resultado sanitizado del smoke.
- Files or artifacts updated: `core/src/commonMain/kotlin/com/playbook/core/ai/{EmbeddingDimensionReconciler.kt,openai/*}`, `composeApp/src/androidMain/kotlin/com/playbook/app/{MainActivity.kt,ai/*}`, `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt`, `gradle/libs.versions.toml`, `core/build.gradle.kts`, `composeApp/build.gradle.kts`, `init.sh`, tests de `:core`/`:composeApp`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`, `AGENTS.md`, `docs/specs/ai-fake-real-switch.md`, `feature_list.json`.
- Known risk or unresolved issue: la key queda embebida en un APK real (no distribuir); iOS sigue fake. El proveedor de imagen limita el tamaño a 1024x1024 y puede devolver JPEG (sin transparencia), documentado en la spec y `docs/technical-discovery.md`. El smoke real usó la key desde `local.properties` fuera del repo; no se versionó. La feature queda `passing` (auto-verificada), pendiente de validación independiente.
- Next best step: validación independiente de `ai-fake-real-switch` con `$feature-validator` (spec `docs/specs/ai-fake-real-switch.md`); tras el `accept`, promover ADR 0004 y seguir con la Ola 1.

### Session 055 — reparación post-validación (revise) (ai-fake-real-switch)

- Date: 2026-10-19
- Goal: cerrar los hallazgos Medium/Low del validator (`revise`) sin ampliar el alcance ni promover ADR 0004.
- Completed:
  - `UrlConnectionHttpTransport`: `instanceFollowRedirects = false`; un 307/308 que `HttpURLConnection` reporta como `HttpRetryException` se devuelve como `HttpResponse(status=3xx)` sin seguir el redirect. Test nuevo con dos `HttpServer` locales: el destino no recibe la petición y el adaptador lanza `AiClientException("chat falló con HTTP 307")` sin key/body.
  - `allowReleaseKey` ahora se lee **sólo** de `findProperty("playbook.ai.allowReleaseKey")`; `PLAYBOOK_AI_ALLOW_RELEASE_KEY` y `local.properties` ya no lo habilitan.
  - Docs: `ARCHITECTURE.md`, `CONSTRAINTS.md`, `docs/technical-discovery.md` y la spec reflejan la política de redirects y el guard por `-P`; `PROGRESS.md` actualizado a 2026-10-19. ADR 0004 sigue `propuesto`.
- Verification run:
  - `./init.sh` → exit 0 (fake, sin red); `PLAYBOOK_AI_MODE=real PLAYBOOK_AI_API_KEY=dummy ./init.sh` → exit 0 con `AI_MODE=fake` y `AI_API_KEY` vacío.
  - `:core:testDebugUnitTest --rerun-tasks` → 72 tests / 0 failures.
  - `:composeApp:testDebugUnitTest -Pplaybook.ai.mode=fake --rerun-tasks` → 5 tests / 0 failures (`UrlConnectionHttpTransportTest` 2, incluido el control de redirect).
  - Build real debug con dummy key → OK (`AI_MODE=real`, `AI_EMBEDDING_DIMENSION=1536`); real sin key → exit 1 con "Modo IA real incompleto".
  - Release: `PLAYBOOK_AI_ALLOW_RELEASE_KEY=true` + `-Pplaybook.ai.mode=real -Pplaybook.ai.apiKey=dummy-key` → `AI_MODE=fake`, key vacía; `-Pplaybook.ai.allowReleaseKey=true` → `AI_MODE=real`; sin allow → fake + warning + key vacía.
  - `:composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL.
  - Esquema v7 (`1.sqm`..`6.sqm`), sin `.sqm` nuevos; `AiClient`/`FakeAiClient`/`AiPorts`/`FakeAiPorts`/`AiAvailability`/`App.kt`/`iosApp` sin cambios; `json.load` y `git diff --check` OK.
- Evidence captured: logs de reparación; `feature_list.json` evidence actualizada.
- Known risk or unresolved issue: la feature sigue `passing`, pendiente de revalidación independiente. La key real no se copió al worktree ni se versionó; el smoke real ya estaba registrado. ADR 0004 sigue `propuesto`.
- Next best step: revalidación independiente con `$feature-validator`.

### Session 055 — reparación post-revalidación (High) (ai-fake-real-switch)

- Date: 2026-10-19
- Goal: corregir el hallazgo High del segundo validator (transporte HTTP bloqueante en Main) y el Low de cobertura de errores; el Low de `findProperty` queda diferido por decisión del autor.
- Completed:
  - `UrlConnectionHttpTransport` ejecuta todo el I/O bloqueante dentro de `withContext(dispatcher)` con `Dispatchers.IO` por defecto e **inyectable** en tests; conserva `instanceFollowRedirects=false` y el mapeo de 307/308 a `HttpResponse(3xx)`.
  - Test `dispatchesBlockingIoOffTheCallerThread` con `RecordingDispatcher`: verifica que el trabajo se despacha fuera del hilo llamador.
  - `OpenAiAdaptersCheck`: tests nuevos de HTTP 500 y fallo de red/timeout; ambos verifican mensaje genérico y ausencia de key/body.
  - Docs: spec, `ARCHITECTURE.md`, `CONSTRAINTS.md` y `docs/technical-discovery.md` documentan el dispatch en `Dispatchers.IO`.
  - Hallazgo Low de `findProperty` (`gradle.properties`/`ORG_GRADLE_PROJECT_*`) **diferido** por decisión del autor; no se cambió el código.
- Verification run:
  - `./init.sh` → exit 0 (fake, sin red).
  - `:core:testDebugUnitTest --rerun-tasks` → **74 tests / 0 failures** (`OpenAiAdaptersCheck` 17).
  - `:composeApp:testDebugUnitTest -Pplaybook.ai.mode=fake --rerun-tasks` → **6 tests / 0 failures** (`UrlConnectionHttpTransportTest` 3).
  - Build real debug con dummy key → OK (`AI_MODE=real`, `AI_EMBEDDING_DIMENSION=1536`); real sin key → exit 1 con "Modo IA real incompleto".
  - Gate con `PLAYBOOK_AI_MODE=real PLAYBOOK_AI_API_KEY=dummy` → exit 0; debug `AI_MODE=fake`, `AI_API_KEY` vacío.
  - Release: `PLAYBOOK_AI_ALLOW_RELEASE_KEY=true` + `-P...mode=real -P...apiKey=dummy-key` → `AI_MODE=fake`, key vacía; `-P...allowReleaseKey=true` → `AI_MODE=real`; sin allow → fake + warning + key vacía.
  - `:composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL.
- Evidence captured: logs de la reparación; `feature_list.json` evidence actualizada.
- Known risk or unresolved issue: el smoke real no se re-ejecutó (sin key en el worktree; evidencia previa); el hardening estricto de `allowReleaseKey` a sólo `-P` queda diferido. La feature sigue `passing`, pendiente de revalidación.
- Next best step: revalidación independiente con `$feature-validator`.

### Session 056 (ai-fake-real-switch)

- Date: 2026-10-19
- Goal: validación independiente de `ai-fake-real-switch` y cierre de aceptación/integración.
- Completed:
  - Validación independiente con veredicto **`accept`**: se re-ejecutaron el gate, tests de `:core`/`:composeApp`, build real debug, fallo de real sin key, guard de release, iOS link, JSON/diff, esquema y archivos protegidos.
  - `feature_list.json`: `ai-fake-real-switch` promovida de `passing` a `accepted` con evidencia de la validación (2026-10-19).
  - `docs/adr/0004-ai-real-integration.md`: promovido de `propuesto` a `accepted`, según el Durable Documentation Impact de la spec.
  - Documentación del residual: `allowReleaseKey` sigue usando `findProperty`, por lo que `gradle.properties`/`ORG_GRADLE_PROJECT_*` también podrían habilitarlo; el hardening estricto a sólo `-P` queda como deuda del autor. Se aclaró la redacción de debug/release en `CONSTRAINTS.md`, `ARCHITECTURE.md`, la spec y `docs/technical-discovery.md`.
- Verification run (validator independiente):
  - `./init.sh` → exit 0 (Android-only, sin dev servers).
  - `PLAYBOOK_AI_MODE=real PLAYBOOK_AI_API_KEY=dummy ./init.sh` → exit 0; debug `AI_MODE=fake`, `AI_API_KEY=""`.
  - `:core:testDebugUnitTest --rerun-tasks` → **74/74, 0 failures** (`OpenAiAdaptersCheck` 17).
  - `:composeApp:testDebugUnitTest -Pplaybook.ai.mode=fake --rerun-tasks` → **6/6, 0 failures** (`UrlConnectionHttpTransportTest` 3).
  - Build real debug con dummy key → OK (`AI_MODE=real`, `AI_EMBEDDING_DIMENSION=1536`); real sin key → exit 1 con "Modo IA real incompleto".
  - Release: env `ALLOW_RELEASE_KEY` no habilita → fake/key vacía; `-Pplaybook.ai.allowReleaseKey=true` → real; sin opt-in → fake + warning + key vacía.
  - `:composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL.
  - `feature_list.json` JSON válido; `git diff --check` OK; esquema v7 (`1.sqm`..`6.sqm`) sin `.sqm` nuevos; `AiClient`/`FakeAiClient`/`AiPorts`/`FakeAiPorts`/`AiAvailability`/`App.kt`/`iosApp` sin cambios.
  - Reparación High verificada: `UrlConnectionHttpTransport` usa `withContext(dispatcher)` con `Dispatchers.IO` por defecto e inyectable; `instanceFollowRedirects=false`; test `dispatchesBlockingIoOffTheCallerThread` en verde.
  - Smoke real **no re-ejecutado** (sin key en el worktree; red/costo). Evidencia registrada consistente: `embedding=1536`, `chat=2`, `tts=47232`, `transcribe=26`, `image=601619` (`image/jpeg`), `track=MECHANICS`, `genres=1`.
- Evidence captured: veredicto `accept` y checks re-ejecutados; `feature_list.json`/`PROGRESS.md` actualizados; ADR 0004 `accepted`.
- Known risk or unresolved issue (Low residual): `findProperty` también resuelve `gradle.properties`/`ORG_GRADLE_PROJECT_*` (hardening a sólo `-P` diferido); el test de fallo de red usa `RuntimeException` en vez de `IOException`/`SocketTimeoutException` (cobertura equivalente por catch-all); el smoke real no se repitió en esta sesión. La clave sigue embebida en un APK real (no distribuir).
- Next best step: integrar la rama (rebase sobre `origin/develop`, commit de la feature y merge a `develop` según la cola única) y continuar con las features de la Ola 1.

### Session 057

- Date: 2026-10-19
- Goal: integrar `ai-fake-real-switch` en `develop` con la cola única: rebase, resolución de conflictos y verificación post-rebase.
- Completed:
  - Rebase de `feature/ai-fake-real-switch` sobre `develop` (`35c012a`, con `game-project-persistence` v8, `genres-catalog-ui` y el fix de harness).
  - Conflictos resueltos en `AGENTS.md`, `PROGRESS.md` y `docs/technical-discovery.md`; se conservaron las secciones de las features integradas y la nueva sección de IA real. Sesiones de `ai-fake-real-switch` renumeradas a **055/056/057** para evitar colisión con las Sesiones 052–054 de `develop`.
  - `feature_list.json`: `ai-fake-real-switch` `accepted` con evidencia de validación e integración; `feature_list.json` válido.
  - `docs/adr/0004-ai-real-integration.md`: `accepted`.
  - Commit de la feature: `feature: complete ai-fake-real-switch` (`1998a7f`). Merge **fast-forward** a `develop` (sin push).
- Verification run (post-rebase):
  - `./init.sh` → exit 0 (Android-only, sin red ni dev servers).
  - `PLAYBOOK_AI_MODE=real PLAYBOOK_AI_API_KEY=dummy ./init.sh` → exit 0; debug `AI_MODE=fake`, `AI_API_KEY=""`.
  - `:core:testDebugUnitTest --rerun-tasks` → **76 tests / 0 failures** (incluye `OpenAiAdaptersCheck` 17, `EmbeddingDimensionReconcilerCheck` 4 y los tests de `game-project-persistence`).
  - `:composeApp:testDebugUnitTest -Pplaybook.ai.mode=fake --rerun-tasks` → **6 tests / 0 failures** (`UrlConnectionHttpTransportTest` 3).
  - Build real debug con dummy key → OK (`AI_MODE=real`, `AI_EMBEDDING_DIMENSION=1536`); real sin key → exit 1 con "Modo IA real incompleto".
  - Release: env `ALLOW_RELEASE_KEY` no habilita → fake/key vacía; `-Pplaybook.ai.allowReleaseKey=true` → real; sin opt-in → fake + warning + key vacía.
  - `:composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL.
  - Esquema global **v8** (`1.sqm`..`7.sqm`), sin `.sqm` nuevos de esta feature; diff de 28 archivos; sin secretos; `json.load` y `git diff --check` OK.
  - Post-merge en `develop` (main repo): `CI=true ./init.sh` → exit 0; `:composeApp:testDebugUnitTest -Pplaybook.ai.mode=fake` → **6/0**.
- Evidence captured: verificación post-rebase y evidencia de integración en `feature_list.json`; este bloque.
- Known risk or unresolved issue (Low residual): `findProperty` también resuelve `gradle.properties`/`ORG_GRADLE_PROJECT_*` (hardening a sólo `-P` diferido); el smoke real no se repitió (sin key en el worktree); la clave sigue embebida en un APK real (no distribuir).
- Next best step: `develop` local actualizado con la feature (sin push); las sesiones activas deben rebasear sobre `develop`. Continuar con las features de la Ola 2.

### Session 058

- Date: 2026-10-19
- Goal: planificar `brainstorming-wizard` (rol planner, one-step) y separar la creación de Notas por paso.
- Completed:
  - Rebase de `feature/brainstorming-wizard` sobre `develop` `e5ff8b9` (incluye `genres-catalog-ui` `accepted` y `GenrePicker.kt`).
  - `feature_list.json`: `brainstorming-wizard.depends_on` suma `genres-catalog-ui`; se agrega `brainstorming-note-per-step` (`not_started`; depende de `brainstorming-wizard` y `create-text-note`).
  - `docs/specs/brainstorming-wizard.md` (nuevo): wizard de 9 pasos, sólo ficha, guardado explícito, reutilización de `GenrePicker`, topes 3/3/5, entrada temporal en la lista, sin IA y sin Nota por paso.
  - `AGENTS.md`: cola de features actualizada (siguiente ready `brainstorming-wizard`).
- Verification run:
  - `python3 -c "import json; json.load(open('feature_list.json'))"` → JSON válido.
  - `git diff --check` → sin errores de whitespace.
  - `CI=true ./init.sh` → exit 0 (BUILD SUCCESSFUL, 60 actionable tasks; Android-only, sin red ni dev servers).
- Evidence captured: spec, entradas de `feature_list.json` y este bloque de sesión.
- Files or artifacts updated: `docs/specs/brainstorming-wizard.md`, `feature_list.json`, `AGENTS.md`, `PROGRESS.md`.
- Known risk or unresolved issue: `brainstorming-note-per-step` queda `not_started` y sin spec; no hay tests de UI/E2E persistentes (la spec lo justifica). El implementer debe partir de `develop` con `genres-catalog-ui` mergeada.
- Next best step: revisión del spec por el autor; tras la aprobación, ejecutar `$feature-implementer` para `brainstorming-wizard`.

### Session 059

- Date: 2026-10-19
- Goal: implementar `brainstorming-wizard` (spec `docs/specs/brainstorming-wizard.md`): wizard de 9 pasos para crear/iterar la ficha del Proyecto.
- Completed:
  - `composeApp/.../BrainstormingWizardScreen.kt` (nuevo): 9 pasos (Título, Género, Core Loop, Audiencia, Concepto, Inspiraciones, Fantasía, Mecánicas clave, Fuera de alcance), progreso `Paso X/9`, Atrás/Siguiente, Guardar explícito y Cancelar. Estado inicial desde `initialProject` (clave `updatedAt`), topes 3/3/5, filas removibles, mecánicas con categoría + descripción y `AlertDialog` de error; guarda un `GameProjectDraft` completo.
  - Paso Género reutiliza `GenrePicker` de `genres-catalog-ui`; Inspiraciones/Fuera de alcance usan `OutlinedTextField`; Mecánicas usan chips de `MechanicCategory` con `✓`/borde y validación de categoría faltante.
  - `App`: parámetro `gameProjectRepository`, estado `project`, carga en `LaunchedEffect(refreshKey)`, `NotesDestination.Brainstorming` y `onSave` con `save(draft)` + `reload()`/`goToList()`; `NotesListScreen` suma la fila temporal "Brainstorming".
  - `MainActivity`/`MainViewController`: construyen y pasan `SqlDelightGameProjectRepository`; la llamada iOS pasó a argumentos nombrados. Comentarios de esquema actualizados a v8.
  - Docs: `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `AGENTS.md`, `feature_list.json` y esta sesión.
- Verification run:
  - `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL.
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; 23 suites / 55 tests / 0 failures / 0 errors / 0 skipped.
  - `CI=true ./init.sh` → exit 0 (Android-only, sin red ni dev servers).
  - iOS (no bloqueante): `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - Esquema v8 sin cambios; `.sqm` = `1.sqm`..`7.sqm`; sin cambios en `:core`, build files, `init.sh`, `iosApp` ni `GenrePicker.kt`.
  - Smoke Android (emulator-5556): entrada, 9 pasos/progreso, guardado vacío (`singleton|local|||||`), ficha completa con Géneros/Inspiraciones/Mecánicas/Fuera de alcance en DB, reapertura con precarga, guardado desde el paso 1 preservando el resto, cancelación sin escritura, iteración/reemplazo sin huérfanas y error de mecánica sin categoría. Evidencia en `composeApp/build/smoke-evidence/brainstorming-wizard/` (`01`–`07`).
  - Controles negativos restaurados: (a) sin `save` no hay fila ni navegación; (b) ignorar `initialProject` deja el Título vacío; (c) sin validación no aparece el error y se descarta la mecánica incompleta.
- Evidence captured: arreglo `evidence` de `brainstorming-wizard` en `feature_list.json`; capturas/UI dumps/DB en `composeApp/build/smoke-evidence/brainstorming-wizard/`.
- Files or artifacts updated: `composeApp/.../{BrainstormingWizardScreen.kt (nuevo), App.kt, NotesListScreen.kt}`, `composeApp/.../androidMain/.../MainActivity.kt`, `composeApp/.../iosMain/.../MainViewController.kt`, `docs/specs/brainstorming-wizard.md`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: no hay tests de UI/E2E persistentes (la spec lo justifica); `brainstorming-note-per-step` queda `not_started` y sin spec; el wizard no se abre solo en el primer arranque (`first-run-project-onboarding`).
- Next best step: validación independiente de `brainstorming-wizard` con `$feature-validator`; tras el `accept`, persistir `accepted`, escribir `docs/validations/brainstorming-wizard.md` e integrar.

### Session 060

- Date: 2026-10-19
- Goal: persistir la aceptación independiente de `brainstorming-wizard`, rebasar sobre `develop` y cerrar con commit y PR.
- Completed:
  - `feature_list.json`: `brainstorming-wizard` `passing` → `accepted`; evidencia de la validación independiente (`docs/validations/brainstorming-wizard.md`) y de los comandos re-ejecutados; corregido el claim del smoke para no afirmar 5 Mecánicas persistidas (se verificó el tope 5/5 en UI y el descarte de filas vacías). `brainstorming-note-per-step` sigue `not_started`.
  - `PROGRESS.md`: estado actual, última feature `accepted`, próxima feature ready (`inline-dictation-30s`) y esta sesión de cierre. Las sesiones de `brainstorming-wizard` se renumeraron a **058/059/060** al rebasar sobre `develop`, que ya usaba 055–057 para `ai-fake-real-switch`.
  - `AGENTS.md`: cola de features actualizada (wizard aceptado; siguiente ready `inline-dictation-30s`).
- Verification run:
  - `./gradlew :composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → 25 suites / 76 tests / 0 failures / 0 errors / 0 skipped en la base rebasada (23 suites / 55 tests en la validación original).
  - `CI=true ./init.sh` → exit 0 (Android-only, sin red ni dev servers), re-ejecutado tras el rebase sobre `origin/develop`.
  - `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (iOS congelado, sólo compilación).
  - Sin cambios en `:core`, esquema (v8, `.sqm` 1..7), build files, `init.sh`, `iosApp/**` ni `GenrePicker.kt`.
- Evidence captured: entrada `accepted` en `feature_list.json` y `docs/validations/brainstorming-wizard.md`.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `AGENTS.md`, `docs/validations/brainstorming-wizard.md` (junto con el resto del diff de la feature ya presente en el árbol).
- Known risk or unresolved issue: hallazgos Low no bloqueantes del validador (encabezado del wizard sin `Paso X/9 · <paso>` en una línea, evidencia de mecánicas del smoke del implementer y emulador compartido); sin tests de UI/E2E persistentes.
- Next best step: PR contra `develop`; tras el merge, planificar/implementar `inline-dictation-30s`.
### Session 061 (ai-real-embeddings)

- Date: 2026-10-19
- Goal: implementar `ai-real-embeddings` (spec `docs/specs/ai-real-embeddings.md`) sobre el runtime/flag/wiring ya entregados por `ai-fake-real-switch`: contrato de dimensión, límite de persistencia, integración real con transporte falso y smoke de indexado key-gated.
- Completed:
  - `:core`: `OpenAiAiClient.embed` valida que **cada** vector mida `config.embeddingDimension` y lanza `AiClientException("embedding devolvió dimensión N, se esperaba M")` sin key/headers/body.
  - `:core`: `NoteIndexingService` acepta `expectedDimension: Int? = null` (`require` > 0 o `null`); un mismatch devuelve `IndexingResult.Failed`, registra `AI_INDEXING` y **no** persiste (ni pisa el embedding previo). El default `null` conserva el comportamiento anterior.
  - `:composeApp`: `MainActivity` y `MainViewController` pasan `aiRuntime.embeddingDimension` (8 fake / 1536 real) al servicio; iOS sigue fake.
  - Tests `:core`: `RealEmbeddingIndexingCheck` (commonTest) + runners Android/iOS con `openAiAiRuntime(config 1536, ScriptedTransport)` y SQLDelight in-memory; cubre éxito, mismatch, re-indexado fallido, RAG, Enlaces, red/500 con presupuesto, fallo transitorio y reconciliación 8→1536. `OpenAiAdaptersCheck` suma 2 tests de dimensión; `NoteIndexingCheck` suma guard/default/`require`.
  - `:composeApp`: `RealAiSmokeTest.smokeRealIndexingFeedsRag` (key-gated, fuera del gate) indexa sobre base in-memory y verifica dimensión, `INDEXED` y RAG con fuentes; `sqldelight.sqlite.driver` test-only en el bloque `androidUnitTest`.
  - Docs: `ARCHITECTURE.md`, `CONSTRAINTS.md`, `docs/technical-discovery.md` (sección “AI Real Embeddings Decisions”), `feature_list.json` y este bloque. `AGENTS.md` no cambió (la línea de próxima feature se actualiza al integrar).
- Verification run:
  - `CI=true ./init.sh` → exit 0 (fake, sin red); `PLAYBOOK_AI_MODE=real PLAYBOOK_AI_API_KEY=dummy CI=true ./init.sh` → exit 0 con `AI_MODE=fake` y `AI_API_KEY=""`.
  - `:core:testDebugUnitTest --rerun-tasks` → **79 tests / 0 failures** (base 76 + 3: integración, 2 de adaptador; `NoteIndexingCheck` extendido).
  - `:composeApp:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` → **7 tests / 0 failures** (smoke key-gated omitido).
  - `:core:iosSimulatorArm64Test --rerun-tasks` → **79 tests / 0 failures**; `:composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - Controles negativos (restaurados byte-idénticos, sha256 y `cmp`): (a) sin guard del adaptador → fallan los 2 tests de mismatch; (b) sin límite de persistencia → falla `NoteIndexingAndroidTest`; (c) HTTP 500 cubierto por el Scenario 7 (`PENDING`/presupuesto).
  - Smoke real key-gated (key sólo en `local.properties`, nunca impresa): `AI smoke OK: embedding=1536, chat=2, tts=42624, transcribe=26, image=609063 (image/jpeg), track=MECHANICS, genres=1`; `AI smoke indexing OK: dim=1536, status=INDEXED, sources=1, answerLen=286`; 2/2 tests OK.
  - `git diff --stat` sin paths protegidos; esquema v8 (`1.sqm`..`7.sqm`) sin `.sqm` nuevos; `feature_list.json` con `json.load` OK; sin key en el diff.
- Evidence captured: arreglos `verification`/`evidence` de `ai-real-embeddings` en `feature_list.json`; logs de tests y smoke sanitizado.
- Files or artifacts updated: `core/src/commonMain/kotlin/com/playbook/core/ai/{NoteIndexingService.kt,openai/OpenAiAdapters.kt}`, `composeApp/src/{androidMain,iosMain}/kotlin/com/playbook/app/Main{Activity,ViewController}.kt`, tests nuevos/modificados de `:core`/`:composeApp`, `composeApp/build.gradle.kts` (sólo dep test-only), `ARCHITECTURE.md`, `CONSTRAINTS.md`, `docs/technical-discovery.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el runtime real no sondea conectividad, así que un arranque offline puede agotar el presupuesto y promover a `FAILED` (sonda = follow-up); la reconciliación no recomputa `note_link` (Relacionadas se recomputa al abrir); los tests con transporte falso validan contrato/máquina, no calidad semántica ni disponibilidad; la dimensión activa no se expone en UI/diagnóstico. La feature queda `passing`, pendiente de validación independiente.
- Next best step: validación independiente de `ai-real-embeddings` con `$feature-validator` (spec `docs/specs/ai-real-embeddings.md`). Sin commit ni merge en esta sesión.

### Session 062 (ai-real-embeddings)

- Date: 2026-10-19
- Goal: persistir la aceptación independiente de `ai-real-embeddings`, rebasar sobre `origin/develop`, re-verificar y cerrar con commit y PR.
- Completed:
  - Rebase de `feature/ai-real-embeddings` sobre `origin/develop` `65adc62` (incluye `brainstorming-wizard` aceptado); conflictos resueltos conservando sesiones/evidencia de ambas features.
  - `feature_list.json`: `ai-real-embeddings` `passing` → `accepted` con evidencia de la validación independiente y de la verificación post-rebase.
  - `PROGRESS.md`/`AGENTS.md`: estado actual y siguiente feature ready actualizados.
- Verification run (post-rebase):
  - `CI=true ./init.sh` → exit 0 (Android-only, fake, sin red).
  - `:core:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` → 79 tests / 0 failures.
  - `:composeApp:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` → 7 tests / 0 failures.
  - `:core:iosSimulatorArm64Test --rerun-tasks` → 79 tests / 0 failures; `:composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - Esquema v8 (`1.sqm`..`7.sqm`), sin `.sqm` nuevos; paths protegidos sin cambios.
  - Smoke real key-gated re-ejecutado por el validador (2/2 OK, `dim=1536`, `INDEXED`, `sources=1`, salida sanitizada); no se repitió en la integración para evitar costo/cuota.
- Evidence captured: entrada `accepted` en `feature_list.json`; evidencia del validador; resultados post-rebase en este bloque.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `AGENTS.md`.
- Known risk or unresolved issue: riesgos aceptados documentados (sin sonda de conectividad real; `note_link` se recomputa al abrir Relacionadas; los tests con transporte falso no validan calidad semántica; dimensión activa no expuesta).
- Next best step: PR contra `develop`; tras el merge, seguir con `sketch-thumbnails` (worktree ya en curso) o `inline-dictation-30s` (ambas ready con spec).

### Session 063 (inline-dictation-30s — implementación)

- Date: 2026-10-19
- Goal: implementar `inline-dictation-30s` (spec `docs/specs/inline-dictation-30s.md`) en el worktree `worktree/inline-dictation-30s`, rama `feature/inline-dictation-30s`, rebasada sobre `develop` `ed48a45`.
- Completed:
  - Nuevo `Dictation.kt` (`commonMain`): `DictationUiState` (`Idle`/`Listening(partial, elapsedMillis)`/`Processing`/`Error`), `DICTATION_MAX_MILLIS = 30_000`, `DICTATION_TICK_MILLIS = 100`, `VoiceCaptureError.message()` (movido) y `appendDictation(body, dictated)`.
  - Eliminados `VoiceCaptureScreen.kt`, `VoiceUiState` y `NotesDestination.VoiceCapture`; "Dictar nota" abre `Create` y arranca la sesión.
  - `VoiceTranscriber.start(..., onStarted = {})` aditivo. Android lo emite después de `startListening(...)` (permiso concedido) y lo limpia en `cancel()`; iOS lo emite sólo si `engine.startAndReturnError(...)` es `true` y agrega un **guard de generación** que invalida callbacks tardíos tras `cancel()`.
  - `App`: dueña de `dictation`, `dictatedText`, `pendingAudio` y `dictationJob`; timer de 30s post-`onStarted` (tick 100ms, sin reiniciar con parciales), auto-stop una vez, `stopDictation()` idempotente, reemplazo/conservación de audio pendiente, `attachAudio(noteId)` en los dos guardados de Nueva nota, `cancelNewNote()`, `DisposableEffect(Unit)` a nivel App y guardado durante dictado.
  - `NoteEditorScreen`: panel de dictado sólo en Nueva nota (después de Cuerpo, antes de Track) con "Dictar", progreso/parcial/"Detener", "Transcribiendo…" deshabilitado y error + "Reintentar"; anexado con `LaunchedEffect(dictatedText)` y `canSave` con guard `dictatedText == null`.
  - Docs: `ARCHITECTURE.md` (puerto/adaptadores/UI/orquestación inline), `docs/technical-discovery.md` (nueva "Inline Dictation Decisions" + nota de la pantalla retirada) y `AGENTS.md` (sólo la línea de cola).
- Verification run:
  - `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL; APK debug generado.
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; **25 suites / 76 tests / 0 failures** (sin regresiones; `:core` no cambió).
  - `CI=true ./init.sh` → exit 0 (Android-only, sin dev servers ni simuladores).
  - `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - Smoke Android (emulador API 35 `emulator-5556`): "Dictar nota" abre Nueva nota y muestra `Listening` con barra; el cap arranca al conceder el permiso (diálogo abierto >1 min → 4s al conceder); permiso denegado → `Error(PERMISSION_DENIED)` sin timer; cancelar durante `Listening` → lista, 0 notas/0 adjuntos y `files/audio` vacío; Guardar durante `Listening` → Nota con el `body` y sin Adjunto/archivo.
  - Auto-stop: se avanzó el reloj del emulador +45s con una sesión `Listening` (no hay entrada de voz real disponible): la UI pasó a `Processing` ("Transcribiendo…", evidencia `43-autostop-after-clock.png`) y luego a `NO_MATCH`, sin crear Nota ni audio; reloj restaurado.
  - `git status`/`git diff`: sólo `composeApp` + docs de la feature; sin cambios en `:core`, esquema/`.sqm`, build files, deps, `init.sh` ni `iosApp`.
- Evidence captured: `composeApp/build/smoke-evidence/inline-dictation-30s/` (`03`, `04`, `32`, `33`, `34`, `41`, `42b`, `43`) y entradas de `feature_list.json`.
- Files or artifacts updated: `composeApp/src/commonMain/kotlin/com/playbook/app/Dictation.kt` (nuevo), `App.kt`, `NoteEditorScreen.kt`, `VoiceCaptureScreen.kt` (borrado), `composeApp/src/commonMain/kotlin/com/playbook/app/voice/VoiceTranscriber.kt`, `composeApp/src/androidMain/kotlin/com/playbook/app/voice/AndroidVoiceTranscriber.kt`, `composeApp/src/iosMain/kotlin/com/playbook/app/voice/IosVoiceTranscriber.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el STT real no es inyectable de forma determinista en este entorno; no se observó una transcripción real con texto/audio (los escenarios de audio real y reemplazo se cubren por diseño/revisión y por los tests aceptados de `voice-attachment-storage`). El auto-stop se ejerció avanzando el reloj del emulador. Los emuladores están compartidos y el API 34 propio sufrió un ANR/crash de entorno; el smoke válido se hizo en `emulator-5556` (API 35). Sin watchdog para `Processing` (límite declarado).
- Next best step: validación independiente (veredicto `accept` en `docs/validations/inline-dictation-30s.md`); cierre e integración en Session 064.

### Session 064 (inline-dictation-30s — integración)

- Date: 2026-10-19
- Goal: cerrar `inline-dictation-30s` (validación independiente `accept`), rebasar sobre `origin/develop` `a00c72d` (con `brainstorming-wizard` y `ai-real-embeddings` integrados), persistir `accepted` y actualizar el PR contra `develop`.
- Completed:
  - Primer rebase sobre `origin/develop` `65adc62` (`brainstorming-wizard`) y rebase final sobre `a00c72d` (`ai-real-embeddings`). Conflictos resueltos preservando todas las features en `AGENTS.md`, `PROGRESS.md` y `docs/technical-discovery.md`; `ARCHITECTURE.md` y `feature_list.json` auto-mergeados conservan `brainstorming-wizard`/`ai-real-embeddings` y el dictado inline. `App.kt` conserva `GameProjectRepository`/`Brainstorming` y el dictado inline, sin `VoiceCapture`.
  - Sesiones de `inline-dictation-30s` renumeradas a **063** (implementación) y **064** (integración) para no colisionar con 061/062 de `ai-real-embeddings`.
  - `feature_list.json`: `inline-dictation-30s` `passing` → `accepted`, con evidencia de la validación independiente (`docs/validations/inline-dictation-30s.md`) y del gate post-rebase final.
  - `AGENTS.md`: cola actualizada (`inline-dictation-30s` y `ai-real-embeddings` aceptadas; `brainstorming-note-per-step` sigue `not_started` sin spec; siguiente ready con spec `sketch-thumbnails`, en curso por otra sesión).
  - Commit final único `feature: complete inline-dictation-30s` (amend) y force-push con lease al PR contra `develop`: https://github.com/yavik14/learn-aidevexpert-gamebook/pull/41.
- Verification run (post-rebase `a00c72d`):
  - `./gradlew :composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL (56/56 tasks).
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → 26 suites / 79 tests / 0 failures / 0 errors / 0 skipped.
  - `CI=true ./init.sh` → exit 0.
  - `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL.
  - `git diff --check origin/develop...HEAD` → OK; sin cambios de esta feature en `:core`, esquema (v8, `.sqm` 1..7), build files, deps, `init.sh` ni `iosApp`.
- Evidence captured: veredicto `accept` en `docs/validations/inline-dictation-30s.md`, entrada `accepted` en `feature_list.json` y este bloque.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `AGENTS.md` (más el diff de la feature validado).
- Known risk or unresolved issue: límites declarados del smoke (STT real no inyectable; auto-stop ejercido avanzando el reloj; sin watchdog de `Processing`; no se observó transcripción real con texto+audio). Los riesgos de merge con `brainstorming-wizard` y `ai-real-embeddings` quedaron resueltos en estos rebases.
- Next best step: merge del PR en `develop`; luego planificar/implementar `sketch-thumbnails` (siguiente ready con spec, ya en curso) o `brainstorming-note-per-step` cuando tenga spec.

### Session 065 (sketch-thumbnails — implementación)

- Date: 2026-10-19
- Goal: implementar `sketch-thumbnails` (spec `docs/specs/sketch-thumbnails.md`): miniatura del boceto en la lista y el GDD, con el store de Adjuntos reubicado en `:core`.
- Completed:
  - `:core`: nuevo `com.playbook.core.storage` con `StoredImage`, `AttachmentFileStore` (`saveImage`/`read`/`delete`) y `expect fun attachmentFileStore(baseDir)`; actuals Android/JVM (`java.io.File`, `canonicalFile` + prefijo) e iOS (`NSFileManager`/`NSData`, normalización de `.`/`..` y separadores) con guard de path traversal. `read` devuelve bytes exactos o `null`.
  - Tests: `AttachmentFileStoreCheck` (Escenarios 1–4) + `AttachmentFileStoreAndroidTest` y `AttachmentFileStoreIosTest`; los runners siembran archivos fuera de la base para que el control de traversal sea real.
  - `:composeApp`: `ImageCapture.{kt,android.kt,ios.kt}` delegan en el store de `:core`; `rememberAttachmentFileStore()` sólo calcula `baseDir` (`filesDir` Android, `Application Support` iOS). Se eliminaron `StoredImage`/`AttachmentFileStore` locales; las rutas guardadas siguen siendo `images/<uuid>.jpg`.
  - UI: `App` calcula `imagePaths` (sólo `IMAGE`, primero por Nota en el orden `created_at ASC, id ASC`) + `loadThumbnail`; `NotesListScreen`/`GddScreen` reciben ambas props **además** de `attachmentKinds`/`attachmentCounts`. `NoteThumbnail.kt` (nuevo) con `produceState` + `Dispatchers.Default`, `Image` 72 dp, recorte `rounded.md` de 16 dp, `ContentScale.Crop` y espaciado interno (sin hueco cuando falla la carga); integrado en `NoteCard` y `GddNoteCard` preservando cuerpo/chips/Géneros/status y el chip "Imagen" del GDD.
  - Docs: `ARCHITECTURE.md`, `docs/technical-discovery.md` ("Thumbnail Decisions"), `docs/risks-and-open-questions.md`, `AGENTS.md` (sólo la cola), `feature_list.json` (`passing`) y este bloque.
- Verification run:
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; 26 suites / 77 tests / 0 failures / 0 errors / 0 skipped; `AttachmentFileStoreAndroidTest` 1 test en verde.
  - `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL.
  - `CI=true ./init.sh` → exit 0 (Android-only, sin red ni dev servers).
  - iOS (no bloqueante): `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL; `./gradlew :core:iosSimulatorArm64Test` → BUILD SUCCESSFUL, 26 suites / 77 tests / 0 failures; `AttachmentFileStoreIosTest` 1 test en verde.
  - Controles negativos (Android/JVM, restaurados byte-idénticos con SHA-256 y suite core re-verificada en verde): (a) `read` siempre `null` → falla Scenario 1; (b) `delete` `true` sin borrar → falla Scenario 3 (`tras delete, read debe devolver null`); (c) sin guard de path traversal → falla Scenario 4 (`read no debe escapar de baseDir`).
  - `git diff --name-only` vacío para esquema/`.sqm`, `init.sh`, `iosApp`, build files y version catalog; `.sqm` siguen `1.sqm`..`7.sqm` (esquema v8) y no hay deps nuevas.
  - Smoke manual Android **no ejecutado**: `adb`/`emulator` no están disponibles en el entorno (no se fabrica evidencia).
- Evidence captured: `feature_list.json` (`sketch-thumbnails` `passing`) y las salidas de tests/build/gate; controles negativos documentados.
- Files or artifacts updated: `core/src/{commonMain,androidMain,iosMain}/.../storage/AttachmentFileStore*.kt` (nuevos), `core/src/{commonTest,androidUnitTest,iosTest}/.../storage/AttachmentFileStore*Test*.kt` (nuevos), `composeApp/.../{ImageCapture.kt,ImageCapture.android.kt,ImageCapture.ios.kt,App.kt,NotesListScreen.kt,GddScreen.kt,NoteThumbnail.kt}`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: se decodifica a tamaño completo (cacheo/redimensionado diferido); sin test de UI/E2E y smoke Android no ejecutado por falta de emulador; varios Adjuntos `IMAGE` por Nota muestran el primero. El `actual` iOS sólo se verifica por compilación + test simulador, no por paridad funcional (iOS congelado).
- Next best step: validación independiente de `sketch-thumbnails` con `$feature-validator` (incluye smoke Android si hay emulador); no commitear todavía.

### Session 066 (sketch-thumbnails — reparación Low)

- Date: 2026-10-19
- Goal: reparar los dos hallazgos Low de la validación independiente `accept` de `sketch-thumbnails` sin ampliar alcance; mantener la feature en `passing`.
- Completed:
  - `NoteThumbnail.kt`: `THUMBNAIL_CORNER` pasa de 8 dp a **16 dp** (`rounded.md` de `DESIGN.md`); el `Spacer` de 8 dp se mueve **dentro** del composable (`Column { Image; Spacer }`), por lo que una imagen faltante o no decodificable no deja hueco ni espaciado.
  - `NotesListScreen.kt`/`GddScreen.kt`: se quitaron los `Spacer(8.dp)` posteriores a `NoteThumbnail`; se conservan 72 dp, recorte rounded, `ContentScale.Crop`, cuerpo/chips/Géneros/status y el chip "Imagen" del GDD.
  - Spec `sketch-thumbnails`: Technical Approach corregido (`RoundedCornerShape(16.dp)` + espaciado interno) sin tocar escenarios, non-goals ni alcance.
  - Docs: referencias de 8 dp → 16 dp en `ARCHITECTURE.md`, `docs/technical-discovery.md`, `PROGRESS.md` y `feature_list.json`; en `docs/risks-and-open-questions.md` se documenta el guard iOS léxico (no resuelve symlinks) como riesgo residual aceptado (rutas generadas por la app, single-user), sin cambiar el contrato de `read`/`delete`.
- Verification run:
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; 26 suites / 77 tests / 0 failures / 0 errors / 0 skipped (`AttachmentFileStoreAndroidTest` 1/1).
  - `./gradlew :composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
  - `CI=true ./init.sh` → exit 0 (Android-only, sin red ni dev servers).
  - iOS no bloqueante: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL; `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL, 26 suites / 77 tests / 0 failures (`AttachmentFileStoreIosTest` 1/1).
  - `git diff --check` OK; sin cambios en `*.sq`/`*.sqm`, `Attachment.sq`, build files, `gradle/libs.versions.toml`, `gradle.properties`, `init.sh` ni `iosApp/**`; `.sqm` siguen `1.sqm`..`7.sqm`; sin deps nuevas ni secrets.
  - Smoke Android no ejecutado: `adb`/`emulator` no están disponibles (límite real declarado, sin evidencia fabricada).
- Evidence captured: esta sesión, actualizaciones de docs y entrada `sketch-thumbnails` de `feature_list.json` (sigue `passing`, con evidencia del repair).
- Files or artifacts updated: `composeApp/.../NoteThumbnail.kt`, `composeApp/.../NotesListScreen.kt`, `composeApp/.../GddScreen.kt`, `docs/specs/sketch-thumbnails.md`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: cacheo/redimensionado diferido; guard iOS léxico aceptado; sin smoke visual Android por falta de emulador.
- Next best step: re-validación independiente de `sketch-thumbnails` (validator); no commitear todavía.

### Session 067 (sketch-thumbnails — cierre, rebase e integración)

- Date: 2026-10-19
- Goal: cerrar `sketch-thumbnails` tras la re-validación independiente `accept`, rebasar sobre `origin/develop`, persistir `accepted`, commitear y abrir el PR.
- Completed:
  - Rebase de `feature/sketch-thumbnails` sobre `origin/develop` `a0d556f` (incluye `ai-real-embeddings` e `inline-dictation-30s`). Conflictos resueltos preservando ambas features y el cierre de `sketch-thumbnails`: `App.kt` conserva el dictado inline + `imagePaths`/`loadThumbnail`; `AGENTS.md`/`PROGRESS.md`/`docs/technical-discovery.md` combinan las secciones de las tres features. Sin merge commits.
  - `feature_list.json`: `sketch-thumbnails` `passing` → `accepted`, con evidencia de la re-validación (`accept`), de los dos Low reparados y de la verificación post-rebase.
  - `docs/validations/sketch-thumbnails.md` (nuevo): veredicto `accept`, repairs cerrados (16 dp, espaciado condicionado a la imagen, riesgo iOS documentado), comandos/resultados y límites.
  - `PROGRESS.md`/`AGENTS.md`: estado actual y cola actualizados; siguiente ready con spec `gdd-document-hybrid`.
- Verification run (post-rebase `a0d556f`):
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; **27 suites / 80 tests / 0 failures / 0 errors / 0 skipped**.
  - `./gradlew :composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
  - `CI=true ./init.sh` → exit 0 (Android-only, sin red ni dev servers).
  - `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL.
  - `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; **27 suites / 80 tests / 0 failures** (no bloqueante).
  - `git diff --check` OK; sin cambios en `*.sq`/`*.sqm` (`1.sqm`..`7.sqm`, esquema v8), `Attachment.sq`, build files, `gradle/libs.versions.toml`, `gradle.properties`, `init.sh` ni `iosApp/**`; sin deps nuevas ni secrets.
  - Smoke visual Android no ejecutado: `adb`/`emulator` no disponibles (límite real declarado, sin evidencia fabricada).
- Evidence captured: `docs/validations/sketch-thumbnails.md`, entrada `accepted` en `feature_list.json` y este bloque.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `AGENTS.md`, `docs/validations/sketch-thumbnails.md`.
- Known risk or unresolved issue: cacheo/redimensionado diferido; guard iOS léxico aceptado; sin smoke visual Android por falta de emulador.
- Next best step: PR contra `develop`; la integración la hace la sesión integradora. Luego `gdd-document-hybrid`.

### Session 068 (coordinación: sync con origin/develop y estado en vuelo)

- Date: 2026-10-19
- Goal: actualizar `develop` con `origin/develop` y reconciliar el estado durable con las features en vuelo.
- Completed:
  - `develop` actualizado de `a0d556f` a `82ef4ec` (`feature: complete sketch-thumbnails`) mediante fast-forward, preservando los worktrees activos.
  - `PROGRESS.md`: última feature `accepted` corregida a `sketch-thumbnails`; bloque **En vuelo** con `brainstorming-note-per-step` y `brainstorming-ai-suggestions` (ambas `in_progress`, con spec y cambios sin commit).
  - `AGENTS.md`: cola actualizada; siguiente ready sin dependencias abiertas y sin worktree = `first-run-project-onboarding` (sin spec; arranca con planner).
  - `feature_list.json` **sin cambios**: los estados en vuelo viven en sus ramas/worktrees y `accepted` se persiste al integrar en `develop`.
- Verification run:
  - `origin/develop` = `82ef4ec`; worktrees activos `brainstorming-note-per-step` y `brainstorming-ai-suggestions`.
  - `python3 -c "import json; json.load(open('feature_list.json'))"` → JSON válido.
  - `git diff --check` → sin errores de whitespace.
  - `CI=true ./init.sh` → exit 0 (Android-only, sin red ni dev servers).
- Evidence captured: estado verificado contra `origin/develop` y ambos worktrees.
- Files or artifacts updated: `PROGRESS.md`, `AGENTS.md`.
- Known risk or unresolved issue: ambas ramas parten de bases anteriores a `82ef4ec` (`a00c72d` y `a0d556f`) y tienen cambios sin commit; deben rebasear sobre `develop` al integrarse. `first-run-project-onboarding` aún no tiene spec.
- Next best step: revisar los specs en vuelo; validar/implementar según corresponda y planificar `first-run-project-onboarding` cuando haya slot libre.

### Session 069 (brainstorming-ai-suggestions — implementación)

- Date: 2026-10-19
- Goal: implementar `brainstorming-ai-suggestions` (spec `docs/specs/brainstorming-ai-suggestions.md`) en el worktree `worktree/brainstorming-ai-suggestions`, rama `feature/brainstorming-ai-suggestions`, desde `origin/develop` `a0d556f` (rebasada a `6db4d38` en el cierre).
- Completed:
  - `:core`: nuevo `BrainstormingSuggestions.kt` con `BrainstormingStep`, `BrainstormingSuggestion`, `Success/Failed/InvalidResponse` y `BrainstormingSuggestionService` (prompt con marcador + `STEP=`, contexto acotado del `GameProjectDraft`, parsing con dedupe/filtro contra el borrador, parcial y límite 2).
  - `FakeAiClient.generate`: rama determinista que devuelve 2 líneas parseables ante el marcador (mecánicas con categoría); el resto conserva `[fake] ...`.
  - `:composeApp`: `BrainstormingWizardScreen` recibe `suggestionService`, con estados idle/loading/loaded/error, botón `Sugerir`, tarjetas con `Usar`/`Agregar`/`Ignorar`, parcial, dedupe, cancelación y limpieza al navegar. `App` recibe `aiClient`; `MainActivity`/`MainViewController` pasan el runtime (iOS fake).
  - Tests `:core` (`BrainstormingSuggestionCheck` + runners Android/iOS): feliz, fake, parcial/inválida, fallo, dedupe/filtro, mecánicas/truncado y contexto (7 tests en Android).
  - Docs: `ARCHITECTURE.md`, `docs/technical-discovery.md`, `DESIGN.md`, `docs/risks-and-open-questions.md`, `AGENTS.md`, `feature_list.json` y este bloque.
- Verification run:
  - `:core:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` → **27 suites / 86 tests / 0 failures / 0 errors / 0 skipped** (7 nuevos).
  - `:core:iosSimulatorArm64Test --rerun-tasks` → **27 suites / 86 tests / 0 failures** (7 nuevos iOS).
  - `:composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
  - `CI=true ./init.sh` → exit 0 (Android-only, fake, sin red ni dev servers).
  - `:composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL (no bloqueante).
  - Esquema v8 (`PRAGMA user_version=8`); sin `.sqm` nuevos ni cambios en `GameProjectRepository`/`init.sh`/build files/`iosApp`.
  - Smoke Android (API 35, fake; evidencia en `composeApp/build/smoke-evidence/brainstorming-ai-suggestions/`): 2 tarjetas en Título, Usar/Ignorar, parcial por dedupe (1 idea), Género (chip), Inspiraciones (2/3), Mecánicas (categoría + descripción), limpieza al navegar y guardado en `game_project` + `game_project_genre` + `game_project_key_mechanic`; sin tablas nuevas.
  - Controles negativos restaurados byte-idénticos: (a) quitar dedupe/filtro → falla `deduplicatesAndFiltersDraftValues`; (b) aplicar al pedir → el Título se auto-rellena; (c) quitar `resetSuggestions` de `goToStep` → las tarjetas persisten en el paso siguiente.
- Evidence captured: arreglo `evidence` de `brainstorming-ai-suggestions` en `feature_list.json`; UI dumps/capturas y DB en `composeApp/build/smoke-evidence/brainstorming-ai-suggestions/`.
- Files or artifacts updated: `core/.../ai/BrainstormingSuggestions.kt` (nuevo), `core/.../ai/FakeAiClient.kt`, `core/src/commonTest/.../BrainstormingSuggestionCheck.kt` (nuevo), `core/src/{androidUnitTest,iosTest}/.../BrainstormingSuggestion*Test.kt` (nuevos), `composeApp/.../BrainstormingWizardScreen.kt`, `App.kt`, `MainActivity.kt`, `MainViewController.kt`, `docs/specs/brainstorming-ai-suggestions.md`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `DESIGN.md`, `docs/risks-and-open-questions.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: la calidad/formato real del proveedor no se verifica con fake (queda para `ai-real-chat`); no hay harness UI/E2E (smoke manual); el estado de error de UI se cubre por tests del core con cliente que falla, no por smoke.
- Next best step: validación independiente de `brainstorming-ai-suggestions` con `$feature-validator` (spec `docs/specs/brainstorming-ai-suggestions.md`). Sin commit ni merge en esta sesión.

### Session 070 (brainstorming-ai-suggestions — cierre e integración)

- Date: 2026-10-19
- Goal: persistir la validación independiente `accept`, rebasar sobre `origin/develop`, re-verificar, commitear, pushear y abrir el PR contra `develop`.
- Completed:
  - Rebase de `feature/brainstorming-ai-suggestions` sobre `origin/develop` `6db4d38` (incluye `sketch-thumbnails`). Conflictos sólo en `AGENTS.md` y `PROGRESS.md`, resueltos preservando la cola/estado de `sketch-thumbnails` y el cierre de `brainstorming-ai-suggestions`; sin merge commits.
  - `feature_list.json`: `brainstorming-ai-suggestions` `passing` → `accepted`, con evidencia del veredicto independiente (`docs/validations/brainstorming-ai-suggestions.md`) y de la verificación post-rebase.
  - `AGENTS.md`/`PROGRESS.md`: cola y estado actualizados (sólo `brainstorming-note-per-step` queda en vuelo; siguiente ready `first-run-project-onboarding`).
  - Hallazgo Low del validador (el dedupe no colapsa espacios internos del borrador) documentado como follow-up; no se amplió el alcance en esta sesión.
- Verification run (post-rebase `6db4d38`):
  - `./gradlew :core:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL; **28 suites / 87 tests / 0 failures / 0 errors / 0 skipped** (7 nuevos de la feature).
  - `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; **28 suites / 87 tests / 0 failures**.
  - `./gradlew :composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
  - `CI=true ./init.sh` → exit 0 (Android-only, fake, sin red ni dev servers).
  - `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL.
  - Smoke Android (emulator-5556, API 35, fake): Sugerir → 2 tarjetas; Usar/Ignorar; navegar y volver sin tarjetas; Guardar; `PRAGMA user_version=8`; `game_project` con `Idea de prueba A` y sin tablas de sugerencias. Evidencia en `composeApp/build/smoke-evidence/brainstorming-ai-suggestions/postrebase-*`.
  - Sin cambios en esquema/`.sqm`, `GameProjectRepository`, build files, `init.sh` ni `iosApp`; sin secrets en el diff.
- Evidence captured: veredicto `accept`, entrada `accepted` en `feature_list.json` y este bloque.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `AGENTS.md`, `docs/validations/brainstorming-ai-suggestions.md` (más el diff validado de la feature).
- Known risk or unresolved issue: el dedupe no colapsa espacios internos del borrador (Low, follow-up); no hay harness UI/E2E; la calidad/formato del proveedor real queda para `ai-real-chat`.
- Next best step: abrir el PR contra `develop`; la integración la hace la sesión integradora. Luego `first-run-project-onboarding`.
### Session 071 (first-run-project-onboarding — implementación)

- Date: 2026-10-19
- Goal: implementar `docs/specs/first-run-project-onboarding.md` (onboarding saltable del Proyecto en el primer arranque) sin ampliar alcance.
- Completed:
  - `FirstRunOnboardingScreen.kt` (nuevo): oferta full-screen con "Tu Proyecto de Juego", "Iniciar Brainstorming" y "Saltar"; `FirstRunLoading` con `CircularProgressIndicator` y texto accesible. Tokens Material 3, sin artefacto de diseño nuevo.
  - `App.kt`: `ProjectLoadState` (`Loading`/`Missing`/`Loaded`), lectura del singleton en el `LaunchedEffect(refreshKey)` sin volver a `Loading` en reloads, gate antes del `when(destination)` y derivación de `project` para el wizard.
  - Ambas acciones crean primero el singleton vacío (`GameProjectDraft(owner = LOCAL_OWNER_ID)`) con guard `onboardingBusy` + `try/finally`; aceptar navega a `Brainstorming` y saltar a `List`. Un fallo registra `STORAGE`/`STORAGE_FAILED`, muestra `AlertDialog` ("No se pudo crear el Proyecto" / "Intentá de nuevo." / "Aceptar") y no navega.
  - Sin flag persistido: la existencia de `game_project` es la señal; con Proyecto no se reofrece. Sin cambios en `:core`, esquema v8, raíces, `NotesListScreen`, wizard, build files, `init.sh` ni `iosApp/**`.
  - Docs: `ARCHITECTURE.md` (ProjectLoadState/gate/acciones), `DESIGN.md` (Core Screens), `docs/technical-discovery.md` (decisiones), `AGENTS.md` (sólo cola), `feature_list.json` (`passing` + evidencia) y este bloque.
- Verification run:
  - `./gradlew :composeApp:assembleDebug --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL.
  - `./gradlew :core:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL; 27 suites / 80 tests / 0 failures / 0 errors / 0 skipped.
  - `CI=true ./init.sh` → exit 0 (Android-only, IA fake, sin red ni dev servers).
  - iOS no bloqueante: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL.
  - Smoke Android real (emulator-5556): `pm clear` + arranque mostró la oferta con `game_project` en 0; "Saltar" creó `singleton|local` con `created_at == updated_at` y hijas 0/0/0; relanzar no reofreció. `pm clear` + "Iniciar Brainstorming" abrió `Paso 1/9`, la fila ya existía antes de navegar y "Cancelar" volvió a la lista; relanzar no reofreció. Aceptar + "Guardar" actualizó la misma fila con título `SmokeProj`, `owner`/`created_at` preservados y `updated_at` avanzado. Base sembrada con una Nota y sin Proyecto: la oferta apareció, "Saltar" creó el Proyecto y la Nota siguió visible.
  - Control negativo (a): se forzó temporalmente el fallo de `save`; apareció el diálogo, la oferta permaneció y `game_project` siguió en 0. Código restaurado y smoke re-verificado en verde.
  - `git diff --check` OK; sin cambios en `*.sq`/`*.sqm` (`1.sqm`..`7.sqm`), `:core`, raíces, build files, `gradle/libs.versions.toml`, `init.sh` ni `iosApp/**`.
- Evidence captured: resultados de build/tests/gate/link iOS y smoke Android (UI dumps/consultas SQL) en este bloque y en `feature_list.json`.
- Files or artifacts updated: `composeApp/src/commonMain/kotlin/com/playbook/app/FirstRunOnboardingScreen.kt` (nuevo), `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt`, `ARCHITECTURE.md`, `DESIGN.md`, `docs/technical-discovery.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el smoke de doble tap no se ejecutó de forma aislada; el guard `onboardingBusy` + upsert del repositorio están cubiertos por revisión y `GameProjectCheck`. El estado `Loading` no se capturó visualmente porque la lectura local es instantánea; su contrato se verificó por revisión y compilación. `get()` que lanza sigue fuera de alcance (fallo local app-wide). Sin tests de UI/E2E persistentes; la verificación de UI es smoke manual.
- Next best step: validación independiente de `first-run-project-onboarding` con `$feature-validator`; no commitear todavía.

### Session 072 (first-run-project-onboarding — cierre e integración)

- Date: 2026-10-20
- Goal: persistir la aceptación de `first-run-project-onboarding`, sincronizar con `origin/develop`, re-verificar, commitear y abrir el PR sin mergear.
- Completed:
  - Validación independiente `accept` (`$feature-validator`), sin bloqueantes. Hallazgos Low no bloqueantes: un doble tap puede abrir el editor tras navegar, el estado `Loading` no se capturó visualmente y no hay UI/E2E persistente.
  - `feature_list.json`: `first-run-project-onboarding` `passing` → `accepted`, con evidencia de la validación, build/tests/gate/iOS link y smoke Android en `emulator-5558`.
  - Rebase sobre `origin/develop` `66611ec` (incluye `brainstorming-ai-suggestions`). Conflictos en `AGENTS.md`, `ARCHITECTURE.md`, `PROGRESS.md` y `docs/technical-discovery.md`, resueltos preservando ambas features; `App.kt` auto-merge conservando `aiClient`/`suggestionService` y el onboarding. Sin merge commits.
  - `PROGRESS.md`/`AGENTS.md`: estado, cola y sesiones de cierre actualizados; sin cambios de alcance ni de código de producto.
- Verification run (pre-rebase, base `6db4d38`):
  - `./gradlew :composeApp:assembleDebug --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL.
  - `./gradlew :core:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` → 27 suites / 80 tests / 0 failures / 0 errors / 0 skipped.
  - `CI=true ./init.sh` → exit 0.
  - `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL.
- Verification run (post-rebase, `origin/develop` `66611ec`):
  - `./gradlew :composeApp:assembleDebug --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL.
  - `./gradlew :core:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` → 28 suites / 87 tests / 0 failures / 0 errors / 0 skipped.
  - `CI=true ./init.sh` → exit 0.
  - `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL.
  - Smoke de validación en `emulator-5558`: oferta, saltar, aceptar+cancelar, relanzar sin oferta, aceptar+Guardar, Notas sin Proyecto y error forzado.
- Evidence captured: veredicto `accept`, resultados pre/post-rebase de build/tests/gate/link iOS y smoke de validación; detalle en `feature_list.json` y en las Sessions 071/072.
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `AGENTS.md`.
- Known risk or unresolved issue: los tres Low de la validación (doble tap, `Loading` no capturado, sin UI/E2E persistente); `get()` que lanza sigue fuera de alcance.
- Next best step: push de la rama y PR contra `develop`; la integración la hace la sesión integradora. No mergear.
### Session 073 (brainstorming-note-per-step — cierre e integración)

- Date: 2026-10-20
- Goal: persistir `accepted`, rebasar sobre `origin/develop`, re-verificar y abrir el PR.
- Completed:
  - `feature_list.json`: `brainstorming-note-per-step` `passing` → `accepted` con la evidencia de la re-validación independiente `accept` (§11 de `docs/validations/brainstorming-note-per-step.md`).
  - `PROGRESS.md`/`AGENTS.md`: estado/cola actualizados (38 accepted, 10 not_started, 1 blocked); la feature ya no figura en vuelo ni pendiente.
  - Commit `feature(brainstorming): guarda cada paso como Nota` y rebase final sobre `origin/develop` `0bcb487` (incluye `brainstorming-ai-suggestions` y `first-run-project-onboarding`); conflictos resueltos conservando las tres features.
- Verification run (post-rebase):
  - `:core:testDebugUnitTest --rerun-tasks` → 29 suites / 89 tests / 0 failures.
  - `:composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL; `CI=true ./init.sh` → exit 0.
  - `:composeApp:linkDebugFrameworkIosSimulatorArm64` y `:core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL.
  - Esquema v8 (`1.sqm`..`7.sqm`), sin `.sqm` nuevo; paths protegidos sin cambios.
- Evidence captured: verificación post-rebase y PR https://github.com/yavik14/learn-aidevexpert-gamebook/pull/45 (no mergeado).
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `AGENTS.md`.
- Known risk or unresolved issue: ninguno funcional; el merge a `develop` queda para la sesión integradora.
- Next best step: PR contra `develop`; tras el merge, seguir con `gdd-export-hybrid`.

### Session 074 (gdd-document-hybrid — implementación)

- Date: 2026-10-20
- Goal: implementar `gdd-document-hybrid` (spec `docs/specs/gdd-document-hybrid.md`) en el worktree `worktree/gdd-document-hybrid`, rama `feature/gdd-document-hybrid`, base `origin/develop` `66611ec`.
- Completed:
  - `:core`: nuevo `GddDocument.kt` con `GddProjectField`, `GddProjectBlock`, `GddDocumentSection`, `GddDocument` y `buildGddDocument(project, notes)`: ficha en orden fijo (`Géneros`, `Core Loop`, `Audiencia`, `Concepto`, `Inspiraciones`, `Fantasía`, `Mecánicas clave`, `Fuera de alcance`), sólo campos no vacíos, listas normalizadas con `trim` y orden preservado, mecánicas `Categoría · descripción` (capitalizadas), `Título` fuera de los campos y secciones reutilizando `buildGdd` (`mecánicas`/`personajes`/`historia`/`Sin track`).
  - `:composeApp`: `GddScreen` recibe `project`, calcula `buildGddDocument` con `remember(project, notes)`, muestra cabecera fija `GDD — <título>` (o `GDD`) + total, ficha en `Card` destacada con labels/valores verticales, estados “Proyecto sin datos adicionales”/“Todavía no hay Proyecto”, secciones y Notas scrolleando juntas, y placeholders sin Notas; se conservan conteos, “Sin notas en este track”, chips, miniatura, apertura de Nota, “Volver” y snackbar. `App` pasa `project = project`.
  - “Compartir”/`buildGddMarkdown(notes, now)` y `GddSharer` quedan **sólo con Notas** (sin ficha; eso es `gdd-export-hybrid`).
  - Tests: `GddDocumentCheck.verifyGddDocument()` (escenarios 1–7) + `GddDocumentAndroidTest`/`GddDocumentIosTest`.
  - Docs: `ARCHITECTURE.md`, `docs/technical-discovery.md`, `feature_list.json`, `PROGRESS.md`, `AGENTS.md`.
- Verification run:
  - `ANDROID_HOME=… ./gradlew :core:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL; **29 suites / 88 tests / 0 failures / 0 errors / 0 skipped** (`GddDocumentAndroidTest` 1/1; `GddProjectionAndroidTest`/`GddMarkdownAndroidTest` 1/1).
  - `ANDROID_HOME=… ./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; **29 suites / 88 tests / 0 failures** (`GddDocumentIosTest` 1/1).
  - `ANDROID_HOME=… ./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL; `ANDROID_HOME=… CI=true ./init.sh` → exit 0; `ANDROID_HOME=… ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - `Schema.version == 8`; `.sqm` `1.sqm`..`7.sqm`; `git diff --name-only` vacío para esquema, repositorios, `GddProjection.kt`/`GddMarkdown.kt`, build files, `gradle/`, `init.sh` e `iosApp`; sin dependencias nuevas.
  - Controles negativos (reproducidos y restaurados byte-idénticos, SHA-256 `938662c9…`): (a) invertir el orden de un campo, (b) incluir un campo vacío, (c) duplicar una Nota, (d) cambiar un título de sección y (e) dejar la categoría de mecánica en minúsculas → `GddDocumentAndroidTest` falla en los cinco; suite final re-verificada en verde.
  - Smoke Android (emulator-5558, API 34, fake, APK SHA-256 `b7edfc61…`; evidencia en `composeApp/build/smoke-evidence/gdd-document-hybrid/`): `01-empty` sin Proyecto ni Notas (Compartir deshabilitado), `03` documento completo con `GDD — Crónica de Ceniza`, 5 notas y ficha en orden, `04`/`05` secciones/conteos/chips/“Imagen”/miniatura/`Sin track`, `06`–`08` abrir Nota y Volver, `09` Proyecto vacío sólo título, `10` sin Proyecto con Notas. Datos QA limpiados con `pm clear`.
- Evidence captured: entrada `passing` de `gdd-document-hybrid` en `feature_list.json` (con comandos/resultados y evidencia de smoke) y este bloque; UI dumps/capturas en `composeApp/build/smoke-evidence/gdd-document-hybrid/` (ignorado por git).
- Files or artifacts updated: `core/.../gdd/GddDocument.kt` (nuevo), `core/src/commonTest/.../gdd/GddDocumentCheck.kt` (nuevo), `core/src/{androidUnitTest,iosTest}/.../gdd/GddDocument*Test.kt` (nuevos), `composeApp/.../GddScreen.kt`, `composeApp/.../App.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: no hay harness UI/E2E persistente (la spec lo justifica); el smoke iOS no se automatizó (iOS congelado) y el thumbnail se validó visualmente, no pixel a pixel. `buildGddDocument` es puro y tiene tests cross-platform. La feature quedó `accepted` tras la validación independiente.
- Next best step: cerrar la feature (commit, rebase, push y PR) sin mergear.

### Session 075 (gdd-document-hybrid — cierre, rebase e integración)

- Date: 2026-10-20
- Goal: persistir la aceptación, sincronizar con `origin/develop`, re-verificar, commitear y abrir el PR sin mergear.
- Completed:
  - Validación independiente `accept` (`$feature-validator`), sin bloqueantes. Smoke Android revisado; iOS sin UI automatizada (congelado).
  - `feature_list.json`: `gdd-document-hybrid` `passing` → `accepted`, con evidencia de la validación (`accept`, 2026-10-20) y de los checks pre/post-rebase.
  - Commit `feature: complete gdd-document-hybrid`; rebase sobre `origin/develop` `739a87b` (incluye `first-run-project-onboarding` y `brainstorming-note-per-step`), conflictos resueltos conservando ambas features.
  - `PROGRESS.md`/`AGENTS.md`: estado/cola actualizados (39 accepted, 9 not_started, 0 passing, 1 blocked); `gdd-export-hybrid` desbloqueada.
- Verification run (post-rebase, `origin/develop` `739a87b`):
  - `ANDROID_HOME=… ./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL; **30 suites / 90 tests / 0 failures** en Android/JVM y en iOS (`GddDocument*Test`, `GddProjection*Test` y `GddMarkdown*Test` verdes).
  - `ANDROID_HOME=… CI=true ./init.sh` → exit 0.
  - `Schema.version == 8`; `.sqm` `1.sqm`..`7.sqm`; sin `.sqm` nuevos, sin dependencias nuevas y sin cambios en paths protegidos.
- Evidence captured: veredicto `accept`, checks post-rebase, smoke Android y PR contra `develop` (no mergeado).
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `AGENTS.md`.
- Known risk or unresolved issue: sin harness UI/E2E persistente; smoke iOS no automatizado; thumbnail validado visualmente. Nada bloqueante.
- Next best step: PR contra `develop`; la integración la hace la sesión integradora. No mergear.

### Session 076 (transcribe-audio-attachment — implementación)

- Date: 2026-10-20
- Goal: implementar `transcribe-audio-attachment` (spec `docs/specs/transcribe-audio-attachment.md`) en worktree/rama `feature/transcribe-audio-attachment` sobre `origin/develop` `739a87b`.
- Completed:
  - `:core`: `AudioAttachmentTranscription.kt` con `AudioTranscriptionResult` (`Transcribed`/`AudioNotFound`/`AudioUnavailable`/`EmptyTranscription`/`Failed`) y `AudioAttachmentTranscriptionService.transcribe(noteId, attachmentId)`; valida Adjunto `AUDIO` de la Nota, lee bytes con `AttachmentFileStore.read`, llama `TranscribeClient.transcribe(bytes, mimeType)` una vez, hace `trim` y no persiste ni indexa. `DiagnosticSource.AI_TRANSCRIBE` + resumen estable `transcription_failed` sin `detail` (sin texto/bytes/rutas/ids).
  - `:core/commonTest`: `TranscribeAudioAttachmentCheck` (fake determinista, store en memoria y SQLDelight in-memory) cubre Scenarios 1, 3–7 y 9: bytes/mime exactos, una llamada, no persistencia, AudioNotFound (otra Nota/inexistente/IMAGE), AudioUnavailable, EmptyTranscription, Failed + diagnóstico, orden/elección entre varios AUDIO, determinismo y re-indexado (`INDEXED` + embedding) al simular Guardar. Runners `TranscribeAudioAttachmentAndroidTest`/`IosTest`.
  - `:composeApp`: `AudioTranscription.kt` (`AudioTranscriptionUiState` Idle/Transcribing/Empty/Unavailable/Failed, mensajes, etiqueta `Audio N · tamaño`, aviso de aplicación) y sección "Audio adjunto" en `NoteEditorScreen` (sólo edición, selector con RadioButton si >1, default primero, botón/estado, diálogo con preview Anexar/Reemplazar/Cancelar). Sin auto-save; el texto se aplica una sola vez y se notifica `onTranscribedTextConsumed`.
  - `App`: recibe `transcribeClient`, construye el servicio, es dueño del job/estado/último texto, descarta resultados si cambió la Nota/destino y resetea al navegar con `LaunchedEffect(destination)`; `key(note.id)` reinicia borrador/selección al cambiar de Nota. `MainActivity`/`MainViewController` pasan `aiRuntime.transcribeClient`/`runtime.transcribeClient`. `onSave` existente (update + `indexBestEffort`) sin cambios.
  - Docs: `ARCHITECTURE.md` (servicio, sección de editor, orquestación en App, AI_TRANSCRIBE y wiring de raíces), `docs/technical-discovery.md` (decisiones), `feature_list.json` (`passing` + evidencia) y este bloque.
- Verification run:
  - `ANDROID_HOME=... CI=true ./init.sh` → exit 0 (Android-only, IA fake, sin red ni key).
  - `:core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL; Android 30 suites/90 tests/0 failures/0 errors/0 skipped; iOS 30/90/0; `TranscribeAudioAttachment{Android,Ios}Test` 1/1.
  - `:composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL; `:composeApp:testDebugUnitTest --rerun-tasks` → 10/0 (`AudioTranscriptionTest` 3/3).
  - Smoke Android (emulator-5558, API 34): `install -r`, `pm clear`, arranque, `adb run-as` siembra Nota/Adjunto + `files/audio/*.m4a` (esquema `user_version=8`). Verificado: cuerpo vacío → texto directo + aviso; cuerpo con texto → preview y Anexar/Reemplazar/Cancelar (Cancelar no toca body); Guardar → body persistido, `status=indexada`, `count(embedding)=1`, badge en lista; varios audios → `Audio 1 · 15 B`/`Audio 2 · 17 B`, primero seleccionado, elegir el segundo transcribe sólo esos bytes; archivo ausente → «No se pudo leer el audio adjunto» + «Reintentar» sin mutar body/DB; sin Guardar la DB no cambia (sin auto-save).
  - Alcance: `pragma user_version=8`; sin `.sqm` nuevos (`1.sqm`..`7.sqm`); `git diff` sin `init.sh`, `AiRuntime`, `OpenAiTranscribeClient`, `TranscribeClient`, `AttachmentRepository`, `AttachmentFileStore`, `composeApp/build`; sin secrets.
- Evidence captured: resultados de gate/tests/build/link, consultas SQL y dumps UI en `composeApp/build/smoke-evidence/transcribe-audio-attachment/` (`seed.sql`, `01`–`10` XML, screenshot).
- Files or artifacts updated: `core/src/commonMain/kotlin/com/playbook/core/ai/AudioAttachmentTranscription.kt` (nuevo), `core/src/commonMain/kotlin/com/playbook/core/diagnostics/Diagnostics.kt`, `core/src/commonTest/kotlin/com/playbook/core/ai/TranscribeAudioAttachmentCheck.kt` (nuevo), `core/src/androidUnitTest/.../TranscribeAudioAttachmentAndroidTest.kt` (nuevo), `core/src/iosTest/.../TranscribeAudioAttachmentIosTest.kt` (nuevo), `composeApp/src/commonMain/kotlin/com/playbook/app/AudioTranscription.kt` (nuevo), `composeApp/src/androidUnitTest/kotlin/com/playbook/app/AudioTranscriptionTest.kt` (nuevo), `composeApp/src/commonMain/kotlin/com/playbook/app/NoteEditorScreen.kt`, `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt`, `composeApp/src/androidMain/.../MainActivity.kt`, `composeApp/src/iosMain/.../MainViewController.kt`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el fake no permite ejercitar por UI transcripción vacía ni error de red (cubiertos por tests del core con stubs); la cancelación en vuelo no se capturó visualmente porque el fake resuelve al instante (job cancelable + guard por Nota/destino revisados); el smoke usa audio sembrado y no hay playback. Sin UI/E2E persistente.
- Next best step: validación independiente de `transcribe-audio-attachment` con `$feature-validator`; no commitear todavía.

### Session 077 (transcribe-audio-attachment — cierre, rebase e integración)

- Date: 2026-10-20
- Goal: persistir la aceptación, rebasar sobre el último `origin/develop`, re-verificar y abrir el PR sin mergear.
- Completed:
  - Validación independiente `accept` (`docs/validations/transcribe-audio-attachment.md`), sin hallazgos High/Medium; sólo Low no bloqueantes.
  - Commit de implementación `feature(ai): transcribe el Adjunto de audio de una Nota`; rebase sobre `origin/develop` `9c75be4` (incluye `gdd-document-hybrid`). Único conflicto en `PROGRESS.md`, resuelto preservando ambas features y renumerando esta sesión a 076/077.
  - `feature_list.json`: `transcribe-audio-attachment` `passing` → `accepted`, con evidencia de la validación y de los checks/smoke post-rebase.
  - `PROGRESS.md`/`AGENTS.md`: estado y cola actualizados (40 accepted, 8 not_started, 0 passing, 1 blocked); `gdd-export-hybrid` sigue como siguiente ready.
- Verification run (post-rebase, `origin/develop` `9c75be4`):
  - `ANDROID_HOME=… CI=true ./init.sh` → exit 0.
  - `:core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL; **31 suites / 91 tests / 0 failures / 0 errors / 0 skipped** en Android/JVM e iOS.
  - `:composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL; `:composeApp:testDebugUnitTest --rerun-tasks` → **10 tests / 0 failures** (`AudioTranscriptionTest` 3/3).
  - Smoke Android post-rebase (emulator-5558): archivo ausente → mensaje + Reintentar; multi-audio con `Audio 1 · 15 B` checked y `Audio 2 · 17 B`, elegir el segundo y Transcribir aplica `[fake] transcripción (17 bytes, audio/mp4)`; Guardar persiste body, `status=indexada`, `count(embedding)=1`; `pragma user_version=8`.
  - Alcance: sin `.sqm` nuevos (`1.sqm`..`7.sqm`), esquema v8, sin cambios en `init.sh`, `AiRuntime`, `OpenAiTranscribeClient`, `TranscribeClient`, `AiPorts`, `AttachmentRepository`, `AttachmentFileStore` ni flags fake/real.
- Evidence captured: veredicto `accept`, checks post-rebase y smoke Android; PR contra `develop` (no mergeado).
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `AGENTS.md`.
- Known risk or unresolved issue: el fake no permite ejercitar vacío/red por UI (cubiertos por stubs del core); cancelación en vuelo no capturada visualmente; smoke con audio sembrado y sin playback; sin UI/E2E persistente. Nada bloqueante.
- Next best step: PR contra `develop`; la integración la hace la sesión integradora. No mergear.

### Session 078 (gdd-export-hybrid — implementación)

- Date: 2026-10-20
- Goal: implementar `gdd-export-hybrid` (spec `docs/specs/gdd-export-hybrid.md`) en `worktree/gdd-export-hybrid`, rama `feature/gdd-export-hybrid`, base `origin/develop` `9c75be4`; rebase final sobre `origin/develop` `dc74959` (incluye `transcribe-audio-attachment`).
- Completed:
  - `:core`: `buildGddMarkdown(project, notes, now)` reusa `buildGddDocument`/`buildGdd`; cabecera `# GDD — <título>` (escapado) o `# GDD — Playbook`, `Total: N notas`, `## Proyecto` con `- **<Label>:** <valor>` y mecánicas `  - Categoría · descripción`, y secciones de Notas vigentes. Valores de ficha colapsados a una línea y escapados; labels/prefijos/`status.code` no. El overload `(notes, now)` delega con `project = null` y queda byte-idéntico.
  - `:composeApp`: `GddScreen` invoca el export híbrido y habilita “Compartir” con Notas o ficha con título/campos; snackbar y `GddSharer` intactos. `App.kt` sin cambios.
  - Tests: `verifyGddMarkdownHybrid()` (escenarios 1–8) + runners `GddMarkdownHybridAndroidTest`/`GddMarkdownHybridIosTest`.
  - Docs: `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/specs/gdd-export-hybrid.md`, `feature_list.json`, `PROGRESS.md`, `AGENTS.md`.
  - Cleanup post-validación: KDoc de `GddScreen` corregido (habilitación de “Compartir” con Notas o ficha con contenido); sin cambios de comportamiento.
- Verification run:
  - `ANDROID_HOME=… ./gradlew :core:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL; **31 suites / 91 tests / 0 failures** (`GddMarkdownHybridAndroidTest` verde).
  - `ANDROID_HOME=… ./gradlew :core:iosSimulatorArm64Test --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL; **31 suites / 91 tests / 0 failures** (`GddMarkdownHybridIosTest` verde).
  - `ANDROID_HOME=… ./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL; `ANDROID_HOME=… CI=true ./init.sh` → exit 0.
  - Post-rebase sobre `origin/develop` `dc74959`: `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test --rerun-tasks` → **32 suites / 92 tests / 0 failures** en cada plataforma (incluye `transcribe-audio-attachment` + `gdd-export-hybrid`); `assembleDebug` + link iOS y `CI=true ./init.sh` → verdes.
  - `Schema.version == 8`; `.sqm` `1.sqm`..`7.sqm`; `git diff` vacío en esquema, build files, `GddSharer*`, `App.kt`, `init.sh` e `iosApp`.
  - Controles negativos reproducidos y restaurados byte-idénticos: (a) ignorar la ficha, (b) duplicar Notas, (c) omitir el título de cabecera y (d) no escapar valores de ficha → `GddMarkdownHybridAndroidTest` falla.
- Evidence captured: entrada `passing` en `feature_list.json` con comandos/resultados y este bloque. Smoke independiente del validador (`emulator-5556`, API 35): “Compartir” habilitado con Notas y Proyecto vacío; el chooser mostró exactamente `# GDD — Playbook\n\nTotal: 1 nota\n\n## historia\n\n### rpg\nGéneros: Zelda\-like — Estado: indexada\n`.
- Files or artifacts updated: `core/.../gdd/GddMarkdown.kt`, `core/src/commonTest/.../gdd/GddMarkdownHybridCheck.kt` (nuevo), `core/src/{androidUnitTest,iosTest}/.../gdd/GddMarkdownHybrid*Test.kt` (nuevos), `composeApp/.../GddScreen.kt`, `docs/specs/gdd-export-hybrid.md`, `ARCHITECTURE.md`, `docs/technical-discovery.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el smoke cubrió “Notas + Proyecto vacío”, pero no por UI “Proyecto con campos sin Notas” ni “sin contenido → deshabilitado”; esos estados quedan cubiertos por tests/inspección. El control negativo (e) de habilitación de “Compartir” también se cubre por inspección de la expresión `enabled`. Hallazgo: el ejemplo de la spec tenía `Zelda-like` sin escapar; se corrigió porque `-` pertenece al set. Kotlin/Native exigió renombrar helpers privados de test para evitar colisión con `gddNote` de `GddMarkdownCheck`.
- Next best step: PR abierto contra `develop`; el orquestador persiste `accepted`. No mergear desde esta sesión.
### Session 079 (ai-suggest-classification — implementación)

- Date: 2026-10-20
- Goal: implementar `ai-suggest-classification` (spec `docs/specs/ai-suggest-classification.md`) en el worktree `worktree/ai-suggest-classification`, rama `feature/ai-suggest-classification`, base `origin/develop` `9c75be4`; rebase final sobre `origin/develop` `ca2261c`.
- Completed:
  - `:core`: nuevo `ClassificationSuggestions.kt` con `CLASSIFICATION_SUGGESTION_TIMEOUT_MILLIS = 20_000`, `ClassificationSuggestionResult` (`Success`/`Empty`/`Failed`), `ClassificationSuggestionService` (trim; `Empty` sin llamar al puerto si el body queda vacío; `withTimeout`; `normalizeGenres`; `AiClientException`/timeout → `Failed`, cancelación del llamador propagada), `AppliedClassification` y `applyClassificationSuggestion` (`Track` no nulo reemplaza, `null` conserva; Géneros fusionados normalizados, idempotente).
  - `FakeJevClient`: mismo `text.hashCode()` elige `Track` y un Género de `GenreCatalog.GENRES`; se eliminó `GENRE_HINT` y se actualizó el KDoc. `AiPorts.kt`, `AiRuntime.kt` y el adaptador real quedaron intactos.
  - `:composeApp`: `NoteEditorScreen` acepta `classificationSuggestionService`, mantiene estado efímero `Idle`/`Loading`/`Ready`/`Empty`/`Error` con `rememberCoroutineScope` + `Job`, botón "Sugerir clasificación" debajo del cuerpo/dictado y antes de `Track` (habilitado sólo con body no vacío y fuera de carga), tarjeta "Sugerencia de la IA" con `LabelChip` de `Track` y `GenreLabelChip` de Géneros, y acciones Confirmar/Descartar/Reintentar. Cambios manuales de body/track/Géneros cancelan y descartan; un contador de request ignora resultados tardíos; Guardar no se bloquea. `App` recibe `jevClient`, crea el servicio recordado y lo pasa a Create/CreateWithImage/Edit; roots Android/iOS pasan `runtime.jevClient` (iOS sigue fake).
  - Tests: `ClassificationSuggestionCheck` (6 checks) + `ClassificationSuggestionAndroidTest`/`ClassificationSuggestionIosTest` (6 `@Test`/`runTest` cada uno), con stubs test-only (determinista, fallo, slow) y `FakeJevClient` real del gate.
  - Docs: `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `CONSTRAINTS.md` (sugerencia vía `JevClient` + regla de confirmación/efímera), `AGENTS.md` (cola), `feature_list.json`, `PROGRESS.md`.
- Verification run:
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; **31 suites / 96 tests / 0 failures / 0 errors / 0 skipped** (base 30/90 + 6 nuevos; `ClassificationSuggestionAndroidTest` 6/6).
  - `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; **31 suites / 96 tests / 0 failures** (`ClassificationSuggestionIosTest` 6/6).
  - `./gradlew :composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL; `CI=true ./init.sh` → exit 0; `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - Controles negativos (reproducidos con `--tests *ClassificationSuggestionAndroidTest*` y restaurados byte-idénticos, SHA-256 `e2a8c759…`): (a) omitir `normalizeGenres` en `suggest` → fallan normalización y vacío (2/6); (b) `apply` reemplaza Géneros en vez de fusionar → falla aplicación (1/6); (c) quitar el catch de `TimeoutCancellationException` → falla timeout (1/6). Suite completa final re-verificada en verde.
  - Smoke Android (emulator-5556, Pixel_3A_API_34; evidencia en `composeApp/build/smoke-evidence/ai-suggest-classification/`): body vacío → botón `enabled="false"`; `body="rpg"` → tarjeta `Track: historia` + Género de catálogo `Shooter` (hash 113129 → índices 2/5); DB antes de Guardar `note=0`, `game_project=1`, `user_version=8`; Confirmar; quitar `Shooter`; agregar `Zelda-like`; Guardar → 1 Nota `rpg`/`historia`/`Zelda-like` (`status=indexada`); relanzar conserva; editar → sugerir + Descartar no cambia y Guardar sin sugerencia avanza `updated_at`; cambiar body con tarjeta visible la descarta; Cancelar no persiste.
  - `Schema.version == 8`; sin `.sqm` nuevos; `git diff` sin cambios en `AiRuntime.kt`, `AiPorts.kt`, `OpenAiAdapters.kt`, `Note`/`NoteDraft`, SQLDelight, build files, `init.sh`, `iosApp/**` ni secrets; en `FakeAiPorts.kt` sólo cambió `FakeJevClient`.
- Evidence captured: entrada `passing` de `ai-suggest-classification` en `feature_list.json` (comandos, conteos, controles negativos, DB/smoke y limitaciones) y este bloque; UI dumps/capturas/copias de DB en `composeApp/build/smoke-evidence/ai-suggest-classification/` (ignorado por git).
- Files or artifacts updated: `core/src/commonMain/kotlin/com/playbook/core/ai/ClassificationSuggestions.kt` (nuevo) y `FakeAiPorts.kt`; `core/src/commonTest/.../ai/ClassificationSuggestionCheck.kt` (nuevo); `core/src/{androidUnitTest,iosTest}/.../ai/ClassificationSuggestion*Test.kt` (nuevos); `composeApp/.../NoteEditorScreen.kt`, `App.kt`, `MainActivity.kt`, `MainViewController.kt`; `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `CONSTRAINTS.md`, `AGENTS.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: no hay harness UI/E2E persistente (la spec lo justifica); `Empty`/`Error` no son alcanzables con el fake de producción y, con el fake, la carga es instantánea: "Sugiriendo…" no se pudo capturar en el smoke (el estado existe y deshabilita la acción). La calidad real de `jev` queda como smoke key-gated fuera del gate. iOS sólo se verificó por compilación (congelado). Nada bloqueante.
- Next best step: validación independiente de `ai-suggest-classification`; luego commit/rebase/PR sin mergear (la sesión integradora es la única que mergea).

### Session 080 (ai-suggest-classification — cierre, rebase e integración)

- Date: 2026-10-20
- Goal: persistir la aceptación, sincronizar con `origin/develop`, re-verificar, commitear y abrir el PR sin mergear.
- Completed:
  - Validación independiente `accept` (`docs/validations/ai-suggest-classification.md`); sin hallazgos Critical/High/Medium.
  - `feature_list.json`: `ai-suggest-classification` `passing` → `accepted`, con la evidencia del validador (checks 31/96, controles negativos y smoke independiente).
  - Commit `feat(ai): sugiere clasificación con JevClient y confirmación del usuario`; rebase sobre `origin/develop` manteniendo el alcance de la spec.
  - `PROGRESS.md`/`AGENTS.md`: estado/cola actualizados (`ai-suggest-classification` aceptada; `gdd-export-hybrid` sigue pendiente de persistencia por su orquestador).
- Verification run (post-rebase, `origin/develop` `ca2261c`):
  - `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; **33 suites / 98 tests / 0 failures** (base 32/92 + 6 nuevos; incluye `transcribe-audio-attachment` y `gdd-export-hybrid`).
  - `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; **33 suites / 98 tests / 0 failures**.
  - `./gradlew :composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL; `CI=true ./init.sh` → exit 0; `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - `Schema.version == 8`; sin `.sqm` nuevos; sin cambios en paths protegidos ni secrets.
- Evidence captured: veredicto `accept`, checks post-rebase y smoke Android (`composeApp/build/smoke-evidence/ai-suggest-classification/`).
- Files or artifacts updated: `feature_list.json`, `PROGRESS.md`, `AGENTS.md`.
- Known risk or unresolved issue: sin harness UI/E2E persistente; `Loading` no capturado con el fake instantáneo; `Empty`/`Error` sólo en tests del core; calidad real de `jev` key-gated. Nada bloqueante.
- Next best step: push de la rama y PR contra `develop`; la integración la hace la sesión integradora. No mergear.
