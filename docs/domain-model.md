# Domain Model

## Core Concepts
- **Nota:** entidad central. Cuerpo de texto, `owner`, `track`, `etiquetas`,
  `estado`, `embedding` (opcional) y `adjuntos`.
- **Adjunto:** archivo original asociado a una Nota (audio de voz o imagen de
  boceto). Su texto derivado (transcripción / OCR) vive en la Nota, no en el
  Adjunto.
- **Track:** enum fijo y único elegido por el usuario (`mecánicas`,
  `personajes`, `historia`). Reemplaza a la antigua `Categoría`.
- **Etiqueta:** vocabulario libre y múltiple, creado y editado manualmente.
- **Enlace / Relación:** conexión Nota↔Nota por similitud semántica, con un
  score.
- **GDD:** vista agregada sobre las Notas, agrupada por track (no es una entidad
  persistida aparte).
- **Consulta:** pregunta en lenguaje natural resuelta sobre las Notas y sus
  embeddings.
- **Indexado:** proceso que genera el embedding de una Nota y sus Enlaces.
- **Owner:** dueño de las Notas. Único en el MVP.

## Relationships
- `Owner` 1—N `Nota`.
- `Nota` 1—N `Adjunto`.
- `Nota` N—N `Etiqueta`.
- `Nota` 1—1 `Track` (enum, elegido por el usuario).
- `Nota` N—N `Nota` mediante `Enlace` (con `score`).
- `GDD` proyecta `Notas`; no tiene tabla propia en el MVP.

## States and Lifecycles
**Nota:**
- `capturada` → `indexada`: el embedding se generó con éxito.
- `capturada` → `pendiente`: la IA no está disponible (offline / falla
  transitoria); se reintenta.
- `pendiente` → `indexada` | `fallida`.
- Editar cuerpo, track o etiquetas no revierte el estado a `capturada`; puede
  disparar re-indexado.

**Adjunto:**
- `subido` → `procesado` (transcripción / OCR) | `fallido`.

**Enlace:**
- `generado` → `recalculado` cuando la Nota se re-indexa.

## Important Scenarios
- Capturar una idea de mecánica por texto → el usuario elige `track=mecánicas`
  y agrega etiquetas; el indexado genera el embedding y los enlaces a notas
  cercanas.
- Grabar una nota de voz sin conexión → queda `pendiente` → al recuperar
  conexión se indexa.
- Consultar "¿qué mecánicas todavía no tienen personaje relacionado?" →
  respuesta desde las Notas y sus embeddings, no desde conocimiento externo.
- Cambiar el track de una nota → el cambio se refleja en el GDD; el embedding no
  cambia salvo que se edite el cuerpo.

## Edge Cases
- Nota cuyo indexado falló: sigue visible en el GDD y consultable, pero sin
  embedding no participa del RAG ni genera Enlaces.
- Adjunto sin texto (audio no transcripto / imagen sin OCR): ¿es indexable para
  RAG? (pregunta abierta).
- Notas casi idénticas: generan enlaces de score alto.
- Re-indexar no debe perder el `track` ni las etiquetas manuales.
- Una Nota sin `track` (por crear o migración) no debe romper la vista GDD.
