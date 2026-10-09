# Feature Implementation Spec: Decisión de runtime de IA (spike + ADR)

## Source Feature

- `id`: `ai-runtime-decision`
- `area`: `ai`
- `depends_on`: `["ai-client-interface"]` (ya `accepted`, Session 014)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Producir la **decisión arquitectónica documentada** sobre el runtime de IA de
Playbook — cloud, on-device o híbrido, siempre detrás del puerto `AiClient` — con
al menos un **spike comparativo reproducible** y su **evidencia honesta**, más un
**ADR** en `docs/adr/` que registre contexto, criterios, alternativas,
trade-offs, decisión, consecuencias y preguntas abiertas.

Esta feature **no cambia comportamiento de producto** ni cablea IA real a la app.
Deja zanjada la estrategia de runtime que `rag-query` (y las features de IA
posteriores) necesitan para implementar el adaptador real. Además fija la
convención de ADRs del repo (`docs/adr/`), que hoy no existe.

## Non-Goals

- **Cablear IA real** (cloud u on-device) a `App`, `MainActivity`,
  `MainViewController` o cualquier consumidor. Lo hará `embeddings-generation`
  (primer consumidor) / `rag-query`.
- Elegir o implementar el **modelo/proveedor/SDK concreto** ni la dimensión del
  embedding. El ADR decide la **estrategia de runtime**, no un vendor ni un
  modelo; la elección puntual queda como follow-up.
- Agregar **dependencias de producción de IA** (SDK cloud, runtime on-device
  nativo). El spike vive sólo en source sets de test.
- Modificar el puerto `AiClient`, `FakeAiClient`, el esquema SQLDelight, la UI o
  `init.sh`.
- Persistencia de embeddings, indexado, enlaces semánticos o RAG.
- Hacer llamadas de red reales o commitear API keys/secrets. Prohibido en el
  repo y en el gate.

## Job Story

When llego a la fase de IA de Playbook y necesito decidir cómo correr embeddings
y LLM de forma realista para Android + iOS,
I want comparar cloud / on-device / híbrido con evidencia y registrar la decisión
en un ADR,
so I can implementar el runtime real (y el RAG) sin re-litigar la arquitectura ni
tocar la lógica de dominio.

## Users And Permissions

- Desarrollador/autor: ejecuta el spike, redacta el ADR y decide. Sin usuarios
  finales ni permisos de aplicación.
- Single-user local-first. El ADR define la política de gestión de API keys
  (ninguna credencial en el repo); no se implementa esa gestión aquí.

## Acceptance Scenarios

### Scenario 1: Existe un ADR decidible

Given el puerto `AiClient` ya `accepted` y esta feature planificada,
When el implementer termina,
Then existe `docs/adr/0001-ai-runtime-decision.md` con: título, `status` y fecha,
contexto, criterios de decisión, al menos **tres alternativas** (cloud-only,
on-device-only, híbrido), trade-offs por alternativa, una **decisión explícita**,
consecuencias y preguntas abiertas.

### Scenario 2: Spike comparativo reproducible y ejecutado

Given el harness del spike en `:core` (source sets de test),
When se corre `./gradlew :core:testDebugUnitTest` (y
`:core:iosSimulatorArm64Test` en macOS),
Then el harness instancia **dos arquetipos de adaptador detrás de `AiClient`**
(local/on-device-like y cloud-like sin red real), corre `verifyAiClientContract`
sobre ambos, verifica el comportamiento offline y el mapeo de fallos a
`AiClientException`, y **imprime un reporte estable** que se captura en
`docs/spikes/ai-runtime-spike.md`.

### Scenario 3: Evidencia honesta (medido vs estimado)

Given el reporte del spike,
When se revisa,
Then separa explícitamente lo **medido en este repo** (conformidad del puerto,
offline, latencia local con `TimeSource.Monotonic`, manejo de fallos) de lo
**estimado/documentado** (latencia, calidad y costo cloud; footprint de modelos
on-device), cita fuentes con fecha para lo no medido, y **no presenta números
simulados como mediciones reales**.

### Scenario 4: Sin IA real ni secrets en la app/gate

Given la feature terminada,
When se inspeccionan `:composeApp`, los entry points y el diff,
Then no hay wiring de IA en la app, no se agregaron dependencias de producción de
IA, no hay API keys ni secrets versionados, y `init.sh` no hace llamadas de red.

### Scenario 5: Gate no bloqueante

Given la feature terminada,
When se ejecuta `./init.sh`,
Then termina en 0, no levanta dev servers ni simuladores, y sigue corriendo el
gate actual (incluye los tests del core en Android/JVM e iOS), sin cambios.

## Repository Research

### Files Inspected

- `AGENTS.md` — flujo SDD planner→implementer→validator; `init.sh` no bloqueante;
  ADRs en `docs/adr/*.md` "cuando existan".
