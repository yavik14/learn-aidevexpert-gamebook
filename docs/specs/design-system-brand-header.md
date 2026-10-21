# Feature Implementation Spec: Design system Playbook y cabecera de marca

## Source Feature

- `id`: `design-system-brand-header`; `area`: `ui`; `status`: `not_started` al planificar.
- `depends_on`: `main-screen-playbook-shell`, `first-run-project-onboarding`,
  `gdd-document-hybrid`, `save-answer-as-note`, `tts-read-aloud`, `ai-real-chat`
  (todas `accepted`); `source`: `feature_list.json`.
- Rama `feature/design-system-brand-header` desde `origin/develop` (`547b024`).
  **Una** feature combinada: paleta + tipografía + estilos + cabecera de marca.

## Goal

Toda la app usa un theme claro Material 3 propio (`PlaybookTheme`) con la paleta,
tipografía, formas y tokens extra de `DESIGN.md`, y una cabecera común: icono +
“Playbook” en onboarding/principal y **Back → icono → título** en secundarias. Se
reemplaza el `MaterialTheme { }` default y se eliminan colores/tamaños ad-hoc.

## Non-Goals (vinculantes)

- **Sólo modo claro:** sin dark theme, `isSystemInDarkTheme`, `dynamicColor` ni
  `darkColorScheme`.
- **Sin splash nativo** (ni `core-splashscreen`, ni actividad/screen nueva, ni
  cambio del icono de launcher). “Primera pantalla” = onboarding.
- **Sin logo/wordmark nuevo:** la marca es `docs/design/icon.png`; el launcher no
  se toca.
- Sin cambios de producto/datos/IA/esquema/navegación/flujos, sin rediseñar
  pantallas ni dependencias nuevas salvo `compose.components.resources`.
- iOS congelado: sólo debe compilar; sin paridad ni smoke iOS.

## Job Story And Users

When abro Playbook o entro a una pantalla secundaria, I want reconocer la marca y
la jerarquía de un vistazo, so I can orientarme y leer sin ruido ni
inconsistencia. Autor único local-first, sin auth ni permisos nuevos; el
desarrollador corre Gradle/smoke Android con IA fake, sin red.

## Scope Decisions (cerradas)

1. Una feature: paleta + tipografía + estilos + cabecera.
2. Bricolage Grotesque (Regular/SemiBold) para headlines/títulos; JetBrains Mono
   (Regular/Medium) para body, labels, botones y chips; OFL versionadas en
   Compose resources.
3. Onboarding/principal = icono + “Playbook”; secundarias = Back → icono → título.
4. Sólo claro. 5. Primera pantalla = onboarding. 6. Tipografía literal a
   `DESIGN.md`. 7. Sin nuevo logo.
8. **Weight 700 → SemiBold 600:** Bricolage se versiona sólo Regular/SemiBold;
   `DESIGN.md` se actualiza para reflejar 600 (desviación documentada).

## Acceptance Scenarios

| # | Given / When / Then |
|---|---|
| 1 | **Theme global.** Given la app arranca, When se recorre cualquier destino, Then no participa `Color(0x…)` fuera de `theme/Color.kt` ni `MaterialTheme { }`; fondo `#F9F6F8`, superficie `#FFFFFF`, acción primaria `#0E4B54`. |
| 2 | **Tipografía.** Given título/cuerpo/label/botón/chip/badge, When se muestrea, Then títulos usan Bricolage SemiBold y el resto JetBrains Mono; `headlineLarge` = 34sp y no hay tamaños de texto en dp. |
| 3 | **Onboarding primero.** Given instalación sin Proyecto, When se abre, Then la primera superficie es el onboarding con cabecera de marca (sin Back), sin splash nativo ni pantalla intermedia. |
| 4 | **Principal.** Given Proyecto cargado, When se mira la cabecera, Then icono (decorativo) + “Playbook” + acciones Ver GDD/Brainstorming/Preguntar, con `contentDescription` único y target ≥48dp. |
| 5 | **Secundarias.** Given editor, GDD, Preguntar, Brainstorming y relacionadas, When se abren, Then cabecera Back (48dp, “Volver”) → icono (decorativo) → título; Back ejecuta el mismo `onBack`/`onCancel` y no se pierde navegación. |
| 6 | **GDD.** Given título largo y fuente 200 %, When se muestra, Then el título ocupa ≤2 líneas con ellipsis y lectura/Compartir quedan en una fila propia debajo, sin recortes. |
| 7 | **Scrim.** Given speed-dial abierto, When aparece el scrim, Then usa el token (tinta 32 %) en vez de `Color.Black`; tap/Back cierran y sigue oculto a accesibilidad. |
| 8 | **AA.** Given los tokens, When se calcula contraste WCAG, Then todo par usado ≥4.5:1 (texto) y ≥3:1 (no textual); derivados no conformes se ajustan y documentan. |
| 9 | **Fuente.** Given Android a 130 % y 200 %, When se recorren todas las pantallas, Then no hay texto recortado/superpuesto y los targets siguen ≥48dp. |
| 10 | **Gate.** Given la feature, When corre `CI=true ./init.sh`, Then exit 0 con IA fake y sin red; build/link iOS OK; sin cambios en datos, esquema, IA, `init.sh` ni `iosApp/**`. |

