# Feature Implementation Spec: Chat real (Brainstorming + Q&A)

## Source Feature

- `id`: `ai-real-chat`; `area`: `ai`; `status`: `not_started` (al planificar).
- `depends_on`: `["ai-fake-real-switch", "brainstorming-wizard", "rag-query"]` (las tres `accepted`); `source`: `feature_list.json`.
- WIP=1: una feature por sesión/worktree; no se tocan `tts-read-aloud` ni `main-screen-playbook-shell`.

> **Alcance.** Con `playbook.ai.mode=real` y la key fuera del repo, Brainstorming y Q&A usan el modelo `chat`; `fake` sigue siendo el default del gate y de iOS. No se reimplementa la selección fake/real ni el adaptador real (los entrega `ai-fake-real-switch`): se endurecen prompt/parsing/normalización, se agregan parámetros por superficie y se define parcial/error, con smoke real key-gated **fuera del gate**.

## Goal

`AiRuntime.aiClient` ya llega a `BrainstormingSuggestionService` y `RagQueryService`; en real ambos llaman a `OpenAiAiClient.generate`. Falta que esa llamada sea usable con respuestas reales: Q&A anclado al contexto, sugerencias limpias aunque el modelo use markdown/ruido, respuesta vacía tratada como fallo y degradación sin fallback a fake ni persistencia.

## Límite Con `ai-fake-real-switch` (No Duplicar)

- **Se reutiliza tal cual:** flag `playbook.ai.mode`, `AiRuntimeConfig`, `AiRuntime`, `openAiAiRuntime`, `OpenAiAiClient`, `UrlConnectionHttpTransport`, wiring de roots, `FakeAiClient`, key/release guard y `AiClientException`.
- **Sólo se toca:** campos opcionales en `GenerateRequest`, su serialización en `OpenAiAiClient.generate`, y prompt/parsing/normalización en `BrainstormingSuggestions.kt` y `RagQueryService.kt`. No se crea otro puerto/adaptador/endpoint/modelo/flag.

## Non-Goals

- Streaming, reintentos/backoff, cacheo, batching, multi-proveedor, costo/cuota o sonda de conectividad.
- Persistir sugerencias/respuestas, crear Notas automáticamente, tocar esquema (v8), `.sqm`, repositorios o `GameProject`.
- Cambiar retrieval/RAG/`maxSources`/umbral/scoring/citas o rediseñar wizard, Q&A, `App.kt` o pantallas.
- Indicador visual fake/real (UI de shell o follow-up), IA real en iOS (ADR 0003), fallback automático a fake.

## Job Story / Users

When tengo el modo real activado, I want que Brainstorming y Q&A devuelvan resultados limpios y acotados aunque el modelo incluya markdown o ruido, so I can usar IA real sin perder control, sin respuestas inventadas y sin romper el flujo si algo falla. Actor: autor/desarrollador (single-user local-first), con key fuera del repo; gate/tests sin red, key ni permisos nuevos.

## Scope Decisions

