# Validación: tts-read-aloud

- **Feature:** `tts-read-aloud`
- **Spec:** `docs/specs/tts-read-aloud.md`
- **Worktree/rama:** `worktree/tts-read-aloud` / `feature/tts-read-aloud`
- **Base:** `origin/develop` `763a357` (al iniciar la validación)
- **Estado previo:** `passing` (Session 084)
- **Fecha:** 2026-10-20
- **Veredicto:** **`accept`** (validación independiente)
- **Estado recomendado:** `passing` → `accepted` (lo persiste el orquestador; esta validación no modifica el estado)

## Alcance revisado

Se revisaron `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, la spec completa,
`ARCHITECTURE.md`, `docs/technical-discovery.md`, los specs de contexto
(`ai-client-ports`, `ai-fake-real-switch`, `rag-answer-citations`,
`gdd-document-hybrid`), los composition roots (`MainActivity`,
`MainViewController`), el diff completo del worktree contra `763a357` y
la evidencia de smoke declarada en
`composeApp/build/smoke-evidence/tts-read-aloud/`.

El worktree tiene los cambios **sin commitear** sobre `763a357`; no se
commiteó, pusheó ni mergeó nada. El único artefacto nuevo de esta corrida es este
documento. No se tocó `worktree/save-answer-as-note`.

Archivos del cambio (12 modificados + 13 nuevos/untracked de la implementación;
este documento es el artefacto adicional de la validación):

- `:core`: `ai/ReadAloud.kt`, `gdd/GddNarration.kt` (nuevos);
  `ai/FakeAiPorts.kt` (sólo `FakeTtsClient`) y
  `commonTest/ai/AiPortsContract.kt` (mime/cabecera WAV).
- `:core` tests: `ReadAloudCheck`/`GddNarrationCheck` (common) +
  `ReadAloudAndroidTest`/`GddNarrationAndroidTest` y
  `ReadAloudIosTest`/`GddNarrationIosTest` (runners).
- `:composeApp`: `ReadAloudPlayer.kt`, `ReadAloudButton.kt`,
  `ReadAloudPlayer.android.kt`, `ReadAloudPlayer.ios.kt` (nuevos); `App.kt`,
  `AskNotesScreen.kt`, `NoteEditorScreen.kt`, `GddScreen.kt`, `MainActivity.kt`,
  `MainViewController.kt`.
- Docs/estado: `ARCHITECTURE.md`, `docs/technical-discovery.md`,
  `docs/specs/tts-read-aloud.md`, `feature_list.json`, `PROGRESS.md`.

## Verificación re-ejecutada

| Check | Resultado |
| --- | --- |
| `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :core:testDebugUnitTest :composeApp:assembleDebug --rerun-tasks --console=plain` | **BUILD SUCCESSFUL** (62 tasks). Android/JVM: **36 suites / 107 tests / 0 failures / 0 errors / 0 skipped**. `ReadAloudAndroidTest` 5, `GddNarrationAndroidTest` 3, `AiPortsAndroidTest` 6. |
| `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :core:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks --console=plain` | **BUILD SUCCESSFUL** (7 tasks). iOS: **36 suites / 107 tests / 0 failures / 0 errors / 0 skipped**. `ReadAloudIosTest` 5, `GddNarrationIosTest` 3. Link iOS OK. |
| `CI=true ANDROID_HOME="$HOME/Library/Android/sdk" ./init.sh` | **exit 0**; gate Android-only con IA fake, sin red ni dev servers. |
| `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :composeApp:testDebugUnitTest --rerun-tasks --console=plain` | **BUILD SUCCESSFUL** (39 tasks). `composeApp` Android/JVM: **4 suites / 10 tests / 0 failures / 0 errors**. `RealAiSmokeTest` queda key-gated y no ejecuta red sin `PLAYBOOK_AI_SMOKE=1`. |
| `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks --console=plain` (post-controles negativos) | **BUILD SUCCESSFUL**; Android/JVM e iOS vuelven a **36/107/0**. |

Los conteos se obtuvieron parseando los XML de
`core/build/test-results/{testDebugUnitTest,iosSimulatorArm64Test}/`, no del
resumen del implementer.

### Controles negativos reproducidos y restaurados

Se copiaron los tres archivos a un directorio temporal y se verificó su
restauración byte-idéntica con `diff -q` y SHA-256.

| Control | Mutación | Resultado esperado | Restauración |
| --- | --- | --- | --- |
| (a) sin truncado | `normalizeReadAloudText` devuelve `trimmed` sin `take(4096)` | `ReadAloudAndroidTest.normalizesTrimEmptyAndLimit` **FAILED** | SHA-256 `04ae216b…` idéntico |
| (b) narración con bodies vacíos | `GddNarration` incluye secciones sin cuerpos | `GddNarrationAndroidTest` **3/3 FAILED** | SHA-256 `64b5fac4…` idéntico |
| (c) síntesis con texto vacío | `ReadAloudService` llama al cliente con el texto en blanco | `ReadAloudAndroidTest.emptyTextDoesNotCallClient` **FAILED** | SHA-256 `04ae216b…` idéntico |
| (d) fake sin `RIFF` | `FakeTtsClient` escribe `RIFX` | `ReadAloudAndroidTest.fakeTtsIsPlayableAndDeterministic` **FAILED** | SHA-256 `305b1da2…` idéntico |

### Alcance y disciplina

- `git diff --name-only 763a357` y `git ls-files --others` no incluyen
  build files, `gradle/`, `init.sh`, `iosApp/**`, `AiPorts.kt`,
  `AiRuntime.kt`, `OpenAiTtsClient`/`OpenAiAdapters.kt`, `AiRuntimeConfig.kt`,
  esquema ni `.sqm`.
- `AiPorts.kt`, `SynthesizedAudio`, `AiRuntime.kt`, `OpenAiAdapters.kt` y
  `AiRuntimeConfig.kt` tienen **diff vacío** contra `763a357`.
- Hay exactamente `1.sqm`..`7.sqm` (sin `.sqm` nuevos); los checks del core
  afirman `PlaybookDatabase.Schema.version == 8`.
- No se agregaron dependencias ni permisos; el gate sigue con IA fake y sin red.

> **Nota de integración:** al iniciar la validación `origin/develop` apuntaba a
> `763a357` (base de esta feature). Durante la validación avanzó a `6a89317`
> (`save-answer-as-note`). El worktree de `tts-read-aloud` sigue en `763a357`;
> la comparación de alcance se hizo contra esa base. La sesión integradora debe
> rebasar sobre el `origin/develop` actual y resolver los conflictos esperables
> en `App.kt`, `AskNotesScreen.kt` y `NoteEditorScreen.kt`, y volver a correr el
> gate después del rebase.

## Cumplimiento de la spec

| Escenario | Estado |
| --- | --- |
| 1. Q&A lee la respuesta | Cumplido. `AskNotesScreen:160` pasa `state.text`; no lee pregunta ni fuentes. El XML de smoke muestra la respuesta y el botón activo. |
| 2. Nota lee el cuerpo | Cumplido. `NoteEditorScreen:332` pasa el `body` actual; Create, CreateWithImage y Edit reciben el wiring. `empty-note-disabled.xml` muestra el botón externo `enabled="false"` con `content-desc="Leer nota en voz alta"` y cuerpo vacío. |
| 3. GDD narra el documento | Cumplido. `buildGddNarration` reusa `buildGddDocument` (título, ficha en orden, secciones con bodies no vacíos, sin conteos/status/chips/Markdown); tests Android/iOS y smoke GDD. |
| 4. Límite y vacío | Cumplido. `normalizeReadAloudText` hace `trim`, vacío → `null` y recorte exacto a 4096; `ReadAloudService` devuelve `EmptyText` sin llamar al puerto; el botón se deshabilita con texto en blanco. Tests y controles negativos (a)/(c). |
| 5. Una sola reproducción | Cumplido por revisión: `App` tiene un único `readAloudPlayer` y `readAloudState`; `readAloud()` detiene/cancela la lectura previa antes de iniciar la nueva (`App.kt:255-260`). No hay harness UI para forzar la carrera, pero la estructura impide dos players. |
| 6. Resultados tardíos | Cumplido por revisión: `request` se verifica antes de `play` y en `onCompleted`/`onError` (`App.kt:265,269,275,281`); `stopReadAloud()` incrementa el request-id (`App.kt:243-248`). El camino no se ejercita con un TTS fake lento; queda como riesgo residual bajo. |
| 7. Detener, navegar y completar | Cumplido. `button-stop.log` (0.58 s) y `navigation-stop.log` (0.42 s) muestran `NuPlayerDriver ... stop`; `LaunchedEffect(destination)` llama `stopReadAloud()` (`App.kt:326`); completar deja `Detenido` (`App.kt:269-272`); `DisposableEffect` cancela, detiene y libera (`App.kt:790-794`). |
| 8. Error y reintento | Cumplido. `ReadAloudService` mapea `AiClientException`, bytes/mime vacíos y demás fallos a `Failed`; `App` mapea `Failed`/`play == false`/callback de error a `Error`; `ReadAloudButton` muestra "Reintentar" + "No se pudo leer en voz alta." y reintenta con el texto actual. Sin modal/crash. |
| 9. Efímero y fake reproducible | Cumplido. El audio vive en `SynthesizedAudio` en memoria; Android usa `MediaDataSource` y no escribe archivos; `FakeTtsClient` devuelve un WAV PCM 16 kHz/mono/16-bit de ~2 s con `RIFF`/`WAVE` y `audio/wav`, determinista (tests + control negativo (d)). |
| 10. Gate y disciplina | Cumplido. Tests Android/JVM e iOS, `assembleDebug`, link iOS y `CI=true ./init.sh` en verde; sin red, sin deps, esquema v8, sin `.sqm` nuevos, sin cambios en `AiRuntime`/`OpenAiTtsClient`/`init.sh`/`iosApp`. |

## Hallazgos

### Critical

Ninguno.

### High

Ninguno.

### Medium

Ninguno.

### Low (no bloqueantes)

1. **Evidencia visual PNG vs XML inconsistente.** Los tres XML
   (`qa-playing.xml`, `note-playing.xml`, `gdd-playing.xml`) contienen
   `"Detener"`, `"Reproduciendo…"` y
   `content-desc="Detener lectura en voz alta"`. Sin embargo, los tres PNG
   (`qa-playing.png`, `note-playing.png`, `gdd-playing.png`) muestran
   **"Leer en voz alta"** (estado detenido), no el estado de reproducción. Los
   timestamps son del mismo segundo y el WAV fake dura ~2 s, por lo que lo más
   probable es que la captura se haya tomado después de completar; el XML sigue
   siendo evidencia válida del estado activo. Acción recomendada: renombrar la
   evidencia o recapturar screenshot+dump en la misma ventana de reproducción
   para que el nombre `*-playing.png` no contradiga la imagen. No afecta el
   comportamiento de la feature.

2. **Limpieza en `DisposableEffect` sin invalidar el request-id.** `App.kt:782-798`
   cancela el job, detiene y libera el player, pero no incrementa
   `readAloudRequest` (a diferencia de `stopReadAloud()`, `App.kt:243-248`). Con
   los clientes actuales (fake inmediato y HTTP cancelable) la cancelación es
   cooperativa y no hay reproducción residual; si en el futuro se inyecta un
   `TtsClient` que ignore cancelación, un resultado tardío podría llamar a
   `play()` después de `release()`. Endurecimiento recomendado: invalidar el
   request-id en `onDispose` (o llamar a `stopReadAloud()` antes de
   `release()`), y opcionalmente marcar el player como liberado. No es un fallo
   observable con el runtime actual.

3. **Spec histórica de `ai-client-ports` desactualizada.** La línea
   `docs/specs/ai-client-ports.md:206` todavía dice
   `FakeTtsClient → SynthesizedAudio(text.encodeToByteArray(), "audio/mpeg")`,
   mientras que la implementación y `docs/technical-discovery.md` usan WAV
   determinista/`audio/wav`. La spec de `tts-read-aloud` autoriza el cambio y la
   documentación durable principal está actualizada, pero conviene una nota de
   supersesión de una línea en la spec antigua para que un agente nuevo no se
   confunda.

4. **Cobertura de concurrencia por revisión, no por test.** Los escenarios 5 y 6
   (una sola reproducción y descarte de resultados tardíos) y el camino
   `Error` visual no tienen test de UI/E2E; se validaron por revisión de código y
   por el smoke declarado. El repo no tiene harness UI/E2E y la spec justifica el
   smoke manual, por lo que es un riesgo residual aceptado y no un bloqueo.

## Seguridad y privacidad

- No hay secrets, API keys, keystores ni credenciales en el diff; el runtime real
  y su manejo de key no se tocaron.
- El audio TTS es efímero y vive en memoria: no se crean Adjuntos, archivos,
  caché ni filas; Android usa `MediaDataSource` y `MediaPlayer`, sin permisos de
  almacenamiento.
- No se agregan logs de contenido ni telemetría; los errores no incluyen datos
  del usuario.
- El texto de entrada se recorta a 4096 caracteres en `:core`; bytes/mime vacíos
  se rechazan antes de reproducir. No hay rutas de archivo, SQL ni URLs nuevas
  controladas por el usuario.
- La llamada externa sigue siendo el `TtsClient` existente del runtime
  OpenAI-compatible; no se agregó endpoint, credencial ni transporte nuevo.
- El audio fake es un tono determinista, no voz; el MP3 real sigue siendo
  key-gated y fuera del gate.

## Documentación y estado durable

- `feature_list.json` (estado del worktree sobre `763a357`): `tts-read-aloud`
  sigue en `passing` con evidencia y notas alineadas con la spec; los conteos
  (43 `accepted`, 4 `not_started`, 1 `passing`, 1 `blocked`, 49 total) coinciden
  con `PROGRESS.md`. Tras el rebase sobre el `origin/develop` actual, la sesión
  integradora debe reconciliar el conteo con `save-answer-as-note` ya aceptada.
- `PROGRESS.md`: Session 084 registra implementación, verificación, smoke,
  controles negativos, riesgos y próximo paso (`$feature-validator`).
- `ARCHITECTURE.md` y `docs/technical-discovery.md`: describen el caso de uso,
  la narración, el reproductor nativo, el estado único, el request-id, el fake
  WAV y las limitaciones de iOS; no contradicen el código.
- `docs/specs/tts-read-aloud.md`: está completa y refleja el alcance
  implementado.
- `AGENTS.md` no requiere cambio para una feature aún no `accepted`; el cierre
  del backlog queda para la sesión integradora.
- `CONSTRAINTS.md`, `CONTEXT.md` y `DESIGN.md`: no requerían cambios (sin reglas
  nuevas de dominio ni artefactos de diseño).
- Única desactualización detectada: la línea histórica de
  `docs/specs/ai-client-ports.md:206` (Low 3).

## Smoke y limitaciones

No se re-ejecutó el smoke para no interferir con las sesiones concurrentes que
comparten los emuladores; se revisó la evidencia registrada. La evidencia
disponible confirma:

- `qa-playing.xml`, `note-playing.xml` y `gdd-playing.xml`: botón "Detener",
  `content-desc="Detener lectura en voz alta"` y texto "Reproduciendo…".
- `button-stop.log`: `NuPlayerDriver` creado a las 14:33:18.210 y `stop` a las
  14:33:18.791 (~0.58 s).
- `navigation-stop.log`: creado a las 14:33:01.556 y `stop` a las
  14:33:01.972 (~0.42 s).
- `empty-note-disabled.xml`: botón externo `enabled="false"` con
  `content-desc="Leer nota en voz alta"` y `body` vacío.
- `note-playing.png`/`gdd-playing.png`/`qa-playing.png`: estado detenido
  (ver Low 1).

Limitaciones declaradas y evaluadas, no bloqueantes: iOS no reproduce (ADR 0003,
sólo compila); no hubo smoke real key-gated de MP3; el fake WAV es un tono, no
voz; la UI de `Error` no se capturó visualmente y queda cubierta por tests del
core y revisión; no hay E2E persistente.

## Recomendación de estado

`passing` → **`accepted`**. La feature cumple los escenarios 1–10 de la spec, el
gate y los tests independientes están en verde, y el alcance se mantuvo sin
esquema/deps/`init.sh`/`iosApp`/runtime real. La sesión integradora debe
persistir `accepted` en `feature_list.json`/`PROGRESS.md`, rebasar sobre el
`origin/develop` actual (que avanzó a `6a89317` con `save-answer-as-note`) y
resolver los conflictos esperables en `App.kt` y
las pantallas. No se requiere reparación de la feature; los Lows 1–3 son
mejoras de evidencia/documentación y el Low 4 es un riesgo residual aceptado.

## Cierre e integración (Session 086)

- **Rebase:** `origin/develop` `6a89317` (incluye `save-answer-as-note`). Conflictos resueltos conservando **ambas** features en `App.kt`, `AskNotesScreen.kt` y `PROGRESS.md`; el árbol quedó con `tts-read-aloud` en `accepted` y `save-answer-as-note` intacta.
- **Post-rebase:** `:core` Android/JVM e iOS **36 suites/107 tests/0 failures** (`ReadAloud*` 5 + `GddNarration*` 3 por plataforma), `:composeApp` **4/10/0**, `assembleDebug` + link iOS y `CI=true ./init.sh` exit 0; esquema v8 sin `.sqm` nuevos.
- **Low 3 corregido:** `docs/specs/ai-client-ports.md:206` documenta `FakeTtsClient` WAV/`audio/wav` y la supersesión de `audio/mpeg`. Lows 1/2/4 quedan documentados como no bloqueantes (no se recapturaron PNGs ni se cambió el request-id en `DisposableEffect` para no alterar el código validado).
- **PR:** #55 `https://github.com/yavik14/learn-aidevexpert-gamebook/pull/55` contra `develop` (sin mergear).
- **Estado:** `feature_list.json` → `tts-read-aloud` `accepted`; `PROGRESS.md`/`AGENTS.md` actualizados.
