# Feature Implementation Spec: Sugerencias de Brainstorming (2 por paso)

## Source Feature

- `id`: `brainstorming-ai-suggestions` · `area`: `ai` · `status`: `not_started`
- `depends_on`: `["brainstorming-wizard", "ai-client-interface"]` (ambas `accepted`)
- `source`: `feature_list.json`

## Goal

Agregar al wizard de 9 pasos un botón **Sugerir** que pida a `AiClient.generate` **2 ideas** para el paso actual y las muestre como tarjetas efímeras. El usuario decide: **Usar/Agregar** una idea al borrador local, **Ignorar** la tarjeta, o seguir escribiendo a mano. La IA nunca auto-rellena, nunca guarda y nunca bloquea la escritura ni el guardado del Proyecto. Prompt, contexto y parsing viven en `:core` y se testean con la IA fake del gate (determinista, sin red). Con el runtime real el mismo servicio funciona sin cambios; la integración real y su calidad quedan para `ai-real-chat`.

## Non-Goals

- **IA real / proveedor / key / flag fake-real**: lo cubre `ai-real-chat`.
- **Persistir sugerencias** (sin tabla/entidad/repositorio/migración) ni **crear Notas** automáticamente (`brainstorming-note-per-step`).
- Cambiar esquema (sigue v8), `GameProject`/`GameProjectDraft`, repositorio ni validación; rediseñar wizard/stepper/navegación/guardado ni topes 3/3/5.
- Imágenes/audio, TTS, transcripción, clasificación, Q&A, streaming, reintentos/backoff, cacheo, telemetría, costo/cuota o prompt hardening más allá de tratar la respuesta como texto plano.
- Tests UI/E2E persistentes: no hay harness; se usa smoke manual Android.

## Job Story

When estoy en un paso del Brainstorming y me quedo sin ideas,
I want pedir 2 propuestas y decidir cuáles incorporar o descartar,
so I can avanzar sin perder el control de la ficha ni de lo ya escrito.

## Users And Permissions

- **Autor** (single-user local-first): pide, acepta, edita o ignora sugerencias; sin auth, permisos ni cuentas nuevas.
- **Desarrollador**: Gradle/tests y smoke Android con IA fake; sin red, key ni secrets.

## Scope Decisions

> Vinculantes para el implementer.

1. **Los 9 pasos**: Título, Género, Core Loop, Audiencia, Concepto, Inspiraciones, Fantasía, Mecánicas clave y Fuera de alcance.
2. **Máximo 2 tarjetas** por pedido; uno nuevo **reemplaza** las anteriores.
3. **Aceptar aplica al control existente**: escalares reemplazan el campo; listas agregan una fila (o completan la primera vacía) si no hay tope; Género agrega el chip; Mecánicas agregan categoría + descripción. Luego se edita en el campo/fila normal, sin editor modal.
4. **Ignorar** quita sólo esa tarjeta y no toca el borrador. Un pedido nuevo puede repetir el texto (no hay memoria de ignoradas).
5. **Efímeras**: se descartan al cambiar de paso, Cancelar, Guardar, recargar `initialProject` o salir del wizard; no sobreviven a navegación ni reapertura.
6. **La escritura manual manda**: campos, Atrás/Siguiente y Guardar siguen habilitados durante la carga; nunca hay auto-avance ni auto-guardado.
7. **Parcial**: 1 idea usable → se muestra con aviso no bloqueante; 0 → error sin modificar nada.
8. **Sin duplicados**: dentro de la respuesta y contra el borrador actual (case-insensitive, espacios normalizados). Si todo ya está, se informa y no se muestran tarjetas.
9. **Sin persistencia ni Notas**: aceptar sólo muta estado local; el borrador se persiste únicamente con el "Guardar" existente.

## Acceptance Scenarios

### Scenario 1: texto escalar
Given el paso Título con Géneros y Concepto cargados,
When el usuario toca "Sugerir",
Then aparece "Sugiriendo…" sin deshabilitar el campo y al responder se muestran como máximo 2 ideas; "Usar" copia una al Título y la tarjeta desaparece.

