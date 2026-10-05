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
---

# Design Direction

> **Estado:** provisional. Paleta, tipografía, logo e iconografía definitivos
> los define el equipo de UI/UX a partir del brief en
> `docs/design/ui-ux-brief.md`. Los tokens YAML de arriba son un punto de
> partida para agentes, **no** una fuente de verdad.

## Overview

Playbook es un cuaderno de diseño de juegos para un único autor (game dev
aficionado). El MVP se organiza en tres tracks: `mecánicas`, `personajes` e
`historia`. La UI es Compose Multiplatform (Material 3) para Android e iOS.

## Existing Design Assets

Ninguno. Solo existe el icono de app por defecto en `iosApp`. Se parte de cero.

## Generated Concept Images

Ninguna todavía. El equipo de UI/UX producirá los conceptos; cuando se acepten,
guardarlos bajo `docs/design/concepts/` y referenciarlos acá.

## Product Feel

**Cuaderno creativo / editorial cálido.** Debe sentirse como "mis ideas": papel,
tinta, tipografía con carácter, calidez. Ni corporativo ni frío, ni un IDE
denso. La captura es el gesto central y debe sentirse inmediata y sin fricción.

## Colors

Provisional (ver front matter). Base papel cálido (`#F7F1E6`) + tinta
(`#2B2118`), primario terracota, secundario verde profundo, acento ámbar. Cada
track debería poder distinguirse con un color/acento propio, siempre con
contraste AA.

## Typography

Provisional. Un serif con carácter para títulos y un sans humanista para cuerpo
y UI. Escala limitada (display, h1, body, label). Números y etiquetas siempre
legibles a tamaño pequeño.

## Layout

- Mobile-first, una columna; navegación por track clara.
- Jerarquía por agrupación (track → nota), no por densidad.
- Insets/edge-to-edge correctos en Android e iOS.

## Shapes

Radios suaves (`sm`/`md`), sin esquinas duras. Bordes sutiles tipo papel.

## Components

- **Selector de Track:** control primario de organización (elegir uno de tres).
- **Chip de Track / Chip de Etiqueta:** identificadores rápidos y editables.
- **Tarjeta de Nota:** cuerpo + track + etiquetas + estado de indexado.
- **Acción de Captura:** entrada rápida a texto / voz / imagen.
- **Badge de Estado:** `capturada`, `pendiente`, `indexada`, `fallida`.
- **Referencia de Nota (cita):** usada en las respuestas RAG.

## Core Screens

1. **Inicio / GDD:** notas agrupadas y resumidas por track.
2. **Lista de Notas:** por track; con estado vacío.
3. **Detalle / Lectura de Nota.**
4. **Editor de Nota de Texto:** cuerpo + track + etiquetas.
5. **Captura de Voz:** grabando / transcribiendo.
6. **Captura de Imagen:** cámara / galería.
7. **Estado de Indexado:** pendiente/fallida + reintento.
8. **Notas Relacionadas:** enlaces por similitud.
9. **Consulta RAG:** pregunta + respuesta con citas a notas.

## Responsive Baseline

Teléfonos primero; tablets y pantallas grandes con ancho de contenido cómodo.
Sin Desktop/Web en el MVP.

## Accessibility Baseline

- Contraste AA (≥ 4.5:1 texto normal).
- Objetivos táctiles ≥ 48dp.
- Soporte de tipografía dinámica / tamaños de sistema.
- Estados no comunicados solo por color (track y estado también con forma/texto).

## Do's and Don'ts

- **Sí:** calidez, papel/tinta, jerarquía por agrupación, captura inmediata.
- **Sí:** distinguir track y estado con más que color.
- **No:** look corporativo/frío, dashboards densos, decoración que estorbe la
  captura.

## Open Design Questions

- Paleta, tipografía y logo definitivos (equipo UI/UX).
- ¿Cómo representar visualmente cada track con colores, a la vez accesibles?
- ¿Cómo se ve "sin indexar / pendiente / fallida" sin parecer error?
- ¿Cómo se diferencia visualmente enlace semántico de relación manual (futuro)?
- ¿Indicador de estado de IA discreto que no bloquee la captura?
