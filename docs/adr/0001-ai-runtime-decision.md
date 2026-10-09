# ADR 0001: Runtime de IA (cloud / on-device / híbrido) detrás de `AiClient`

- **Status:** accepted (validación independiente de `ai-runtime-decision`,
  2026-10-09).
- **Fecha:** 2026-10-09
- **Feature:** `ai-runtime-decision`
- **Decisores:** implementer de `ai-runtime-decision` (propuesta); validador
  independiente (accept).
- **Relacionado:** `docs/spikes/ai-runtime-spike.md`, `ARCHITECTURE.md` (sección
  AI), `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`,
  `docs/build-brief.md`, `docs/idea/concept-prompt.md`.

## Contexto

Playbook es una app **local-first single-user** (Android + iOS en paralelo, KMP +
Compose Multiplatform) que construye un GDD vivo a partir de notas y quiere
ofrecer embeddings, enlazado por similitud y consulta en lenguaje natural (RAG).
El puerto `AiClient` ya existe en `:core` (feature `ai-client-interface`,
`accepted`) con `embed(texts): List<Embedding>` y `generate(request): String`, más
un `FakeAiClient` determinista y sin red.

Faltaba decidir **dónde corre la IA**: cloud, on-device o híbrido. Es el riesgo
principal postergado (`docs/risks-and-open-questions.md`). Restricciones del
producto y del repo:

- **Offline-first:** la captura nunca se bloquea por la IA; el indexado tolera
  fallos y se reintenta (`offline-pending-retry`).
- **Código compartido real en KMP** es criterio de éxito; evitar lógica duplicada
  por plataforma.
- **Sin backend, sin cuentas.** Es un proyecto de aprendizaje; los secrets no se
  versionan nunca.
- **On-device no es requisito del MVP** (`docs/build-brief.md`).
- El repo **no puede** ejecutar red ni modelos nativos en el gate: la decisión se
  apoya en un **spike comparativo de arquetipos detrás del puerto** y en
  evidencia medida vs estimada.

## Criterios de decisión

1. **Offline-first:** la captura no se bloquea; el indexado tolera fallos.
2. **Código compartido KMP** (Android + iOS) vs superficie nativa por plataforma.
3. **Secrets y costo** (cloud) vs **peso/tamaño** (on-device).
4. **Latencia** aceptable para indexado asíncrono y RAG.
5. **Calidad** suficiente y **privacidad**.
6. **Testabilidad/determinismo** con el fake existente.
7. **Escalabilidad a Fase B** sin refactor mayor.

## Alternativas consideradas

### A. Cloud-only

Todas las llamadas (`embed` y `generate`) van a una API cloud detrás de
`AiClient`; el adaptador es puro KMP (HTTP + serialización).

| Criterio | Evaluación |
| --- | --- |
| Offline-first | **Medio.** La captura no depende de red; el indexado/RAG sí. Mitigable con `pendiente` + reintento. |
| Código compartido KMP | **Alto.** Adaptador único compartido. |
| Secrets/costo | **Bajo.** Requiere API key y costo recurrente; sin backend, la gestión de keys en un local-first es no trivial. |
| Latencia | **Medio.** Red variable; aceptable para indexado async y RAG. |
| Calidad/privacidad | Calidad **Alta**; privacidad **Baja** (las notas salen del dispositivo). |
| Testabilidad | **Alto.** El puerto y el fake desacoplan; el adaptador real no es testeable sin red. |
| Escalabilidad Fase B | **Medio.** Escala, pero ata cada consumidor al proveedor y al costo. |

### B. On-device-only

Runtime nativo por plataforma (modelos de embeddings y, eventualmente, LLM)
detrás de `AiClient`.

| Criterio | Evaluación |
| --- | --- |
| Offline-first | **Alto.** Funciona sin red. |
| Código compartido KMP | **Bajo.** Dependencias nativas pesadas por plataforma; mucha superficie no compartida. |
| Secrets/costo | Sin keys ni costo, pero **peso/tamaño Bajo**: modelos pesan y agrandan la app. |
| Latencia | **Medio.** Depende del hardware; la generación suele ser lenta. |
| Calidad/privacidad | Privacidad **Alta**; calidad **Media/Baja** hoy, sobre todo en generación. |
| Testabilidad | **Medio.** El adaptador nativo no se ejercita con el fake; CI más pesada. |
| Escalabilidad Fase B | **Bajo/Medio.** La Fase B (generación de niveles) es pesada on-device. |

### C. Híbrido (por fases, cloud-first)

Un adaptador cloud detrás de `AiClient` para el primer flujo real, y la
posibilidad de inyectar adaptadores on-device (p. ej. embeddings) más adelante,
sin tocar dominio ni UI.

| Criterio | Evaluación |
| --- | --- |
| Offline-first | **Alto.** Embebido local elimina la dependencia de red del indexado; el cloud queda tolerante a fallos. |
| Código compartido KMP | **Alto/Medio.** El segmento cloud es compartido; el on-device es parcial. |
| Secrets/costo | **Medio.** El segmento cloud necesita key/costo; el local no. |
| Latencia | **Alto.** Embedding local inmediato; generación cloud cuando hay red. |
| Calidad/privacidad | **Alto.** Privacidad con lo local; calidad con lo cloud. |
| Testabilidad | **Alto.** Mismo puerto y mismo fake; adaptadores sustituibles. |
| Escalabilidad Fase B | **Alto.** Cada consumidor elige el runtime sin refactor. |

## Decisión

**Se adopta la alternativa C (híbrida por fases), ejecutada en este orden:**

