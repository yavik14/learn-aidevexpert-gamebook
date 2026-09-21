# Risks and Open Questions

## Blocking Next Phase
- **Runtime de IA (cloud / on-device / híbrido) y proveedor.** `AiClient` se
  diseña ya, pero el flujo de IA no se puede validar sin elegir. Es el riesgo
  principal del proyecto, hoy postergado a propósito.
- **¿Los Adjuntos sin texto entran al RAG?** Definir si un audio sin transcribir
  o un boceto sin OCR son indexables (y cómo). Afecta el pipeline de
  enriquecimiento.
- **Diseño visual (`DESIGN.md`).** Diferido; bloquea las features de UI.

## Implementation-Time Questions
- Modelo de STT y cámara por plataforma, más manejo de permisos.
- Modelo y dimensión del embedding concreto.
- ¿Los enlaces son bidireccionales? ¿Cuándo se recalculan y con qué umbral?
- Estrategia para no pisar correcciones manuales al re-enriquecer.
- Orden final y granularidad de las features del MVP (fase harness).

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

## Risks
- **Postergar el runtime de IA** mantiene abierto el riesgo principal; si se
  deja para el final, el flujo de IA puede no llegar a validarse.
- **Android + iOS en paralelo** duplica esfuerzo nativo y puede retrasar el
  primer flujo end-to-end.
- **Alcance amplio del MVP** (texto + voz + imagen + GDD + RAG): sin respetar la
  secuencia, se corre el riesgo de terminar varias cosas a medias y ninguna
  end-to-end.
- **Sin `DESIGN.md`**, drift visual en las features de UI.

## Research Tasks
- Spike comparativo cloud vs on-device para clasificación y embeddings.
- Evaluar `sqlite-vec` u otra extensión vectorial si el volumen de notas crece
  y la similitud en memoria deja de alcanzar.
