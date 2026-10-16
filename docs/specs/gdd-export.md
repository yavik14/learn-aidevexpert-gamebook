# Feature Implementation Spec: Exportar/compartir el GDD como Markdown

## Source Feature

- `id`: `gdd-export`
- `area`: `gdd`
- `depends_on`: `["gdd-view"]` (`accepted` 2026-10-13)
- `status`: `not_started` (al planificar)
- `source`: `feature_list.json`

> **Feature derivada.** `gdd-export` no estaba en el brief original: cierra el
> GDD como **documento compartible**. El contrato ya está cerrado con el usuario;
> esta spec lo plasma sin re-derivar alcance. Se apoya en `gdd-view` (proyección
> `buildGdd` + pantalla `GddScreen`) y **no** toca `App.kt` (lo trabaja en
> paralelo `offline-pending-retry`).

## Goal

Permitir que el usuario **exporte/comparta el GDD como texto Markdown** desde la
propia vista de GDD. El documento se construye con una **proyección pura en
`:core`** (`buildGddMarkdown(notes, now)`), determinista y cubierta por tests
cross-platform, y se entrega al **share sheet del sistema** mediante un puerto
`GddSharer` (`expect`/`actual`) **en memoria** (`ACTION_SEND` `text/plain` en
Android; `UIActivityViewController` en iOS). No se escriben archivos, no hay
permisos nuevos, no se persiste nada y no hay cambios de esquema ni de
dependencias.

