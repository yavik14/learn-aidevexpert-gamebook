# Feature Implementation Spec: Configurar el icono de la aplicación

## Source Feature

- `id`: `app-icon-configuration`
- `area`: `ui`
- `depends_on`: `["kmp-project-bootstrap"]` (accepted)
- `status`: `not_started` (al planificar); `source`: `feature_list.json`

> **Alcance.** Hoy el `AndroidManifest.xml` **no** declara `android:icon`, así que
> el launcher muestra el **icono por defecto** del sistema. Esta feature configura
> como icono de la app el arte `docs/design/icon.png` (gamepad + lápiz), generando
> recursos Android (**adaptive icon** en API 26+ + PNG legacy por densidad) y
> declarándolos en el manifest. iOS está **congelado** (su `AppIcon` ya existe y se
> deja como está). Sin dependencias nuevas.

## Goal

Que la app muestre el **icono de Playbook** en el launcher de Android (adaptive en
API 26+ y legacy en versiones previas), generado desde la **fuente de verdad**
`docs/design/icon.png`, con fondo **sin costuras** entre el `background` del
adaptive icon y el padding del `foreground`. Entregable: recursos Android
(`mipmap`/`drawable`/`values`) + `AndroidManifest.xml`. Sin cambios de código
Kotlin, sin deps, sin esquema, sin tocar iOS.

## Non-Goals

- **iOS**: `AppIcon` queda como está (iOS congelado, `docs/adr/0003-ios-congelado-mvp.md`).
- **Splash screen**, ícono de notificación, `monochrome`/themed icon (Android 13
  "iconos temáticos"), play store assets ni banner/feature graphic.
- **Rediseño del arte**: se usa `docs/design/icon.png` como fuente; no se crea arte
  nuevo (si el autor entrega uno nuevo, se regeneran los PNG).
- Tokens/`DESIGN.md`, pantallas, `:core`, esquema, `init.sh`, deps.

## Job Story

When instalo/abro la app en Android, I want ver el icono de Playbook en el launcher,
so I can reconocerla de un vistazo en vez del icono genérico del sistema.

## Users And Permissions

- Autor (único usuario): ve el launcher. Sin auth/permisos.
- Desarrollador: corre Gradle/tests. Sin secrets.

## Scope Decisions

> **Validadas con el autor (2026-10-16)**, con previews del foreground generados
> desde `docs/design/icon.png`.

1. **Adaptive icon + fallback legacy** (lo que pide la nota de la feature):
   `mipmap-anydpi-v26/ic_launcher.xml` (`<adaptive-icon>` con `background` de color y
   `foreground` de imagen) para API 26+, y `mipmap-*/ic_launcher.png` por densidad
   para versiones anteriores. `android:roundIcon` apunta al **mismo** `@mipmap/ic_launcher`.
2. **Fondo sin costuras:** el arte tiene fondo crema; se muestrea y se usa como
   `ic_launcher_background` **y** como color de padding del `foreground`, evitando
   un borde visible dentro del mask. Color dominante medido: **`#F7EFE2`** (crema
   cálido).
3. **Foreground recortado** para eliminar el marco redondeado del arte (recorte
   central `sips -c 800`) y centrado en la **safe zone** (~66dp de 108dp ⇒ ~264px
   sobre 432px), evitando que el mask recorte el dibujo.
4. **Generación con `sips`** (macOS, sin deps nuevas): recortar (`-c`), escalar
   (`-z`), rellenar con color sólido (`-p … --padColor F7EFE2`). Los PNG resultantes
   se **commitean** (el repo no regenera arte en build).
5. **`android:roundIcon` = mismo `@mipmap/ic_launcher`** y foreground en un único
   `drawable-nodpi` (menos archivos; el sistema escala).

## Acceptance Scenarios

### Scenario 1: el launcher muestra el icono de Playbook
Given la app instalada en un dispositivo/emulador Android, When se mira el launcher,
Then el icono de Playbook aparece en lugar del icono por defecto.

### Scenario 2: adaptive icon (API 26+)
Given un dispositivo API 26+, When el launcher resuelve el icono, Then usa
`@mipmap/ic_launcher` (`mipmap-anydpi-v26/ic_launcher.xml`) con
`background = @color/ic_launcher_background` y
`foreground = @drawable/ic_launcher_foreground`, **sin costura** entre ambos.

### Scenario 3: PNG legacy por densidad (API < 26)
Given un dispositivo con API < 26, When el launcher resuelve el icono, Then usa el
`ic_launcher.png` de la densidad correspondiente (48/72/96/144/192 para
mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi).

