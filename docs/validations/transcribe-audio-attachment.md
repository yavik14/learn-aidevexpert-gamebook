# Validación: transcribe-audio-attachment

- **Feature:** `transcribe-audio-attachment`
- **Spec:** `docs/specs/transcribe-audio-attachment.md`
- **Worktree/rama:** `worktree/transcribe-audio-attachment` / `feature/transcribe-audio-attachment`
- **Base:** `origin/develop` `739a87b` (la rama está 1 commit detrás de `develop`, `9c75be4` `gdd-document-hybrid`)
- **Estado previo:** `passing` (implementada y auto-verificada en Session 074)
- **Fecha:** 2026-10-20
- **Veredicto:** **`accept`** (validación independiente)

## Alcance revisado

Se revisaron la spec completa, el diff contra la base (`739a87b`), los archivos
nuevos/modificados, `feature_list.json`, `PROGRESS.md` (Session 074),
`ARCHITECTURE.md`, `docs/technical-discovery.md` y la evidencia de smoke
declarada en `composeApp/build/smoke-evidence/transcribe-audio-attachment/`.
El validador **no implementó, no commiteó y no pusheó** nada. El único artefacto
nuevo de esta corrida es este documento.

Archivos del diff (9 modificados + 7 nuevos/untracked):

- `:core`: `ai/AudioAttachmentTranscription.kt` (nuevo),
  `diagnostics/Diagnostics.kt` (`AI_TRANSCRIBE`).
- `:core` tests: `TranscribeAudioAttachmentCheck.kt` (common),
  `TranscribeAudioAttachmentAndroidTest.kt`, `TranscribeAudioAttachmentIosTest.kt`.
- `:composeApp`: `AudioTranscription.kt` (nuevo), `NoteEditorScreen.kt`, `App.kt`,
  `MainActivity.kt`, `MainViewController.kt`,
  `androidUnitTest/.../AudioTranscriptionTest.kt` (nuevo).
- Docs/estado: `ARCHITECTURE.md`, `docs/technical-discovery.md`,
  `docs/specs/transcribe-audio-attachment.md` (nuevo), `feature_list.json`,
  `PROGRESS.md`.

## Verificación re-ejecutada

| Check | Resultado |
| --- | --- |
| `CI=true ./init.sh` (invocación exacta, sin `ANDROID_HOME`) | **No ejecutable en este worktree**: aborta en la precondición de SDK porque no hay `local.properties` (gitignored) ni `ANDROID_HOME` exportado. Es una condición de entorno, no un fallo de la feature. |
| `ANDROID_HOME=/Users/javierrodriguez/Library/Android/sdk CI=true ./init.sh` | **exit 0**; `:composeApp:assembleDebug` + `:core:testDebugUnitTest`, `BUILD SUCCESSFUL` (IA fake, sin red ni key). |
| `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks -Pplaybook.ai.mode=fake` | **BUILD SUCCESSFUL** (20 tasks ejecutadas). Android/JVM: **30 suites / 90 tests / 0 failures / 0 errors / 0 skipped**. iOS: **30 suites / 90 tests / 0 failures / 0 errors / 0 skipped**. Incluye `TranscribeAudioAttachmentAndroidTest` y `TranscribeAudioAttachmentIosTest` (1/1 cada uno). |
| `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 -Pplaybook.ai.mode=fake` | **BUILD SUCCESSFUL** (59 tasks up-to-date; el APK resultante se instaló y se usó en el smoke). |
| `./gradlew :composeApp:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` | **BUILD SUCCESSFUL** (39 tasks ejecutadas). **4 suites / 10 tests / 0 failures**; `AudioTranscriptionTest` 3/3. |
| Smoke Android real (ver abajo) | **Ejecutado de forma independiente en `emulator-5558`** con APK e IA fake. |

Conteos obtenidos de los XML de `build/test-results`, no de la salida resumida
del implementer.

## Smoke Android independiente

Se instaló el APK debug actual (`composeApp-debug.apk`, 11.072.130 B) con
`adb -s emulator-5558 install -r`, se limpió el paquete (`pm clear`) y se sembró
con `adb run-as com.playbook.app`:

- `game_project` singleton, 4 Notas y 5 Adjuntos `AUDIO` (`seed.sql` del
  implementer).
- `files/audio/*.m4a` de 13, 20, 15 y 17 bytes; el Adjunto de
  `smoke-note-missing` apunta a un archivo inexistente.
- `PRAGMA user_version` = **8**.