## Repository Research

### Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `DESIGN.md`, `ARCHITECTURE.md`,
  `docs/technical-discovery.md`, `docs/design/{paleta,icon}.png`.
- Specs: `docs/specs/main-screen-playbook-shell.md`,
  `docs/specs/first-run-project-onboarding.md`.
- UI: `App.kt` (`MaterialTheme { }` en `:146`), `NotesListScreen.kt`
  (`TopAppBar` en `:166`, `Color.Black.copy(alpha = 0.32f)` en `:148`),
  `FirstRunOnboardingScreen.kt`, `NoteEditorScreen.kt`, `GddScreen.kt`,
  `AskNotesScreen.kt`, `BrainstormingWizardScreen.kt`, `RelatedNotesScreen.kt`,
  `LabelChip.kt`, `GenrePicker.kt`, `EnrichmentStatus.kt`, `ReadAloudButton.kt`,
  `NoteThumbnail.kt`, `ImageGenerationDialog.kt`, `AudioTranscription.kt`,
  `Dictation.kt`; plataforma: `MainActivity.kt`, `MainViewController.kt`,
  `AndroidManifest.xml`, `res/values/{themes,colors}.xml`, `composeApp/build.gradle.kts`.

### Findings

- `Color(0x…|Color.Black…)` sólo en el scrim de la principal;
  `MaterialTheme\s*\{` sólo `App.kt:146`; no hay `fontSize`, `.sp`, `FontFamily`
  ni `Typography(` hardcodeados; no existe `composeResources` ni
  `components.resources`.
- Patrones: `App` dueño de navegación/estado; pantallas “tontas” con callbacks;
  sin DI/`ViewModel`/Flow; Material 3; chips/badges con texto y forma; sin
  `material-icons` en `commonMain` (glifos `Text` + `contentDescription`).
- Gaps: theme default (violeta/tipografía sistema); sin fuentes ni icono de
  cabecera en `composeResources`; secundarias sin top bar (GDD/relacionadas usan
  fila “Volver”; editor/Preguntar/wizard usan título sin Back); sin edge-to-edge
  ni light system bars.

## Technical Approach

### 1. Theme claro Material 3

- `theme/Color.kt` (único archivo con `Color(0x…)`), `theme/Type.kt`,
  `theme/Shapes.kt` (`small` 8dp, `medium` 16dp, `large` 24dp) y
  `theme/PlaybookTheme.kt` (`lightColorScheme` + `PlaybookShapes` +
  `playbookTypography()` + `CompositionLocalProvider` de tokens extra).
  `App.kt`: `MaterialTheme { … }` → `PlaybookTheme { … }`.
- M3: `primary #0E4B54`/`onPrimary #FFFFFF`, `primaryContainer #98E4FF`/
  `onPrimaryContainer #2C2926`, `secondary #6B4A2A`/`onSecondary #FFFFFF`,
  `secondaryContainer #FFE17D`/`onSecondaryContainer #2C2926`,
  `tertiary #FFE17D`/`onTertiary #2C2926`, `background #F9F6F8`/`onBackground
  #2C2926`, `surface #FFFFFF`/`onSurface #2C2926`, `surfaceVariant #EFEAF0`/
  `onSurfaceVariant #4A4440`, `outline #6F6862`, `outlineVariant #D8D2D6`,
  `error #C0392B`/`onError #FFFFFF`, `errorContainer #F7D9D5`/
  `onErrorContainer #7A1F16`, `scrim #2C2926`, `inverseSurface #2C2926`/
  `inverseOnSurface #F9F6F8`, `inversePrimary #98E4FF`.