### Scenario 2: nunca auto-rellena
Given cualquier paso, When se toca "Sugerir" y llega la respuesta,
Then el borrador no cambia hasta Usar/Agregar; el texto manual previo permanece.

### Scenario 3: listas y topes
Given Inspiraciones con 2 filas y 2 ideas, When el usuario toca "Agregar",
Then se agrega/completa una fila respetando el tope 3; al tope el botón queda deshabilitado y no se pierde nada.

### Scenario 4: Mecánicas clave
Given el paso Mecánicas y una respuesta `Movimiento: ...` / `Interacción: ...`,
When el usuario agrega las ideas,
Then cada una se vuelve una fila con categoría y descripción editables, hasta el tope 5. Una línea sin categoría válida se descarta.

### Scenario 5: Género
Given Género con un chip ya elegido y una respuesta que lo repite,
When se muestran las sugerencias,
Then la repetida no aparece (se filtra contra el borrador); "Agregar" incorpora la nueva vía `normalizeGenres`, sin duplicar chips.

### Scenario 6: ignorar y volver a pedir
Given 2 tarjetas visibles, When el usuario ignora una y toca "Sugerir" de nuevo,
Then la ignorada desaparece, el pedido nuevo reemplaza el conjunto y el borrador no cambió por ignorar.

### Scenario 7: parcial, vacía o fallo
Given un `AiClient` que devuelve 1 idea, ninguna usable o lanza `AiClientException`,
When el usuario toca "Sugerir",
Then la parcial se muestra con aviso; 0 usables o el fallo muestran un error no bloqueante con "Reintentar", sin tarjetas y sin tocar el borrador. La escritura y el guardado siguen disponibles.

### Scenario 8: navegación
Given sugerencias visibles en un paso, When el usuario avanza, retrocede, cancela o guarda,
Then desaparecen; al volver no se muestran hasta tocar "Sugerir". Un resultado tardío de otro paso se ignora.

### Scenario 9: deduplicación
Given líneas repetidas (distinta capitalización/espacios) o valores ya presentes,
When se parsea y muestra,
Then se conserva una copia por idea y se muestran como máximo 2 únicas.

### Scenario 10: gate honesto
Given la app con IA fake,
When corren `:core:testDebugUnitTest`, `:composeApp:assembleDebug` y `CI=true ./init.sh`,
Then todo pasa sin red ni key, el esquema sigue en v8 sin `.sqm` nuevo y el gate termina en `0`.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `CONTEXT.md` — Android-first, iOS congelado, WIP=1, IA fake en gate y dependencias `accepted`.
- `docs/specs/brainstorming-wizard.md`, `ai-client-interface.md`, `ai-fake-real-switch.md` — 9 pasos, guardado completo, topes 3/3/5, `AiClient.generate(GenerateRequest)` y runtime inyectado.
- `docs/adr/0001-ai-runtime-decision.md`, `docs/adr/0004-ai-real-integration.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md` — chat → Brainstorming; key fuera del repo; fake en gate; calidad real pendiente.
- `DESIGN.md` — stepper 9 pasos y 2 sugerencias; presentación fina abierta.
- `core/.../ai/{AiClient,FakeAiClient}.kt`, `core/.../model/{GameProject,Genres}.kt` — `GenerateRequest(prompt, context)`, `GameProjectDraft`, `MechanicCategory`, `GenreCatalog`, `normalizeGenres`; el fake devuelve hoy `[fake] ...`.
- `composeApp/.../{App,BrainstormingWizardScreen}.kt` y `.../{androidMain,iosMain}/.../Main{Activity,ViewController}.kt` — estado con `remember`, servicios por parámetro, `rememberCoroutineScope`, topes, guardado y roots con `aiRuntime.aiClient`.

### Existing Patterns To Follow

- Dominio en `:core`; UI "tonta" en `:composeApp`; dependencias por parámetro, sin DI/ViewModel/Flow.
- Tests compartidos en `commonTest` + `@Test` por plataforma; `AiClientException` es el fallo esperado; normalizar con helpers existentes y no persistir hasta Guardar.
- No loguear prompt/contexto/respuesta; Material 3, texto/forma y objetivos ≥48dp.

