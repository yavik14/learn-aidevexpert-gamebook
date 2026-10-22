# Feature Implementation Spec: Guion y storyboard del vídeo demo del MVP con IA

## Source Feature

- `id`: `video-script-mvp-ai`; `area`: `docs`; `status`: `not_started` al planificar.
- `depends_on`: `["readme-project-documentation"]` (`accepted`); `source`: `feature_list.json`. Rama `feature/video-script-mvp-ai` desde `origin/develop` (`4342edb`). Tarea de **documentación**: no implementa producto ni graba el vídeo.

> **Alcance.** Producir tres documentos en `docs/video/`: guion minuto a minuto,
> storyboard/diapositivas y plan de grabación. El vídeo explica Playbook, su
> arquitectura, el harness/SDD, las herramientas de IA, la historia real del
> desarrollo, una demo Android con **IA real**, resultados, riesgos, mejoras y
> conclusiones. No se toca código, esquema, IA, `init.sh`, build files ni `iosApp/`.

## Goal y audiencia

El evaluador técnico del curso debe entender en **10–15 minutos** qué se construyó,
cómo se construyó y qué se puede demostrar sin confiar en afirmaciones no
verificadas. El vídeo debe sentirse como un recorrido de ingeniería, no como un
marketing pitch: producto, decisiones, harness, herramientas, demo real y límites
honestos.

- **Audiencia:** evaluador técnico del curso. **Tono:** claro y directo; no se explican fundamentos básicos de Android, Kotlin, Compose, Git o IA. Los términos técnicos se usan en inglés cuando aportan precisión (`worktree`, `build flag`, `gate`, `prompt`, `fake/real`).
- **Idioma:** español de España (tuteo, sin voseo ni regionalismos).
- **Narración:** primera persona (“construí”, “probé”), coherente con la autoría real.
- **Criterio de éxito:** el evaluador puede seguir el hilo, reproducir el gate, distinguir lo verificado de lo declarado y ubicar la fuente de cada claim.

## Success Criteria

1. Existen `docs/video/guion-demo-mvp.md`, `docs/video/storyboard-demo-mvp.md` y `docs/video/plan-grabacion-demo-mvp.md`, enlazados entre sí y con las fuentes.
2. El contenido narrado suma **13:30**, con cierre y margen hasta **14:10** y **15:00** como límite duro; hay recorte planificado hasta **10:00**.
3. La demo usa **solo IA real** y datos generados **en vivo**; el fake se explica, pero no se graba como real. No se muestran dictado inline ni transcripción de Adjuntos, y no se simulan. Cualquier fallback es un clip de respaldo real.
4. Los claims de producto, arquitectura, historia y métricas son trazables a `PROGRESS.md`, `feature_list.json`, specs, validaciones, ADRs o `git log`.
5. La matriz Chat/CLI/IDE/ADE distingue uso, evidencia y confirmaciones pendientes; no inventa asistentes, herramientas ni fechas.
6. No hay secrets, API keys, keystores, rutas personales ni contenido de `local.properties`. `CI=true ./init.sh` sale 0 y `git diff --check` está limpio.

## Non-Goals

- Grabar, editar, publicar o subir el vídeo; crear clips, capturas, audio o diapositivas binarias (`.pptx`, `.key`, PDFs) o assets nuevos.
- Cambiar producto, código, tests, esquema, migraciones, IA, puertos, build files, `gradle.properties`, `init.sh`, `iosApp/`, CI o release.
- Inventar herramientas, asistentes de chat, fechas, commits, métricas, features, validaciones, usuarios, impacto ni resultados de negocio.
- Afirmar publicación en Play/TestFlight, paridad iOS, E2E persistente, CI/CD versionado o smoke E2E de cierre.
- Duplicar la documentación: el guion enlaza fuentes, no las reescribe.

## Job Story y usuarios

When preparo la defensa del MVP ante un evaluador técnico, I want un guion y un
plan de grabación que muestren el producto y el proceso con evidencia real, so I
can grabar una pieza creíble sin improvisar ni exagerar.

- **Autor:** ensaya, graba y decide las confirmaciones pendientes.
- **Evaluador:** sigue la narrativa y comprueba claims en las fuentes.
- **Futuro colaborador:** reutiliza el guion para entender producto y metodología.

