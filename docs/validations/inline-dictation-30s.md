# Validación independiente: dictado inline de 30s (`inline-dictation-30s`)

- Fecha: 2026-10-19
- Validador: `feature-validator` (rol independiente del implementer)
- Feature: `inline-dictation-30s` — `feature_list.json` (status `passing`)
- Spec: `docs/specs/inline-dictation-30s.md` (commit `4e48217`)
- Rama/worktree: `feature/inline-dictation-30s` en
  `worktree/inline-dictation-30s`, validada sobre la base `ed48a45`
- Veredicto: **accept**
- Estado recomendado: `accepted` (lo persiste la sesión integradora; este
  validador no modificó `feature_list.json` ni creó commits)

## 1. Alcance validado

Dictado **inline en Nueva nota** (`Create` y `CreateWithImage`): panel con
barra de progreso de 30s, transcripción parcial, Detener/Processing/Error,
auto-stop, `onStarted` aditivo en el puerto y los adaptadores, guard de
generación iOS, `pendingAudio` con reemplazo/conservación, y limpieza sin
huérfanos al cancelar/guardar/salir. Se retiraron `VoiceCaptureScreen.kt`,
`VoiceUiState` y `NotesDestination.VoiceCapture`.

Sólo `:composeApp` + docs/evidencia. Sin cambios de esta feature en `:core`,
esquema/`.sqm`, build files, dependencias, `init.sh` ni `iosApp`.

## 2. Comandos re-ejecutados

| Comando | Resultado |
| --- | --- |
| `./gradlew :composeApp:assembleDebug --rerun-tasks` | **BUILD SUCCESSFUL** en 38s — 56/56 tasks ejecutadas (no `UP-TO-DATE`) |
| `./gradlew :core:testDebugUnitTest --rerun-tasks` | **BUILD SUCCESSFUL** en 12s — **25 suites / 76 tests / 0 failures / 0 errors / 0 skipped** |
| `CI=true ./init.sh` | **exit 0** — gate Android-only (`assembleDebug` + `testDebugUnitTest`), IA fake, sin dev servers ni red |
| `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` | **BUILD SUCCESSFUL** en 28s — 4/4 tasks ejecutadas (iOS no bloqueante) |
| `git diff --check` | **exit 0** |
| `git diff --name-only \| grep -E '(^core/\|\.sqm$\|gradle\|build\.gradle\|settings\.gradle\|init\.sh\|^iosApp/\|libs\.versions)'` | **sin coincidencias** |
| `git status --short` | sólo archivos de la feature + `Dictation.kt` (untracked, esperado) |
| `grep -rn` de `VoiceCaptureScreen`/`VoiceUiState`/`NotesDestination.VoiceCapture` en `composeApp/src` | **sin referencias** (sólo menciones históricas en docs/specs) |

APK debug regenerado (`composeApp/build/outputs/apk/debug/composeApp-debug.apk`,
21:46) y framework iOS regenerado (`ComposeApp.framework`, 21:47).

## 3. Validación contra la spec

### Escenarios de aceptación

