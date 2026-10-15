---
name: Playbook
description: >-
  Dirección visual inicial (provisional). Cuaderno creativo / editorial cálido
  para una app móvil de notas de diseño de juegos. Los tokens no son
  definitivos: están pendientes de la entrega del equipo de UI/UX.
status: provisional
designAssets:
  sourceOfTruth: []
  generatedConcepts: []
colors:
  primary: "#8C4A2F"
  secondary: "#2F5D50"
  accent: "#D98C2B"
  background: "#F7F1E6"
  surface: "#FFFDF8"
  text: "#2B2118"
typography:
  display:
    fontFamily: serif editorial (p. ej. Fraunces / Newsreader)
    fontSize: 34
    fontWeight: 600
  h1:
    fontFamily: serif editorial (p. ej. Fraunces / Newsreader)
    fontSize: 28
    fontWeight: 600
  body:
    fontFamily: sans humanista (p. ej. Inter / Source Sans)
    fontSize: 16
    fontWeight: 400
  label:
    fontFamily: sans humanista (p. ej. Inter / Source Sans)
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
    backgroundColor: "{colors.primary}"
    textColor: "#FFFDF8"
    rounded: "{rounded.md}"
  track-chip:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.sm}"
  genre-chip:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.sm}"
  fab:
    backgroundColor: "{colors.primary}"
    textColor: "#FFFDF8"
---

# Design Direction

> **Estado:** provisional. Paleta, tipografía, logo e iconografía definitivos
> los define el equipo de UI/UX a partir del brief en
> `docs/design/ui-ux-brief.md`. Los tokens YAML de arriba son un punto de
> partida para agentes, **no** una fuente de verdad.
>
> **2026-10-15:** revisión de alcance (Android-first). Se suma la pantalla
> principal rediseñada (TopAppBar "Playbook" + iconos + FAB desplegable), el
> wizard de Brainstorming, Géneros (reemplazan etiquetas) y bocetos con miniatura.

## Overview

Playbook es un cuaderno de diseño de juegos para un único autor (game dev
aficionado). El MVP se organiza en tres tracks: `mecánicas`, `personajes` e
`historia`, y clasifica las Notas con **Géneros** (catálogo + personalizados). El
eje del producto es el **GDD de un Proyecto de Juego**. La UI es Compose
Multiplatform (Material 3). **Android-first**: iOS queda congelado.

## Existing Design Assets

Ninguno. Solo existe el icono de app por defecto. Se parte de cero.

## Generated Concept Images

Ninguna. No hay skill de generación de imágenes disponible en el runtime actual;
la dirección se describe en prosa. El equipo de UI/UX producirá los conceptos.

## Product Feel

**Cuaderno creativo / editorial cálido.** Debe sentirse como "mis ideas": papel,
tinta, tipografía con carácter, calidez. Ni corporativo ni frío, ni un IDE
denso. La captura es el gesto central y debe sentirse inmediata y sin fricción;
el GDD debe leerse como un documento del juego.

## Colors

Provisional (ver front matter). Base papel cálida (`#F7F1E6`) + tinta
(`#2B2118`), primario terracota, secundario verde profundo, acento ámbar. Cada
track debería poder distinguirse con un color/acento propio, siempre con
contraste AA.

## Typography

Provisional. Un serif con carácter para títulos y un sans humanista para cuerpo
y UI. Escala limitada (display, h1, body, label). Números y etiquetas siempre
legibles a tamaño pequeño.

## Layout

- Mobile-first, una columna; navegación por track clara.
- **Pantalla principal:** `TopAppBar` con título **"Playbook"** y, a la derecha,
  tres **iconos** (Ver GDD, Brainstorming, Preguntar); **FAB desplegable**
  (speed-dial) abajo-derecha con Nota de texto, Galería y Cámara.
- Jerarquía por agrupación (track → nota), no por densidad.
- Insets/edge-to-edge correctos.