## Scope Decisions (vinculantes)

1. **Duración:** contenido 13:30; cierre 13:30–14:10; margen hasta 15:00; mínimo aceptable 10:00.
2. **Demo:** Android con IA real únicamente y datos generados **en vivo** por Playbook (sin precargar Proyecto ni Notas). El fake se menciona como estrategia de gate/test; nunca se usa para grabar. **No se muestran** dictado inline ni transcripción de Adjuntos porque el dictado no está operativo en el dispositivo de grabación; no se simulan.
3. **Herramientas confirmadas:** Chat = **Gemini**; OpenCode Desktop (brief, subagentes y skills) y VS Code (editor/terminal) se muestran **sin etiqueta estricta de ADE**; Android Studio se usa para validación y para la demo mediante su ventana de dispositivo; CLI = terminal + Gradle + adb + git. El concepto ADE se explica como Agentic Development Environment (OpenCode), sin forzar una única herramienta en pantalla.
4. **Historia:** fechas, commits, sesiones, features `accepted` y validaciones salen de `PROGRESS.md`, `feature_list.json` y `git log` al grabar. Las métricas dinámicas se marcan como “actualizar al grabar”.
5. **Claims:** cada afirmación relevante se apoya en una fuente enlazada. Sin evidencia se usa lenguaje genérico o “a confirmar por el autor”.
6. **Seguridad:** la API key vive en `local.properties` o en el entorno, fuera del repo; no aparece en pantalla, terminal, guion, storyboard ni plan. El build de demo usa `-Pplaybook.ai.mode=real`; `init.sh` no se usa para instalar porque fuerza fake.
7. **Entregables:** Markdown en español de España, enlaces relativos y sin dependencias de imágenes no versionadas. Assets permitidos: `docs/design/icon.png`, `docs/design/paleta.png` y capturas de la grabación.
8. **Alcance:** solo documentación. `feature_list.json`, `PROGRESS.md` y `AGENTS.md` se actualizan como artefactos durables; el código queda intacto.

## Timebox Contract

| # | Desde | Hasta | Dur. | Bloque | Contenido mínimo |
|---|---|---|---|---|---|
| 1 | 0:00 | 0:40 | 0:40 | Apertura | Qué es Playbook y qué verá el evaluador |
| 2 | 0:40 | 2:00 | 1:20 | Producto y tesis | Notas → GDD vivo; captura, Proyecto, Géneros, Q&A, IA |
| 3 | 2:00 | 3:20 | 1:20 | Arquitectura | `:core` KMP + `:composeApp`, SQLDelight v8, repos, IA fake/real, design system, iOS congelado |
| 4 | 3:20 | 5:50 | 2:30 | Harness/SDD/herramientas | `AGENTS.md`, `init.sh`, `PROGRESS.md`, `feature_list.json`, specs, validaciones, tres roles, worktrees, gate, Chat/CLI/IDE/ADE |
| 5 | 5:50 | 11:20 | 5:30 | Demo Android real | Onboarding/Proyecto, Brainstorming, Nueva Nota, GDD, Q&A e imagen generada |
| 6 | 11:20 | 12:20 | 1:00 | Resultados e historia | Hitos reales, features accepted, qué funcionó y qué no |
| 7 | 12:20 | 13:30 | 1:10 | Lecciones y futuro | Cuándo usar cada herramienta; límites, deuda y próximos pasos |
| 8 | 13:30 | 14:10 | 0:40 | Conclusiones y cierre | Síntesis y llamada a explorar el repo |

**Recortes ordenados si se pasa:** (1) detalle de embeddings/relacionadas; (2)
detalle de resultados/historia; (3) detalle de arquitectura; (4) futuro. **Nunca
se recorta:** tesis de producto, harness/SDD, demo real (incluida la imagen) y
conclusión. **Plan mínimo 10:00:** demo 5:00, harness 2:00, producto 1:00,
arquitectura 1:00, cierre 1:00.

## Deliverable Contracts

### 1. `docs/video/guion-demo-mvp.md`

