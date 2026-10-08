# Risks and Open Questions

## Blocking Next Phase
- **Runtime de IA (cloud / on-device / híbrido) y proveedor.** El puerto
  `AiClient` ya existe en `:core` (feature `ai-client-interface`), pero sigue
  **sin decidirse** el runtime/proveedor, así que el flujo de IA (embeddings +
  RAG) no se puede validar. Es el riesgo principal del proyecto, hoy postergado
  a propósito a `ai-runtime-decision`.
- **¿Los Adjuntos sin texto entran al RAG?** Definir si un audio sin transcribir
  o un boceto sin OCR son indexables (y cómo). Afecta el pipeline de indexado.

## Implementation-Time Questions
- Tokens visuales definitivos (paleta, tipografía, logo) — pendientes del equipo
  de UI/UX; `DESIGN.md` tiene una dirección provisional.
- Modelo de STT y cámara por plataforma, más manejo de permisos.
- Modelo y dimensión del embedding concreto.
- ¿Los enlaces son bidireccionales? ¿Cuándo se recalculan y con qué umbral?
- Comportamiento al cambiar el `track` de una Nota ya indexada.
- Orden final y granularidad de las features del MVP (fase harness). Requiere
  **re-derivar `feature_list.json`** tras el cambio de alcance (se descartó el
  enriquecimiento/clasificación IA y se introdujeron los tracks).

## Later / Not MVP
- Multi-usuario, cuentas, sync y backend.
- Fase B: editor de niveles, generación desde prompt, dificultad, export
  Tiled/Godot/Unity/LÖVE.
- Desktop y Web.

## Assumptions
- Un único usuario por instalación.
- La captura nunca debe bloquearse por la IA.
- SQLDelight + similitud en memoria alcanza sin una vector DB dedicada.
- Los criterios de éxito son de aprendizaje, no de escala.
- Sin clasificación IA, el usuario organiza cada Nota eligiendo un `track`; la
  auto-organización no es un requisito del MVP.

## Risks
- **Postergar el runtime de IA** mantiene abierto el riesgo principal; si se
  deja para el final, el flujo de IA (embeddings + RAG) puede no llegar a
  validarse.
- **Android + iOS en paralelo** duplica esfuerzo nativo y puede retrasar el
  primer flujo end-to-end.
- **Alcance amplio del MVP** (texto + voz + imagen + GDD + RAG): sin respetar la
  secuencia, se corre el riesgo de terminar varias cosas a medias y ninguna
  end-to-end.
- **Sin clasificación IA**, notas mal clasificadas por el usuario ensucian el
  GDD; el diseño de UI debe hacer fácil elegir y corregir el track.
- **Sin tokens visuales definitivos**, drift visual en las features de UI
  mientras el equipo de UI/UX no entregue la dirección.

## Research Tasks
- Spike comparativo cloud vs on-device para clasificación y embeddings.
- Evaluar `sqlite-vec` u otra extensión vectorial si el volumen de notas crece
  y la similitud en memoria deja de alcanzar.