### Current Gaps

- No existe servicio de prompt/contexto/parsing ni tests; el wizard no recibe `AiClient` ni tiene estados de sugerencias.
- `FakeAiClient.generate` devuelve una línea con eco; no es parseable como 2 ideas. No hay harness UI/E2E persistente.

## Technical Approach

### 1. Servicio en `:core`

Crear `core/src/commonMain/kotlin/com/playbook/core/ai/BrainstormingSuggestions.kt`:

```kotlin
enum class BrainstormingStep {
    TITLE, GENRE, CORE_LOOP, AUDIENCE, CONCEPT,
    INSPIRATIONS, FANTASY, KEY_MECHANICS, OUT_OF_SCOPE,
}

data class BrainstormingSuggestion(val text: String, val category: MechanicCategory? = null)

sealed interface BrainstormingSuggestionResult {
    data class Success(val suggestions: List<BrainstormingSuggestion>, val partial: Boolean = false) : BrainstormingSuggestionResult
    data class Failed(val cause: AiClientException) : BrainstormingSuggestionResult
    data object InvalidResponse : BrainstormingSuggestionResult
}

class BrainstormingSuggestionService(private val aiClient: AiClient) {
    suspend fun suggest(step: BrainstormingStep, draft: GameProjectDraft): BrainstormingSuggestionResult
}
```

- **Prompt** en español, encabezado por el marcador estable `PLAYBOOK_BRAINSTORMING_SUGGESTIONS_V1` y `STEP=<enum>`. Pide 2 ideas, una por línea, sin texto extra, ≤200 caracteres, sin repetir lo presente; para `KEY_MECHANICS` exige `Movimiento|Interacción|Conflicto: descripción`.
- **Contexto** (`GenerateRequest.context`): `"<campo>: <valor>"` con campos no vacíos, sin `owner`/`id`/timestamps; máximo 12 entradas y valores >300 caracteres truncados:
  - `TITLE`: Géneros, Concepto, Título actual. `GENRE`: Título, Concepto, Géneros actuales + catálogo.
  - `CORE_LOOP`: Título, Géneros, Concepto, Fantasía. `AUDIENCE`: Título, Géneros, Core Loop, Concepto.
  - `CONCEPT`: Título, Géneros, Core Loop, Audiencia. `FANTASY`: Título, Géneros, Core Loop, Audiencia, Concepto.
  - `INSPIRATIONS`: Título, Géneros, Concepto, Inspiraciones. `KEY_MECHANICS`: Título, Géneros, Core Loop, Mecánicas.
  - `OUT_OF_SCOPE`: Título, Géneros, Concepto, Core Loop, lista. En escalares se agrega el campo actual como "Borrador actual".
- **Parsing**:
  - Normalizar `\r\n`/`\r`; separar líneas; quitar viñetas (`-`, `*`, `•`), numeración (`1.`, `1)`, `2.`, etc.) y comillas; `trim` y colapsar espacios.
  - `KEY_MECHANICS`: regex case/accent-insensitive `^(movimiento|interaccion|conflicto)\s*[:\-]\s*(.+)$`; mapear a `MechanicCategory`; descartar líneas sin categoría válida.
  - Descartar vacías; deduplicar case-insensitive; descartar valores ya presentes en el borrador (escalar/Géneros/lista o categoría+descripción).
  - Truncar a 280 caracteres; tomar las **primeras 2** únicas. 0 usables → `InvalidResponse`; 1 → `Success(partial = true)`; 2 → `Success(partial = false)`.
- **Errores**: `AiClientException` → `Failed(cause)`; `CancellationException` se propaga. No loguear datos del usuario.

### 2. Fake determinista

Modificar `FakeAiClient.generate`: si el prompt contiene el marcador, devolver **2 líneas parseables** deterministas (en `KEY_MECHANICS`, con categorías válidas); en otro caso conservar `"[fake] ..."`. Sin `Random`, red ni key. Así el gate/smoke ejercitan el flujo completo sin cambiar el fake de RAG/Q&A.

### 3. Wizard