- Tokens extra (`PlaybookColors`): `actionPrimary #0E4B54`, `actionAlt #6B4A2A`,
  `actionWarn #7A7A2E`, `trackMechanics #0E4B54`, `trackCharacters #6B4A2A`,
  `trackStory #7A7A2E`, `statusCaptured #4A4440`, `statusPending #7A7A2E`,
  `statusIndexed #0E4B54`, `statusFailed #C0392B`, `scrim Color(0x522C2926)`
  (tinta 32 %). Track/estado son para forma/borde/relleno, **no** texto sin AA.

### 2. Contraste AA

| Uso | Par | Ratio |
|---|---|---|
| Texto/base | Ink `#2C2926` / `#F9F6F8` | 13.5:1 |
| Texto/superficie | Ink / `#FFFFFF` | 14.4:1 |
| Acción | `#FFFFFF` / `#0E4B54` | 9.8:1 |
| Sobre celeste | Ink / `#98E4FF` | 10.2:1 |
| Sobre amarillo | Ink / `#FFE17D` | 11.2:1 |
| Error/superficie | `#C0392B` / `#FFFFFF` | 5.4:1 |
| Outline (no texto) | `#6F6862` / `#FFFFFF` | 5.5:1 |

`#7A7A2E` sobre base ≈4.2:1: sólo relleno/borde; para texto usar `#5F5F22`
(≈6.2:1). Todo par nuevo se mide con WCAG; si no alcanza se oscurece en
`Color.kt` y se documenta. Estados siguen con texto + forma, no sólo color.

### 3. Tipografía y fuentes (Compose resources)

Escala en `sp` (`size/lineHeight`): `headlineLarge` 34/40, `headlineMedium`
28/34, `headlineSmall` 24/30, `titleLarge` 20/26, `titleMedium` 18/24,
`titleSmall` 14/20 (Bricolage SemiBold); `bodyLarge` 16/24, `bodyMedium` 14/20,
`bodySmall` 13/18 (JetBrains Mono Regular); `labelLarge` 14/20, `labelMedium`
13/18, `labelSmall` 12/16 (JetBrains Mono Medium); `display*` Bricolage SemiBold
40/34/30. Sin `fontSize`/`.sp` fuera de `Type.kt`.

- Dependencia: `commonMain.dependencies { implementation(compose.components.resources) }`
  (para `Res`, `Font`, `painterResource`); en `composeApp/build.gradle.kts`:
  `compose.resources { packageOfResClass = "com.playbook.app.resources" }`.
- Recursos versionados: `composeResources/font/{bricolage_grotesque_regular,
  bricolage_grotesque_semibold,jetbrains_mono_regular,jetbrains_mono_medium}.ttf`;
  `composeResources/drawable/playbook_icon.png` (`sips -Z 192` desde
  `docs/design/icon.png`); `composeResources/files/licenses/OFL-BricolageGrotesque.txt`
  y `OFL-JetBrainsMono.txt`.
- Origen reproducible (descargar, `shasum -a 256`, commitear; sin red en gate):
  Bricolage commit `84745e5b96261ae5f8c6c856e262fe78d1d6efdd`, rutas
  `.../fonts/ttf/BricolageGrotesque-{Regular,SemiBold}.ttf` y `.../OFL.txt` en
  `raw.githubusercontent.com/ateliertriay/bricolage/<sha>/`; JetBrains Mono tag
  `v2.304`, `.../fonts/ttf/JetBrainsMono-{Regular,Medium}.ttf` y `.../OFL.txt`.
- Carga: `FontFamily(Font(Res.font.bricolage_grotesque_regular, Normal),
  Font(Res.font.bricolage_grotesque_semibold, SemiBold))` y equivalente mono con
  `Medium`; `Font(...)` es `@Composable`, así que familias/Typography se
  construyen dentro de `PlaybookTheme`. Icono con
  `painterResource(Res.drawable.playbook_icon)`; no cambia el launcher.

### 4. `PlaybookBrandHeader`

Nuevo `composeApp/.../PlaybookBrandHeader.kt`:

```kotlin
@Composable
fun PlaybookBrandHeader(
    modifier: Modifier = Modifier,
    title: String? = null,                 // null = modo marca
    onBack: (() -> Unit)? = null,          // sólo secundarias
    actions: @Composable RowScope.() -> Unit = {},
)
```

- Marca (`title == null`): icono 28dp decorativo (`contentDescription = null`,
  recorte `RoundedCornerShape(6.dp)`) + `Text("Playbook", titleLarge)` + acciones.
