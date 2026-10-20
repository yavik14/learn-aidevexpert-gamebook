# Feature Implementation Spec: Sugerencia de clasificación (Track/Género) con `jev`

## Source Feature

- `id`: `ai-suggest-classification` · `area`: `ai`
- `depends_on`: `["ai-fake-real-switch", "genres-catalog-ui"]` (ambas `accepted`)
- `status`: `not_started` (al planificar); `source`: `feature_list.json`

> **Alcance.** La IA **propone** `Track` y Géneros desde el texto de una Nota con `JevClient.suggest(text)`. El usuario **confirma o edita** y sólo Guardar persiste. No hay auto-asignación, persistencia de la propuesta ni cambios de esquema/indexado/creación de Notas.

## Goal

Que desde el editor (Nueva nota y Edición) el usuario pida una sugerencia, la vea como propuesta efímera, la confirme o descarte, y ajuste `Track`/Géneros con los controles existentes antes de guardar. Usa el `JevClient` vigente (fake determinista por defecto; real en debug), normaliza contra el catálogo y nunca bloquea el guardado si falla o demora.

## Non-Goals

- Clasificación automática al escribir/guardar/indexar; múltiples propuestas; aceptar Géneros de a uno; Categoría/Tipo/Nivel.
- Persistir la sugerencia, cambiar `Note`/`NoteDraft`, `NoteStatus`, indexado, RAG, Enlaces o creación/edición de Notas.
- Modificar `AiRuntime`, `AiPorts.kt`, `OpenAiJevClient` ni el proveedor real; de `FakeAiPorts.kt` sólo se ajusta `FakeJevClient` para devolver un Género del catálogo. Usar red/API key en tests o gate.
- Visión/OCR, chat generativo, rediseñar editor/`GenrePicker`/`Track`, agregar dependencias, DI/ViewModel/Flow o navegación.
- Paridad o smoke iOS (congelado; sólo debe compilar).

## Job Story

When capturo o edito una Nota y no quiero decidir de memoria su clasificación, I want pedir una propuesta a la IA y revisarla, so I can clasificar más rápido sin perder el control ni guardar algo que no confirmé.

## Users And Permissions

- Autor único local-first: pide, confirma/edita o descarta; sin auth ni permisos nuevos. Desarrollador: corre Gradle/tests y smoke manual Android.

## Scope Decisions

> **Resueltas en esta planificación (2026-10-20). Vinculantes.**

1. **Dónde:** en `NoteEditorScreen`, en **Nueva nota** y **Edición**, debajo del cuerpo/dictado y antes de `Track`. Botón **"Sugerir clasificación"**; acción explícita, nunca automática.
2. **Habilitación:** `body.isNotBlank()` y no está cargando. Una Nota sólo-imagen no sugiere. Guardar sigue habilitado durante carga/error: la IA no lo bloquea.
3. **Estados:** `Idle`, `Loading` ("Sugiriendo…" deshabilitado), `Ready`, `Empty` y `Error`. `Error` cubre `AiClientException` y timeout, sin detalles técnicos ni contenido del usuario.
4. **Timeout:** `ClassificationSuggestionService` usa **20 s** (`withTimeout`, inyectable en tests) además de los timeouts del transporte. Timeout o fallo → `Failed`, sin excepción hacia la UI. Es un tope de espera de la UI: como el transporte Android es bloqueante, la cancelación del I/O puede demorar hasta su `readTimeout`; Guardar nunca espera la sugerencia.
5. **Confirmación:** "Confirmar" aplica al borrador local y cierra la tarjeta; **no guarda, no navega, no persiste**. "Descartar" limpia sin cambios. Luego se edita con `Track`/`GenrePicker` y recién Guardar persiste.
6. **Aplicación:** `Track` sugerido no nulo **reemplaza** al actual; `null` conserva el actual. Géneros se **fusionan** normalizados con los elegidos (unión): no se quitan Géneros del usuario ni se duplican. Es idempotente.
7. **Edición manual:** cambiar `body`, `track` o Géneros descarta la propuesta y cancela la carga; "Confirmar" nunca pisa una edición manual posterior.
8. **Formato:** tarjeta "Sugerencia de la IA" con `track.code` (o "Sin track") y Géneros como `GenreLabelChip` (catálogo vs personalizado); si no hay, "Sin géneros sugeridos". No se muestra el prompt ni texto crudo del proveedor.
9. **Parsing/normalización:** el puerto devuelve `ClassificationSuggestion` tipada; el adaptador real ya mapea `none`/desconocidos a `null`/vacío. El consumidor aplica `trim`, descarta vacíos, canoniza contra `GenreCatalog` con `normalizeGenres`, deduplica y ordena. Un valor fuera del catálogo queda como personalizado. Sin `Track` ni Géneros usables → `Empty`.
10. **Fake:** `FakeJevClient` se ajusta para devolver `Track` y un Género **de `GenreCatalog.GENRES`** deterministas por hash del texto (sin red). Así el smoke ejercita el chip de catálogo; el camino personalizado se cubre con un stub en tests.

