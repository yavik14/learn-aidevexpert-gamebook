# Guion del vídeo demo del MVP con IA

- **Versión:** 1.0
- **Fecha de planificación:** 2026-10-22
- **Feature:** `video-script-mvp-ai` (spec: [`docs/specs/video-script-mvp-ai.md`](../specs/video-script-mvp-ai.md))
- **Estado:** listo para ensayar y grabar; el vídeo no se ha grabado en esta tarea.
- **Objetivo de contenido:** 13:30 (810 s).
- **Cierre planificado:** 14:10 (850 s).
- **Límite duro:** 15:00 (900 s). Entre 14:10 y 15:00 solo hay margen de seguridad.
- **Mínimo aceptable:** 10:00 (600 s), con el plan de recortes de la sección 7.
- **Audiencia:** evaluador técnico del curso. No se explican fundamentos de Android, Kotlin, Compose, Git ni IA.
- **Idioma y voz:** español de España, tuteo, primera persona del singular.
- **Formato:** demo Android con **IA real** y datos generados **en vivo**. El modo fake se explica como estrategia de gate y tests, pero **no se graba como si fuera real**.
- **Documentos hermanos:** [storyboard](storyboard-demo-mvp.md) y [plan de grabación](plan-grabacion-demo-mvp.md).
- **Fuentes primarias:** [README](../../README.md), [ARCHITECTURE](../../ARCHITECTURE.md), [`PROGRESS.md`](../../PROGRESS.md), [`feature_list.json`](../../feature_list.json), [`AGENTS.md`](../../AGENTS.md), [plan de ejecución](../mvp-execution-plan.md), [ADRs](../adr/), [specs](../specs/) y [validaciones](../validations/).

> **Vigencia de las métricas.** Las cifras de features, commits, specs, validaciones y sesiones
> cambian entre la planificación y la grabación. En este guion se marcan como
> **“actualizar al grabar”**. La fuente es siempre el repositorio en ese momento, no este
> documento. Snapshot de planificación (antes de esta tarea): 52 features en
> `origin/develop` (51 `accepted`, 1 `blocked`), 85 commits, 30 validaciones, 51 specs y
> sesiones 001–101.

---

## 1. Contrato de duración

| Concepto | Tiempo | Segundos | Regla |
|---|---:|---:|---|
| Bloques 1–7 (contenido narrado) | 13:30 | 810 | Objetivo. La suma de la columna `dur` es exacta. |
| Bloque 8 (conclusiones y cierre) | 0:40 | 40 | Cierre planificado. |
| **Total planificado** | **14:10** | **850** | Objetivo de exportación. |
| Margen de seguridad | 0:50 | 50 | Solo para respiración, titulares o un imprevisto. |
| **Límite duro** | **15:00** | **900** | No se publica nada que lo supere. |
| Recorte mínimo | 10:00 | 600 | Se aplica la tabla de recortes si el ensayo se pasa. |

**Ritmo de lectura:** 140–160 palabras por minuto. A 13:30 eso son unas 1.900–2.150
palabras. La narración de este guion tiene **2.052 palabras** (≈13:30 a 152 ppm); el ensayo
en voz alta manda. Si al leerla en voz alta se pasa de 13:30, se aplican los recortes en el
orden de la sección 7, nunca se acelera la voz hasta que no se entienda.

**Cómo se cuenta el tiempo:** el cronómetro corre desde la primera palabra de la apertura.
Las pausas para enseñar una pantalla cuentan. Los loadings reales se recortan con
**jump-cut honesto al resultado**: se corta la espera, no se fabrica el resultado. Si una
llamada falla, se usa un clip de respaldo real o se detiene la toma; no se cambia a fake.

---

## 2. Convenciones del guion

- `[PANTALLA: …]`: lo que debe verse.
- `[LOWER-THIRD: …]`: rótulo inferior de 3–5 s.
- `[ACCIÓN: …]`: gesto o comando que se ejecuta en directo.
- `[JUMP-CUT: …]`: corte de espera real.
- `«…»`: contenido que produce la IA o el usuario **en vivo**. No se rellena en el guion
  con resultados inventados; se lee lo que aparezca en pantalla.
- La narración literal está en la sección 4. Se lee tal cual, salvo los placeholders
  `«…»`, que se sustituyen por lo que se vea en la grabación.

---

## 3. Tabla de tiempos

### 3.1 Bloques principales

| # | in | out | dur | acum. | Bloque | Objetivo | Qué se ve | Qué se dice (resumen) | Transición | Si falla |
|---|---:|---:|---:|---:|---|---|---|---|---|---|
| 1 | 0:00 | 0:40 | 0:40 | 0:40 | Apertura | Situar al evaluador | Portada con icono, título y “MVP Android-first” | Qué es Playbook y qué podrá comprobar | Corte a la diapositiva de tesis | F1 |
| 2 | 0:40 | 2:00 | 1:20 | 2:00 | Producto y tesis | Entender el producto sin marketing | Diagrama “captura → Nota → GDD vivo” + lista real | Notas, Proyecto, Géneros, Q&A y el patrón “la IA sugiere, tú confirmas” | Fundido a arquitectura | F2 |
| 3 | 2:00 | 3:20 | 1:20 | 3:20 | Arquitectura | Entender cómo está construido | Diagrama de módulos, esquema v8, puertos de IA, fake/real | `:core` + `:composeApp`, SQLDelight, repos, GDD como proyección, iOS congelado | Corte a harness | F3 |
| 4 | 3:20 | 5:50 | 2:30 | 5:50 | Harness, SDD y herramientas | Entender cómo se ha construido | Flujo planner → implementer → validator; terminal; matriz de herramientas | Specs, estados, gate, worktrees, validación independiente, Gemini/CLI/IDE/OpenCode | Corte a la demo | F4 |
| 5 | 5:50 | 11:20 | 5:30 | 11:20 | Demo Android real | Ver el producto funcionando | Ventana del Pixel 8 Pro en Android Studio | Onboarding, Brainstorming, Nota, GDD, Q&A, relacionadas e imagen | Corte a resultados | F5 (por superficie) |
| 6 | 11:20 | 12:20 | 1:00 | 12:20 | Resultados e historia | Medir lo construido | Diapositiva de métricas y línea temporal | Snapshot real, hitos, qué funcionó y qué no | Fundido a lecciones | F6 |
| 7 | 12:20 | 13:30 | 1:10 | 13:30 | Lecciones y futuro | Cerrar el aprendizaje | Matriz “cuándo usar cada herramienta” + deuda | Límites honestos y próximos pasos | Corte a cierre | F7 |
| 8 | 13:30 | 14:10 | 0:40 | 14:10 | Conclusiones y cierre | Cerrar y dirigir al repo | Icono, enlaces y frase final | Síntesis y llamada a explorar el repositorio | Fin | F8 |

