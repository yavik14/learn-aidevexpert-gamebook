# Feature Implementation Spec: Export del GDD con la ficha del Proyecto

## Source Feature

- `id`: `gdd-export-hybrid` · `area`: `gdd` · `status`: `not_started` (al planificar)
- `depends_on`: `["gdd-export", "gdd-document-hybrid"]` (ambas `accepted`)
- `source`: `feature_list.json`. Verificación provisional a cerrar: test de
  `:core` del Markdown híbrido + smoke Android del share.

## Goal

Que el Markdown exportado incluya la **ficha del Proyecto** (mismos campos y
orden que `GddDocument`) además de las Notas por `Track`. Sigue siendo una función
pura en `:core`; `GddScreen` la invoca y entrega el texto al `GddSharer`, sin
archivos, PDF, permisos, adjuntos, cambios de esquema ni de share.

## Non-Goals

- PDF, archivos, `FileProvider`, permisos, guardar o previsualizar el Markdown.
- Adjuntos, miniaturas, Notas relacionadas o contenido fuera de `GddDocument`.
- Esquema/migraciones/repositorios, modelos, IA, red o dependencias nuevas.
- Rediseño o parámetros nuevos en `GddScreen`; cambios en `App.kt`,
  `MainActivity`/`MainViewController`, `init.sh` o `iosApp`.
- Nuevo mecanismo de share (`GddSharer` no cambia) ni persistir el Markdown.

## Job Story

When ya tengo ficha y Notas, I want compartir el GDD en un Markdown, so I can
sacar el diseño de la app sin reescribirlo.

## Users And Permissions

- Autor single-user local-first: comparte Proyecto y Notas; sin auth, permisos
  nuevos ni red. Desarrollador: Gradle/tests con IA fake, sin secrets.

## Scope Decisions

1. **Firma + overload.** `buildGddMarkdown(project, notes, now)` es la API; el
   overload `(notes, now)` delega con `project = null` y queda byte-idéntico.
2. **Una proyección.** `buildGddDocument(project, notes)` aporta ficha y
   secciones; no se reagrupa ni se duplican Notas.
3. **Título en cabecera.** `# GDD — <título>` o `# GDD — Playbook`; no se repite.
4. **Ficha sólo con campos.** `project == null` o `fields.isEmpty()` → sin
   `## Proyecto`.
5. **Sin Notas no hay secciones.** Se mantiene la omisión; `Total` da el conteo.
6. **Compartir con contenido exportable.** Habilitado con Notas o ficha con
   título/campos; deshabilitado sin nada. Sin cambios en `App.kt`.
7. **Snackbar/share intactos.** `"Listo para compartir"`/`"No disponible"`;
   `GddSharer` y `currentTimestampLabel()` no cambian.

## Format Contract (determinista)

`buildGddMarkdown(project, notes, now)` produce, en orden:

1. `# GDD — <título escapado>` o `# GDD — Playbook`.
2. Línea en blanco y `Total: N notas` (singular `Total: 1 nota`).
3. Si `project != null` y `fields.isNotEmpty()`: línea en blanco, `## Proyecto`,
   línea en blanco y un ítem por campo en el orden de `GddDocument`:
   `- **<Label>:** <valor escapado>` (listas unidas con `, `). Para `Mecánicas
   clave`: `- **Mecánicas clave:**` y una línea por mecánica con dos espacios de
   indentación, `  - <Categoría> · <descripción escapada>`.
4. Línea en blanco y las secciones no vacías de `GddDocument.sections`, con el
   formato vigente de `gdd-export` (labels, Notas, metadatos y escapado).
5. Documento termina en `\n`; bloques separados por una línea en blanco.