1. **Q&A anclado sin romper `rag-query`:** `prompt` sigue siendo la pregunta y `context` los cuerpos de las fuentes; el grounding viaja en `GenerateRequest.instructions` (responder sólo con el contexto, no usar conocimiento externo, no inventar, decir si no alcanza, no enumerar fuentes, español, texto plano).
2. **Parámetros por superficie:** Brainstorming `temperature=0.7`, `maxTokens=400`; Q&A `temperature=0.2`, `maxTokens=600`. Constantes junto a cada servicio (no `BuildConfig`/`AiRuntimeConfig`); el adaptador los envía sólo si están presentes.
3. **Parsing tolerante:** acepta encabezados, `**`/`__`/backticks, viñetas, numeración, comillas y preámbulos; descarta ruido y normaliza espacios. No reconstruye dos ideas en una línea: si no hay 2 usables → parcial/inválido.
4. **Dedupe:** ideas y borrador se comparan con `trim` + espacios colapsados + lowercase (cierra el Low de `brainstorming-ai-suggestions`).
5. **Normalización Q&A:** `trim`, quitar un code fence envolvente, encabezados (`#`) y marcas `**`/`__`, colapsar `\n{3,}` a `\n\n`; vacío/sin sustancia → `RagAnswer.Failed` + `AI_QUERY`; un refusal textual (“no tengo información suficiente”) es un `Answer` normal. No se reinterpreta markdown (enlaces/código) ni se eliminan citas que el modelo escriba, pero el prompt pide no enumerarlas.
6. **Errores:** fallo real → `Failed`; `InvalidResponse` → aviso existente; `content` `null`/no-string/refusal sin texto → respuesta vacía → `Failed`. La UI mantiene escritura/borrador y "Reintentar"; nunca cae a fake ni loguea key, prompt, contexto o respuesta.
7. **Sin persistencia ni UI nueva:** aceptar muta sólo el borrador; guardar Q&A como Nota sigue siendo la acción existente.
8. **Indicador fake/real:** fuera de alcance; queda pendiente de UI.

## Acceptance Scenarios

1. **Brainstorming real con markdown/preámbulo:** dado un chat que responde con preámbulo, encabezado y dos ideas en markdown (`**1. ...**`, `**2. ...**`), `suggest(TITLE, draft)` devuelve `Success` con 2 ideas limpias (sin `**`, prefijo ni preámbulo), `partial=false`, borrador intacto.
2. **Mecánicas reales:** dado `- **Movimiento:** ...` y `- **Interacción**: ...`, devuelve 2 sugerencias con `MOVEMENT`/`INTERACTION` y descripciones limpias; líneas sin categoría se descartan.
3. **Parcial, ruido y duplicados:** 1 idea usable → `Success(partial=true)`; sólo preámbulo/encabezados/separadores o ideas ya presentes → `InvalidResponse`; sin excepción, sin persistencia y con el aviso/error existente.
4. **Contrato Q&A real:** con Notas indexadas y runtime real, `ask(question)` conserva `prompt=question.trim()` y `context=cuerpos de sources`, agrega `instructions` + `temperature=0.2` + `maxTokens=600`, y devuelve `Answer` con `text` normalizado (sin fence/encabezados/`**`/`__`) y `sources` con el mismo orden/score.
5. **Respuesta vacía vs refusal:** si el modelo devuelve sólo fences/espacios/marcas → `RagAnswer.Failed` (nunca `Answer`), diagnóstico `AI_QUERY` sin contenido y UI con "Reintentar"; un refusal textual se muestra como `Answer` con sus fuentes.
6. **Errores reales:** 401/500/timeout/JSON inválido o respuesta vacía → `Failed`/`Error` sanitizado, UI usable y sin key, prompt, contexto ni respuesta en logs.
7. **Gate fake intacto:** sin key y `CI=true ./init.sh` usa `FakeAiClient`, sin red, tests verdes y exit 0; el fake sigue devolviendo 2 ideas parseables con el marcador.
8. **iOS congelado:** tests compartidos y link pasan con transporte falso, runtime fake y sin key/red.
9. **Smoke real key-gated (fuera del gate):** con `playbook.ai.mode=real` y key en `local.properties`/env, Brainstorming da 2 ideas parseables y Q&A respuesta no vacía con fuentes; la salida sólo reporta conteos/longitudes/estado.
10. **No regresión:** sin cambios de esquema/`.sqm`, flag, adaptadores nuevos, persistencia, deps, `init.sh`, build files ni UI; el contrato de RAG se preserva.