### 3.2 Desglose de la demo (bloque 5)

| # | in | out | dur | acum. | Superficie | Objetivo | Qué se ve | Si falla |
|---|---:|---:|---:|---:|---|---|---|---|
| 5.1 | 5:50 | 6:35 | 0:45 | 6:35 | Onboarding / Proyecto | Arranque limpio y ficha | “Tu Proyecto de Juego”, “Iniciar Brainstorming”, “Paso 1/9” | B01 |
| 5.2 | 6:35 | 7:25 | 0:50 | 7:25 | Brainstorming | Sugerencias reales con confirmación | Campo Título, botón “Sugerir”, ideas, “Usar”/“Ignorar”, Género | B02 |
| 5.3 | 7:25 | 8:20 | 0:55 | 8:20 | Nueva Nota | Ideas, clasificación y guardado | Editor, “Sugerir ideas”, “Sugerir clasificación”, “Guardar” | B03 |
| 5.4 | 8:20 | 8:55 | 0:35 | 8:55 | GDD híbrido | Ficha + Notas por Track | “Ver GDD”, ficha del Proyecto, secciones, “Compartir” | B04 |
| 5.5 | 8:55 | 10:10 | 1:15 | 10:10 | Q&A | Respuesta con citas, guardar y leer | “Preguntar”, respuesta, “Fuentes”, “Guardar como Nota”, “Leer en voz alta” | B05 |
| 5.6 | 10:10 | 10:45 | 0:35 | 10:45 | Indexado / relacionadas | Enlaces reales por similitud | “Ver relacionadas”, estado `INDEXED`/`PENDING`, tarjetas | B06 |
| 5.7 | 10:45 | 11:20 | 0:35 | 11:20 | Imagen generada | Cerrar con imagen real | “Generar con Playbook”, preview, guardar con Adjunto | B07 |

- **Suma del bloque 5:** 45 + 50 + 55 + 35 + 75 + 35 + 35 = **330 s = 5:30**.
- **Suma de bloques 1–7:** 40 + 80 + 80 + 150 + 330 + 60 + 70 = **810 s = 13:30**.
- **Suma con cierre:** 810 + 40 = **850 s = 14:10**.

---

## 4. Narración literal

> **Regla de oro:** la narración no afirma resultados que no estén en pantalla. Los
> placeholders `«…»` se sustituyen por lo que devuelva la app en vivo. Si la IA devuelve
> una sola sugerencia o tarda, se dice; no se rellena el hueco con una invención.

### 4.1 Bloque 1 · Apertura · 0:00–0:40

**Objetivo:** que el evaluador sepa qué va a ver y con qué criterio mirarlo.

**En pantalla:** portada a pantalla completa con [`icon.png`](../design/icon.png), título
“Playbook” y subtítulo “MVP Android-first · IA real”. `[LOWER-THIRD: Playbook · demo del MVP · 2026]`.

**Narración (literal):**

> He construido Playbook, una app de notas de diseño de juegos que convierte capturas
> dispersas en un GDD vivo. En los próximos trece minutos y medio te voy a enseñar qué es,
> cómo está construida y, sobre todo, qué puedes comprobar tú mismo en el repositorio. No
> voy a vender humo: verás la app Android funcionando con IA real, datos creados en directo
> y el harness que me ha permitido llegar a un MVP validado feature a feature. Y al final
> tendrás criterio para distinguir lo que está verificado de lo que solo está declarado.

**Acciones:** `[ACCIÓN: mostrar la portada y el lower-third; sin interacción]`.

**Transición:** corte seco a la diapositiva de producto.

**Si falla:** F1.

---

### 4.2 Bloque 2 · Producto y tesis · 0:40–2:00

**Objetivo:** entender la tesis sin explicar fundamentos.

**En pantalla:** diagrama “captura → Nota → Proyecto → GDD vivo”; a la derecha, una captura
real de la lista de Notas. Aparecen las palabras `Track`, `Géneros`, `Q&A`, `Brainstorming`
y `la IA sugiere · tú confirmas`.

**Narración (literal):**

> Playbook nace de un problema concreto: las ideas de un juego viven dispersas en notas,
> documentos y chats. La tesis es simple: cada captura debe alimentar un único documento
> vivo, el GDD del Proyecto. La app es Android-first, local-first y single-user: no hay
> cuentas, backend ni sincronización. Local-first significa que tus datos viven en el
> dispositivo; no hay un servicio remoto que pueda filtrar el proyecto. Capturas texto, voz
> o imagen; clasificas con Track y Géneros; el Proyecto tiene ficha y un Brainstorming de
> nueve pasos. La IA sugiere clasificación, contenido e ideas, pero tú confirmas: nunca
> auto-asigna ni guarda por ti. El GDD combina la ficha con las Notas agrupadas por Track, y
> el Q&A responde con citas de tus propias notas. Lo importante es que la captura no se
> queda en un cajón: alimenta el documento que usarás para diseñar y para explicar el juego.
> Ese es el producto; ahora veamos cómo está construido.

**Acciones:** `[ACCIÓN: aparecen los pasos del diagrama; entra la captura de la lista]`.

**Transición:** fundido a la diapositiva de arquitectura.

**Si falla:** F2.

---

### 4.3 Bloque 3 · Arquitectura · 2:00–3:20

**Objetivo:** que el evaluador pueda situar módulos, persistencia e IA en el repo.

**En pantalla:** diagrama `iosApp → :composeApp → :core`; debajo, una captura del árbol de
`core/src/commonMain` y la tabla de migraciones `1.sqm … 7.sqm`. `[LOWER-THIRD: :core KMP + :composeApp · esquema v8]`.

**Narración (literal):**