| # | Escenario | Resultado | Evidencia |
| --- | --- | --- | --- |
| 1 | Entrar a dictar desde la lista | **Pasa (código + smoke)** | `App.kt:447-452` abre `Create` y llama `startDictation()`; `03-editor-listening-2s.xml` muestra Nueva nota en `Listening` |
| 2 | Control "Dictar" en el editor | **Pasa** | `NoteEditorScreen.kt:281-343`; `Dictation.kt` estados; smoke `03`/`04` |
| 3 | Progreso y parcial en vivo | **Pasa (código); parcial real no observado** | Timer independiente del parcial (`App.kt:257-278`, `294-300`); `03`/`42b` muestran avance 1s→28s |
| 4 | Auto-stop a los 30s | **Pasa (código + smoke con reloj)** | `App.kt:257-278` llama `stopDictation()` una sola vez al cap; `42b` (28s/30s) → `43` (`Processing`) → `43-autostop-state.xml` (`NO_MATCH`) |
| 5 | Detener manualmente | **Pasa (código)** | `stopDictation()` sólo desde `Listening` (`App.kt:244-255`), por lo que es idempotente |
| 6 | Guardar persiste texto + audio | **Pasa (código); audio real no observado** | `saveTextNote` + `attachAudio` (`App.kt:213-231,351-378`); persistencia del Adjunto `AUDIO` ya cubierta por tests aceptados de `voice-attachment-storage` |
| 7 | Transcripción vacía con audio | **Pasa (código + smoke sin audio)** | Audio nuevo descartado y `Error(NO_MATCH)` conservando el pendiente (`App.kt:304-310`); `04`, `42c`, `43`; `checks.txt` con `files/audio` vacío |
| 8 | Cancelar el editor | **Pasa (código + smoke)** | `cancelNewNote()` (`App.kt:339-344`); `34-after-cancel` vuelve a la lista sin Nota; `files/audio` vacío |
| 9 | Re-dictar y reemplazo del audio | **Pasa (código); no ejercido con audio real** | `startDictation()` no toca `pendingAudio` (`App.kt:281-318`); reemplazo sólo con audio nuevo (`App.kt:311-315`) |
| 10 | Degradación sin grabación | **Pasa (código); no ejercido** | `audio == null` conserva pendiente y anexa texto (`App.kt:311-318`); adaptadores entregan `null` sin fallar |
| 11 | Gate honesto | **Pasa** | Tabla de la sección 2 |
| 12 | Permiso denegado | **Pasa (código + smoke)** | `onStarted` no se emite en la ruta denegada (`AndroidVoiceTranscriber.kt:51-79,103-120`; `IosVoiceTranscriber.kt:91-137`); `32-permission-denied.xml` |
| 13 | Re-dictar mientras escucha | **Pasa (código)** | `startDictation()` cancela job + `voiceTranscriber.cancel()` y conserva `pendingAudio` (`App.kt:281-285`) |
| 14 | Alternar imagen durante el dictado | **Pasa (código); no ejercido en smoke** | Sin `DisposableEffect` por destino (`App.kt:426-434`); `pendingAudio` vive en `App`; handlers `Create ↔ CreateWithImage` (`App.kt:498-547`) |
| 15 | Permiso lento | **Pasa (código + smoke)** | Timer sólo en `onStarted` (`App.kt:320-330`); `03-editor-listening-2s.xml` muestra 4s tras diálogo abierto >1 min |
| 16 | Guardar durante el dictado | **Pasa (código + smoke)** | `saveTextNote` cancela la sesión antes de crear (`App.kt:351-354`); `41` + `41-after-save-live.xml`; `checks.txt` (`attachments = 0`, sin archivo) |

### Verificación puntual solicitada

- **Timer sólo en `onStarted`:** confirmado; `startDictation()` deja
  `Listening("", 0)` pero no lanza el timer; `launchDictationTimer()` se invoca
  únicamente desde el callback `onStarted` cuando el estado es `Listening`.
- **Auto-stop una sola vez:** el timer llama `stopDictation()` y retorna; el
  estado pasa a `Processing`, por lo que ticks posteriores salen.
- **`stopDictation()` idempotente:** sólo actúa desde `Listening`.
- **`pendingAudio`:** se conserva al re-dictar/error/vacío, se reemplaza sólo con
  audio nuevo y se borra al cancelar o al adjuntar.
- **Sin huérfanos:** cancelar borra el pendiente; los adaptadores descartan el
  audio parcial al cancelar; guardar adjunta o borra el archivo; si la Nota no se
  crea se descarta el pendiente (y la imagen, si aplica).
- **`canSave`:** exige `dictatedText == null` además de body/imagen
  (`NoteEditorScreen.kt:113-114`).
- **Guard iOS:** `sessionGeneration` invalida autorización, arranque y
  reconocimiento tardíos tras `cancel()` (`IosVoiceTranscriber.kt:65,89-93,
  112-140,171-210`).
- **`onStarted` no se emite con permiso denegado ni errores:** Android no lo
  invoca en la ruta de permiso denegado/`UNAVAILABLE`; iOS sólo tras
  `engine.startAndReturnError(...) == true`.