- Cabecera con versión, fecha, objetivo 13:30, límite 15:00, audiencia y fuentes.
- Tabla de tiempos: `#`, `in`, `out`, `dur`, `acumulado`, `bloque`, `objetivo`, `qué se ve`, `qué se dice`, `transición`, `si falla`.
- Narración **palabra por palabra** por bloque, en español de España. A 140–160 palabras/minuto, 13:30 ronda 1.900–2.150 palabras; hay que comprobarlo leyendo en voz alta, no asumirlo.
- Cada bloque incluye objetivo, acciones en pantalla, texto narrado, transición y fallback. Los cortes de loading se explicitan como jump-cut honesto al resultado, nunca como resultado fabricado.
- Al final: tabla de recortes, confirmaciones del autor y mapa claim → fuente.

### 2. `docs/video/storyboard-demo-mvp.md`

- Secuencia de planos/diapositivas con título, duración, composición, texto en pantalla, notas visuales y acción esperada.
- Mínimo: portada, tesis/producto, arquitectura, harness/SDD, matriz de herramientas, demo (onboarding, Brainstorming, Nota, GDD, Q&A), resultados, lecciones/futuro y cierre.
- Reutiliza `docs/design/icon.png` y `docs/design/paleta.png`; no crea assets binarios. Las capturas se describen como “a capturar en la grabación”.
- Indica qué planos son de pantalla completa, cuáles llevan lower-third y dónde entran los clips de respaldo.

### 3. `docs/video/plan-grabacion-demo-mvp.md`

- **Pre-flight:** JDK 21, Android SDK, `adb`, **Google Pixel 8 Pro con Android 17** por USB, Android Studio con Device Mirroring, formato vertical nativo, locale `es-ES`, font scale 1.0, notificaciones silenciadas, red estable, espacio libre y batería.
- **Seguridad de la key:** key en `local.properties`/entorno, fuera de cámara; no abrir `local.properties`, terminal con variables ni paneles de logs que la expongan; rotarla si se filtra.
- **Build real:** `./gradlew :composeApp:installDebug -Pplaybook.ai.mode=real` con key disponible; arranque con `adb shell am start -n com.playbook.app/.MainActivity`. No usar `init.sh` para instalar.
- **Pre-flight de IA real:** ejecutar `RealAiSmokeTest` key-gated. La demo usa chat, embedding, jev, tts e image; `transcribe` no se muestra. Si el smoke completo falla solo en `transcribe`, se documenta y no bloquea la demo.
- **Datos demo:** se generan **en vivo** con las sugerencias reales de Playbook; no se precargan Proyecto ni Notas y se parte de instalación limpia. El guion usa placeholders para lo que produce la IA y no inventa resultados.
- **Imagen:** se genera en vivo con “Generar con Playbook” y se guarda como Nota con Adjunto; si falla, se usa un clip real.
- **Comandos:** build, instalación, arranque, `adb shell pm clear com.playbook.app` solo para onboarding limpio (destructivo), y captura desde la ventana de dispositivo de Android Studio.
- **Captura:** grabar la ventana de dispositivo de Android Studio. Si Device Mirroring no arranca o pierde frames, fallback a la grabadora del Pixel o `adb screenrecord` por tramos; el guion no cambia.
- **Checklist:** orden de clips, tomas de respaldo, micrófono, monitor, duración, silencios, notificaciones y verificación de que no aparece la key.
- **Fallback:** clip real por superficie; si falla la red o el proveedor, se detiene la grabación y se reprograma. Nunca fake ni resultados simulados.
- **Publicación:** exportar para **WeTransfer**, sin música; fijar nombre de archivo, resolución y duración. El vídeo no se sube desde el repo.
- **Limpieza:** borrar datos demo, clips y archivos de trabajo; revisar `git status`; no commitear capturas pesadas ni secretos.

## Demo Real Contract

| Superficie | Acción en pantalla | Evidencia esperada | Fallback |
|---|---|---|---|
| Onboarding/Proyecto | Primer arranque, crear/saltar y abrir Brainstorming | Onboarding y ficha | Clip real previo |
| Brainstorming | 1–2 pasos con “Sugerir” y confirmar | Ideas reales y estado guardado | Clip real previo |
| Nueva Nota | “Sugerir ideas”, “Sugerir clasificación”, guardar | Ideas, clasificación y Nota persistida | Clip real previo |
| GDD híbrido | Abrir GDD con ficha + Notas por Track | Documento completo | Clip real previo |
| Q&A | Preguntar, ver citas, “Guardar como Nota”, leer en voz alta | Respuesta con fuentes y Nota creada | Clip real previo |
| Embeddings/relacionadas | Indexado y “Ver relacionadas” | Estado `INDEXED` y enlaces | Clip real previo |
| Imagen | “Generar con Playbook” y guardar | Preview y Adjunto | Clip real previo |

