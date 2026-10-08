# Feature Implementation Spec: Interfaz AiClient y wiring

## Source Feature

- `id`: `ai-client-interface`
- `area`: `ai`
- `depends_on`: `["note-model-crud-core"]` (ya `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Establecer en `:core` el **puerto `AiClient`** hacia el runtime de IA, de modo que
el dominio dependa sólo de una abstracción y no de un proveedor concreto
(cloud / on-device / híbrido). La feature entrega además una **implementación
determinista y sin red (`FakeAiClient`)** inyectable, que actúa como runtime por
defecto hasta que `ai-runtime-decision` elija el real, y una **batería de tests
de contrato** que se corre contra más de una implementación para demostrar que el
runtime se puede cambiar sin tocar el core.

Habilita `ai-runtime-decision` (spike + ADR) y `embeddings-generation` (primer
consumidor real). No incluye UI, persistencia de vectores, indexado ni RAG.

## Non-Goals

- **Wiring en la app/UI**: no se modifica `App`, `MainActivity` ni
  `MainViewController`. No hay consumidor de IA todavía; agregar un `AiClient`
  sin uso sería una dependencia muerta. El composition root elegirá la
  implementación al cablear el primer consumidor (`embeddings-generation`).
- Indexado, generación/persistencia de embeddings (BLOB), clasificación
  automática, enlaces semánticos, RAG ni estados de la Nota
  (`embeddings-generation`, `semantic-linking`, `rag-query`).
- Elegir el runtime/proveedor de IA o definir la dimensión/modelo concreto del
  embedding: es `ai-runtime-decision`.
- Añadir tablas, migraciones o cambiar el esquema SQLDelight (sigue v1, sin
  `.sqm`).
- Detectar conectividad, reintentos, colas o scheduling (features posteriores).
- UI, `DESIGN.md`, permisos o integraciones nativas.

## Job Story

When trabajo en el core de Playbook y necesito capacidades de IA,
I want depender de una interfaz `AiClient` e inyectar una implementación fake o
real,
so I can desarrollar y testear sin red, elegir el runtime más adelante, y
cambiarlo sin tocar la lógica de dominio.

## Users And Permissions

- Desarrollador (autor): único actor; ejecuta Gradle/tests y elige la
  implementación en el composition root. Sin usuarios finales ni permisos.
- El MVP es single-user local-first; `AiClient` no maneja cuentas ni API keys
  (su gestión es parte de `ai-runtime-decision`).

## Acceptance Scenarios

### Scenario 1: El core depende de la abstracción, no de un runtime

Given el puerto `AiClient` definido en `:core` (`com.playbook.core.ai`),
When se escribe un consumidor tipado contra `AiClient` (p. ej. el helper de
contrato de los tests),
Then compila y opera sin referenciar ninguna implementación concreta (ni `FakeAiClient`
ni un proveedor), es decir, hay inversión de dependencia.

### Scenario 2: `FakeAiClient` es determinista y sin red

Given `FakeAiClient(dimension = 8)`,
When se llama `embed(texts)` dos veces con la misma entrada,
Then devuelve un `Embedding` por texto, en el mismo orden, todos con la misma
dimensión > 0, y resultados idénticos entre llamadas; textos distintos producen
vectores distintos. `generate(request)` es también determinista y no vacío.

### Scenario 3: Contrato del puerto

Given una implementación de `AiClient`,
When se corre `verifyAiClientContract(client)`,
Then se cumplen: `embed(emptyList()) == emptyList()`; tamaño de salida igual al
de entrada; dimensión consistente y > 0; determinismo de `embed` y `generate`; y
respuesta de `generate` no vacía.

### Scenario 4: El runtime se cambia sin tocar el core

Given el contrato de `AiClient`,
When se corre contra `FakeAiClient` y contra una segunda implementación definida
sólo en `commonTest`,
Then ambas pasan el mismo contrato y ningún archivo de producción de `:core`
cambia; sustituir la implementación es transparente para los consumidores.

### Scenario 5: Ambas plataformas

Given los tests de contrato,
When se corren `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test`,
Then el contrato (fake + segunda implementación) pasa en Android/JVM y en iOS.

### Scenario 6: Gate honesto

Given la feature implementada,
When se ejecuta `./init.sh`,
Then sigue terminando en 0, sin dev servers ni simuladores, e incluye los nuevos
tests del core en ambas plataformas (sin cambios en `init.sh`).

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `note-model-crud-core` es la
  última `accepted` (Session 008); esta es la próxima feature tras el pedido del
  usuario.
- `feature_list.json` — metadata de `ai-client-interface`; `depends_on`
  satisfecho; nota "requisito explícito del concept-prompt".
- `docs/idea/concept-prompt.md` — "Runtime de IA: (A) nube, (B) on-device, (C)
  híbrido detrás de una interfaz `AiClient`. IMPORTANTE: diseñar `AiClient` como
  abstracción para poder cambiar de estrategia sin tocar el core."
- `docs/technical-discovery.md` — "IA: interfaz `AiClient` como abstracción.
  Runtime todavía sin decidir"; "Proveedor de IA: embeddings + LLM detrás de
  `AiClient`"; "Tests unitarios del core KMP con un `AiClient` fake
  (deterministas, sin red)".
- `docs/build-brief.md` — slice 4: "Generación de embeddings por Nota detrás de
  `AiClient`"; carga/IA nunca debe bloquear la captura.
- `docs/domain-model.md`, `CONTEXT.md` — el indexado es un proceso asíncrono que
  genera el embedding; no hay clasificación IA en el MVP (descartada).
- `docs/risks-and-open-questions.md` — el runtime de IA es el riesgo principal,
  hoy postergado; "Modelo y dimensión del embedding concreto" abierto.
- `ARCHITECTURE.md` — dirección `:composeApp → :core`; dominio/repositorio en
  `:core`; `Deferred: ... IA`; sin DI (`dependencias como parámetros`).
- `docs/specs/note-model-crud-core.md` — dependencia satisfecha; patrón de tests
  compartidos en `commonTest` + `@Test` por plataforma.
- `docs/specs/notes-list-ui.md` — patrón de wiring por parámetros y justificación
  de no introducir DI/ViewModel/coroutines en la UI.
- `core/build.gradle.kts` — `:core` KMP; `commonMain` (SQLDelight runtime),
  `commonTest` (`kotlin-test`); sin coroutines.
- `gradle/libs.versions.toml` — versiones centralizadas; sin coroutines.
- `core/src/commonMain/kotlin/com/playbook/core/**` — no existe paquete `ai`.
- `init.sh` — gate: `assembleDebug` + `testDebugUnitTest` + link iOS +
  `iosSimulatorArm64Test`; sin procesos de larga duración.
- `composeApp/src/{androidMain,iosMain}/.../Main{Activity,ViewController}.kt` —
  composition root actual (crea DB + `NoteRepository`); **no se toca**.

### Existing Patterns To Follow

- Lógica de dominio y puertos en `:core`; implementaciones concretas inyectadas
  desde afuera (`:composeApp`/tests). `:core` nunca depende de UI.
- Sin framework DI: las dependencias se pasan como parámetros/constructores.
- Tests compartidos en `commonTest` que reciben la dependencia a ejercitar +
  `@Test` por plataforma (`androidUnitTest`, `iosTest`).
- Versiones centralizadas en `gradle/libs.versions.toml`.
- Documentos en español, términos técnicos en inglés.

### Current Gaps

- No existe `AiClient` ni ningún paquete `ai` en `:core`.
- No hay forma de testear lógica de IA sin red ni de aislar el runtime.
- No hay dependencia de coroutines en `:core`, pese a que las llamadas de IA son
  inherentemente asíncronas y pueden fallar (red/cuota/modelo).
- "Wiring" de IA en la app no aplica aún: no hay consumidor de IA.

## Technical Approach

### Puerto y tipos (`core/src/commonMain/kotlin/com/playbook/core/ai/`)

```kotlin
package com.playbook.core.ai

/** Vector de embedding (valores en punto flotante). */
data class Embedding(val values: List<Float>)

/** Entrada de [AiClient.generate]; `context` ancla la respuesta (RAG). */
data class GenerateRequest(
    val prompt: String,
    val context: List<String> = emptyList(),
)

/** Falla del runtime de IA (red, cuota, modelo, etc.). */
class AiClientException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Puerto del core hacia el runtime de IA. El core depende sólo de esta
 * abstracción; la implementación concreta (fake, cloud, on-device) se inyecta
 * desde afuera y puede cambiarse sin tocar el core.
 */
interface AiClient {
    /** Devuelve un [Embedding] por cada texto, en el mismo orden. */
    suspend fun embed(texts: List<String>): List<Embedding>

    /** Genera una respuesta de texto a partir del [request]. */
    suspend fun generate(request: GenerateRequest): String
}
```

Decisiones clave del contrato:

- **`suspend`**: las llamadas de IA son asíncronas y cancelables; un contrato
  síncrono forzaría a un runtime de red/on-device a bloquear o a cambiar el
  puerto al decidirse el runtime (justo lo que el concept-prompt prohíbe).
  `kotlinx-coroutines-core` es el primitivo estándar KMP.
- **Embebido + generación**: el concept-prompt pide "embeddings + LLM detrás de
  `AiClient`". Definir ambos ahora evita reabrir el puerto al llegar el RAG.
  `generate` queda como seam sin consumidor actual; la forma concreta de
  citas/streaming se decidirá en `rag-query` sin romper este método.
- **Fallos**: las implementaciones reportan fallos lanzando `AiClientException`
  (o un subtipo). La clasificación transitorio/persistente y el mapeo a estados
  de la Nota (`pendiente`/`fallida`) son de `embeddings-generation`.
- **Dimensión del embedding**: no se fija en el puerto; es responsabilidad del
  runtime (abierto en `risks-and-open-questions.md`).

### Implementación fake (`FakeAiClient.kt`, `commonMain`)

`FakeAiClient(private val dimension: Int = 8) : AiClient`, determinista y sin
red:

- `embed(texts)`: un vector por texto derivado de `text.hashCode()` (string
  estable entre plataformas): mismo texto → mismo vector; textos distintos →
  vectores distintos (salvo colisión de hash). `embed(emptyList())` →
  `emptyList()`. `require(dimension > 0)`.
- `generate(request)`: respuesta determinista y no vacía (p. ej. `"[fake] " +
  prompt` y la cantidad de `context`), suficiente para que los tests de los
  próximos consumidores sean predecibles.

Vive en `commonMain` (no en `commonTest`) porque es **el runtime por defecto**
mientras el real no se decide y debe poder inyectarse desde el composition root;
también lo usan los tests del core.

### Inyección / wiring (decisión de alcance)

No hay consumidor de IA todavía (`embeddings-generation` es el primero), por lo
que **no se agrega `AiClient` a `App(...)` ni a los entry points**: sería una
dependencia muerta y violaría el alcance. La inyección queda materializada como:

- el puerto `AiClient` en `:core` (los consumidores se tipan contra él),
- una implementación concreta (`FakeAiClient`) en `:core`,
- tests que inyectan implementaciones distintas por el mismo puerto.

El composition root (`MainActivity`/`MainViewController`) elegirá la
implementación concreta (fake hoy; real cuando `ai-runtime-decision` lo decida)
al cablear el primer consumidor. Regla durable: **la lógica de dominio consume
sólo el puerto `AiClient`; los adaptadores concretos se inyectan**. `:core`
expone el puerto y un adaptador fake por defecto para desarrollo/tests; los
adaptadores reales (cloud/on-device) vivirán fuera de `:core` y se inyectarán sin
tocar la lógica de dominio.

### Dependencias y `init.sh`

- `gradle/libs.versions.toml`: agregar `kotlinx-coroutines = "1.10.x"`
  (compatible con Kotlin 2.1.21; el implementer verifica y ajusta si hace falta)
  con `kotlinx-coroutines-core` y `kotlinx-coroutines-test`.
- `core/build.gradle.kts`: `commonMain` → `coroutines-core`; `commonTest` →
  `coroutines-test` (necesario para `runTest` sobre `suspend`).
- `init.sh`: **sin cambios**. Ya corre los tests del core en Android/JVM e iOS,
  no inicia dev servers y puede imprimir los comandos manuales.

### Tests

- `commonTest/.../ai/AiClientContract.kt`:
  - `suspend fun verifyAiClientContract(client: AiClient)`: contrato genérico
    (Scenarios 2–3).
  - `suspend fun verifyFakeAiClient()`: comportamiento específico del fake
    (dimensión `DEFAULT_DIMENSION`, determinismo y que textos distintos produzcan
    vectores distintos).
  - `ConstantAiClient` (privada, test-only): segunda implementación que satisface
    el contrato, para probar la sustitución sin tocar producción.
- `androidUnitTest/.../ai/AiClientAndroidTest.kt` y
  `iosTest/.../ai/AiClientIosTest.kt`: `@Test fun ... = runTest { ... }` que
  corren el contrato contra `FakeAiClient` y `ConstantAiClient`.

## Expected File Changes

- `core/src/commonMain/kotlin/com/playbook/core/ai/AiClient.kt` — crear;
  `AiClient`, `Embedding`, `GenerateRequest`, `AiClientException`.
- `core/src/commonMain/kotlin/com/playbook/core/ai/FakeAiClient.kt` — crear;
  implementación determinista por defecto.
- `core/src/commonTest/kotlin/com/playbook/core/ai/AiClientContract.kt` — crear;
  contrato compartido + `ConstantAiClient`.
- `core/src/androidUnitTest/kotlin/com/playbook/core/ai/AiClientAndroidTest.kt` —
  crear; `@Test` en JVM.
- `core/src/iosTest/kotlin/com/playbook/core/ai/AiClientIosTest.kt` — crear;
  `@Test` en iOS.
- `gradle/libs.versions.toml` — modificar; versión y libs de coroutines.
- `core/build.gradle.kts` — modificar; deps `coroutines-core`/`coroutines-test`.
- `ARCHITECTURE.md` — modificar; sección de IA (puerto, adaptador, dirección).
- `docs/technical-discovery.md` — modificar; "AI Decisions (ai-client-interface)".
- `docs/risks-and-open-questions.md` — modificar (ligero); el puerto existe y
  desbloquea `ai-runtime-decision`.
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado y evidencia.

No se esperan cambios en `:composeApp`, `iosApp/**`, `init.sh`, `Note.sq` ni el
esquema de persistencia.

## Visual Design Impact

- UI involved: no.
- Design source: not applicable.
- Screens or states affected: ninguna.
- New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — aparece el puerto `AiClient` y la regla de
  dependencia (el core depende de la abstracción; los runtimes concretos se
  inyectan). No cambian módulos ni dirección `:composeApp → :core`.
- `CONSTRAINTS.md`: not needed — no se crea el archivo; la regla durable de
  puerto/adaptador queda en `ARCHITECTURE.md` (evita ceremonia y duplicación).
- `AGENTS.md`: not needed — no cambian workflow, arranque ni gate; la línea
  "Próxima feature en cola" (`create-text-note`) sigue vigente por orden de
  `feature_list.json`.
- Other docs: `docs/technical-discovery.md` — update; `docs/risks-and-open-questions.md`
  — update ligero; `docs/idea/concept-prompt.md`, `CONTEXT.md`,
  `docs/domain-model.md` — not needed (la spec se alinea con ellos);
  `PROGRESS.md`/`feature_list.json` — update al cerrar.

## Implementation Plan

1. Agregar `kotlinx-coroutines` (core/test) a `libs.versions.toml` y `:core`.
2. Crear `AiClient`, `Embedding`, `GenerateRequest` y `AiClientException`.
3. Implementar `FakeAiClient` determinista (sin red).
4. Escribir `AiClientContract.kt` (contrato + segunda implementación test-only) y los `@Test` por plataforma.
5. Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md` y `docs/risks-and-open-questions.md`.
6. Correr la verificación (`./init.sh` + tareas de test), registrar evidencia y cerrar estado.

## Implementation Tasks

- [x] Agregar `kotlinx-coroutines-core`/`-test` a `gradle/libs.versions.toml` y `core/build.gradle.kts`.
- [x] Crear `AiClient`, `Embedding`, `GenerateRequest` y `AiClientException` en `com.playbook.core.ai`.
- [x] Crear `FakeAiClient` determinista con dimensión configurable (default 8).
- [x] Escribir `verifyAiClientContract`/`verifyFakeAiClient` + `ConstantAiClient` en `commonTest`.
- [x] Escribir `AiClientAndroidTest` (JVM) e `AiClientIosTest` (iOS) con `runTest`.
- [x] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md` y `docs/risks-and-open-questions.md`.
- [x] Correr la verificación y registrar evidencia en `feature_list.json`/`PROGRESS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest` → contrato de `AiClient` (fake + segunda
  implementación) en verde, junto con los tests previos.
- `./gradlew :core:iosSimulatorArm64Test` (macOS) → el mismo contrato en verde en
  iOS.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (el `:core` con
  coroutines compila en Android).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL
  (el `:core` con coroutines compila/enlaza en iOS).
- `./init.sh` → exit 0; corre el gate no bloqueante actual (tests del core en
  Android/JVM e iOS), **no** inicia dev servers ni simuladores, y no cambia.
- Control negativo (evidencia de sustitución): el contrato debe pasar también
  con `ConstantAiClient` sin modificar ningún archivo de producción de `:core`.
- E2E persistente: **no aplica**. No hay flujo de usuario/API observable
  (no hay consumidor ni UI de IA), y no existe harness E2E persistente; la
  cobertura es de tests del core en dos plataformas + compilación de la app. Se
  justifica explícitamente.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` con los
  tests de contrato en verde (nombres y que corren contra 2 implementaciones).
- Salida de `:composeApp:assembleDebug` y `:composeApp:linkDebugFrameworkIosSimulatorArm64`.
- Salida de `./init.sh` (exit 0) sin procesos de larga duración.
- Confirmación de que el esquema sigue en v1 (sin `.sqm`) y de que no se tocó
  `:composeApp`/`iosApp`/`init.sh`.
- Versión de `kotlinx-coroutines` usada y confirmación de compatibilidad con
  Kotlin 2.1.21.

## Validator Checklist

- [ ] Alcance respetado: sólo puerto `AiClient` + `FakeAiClient` + tests de
      contrato; sin UI, persistencia de embeddings, indexado, RAG ni runtime real.
- [ ] Escenarios de aceptación 1–6 pasan.
- [ ] El core no referencia ninguna implementación concreta en su lógica de
      dominio; los consumidores se tipan contra `AiClient`.
- [ ] El contrato pasa con `FakeAiClient` y con una segunda implementación sin
      tocar archivos de producción de `:core` (runtime sustituible).
- [ ] `FakeAiClient` es determinista y no usa red.
- [ ] Evidencia en `feature_list.json` / `PROGRESS.md`.
- [ ] No hay E2E persistente y la spec justifica por qué.
- [ ] `ARCHITECTURE.md` y `docs/technical-discovery.md` actualizados.
- [ ] `init.sh` sin cambios, no bloqueante y sin procesos de larga duración.
