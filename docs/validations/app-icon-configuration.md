# Validación independiente: `app-icon-configuration`

- **Feature id:** `app-icon-configuration`
- **Spec:** `docs/specs/app-icon-configuration.md`
- **Fecha:** 2026-10-16
- **Rol:** validator (independiente del implementer)
- **Worktree:** `worktree/app-icon-configuration` (rama `feature/app-icon-configuration`, base `43ce627`)
- **Estado en `feature_list.json` al validar:** `passing` (implementada, auto-verificada, sin commitear)
- **Veredicto:** **accept**

## Alcance validado

Configuración del **icono de launcher Android** desde la fuente de verdad
`docs/design/icon.png`:

- **Adaptive icon (API 26+):** `mipmap-anydpi-v26/ic_launcher.xml`
  (`background = @color/ic_launcher_background` + `foreground = @drawable/ic_launcher_foreground`).
- **PNG legacy (API < 26):** `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
  (48/72/96/144/192).
- **Foreground:** `drawable-nodpi/ic_launcher_foreground.png` (432×432, arte centrado
  con padding crema `#F7EFE2`).
- **`colors.xml`:** `ic_launcher_background = #F7EFE2`.
- **Manifest:** `<application>` declara `android:icon` y `android:roundIcon`
  = `@mipmap/ic_launcher` (sin otros cambios).
- **Docs:** `docs/technical-discovery.md` ("App Icon Decisions") y `DESIGN.md`.

Sin cambios de código Kotlin, deps, esquema ni iOS.

## Checks re-ejecutados por el validador (no se confió en el resumen)

| Check | Comando | Resultado |
| --- | --- | --- |
| Build Android | `./gradlew :composeApp:assembleDebug` | **BUILD SUCCESSFUL** (54 tasks). APK `composeApp-debug.apk` = 10 780 237 bytes |
| Icono declarado/resuelto | `aapt dump badging composeApp-debug.apk` | `application: label='Playbook' icon='res/mipmap-anydpi-v26/ic_launcher.xml'`; `application-icon-120..65535` → mismo `mipmap-anydpi-v26/ic_launcher.xml` |
| Recursos empaquetados | `aapt list` | `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/drawable-nodpi-v4/ic_launcher_foreground.png`, `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}-v4/ic_launcher.png` (5 densidades) |
| Color de fondo | `aapt dump --values resources` | `com.playbook.app:color/ic_launcher_background: t=0x1d d=0xfff7efe2 (color) #fff7efe2` |
| Composición adaptive | `aapt dump xmltree .../res/mipmap-anydpi-v26/ic_launcher.xml` | `background` → `@0x7f020004` (= `color/ic_launcher_background`); `foreground` → `@0x7f040006` (= `drawable/ic_launcher_foreground`) |
| Dimensiones PNG | `sips -g pixelWidth -g pixelHeight -g hasAlpha -g space` | mdpi 48², hdpi 72², xhdpi 96², xxhdpi 144², xxxhdpi 192², foreground 432². Todos **RGB, sin alfa** (fuente 1024² RGB sin alfa) |
| Sin costura (independiente) | Decodificación BMP del foreground (`sips -s format bmp` + parseo de píxeles) | Esquinas/padding `(0,0)`,`(2,2)`,`(5,5)`,`(216,10)`,`(10,216)`,`(431,431)` = **`#F7EFE2` exacto** == color de `background`; centro `(216,216)` = `#DBD5C9` (arte) |
| Gate estándar | `./init.sh` | **exit 0**; Android-only, sin dev servers ni red |
| Alcance/archivos protegidos | `git diff`/`git status` | `:core`, `iosApp/**`, `init.sh`, `gradle/libs.versions.toml`, `composeApp/build.gradle.kts` y cualquier `*.kt`: **sin cambios**. Sin deps nuevas. Sin strings tipo secret |
| Docs durables | inspección | `docs/technical-discovery.md` + `DESIGN.md` actualizados; `AGENTS.md` sin cambios (la cola sigue siendo `genres-catalog-persistence`, por lo que el update acotado de la spec no aplica) |
| Smoke visual | emulador `emulator-5556` (API 35) + `adb install -r` (Success) | Cajón de apps muestra el icono de Playbook (gamepad + lápiz) en mask **circular adaptativo** sobre crema, centrado, **sin costura ni recorte** del arte |

