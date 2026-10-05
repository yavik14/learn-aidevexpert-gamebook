# Brief de UI/UX — Playbook

Este documento es el prompt/encargo para el equipo de UI/UX. El objetivo es la
**conceptualización visual** de Playbook para el MVP: identidad, paleta,
tipografía, componentes base y pantallas clave.

---

## Rol

Actuá como diseñador/a de producto UI/UX para una app móvil. Vamos a
conceptualizar la identidad visual y las pantallas del MVP. **No implementes
todavía**: entregá dirección de diseño y mockups.

## Producto

**Playbook** (nombre tentativo) es una app móvil de **notas de diseño de
juegos**. El usuario captura ideas en texto, voz o foto de bocetos, las organiza
y la app va construyendo un **GDD (Game Design Document) vivo y navegable**.
Sobre las notas, el usuario puede hacer consultas en lenguaje natural y recibir
respuestas basadas en sus propias notas.

Es un **proyecto de aprendizaje personal** (un solo autor, game dev aficionado),
no un producto comercial. Prioriza claridad y aprendizaje sobre completitud.

## Usuario

- **Primario:** el autor; game dev aficionado. Único usuario del MVP.
- Usa la app para capturar ideas sueltas y luego encontrarlas y conectarlas.
- Contexto de uso: rápido, en el momento, a veces sin conexión.

## Estructura del producto: 3 tracks

El MVP se organiza en tres pilares (elegidos manualmente por el usuario):

1. **Mecánicas**
2. **Personajes**
3. **Historia**

Cada Nota pertenece a **un** track y puede tener **etiquetas libres**. La IA
**no** clasifica ni etiqueta automáticamente (se descartó del MVP); sí genera
embeddings para enlazar notas por similitud y responder consultas.

## Feel buscado

**Cuaderno creativo / editorial cálido.** Debe sentirse como "mis ideas": papel,
tinta, tipografía con carácter, calidez. Ni corporativo/frío, ni un IDE denso.
La captura es el gesto central: inmediata y sin fricción.

## Plataformas y tecnología

- Android + iOS en paralelo.
- UI en **Compose Multiplatform** (Material 3). Es deseable que los tokens se
  puedan mapear a un theme de Material 3.
- Mobile-first: teléfonos primero, con buen comportamiento en tablets.
- Considerar modo claro (y proponer si el oscuro aplica).
- La UI debe respetar edge-to-edge e insets del sistema.

## Pantallas / superficies a conceptualizar

1. **Inicio / GDD:** notas agrupadas y resumidas por track.
2. **Lista de Notas:** dentro de un track; con estado vacío.
3. **Detalle / Lectura de Nota.**
4. **Editor de Nota de Texto:** cuerpo + selector de track + etiquetas.
5. **Captura de Voz:** grabando y transcribiendo.
6. **Captura de Imagen:** cámara / galería.
7. **Estado de indexado:** pendiente / fallida + reintento (discreto, no bloquea
   la captura).
8. **Notas Relacionadas:** enlaces por similitud desde una nota.
9. **Consulta RAG:** pregunta en lenguaje natural + respuesta con citas a las
   notas usadas.

## Estados transversales a cubrir

- Vacío (sin notas).
- Offline / `pendiente` (la captura nunca se bloquea).
- `fallida` (indexado) con reintento.
- Nota sin indexar (visible y consultable, pero sin enlaces).
- Listas largas y notas casi idénticas (enlaces de score alto).

## Entregables que pedimos

1. **Dirección visual + rationale:** qué se siente y por qué.
2. **Paleta** con valores (hex) y verificación de contraste (AA).
3. **Escala tipográfica** (familias, tamaños, pesos, usos).
4. **Espaciado, radios y elevación** base.
5. **Componentes base:** selector/visor de track, chip de etiqueta, tarjeta de
   nota, acción de captura, badge de estado, referencia/cita de nota, campos de
   entrada.
6. **Iconografía** base (captura por texto/voz/imagen, estados).
7. **3–5 pantallas clave en alta fidelidad** (priorizar: Inicio/GDD, Editor de
   Nota, Captura, Consulta RAG).
8. **Tokens exportables** (Figma/Pencil variables o JSON) si es posible.
9. **Propuesta de logo/wordmark** para "Playbook" (tentativo).

## Restricciones

- **No hay identidad previa:** sin logo, colores ni tipografía de marca. Se
  define desde cero.
- El nombre "Playbook" es **tentativo**; no lo trates como definitivo.
- No usar assets de terceros con licencias dudosas.
- No inventar funcionalidad fuera del MVP (ver siguientes non-goals).

## Non-goals del MVP (no diseñar)

- Multi-usuario, cuentas, autenticación o colaboración.
- Clasificación/etiquetado automático por IA.
- Editor de niveles 2D ni generación de niveles (Fase B).
- Desktop ni Web.

## Accesibilidad

- Contraste AA (≥ 4.5:1 en texto normal).
- Objetivos táctiles ≥ 48dp.
- Soporte de tamaños de tipografía del sistema.
- Track y estado no comunicados **solo** por color.

## Preguntas que queremos que resuelvan

- ¿Cómo se representa cada track con color, forma y texto a la vez, sin ruido?
- ¿Cómo se ve "pendiente / fallida / sin indexar" sin parecer un error grave?
- ¿Cómo diferenciar visualmente un enlace semántico de una relación manual
  (futuro)?
- ¿El feel de cuaderno admite un modo oscuro coherente?

## Formato de respuesta

Resumí primero en 3–5 líneas qué entendiste. Luego presentá la dirección visual
con rationale y los mockups. Si algo no está definido, preguntá una cosa a la
vez antes de asumir.
