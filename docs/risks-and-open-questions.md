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
- Modelo de STT por plataforma (voz). La cámara y el manejo de permisos ya se
  resolvieron en `image-capture-camera`: Android pide `CAMERA` en runtime y usa
  `FileProvider`/`PickVisualMedia`; iOS declara `NSCameraUsageDescription` y usa
  `UIImagePickerController`/`PHPickerViewController`; la galería no requiere
  permiso en ninguna plataforma.
- Modelo y dimensión del embedding concreto.
- ¿Los enlaces son bidireccionales? ¿Cuándo se recalculan y con qué umbral?
- Comportamiento al cambiar el `track` de una Nota ya indexada.
- Orden final y granularidad de las features del MVP (fase harness). Requiere
  **re-derivar `feature_list.json`** tras el cambio de alcance (se descartó el
  enriquecimiento/clasificación IA y se introdujeron los tracks).

## Resolved / Tooling Limitations
- **Migración de esquema sin precedentes:** la v1 → v2 (`1.sqm` + `Attachment.sq`)
  quedó cubierta por `verifyMigrationV1ToV2` en Android/JVM e iOS, y verificada en
  el simulador iOS real (`user_version` 1→2 con notas preservadas y tabla
  `attachment`). Sin bloqueos.
- **`FileProvider` + cámara Android:** `ActivityResultContracts.TakePicture` no
  agrega los flags de grant de URI y la app de cámara no podía escribir el output;
  resuelto con un intent `ACTION_IMAGE_CAPTURE` explícito con
  `FLAG_GRANT_READ/WRITE_URI_PERMISSION` + `clipData`.
- **Cámara iOS fuera del harness:** el simulador no expone cámara; la captura real
  de cámara en iOS sólo se valida en dispositivo físico → se mapea a
  `CAMERA_UNAVAILABLE` y no se fabrica evidencia. La galería (`PHPicker`) queda
  verificada por build/link/launch, sin input automation iOS disponible
  (`idb`/`cliclick` ausentes; `osascript`/System Events bloqueado), igual que en
  features previas de UI.

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
