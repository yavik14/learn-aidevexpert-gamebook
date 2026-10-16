# Feature Implementation Spec: Puertos de IA para los 6 modelos

## Source Feature

- `id`: `ai-client-ports`
- `area`: `ai`
- `depends_on`: `["ai-runtime-decision", "ai-client-interface"]` (ambas `accepted`)
- `status`: `not_started` (al planificar); `source`: `feature_list.json`

> **Alcance.** El MVP tiene un proveedor propio OpenAI-compatible con 6 modelos
> (`chat`, `embedding`, `transcribe`, `tts`, `image`, `jev`; ADR 0004). `AiClient`
> hoy sólo cubre **chat** (`generate`) y **embedding** (`embed`) de forma
> text-only. Esta feature **define los contratos** (puertos) faltantes
> —`transcribe`, `tts`, `image`, `jev`— y sus **adaptadores fake deterministas**
> en `:core`, con tests de contrato. **No** implementa adaptadores reales, ni
> wiring, ni build flag (eso es `ai-fake-real-switch` y las features de cada caso
> de uso).

## Goal

Que `:core` exponga un **contrato por modelo** de IA, sustituible e inyectable, con
un **fake determinista y sin red** para cada uno, de modo que las features de caso
de uso (transcripción, tts, generación de imagen, sugerencia de clasificación) y el
selector fake/real se construyan sobre contratos estables, sin acoplarse al
proveedor. Entregable: `:core` (puertos + fakes + tests). Sin red, sin key, sin
deps nuevas, sin cambio de esquema, sin UI ni wiring.

## Non-Goals

- **Adaptadores reales** (HTTP/SDK) y el **build flag** fake/real con API key:
  `ai-fake-real-switch` y `ai-real-*`.
- **Casos de uso** concretos (transcribir un Adjunto, leer en voz alta, generar y
  adjuntar imagen, sugerir clasificación, chat del Brainstorming/Q&A): sus features.
- Duplicar `chat`/`embedding`: ya los cubre `AiClient`; esta feature **no** crea
  `ChatClient`/`EmbeddingClient` ni cambia `AiClient`/`FakeAiClient`.
- `GenreCatalog`/canonización (es `genres-catalog-persistence`); la sugerencia `jev`
  devuelve strings crudos.
- UI, wiring en `App`, `AiAvailability`, DI, esquema, deps o `init.sh`.

## Scope Decisions

> **Validadas con el autor (2026-10-16)** antes de implementar.

1. **Nombres alineados al modelo (`*Client`)**: `TranscribeClient`, `TtsClient`,
   `ImageClient`, `JevClient` (simétricos con `AiClient` y con los 6 nombres del ADR
   0004). Los fakes/contratos siguen ese nombre (`FakeTranscribeClient`,
   `verifyTranscribeClientContract`, …).
2. **`chat` y `embedding` permanecen en `AiClient`** (`generate`/`embed`): **no** se
   crean `ChatClient`/`EmbeddingClient` ni se modifica `ai-client-interface`
   (aceptada).
3. **Sin agregado `AiRuntime`**: los 4 puertos son interfaces independientes; el
   bundle/injección conjunta es responsabilidad de `ai-fake-real-switch`.

## Job Story

When construya los casos de uso de IA del MVP, I want que el core ya exponga un
contrato claro y sustituible por modelo (con un fake offline), so I can desarrollar
y testear cada superficie (transcribir, tts, imagen, clasificar) sin proveedor real
ni red, y cambiar al real sin tocar el core.

## Users And Permissions

- Desarrollador (autor): único actor; inyecta las implementaciones y corre
  Gradle/tests. MVP single-user local-first; sin auth ni permisos.

## Model → Port Mapping (trazabilidad de los 6 modelos)

| modelo ADR 0004 | puerto / operación | estado |
|---|---|---|
| `chat` | `AiClient.generate(GenerateRequest)` | **existente** (`ai-client-interface`) |
| `embedding` | `AiClient.embed(List<String>)` | **existente** (`ai-client-interface`) |
| `transcribe` | `TranscribeClient.transcribe(audio, mimeType)` | **nuevo** (esta feature) |
| `tts` | `TtsClient.synthesize(text)` | **nuevo** (esta feature) |
| `image` | `ImageClient.generateImage(prompt)` | **nuevo** (esta feature) |
| `jev` | `JevClient.suggest(text)` | **nuevo** (esta feature) |

Todos los fallos del runtime se señalan con el tipo existente `AiClientException`.

## Acceptance Scenarios

