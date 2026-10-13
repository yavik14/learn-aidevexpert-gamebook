# Feature Implementation Spec: OCR del boceto a texto derivado (decisión de viabilidad)

## Source Feature

- `id`: `image-ocr-optional`
- `area`: `capture-image`
- `depends_on`: `["image-capture-camera"]` (ya `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Cerrar de forma **explícita y verificable** la pregunta de viabilidad de
`image-ocr-optional`: ¿conviene extraer el **texto manuscrito** de un boceto
adjunto y guardarlo como **texto derivado** de la Nota en este proyecto
(KMP + Compose Multiplatform, Android + iOS en paralelo, local-first, sin
dependencias pesadas sin justificación)?

La spec **decide no integrar OCR en el MVP** (decisión fundamentada con una
evaluación de alternativas) y, dado que la feature es opcional **por diseño**
(`notes`: "Opcional/min. Si se descarta, actualizar docs y el estado de la
feature"), el entregable es un **registro de decisión** + la actualización de los
documentos durables + la actualización del estado/evidencia de la feature. No se
agrega código de producto, dependencias ni cambios de esquema.

> **Decisión de viabilidad (resumen):** **DISCARD** para el MVP. Los motores
> on-device viables son orientados a **texto impreso** (no manuscrito), un seam
> multiplataforma exigiría una dependencia Android-only pesada (ML Kit) con poca
> superficie compartida KMP, y el puerto `AiClient` es **text-only** (no puede
> hacer visión sin extenderse, fuera de alcance). Se documenta y se fija un
> disparador de re-evaluación.

## Non-Goals

- **No** implementar OCR real (ni Android, ni iOS, ni cloud), ni un puerto
  `TextRecognizer`, ni adaptadores por plataforma.
- **No** agregar dependencias (ML Kit, Tesseract, SDKs de visión cloud) ni tocar
  `gradle/libs.versions.toml`/`build.gradle.kts`.
- **No** cambiar `:core` (modelo, repositorios, esquema SQLDelight, `AiClient`),
  ni la UI (`App`, `NoteEditorScreen`, `NotesListScreen`), ni el flujo de captura
  de imagen ya `accepted`.
- **No** guardar texto derivado en el `Adjunto` (el modelo de dominio ubica el
  texto derivado en la Nota); esta feature no define ese flujo.
- **No** resolver la pregunta abierta de RAG sobre adjuntos sin texto
  (`docs/risks-and-open-questions.md`); sólo se deja más precisa.
- **No** formalizar tokens de diseño ni tocar `DESIGN.md`.

## Job Story

When un boceto tiene anotaciones manuscritas que me gustaría buscar o enlazar con
el resto de mis notas,
I want saber si el OCR es viable en este proyecto y, si no lo es, tenerlo
documentado con el motivo y cuándo reevaluarlo,
so I can evitar prometer una capacidad que hoy no aporta valor y no cargar la app
con dependencias pesadas sin justificación.

## Users And Permissions

- Autor/usuario único del MVP (local-first). Sin auth.
- Sin cambios de permisos: no se toca `AndroidManifest.xml` ni `Info.plist`.

## Acceptance Scenarios

### Scenario 1: Registro de decisión con alternativas y rationale

Given la feature `image-ocr-optional` sin spec previa,
When se cierra la fase de decisión,
Then existe `docs/adr/0002-ocr-decision.md` (formato MADR-lite, consistente con
`docs/adr/0001-ai-runtime-decision.md`) que documenta: contexto, criterios,
al menos 4 alternativas (Android ML Kit, iOS Vision, `AiClient`/visión cloud,
descartar), trade-offs, la decisión **DISCARD para el MVP**, consecuencias y
**disparadores de re-evaluación**.

### Scenario 2: Documentos durables reflejan el descarte

Given la decisión tomada,
When se actualizan los documentos durables,
Then `docs/technical-discovery.md` (Integrations + sección "OCR Decision"),
`docs/risks-and-open-questions.md` (OCR fuera del MVP; pregunta de RAG
precisada) y `ARCHITECTURE.md` (sección "Deferred") dejan explícito que el OCR del
boceto **no** está en el MVP, con puntero al registro de decisión y el
disparador de re-evaluación.

### Scenario 3: Estado de la feature actualizado

Given la decisión y los docs,
When se cierra la feature,
Then la entrada `image-ocr-optional` en `feature_list.json` registra el descarte
en `notes` + `evidence` (con puntero al registro de decisión) y el estado se
mueve a `passing` (auto-verificado) para su validación independiente, y
`PROGRESS.md`/`AGENTS.md` quedan al día.

### Scenario 4: Gate honesto, sin código ni dependencias nuevas

Given la feature implementada como decisión + docs,
When se ejecuta `./init.sh` y los comandos de Gradle,
Then `./init.sh` → exit 0 sin dev servers ni simuladores; la app Android compila,
el framework iOS enlaza y los tests del core (Android/JVM e iOS) corren verdes
**sin** cambios en código de producto, sin dependencias nuevas y con el esquema
sin cambios (`PlaybookDatabase.Schema.version == 4`, sin `.sqm` nuevo).

### Scenario 5: Control negativo del descarte

Given el árbol de trabajo de la feature,
When se inspecciona el código,
Then **no** existe ningún artefacto de OCR (`ML Kit`, `com.google.mlkit`,
`VNRecognizeTextRequest`, `TextRecognizer`, adaptador de visión) en `:core` ni en
`:composeApp`, y `AiClient`/su contrato no cambiaron.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `image-capture-camera` `accepted`
  (2026-10-09); `image-ocr-optional` es la próxima feature "en cola" sin spec.
- `feature_list.json` — `image-ocr-optional` (`area: capture-image`,
  `depends_on: [image-capture-camera]`, `status: not_started`); verificación
  provisional: "test de OCR sobre una imagen de muestra; si no es viable, se
  documenta el descarte"; nota: "Opcional/min. Si se descarta, actualizar docs y
  el estado de la feature."
- `CONTEXT.md` — `Adjunto` se **distingue** del texto derivado
  (transcripción / OCR); el texto derivado vive en la Nota.
- `docs/domain-model.md` — `Nota` 1—N `Adjunto`; edge case "Adjunto sin texto
  (audio no transcripto / imagen sin OCR): ¿es indexable para RAG?" (abierto).
- `docs/build-brief.md` — slice 3: captura por imagen → Nota con `Adjunto`; el
  OCR **no** figura como requisito del MVP.
- `docs/technical-discovery.md` — Integrations: "Cámara / galería: captura de
  bocetos; el OCR (si existe) es texto derivado"; Constraints: offline-first y
  "costo de KMP × 2 plataformas".
- `docs/risks-and-open-questions.md` — "¿Los Adjuntos sin texto entran al RAG?"
  (Blocking Next Phase); runtime de IA decidido (ADR 0001).
- `docs/adr/0001-ai-runtime-decision.md` — precedente MADR-lite: alternativas
  puntuadas, decisión, consecuencias, evidencia medido vs estimado, preguntas
  abiertas; estrategia **híbrida por fases, cloud-first** detrás de `AiClient`.
- `ARCHITECTURE.md` — `:core` (dominio/persistencia) vs `:composeApp`
  (UI/nativo); esquema **v4** (`attachment`, `3.sqm`); sección "Deferred" ya
  lista "OCR del boceto (`image-ocr-optional`)".
- `docs/specs/image-capture-camera.md` (predecesora) — Non-Goals explícitos:
  "OCR (`image-ocr-optional`), texto derivado del Adjunto"; patrón de
  integración nativa con `expect/actual` o puerto + adaptadores.
- `composeApp/src/commonMain/.../ImageCapture.kt` — `PickedImage`, `ImagePicker`,
  `AttachmentFileStore`, `decodeImageBitmap`: la imagen ya se captura y persiste;
  **no** hay seam de reconocimiento de texto.
- `composeApp/src/commonMain/.../App.kt`, `NoteEditorScreen.kt` — flujo de
  creación con imagen; `body` es texto escrito por el usuario; no hay campo de
  texto derivado.
- `core/src/commonMain/.../model/Attachment.kt` — `Attachment`/`AttachmentKind`
  (`IMAGE`); KDoc: "No guarda el texto derivado (OCR/transcripción)".
- `core/src/commonMain/kotlin/com/playbook/core/ai/AiClient.kt` (referido) —
  `embed(texts)` / `generate(request)` **text-only**; `FakeAiClient` default;
  runtime real cloud es follow-up del ADR 0001.
- `core/build.gradle.kts`, `composeApp/build.gradle.kts`,
  `gradle/libs.versions.toml` — deps actuales (SQLDelight, coroutines, Compose MP,
  `activity-compose`); ninguna de visión/OCR.
- `init.sh` — gate no bloqueante (assembleDebug + tests core Android/JVM e iOS +
  link framework iOS), sin dev servers ni simuladores.
- `docs/release/ios-testflight.md`, `docs/release/android.md` (referidos) —
  política de no versionar secrets, relevante para descartar OCR cloud con keys.

### Existing Patterns To Follow

- **Decisiones fundamentadas como artefacto durable:** `ai-runtime-decision`
  produjo un ADR MADR-lite + docs; esta feature reutiliza ese formato para una
  decisión de integración.
- **Integraciones nativas tras un puerto/seam** (`VoiceTranscriber`,
  `ImageCapture`): si en el futuro se implementa OCR, encajaría el mismo patrón;
  hoy no se crea el seam.
- **Local-first / offline-first:** la captura nunca se bloquea; OCR sería texto
  derivado asíncrono.
- **Sin dependencias pesadas sin justificación** y **nunca versionar secrets**.
- Documentos en **español**, términos técnicos en **inglés**.
- `init.sh` no bloqueante, sin dev servers ni simuladores.

### Current Gaps

- No existe evaluación escrita de opciones de OCR para este proyecto.
- El puerto `AiClient` no tiene superficie de visión (ni la tendrá en esta
  feature); no hay adaptador real cloud/on-device (Fake default).
- No hay seam compartido de reconocimiento de texto ni adaptador nativo.
- La calidad del OCR sobre **manuscrito** no está garantizada por los motores
  on-device de bajo costo (ver Technical Approach).

## Technical Approach

### Evaluación de alternativas (viabilidad)

| Alternativa | Fuente / costo | Manuscrito | KMP compartido | Secrets | Veredicto |
| --- | --- | --- | --- | --- | --- |
| **Android ML Kit Text Recognition v2** (`com.google.mlkit:text-recognition*`) | Dep Android-only (modelo bundled varios MB, o Play Services) | **No oficial** (orientado a texto impreso) | Bajo (`androidMain` sólo) | No (offline con bundled) | Se descarta: dep pesada por plataforma, calidad incierta en manuscrito, poco compartida |
| **iOS Vision `VNRecognizeTextRequest`** | Framework del sistema (sin dep), iOS 15.3 ok | Limitado (primario impreso) | Bajo (`iosMain` sólo) | No | Se descarta como integración MVP: asimétrico vs Android y calidad manuscrita no garantizada |
| **`AiClient` (puerto existente)** | Ya existe, pero `embed(texts)`/`generate(request)` son **text-only** | N/A | Alto (si fuera text-only) | — | No viable: no acepta imágenes; extender el puerto a visión está fuera de alcance y rompe su neutralidad de vendor |
| **OCR cloud (API externa)** | Requiere key/red/costo | Variable | Medio | **Sí** (rompe local-first y la política de no versionar secrets) | Se descarta: viola offline-first y la política de secrets del ADR 0001 |
| **Descartar para el MVP** | Sin costo | — | — | No | **Elegida**: ver decisión |

### Decisión

**Se descarta la integración de OCR para el MVP.** Fundamento:

1. **El objetivo es texto manuscrito** de un boceto. Los motores on-device de
   bajo costo (ML Kit Text Recognition; Vision) están orientados a **texto
   impreso**; la calidad sobre manuscrito no es confiable, así que el valor de
   producto hoy es bajo.
2. **Asimetría y peso de dependencias:** iOS aportaría OCR sin dep, pero Android
   exigiría una dependencia nativa pesada (ML Kit) con **poca superficie
   compartida KMP** y costo de tamaño/CI. "Sin deps pesadas sin justificación".
3. **`AiClient` no puede hacer visión** sin extender el puerto (hoy text-only y
   vendor-neutral por diseño); un proveedor de visión real es follow-up del ADR
   0001 y no existe adaptador real todavía.
4. **La feature es explícitamente opcional/min** y el OCR no es requisito del MVP
   (`docs/build-brief.md`); es un "nice to have".
5. **Offline-first:** el OCR sería texto derivado asíncrono que no debe bloquear
   la captura; no aporta al flujo end-to-end mínimo.

### Disparadores de re-evaluación

- Existe un adaptador **`AiClient` con capacidad de visión** (cloud con key
  provista por el usuario, o fase 2 on-device) por el mismo puerto.
- El producto **necesita** texto derivado de `Adjunto` para RAG
  (`docs/risks-and-open-questions.md`, "Blocking Next Phase").
- Aparece **evidencia de calidad** aceptable de OCR sobre **manuscrito** en una
  plataforma objetivo (idealmente sin dep pesada).

### Entregable (docs, sin producto)

- Registro de decisión `docs/adr/0002-ocr-decision.md`.
- Actualización de `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`
  y `ARCHITECTURE.md` (Deferred).
- Actualización de `feature_list.json` (notas/evidencia/estado), `PROGRESS.md` y
  `AGENTS.md` al cerrar.

## Expected File Changes

- `docs/adr/0002-ocr-decision.md` — **crear**; registro MADR-lite (contexto,
  criterios, alternativas, trade-offs, decisión DISCARD, consecuencias,
  disparadores de re-evaluación, evidencia y límites honestos).
- `docs/technical-discovery.md` — **modificar**; Integrations (OCR fuera del MVP)
  + sección "OCR Decision (image-ocr-optional)".
- `docs/risks-and-open-questions.md` — **modificar**; OCR descartado del MVP y
  pregunta de RAG de adjuntos sin texto precisada.
- `ARCHITECTURE.md` — **modificar**; "Deferred" con el descarte y el disparador de
  re-evaluación; línea introductoria si aplica.
- `feature_list.json` — **modificar al cerrar**; entry `image-ocr-optional`
  (`notes`/`evidence`/`status`).
- `PROGRESS.md` — **modificar al cerrar**; sesión con decisión y evidencia.
- `AGENTS.md` — **modificar al cerrar**; línea "Próxima feature en cola".
- `docs/specs/image-ocr-optional.md` — este contrato (puede marcarse tasks).

No hay cambios en `core/src/**`, `composeApp/src/**`, `iosApp/**`,
`gradle/libs.versions.toml`, `build.gradle.kts` ni `init.sh`.

## Visual Design Impact

- UI involved: no — es una feature de decisión + documentación; no hay superficie
  de UI nueva. No se toca `DESIGN.md`.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — la sección "Deferred" pasa de listar el OCR
  como pendiente a declararlo descartado en el MVP con puntero al ADR y
  disparador de re-evaluación.
- `CONSTRAINTS.md`: not needed — no surge una regla MUST/MUST NOT durable nueva;
  "sin deps pesadas sin justificación" y la política de secrets ya existen.
- `AGENTS.md`: **update** — cambia la línea "Próxima feature en cola"; el
  workflow y el gate no cambian.
- Other docs: `docs/adr/0002-ocr-decision.md` (create),
  `docs/technical-discovery.md` (update), `docs/risks-and-open-questions.md`
  (update), `feature_list.json`/`PROGRESS.md` (update al cerrar).
  `CONTEXT.md`/`docs/domain-model.md`: not needed — el glosario y el edge case de
  "Adjunto sin OCR" ya contemplan el escenario; el ADR lo aclara.

## Implementation Plan

1. Redactar `docs/adr/0002-ocr-decision.md` con la evaluación de alternativas y la
   decisión DISCARD + disparadores (sin ejecutar OCR).
2. Actualizar `docs/technical-discovery.md` (Integrations + "OCR Decision"),
   `docs/risks-and-open-questions.md` y `ARCHITECTURE.md` (Deferred).
3. Verificar el gate (`./init.sh` y comandos Gradle) y confirmar que **no** hay
   cambios de producto, dependencias ni esquema.
4. Actualizar `feature_list.json`, `PROGRESS.md` y `AGENTS.md`; registrar
   evidencia del descarte.

## Implementation Tasks

- [x] Crear `docs/adr/0002-ocr-decision.md` (MADR-lite: alternativas, tabla de
      trade-offs, decisión DISCARD, consecuencia, disparadores, límites).
- [x] Actualizar `docs/technical-discovery.md` con "OCR Decision
      (image-ocr-optional)" y la línea de Integrations.
- [x] Actualizar `docs/risks-and-open-questions.md` (OCR fuera del MVP; RAG de
      adjuntos sin texto sigue abierto).
- [x] Actualizar `ARCHITECTURE.md` ("Deferred": OCR descartado + disparador).
- [x] Correr `./init.sh` y los comandos Gradle; confirmar sin cambios de
      producto/deps/esquema.
- [x] Confirmar el control negativo (sin `mlkit`/`VNRecognizeTextRequest`/
      `TextRecognizer`/`AiClient` modificados).
- [x] Actualizar `feature_list.json`, `PROGRESS.md` y `AGENTS.md` al cerrar.

## Verification Plan

### Verificable en el harness (`init.sh` / Gradle)

- `./init.sh` → exit 0; gate no bloqueante, sin dev servers ni simuladores; con
  `init.sh` sin cambios (`git diff -- init.sh` vacío).
- `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test
  :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64
  --rerun-tasks` → BUILD SUCCESSFUL con los tests existentes en verde (Android/JVM
  e iOS), 0 failures.
- Esquema sin cambios: `PlaybookDatabase.Schema.version == 4`; sin `.sqm` nuevo
  (`find core/src -name '*.sqm'` = los 3 existentes).
- Sin dependencias nuevas: `git diff` vacío en `gradle/libs.versions.toml`,
  `core/build.gradle.kts` y `composeApp/build.gradle.kts`.
- Alcance: `git diff` sin cambios en `core/src/**`, `composeApp/src/**`,
  `iosApp/**`; sólo documentación + `feature_list.json`/`PROGRESS.md`/`AGENTS.md`.
- Control negativo: `grep -riE "mlkit|VNRecognizeTextRequest|TextRecognizer"
  core/src composeApp/src` → sin resultados; el contrato de `AiClient` sin
  cambios.

### Comprobaciones documentales (manuales/objetivas)

- `docs/adr/0002-ocr-decision.md` existe e incluye: contexto, criterios, ≥4
  alternativas, tabla de trade-offs, decisión DISCARD, consecuencias,
  disparadores de re-evaluación y límites honestos (no se ejecutó OCR).
- `docs/technical-discovery.md`, `docs/risks-and-open-questions.md` y
  `ARCHITECTURE.md` mencionan el descarte y apuntan al ADR.

### E2E

- E2E persistente: **no aplica**. La feature no cambia comportamiento observable
  de usuario/API (es una decisión + documentación) y el repo **no** tiene harness
  de UI/E2E; `init.sh` no levanta simuladores. La "verificación" es documental +
  el gate no bloqueante, consistente con `ai-runtime-decision`.

### `init.sh`

- Debe ejecutar el gate estándar no bloqueante del estado actual; **no** debe
  levantar procesos de larga duración (sin simuladores ni dev servers). No
  requiere cambios.

## Evidence To Capture

- Contenido de `docs/adr/0002-ocr-decision.md` (a crear) y diff de los docs
  durables actualizados.
- Salida de `./init.sh` (exit 0) y de los comandos Gradle con tests en verde.
- Confirmación de `Schema.version == 4` sin `.sqm` nuevo y sin dependencias
  nuevas.
- `git diff` sin cambios de código de producto.
- Control negativo: `grep` sin artefactos de OCR en `:core`/`:composeApp`.

## Validator Checklist

- [ ] Alcance respetado: decisión + documentación; **sin** implementación de OCR,
      sin deps, sin cambios de `:core`/`:composeApp`/UI/esquema.
- [ ] Escenarios 1–5 pasan (la decisión existe, los docs la reflejan, el gate es
      honesto y el control negativo no halla artefactos de OCR).
- [ ] El ADR contiene alternativas reales (ML Kit / Vision / `AiClient`+cloud /
      descartar), trade-offs y **disparadores de re-evaluación**.
- [ ] Se declara honestamente que no se ejecutó OCR ni se midió calidad.
- [ ] No hay E2E persistente y la spec lo justifica.
- [ ] `feature_list.json` (entry `image-ocr-optional`), `PROGRESS.md` y
      `AGENTS.md` actualizados; evidencia registrada.
- [ ] `init.sh` sin cambios, no bloqueante y sin procesos de larga duración.

## Open Questions / Bloqueos

- **Representación del estado final:** ¿una feature descartada debe quedar
  `accepted` (el entregable es la decisión, como `ai-runtime-decision`) o el
  harness prefiere otro estado/removerla? La spec propone `passing → accepted` con
  el descarte documentado; si el orquestador/validador prefiere otra convención,
  registrarla en `notes` de la entrada.
- **RAG de adjuntos sin texto** (pregunta abierta, `docs/risks-and-open-questions.md`):
  el descarte de OCR implica que un boceto no aporta texto indexable; definir si
  un `Adjunto` sin texto participa del RAG queda para la feature de indexado/RAG.
- **Calidad de manuscrito:** no se midió (no se ejecutó OCR). Si en el futuro hay
  evidencia de calidad aceptable sin dep pesada, aplicar los disparadores de
  re-evaluación y reabrir la feature con un seam `TextRecognizer` (patrón
  `VoiceTranscriber`).
- **`AiClient` con visión:** extender el puerto para aceptar imágenes es una
  decisión de arquitectura aparte (vendor-neutral) y no se aborda aquí.