## Shapes

Radios suaves (`sm`/`md`), sin esquinas duras. Bordes sutiles tipo papel. Las
miniaturas de boceto usan `rounded.md` con recorte.

## Components

- **TopAppBar:** título "Playbook" + acciones por **icono** (con `contentDescription`
  para accesibilidad).
- **FAB desplegable (speed-dial):** acción principal "Nueva nota" que despliega
  Nota de texto / Galería / Cámara.
- **Selector de Track:** control primario de organización (elegir uno de tres).
- **Chip de Track / Chip de Género:** identificadores rápidos y editables. El de
  Género se elige del catálogo o se escribe uno personalizado.
- **Chip de categoría de mecánica:** `Movimiento`/`Interacción`/`Conflicto`.
- **Tarjeta de Nota:** cuerpo + track + géneros + estado de indexado; si es un
  boceto, **miniatura** de la imagen (además del contenido).
- **Acción de Captura:** entrada rápida a texto / voz / imagen.
- **Dictado inline:** en "Nueva nota", con **barra de progreso de 30s** y
  auto-stop.
- **Badge de Estado:** `capturada`, `pendiente`, `indexada`, `fallida`.
- **Referencia de Nota (cita):** usada en las respuestas del Q&A.
- **Wizard de Brainstorming:** stepper de 9 pasos con progreso (Paso X/9),
  navegación atrás/siguiente y, por paso, hasta 2 **sugerencias** y un campo
  propio.
- **Botón "Sugerir" / "Guardar como Nota":** acciones por paso/respuesta.

## Core Screens

1. **Pantalla principal (Playbook):** notas agrupadas por track; TopAppBar
   ("Playbook" + iconos GDD/Brainstorming/Preguntar); FAB desplegable de captura.
2. **Nueva nota / Editor:** cuerpo + track + géneros; **dictado inline 30s**.
3. **Bocetos (captura):** cámara / galería → Nota con imagen (+ **miniatura** en
   listas).
4. **Brainstorming (wizard):** 9 pasos para crear/iterar el Proyecto de Juego;
   sugerencias de IA; crear Nota por paso.
5. **GDD (documento del Proyecto):** ficha del Proyecto (Proyecto, Historia/Lore,
   Personajes) + Notas por track; exportar/compartir.
6. **Preguntar (Q&A):** pregunta + respuesta con citas; "Guardar como Nota".
7. **Notas Relacionadas:** enlaces por similitud desde una nota.
8. **Estado de indexado:** pendiente/fallida + reintento (discreto).

## Responsive Baseline

Teléfonos primero; tablets y pantallas grandes con ancho de contenido cómodo.
Sin Desktop/Web en el MVP.

## Accessibility Baseline

- Contraste AA (≥ 4.5:1 texto normal).
- Objetivos táctiles ≥ 48dp (incluye iconos de la TopAppBar y el FAB).
- Soporte de tipografía dinámica / tamaños de sistema.
- Estados e iconos no comunicados solo por color (con forma/texto/`contentDescription`).

## Do's and Don'ts

- **Sí:** calidez, papel/tinta, jerarquía por agrupación, captura inmediata,
  GDD como documento.
- **Sí:** distinguir track y estado con más que color.
- **No:** look corporativo/frío, dashboards densos, decoración que estorbe la
  captura.

## Open Design Questions

- Paleta, tipografía y logo definitivos (equipo UI/UX).
- ¿Cómo representar visualmente cada track con colores, a la vez accesibles?
- ¿Cómo se ve "sin indexar / pendiente / fallida" sin parecer error?
- ¿Cómo se diferencia visualmente enlace semántico de relación manual (futuro)?
- Relación visual entre la **miniatura** de un boceto y el chip de estado.
- Presentación de las **2 sugerencias** de IA en cada paso del wizard (inline,
  tarjetas, carrusel).
- Aspecto del **FAB desplegable** y su expansión/colapso.
