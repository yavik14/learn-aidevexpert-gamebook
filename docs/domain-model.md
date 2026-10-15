# Domain Model

## Core Concepts
- **Proyecto de Juego:** el juego que se diseña. Entidad central del MVP, **única
  por instalación** (singleton). Su **ficha** es estructurada: título, Géneros,
  Core Loop, Audiencia, Concepto, Inspiraciones (≤3), Fantasía, 5 Mecánicas clave y
  Fuera de alcance (≤3). Se crea en el primer arranque (opcionalmente vía el flujo
  de Brainstorming).
- **Nota:** unidad de captura. Cuerpo de texto, `owner`, `track`, `géneros`,
  `estado`, `embedding` (opcional) y `adjuntos`.
- **Adjunto:** archivo original asociado a una Nota (audio de voz o imagen de
  boceto). Su texto derivado (transcripción / OCR) vive en la Nota, no en el
  Adjunto.
- **Track:** enum fijo y único elegido por el usuario (`mecánicas`,
  `personajes`, `historia`). Estructura el GDD. Reemplaza a la antigua
  `Categoría`.
- **Género:** clasificación descriptiva **múltiple** de una Nota. Se elige del
  **catálogo fijo** (Platformer, Metroidvania, JRPG, ActionRPG, Roguelike,
  Shooter, Puzzle, Adventure, Towerdefense, Novel, Farming, Arcade) o se escribe
  uno **personalizado** por Nota. Reemplaza a la antigua `Etiqueta`.
- **Mecánica clave:** una de las 5 mecánicas del Proyecto; cada una con
  **categoría** (`Movimiento`/`Interacción`/`Conflicto`) y descripción corta.
- **Enlace / Relación:** conexión Nota↔Nota por similitud semántica, con un
  score.
- **GDD (Game Design Document):** **documento híbrido** del Proyecto. Combina la
  **ficha estructurada** del Proyecto y secciones construidas con **Notas**
  agrupadas por `Track` (incluye Historia/Lore y Personajes). No es una entidad
  persistida aparte; es una vista sobre la ficha + las Notas.
- **Consulta (Q&A):** pregunta en lenguaje natural resuelta sobre las Notas y sus
  embeddings.
- **Indexado:** proceso que genera el embedding de una Nota y sus Enlaces.
- **Owner:** dueño de las Notas/Proyecto. Único en el MVP.

## Relationships
- `Owner` 1—1 `Proyecto` (MVP; post-MVP 1—N).
- `Proyecto` 1—N `Nota` (MVP: todas las Notas pertenecen al único Proyecto).
- `Proyecto` 1—1 `GDD` (ficha + secciones).
- `Nota` 1—N `Adjunto`.
- `Nota` N—N `Género` (catálogo fijo + personalizados).
- `Nota` 1—1 `Track` (enum, elegido por el usuario).
- `Nota` N—N `Nota` mediante `Enlace` (con `score`).
- `GDD` proyecta `Notas` por `Track` y muestra la ficha del `Proyecto`; no tiene
  tabla propia más allá de los campos del `Proyecto`.

## Secciones del GDD
- **Proyecto / Brainstorming:** ficha estructurada (Título, Género, Core Loop,
  Audiencia, Concepto, Inspiraciones, Fantasía, Mecánicas clave, Fuera de
  alcance).
- **Historia / Lore:** Premisa/Sinopsis, Estructura narrativa, Objetivo principal
  (contenido estructurado y/o Notas del track `historia`).
- **Personajes:** Protagonista, Aliados/NPCs, Antagonistas/Jefes (Notas del track
  `personajes`).
- **Mecánicas / Notas:** Notas agrupadas por `Track`.

## Capacidades de IA
- **Generación (chat):** sugerencias del Brainstorming y respuestas del Q&A.
- **Embeddings:** indexado de la Nota (y los Enlaces/RAG que dependen de él).
- **Transcripción (transcribe):** transcribe el Adjunto `AUDIO` → texto al `body`
  (texto derivado); dispara re-indexado.
- **Lectura en voz alta (tts):** reproduce texto (Q&A/Nota/GDD); no crea entidades.
- **Generación de imagen (image):** crea una imagen desde texto y la persiste como
  `Adjunto` de una Nota.
- **Sugerencia de clasificación (jev):** propone `Track`/`Género`; el usuario
  confirma o edita (**nunca** auto-asigna).

Todas operan detrás de puertos de IA; en tests/gate, con adaptador **fake**
(determinista, sin red). El proveedor del MVP es propio (OpenAI-compatible).

## States and Lifecycles
**Nota:**
- `capturada` → `indexada`: el embedding se generó con éxito.
- `capturada` → `pendiente`: la IA no está disponible (offline / falla
  transitoria); se reintenta.
- `pendiente` → `indexada` | `fallida`.
- Editar cuerpo, track o géneros no revierte el estado a `capturada`; puede
  disparar re-indexado.

**Proyecto:**
- `vacío` → `esbozado`: se completó (parte de) el Brainstorming.
- Se itera reabriendo el Brainstorming; no hay estados terminales.

**Adjunto:**
- `subido` → `procesado` (transcripción / OCR) | `fallido`.

**Enlace:**
- `generado` → `recalculado` cuando la Nota se re-indexa.

## Important Scenarios
- Primer arranque sin Proyecto → se ofrece el Brainstorming → se crea el
  Proyecto (ficha) con su Título, Género, Core Loop, etc., y Notas iniciales por
  paso.
- Capturar un boceto (cámara/galería) → el usuario le elige Track y Géneros
  (manual) → aparece en el GDD con su **miniatura**.
- Capturar una idea de mecánica por texto → `track=mecánicas` + Géneros; el
  indexado genera el embedding y los enlaces a notas cercanas.
- Iterar un Proyecto: reabrir el Brainstorming y ajustar campos o agregar Notas.
- Consultar "¿qué mecánicas todavía no tienen personaje relacionado?" → respuesta
  desde las Notas y sus embeddings, no desde conocimiento externo; "guardar como
  Nota" crea una Nota pre-clasificada por contexto.

## Edge Cases
- Nota cuyo indexado falló: sigue visible en el GDD y consultable, pero sin
  embedding no participa del RAG ni genera Enlaces.
- Adjunto sin texto (audio no transcripto / imagen sin OCR): ¿es indexable para
  RAG? (pregunta abierta).
- Notas casi idénticas: generan enlaces de score alto.
- Re-indexar no debe perder el `track` ni los Géneros.
- Una Nota sin `track` (por crear o migración) no debe romper la vista GDD.
- Migración de Etiquetas libres existentes → Géneros personalizados (se
  conservan).
- Género escrito a mano que coincide con el catálogo: se trata como de catálogo
  (normalización case-insensitive).
- **Transcripción no disponible/falla:** la Nota queda sin texto derivado, sin
  bloquear la captura.
- **Imagen generada:** se persiste como `Adjunto` (igual que un boceto) y es
  visible con su miniatura.
- **Sugerencia de clasificación descartada:** si el usuario no confirma, no se
  persiste ningún `Track`/`Género`.
