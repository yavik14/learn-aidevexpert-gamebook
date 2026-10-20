# Risks and Open Questions

## Blocking Next Phase
- **¿Los Adjuntos sin texto entran al RAG?** Definir si un audio sin transcribir
  o un boceto sin OCR son indexables (y cómo). Afecta el pipeline de indexado.
  Precisión: el **OCR del boceto queda descartado del MVP**
  (`docs/adr/0002-ocr-decision.md`), así que un `Adjunto` de imagen **no** aporta
  texto indexable; un Adjunto `AUDIO` sí existe pero la transcripción vive en el
  `body`. `rag-query` sólo indexa `note.body` no vacío. La pregunta se reduce a si
  un `Adjunto` sin texto participa del RAG o se excluye, y la decide la feature de
  indexado/RAG.
- **Re-derivar `feature_list.json`.** El alcance cambió el 2026-10-15
  (Android-first, Géneros, Proyecto/Brainstorming, GDD híbrido). El backlog actual
  está agotado (todo `accepted` salvo el upload iOS `blocked`); hay que derivar las
  features del MVP revisado antes de la próxima implementación (fase harness).

## Resolved
- **Plataforma iOS (congelar):** **decidido** el 2026-10-15 (Android-first). iOS
  queda fuera del alcance y del gate; se conserva el código. Ver
  `docs/adr/0003-ios-congelado-mvp.md`.
- **OCR del boceto (`image-ocr-optional`):** **descartado del MVP** (2026-10-13).
  Decisión, alternativas y disparadores en `docs/adr/0002-ocr-decision.md`. Sin
  código/deps/esquema nuevos. Si se reabre, patrón puerto `TextRecognizer` +
  adaptadores nativos.
- **Runtime de IA (cloud / on-device / híbrido):** **decidido** en
  `ai-runtime-decision` (2026-10-09). Estrategia **híbrida por fases, cloud-first**
  detrás del puerto `AiClient` (`docs/adr/0001-ai-runtime-decision.md`, evidencia en
  `docs/spikes/ai-runtime-spike.md`). El **proveedor real del MVP** quedó definido
  en `docs/adr/0004-ai-real-integration.md` (propio, OpenAI-compatible;
  chat/embedding/transcribe/tts/image/jev; key por build flag; fake/real). El
  Brainstorming reusa este puerto para las sugerencias.
- **Enlaces (direccionalidad, umbral y recálculo):** **resuelto** en
  `semantic-linking` (2026-10-13); UI de lectura en `note-links-ui` (2026-10-14).
  Follow-up: el **trigger de ciclo de vida** del recomputo (al indexar/editar,
  offline) y el recomputo incremental/background.
- **Respuesta RAG desde las Notas propias:** **resuelto** en `rag-query`
  (2026-10-13). Retrieval por similitud coseno real + `GenerateRequest(prompt,
  context)`; sin Notas indexadas devuelve `NoIndexedNotes` y **no** llama al
  runtime. Queda abierto el **runtime real** (hoy `FakeAiClient`) y la **calidad**.
- **Miniaturas de imagen:** **implementadas** en `sketch-thumbnails`
  (2026-10-19). El puerto `AttachmentFileStore` se **reubicó en `:core`**
  (`com.playbook.core.storage`) y se extendió con
  `read(relativePath): ByteArray?`; la UI decodifica con `decodeImageBitmap()`
  existente (sin deps nuevas). El render es perezoso (`produceState`) y degrada
  sin imagen si el archivo falta/no decodifica. **Sigue abierto** el
  cacheo/redimensionado de miniaturas (memoria o disco): hoy se decodifica a
  tamaño completo y se escala en pantalla, con picos de memoria posibles en
  bocetos grandes.
  - **Riesgo residual aceptado (iOS):** el guard de path traversal es léxico
    (normaliza `.`/`..` y separadores) y no resuelve symlinks. Se acepta porque
    las rutas las genera la propia app (UUID), el usuario es único y no hay
    entrada externa de paths; no se cambia el contrato de `read`/`delete`.