### Scenario 1: contrato de `transcribe`
Given `TranscribeClient`, When `transcribe(bytes, "audio/mp4")` con un fake, Then
devuelve un `String` no vacío, determinista (misma entrada → mismo texto) y sin red;
un byte-array vacío no rompe el contrato.

### Scenario 2: contrato de `tts`
Given `TtsClient`, When `synthesize("hola")`, Then devuelve un
`SynthesizedAudio` con `bytes` no vacío y `mimeType` no vacío, determinista.

### Scenario 3: contrato de `image`
Given `ImageClient`, When `generateImage("un jefe final")`, Then devuelve un
`GeneratedImage` con `bytes` y `mimeType` no vacíos, determinista; prompts distintos
producen bytes distintos.

### Scenario 4: contrato de `jev`
Given `JevClient`, When `suggest("un RPG por turnos")`, Then devuelve
una `ClassificationSuggestion` con `track ∈ Track.entries ∪ {null}`, `genres` como
lista de strings no vacíos y determinista; **no** auto-asigna nada (sólo propone).

### Scenario 5: sustituibilidad del contrato
Given el contrato de un puerto nuevo, When se corre con un fake de producción y con
una implementación constante *test-only*, Then ambos lo satisfacen sin tocar
archivos de producción de `:core`.

### Scenario 6: fakes deterministas y sin red
Given los 4 fakes, When se invocan dos veces con la misma entrada, Then el resultado
es idéntico (salvo colisión de hash donde aplique) y no hay red ni key.

### Scenario 7: `AiClient` y el esquema no cambian
Given esta feature, When se inspecciona el diff, Then `AiClient.kt`/`FakeAiClient.kt`
y el esquema SQLDelight quedan **sin cambios**; no hay deps ni `.sqm` nuevos.

### Scenario 8: gate honesto
Given los escenarios 1–7, When corren `:core:testDebugUnitTest`, `:composeApp:assembleDebug`
y `./init.sh`, Then todo pasa en Android/JVM sin red y `./init.sh` termina en `0`.

## Repository Research

### Files Inspected

- `docs/adr/0004-ai-real-integration.md` — 6 modelos, mapeo modelo→caso de uso,
  "extender `AiClient` y/o nuevos puertos (transcribe/tts/image/jev)"; el diseño del
  contrato es **esta** feature.
- `docs/adr/0001-ai-runtime-decision.md`, `docs/technical-discovery.md` (§AI) —
  runtime híbrido cloud-first detrás de puertos; tests/gate siempre fake.
- `CONSTRAINTS.md` (§IA real) — key nunca versionada; tests/gate fake; proveedor
  propio OpenAI-compatible.
- `core/src/commonMain/kotlin/.../ai/{AiClient,FakeAiClient,AiAvailability,
  NoteIndexingService,RagQueryService}.kt` — `AiClient` text-only
  (`embed`/`generate`), `AiClientException`, fake determinista con LCG sobre
  `String.hashCode`.
- `core/src/commonTest/kotlin/.../ai/AiClientContract.kt` — patrón de contrato
  (`verifyAiClientContract`) + implementación test-only `ConstantAiClient` +
  comportamiento específico del fake.
- `core/src/{androidUnitTest,iosTest}/.../ai/AiClient*Test.kt` — runners con
  `@Test`/`runTest`.
- `composeApp/.../voice/VoiceTranscriber.kt` — patrón de puerto para STT **nativo**
  (distinto del `transcribe` de IA: no confundir; ver Non-Goals).
- `core/build.gradle.kts` — `kotlinx-coroutines` ya disponible en `commonMain`/
  `commonTest` (`suspend` + `runTest`).

### Existing Patterns To Follow

- Puertos como `interface` en `commonMain` de `:core`; tipos de dominio/reporte en
  el mismo archivo (como `Embedding`/`GenerateRequest` en `AiClient.kt`).
- Fake determinista en `commonMain` (inyectable, sin red) + contrato en
  `commonTest` corrido desde runners Android/JVM e iOS.
- Implementación test-only en `commonTest` para probar sustituibilidad sin tocar
  producción.
- Errores del runtime con `AiClientException`; métodos `suspend`.
- Sin DI/Flow; sin dependencias nuevas.

### Current Gaps

- No existen contratos para `transcribe`, `tts`, `image` ni `jev`.
- No hay fakes para esos modelos (sólo `FakeAiClient` para chat/embedding).

## Technical Approach

### 1. Contratos nuevos (`core/.../ai/AiPorts.kt`, crear)