## 4. Smoke Android: evidencia inspeccionada y límites

Se inspeccionó la evidencia existente en
`composeApp/build/smoke-evidence/inline-dictation-30s/` (15 archivos, no
regenerada por este validador):

- `03` (Listening 4s tras permiso lento), `04` (`NO_MATCH`), `32`
  (`PERMISSION_DENIED`), `33`/`34` (cancelación sin Nota), `41` (Guardar durante
  Listening) + `41-after-save-live.xml`, `42b` (28s/30s) y `43`
  (`Processing` → `NO_MATCH`).
- `checks.txt` confirma `files/audio` vacío y, al cierre, 3 Notas / 0 Adjuntos
  (las Notas son las creadas por el smoke de guardado).

**Límites honestos (coinciden con la spec):**

- No se observó una transcripción real con texto + audio; el entorno no ofrece
  STT inyectable de forma determinista. Los escenarios 6/7/9/10/14 se validan por
  revisión de código y por la persistencia ya aceptada de
  `voice-attachment-storage`, no por un smoke de audio real.
- El auto-stop se ejerció avanzando el reloj del emulador, no con 30s de voz
  real; la evidencia muestra el paso a `Processing` y el posterior `NO_MATCH`.
- No hay harness E2E persistente; la spec lo justifica. `Processing` sin final
  sigue sin watchdog (límite declarado; "Cancelar" es la salida).

## 5. Hallazgos

Sin hallazgos **Critical/High/Medium**.

- **Low — barra visible en 0s durante el diálogo de permiso:** `startDictation()`
  fija `Listening("", 0)` antes de `onStarted`, así que la barra puede verse en
  0s mientras el permiso está abierto. Es consistente con la spec §5 (el cap no
  corre) y no afecta el auto-stop; sólo el fraseo del Scenario 2 podría leerse
  como "la barra aparece después del permiso". No requiere cambio.
- **Low — sin tests automatizados del timer/`pendingAudio`:** la verificación es
  smoke + revisión, como admite el plan de verificación. No bloquea.
- **Low — kill del proceso durante la grabación:** podría dejar un `.m4a`
  parcial sin limpiar; no está cubierto por la spec y es un riesgo preexistente
  del adaptador de grabación. No bloquea.

## 6. Seguridad

Revisión acotada al diff, sin hallazgos:

- Sin secrets, API keys ni keystores.
- Sin permisos nuevos (mismo `RECORD_AUDIO`/`NSMicrophoneUsageDescription`);
  el permiso denegado se maneja con error explícito y sin efectos.
- Sin red ni servicios externos nuevos; el STT nativo es el ya aceptado.
- Archivos de audio en almacenamiento privado con nombre UUID y ruta relativa
  controlada; sin path traversal introducido.
- Diagnósticos sin transcripción, rutas ni contenido; sólo `VOICE_FAILED`/
  `STORAGE_FAILED` con el nombre del error.

## 7. Docs y handoff

- `ARCHITECTURE.md` y `docs/technical-discovery.md` actualizados y coherentes
  con el comportamiento implementado.
- `AGENTS.md` cambió sólo la línea de cola; `feature_list.json` quedó `passing`
  con evidencia; `PROGRESS.md` registra la sesión y los límites.
- `DESIGN.md`/`CONSTRAINTS.md` no requerían cambios.
- **Riesgo de integración:** la rama está detrás de `develop` (`65adc62`,
  `brainstorming-wizard` ya integrado). El rebase tendrá conflictos esperados en
  `App.kt`, `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `ARCHITECTURE.md` y
  `docs/technical-discovery.md`; la sesión integradora debe resolverlos,
  actualizar la cola de `AGENTS.md` y volver a correr el gate antes de marcar
  `accepted`.

## 8. Veredicto

**`accept`.** La implementación satisface la spec, la verificación re-ejecutada
pasa y los límites están declarados de forma honesta. Estado recomendado:
`accepted`; la persistencia del estado y el commit quedan a cargo de la sesión
integradora.