- Detalle: `IconButton` 48dp con `Text("←")` (`clearAndSetSemantics {}`,
  `contentDescription = "Volver"`, role Button) → icono 24–28dp decorativo →
  título `headlineSmall`, `weight(1f)`, ≤2 líneas con ellipsis → acciones.
- Contenedor `Surface(background)` + `fillMaxWidth().statusBarsPadding()
  .heightIn(min = 56.dp).padding(horizontal = 8.dp)`; consume la status bar una
  sola vez. Sin `TopAppBar` ni `material-icons`: si `←` no renderiza, usar
  `Volver` conservando el `contentDescription`.
- Usarlo como `topBar` de `Scaffold` en todas las pantallas; en GDD,
  lectura/Compartir quedan en fila debajo.

### 5. Auditoría de pantallas

| Archivo | Cambio |
|---|---|
| `NotesListScreen.kt` | `TopAppBar` → `PlaybookBrandHeader` (marca) + acciones; scrim → token. |
| `FirstRunOnboardingScreen.kt` | `Scaffold` + cabecera marca; sin Back. `FirstRunLoading` igual. |
| `NoteEditorScreen.kt` | `Scaffold` + detalle “Nueva nota”/“Editar nota”; Back = `onCancel`; se conserva Cancelar. |
| `GddScreen.kt` | detalle `GDD — <título>`; se retira “Volver” de la fila; acciones debajo; snackbar hereda theme. |
| `AskNotesScreen.kt` | detalle “Preguntar”; se retira “Volver” inferior; acción al final. |
| `BrainstormingWizardScreen.kt` | detalle “Brainstorming”; Back = `onCancel` (no “Atrás”); stepper intacto. |
| `RelatedNotesScreen.kt` | detalle “Notas relacionadas”; se retira “Volver” de la fila. |
| `LabelChip/GenrePicker/EnrichmentStatus` | sin estructura nueva; roles del theme (`primaryContainer` catálogo, `secondaryContainer` libre, `surfaceVariant`+`outline` badge). |
| `ReadAloudButton/NoteThumbnail/ImageGenerationDialog/AudioTranscription/Dictation` | sin flujo nuevo; auditar sp, targets y superficies. |
| `App.kt` | `PlaybookTheme`; `AlertDialog` heredan theme. |
| `MainActivity.kt` + `themes.xml`/`colors.xml` | `enableEdgeToEdge` light; `windowBackground #F9F6F8`, barras transparentes/base y flags light (values-v27 para nav bar). |

Todos los `Scaffold` consumen `innerPadding`; sin paddings manuales de barras ni
segundo `statusBarsPadding`. El scrim sigue dentro del contenido, con
`clearAndSetSemantics {}` y cierre por tap/Back.

## Expected File Changes

- Crear: `theme/{Color,Type,Shapes,PlaybookTheme}.kt`, `PlaybookBrandHeader.kt`,
  `composeResources/{font,drawable,files/licenses}/…` y (recomendado)
  `androidUnitTest/.../theme/PlaybookContrastTest.kt`.
- Modificar: `App.kt`, `NotesListScreen.kt`, `FirstRunOnboardingScreen.kt`,
  `NoteEditorScreen.kt`, `GddScreen.kt`, `AskNotesScreen.kt`,
  `BrainstormingWizardScreen.kt`, `RelatedNotesScreen.kt`, `MainActivity.kt`,
  `res/values/{themes,colors}.xml` (+`values-v27`), `composeApp/build.gradle.kts`.
- Actualizar al cerrar: `DESIGN.md`, `ARCHITECTURE.md`,
  `docs/technical-discovery.md`, `CONSTRAINTS.md`,
  `docs/risks-and-open-questions.md`, `feature_list.json`, `PROGRESS.md`,
  `AGENTS.md`.
- **Sin cambios** en `:core`, SQLDelight/`.sqm`, IA, `init.sh`, `iosApp/**`,
  icono de launcher ni `libs.versions.toml`.

## Visual Design Impact

UI involved: yes. Fuente `DESIGN.md` (`Colors`, `Typography`, `Shapes`,
`Components`, `Accessibility Baseline`) y `docs/design/paleta.png`. Afecta theme
global, cabecera de 7 pantallas + onboarding/loading, chips, badges, botones,
diálogos, snackbars, scrim y system bars. Sin artefacto nuevo; no se inventa
logo. Desviación 700→600 y ajustes AA se registran en `DESIGN.md`.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — UI: `PlaybookTheme`, tokens,
  `PlaybookBrandHeader`, fuentes/resources y edge-to-edge.
