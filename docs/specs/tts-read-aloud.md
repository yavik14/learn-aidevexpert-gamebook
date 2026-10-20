# Feature Implementation Spec: Lectura en voz alta (TTS)

## Source Feature

- `id`: `tts-read-aloud` · `area`: `ai`
- `depends_on`: `["ai-fake-real-switch"]` (ambas `accepted`)
- `status`: `not_started` (al planificar); `source`: `feature_list.json`
- Planificado sobre `origin/develop` `763a357`.

> **Alcance.** Leer en voz alta la **respuesta del Q&A**, el **cuerpo de la Nota** y
> la **narración del GDD** con `TtsClient.synthesize`. Un caso de uso en `:core`
> normaliza el texto (vacío/4096) y un reproductor nativo en `:composeApp`
> reproduce audio **efímero en memoria**. Sin Adjunto, archivos, caché, red en gate
> ni cambios al runtime real. Las 3 superficies caben juntas: comparten modelo,
> servicio, reproductor y estado; **no se divide**.

## Goal

Que cualquiera de esos textos se escuche con **una sola reproducción a la vez**,
estados explícitos (`Preparando`/`Reproduciendo`/`Detenido`/`Error`) y limpieza al
detener, navegar o cerrar. Entregable: `:core` (texto/servicio + fake reproducible +
tests Android/JVM e iOS) y `:composeApp` (botones, estado en `App`, reproductor
`expect`/`actual`, wiring y smoke Android). Sin esquema, deps, `.sqm`, `init.sh` ni
adaptadores reales.

## Non-Goals

- Transcripción/grabación, Adjuntos de audio, background, notificaciones,
  play/pausa/seek, selección de voz/velocidad/idioma, streaming, **audio focus**.
- Persistir/cachear audio ni archivos temporales; descargar o guardar el MP3.
- Cambiar `TtsClient`/`SynthesizedAudio`, `AiRuntime`, `OpenAiTtsClient`, el build
  flag, el esquema (v8), dependencias, `init.sh` o `iosApp`.
- Rediseñar pantallas, lectura desde la lista o de Adjuntos. iOS sólo compila
  (ADR 0003); sin verificación funcional.

## Scope Decisions

> **Vinculantes para el implementer.**

1. **Superficie y texto exacto** (siempre `trim` + máximo 4096):
   - **Q&A:** `AskUiState.Answered.text`; no se leen pregunta ni citas.
   - **Nota:** `body` actual del borrador del editor (**Nueva nota** y **Edición**);
     no se leen `track`, Géneros, estado ni Adjuntos.
   - **GDD:** `buildGddNarration(project, notes)` (ver §3): documento completo.