Normalización (sin mutar el Proyecto): cada valor se colapsa a una línea (runs de
espacios/tabs/saltos → un espacio) y se escapa; el título sigue la misma regla.
`Mecánicas clave` se parte por los `\n` de `GddDocument`; cada línea no vacía se
colapsa, escapa y se emite como viñeta. Labels, prefijos, `Total:`, `## `,
títulos de sección, `status.code` y `Nota <now>` son generados y **no** se
escapan. Se reutiliza el set de `GddMarkdown.kt`
(`` \ ` * _ { } [ ] ( ) # + - . ! | > ``); no se duplica.

Ejemplo canónico (`now = "2026-10-15 14:30"`; Proyecto completo + 2 Notas):

```
# GDD — Crónica de Ceniza

Total: 2 notas

## Proyecto

- **Géneros:** JRPG, Zelda\-like
- **Core Loop:** Explorar y combatir
- **Audiencia:** Adultos
- **Concepto:** Un reino en cenizas
- **Inspiraciones:** Dark Souls, Hollow Knight
- **Fantasía:** Ser un héroe
- **Mecánicas clave:**
  - Movimiento · Correr
  - Interacción · Hablar
  - Conflicto · Pelear
- **Fuera de alcance:** Multijugador

## mecánicas

### Jefe final
aparece en el nivel 3
Géneros: combate, jefe — Estado: capturada

## Sin track

### Protagonista sin nombre
Géneros: lore — Estado: indexada
```

## Acceptance Scenarios

1. **Documento completo.** Given Proyecto con título/todos los campos y Notas de varios `track`, When se exporta, Then la salida es el ejemplo canónico.
2. **Sin Proyecto (compatibilidad).** Given `project == null`, When se usa la firma nueva y el overload viejo, Then ambas salidas son byte-idénticas al contrato de `gdd-export`; no aparece `## Proyecto`.
3. **Proyecto sin Notas.** Given Proyecto visible y `notes.isEmpty()`, Then cabecera + `Total: 0 notas` + ficha (si tiene campos), sin secciones.
4. **Proyecto sin campos.** Given Proyecto con título y `fields == emptyList()` (o `project == null`), Then no aparece `## Proyecto`; el título, si existe, va en la cabecera. Sin Notas, termina tras `Total: 0 notas`.
5. **Sin Proyecto y sin Notas.** Given `project == null` y `notes.isEmpty()`, Then la salida es `# GDD — Playbook\n\nTotal: 0 notas\n` y “Compartir” está deshabilitado.
6. **Orden, listas y mecánicas.** Given la ficha del ejemplo, Then el orden es el de `GddDocument`; las listas preservan orden y se unen con `, `; las mecánicas salen como viñetas `Categoría · descripción`.
7. **Escapado.** Given título/textos/listas/descripciones con `#`, `*`, `_`, `[`, `]`, `-`, `.`, `!`, `|`, `>`, backslash, Then salen escapados en cabecera y ficha; labels, prefijos, `status.code` y `## <title>` no se escapan; las Notas conservan su escapado.
8. **Determinismo y no duplicación.** Given la misma entrada y `now`, Then dos llamadas devuelven lo mismo; no se mutan Proyecto/listas; ninguna Nota se pierde ni se repite; las secciones conservan el orden de `buildGdd`.
9. **UI — Compartir y snackbar.** Given Proyecto con contenido y/o Notas, Then “Compartir” está habilitado y llama `buildGddMarkdown(project, notes, currentTimestampLabel())` + `GddSharer.share`, con snackbar `"Listo para compartir"`/`"No disponible"`; con Proyecto vacío sin título y sin Notas está deshabilitado. `App.kt` y `GddSharer` no cambian.
10. **Gate honesto.** Given la feature implementada, When corren tests de `:core` (Android/JVM e iOS), `assembleDebug`, `linkDebugFrameworkIosSimulatorArm64` y `CI=true ./init.sh`, Then todo pasa; `Schema.version == 8`, `.sqm` = `1`..`7`, sin `.sqm`/deps nuevas ni cambios en `App.kt`/`init.sh`/`iosApp`.

## Repository Research

Inspected: `AGENTS.md`, `PROGRESS.md`, `feature_list.json`,
`docs/mvp-execution-plan.md`, `CONTEXT.md`, `docs/domain-model.md`, `DESIGN.md`;
specs `gdd-export.md`, `gdd-document-hybrid.md`, `game-project-persistence.md`;
`docs/technical-discovery.md`, `docs/risks-and-open-questions.md`;
`core/.../gdd/{GddMarkdown,GddDocument,GddProjection}.kt`, `GddMarkdownCheck.kt`
+ runners; `composeApp/.../{GddScreen,GddSharer,App}.kt` e `init.sh`.

Patterns: proyección pura en `:core` (sin SQLDelight/IA), UI fina que invoca
puertos, tests `commonTest` + runner por plataforma, generados no escapados y
contenido de usuario escapado, gate no bloqueante con IA fake.

Gaps: `buildGddMarkdown` sólo acepta Notas y “Compartir” sólo se habilita con
Notas; no hay tests del Markdown híbrido.

## Technical Approach

- En `GddMarkdown.kt`, agregar el overload principal; el actual delega:
  `buildGddMarkdown(notes, now) = buildGddMarkdown(null, notes, now)`.
- Flujo: `val document = buildGddDocument(project, notes)`; cabecera con
  `document.project?.title` (colapsado/escapado) o `"Playbook"`; `Total` como
  hoy; si hay campos, `## Proyecto` + `projectFieldLines(fields)`; luego
  `document.sections.filter { it.notes.isNotEmpty() }` con `section.title` y
  `noteBlock(note, now)`. Helpers: `projectFieldLines`, `singleLine`,
  `mechanicLines`; reutilizar `escapeMarkdown` (subir a `internal` si hace falta)
  y **no** duplicar set ni formateo de Notas.
- En `GddScreen.kt`, `onClick` usa
  `buildGddMarkdown(project, notes, currentTimestampLabel())` + `sharer.share` +
  snackbar; `enabled = notes.isNotEmpty() || document.project?.let { it.title !=
  null || it.fields.isNotEmpty() } == true`.
- Sin cambios en `GddDocument.kt`, `GddProjection.kt`, modelos, repositorios,
  esquema v8, build files, `GddSharer`, `App.kt`, `init.sh`, `iosApp` ni deps.

## Expected File Changes

- `core/.../gdd/GddMarkdown.kt` — modificar; overload, cabecera, ficha y helpers.
- `core/src/commonTest/.../gdd/GddMarkdownHybridCheck.kt` — crear;
  `verifyGddMarkdownHybrid()` (escenarios 1–8).
- `core/src/{androidUnitTest,iosTest}/.../gdd/GddMarkdownHybrid{Android,Ios}Test.kt`
  — crear; runners.
- `composeApp/.../GddScreen.kt` — modificar; llamada y habilitación.
- `ARCHITECTURE.md`, `docs/technical-discovery.md` — actualizar al implementar.
- `feature_list.json`, `PROGRESS.md`, `AGENTS.md` — actualizar al cerrar; **no**
  en planning.

Sin cambios en `GddDocument.kt`, `GddProjection.kt`, `GddMarkdownCheck.kt`,
`GddSharer*`, `App.kt`, esquema/`.sqm`, repositorios, build files, `init.sh` ni
`iosApp/**`.

## Visual Design Impact

- UI involved: mínimo (botón/snackbar existentes); `DESIGN.md` provisional, sin
  dirección visual nueva. “Compartir” habilitado también con ficha sin Notas.
  New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md` y `docs/technical-discovery.md`: **update** — export híbrido.
- `CONSTRAINTS.md`/`CONTEXT.md`/`docs/domain-model.md`: **not needed**.
- `AGENTS.md`: **update (acotado)** — sólo la cola al cerrar.
- `feature_list.json`/`PROGRESS.md`: **update** al implementar/validar.

## Implementation Plan

1. Extender `GddMarkdown.kt`; escribir `verifyGddMarkdownHybrid()` + runners.
2. Actualizar `GddScreen.kt`, docs durables y registrar evidencia.

## Implementation Tasks

- [x] Agregar `buildGddMarkdown(project, notes, now)` y delegar el overload viejo.
- [x] Emitir cabecera, `## Proyecto`, campos/orden, listas y mecánicas.
- [x] Reutilizar `buildGddDocument`/`buildGdd`, `escapeMarkdown` y `noteBlock` sin
      duplicar Notas ni el set de escapado.
- [x] Escribir `verifyGddMarkdownHybrid()` + runners Android/JVM e iOS.
- [x] Actualizar `GddScreen` sin tocar `App.kt`.
- [x] Actualizar docs durables y registrar evidencia/estado.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` y
  `:core:iosSimulatorArm64Test --rerun-tasks` → `GddMarkdownHybrid*Test` verde
  (escenarios 1–8) + previos; `assembleDebug` y link iOS → BUILD SUCCESSFUL.
- `CI=true ./init.sh` → exit 0, sin dev servers; sin `local.properties`, exportar
  `ANDROID_HOME` o crear uno **sin** API key.
- Esquema: `Schema.version == 8`, `.sqm` = `1`..`7`, sin `.sqm` nuevo; `git diff`
  vacío en `core/.../sqldelight/**`, build files y `GddSharer*`.
- **Controles negativos** (restaurar byte-idéntico): ignorar la ficha, agregar
  Notas fuera de `GddDocument`, omitir el título de cabecera, no escapar un valor
  o habilitar “Compartir” sólo con Notas → fallan Scenarios 1, 8, 1, 7 y 9.
- **Smoke Android** (si hay `adb`/emulador): Proyecto completo + Notas; Proyecto
  sin Notas; Notas sin Proyecto; Proyecto vacío sin Notas (botón deshabilitado) y
  snackbar; evidencia en `composeApp/build/smoke-evidence/gdd-export-hybrid/`. Si
  no hay dispositivo, declararlo y dejar `passing` con tests/build/gate verdes.
- **E2E**: no aplica — sin harness UI/E2E y `init.sh` no levanta simuladores; la
  lógica se cubre en `:core` y la UI con smoke.

## Evidence To Capture

- Tests de `:core` (Android/JVM e iOS) con `GddMarkdownHybrid*Test` y previos
  verdes; `assembleDebug`, link iOS y `CI=true ./init.sh` exit 0.
- Ejemplo canónico byte a byte, overload viejo compatible, esquema v8/deps
  intactos, controles negativos y smoke Android (o salvedad honesta).

## Implementation Findings (implementer, 2026-10-20)

- El `Zelda-like` del ejemplo canónico sale con el guion escapado porque `-`
  pertenece al set; el ejemplo se corrigió para reflejarlo.
- Kotlin/Native exigió nombres únicos para los helpers privados de test
  (`hybridNote`/`hybridProject`) para evitar colisión con `gddNote` de
  `GddMarkdownCheck`.

## Validator Checklist

- [ ] Alcance respetado: Markdown híbrido en memoria; sin PDF/archivos/permisos,
      adjuntos, esquema, IA, red, `App.kt` ni nuevo share; Escenarios 1–10 pasan.
- [ ] Ficha con campos/orden de `GddDocument`, sin repetir título ni duplicar
      Notas; secciones y escapado de Notas vigentes.
- [ ] `buildGddMarkdown(notes, now)` byte-idéntico y delegando al overload nuevo.
- [ ] “Compartir” se habilita con ficha y/o Notas y se deshabilita sin contenido
      exportable; snackbar y `GddSharer` intactos.
- [ ] Sin cambios de esquema/deps/`App.kt`/`init.sh`/`iosApp`; gate verde; docs y
      evidencia actualizadas; E2E no aplica.
