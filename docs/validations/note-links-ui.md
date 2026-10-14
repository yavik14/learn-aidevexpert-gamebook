# Validación independiente: `note-links-ui`

- **Feature id:** `note-links-ui`
- **Spec:** `docs/specs/note-links-ui.md`
- **Fecha:** 2026-10-14
- **Rol:** validator (independiente del implementer)
- **Worktree:** `worktree/gamebook/note-links-ui` (rama `feature/note-links-ui`)
- **Estado en `feature_list.json` al validar:** `passing` (implementada, auto-verificada, sin commitear)
- **Veredicto:** **accept**

## Alcance validado

Superficie de lectura de los Enlaces `semantic-linking`: proyección pura
`buildRelatedNotes`/`RelatedNote` en `:core`, pantalla `RelatedNotesScreen` (estado
vacío + tarjetas con `score` textual y chips), `NotesDestination.Related(noteId)` +
botón "Ver relacionadas" en el editor, y wiring de `SqlDelightNoteLinkRepository`/
`SemanticLinkingService` en los composition roots Android/iOS. Incluye el **trigger
mínimo** de la spec: `recomputeAll()` **best-effort** al abrir la pantalla (fallo
ignorado), único disparador. Sin trigger de ciclo de vida, `NoteStatus`, RAG ni
esquema.

## Checks re-ejecutados por el validador (no se confió en el resumen)

| Check | Comando | Resultado |
| --- | --- | --- |
| Gate estándar | `CI=true ./init.sh` | exit 0; sin dev servers ni simuladores; `init.sh` sin cambios |
| Tests core Android/JVM | `./gradlew :core:testDebugUnitTest --rerun-tasks --console=plain` | BUILD SUCCESSFUL; **40 tests, 0 failures/errors** (`RelatedNotesTest` 7/7) |
| Tests core iOS | `./gradlew :core:iosSimulatorArm64Test --rerun-tasks --console=plain` | BUILD SUCCESSFUL; **40 tests, 0 failures/errors** (`RelatedNotesTest` 7/7) |
| Build Android | `./gradlew :composeApp:assembleDebug --console=plain` | BUILD SUCCESSFUL |
| Link framework iOS | `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --console=plain` | BUILD SUCCESSFUL |
| Host iOS | `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator build` | `** BUILD SUCCEEDED **` |

Conteos desde `core/build/test-results/*/TEST-*.xml`: 40 tests por plataforma, 0
failures/errors/skipped.

Esquema: `PlaybookDatabase.Schema.version.get() == 5` en el código generado
(`core/build/generated/sqldelight/.../core/PlaybookDatabaseImpl.kt:39-40`). Sin `.sqm`
nuevo (`1.sqm`–`4.sqm`).

## Scope discipline

- `git diff --stat -- init.sh gradle/libs.versions.toml composeApp/build.gradle.kts core/build.gradle.kts 'core/src/commonMain/sqldelight'` → **vacío** (sin cambios, sin dependencias nuevas, sin `.sq`/`.sqm` nuevos).
- `git status --porcelain` sólo lista los archivos de la feature (`RelatedNotes.kt`+test, `RelatedNotesScreen.kt`, `App.kt`, `NoteEditorScreen.kt`, `MainActivity.kt`, `MainViewController.kt`, docs, spec, `PROGRESS.md`, `feature_list.json`); sin keystores/secrets.
- El recomputo al abrir está **explícitamente dentro del alcance** de la spec (sección "Decisión de alcance: incluye un trigger mínimo"); es el único disparador y es best-effort. No se agregó trigger de ciclo de vida, `NoteStatus`, RAG ni esquema.

## Control negativo propio (reproducido y restaurado byte-idéntico)

1. `RelatedNotes.kt`: `compareByDescending<RelatedNote> { it.score }` → `compareBy<RelatedNote> { it.score }`
   (`./gradlew :core:testDebugUnitTest --tests com.playbook.core.linking.RelatedNotesTest --rerun-tasks`)
   → **7 tests, 1 failed** (`ordersByScoreDescending`, `RelatedNotesTest.kt:49`).
   Restaurado: sha256 `1c6cb4cf6a5244dcf7b04fc571ceb299f3622271603453180b7e0526945eeecc` (idéntico).
2. Tras restaurar, la suite completa vuelve a **40/0 Android y 40/0 iOS**.

## Escenarios de aceptación 1–6

1. **Proyección pura** — cubierto por `RelatedNotesTest` (7 casos): otro extremo desde
   ambos lados, orden `score` desc, desempate por `note.id` asc, omisión de ids
   ausentes, exclusión de la propia Nota y de enlaces ajenos, sin enlaces, no-mutación
   y determinismo. **Control negativo propio** prueba que el orden es sensible.
2. **Camino feliz** — `RelatedNotesScreen` renderiza cuerpo origen + tarjetas con
   `score` textual ("Similitud 1.00") y chips `track`/`status`/Etiquetas; tocar abre
   la Nota (`NotesDestination.Edit`). Verificado por revisión de código + smoke Android
   (`Similitud 1.00` con coseno 1.0 por embeddings idénticos del `FakeAiClient`).
3. **Estado vacío** — "No hay notas relacionadas" + línea de apoyo; verificado por
   revisión de código y smoke Android (Nota sin enlaces).
4. **Recomputo best-effort al abrir** — `runCatching { semanticLinkingService.recomputeAll() }`
   seguido de `buildRelatedNotes(...)`; el fallo se ignora y se muestra lo persistido.
   Verificado por revisión de código; el smoke Android confirma la materialización
   (dos Notas con el mismo cuerpo → enlace coseno 1.0).
5. **Navegación y "Volver"** — tocar tarjeta → `Edit(noteId)`; "Volver" → `List`; Nota
   de origen inexistente → `goToList()`. Verificado por revisión de código + smoke Android.
