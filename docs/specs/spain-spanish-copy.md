# Feature Implementation Spec: Copy en español de España y voz de marca Playbook

## Source Feature

- `id`: `spain-spanish-copy`
- `area`: `ui`
- `depends_on`: `main-screen-playbook-shell`, `gdd-document-hybrid`, `ai-real-chat`
  (las tres `accepted`)
- `status`: `not_started` (al planificar)
- `source`: `feature_list.json`
- WIP=1: una feature por sesión/worktree; no se reabren decisiones de producto ni
  se amplía el alcance.

> **Alcance.** Migrar el copy visible de producto a español de España (tuteo, sin
> voseo ni regionalismos rioplatenses), usar “Añadir” en lugar de “Agregar” y
> reemplazar “La IA”/“Generar con IA” por “Playbook”/“Generar con Playbook”. El
> gate y los tests siguen usando IA fake.

## Goal

Todo el copy visible de Playbook (pantallas, diálogos, avisos, etiquetas de
accesibilidad y prompts de IA) usa español de España y la voz de marca
**Playbook**: tuteo, “Añadir”, y “Playbook” como sujeto de las acciones de IA en
lugar de “La IA”. La app no debe mostrar “Agregar”, “Generar con IA”, “La IA” ni
formas de voseo (`Probá`, `Escribí`, `Elegí`, `Podés`, `Guardá`, `Intentá`,
`Revisá`, `Reintentá`, `Habilitalo`, `Completalo`, `Describí`, `volvé`, `acá`).

## Non-Goals

- i18n multidioma, recursos `strings.xml`, selector de idioma ni traducciones.
- Coma decimal, fechas `dd/MM/yyyy` ni formato regional de números/fechas.
- Cambiar el idioma de dictado/reconocimiento (`es-ES`) ni la configuración de voz.
- Traducir términos de dominio (`Track`, `GDD`, `Core Loop`, `Genre`/`Género` como
  se usa hoy, categorías de mecánica) ni renombrar entidades.
- Cambiar lógica de prompts, marcadores, temperatura, tokens o el contrato de los
  servicios de IA.
- Reescribir evidencia histórica (`docs/validations/`, entradas antiguas de
  `PROGRESS.md`/`feature_list.json`, specs previas).

## Job Story / Users

When uso Playbook en España, I want que todo el texto de la app y las respuestas
sugeridas suenen naturales en castellano y con una voz de marca coherente, so I
can usar la app sin encontrar voseo, “Agregar” ni referencias a “La IA”.

## Scope Decisions

1. **Tuteo:** se reemplaza el voseo por formas de tú (`prueba`, `escribe`,
   `elige`, `puedes`, `guarda`, `inténtalo`, `revisa`, `reintenta`, `habilítalo`,
   `complétalo`, `describe`, `vuelve`). “Aquí” reemplaza a “acá”.
2. **“Añadir”:** todos los botones/etiquetas de acción usan “Añadir” (incluidas
   las etiquetas de accesibilidad “Añadir género X”). No se usa “Agregar” en copy
   visible.
3. **Voz de marca Playbook:** “La IA” se reemplaza por “Playbook” cuando la app
   habla de sus acciones (“Playbook está pensando ideas…”, “Playbook no propuso
   una clasificación.”, “Sugerencia de Playbook”). “Generar con IA” pasa a
   “Generar con Playbook” y “Generar imagen con IA” a “Generar imagen con
   Playbook”. “Runtime de IA” en copy visible pasa a “conexión”.
4. **Prompts:** los prompts internos también usan tuteo y declaran español de
   España, sin voseo; la lógica (marcadores, contexto, formato, temperatura,
   tokens) no cambia.
5. **Cambios de soporte incluidos:** `INTERNET` en el manifest Android,
   `playbook.ai.mode = real` en `gradle.properties` y la marca Playbook en
   `BrainstormingWizardScreen`. **Excepción aceptada por el autor:** el repo queda
   con `real` como modo por defecto; el gate y toda verificación manual deben
   usar `-Pplaybook.ai.mode=fake` (o configurar una key fuera del repo). El build
   por defecto sin flag/key falla a propósito y no forma parte de la verificación
   de esta feature.