```kotlin
package com.playbook.core.ai

import com.playbook.core.model.Track

/** Salida de tts: audio sintetizado (bytes + mime). */
class SynthesizedAudio(val bytes: ByteArray, val mimeType: String)

/** Salida de image: imagen generada (bytes + mime). */
class GeneratedImage(val bytes: ByteArray, val mimeType: String)

/** Propuesta de `jev`: Track y/o Géneros. **Nunca** se auto-asigna. */
data class ClassificationSuggestion(val track: Track?, val genres: List<String>)

/** `transcribe`: convierte audio en texto (el Adjunto AUDIO → body). */
interface TranscribeClient {
    suspend fun transcribe(audio: ByteArray, mimeType: String): String
}

/** `tts`: lee un texto en voz alta (Q&A/Nota/GDD). */
interface TtsClient {
    suspend fun synthesize(text: String): SynthesizedAudio
}

/** `image`: genera una imagen desde un texto (se adjunta a una Nota). */
interface ImageClient {
    suspend fun generateImage(prompt: String): GeneratedImage
}

/** `jev`: sugiere `Track`/`Género` a partir de texto (el usuario confirma). */
interface JevClient {
    suspend fun suggest(text: String): ClassificationSuggestion
}
```

`SynthesizedAudio`/`GeneratedImage` son `class` (no `data class`) porque contienen
`ByteArray` (su `equals` sería por identidad); los tests comparan `bytes`/`mimeType`.

### 2. Fakes (`core/.../ai/FakeAiPorts.kt`, crear)

Deterministas, sin red, sin key:

- `FakeTranscribeClient` → `"[fake] transcripción (${audio.size} bytes, $mimeType)"`
  (determinista por tamaño+mime; tolera `audio` vacío).
- `FakeTtsClient` → `SynthesizedAudio(text.encodeToByteArray(), "audio/mpeg")`.
- `FakeImageClient` → `GeneratedImage("[fake image] $prompt".encodeToByteArray(), "image/png")`.
- `FakeJevClient` → `track` determinista por hash sobre `Track.entries`,
  `genres = listOf(GENRE_HINT)` con una constante **ilustrativa** privada (la
  canonización contra el catálogo es del consumidor / `genres-catalog-persistence`;
  la sugerencia es sólo una propuesta).

### 3. Tests (`core/src/commonTest/.../ai/AiPortsContract.kt`, crear)

- `verifyTranscribeClientContract(port)`, `verifyTtsClientContract(port)`,
  `verifyImageClientContract(port)`, `verifyJevClientContract(port)`
  (escenarios 1–4, 6).
- Una implementación *test-only* (p. ej. `ConstantTtsClient`) que cumpla el
  contrato para demostrar sustituibilidad (Scenario 5), sin tocar producción.
- Runners `AiPortsAndroidTest`/`AiPortsIosTest` (`@Test` + `runTest`).

## Expected File Changes

- `core/src/commonMain/kotlin/com/playbook/core/ai/AiPorts.kt` — **crear**; 4
  interfaces + `SynthesizedAudio`/`GeneratedImage`/`ClassificationSuggestion`.
- `core/src/commonMain/kotlin/com/playbook/core/ai/FakeAiPorts.kt` — **crear**; 4 fakes.
- `core/src/commonTest/kotlin/com/playbook/core/ai/AiPortsContract.kt` — **crear**;
  contratos + comportamiento de fakes + impl test-only.
- `core/src/androidUnitTest/kotlin/com/playbook/core/ai/AiPortsAndroidTest.kt` — **crear**.
- `core/src/iosTest/kotlin/com/playbook/core/ai/AiPortsIosTest.kt` — **crear** (iOS
  debe compilar; no es requisito del gate Android-only).
- `ARCHITECTURE.md`, `docs/technical-discovery.md` — **update**.
- `AGENTS.md` — update **acotado** (sólo "próxima feature en cola").
- `feature_list.json`, `PROGRESS.md` — update al cerrar.

No se esperan cambios en `AiClient.kt`, `FakeAiClient.kt`, `AiAvailability.kt`,
`NoteIndexingService.kt`, `RagQueryService.kt`, esquema/`.sqm`, `:composeApp`,
`iosApp/**`, `gradle/libs.versions.toml`, `core/build.gradle.kts` ni `init.sh`.

## Visual Design Impact

- UI involved: no. Design source: not applicable.
- Screens or states affected: ninguno (sin UI ni wiring).
- New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — sección AI: catálogo de puertos por modelo
  (chat/embedding en `AiClient`; `TranscribeClient`/`TtsClient`/
  `ImageClient`/`JevClient`) + fakes.