### Scenario 4: manifest declarado
Given el `AndroidManifest.xml`, When se inspecciona, Then `<application>` declara
`android:icon="@mipmap/ic_launcher"` y `android:roundIcon="@mipmap/ic_launcher"`
(sin tocar `label`, permisos ni provider).

### Scenario 5: iOS intacto
Given iOS congelado, When se inspecciona `iosApp/**`, Then `AppIcon`/assets no
cambiaron.

### Scenario 6: gate honesto
Given los escenarios 1–5, When corren `:composeApp:assembleDebug` y `./init.sh`,
Then compila sin deps nuevas, sin cambios de esquema ni código Kotlin, y `./init.sh`
termina en `0`.

## Repository Research

### Files Inspected

- `composeApp/src/androidMain/AndroidManifest.xml` — `<application>` **sin
  `android:icon`** (icono por defecto); `label = "Playbook"`; `FileProvider`.
- `composeApp/src/androidMain/res/` — sólo `values/themes.xml` y `xml/file_paths.xml`;
  **no hay** `mipmap`/`drawable` ni `colors.xml`.
- `docs/design/icon.png` — 1024×1024, PNG **RGB (sin alfa)**, fondo crema medido
  `#F7EFE2` (gamepad + lápiz, acentos celeste/ámbar, marco redondeado interior).
- `DESIGN.md` (§Existing Design Assets: `icon.png` es la fuente de verdad del icono;
  "Su configuración en el build es la feature `app-icon-configuration`").
- `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/` — `Contents.json` +
  `app-icon-1024.png` **ya existen** (iOS se deja como está).
- Herramientas del entorno: `sips` disponible (`-z` escala, `-p/--padColor`
  rellena con color sólido —sin alfa—, `-c` recorta al centro). `magick`/`convert`
  **no** están; no se agregan deps.

### Existing Patterns To Follow

- Recursos Android en `composeApp/src/androidMain/res/`.
- El build ya resuelve recursos estándar de AGP (`namespace = com.playbook.app`);
  no hay que tocar `build.gradle.kts`.
- iOS congelado: no se exige paridad ni verificación iOS (`CONSTRAINTS.md`).

### Current Gaps

- No hay `android:icon` ni recursos `mipmap`/`drawable`; el launcher usa el default.
- No existe `colors.xml` para el `background` del adaptive icon.

## Technical Approach

### 1. Recursos generados (desde `docs/design/icon.png`, con `sips`)

```bash
ICON=docs/design/icon.png
# Legacy por densidad (cuadrado, arte completo)
for d in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  n=${d%%:*}; px=${d##*:}
  sips -z $px $px "$ICON" --out composeApp/src/androidMain/res/mipmap-$n/ic_launcher.png
done
# Foreground adaptive: recortar el marco → escalar al safe zone (66%) → rellenar crema 432
sips -c 800 800 "$ICON" --out /tmp/fg.png      # quita el marco redondeado
sips -z 264 264 /tmp/fg.png --out /tmp/fg2.png  # safe zone ~66dp de 108dp
sips -p 432 432 --padColor F7EFE2 /tmp/fg2.png \
  --out composeApp/src/androidMain/res/drawable-nodpi/ic_launcher_foreground.png
```

- `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` — **crear** (legacy).
- `drawable-nodpi/ic_launcher_foreground.png` — **crear** (108dp @ 432px, arte
  centrado, padding crema `#F7EFE2`).
- `values/colors.xml` — **crear**: `<color name="ic_launcher_background">#F7EFE2</color>`.

### 2. Adaptive icon

`mipmap-anydpi-v26/ic_launcher.xml` (**crear**):
```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

### 3. Manifest

`AndroidManifest.xml` (**modificar**): agregar a `<application>`:
```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher"
```
Sin otros cambios (`label`, permisos, `FileProvider` intactos).

## Expected File Changes

- `composeApp/src/androidMain/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
  — **crear** (5 PNG legacy).
- `composeApp/src/androidMain/res/drawable-nodpi/ic_launcher_foreground.png` —
  **crear** (foreground adaptive).
- `composeApp/src/androidMain/res/mipmap-anydpi-v26/ic_launcher.xml` — **crear**.
- `composeApp/src/androidMain/res/values/colors.xml` — **crear**.
- `composeApp/src/androidMain/AndroidManifest.xml` — modificar (`icon`/`roundIcon`).
- `docs/technical-discovery.md`, `DESIGN.md` — **update** (ver abajo).
- `AGENTS.md` — update **acotado** (sólo "próxima feature en cola").
- `feature_list.json`, `PROGRESS.md` — update al cerrar.

No se esperan cambios en `:core`, `iosApp/**`, `init.sh`, `gradle/libs.versions.toml`,
`composeApp/build.gradle.kts` ni en el código Kotlin.

## Visual Design Impact