- `BrainstormingWizardScreen` recibe `suggestionService` y mapea `step` → `BrainstormingStep`.
- El snapshot para `suggest` se arma con el mismo draft completo de Guardar (normalización incluida), sin persistir.
- Estado local: `Idle`, `Loading`, `Loaded(suggestions, partial)` o `Error(message)`, más el `Job` en curso (`rememberCoroutineScope`).
- "Sugerir" deshabilitado mientras carga ("Sugiriendo…" + indicador); el resto sigue habilitado. "Reintentar" repite el pedido.
- Tarjetas (máx. 2) debajo del contenido y arriba de Atrás/Siguiente: texto, categoría visible en Mecánicas y botones "Usar" (escalar), "Agregar" (Género/listas/Mecánicas) e "Ignorar".
- "Usar"/"Agregar" deshabilitado si la idea ya está o se alcanzó el tope; "Ignorar" siempre disponible. Aplicar no navega ni guarda; al aplicar se quita la tarjeta y, si no quedan, se vuelve a `Idle`.
- Al cambiar de paso, cancelar el `Job`, limpiar el estado y comparar el paso capturado antes de aplicar un resultado tardío. Igual al cancelar/guardar y en `DisposableEffect`.

### 4. Wiring

- `App` recibe `aiClient: AiClient`; crea `val suggestionService = remember(aiClient) { BrainstormingSuggestionService(aiClient) }` y lo pasa al wizard.
- `MainActivity` pasa `aiClient = aiRuntime.aiClient`; `MainViewController`, `aiClient = runtime.aiClient` (iOS sigue fake).
- No se toca `AiRuntime`, `NoteIndexingService`, `RagQueryService`, esquema ni `init.sh`.

## Expected File Changes

- `core/.../ai/BrainstormingSuggestions.kt` — crear; tipos, prompt/contexto, parsing y servicio.
- `core/.../ai/FakeAiClient.kt` — modificar; rama determinista con 2 sugerencias.
- `core/src/commonTest/.../ai/BrainstormingSuggestionCheck.kt` — crear; feliz, parcial, inválida, fallo, dedupe, mecánicas y contexto.
- `core/src/{androidUnitTest,iosTest}/.../ai/BrainstormingSuggestion*Test.kt` — crear; runners por plataforma.
- `composeApp/.../BrainstormingWizardScreen.kt` — modificar; botón, estados, tarjetas y aplicación al borrador.
- `composeApp/.../App.kt` — modificar; `aiClient` y servicio recordado.
- `composeApp/.../{androidMain,iosMain}/.../Main{Activity,ViewController}.kt` — modificar; pasar el `AiClient` del runtime.
- Docs: `ARCHITECTURE.md`, `docs/technical-discovery.md`, `DESIGN.md`, `docs/risks-and-open-questions.md`, `AGENTS.md` (cola), `feature_list.json` y `PROGRESS.md` al implementar/cerrar.
- **Sin cambios**: esquema/`.sqm`, `GameProject.sq`, repositorio, `CONSTRAINTS.md`, `init.sh`, build files, version catalog e `iosApp/**`.

## Visual Design Impact

- UI involved: **yes**; fuente `DESIGN.md` (stepper + 2 sugerencias por paso).
- Estados: idle, cargando, 2 tarjetas, parcial, error, sin duplicados, tope alcanzado, ignorada/aplicada.
- Nuevo artefacto: **no**; Material 3 existente, texto + forma (no sólo color), botones ≥48dp y `contentDescription` si hay iconos.
- iOS congelado: sin paridad ni smoke; sólo compilar.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — servicio en `:core`, uso de `AiClient.generate`, estado efímero y wiring `App`/roots.
- `docs/technical-discovery.md`: **update** — prompt/contexto, parsing, límite 2, parcial/error, descarte al navegar y fake determinista.
- `DESIGN.md`: **update** — cerrar la pregunta abierta de presentación.
- `docs/risks-and-open-questions.md`: **update** — fake cubierto; calidad real pendiente de `ai-real-chat`; sin persistencia.
- `AGENTS.md`: **update acotado** — sólo la cola al cerrar.
- `CONSTRAINTS.md`, `CONTEXT.md`, `docs/domain-model.md`: **not needed** — no hay regla/entidad nueva; el glosario ya describe las 2 sugerencias.
- `feature_list.json` y `PROGRESS.md`: **update** con evidencia.

