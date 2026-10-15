# Context

Glosario y lenguaje de dominio del proyecto. Solo vocabulario compartido:
no es un PRD, ni un plan, ni un log de decisiones.

## Glossary

### Playbook
Aplicación de notas de diseño de juegos (nombre tentativo) que construye un
GDD vivo a partir de capturas del usuario.

### Proyecto de Juego (Game Project)
El juego que se está diseñando. Agrupa un GDD, sus Notas y su ficha. En el MVP
hay un **único** Proyecto (singleton) por instalación; se crea en el primer
arranque (opcionalmente con el flujo de Brainstorming). Múltiples Proyectos y el
**Banco de ideas** son post-MVP.

### GDD (Game Design Document)
Documento principal de un Proyecto. Es **híbrido**: una **ficha estructurada**
(Proyecto/Brainstorming) más secciones construidas con **Notas** agrupadas por
`Track` (incluye Historia/Lore y Personajes). No es una tabla única; combina
campos del Proyecto y la proyección de Notas.

### Nota
Unidad mínima de captura creada por el usuario. Tiene `owner`, cuerpo, `track`,
`géneros`, `estado` y `adjuntos`. Es la entidad central del dominio.

### Captura
Insumo con el que se crea una Nota: texto, voz (speech-to-text) o imagen
(foto de boceto). Puede generar uno o más Adjuntos.

### Adjunto (Attachment)
Archivo asociado a una Nota: audio original de la voz o imagen del boceto.
Se distingue del texto derivado (transcripción / OCR).

### Track
Pilar de diseño al que pertenece una Nota. Clasificación única y fija elegida
por el usuario. Valores: `mecánicas`, `personajes`, `historia`. Es la
estructura principal del GDD (reemplaza a la antigua `Categoría`).

### Género (Genre)
Clasificación descriptiva y múltiple de una Nota: el género o géneros de juego a
los que se refiere. Se elige de un **catálogo fijo** (Platformer, Metroidvania,
JRPG, ActionRPG, Roguelike, Shooter, Puzzle, Adventure, Towerdefense, Novel,
Farming, Arcade) o se escribe uno **personalizado** si no está en el catálogo. El
catálogo es una constante de la app; lo personalizado se guarda por Nota. Reemplaza
a la antigua `Etiqueta`.

### Brainstorming
Flujo guiado de 9 pasos para crear o iterar el Proyecto de Juego (Título, Género,
Core Loop, Audiencia, Concepto, Inspiraciones, Fantasía, Mecánicas clave, Fuera de
alcance). La IA puede sugerir 2 ideas por paso o el usuario aporta las suyas. Es
una superficie distinta del Q&A.

### Mecánica clave (Key mechanic)
Una de las 5 mecánicas centrales del Proyecto (paso "Keys" del Brainstorming). Cada
una tiene una **categoría** (`Movimiento`/`Interacción`/`Conflicto`) y una
descripción corta.

### Core Loop
Descripción concisa (1–2 frases) de la acción central que el jugador repetirá
durante todo el juego. Campo del Proyecto.

### Audiencia
A quién va dirigido el juego. Campo del Proyecto.

### Inspiración
Juego de referencia (base o influencia) del Proyecto. Hasta 3.

### Fantasía
La fantasía de jugador: qué experiencia/poder imagina vivir el jugador. Campo del
Proyecto.

### Fuera de alcance (Out)
Elementos o características que el juego **no** incluirá. Hasta 3. Campo del
Proyecto.

### Banco de ideas (Idea bank)
Reserva de ideas/proyectos que no se están desarrollando. **Post-MVP**; no se
modela todavía.

### Enlace / Relación
Conexión entre dos Notas por similitud semántica (embeddings).

### Consulta (Q&A)
Pregunta en lenguaje natural respondida a partir de las Notas propias del usuario
y sus embeddings. Superficie "Preguntar", distinta del flujo de Brainstorming.

### Indexado (IA)
Proceso asíncrono que genera el embedding de una Nota y, con él, sus Enlaces.
No clasifica ni etiqueta: eso lo decide el usuario.

### Proveedor de IA
Servicio de inferencia del MVP: **propio, compatible con la API de OpenAI**
(`https://inference.devexpert.io/v1`), con modelos **chat, embedding, transcribe,
tts, image y jev**. Se accede detrás de puertos de IA; la **API key** no se versiona.

### Entorno fake / real (IA)
Modo de ejecución de la IA elegido por **build flag**: `fake` (determinista, sin
red; default y siempre en tests/gate) o `real` (usa el proveedor y la API key).

### Transcripción de audio
Uso del modelo `transcribe`: convertir el Adjunto `AUDIO` de una Nota en texto
(que pasa al `body` de la Nota). Es un texto derivado, distinto del Adjunto.

### Lectura en voz alta (TTS)
Uso del modelo `tts`: reproducir por audio un texto (respuestas del Q&A, el cuerpo
de una Nota o la narración del GDD).

### Generación de imagen
Uso del modelo `image`: crear una imagen a partir de un texto y adjuntarla a una
Nota como Adjunto.

### Sugerencia de clasificación (IA)
Uso del modelo `jev`: proponer `Track`/`Género` a partir de texto. La IA **sugiere**;
el usuario **confirma o edita**. Nunca se auto-asigna (non-goal revisado 2026-10-15).

### Owner
Identificador del dueño de una Nota. En el MVP es único (single-user), pero se
modela para no cerrar la puerta a multi-usuario.

## Rejected / Ambiguous Terms

### Categoría / Tipo de nota
Descartados del MVP. La Nota se clasifica con un único `Track` fijo
(`mecánicas`/`personajes`/`historia`) elegido por el usuario; no hay `Categoría`
ni `Tipo`. La IA puede **sugerir** `Track`/`Género` (con confirmación del usuario),
pero no existe clasificación automática `Categoría`/`Tipo`.

### Etiqueta (Tag)
**Sustituida por `Género`.** La Nota ya no tiene vocabulario libre genérico; su
clasificación descriptiva múltiple es el `Género` (catálogo + personalizados).
Las etiquetas libres existentes se migran a Géneros personalizados.

### Track vs Género
No son sinónimos. `Track` es el enum fijo (uno por Nota) que estructura el GDD;
`Género` es la clasificación múltiple del catálogo + personalizados.

### Brainstorming vs Preguntar (Q&A)
No son sinónimos. `Brainstorming` es el flujo guiado de 9 pasos del Proyecto;
`Preguntar` es el Q&A sobre Notas/GDD (RAG). Son dos superficies distintas.

### Nivel
Ambiguo. No es un `Track`. Reservar `Level` (tilemap) para la Fase B (Level
Design Lab); para una nota sobre el diseño de un nivel, usar el track
`mecánicas` o `historia` según corresponda.

### Contenido
Término vago. Usar `Nota` o `Adjunto` según corresponda.
