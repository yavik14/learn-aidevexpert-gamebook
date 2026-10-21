# Feature Implementation Spec: Sugerencias de contenido en el editor de Notas

## Source Feature

- `id`: `note-content-suggestions`
- `area`: `ai`
- `depends_on`: `["brainstorming-ai-suggestions", "create-text-note", "ai-fake-real-switch", "game-project-persistence", "genres-catalog-ui"]` (todas `accepted`)
- `status`: `not_started` (al planificar); `source`: `feature_list.json`

> **Alcance.** En **Nueva nota** y **Edición**, la acción **"Sugerir ideas"** pide
> a `AiClient.generate` **2 ideas de contenido** para la Nota y las muestra como
> tarjetas efímeras. El usuario decide con **"Añadir"** (anexa la idea al final del
> `body` como párrafo) o **"Ignorar"** (descarta esa tarjeta). No hay
> auto-guardado, no se persisten sugerencias y la clasificación existente
> ("Sugerir clasificación") queda intacta. Prompt, contexto, parser y helper de
> anexado viven en `:core` y se testean con la IA fake del gate.

## Goal

Que el autor pueda desbloquear una Nota pidiendo dos ideas concretas y decidir si
las incorpora al texto sin perder lo ya escrito. La IA usa como contexto el
`body`, el `Track`, los Géneros de la Nota y la ficha del Proyecto (título, core
loop, concepto, fantasía, mecánicas, etc.); funciona también con `body` vacío y
sin Proyecto. La sugerencia es efímera: sólo "Añadir" muta el borrador local y
sólo "Guardar" persiste.

## Non-Goals

- Cambiar "Sugerir clasificación", `ClassificationSuggestionService`, `JevClient`
  ni fusionar ambas acciones.
- Auto-guardar, persistir sugerencias (sin tabla/entidad/migración), historial,
  streaming, retries/backoff, cacheo o telemetría de contenido.
- Cambiar el esquema (sigue **v8**), `Note`/`NoteDraft`, repositorios, indexado,
  RAG, Enlaces, GDD o la navegación.
- Cambiar `AiRuntime`, `AiClient`, `AiPorts`, adaptadores OpenAI, el build flag
  fake/real o la API key.
- Rediseñar el editor, el wizard de Brainstorming o el picker de Géneros; agregar
  dependencias, DI/ViewModel/Flow o un harness UI/E2E.
- IA real en iOS (iOS congelado; sólo debe compilar) ni paridad de smoke iOS.

## Job Story

When estoy escribiendo o editando una Nota y me quedo sin contenido,
I want pedir dos ideas afines a mi Proyecto y anexar sólo las que me sirvan,
so I can avanzar sin perder el texto que ya tenía ni guardar algo sin revisarlo.

## Users And Permissions

- **Autor** (single-user local-first): pide, añade o ignora ideas; sin auth,
  cuentas ni permisos nuevos.
- **Desarrollador**: corre Gradle/tests y smoke Android con IA fake; sin red, key
  ni secrets.

## Scope Decisions

> Vinculantes para el implementer.

1. **Acción:** "Sugerir ideas" produce exactamente hasta **2 ideas de contenido**
   para la Nota; no es clasificación ni crea Notas.
2. **Convivencia:** "Sugerir clasificación" se conserva sin cambios. Ambas
   acciones tienen estado, `Job` y tarjetas independientes; usar una no limpia la
   otra.
3. **Dónde:** en `NoteEditorScreen`, en **Nueva nota** y **Edición**, debajo del
   cuerpo/dictado y **antes** de la sección de "Sugerir clasificación".
4. **Habilitación:** la acción está disponible **siempre** (también con `body`
   vacío), salvo mientras su propia petición está en curso. Con `body` vacío el
   contexto son Proyecto, `Track` y Géneros; con texto, además el `body`.
5. **Añadir:** anexa al final del `body` como párrafo separado por **una línea en
   blanco** (`body.trimEnd() + "\n\n" + idea`), preservando el texto existente.
   Quita **sólo** esa tarjeta y mantiene la otra si existe. **Nunca** guarda.
6. **Ignorar:** quita sólo esa tarjeta y no toca el `body`. Un pedido nuevo
   reemplaza el conjunto de tarjetas; no hay memoria de ignoradas.
