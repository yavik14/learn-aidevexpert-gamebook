# Domain Model

## Core Concepts
- **Nota:** entidad central. Cuerpo de texto, `owner`, `categoría`, `etiquetas`,
  `tipo`, `estado` y `adjuntos`.
- **Adjunto:** archivo original asociado a una Nota (audio de voz o imagen de
  boceto). Su texto derivado (transcripción / OCR) vive en la Nota, no en el
  Adjunto.
- **Categoría:** enum fijo y único (`mecánica`, `nivel`, `narrativa`, `arte`,
  `otro`).
- **Etiqueta:** vocabulario libre y múltiple, sugerido por IA y editable.
- **Tipo de nota:** enum inferido por IA (`idea`, `pregunta`, `referencia`,
  `decisión`, `tarea`), corregible por el usuario.
- **Enlace / Relación:** conexión Nota↔Nota por similitud semántica, con un
  score.
- **GDD:** vista agregada sobre las Notas (no es una entidad persistida aparte).
- **Consulta:** pregunta en lenguaje natural resuelta sobre las Notas y sus
  embeddings.
- **Owner:** dueño de las Notas. Único en el MVP.

## Relationships
- `Owner` 1—N `Nota`.
- `Nota` 1—N `Adjunto`.
- `Nota` N—N `Etiqueta`.
- `Nota` 1—1 `Categoría` (enum).
- `Nota` 1—1 `Tipo de nota` (enum inferido).
- `Nota` N—N `Nota` mediante `Enlace` (con `score`).
- `GDD` proyecta `Notas`; no tiene tabla propia en el MVP.

## States and Lifecycles
**Nota:**
- `capturada` → `enriquecida`: el enriquecimiento IA termina con éxito.
- `capturada` → `pendiente`: la IA no está disponible (offline / falla
  transitoria); se reintenta.
- `pendiente` → `enriquecida` | `fallida`.
- Una corrección manual del usuario no revierte el estado a `capturada`.

**Adjunto:**
- `subido` → `procesado` (transcripción / OCR) | `fallido`.

**Enlace:**
- `generado` → `recalculado` cuando la Nota se re-enriquece.

## Important Scenarios
- Capturar una idea de mecánica por texto → la IA infiere `tipo=idea`,
  `categoría=mecánica`, etiquetas y enlaces a notas cercanas.
- Grabar una nota de voz sin conexión → queda `pendiente` → al recuperar
  conexión se enriquece.
- Consultar "¿qué mecánicas todavía no tienen nivel asignado?" → respuesta desde
  las Notas y sus embeddings, no desde conocimiento externo.
- Corregir una categoría sugerida → el cambio se refleja en el GDD y no se
  pisa en el próximo enriquecimiento.

## Edge Cases
- Nota cuyo enriquecimiento falló: debe seguir visible en el GDD y ser
  consultable.
- Adjunto sin texto (audio no transcripto / imagen sin OCR): ¿es indexable para
  RAG? (pregunta abierta).
- Notas casi idénticas: generan enlaces de score alto.
- Re-enriquecer no debe perder correcciones manuales del usuario.
- Distinguir la `Categoría` "nivel" de Etiquetas que mencionan niveles.
