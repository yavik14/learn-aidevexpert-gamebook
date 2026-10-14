# Validación independiente: `note-enrichment-status-ui`

- **Feature id:** `note-enrichment-status-ui`
- **Spec:** `docs/specs/note-enrichment-status-ui.md`
- **Fecha:** 2026-10-14
- **Rol:** validator (independiente del implementer)
- **Worktree:** `worktree/gamebook/note-enrichment-status-ui` (rama `feature/note-enrichment-status-ui`)
- **Estado en `feature_list.json` al validar:** `passing` (implementada, auto-verificada, sin commitear)
- **Veredicto:** **accept**

## Alcance validado

Estado de enriquecimiento visible + re-indexado manual: `EnrichmentStatus.kt`
(`statusLabel`/`StatusBadge`), `App` que usa `NoteIndexingOrchestrator` (reemplaza
el `NoteIndexingService` directo) con `reload()` tras indexar, `reindex(note)`
manual en el editor, badge textual en lista y editor, y regresión del orquestador
en `:core`. Sin clasificación IA, sin reintento automático, sin esquema nuevo
(v5, sin `.sqm`).

## Checks re-ejecutados por el validador (no se confió en el resumen)

| Check | Comando | Resultado |
| --- | --- | --- |
| Gate estándar | `CI=true ./init.sh` | exit 0; sin dev servers ni simuladores; `init.sh` sin cambios |
| Tests core Android/JVM | `./gradlew :core:testDebugUnitTest --rerun-tasks --console=plain` | BUILD SUCCESSFUL; **40 tests, 0 failures/errors** (`NoteIndexingOrchestratorAndroidTest` 1/1) |
| Tests core iOS | `./gradlew :core:iosSimulatorArm64Test --rerun-tasks --console=plain` | BUILD SUCCESSFUL; **40 tests, 0 failures/errors** (`NoteIndexingOrchestratorIosTest` 1/1) |
| Build Android | `./gradlew :composeApp:assembleDebug --rerun-tasks --console=plain` | BUILD SUCCESSFUL (57 tareas) |
| Link framework iOS | `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks --console=plain` | BUILD SUCCESSFUL (57 tareas) |

Conteos desde `core/build/test-results/*/TEST-*.xml`: 40 tests por plataforma, 0
failures/errors/skipped. El check compartido `verifyNoteIndexingOrchestrator` se
ejecuta en ambas plataformas (`NoteIndexingOrchestrator{Android,Ios}Test`).

Esquema: `PlaybookDatabase.Schema.version.get() == 5` en el código generado
(`core/build/generated/sqldelight/.../core/PlaybookDatabaseImpl.kt:39-40`); sin
`.sqm` nuevo (`find core/src -name '*.sqm'` → `1.sqm`–`4.sqm`).

## Reconciliación con `note-links-ui` (merge)

- `NotesDestination.Related(noteId)` y `onOpenRelated` conservados y funcionales en
  `App.kt` (rama `Related` + `LaunchedEffect(current.noteId, refreshKey)` +
  `RelatedNotesScreen`), y `NoteEditorScreen` sigue recibiendo `onOpenRelated` y
  mostrando "Ver relacionadas" en fila propia.
- Los nuevos props del editor (`initialStatus`, `onReindex`) se agregaron **con
  default `null`** después de `onOpenRelated`, por lo que no rompen ese contrato.
- Dos únicos llamadores de `App(...)` (`MainActivity`, `MainViewController`)
  actualizados; no quedan referencias a `noteIndexingService` como dependencia de
  `App`.
- `RelatedNotesTest` 7/7 y `NoteLinkRepository*Test` 1/1 verdes en ambas plataformas.

## Comportamiento verificado por revisión de código (independiente)

- `App` usa `NoteIndexingOrchestrator`; `indexBestEffort` llama
  `noteIndexingOrchestrator.index(note)` y `reload()` al resolver el `suspend`;
  `body` en blanco retorna sin indexar.
- `reindex(note)` usa el mismo orquestador + `reload()`; sin scheduling,
  conectividad ni `FAILED`.
- `NoteIndexingOrchestrator` sólo llama `updateStatus`; `updateNoteStatus` es
  `UPDATE note SET status = ? WHERE id = ?` (no re-sella `updated_at`), así que
  `track`/`tags`/`body`/`owner`/timestamps quedan intactos.
