# Storyboard del vídeo demo del MVP con IA

- **Versión:** 1.0
- **Fecha de planificación:** 2026-10-22
- **Feature:** `video-script-mvp-ai` (spec: [`docs/specs/video-script-mvp-ai.md`](../specs/video-script-mvp-ai.md))
- **Narración y tiempos:** [guion-demo-mvp.md](guion-demo-mvp.md)
- **Pre-flight, captura y publicación:** [plan-grabacion-demo-mvp.md](plan-grabacion-demo-mvp.md)
- **Duración total:** 14:10 (850 s); 13:30 de contenido y 0:40 de cierre; límite 15:00.
- **Formato maestro propuesto:** 1920×1080, 30 fps, H.264, audio de voz; dispositivo vertical
  a la derecha en los planos de demo. La resolución y el nombre final del archivo quedan
  **a confirmar por el autor** (ver [plan de grabación §11](plan-grabacion-demo-mvp.md#11-publicación-en-wetransfer)).
- **Regla de assets:** sólo se reutilizan [`icon.png`](../design/icon.png) y
  [`paleta.png`](../design/paleta.png). No se crean imágenes, PDFs ni diapositivas binarias.
  Las capturas de la app se describen como **“a capturar en la grabación”**.

---

## 1. Principios visuales

1. **Una idea por plano.** El texto en pantalla es un titular corto y, como máximo, tres
   apoyos. La narración explica; la diapositiva no compite.
2. **La app manda.** En la demo, el 70 % del plano es la ventana del dispositivo. Los
   laterales solo llevan el nombre de la superficie y el tiempo.
3. **Contraste y legibilidad.** Fondo claro de la paleta Playbook, texto oscuro, tipografía
   del design system. Nada de texto sobre zonas con movimiento.
4. **Honestidad visual.** Un loading real se ve; si se recorta, se corta al resultado real.
   No se usan maquetas que parezcan la app.
5. **Seguridad.** Nunca entra en plano la key, el archivo local de propiedades, una terminal
   con variables de entorno ni un panel de logs que pueda contenerla.
6. **Continuidad.** Los planos estáticos usan el mismo fondo, el mismo icono y la misma
   tipografía. Los cortes a la demo son secos; los cambios de tema, fundidos de 0,3 s.

**Paleta de referencia:** [`paleta.png`](../design/paleta.png) y [DESIGN.md](../../DESIGN.md).
No se inventan colores nuevos: se usan los tokens del design system.

---

## 2. Secuencia de planos

| # | in | out | dur | Plano | Tipo | Composición | Texto en pantalla | Acción esperada |
|---|---:|---:|---:|---|---|---|---|---|
| P00 | 0:00 | 0:40 | 0:40 | Portada | Diapositiva full-screen | Icono grande a la izquierda; título y subtítulo a la derecha; franja de paleta abajo | “Playbook” / “MVP Android-first · IA real” / “Demo del MVP” | Fundido de entrada; sin movimiento de cámara |
| P01 | 0:40 | 2:00 | 1:20 | Producto y tesis | Diapositiva con diagrama | Diagrama horizontal de 4 pasos; captura de la lista a la derecha | “Captura → Nota → Proyecto → GDD vivo” / “La IA sugiere · tú confirmas” | Aparecen los pasos en orden; la captura entra con un corte |
| P02 | 2:00 | 3:20 | 1:20 | Arquitectura | Diapositiva técnica | Diagrama de módulos arriba; tabla de persistencia y puertos abajo | “iosApp → :composeApp → :core” / “SQLDelight 2.1.0 · esquema v8” / “fake 8 dim · real 1536 dim” | Se resaltan módulos y después la fila de IA |
| P03 | 3:20 | 4:50 | 1:30 | Harness / SDD | Diapositiva de flujo | Flujo de tres roles; debajo, estados y gate; terminal a la derecha | “planner → implementer → validator” / “passing ≠ accepted” / “CI=true ./init.sh” | Se recorren los estados; la terminal muestra el gate real |
| P04 | 4:50 | 5:50 | 1:00 | Herramientas | Matriz 2×2 | Chat, CLI, IDE y ADE; cada celda con uso y evidencia | “Gemini · CLI · Android Studio · OpenCode” / “cada herramienta, su trabajo” | Se ilumina una celda por frase; sin pantallas de chat |
| P05 | 5:50 | 6:35 | 0:45 | Demo · onboarding | Captura de dispositivo | Ventana vertical centrada; panel lateral “5.1 Onboarding” | “Instalación limpia” / “Sin Proyecto ni Notas” | Primer arranque; “Iniciar Brainstorming” |
| P06 | 6:35 | 7:25 | 0:50 | Demo · Brainstorming | Captura de dispositivo | Ventana vertical; panel lateral “5.2 Brainstorming” | “Paso 1/9” / “Sugerir → Usar / Ignorar” / “Guardar” | Título, sugerencia real, Género, Guardar |
| P07 | 7:25 | 8:20 | 0:55 | Demo · Nueva Nota | Captura de dispositivo | Ventana vertical; panel lateral “5.3 Nueva Nota” | “Sugerir ideas” / “Sugerir clasificación” / “Guardar” | Cuerpo, ideas, clasificación, guardado |
| P08 | 8:20 | 8:55 | 0:35 | Demo · GDD | Captura de dispositivo | Ventana vertical; panel lateral “5.4 GDD híbrido” | “Cabecera GDD — título” / “Solo campos no vacíos” / “Proyección pura” | Abrir GDD, recorrer cabecera/ficha y sección |
| P09 | 8:55 | 10:10 | 1:15 | Demo · Q&A | Captura de dispositivo | Ventana vertical; panel lateral “5.5 Q&A” | “Respuesta con fuentes” / “Guardar como Nota” / “Leer en voz alta” | Pregunta, citas, guardar, reproducir audio |
| P10 | 10:10 | 10:45 | 0:35 | Demo · relacionadas | Captura de dispositivo | Ventana vertical; panel lateral “5.6 Relacionadas” | “Embeddings · similitud coseno” / “INDEXED / PENDING” / “Umbral 0.8” | Ver relacionadas o reindexar |
| P11 | 10:45 | 11:20 | 0:35 | Demo · imagen | Captura de dispositivo | Ventana vertical; panel lateral “5.7 Imagen” | “Generar con Playbook” / “Nota + Adjunto” | Prompt, preview, guardar |
| P12 | 11:20 | 12:20 | 1:00 | Resultados e historia | Diapositiva de datos | Línea temporal arriba; cuatro tarjetas de métricas debajo | “52 features · 51 accepted · 1 blocked” / “actualizar al grabar” | Se resaltan las fechas y la fuente |
| P13 | 12:20 | 13:30 | 1:10 | Lecciones y futuro | Diapositiva a dos columnas | Izquierda “qué funciona”; derecha “deuda / siguiente paso” | “La IA acelera · la evidencia decide” / “iOS congelado · sin E2E · key-gated” | Se subrayan 3 lecciones y 3 límites |
| P14 | 13:30 | 14:10 | 0:40 | Cierre | Diapositiva full-screen | Icono, “Playbook” y tarjeta de enlaces; fondo de paleta | “README · ARCHITECTURE · PROGRESS” / “CI=true ./init.sh” / “Gracias” | Fundido a negro |
| P15 | 14:10 | 15:00 | 0:50 | Margen | Tarjeta estática | Solo el icono y “Playbook” sobre fondo neutro | “Playbook” | Se mantiene si hace falta margen; no lleva narración |

**Suma de planos:** 40 + 80 + 80 + 90 + 60 + 45 + 50 + 55 + 35 + 75 + 35 + 35 + 60 + 70 + 40 = **850 s = 14:10**.
**Contenido (P00–P13):** 810 s = **13:30**. **Cierre (P14):** 40 s. **Margen (P15):** 50 s, no se publica salvo que sobre tiempo.

---

## 3. Detalle por plano

### P00 · Portada · 0:00–0:40

- **Composición:** fondo claro; [`icon.png`](../design/icon.png) a la izquierda, a un 22–25 %
  del ancho; a la derecha, “Playbook” en titular y dos líneas de subtítulo; una franja
  inferior con [`paleta.png`](../design/paleta.png) como referencia cromática, sin texto
  encima.
- **Texto en pantalla:** “Playbook” · “MVP Android-first · IA real” · “Demo del MVP”.
- **Notas visuales:** no animar el icono ni hacer zoom. El primer fotograma debe ser estable
  para que el evaluador lea el título. Sin música.
- **Acción:** solo entrada y salida. La narración arranca en el primer fotograma.
- **Clip de respaldo:** no aplica; es una diapositiva.

### P01 · Producto y tesis · 0:40–2:00

- **Composición:** tercio superior con el diagrama “Captura → Nota → Proyecto → GDD vivo”;
  dos tercios inferiores con una captura real de la lista de Notas (a capturar en la
  grabación) y tres etiquetas: `Track`, `Géneros`, `Q&A`.
- **Texto en pantalla:** “Captura → Nota → Proyecto → GDD vivo” / “La IA sugiere · tú
  confirmas” / “local-first · single-user”.
- **Notas visuales:** el diagrama se construye por pasos, pero sin animaciones largas. La
  captura de la lista debe estar limpia, sin notificaciones ni datos personales.
- **Acción:** aparece cada paso del diagrama al nombrarlo; la captura entra cuando se dice
  “GDD vivo”.
- **Fuente visible:** [`README.md` §2–3](../../README.md).

### P02 · Arquitectura · 2:00–3:20

- **Composición:** diagrama de tres cajas (`iosApp`, `:composeApp`, `:core`) con flechas de
  dependencia; debajo, dos columnas: “Persistencia” (SQLDelight 2.1.0, v8, `1.sqm`–`7.sqm`,
  repos) e “IA” (`AiClient`, puertos por modelo, fake/real, 8/1536).
- **Texto en pantalla:** “iosApp → :composeApp → :core” / “SQLDelight 2.1.0 · esquema v8” /
  “fake 8 dim · real 1536 dim” / “iOS congelado”.
- **Notas visuales:** usar [`paleta.png`](../design/paleta.png) como muestra pequeña en la
  esquina de “design system”; no llenar la diapositiva de logos. Resaltar `:core` cuando se
  habla de dominio y persistencia; resaltar `:composeApp` al hablar de UI.
- **Acción:** iluminar cada caja al nombrarla. La tabla de migraciones se ve completa, sin
  scroll.
- **Fuente visible:** [`ARCHITECTURE.md`](../../ARCHITECTURE.md), [ADR 0004](../adr/0004-ai-real-integration.md).

### P03 · Harness / SDD · 3:20–4:50

- **Composición:** flujo horizontal de tres roles; debajo, la cadena de estados
  `not_started → in_progress → passing → accepted`; a la derecha, una terminal con la salida
  real de `CI=true ./init.sh` (a capturar en la grabación).
- **Texto en pantalla:** “planner → implementer → validator” / “passing ≠ accepted” /
  “gate Android-only · IA fake” / “CI=true ./init.sh”.
- **Notas visuales:** no mostrar rutas personales ni el nombre de usuario. La terminal se
  recorta para que solo se vea el comando y el resultado. Si la terminal tiene historial,
  limpiarla antes de grabar. El gate y sus tests automáticos son fake; el smoke real es manual
  y key-gated, no forma parte del gate ni se presenta como test automático.
- **Acción:** se recorre el flujo de izquierda a derecha; la terminal aparece al decir
  “gate”. Si el gate no se ha ejecutado en la toma, se usa un clip real de respaldo.
- **Fuente visible:** [`AGENTS.md`](../../AGENTS.md), [`README.md` §7 y §9](../../README.md), [plan de ejecución](../mvp-execution-plan.md).

### P04 · Matriz de herramientas · 4:50–5:50

- **Composición:** matriz 2×2. Arriba izquierda **Chat** (Gemini); arriba derecha **CLI**
  (terminal, Gradle, adb, git); abajo izquierda **IDE** (Android Studio); abajo derecha
  **ADE** (OpenCode Desktop + VS Code). Cada celda: uso en una línea y evidencia en otra.
- **Texto en pantalla:** “Gemini · apoyo puntual” / “CLI · ejecuta y verifica” / “Android
  Studio · valida y muestra” / “OpenCode · specs, skills, worktrees” / “cada herramienta, su
  trabajo”.
- **Notas visuales:** no se muestra una captura de Gemini salvo que el autor lo pida. No se
  etiqueta toda la pantalla como “ADE”; la etiqueta se explica en la celda de OpenCode.
- **Acción:** se ilumina una celda por frase. Sin transiciones que oculten el texto.
- **Fuente visible:** [guion §5](guion-demo-mvp.md#5-herramientas-en-pantalla), [plan de grabación](plan-grabacion-demo-mvp.md).

### P05 · Demo · onboarding · 5:50–6:35

- **Composición:** ventana de dispositivo de Android Studio, vertical, centrada y a altura
  completa; panel lateral izquierdo con “5.1 · Onboarding” y el tiempo. Fondo neutro.
- **Texto en pantalla (panel):** “Instalación limpia” / “Sin Proyecto ni Notas”.
- **Notas visuales:** la barra de estado del Pixel debe estar limpia; notificaciones
  silenciadas. No se ve la barra de herramientas del IDE con rutas. El reflejo de la ventana
  no debe tapar la app.
- **Acción:** primer arranque real; “Iniciar Brainstorming”; transición a “Paso 1/9”.
- **Clip de respaldo:** B01.
- **Lower-third:** L5 (5:54–6:02).

### P06 · Demo · Brainstorming · 6:35–7:25

- **Composición:** misma ventana; panel “5.2 · Brainstorming”; zoom suave al campo de texto
  y a las tarjetas de sugerencia.
- **Texto en pantalla (panel):** “Paso 1/9” / “Sugerir → Usar / Ignorar” / “Guardar”.
- **Notas visuales:** no se tapa la salida de la IA. Si tarda, se muestra “Sugiriendo…” y
  luego se corta al resultado real. No se escribe la sugerencia a mano. “Guardar” persiste
  solo los campos completados (Título y Géneros) y vuelve a la lista; Cancelar no escribe.
- **Acción:** escribir “Proyecto Aurora”; “Sugerir”; “Usar”; pasar a “Género”; sugerir;
  “Guardar”.
- **Clip de respaldo:** B02.
- **Lower-third:** ninguno; el panel ya da contexto.

### P07 · Demo · Nueva Nota · 7:25–8:20

- **Composición:** ventana vertical; panel “5.3 · Nueva Nota”. Cuando aparece la sección de
  sugerencias, hacer un zoom del 10–15 % para que se lea; volver al plano completo al
  guardar.
- **Texto en pantalla (panel):** “Sugerir ideas” / “Sugerir clasificación” / “Guardar”.
- **Notas visuales:** el teclado puede tapar la mitad inferior; si ocurre, cerrarlo antes de
  pulsar “Sugerir”. En Nueva nota se renderiza el panel “Dictado”: mantenerlo fuera de cuadro
  y no interactuar. Si entra en el plano, repetir la toma o usar B03. No se usa ni se simula;
  la transcripción de Adjuntos tampoco forma parte de la demo.
- **Acción:** cuerpo; “Sugerir ideas” → “Añadir”; “Sugerir clasificación” → “Confirmar”;
  “Guardar”.
- **Clip de respaldo:** B03.

### P08 · Demo · GDD híbrido · 8:20–8:55

- **Composición:** ventana vertical; panel “5.4 · GDD híbrido”. Plano completo durante la
  cabecera y la ficha, y zoom a la sección de la Nota. La cabecera es `GDD — <título>` (o solo
  “GDD”); la ficha solo muestra los campos no vacíos, en orden fijo.
- **Texto en pantalla (panel):** “Cabecera GDD — título” / “Solo campos no vacíos” /
  “Proyección pura”.
- **Notas visuales:** en esta toma solo hay Título y Géneros: el Título va en la cabecera y
  Géneros es el único campo de la ficha. No se enumeran campos vacíos. No abrir la hoja de
  compartir si tapa la pantalla; el botón “Compartir” se muestra, no necesariamente se pulsa
  en la toma principal.
- **Acción:** “Ver GDD”; recorrer cabecera, ficha (solo campos no vacíos) y sección; volver.
- **Clip de respaldo:** B04.

### P09 · Demo · Q&A · 8:55–10:10

- **Composición:** ventana vertical; panel “5.5 · Q&A”. Al aparecer “Fuentes”, zoom a la
  lista numerada. Al pulsar “Leer en voz alta”, mantener el plano y dejar oír 2–3 s.
- **Texto en pantalla (panel):** “Respuesta con fuentes” / “Guardar como Nota” / “Leer en voz
  alta”.
- **Notas visuales:** el audio de la app entra por el micro; no taparlo con la voz. Bajar la
  voz durante la reproducción. No mostrar el diálogo de guardar más de 3 s.
- **Acción:** pregunta; enviar; “Guardar como Nota” → “Guardar”; “Leer en voz alta”.
- **Clip de respaldo:** B05.
- **Lower-third:** L6 (8:58–9:06).

### P10 · Demo · relacionadas · 10:10–10:45

- **Composición:** ventana vertical; panel “5.6 · Relacionadas”. Si el estado es `PENDING`,
  mostrar el botón “Re-indexar” y el cambio a `INDEXED`.
- **Texto en pantalla (panel):** “Embeddings · similitud coseno” / “INDEXED / PENDING” /
  “Umbral 0.8”.
- **Notas visuales:** no cortar justo cuando aparece el estado; dejar 1 s para que se lea. El
  enlace exige similitud ≥ 0.8 y reindexar no lo garantiza: comprobar el par en el ensayo; si
  no supera 0.8, reformular en el ensayo o usar B06 real. No presentar un estado vacío como si
  tuviera enlaces.
- **Acción:** abrir Nota; “Ver relacionadas”; si hace falta, “Re-indexar”; volver a abrir.
- **Clip de respaldo:** B06.

### P11 · Demo · imagen · 10:45–11:20

- **Composición:** ventana vertical; panel “5.7 · Imagen”. Preview centrado; después, la Nota
  con Adjunto en la lista.
- **Texto en pantalla (panel):** “Generar con Playbook” / “Nota + Adjunto”.
- **Notas visuales:** no mostrar el prompt completo si contiene datos personales; el prompt
  es del autor y debe ser genérico. La imagen generada es la real; no se sustituye por un
  asset del repo.
- **Acción:** “Añadir imagen” → “Generar con Playbook”; prompt; generar; guardar.
- **Clip de respaldo:** B07.

### P12 · Resultados e historia · 11:20–12:20

- **Composición:** línea temporal horizontal con cinco hitos; debajo, cuatro tarjetas:
  features, commits, specs, validaciones. En la esquina, “Fuente: PROGRESS.md ·
  feature_list.json · git log” y “actualizar al grabar”.
- **Texto en pantalla:** “52 features · 51 accepted · 1 blocked” / “85 commits” / “30
  validaciones” / “51 specs” / “sesiones 001–101” / “actualizar al grabar”.
- **Notas visuales:** los números se actualizan antes de grabar; si cambian, se cambia la
  diapositiva, no la narración. No usar gráficos que sugieran una tendencia inventada.
- **Acción:** se resaltan las fechas 2026-09-21, 2026-09-24, 2026-10-02, 2026-10-15 y
  2026-10-21.
- **Fuente visible:** [`PROGRESS.md`](../../PROGRESS.md), [`feature_list.json`](../../feature_list.json).

### P13 · Lecciones y futuro · 12:20–13:30

- **Composición:** dos columnas. Izquierda “qué funciona”: gate fake, validación
  independiente, paralelismo controlado. Derecha “deuda / siguiente paso”: iOS congelado,
  publicación, E2E, dark mode, miniaturas, enlaces. Abajo, una frase de cierre.
- **Texto en pantalla:** “La IA acelera · la evidencia decide” / “iOS congelado · sin E2E ·
  key-gated” / “descongelar iOS · publicar · medir”.
- **Notas visuales:** no prometer fechas. “Siguiente paso” es una dirección documentada en
  [riesgos](../risks-and-open-questions.md), no un roadmap comprometido.
- **Acción:** se subrayan tres lecciones y tres límites; sin animación de lista interminable.
- **Fuente visible:** [`README.md` §8–11](../../README.md), [riesgos](../risks-and-open-questions.md).

### P14 · Cierre · 13:30–14:10

- **Composición:** full-screen con [`icon.png`](../design/icon.png) pequeño arriba, “Playbook”
  y una tarjeta con `README.md`, `ARCHITECTURE.md`, `PROGRESS.md` y `CI=true ./init.sh`.
- **Texto en pantalla:** “README · ARCHITECTURE · PROGRESS” / “CI=true ./init.sh” / “Gracias”.
- **Notas visuales:** sin música; sin créditos no confirmados. Fundido a negro de 0,5 s.
- **Acción:** estático; solo el fundido final.

### P15 · Margen · 14:10–15:00

- **Composición:** tarjeta estática con el icono y “Playbook” sobre fondo neutro.
- **Texto en pantalla:** “Playbook”.
- **Notas visuales:** no se publica como contenido; existe para que el editor tenga 50 s de
  margen. No lleva narración ni música.
- **Acción:** ninguna.

---

## 4. Assets existentes y capturas

| Asset | Uso | Estado |
|---|---|---|
| [`docs/design/icon.png`](../design/icon.png) | Portada P00, cierre P14, margen P15 | Existente y versionado |
| [`docs/design/paleta.png`](../design/paleta.png) | Franja de portada P00 y muestra de design system P02 | Existente y versionado |
| Captura de la lista de Notas | P01 | **A capturar en la grabación** |
| Terminal del gate | P03 | **A capturar en la grabación** |
| Ventana de dispositivo | P05–P11 | **A capturar en la grabación** |
| Diapositivas de datos | P12–P13 | Se construyen en el editor; no son assets binarios del repo |

No se crean ni se versionan imágenes nuevas, `.pptx`, `.key`, PDFs ni clips. Los clips de
respaldo se guardan fuera del repo o en una carpeta ignorada por git, y se borran al cerrar
la sesión (ver [plan de grabación §12](plan-grabacion-demo-mvp.md#12-limpieza)).

---

## 5. Lower-thirds

| Código | Entrada | Salida | Texto | Plano |
|---|---:|---:|---|---|
| L1 | 0:04 | 0:10 | Playbook · demo del MVP · 2026 | P00 |
| L2 | 2:04 | 2:12 | `:core` + `:composeApp` · esquema v8 | P02 |
| L3 | 3:24 | 3:32 | planner → implementer → validator | P03 |
| L4 | 4:54 | 5:02 | Gemini · CLI · Android Studio · OpenCode | P04 |
| L5 | 5:54 | 6:02 | Demo real · Pixel 8 Pro · Android 17 | P05 |
| L6 | 8:58 | 9:06 | Q&A con citas · RAG sobre tus Notas | P09 |

**Reglas:** máximo 3–5 s por lower-third, tipografía del design system, fondo con contraste
suficiente y sin tapar la app. No se usan para repetir la narración palabra por palabra.

---

## 6. Clips de respaldo

Se graban en el **ensayo**, con el mismo build real y la misma app. No son maquetas. Si una
superficie falla en la toma principal, se inserta su clip completo, con el mismo audio o con
la narración grabada encima, y se declara si hace falta.

| Código | Superficie | Duración objetivo | Contenido mínimo | Se usa si falla |
|---|---|---:|---|---|
| B01 | Onboarding / Proyecto | 0:45 | Primer arranque limpio, “Iniciar Brainstorming”, “Paso 1/9” | P05 |
| B02 | Brainstorming | 0:50 | Título, “Sugerir”, “Usar”, Género, “Guardar” | P06 |
| B03 | Nueva Nota | 0:55 | Cuerpo, “Sugerir ideas”, “Sugerir clasificación”, “Guardar” | P07 |
| B04 | GDD híbrido | 0:35 | Cabecera + ficha (solo campos no vacíos) + sección por Track | P08 |
| B05 | Q&A | 1:15 | Respuesta, “Fuentes”, “Guardar como Nota”, “Leer en voz alta” | P09 |
| B06 | Relacionadas | 0:35 | `INDEXED`, “Ver relacionadas”, tarjetas con similitud ≥ 0.8 | P10 |
| B07 | Imagen | 0:35 | “Generar con Playbook”, preview, guardado | P11 |

**Prohibiciones:** un clip de respaldo no puede ser de una versión anterior que no coincida
con la UI actual, no puede llevar fake y no puede contener la key. Si no hay clip real, se
detiene la grabación y se reprograma; no se rellena con una maqueta.

---

## 7. Continuidad y transiciones

- **P00 → P01:** corte seco al terminar la primera frase. Sin fundido largo.
- **P01 → P02:** fundido de 0,3 s; el diagrama de producto se desvanece y entra el de
  módulos.
- **P02 → P03:** corte; la terminal entra desde la derecha si ya está grabada.
- **P03 → P04:** corte; la matriz ocupa el mismo fondo.
- **P04 → P05:** corte a la ventana de dispositivo; medio segundo de silencio antes de la
  narración de la demo.
- **Dentro de la demo:** cortes secos entre superficies; los loadings se recortan con
  `[JUMP-CUT]` al resultado real. No se usan transiciones que oculten si el resultado es
  real.
- **P11 → P12:** corte a la diapositiva de resultados.
- **P12 → P13:** fundido de 0,3 s.
- **P13 → P14:** corte al cierre; fundido a negro al final.

**Continuidad de estado:** la demo se graba en una sola sesión, en el orden P05–P11, sin
reinstalar entre superficies. Si hay que reiniciar, se vuelve a empezar la demo desde P05 o
se usan los clips B01–B07, no se mezclan estados.

---

## 8. Verificación de duración del storyboard

| Tramo | Planos | Duración |
|---|---|---:|
| Apertura + producto | P00–P01 | 2:00 |
| Arquitectura | P02 | 1:20 |
| Harness + herramientas | P03–P04 | 2:30 |
| Demo | P05–P11 | 5:30 |
| Resultados | P12 | 1:00 |
| Lecciones | P13 | 1:10 |
| **Contenido** | P00–P13 | **13:30** |
| Cierre | P14 | 0:40 |
| **Total planificado** | P00–P14 | **14:10** |
| Margen | P15 | 0:50 |
| **Límite** | P00–P15 | **15:00** |

La suma coincide con la tabla del [guion](guion-demo-mvp.md#3-tabla-de-tiempos). Si el
ensayo cambia un plano, se actualizan los dos documentos a la vez.

---

## 9. Enlaces

- [Guion del vídeo](guion-demo-mvp.md)
- [Plan de grabación](plan-grabacion-demo-mvp.md)
- [README](../../README.md) · [ARCHITECTURE](../../ARCHITECTURE.md) · [PROGRESS](../../PROGRESS.md)
- [Plan de ejecución del MVP](../mvp-execution-plan.md)
- [ADRs](../adr/) · [specs](../specs/) · [validaciones](../validations/)
- [Diseño](../../DESIGN.md) · [`icon.png`](../design/icon.png) · [`paleta.png`](../design/paleta.png)