## Acceptance Scenarios

### Scenario 1: cuerpo vacío
Given el editor con `body` vacío, When se renderiza, Then "Sugerir clasificación" está deshabilitado y no hay tarjeta.

### Scenario 2: sugerencia feliz sin auto-asignar
Given una Nueva nota con un body no vacío, When se toca "Sugerir clasificación", Then se ve "Sugiriendo…" y luego una tarjeta con el `Track` y un Género del catálogo, ambos deterministas; And `Track`/Géneros/`Note` no cambiaron y no hubo escritura en la base.

### Scenario 3: confirmar, editar y guardar
Given la tarjeta anterior, When se toca "Confirmar", Then el selector y `GenrePicker` reflejan la propuesta y la tarjeta desaparece. When se quita el Género sugerido, se agrega `"Zelda-like"` como personalizado y se Guarda, Then la Nota persiste con ese `Track` y Géneros y sobrevive al relanzar.

### Scenario 4: descartar y fallo no bloqueante
Given una sugerencia visible, When se toca "Descartar", Then no cambia ningún control ni la base. Given un cliente que lanza `AiClientException` o supera el timeout, When se sugiere, Then hay error textual con "Reintentar"/"Descartar", Guardar sigue habilitado y la Nota puede guardarse sin clasificación.

### Scenario 5: propuesta vacía
Given un cliente que devuelve `ClassificationSuggestion(null, emptyList())` o sólo strings en blanco, When se sugiere, Then se muestra "La IA no propuso una clasificación." sin confirmar; Descartar limpia el estado.

### Scenario 6: clasificación previa del usuario
Given `track = MECHANICS` y Géneros `["Puzzle"]`, When la sugerencia es `track = STORY` y `[" jrpg ", "Puzzle", "JRPG", "  "]` y se confirma, Then el `Track` pasa a `STORY` y los Géneros quedan `["JRPG", "Puzzle"]` (unión normalizada, sin duplicados ni pérdida de `Puzzle`).

### Scenario 7: editar Nota existente
Given una Nota en Edición, When se sugiere, confirma y guarda, Then persiste la clasificación confirmada, `updatedAt` avanza y lista/GDD la reflejan; When se descarta, Then la Nota queda intacta.

### Scenario 8: descarte por cambios
Given una sugerencia en carga o cargada, When cambia el `body`, el `track` o los Géneros a mano, Then se cancela/ignora el resultado tardío, se vuelve a `Idle` y no se confirma una propuesta vieja.

### Scenario 9: sin persistencia de la propuesta
Given una sugerencia mostrada o confirmada sin Guardar, When se inspecciona la base o se sale del editor, Then no hay filas nuevas ni cambios; la propuesta es estado de UI efímero.

