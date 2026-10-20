# Feature Implementation Spec: Onboarding del Proyecto en el primer arranque

## Source Feature

- `id`: `first-run-project-onboarding`; `area`: `ui`
- `depends_on`: `["brainstorming-wizard"]` (`accepted`)
- `status`: `not_started` (al planificar); `source`: `feature_list.json`
- Rama: `feature/first-run-project-onboarding` desde `origin/develop` (`6db4d38`); WIP=1.

## Goal

Si la instalación **no tiene Proyecto**, el primer arranque ofrece iniciar el
Brainstorming (saltable) y, al aceptar o saltar, deja creado el Proyecto
singleton. Si el Proyecto ya existe, la app arranca en su destino normal sin
volver a ofrecerlo. La existencia del singleton
(`GameProjectRepository.get() != null`) es la única señal: **no** hay flag
persistido de “onboarding visto”. Es **sólo UI + wiring en `:composeApp`**: no
cambia `:core`, no crea Notas y no usa IA.

## Non-Goals

- IA, sugerencias, Notas automáticas o “Nota por paso”; múltiples Proyectos,
  `delete()`/reset o migración de datos.
- Cambios en `:core`, SQLDelight/`.sqm`, esquema (sigue v8), deps, build files,
  `init.sh` o `iosApp/**`.
- Rediseñar `NotesListScreen` o `BrainstormingWizardScreen`; sólo se agrega la
  oferta y el estado de carga. Nada de onboarding multi-paso, tutorial, permisos
  o flag persistido.
- Paridad/smoke iOS; sólo debe seguir compilando. Tampoco se agrega manejo de fallos de **lectura** de `get()` (fallo local app-wide, fuera de este slice).

## Job Story And Users

**Job story.** When abro Playbook por primera vez sin Proyecto, I want decidir
entre arrancar el Brainstorming o saltarlo creando un Proyecto vacío, so I can
capturar ideas sin quedar bloqueado y completar la ficha más tarde.

- **Autor** (single-user local-first): dueño del Proyecto; sin auth ni permisos
  nuevos. Aceptar o saltar deja la ficha completable desde la lista.
- **Desarrollador**: corre Gradle/tests y smoke Android con IA fake, sin red ni
  secrets.

## Scope Decisions

1. **Detección por estado, no por flag.** `get() == null` = sin Proyecto; `get() != null` = existente. No se persiste “onboarding visto”.
2. **Loading ≠ Missing.** La oferta aparece sólo tras confirmar que no hay Proyecto; los `reload()` posteriores no vuelven a `Loading`.
3. **Aceptar también crea.** “Iniciar Brainstorming” persiste el singleton vacío y luego navega; “Saltar” lo persiste y va a la lista. Si falla, no navega.
4. **Proyecto vacío válido.** `save(GameProjectDraft(owner = "local"))`: `id = "singleton"`, escalares `null`, listas/hijas vacías, `createdAt == updatedAt`; Inspiraciones/Fuera de alcance/Mecánicas quedan `0/0/0` (topes 3/3/5).
5. **Idempotencia.** `save` es upsert y la UI bloquea dobles taps: nunca hay segunda fila ni segundo onboarding; reintentar es seguro.
6. **Error de guardado.** `AlertDialog` “No se pudo crear el Proyecto” + “Intentá de nuevo.”; no navega ni escribe parcialmente y registra `STORAGE`/`STORAGE_FAILED`.
7. **Cancelar el wizard no reabre el onboarding.** Aceptar ya creó el Proyecto; “Cancelar” vuelve a la lista con un Proyecto vacío, completable desde “Brainstorming”.
8. **Instalaciones existentes sin Proyecto** (p. ej. con Notas previas) también ven la oferta; saltar preserva Notas/Adjuntos.
9. **Oferta full-screen** con tokens Material 3; la lista no se muestra hasta resolverla. No se rediseña la pantalla principal.

## Acceptance Scenarios