6. **Ambas plataformas + gate** — tests 40/40 Android/JVM e iOS, `assembleDebug`, link
   del framework iOS y `xcodebuild` verdes, `CI=true ./init.sh` exit 0, esquema v5, sin
   `.sqm` ni dependencias nuevas.

## Seguridad (checklist scoped al diff)

- Sin secrets/tokens/keys/URLs privadas en el diff.
- Todo local-first y single-user: sin auth, sin red, sin superficie de entrada nueva.
  `buildRelatedNotes` opera en memoria (sin SQL ni rutas de archivo); la lectura de
  enlaces usa SQLDelight parametrizado.
- Sin dependencias nuevas ni artefactos de build versionados (smoke en `build/`,
  ignorado por `.gitignore`).
- **No se encontraron hallazgos de seguridad.**

## Documentación durable

`ARCHITECTURE.md` (nueva sección "Enlaces (notas relacionadas)", wiring y surfaces
actualizados), `docs/technical-discovery.md` (nueva "Linking UI Decisions"),
`docs/risks-and-open-questions.md` (UI de lectura resuelta; trigger de ciclo de vida
como follow-up) y `AGENTS.md` (bloque "Próxima feature en cola") actualizados y
consistentes con el código. `CONSTRAINTS.md` no era necesario. Spec marcada en sus
tasks (sin cambio de alcance). `init.sh` sin cambios y sigue siendo un gate real no
bloqueante.

## Limitaciones declaradas honestamente (y las propias del validador)

- **Smoke iOS:** el implementer sólo verificó el **arranque** en simulador (wiring +
  esquema v5 sin crash); no ejecutó el camino interactivo ni el OCR que menciona el
  plan de verificación (sin `idb`/`cliclick`/OCR disponibles). Declarado sin fabricar
  evidencia. El `commonMain` compartido se ejercita en Android y el path de datos con
  tests del core en ambas plataformas.
- **Smoke Android:** el validador **no** re-ejecutó el smoke interactivo (sin `adb` en
  su entorno); se apoyó en la evidencia del implementer (`build/smoke/relnotes-*.png`,
  `relnotes_smoke.py`) y en la re-ejecución de los tests de proyección.
- **E2E persistente:** no aplica (sin harness de UI/E2E y `init.sh` no levanta
  simuladores); justificado en la spec.

## Hallazgos

### Finding: `relatedNotes` puede mostrar datos transitorios de otra Nota al reabrir

- **Severity:** Low (no bloqueante)
- **Evidence:** `App.kt` hoistea `var relatedNotes by remember { mutableStateOf(emptyList<RelatedNote>()) }`
  y sólo la asigna dentro del `LaunchedEffect(current.noteId, refreshKey)` de la rama
  `Related`. Al abrir "Notas relacionadas" de una Nota distinta de la última vista, el
  primer frame renderiza el valor anterior (p. ej. las relacionadas de la Nota X bajo
  el cuerpo de la Nota Y) hasta que corre el `LaunchedEffect` (que incluye el
  `recomputeAll()` síncrono). No hay un reset a `emptyList()` al entrar a `Related`.
- **Why it matters:** parpadeo/estado transitorio incorrecto de la UI; no viola ningún
  escenario de la spec (2/3 se verificaron con el flujo lineal del smoke) pero es
  visible si se navega entre relacionadas de Notas distintas.
- **Required change:** no bloquea; opcionalmente, resetear `relatedNotes = emptyList()`
  al cambiar de `noteId` (o keyear el `remember` del estado por `noteId`).
- **Verification after fix:** test de UI/Compose o smoke que abra relacionadas de la
  Nota X, vuelva y abra las de la Nota Y sin ver las tarjetas de X.

### Finding: cobertura de smoke iOS menor que el plan de verificación

- **Severity:** Low
- **Evidence:** el plan pedía el mismo camino verificado por OCR cuando no hubiera
  input automation; el implementer sólo verificó el arranque (lista `Notas`, sin crash)
  y lo declaró. El mismo `commonMain` se ejercita en Android.
- **Why it matters:** menor reproducibilidad del flujo UI en iOS; mitigado por el smoke
  Android y por la proyección cubierta en iOS.
- **Required change:** no bloquea; deuda a cerrar cuando exista input automation/OCR.
- **Verification after fix:** smoke iOS del camino "Ver relacionadas" → abrir → "Volver".

No hay hallazgos Critical/High/Medium.

## Veredicto final

**accept.** La implementación se ciñe a la spec: la proyección pura (Scenario 1) queda
cubierta por `RelatedNotesTest` 7/7 en Android/JVM e iOS (40/40 por plataforma, 0
failures) y es sensible a fallos reales (control negativo propio, restaurado
byte-idéntico); el estado vacío, el camino feliz, el recomputo best-effort y la
navegación (Scenarios 2–5) se verifican por revisión de código + smoke Android; el
Scenario 6 (ambas plataformas + gate) se re-ejecutó de verdad (`./init.sh` exit 0,
builds + link + `xcodebuild` verdes). El trigger `recomputeAll()` best-effort al abrir
está **explícitamente dentro del alcance** de la spec, es el único disparador y no
propaga fallos. El alcance se respeta (esquema v5, sin `.sqm`/dependencias/`init.sh`
nuevos, sin `NoteStatus`/RAG/trigger de ciclo de vida), la seguridad no arroja
hallazgos y la documentación durable está actualizada. Hallazgos Low no bloqueantes:
dato transitorio de `relatedNotes` al cambiar de Nota y cobertura de smoke iOS limitada
al arranque (ambos declarados honestamente). Recomendación: promover `note-links-ui` a
`accepted` en `feature_list.json`.