- **Generación de imagen desde texto:** **implementada** en
  `image-generation-from-text` (2026-10-20). `TextImageGenerationService`
  (`:core`) valida prompt/`GeneratedImage`; la UI ofrece "Generar con IA" desde
  la lista y reutiliza `CreateWithImage`/`saveNoteWithImage` para persistir el
  Adjunto `IMAGE`. El fake devuelve un PNG decodificable con chunk `tEXt`
  determinista, así el smoke fake muestra preview/miniatura sin red. El store
  conserva la extensión `.jpg` fija (el mime real va en la columna) y el
  proveedor real puede devolver JPEG; ambos comportamientos están aceptados.
  **Sigue abierto** el smoke real key-gated y el cacheo de miniaturas. Sin
  esquema, deps ni cambios de `init.sh`.
- **Migración de Etiquetas → Géneros:** **resuelto** en
  `genres-catalog-persistence` (2026-10-16): las etiquetas libres existentes se
  copian **verbatim** como **Géneros personalizados** de cada Nota mediante el
  **renombre** de la tabla N—N `note_tag`→`note_genre` (esquema **v7**, `6.sqm`,
  `INSERT … SELECT` + `DROP TABLE note_tag`); no se reutiliza la tabla vieja ni se
  materializa el catálogo en la base.
- **Proyecto de Juego (esquema v8):** **resuelto** en `game-project-persistence`
  (2026-10-19): ficha singleton en `game_project` + tablas hijas (`7.sqm` v7→v8,
  aditivo), `GameProjectRepository` (`get`/`save` upsert) en `:core`. Los campos
  son opcionales (`0..3`/`0..5`), el estado `vacío`/`esbozado` se **deriva** (no
  se persiste), no hay FK Nota→Proyecto y la feature no cablea UI ni crea fila de
  Proyecto al migrar. Ver `docs/specs/game-project-persistence.md`.

## Implementation-Time Questions
- **IA real (proveedor propio):** modelos y dimensión concretos de embedding;
  comportamiento ante fallos de red/timeout por caso (chat/embedding/transcribe/
  tts/image/jev); límites de costo/cuota; y cómo se degrada cada superficie cuando
  no hay key (modo fake) o la API falla.
- **Pre-clasificación por contexto** al "guardar como Nota": **resuelta** en
  `brainstorming-note-per-step` (implementada 2026-10-19; ver
  `docs/specs/brainstorming-note-per-step.md`): los 9 pasos generan Nota con el
  mapeo Título→sin Track; Género→sin Track (body = Géneros); Core Loop→
  `mecánicas`; Audiencia→sin Track; Concepto→`historia`; Inspiraciones→sin
  Track; Fantasía→`historia`; Keys→`mecánicas`; Out→sin Track. La Nota es final,
  indexada best-effort y los duplicados exactos se bloquean por contenido. Para
  Q&A, **resuelta** en `save-answer-as-note` (implementada 2026-10-20): la
  respuesta se guarda sola (`body` sin la pregunta), `track = null` ("Sin track")
  y sin Géneros porque no hay un Track único ni clasificación confirmada; el
  usuario los agrega luego en el editor. Mismo criterio de duplicado exacto
  (contenido + Track + Géneros). Ver `docs/specs/save-answer-as-note.md`.
- **UI fina del wizard:** **resuelta** en `brainstorming-wizard` (2026-10-19;
  validación independiente `accept` en `docs/validations/brainstorming-wizard.md`): 9 pasos,
  progreso `Paso X/9`, guardado explícito, reapertura con precarga, topes 3/3/5,
  `GenrePicker` compartido y sin sugerencias IA (esas quedan en
  `brainstorming-ai-suggestions`). Ver `docs/specs/brainstorming-wizard.md`.
- **Normalización de Géneros:** **resuelto** en `genres-catalog-persistence`
  (2026-10-16): `normalizeGenres` aplica trim, descarta vacíos, **canoniza**
  case-insensitive contra `GenreCatalog` (un personalizado que coincide con el
  catálogo se trata como de catálogo), deduplica case-insensitive conservando la
  primera grafía y ordena. Ver `docs/specs/genres-catalog-persistence.md`; la UI
  del picker es `genres-catalog-ui`.
- Tokens visuales definitivos (paleta, tipografía, logo) — pendientes del equipo
  de UI/UX; `DESIGN.md` tiene una dirección provisional.
- El **STT**, la **cámara** y el **audio del dictado como Adjunto** ya se
  resolvieron en features previas (ver `docs/technical-discovery.md`). La
  **reproducción** del audio queda diferida.
- ¿El umbral de similitud de `semantic-linking` aplica también al RAG? Hoy el
  retrieval del RAG es top-K **sin umbral**.
- Comportamiento al cambiar el `track` de una Nota ya indexada.