6. **Alcance documental:** se actualizan sólo referencias vigentes de
   `DESIGN.md` y `ARCHITECTURE.md`; la evidencia histórica no se reescribe.

## Acceptance Scenarios

1. **Pantalla principal y capturas:** la lista vacía dice “Tus ideas capturadas
   aparecerán aquí.” Los botones/diálogos dicen “Añadir imagen”, “Generar con
   Playbook” y “Generar imagen con Playbook”; no queda “Agregar imagen” ni
   “Generar con IA”.
2. **Géneros:** el campo personalizado dice “Escribe uno nuevo…”, el botón
   “Añadir” y la etiqueta accesible “Añadir género $label”.
3. **Editor de Nota:** placeholder “Escribe tu idea…”, “Elige cómo integrar la
   transcripción:”, “Sugerencia de Playbook” y “Playbook no propuso una
   clasificación.”.
4. **Dictado y transcripción:** todos los errores usan tuteo (“Habilítalo”,
   “reintenta”, “Prueba de nuevo”, “Revisa tu red e inténtalo otra vez”) y el
   aviso dice “Transcripción aplicada. Guarda para conservarla.”.
5. **Brainstorming:** avisos en tuteo, “Playbook está pensando ideas…”, “Playbook
   devolvió una sola idea. Puedes pedir de nuevo.”, botones “Añadir”/“Añadir
   inspiración”/“Añadir fuera de alcance”/“Añadir mecánica” y placeholders
   “Describe…”. No queda voseo ni “La IA”.
6. **Q&A:** “Escribe una pregunta sobre tus notas.”, “Guarda una nota de texto y
   vuelve a preguntar.” y “Playbook no pudo responder. Revisa la conexión e
   inténtalo de nuevo.”.
7. **GDD y Notas relacionadas:** “Complétalo desde Brainstorming.”, “Tus ideas
   capturadas aparecerán aquí, agrupadas por track.” y “Cuando otras Notas se
   parezcan a esta, las verás aquí.”.
8. **Prompts:** `RAG_INSTRUCTIONS` contiene “español de España” y no contiene
   “Respondé”; el prompt de Brainstorming contiene “Eres Playbook” y “español de
   España” y no contiene “Sugerí”; el system prompt de chat contiene “Eres
   Playbook” y “español de España”.
9. **Sin regresiones:** tests, gate, build Android y link iOS pasan con IA fake;
   no se tocan esquema, `.sqm`, dependencias, `init.sh`, adaptadores/runtime
   reales ni `iosApp/**`.
10. **Barrido de copy:** los greps de verificación no encuentran copy visible con
    voseo, “Agregar”, “IA” ni “Generar con IA”; sólo se admiten comentarios
    internos no visibles y términos técnicos justificados.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `DESIGN.md`,
  `ARCHITECTURE.md`.
- Specs aceptadas: `main-screen-playbook-shell`, `gdd-document-hybrid`,
  `ai-real-chat`, `inline-dictation-30s`, `image-generation-from-text`,
  `brainstorming-ai-suggestions`.
- UI: `GenrePicker.kt`, `App.kt`, `RelatedNotesScreen.kt`, `GddScreen.kt`,
  `Dictation.kt`, `NotesListScreen.kt`, `FirstRunOnboardingScreen.kt`,
  `NoteEditorScreen.kt`, `ImageGenerationDialog.kt`,
  `BrainstormingWizardScreen.kt`, `AskNotesScreen.kt`, `AudioTranscription.kt`.
- IA: `BrainstormingSuggestions.kt`, `RagQueryService.kt`,
  `openai/OpenAiAdapters.kt`.
- Tests: `AudioTranscriptionTest.kt`, `RagQueryCheck.kt`,
  `BrainstormingSuggestionCheck.kt`.

### Existing Patterns To Follow

- Copy inline en `commonMain` (sin recursos i18n); helpers puros para mensajes de
  estado (`VoiceCaptureError.message()`, `AudioTranscriptionUiState.message()`).
- Prompts como constantes/funciones en `:core`; los tests verifican contrato y
  marcadores, no el texto completo.