1. **MVP / primer flujo real (cloud-first):** implementar el **primer adaptador
   real en la nube** detrás del puerto `AiClient` (lo materializará
   `embeddings-generation`, y `rag-query` para `generate`). El indexado sigue
   **asíncrono y tolerante a fallos** (`capturada → pendiente → indexada/fallida`
   con reintento), por lo que la captura nunca se bloquea.
2. **Siguiente iteración (on-device):** cuando el flujo cloud esté validado y se
   elija un runtime nativo concreto, agregar un adaptador **on-device** (empezando
   por embeddings) inyectado por el mismo puerto. No requiere tocar dominio ni UI.
3. **Default offline/determinista:** `FakeAiClient` sigue siendo el adaptador por
   defecto inyectable mientras no haya uno real configurado.

El puerto `AiClient` **no cambia**: es agnóstico de proveedor, modelo, dimensión
y ubicación, y es lo que permite esta estrategia por fases. Esta decisión fija la
**estrategia de runtime**; la elección de proveedor/modelo/dimensión y la gestión
de keys quedan como follow-up (ver "Preguntas abiertas").

### Política de secrets (obligatoria)

Ninguna API key, token o secret se versiona ni se commitea. El adaptador cloud
leerá su credencial de configuración de build/entorno **fuera del repo** (p. ej.
`local.properties` ignorado o variable de entorno inyectada en build), nunca de
un archivo versionado. Si la UX termina siendo "key provista por el usuario",
tampoco debe persistirse en claro en el repo; su manejo se diseña en la feature
que implemente el adaptador cloud.

## Consecuencias

**Positivas**

- Camino más rápido a un flujo de IA **real** (criterio de aprendizaje) sin
  comprometer el core ni la captura offline-first.
- El puerto absorbe cambios de proveedor/modelo/runtime; la decisión por fases se
  puede revisar sin refactor de dominio.
- La ruta on-device queda abierta para mejorar privacidad y quitar dependencia de
  red/costo del indexado.

**Negativas / costos**

- El primer consumidor cloud **obliga** a resolver gestión de keys y control de
  costo antes de `embeddings-generation`/`rag-query`.
- A largo plazo conviven dos familias de adaptadores (cloud y on-device), con más
  superficie de test.
- Riesgo de fuga de vocabulario del proveedor al puerto: el puerto debe seguir
  siendo vendor-neutral (mitigación: los tipos del puerto no cambian).

**Neutras**

- No se agregan dependencias de IA a producción en esta feature; el spike es
  test-only y sin red.

## Evidencia

- Spike comparativo reproducible, test-only y sin red:
  `docs/spikes/ai-runtime-spike.md`.
- Comandos: `./gradlew :core:testDebugUnitTest` y
  `./gradlew :core:iosSimulatorArm64Test` (en macOS). Ambos verdes con el harness
  del spike (2 tests por plataforma) más los tests previos.
- El reporte del spike verifica conformidad de contrato sobre **dos arquetipos**
  (local/on-device-like y cloud-like) detrás de `AiClient`, offline del local,
  mapeo de fallo cloud-like → `AiClientException` con degradación de un consumidor
  y rechazo de un embedding vacío (control negativo). Latencia local informativa:
  `elapsed_us=683 / ns_per_note=1138` (Android/JVM) y `elapsed_us=798 /
  ns_per_note=1330` (iOS), medida **sobre el fake determinista del repo**, no
  sobre un modelo real.
- **No medido aquí** (estimación/limitación): latencia, calidad y costo reales de
  cloud (requieren key + red, prohibido en gate/repo); footprint/memoria/tiempo de
  un modelo on-device real (requieren deps nativas pesadas). Se documentan como
  estimaciones con fuentes fechadas en el reporte del spike, no como mediciones.

## Sonda manual opcional (key-gated, fuera del gate)

Opcional y **nunca** parte de `./init.sh`. Requiere una key propia en el entorno
del desarrollador; **no** se comitea. Ejemplo genérico (sustituir proveedor,
modelo y endpoint al elegirlos):

```bash
# NO ejecutar en CI ni en init.sh. La key vive sólo en el entorno local.
curl -sS "https://<provider>/v1/embeddings" \
  -H "Authorization: Bearer $AI_RUNTIME_PROBE_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"model":"<model>","input":"jefe final"}' \
  | jq '.data[0].embedding | length'
```

- Variable de entorno: `AI_RUNTIME_PROBE_KEY`.
- Resultado esperado: una dimensión `> 0` (p. ej. 768/1536, según el modelo).
- Resultado registrado, si se obtiene, debe ir **fechado** en
  `docs/spikes/ai-runtime-spike.md` como medición puntual del proveedor, no como
  una medición del repo.

## Preguntas abiertas

- ¿Qué proveedor cloud y qué modelos de **embeddings** y **generación**? (La
  decisión de runtime no fija vendor.)
- ¿Cuál es la **dimensión** del embedding y cómo se versiona para poder
  recalcular si cambia?
- **Gestión de keys:** ¿key provista por el usuario en la app, o inyectada en
  build para uso personal? Sin backend, es la decisión de UX/seguridad pendiente.
- ¿`embed` y `generate` comparten proveedor o pueden ser de proveedores
  distintos?
- ¿Qué runtime **on-device** concreto (y en qué plataforma primero) para el
  adaptador local futuro?
- Límites de costo/cuota y posible caché de embeddings para no re-indexar.
- ¿Qué datos (contenido de notas) se envían al proveedor y con qué política de
  retención?