`GddScreen` suma un botón **"Compartir"** (deshabilitado si no hay Notas) y
muestra un **snackbar** con el resultado ("Listo para compartir" / "No
disponible"). El share es **autocontenido en `GddScreen`**: no se modifica
`App.kt`.

## Non-Goals

- **Archivos / PDF / compartir adjuntos**: sólo texto Markdown en memoria (sin
  `FileProvider`, sin guardar, sin permisos).
- **Adjuntos o Notas relacionadas en el Markdown**: el documento lleva Notas,
  sus Etiquetas y su `status`; nada más.
- **Esquema o migraciones** (`gdd-export` **no** agrega `.sqm` ni toca
  `Schema.version`), dependencias nuevas, backend ni red.
- **UI nueva** más allá del botón "Compartir" + snackbar; sin pantalla de
  previsualización ni edición del Markdown.
- **`App.kt`/`MainActivity`/`MainViewController`/`init.sh`/`iosApp`**: sin
  cambios.
- Framework DI, `ViewModel`/Flow, coroutines propias del módulo, librería de
  navegación; formalizar `DESIGN.md`/tokens (sigue `provisional`).

## Job Story

When ya tengo mi GDD armado con las Notas de mi juego, I want exportarlo como un
documento Markdown y compartirlo con quien quiera, so I can sacar el diseño de la
app sin reescribirlo a mano.

## Users And Permissions

- Autor/usuario único del MVP (local-first): ve todas las Notas del dispositivo y
  puede compartirlas con cualquier app que acepte texto.
- Sin auth ni permisos nuevos. El share usa el mecanismo del sistema; no se pide
  ningún permiso (el texto Markdown es contenido propio).

## Terminology Reconciliation

No aplica: el entry usa terminología vigente (GDD = vista agregada por `track`).
No hay "categoría/tipo/nivel" ni agrupación por etiqueta.

## Format Contract (determinista)

> **Nota de supersesión (2026-10-16).** El prefijo de la línea de metadatos
> `Etiquetas:` de esta spec fue **reemplazado por `Géneros:`** en
> `genres-catalog-persistence` (renombre de dominio Etiquetas→Géneros; no se
> reescribe esta spec aceptada). `GddMarkdown` y `GddMarkdownCheck` ya emiten y
> esperan `Géneros:`. El resto del "Format Contract" sigue vigente.

`buildGddMarkdown(notes: List<Note>, now: String): String` produce exactamente:

- **Encabezado** (sin fecha):
  - línea `# GDD — Playbook`
  - línea en blanco
  - línea `Total: N notas` (singular `Total: 1 nota`; `Total: 0 notas` si vacío)
- **Secciones** `## <label>` en orden fijo: `mecánicas`, `personajes`,
  `historia`, `Sin track`; **se omiten** las secciones sin Notas. `label` es
  `track.code` o `Sin track` (generados, **no** se escapan).
- **Por Nota** (dentro de su sección):
  - `### <título>`, donde **título** = primera línea del `body` (trim,
    **escapada**); si el `body` está vacío (trim) → `Nota <fecha>` con
    `<fecha> = now` (generado, **no** se escapa).
  - Debajo del título, **sólo el resto del `body`** (escapado), sin repetir la
    primera línea; si no hay resto, sólo el heading.
  - Línea de metadatos: `Etiquetas: a, b — Estado: <status.code>` (Etiquetas
    escapadas, `status.code` no); si no hay Etiquetas → sólo
    `Estado: <status.code>`.
- Bloque de Nota y bloque de sección separados por **una línea en blanco**;
  documento termina con `\n`.

Ejemplo canónico (`now = "2026-10-15 14:30"`; 4 Notas: mecánicas 2, personajes 1,
sin track 1; historia vacía → omitida):

```
# GDD — Playbook

Total: 4 notas

## mecánicas

### Jefe final
aparece en el nivel 3
Etiquetas: combate, jefe — Estado: capturada

### Nota 2026-10-15 14:30
Estado: pendiente

## personajes

### Idea
Estado: capturada

## Sin track

### Protagonista sin nombre
Etiquetas: lore — Estado: indexada
```

- **Escapado de Markdown en contenido de usuario** (título, cuerpo y Etiquetas):
  backslash-escape de cada carácter del set (backslash primero):

  ```
  \  `  *  _  {  }  [  ]  (  )  #  +  -  .  !  |  >
  ```

  El fallback `Nota <fecha>`, los labels de sección y `status.code` **no** se
  escapan (son generados).

## Acceptance Scenarios

### Scenario 1: encabezado y total (plural)

Given 2+ Notas, When `buildGddMarkdown(notes, now)`, Then arranca con
`# GDD — Playbook`, línea en blanco y `Total: N notas` (sin fecha), seguido de las
secciones.

### Scenario 2: total en singular y lista vacía

Given 1 Nota, Then `Total: 1 nota`; Given `emptyList()`, Then sólo el encabezado
con `Total: 0 notas` y **ninguna** sección.

### Scenario 3: orden fijo y omisión de secciones vacías

Given Notas en varios `track` (incluida al menos una sin `track`) y al menos un
`track` sin Notas, When se proyecta, Then el orden de secciones es
`mecánicas`, `personajes`, `historia`, `Sin track` y las vacías **no aparecen**;
`Sin track` sólo aparece si hay Notas sin `track`.

### Scenario 4: título = primera línea; cuerpo = resto

Given una Nota con `body` multilínea, Then el heading es la primera línea (trim)
y debajo se muestra **sólo** el resto (nunca se repite la primera línea); Given un
`body` de una sola línea, Then sólo heading + metadatos.

### Scenario 5: fallback `Nota <fecha>` con body vacío

Given una Nota con `body` vacío o compuesto sólo por espacios, Then el heading es
`Nota <now>` (con el `now` inyectado, sin escapar) y no hay línea de cuerpo.

### Scenario 6: escapado de Markdown

Given una Nota cuyo título/cuerpo/Etiquetas contienen caracteres del set (p. ej.
`#`, `*`, `_`, `[`, `]`, `-`, `.`, `!`, `|`, `>`), When se proyecta, Then cada uno
sale backslash-escapado en el documento; los labels generados y `status.code` no
se tocan.

### Scenario 7: nota sin etiquetas

Given una Nota sin Etiquetas, Then la línea de metadatos es sólo
`Estado: <status.code>` (sin `Etiquetas:` ni el separador `—`).

### Scenario 8: determinismo

Given la misma lista y el mismo `now`, Then dos llamadas devuelven exactamente el
mismo string; `now` sólo se usa en el fallback `Nota <fecha>`.

### Scenario 9: UI — Compartir y snackbar

Given `GddScreen` sin Notas, Then el botón **"Compartir"** está deshabilitado;
Given Notas, When se toca, Then se genera el Markdown (`buildGddMarkdown` con el
timestamp de plataforma) y se invoca `GddSharer.share`; la UI muestra
`"Listo para compartir"` si devuelve `true` y `"No disponible"` si devuelve
`false`, sin salir de la pantalla.

### Scenario 10: gate honesto y límites

Given la feature implementada, When corren los tests de `:core` (Android/JVM e
iOS), `:composeApp:assembleDebug`,
`:composeApp:linkDebugFrameworkIosSimulatorArm64` y `./init.sh`, Then todo pasa;
`gdd-export` **no** agrega `.sqm`, **no** cambia `Schema.version`, **no** agrega
dependencias y **no** toca `App.kt`/`MainActivity`/`MainViewController`/`init.sh`/
`iosApp`.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json` — flujo SDD/WIP=1; `gdd-view`
  `accepted`; entry `gdd-export` (`not_started`, `depends_on ["gdd-view"]`) con
  `verification`/`notes` provisionales a cerrar.
- `CONTEXT.md`, `docs/domain-model.md` — el GDD es una **vista** de las Notas (sin
  tabla); `Track`/`Etiqueta`/`NoteStatus` con `code` en español.
- `docs/specs/gdd-view.md`, `docs/specs/offline-pending-retry.md` — plantilla y
  convenciones (escenarios Given/When/Then, controles negativos, gate honesto);
  `offline-pending-retry` es dueña de la v6 (paralela, toca `App.kt`).
- `ARCHITECTURE.md` — secciones "GDD", "UI", "Modules", "Verification";
  `:composeApp → :core`, sin DI; `GddScreen` es "tonta".
- `docs/technical-discovery.md` — SQLDelight única fuente local; UI sin
  DI/ViewModel/Flow; patrón de decisiones por feature.
- `composeApp/src/commonMain/kotlin/com/playbook/app/GddScreen.kt` — `Column` con
  `Row` de "Volver" + "GDD" + resumen; `buildGdd(notes)` en `remember`; sin
  `Scaffold`/snackbar hoy.
- `composeApp/src/commonMain/kotlin/com/playbook/app/ImageCapture.kt` y
  `ImageCapture.android.kt`/`ImageCapture.ios.kt` — patrón `@Composable expect fun
  rememberX(): X` + `interface` en `commonMain` y `actual` por plataforma;
  `topViewController()` **privado** al final de `ImageCapture.ios.kt`.
- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt` — `GddScreen(...)` se
  invoca con `notes`/`attachmentCounts`/`onOpenNote`/`onBack`; `App` hoistea el
  estado y el `rememberCoroutineScope`. No debe modificarse.
- `core/src/commonMain/kotlin/com/playbook/core/gdd/GddProjection.kt` — `GddSection`
  + `buildGdd(notes)`: 3 secciones de `track` siempre + "Sin track" sólo si hay
  Notas sin `track`; orden dentro de sección = orden de entrada.
- `core/src/commonTest/.../gdd/GddProjectionCheck.kt` + runners
  `GddProjection{Android,Ios}Test.kt` — patrón `verifyX()` en `commonTest` + `@Test`
  por plataforma.
- `core/src/commonMain/kotlin/com/playbook/core/model/{Note,Track,NoteStatus}.kt`
  — `Note.body/tags/status/track`; `Track.code` (`mecánicas`/`personajes`/
  `historia`); `NoteStatus.code` (`capturada`/`pendiente`/`indexada`/`fallida`).
- `init.sh` — gate no bloqueante (assembleDebug + tests core Android/iOS + link
  framework iOS), sin dev servers ni simuladores.

### Existing Patterns To Follow

- **Dirección `UI → core`**: la construcción del documento vive en `:core` (pura,
  testeable cross-platform); `:composeApp` sólo renderiza y cablea.
- **Puerto + adaptadores**: `@Composable expect fun rememberX(): X` con
  `interface X` en `commonMain` y `actual` en `androidMain`/`iosMain` (patrón
  `ImageCapture`/`VoiceTranscriber`), sin DI.
- **Tests de lógica en `:core`**: helper `verifyX()` en `commonTest` + un `@Test`
  por plataforma (Android `JdbcSqliteDriver`, iOS), sin `expect`/`actual` en tests.
- **Documentos en español**, identificadores Kotlin en inglés, valores de dominio
  en español; `init.sh` no bloqueante.

### Current Gaps

- No existe ninguna construcción de Markdown del GDD ni sus tests.
- No existe puerto de share (`GddSharer`) ni formateo de timestamp por plataforma
  en `:composeApp`.
- `GddScreen` no tiene "Compartir" ni snackbar; `topViewController()` es privado
  en `ImageCapture.ios.kt` (a extraer a un helper interno compartido).
- No hay harness de UI/E2E: la lógica del documento se cubre en `:core`; la UI con
  smoke manual.

## Technical Approach

### 1. Proyección pura `:core` (sin esquema ni dependencias)

Nuevo `core/src/commonMain/kotlin/com/playbook/core/gdd/GddMarkdown.kt`:

```kotlin
package com.playbook.core.gdd

import com.playbook.core.model.Note

/**
 * Construye el GDD como texto Markdown determinista. Reusa [buildGdd] y omite
 * secciones vacías. [now] llega ya pre-formateado por la plataforma (p. ej.
 * "yyyy-MM-dd HH:mm") y sólo se usa en el fallback `Nota <now>` de un body vacío.
 */
fun buildGddMarkdown(notes: List<Note>, now: String): String
```

- Reutiliza **`buildGdd(notes)`** (ya agrupado/ordenado) y filtra
  `section.notes.isNotEmpty()`.
- Helpers `private`: `title(note, now)`, `bodyRest(body)`, `metadataLine(note)` y
  `escapeMarkdown(value)` (set de caracteres del "Format Contract").
- Sin dependencias: ni fecha, ni SQLDelight, ni `AiClient`. El formateo de fecha
  es responsabilidad de `:composeApp` (ver abajo), por eso `now` es `String`.

### 2. Puerto de share `:composeApp` (expect/actual + timestamp)

`composeApp/src/commonMain/kotlin/com/playbook/app/GddSharer.kt`:

```kotlin
package com.playbook.app

/** Comparte texto Markdown con el share sheet del sistema. */
interface GddSharer {
    /** @return true si se pudo presentar el share; false si no está disponible. */
    fun share(markdown: String): Boolean
}

@Composable
expect fun rememberGddSharer(): GddSharer

/** Timestamp local pre-formateado ("yyyy-MM-dd HH:mm"); sin dependencias de fecha en :core. */
expect fun currentTimestampLabel(): String
```

- `GddSharer.android.kt` (`actual`): `remember(LocalContext.current)`; `share`
  arma `Intent(ACTION_SEND)` con `type = "text/plain"` y `EXTRA_TEXT = markdown`,
  y lanza `Intent.createChooser(...)` (`addFlags(FLAG_ACTIVITY_NEW_TASK)` si el
  contexto no es `Activity`). Devuelve `true` si lanza; `false` ante
  `ActivityNotFoundException`/excepción. `currentTimestampLabel()` usa
  `SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())`.
- `GddSharer.ios.kt` (`actual`): `remember { IosGddSharer() }`; `share` crea
  `UIActivityViewController(activityItems = listOf(markdown), null)` y lo presenta
  desde `topViewController()`; devuelve `true` si hay presenter, `false` si no.
  `currentTimestampLabel()` usa `NSDateFormatter` (`dateFormat = "yyyy-MM-dd
  HH:mm"`).
- **Extracción del helper iOS**: mover `topViewController()` de
  `ImageCapture.ios.kt` a un helper **interno compartido** en `iosMain`
  (`IosViewControllers.kt`, `internal fun topViewController(): UIViewController?`)
  y reusarlo desde `ImageCapture.ios.kt` y `GddSharer.ios.kt`. **No** se cambia la
  lógica de captura de `ImageCapture` (sólo se elimina su copia privada y sus
  imports huérfanos si los hubiera).

### 3. UI en `GddScreen` (autocontenida, sin tocar `App.kt`)

- Envolver el contenido en `Scaffold(snackbarHost = { SnackbarHost(hostState) })`
  con `rememberCoroutineScope()` **dentro de `GddScreen`**.
- En el `Row` del encabezado, junto a "Volver", agregar `TextButton`
  **"Compartir"** con `enabled = notes.isNotEmpty()`.
- `onClick`: `val now = currentTimestampLabel()`;
  `val markdown = buildGddMarkdown(notes, now)`;
  `val ok = sharer.share(markdown)`; `scope.launch { hostState.showSnackbar(if
  (ok) "Listo para compartir" else "No disponible") }`.
- No se agregan parámetros a `GddScreen` (obtiene el sharer por
  `rememberGddSharer()` y el timestamp por `currentTimestampLabel()`), por lo que
  `App.kt` **no cambia**.

### 4. Esquema, dependencias e `init.sh`

- **Sin esquema**: `gdd-export` no agrega `.sqm` ni toca `Schema.version`. La v6
  la introduce `offline-pending-retry` (paralela): confirmar `Schema.version == 6`
  y que el diff de `gdd-export` no toca `core/src/commonMain/sqldelight/**`.
- **Sin dependencias nuevas**: `Scaffold`/`SnackbarHost`/`rememberCoroutineScope`
  ya están (`compose.material3` + coroutines); el timestamp usa APIs de
  plataforma.
- **`init.sh` sin cambios**; el gate sigue no bloqueante.

### Riesgos y decisiones

- **`now` en `:core`**: se inyecta como `String` ya formateado para evitar una
  dependencia de fecha en `:core`; en tests es fijo → salida determinista.
- **Snackbar dentro de `GddScreen`**: minimiza conflictos con `offline-pending-retry`
  (que sí toca `App.kt`) a costa de un `Scaffold` local; aceptado.
- **Share en simulador iOS**: no aporta evidencia interactiva (sin input
  automation); se declara honestamente y se ejercita el `commonMain` en Android.

## Expected File Changes

- `core/src/commonMain/kotlin/com/playbook/core/gdd/GddMarkdown.kt` — crear;
  `buildGddMarkdown` + helpers (`title`/`bodyRest`/`metadataLine`/`escapeMarkdown`).
- `core/src/commonTest/kotlin/com/playbook/core/gdd/GddMarkdownCheck.kt` — crear;
  `verifyGddMarkdown()` (escenarios 1–8).
- `core/src/androidUnitTest/kotlin/com/playbook/core/gdd/GddMarkdownAndroidTest.kt`
  — crear; `@Test` que corre el helper en JVM.
- `core/src/iosTest/kotlin/com/playbook/core/gdd/GddMarkdownIosTest.kt` — crear;
  idem iOS.
- `composeApp/src/commonMain/kotlin/com/playbook/app/GddSharer.kt` — crear;
  `GddSharer` + `rememberGddSharer()` + `currentTimestampLabel()` (`expect`).
- `composeApp/src/androidMain/kotlin/com/playbook/app/GddSharer.android.kt` —
  crear; `actual` (Intent `ACTION_SEND`/chooser + `SimpleDateFormat`).
- `composeApp/src/iosMain/kotlin/com/playbook/app/GddSharer.ios.kt` — crear;
  `actual` (`UIActivityViewController` + `NSDateFormatter`).
- `composeApp/src/iosMain/kotlin/com/playbook/app/IosViewControllers.kt` — crear;
  `internal fun topViewController()` compartido.
- `composeApp/src/iosMain/kotlin/com/playbook/app/ImageCapture.ios.kt` — modificar;
  borrar su `topViewController()` privado y reusar el helper compartido (sin
  tocar la lógica de captura).
- `composeApp/src/commonMain/kotlin/com/playbook/app/GddScreen.kt` — modificar;
  `Scaffold`/snackbar + botón "Compartir" + share.
- `ARCHITECTURE.md` — modificar; sección "GDD": puerto de share + proyección de
  export.
- `docs/technical-discovery.md` — modificar; decisión "GDD export".
- `AGENTS.md` — modificar al cerrar; sólo la línea "Próxima feature en cola".
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado y evidencia.

No se esperan cambios en `App.kt`, `MainActivity.kt`, `MainViewController.kt`,
`core/src/commonMain/sqldelight/**`, `core/build.gradle.kts`,
`composeApp/build.gradle.kts`, `gradle/libs.versions.toml`, `init.sh`, `iosApp/**`
ni `DESIGN.md`.

## Visual Design Impact

- UI involved: yes (mínimo). Design source: `DESIGN.md` (provisional); sin
  dirección visual nueva.
- Screens or states affected: `GddScreen` (nuevo botón "Compartir" en el encabezado
  + snackbar de resultado, autocontenido). El botón se comunica con texto, no sólo
  color, y respeta targets ≥ 48dp.
- New design artifact required: no — no hay pantallas/tokens nuevos; el botón y el
  snackbar siguen los componentes `material3` vigentes.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — sección "GDD": `buildGddMarkdown` (proyección
  pura de export, sin tabla) y el puerto `GddSharer` (share de texto en memoria,
  sin archivos/permisos); `GddScreen` suma "Compartir".
- `CONSTRAINTS.md`: not needed — no surge una regla MUST/MUST NOT durable nueva
  (el "sin archivos/permisos/sin tocar `App.kt`" es alcance de la feature).
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola".
- Other docs: `docs/technical-discovery.md` **update** (nueva sección "GDD Export
  Decisions (gdd-export)"); `DESIGN.md`/`docs/design/ui-ux-brief.md` **not needed**
  (sin dirección visual nueva); `CONTEXT.md`/`docs/domain-model.md` **not needed**
  (el GDD ya está definido; el export no cambia el dominio);
  `feature_list.json`/`PROGRESS.md` **update** al cerrar.

## Implementation Plan

1. Crear `GddMarkdown.kt` en `:core` (`buildGddMarkdown` + helpers).
2. Escribir `verifyGddMarkdown()` + runners Android/iOS; correr `:core` en ambas
   plataformas.
3. Crear `GddSharer.kt` (`expect`) + `GddSharer.android.kt`/`GddSharer.ios.kt`
   (`actual`); extraer `topViewController()` a `IosViewControllers.kt` y reusarlo.
4. Modificar `GddScreen.kt` (`Scaffold`/snackbar + botón "Compartir" + share),
   sin tocar `App.kt`.
5. Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`.
6. Correr verificación + controles negativos, smoke manual Android, registrar
   evidencia; actualizar `feature_list.json`, `PROGRESS.md` y `AGENTS.md`.

## Implementation Tasks

- [x] Crear `buildGddMarkdown(notes, now)` en `:core` con el "Format Contract".
- [x] Implementar el escapado de Markdown del contenido de usuario.
- [x] Escribir `verifyGddMarkdown()` + `GddMarkdown{Android,Ios}Test`.
- [x] Crear `GddSharer` (`expect`/`actual`) y `currentTimestampLabel()`.
- [x] Extraer `topViewController()` a un helper interno de `iosMain` y reusarlo.
- [x] Sumar "Compartir" + snackbar en `GddScreen` (sin tocar `App.kt`).
- [x] Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`.
- [x] Correr verificación + controles negativos y registrar evidencia.
- [x] Smoke manual Android del share; declarar el límite de iOS.
- [x] Actualizar `feature_list.json`, `PROGRESS.md` y `AGENTS.md`.

## Implementation Findings (implementer, 2026-10-15)

- **`Schema.version` real = 5, no 6.** La spec asumía `== 6` porque
  `offline-pending-retry` (que introduce la v6) se trabajaría en paralelo; en este
  worktree esa feature **no** está implementada, así que
  `PlaybookDatabaseImpl.Schema.version == 5`, con `1.sqm`–`4.sqm` y sin `.sqm`
  nuevo. `gdd-export` no toca el esquema: el requisito real ("no agrega `.sqm` ni
  cambia `Schema.version`") se cumple. No cambia el alcance.
- **Smoke interactivo no ejecutado.** No había `adb`/emulador Android en el
  entorno y el gesto de compartir iOS no es automatizable (sin
  `idb`/`cliclick`/`applesimutils`). Se declaró honestamente y no se fabricó
  evidencia; la generación del documento se cubre con tests del core en dos
  plataformas y la app compila/enlaza en Android e iOS.

## Verification Plan

- `./gradlew :core:testDebugUnitTest` → BUILD SUCCESSFUL; `GddMarkdownAndroidTest`
  1/1 (escenarios 1–8) + `GddProjectionAndroidTest` y el resto del core verdes, 0
  failures.
- `./gradlew :core:iosSimulatorArm64Test` (macOS) → el mismo helper verde en iOS.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (UI + share Android).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL
  (share iOS compila/enlaza).
- Esquema: **`gdd-export` no agrega `.sqm`**; confirmar `Schema.version == 6`
  (la v6 es de `offline-pending-retry`, trabajada en paralelo) y
  `git diff -- core/src/commonMain/sqldelight/**` vacío. Sin dependencias nuevas
  (`git diff -- gradle/libs.versions.toml composeApp/build.gradle.kts` vacío).
- **Controles negativos** (restaurar byte-idéntico y re-verificar en verde):
  (a) no omitir secciones vacías (dejar las 3 de `track` siempre) → falla el
  Scenario 3; (b) repetir el `body` completo bajo el título en vez del resto →
  falla el Scenario 4; (c) un escapado que ignore el set → falla el Scenario 6.
- `./init.sh` → exit 0; ejecuta el gate no bloqueante actual, **no** inicia dev
  servers ni simuladores, y puede imprimir comandos manuales.
- **Smoke manual Android** (emulador): abrir el GDD con Notas → "Compartir"
  habilitado → share sheet con el texto Markdown; GDD vacío → botón
  deshabilitado. **iOS**: `xcodebuild`/link + install/launch; el gesto de compartir
  no se automatiza (sin `idb`/`cliclick`), declarado honestamente.
- E2E persistente: **no aplica** — no hay harness de UI/E2E y `init.sh` no levanta
  simuladores. La generación del documento se cubre con tests del core en dos
  plataformas y la UI con smoke manual, igual que en las features de UI previas.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest`/`:core:iosSimulatorArm64Test` con
  `GddMarkdown` verde (nombres/conteos) y los tests previos verdes.
- Salida de `:composeApp:assembleDebug` y del link del framework iOS.
- Confirmación de que `gdd-export` **no** agrega `.sqm` (`Schema.version == 6`,
  `git diff` del esquema vacío), sin dependencias nuevas y sin cambios en
  `App.kt`/`MainActivity`/`MainViewController`/`init.sh`/`iosApp`.
- Controles negativos (a)/(b)/(c) reproducidos y restaurados, con el test que
  falla.
- Smoke manual Android: capturas/UI dump del share con el Markdown y del botón
  deshabilitado sin Notas; nota del límite de iOS.
- Salida de `./init.sh` (exit 0) sin procesos de larga duración.

## Validator Checklist

- [ ] Alcance respetado: export a **texto Markdown en memoria**; sin archivos/PDF,
      permisos, backend, adjuntos/notas relacionadas en el documento, ni cambios de
      esquema/deps/`App.kt`.
- [ ] Escenarios de aceptación 1–10 pasan.
- [ ] `buildGddMarkdown` es determinista y cumple el "Format Contract"
      (encabezado sin fecha, total singular/plural, orden fijo, omisión de vacías,
      `Sin track`, título = 1ra línea, cuerpo = resto, fallback `Nota <now>`,
      metadatos, escapado del contenido de usuario), cubierto en Android/JVM e iOS.
- [ ] `GddScreen` muestra "Compartir" deshabilitado sin Notas, genera el Markdown
      y llama `GddSharer.share` con snackbar "Listo para compartir"/"No
      disponible"; `App.kt` sin cambios.
- [ ] `ImageCapture.ios.kt` reusa el helper `topViewController()` compartido; su
      lógica de captura no cambia.
- [ ] `gdd-export` no agrega `.sqm` ni cambia `Schema.version` (`== 6`), sin deps
      nuevas; `init.sh` sin cambios y no bloqueante.
- [ ] `ARCHITECTURE.md` y `docs/technical-discovery.md` actualizados (`AGENTS.md`
      sólo la línea de próxima feature); `feature_list.json` y `PROGRESS.md` con la
      evidencia.
- [ ] No hay E2E persistente y la spec lo justifica; límite de iOS declarado.