7. **Efímeras:** no se persisten. Cambiar a mano `body`, `Track` o Géneros,
   guardar, cancelar o salir descarta las tarjetas restantes. Un resultado tardío
   nunca se aplica a un borrador ya cambiado.
8. **Parcial/inválida/error:** 1 idea usable → se muestra con aviso; 0 usables →
   estado de error con "Reintentar"; `AiClientException` → error con "Reintentar"
   sin romper la UI ni bloquear Guardar.
9. **Copy:** español de España (tuteo, sin voseo). La etiqueta final es
   **"Añadir"** (no "Agregar"), coherente con `spain-spanish-copy`, el wizard y
   `DESIGN.md`.
10. **Sin persistencia ni esquema:** no hay tabla, entidad, query ni `.sqm`
    nuevos. Las sugerencias no aparecen en la lista, GDD, RAG ni Enlaces.
11. **Fake determinista:** `FakeAiClient` devuelve 2 ideas parseables cuando el
    prompt contiene el marcador nuevo; el gate nunca usa red ni key.
12. **Sin auto-guardado:** añadir una idea sólo modifica el borrador local; el
    Guardar existente persiste el `body` final (con las ideas anexadas) y
    re-indexa por el camino ya existente.

## Acceptance Scenarios

### Scenario 1: body vacío con Proyecto

Given una Nueva nota con `body` vacío y un Proyecto cargado,
When se toca "Sugerir ideas",
Then se muestra "Sugiriendo ideas…" y luego 2 tarjetas; el `body` sigue vacío y
el contexto enviado incluye la ficha del Proyecto, el `Track` y los Géneros, sin
`owner`, ids ni timestamps.

### Scenario 2: añadir una idea y mantener la otra

Given 2 tarjetas y un `body` con texto previo,
When se toca "Añadir" en la primera,
Then el `body` queda `textoPrevio.trimEnd() + "\n\n" + idea` y sólo desaparece esa
tarjeta; la segunda sigue visible y no hubo escritura en la base.

### Scenario 3: añadir las dos ideas

Given el estado anterior, When se añade la segunda idea,
Then el `body` contiene los dos párrafos separados por una línea en blanco y la
sección vuelve a `Idle`; "Guardar" persiste ese `body` y relanzar la app lo
conserva.

### Scenario 4: ignorar

Given 2 tarjetas, When se toca "Ignorar" en una,
Then desaparece sólo esa tarjeta, la otra permanece y el `body` no cambia.

### Scenario 5: edición de una Nota existente

Given una Nota en Edición, When se sugiere, se añade una idea y se guarda,
Then la Nota persiste el `body` ampliado, `updatedAt` avanza y lista/GDD lo
reflejan. Si se Cancela o se sale sin guardar, la Nota conserva su `body` previo.

### Scenario 6: cambios manuales descartan

Given una sugerencia en carga o cargada, When se edita a mano el `body`, se cambia
el `Track` o se modifican los Géneros,
Then se cancela el `Job`, se ignoran resultados tardíos, se vuelve a `Idle` y no
se anexa nada.

### Scenario 7: guardar/cancelar/salir

Given tarjetas visibles, When se guarda, se cancela o se sale del editor,
Then las tarjetas se descartan y no se persiste ninguna sugerencia.

### Scenario 8: parcial, inválida y fallo

Given un cliente que devuelve 1 idea, ninguna usable o lanza `AiClientException`,
When se pide la sugerencia,
Then la parcial se muestra con "Playbook devolvió una sola idea…"; 0 usables y el
fallo muestran "No se pudieron obtener ideas…" + "Reintentar", sin tarjetas y sin
tocar el `body`. Guardar sigue habilitado.

### Scenario 9: parser robusto y dedupe

Given una respuesta con encabezados, fence, numeración, viñetas, `**negritas**`,
comillas, preámbulos o líneas repetidas con distinta capitalización/espacios,
When se parsea,
Then se obtienen hasta 2 ideas limpias, únicas y recortadas a 200 caracteres.

### Scenario 10: sin Proyecto

Given un Proyecto ausente y sólo `body`/`Track`/Géneros disponibles,
When se pide la sugerencia,
Then el servicio no falla, usa únicamente lo disponible (o contexto vacío si no
hay nada) y devuelve ideas.