2. **Botón `ReadAloudButton` compartido** en cada superficie. Detenido: **"Leer en
   voz alta"**, `contentDescription` "Leer respuesta/nota/GDD en voz alta",
   habilitado sólo con texto no vacío. Preparando/reproduciendo: **"Detener"**,
   `contentDescription` "Detener lectura en voz alta". Error: **"Reintentar"**,
   `contentDescription` "Reintentar lectura en voz alta". El estado se anuncia
   además con texto ("Preparando audio…", "Reproduciendo…", "No se pudo leer en voz
   alta."), nunca sólo color (`DESIGN.md`).
3. **Narración del GDD.** `buildGddNarration(project, notes)` reusa
   `buildGddDocument` y devuelve, determinista: cabecera `GDD — <título>` o `GDD`
   (sólo si hay contenido); si hay campos, `Proyecto` y `"<label>: <value>"` por
   campo (saltos internos colapsados); por sección con algún `body` no vacío,
   `section.title` y sus cuerpos no vacíos en el orden existente (Notas
   sólo-imagen omitidas). Sin conteos, `status`, Géneros, chips ni Markdown.
   `""` si no hay título, campos ni cuerpos narrables.
4. **Límite/vacío.** `normalizeReadAloudText(raw): String?` hace `trim`; vacío →
   `null`; más de `READ_ALOUD_MAX_CHARS = 4096` → corta a **exactamente 4096**.
   Con vacío el botón queda deshabilitado y `ReadAloudService` **no llama** al
   cliente.
5. **Una sola reproducción.** `App` posee un único `ReadAloudUiState`, un único
   `ReadAloudPlayer` y un **request-id monotónico**; iniciar otra lectura detiene y
   cancela la anterior, y todo resultado viejo se descarta antes de `play` y en los
   callbacks. Sólo la superficie objetivo muestra estados activos.
6. **Detener/limpiar.** `stopReadAloud()` cancela el job, llama `player.stop()` y
   vuelve a `Detenido`. Se invoca al tocar "Detener", al cambiar `destination`, al
   completar/fallar y en `DisposableEffect(Unit).onDispose` con `player.release()`.
7. **Error no bloqueante.** `AiClientException`, audio/mime vacío, fallo del player
   o `play == false` → `Error` textual en esa superficie; el resto sigue usable y
   "Reintentar" repite desde cero. Sin modal, crash ni datos del usuario en logs.
8. **Reproductor nativo.** `ReadAloudPlayer` + `rememberReadAloudPlayer()` como
   `expect`/`actual` en `:composeApp` (patrón `GddSharer`):
   `play(audio, onCompleted, onError): Boolean`, `stop()`, `release()`. Android:
   `MediaPlayer` + `MediaDataSource` en memoria. iOS: actual mínimo que compila y
   devuelve `false`/`onError` (sin playback, iOS congelado).
9. **Fake reproducible.** `FakeTtsClient` devuelve un **WAV PCM válido y
   determinista** (16 kHz, mono, 16-bit, ~2 s, tono suave) con
   `mimeType = "audio/wav"` en vez de texto UTF-8/`audio/mpeg`. Motivo: smoke
   Android reproducible sin red; no simula voz. Sólo se ajusta la aserción de mime
   en `verifyFakeAiPorts`; el contrato del puerto no cambia.

## Acceptance Scenarios

### Scenario 1: Q&A lee la respuesta
Given `Answered(text)` no vacío, Then `synthesize` recibe una vez `text.trim()` (máx.
4096) y al reproducir se ve "Reproduciendo…"/"Detener"; no se leen pregunta ni
fuentes. En los demás estados no hay botón habilitado.

### Scenario 2: Nota lee el cuerpo
Given Nueva nota o Edición con `body` no vacío, Then se envía el `body` actual
(trim/4096), sin metadata ni Adjuntos. Con `body` vacío (Nota sólo-imagen) el botón
queda deshabilitado y no hay llamada.

### Scenario 3: GDD narra el documento
Given Proyecto con título/campos y Notas de varios Tracks (una sin body), Then el
texto es exactamente `buildGddNarration(project, notes)`: cabecera, campos en orden
y secciones con cuerpos no vacíos, sin conteos/chips/`status`/Markdown. Sin
contenido narrable → deshabilitado y sin llamada.

### Scenario 4: límite y vacío
Given 5000 caracteres, Then `synthesize` recibe exactamente 4096. Given texto en
blanco, Then `normalizeReadAloudText` → `null`, el servicio → `EmptyText` sin
invocar al cliente y la UI no bloquea.

### Scenario 5: una sola reproducción
Given una lectura en Q&A, When se inicia otra en Nota/GDD, Then el player anterior
recibe `stop()`, su job se cancela y sólo la nueva superficie muestra estados
activos. Nunca hay dos audios ni estados cruzados.

### Scenario 6: resultados tardíos
Given una lectura cancelada o reemplazada, When su `synthesize` resuelve después,
Then el request-id la descarta: no llama a `play`, no suena y no pisa el estado de
la lectura vigente.

### Scenario 7: detener, navegar y completar
Given "Reproduciendo…", When se toca "Detener", se navega o se sale de composición,
Then el player se detiene, el job se cancela, el estado vuelve a `Detenido` y no
suena nada más; completar el audio también deja `Detenido`.

### Scenario 8: error y reintento
Given `AiClientException`, audio/mime vacío o player que falla/devuelve `false`,
Then se muestra "No se pudo leer en voz alta." con "Reintentar", la UI sigue usable
y reintentar empieza de cero. Sin crash ni modal.

### Scenario 9: efímero y fake reproducible
Given cualquier lectura, no hay archivos/filas/caché (todo en memoria). Given
`FakeTtsClient` dos veces con el mismo texto, Then bytes idénticos con `RIFF`/`WAVE`,
mime `audio/wav` y reproducibles por Android.

### Scenario 10: gate y disciplina
Given la implementación, When corren tests `:core` Android/iOS, `assembleDebug`,
link iOS y `CI=true ./init.sh`, Then todo pasa sin red con IA fake, esquema v8 sin
`.sqm` nuevos, sin deps ni cambios en `AiRuntime`/`OpenAiTtsClient`/build files y
gate `0`.

## Repository Research

- Specs: `ai-client-ports`/`ai-fake-real-switch` (contrato/fake/real), `transcribe-audio-attachment`/`image-generation-from-text`/`ai-suggest-classification` (servicio `:core`, estados, wiring, tests), `create-text-note`/`inline-dictation-30s`/`voice-attachment-storage` (editor/ciclo de vida) y `rag-answer-citations`/`gdd-document-hybrid` (Q&A/GDD).
- Código: `AiPorts`/`FakeAiPorts`, `AiRuntime`/`OpenAiAdapters` (no tocar), `GddDocument`, `Note`/`GameProject`, y roots Android/iOS.
- UI: `App`, `AskNotesScreen`, `NoteEditorScreen`, `GddScreen`, `GddSharer` + actuals (patrón `expect`/`actual`).
- Docs: `DESIGN.md` (tts, no sólo color) y `docs/technical-discovery.md` (4096, fake/real, sin playback).

## Technical Approach

### 1. `:core` — texto y servicio (`ai/ReadAloud.kt`, crear)

```kotlin
const val READ_ALOUD_MAX_CHARS: Int = 4096
fun normalizeReadAloudText(raw: String): String?
sealed interface ReadAloudResult {
    data class Ready(val audio: SynthesizedAudio) : ReadAloudResult
    data object EmptyText : ReadAloudResult
    data class Failed(val cause: Throwable?) : ReadAloudResult
}
class ReadAloudService(private val ttsClient: TtsClient) {
    suspend fun synthesize(rawText: String): ReadAloudResult
}
```

`synthesize`: normaliza; `null` → `EmptyText` **sin llamar** al puerto; si no llama
a `ttsClient.synthesize(text)`, exige `bytes`/`mimeType` no vacíos; `AiClientException`
y demás `Throwable` → `Failed` (propaga `CancellationException`). Sin persistencia,
diagnóstico de contenido ni conocimiento de UI/plataforma.

### 2. `:core` — narración (`gdd/GddNarration.kt`, crear)

`fun buildGddNarration(project: GameProject?, notes: List<Note>): String` según
Scope Decision 3, reusando `buildGddDocument`. Pura, determinista, sin mutar
entradas; el límite 4096 se aplica en `normalizeReadAloudText`/servicio.

### 3. `:core` — fake (`ai/FakeAiPorts.kt`, modificar sólo `FakeTtsClient`)

WAV PCM 16-bit/16 kHz/mono, ~2 s, tono determinista de amplitud baja y cabecera
`RIFF`/`WAVE`; `audio/wav`. Sin `Random`, red ni archivos. Actualizar la aserción de
mime en `AiPortsContract.kt` y verificar la cabecera WAV.

### 4. `:composeApp` — reproductor, estado y botones

- `ReadAloudPlayer.kt` (commonMain): interfaz, `rememberReadAloudPlayer()`,
  `ReadAloudSurface` (`QA_ANSWER`, `NOTE`, `GDD`) y `ReadAloudUiState` (`Detenido`,
  `Preparando(surface)`, `Reproduciendo(surface)`, `Error(surface)`).
- Android: `MediaPlayer` + `MediaDataSource` en memoria, `prepareAsync`/`start`,
  listeners de completado/error y `stop`/`release` idempotentes, sin archivos.
- iOS: actual mínimo que compila; `play` → `false` (UI muestra `Error`),
  `stop`/`release` no-op. Sin playback.
- `App`: recibe `ttsClient`, crea `ReadAloudService` y `rememberReadAloudPlayer()`;
  estado/job/request-id; `readAloud(surface, raw)` y `stopReadAloud()` según Scope
  Decisions 5–7; extiende `LaunchedEffect(destination)` y `DisposableEffect(Unit)`.
- `ReadAloudButton` compartido: lo usan `AskNotesScreen` (tarjeta Respuesta),
  `NoteEditorScreen` (bajo el `body`) y `GddScreen` (fila superior, con
  `remember(project, notes) { buildGddNarration(...) }`).
- Roots: `MainActivity` pasa `aiRuntime.ttsClient`; `MainViewController` pasa
  `runtime.ttsClient`. `AiRuntime` no cambia.

## Expected File Changes

- `docs/specs/tts-read-aloud.md` — crear (este spec).
- `core/.../ai/ReadAloud.kt` y `core/.../gdd/GddNarration.kt` — crear.
- `core/.../ai/FakeAiPorts.kt` — sólo `FakeTtsClient`; `AiPortsContract.kt` — ajustar mime y verificar WAV.
- Checks `ReadAloudCheck`/`GddNarrationCheck` en `commonTest` + runners Android/JVM e iOS — crear.
- `composeApp/.../ReadAloudPlayer.kt` + `ReadAloudButton.kt` (o uno solo) y actuals Android/iOS — crear.
- `composeApp/.../{App,AskNotesScreen,NoteEditorScreen,GddScreen}.kt` y `Main{Activity,ViewController}.kt` — modificar (estado/botones, `ttsClient`).
- Cierre (fuera del planner): docs durables, `feature_list.json`, `PROGRESS.md`, `AGENTS.md`.

**Sin cambios:** `AiPorts.kt`, `SynthesizedAudio`, `AiRuntime.kt`,
`OpenAiAdapters.kt`, `AiRuntimeConfig.kt`, esquema/`.sqm` (v8), deps, `init.sh`,
`iosApp/**`, `GddDocument.kt`, repositorios y pantallas no listadas.

## Visual Design Impact

- UI: sí; `DESIGN.md` (tts, texto además de color, targets ≥48 dp). `AskNotesScreen`, `NoteEditorScreen` y `GddScreen` ganan botón de 4 estados; sin rediseño, tokens ni artefactos nuevos.

## Durable Documentation Impact

- `ARCHITECTURE.md`: caso de uso, reproductor nativo y estado único en `App`.
- `docs/technical-discovery.md`: 4096, fake WAV, audio efímero, iOS mínimo, request-id y stop.
- `feature_list.json`/`PROGRESS.md`: al implementar/validar; `CONSTRAINTS`/`CONTEXT`/`DESIGN`: not needed.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` y `:core:iosSimulatorArm64Test --rerun-tasks` → `ReadAloud`, `GddNarration`, `FakeTtsClient` y contrato previo verdes, 0 failures.
- `./gradlew :composeApp:assembleDebug` y `:composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
- `CI=true ./init.sh` → 0, sin red/dev servers; `Schema.version == 8`, `.sqm` 1..7, sin deps ni cambios en `AiRuntime`/`OpenAiTtsClient`/build files.
- Controles negativos (restaurar): (a) no truncar → falla Scenario 4; (b) narrar Notas sin body → falla Scenario 3; (c) llamar con texto vacío → falla Scenario 4; (d) fake sin RIFF → falla Scenario 8.
- Smoke Android por superficie: Q&A, Nota con body y GDD ficha+Notas → Leer/Detener; navegar detiene; completar vuelve a detenido; audio audible sin archivos. Evidencia en `composeApp/build/smoke-evidence/tts-read-aloud/`; sin `adb`/emulador, salvedad honesta (no fabricar evidencia). Smoke real de MP3 **opcional key-gated**, fuera del gate y no bloqueante.
- iOS: sólo compilación (congelado). E2E persistente: no aplica (no hay harness UI/E2E y `init.sh` no levanta simuladores).

## Evidence To Capture

- Salidas de tests `:core` Android/JVM e iOS, `assembleDebug`, link iOS y `CI=true ./init.sh` 0; esquema v8 sin `.sqm`, sin deps, sin archivos/caché y sin cambios en `AiRuntime`/`OpenAiTtsClient`/`AiPorts`.
- Smoke Android por superficie, stop al navegar/completar, ausencia de audio simultáneo/resultados tardíos y controles negativos reproducidos/restaurados.

## Validator Checklist

- [ ] Escenarios 1–10 pasan; 3 superficies con un solo servicio/player, request-id y una reproducción a la vez.
- [ ] Texto por superficie correcto; vacío deshabilitado; 4096 exactos; estados/`contentDescription` completos.
- [ ] Error no bloqueante y reintentable; stop al tocar, navegar, completar y `DisposableEffect` (sin audio residual).
- [ ] Audio efímero (sin Adjuntos, archivos, caché, red en gate ni persistencia).
- [ ] `FakeTtsClient` determinista y reproducible; contrato actualizado sin tocar puerto ni runtime real.
- [ ] Sin esquema/deps/`init.sh`/`iosApp`; iOS compila; docs y evidencia; E2E persistente justificado; audio focus y timeout propio declarados como no-goal/límite.

## Implementation Notes / Risks

- `MediaDataSource` requiere API 23+ (minSdk 24); si el emulador no reproduce el WAV, la UI debe mostrar `Error`, no crashear.
- El fake cambia a `audio/wav` (ningún consumidor previo dependía del mime); el adaptador real sigue en MP3 y no se toca.
- iOS no reproduce (actual mínimo): límite aceptado del iOS congelado. No hay pausa/reanudación ni audio focus; no hay timeout propio de síntesis (los del transporte + "Detener" son la salida).
