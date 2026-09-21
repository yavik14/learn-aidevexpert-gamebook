# Context

Glosario y lenguaje de dominio del proyecto. Solo vocabulario compartido:
no es un PRD, ni un plan, ni un log de decisiones.

## Glossary

### Playbook
Aplicación de notas de diseño de juegos (nombre tentativo) que construye un
GDD vivo a partir de capturas del usuario.

### Nota
Unidad mínima de captura creada por el usuario. Tiene `owner`, cuerpo, categoría,
etiquetas, tipo inferido y adjuntos. Es la entidad central del dominio.

### Captura
Insumo con el que se crea una Nota: texto, voz (speech-to-text) o imagen
(foto de boceto). Puede generar uno o más Adjuntos.

### Adjunto (Attachment)
Archivo asociado a una Nota: audio original de la voz o imagen del boceto.
Se distingue del texto derivado (transcripción / OCR).

### Categoría
Clasificación única y fija de una Nota. Valores: `mecánica`, `nivel`,
`narrativa`, `arte`, `otro`.

### Etiqueta (Tag)
Descriptor libre y múltiple de una Nota, sugerido por la IA y editable por el
usuario.

### Tipo de nota
Clasificación inferida por IA del rol de la Nota. Valores fijos: `idea`,
`pregunta`, `referencia`, `decisión`, `tarea`. Corregible por el usuario.
Es un concepto distinto de Categoría.

### GDD (Game Design Document)
Vista agregada y navegable que agrupa y resume las Notas del usuario.

### Enlace / Relación
Conexión entre dos Notas por similitud semántica (embeddings).

### Consulta (RAG)
Pregunta en lenguaje natural respondida a partir de las Notas propias del
usuario y sus embeddings.

### Enriquecimiento (IA)
Proceso asíncrono que asigna Categoría, Etiquetas, Tipo de nota y Enlaces a una
Nota.

### Owner
Identificador del dueño de una Nota. En el MVP es único (single-user), pero se
modela para no cerrar la puerta a multi-usuario.

## Rejected / Ambiguous Terms

### Categoría vs Etiqueta
No son sinónimos. Usar `Categoría` para el enum fijo y único, y `Etiqueta` para
el vocabulario libre y múltiple.

### Nivel
Ambiguo. Usar "Nivel" solo como valor de `Categoría` (diseño de un nivel).
Reservar `Level` (tilemap) para la Fase B (Level Design Lab).

### Contenido
Término vago. Usar `Nota` o `Adjunto` según corresponda.