### Scenario 11: convivencia con clasificación

Given una propuesta de clasificación visible,
When se pide "Sugerir ideas" y se añade una idea,
Then la propuesta de clasificación sigue visible y sin cambios; y viceversa,
confirmar la clasificación no limpia las tarjetas de contenido.

### Scenario 12: gate honesto

Given la implementación con IA fake,
When corren los comandos de verificación,
Then todo pasa sin red ni key, el esquema sigue en v8 sin `.sqm` nuevos y el gate
termina en `0`.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json` — Android-first, iOS congelado,
  gate Android-only con IA fake y dependencias `accepted`.
- `docs/specs/{brainstorming-ai-suggestions,ai-suggest-classification,transcribe-audio-attachment,spain-spanish-copy}.md`
  — patrón de servicio efímero en `:core`, estados de UI, copy es-ES y parser.
- `ARCHITECTURE.md`, `docs/technical-discovery.md`, `CONSTRAINTS.md`, `DESIGN.md`
  — regla "la IA sugiere y el usuario confirma", superficies de IA del editor y
  objetivos táctiles/AA.
- `core/.../ai/{AiClient,FakeAiClient,BrainstormingSuggestions,ClassificationSuggestions}.kt`
  — `GenerateRequest`, marcador del fake, parser de Brainstorming y estados de
  sugerencia de clasificación.
- `core/.../model/{GameProject,Genres,Track}.kt` — ficha del Proyecto, catálogo y
  `normalizeGenres`; `Note`/`NoteDraft` no cambian.
- `composeApp/.../{App,NoteEditorScreen,BrainstormingWizardScreen}.kt` — orden del
  editor, estados locales con `rememberCoroutineScope`, tarjetas del wizard,
  wiring por parámetro y los tres call sites del editor.
- `composeApp/src/{androidMain,iosMain}/.../Main{Activity,ViewController}.kt` —
  `App` ya recibe `aiClient`; no hace falta tocar los roots.

### Existing Patterns To Follow

- Caso de uso en `:core` que envuelve `AiClient` y devuelve un resultado `sealed`
  (`BrainstormingSuggestionService`); lógica pura y testeable en `commonTest` con
  runners Android/JVM e iOS.
- IA fake determinista en el gate; fallos vía `AiClientException`; sin logs con
  contenido del usuario.
- Estado de Compose local + `rememberCoroutineScope` + request-id para descartar
  resultados tardíos; sin DI/ViewModel/Flow.
- UI que comunica con texto + forma (no sólo color), botones ≥48dp y copy es-ES.

### Current Gaps

- No existe `NoteContentSuggestionService`, ni prompt/contexto/parser de contenido,
  ni rama del fake para este caso de uso.
- `NoteEditorScreen` sólo tiene sugerencia de clasificación; no recibe `project`
  ni un servicio de ideas de contenido.
- No hay tests del nuevo servicio/parser ni smoke de la acción.

## Technical Approach

### 1. Servicio y contrato en `:core`

Crear `core/src/commonMain/kotlin/com/playbook/core/ai/NoteContentSuggestions.kt`:

```kotlin
data class NoteContentSuggestion(val text: String)

sealed interface NoteContentSuggestionResult {
    data class Success(
        val suggestions: List<NoteContentSuggestion>,
        val partial: Boolean = false,
    ) : NoteContentSuggestionResult
    data object InvalidResponse : NoteContentSuggestionResult
    data class Failed(val cause: AiClientException) : NoteContentSuggestionResult
}

class NoteContentSuggestionService(private val aiClient: AiClient) {
    suspend fun suggest(
        body: String,
        track: Track?,
        genres: List<String>,
        project: GameProject?,
    ): NoteContentSuggestionResult
}