Reglas: una toma continua cuando sea posible; si una llamada tarda, jump-cut al
resultado; mostrar UI real, no maquetas. La key no se muestra. El modo real se
verifica antes de grabar y se declara en el guion. **No se muestran** dictado
inline ni transcripción de Adjuntos; no se simulan resultados.

## Herramientas: matriz obligatoria

| Herramienta | Uso en el vídeo | Evidencia / confirmación |
|---|---|---|
| Chat | **Gemini**, apoyo puntual en ideas, redacción o revisión | Nombrado en la narración; sin pantalla salvo que el autor lo pida |
| CLI | Terminal, Gradle, adb, git, gate y smoke | Comandos reproducibles del README/plan; sin key en pantalla |
| IDE | **Android Studio** para validación y demo con Device Mirroring | Ventana de dispositivo en pantalla; VS Code se muestra aparte |
| ADE (OpenCode) | OpenCode Desktop (brief, subagentes, skills y worktrees) + VS Code como editor/terminal | `.opencode/agents/`, `.opencode/skills/`, specs/validaciones y `PROGRESS.md`; sin etiqueta estricta |
| Harness | `AGENTS.md`, `init.sh`, `PROGRESS.md`, `feature_list.json` | Gate Android-only con IA fake; estado `accepted` |
| Git | Ramas `feature/*`, worktrees, rebase y commits | `git log`, `PROGRESS.md` |

## Historia, resultados y claims

- **Fuentes primarias:** `PROGRESS.md` (sesiones 001–101), `feature_list.json`, `README.md`, `ARCHITECTURE.md`, ADRs, specs y validaciones.
- **Snapshot verificado al planificar (2026-10-21, antes de esta tarea):** 52 features en `origin/develop` (51 `accepted`, 1 `blocked`), 85 commits, 30 validaciones, 51 specs; primer commit 2026-09-21; harness 2026-09-24; bootstrap 2026-10-02; revisión Android-first 2026-10-15; README aceptado 2026-10-21.
- **Confirmaciones del autor (2026-10-22):** chat con **Gemini**; OpenCode Desktop + VS Code sin etiqueta estricta; **Android Studio** para validación y demo vía ventana de dispositivo; **Google Pixel 8 Pro (Android 17)**; datos generados en vivo; **imagen sí**, dictado y transcripción no; **WeTransfer** y sin música.
- **Métricas dinámicas:** features, commits, specs, validaciones y sesiones se recalculan al grabar. El guion indica la fuente y evita cifras congeladas.
- **Historia honesta:** hitos, rebases, conflictos en hotspots de UI, validadores que detectaron overclaims y límites (iOS congelado, upload bloqueado, sin E2E, IA key-gated). No se inventan anécdotas.
- **Claims permitidos:** “el gate es Android-only y usa IA fake”; “la validación es independiente”; “iOS compila pero no tiene paridad”; “la IA real depende de red, cuota y key”. **Prohibidos:** publicación en tiendas, usuarios reales, impacto, cobertura E2E o calidad real medida sin evidencia.

## Expected File Changes

- `docs/video/guion-demo-mvp.md`, `docs/video/storyboard-demo-mvp.md` y `docs/video/plan-grabacion-demo-mvp.md` — nuevos.
- `docs/specs/video-script-mvp-ai.md` — este spec.
- `feature_list.json`, `PROGRESS.md` y `AGENTS.md` — estado, evidencia y cola.
- `docs/validations/video-script-mvp-ai.md` — lo crea el validador si acepta.

Sin cambios en `composeApp/`, `core/`, `iosApp/`, `init.sh`, build files, IA ni
esquema.

## Implementation Plan