> La arquitectura es deliberadamente pequeña. Dos módulos: :core, una librería Kotlin
> Multiplatform sin UI con dominio, persistencia y puertos de IA; y :composeApp, la UI en
> Compose Multiplatform, la app Android y el framework iOS. La dirección de dependencias es
> iosApp → :composeApp → :core. La persistencia es SQLDelight 2.1.0 sobre esquema v8, con
> migraciones de 1.sqm a 7.sqm y claves foráneas activadas. Hay repositorios para Nota,
> Adjunto, Embedding, Enlace y Proyecto; los binarios de imagen y audio viven en storage
> privado y en la base solo guardo rutas relativas. La IA está detrás de puertos: AiClient
> para chat y embeddings, y puertos por modelo para transcribe, tts, image y jev. Un build
> flag alterna fake y real: fake es determinista, sin red y de 8 dimensiones; real usa un
> proveedor OpenAI-compatible y 1536 dimensiones. El gate y sus tests automáticos usan fake;
> el smoke real es manual y key-gated. El
> GDD no es una tabla: es una proyección pura que se calcula desde el Proyecto y las Notas.
> La UI vive bajo PlaybookTheme, con Bricolage Grotesque, JetBrains Mono y cabecera de
> marca. E iOS queda congelado: compila, pero no tiene paridad ni gate.

**Acciones:** `[ACCIÓN: resaltar :core y :composeApp; mostrar la tabla de migraciones]`.

**Transición:** corte a la diapositiva de harness.

**Si falla:** F3.

---

### 4.4 Bloque 4 · Harness, SDD y herramientas · 3:20–5:50

**Objetivo:** explicar el método y separar el uso de cada herramienta.

**En pantalla:** flujo `planner → implementer → validator`; estados `not_started →
in_progress → passing → accepted`; terminal con `CI=true ./init.sh`; y la matriz de
herramientas de la sección 5. `[LOWER-THIRD: Harness SDD · gate Android-only · IA fake]`.

**Narración (literal):**