- Botón "Re-indexar": `enabled = initialStatus != NoteStatus.INDEXED && body.isNotBlank()`.
- `StatusBadge` comunica con texto + forma (borde + `surfaceVariant`), distinto de
  `LabelChip` (sin borde, `secondaryContainer`); "nunca sólo color".

## Control negativo propio (reproducido y restaurado byte-idéntico)

1. `core/.../NoteIndexingOrchestrator.kt`: tras `updateStatus` se agregó
   temporalmente `noteRepository.update(note.copy(body = "PISADO"))` (simula "el
   re-indexado pisa el contenido").
   `./gradlew :core:testDebugUnitTest --tests '*NoteIndexingOrchestrator*' --rerun-tasks`
   → **1 test, 1 failed** (`transitionsNoteStatusByIndexingResult`, `AssertionError`).
   Restaurado con `git checkout`; sha256 idéntico
   `8dcfd14dfcb2cea2606e43bc07f80dc661afc2b222e8aa2fbedc11b57ee64dc6` y re-ejecutado
   en verde.
2. Tras restaurar, la suite completa vuelve a **40/0 Android y 40/0 iOS**.

## Escenarios de aceptación 1–6

1. **Guardar texto indexa y lo refleja** — `indexBestEffort` → orquestador (`CAPTURED→INDEXED`) + `reload()`. Cubierto por `NoteIndexingOrchestrator*Test` Scenario 1 (embedding persistido) y por el smoke Android del implementer (fila `embedding`).
2. **Fallo de IA deja pendiente y visible** — orquestador falla → `PENDING`; badge "pendiente"; botón habilitado. Cubierto por Scenario 2/3/5 del test + revisión de código.
3. **Re-indexado manual preserva clasificación** — Scenario 8 (nuevo): re-indexar `PENDING` con `track`/`tags`/`body`/`owner`/timestamps conocidos → `INDEXED` y **idénticos**. Control negativo propio prueba que es sensible.
4. **Indexada no ofrece re-indexar** — botón deshabilitado (`INDEXED != INDEXED`).
5. **Nota sin texto** — `body.isBlank()` → no indexa; botón deshabilitado.
6. **Sin esquema, ambas plataformas y gate honesto** — v5, sin `.sqm`, tests 40/40 por plataforma, builds/link verdes, `CI=true ./init.sh` exit 0.

## Scope discipline

- `git diff --stat -- core/src/commonMain core/src/androidMain core/src/iosMain
  'core/src/commonMain/sqldelight' iosApp init.sh gradle/libs.versions.toml
  composeApp/build.gradle.kts core/build.gradle.kts build.gradle.kts` → **vacío**:
  sin cambios en `:core` de producción, `iosApp`, `init.sh`, esquema, `.sqm` ni
  dependencias.
- `git status` sólo lista los archivos de la feature (`EnrichmentStatus.kt`
  nuevo, `App.kt`, `NotesListScreen.kt`, `NoteEditorScreen.kt`, `MainActivity.kt`,
  `MainViewController.kt`, `NoteIndexingOrchestratorCheck.kt`, docs, spec,
  `PROGRESS.md`, `feature_list.json`); sin keystores/secrets.
- Sin `ViewModel`/`androidx.lifecycle`/Flow/`androidx.navigation` introducidos
  (las menciones en `App.kt`/`GddScreen.kt` son KDoc "sin ViewModel").
- Spec sin cambio de alcance: el diff sólo marca los tasks `[x]`.

## Seguridad (checklist scoped al diff)

- Sin secrets/tokens/keys/URLs privadas en el diff.
- Local-first single-user: sin auth ni red nuevas; `FakeAiClient` determinista.
- Sin dependencias nuevas ni artefactos de build versionados (smoke en `build/`,
  ignorado por `.gitignore`).
- Sin superficie de entrada nueva (el estado se deriva del `NoteStatus`
  persistido).
- **No se encontraron hallazgos de seguridad.**

## Documentación durable

`ARCHITECTURE.md` (wiring del orquestador/`reload`, `StatusBadge`, re-indexado
manual, firma de `App`), `docs/technical-discovery.md` (nueva "Enrichment Status
UI Decisions"), `CONSTRAINTS.md` (MUST NOT de reintento automático y de pisar
`track`/`tags`/`body`), `AGENTS.md` (sólo el bloque "Próxima feature en cola") y
`feature_list.json`/`PROGRESS.md` actualizados y consistentes con el código y la
spec. `CONTEXT.md`/`DESIGN.md`/`docs/domain-model.md` correctamente no tocados
(la spec lo justifica). Spec marcada en sus tasks. `init.sh` sigue siendo un gate
real no bloqueante.

## Limitaciones declaradas honestamente (y las propias del validador)

- **Smoke Android:** el validador **no** re-ejecutó el smoke interactivo (sin
  `adb`/emulador en su entorno); se apoya en la evidencia del implementer
  (`build/smoke/enrich-*.png`, `enrich_smoke.py`) y en la re-ejecución completa de
  los tests del core en ambas plataformas, que cubren el comportamiento a nivel
  del orquestador.
- **Smoke iOS:** sólo arranque/render de la lista (sin `idb`/`cliclick`/OCR y con
  el container del simulador restringido); declarado sin fabricar evidencia. El
  `commonMain` se ejercita en Android.
- **E2E persistente:** no aplica (sin harness de UI/E2E y `init.sh` no levanta
  simuladores); justificado en la spec.

## Hallazgos

### Finding: "Re-indexar" indexa la Nota persistida, no el borrador sin guardar

- **Severity:** Low (no bloqueante)
- **Evidence:** `App.kt` `onReindex = { reindex(note) }` pasa la `note` de la lista
  (estado persistido), mientras el botón se habilita con `body.isNotBlank()` del
  **borrador** (`NoteEditorScreen.kt:111`). Si el usuario edita el `body` de una
  Nota `PENDING` y toca "Re-indexar" sin guardar, se embebe el `body` guardado, no
  el del borrador.
- **Why it matters:** el embedding puede no reflejar la edición más reciente. No
  viola ningún escenario de la spec (Scenario 3 parte de una Nota persistida) y ya
  está declarado como riesgo conocido en `PROGRESS.md`; es una decisión de producto
  razonable (re-indexar = indexar el estado persistido).
- **Required change:** no bloquea. Si se desea, deshabilitar "Re-indexar" cuando el
  borrador difiere de la Nota persistida, o guardar antes de indexar.
- **Verification after fix:** smoke que edite el `body` de una Nota `PENDING` y
  confirme que el embedding corresponde al texto mostrado.

### Finding: sin estado visual de "re-indexando"

- **Severity:** Low
- **Evidence:** `reindex` lanza el `suspend` y no expone progreso; taps repetidos
  son idempotentes por el `upsert` del embedding y `updateStatus`.
- **Why it matters:** el usuario no recibe feedback inmediato en Notas muy
  costosas; hoy la IA es `FakeAiClient` (instantáneo), así que el impacto es nulo.
- **Required change:** no bloquea; deuda a cerrar con el runtime real/logging.
- **Verification after fix:** estado intermedio de "re-indexando" en el editor.

No hay hallazgos Critical/High/Medium.

## Veredicto final

**accept.** La implementación se ciñe a la spec: `App` usa
`NoteIndexingOrchestrator` (con `reload()` tras indexar), el `NoteStatus` mostrado
es real y se comunica con texto/forma en lista y editor, y el re-indexado manual
preserva `track`/`tags`/`body`/`owner`/timestamps (Scenario 8 cubierto y sensible
por control negativo propio, restaurado byte-idéntico). Los 6 escenarios quedan
cubiertos por los tests del core en Android/JVM e iOS (40/40 por plataforma, 0
failures) + builds/link verdes + `CI=true ./init.sh` exit 0 + smoke Android del
implementer. El alcance se respeta (`:core` de producción, `iosApp`, `init.sh`,
esquema, `.sqm` y dependencias sin cambios; sin clasificación IA ni reintento
automático), la reconciliación con `note-links-ui` está intacta, la seguridad no
arroja hallazgos y la documentación durable está actualizada. Hallazgos Low no
bloqueantes: re-indexado sobre la Nota persistida (no el borrador) y ausencia de
estado "re-indexando" (ambos declarados honestamente). Recomendación: promover
`note-enrichment-status-ui` a `accepted` en `feature_list.json`.