- `CONSTRAINTS.md`: **update** — MUST/MUST NOT de tokens de color, texto en sp,
  sólo modo claro y cabecera común.
- `DESIGN.md`: **update** — tokens implementados, pesos 600/500, cabecera,
  preguntas de acción/logo resueltas y ajustes AA.
- `docs/technical-discovery.md`: **update** — Compose resources, OFL/icono,
  dependencia, edge-to-edge y verificación de contraste.
- `docs/risks-and-open-questions.md`: **update** — tokens visuales implementados;
  dark mode diferido.
- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`: **update al cerrar** —
  cola/estado/evidencia.
- `docs/mvp-execution-plan.md`, `docs/design/ui-ux-brief.md`, `README.md`:
  **not needed** — no cambia plan, encargo ni cierre.

## Implementation Plan And Tasks

1. Versionar TTF/OFL/icono; agregar `compose.components.resources` y
   `packageOfResClass`.
2. Crear `Color.kt`/`Type.kt`/`Shapes.kt`/`PlaybookTheme.kt`; migrar `App.kt` a
   `PlaybookTheme` y compilar.
3. Crear `PlaybookBrandHeader` y migrar principal, onboarding/loading, editor,
   GDD, Preguntar, wizard y relacionadas.
4. Auditar scrim, chips/badges/diálogos/snackbars, edge-to-edge y system bars.
5. Verificar AA y font scale 130/200 %; correr gate, build, link iOS y smoke.
6. Actualizar docs durables y evidencia.

## Verification Plan

- `CI=true ./init.sh` → exit 0 (gate Android-only, IA fake, sin red; sin cambios).
- `./gradlew :composeApp:assembleDebug -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL.
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL
  (iOS congelado: sólo compilar).
- `./gradlew :composeApp:testDebugUnitTest --tests '*PlaybookContrastTest*'` (si
  se agrega) → 0 failures.
- `rg -n 'Color\(0x|Color\.(Black|White|Gray|Red|Green|Blue)' composeApp/src/commonMain`
  → sólo `theme/Color.kt`; `rg -n 'MaterialTheme\s*\{'` → 0; `rg -n
  'fontSize\s*=|\.sp\b'` → sólo `theme/Type.kt`; `rg -n 'TopAppBar'` → 0.
- Smoke Android (IA fake): onboarding, principal (3 acciones + speed-dial/scrim),
  editor (Back), GDD (título largo + lectura/Compartir), Preguntar (Back,
  pregunta/respuesta/guardar), wizard (Back=Cancelar, stepper) y relacionadas
  (Back), sin regresiones de textos/datos/navegación.
- Font scale 130 % y 200 % en los mismos destinos: sin recortes/solapamientos;
  targets ≥48dp. Capturas en
  `composeApp/build/smoke-evidence/design-system-brand-header/`.
- AA: tabla + test (o cálculo documentado); ≥4.5:1 texto / ≥3:1 no textual.
- E2E persistente: **no aplica** (sin harness UI/E2E; `init.sh` no levanta
  emuladores); se cubre con build + smoke manual + test de tokens.

## Evidence To Capture

- Salida de gate, `assembleDebug`, link iOS y contraste.
- Capturas: onboarding, principal, editor, GDD, Preguntar, wizard, relacionadas;
  font scale 130/200 %; cabecera con icono/Back; speed-dial con scrim.
- SHA-256 de TTF/PNG y confirmación de que launcher/`iosApp` no cambiaron.
- `git diff --stat` sólo con archivos de la feature.

## Validator Checklist

- [ ] Sólo theme + cabecera + auditoría; sin cambios de producto.
- [ ] Escenarios 1–10 pasan; onboarding primero y sin splash.
- [ ] Sin `MaterialTheme { }`, `TopAppBar` ni colores/tamaños ad-hoc fuera de
      `theme/`; scrim por token.
- [ ] Fuentes/OFL/icono versionados con origen/hashes; `compose.components.resources`
      sólo donde hace falta.
- [ ] AA y font scale 130–200 % verificados; Back/targets ≥48dp y semántica.
- [ ] Cabeceras marca/Back→icono→título; GDD conserva lectura/Compartir.
- [ ] Gate/build/link iOS/evidencia y docs durables actualizados.
- [ ] Sin cambios en `:core`, esquema, IA, `init.sh`, `iosApp/**` ni launcher.
