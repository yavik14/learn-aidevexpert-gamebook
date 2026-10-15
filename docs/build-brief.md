# Build Brief

## Problem
Las ideas de diseño de juegos (mecánicas, niveles, narrativa, personajes, arte)
se capturan en múltiples lugares y quedan dispersas, sin enlaces entre sí y sin
una forma ágil de consultarlas. Mantener un GDD al día es manual y costoso, y
no hay un documento que concentre la visión del juego mientras se itera.

## Current Workaround / Existing System
Mezcla desordenada de varias herramientas (notas sueltas, chats con IA,
papel/bocetos, documentos). Nada se enlaza automáticamente ni se resume solo, y
no hay búsqueda semántica sobre lo ya capturado.

## Target Users
- **Primario:** el autor (game dev aficionado). Único usuario del MVP.
- **Secundario (post-MVP):** otras personas que diseñan juegos y toman notas
  dispersas.

## Goals
- Capturar ideas rápido en texto, voz e imagen (bocetos).
- Construir un **GDD vivo y navegable**: el documento principal de un Proyecto de
  Juego, con una ficha estructurada y secciones alimentadas por Notas.
- Arrancar/iterar el juego con un **flujo de Brainstorming** guiado (con
  sugerencias de IA opcionales).
- Organizar cada Nota en uno de tres tracks de diseño (`mecánicas`, `personajes`,
  `historia`, elección manual) y clasificarla con **Géneros** (catálogo fijo +
  personalizados).
- Enlazar notas por similitud semántica.
- Consultar en lenguaje natural sobre las notas/GDD propias (Q&A) y poder guardar
  una respuesta como Nota.
- Aprender: IA integrada en un flujo real, KMP compartido de verdad, al menos
  una integración nativa, y base escalable a la Fase B sin refactor mayor.

## Non-Goals
- **iOS en el MVP:** queda **congelado** (Android-first). Ver
  `docs/adr/0003-ios-congelado-mvp.md`. No se exige paridad ni verificación iOS.
- Multi-usuario, cuentas, autenticación o colaboración (MVP single-user
  local-first).
- **Múltiples Proyectos** y **Banco de ideas** (post-MVP).
- Clasificación/tipificación automática por IA (categoría, tipo, o Género
  inferido de la imagen) — descartada del MVP; el usuario elige el track y los
  Géneros.
- Editor de niveles 2D, generación automática de niveles, evaluación de
  dificultad y export a Godot/Unity/LÖVE (Fase B).
- Desktop y Web.
- Runtime de IA on-device como requisito del MVP (decisión abierta detrás de
  `AiClient`).

## MVP Slice
Alcance del concepto, construido como **secuencia explícita de features** (no
todo en paralelo). Orden preliminar (a re-derivar en la fase harness):

1. **Pantalla principal (Playbook):** TopAppBar "Playbook" con iconos de GDD,
   Brainstorming y Preguntar; FAB desplegable (Nota de texto, Galería, Cámara).
2. **Nota de texto + dictado inline:** CRUD de texto; dictado integrado con cap
   de 30s (progreso + auto-stop) que transcribe al cuerpo y conserva el Adjunto
   de audio.
3. **Bocetos:** foto/galería → Nota con imagen como contenido principal,
   clasificada manualmente (Track + Géneros) y **visible como miniatura** en la
   lista/GDD.
4. **Géneros:** reemplazo de etiquetas libres por Géneros (catálogo fijo +
   personalizados, múltiples), con migración de datos existentes.
5. **Proyecto + Brainstorming:** entidad Proyecto (ficha estructurada) creada en
   el primer arranque (oferta saltable del wizard de 9 pasos, con sugerencias IA).
6. **GDD híbrido:** documento que combina la ficha del Proyecto (Proyecto,
   Historia/Lore, Personajes) y las Notas agrupadas por Track, con export.
7. **Q&A (Preguntar):** consulta en lenguaje natural sobre las Notas/GDD con
   citas y "guardar como Nota".

El detalle granular de features y su criterio de done se deriva en la fase
harness, no en este brief.

## Validation Plan
- Flujo end-to-end: primer arranque → Brainstorming (crear Proyecto) o saltar →
  crear Notas (texto/voz/boceto) eligiendo Track y Géneros → indexada →
  enlazada y agrupada en el GDD → consulta en lenguaje natural respondida con
  notas reales → guardar una respuesta como Nota.
- Tests del core KMP con un `AiClient` fake (deterministas, sin red).
- Prueba manual en Android (emulador/dispositivo). iOS queda fuera del gate.

## Success Criteria
- IA funcional en un flujo real (sugerencias de Brainstorming y Q&A), no un
  chatbot decorativo.
- Código compartido real en KMP (sin lógica duplicada por plataforma).
- Integraciones nativas resueltas correctamente (STT, cámara, audio).
- El GDD se siente un documento del juego, no una lista de notas.
- Base de código extensible a la Fase B sin refactor mayor.
- Distribución Android (release) operativa.

## Notes
- Idioma de los documentos: español, con términos técnicos en inglés.
- `Owner` se modela aunque el MVP sea single-user.
- El foco es aprender; el alcance puede recortarse si amenaza el aprendizaje.
- 2026-10-05: alcance revisado. Se introducen los **tracks**
  (`mecánicas`/`personajes`/`historia`) y se descarta la clasificación automática
  por IA; se mantienen embeddings, enlaces y RAG.
- **2026-10-15: alcance revisado (Android-first).** Se **congela iOS** (ver
  `docs/adr/0003-ios-congelado-mvp.md`); las **Etiquetas libres** se reemplazan
  por **Géneros** (catálogo fijo + personalizados); el **Proyecto de Juego**
  (singleton) y su **Brainstorming** pasan a ser el eje del GDD (documento
  híbrido: ficha + Notas); se suman pantalla principal rediseñada, dictado 30s,
  bocetos con miniatura y "guardar respuesta como Nota". Múltiples Proyectos y
  Banco de ideas quedan post-MVP. `feature_list.json` debe re-derivarse antes de
  la próxima implementación.
