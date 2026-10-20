# Validación independiente: `ai-real-chat`

- **Feature id:** `ai-real-chat`
- **Spec:** `docs/specs/ai-real-chat.md`
- **Fecha:** 2026-10-20
- **Rol:** validator (independiente del implementer)
- **Worktree:** `worktree/ai-real-chat` (rama `feature/ai-real-chat`)
- **Base validada:** `origin/develop` `6a89317`
- **Estado en `feature_list.json` al validar:** `passing`
- **Veredicto:** **accept**

> Esta validación no modifica `feature_list.json` ni commitea. El estado
> recomendado es `accepted`; la sesión integradora debe persistirlo después de
> rebasar sobre la `develop` actual y repetir la verificación.

## Alcance validado

Chat real de Brainstorming y Q&A sin duplicar `ai-fake-real-switch`:

- `GenerateRequest` con `instructions`/`temperature`/`maxTokens` opcionales.
- Serialización opcional en `OpenAiAiClient.generate` y contexto rotulado
  `Contexto provisto:`.
- Brainstorming `temperature=0.7`/`maxTokens=400`, prompt endurecido y parser
  tolerante a markdown/preámbulos/ruido, con dedupe normalizado.
- Q&A `temperature=0.2`/`maxTokens=600`, `RAG_INSTRUCTIONS` de grounding,
  `prompt=question.trim()` y `context=cuerpos de sources` intactos, normalización
  de fences/encabezados/`**`/`__`, vacío → `Failed` + `AI_QUERY` y refusal
  textual → `Answer`.
- Sin UI nueva, persistencia, fallback a fake, reintentos/streaming, indicador
  fake/real ni IA real en iOS.

## Checks re-ejecutados por el validador

| Check | Comando | Resultado |
| --- | --- | --- |
| Gate estándar | `CI=true ANDROID_HOME=<sdk> ./init.sh` | exit 0; `assembleDebug` + `:core:testDebugUnitTest` verdes; sin dev servers ni simuladores |
| Core Android/JVM | `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test -Pplaybook.ai.mode=fake --rerun-tasks` | BUILD SUCCESSFUL; **34 suites / 103 tests / 0 failures / 0 errors** |
| Core iOS | (mismo comando) | BUILD SUCCESSFUL; **34 suites / 103 tests / 0 failures / 0 errors** |
| App Android + iOS link | `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest :composeApp:linkDebugFrameworkIosSimulatorArm64 -Pplaybook.ai.mode=fake --rerun-tasks` | BUILD SUCCESSFUL; **4 suites / 11 tests / 0 failures** en `:composeApp` |
| Smoke real key-gated | `PLAYBOOK_AI_SMOKE=1 ./gradlew :composeApp:testDebugUnitTest --tests "com.playbook.app.ai.RealAiSmokeTest.smokeRealChat" -Pplaybook.ai.mode=real -Pplaybook.ai.smoke=1 --rerun-tasks` | BUILD SUCCESSFUL; `AI smoke chat OK: suggestions=2, partial=false, answerLen=194, sources=1` |

Los conteos se obtuvieron de `core/build/test-results/*/TEST-*.xml` y
`composeApp/build/test-results/testDebugUnitTest/TEST-*.xml`, no del resumen del
implementer.

El smoke real se ejecutó con la key existente en `local.properties` (archivo
gitignored). La salida sólo incluye conteos/longitudes; no se imprimió ni
registró la key, el prompt, el contexto, las sugerencias ni la respuesta. El
`answerLen` varía entre corridas (evidencia archivada 157; re-ejecución 194), lo
cual es esperable por variabilidad del modelo; las aserciones validan el contrato
(2 ideas, `partial=false`, respuesta no vacía y fuentes), no el texto exacto.

## Controles negativos

- **Reproducido de forma independiente:** se quitó
  `instructions = RAG_INSTRUCTIONS,` de `RagQueryService.kt` y se corrió
  `./gradlew :core:testDebugUnitTest --tests "com.playbook.core.ai.RagQueryAndroidTest.realChatContractAndNormalization" -Pplaybook.ai.mode=fake --rerun-tasks`.
  Resultado: **1 test failed** (`AssertionError` en
  `RagQueryAndroidTest.kt:47`). Archivo restaurado byte-idéntico
  (`sha256 1eeb41f3b36b9ae7df58e490c243a16de8ac7e53c3df73d4ef5d10cf755e23c8`);
  la suite completa volvió a **34/103/0** en Android/JVM e iOS.
- **Evidencia inspeccionada, no re-ejecutada:** (b) sin limpieza de markdown
  falla `BrainstormingSuggestionAndroidTest.realMarkdownAndNoiseAreParsed`;
  (c) respuesta vacía como `Answer` falla
  `RagQueryAndroidTest.realChatContractAndNormalization`. Ambos casos están
  cubiertos por tests en el árbol final y por la evidencia de
  `feature_list.json`/`PROGRESS.md`.

## Escenarios de aceptación 1–10

