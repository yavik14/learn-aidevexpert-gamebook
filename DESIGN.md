---
name: Playbook
description: >-
  Dirección visual (provisional) derivada de la paleta del autor
  (docs/design/paleta.png). App Android-first de notas de diseño de juegos.
status: provisional
designAssets:
  sourceOfTruth:
    - docs/design/paleta.png
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
  danger: "#C0392B"
typography:
  headline:
    fontFamily: Bricolage Grotesque
    fontSize: 34
    fontWeight: 700
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

## Overview

Playbook es una app Android-first (Compose Multiplatform, Material 3) de notas de
diseño de juegos para un único autor. Se organiza en tres `Track`
(`mecánicas`/`personajes`/`historia`), clasifica con **Géneros** y construye el
**GDD** de un **Proyecto**. iOS queda congelado.

## Existing Design Assets

- `docs/design/paleta.png` — paleta del autor (Primary `#98E4FF`, Secondary
  `#2C2926`, Tertiary `#FFE17D`, Neutral `#F9F6F8`; tipografías Bricolage
  Grotesque + JetBrains Mono; componentes de referencia: botones, search field,
  nav bar, chips, progress bars, iconos de acción). **Autoridad: fuente de verdad
  de color/tipografía** (provisional).
- `docs/design/ui-ux-brief.md` — encargo de UI/UX.

## Generated Concept Images

Ninguna: no hay skill de generación de imágenes en el runtime actual. la
dirección se describe en prosa/tokens.

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
- Cada `Track` debe distinguirse con más que color (forma/texto).

## Typography

- **Headline:** Bricolage Grotesque (títulos).
- **Body / Label:** JetBrains Mono (UI y cuerpo).
- Escala: headline (34), body (16), label (13). Números y etiquetas legibles a
  tamaño pequeño; soporte de tamaños del sistema.

## Layout

- Mobile-first, una columna.
- **Pantalla principal:** top bar con "Playbook" y, a la derecha, iconos de
  **Ver GDD**, **Brainstorming** y **Preguntar**; **FAB desplegable** (speed-dial)
  abajo-derecha con Nota de texto, Galería y Cámara.
- Jerarquía por agrupación (Track → Nota). Edge-to-edge e insets correctos.

## Shapes

Radios suaves (`sm`/`md`). Superficies tipo papel; miniaturas con recorte
`rounded.md`.

## Components

- **TopAppBar:** "Playbook" + acciones por **icono** (con `contentDescription`).
- **FAB desplegable:** "Nueva nota" → Nota de texto / Galería / Cámara.
- **Selector de Track**; **Chip de Track**, **Chip de Género** (catálogo + libre),
  **Chip de categoría de mecánica** (`Movimiento`/`Interacción`/`Conflicto`).
- **Tarjeta de Nota:** cuerpo + track + géneros + estado; **miniatura** si es boceto.
- **Dictado inline:** barra de progreso de **30s** con auto-stop.
- **Badge de Estado:** `capturada`/`pendiente`/`indexada`/`fallida`.
- **Wizard de Brainstorming:** stepper 9 pasos con progreso (Paso X/9) y 2
  sugerencias por paso.
- **Referencia de Nota (cita):** respuestas del Q&A.
- **Superficies de IA:** control **play/pausa** (tts), acción **Generar imagen**,
  acción **Transcribir** (Adjunto AUDIO), **sugerencia de clasificación**
  (chips sugeridos + "Confirmar"/"Editar"), e **indicador de IA** (fake/real/“no
  disponible”).

## Core Screens

1. **Pantalla principal (Playbook):** notas por track; top bar + iconos; FAB de captura.
2. **Nueva nota / Editor:** cuerpo + track + géneros; dictado inline 30s.
3. **Bocetos:** cámara/galería → Nota con imagen (miniatura en listas).
4. **Brainstorming (wizard):** 9 pasos para crear/iterar el Proyecto; sugerencias IA; crear Nota por paso.
5. **GDD (documento del Proyecto):** ficha + Historia/Lore + Personajes + Notas por Track; exportar/compartir.
6. **Preguntar (Q&A):** pregunta + respuesta con citas; "Guardar como Nota"; lectura en voz alta.
7. **Notas Relacionadas:** enlaces por similitud.
8. **Estado de indexado:** pendiente/fallida + reintento.
9. **Sugerencia de clasificación:** propuesta de Track/Género con confirmación.

## Responsive Baseline

Teléfonos primero; tablets con ancho cómodo. Sin Desktop/Web en el MVP.

## Accessibility Baseline

- Contraste AA (≥ 4.5:1 texto normal).
- Objetivos táctiles ≥ 48dp (top bar, FAB, controles de IA).
- Tipografía dinámica / tamaños del sistema.
- Estados e iconos con forma/texto/`contentDescription`, no solo color.

## Do's and Don'ts

- **Sí:** calidez, papel/tinta, jerarquía por agrupación, captura inmediata, GDD
  como documento, IA discreta que no bloquea.
- **Sí:** distinguir track/estado con más que color.
- **No:** look corporativo/frío, dashboards densos, decoración que estorbe la captura.

## Open Design Questions

- Valores de acción/estado definitivos (afinar los derivados de la captura).
- Logo/wordmark definitivo.
- Representación visual de cada `Track` (color + forma).
- "Pendiente/fallida/sin indexar" sin parecer error grave.
- Presentación de las **2 sugerencias** de IA por paso del wizard.
- Aspecto del **FAB desplegable** y su expansión/colapso.
- Affordances de IA (play/pausa, generar imagen, transcribir) e indicador fake/real.