1. Leer las fuentes y verificar estado real (features, commits, gate, rutas).
2. Escribir el guion con tabla de tiempos, narración y fallbacks.
3. Escribir el storyboard alineado con los bloques del guion.
4. Escribir el plan de grabación con pre-flight, datos, comandos y limpieza.
5. Enlazar los tres documentos entre sí y con README, arquitectura, plan, ADRs, specs y validaciones.
6. Actualizar `feature_list.json` a `passing` con evidencia y `PROGRESS.md`/`AGENTS.md`; no tocar código.
7. Ejecutar la verificación y registrar resultados reales.

## Verification Plan

- **Enlaces:** comprobar los tres archivos y resolver enlaces relativos con un script one-off.
- **Duración:** sumar la columna `dur`; 13:30 de contenido, 14:10 planificado, 15:00 de límite y recorte mínimo 10:00.
- **Claims:** revisar el mapa claim → fuente y que las cifras dinámicas digan “actualizar al grabar”.
- **Seguridad:** greps de `sk-`, `PLAYBOOK_AI_API_KEY=`, `apiKey=`, `BEGIN ... PRIVATE KEY`, `password=` y `local.properties`; sin coincidencias.
- **Estilo:** español de España, sin voseo; audiencia técnica; sin fundamentos.
- **Repo:** `python3 -m json.tool feature_list.json`, `git diff --check` y `CI=true ./init.sh` → exit 0.

## Acceptance Scenarios

1. **Tres entregables:** existen los tres documentos, comparten cabecera y se enlazan entre sí.
2. **Duración:** declaran 13:30 de contenido, 14:10 planificado y 15:00 de límite; la suma es coherente y hay recortes hasta 10:00.
3. **Producto y arquitectura fieles:** tesis, superficies, módulos, esquema v8, repositorios, IA fake/real, design system e iOS congelado coinciden con `README.md`/`ARCHITECTURE.md`; no se atribuyen features inexistentes.
4. **Harness/SDD fiel:** roles, estados, gate Android-only, worktrees y validación independiente se explican sin exagerar.
5. **Herramientas:** la matriz cubre Chat/CLI/IDE/ADE y marca como “a confirmar” lo que depende del autor.
6. **Demo real:** exige IA real, datos generados en vivo, pre-flight de los modelos usados (chat, embedding, jev, tts, image), seguridad de la key y fallback con clips reales; no hay fake, dictado ni transcripción simulados.
7. **Historia/claims:** hitos y cifras trazables o dinámicos; sin fechas, métricas ni anécdotas inventadas.
8. **Alcance:** solo docs; `CI=true ./init.sh` sale 0, JSON válido, sin secrets y `git diff --check` limpio.

## Evidence To Record

- Rutas, número de líneas y enlaces de los tres documentos.
- Salida de la comprobación de duración y de enlaces.
- Greps de seguridad y `git diff --check`.
- Resultado de `CI=true ./init.sh`.
- Confirmaciones del autor registradas en la sección de historia.

## Risks And Open Questions

- **IA real:** latencia, cuota y disponibilidad; si falla, se reprograma o se usa un clip real previo. No se cambia a fake.
- **Datos demo en vivo:** el onboarding solo aparece sin Proyecto; limpiar datos es destructivo y debe hacerse antes de grabar, no durante. Al no precargar Notas, la toma depende de la IA real y puede requerir repeticiones.
- **Dictado/transcripción:** el dictado no está operativo en el dispositivo de grabación; ambos quedan fuera de la demo y no se simulan. La transcripción de Adjuntos no se usa.
- **Métricas:** pueden cambiar entre guion y grabación; indicar fuente y fecha.
- **Publicación:** WeTransfer y sin música; solo queda confirmar el nombre de archivo final.

## Validator Checklist

- [ ] Los tres documentos existen, se enlazan y usan español de España.
- [ ] Duración y recortes cumplen 13:30 / 14:10 / 15:00 / 10:00.
- [ ] Producto, arquitectura, SDD, historia y métricas son trazables; sin overclaims.
- [ ] La matriz Chat/CLI/IDE/ADE separa evidencia de confirmaciones.
- [ ] La demo es solo real, con datos en vivo, pre-flight de modelos usados, comandos, fallback real y key segura; sin dictado ni transcripción simulados.
- [ ] No hay secrets, enlaces rotos ni cambios de código.
- [ ] `CI=true ./init.sh` sale 0 y `git diff --check` está limpio.
