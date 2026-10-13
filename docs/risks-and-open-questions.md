# Risks and Open Questions

## Blocking Next Phase
- **¿Los Adjuntos sin texto entran al RAG?** Definir si un audio sin transcribir
  o un boceto sin OCR son indexables (y cómo). Afecta el pipeline de indexado.
  Precisión tras `image-ocr-optional` (2026-10-13): el **OCR del boceto queda
  descartado del MVP** (ver "Resolved"), así que un `Adjunto` de imagen **no**
  aporta texto indexable; la pregunta se reduce a si un `Adjunto` sin texto
  participa del RAG o se excluye, y la decide la feature de indexado/RAG.

## Resolved
- **OCR del boceto (`image-ocr-optional`):** **descartado del MVP** (2026-10-13).
  Decisión, alternativas (ML Kit / iOS Vision / `AiClient`+visión / OCR cloud /
  descartar), trade-offs y **disparadores de re-evaluación** en
  `docs/adr/0002-ocr-decision.md`. Motivo: el objetivo es texto **manuscrito** de
  calidad incierta con los motores on-device de bajo costo, Android exigiría una
  dependencia pesada (ML Kit) poco compartida, y `AiClient` es text-only. Sin
  código/deps/esquema nuevos. Si se reabre, el patrón sería un puerto
  `TextRecognizer` + adaptadores nativos (patrón `VoiceTranscriber`).
- **Runtime de IA (cloud / on-device / híbrido):** **decidido** en
  `ai-runtime-decision` (2026-10-09). Estrategia **híbrida por fases, cloud-first**
  detrás del puerto `AiClient`; decisión y trade-offs en
  `docs/adr/0001-ai-runtime-decision.md`, evidencia en
  `docs/spikes/ai-runtime-spike.md`. Ya no bloquea el flujo de IA; el primer
  adaptador real lo materializa `embeddings-generation`/`rag-query`. Proveedor,
  modelo, dimensión y gestión de keys quedan como follow-ups del ADR.
- **Enlaces (direccionalidad, umbral y recálculo):** **resuelto** en
  `semantic-linking` (2026-10-13). Enlaces **bidireccionales guardados una sola
  vez** con orden canónico `note_a_id < note_b_id` (se leen desde ambos lados);
  umbral inclusivo `score >= 0.8` (`DEFAULT_SIMILARITY_THRESHOLD`, inyectable);
  recomputo explícito e idempotente (`SemanticLinkingService.recomputeAll()`).
  Queda como **follow-up**: el **trigger** del recomputo (al indexar/editar,
  offline) y la UI de lectura (`note-links-ui`).

## Implementation-Time Questions
- Tokens visuales definitivos (paleta, tipografía, logo) — pendientes del equipo
  de UI/UX; `DESIGN.md` tiene una dirección provisional.
- El **STT** (`voice-capture-stt`) y la **cámara** (`image-capture-camera`) ya se
  resolvieron: `SpeechRecognizer`/`SFSpeechRecognizer` para voz; `CAMERA` +
  `FileProvider`/`PickVisualMedia` en Android y
  `UIImagePickerController`/`PHPickerViewController` en iOS (galería sin permiso)
  para imagen.
- Modelo, proveedor y dimensión del embedding concreto; gestión de keys cloud
  (usuario vs build) — follow-ups del ADR 0001.
- Runtime on-device concreto para la fase 2 (híbrida).
- Comportamiento al cambiar el `track` de una Nota ya indexada.
- Orden final y granularidad de las features del MVP (fase harness). Requiere
  **re-derivar `feature_list.json`** tras el cambio de alcance (se descartó el
  enriquecimiento/clasificación IA y se introdujeron los tracks).

## Resolved / Tooling Limitations
- **Migración de esquema sin precedentes:** la v1 → v2 (`1.sqm` + tabla
  `embedding`) quedó cubierta por `verifyDatabaseMigration` en Android/JVM e iOS, y
  verificada en el simulador iOS real (`user_version` 1→2 con notas preservadas y
  tabla `embedding`). La v4 → v5 (`4.sqm`, tabla `note_link`) quedó cubierta por
  `verifyDatabaseMigration`/`verifyMigrationV1ToV5`/`verifyNoteTagMigration` y
  verificada en Android (emulador) e iOS (simulador) con `user_version` 4→5,
  `note_link` + índice y Notas preservadas. Sin bloqueos.
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
- **Android + iOS en paralelo** duplica esfuerzo nativo y puede retrasar el
  primer flujo end-to-end.
- **Alcance amplio del MVP** (texto + voz + imagen + GDD + RAG): sin respetar la
  secuencia, se corre el riesgo de terminar varias cosas a medias y ninguna
  end-to-end.
- **Sin clasificación IA**, notas mal clasificadas por el usuario ensucian el
  GDD; el diseño de UI debe hacer fácil elegir y corregir el track.
- **Sin tokens visuales definitivos**, drift visual en las features de UI
  mientras el equipo de UI/UX no entregue la dirección.
- **Verificación del STT no determinista:** no hay harness de UI/E2E ni forma de
  inyectar audio o simular el motor de voz nativo; el emulador/simulador puede no
  tener entrada de audio o servicio de reconocimiento. La integración se verifica
  con smoke manual (permiso concedido/denegado, cancelar, sin habla) y el happy
  path de audio real → Nota puede requerir dispositivo físico. Se documenta como
  limitación de entorno, no como fallo del código (`voice-capture-stt`).

## Research Tasks
- **Hecho (2026-10-09):** spike comparativo de arquetipos cloud vs on-device
  detrás de `AiClient` → `docs/spikes/ai-runtime-spike.md`; decisión en
  `docs/adr/0001-ai-runtime-decision.md`.
- **Pendiente:** verificar latencia/calidad/costo cloud reales con la sonda
  key-gated del ADR (fuera del gate) al elegir proveedor/modelo.
- Evaluar `sqlite-vec` u otra extensión vectorial si el volumen de notas crece
  y la similitud en memoria deja de alcanzar.