- `PROGRESS.md` — `ai-client-interface` `accepted` (Session 014); runtime real y
  dimensión abiertos "para `ai-runtime-decision`".
- `feature_list.json` — metadata de la feature; `depends_on` satisfecho;
  `verification` provisional ("ADR ... spike ejecutado y registrado").
- `docs/specs/ai-client-interface.md` — puerto `AiClient`, `FakeAiClient`, patrón
  de tests de contrato; afirma "elegir el runtime es `ai-runtime-decision`".
- `docs/technical-discovery.md` — IA detrás de `AiClient`; runtime sin decidir;
  constraints de keys/costos/offline; testing con fake sin red.
- `docs/risks-and-open-questions.md` — runtime de IA = riesgo principal postergado;
  research task "spike comparativo cloud vs on-device"; modelo/dimensión abiertos.
- `docs/domain-model.md`, `CONTEXT.md` — indexado asíncrono; la captura nunca se
  bloquea por IA; sin clasificación automática.
- `docs/build-brief.md` — on-device **no** es requisito del MVP; IA en un flujo
  real; offline-first.
- `ARCHITECTURE.md` — puerto AI y regla durable; "Deferred: runtime concreto".
- `docs/idea/concept-prompt.md` — decisión A/B/C; `AiClient` como abstracción.
- `docs/design/ui-ux-brief.md` — sin superficie visual para esta feature.
- `core/src/commonMain/kotlin/com/playbook/core/ai/AiClient.kt` y `FakeAiClient.kt`
  — contrato y adaptador disponibles para el spike.
- `core/src/commonTest/kotlin/com/playbook/core/ai/AiClientContract.kt` —
  `verifyAiClientContract`, `verifyFakeAiClient` y `ConstantAiClient` reutilizables.
- `core/build.gradle.kts` — source sets y deps de test (`kotlin-test`,
  `coroutines-test`); sin dependencias de IA.
- `init.sh` — gate no bloqueante (assembleDebug + tests core + link iOS + test iOS).
- `docs/adr/` — **no existe** (glob sin resultados); esta feature crea el directorio.

### Existing Patterns To Follow

- Puertos en `:core`, adaptadores inyectados; `:core` nunca depende de UI.
- Sin DI: dependencias por constructor/parámetro.
- Tests compartidos en `commonTest` + `@Test` por plataforma (`androidUnitTest`,
  `iosTest`), con `runTest` de `kotlinx-coroutines-test` ya disponible.
- Código experimental/spike en source sets de **test**, nunca en `commonMain`.
- Documentos en español, términos técnicos en inglés.
- ADR: no hay precedente; se fija un formato MADR-lite (ver Technical Approach).

### Current Gaps

- No existe `docs/adr/` ni convención de ADRs en el repo.
- No hay comparación ni medición de runtimes de IA; el runtime real sigue sin
  decidir (riesgo principal postergado).
- No hay red ni credenciales en el gate: la latencia/calidad/costo cloud reales
  **no son ejecutables aquí** (se documentan como límite, no como medición).
- On-device real requiere dependencias nativas pesadas por plataforma; tampoco es
  ejecutable en este repo hoy (se documenta como límite).
- `feature_list.json` conserva terminología stale (`ai-enrichment-classify-tags-type`
  menciona categoría/tipo), ya descartada en `CONTEXT.md`; fuera del alcance de
  esta feature (normalizar al re-derivar).

## Technical Approach

### Entregables

1. **ADR** en `docs/adr/0001-ai-runtime-decision.md` con formato MADR-lite:
   Título, `status`, Fecha, Contexto, Criterios de decisión, Alternativas
   consideradas (cloud-only / on-device-only / híbrido), Decisión, Consecuencias,
   Evidencia y Preguntas abiertas.
2. **Spike comparativo reproducible** en `:core`, sólo en source sets de test,
   bajo el paquete `com.playbook.core.ai.spike`.
3. **Reporte del spike** en `docs/spikes/ai-runtime-spike.md`: metodología, cómo
   reproducir, resultados medidos (stdout capturado), separación medido vs
   estimado y límites.

### Criterios de decisión (cada alternativa se puntúa en el ADR)

- **Offline-first:** la captura nunca se bloquea; el indexado tolera fallos y
  reintenta.
- **Código compartido KMP** (Android + iOS) vs superficie nativa por plataforma.
- **Secrets y costo** (cloud) vs **peso/tamaño** (on-device).
- **Latencia** aceptable para indexado asíncrono y RAG.
- **Calidad** suficiente y **privacidad**.
- **Testabilidad/determinismo** con el fake existente.
- **Escalabilidad a Fase B** sin refactor mayor.

### Spike: qué se puede y qué no se puede ejecutar

**Ejecutable en este repo (offline, test-only):**

