# Build Brief

## Problem
Las ideas de diseño de juegos (mecánicas, niveles, narrativa, arte) se capturan
en múltiples lugares y quedan dispersas, sin enlaces entre sí y sin una forma
ágil de consultarlas. Mantener un GDD al día es manual y costoso.

## Current Workaround / Existing System
Mezcla desordenada de varias herramientas (notas sueltas, chats con IA,
papel/bocetos, documentos). Nada se enlaza automáticamente ni se resume solo, y
no hay búsqueda semántica sobre lo ya capturado.

## Target Users
- **Primario:** el autor (game dev aficionado). Único usuario del MVP.
- **Secundario (post-MVP):** otras personas que diseñan juegos y toman notas
  dispersas.

## Goals
- Capturar ideas rápido en texto, voz e imagen de bocetos.
- Clasificar, etiquetar y tipificar notas automáticamente con IA.
- Enlazar notas por similitud semántica.
- Construir un GDD vivo y navegable.
- Consultar en lenguaje natural sobre las notas propias (RAG).
- Aprender: IA integrada en un flujo real, KMP compartido de verdad, al menos
  una integración nativa, y base escalable a la Fase B sin refactor mayor.

## Non-Goals
- Multi-usuario, cuentas, autenticación o colaboración (MVP single-user
  local-first).
- Editor de niveles 2D, generación automática de niveles, evaluación de
  dificultad y export a Godot/Unity/LÖVE (Fase B).
- Desktop y Web en el MVP (se apunta a Android + iOS en paralelo).
- Runtime de IA on-device como requisito del MVP (decisión abierta detrás de
  `AiClient`).
- Dirección de diseño visual formal (`DESIGN.md`) — diferida explícitamente.

## MVP Slice
Alcance completo del concepto, pero construido como **secuencia explícita de
features** (no todo en paralelo). Orden preliminar:

1. Modelo de `Nota` + persistencia local (SQLDelight) + CRUD de notas de texto.
2. Captura por voz (speech-to-text nativo) → `Nota` con transcripción.
3. Captura por imagen de boceto (cámara) → `Nota` con `Adjunto`.
4. Enriquecimiento IA (categoría, etiquetas, tipo) detrás de `AiClient`, con
   estados `capturada → pendiente/enriquecida/fallida` y reintento.
5. Enlazado por similitud (embeddings como BLOB + similitud coseno en memoria).
6. Vista de GDD que agrupa y resume las notas.
7. Consulta en lenguaje natural (RAG) sobre las notas propias.

El detalle granular de features y su criterio de done se deriva en la fase
harness, no en este brief.

## Validation Plan
- Flujo end-to-end: crear nota (texto/voz/imagen) → enriquecida por IA →
  enlazada y agrupada en el GDD → consulta en lenguaje natural respondida con
  notas reales.
- Tests del core KMP con un `AiClient` fake (deterministas, sin red).
- Prueba manual en Android y iOS.

## Success Criteria
- IA funcional en un flujo real, no un chatbot decorativo.
- Código compartido real en KMP (sin lógica duplicada por plataforma).
- Al menos una integración nativa resuelta correctamente (STT y/o cámara).
- Base de código extensible a la Fase B sin refactor mayor.
- Aproximarse a "lanzar a producción" (distribución en Android/iOS).

## Notes
- Idioma de los documentos: español, con términos técnicos en inglés.
- `Owner` se modela aunque el MVP sea single-user.
- El foco es aprender; el alcance puede recortarse si amenaza el aprendizaje.