- La UI es “tonta”; los cambios son de texto y no alteran estados ni callbacks.

### Current Gaps

- Copy con voseo y regionalismos rioplatenses en listas, editor, dictado,
  transcripción, wizard, Q&A, GDD y notas relacionadas.
- “Agregar” en botones y etiquetas de accesibilidad de Géneros, imágenes,
  inspiraciones, mecánicas y sugerencias.
- “La IA”, “Generar con IA” y “runtime de IA” en copy de producto.
- Prompts con voseo y sin declaración explícita de español de España.

## Copy Inventory

### UI (`composeApp/src/commonMain/kotlin/com/playbook/app/`)

| Archivo | Antes | Después |
| --- | --- | --- |
| `GenrePicker.kt` | “Escribí uno nuevo…” | “Escribe uno nuevo…” |
| `GenrePicker.kt` | “Agregar” | “Añadir” |
| `GenrePicker.kt` | “Agregar género $label” | “Añadir género $label” |
| `App.kt` | “Escribí una descripción para generar la imagen.” | “Escribe una descripción para generar la imagen.” |
| `App.kt` | “La IA devolvió una imagen inválida. Intentá de nuevo.” | “Playbook devolvió una imagen inválida. Inténtalo de nuevo.” |
| `App.kt` | “No se pudo generar la imagen. Revisá la conexión e intentá de nuevo.” | “No se pudo generar la imagen. Revisa la conexión e inténtalo de nuevo.” |
| `App.kt` | “No se pudo generar la imagen. Intentá de nuevo.” | “No se pudo generar la imagen. Inténtalo de nuevo.” |
| `App.kt` | “Intentá de nuevo.” | “Inténtalo de nuevo.” |
| `App.kt` | “Permiso de cámara denegado. Habilitalo en los ajustes del sistema para usarla.” | “Permiso de cámara denegado. Habilítalo en los ajustes del sistema para usarla.” |
| `App.kt` | “Agregar imagen” | “Añadir imagen” |
| `App.kt` | “No se pudo agregar la imagen” | “No se pudo añadir la imagen” |
| `App.kt` | “Ocurrió un error al agregar la imagen.” | “Ocurrió un error al añadir la imagen.” |
| `App.kt` | “Generar con IA” | “Generar con Playbook” |
| `RelatedNotesScreen.kt` | “Cuando otras Notas se parezcan a esta, vas a verlas acá.” | “Cuando otras Notas se parezcan a esta, las verás aquí.” |
| `GddScreen.kt` | “Completalo desde Brainstorming.” | “Complétalo desde Brainstorming.” |
| `GddScreen.kt` | “Tus ideas capturadas van a aparecer acá, agrupadas por track.” | “Tus ideas capturadas aparecerán aquí, agrupadas por track.” |
| `Dictation.kt` | “Permiso de micrófono denegado. Habilitalo en los ajustes del sistema y reintentá.” | “Permiso de micrófono denegado. Habilítalo en los ajustes del sistema y reintenta.” |
| `Dictation.kt` | “No se escuchó nada. Probá de nuevo.” | “No se escuchó nada. Prueba de nuevo.” |
| `Dictation.kt` | “No hay conexión para transcribir. Revisá tu red e intentá otra vez.” | “No hay conexión para transcribir. Revisa tu red e inténtalo otra vez.” |
| `Dictation.kt` | “El reconocimiento está ocupado. Reintentá en un momento.” | “El reconocimiento está ocupado. Reintenta en un momento.” |
| `Dictation.kt` | “No se pudo transcribir. Probá de nuevo.” | “No se pudo transcribir. Prueba de nuevo.” |
| `NotesListScreen.kt` | “Tus ideas capturadas van a aparecer acá.” | “Tus ideas capturadas aparecerán aquí.” |
| `NotesListScreen.kt` | “Agregar imagen” | “Añadir imagen” |
| `NotesListScreen.kt` | “Galería y generar imagen con IA” | “Galería y generar imagen con Playbook” |
| `FirstRunOnboardingScreen.kt` | “Podés empezar con el Brainstorming guiado o crear el Proyecto vacío y completarlo después.” | “Puedes empezar con el Brainstorming guiado o crear el Proyecto vacío y completarlo después.” |
| `NoteEditorScreen.kt` | “Escribí tu idea…” | “Escribe tu idea…” |
| `NoteEditorScreen.kt` | “El cuerpo ya tiene texto. Elegí cómo integrar la transcripción:” | “El cuerpo ya tiene texto. Elige cómo integrar la transcripción:” |
| `NoteEditorScreen.kt` | “Sugerencia de la IA” | “Sugerencia de Playbook” |
| `NoteEditorScreen.kt` | “La IA no propuso una clasificación.” | “Playbook no propuso una clasificación.” |
| `ImageGenerationDialog.kt` | “Describí la imagen que querés crear.” | “Describe la imagen que quieres crear.” |
| `ImageGenerationDialog.kt` | “Generar imagen con IA” | “Generar imagen con Playbook” |
| `BrainstormingWizardScreen.kt` | “La IA no devolvió sugerencias nuevas o válidas. Probá de nuevo.” | “Playbook no devolvió sugerencias nuevas o válidas. Prueba de nuevo.” |
| `BrainstormingWizardScreen.kt` | “La IA está pensando ideas…” | “Playbook está pensando ideas…” |
| `BrainstormingWizardScreen.kt` | “La IA devolvió una sola idea. Podés pedir de nuevo.” | “Playbook devolvió una sola idea. Puedes pedir de nuevo.” |
| `BrainstormingWizardScreen.kt` | “No se pudieron obtener sugerencias. Probá de nuevo.” | “No se pudieron obtener sugerencias. Prueba de nuevo.” |
| `BrainstormingWizardScreen.kt` | “Elegí una categoría para cada mecánica con descripción.” | “Elige una categoría para cada mecánica con descripción.” |
| `BrainstormingWizardScreen.kt` | “No se pudo guardar el Proyecto. Intentá de nuevo.” | “No se pudo guardar el Proyecto. Inténtalo de nuevo.” |
| `BrainstormingWizardScreen.kt` | “Describí la idea central del juego.” | “Describe la idea central del juego.” |
| `BrainstormingWizardScreen.kt` | “Describí la mecánica en una frase.” | “Describe la mecánica en una frase.” |
| `BrainstormingWizardScreen.kt` | “Agregar inspiración” | “Añadir inspiración” |
| `BrainstormingWizardScreen.kt` | “Agregar fuera de alcance” | “Añadir fuera de alcance” |
| `BrainstormingWizardScreen.kt` | “Agregar mecánica” | “Añadir mecánica” |
| `BrainstormingWizardScreen.kt` | “Agregar” (botón) | “Añadir” |
| `AskNotesScreen.kt` | “Intentá de nuevo.” | “Inténtalo de nuevo.” |
| `AskNotesScreen.kt` | “Escribí una pregunta sobre tus notas.” | “Escribe una pregunta sobre tus notas.” |
| `AskNotesScreen.kt` | “Todavía no hay notas indexadas. Guardá una nota de texto y volvé a preguntar.” | “Todavía no hay notas indexadas. Guarda una nota de texto y vuelve a preguntar.” |
| `AskNotesScreen.kt` | “No se pudo responder. Revisá el runtime de IA e intentá de nuevo.” | “Playbook no pudo responder. Revisa la conexión e inténtalo de nuevo.” |
| `AudioTranscription.kt` | “Transcripción aplicada. Guardá para conservarla.” | “Transcripción aplicada. Guarda para conservarla.” |
| `AudioTranscription.kt` | “No se pudo transcribir. Revisá la conexión e intentá de nuevo” | “No se pudo transcribir. Revisa la conexión e inténtalo de nuevo” |