## Repository Research

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `CONTEXT.md`, `DESIGN.md`: Android-first, iOS congelado, WIP=1, fake en gate y estados de UI.
- Specs `ai-fake-real-switch` (flag/adaptadores/wiring/smoke), `ai-client-ports`, `brainstorming-ai-suggestions` (prompt/parsing), `rag-query` (`prompt=question`, `context=sources`), `rag-answer-citations` (fuentes).
- ADR 0004, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`: chat → Brainstorming/Q&A, calidad real pendiente y parámetros por caso como pregunta abierta.
- `core/.../ai/{AiClient,FakeAiClient,BrainstormingSuggestions,RagQueryService}.kt`, `.../ai/openai/{OpenAiAdapters,AiRuntime,OpenAiHttp,OpenAiJson}.kt`; checks `BrainstormingSuggestionCheck`, `RagQueryCheck`, `OpenAiAdaptersCheck` y runners Android/iOS.
- `composeApp/.../app/{App,AskNotesScreen,BrainstormingWizardScreen}.kt`, `.../ai/RealAiSmokeTest.kt`, roots, `composeApp/build.gradle.kts`, `init.sh`.

**Patrones:** dominio en `:core`; UI "tonta" con estados/callbacks; sin DI/ViewModel/Flow; tests en `commonTest` + runners `@Test` Android/JVM e iOS con `HttpTransport` falso; `AiClientException` como fallo esperado y mensajes sanitizados; defaults compatibles para no romper features aceptadas.

**Gaps:** `GenerateRequest` sólo lleva `prompt`/`context` (sin `instructions`/`temperature`/`max_tokens`); el parser no tolera preámbulos/encabezados y `**Movimiento:**` no matchea; el Q&A devuelve texto crudo y el prompt no instruye grounding; no hay smoke real de Brainstorming y el de Q&A no valida el contrato de chat.

## Technical Approach

1. **`GenerateRequest` opcional:** `instructions: String? = null`, `temperature: Double? = null`, `maxTokens: Int? = null`; validar `0.0..2.0` y `maxTokens > 0` cuando no son `null`. Llamadas existentes intactas.
2. **Adaptador:** si `instructions` no está en blanco, agregarla al `system` después del prompt base; rotular el contexto como `Contexto provisto:` (sirve para Notas y borrador). Agregar `temperature`/`max_tokens` al body sólo si están presentes. No tocar parsing, errores ni auth.
3. **Brainstorming:** mantener marcador `PLAYBOOK_BRAINSTORMING_SUGGESTIONS_V1` y `STEP=<enum>`; reforzar prompt contra preámbulos/markdown y pedir una idea por línea. Pasar `temperature=0.7`/`maxTokens=400`. Parser: normalizar CRLF, quitar fence envolvente; por línea quitar `#`, `>`, `-*•—`, numeración (`1.`, `1)`, `(1)`), `**`/`__`/backticks, comillas y prefijo `Idea/Opción N:`; descartar vacías, separadores, líneas que terminan en `:` con ≤6 palabras y preámbulos (`aquí`, `claro`, `por supuesto`, `estas son`, `te propongo`, `te sugiero`) o exactamente `Ideas`/`Sugerencias`/`Opciones`. KEY_MECHANICS: regex case/accent-insensitive `^(movimiento|interaccion|conflicto)\s*[:\-]\s*(.+)$`; sin categoría/descripción se descarta. Dedupe/filtro normalizado; truncar a 280 y tomar 2.
4. **Q&A:** `RAG_INSTRUCTIONS` en `instructions`; mantener `prompt=question.trim()` y `context=top.map { it.note.body }`; `temperature=0.2`, `maxTokens=600`. Normalizar: `trim`, quitar fence envolvente, encabezados y `**`/`__`, colapsar `\n{3,}` y `trim`; vacío → `Failed(AiClientException("chat devolvió una respuesta vacía"))` + `AI_QUERY`. `sources` no se toca.
5. **UI:** sin cambios; `AskUiState`/`SuggestUiState` ya cubren carga, respuesta, parcial, inválido y error con "Reintentar".
6. **Smoke:** en `RealAiSmokeTest`, `smokeRealChat` key-gated: sugerencias de Título (2 no vacías, sin `**`, `partial=false`) y Q&A sobre SQLDelight in-memory con Nota indexada (`Answer`, texto no vacío, `sources` no vacío). Imprimir sólo `suggestions/partial/answerLen/sources`.