### Scenario 10: gate y disciplina
Given la implementación, When corren `:core:testDebugUnitTest`, `:composeApp:assembleDebug` y `CI=true ./init.sh`, Then todo pasa sin red con IA fake, `init.sh` sale `0`, iOS compila y no hay cambios de esquema, `.sqm`, `AiRuntime`, puertos/fakes, build files ni secrets.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `docs/mvp-execution-plan.md` — Android-first, iOS congelado, gate Android-only, WIP=1.
- `CONTEXT.md`, `CONSTRAINTS.md`, `DESIGN.md` — `Track` vs Género, regla durable "la IA sugiere y el usuario confirma", superficie de sugerencia.
- `docs/specs/{ai-client-ports,ai-fake-real-switch,genres-catalog-ui,brainstorming-ai-suggestions}.md` — contrato `JevClient.suggest`, `AiRuntime`, `GenrePicker` y patrón de IA efímera con estados carga/error/resultado.
- `docs/technical-discovery.md`, `docs/risks-and-open-questions.md` — fake/real, adaptadores y riesgo "sin clasificación IA".
- `core/.../ai/{AiPorts,FakeAiPorts}.kt`, `openai/{AiRuntime,OpenAiAdapters}.kt` — contrato tipado, fake determinista y parseo real (`OpenAiJevClient`).
- `core/.../model/{Genres,Track,Note}.kt` — catálogo, `normalizeGenres`, `Track.code`/`fromCode`, `Note`/`NoteDraft`.
- `composeApp/.../{NoteEditorScreen,GenrePicker,LabelChip,App}.kt` y roots Android/iOS — editor, chips, wiring y composition roots.
- `core/src/{commonTest,androidUnitTest,iosTest}/.../ai/` — checks compartidos + runners Android/JVM e iOS (`FakeAiClient`, stubs, `runTest`).

### Existing Patterns To Follow

- Servicio de caso de uso en `:core` que envuelve un puerto y devuelve un resultado sealed (`BrainstormingSuggestionService`); lógica pura testeable.
- IA fake determinista en gate; fallos vía `AiClientException`; sin logs con contenido del usuario.
- Estado de Compose local + `rememberCoroutineScope` (sin DI/ViewModel/Flow).
- Selección con texto + forma + color (no sólo color); `GenreLabelChip` para catálogo vs personalizado.

### Current Gaps

- `JevClient` no tiene consumidor de UI; `AiRuntime.jevClient` no llega a `App`.
- No existe servicio de normalización/aplicación ni estados de sugerencia.
- No hay tests de sugerencia de clasificación (sólo contratos de puertos).

## Technical Approach

### 1. Servicio puro en `:core`

Crear `core/src/commonMain/kotlin/com/playbook/core/ai/ClassificationSuggestions.kt`:

```kotlin
const val CLASSIFICATION_SUGGESTION_TIMEOUT_MILLIS: Long = 20_000
sealed interface ClassificationSuggestionResult {
    data class Success(val suggestion: ClassificationSuggestion) : ClassificationSuggestionResult
    data object Empty : ClassificationSuggestionResult
    data class Failed(val cause: Throwable?) : ClassificationSuggestionResult
}
class ClassificationSuggestionService(
    private val jevClient: JevClient,
    private val timeoutMillis: Long = CLASSIFICATION_SUGGESTION_TIMEOUT_MILLIS,
) { suspend fun suggest(text: String): ClassificationSuggestionResult }
data class AppliedClassification(val track: Track?, val genres: List<String>)
fun applyClassificationSuggestion(currentTrack: Track?, currentGenres: List<String>, suggestion: ClassificationSuggestion): AppliedClassification
```