## Cobertura de escenarios

| Escenario | Estado | Evidencia |
| --- | --- | --- |
| 1. Launcher muestra el icono de Playbook | ✅ | Smoke visual (API 35); icono presente en el cajón |
| 2. Adaptive icon (API 26+) | ✅ | `xmltree` (background color + foreground bitmap) + captura del mask circular sin costura |
| 3. PNG legacy por densidad (API < 26) | ✅ (por recursos) | 5 mipmaps con dimensiones 48/72/96/144/192 presentes y empaquetados; **no** hay AVD API < 26 disponible para smoke visual (ver L1) |
| 4. Manifest declarado | ✅ | Diff de 2 líneas: `android:icon` + `android:roundIcon`; `label`/permisos/`FileProvider` intactos |
| 5. iOS intacto | ✅ | `git status --porcelain -- iosApp` vacío; `git diff iosApp` vacío |
| 6. Gate honesto | ✅ | `assembleDebug` + `init.sh` exit 0; sin deps/esquema/Kotlin |

## Controles negativos propios (independientes del implementer)

1. **Control A — quitar `android:icon`/`android:roundIcon` del manifest:** recompilado
   OK y `aapt dump badging` → `application: label='Playbook' icon=''` (el launcher
   cae al icono por defecto). Restaurado.
2. **Control B — `@drawable/ic_launcher_foreground_missing` en `ic_launcher.xml`:**
   `./gradlew :composeApp:assembleDebug` → **FAILED** en
   `:composeApp:processDebugResources` con
   `AAPT: error: resource drawable/ic_launcher_foreground_missing (aka com.playbook.app:drawable/ic_launcher_foreground_missing) not found`.
   Ambos controles **restaurados byte-idénticos**: md5 de manifest y de los 8
   recursos tras restaurar == md5 pre-control; re-verificado en verde
   (`BUILD SUCCESSFUL` + `badging icon='res/mipmap-anydpi-v26/ic_launcher.xml'`).

## Hallazgos

| Severidad | Nota |
| --- | --- |
| **Info (L1)** | El escenario 3 (legacy API < 26) no se pudo *smokear* visualmente: los AVD disponibles son API 26/31/32/34/35 (ninguno < 26). Se validó por inspección de recursos (presencia + dimensiones + empaquetado en el APK), que es lo que exige la spec. No bloquea. |
| **Info (L2)** | `AGENTS.md` no se editó. La spec pedía "update acotado (sólo 'próxima feature en cola')"; como esa cola no cambia (`genres-catalog-persistence` sigue siendo la primera ready), no aplica el cambio. Correcto. |

Sin hallazgos `major`/`critical`. Sin problemas de seguridad relevantes (recursos
estáticos, sin deps, sin secrets, sin cambios de permisos).

## Documentación y handoff

- Docs durables alineados con la spec y con el comportamiento implementado.
- `feature_list.json`: entrada `passing`, `depends_on ["kmp-project-bootstrap"]`, 9
  líneas de verificación + 9 de evidencia; JSON válido.
- `PROGRESS.md`: sesión 048 con evidencia; "Last verified at: 2026-10-16".
- No hay harness E2E persistente; la spec lo justifica (configuración de recursos +
  verificación visual).

## Veredicto: `accept`

La feature cumple la spec, los escenarios de aceptación se satisfacen (el 3 por
recursos, con limitación declarada), la verificación se re-ejecutó de forma
independiente, los controles negativos se reprodujeron y restauraron byte-idénticos,
y no hay desviaciones de alcance ni cambios fuera de los esperados. El orquestador
puede persistir el estado `accepted` en `feature_list.json`.