## Implementation Plan

1. Crear tipos/servicio, rama fake determinista y tests (`Check` + runners) en `:core`.
2. Cablear `aiClient` en `App` y ambos roots.
3. Agregar estados, botón y tarjetas al wizard con cancelación y limpieza.
4. Verificar, hacer smoke/controles negativos, actualizar docs y cerrar estado.

## Implementation Tasks

- [x] Crear `BrainstormingSuggestions.kt` (enum, tipos, prompt/contexto, parsing) y extender `FakeAiClient` con 2 sugerencias parseables.
- [x] Tests del servicio/parser (fake, parcial, inválida, fallo, dedupe, mecánicas, contexto).
- [x] UI del wizard (Sugerir, estados, tarjetas, Usar/Agregar/Ignorar) y cancelación/limpieza al navegar.
- [x] Wiring `App` + `MainActivity` + `MainViewController`.
- [x] Verificación, smoke, controles negativos y evidencia.
- [x] Docs durables, `feature_list.json` y `PROGRESS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → todos verdes, incluidos los nuevos tests.
- `./gradlew :composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
- `CI=true ./init.sh` → exit 0 (Android-only, fake, sin red ni dev servers).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (no bloqueante).
- `Schema.version == 8`; sin `.sqm` nuevos; sin diff en esquema, build files, `init.sh` ni `iosApp`; `:core` sólo cambia lo indicado.
- **Smoke Android** (si no hay dispositivo, declararlo): "Sugerir" en Título → 2 tarjetas y campo intacto; "Usar" una, editar el campo, "Ignorar" la otra.
- Repetir en Género (chip), Inspiraciones (tope 3), Mecánicas (fila con categoría) y Fuera de alcance; navegar y volver → sin tarjetas y "Sugerir" funciona.
- Guardar → persiste lo aceptado/editado, sin tablas nuevas.
- **Controles negativos** (restaurar byte-idéntico y re-verificar): (a) no filtrar duplicados → tarjetas repetidas o >2; (b) aplicar al pedir → el campo cambia sin Usar/Agregar; (c) no limpiar al cambiar de paso → un resultado tardío altera el paso nuevo.
- **E2E persistente: no aplica** — sin harness UI/E2E; cobertura por tests del core, compilación y smoke manual Android.

## Evidence To Capture

- Salida de los tests del core con nombres/conteos, `assembleDebug`, `CI=true ./init.sh` (exit 0) y link iOS.
- Esquema v8 sin `.sqm` nuevos y diff sin cambios protegidos.
- Smoke Android: capturas/UI dumps de idle, cargando, 2 tarjetas, parcial/error si se induce, aceptar/editar/ignorar, navegación/limpieza y guardado final.
- Controles negativos (a)/(b)/(c) reproducidos y restaurados.
- `git status` sólo con archivos de la feature y docs; sin key, secretos, deps ni esquema.

## Validator Checklist

- [ ] Alcance respetado: sólo sugerencias en los 9 pasos; sin persistencia, Notas automáticas, IA real, esquema, rediseño ni deps.
- [ ] Escenarios 1–10 pasan (con los límites declarados del smoke).
- [ ] La IA nunca auto-rellena: sólo Usar/Agregar mutan el borrador; escritura y guardado disponibles durante carga/error.
- [ ] Máximo 2, dedupe contra respuesta y borrador, parcial con aviso, 0 como error; mecánicas con categoría válida.
- [ ] Sugerencias efímeras y resultados tardíos no contaminan otro paso.
- [ ] Tests del core con IA fake determinista (sin red), gate exit 0 y sin cambios en `GameProject`/repositorio/esquema/`init.sh`/build files; iOS compila.
- [ ] Docs durables actualizados; `feature_list.json`/`PROGRESS.md` con evidencia; `AGENTS.md` sólo la cola.
- [ ] No hay E2E persistente y la spec lo justifica.
