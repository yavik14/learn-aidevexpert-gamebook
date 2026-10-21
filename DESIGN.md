---
name: Playbook
description: >-
  Dirección visual derivada de la paleta del autor (docs/design/paleta.png),
  implementada en el theme claro de la app. App Android-first de notas de diseño
  de juegos.
status: implemented
designAssets:
  sourceOfTruth:
    - docs/design/paleta.png
    - docs/design/icon.png
  generatedConcepts: []
colors:
  primary: "#98E4FF"
  secondary: "#2C2926"
  tertiary: "#FFE17D"
  background: "#F9F6F8"
  surface: "#FFFFFF"
  text: "#2C2926"
  actionPrimary: "#0E4B54"
  actionEdit: "#0E4B54"
  actionAlt: "#6B4A2A"
  actionWarn: "#7A7A2E"
  actionWarnText: "#5F5F22"
  danger: "#C0392B"
  scrim: "#522C2926"
  trackMechanics: "#0E4B54"
  trackCharacters: "#6B4A2A"
  trackStory: "#7A7A2E"
  statusCaptured: "#4A4440"
  statusPending: "#7A7A2E"
  statusIndexed: "#0E4B54"
  statusFailed: "#C0392B"
typography:
  headline:
    fontFamily: Bricolage Grotesque
    fontSize: 34
    fontWeight: 600
  body:
    fontFamily: JetBrains Mono
    fontSize: 16
    fontWeight: 400
  label:
    fontFamily: JetBrains Mono
    fontSize: 13
    fontWeight: 500
rounded:
  sm: 8
  md: 16
  lg: 24
spacing:
  sm: 8
  md: 16
components:
  button-primary:
    backgroundColor: "{colors.actionPrimary}"
    textColor: "#FFFFFF"
    rounded: "{rounded.md}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.secondary}"
    borderColor: "{colors.secondary}"
    rounded: "{rounded.md}"
  chip-track:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.sm}"
  chip-genre:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.text}"
    rounded: "{rounded.sm}"
  chip-label-alt:
    backgroundColor: "{colors.tertiary}"
    textColor: "{colors.text}"
    rounded: "{rounded.sm}"
  fab:
    backgroundColor: "{colors.actionPrimary}"
    textColor: "#FFFFFF"
---

# Design Direction

> **Estado:** provisional. Paleta y tipografía derivadas de `docs/design/paleta.png`
> (fuente de verdad de color). Los valores de **acción/estado** (teal oscuro,
> marrón, oliva, rojo) son derivados/aproximados de la captura y se afinan cuando
> llegue la entrega formal de UI/UX. `docs/design/ui-ux-brief.md` es el encargo de
> diseño.
>
> **2026-10-15:** revisión Android-first (iOS congelado). Nueva identidad (celeste
> + oscuro cálido + amarillo; Bricolage Grotesque + JetBrains Mono); pantalla
> principal con top bar + iconos + FAB desplegable; Géneros; Proyecto/Brainstorming;
> superficies de IA (tts, generación de imagen, transcripción, sugerencia de
> clasificación) e indicador fake/real.

## Implementation Status

Implementado 2026-10-21 (`design-system-brand-header`): `PlaybookTheme` (M3 claro,
sin dark/dynamic color), tokens extra de acción/track/estado/scrim, tipografía
Bricolage Grotesque + JetBrains Mono desde `composeResources` (OFL), shapes
8/16/24 dp, cabecera común `PlaybookBrandHeader` y edge-to-edge con system bars
claras. Los colores literales viven sólo en `theme/Color.kt` y los tamaños de
texto sólo en `theme/Type.kt`.

## Overview

Playbook es una app Android-first (Compose Multiplatform, Material 3) de notas de
diseño de juegos para un único autor. Se organiza en tres `Track`
(`mecánicas`/`personajes`/`historia`), clasifica con **Géneros** y construye el
**GDD** de un **Proyecto**. iOS queda congelado.

## Existing Design Assets

- `docs/design/icon.png` — icono de la app (gamepad + lápiz, estilo boceto, acentos
  celeste/ámbar). **Autoridad: fuente de verdad del icono** (provisional).
  **Configurado en el build Android** por `app-icon-configuration`: adaptive icon
  (`mipmap-anydpi-v26/ic_launcher.xml`, fondo crema `#F7EFE2` + foreground) y PNG
  legacy por densidad (`mipmap-*/ic_launcher.png`). iOS congelado (su `AppIcon`
  queda como está).
- `docs/design/paleta.png` — paleta del autor (Primary `#98E4FF`, Secondary
  `#2C2926`, Tertiary `#FFE17D`, Neutral `#F9F6F8`; tipografías Bricolage
  Grotesque + JetBrains Mono; componentes de referencia: botones, search field,
  nav bar, chips, progress bars, iconos de acción). **Autoridad: fuente de verdad
  de color/tipografía** (provisional).
- `docs/design/ui-ux-brief.md` — encargo de UI/UX.

## Generated Concept Images