| # | Cobertura |
| --- | --- |
| 1 | `verifyBrainstormingSuggestionRealFormat`: preámbulo + encabezado + `**1. ...**`/`**2. ...**` → 2 ideas limpias, `partial=false`. |
| 2 | Mismo check: mecánicas `Movimiento`/`Interacción` con markdown → categorías y descripciones limpias. |
| 3 | Checks de parcial (1 idea), ruido/encabezados/separadores (0 → `InvalidResponse`) y dedupe normalizado contra borrador y duplicados. |
| 4 | `verifyRagRealChatContract`: `prompt`/`context` intactos, `instructions=RAG_INSTRUCTIONS`, `0.2`/`600`, normalización y `sources` preservadas. |
| 5 | `verifyRagRealChatContract`: fences/espacios/marcas → `Failed` + `AI_QUERY`; refusal textual → `Answer`. |
| 6 | Tests existentes de `AiClientException` (401/500/timeout/JSON inválido) sanitizados; el servicio mapea a `Failed` sin exponer datos. |
| 7 | Gate con `-Pplaybook.ai.mode=fake` y `CI=true ./init.sh` en verde; el fake sigue devolviendo 2 ideas parseables. |
| 8 | `:core:iosSimulatorArm64Test` (34/103) y `linkDebugFrameworkIosSimulatorArm64` en verde; iOS sin key/red. |
| 9 | Smoke real key-gated fuera del gate: 2 ideas parseables y Q&A con respuesta no vacía + 1 fuente. |
| 10 | Sin cambios en flag, `AiRuntime`, `AiRuntimeConfig`, transporte, roots, `FakeAiClient`, key/release guard, adaptadores nuevos, esquema/`.sqm`, deps, build files, `init.sh`, UI ni `iosApp`. |

## Scope discipline y arquitectura

- `git status`/`git diff`: sólo los archivos de la spec (core chat, adaptador
  `OpenAiAdapters.kt`, tests, smoke test, docs y estado). Sin archivos nuevos de
  producción fuera de alcance.
- `init.sh`, `build.gradle.kts`, `settings.gradle.kts`, `gradle/**`,
  `libs.versions.toml`, `FakeAiClient.kt`, `AiRuntime.kt`, `AiRuntimeConfig.kt`,
  roots, `App.kt`, pantallas, `iosApp/**` y SQLDelight **sin diff**.
- `PlaybookDatabase.Schema.version == 8`; `.sqm` existentes `1..7`, sin `.sqm`
  nuevo.
- No hay persistencia de sugerencias/respuestas, ni fallback a fake, ni
  reintentos/streaming, ni indicador fake/real.
- La extensión del adaptador es la permitida por la spec; no se creó otro
  puerto/adaptador/endpoint/modelo/flag.

## Seguridad (checklist)

- **Secretos:** no hay key, token, password ni keystore en el diff. La key de IA
  está sólo en `local.properties`, que está gitignored; no se imprimió.
- **Datos sensibles:** no se encontró la key, el prompt de smoke ni el cuerpo de
  la Nota/pregunta en `composeApp/build/test-results/**` ni en
  `composeApp/build/smoke-evidence/**`.
- **Logs:** el smoke imprime sólo `suggestions/partial/answerLen/sources`; los
  mensajes de error del adaptador siguen sanitizados (sin body, headers ni key).
- **Superficie externa:** no se agregaron endpoints, URLs, permisos ni
  dependencias; el transporte y la configuración existentes se reutilizan.
- **Riesgo residual:** el contexto RAG son Notas del usuario y el modelo real
  podría recibir instrucciones embebidas en ellas (prompt injection). Es un
  riesgo inherente al RAG ya existente; la instrucción de grounding reduce el
  impacto, pero no lo elimina. No es un bloqueo de esta feature.

## Hallazgos

No hay hallazgos Critical/High/Medium que impidan la aceptación. Notas no
bloqueantes:

- **Low — fences con preámbulo:** `stripWrappingFence` sólo quita un fence cuando
  envuelve toda la respuesta. Si el modelo antepone un preámbulo y luego envuelve
  las ideas en un fence con lenguaje (por ejemplo, un bloque de código markdown),
  la línea del lenguaje podría tratarse como una idea y desplazar una idea real.
  El prompt pide no usar markdown, el escenario 1 (preámbulo + encabezado +
  `**`/`__`) sí está cubierto y el smoke real pasó; se registra como riesgo
  heurístico documentado, no como incumplimiento del contrato.
- **Low — heurísticas de ruido:** una idea legítima que empiece con “Aquí …” o
  que termine en “:” con pocas palabras puede descartarse como preámbulo. Es
  consistente con la lista de preámbulos de la spec y el riesgo está documentado.
- **Low — `__` en código inline:** la normalización quita `__` globalmente, tal
  como pide la spec, pero podría alterar un identificador dentro de código inline.
  No afecta los escenarios de aceptación.
- **Integración:** la rama está 3 commits detrás de `origin/develop`
  (`tts-read-aloud`). Antes de mergear, rebasar y repetir gate/tests/smoke; se
  esperan conflictos sólo en `AGENTS.md`, `PROGRESS.md`, `feature_list.json` y
  documentación compartida.

## Docs y harness

- `ARCHITECTURE.md`, `CONSTRAINTS.md`, `docs/technical-discovery.md`,
  `docs/risks-and-open-questions.md` y `docs/adr/0004-ai-real-integration.md`
  describen el comportamiento implementado y sus riesgos residuales.
- `AGENTS.md` actualiza sólo la cola; `feature_list.json` y `PROGRESS.md`
  registran evidencia verificable y el estado `passing`.
- `init.sh` sigue siendo un gate no bloqueante, Android-only, sin dev servers.
- No hay E2E persistente aplicable: no se agregó un flujo de UI nuevo; la
  cobertura es `:core` Android/iOS + `:composeApp` + smoke key-gated, como
  justifica la spec.

## Estado recomendado

`accepted`. El integrador debe persistir el estado, rebasar la rama sobre la
`develop` actual y volver a correr la verificación después del rebase.