- Conformidad de ambos arquetipos con `AiClient` (`verifyAiClientContract`).
- Sustituibilidad: el mismo consumidor tipado contra el puerto, sin tocar
  producción.
- Offline: `LocalSpikeAiClient` (on-device-like) opera sin red;
  `RemoteSpikeAiClient` (cloud-like) puede simular fallo → `AiClientException`.
- **Latencia local** con `kotlin.time.TimeSource.Monotonic` sobre el adaptador
  local (throughput por nota).

**NO ejecutable aquí (se documenta como estimación/limitación):**

- Latencia, calidad y costo **reales** de un proveedor cloud (requieren
  credenciales + red: prohibido en el gate y en el repo).
- Footprint/memoria/tiempo de un **modelo on-device real** (requieren deps
  nativas pesadas por plataforma).

El harness imprime un reporte estable (`println`) que el implementer captura en
`docs/spikes/ai-runtime-spike.md`. **No se asertan latencias absolutas** (dependen
del entorno y volverían flaky el gate); sólo conformidad, offline y manejo de
fallos. El ADR puede documentar una **sonda manual opcional key-gated** (comando,
nombre de variable de entorno, resultado esperado) que NUNCA corre en el gate y
NUNCA incluye keys en el repo; sus resultados, si se obtienen, van fechados.

### Diseño del harness (provisional, test-only)

En `core/src/commonTest/kotlin/com/playbook/core/ai/spike/`:

- `AiRuntimeSpike.kt`:
  - `LocalSpikeAiClient(dimension)` : `AiClient` — arquetipo on-device-like, sin
    red (puede delegar en `FakeAiClient`).
  - `RemoteSpikeAiClient(...)` : `AiClient` — arquetipo cloud-like **sin red
    real**, con modo de fallo configurable que lanza `AiClientException`.
  - `suspend fun runAiRuntimeSpike(): String` — corre el contrato sobre ambos en
    modo éxito, verifica offline/fallo, mide la latencia local e imprime/devuelve
    el reporte.
- `AiRuntimeSpikeAndroidTest.kt` (`androidUnitTest`) y
  `AiRuntimeSpikeIosTest.kt` (`iosTest`): `@Test` con `runTest` que corre el
  harness en cada plataforma.

Ubicación y nombres son guía; el implementer puede ajustarlos preservando:
test-only, sin deps nuevas, sin red, determinista en la parte de conformidad y sin
asserts de latencia. `RemoteSpikeAiClient` **no** importa SDKs ni lee variables de
entorno con keys: es sólo un doble de la frontera cloud.

### `init.sh`

**Sin cambios.** Los tests del spike corren dentro de
`:core:testDebugUnitTest`/`:core:iosSimulatorArm64Test`, ya incluidos en el gate.
El reporte impreso es informativo. No se agregan tareas Gradle ni se ejecuta red.

## Expected File Changes

- `docs/adr/0001-ai-runtime-decision.md` — crear; ADR con la decisión.
- `docs/spikes/ai-runtime-spike.md` — crear; reporte y evidencia del spike.
- `core/src/commonTest/kotlin/com/playbook/core/ai/spike/AiRuntimeSpike.kt` —
  crear; harness + arquetipos test-only.
- `core/src/androidUnitTest/kotlin/com/playbook/core/ai/spike/AiRuntimeSpikeAndroidTest.kt`
  — crear; runner JVM.
- `core/src/iosTest/kotlin/com/playbook/core/ai/spike/AiRuntimeSpikeIosTest.kt` —
  crear; runner iOS.
- `ARCHITECTURE.md` — modificar; sección AI con la decisión y su dirección.
- `docs/technical-discovery.md` — modificar; "AI Decisions (ai-runtime-decision)".
- `docs/risks-and-open-questions.md` — modificar; cerrar/degradar el riesgo
  bloqueante y dejar los residuales.
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado + evidencia
  (lo hace el implementer, no el planner).

No se esperan cambios en `:core` `commonMain`, `:composeApp`, `iosApp/**`,
`init.sh`, esquema SQLDelight ni UI. Si el implementer necesita un archivo extra
(p. ej. `CONSTRAINTS.md`), lo justifica en el cierre.

## Visual Design Impact

- UI involved: no.
- Design source: not applicable.
- Screens or states affected: ninguna.
- New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — la sección AI registra el runtime decidido, el
  adaptador previsto y la regla de dirección; no cambian módulos ni dependencias.
- `CONSTRAINTS.md`: **not needed (por defecto)** — la decisión y sus reglas
  durables quedan en el ADR y en `ARCHITECTURE.md`. Si el ADR fija un MUST/MUST
  NOT repo-wide no capturado allí, el implementer crea `CONSTRAINTS.md` y lo
  reporta.