| # | Given / When / Then |
|---|---|
| 1 | **Primer arranque sin Proyecto.** Given una base v8 sin fila en `game_project`, When se abre la app, Then se ve el estado de carga (ni lista ni oferta) y luego la oferta con “Iniciar Brainstorming” y “Saltar”; `game_project` sigue sin filas y la lista no se muestra. |
| 2 | **Saltar.** Given la oferta, When el usuario toca “Saltar”, Then se crea el singleton vacío (`owner = "local"`, escalares `null`, listas/hijas vacías, `createdAt == updatedAt`), se muestra la lista y la oferta desaparece. |
| 3 | **Aceptar.** Given la oferta, When el usuario toca “Iniciar Brainstorming”, Then el singleton vacío se persiste **antes** de navegar, el wizard abre en `Paso 1/9 · Título` con la ficha vacía, y “Cancelar” vuelve a la lista dejando el Proyecto creado. |
| 4 | **Proyecto existente.** Given un Proyecto persistido (por saltar, aceptar o el wizard), When se abre o relanza la app, Then no aparece la oferta ni se escribe `game_project`; se renderiza el destino normal y “Brainstorming” sigue disponible. |
| 5 | **Segundo arranque.** Given la app se cerró después de crear el Proyecto vacío, When se vuelve a abrir, Then arranca en la lista directamente, sin oferta. |
| 6 | **Error al crear.** Given la oferta y un repositorio que lanza al guardar, When el usuario toca una acción, Then se muestra el diálogo de error, se permanece en la oferta, no se navega, no queda fila en `game_project` y se puede reintentar al cerrarlo. |
| 7 | **Idempotencia.** Given dos invocaciones rápidas de la misma acción, When corre el guardado, Then sólo se ejecuta uno, se crea/actualiza una única fila `id = "singleton"` y no hay Proyecto duplicado. |
| 8 | **Aceptar y luego guardar el wizard.** Given el Proyecto vacío creado por “Iniciar Brainstorming”, When el usuario completa el wizard y toca “Guardar”, Then se actualiza la **misma** fila singleton, `owner`/`createdAt` se preservan, `updatedAt` se reescribe con el reloj actual y la ficha queda persistida. |
| 9 | **Notas sin Proyecto.** Given una base con Notas/Adjuntos y sin `game_project`, When el usuario salta la oferta, Then se crea el Proyecto vacío y las Notas/Adjuntos siguen visibles y operativos. |
| 10 | **Gate honesto.** Given los escenarios 1–9, When corren `:composeApp:assembleDebug`, `:core:testDebugUnitTest` y `CI=true ./init.sh`, Then todo pasa en Android/JVM con IA fake, sin red ni deps nuevas, esquema v8 sin `.sqm` nuevo y `./init.sh` en `0`. |

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `docs/mvp-execution-plan.md`
  — Android-first, iOS congelado y gate.
- `CONTEXT.md`, `DESIGN.md`, `docs/domain-model.md`,
  `docs/risks-and-open-questions.md`, `docs/technical-discovery.md` — dominio,
  primer arranque y decisiones del wizard.
- `docs/specs/game-project-persistence.md`, `docs/specs/brainstorming-wizard.md`
  — `get()`/`save()`, ficha vacía, wiring y no-apertura automática.
- `composeApp/.../App.kt` — `project` nullable en `LaunchedEffect(refreshKey)`;
  no distingue carga de ausencia ni ofrece onboarding.
- `composeApp/.../{NotesListScreen,BrainstormingWizardScreen}.kt` — entrada al
  wizard; cancelar no escribe y reabre con precarga.
- `core/.../repository/*GameProject*.kt`, `core/.../model/GameProject.kt`,
  `GameProjectCheck.kt` — singleton, upsert, owner, `createdAt` y límites.
- `composeApp/.../{MainActivity,MainViewController}.kt` — ya construyen y pasan
  `SqlDelightGameProjectRepository`; no requieren cambios. `composeApp`/`init.sh`
  no tienen harness de UI/E2E y el gate es Android-only.

### Patterns And Gaps

- `App` sin DI/`ViewModel`/Flow; pantallas “tontas”, lectura en
  `LaunchedEffect(refreshKey)`, mutación + `reload()`, repositorios síncronos y
  errores con `runCatching` + diálogo; Material 3, texto/forma, no sólo color.
- Gap: `project == null` hoy significa “cargando” o “sin Proyecto”; no hay
  oferta, creación al aceptar/saltar ni error de esa creación; sin UI/E2E.
- **Integración:** `brainstorming-note-per-step` y `brainstorming-ai-suggestions`
  tocan `App.kt`/`BrainstormingWizardScreen.kt`; rebasar esta rama tras
  integrarlas y re-verificar el wiring, sin tocar sus worktrees.

## Technical Approach

### 1. Estado de carga en `App.kt`

Reemplazar `var project by remember { mutableStateOf<GameProject?>(null) }` por:

```kotlin
private sealed interface ProjectLoadState {
    data object Loading : ProjectLoadState
    data object Missing : ProjectLoadState
    data class Loaded(val project: GameProject) : ProjectLoadState
}
```

