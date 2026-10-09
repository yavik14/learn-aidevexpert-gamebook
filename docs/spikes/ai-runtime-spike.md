# Spike: runtime de IA (cloud vs on-device) detrás de `AiClient`

- **Feature:** `ai-runtime-decision`
- **Fecha de ejecución:** 2026-10-09
- **Estado:** ejecutado y verde en Android/JVM e iOS (macOS)
- **Alcance:** test-only, sin red real, sin API keys, sin deps de producción de IA.
- **Decisión asociada:** `docs/adr/0001-ai-runtime-decision.md`

## Metodología

El repo **no puede** ejecutar latencia/calidad/costo cloud reales (requieren keys
y red, prohibidos en el gate y en el repo) ni un modelo on-device real (requiere
dependencias nativas pesadas por plataforma). Por eso el spike no mide runtimes
reales: compariza **dos arquetipos de adaptador detrás del puerto `AiClient`** y
verifica todo lo que sí es ejecutable y determinista en este entorno.

Harness (sólo source sets de test de `:core`):

- `core/src/commonTest/kotlin/com/playbook/core/ai/spike/AiRuntimeSpike.kt`
  - `LocalSpikeAiClient` — arquetipo **on-device-like**: corre localmente, sin
    red, determinista (delega en `FakeAiClient`).
  - `RemoteSpikeAiClient` — arquetipo **cloud-like sin red real**: doble de la
    frontera cloud; `failMode = true` simula proveedor no disponible lanzando
    `AiClientException`. No importa SDKs ni lee keys/entorno.
  - `runAiRuntimeSpike(): String` — corre el contrato sobre ambos, verifica
    offline, mapeo de fallos y degradación, control negativo del contrato,
    sustituibilidad y mide latencia local con `TimeSource.Monotonic`.
  - `verifyContractRejectsBrokenClient()` — control negativo persistente: un
    adaptador con embedding vacío debe romper `verifyAiClientContract`.
- Runners `@Test` con `runTest`:
  - `core/src/androidUnitTest/.../spike/AiRuntimeSpikeAndroidTest.kt`
  - `core/src/iosTest/.../spike/AiRuntimeSpikeIosTest.kt`

## Cómo reproducir

```bash
# Desde la raíz del repo. No hace red y no levanta simuladores/dev servers.
./gradlew :core:testDebugUnitTest          # Android/JVM
./gradlew :core:iosSimulatorArm64Test      # iOS (macOS; Apple Silicon)
```

El reporte sale por `stdout` de los tests. Para verlo en consola:

```bash
./gradlew :core:testDebugUnitTest --info    # imprime el stdout del test
```

o se lee de los reportes XML:

- `core/build/test-results/testDebugUnitTest/TEST-com.playbook.core.ai.spike.AiRuntimeSpikeAndroidTest.xml`
- `core/build/test-results/iosSimulatorArm64Test/TEST-com.playbook.core.ai.spike.AiRuntimeSpikeIosTest.xml`

## Resultados medidos (este repo, este entorno)

Conteo de tests (reportes XML, `failures=0`, `errors=0`):

| Plataforma | Suite del spike | Resto del core |
| --- | --- | --- |
| Android/JVM (`testDebugUnitTest`) | `AiRuntimeSpikeAndroidTest` **2/2** | Greeting 1, AiClient 3, NotePersistence 2, NoteRepository 1 |
| iOS (`iosSimulatorArm64Test`) | `AiRuntimeSpikeIosTest` **2/2** | Greeting 1, AiClient 3, NotePersistence 2, NoteRepository 1 |

### Reporte impreso — Android/JVM

```text
=== ai-runtime-spike (test-only, sin red real) ===
adapter=local/on-device-like contract=OK offline=OK determinism=OK
adapter=remote/cloud-like contract=OK failure-mapping=OK degrade=OK
contract-negative-control=OK (embedding vacío rechazado)
substitution=OK (mismo consumidor contra ambos, sin tocar producción)
local.latency warmup_rounds=1 measured_rounds=200 texts_per_round=3 notes=600 elapsed_us=683 ns_per_note=1138
local.dimension=8 remote.dimension=8
note=la latencia local mide el fake determinista del repo, NO un modelo on-device real (eso es estimación documentada)
```

### Reporte impreso — iOS

```text
=== ai-runtime-spike (test-only, sin red real) ===
adapter=local/on-device-like contract=OK offline=OK determinism=OK
adapter=remote/cloud-like contract=OK failure-mapping=OK degrade=OK
contract-negative-control=OK (embedding vacío rechazado)
substitution=OK (mismo consumidor contra ambos, sin tocar producción)
local.latency warmup_rounds=1 measured_rounds=200 texts_per_round=3 notes=600 elapsed_us=798 ns_per_note=1330
local.dimension=8 remote.dimension=8
note=la latencia local mide el fake determinista del repo, NO un modelo on-device real (eso es estimación documentada)
```