- `suggest`: `trim`; si queda vacío → `Empty` **sin llamar** al cliente. Si no, `withTimeout(timeoutMillis) { jevClient.suggest(body) }`; normaliza Géneros con `normalizeGenres`; sin `Track` ni Géneros usables → `Empty`; si no, `Success`. `AiClientException`/`TimeoutCancellationException` → `Failed`; una cancelación del llamador se propaga.
- `apply`: `track = suggestion.track ?: currentTrack`; `genres = normalizeGenres(currentGenres + suggestion.genres)`. Pura, sin repositorios.
- No loguear `body` ni la propuesta; no tocar `NoteStatus`, indexado ni esquema.

### 2. Fake `jev`

En `FakeAiPorts.kt`, `FakeJevClient.suggest(text)` devuelve `Track` y
`GenreCatalog.GENRES[hash.mod(size)]` usando el mismo `text.hashCode()` para
ambos; elimina `GENRE_HINT` y actualiza el KDoc. Sigue determinista, sin red y
sin `Random`; el contrato `verifyJevClientContract` sigue pasando.

### 3. Editor

En `NoteEditorScreen.kt`, parámetro `classificationSuggestionService: ClassificationSuggestionService` y estado local `SuggestionUiState` (`Idle`/`Loading`/`Ready`/`Empty`/`Error`) + `Job`.

- Bajo el cuerpo (y dictado), antes de `Track`: `OutlinedButton` "Sugerir clasificación" (`enabled = body.isNotBlank() && state != Loading`, `defaultMinSize(minHeight = 48.dp)`).
- `Loading`: "Sugiriendo…" deshabilitado. `Ready`: tarjeta con `Track`, chips `GenreLabelChip`, "Confirmar" (aplica y vuelve a `Idle`) y "Descartar".
- `Empty`: "La IA no propuso una clasificación." + "Descartar". `Error`: "No se pudo obtener la sugerencia." + "Reintentar" (body actual) + "Descartar".
- Cambios manuales de `body`/`selectedTrack`/`genres` cancelan el `Job` y vuelven a `Idle`; al salir de composición el `rememberCoroutineScope` cancela el trabajo. Guardar no espera la sugerencia.
- El editor sigue sin persistir; `onSave` conserva su firma.

### 4. Wiring

- `App` recibe `jevClient: JevClient`, crea `remember(jevClient) { ClassificationSuggestionService(jevClient) }` y la pasa a los tres `NoteEditorScreen` (Create, CreateWithImage, Edit).
- `MainActivity` pasa `aiRuntime.jevClient`; `MainViewController` pasa `runtime.jevClient` (iOS sigue `fakeAiRuntime()`). `AiRuntime` no cambia.

## Expected File Changes