- `CONSTRAINTS.md`: **not needed** — las reglas (key no versionada; tests/gate fake;
  proveedor propio) ya existen (§IA real, ADR 0004).
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola".
- Other docs: `docs/technical-discovery.md` **update** (nueva "AI Ports Decisions
  (ai-client-ports)": contratos, fakes, mapeo modelo→puerto). ADR 0004 queda
  `propuesto` (su promoción y los adaptadores reales son de `ai-fake-real-switch`/
  `ai-real-*`). `CONTEXT.md`/`docs/domain-model.md` **not needed** (ya describen las
  capacidades de IA). `feature_list.json`/`PROGRESS.md` **update** al cerrar.

## Implementation Plan

1. Crear `AiPorts.kt` (4 interfaces + tipos de salida/sugerencia).
2. Crear `FakeAiPorts.kt` (4 fakes deterministas, sin red).
3. Crear `AiPortsContract.kt` (escenarios 1–6) + runners Android/iOS.
4. Correr `:core` en Android/JVM (y iOS si el entorno lo permite).
5. Correr verificación + controles negativos, actualizar docs y cerrar.

## Implementation Tasks

- [x] `AiPorts.kt`: `TranscribeClient`/`TtsClient`/`ImageClient`/
      `JevClient` + `SynthesizedAudio`/`GeneratedImage`/
      `ClassificationSuggestion`.
- [x] `FakeAiPorts.kt`: los 4 fakes deterministas.
- [x] `AiPortsContract.kt`: contratos 1–4/6 + impl test-only (5).
- [x] Runners `AiPortsAndroidTest`/`AiPortsIosTest`.
- [x] Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`; `AGENTS.md` (cola).
- [x] Correr verificación + controles negativos y registrar evidencia.
- [x] Actualizar `feature_list.json` y `PROGRESS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → `AiPortsAndroidTest` +
  `AiClientAndroidTest` + resto del core en verde, 0 failures.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (el `:core` con los
  nuevos puertos compila en Android).
- `./init.sh` → exit 0; gate Android-only, sin dev servers ni red.
- `git status`/`git diff` → `AiClient.kt`/`FakeAiClient.kt` sin cambios;
  `Schema.version` sin cambios; sin `.sqm` ni deps nuevas.
- **iOS (no bloqueante, macOS):** `./gradlew :core:iosSimulatorArm64Test` y
  `:composeApp:linkDebugFrameworkIosSimulatorArm64` compilan (iOS se conserva).
- **Controles negativos** (restaurar byte-idéntico y re-verificar en verde):
  (a) hacer un fake no determinista (p. ej. incluir `Random`) → falla el Scenario 6;
  (b) `FakeImageClient` devolviendo `GeneratedImage(ByteArray(0), "")` → falla el
  Scenario 3;
  (c) `TranscribeClient` devolviendo `""` → falla el Scenario 1.
- E2E persistente: **no aplica** (sin consumidor ni flujo de usuario/API observable;
  no hay harness E2E y `init.sh` no levanta simuladores). Cobertura: tests del core
  en Android/JVM + compilación de la app.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` con `AiPortsAndroidTest` + resto en verde
  (nombres y conteos) y de `:composeApp:assembleDebug`.
- `git diff` vacío para `AiClient.kt`/`FakeAiClient.kt`, esquema, build files e
  `init.sh`; sin deps ni `.sqm` nuevos.
- Controles negativos (a)/(b)/(c) reproducidos y restaurados, con el test que falla.
- `./init.sh` → exit 0, sin procesos de larga duración ni red.
- Confirmación de que no hay adaptadores reales, keys, red ni wiring.

## Validator Checklist

- [ ] Alcance respetado: sólo contratos + fakes + tests; **sin** adaptadores reales,
      build flag, wiring, UI ni cambios de esquema/deps.
- [ ] Escenarios de aceptación 1–8 pasan.
- [ ] Mapeo modelo→puerto completo (chat/embedding vía `AiClient`; los 4 nuevos).
- [ ] `AiClient`/`FakeAiClient`/`AiAvailability`/servicios sin cambios; errores usan
      `AiClientException`.
- [ ] `:composeApp` compila; `init.sh` sin cambios; iOS sigue compilando.
- [ ] `ARCHITECTURE.md` y `docs/technical-discovery.md` actualizados; `AGENTS.md`
      sólo la línea de cola.
- [ ] `feature_list.json` y `PROGRESS.md` actualizados con la evidencia.
- [ ] No hay E2E persistente y la spec lo justifica.