## Resolved / Tooling Limitations
- **Migración de esquema sin precedentes:** v1→v2 (`1.sqm` + `embedding`), v4→v5
  (`4.sqm` + `note_link`) y v5→v6 (`5.sqm` + `note.index_attempts`) quedaron
  cubiertas por tests de migración en Android/JVM e iOS. Sin bloqueos.
- **`FileProvider` + cámara Android:** resuelto con un intent
  `ACTION_IMAGE_CAPTURE` explícito con flags de grant de URI + `clipData`.
- **Cámara iOS fuera del harness:** el simulador no expone cámara; se mapea a
  `CAMERA_UNAVAILABLE` y no se fabrica evidencia. Relevancia menor desde el
  congelamiento de iOS.
- **Smoke interactivo iOS:** sin input automation (`idb`/`cliclick` ausentes;
  `osascript` bloqueado). Relevancia menor desde el congelamiento de iOS.

## Later / Not MVP
- **Múltiples Proyectos** (varios juegos) y **Banco de ideas**.
- **Descongelar iOS** (retomar paridad y verificación).
- Multi-usuario, cuentas, sync y backend.
- Fase B: editor de niveles, generación desde prompt, dificultad, export
  Tiled/Godot/Unity/LÖVE.
- Desktop y Web.

## Assumptions
- **Android-first** en el MVP; un único Proyecto (singleton) por instalación.
- Un único usuario por instalación.
- La captura nunca debe bloquearse por la IA.
- SQLDelight + similitud en memoria alcanza sin una vector DB dedicada.
- Los criterios de éxito son de aprendizaje, no de escala.
- La IA **sugiere** `track` y Géneros y el usuario **confirma o edita**
  (`ai-suggest-classification`); la auto-asignación y la clasificación automática
  al indexar no son requisitos del MVP.

## Risks
- **IA real (red/costo/clave):** la integración con el proveedor real depende de
  red, cuota y una API key (build-time, nunca versionada). Mitigación: `fake` por
  defecto y en tests/gate; tolerancia a fallos (indexado `PENDING`/reintento); key
  fuera del repo; el gate no usa red.
- **Congelar iOS** puede generar drift/deuda si se retoma: nuevos `expect` sin
  `actual` iOS completo. Mitigación: se provee un `actual` mínimo y se conserva el
  build; el ADR define los disparadores de re-evaluación.
- **Alcance amplio del MVP ampliado** (principal + texto/voz 30s + bocetos +
  Géneros + Proyecto/Brainstorming + GDD híbrido + Q&A): sin respetar la secuencia,
  riesgo de terminar varias cosas a medias.
- **Catálogo de Géneros rígido:** si falta un género frecuente, el usuario recurre a
  personalizados que no se reutilizan; podría necesitar promoción al catálogo (no
  MVP).
- **Sugerencias de Brainstorming:** **resueltas** en
  `brainstorming-ai-suggestions` (2026-10-19) con IA fake determinista, parsing
  de 2 ideas, dedupe y UI efímera (spec
  `docs/specs/brainstorming-ai-suggestions.md`). La **calidad real** y el formato
  del proveedor no se verifican hasta `ai-real-chat` (ADR 0004); el fake no
  valida calidad semántica.
- **Calidad real de `jev` pendiente de smoke key-gated:** la sugerencia de
  clasificación ya existe con confirmación explícita y fake determinista
  (`ai-suggest-classification`, 2026-10-20), pero la semántica del modelo real
  (aciertos de `Track`/Género, formato/`none`) no se verifica en el gate. La UI
  facilita confirmar/descartar y corregir `track` y Géneros; la prueba real queda
  como follow-up fuera del gate (sin red ni API key en tests).
- **Verificación del STT no determinista** (audio real puede requerir dispositivo
  físico) y **doble cliente de micrófono en Android** (`MediaRecorder` +
  `SpeechRecognizer`): mitigación por degradación tolerante; persistente desde
  `voice-attachment-storage`.

## Research Tasks
- **Hecho (2026-10-09):** spike cloud vs on-device detrás de `AiClient`
  (`docs/spikes/ai-runtime-spike.md`; ADR 0001).
- **Pendiente:** verificar latencia/calidad/costo cloud reales con la sonda
  key-gated del ADR (fuera del gate) al elegir proveedor/modelo.
- Evaluar `sqlite-vec` u otra extensión vectorial si el volumen de notas crece y la
  similitud en memoria deja de alcanzar.
