# Validación independiente: `ai-client-ports`

- **Feature id:** `ai-client-ports`
- **Spec:** `docs/specs/ai-client-ports.md`
- **Fecha:** 2026-10-16
- **Rol:** validator (independiente del implementer)
- **Estado en `feature_list.json` al validar:** `passing`
- **Veredicto:** **accept**

## Alcance validado

Contratos (puertos) de IA por modelo que `AiClient` no cubre y sus fakes
deterministas en `:core`: `TranscribeClient`/`TtsClient`/`ImageClient`/`JevClient`
+ `SynthesizedAudio`/`GeneratedImage`/`ClassificationSuggestion` (`AiPorts.kt`),
`FakeTranscribeClient`/`FakeTtsClient`/`FakeImageClient`/`FakeJevClient`
(`FakeAiPorts.kt`), contratos genéricos `verify*ClientContract` + `verifyFakeAiPorts`
+ `ConstantTtsClient` test-only (`AiPortsContract.kt`) y runners
`AiPortsAndroidTest`/`AiPortsIosTest`. Sin adaptadores reales, build flag, wiring,
UI ni cambios de esquema/deps.

## Checks re-ejecutados por el validador (no se confió en el resumen)

| Check | Comando | Resultado |
| --- | --- | --- |
| Tests core Android/JVM | `./gradlew :core:testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL (16 tasks); **52 tests, 0 failures, 0 errors** |
| Build Android | `./gradlew :composeApp:assembleDebug` | BUILD SUCCESSFUL (54 tasks) |
| Gate estándar | `./init.sh` | **exit 0**; sin dev servers ni red; `git diff -- init.sh` vacío |
| iOS (no bloqueante) | `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` | BUILD SUCCESSFUL; **52 tests, 0 failures, 0 errors** |
| iOS compile | `./gradlew :core:compileKotlinIosSimulatorArm64` | BUILD SUCCESSFUL (iOS se conserva) |

Conteos desde `core/build/test-results/testDebugUnitTest/*.xml`: **52** tests,
0 failures/errors; `TEST-com.playbook.core.ai.AiPortsAndroidTest.xml` con 6
testcases: `fakeTranscribeSatisfiesContract`, `fakeTtsSatisfiesContract`,
`fakeImageSatisfiesContract`, `fakeJevSatisfiesContract`,
`constantTtsSatisfiesContract`, `fakesAreDeterministicAndOffline`. En iOS,
`AiPortsIosTest` 6/6, 52 en total, 0 failures/errors. Coincide con la evidencia
registrada por el implementer.

## Controles negativos propios (reproducidos y restaurados byte-idéntico)

Diferentes de los del implementer (que usó `Random` en image, image vacío y
transcribe vacío). Baseline y restauración verificados por sha256 de
`FakeAiPorts.kt` = `c31e098e1e7a72318adfdade155916e53c39a1d6d3004d1ca061ee37e294111f`.

1. **`FakeImageClient` ignora el prompt** (constante `"[fake image]"`, sin
   `$prompt`) → cláusula Scenario 3 "prompts distintos producen bytes distintos"
   → `AiPortsAndroidTest.fakeImageSatisfiesContract` **FAILED**
   (`AssertionError` en `AiPortsAndroidTest.kt:19`). Restaurado; hash idéntico y
   test en verde.
2. **`FakeJevClient` con `genres = emptyList()`** → cláusula Scenario 4 "lista de
   strings no vacíos" → `AiPortsAndroidTest.fakeJevSatisfiesContract` **FAILED**
   (`AssertionError` en `AiPortsAndroidTest.kt:24`). Restaurado; hash idéntico y
   suite completa `--rerun-tasks` **52/0** en verde.

Ambos controles prueban que los tests de contrato son sensibles a fallos reales
(no tautológicos) y que la restauración dejó el archivo byte-idéntico.

## Cobertura de escenarios de aceptación 1–8

1. **`transcribe`** — `verifyTranscribeClientContract`: texto no vacío,
   determinista, tolera `ByteArray(0)` — cubierto (`fakeTranscribeSatisfiesContract`).
2. **`tts`** — `verifyTtsClientContract`: `bytes`/`mimeType` no vacíos,
   deterministas — cubierto (`fakeTtsSatisfiesContract`).
3. **`image`** — `verifyImageClientContract`: `bytes`/`mimeType` no vacíos,
   deterministas, prompts distintos → bytes distintos — cubierto
   (`fakeImageSatisfiesContract`; control negativo sensible).
4. **`jev`** — `verifyJevClientContract`: `track ∈ Track.entries ∪ {null}`,
   `genres` no vacíos, determinista; sólo propone (no persiste) — cubierto
   (`fakeJevSatisfiesContract`; control negativo sensible).
5. **Sustituibilidad** — `ConstantTtsClient` (test-only, `commonTest`) cumple el
   mismo `verifyTtsClientContract` que `FakeTtsClient` sin tocar producción —
   cubierto (`constantTtsSatisfiesContract`).
6. **Fakes deterministas y sin red** — `verifyFakeAiPorts` sobre los 4 fakes
   (misma entrada → mismo output; imágenes distintas por prompt); sin `Random`,
   sin red — cubierto (`fakesAreDeterministicAndOffline`).
7. **`AiClient`/esquema sin cambios** — `git diff` vacío para `AiClient.kt`,
   `FakeAiClient.kt`, `AiAvailability.kt`, servicios, `sqldelight/**`,
   `libs.versions.toml`, `core/build.gradle.kts`, `:composeApp`, `iosApp`,
   `init.sh`; sin `.sqm` nuevo (`1.sqm`–`5.sqm`), `user_version`/esquema v6 sin
   cambio (versionado por nº de migraciones); sin deps nuevas — verificado.
8. **Gate honesto** — `:core:testDebugUnitTest`, `:composeApp:assembleDebug` y
   `./init.sh` (exit 0) en verde, Android/JVM sin red — verificado.

Mapeo modelo→puerto completo: `chat`/`embedding` vía `AiClient` (intacto) +
`transcribe`/`tts`/`image`/`jev` en los 4 puertos nuevos. Sin agregado `AiRuntime`
(decisión de scope respetada).

## Scope discipline

- Sólo se crearon los 5 archivos esperados (`AiPorts.kt`, `FakeAiPorts.kt`,
  `AiPortsContract.kt`, `AiPortsAndroidTest.kt`, `AiPortsIosTest.kt`); los demás
  cambios son docs (`ARCHITECTURE.md`, `docs/technical-discovery.md`,
  `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, checkboxes de la spec).
- **Sin** adaptadores reales, build flag, wiring, UI, DI, cambios de esquema,
  deps ni `init.sh` (`git status` sólo muestra los paths esperados).
- La modificación de la spec son sólo checkboxes de `Implementation Tasks`; no
  se alteraron reglas de verificación ni alcance.
- `AiClient.kt`/`FakeAiClient.kt` intactos: no se duplicaron `ChatClient`/
  `EmbeddingClient` (Scope Decision 2 respetada).

## Arquitectura

Contratos como `interface` en `commonMain`; tipos de salida/sugerencia en el
mismo archivo (patrón de `AiClient.kt`); fakes deterministas en `commonMain`
(injectables); contratos genéricos + impl test-only en `commonTest`; runners
Android/JVM e iOS con `runTest`. Concuerda con `ARCHITECTURE.md` (sección AI) y
con los patrones existentes. `SynthesizedAudio`/`GeneratedImage` son `class` (no
`data class`) por contener `ByteArray`, según la spec. `FakeJevClient` usa
`.mod(Track.entries.size)`, que es no-negativo → sin riesgo de índice negativo.

## Seguridad (checklist scoped al diff)

- Sin secrets, tokens, credenciales, URLs privadas ni claves en los archivos
  nuevos (grep de `api_key|secret|token|password|bearer|http(s)://` → sin
  coincidencias).
- Fakes sin red, sin proveedor ni key (escenario 6); sin dependencias nuevas
  (`libs.versions.toml`/`core/build.gradle.kts` sin cambios).
- Sin superficie de entrada/auth/red nueva; sin datos personales. **No se
  encontraron hallazgos de seguridad.**

## Documentación durable

`ARCHITECTURE.md` (sección AI: catálogo puertos por modelo + fakes) y
`docs/technical-discovery.md` ("AI Ports Decisions (ai-client-ports)") actualizados
y consistentes con el código. `AGENTS.md` cambió sólo la línea "Próxima feature en
cola". `CONSTRAINTS.md`/`CONTEXT.md`/`docs/domain-model.md` no requeridos (la spec
lo justifica: reglas de IA ya existentes y capacidades ya descritas). `PROGRESS.md`
y `feature_list.json` con evidencia y estado `passing`.

## E2E

No hay harness E2E persistente en el repo (`init.sh` no levanta simuladores) y la
feature no tiene consumidor ni flujo de usuario/API observable; la spec justifica
la ausencia. Cobertura = tests de contrato del core en Android/JVM e iOS +
compilación de la app.

## Hallazgos

No hay hallazgos Critical/High/Medium. Nota informativa (Low, no bloqueante):
los puertos declaran que los fallos se señalan con `AiClientException` (KDoc),
pero los fakes nunca fallan y no hay un escenario de aceptación que ejercite la
excepción; queda como contrato documentado a verificar cuando existan los
adaptadores reales (`ai-real-*`), fuera del alcance de esta feature.

## Veredicto final

**accept.** La implementación se ciñe a la spec: los escenarios 1–8 pasan, la
verificación se re-ejecutó de verdad (Android 52/0, iOS 52/0, `assembleDebug` y
`init.sh` exit 0), el alcance y la seguridad se respetan, la documentación durable
está actualizada y dos controles negativos propios (distintos de los del
implementer) demuestran que los tests son sensibles a fallos reales. `git status`
quedó limpio de artefactos del validador. `feature_list.json` permanece en
`passing` (el orquestador debe promoverlo a `accepted`).