- `AGENTS.md`: **update (menor)** — ajustar la línea "Próxima feature en cola" y
  el estado; no cambian workflow, arranque ni gate.
- Other docs: `docs/technical-discovery.md` y `docs/risks-and-open-questions.md`
  — update; `docs/adr/` y `docs/spikes/` — create; `CONTEXT.md`,
  `docs/domain-model.md`, `docs/build-brief.md`, `docs/idea/concept-prompt.md` —
  not needed (la decisión se alinea con ellos); `PROGRESS.md`/`feature_list.json`
  — update al cerrar.

## Implementation Plan

1. Crear `docs/adr/` y redactar el ADR en formato MADR-lite (contexto, criterios,
   ≥3 alternativas y trade-offs).
2. Implementar el harness del spike (test-only) en `:core` con los dos arquetipos
   detrás de `AiClient`.
3. Escribir los runners por plataforma y verificar conformidad, offline y mapeo de
   fallos; capturar el reporte impreso.
4. Ejecutar el spike y volcar los resultados en `docs/spikes/ai-runtime-spike.md`
   (medido vs estimado).
5. Cerrar el ADR con decisión, consecuencias y preguntas abiertas, citando la
   evidencia del spike.
6. Actualizar los docs durables y correr `./init.sh`; registrar evidencia y estado.

## Implementation Tasks

- [x] Crear `docs/adr/0001-ai-runtime-decision.md` con el formato MADR-lite y las
      ≥3 alternativas.
- [x] Definir los criterios de decisión y puntuar cada alternativa.
- [x] Implementar `AiRuntimeSpike.kt` (arquetipos local/cloud + harness) en
      `commonTest`.
- [x] Escribir los runners `@Test` en `androidUnitTest` e `iosTest`.
- [x] Ejecutar el spike y capturar el reporte; separar medido vs estimado.
- [x] Volcar la evidencia en `docs/spikes/ai-runtime-spike.md`.
- [x] Cerrar el ADR con decisión, consecuencias y preguntas abiertas.
- [x] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md`,
      `docs/risks-and-open-questions.md` y el estado en
      `feature_list.json`/`PROGRESS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest` → el harness del spike pasa (contrato sobre
  los dos arquetipos, offline, mapeo de fallo → `AiClientException`) junto con los
  tests previos, e imprime el reporte.
- `./gradlew :core:iosSimulatorArm64Test` (macOS) → lo mismo en iOS.
- `./gradlew :composeApp:assembleDebug` y
  `:composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (sin deps
  nuevas).
- `./init.sh` → exit 0; sin cambios; sin dev servers/simuladores; sin red.
- Inspección: existen `docs/adr/0001-ai-runtime-decision.md` y
  `docs/spikes/ai-runtime-spike.md` con evidencia y separación medido/estimado;
  `git status` sin keys/secrets; sin wiring de IA en la app.
- Control negativo: `RemoteSpikeAiClient` en modo fallo debe lanzar
  `AiClientException` (y un consumidor puede degradar); forzar un embedding
  vacío/inválido debe romper el contrato. Registrar el resultado y restaurar.
- E2E persistente: **no aplica** — no hay flujo de usuario/API observable (es una
  decisión + documentación + spike offline), y no existe harness E2E persistente;
  `init.sh` no levanta simuladores. Se justifica explícitamente.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` con el
  harness del spike en verde (nombres y conteos).
- Reporte impreso del spike (latencia local y métricas) capturado en
  `docs/spikes/ai-runtime-spike.md`.
- Decisión del ADR y su vínculo explícito con la evidencia.
- Confirmación de ausencia de keys/secrets y de deps de producción de IA
  (`git status`; `libs.versions.toml`/`core/build.gradle.kts` sin cambios de
  producción).
- Salida de `./init.sh` (exit 0) y de `assembleDebug` + link del framework iOS.

## Validator Checklist

- [ ] El ADR existe con contexto, ≥3 alternativas, trade-offs, decisión,
      consecuencias, evidencia y preguntas abiertas.
- [ ] El spike es reproducible con los comandos del repo y su evidencia está
      registrada.
- [ ] La evidencia distingue medido vs estimado y no presenta simulación cloud
      como medición real.
- [ ] No hay wiring de IA real en la app ni secrets/keys versionados.
- [ ] No se agregaron dependencias de producción de IA ni cambió el puerto,
      el esquema ni la UI.
- [ ] `init.sh` sigue no bloqueante (exit 0, sin dev servers/simuladores/red).
- [ ] `ARCHITECTURE.md`, `docs/technical-discovery.md` y
      `docs/risks-and-open-questions.md` actualizados coherentemente.
- [ ] `feature_list.json`/`PROGRESS.md` con estado y evidencia.
- [ ] No hay E2E persistente y la spec justifica por qué.
- [ ] Alcance respetado: decisión + spike + docs; sin implementar el runtime real.