- `docs/specs/ai-suggest-classification.md` — **create** (este spec).
- `core/.../ai/ClassificationSuggestions.kt` — **create**; servicio, resultado, timeout y aplicación pura.
- `core/.../ai/FakeAiPorts.kt` — **modify**; `FakeJevClient` devuelve un Género del catálogo determinista por hash (sin red).
- `core/src/commonTest/.../ai/ClassificationSuggestionCheck.kt` — **create**; 6 checks con fake (incluye Género ∈ catálogo y determinismo) y stubs test-only.
- `core/src/{androidUnitTest,iosTest}/.../ai/ClassificationSuggestion*Test.kt` — **create**; runners `@Test`/`runTest`.
- `composeApp/.../NoteEditorScreen.kt` — **modify**; acción, estados, tarjeta y aplicación al borrador.
- `composeApp/.../App.kt` — **modify**; `jevClient`, servicio recordado y paso a los editores.
- `composeApp/src/{androidMain,iosMain}/.../Main{Activity,ViewController}.kt` — **modify**; pasar `runtime.jevClient`.
- Cierre: `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `CONSTRAINTS.md` (corregir "vía `AiClient`" → `JevClient`), `AGENTS.md` (sólo cola), `feature_list.json`, `PROGRESS.md`.

**Sin cambios:** `AiRuntime.kt`, `AiPorts.kt`, `OpenAiAdapters.kt`, `Note`/`NoteDraft`, SQLDelight/`.sqm`, `init.sh`, `iosApp/**`, build files, `gradle/libs.versions.toml` ni dependencias. En `FakeAiPorts.kt` el único cambio permitido es `FakeJevClient`.

## Visual Design Impact

- UI involved: **yes**; fuente `DESIGN.md` (sugerencia = chips + Confirmar/Editar; "no sólo color").
- Afecta `NoteEditorScreen` en creación/edición con estados idle/loading/ready/empty/error. "Editar" se cumple con `Track`/`GenrePicker` tras confirmar; no hay modo nuevo.
- Nuevo artefacto: **no**; Material 3, textos explícitos, chips con texto y controles ≥48.dp.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — servicio de sugerencia y wiring `jevClient`.
- `CONSTRAINTS.md`: **update acotado** — sugerencia vía `JevClient` y confirmación explícita.
- `AGENTS.md`: **update acotado al cierre** — sólo la próxima feature.
- `docs/technical-discovery.md`: **update** — timeout, normalización, reemplazo de Track / unión de Géneros, ausencia de persistencia y ajuste de `FakeJevClient` al catálogo.
- `docs/risks-and-open-questions.md`: **update** — riesgo "sin clasificación IA" pasa a "calidad real de `jev` pendiente de smoke key-gated".
- `DESIGN.md`, `CONTEXT.md`, `docs/domain-model.md`: **not needed**.
- `feature_list.json`, `PROGRESS.md`: **update al cerrar** con evidencia.

## Implementation Plan

1. Crear servicio + aplicación pura en `:core`, ajustar `FakeJevClient` al catálogo, checks y runners.
2. Correr `:core:testDebugUnitTest` (fake, sin red) y `:core:iosSimulatorArm64Test`.
3. Agregar estado/acción/tarjeta en `NoteEditorScreen`.
4. Cablear `jevClient` en `App` y roots Android/iOS.
5. Compilar, `CI=true ./init.sh`, smoke Android y controles negativos.
6. Docs durables y cierre de `feature_list.json`/`PROGRESS.md`.

## Implementation Tasks

- [ ] `ClassificationSuggestions.kt`: resultado, servicio con timeout, normalización y `applyClassificationSuggestion`.
- [ ] `FakeJevClient`: Género de `GenreCatalog.GENRES` determinista por hash; sin `GENRE_HINT`.
- [ ] `ClassificationSuggestionCheck.kt` + runners Android/iOS (6 tests).
- [ ] `NoteEditorScreen`: sección, estados, tarjeta, confirmar/descartar, reintentar y descarte por cambios manuales.
- [ ] `App` + roots: `jevClient` y servicio recordado.
- [ ] Verificación, controles negativos y smoke Android.
- [ ] Docs durables y cierre de `feature_list.json`/`PROGRESS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → **31 suites / 96 tests** esperados sobre la base rebasada en `develop` (`9c75be4`: 30/90, ya incluye `GddDocument*Test`) + 6 nuevos, 0 failures, sin red. Si otra feature se integra antes del cierre, el total sube: el contrato es "base vigente + `ClassificationSuggestion*Test` 6/6, 0 failures".
- `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → mismos conteos, 0 failures (iOS no bloqueante, macOS).
- `./gradlew :composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL; `CI=true ./init.sh` → exit 0 (Android-only, IA fake, sin dev servers).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
- `git diff`/`git status`: sin cambios en `AiRuntime.kt`, `AiPorts.kt`, `OpenAiAdapters.kt`, esquema/`.sqm`, build files, `init.sh`, `iosApp/**` ni secrets; en `FakeAiPorts.kt` sólo cambia `FakeJevClient`; `Schema.version` sigue en v8.
- **Controles negativos** (restaurar byte-idéntico y re-verificar): (a) omitir `normalizeGenres` → falla la normalización; (b) `apply` reemplaza Géneros en vez de fusionarlos → falla la aplicación; (c) quitar el catch del timeout → falla el test de timeout.
- **Smoke Android** (`emulator-5556`; `adb` en `~/Library/Android/sdk/platform-tools/adb`):
  1. Instalar/lanzar; Nueva nota sin body → botón deshabilitado.
  2. Escribir body, sugerir, ver "Sugiriendo…" y la tarjeta (Track + Género del catálogo deterministas); verificar en DB que no se creó/modificó ninguna Nota.
  3. Confirmar; quitar el Género sugerido, agregar `"Zelda-like"`, Guardar; reabrir y relanzar.
  4. Editar Nota existente: sugerir y Descartar → sin cambios; Guardar sin sugerencia sigue funcionando.
  5. Cambiar el body con sugerencia visible → la tarjeta desaparece.
  6. Capturar UI dumps/capturas y chequeo SQL en `composeApp/build/smoke-evidence/ai-suggest-classification/`.
  `Empty`/`Error` no son alcanzables con el fake de producción: quedan cubiertos por tests del core (declarado honestamente).
- **E2E persistente: no aplica** — no hay harness UI/E2E e `init.sh` no levanta simuladores; lógica en tests cross-platform y UI en smoke manual Android.

## Accepted Limitations

- El fake devuelve un Género de catálogo determinista; el camino personalizado sólo se cubre con un stub en tests.
- `Empty`/`Error` no son alcanzables en el smoke con el fake de producción; se cubren con tests del core.
- El timeout de 20 s es best-effort: el I/O bloqueante del transporte Android puede demorar hasta su `readTimeout`; Guardar nunca espera.
- No hay harness UI/E2E; la UI se valida con smoke manual Android.
- La calidad real de `jev` queda como smoke key-gated follow-up, fuera del gate.

## Evidence To Capture

- Tests de `:core` en Android/JVM e iOS con el nuevo suite y 0 failures; `assembleDebug` y `CI=true ./init.sh` exit 0.
- Diff acotado sin cambios en runtime/`AiPorts`/adaptador real/esquema/build files/`init.sh` ni secrets; `FakeJevClient` devuelve un Género del catálogo determinista; `Schema.version == 8`.
- Controles negativos (a)/(b)/(c) reproducidos y restaurados.
- Smoke: idle/loading/ready/descartar, confirmar, edición manual, persistencia tras relanzar y DB intacta antes de Guardar, con capturas/UI dumps.
- `feature_list.json`/`PROGRESS.md` con evidencia; docs durables actualizados.

## Validator Checklist

- [ ] Sólo la acción explícita llama a `JevClient.suggest`; nunca auto-asigna ni persiste la propuesta; Guardar no se bloquea por la IA.
- [ ] Botón habilitado sólo con body no vacío; loading/ready/empty/error visibles y accionables; timeout/fallo no lanzan hacia la UI.
- [ ] `Track` sugerido reemplaza sólo al confirmar; `null` conserva el actual; Géneros se fusionan normalizados sin duplicados ni pérdidas.
- [ ] Normalización: trim, vacíos descartados, catálogo canonizado, personalizado preservado, orden/dedupe de `normalizeGenres`.
- [ ] Cambios de body/track/Géneros o salida del editor descartan/cancelan la propuesta; resultados tardíos ignorados.
- [ ] Escenarios 1–10 pasan; `:core` 31/96 esperados (o base vigente + 6 tests nuevos), build, gate e iOS link en verde.
- [ ] Sin cambios en runtime/`AiPorts`/adaptador real/esquema/indexado/creación de Notas/build files/`init.sh`/secrets; en `FakeAiPorts.kt` sólo cambia `FakeJevClient`.
- [ ] Smoke Android registrado honestamente (limitación de error/empty).
- [ ] `feature_list.json`/`PROGRESS.md` actualizados; docs durables; `AGENTS.md` sólo la cola.