### Prompts (`core/src/commonMain/kotlin/com/playbook/core/ai/`)

| Archivo | Antes | Después |
| --- | --- | --- |
| `BrainstormingSuggestions.kt` | “Sos un asistente de diseño de videojuegos. Sugerí exactamente 2 ideas…” | “Eres Playbook, un asistente de diseño de videojuegos. Sugiere exactamente 2 ideas…” |
| `BrainstormingSuggestions.kt` | “Respondé sólo con 2 líneas…” | “Responde solo con 2 líneas…” |
| `BrainstormingSuggestions.kt` | “Escribí en español, máximo 200 caracteres…” | “Escribe en español de España (castellano), sin voseo, máximo 200 caracteres…” |
| `BrainstormingSuggestions.kt` | “Para cada idea usá el formato…” | “Para cada idea usa el formato…” |
| `RagQueryService.kt` | “Respondé la pregunta…” | “Responde la pregunta…” |
| `RagQueryService.kt` | “decilo explícitamente” | “dilo explícitamente” |
| `RagQueryService.kt` | “Escribí en español, en texto plano…” | “Escribe en español de España (castellano), sin voseo, en texto plano…” |
| `openai/OpenAiAdapters.kt` | “Sos un asistente de diseño de videojuegos. Respondé en español, de forma concreta y práctica.” | “Eres Playbook, un asistente de diseño de videojuegos. Responde en español de España (castellano), sin voseo, de forma concreta y práctica.” |