fun appendNoteContentSuggestion(body: String, suggestion: String): String
```

- **Prompt estable** (español de España, sin voseo), encabezado por el marcador
  `internal const val NOTE_CONTENT_PROMPT_MARKER = "PLAYBOOK_NOTE_CONTENT_SUGGESTIONS_V1"`:
  pide exactamente 2 ideas de contenido, una por línea, sin títulos, preámbulos,
  numeración, viñetas, negritas ni comillas; máximo ~200 caracteres por idea; no
  repetir el texto actual ni el contexto; ideas concretas para el Proyecto.
- **Llamada:** `aiClient.generate(GenerateRequest(prompt = prompt, context =
  contexto, temperature = 0.7, maxTokens = 300))`. No se agrega timeout propio:
  el `Job` de la UI se cancela al cambiar el borrador y Guardar nunca espera.
- **Contexto** (`GenerateRequest.context`), sólo valores no vacíos y en orden
  determinista, **sin** `owner`, `id`, `createdAt`, `updatedAt` ni ids de Nota:
  - Proyecto: `Título del Proyecto`, `Géneros del Proyecto`, `Core Loop`,
    `Audiencia`, `Concepto`, `Fantasía`, `Mecánicas clave` (`categoría ·
    descripción`), `Inspiraciones`, `Fuera de alcance`.
  - Nota: `Track de la Nota` (`track.code`), `Géneros de la Nota`
    (`normalizeGenres(...)` unidos por `; `) y `Texto actual de la Nota`.
  - Límites: máximo **12** entradas; valores de Proyecto/listas truncados a **300**
    caracteres; `body` truncado a **1200**; una línea `"<label>: <valor>"` por
    entrada. Sin Proyecto se usan sólo las entradas disponibles; sin contexto se
    llama igualmente al runtime (la acción está siempre disponible).
- **Parsing** (`internal fun parseNoteContentSuggestions(raw, body)`): normalizar
  `\r\n`/`\r`, quitar un fence de backticks envolvente; por línea quitar
  encabezados (`#`), blockquotes (`>`), viñetas (`-`/`*`/`•`/`—`), numeración
  (`1.`, `1)`, `(1)`), `**`/`__`/backticks, prefijo `Idea/Opción N:` y comillas;
  colapsar espacios; descartar vacías, separadores, ruido (`Ideas`,
  `Sugerencias`, `Opciones`), preámbulos (`aquí`, `claro`, `por supuesto`, `estas
  son`, `te propongo`, `te sugiero`) y líneas que terminan en `:` con ≤6 palabras.
  Deduplicar case-insensitive y descartar una idea idéntica al `body` normalizado;
  truncar a **200** caracteres; tomar las **primeras 2**.
- **Resultado:** 0 usables → `InvalidResponse`; 1 → `Success(partial = true)`;
  2 → `Success(partial = false)`. `AiClientException` → `Failed(cause)`;
  `CancellationException` se propaga. No persiste, no indexa, no diagnostica
  contenido.
- **Anexado** (`appendNoteContentSuggestion`): `idea.trim()` y `body.trimEnd()`;
  si el `body` queda vacío → la idea; si no → `"$body\n\n$idea"`. Una idea en
  blanco es no-op. Preserva el texto existente (sólo recorta el espacio final).

### 2. Parser: decisión de no reutilizar el de Brainstorming

El parser de `BrainstormingSuggestions.kt` es `private`, depende del `step` y del
`GameProjectDraft` (categorías de mecánicas, filtro contra el borrador) y su
contrato está aceptado y cubierto por tests. Extraerlo obligaría a tocar ese
archivo y arriesgar regresiones en los 9 pasos. Se implementa un parser **acotado
e independiente** en `NoteContentSuggestions.kt`, con tests propios; no se
modifican `BrainstormingSuggestions.kt`, el wizard ni sus tests. La duplicación
(≈60 líneas de limpieza de líneas) es una limitación aceptada y un refactor
compartido queda para una feature futura.

### 3. Fake determinista

En `FakeAiClient.generate`, después de la rama de Brainstorming, añadir:

```kotlin
if (request.prompt.contains(NOTE_CONTENT_PROMPT_MARKER)) {
    return "1. Añade un detalle sensorial que refuerce la fantasía del juego\n" +
        "2. Describe una variante del core loop para probar en la próxima iteración"
}
```

Así el gate y el smoke ejercitan 2 ideas parseables, sin red, `Random` ni key. La
rama de Brainstorming y el fallback `"[fake] …"` no cambian.

### 4. UI en `NoteEditorScreen`

Nuevos parámetros: `noteContentSuggestionService: NoteContentSuggestionService` y
`project: GameProject? = null`. Estado local independiente:

```kotlin
private sealed interface ContentSuggestionUiState {
    data object Idle : ContentSuggestionUiState
    data object Loading : ContentSuggestionUiState
    data class Loaded(
        val suggestions: List<NoteContentSuggestion>,
        val partial: Boolean,
    ) : ContentSuggestionUiState
    data object Error : ContentSuggestionUiState
}
```

Más `Job` y contador de request. Funciones: `requestContentSuggestions()`
(cancela la anterior, `Loading`, llama al servicio con `body.trim()`,
`selectedTrack`, `genres` y `project`, y descarta resultados tardíos),
`addContentSuggestion(...)`, `ignoreContentSuggestion(...)` y
`discardContentSuggestions()`.

- **Sección** inmediatamente antes de `ClassificationSuggestionSection`:
  - `Idle`: `OutlinedButton("Sugerir ideas")` habilitado (≥48dp).
  - `Loading`: botón deshabilitado "Sugiriendo ideas…" + `CircularProgressIndicator`
    (20dp) + texto "Playbook está pensando ideas…".
  - `Loaded`: botón "Sugerir ideas" (pide un conjunto nuevo y reemplaza) + aviso
    "Playbook devolvió una sola idea. Puedes pedir de nuevo." si `partial`; una
    `Surface` por idea (surfaceVariant, borde outline, shape medium) con el texto
    y `TextButton("Añadir")` + `TextButton("Ignorar")` (≥48dp).
  - `Error`: texto "No se pudieron obtener ideas. Inténtalo de nuevo." + botón
    "Reintentar".
- **Añadir:** `body = appendNoteContentSuggestion(body, idea.text)` y quitar sólo
  esa tarjeta; si no quedan, `Idle`. No guarda, no navega y no limpia la
  clasificación.
- **Ignorar:** quitar sólo esa tarjeta; si no quedan, `Idle`.
- **Descarte:** `onValueChange` del `body`, cambios de `Track`/Géneros y la
  aplicación de dictado/transcripción llaman a `discardContentSuggestions()`;
  Guardar y Cancelar también (además de `discardSuggestion()`). Aceptar una idea
  **no** descarta la clasificación (secciones independientes).
- Guardar nunca espera la IA y sigue habilitado durante carga/error.

### 5. Wiring

`App` crea `val noteContentSuggestionService = remember(aiClient) {
NoteContentSuggestionService(aiClient) }` y lo pasa a los tres
`NoteEditorScreen` (`Create`, `CreateWithImage`, `Edit`) junto con
`project = project` (el `GameProject?` ya cargado por `refreshKey`). No se tocan
`MainActivity`/`MainViewController`, `AiRuntime` ni los puertos.

## Expected File Changes

- `docs/specs/note-content-suggestions.md` — create (este spec).
- `core/.../ai/NoteContentSuggestions.kt` — create; tipos, prompt, contexto,
  parser, servicio y `appendNoteContentSuggestion`.
- `core/.../ai/FakeAiClient.kt` — modify; rama determinista del marcador nuevo.
- `core/src/commonTest/.../ai/NoteContentSuggestionCheck.kt` — create; checks con
  `FakeAiClient` y stubs.
- `core/src/{androidUnitTest,iosTest}/.../ai/NoteContentSuggestion*Test.kt` —
  create; runners `@Test`/`runTest`.
- `composeApp/.../NoteEditorScreen.kt` — modify; sección, estados, tarjetas,
  añadir/ignorar y descartes.
- `composeApp/.../App.kt` — modify; servicio recordado y `project` en los tres
  editores.
- Cierre: `ARCHITECTURE.md`, `docs/technical-discovery.md`, `DESIGN.md`,
  `CONSTRAINTS.md` (regla acotada), `docs/risks-and-open-questions.md`,
  `AGENTS.md` (sólo cola), `feature_list.json`, `PROGRESS.md`.

**Sin cambios:** `BrainstormingSuggestions.kt`/wizard, `ClassificationSuggestions.kt`/
sección de clasificación, `AiRuntime`/`AiPorts`/`OpenAi*`, `Note`/`NoteDraft`,
SQLDelight/`.sqm` (v8), `init.sh`, build files, `gradle/libs.versions.toml`,
`iosApp/**` ni `MainActivity`/`MainViewController`.

## Visual Design Impact

