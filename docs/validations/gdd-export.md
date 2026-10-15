# Validación independiente: `gdd-export`

- **Feature id:** `gdd-export`
- **Spec:** `docs/specs/gdd-export.md`
- **Fecha:** 2026-10-15
- **Rol:** validator (independiente del implementer)
- **Worktree:** `worktree/gamebook/gdd-export` (rama `feature/gdd-export`, base `20be2ed`)
- **Estado en `feature_list.json` al validar:** `passing` (implementada, auto-verificada, sin commitear)
- **Veredicto:** **accept**

## Alcance validado

Export del GDD como **texto Markdown en memoria**: proyección pura
`com.playbook.core.gdd.buildGddMarkdown(notes, now)` en `:core` (reusa `buildGdd`,
omite secciones vacías, cumple el "Format Contract") + puerto `GddSharer`
(expect/actual: `ACTION_SEND` `text/plain` en Android, `UIActivityViewController`
en iOS) + botón "Compartir" (deshabilitado sin Notas) y snackbar en `GddScreen`
(`Scaffold`/`SnackbarHostState` local, sin nuevos parámetros → `App.kt` sin
cambios). `topViewController()` se extrajo a `IosViewControllers.kt` compartido y
`ImageCapture.ios.kt` lo reusa sin cambiar su lógica.

## Checks re-ejecutados por el validador (no se confió en el resumen)

| Check | Comando | Resultado |
| --- | --- | --- |
| Tests core Android/JVM + iOS | `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks` | BUILD SUCCESSFUL (20 tareas); **43 tests, 0 failures/errors** en cada plataforma |
| `GddMarkdown*` presente | inspección de `core/build/test-results/*/TEST-*.xml` | `GddMarkdownAndroidTest` 1/0 e `GddMarkdownIosTest` 1/0; `GddProjection{Android,Ios}Test` 1/0 |
| Build Android + link iOS | `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` | BUILD SUCCESSFUL (57 tareas) |
| Gate estándar | `CI=true ./init.sh` | exit 0; sin dev servers ni simuladores; `init.sh` sin cambios |
| Esquema | `find core/src -name '*.sqm'` + código generado | sólo `1.sqm`–`4.sqm`; `PlaybookDatabaseImpl.kt:39-40` → `override val version … get() = 5` |
| Paths protegidos | `git diff --name-only` del set protegido | **vacío** (`Note.sq`, `Note.kt`, `repository/**`, `App.kt`, `MainActivity.kt`, `MainViewController.kt`, `init.sh`, `iosApp/**`, `gradle/**`, ambos `build.gradle.kts`) |
| `feature_list.json` | parseo JSON | válido; entrada `passing`, `depends_on ["gdd-view"]`, 9 líneas de evidencia |

## Format Contract (verificado contra el código y el documento canónico)

`buildGddMarkdown` cumple todas las cláusulas: encabezado `# GDD — Playbook` +
línea en blanco + `Total: N notas` (singular `Total: 1 nota`, `0 notas` para lista
vacía, **sin fecha**); orden fijo `mecánicas`, `personajes`, `historia`,
`Sin track` con omisión de vacías y `Sin track` sólo si hay Notas sin track;
`### <título>` = primera línea del `body` (trim, escapada), cuerpo = **resto**
(nunca repite la primera línea), body de una línea → sólo heading + metadatos;
fallback `Nota <now>` (no escapado) con body en blanco; metadatos
`Etiquetas: a, b — Estado: <code>` (Etiquetas escapadas, `status.code` no; sin
Etiquetas → sólo `Estado: …`); set de escape exacto de 17 caracteres
`` \ ` * _ { } [ ] ( ) # + - . ! | > `` aplicado sólo a contenido de usuario;
determinismo (`now` sólo influye en el fallback) y documento terminado en `\n`.
El ejemplo canónico de la spec se asserta verbatim en `verifyGddMarkdown()`.

## Control negativo propio (independiente del implementer)

1. **Control A** — dejar sólo el total en plural (`"Total: $count notas"`):
   `GddMarkdownAndroidTest > buildsDeterministicMarkdown FAILED`
   (`java.lang.AssertionError`). Restaurado desde backup → sha256
   `e165dae18f86ce4ddc401a8de495fdbde0788a1a9ce8b00c9fc2b5fe16e4c7b5` (idéntico);
   suite verde 43/0 en ambas plataformas.
2. **Control B** — quitar `{`, `}`, `+` de `MARKDOWN_SPECIAL_CHARS`: los tests
   **siguen pasando** → evidencia del hallazgo **L1** (gap de cobertura en el
   test de escapado; la implementación sí incluye esos caracteres). Restaurado
   byte-idéntico y verde 43/0.

## Hallazgos por severidad

**Low (no bloqueantes):**

- **L1 — Cobertura del test de escapado.** `{`, `}`, `+` están en el set de
  implementación pero no se ejercitan en `verifyGddMarkdown()` (el input
  "tricky" los omite), probado por el Control B. La implementación es correcta e
  íntegra. *Follow-up:* agregar `{ } +` a los inputs del escenario 6.
- **L2 — Sin smoke interactivo para el escenario 9.** No hay `adb`/emulador ni
  input automation iOS (`idb`/`cliclick`/`applesimutils` ausentes); el botón y el
  snackbar se verificaron por revisión estática + compilación/enlace Android/iOS.
  Declarado honestamente, consistente con la norma del repo.
- **L3 — Nit de bookkeeping.** La spec marca `[x] Smoke manual Android del share`
  mientras `Implementation Findings` dice que **no** se ejecutó. Recomendado
  desmarcar o anotar.

**Info (sin acción):**

- La spec todavía dice `Schema.version == 6` (asunción de `offline-pending-retry`,
  no integrada en este worktree). La implementación **no** tergiversa el valor:
  `Implementation Findings`, `feature_list.json`, `PROGRESS.md` y
  `technical-discovery.md` registran el real **v5** con `1.sqm`–`4.sqm` y sin
  `.sqm` nuevo. Honesto y fuera de alcance para bloquear.
- `catch (_: Throwable)` amplio en Android es defendible (devuelve `false` en
  lugar de crashear si el share no está disponible).

## Seguridad

Sin hallazgos de seguridad/privacidad. Sin permisos nuevos, sin `FileProvider`,
sin archivos/almacenamiento compartido, sin red/backend, sin secrets/keystores;
el contenido compartido son las Notas propias del usuario, sólo por acción
explícita, vía el share sheet del sistema. `git status` sin keystores/secrets.

## Documentación / harness

`ARCHITECTURE.md` (sección GDD: `buildGddMarkdown` + `GddSharer`),
`docs/technical-discovery.md` (nueva sección "GDD Export Decisions"),
`AGENTS.md` (sólo la línea de próxima feature), `feature_list.json` (estado +
evidencia) y `PROGRESS.md` (Session 044) actualizados y consistentes.
`CONSTRAINTS.md`/`DESIGN.md` correctamente no requeridos. `init.sh` sin cambios y
sigue siendo un gate real no bloqueante.

## Follow-up requerido

Ninguno bloqueante. Limpiezas opcionales recomendadas: ejercitar `{ } +` en el
test de escapado (L1) y anotar el checkbox de smoke en la spec (L3).