> Lo que más me interesa enseñarte no es solo la app, sino cómo la he construido. El
> repositorio tiene un harness SDD: AGENTS.md define las reglas; feature_list.json es la
> cola y el estado; PROGRESS.md es la bitácora; docs/specs contiene una spec por feature; y
> docs/validations guarda las validaciones independientes. Cada feature recorre tres roles
> separados: planner, implementer y validator. El planner escribe la spec; el implementer
> implementa y se autoverifica; el validator revisa de forma independiente y devuelve
> accept, revise o block. Cada spec declara escenarios, non-goals, archivos esperados y
> verificación; el implementer no puede ampliar el alcance sin volver al planner. El estado
> va de not_started a in_progress, passing y accepted. Passing significa implementado y
> autoverificado; accepted solo lo habilita un validador independiente. Cuando una
> validación devuelve revise, la feature vuelve al implementer con hallazgos concretos;
> cuando devuelve accept, la sesión integradora es la única que la marca accepted. Así el
> estado no depende de que yo diga que está hecho. Eso no es burocracia: en este proyecto
> la validación independiente encontró defectos reales que el implementer no vio. Trabajo
> una feature por sesión,
> worktree y rama feature/*, con WIP=1 por sesión. El MVP restante se ejecutó con hasta tres
> sesiones en paralelo, coordinadas por una sesión integradora, con una sola cola de merge y
> rebases después de cada integración. El paralelismo funcionó, a costa de rebases y
> conflictos en los hotspots de UI. El gate es ./init.sh: Android-only, no bloqueante, sin
> dev servers y con IA fake. Ejecuta :composeApp:assembleDebug y :core:testDebugUnitTest; no
> usa red ni key. En las herramientas, Gemini me ha apoyado puntualmente en ideas, redacción
> y revisión. La terminal, Gradle, adb y git son la CLI real del proyecto. Android Studio lo
> uso para validar y para la demo, con su ventana de dispositivo y Device Mirroring. OpenCode
> Desktop lo uso como entorno de desarrollo agéntico: brief, subagentes, skills y worktrees;
> y VS Code como editor y terminal. No necesito etiquetar cada herramienta con una sigla: lo
> importante es que el resultado queda en specs, validaciones, PROGRESS y commits. Puedes
> reproducir el gate tú mismo con CI=true ./init.sh.

**Acciones:** `[ACCIÓN: recorrer los tres roles y los estados; mostrar el gate real o su clip]`.

**Transición:** corte a la ventana de dispositivo; silencio de medio segundo antes de la
demo.

**Si falla:** F4.

---

### 4.5 Bloque 5 · Demo Android real · 5:50–11:20

**Objetivo:** demostrar las superficies con IA real y datos creados en directo.

**Condición de entrada:** instalación limpia, sin Proyecto ni Notas. Build real y smoke
completo ya verificado según el [plan de grabación §5](plan-grabacion-demo-mvp.md#5-smoke-real-de-los-modelos);
si el smoke falló solo en `transcribe`, el pre-flight manual de `tts`, `image` y `jev` está
hecho y registrado. La ventana de dispositivo está en vertical, sin notificaciones y sin la
key visible.

#### 4.5.1 Onboarding y Proyecto · 5:50–6:35

**En pantalla:** primer arranque; “Tu Proyecto de Juego”; botones “Iniciar Brainstorming” y
“Saltar”; después, “Paso 1/9” del wizard.

**Narración (literal):**

> Parto de una instalación limpia, sin Proyecto ni Notas. Este es el onboarding de primer
> arranque. Playbook me ofrece empezar con el Brainstorming guiado o saltar y crear el
> Proyecto vacío. Voy a empezar con el Brainstorming. Al continuar, se crea el singleton del
> Proyecto. El onboarding no guarda un flag: la existencia del
> Proyecto es lo que evita que vuelva a ofrecerse. Si salto, se crea un Proyecto vacío y voy
> a la lista; si empiezo el Brainstorming, se crea igual y entro en el wizard. En ambos
> casos, los datos quedan en la base local. Fíjate en que no hay datos precargados: lo que
> veas a partir de aquí lo genera la app en directo.

**Acciones:** `[ACCIÓN: tocar “Iniciar Brainstorming”]`; esperar la transición real.

**Transición:** corte dentro del wizard.

**Si falla:** B01.

#### 4.5.2 Brainstorming con sugerencias · 6:35–7:25

**En pantalla:** paso “Título”, campo de texto, botón “Sugerir”; tarjetas con ideas y
botones “Usar”/“Ignorar”; después, paso “Género” con el mismo patrón.

**Narración (literal):**

> El wizard tiene nueve pasos, todos opcionales. En Título escribo una idea mía, por
> ejemplo «Proyecto Aurora», y pulso Sugerir. Playbook propone hasta dos ideas reales; yo
> decido. Uso una o la ignoro: la IA no escribe por mí. Si la sugerencia no me convence, la
> ignoro sin miedo: el paso sigue vacío y puedo escribirlo a mano. Eso mantiene el control en
> el usuario. Avanzo a Género y repito: sugerencia, confirmación. Este patrón se repite en
> todo el producto: la IA propone y el usuario confirma. Confirmo el género y pulso Guardar:
> la ficha se persiste solo con los campos que he completado, Título y Géneros, y vuelvo a la
> lista. Cancelar no escribe. Los pasos son opcionales y puedo guardar cada paso como Nota,
> pero eso lo dejo para otra toma para no alargar. Ahora sigo para llegar al resto de la demo.

**Acciones:** `[ACCIÓN: escribir «Proyecto Aurora»]`, `[ACCIÓN: pulsar “Sugerir”]`,
`[ACCIÓN: pulsar “Usar” en una idea]`, `[ACCIÓN: pasar a “Género” y sugerir]`,
`[ACCIÓN: pulsar “Guardar”]`.
`[JUMP-CUT: si la sugerencia tarda, cortar la espera y mostrar el resultado real]`.

**Transición:** “Guardar” vuelve a la lista; desde ahí, Nueva nota.

**Si falla:** B02.

#### 4.5.3 Nueva Nota con sugerencias y guardado · 7:25–8:20

**En pantalla:** editor de Nueva nota; cuerpo; “Sugerir ideas”; “Sugerir clasificación”;
Track y Géneros; “Guardar”.

**Narración (literal):**

> Salgo a la pantalla principal y creo una Nota de texto. Escribo el núcleo de una mecánica,
> por ejemplo un combate por turnos con recursos que se agotan. Antes de guardar pulso
> Sugerir ideas: Playbook devuelve hasta dos ideas de contenido y yo añado la que me sirve.
> Luego Sugerir clasificación: propone Track y Géneros; confirmo o descarto. Guardo. La Nota
> queda persistida y el indexado arranca en segundo plano. El indexado es asíncrono: la Nota
> se guarda aunque la IA falle; si falla, queda PENDING y se reintenta. Eso es importante:
> la captura no se bloquea por la red. Si una llamada tarda, corto honestamente al
> resultado; no fabrico la respuesta.

**Acciones:** `[ACCIÓN: abrir “Nueva nota”]`, `[ACCIÓN: escribir el cuerpo]`,
`[ACCIÓN: “Sugerir ideas” → “Añadir”]`, `[ACCIÓN: “Sugerir clasificación” → “Confirmar”]`,
`[ACCIÓN: “Guardar”]`. `[JUMP-CUT: de “Sugiriendo…” al resultado real]`.

**Riesgo asumido (E):** en Nueva nota se renderiza el panel “Dictado”. Mantenerlo fuera de
cuadro y no interactuar; si entra en el plano, repetir la toma o usar B03. No se usa ni se
simula.

**Transición:** volver a la lista y abrir el GDD.

**Si falla:** B03.

#### 4.5.4 GDD híbrido · 8:20–8:55

**En pantalla:** pantalla “GDD” con la cabecera `GDD — <título>` (o solo “GDD”), la ficha del
Proyecto debajo y las secciones por Track; la Nota recién creada aparece en su sección; botón
“Compartir”.

**Narración (literal):**

> Abro el GDD. No es una pantalla que guarde un documento aparte: se calcula desde el
> Proyecto y las Notas. La cabecera lleva el título del Proyecto, «GDD — Proyecto Aurora», y
> debajo está la ficha; después, las secciones por Track. La ficha muestra solo los campos no
> vacíos y en un orden fijo; el Título va en la cabecera, no como campo. En esta toma solo he
> rellenado Título y Géneros, así que verás el Título arriba y Géneros como único campo de la
> ficha; los que no he rellenado no aparecen. La Nota que acabo de guardar aparece en su
> sección. Compartir genera Markdown en memoria, sin archivos temporales.

**Acciones:** `[ACCIÓN: abrir “Ver GDD”]`; recorrer la cabecera, la ficha (solo campos no
vacíos) y bajar hasta la sección de la Nota. No pulsar “Compartir” si el sistema abre una hoja
que tape la demo; si se pulsa, volver con Back.

**Orden fijo de la ficha (referencia, no se narra):** Géneros, Core Loop, Audiencia, Concepto,
Inspiraciones, Fantasía, Mecánicas clave y Fuera de alcance. El Título va en la cabecera, no
como campo. En esta toma solo aparecen el Título (cabecera) y Géneros.

**Transición:** volver a la principal y abrir Q&A.

**Si falla:** B04.

#### 4.5.5 Q&A con citas, guardar y leer en voz alta · 8:55–10:10

**En pantalla:** pantalla “Preguntar”; pregunta; respuesta; “Fuentes” numeradas; “Guardar
como Nota”; diálogo de confirmación; “Leer en voz alta” y reproducción.

**Narración (literal):**

> Ahora el Q&A. Pregunto en lenguaje natural por lo que acabo de escribir. Playbook recupera
> las Notas más similares, genera una respuesta y me enseña las fuentes numeradas. Esto es
> RAG sobre mis notas, no un chat genérico: si no hay notas indexadas, no inventa. La
> respuesta no es un texto suelto: cada fuente lleva su rango, un fragmento y el Track. Si la
> respuesta no me convence, puedo abrir la fuente. La respuesta me sirve, así que pulso
> Guardar como Nota; la Nota se crea sin track y sin géneros, y se indexa también. Si ya
> existe una Nota igual, la app avisa y no duplica. Por último, pulso Leer en voz alta y
> escucho la respuesta con el modelo de tts. Si el audio falla, no lo simulo: uso un clip
> real o repito la toma.

**Acciones:** `[ACCIÓN: escribir una pregunta sobre la Nota]`, `[ACCIÓN: enviar]`,
`[ACCIÓN: “Guardar como Nota” → “Guardar”]`, `[ACCIÓN: “Leer en voz alta”]`.
`[JUMP-CUT: de “Pensando…” a la respuesta real]`; dejar sonar 2–3 s de audio real.

**Transición:** volver a la Nota y abrir relacionadas.

**Si falla:** B05.

#### 4.5.6 Indexado y notas relacionadas · 10:10–10:45

**En pantalla:** editor o lista con el estado de indexado; “Ver relacionadas”; tarjetas con
puntuación y chips. Si el estado es `PENDING`, se espera o se reindexa.

**Narración (literal):**

> Con dos Notas, abro Ver relacionadas. El enlace sale de la similitud entre embeddings; el
> estado de indexado es visible y verdadero. Si una Nota aún está PENDING, espero o la
> reindexo. No hay magia: hay un umbral de 0.8 y un cálculo de similitud coseno. Los enlaces
> se recalculan de forma best-effort al abrir; es una solución de MVP, no un motor de
> grafos.

**Acciones:** `[ACCIÓN: abrir la Nota guardada]`, `[ACCIÓN: “Ver relacionadas”]`; si aún no
hay enlace, `[ACCIÓN: “Re-indexar”]` y abrir de nuevo. `[JUMP-CUT: de la espera al estado
INDEXED real]`.

**Riesgo asumido (D):** el enlace exige similitud ≥ 0.8 y reindexar no lo garantiza. Antes de
grabar, comprobar el par de Notas en el ensayo; si no supera 0.8, reformular en el ensayo o
usar B06 real. No presentar un estado vacío como si tuviera enlaces.

**Transición:** volver a la principal y abrir “Añadir imagen”.

**Si falla:** B06.

#### 4.5.7 Imagen generada · 10:45–11:20

**En pantalla:** diálogo “Añadir imagen”; “Generar con Playbook”; prompt; preview; “Guardar”
y Nota con Adjunto.

**Narración (literal):**

> Y la última superficie: imagen generada. Escribo un prompt descriptivo, pulso Generar con
> Playbook y espero el preview. El prompt lo escribo yo; la imagen no se guarda hasta que
> confirmo. Si el proveedor devuelve un formato inesperado, la app lo rechaza y me deja
> reintentar. Si me gusta, la guardo como Nota con Adjunto de imagen. El binario va a storage
> privado y la base guarda la ruta relativa. La Nota queda en la lista con su miniatura, como
> cualquier otra captura. Con esto has visto las superficies del MVP funcionando con IA real y
> datos creados en directo.

**Acciones:** `[ACCIÓN: “Añadir imagen” → “Generar con Playbook”]`, `[ACCIÓN: escribir el
prompt]`, `[ACCIÓN: generar]`, `[ACCIÓN: guardar]`. `[JUMP-CUT: de la espera al preview
real]`.

**Transición:** corte a resultados.

**Si falla:** B07.

---

### 4.6 Bloque 6 · Resultados e historia · 11:20–12:20

**Objetivo:** dar números reales y separar lo construido de lo que queda.

**En pantalla:** diapositiva con una línea temporal (2026-09-21, 2026-09-24, 2026-10-02,
2026-10-15, 2026-10-21) y la tarjeta “actualizar al grabar” con features, commits, specs y
validaciones. Fuente visible: [`PROGRESS.md`](../../PROGRESS.md),
[`feature_list.json`](../../feature_list.json) y `git log`.

**Narración (literal):**

> ¿Qué ha salido de todo esto? El snapshot que usaré al grabar es el que salga en ese
> momento de feature_list.json, PROGRESS.md y git log. A fecha de planificación,
> origin/develop tenía 52 features: 51 accepted y una blocked, la subida firmada a
> TestFlight, que depende de una cuenta de Apple Developer. Había 85 commits, 30
> validaciones y 51 specs, con sesiones registradas de la 001 a la 101. El primer commit es
> del 21 de septiembre; el harness, del 24; el bootstrap, del 2 de octubre; la revisión
> Android-first, del 15 de octubre; y el README, del 21. Esas cifras son dinámicas:
> actualízalas al grabar. El objetivo no es impresionar con cifras, sino que puedas
> repetirlas con las fuentes delante. Lo que funcionó: el gate fake, la validación
> independiente y el paralelismo controlado. Lo que no: iOS sigue congelado, no hay E2E
> persistente ni CI/CD versionado, y la IA real depende de red, cuota y key.

**Acciones:** `[ACCIÓN: resaltar la línea temporal y las tarjetas; actualizar las cifras]`.

**Transición:** fundido a lecciones.

**Si falla:** F6.

---

### 4.7 Bloque 7 · Lecciones y futuro · 12:20–13:30

**Objetivo:** extraer criterio y reconocer deuda sin prometer un roadmap.

**En pantalla:** matriz “para qué sirve cada herramienta” y una columna “deuda / siguiente
paso” con iOS, publicación, E2E, dark mode, miniaturas y enlaces. Enlace visible a
[riesgos](../risks-and-open-questions.md) y al [plan de ejecución](../mvp-execution-plan.md).

**Narración (literal):**

> La lección más útil es que cada herramienta tiene un trabajo distinto. Gemini me ayuda a
> pensar y redactar; la CLI ejecuta y verifica; el IDE valida y muestra el dispositivo;
> OpenCode organiza el trabajo agéntico con specs, skills y subagentes. Ninguna sustituye al
> gate ni a la validación. Si tuviera que resumirlo: la IA acelera la producción, pero la
> evidencia decide. También aprendí que el coste de la IA no se mide solo en euros: se mide
> en latencia, en reintentos y en la paciencia del usuario. Por eso el gate es fake y el
> smoke real es key-gated: no quiero que un test dependa de la red. En el debe quedan la
> paridad iOS, la subida a tiendas, un smoke E2E
> persistente, el dark mode, la caché de miniaturas y un ciclo de vida más fino para los
> enlaces. El siguiente paso no es añadir features a ciegas: es descongelar iOS con
> presupuesto, completar la publicación y medir latencia, coste y calidad de la IA real con
> un smoke key-gated.

**Acciones:** `[ACCIÓN: subrayar tres lecciones y tres límites]`.

**Transición:** corte al cierre.

**Si falla:** F7.

---

### 4.8 Bloque 8 · Conclusiones y cierre · 13:30–14:10

**Objetivo:** sintetizar y dejar una acción concreta.

**En pantalla:** [`icon.png`](../design/icon.png), “Playbook”, y una tarjeta con
`README.md`, `ARCHITECTURE.md`, `CI=true ./init.sh` y `PROGRESS.md`.

**Narración (literal):**

> Playbook es, en resumen, una app pequeña con una idea grande: convertir capturas dispersas
> en un GDD vivo, con IA que sugiere y un usuario que decide. Todo lo que has visto está en
> el repositorio: README, ARCHITECTURE, specs, validaciones y PROGRESS. El gate es
> reproducible y la demo usa IA real. Si algo de lo que has visto no te cuadra, el
> repositorio tiene la última palabra: cada claim de este guion apunta a una fuente. Si
> quieres comprobarlo, empieza por CI=true ./init.sh y sigue los enlaces de este guion.
> Gracias por verlo.

**Acciones:** `[ACCIÓN: plano estático; fundido a negro al terminar]`.

**Transición:** fin. Si hay margen, mantener la tarjeta 5 s y fundir a negro. No añadir
música ni créditos nuevos.

**Si falla:** F8.

---

## 5. Herramientas en pantalla

| Herramienta | Uso real en el vídeo | Qué se ve | Evidencia o confirmación |
|---|---|---|---|
| Chat | **Gemini**, apoyo puntual en ideas, redacción o revisión | Nombrado en la narración; sin pantalla salvo que el autor lo pida | Confirmación del autor (2026-10-22); uso declarado, no verificable en el repo |
| CLI | Terminal, Gradle, adb, git, gate y smoke | `CI=true ./init.sh` y comandos del plan, sin key | [`README.md` §5 y §9](../../README.md), [`init.sh`](../../init.sh), [plan de grabación](plan-grabacion-demo-mvp.md) |
| IDE | **Android Studio** para validación y demo con Device Mirroring | Ventana de dispositivo y ejecución | Confirmación del autor; [plan de grabación](plan-grabacion-demo-mvp.md) |
| ADE (OpenCode) | OpenCode Desktop: brief, subagentes, skills y worktrees; VS Code como editor/terminal | `.opencode/agents/`, `.opencode/skills/`, specs, validaciones y `PROGRESS.md` | [`.opencode/agents/`](../../.opencode/agents/), [`.opencode/skills/`](../../.opencode/skills/), [`AGENTS.md`](../../AGENTS.md) |
| Harness | `AGENTS.md`, `init.sh`, `PROGRESS.md`, `feature_list.json` | Gate Android-only con IA fake y estados `accepted` | [`AGENTS.md`](../../AGENTS.md), [`init.sh`](../../init.sh), [`feature_list.json`](../../feature_list.json) |
| Git | Ramas `feature/*`, worktrees, rebase y commits | `git log`, `PROGRESS.md` | [`PROGRESS.md`](../../PROGRESS.md), [plan de ejecución](../mvp-execution-plan.md) |

**Nota de honestidad:** Gemini, OpenCode Desktop, VS Code y Android Studio son herramientas
de trabajo confirmadas por el autor. El vídeo no afirma que una sola herramienta lo haya
hecho todo. La etiqueta “ADE” se explica como *Agentic Development Environment* aplicada a
OpenCode, sin forzar que toda la pantalla sea ADE.

---

## 6. Fallbacks por bloque

| Código | Superficie | Fallback permitido | Prohibido |
|---|---|---|---|
| F1 | Portada | Diapositiva estática con [`icon.png`](../design/icon.png) y narración | Inventar una portada nueva |
| F2 | Producto/tesis | Diapositiva estática + captura real de la lista | Maqueta que parezca app real |
| F3 | Arquitectura | Diapositiva + árbol de archivos del repo | Diagrama con módulos inexistentes |
| F4 | Harness | Diapositiva + salida real del gate o clip real de terminal | Mostrar un `BUILD SUCCESSFUL` no ejecutado |
| F5 | Demo | Clip real pregrabado de **esa** superficie (B01–B07); si no hay clip, se detiene y se reprograma | Fake, maqueta, resultado inventado, dictado o transcripción simulados |
| F6 | Resultados | Diapositiva con la fuente visible y la fecha; recalcular al grabar | Cifras congeladas o inventadas |
| F7 | Futuro | Diapositiva enlazada a riesgos y plan | Prometer un roadmap no documentado |
| F8 | Cierre | Tarjeta estática con enlaces | Créditos o música no confirmados |

**Regla de la demo:** si falla la red o el proveedor de IA, se detiene la grabación y se
reprograma. No se cambia a fake, no se simula una respuesta y no se rellena un texto a mano.
Si solo falla una superficie, se puede usar su clip real de respaldo, grabado en el ensayo,
siempre que el clip muestre la misma app y el mismo build.

---

## 7. Tabla de recortes

Base: 14:10 (850 s, cierre incluido). Objetivo mínimo: 10:00 (600 s). Se recorta en este
orden y se para al llegar a 10:00. Nunca se recortan la tesis de producto, el harness/SDD,
la demo real (incluida la imagen) ni la conclusión.

| Orden | Recorte | Quita | Total tras el recorte | Qué se conserva |
|---:|---|---:|---:|---|
| 1 | Detalle de embeddings y “Ver relacionadas” | 0:35 | 13:35 | El indexado se menciona; la superficie 5.6 se retira |
| 2 | Detalle de resultados e historia | 1:00 | 12:35 | Se deja una frase con la fuente y la fecha |
| 3 | Bloque de futuro | 1:10 | 11:25 | Se deja una frase de deuda y siguiente paso |
| 4 | Detalle de arquitectura | 0:20 | 11:05 | Módulos, esquema y fake/real en una frase |
| 5 | Esperas y transiciones del demo, sin retirar superficies | 0:30 | 10:35 | Onboarding, Brainstorming, Nota, GDD, Q&A e imagen |
| 6 | Detalle de producto | 0:20 | 10:15 | Tesis, superficies y patrón “sugiere/confirma” |
| 7 | Detalle de harness | 0:15 | 10:00 | Tres roles, estados y gate |

**Plan mínimo resultante (10:00):** apertura + cierre 1:00, producto 1:00, arquitectura 1:00,
harness 2:00 y demo 5:00. La demo mínima mantiene onboarding, Brainstorming, Nota, GDD, Q&A
e imagen; pierde el detalle de relacionadas y acorta esperas, pero no simula nada.

**Qué no se recorta nunca:** la tesis de producto, el harness/SDD, la demo real (incluida la
imagen generada) y la conclusión. Si aun así se pasa de 15:00, se divide la grabación o se
repite el bloque; no se acelera la narración ni se cortan palabras a mitad de frase.

---

## 8. Confirmaciones del autor

| Tema | Decisión cerrada | Estado |
|---|---|---|
| Audiencia | Evaluador técnico del curso | Confirmado (2026-10-22) |
| Idioma y voz | Español de España, primera persona, sin fundamentos | Confirmado |
| Duración | 13:30 de contenido; 14:10 con cierre; límite 15:00; mínimo 10:00 | Confirmado |
| Chat | Gemini, nombrado | Confirmado |
| Herramientas | OpenCode Desktop + VS Code sin etiqueta estricta; Android Studio para validación y demo | Confirmado |
| Dispositivo | Google Pixel 8 Pro con Android 17, vertical nativo | Confirmado |
| Captura | Ventana de dispositivo de Android Studio; fallback a grabadora del Pixel o `adb screenrecord` | Confirmado |
| Demo | IA real, datos en vivo, sin precargar Proyecto ni Notas | Confirmado |
| Superficies | Onboarding, Brainstorming, Nota, GDD, Q&A, relacionadas e imagen | Confirmado |
| Fuera de la demo | Dictado inline y transcripción de Adjuntos; no se simulan | Confirmado |
| Pre-flight | Smoke completo key-gated de seis modelos; si falla `transcribe`, pre-flight manual de tts/image/jev | Ajustado (2026-10-22) |
| Publicación | WeTransfer, sin música | Confirmado |
| Nombre del archivo | Propuesta: `playbook-demo-mvp-2026-10-22.mp4` | **A confirmar por el autor** |
| Resolución | Propuesta: 1920×1080, 30 fps, dispositivo vertical a la derecha | **A confirmar por el autor** |
| Gemini en pantalla | Solo se nombra; no se muestra salvo que el autor lo pida | **A confirmar por el autor** |

---

## 9. Mapa claim → fuente

Cada fila es una afirmación del guion y su fuente durable. Si una fuente no existe o cambia,
se corrige el guion; no se corrige la fuente para que encaje.

| Claim | Dónde se dice | Fuente |
|---|---|---|
| Playbook es Android-first, local-first y single-user | 4.2 | [`README.md` §2](../../README.md) |
| Convierte capturas en un GDD vivo con ficha + Notas por Track | 4.2, 4.5.4 | [`README.md` §2–3](../../README.md), [spec GDD híbrido](../specs/gdd-document-hybrid.md), [`PROGRESS.md`](../../PROGRESS.md) |
| Captura texto, voz e imagen; Track y Géneros | 4.2 | [`README.md` §3](../../README.md), [specs de captura](../specs/create-text-note.md), [géneros](../specs/genres-catalog-ui.md) |
| Proyecto singleton y Brainstorming de 9 pasos | 4.2, 4.5.1–4.5.2 | [`README.md` §3](../../README.md), [spec onboarding](../specs/first-run-project-onboarding.md), [spec wizard](../specs/brainstorming-wizard.md) |
| La IA sugiere clasificación/contenido/ideas y el usuario confirma | 4.2, 4.5.2–4.5.3 | [`README.md` §3](../../README.md), [spec clasificación](../specs/ai-suggest-classification.md), [spec contenido](../specs/note-content-suggestions.md) |
| Q&A con citas y “Guardar como Nota”; lectura en voz alta | 4.5.5 | [`README.md` §3](../../README.md), [spec RAG](../specs/rag-answer-citations.md), [spec guardar](../specs/save-answer-as-note.md), [spec tts](../specs/tts-read-aloud.md) |
| Relacionadas por similitud y estado de indexado visible | 4.5.6 | [`README.md` §3](../../README.md), [spec enlaces](../specs/note-links-ui.md), [spec estado](../specs/note-enrichment-status-ui.md) |
| Imagen generada con preview y Adjunto | 4.5.7 | [`README.md` §3](../../README.md), [spec imagen](../specs/image-generation-from-text.md), [validación](../validations/image-generation-from-text.md) |
| Dirección `iosApp → :composeApp → :core` | 4.3 | [`README.md` §6](../../README.md), [`ARCHITECTURE.md` §Modules](../../ARCHITECTURE.md) |
| SQLDelight 2.1.0, esquema v8, migraciones `1.sqm`–`7.sqm` | 4.3 | [`README.md` §6](../../README.md), [`ARCHITECTURE.md` §Persistence](../../ARCHITECTURE.md), [validación de persistencia](../validations/game-project-persistence.md) |
| Repositorios Nota/Adjunto/Embedding/Enlace/Proyecto; rutas relativas | 4.3 | [`README.md` §6](../../README.md), [`ARCHITECTURE.md` §Persistence](../../ARCHITECTURE.md) |
| Puertos `AiClient`, `TranscribeClient`, `TtsClient`, `ImageClient`, `JevClient` | 4.3 | [`README.md` §6](../../README.md), [`ARCHITECTURE.md` §AI](../../ARCHITECTURE.md), [spec puertos](../specs/ai-client-ports.md) |
| Fake determinista de 8 dimensiones; real OpenAI-compatible de 1536 | 4.3 | [`README.md` §6](../../README.md), [ADR 0004](../adr/0004-ai-real-integration.md), [spec fake/real](../specs/ai-fake-real-switch.md) |
| El gate y los tests automáticos del gate usan fake, sin red ni key; el smoke real es manual y key-gated | 4.3, 4.4 | [`README.md` §5 y §9](../../README.md), [`AGENTS.md`](../../AGENTS.md), [`init.sh`](../../init.sh), [`RealAiSmokeTest.kt`](../../composeApp/src/androidUnitTest/kotlin/com/playbook/app/ai/RealAiSmokeTest.kt) |
| GDD como proyección pura, sin tabla propia | 4.3, 4.5.4 | [`README.md` §6](../../README.md), [`ARCHITECTURE.md` §GDD](../../ARCHITECTURE.md) |
| Design system con Bricolage Grotesque, JetBrains Mono y cabecera | 4.3 | [`DESIGN.md`](../../DESIGN.md), [spec design system](../specs/design-system-brand-header.md), [validación](../validations/design-system-brand-header.md) |
| iOS congelado: compila, sin paridad ni gate | 4.3, 4.6, 4.7 | [ADR 0003](../adr/0003-ios-congelado-mvp.md), [`README.md` §9–10](../../README.md) |
| Harness: `AGENTS.md`, `init.sh`, `PROGRESS.md`, `feature_list.json`, specs y validaciones | 4.4 | [`AGENTS.md`](../../AGENTS.md), [`README.md` §7](../../README.md), [`PROGRESS.md`](../../PROGRESS.md) |
| Tres roles y ciclo `not_started → in_progress → passing → accepted` | 4.4 | [`AGENTS.md`](../../AGENTS.md), [`README.md` §7](../../README.md) |
| WIP=1 por sesión y hasta tres sesiones en paralelo con cola de merge | 4.4 | [`AGENTS.md`](../../AGENTS.md), [plan de ejecución](../mvp-execution-plan.md), [`README.md` §7](../../README.md) |
| La validación independiente encontró defectos reales | 4.4 | [`README.md` §8](../../README.md), [validaciones](../validations/) |
| Snapshot: 52 features (51 `accepted`, 1 `blocked`), 85 commits, 30 validaciones, 51 specs, sesiones 001–101 | 4.6 | `PROGRESS.md` (estado y Session 101), `feature_list.json`, `git rev-list --count HEAD`; **actualizar al grabar** |
| Fechas 2026-09-21, 2026-09-24, 2026-10-02, 2026-10-15 y 2026-10-21 | 4.6 | `git log`, [`PROGRESS.md`](../../PROGRESS.md); **actualizar al grabar** |
| `ios-release-pipeline-first-upload` está `blocked` y depende de una cuenta Apple | 4.6 | [`README.md` §4 y §10](../../README.md), [runbook iOS](../release/ios-testflight.md), [`PROGRESS.md`](../../PROGRESS.md) |
| No hay E2E persistente, CI/CD versionado ni subida a tiendas registrada | 4.6, 4.7 | [`README.md` §9](../../README.md) |
| La IA real depende de red, cuota y key | 4.6, 4.7 | [ADR 0004](../adr/0004-ai-real-integration.md), [`README.md` §10](../../README.md), [riesgos](../risks-and-open-questions.md) |
| Gemini, OpenCode Desktop, VS Code y Android Studio son las herramientas usadas | 4.4, 5 | Confirmación del autor (2026-10-22); uso declarado, no verificable en el repo |
| El smoke completo cubre chat, embedding, jev, tts, image y transcribe; si falla transcribe, tts/image/jev se validan con pre-flight manual | 4.5, plan | [`RealAiSmokeTest.kt`](../../composeApp/src/androidUnitTest/kotlin/com/playbook/app/ai/RealAiSmokeTest.kt), [plan de grabación §5](plan-grabacion-demo-mvp.md#5-smoke-real-de-los-modelos) |
| El build de demo usa `-Pplaybook.ai.mode=real` y no `init.sh` para instalar | 4.5, plan | [`README.md` §5](../../README.md), [spec fake/real](../specs/ai-fake-real-switch.md), [plan de grabación](plan-grabacion-demo-mvp.md) |
| Dictado inline y transcripción de Adjuntos quedan fuera y no se simulan | 4.5 | [Spec de esta feature](../specs/video-script-mvp-ai.md); decisión del autor (2026-10-22) |
| Publicación en WeTransfer, sin música | 8, plan | Confirmación del autor (2026-10-22), [plan de grabación](plan-grabacion-demo-mvp.md) |

---

## 10. Claims que no se hacen

- No se afirma publicación en Google Play ni en TestFlight.
- No se afirman usuarios reales, impacto de negocio ni métricas de producto.
- No se afirma cobertura E2E persistente, CI/CD versionado ni calidad medida de la IA.
- No se afirma paridad iOS: solo que el proyecto compila y queda congelado.
- No se afirma que el dictado o la transcripción de Adjuntos estén operativos en la demo.
- No se afirma que el modo fake sea la demo: el fake se explica como estrategia de gate.
- No se inventan anécdotas, fechas, commits, herramientas ni resultados de la IA.

---

## 11. Checklist de grabación (resumen)

La lista completa está en el [plan de grabación](plan-grabacion-demo-mvp.md).

- [ ] Instalación limpia, sin Proyecto ni Notas.
- [ ] Build real con `-Pplaybook.ai.mode=real` y arranque correcto.
- [ ] Smoke completo de los seis modelos en verde; si falla solo `transcribe`, pre-flight
      manual de `tts`, `image` y `jev` registrado.
- [ ] En Brainstorming, “Guardar” persiste Título y Géneros y vuelve a la lista.
- [ ] Par de “Ver relacionadas” comprobado en el ensayo (≥ 0.8) o B06 real preparado.
- [ ] Panel “Dictado” fuera de cuadro y sin interactuar.
- [ ] Key fuera de cámara, terminal, logs y guion.
- [ ] Pixel 8 Pro en vertical, locale `es-ES`, font scale 1.0, notificaciones silenciadas.
- [ ] Device Mirroring estable; fallback de captura probado.
- [ ] Clips de respaldo reales B01–B07 grabados en el ensayo.
- [ ] Cronómetro: 13:30 de contenido y 14:10 con cierre; 15:00 como límite.
- [ ] Sin dictado, sin transcripción y sin fake en la demo.

---

## 12. Vigencia

Documento vigente a **2026-10-22**. Si cambia el producto, la arquitectura, el harness o las
herramientas, se actualizan este guion, el [storyboard](storyboard-demo-mvp.md) y el
[plan de grabación](plan-grabacion-demo-mvp.md) antes de grabar. Las fuentes operativas
siguen siendo [`PROGRESS.md`](../../PROGRESS.md) y [`feature_list.json`](../../feature_list.json).