- UI involved: **yes**; fuente `DESIGN.md` (editor, superficies de IA, AA y ≥48dp).
- Afecta sólo `NoteEditorScreen` en creación/edición con estados `Idle`,
  `Loading`, `Loaded` (2 tarjetas o parcial) y `Error`.
- Nuevo artefacto: **no**; Material 3 existente, texto + borde + forma (no sólo
  color), `contentDescription` sólo si se agregan iconos (no se requieren).

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — servicio en `:core`, contexto/parser, rama del
  fake, sección del editor y wiring de `App`.
- `CONSTRAINTS.md`: **update acotado** — "Sugerir ideas" es explícita y efímera;
  sólo "Añadir" anexa al `body` y nunca hay auto-guardado ni persistencia.
- `docs/technical-discovery.md`: **update** — prompt/marcador, límites de
  contexto, parser, parcial/error/descarte y decisión de no reutilizar el parser.
- `DESIGN.md`: **update** — editor con "Sugerir ideas" y tarjetas Añadir/Ignorar.
- `docs/risks-and-open-questions.md`: **update** — calidad real del chat para
  contenido pendiente de smoke key-gated; parser heurístico.
- `AGENTS.md`: **update sólo al cierre** — próxima feature de la cola.
- `CONTEXT.md`, `docs/domain-model.md`: **not needed** — no hay entidad ni regla
  de dominio nueva; la sugerencia es UI/IA efímera.
- `feature_list.json`, `PROGRESS.md`: **update al cerrar** con evidencia.

## Implementation Plan

1. Crear tipos, prompt, contexto, parser, servicio y helper de anexado en `:core`.
2. Añadir la rama determinista a `FakeAiClient`.
3. Escribir `NoteContentSuggestionCheck` + runners Android/iOS y correr `:core`.
4. Implementar la sección y los descartes en `NoteEditorScreen`.
5. Cablear el servicio y `project` en `App` (tres editores).
6. Verificar, hacer smoke/controles negativos, actualizar docs y cerrar estado.

## Implementation Tasks

- [ ] `NoteContentSuggestions.kt`: tipos, marcador, prompt, contexto acotado,
      parser, servicio y `appendNoteContentSuggestion`.
- [ ] `FakeAiClient`: 2 ideas deterministas para el marcador nuevo.
- [ ] `NoteContentSuggestionCheck` + runners Android/iOS (feliz, fake, parcial,
      inválida, fallo, dedupe, límites, body vacío, sin Proyecto, truncado y
      anexado).
- [ ] `NoteEditorScreen`: sección antes de clasificación, estados, tarjetas,
      Añadir/Ignorar, Reintentar y descartes por cambios/guardar/cancelar.
- [ ] `App`: servicio recordado y `project` en los tres editores.
- [ ] Verificación, controles negativos y smoke Android.
- [ ] Docs durables y cierre de `feature_list.json`/`PROGRESS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest -Pplaybook.ai.mode=fake --rerun-tasks` →
  base vigente (≈36 suites/111 tests) + `NoteContentSuggestion*Test`, **0
  failures**, sin red ni key.
- `./gradlew :core:iosSimulatorArm64Test -Pplaybook.ai.mode=fake --rerun-tasks` →
  mismos tests, 0 failures (no bloqueante, macOS).
- `./gradlew :composeApp:assembleDebug -Pplaybook.ai.mode=fake --rerun-tasks` →
  BUILD SUCCESSFUL.
- `./gradlew :composeApp:testDebugUnitTest -Pplaybook.ai.mode=fake --rerun-tasks`
  → BUILD SUCCESSFUL.