## Technical Approach

1. Aplicar las sustituciones exactas del inventario en `composeApp/src/commonMain`
   y `core/src/commonMain`, incluyendo comentarios que citan literalmente copy.
2. Conservar los cambios locales de soporte: permiso `INTERNET`, modo real en
   `gradle.properties` y textos de marca en `BrainstormingWizardScreen.kt`.
3. Actualizar `AudioTranscriptionTest.kt` y agregar aserciones de copy es-ES en
   `RagQueryCheck.kt`/`BrainstormingSuggestionCheck.kt`.
4. Agregar `DictationCopyTest.kt` (test puro de `VoiceCaptureError.message()`).
5. Actualizar referencias vigentes de `DESIGN.md`/`ARCHITECTURE.md`.
6. Ejecutar tests, builds y gate; registrar evidencia en `feature_list.json` y
   `PROGRESS.md`; estado final `passing` (sin `accepted`).

## Expected File Changes

- UI: `GenrePicker.kt`, `App.kt`, `RelatedNotesScreen.kt`, `GddScreen.kt`,
  `Dictation.kt`, `NotesListScreen.kt`, `FirstRunOnboardingScreen.kt`,
  `NoteEditorScreen.kt`, `ImageGenerationDialog.kt`,
  `BrainstormingWizardScreen.kt`, `AskNotesScreen.kt`, `AudioTranscription.kt`.
- Config de soporte: `composeApp/src/androidMain/AndroidManifest.xml`,
  `gradle.properties`.
- IA: `BrainstormingSuggestions.kt`, `RagQueryService.kt`,
  `openai/OpenAiAdapters.kt`.
- Tests: `AudioTranscriptionTest.kt`, `RagQueryCheck.kt`,
  `BrainstormingSuggestionCheck.kt`, `DictationCopyTest.kt` (nuevo).
- Docs: `docs/specs/spain-spanish-copy.md` (nuevo), `DESIGN.md`,
  `ARCHITECTURE.md`, `feature_list.json`, `PROGRESS.md`.

**Sin cambios:** esquema, `.sqm`, dependencias, `init.sh`, runtime/adaptadores
reales, `iosApp/**`, `docs/validations/` ni evidencia histórica.

## Visual Design Impact

UI involved: **sí**, sólo texto. No hay cambios de layout, color, tipografía,
componentes ni artefactos visuales nuevos. La voz de marca queda reflejada en
`DESIGN.md`.

## Durable Documentation Impact

- `DESIGN.md`: update — copy de acciones de imagen y “Añadir” con voz Playbook.
- `ARCHITECTURE.md`: update — referencias de copy vigentes.
- `feature_list.json` y `PROGRESS.md`: update con estado y evidencia.
- `docs/validations/`, specs previas y evidencias históricas: **no** se
  reescriben.

## Implementation Plan / Tasks