### Qué significa cada línea (medido)

- **`contract=OK`** (ambos arquetipos): `verifyAiClientContract` pasa; el mismo
  contrato genérico acepta implementaciones distintas del puerto.
- **`offline=OK` / `determinism=OK`** (local): el adaptador local no usa red y es
  determinista.
- **`failure-mapping=OK` / `degrade=OK`** (remote): en modo fallo,
  `RemoteSpikeAiClient` lanza `AiClientException` en `embed` y `generate`, y un
  consumidor tipado que captura `AiClientException` degrada sin romper.
- **`contract-negative-control=OK`**: un adaptador con embedding vacío/inválido
  **rompe** el contrato (`verifyContractRejectsBrokenClient`).
- **`substitution=OK`**: el mismo consumidor (`consumeViaPort`) corre igual contra
  local y cloud-like, sin tocar archivos de producción.
- **`local.latency ...`**: latencia/throughput **informativos** del adaptador
  local medidos con `TimeSource.Monotonic`, con 1 round de warm-up + 200 rounds ×
  3 textos = 600 llamadas a `embed`. En esta corrida: **683 µs totales / ~1.14 µs
  por nota** (Android/JVM) y **798 µs / ~1.33 µs por nota** (iOS arm64). **No se
  asertan** latencias absolutas (son dependientes del entorno y volverían flaky el
  gate).

### Control negativo ejecutado (registro honesto)

- Dentro del harness (persistente): `contractRejectsBrokenClient` pasa — el
  contrato **rechaza** un cliente que devuelve `Embedding(emptyList())`.
- Control manual (temporal, restaurado): se forzó `LocalSpikeAiClient.embed` a
  devolver embeddings vacíos y `:core:testDebugUnitTest --tests
  ...AiRuntimeSpikeAndroidTest` falló con
  `AssertionError: la dimensión del embedding debe ser > 0`
  (`tests="2" failures="1"`). Restaurado; re-ejecutado en verde. Confirma que el
  harness no es un "siempre verde": detecta un adaptador que rompe el contrato.

## Medido vs estimado

### Medido en este repo (evidencia dura)

- Conformidad del contrato del puerto sobre dos arquetipos.
- Comportamiento offline/determinista del adaptador local.
- Mapeo de fallo cloud-like → `AiClientException` y degradación del consumidor.
- Rechazo de un embedding inválido por el contrato.
- Sustituibilidad del runtime detrás del mismo puerto.
- Latencia/throughput del **fake determinista** del repo (µs/nota): documentada
  arriba. Es una medición real, pero del **fake**, no de un modelo.

### Estimado / documentado (NO medido aquí, con fuentes y fecha)

> Los números siguientes son **estimaciones de la industria** para dimensionar la
> decisión, **no** mediciones de este repo. Deben re-verificarse con la sonda
> key-gated del ADR y/o al integrar un runtime real.

- **Latencia cloud (embeddings):** típicamente del orden de decenas a cientos de
  ms por request según proveedor, región y tamaño de lote (documentación pública
  de proveedores cloud, consultada 2026-10-09). No ejecutable en el gate.
- **Latencia cloud (generación LLM):** desde cientos de ms hasta varios segundos
  para respuestas largas; aceptable para RAG asíncrono. Estimación, fuentes
  públicas, 2026-10-09.
- **Calidad:** los modelos cloud suelen superar a los on-device pequeños en
  calidad de generación; los embeddings on-device pequeños pueden ser suficientes
  para similitud personal. Estimación cualitativa (comparativas públicas,
  2026-10-09).
- **Costo:** pago por token/request en cloud; variable según proveedor y volumen.
  Estimación, tarifas públicas, 2026-10-09. Requiere límites y caché.
- **Footprint on-device:** un modelo de embeddings cuantizado puede sumar decenas
  a cientos de MB, y un LLM local, varios GB, más el costo de runtime nativo por
  plataforma. Estimación (tamaños de modelos públicos, 2026-10-09). No medido:
  requeriría dependencias nativas pesadas.

## Límites del spike

- No mide un proveedor cloud real (prohibido red/keys en gate y repo).
- No mide un modelo on-device real (requiere deps nativas pesadas por plataforma).
- La latencia local mide el fake determinista; sirve para ejercitar el
  instrumento de medición, **no** para prometer performance on-device.
- El arquetipo cloud-like es un **doble** de la frontera, no un cliente HTTP.

## Sonda manual opcional (fuera del gate)

Descrita en `docs/adr/0001-ai-runtime-decision.md` (sección "Sonda manual
opcional"). Usa `AI_RUNTIME_PROBE_KEY`, **nunca** corre en `./init.sh`, **nunca**
comitea la key. Sus resultados, si se obtienen, se agregan aquí fechados como
medición puntual del proveedor elegido (y no como medición del repo).