La app genera conceptos visuales desde texto con el modelo `image` (runtime
fake/real del MVP) y los adjunta a una Nota. La superficie es la acción
**"Generar con Playbook"** dentro del diálogo "Añadir imagen" de la pantalla
principal: prompt → carga → previsualización en el editor → Guardar. La imagen
resultante se ve con las miniaturas existentes (tarjeta de Nota). No hay
historial de prompts, edición/retocado ni múltiples imágenes por Nota.

## Product Feel

**Cuaderno creativo, moderno y cálido.** Tinta oscura cálida sobre papel neutro,
acento celeste (primary) y amarillo (tertiary); tipografía con carácter
(grotesque + mono). La captura es el gesto central y debe sentirse inmediata; el
GDD debe leerse como un documento del juego.

## Colors

- Base neutra `#F9F6F8` (background) con superficie blanca `#FFFFFF`.
- **Primary** `#98E4FF` (celeste) para acentos y chips; **acción primaria** en
  teal oscuro derivado (`#0E4B54`).
- **Secondary** `#2C2926` (texto/tinta). **Tertiary** `#FFE17D` (amarillo) para
  etiquetas/realces.
- **Acciones/estado (derivados):** teal `#0E4B54`, marrón `#6B4A2A`, oliva
  `#7A7A2E`, rojo `#C0392B`. Contraste AA obligatorio.
- **Scrim:** tinta `#2C2926` al 32 % (`#522C2926`), no negro puro.
- **Superficies M3 derivadas:** `surfaceVariant #EFEAF0`/`onSurfaceVariant
  #4A4440`, `outline #6F6862`, `outlineVariant #D8D2D6` y rampa
  `surfaceContainer*` neutra (sin violeta default). `errorContainer #F7D9D5`/
  `onErrorContainer #7A1F16`.
- Cada `Track` debe distinguirse con más que color (forma/texto). Los tokens de
  track/estado (`#0E4B54`, `#6B4A2A`, `#7A7A2E`, `#4A4440`, `#C0392B`) se usan
  como forma/borde/relleno; para texto sobre base, el oliva AA es `#5F5F22`.

## Typography

- **Headline:** Bricolage Grotesque (títulos). Se versionan **Regular (400)** y
  **SemiBold (600)**; los títulos usan **SemiBold 600** (desviación documentada
  desde 700: no se versiona Bold para no cargar un tercer peso).
- **Body / Label:** JetBrains Mono **Regular (400)** y **Medium (500)** para
  cuerpo, labels, botones, chips y badges.
- Escala: headline (34), body (16), label (13), todo en `sp` (font scale del
  sistema aplica). `display*` 40/34/30 y alturas de línea proporcionales.
- Familias cargadas desde `composeResources/font` (TTF OFL versionados); los
  el glifo sin cobertura en esas familias (`✦`, U+2726) usa fallback del
  sistema; `←` (U+2190) sí está en Bricolage.

## Layout

- Mobile-first, una columna.
- **Cabecera común:** modo marca (icono decorativo + "Playbook") en onboarding y
  pantalla principal; modo detalle (Back 48dp → icono decorativo → título ≤2
  líneas con ellipsis) en secundarias. En la principal, acciones por icono
  **Ver GDD**, **Brainstorming** y **Preguntar**; **FAB desplegable** (speed-dial)
  abajo-derecha con Nota de texto, Galería y Cámara.
- Jerarquía por agrupación (Track → Nota). Edge-to-edge real: barras
  transparentes/light, `windowBackground` base y cada `Scaffold` consume sus
  insets una sola vez.

## Shapes

Radios suaves: `sm` 8dp, `md` 16dp, `lg` 24dp (más extremos 4/32dp en la escala
M3). Superficies tipo papel; miniaturas con recorte `rounded.md`.

## Components

- **Cabecera de marca (`PlaybookBrandHeader`):** implementada 2026-10-21 sobre
  `Surface(background)` + `statusBarsPadding`, altura mínima 56dp. Marca: icono
  28dp decorativo + "Playbook"; detalle: Back 48dp ("Volver") → icono 24dp
  decorativo → título Bricolage `headlineSmall` ≤2 líneas. Acciones por icono con
  un único `contentDescription` (fallback textual `GDD`/`✦`/`?`, sin
  `material-icons` en `commonMain`). No es el icono de launcher: usa
  `playbook_icon.png`.
- **FAB desplegable:** "Nueva nota" → Nota de texto / Galería / Cámara. Estado
  local `+`/`×`, acciones apiladas, scrim y cierre por Back (Android). “Galería”
  abre el diálogo “Añadir imagen” (conserva “Generar con Playbook”); “Cámara” lanza el
  picker directo.
- **Selector de Track**; **Chip de Track**, **Chip de Género** (catálogo + libre),
  **Chip de categoría de mecánica** (`Movimiento`/`Interacción`/`Conflicto`).