Inicializar en `Loading`. En el `LaunchedEffect(refreshKey)` existente, tras
Notas/Adjuntos: `val loaded = gameProjectRepository.get(); projectState =
loaded?.let(::Loaded) ?: Missing`. **No** volver a `Loading` en cada `reload()`.
Derivar `val project = (projectState as? Loaded)?.project` para el wizard.

### 2. Gate y acciones en `App.kt`

- Antes del `when (destination)`: `Loading -> FirstRunLoading()`;
  `Missing -> FirstRunOnboardingScreen(...)`; `Loaded ->` el `when` actual. Los
  diálogos existentes y `DisposableEffect` quedan fuera del gate.
- Helper `createProject(action: (GameProject) -> Unit)`: si `onboardingBusy`
  retornar; marcarlo con `try/finally`; `runCatching { gameProjectRepository.save(
  GameProjectDraft(owner = LOCAL_OWNER_ID)) }`; éxito → `projectState = Loaded(saved)`,
  `reload()`, `action(saved)`; fallo → `recordFailure(STORAGE, STORAGE_FAILED)` y error.
- “Iniciar Brainstorming”: `createProject { destination = Brainstorming }`.
  “Saltar”: `createProject { goToList() }`.
- `onboardingBusy`/`onboardingError` en `App`; error con `AlertDialog` (“No se
  pudo crear el Proyecto”, “Intentá de nuevo.”, “Aceptar”). `LOCAL_OWNER_ID`
  sigue privado de `App.kt`.

### 3. `FirstRunOnboardingScreen.kt` (crear)

`FirstRunOnboardingScreen(onStartBrainstorming, onSkip, modifier)` y
`FirstRunLoading(modifier)`.

- Oferta full-screen: título “Tu Proyecto de Juego”, texto que explica el
  Proyecto único y que se puede completar después, `Button` “Iniciar
  Brainstorming” y `OutlinedButton` “Saltar”.
- `FirstRunLoading`: `CircularProgressIndicator` centrado + “Cargando…”
  accesible; no muestra la lista. Tokens/tipografía de `MaterialTheme`; sin
  artefacto de diseño nuevo.
- No se toca `BrainstormingWizardScreen`, `NotesListScreen`, `:core`, raíces,
  build files, `init.sh` ni `iosApp/**`.

## Expected File Changes

- `docs/specs/first-run-project-onboarding.md` — create (planner; ya existe).
- `composeApp/src/commonMain/kotlin/com/playbook/app/FirstRunOnboardingScreen.kt`
  — create; oferta y carga.
- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt` — modify;
  `ProjectLoadState`, gate, acciones, idempotencia, error y wiring al wizard.
- `ARCHITECTURE.md` y `docs/technical-discovery.md` — update; `DESIGN.md`
  (Core Screens) y `AGENTS.md` (sólo cola) — update acotado;
  `feature_list.json` y `PROGRESS.md` — evidencia.
- Sin cambios: `core/**`, `composeApp/src/{androidMain,iosMain}/**`,
  `NotesListScreen.kt`, `BrainstormingWizardScreen.kt`,
  `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`, `init.sh`,
  `iosApp/**`, `.sqm`/esquema.

## Visual Design And Documentation

- **UI yes.** Fuente: `DESIGN.md`; se reutilizan tokens, tipografía y Material 3.
  Estados: oferta (título, texto, dos acciones), carga y `AlertDialog` de error.
  `NotesListScreen` no se rediseña. New design artifact: **no**.
- `ARCHITECTURE.md`: **update** — `ProjectLoadState`, gate y creación del
  singleton, sin flag persistido. `DESIGN.md`: **update acotado** — Core Screens
  incluye el onboarding saltable.
- `CONSTRAINTS.md`: **not needed** — la regla del singleton ya está en
  `CONTEXT.md`/`docs/domain-model.md`; ambos **not needed** (ya describen la
  creación del Proyecto en el primer arranque vía Brainstorming).
- `AGENTS.md`: **update acotado** — sólo la línea de próximas features.
  `docs/technical-discovery.md`: **update** — quitar “no hay apertura automática”.
  `docs/risks-and-open-questions.md`: **not needed** salvo riesgo nuevo.
- `feature_list.json`/`PROGRESS.md`: **update** al implementar/validar.

## Implementation Plan And Tasks

1. Crear `FirstRunOnboardingScreen.kt`; introducir `ProjectLoadState` y gatear
   loading → oferta → contenido normal.
2. Implementar aceptar/saltar con singleton, idempotencia y error; verificar
   compilación, tests, gate y smoke.
3. Actualizar docs durables y evidencia.

- [x] `FirstRunOnboardingScreen.kt` con oferta accesible y `FirstRunLoading`.
- [x] `ProjectLoadState` en `App` sin resetear a `Loading` en reloads.
- [x] Gate de onboarding antes de `NotesDestination`.
- [x] “Saltar” y “Iniciar Brainstorming” crean el singleton y navegan; cancelar
      no reofrece.
- [x] Guard de doble acción + `AlertDialog` de error con reintento.
- [x] Docs durables, `feature_list.json`, `PROGRESS.md`, verificación y smoke.

## Verification Plan

- `./gradlew :composeApp:assembleDebug --rerun-tasks -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL.
- `./gradlew :core:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` → 0 failures
  (incluye `GameProjectCheck`: Proyecto vacío y upsert).
- `CI=true ./init.sh` → exit 0; Android-only, IA fake, sin red.
- `Schema.version == 8`; `.sqm` 1..7; sin `.sqm` nuevo; diff vacío en `:core`, build files, `init.sh`, `iosApp/**` y raíces.
- **iOS (no bloqueante):** `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` compila.
- **Smoke Android** (sin `adb`/emulador, declararlo honestamente):
  1. `installDebug`, `pm clear com.playbook.app`, lanzar; `game_project` con 0
     filas; ver loading y luego la oferta sin lista.
  2. “Saltar” → lista; una fila `id = "singleton"`, `owner = "local"`,
     escalares `NULL`, `created_at == updated_at`, hijas vacías.
  3. Force-stop + relanzar → lista directa, sin oferta.
  4. `pm clear` → oferta → “Iniciar Brainstorming” → wizard `Paso 1/9`; la fila
     singleton ya existe; “Cancelar” → lista.
  5. Force-stop + relanzar → sin oferta; abrir “Brainstorming”, completar Título
     y Guardar → misma fila, `owner`/`created_at` preservados, `updated_at`
     avanzado.
  6. Sembrar una base con Notas y sin `game_project` → oferta → “Saltar” →
     Notas intactas.
- Consultas: `SELECT id, owner, title, created_at, updated_at FROM game_project;`
  y `COUNT(*)` de las cuatro tablas hijas vía `adb shell run-as com.playbook.app
  sqlite3 databases/playbook.db`.
- **Controles negativos** (restaurar y re-verificar): (a) hacer que el guardado
  lance → diálogo, se permanece en la oferta, sin fila; (b) navegar al aceptar
  sin crear → tras “Cancelar” el onboarding se reofrece; (c) doble acción →
  una sola fila.
- **E2E persistente: no aplica** — no hay harness de UI/E2E y `init.sh` no
  levanta simuladores. Cobertura: `GameProjectCheck`, compilación y smoke.

## Evidence And Validator Checklist

- Evidencia: salida de `:composeApp:assembleDebug`, `:core:testDebugUnitTest` y
  `CI=true ./init.sh` (exit 0); `Schema.version == 8` y `.sqm` 1..7; capturas/UI
  dumps de loading, oferta, lista tras “Saltar”, wizard tras “Iniciar
  Brainstorming”, error y relanzamientos sin oferta; queries de `game_project`/
  hijas; controles negativos reproducidos/restaurados; `git status` sólo con
  archivos de la feature, sin secrets, deps, `.sqm` ni cambios de esquema.
- [ ] Alcance: sólo UI + wiring; sin IA, Notas, `:core`, esquema, deps, rediseño
  de lista/wizard, onboarding multi-paso ni iOS más allá de compilar.
- [ ] Escenarios 1–10 pasan (con límites del smoke declarados).
- [ ] `Loading` vs `Missing` explícito; oferta sólo tras la lectura y no en
  reloads.
- [ ] Aceptar y saltar crean el singleton vacío antes de navegar; error no navega
  ni escribe; reintento disponible.
- [ ] Proyecto existente no se reofrece al arrancar/relanzar; sin flag persistido
  ni segunda fila.
- [ ] Aceptar + Cancelar deja Proyecto vacío sin reoferta; aceptar + Guardar
  actualiza la misma fila preservando `owner`/`createdAt`.
- [ ] Proyecto vacío cumple `owner = "local"`, `id = "singleton"`, escalares
  `null`, listas/hijas vacías, `createdAt == updatedAt` y 3/3/5.
- [ ] `init.sh` sin cambios y exit 0; iOS compila; sin `.sqm` nuevo; docs
  durables y `feature_list.json`/`PROGRESS.md` con evidencia. No hay E2E
  persistente y la spec lo justifica.