Resultados observados en la UI (dumps `uiautomator`) y en SQLite vía `run-as`:

1. **Nota existente con cuerpo** (`smoke-note-text`, audio 20 B): aparece
   "Audio adjunto" + "Audio 1 · 20 B" + "Transcribir". Al tocar "Transcribir", el
   diálogo muestra la vista previa `[fake] transcripción (20 bytes, audio/mp4)`
   y las acciones **Anexar / Reemplazar / Cancelar**.
2. **Cancelar**: el cuerpo y la DB quedan intactos (`Idea existente del jefe
   final`, status `capturada`).
3. **Anexar**: el borrador queda
   `Idea existente del jefe final\n[fake] transcripción (20 bytes, audio/mp4)` y
   aparece "Transcripción aplicada. Guardá para conservarla."; **la DB no cambia**
   antes de Guardar (sin auto-save).
4. **Guardar**: la DB persiste el `body` transcripto, `status=indexada` y
   `count(embedding)=1`; la lista muestra el badge `indexada`.
5. **Reemplazar** (reabriendo la Nota): el borrador queda exactamente
   `[fake] transcripción (20 bytes, audio/mp4)` y la DB conserva el valor
   anterior hasta Guardar. Salir sin Guardar deja la DB sin cambios.
6. **Cuerpo vacío** (`smoke-note-empty`, 13 B): "Transcribir" aplica directo
   `[fake] transcripción (13 bytes, audio/mp4)` + aviso; la DB sigue con `body`
   vacío antes de Guardar.
7. **Varios audios** (`smoke-note-multi`): etiquetas "Audio 1 · 15 B" y
   "Audio 2 · 17 B"; el `RadioButton` del primero está `checked=true`. Se elige
   el segundo, "Transcribir" produce
   `[fake] transcripción (17 bytes, audio/mp4)` y la DB no cambia.
8. **Archivo ausente** (`smoke-note-missing`, 10 B sin `files/audio/...`):
   aparece "No se pudo leer el audio adjunto" y el botón "Reintentar"; el
   `body`/DB no cambian.

El smoke confirma la UI real para los escenarios 1, 2, 4, 5 y el camino de
guardado/re-indexado del 3. Los escenarios de transcripción vacía, error de
IA/red y cancelación en vuelo no son reproducibles con el `FakeTranscribeClient`
determinista (siempre responde texto y resuelve de inmediato); quedan cubiertos
por los stubs del core y por revisión de código, como ya declaró la evidencia.

## Cumplimiento de la spec

| Escenario | Estado |
| --- | --- |
| 1. Nota con audio y cuerpo vacío | Cumplido. Test del core (bytes/mime exactos y una sola llamada) + smoke (aplicación directa y aviso). |
| 2. Cuerpo con texto → confirmación | Cumplido. Diálogo con vista previa y Anexar/Reemplazar/Cancelar; smoke verificó las tres acciones, incluida la semántica de anexado y el reemplazo exacto. |
| 3. Guardar persiste y re-indexa | Cumplido. El `onSave` existente (`update` + `indexBestEffort`) no se modificó; test del core (INDEXED + embedding) y smoke (DB `indexada`, embedding 1). Cancelar/salir no persiste ni re-indexa. |
| 4. Sin audio o varios audios | Cumplido. La sección sólo se muestra en `isEditing` con Adjuntos `AUDIO`; orden `created_at ASC, id ASC`; default el primero; elegir no lee ni transcribe (el test verifica que no se suman llamadas); smoke verificó el `checked` y la transcripción del segundo. |
| 5. Archivo ausente o ilegible | Cumplido. `read == null` → `AudioUnavailable`, sin llamar al transcriptor; mensaje "No se pudo leer el audio adjunto"; smoke confirmó body/DB intactos. |
| 6. Transcripción vacía | Cumplido. `"   \n  "` → `EmptyTranscription`, sin mutar el body; mensaje y "Reintentar" mapeados en `AudioTranscriptionUiState.Empty`. No verificable por UI con el fake. |
| 7. Error de IA / red | Cumplido. `AiClientException` → `Failed`, diagnóstico `AI_TRANSCRIBE`/`transcription_failed` sin `detail`, mensaje y reintento; body/DB intactos en el test. |
| 8. Cambio de Nota o salida durante la transcripción | Cumplido por revisión: `App` posee un `Job` cancelable, `LaunchedEffect(destination)` lo cancela y limpia el estado, y el resultado se descarta si el destino ya no es la misma Nota; `key(note.id)` reinicia el editor. El fake no permite capturar la carrera en UI. |
| 9. Contrato y determinismo (fake) | Cumplido. Dos transcripciones del mismo archivo devuelven el mismo texto, sin red ni key; los tests corren con `-Pplaybook.ai.mode=fake`. |
| 10. Alcance intacto | Cumplido. Sin `.sqm` nuevos (`1.sqm`..`7.sqm`), esquema v8, sin cambios en `init.sh`, `AiRuntime`, `OpenAiTranscribeClient`, `AiClient`, `TranscribeClient`, `AiPorts`, `AttachmentRepository`, `AttachmentFileStore`, build files ni flags fake/real. `Diagnostics.kt` cambia sólo por la fuente `AI_TRANSCRIBE` que la spec exige. |

