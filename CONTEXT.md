# Context

Glosario y lenguaje de dominio del proyecto. Solo vocabulario compartido:
no es un PRD, ni un plan, ni un log de decisiones.

## Glossary

### Playbook
Aplicación de notas de diseño de juegos (nombre tentativo) que construye un
GDD vivo a partir de capturas del usuario.

### Nota
Unidad mínima de captura creada por el usuario. Tiene `owner`, cuerpo, `track`,
`etiquetas`, `estado` y `adjuntos`. Es la entidad central del dominio.

### Captura
Insumo con el que se crea una Nota: texto, voz (speech-to-text) o imagen
(foto de boceto). Puede generar uno o más Adjuntos.

### Adjunto (Attachment)
Archivo asociado a una Nota: audio original de la voz o imagen del boceto.
Se distingue del texto derivado (transcripción / OCR).

### Track
Pilar de diseño al que pertenece una Nota. Clasificación única y fija elegida
por el usuario. Valores: `mecánicas`, `personajes`, `historia`. Es la
estructura principal del producto (reemplaza a la antigua `Categoría`).

### Etiqueta (Tag)
Descriptor libre y múltiple de una Nota, creado y editado manualmente por el
usuario.

### GDD (Game Design Document)
Vista agregada y navegable que agrupa y resume las Notas del usuario por track.

### Enlace / Relación
Conexión entre dos Notas por similitud semántica (embeddings).

### Consulta (RAG)
Pregunta en lenguaje natural respondida a partir de las Notas propias del
usuario y sus embeddings.

### Indexado (IA)
Proceso asíncrono que genera el embedding de una Nota y, con él, sus Enlaces.
No clasifica ni etiqueta: eso lo decide el usuario.

### Owner
Identificador del dueño de una Nota. En el MVP es único (single-user), pero se
modela para no cerrar la puerta a multi-usuario.

## Rejected / Ambiguous Terms

### Categoría / Tipo de nota
Descartados del MVP. La Nota se clasifica con un único `Track` fijo
(`mecánicas`/`personajes`/`historia`) elegido por el usuario; no hay `Categoría`
ni `Tipo` inferidos por IA.

### Track vs Etiqueta
No son sinónimos. Usar `Track` para el enum fijo y único, y `Etiqueta` para el
vocabulario libre y múltiple.

### Nivel
Ambiguo. No es un `Track`. Reservar `Level` (tilemap) para la Fase B (Level
Design Lab); para una nota sobre el diseño de un nivel, usar el track
`mecánicas` o `historia` según corresponda.

### Contenido
Término vago. Usar `Nota` o `Adjunto` según corresponda.