- **Tarjeta de Nota:** cuerpo + track + géneros + estado; **miniatura** si es boceto.
- **Dictado inline:** barra de progreso de **30s** con auto-stop.
- **Badge de Estado:** `capturada`/`pendiente`/`indexada`/`fallida`.
- **Wizard de Brainstorming:** stepper 9 pasos con progreso (Paso X/9) y 2
  sugerencias por paso: botón "Sugerir", tarjetas con "Usar"/"Añadir" e
  "Ignorar"; Playbook sugiere y el usuario decide. Se descartan al navegar.
- **Referencia de Nota (cita):** respuestas del Q&A.
- **Superficies de IA:** control **play/pausa** (tts), acción **Generar imagen**,
  acción **Transcribir** (Adjunto AUDIO), **sugerencia de contenido** en el
  editor ("Sugerir ideas" → hasta 2 tarjetas con "Añadir"/"Ignorar", aviso de
  respuesta parcial y "Reintentar"), **sugerencia de clasificación**
  (chips sugeridos + "Confirmar"/"Editar"), e **indicador de IA** (fake/real/“no
  disponible”).
- **Editor con ideas de contenido:** debajo del cuerpo/dictado y antes de
  "Sugerir clasificación"; "Sugerir ideas" está disponible también con el cuerpo
  vacío, la carga muestra "Sugiriendo ideas…"/"Playbook está pensando ideas…" y
  cada tarjeta explica con texto y borde (no sólo color), con acciones ≥48 dp.
  Añadir anexa un párrafo al final; nada se guarda hasta Guardar.

## Core Screens

1. **Pantalla principal (Playbook):** notas por track; top bar + iconos; FAB de captura.
2. **Nueva nota / Editor:** cuerpo + track + géneros; dictado inline 30s; "Sugerir ideas" (contenido) y "Sugerir clasificación".
3. **Bocetos:** cámara/galería → Nota con imagen (miniatura en listas).
4. **Brainstorming (wizard):** 9 pasos para crear/iterar el Proyecto; sugerencias IA; crear Nota por paso.
5. **GDD (documento del Proyecto):** ficha + Historia/Lore + Personajes + Notas por Track; exportar/compartir.
6. **Preguntar (Q&A):** pregunta + respuesta con citas; "Guardar como Nota"; lectura en voz alta.
7. **Notas Relacionadas:** enlaces por similitud.
8. **Estado de indexado:** pendiente/fallida + reintento.
9. **Sugerencia de clasificación:** propuesta de Track/Género con confirmación.
10. **Onboarding del primer arranque:** si no hay Proyecto, oferta saltable para
    iniciar el Brainstorming o crear un Proyecto vacío; no reaparece cuando el
    Proyecto ya existe.

## Responsive Baseline

Teléfonos primero; tablets con ancho cómodo. Sin Desktop/Web en el MVP.

## Accessibility Baseline

- Contraste AA (≥ 4.5:1 texto normal; ≥ 3:1 no textual) verificado con
  `PlaybookContrastTest` sobre los pares usados. Pares clave: tinta/base 13.5:1,
  blanco/teal 9.8:1, tinta/celeste 10.2:1, tinta/amarillo 11.2:1, error/blanco
  5.4:1, outline/blanco 5.5:1.
- Objetivos táctiles ≥ 48dp (cabecera, FAB, celdas y controles de IA); el Back de
  la cabecera es 48dp.
- Tipografía dinámica / tamaños del sistema: verificado a font scale 130 % y
  200 % en onboarding, principal, editor, GDD, Q&A, wizard y relacionadas.
- Estados e iconos con forma/texto/`contentDescription`, no solo color.

## Do's and Don'ts

- **Sí:** calidez, papel/tinta, jerarquía por agrupación, captura inmediata, GDD
  como documento, IA discreta que no bloquea.
- **Sí:** distinguir track/estado con más que color.
- **No:** look corporativo/frío, dashboards densos, decoración que estorbe la captura.

## Open Design Questions

- ~~Valores de acción/estado definitivos.~~ **Resuelto** (2026-10-21): tokens
  implementados en `theme/Color.kt` con AA medido (ver Accessibility Baseline).
- ~~Logo/wordmark definitivo.~~ **Resuelto para el MVP** (2026-10-21): la marca es
  `docs/design/icon.png`; la cabecera usa `playbook_icon.png` y no se toca el
  icono de launcher. Un wordmark nuevo queda para una entrega de UI/UX formal.
- Representación visual de cada `Track` (color + forma): el color está tokenizado
  (`trackMechanics`/`trackCharacters`/`trackStory`); falta la forma propia.
- "Pendiente/fallida/sin indexar" sin parecer error grave.
- **Dark mode: diferido** (fuera del MVP; el theme es sólo claro).
- ~~Aspecto del **FAB desplegable** y su expansión/colapso.~~ **Resuelto**
  (2026-10-20): `+`/`×`, acciones Nota de texto/Galería/Cámara, scrim y Back;
  ver Components.
- Affordances de IA (play/pausa, generar imagen, transcribir) e indicador fake/real.