## Hallazgos

### High

Ninguno.

### Medium

Ninguno.

### Low (no bloqueantes)

1. **Mensaje de `AudioNotFound`:** un Adjunto borrado, de otra Nota o no-`AUDIO`
   se mapea a `AudioTranscriptionUiState.Unavailable` y muestra "No se pudo leer
   el audio adjunto". La spec no define un mensaje distinto para ese caso y el
   resultado es seguro (no muta el body ni persiste), pero el texto es
   impreciso. Mejora opcional: un estado/mensaje "El audio adjunto ya no está
   disponible".
2. **`CI=true ./init.sh` en un worktree limpio:** la invocación exacta necesita
   `ANDROID_HOME` (o `local.properties`, que no se versiona). Es una condición
   preexistente del harness, no de la feature; con `ANDROID_HOME` el gate da
   exit 0. Conviene que la sesión integradora lo tenga en cuenta al rebasar.
3. **Sin E2E/Compose UI persistente:** el repo no tiene un harness E2E; el smoke
   es manual. Vacío, red y cancelación en vuelo no se ejercitan por UI con el
   fake. La cobertura equivalente está en los stubs del core; riesgo residual ya
   documentado por el implementer.
4. **Lectura local en el dispatcher del llamador:** `AttachmentFileStore.read`
   es síncrona y se invoca dentro del `scope.launch` de `App` (main), igual que
   los demás flujos de IA existentes. Con audios pequeños no es un problema;
   si crecen, mover la lectura a un dispatcher de IO sería una mejora futura.
5. **Rama 1 commit detrás de `develop`:** `develop` avanzó con
   `gdd-document-hybrid` (`9c75be4`), que también toca `App.kt` y
   `NoteEditorScreen.kt`. El rebase/merge queda a cargo de la sesión
   integradora; no afecta la validez de esta feature.

## Seguridad y privacidad

- `AI_TRANSCRIBE` se registra con el resumen estable `transcription_failed` y
  **sin `detail`**; el test verifica además que no aparecen ids de Nota ni de
  Adjunto. El camino de fallo inesperado en `App` también registra sin `detail`.
- No hay secrets, API keys ni keystores en el diff. No se agregaron permisos,
  dependencias ni red al gate; todo corre con IA fake.
- No se agregan Adjuntos, playback ni micrófono; no hay cambios de esquema ni de
  almacenamiento.
- Sin telemetría remota. La lectura de bytes y el texto transcripto no se
  escriben en logs.

## Documentación y estado durable

- `feature_list.json`: `passing` con evidencia y notas alineadas con la spec;
  los conteos de estado (38 `accepted`, 9 `not_started`, 1 `passing`, 1
  `blocked`, 49 total) coinciden con `PROGRESS.md`.
- `PROGRESS.md`: Session 074 registra implementación, verificación y riesgos.
- `ARCHITECTURE.md` y `docs/technical-discovery.md`: describen el servicio, el
  wiring, el editor, la selección determinista, el guardado/re-indexado y la
  privacidad del diagnóstico; no contradicen el código.
- `AGENTS.md` no requiere cambios para una feature aún no aceptada; el próximo
  paso del backlog queda para la sesión integradora.
- La evidencia de smoke declarada existe y es consistente con lo reproducido.

## Recomendación de estado

`passing` → **`accepted`**. La sesión integradora debe persistir el estado y la
evidencia de validación en `feature_list.json`/`PROGRESS.md`, rebasar sobre
`origin/develop` (`9c75be4`) y resolver los conflictos esperables en `App.kt` y
`NoteEditorScreen.kt` antes del merge. No se requiere reparación de la feature.
