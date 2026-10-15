# ADR 0004: Integración de IA real (proveedor propio OpenAI-compatible)

- **Status:** propuesto (dirección aprobada por el autor el 2026-10-15; pendiente
  de promoción a `accepted` si corresponde).
- **Fecha:** 2026-10-15
- **Contexto de origen:** revisión de alcance del MVP Android-first (discovery).
- **Decisores:** autor del producto (game dev aficionado).
- **Relacionado:** `docs/adr/0001-ai-runtime-decision.md`,
  `docs/adr/0003-ios-congelado-mvp.md`, `docs/technical-discovery.md`,
  `docs/build-brief.md`, `docs/risks-and-open-questions.md`, `CONTEXT.md`,
  `CONSTRAINTS.md`.

## Contexto

El MVP original dejó la IA detrás del puerto `AiClient` con un `FakeAiClient`
determinista; el ADR 0001 decidió una estrategia **híbrida por fases, cloud-first**
y dejó proveedor/modelo/keys como follow-ups. La revisión de alcance 2026-10-15
quiere **IA real** en el MVP para dar funcionalidad completa: generación (Q&A y
sugerencias del Brainstorming), embeddings (indexado), transcripción de audio,
lectura en voz alta (tts), generación de imagen y sugerencia de clasificación.

El autor dispone de un **proveedor propio** compatible con la API de OpenAI
(`https://inference.devexpert.io/v1`) con modelos **chat, tts, transcribe,
embedding, jev e image**, y pide "mínimo un caso de uso por modelo".

Restricciones relevantes:

- **Nunca versionar secrets**: la API key no puede ir en el repo.
- El **gate** (`init.sh`) es no bloqueante, sin red; los **tests** deben ser
  deterministas.
- `AiClient` hoy es **text-only** (`embed`+`generate`); los demás casos necesitan
  contratos nuevos.
- El non-goal "sin clasificación IA" se **revisó** (2026-10-15): la IA **sugiere**
  y el usuario **confirma**.

## Criterios de decisión

1. **Funcionalidad completa del MVP** con un caso de uso por modelo.
2. **Código compartido KMP** y encaje con `AiClient`/puertos existentes.
3. **Secrets y costo**: key fuera del repo; control de cuota.
4. **Determinismo del gate/tests** (fake offline).
5. **Reversibilidad** (poder cambiar de proveedor/modelo).
6. **Offline-first** de la captura (la IA no debe bloquear).

## Alternativas consideradas

### A. Proveedor propio OpenAI-compatible (elegido)

Usar `https://inference.devexpert.io/v1` con los 6 modelos y una sola key.

- **A favor:** un solo proveedor y protocolo estándar (OpenAI) para todos los
  casos; el `AiClient` actual encaja; se controla modelo/costo/cuota; base URL y
  key configurables.
- **En contra:** dependencia de red y de un proveedor concreto; hay que
  implementar los puertos de tts/transcribe/image/jev.

### B. Proveedor público multiuso (OpenAI/Gemini)

Usar un proveedor cloud comercial conocido.

- **A favor:** ecosistema y docs; embeddings+chat en un proveedor.
- **En contra:** costo/keys ajenas al proyecto; el autor ya tiene un proveedor
  propio; no aporta sobre A.

### C. Multi-proveedor conmutable

Abstracción que permita elegir proveedor entre varios.

- **A favor:** flexibilidad.
- **En contra:** sobre-ingeniería para el MVP; mayor superficie y configuración.
  Se mantiene como **reversible** detrás de los puertos (no se implementa).

### D. On-device (fase 2 del ADR 0001)

Modelos locales.

- **A favor:** offline.
- **En contra:** peso, tamaño y calidad; fuera de alcance del MVP. Se conserva
  como fase 2 del ADR 0001.

## Decisión

**Se adopta A: proveedor propio, compatible con OpenAI, detrás de puertos de IA.**

1. **Base URL** `https://inference.devexpert.io/v1`; **6 modelos**: chat, tts,
   transcribe, embedding, jev, image.
2. **Mapeo modelo → caso de uso (mínimo uno):**
   - chat → generación en Q&A y sugerencias del Brainstorming;
   - embedding → indexado (y con él Enlaces/RAG);
   - transcribe → transcripción del Adjunto AUDIO;
   - tts → lectura en voz alta (Q&A, Nota, narración del GDD);
   - image → generación de imagen desde texto (adjunta a una Nota);
   - jev → **sugerencia de clasificación** (texto → `Track`/`Género`, con
     confirmación del usuario).
3. **Puertos:** extender `AiClient` (text-only) y/o nuevos puertos (transcribe,
   tts, image, jev). El diseño del contrato es la feature `ai-client-ports`.
4. **Fake/real por build flag:** `fake` (determinista, **default y siempre en
   tests/gate**) o `real` (requiere **API key**). La key se provee por build flag
   (env/gradle property, **gitignored**); **nunca** se versiona.
5. **Clasificación asistida:** la IA **sugiere**, el usuario **confirma**; nunca
   se auto-asigna.

## Consecuencias

**Positivas**

- Funcionalidad completa del MVP con un proveedor y protocolo estándar.
- `init.sh` y los tests siguen **offline/deterministas** (modo fake).
- Reversible: cambiar de proveedor/modelo es configuración detrás de los puertos.

**Negativas / costos**

- Superficie nueva (puertos + adaptadores) y dependencia de red para la IA real.
- Costo/cuota y manejo de fallos de red por caso (a resolver en cada spec).
- El gate **no** ejercita la API real: la calidad del modelo real no se verifica
  automáticamente (smoke manual con key, fuera del gate).

**Neutras**

- No cambia el esquema por sí mismo; `ai-suggest-classification` toca Géneros y
  `ai-real-embeddings` respeta la máquina de estados existente.

## Disparadores de re-evaluación

Reabrir este ADR si ocurre **cualquiera** de:

- Se necesita un proveedor o modelo distinto (calidad/costo) o multi-proveedor.
- Se quiere IA on-device (fase 2 del ADR 0001).
- Aparecen límites de cuota/costo que exijan cacheo, batching o degradación.

## Preguntas abiertas

- Modelos y dimensión concretos de embedding; parámetros por caso.
- Política de reintentos/timeouts por caso y UX de "IA no disponible".
- Límites de costo/cuota y observabilidad de uso.
