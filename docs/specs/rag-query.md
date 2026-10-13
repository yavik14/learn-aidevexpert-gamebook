# Feature Implementation Spec: Consulta en lenguaje natural sobre las notas (RAG)

## Source Feature

- `id`: `rag-query`
- `area`: `rag`
- `depends_on`: `["embeddings-generation", "ai-runtime-decision"]` (ambas `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Permitir que el usuario **pregunte en lenguaje natural** y reciba una **respuesta
generada a partir de sus propias Notas**: se embebe la pregunta con el puerto
`AiClient`, se recuperan por **similitud coseno en memoria** las Notas con
embedding persistido más cercanas (top-K), y se genera la respuesta con
`AiClient.generate(GenerateRequest(prompt = pregunta, context = cuerpos
recuperados))`. Entrega (a) `RagQueryService` + utilidad de similitud en `:core`,
(b) una pantalla mínima de "Preguntar" en la UI y (c) el **primer wiring del
runtime de IA a la app**.

Para que el flujo sea observable end-to-end, la feature también cablea el
**indexado best-effort al guardar** una Nota reutilizando el
`NoteIndexingService` ya existente (`embeddings-generation` lo dejó sin
consumidor). Sin esto, ninguna Nota del usuario tendría embedding y el RAG
siempre vería "sin notas indexadas".

### Limitación honesta del runtime (documentada)

El **adaptador real** (cloud/on-device) **no** está implementado: el proveedor,
el modelo, la dimensión y la gestión de keys son follow-ups del ADR 0001
(`docs/adr/0001-ai-runtime-decision.md`). La feature consume el puerto `AiClient`
y la app inyecta `FakeAiClient` (determinista, sin red) por defecto; por lo tanto
la "respuesta generada" que se muestra hoy es la del adaptador inyectado
(`FakeAiClient`), no una respuesta de un LLM real. La verificación prueba el
**pipeline** (retrieval real por similitud + petición de generación anclada en
las Notas recuperadas), no la calidad de la respuesta.

## Non-Goals

- **Citas / atribución / navegación a las Notas usadas**: es `rag-answer-citations`.
  La respuesta en la UI muestra **sólo el texto generado**; el `RagQueryService`
  devuelve las fuentes como dato de retrieval, pero la UI **no** las renderiza.
- **Scheduling/asincronía de indexado con `NoteStatus`**: la transición
  `capturada → pendiente → indexada/fallida`, reintentos, conectividad y colas son
  `note-enrichment-status-ui` / `offline-pending-retry`. El indexado al guardar es
  **best-effort, síncrono al flujo de UI y silencioso ante fallo**; no cambia
  `NoteStatus`.
- **Enlaces semánticos persistidos** (`semantic-linking`, `note-links-ui`): el
  retrieval del RAG es **efímero** (por consulta) y no crea filas `link`.
- **Enriquecimiento/clasificación IA** (`ai-enrichment-classify-tags-type`),
  OCR (`image-ocr-optional`), GDD (`gdd-view`).
- **Adaptador cloud/on-device real**, elección de proveedor/modelo/dimensión y
  gestión de keys (follow-ups del ADR 0001). Sólo se inyecta `FakeAiClient`.
- **Streaming** de la respuesta, historial de consultas, multi-turno o
  conversación (vision de producto; fuera del MVP).
- **Cambios de esquema/migraciones**: se reusan `note` y `embedding` (esquema v4);
  sin `.sqm` nuevos.
- **Dependencias nuevas**: Compose y `kotlinx-coroutines` ya están presentes.

## Job Story

When estoy diseñando un juego y acumulé varias Notas,
I want preguntar en lenguaje natural y recibir una respuesta armada sólo con lo
que escribí,
so I can encontrar y conectar mis ideas sin releer todo ni depender de
conocimiento externo.

## Users And Permissions

- Usuario local single-user (MVP): escribe una pregunta y ve la respuesta. Sin
  cuentas, auth ni permisos nuevos.
- El runtime de IA conlleva costo/key sólo si se elige un adaptador cloud real
  (fuera de alcance); `FakeAiClient` no usa red ni secretos.

## Acceptance Scenarios

### Scenario 1: Recuperar y responder con las Notas reales (happy path)

Given varias Notas persistidas cuyos embeddings se generaron con
`NoteIndexingService` sobre el mismo `AiClient` de la consulta,
When se llama `RagQueryService.ask(question)`,
Then el resultado es `RagAnswer.Answer` con `text` = lo devuelto por
`AiClient.generate` y `sources` ordenadas por score coseno descendente (la Nota
más cercana primero, top-K), y la `GenerateRequest` capturada tiene
`prompt = question.trim()` y `context` = cuerpos de las `sources` en ese orden;
`embed` se llamó una vez (con la pregunta) y `generate` una vez.

### Scenario 2: Sin Notas indexadas no se genera nada

Given Notas persistidas sin ninguna fila en `embedding`,
When se llama `ask(question)`,
Then el resultado es `RagAnswer.NoIndexedNotes` y el `AiClient` **no** recibe
ninguna llamada (`embed`/`generate` sin invocar): las respuestas provienen sólo
de las Notas (`docs/domain-model.md`), nunca de conocimiento externo.

### Scenario 3: Fallo del runtime se mapea a `Failed`

Given un `AiClient` de test que lanza `AiClientException` en `embed` o en
`generate`,
When se llama `ask(question)` con al menos una Nota indexada,
Then el resultado es `RagAnswer.Failed(cause)` con la causa propagada; no hay
respuesta inventada ni excepción sin capturar.

### Scenario 4: Respuesta inválida de `embed` no produce respuesta

Given un `AiClient` de test cuyo `embed` devuelve `0` vectores, `>1` vectores o
un vector de dimensión `0`,
When se llama `ask(question)`,
Then el resultado es `RagAnswer.Failed` con `AiClientException` y no se llama a
`generate`.

### Scenario 5: Contrato de similitud coseno (control negativo)

Given `cosineSimilarity(a, b)`,
Then el mismo vector da `1f`, vectores ortogonales dan `0f`, vectores opuestos
dan `-1f`, un vector nulo da `0f` (sin `NaN`), `cos(a, b) == cos(b, a)`, y
tamaños distintos lanzan `IllegalArgumentException`. Invertir el signo del
`dot product` debe romper el test (control negativo del validador).

### Scenario 6: Ranking determinista, top-K y dimensiones incompatibles

Given más `maxSources` candidatos con scores distintos y alguno con dimensión
distinta a la del vector de la pregunta,
When se llama `ask(question)`,
Then `sources` tiene a lo sumo `maxSources` elementos, ordenados por score
descendente con desempate estable por `note.id`, y los embeddings con dimensión
incompatible se descartan (no rompen el cálculo). Con `maxSources = 0` o todos
los candidatos descartados → `NoIndexedNotes`.

### Scenario 7: Indexado best-effort al guardar (glue para el RAG)

Given la app con `NoteIndexingService` cableado,
When el usuario crea una Nota de texto con body no vacío (y también al editarla),
Then se persiste una fila `embedding` para esa Nota sin bloquear ni fallar el
guardado, el `NoteStatus` **no** cambia y, si el runtime falla, el fallo se
ignora (la Nota queda guardada). Las Notas con body en blanco (p. ej. sólo
imagen) no se indexan.

### Scenario 8: UI de "Preguntar" (consulta)

Given la pantalla de consulta alcanzable desde la lista ("Preguntar"),
When el usuario escribe una pregunta y toca "Preguntar",
Then la acción se deshabilita con la pregunta en blanco, mientras corre se
muestra un estado de carga, y al terminar se muestra el **texto** de la respuesta
(sin listar ni navegar a Notas fuente); si no hay Notas indexadas se muestra un
mensaje explícito, y si el runtime falla se muestra un error con "Reintentar".

### Scenario 9: Ambas plataformas + gate honesto

Given los tests compartidos de `:core`,
When se corren `:core:testDebugUnitTest`, `:core:iosSimulatorArm64Test`,
`:composeApp:assembleDebug`, `:composeApp:linkDebugFrameworkIosSimulatorArm64`,
`xcodebuild` del scheme `iosApp` y `./init.sh`,
Then todo pasa en Android/JVM e iOS, `./init.sh` termina en 0 sin dev servers ni
simuladores, y el esquema sigue en **v4** (sin `.sqm` nuevos).

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `embeddings-generation` y
  `ai-runtime-decision` están `accepted`; `rag-query` es la feature elegida.
- `feature_list.json` — metadata de `rag-query` (diferenciador; runtime decidido);
  `rag-answer-citations` depende de esta feature.
- `docs/adr/0001-ai-runtime-decision.md` — estrategia híbrida por fases,
  cloud-first, detrás de `AiClient`; `rag-query` materializa `generate`; proveedor/
  modelo/dimensión/keys quedan como follow-up.
- `docs/domain-model.md`, `CONTEXT.md` — "Consulta" = pregunta resuelta sobre las
  Notas y sus embeddings, **no** desde conocimiento externo; "sin embedding no
  participa del RAG"; RAG vive sobre Notas indexadas.
- `docs/build-brief.md`, `docs/technical-discovery.md` — vectores BLOB +
  similitud coseno **en memoria**; proveedor LLM detrás de `AiClient`; tests sin
  red con `AiClient` fake.
- `docs/risks-and-open-questions.md` — adjuntos sin texto indexables (abierto, no
  bloquea: RAG sólo indexa `note.body` no vacío); el wiring de `NoteIndexingService`
  se declaró como deferred.
- `ARCHITECTURE.md` — dirección `:composeApp → :core`; secciones Domain/Embedding/
  AI/UI; **Deferred** dice que el wiring de `AiClient`/`NoteIndexingService` a la
  app seguía pendiente; el RAG lo realiza aquí.
- `docs/specs/embeddings-generation.md` y `.../ai-client-interface.md` — contratos
  del puerto (`embed`/`generate`), `NoteIndexingService`, `EmbeddingRepository`,
  tests compartidos por plataforma.
- `DESIGN.md` (provisional) — Core Screen 9 "Consulta RAG: pregunta + respuesta
  con citas"; aquí se entrega sólo pregunta + respuesta (citas = feature aparte);
  regla de accesibilidad "no sólo color".
- `core/src/commonMain/kotlin/com/playbook/core/ai/{AiClient,FakeAiClient,
  NoteIndexingService}.kt` — `GenerateRequest(prompt, context)`, `Embedding`,
  `AiClientException`; fake determinista; servicio `index(note)` → `IndexingResult`.
- `core/src/commonMain/kotlin/com/playbook/core/repository/{EmbeddingRepository,
  SqlDelightEmbeddingRepository,NoteRepository,SqlDelightNoteRepository}.kt` —
  `getAll()/getByNoteId()` de embeddings; `getAll()/getById()` de Notas.
- `core/src/commonMain/kotlin/com/playbook/core/model/{Note,NoteEmbedding}.kt` —
  `Note.body`/`id`; `NoteEmbedding(noteId, values, indexedAt)`.
- `core/src/commonMain/sqldelight/com/playbook/core/db/{Note,embedding}.sq` —
  esquema v4; `selectAllEmbeddings`, `selectAllNotes`.
- `core/src/commonTest/kotlin/com/playbook/core/ai/NoteIndexingCheck.kt` y
  `.../repository/EmbeddingRepositoryCheck.kt` — patrón de test compartido que
  recibe un `SqlDriver`, `createDatabase(...)`, repos con `idFactory`/`clock`
  deterministas y stubs de `AiClient` (recording/failing/stub).
- `composeApp/src/commonMain/kotlin/com/playbook/app/{App,NotesListScreen}.kt` —
  `App` hoistea estado y `NotesDestination`; `LaunchedEffect(refreshKey)`;
  `NotesListScreen` con acciones "Dictar nota" y FABs.
- `composeApp/src/{androidMain,iosMain}/kotlin/com/playbook/app/Main{Activity,
  ViewController}.kt` — composition root: crea DB + repos + voiceTranscriber y
  llama `App(...)`.
- `init.sh` — gate no bloqueante (assembleDebug + tests core + link iOS +
  iosSimulatorArm64Test).

### Existing Patterns To Follow

- Lógica de dominio, puertos y servicios en `:core`; UI y wiring en `:composeApp`;
  `:core` nunca depende de UI.
- Sin DI: dependencias por constructor; los composition roots construyen las
  implementaciones y las pasan a `App(...)`.
- Resultado sealed (`IndexingResult`) con `Failed(cause: AiClientException)`; los
  servicios `suspend` capturan `AiClientException` y no propagan.
- Tests compartidos en `commonTest` que reciben un `SqlDriver` y corren con `@Test`
  por plataforma (`JdbcSqliteDriver.IN_MEMORY` / `inMemoryDriver`), `runTest` para
  `suspend`; stubs de `AiClient` privados en el test.
- UI sin DI/`ViewModel`/Flow/navegación: estado de Compose + `sealed interface`
  de destinos; acciones por callbacks; estados comunicados con texto.
- Documentos en español, términos técnicos en inglés.

### Current Gaps

- No existe retrieval por similitud ni ninguna utilidad `cosineSimilarity`.
- `AiClient.generate` no tiene consumidor real (`NoteIndexingService` sólo usa
  `embed`); no existe un caso de uso RAG.
- `AiClient`/`NoteIndexingService` **no** están cableados a la app: no hay
  embeddings en el flujo del usuario.
- No hay pantalla de consulta ni acción en la lista.
- `:core` no tiene `kotlin.math` util de similitud; no hay tests de ranking.

## Technical Approach

### 1. Similitud coseno (`:core`, `com.playbook.core.ai`)

`CosineSimilarity.kt` con `fun cosineSimilarity(a: List<Float>, b: List<Float>):
Float` (puro Kotlin common, `kotlin.math.sqrt`):

- `require(a.size == b.size)`; el caller garantiza dimensiones compatibles.
- `dot / (||a|| * ||b||)`; si el denominador es `0f`, devuelve `0f` (evita `NaN`
  por vectores nulos). Se expone como utilidad reusable (la podrá consumir
  `semantic-linking`; aquí no se persiste ningún enlace).

### 2. Servicio RAG (`:core`, `com.playbook.core.ai.RagQueryService`)

```kotlin
data class RetrievedNote(val note: Note, val score: Float)

sealed interface RagAnswer {
    data class Answer(val text: String, val sources: List<RetrievedNote>) : RagAnswer
    data object NoIndexedNotes : RagAnswer
    data class Failed(val cause: AiClientException) : RagAnswer
}

class RagQueryService(
    private val aiClient: AiClient,
    private val noteRepository: NoteRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val maxSources: Int = DEFAULT_MAX_SOURCES, // 5
) {
    suspend fun ask(question: String): RagAnswer
}
```

`ask(question)`:

1. `require(question.isNotBlank())` (la UI deshabilita la acción con la pregunta
   vacía; el core es defensivo).
2. `embeddingRepository.getAll()`; si está vacío → `NoIndexedNotes` **sin** llamar
   al runtime.
3. `noteRepository.getAll().associateBy { it.id }`; se descartan embeddings sin
   Nota (defensivo) → si no queda ningún candidato → `NoIndexedNotes`.
4. `aiClient.embed(listOf(question.trim()))` (con `try/catch AiClientException` →
   `Failed`); si no devuelve exactamente 1 vector no vacío → `Failed`.
5. Score coseno de la pregunta contra cada candidato cuya dimensión coincida
   (los de dimensión distinta se descartan); ordenar por score **descendente** con
   desempate estable por `note.id`; tomar los primeros `maxSources`.
6. `context = top.map { it.note.body }`;
   `aiClient.generate(GenerateRequest(prompt = question.trim(), context))`
   (con `try/catch` → `Failed`).
7. `Answer(text = respuesta, sources = top)`.

Decisiones: **sin umbral** de similitud (se usa top-K: la calibración de umbral es
una pregunta abierta de `semantic-linking`); **sin vector DB** (BLOB + similitud
en memoria, `docs/technical-discovery.md`); el `prompt` es la pregunta y el
`context` los cuerpos (el adaptador formatea el contexto; el puerto ya declara
que `context` ancla la respuesta RAG).

### 3. Indexado best-effort al guardar (wiring en `:composeApp`)

`App(...)` recibe `noteIndexingService: NoteIndexingService` y
`ragQueryService: RagQueryService`. Tras crear/editar una Nota (texto, voz,
imagen o edición) y con body no vacío, se dispara
`scope.launch { noteIndexingService.index(note) }` (best-effort, sin bloquear ni
fallar el guardado; `NoteStatus` intacto). Es el mínimo glue para que el RAG vea
las Notas del usuario. El wiring de estados/reintento sigue siendo de
`note-enrichment-status-ui`/`offline-pending-retry`.

### 4. UI de consulta (`:composeApp/commonMain`)

- `AskNotesScreen.kt` (nueva): `OutlinedTextField` para la pregunta, botón
  "Preguntar" (deshabilitado si la pregunta está en blanco o mientras corre),
  estado de carga ("Pensando…"), `Surface`/`Card` con el **texto** de la
  respuesta, mensaje explícito de "sin notas indexadas", error con "Reintentar" y
  "Volver". Estados comunicados con texto (accesibilidad de `DESIGN.md`).
- `App.kt`: nuevo `NotesDestination.Ask`; estado `AskUiState`
  (`Idle`/`Asking`/`Answered(text)`/`NoIndexedNotes`/`Error`); botón "Preguntar"
  en `NotesListScreen`; `rememberCoroutineScope()` para invocar el servicio
  `suspend` (nuevo patrón justificado: las llamadas de IA son asíncronas; no se
  introduce `ViewModel`/Flow). `Answered` no muestra `sources`.
- `NotesListScreen.kt`: nueva acción "Preguntar" (`onAsk`).

### 5. Composition root

`MainActivity`/`MainViewController` construyen `SqlDelightEmbeddingRepository`,
`FakeAiClient()` (default; sin red ni keys), `NoteIndexingService(aiClient,
embeddingRepository)` y `RagQueryService(aiClient, noteRepository,
embeddingRepository)` y los pasan a `App(...)`. Sin cambios de esquema ni build.

## Expected File Changes

- `core/src/commonMain/kotlin/com/playbook/core/ai/CosineSimilarity.kt` — crear.
- `core/src/commonMain/kotlin/com/playbook/core/ai/RagQueryService.kt` — crear
  (`RetrievedNote`, `RagAnswer`, `RagQueryService`).
- `core/src/commonTest/kotlin/com/playbook/core/ai/RagQueryCheck.kt` — crear
  (helpers compartidos + stubs de `AiClient`).
- `core/src/androidUnitTest/kotlin/com/playbook/core/ai/RagQueryAndroidTest.kt` —
  crear; `@Test` JVM con `runTest`.
- `core/src/iosTest/kotlin/com/playbook/core/ai/RagQueryIosTest.kt` — crear;
  `@Test` iOS con `runTest`.
- `composeApp/src/commonMain/kotlin/com/playbook/app/AskNotesScreen.kt` — crear.
- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt` — modificar; params,
  `NotesDestination.Ask`, `AskUiState`, index-on-save y orquestación de la consulta.
- `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt` —
  modificar; acción "Preguntar".
- `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt` —
  modificar; embedding repo + `FakeAiClient` + servicios.
- `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt` —
  modificar; idem.
- `ARCHITECTURE.md`, `docs/technical-discovery.md`,
  `docs/risks-and-open-questions.md` — modificar; sección/decisiones RAG.
- `AGENTS.md` — modificar sólo la línea "Próxima feature en cola" al cierre.
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado y evidencia.

No se esperan cambios en el esquema SQLDelight (`Note.sq`/`embedding.sq`/`.sqm`),
`init.sh`, `gradle/libs.versions.toml` ni `composeApp/build.gradle.kts`.

## Visual Design Impact

- UI involved: yes.
- Design source: `DESIGN.md` (provisional) — Core Screen 9 "Consulta RAG" y la
  regla "no sólo color".
- Screens or states affected: nueva pantalla "Preguntar" (pregunta, cargando,
  respuesta, sin notas indexadas, error) y una acción "Preguntar" en la lista.
- New design artifact required: no — se reutilizan componentes Material 3
  existentes; sin tokens nuevos. Las **citas** visuales quedan para
  `rag-answer-citations`.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — nueva sección RAG (retrieval por similitud,
  `RagQueryService`, wiring de `AiClient`/`NoteIndexingService` a la app, pantalla
  de consulta); corrige el "Deferred" que decía que el wiring seguía pendiente.
- `CONSTRAINTS.md`: **not needed** — no surge un MUST/MUST NOT nuevo; la regla
  "el dominio consume sólo el puerto `AiClient`" ya está en `ARCHITECTURE.md`.
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola".
- Other docs: `docs/technical-discovery.md` — update ("RAG Decisions");
  `docs/risks-and-open-questions.md` — update (respuesta desde las Notas; runtime
  real y calidad de respuesta siguen abiertos); `docs/domain-model.md`/`CONTEXT.md`
  — not needed (la spec se alinea); `PROGRESS.md`/`feature_list.json` — update al
  cerrar.

## Implementation Plan

1. Crear `cosineSimilarity` y su test compartido (Scenario 5).
2. Crear `RagQueryService`/`RagAnswer`/`RetrievedNote` y los helpers de test
   (Scenarios 1–4, 6).
3. Escribir los `@Test` por plataforma (`runTest`) y correr `:core` en ambas.
4. Crear `AskNotesScreen.kt` y cablear `NotesListScreen` (`onAsk`) y `App`
   (`NotesDestination.Ask`, `AskUiState`, `rememberCoroutineScope`).
5. Cablear index-on-save y construir los servicios en `MainActivity`/
   `MainViewController`; `App(...)` recibe los nuevos params.
6. Actualizar docs durables; correr verificación, control negativo y smoke; registrar
   evidencia.

## Implementation Tasks

- [x] Crear `CosineSimilarity.kt` y `RagQueryCheck` (contrato de similitud).
- [x] Crear `RagQueryService.kt` (`RagAnswer`, `RetrievedNote`, `DEFAULT_MAX_SOURCES`).
- [x] Escribir los helpers compartidos con stubs de `AiClient` (recording/failing/stub).
- [x] Escribir `RagQueryAndroidTest`/`RagQueryIosTest` con `runTest`.
- [x] Crear `AskNotesScreen.kt`.
- [x] Modificar `NotesListScreen.kt` (acción "Preguntar").
- [x] Modificar `App.kt` (`NotesDestination.Ask`, `AskUiState`, consulta con `rememberCoroutineScope`, index-on-save).
- [x] Modificar `MainActivity.kt`/`MainViewController.kt` (embedding repo + `FakeAiClient` + servicios).
- [x] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`.
- [x] Correr la verificación + control negativo + smoke y registrar evidencia.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → `RagQueryAndroidTest` en
  verde (retrieval, no-indexed, fallo, respuesta inválida, similitud, ranking) +
  los previos (`NoteIndexing` 1/1, `EmbeddingRepository` 1/1, `AiClient` 3/3,
  `NotePersistence` 2/2, `NoteRepository` 1/1, `AttachmentRepository` 1/1,
  `Migration` 1/1, `Greeting` 1/1), 0 failures.
- `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` (macOS) → mismos tests en
  verde en iOS, 0 failures.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (UI + wiring Android).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL
  (UI + wiring iOS); `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp
  -configuration Debug -sdk iphonesimulator build` → BUILD SUCCEEDED.
- Confirmar esquema **v4** (`PlaybookDatabase.Schema.version == 4`), sin `.sqm`
  nuevos y sin cambios en `Note.sq`/`embedding.sq`.
- `./init.sh` → exit 0; gate no bloqueante, sin dev servers ni simuladores, sin
  cambios en `init.sh`.
- **Control negativo** (restaurar después): invertir el signo del `dot product`
  en `cosineSimilarity` debe romper el Scenario 5; forzar `ask` a generar sin
  recuperar fuentes debe romper el Scenario 2 (no debe llamarse `generate`).
- **Smoke Android** (emulador): crear una Nota con body → verificar una fila en
  `embedding`; abrir "Preguntar", preguntar → "Pensando…" → respuesta mostrada
  (texto de `FakeAiClient` con `context=N`, N ≥ 1) sin listar fuentes; estado con
  pregunta en blanco (acción deshabilitada); "Volver" a la lista.
- **Smoke iOS** (simulador): build/install/launch; OCR de la pantalla "Preguntar"
  y de una respuesta. La interacción de tecleo en iOS puede no ser automatizable
  (limitación histórica de tooling: sin `idb`/`cliclick`, `osascript` bloqueado);
  si no se puede, se declara honestamente y se ejerce el `commonMain` vía Android.
- E2E persistente: **no aplica** (no hay harness de UI/E2E y `init.sh` no levanta
  simuladores; justificado). El pipeline RAG se cubre con tests del core en dos
  plataformas; la UI con smoke manual.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest`/`:core:iosSimulatorArm64Test` con
  `RagQuery` en verde (nombres y conteos por plataforma).
- Salida de `assembleDebug`, link del framework iOS y `xcodebuild` del scheme.
- `./init.sh` → exit 0 sin procesos de larga duración.
- `Schema.version == 4`, sin `.sqm` nuevos ni cambios en `Note.sq`/`embedding.sq`.
- Control negativo reproducido y restaurado.
- Smoke (Android e iOS): fila `embedding` tras guardar, pantalla de consulta,
  respuesta mostrada sin fuentes, estado sin notas indexadas; limitación iOS
  declarada honestamente si aplica.
- Confirmación de que no se tocaron `init.sh`, `gradle/libs.versions.toml` ni
  `composeApp/build.gradle.kts` (sin dependencias nuevas).

## Validator Checklist

- [ ] Alcance respetado: retrieval + generación + pantalla de consulta; **sin**
      citas/navegación (`rag-answer-citations`), sin enlaces persistidos, sin
      estados/reintento de `NoteStatus`, sin adaptador real.
- [ ] Escenarios de aceptación 1–9 pasan.
- [ ] El RAG sólo responde desde Notas con embedding; sin embeddings devuelve
      `NoIndexedNotes` y **no** llama al runtime.
- [ ] La respuesta se genera con `GenerateRequest(prompt = pregunta, context =
      cuerpos recuperados)` y las fuentes van ordenadas por score (top-K).
- [ ] `cosineSimilarity` maneja vector nulo (`0f`, sin `NaN`) y dimensiones
      distintas (fail-fast); el control negativo rompe el test.
- [ ] El indexado al guardar es best-effort, no bloquea el guardado, no cambia
      `NoteStatus` y es silencioso ante fallo.
- [ ] La UI muestra sólo el texto de la respuesta (sin citas) y comunica los
      estados con texto; `rememberCoroutineScope` es el único patrón nuevo y no
      se introdujo `ViewModel`/Flow.
- [ ] Evidencia en `feature_list.json`/`PROGRESS.md`; limitación del runtime real
      (`FakeAiClient`) declarada honestamente.
- [ ] No hay E2E persistente y la spec justifica por qué.
- [ ] `ARCHITECTURE.md` y `docs/technical-discovery.md` actualizados; esquema v4;
      `init.sh` sin cambios, no bloqueante y sin procesos de larga duración.

## Open Questions

- **Runtime real**: la respuesta hoy es la de `FakeAiClient`; ¿qué proveedor/modelo
  cloud se elige y cómo se gestiona la key (ADR 0001)? La calidad de la respuesta
  no es verificable hasta entonces.
- **Indexado**: ¿el indexado best-effort al guardar debe migrarse a
  `offline-pending-retry`/`note-enrichment-status-ui` cuando existan (estados y
  reintento)? Aquí se deja explícitamente sin estados ni reintento.
- **Umbral de similitud**: hoy se usa top-K sin umbral; ¿la calibración de
  `semantic-linking` aplica también al RAG?
- **Adjuntos sin texto**: siguen fuera del RAG (`note.body` es la única fuente
  indexada); confirmar en `image-ocr-optional`/`voice-attachment-storage`.