- UI involved: yes (icono de launcher). Design source: `docs/design/icon.png`
  (fuente de verdad, `DESIGN.md`).
- Screens or states affected: launcher/recents de Android.
- New design artifact required: no — se usa el arte existente; sólo se generan
  bitmaps derivados.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **not needed** — configuración de recursos, sin cambio de
  arquitectura/dominios.
- `CONSTRAINTS.md`: **not needed** — sin regla MUST/MUST NOT durable nueva.
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola".
- Other docs: `docs/technical-discovery.md` **update** ("App Icon Decisions
  (app-icon-configuration)": fuente, pipeline `sips`, fondo `#F7EFE2`, adaptive +
  legacy, iOS intacto); `DESIGN.md` **update (menor)** — marcar el icono como
  configurado en el build. `feature_list.json`/`PROGRESS.md` **update** al cerrar.

## Implementation Plan

1. Generar los PNG (legacy + foreground) con `sips` desde `docs/design/icon.png`.
2. Crear `colors.xml` y `mipmap-anydpi-v26/ic_launcher.xml`.
3. Declarar `android:icon`/`android:roundIcon` en el manifest.
4. Compilar (`assembleDebug`) y verificar recursos; smoke visual en emulador.
5. Correr `./init.sh`, actualizar docs y cerrar.

## Implementation Tasks

- [ ] Generar `mipmap-*/ic_launcher.png` (5 densidades) y `drawable-nodpi/ic_launcher_foreground.png`.
- [ ] Crear `values/colors.xml` (`ic_launcher_background = #F7EFE2`).
- [ ] Crear `mipmap-anydpi-v26/ic_launcher.xml`.
- [ ] Modificar `AndroidManifest.xml` (`android:icon` + `android:roundIcon`).
- [ ] Verificar `:composeApp:assembleDebug` + `aapt dump badging` + smoke visual en emulador.
- [ ] Actualizar `docs/technical-discovery.md`, `DESIGN.md` y `AGENTS.md` (cola).
- [ ] Correr `./init.sh`, registrar evidencia y cerrar (`feature_list.json`/`PROGRESS.md`).

## Verification Plan

- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL; recursos resueltos.
- `aapt dump badging composeApp/build/outputs/apk/debug/composeApp-debug.apk` (o
  `apkanalyzer`) → `application: label='Playbook' icon='...'` apuntando a
  `ic_launcher`; sin errores de resource linking.
- `./init.sh` → exit 0; gate Android-only, sin dev servers ni red.
- **Smoke visual Android** (emulador): instalar y ver el icono de Playbook en el
  launcher; captura de pantalla. Verificar que el adaptive compone bien (sin
  costuras ni recortes del arte dentro del mask).
- `git status` → sólo recursos/manifest + docs; sin cambios de código Kotlin, deps,
  esquema ni `iosApp/**`.
- **Controles negativos** (restaurar y re-verificar):
  (a) quitar `android:icon` del manifest → el launcher vuelve al icono por defecto
  (check visual);
  (b) referenciar un `@drawable/` inexistente en `ic_launcher.xml` →
  `assembleDebug` falla por resource linking.
- E2E persistente: **no aplica** (sin harness E2E; es configuración de recursos +
  verificación visual).

## Evidence To Capture

- Salida de `./gradlew :composeApp:assembleDebug` y de `aapt dump badging` (icono
  declarado).
- Captura del launcher (emulador Android) con el icono de Playbook; nota del API
  level usado (adaptive vs legacy).
- `./init.sh` → exit 0.
- Controles negativos (a)/(b) reproducidos y restaurados.
- `git status` sin cambios en `:core`/`iosApp`/`init.sh`/build files; sin deps.

## Validator Checklist

- [ ] Alcance respetado: sólo icono Android (adaptive + legacy) + manifest; **sin**
      splash, monochrome, store assets, arte nuevo ni cambios de código/deps/iOS.
- [ ] Escenarios de aceptación 1–6 pasan.
- [ ] `android:icon`/`android:roundIcon` declarados; `mipmap-anydpi-v26/ic_launcher.xml`
      compone `background`(color)+`foreground`(bitmap) sin costuras.
- [ ] PNG legacy en las 5 densidades; foreground en `drawable-nodpi`.
- [ ] `colors.xml` con `#F7EFE2`; fondo medido == padding (sin borde visible).
- [ ] `iosApp/**`, `:core`, esquema e `init.sh` sin cambios; sin deps nuevas.
- [ ] `docs/technical-discovery.md`/`DESIGN.md` actualizados; `AGENTS.md` sólo la cola.
- [ ] `feature_list.json` y `PROGRESS.md` actualizados con la evidencia.
- [ ] No hay E2E persistente y la spec lo justifica.