- [ ] Crear spec y entrada `feature_list.json` (planificación).
- [ ] Aplicar copy es-ES y voz Playbook en UI.
- [ ] Actualizar prompts de IA sin cambiar su lógica.
- [ ] Actualizar/agregar tests de copy.
- [ ] Actualizar `DESIGN.md` y `ARCHITECTURE.md`.
- [ ] Ejecutar tests/build/link/gate y greps de barrido.
- [ ] Registrar evidencia y dejar estado `passing`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL y tests verdes.
- `CI=true ./init.sh` → exit 0 (gate Android-only, IA fake).
- Build/instalación manual: usar siempre `-Pplaybook.ai.mode=fake` (excepción
  aceptada); el modo `real` por defecto requiere key fuera del repo.
- Greps de barrido sobre `composeApp/src/commonMain` y `core/src/commonMain`:
  voseo/regionalismos, “Agregar”, “IA” y “La IA”. Inspeccionar hits y justificar
  sólo comentarios internos o términos técnicos no visibles.
- Si hay emulador disponible: smoke Android fake de lista vacía, diálogo de
  imagen (“Añadir imagen”/“Generar con Playbook”), picker de Géneros, editor de
  Nota, dictado/errores, Brainstorming, Q&A, GDD y Notas relacionadas.
- Tests de copy: `RagQueryCheck` (“español de España”, sin “Respondé”),
  `BrainstormingSuggestionCheck` (“Eres Playbook”, “español de España”, sin
  “Sugerí”), `DictationCopyTest` (“Habilítalo”, “reintenta”, tuteo) y
  `AudioTranscriptionTest` actualizado.
- E2E persistente: **no aplica** (cambio de copy; no hay harness E2E).

## Evidence To Capture

- Salida de tests de `:core` y `:composeApp` (suites/tests/fallos).
- `assembleDebug`, link iOS y `CI=true ./init.sh` con resultado.
- Resultado de los greps de barrido y justificación de hits.
- Smoke Android (si hay emulador) con capturas/rutas.
- Estado `passing` y evidencia en `feature_list.json`/`PROGRESS.md`.
- `git diff` acotado: sin esquema, `.sqm`, deps, `init.sh`, runtime/adaptadores
  reales, `iosApp/**` ni secrets.

## Known Risks

- **Copy fuera de los archivos inventariados:** puede quedar texto en pantallas
  no cubiertas; el barrido de greps y el smoke lo detectan.
- **Tests que usan voseo como dato de entrada:** no se modifican porque no son
  copy de la app.
- **“IA” en términos técnicos/comentarios:** puede aparecer en diagnósticos o
  documentación; no debe quedar en copy visible.
- **Modo real en `gradle.properties` (excepción aceptada):** el gate fuerza fake;
  cualquier build/instalación manual debe usar `-Pplaybook.ai.mode=fake` o
  configurar la key fuera del repo. El build por defecto sin flag/key falla por
  diseño y no es un criterio de aceptación de esta feature.

## Note On Historical Copy

Esta spec reemplaza el copy citado en specs y validaciones anteriores. La
evidencia histórica (`docs/validations/`, entradas antiguas de `PROGRESS.md` y
`feature_list.json`) se conserva sin reescribir; las referencias de producto
vigentes en `DESIGN.md`/`ARCHITECTURE.md` sí se actualizan.

## Validator Checklist

- [ ] Todo el copy visible de los archivos inventariados usa español de España y
      tuteo; no quedan formas de voseo/regionalismos.
- [ ] “Agregar” se reemplazó por “Añadir” en botones y etiquetas accesibles.
- [ ] “La IA”/“Generar con IA”/“runtime de IA” desaparecieron del copy visible;
      se usa “Playbook”/“Generar con Playbook”.
- [ ] Los prompts contienen “Eres Playbook”/“español de España” y no voseo, sin
      cambios de lógica, marcadores, temperatura ni tokens.
- [ ] Tests nuevos/actualizados verdes; gate, build Android y link iOS en verde.
- [ ] Sin cambios en esquema, `.sqm`, deps, `init.sh`, runtime/adaptadores reales,
      `iosApp/**` ni secrets.
- [ ] `DESIGN.md`/`ARCHITECTURE.md` actualizados; evidencia histórica intacta.
- [ ] `feature_list.json` en `passing` con evidencia y `PROGRESS.md` actualizado;
      no se marcó `accepted`.