- `CI=true ./init.sh` → exit 0 (Android-only, IA fake, sin dev servers).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 -Pplaybook.ai.mode=fake --rerun-tasks`
  → BUILD SUCCESSFUL (no bloqueante).
- `git diff`/`git status`: sin cambios en esquema/`.sqm`, `AiRuntime`, `AiPorts`,
  adaptadores OpenAI, `BrainstormingSuggestions.kt`, clasificación, build files,
  `init.sh`, `iosApp/**` ni secrets; `Schema.version == 8`.
- **Smoke Android** (si no hay dispositivo/emulador, declararlo):
  1. Nueva nota con Proyecto cargado y `body` vacío → "Sugerir ideas" habilitado,
     carga y 2 tarjetas; verificar que el `body` sigue vacío.
  2. "Añadir" una → párrafo anexado y la otra tarjeta permanece; "Ignorar" la
     restante → desaparece sin tocar el `body`.
  3. Pedir de nuevo y añadir dos → dos párrafos separados por línea en blanco.
  4. Editar a mano el `body`, el `Track` y un Género con tarjetas visibles → se
     descartan y "Sugerir ideas" vuelve a funcionar.
  5. Guardar → reabrir y relanzar: el `body` final persiste; volcar la base y
     comprobar que no hay tablas/filas de sugerencias.
  6. Edición de una Nota existente: añadir, cancelar y salir sin guardar → la Nota
     no cambia.
  7. Convivencia: pedir ambas sugerencias y comprobar que son independientes.
  8. Evidencia en `composeApp/build/smoke-evidence/note-content-suggestions/`.
- **Controles negativos** (restaurar byte-idéntico y re-verificar): (a) quitar la
  rama del fake → fallan los tests del fake; (b) anexar con `\n` o reemplazar el
  `body` → falla el test de anexado; (c) omitir el dedupe → falla el test de
  duplicados; (d) no descartar en cambios manuales → el smoke muestra tarjetas
  obsoletas.
- **E2E persistente: no aplica** — no hay harness UI/E2E e `init.sh` no levanta
  simuladores; la lógica se cubre con tests cross-platform y la UI con smoke
  manual Android.

## Accepted Limitations

- Parser heurístico: un modelo real puede ignorar el formato; parcial/inválida se
  cubren con stubs.
- La calidad real del texto no se verifica en el gate (fake); un smoke key-gated
  queda como follow-up opcional.
- Error/Invalid no son alcanzables con el fake de producción en el smoke; se
  cubren con tests del core.
- Sin timeout propio: un transporte lento puede demorar "Sugiriendo ideas…";
  Guardar nunca espera y editar/cancelar corta el `Job`.
- Duplicación deliberada del parser de limpieza respecto de Brainstorming.

## Evidence To Capture

- Salida de `:core` Android/JVM e iOS con el nuevo suite y 0 failures;
  `assembleDebug`, `:composeApp:testDebugUnitTest`, link iOS y `CI=true ./init.sh`
  exit 0.
- Diff acotado sin cambios en esquema/`.sqm` (v8), runtime/puertos, clasificación,
  build files, `init.sh` ni secrets; `FakeAiClient` sólo suma la rama nueva.
- Controles negativos reproducidos y restaurados.
- Smoke: capturas/UI dumps de `Idle`/`Loading`/2 tarjetas/parcial, añadir/ignorar,
  descarte por cambios, guardado, reapertura y DB sin sugerencias.
- `feature_list.json`/`PROGRESS.md` con evidencia; docs durables actualizados.

## Validator Checklist

- [ ] Sólo la acción explícita llama a `AiClient.generate`; nunca auto-guarda ni
      persiste sugerencias; Guardar no se bloquea por la IA.
- [ ] "Sugerir ideas" está disponible con `body` vacío y convive sin cambios con
      "Sugerir clasificación" (estados/jobs independientes).
- [ ] "Añadir" anexa un párrafo separado por línea en blanco y preserva el texto;
      "Ignorar" sólo quita esa tarjeta.
- [ ] Máximo 2 ideas, dedupe, parcial con aviso, 0 usables como error y fallo de
      `AiClient` como error con "Reintentar" sin romper la UI.
- [ ] Contexto acotado (12 entradas/300/1200), sin `owner`, ids ni timestamps; sin
      Proyecto usa sólo lo disponible.
- [ ] Cambios manuales de `body`/`Track`/Géneros y Guardar/Cancelar/salir
      descartan las tarjetas; resultados tardíos ignorados.
- [ ] Etiqueta final "Añadir"; copy es-ES; objetivos ≥48dp y estados textuales.
- [ ] Tests del core con IA fake determinista, gate exit 0, esquema v8 sin `.sqm`,
      iOS compila y no hay cambios en paths protegidos.
- [ ] Docs durables y `feature_list.json`/`PROGRESS.md` actualizados; no hay E2E
      persistente y la spec lo justifica.