## Expected File Changes

- `core/.../ai/AiClient.kt` — modify; campos opcionales de `GenerateRequest`.
- `core/.../ai/openai/OpenAiAdapters.kt` — modify; `instructions`/`temperature`/`max_tokens` y rótulo neutral del contexto.
- `core/.../ai/BrainstormingSuggestions.kt` y `RagQueryService.kt` — modify; prompt, parámetros, parser/normalización e instrucciones.
- `core/src/commonTest/.../{BrainstormingSuggestionCheck,RagQueryCheck}.kt` y `.../ai/openai/OpenAiAdaptersCheck.kt` — modify; nuevos casos.
- `core/src/{androidUnitTest,iosTest}/.../ai/{BrainstormingSuggestion,RagQuery}*Test.kt` — modify; `@Test` de los nuevos checks.
- `composeApp/src/androidUnitTest/.../ai/RealAiSmokeTest.kt` — modify; `smokeRealChat`.
- Docs: `ARCHITECTURE.md`, `CONSTRAINTS.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `docs/adr/0004-ai-real-integration.md`, `AGENTS.md` (cola), `feature_list.json`, `PROGRESS.md`.

**Sin cambios:** `FakeAiClient`, `AiRuntime`, `AiRuntimeConfig`, `init.sh`, build files, `App.kt`, pantallas, roots, `iosApp/**`, esquema/`.sqm`, repositorios y dependencias.

## Visual Design Impact

UI involved: **no**; se reutilizan estados existentes. New design artifact: **no**. El indicador fake/real queda pendiente (follow-up de UI).

## Durable Documentation Impact

- `ARCHITECTURE.md`: update — `GenerateRequest` opcional, grounding Q&A, parámetros y normalización.
- `CONSTRAINTS.md`: update — no fallback a fake, no persistir/loguear sugerencias/respuestas, preservar `prompt`/`context` del RAG.
- `docs/technical-discovery.md`: update — "AI Real Chat Decisions". `docs/risks-and-open-questions.md`: update — cierra calidad real de Brainstorming y registra riesgos residuales. `docs/adr/0004-ai-real-integration.md`: update — resuelve "parámetros por caso".
- `AGENTS.md`: update acotado (sólo cola). `DESIGN.md`, `CONTEXT.md`, `docs/domain-model.md`: not needed. `feature_list.json`/`PROGRESS.md`: update con evidencia.

## Implementation Plan / Tasks

1. Extender `GenerateRequest`/`OpenAiAiClient` + tests de contrato.
2. Endurecer parser y parámetros del Brainstorming + tests.
3. Agregar grounding/parámetros/normalización al Q&A + tests.
4. Agregar `smokeRealChat`, correr smoke real fuera del gate y controles negativos.
5. Correr gate/tests/build/link y actualizar docs/estado.

- [ ] Campos opcionales de `GenerateRequest` y serialización en el adaptador.
- [ ] Prompt, parámetros y parser tolerante del Brainstorming.
- [ ] Instrucciones, parámetros y normalización del Q&A.
- [ ] Tests de markdown/ruido/dedupe, contrato RAG y body del adaptador.
- [ ] Runners Android/JVM e iOS para los nuevos checks.
- [ ] `smokeRealChat` key-gated con evidencia sanitizada.
- [ ] Controles negativos, verificación de alcance y docs/estado.

## Verification Plan

- `CI=true ./init.sh` → exit 0, fake, sin red ni key.
- `./gradlew :core:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` → verde con los tests nuevos.
- `./gradlew :core:iosSimulatorArm64Test --rerun-tasks -Pplaybook.ai.mode=fake` → verde.
- `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest :composeApp:linkDebugFrameworkIosSimulatorArm64 -Pplaybook.ai.mode=fake --rerun-tasks` → BUILD SUCCESSFUL.
- **Smoke real (fuera del gate, key en `local.properties` o `PLAYBOOK_AI_API_KEY`):** `PLAYBOOK_AI_SMOKE=1 ./gradlew :composeApp:testDebugUnitTest --tests "com.playbook.app.ai.RealAiSmokeTest" -Pplaybook.ai.mode=real -Pplaybook.ai.smoke=1`. Debe reportar 2 ideas + Q&A con respuesta/fuentes. Si el proveedor rechaza `temperature`/`max_tokens` (HTTP 400) o devuelve 1 idea, el implementer **debe detenerse y reportar**; no quitarlos en silencio.
- **Controles negativos** (restaurar byte-idéntico): (a) quitar `instructions` del RAG → falla su test; (b) no limpiar markdown → falla el parser; (c) devolver espacios/fences vacíos → el test exige `Failed`, no `Answer`.
- **Bloqueo honesto:** sin key/red, el implementer puede dejar código/tests fake verdes, pero **no marca `passing` ni cierra la feature** sin smoke real; reporta el bloqueo. El Q&A smoke usa el indexado real (`ai-real-embeddings`, aceptada); si el embedding falla, también bloquea.
- **Alcance:** `Schema.version == 8`, `.sqm` 1..7, sin cambios en `init.sh`, build files, `FakeAiClient`, `AiRuntime`, roots, `App.kt`, pantallas, `iosApp` ni deps; `local.properties` gitignored.
- E2E persistente: **no aplica** — sin flujo de UI nuevo; cobertura `:core` Android/iOS + tests de `:composeApp` + smoke key-gated.

## Evidence To Capture

- Conteos/nombres de tests de `:core` en Android/JVM e iOS; `CI=true ./init.sh` exit 0; `assembleDebug`, tests de `:composeApp` y link iOS.
- Smoke sanitizado: `AI smoke chat OK: suggestions=2, partial=false, answerLen=NNN, sources=N`.
- Controles negativos reproducidos/restaurados; `git diff` sin cambios protegidos; key fuera del repo.

## Known Risks

- **Variabilidad del modelo:** el parser es heurístico; el smoke cubre una muestra, no todos los formatos.
- **Parámetros:** si el proveedor rechaza `temperature`/`max_tokens`, el smoke falla; la decisión requiere reporte, no cambio silencioso. El smoke no puede probar que el proveedor los **honre**, sólo que los acepta y devuelve el resultado esperado.
- **Grounding no garantizado:** la instrucción reduce alucinaciones; las citas son retrieval, no prueba semántica.
- **Sin reintentos/streaming:** el fallo requiere "Reintentar" manual.
- **Indicador fake/real ausente** en UI (follow-up) y **iOS** no verifica chat real (congelado). **Costo/red:** el smoke depende de cuota/key.

## Validator Checklist

- [ ] No se duplica `ai-fake-real-switch` (flag, adaptadores, transporte, wiring, key/release guard).
- [ ] Escenarios 1–10 pasan con fake en Android/JVM e iOS y gate en verde.
- [ ] `prompt`/`context` del RAG intactos; sin persistencia.
- [ ] Formato inválido/parcial/errores degradan a `Failed`/`InvalidResponse`, sin fallback a fake ni datos sensibles en logs.
- [ ] Smoke real ejecutado/documentado fuera del gate con salida sanitizada.
- [ ] Sin cambios de esquema/`.sqm`, build files, `init.sh`, UI, roots, `iosApp` ni deps.
- [ ] Docs durables actualizados; `feature_list.json`/`PROGRESS.md` con evidencia.
- [ ] No hay E2E persistente y la spec justifica por qué.
