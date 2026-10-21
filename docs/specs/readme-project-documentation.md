# Spec: README-bitácora del proyecto
## Source Feature
- `id`: `readme-project-documentation` · `area`: `docs` · `status`: `in_progress`.
- `depends_on`: las 21 features del MVP restante, todas `accepted` (persistencia,
  Géneros, Proyecto, Brainstorming, onboarding, shell, miniaturas, dictado, GDD,
  guardar respuesta, IA fake/real, chat, embeddings, transcripción, TTS, imagen,
  clasificación, design system, copy es-ES y sugerencias).
- `verification`: README en raíz, en español, enlaces relativos resueltos,
  comandos coherentes con `init.sh`/`docs/release/android.md`, sin secrets y
  `CI=true ./init.sh` exit 0.
> **Alcance.** Documentación de cierre, no feature de producto. Entregable:
> `README.md` en la raíz. No se toca código, esquema, IA, `init.sh`, `iosApp/`,
> build files, CI ni release. `PROGRESS.md` y `feature_list.json` siguen siendo
> la fuente de verdad operativa.
## Goal y audiencia
README que permita entender Playbook sin leer todo el historial: qué es, cómo
ejecutarlo, qué está verificado y por dónde continuar. Audiencias: **autor**
(recorrido y pendientes), **evaluador** (reproducir el gate y distinguir lo
verificado de lo declarado) y **futuro colaborador** (arquitectura, metodología y
próximos pasos). Español de España, honesto, enlazando fuentes durables en lugar
de duplicarlas.
## Non-Goals
- Cambiar código, tests, esquema, migraciones, IA, puertos, build files,
  `gradle.properties`, `init.sh`, `iosApp/`, CI o release.
- Crear capturas, GIFs, badges, logo, `LICENSE` ni assets.
- Duplicar specs, validaciones, ADRs o el `PROGRESS.md` completo.
- Inventar roadmap, métricas, conteos, fechas ni afirmar publicación en
  Play/TestFlight.
- Reescribir documentación histórica aunque conserve referencias obsoletas.
## Acceptance Scenarios
1. **README en raíz:** existe `README.md`, en es-ES (tuteo, sin voseo), con el
   icono `docs/design/icon.png` enlazado y las 13 secciones obligatorias.
2. **Quick start:** con JDK 21 y Android SDK, `./init.sh` y `CI=true ./init.sh`
   salen 0; la instalación incluye `-Pplaybook.ai.mode=fake`; el arranque usa
   `com.playbook.app/.MainActivity`.
3. **IA sin secrets:** se explica precedencia `-P` > env > `local.properties`,
   que `gradle.properties` trae `playbook.ai.mode = real` por la excepción
   aceptada de `spain-spanish-copy`, que `init.sh` fuerza `fake` y que la key vive
   fuera del repo.
4. **Arquitectura/metodología fieles:** módulos, esquema v8, repositorios,
   puertos/runtime de IA, design system, estado sin DI/ViewModel, SDD, worktrees,
   gate Android-only y WIP coinciden con las fuentes; sin capas inventadas.
5. **Límites honestos:** se declara que no hay E2E persistente, CI/CD versionado,
   smoke E2E de cierre ni subida a Play/TestFlight registrada; iOS sólo compila.
6. **Alcance intacto:** sólo cambian `README.md`, `feature_list.json` y
   `PROGRESS.md`; `CI=true ./init.sh` sale 0 y el JSON sigue válido.
## Repository Research
### Inspected
`AGENTS.md`, `PROGRESS.md` (estado y sesiones 094–099), `feature_list.json`,
`ARCHITECTURE.md`, `CONSTRAINTS.md`, `CONTEXT.md`, `DESIGN.md`,
`docs/mvp-execution-plan.md`, `docs/technical-discovery.md`,
`docs/risks-and-open-questions.md`, `docs/adr/0001..0004`,
`docs/release/android.md`, `docs/release/ios-testflight.md`, los specs de
`spain-spanish-copy`/`app-icon-configuration`, `init.sh`, `gradle.properties`,
`composeApp/build.gradle.kts`, `AndroidManifest.xml`, `App.kt`, `.gitignore`,
`docs/design/`, `docs/specs/`, `docs/validations/` y `scripts/ios-release/`.
No existe `README.md`, `.github/` ni CI versionado.
### Verified findings
- `:core` es KMP sin UI; `:composeApp` es UI Compose Multiplatform + app Android +
  framework iOS; `iosApp/` es el host Xcode congelado.
- SQLDelight 2.1.0, esquema **v8**, `.sqm` 1..7, repositorios de Nota, Adjunto,
  Embedding, Enlace y Proyecto; `minSdk 24`, `compileSdk/targetSdk 36`.
- IA tras `AiClient` + puertos por modelo; `AiRuntime`, `fakeAiRuntime()`,
  `openAiAiRuntime(...)`, `UrlConnectionHttpTransport` Android; iOS sigue fake.
- `App` hoistea estado con `NotesDestination`, `refreshKey`, `LaunchedEffect` y
  `rememberCoroutineScope`; **sin DI, ViewModel, Flow ni navegación**.
- `PlaybookTheme` claro, `PlaybookBrandHeader`, edge-to-edge, Bricolage Grotesque +
  JetBrains Mono, AA medido con `PlaybookContrastTest`; sin dark mode.
- Gate: `./init.sh` corre `assembleDebug` + `:core:testDebugUnitTest` con
  `-Pplaybook.ai.mode=fake`; no levanta procesos. No hay CI/CD: `CI=true` es
  convención del plan e `init.sh` no la lee.
- `gradle.properties` contiene `playbook.ai.mode = real`; `installDebug` sin flag
  puede fallar sin key. El README usa `-Pplaybook.ai.mode=fake`.
### Gaps
- No existe `README.md` ni índice curado de docs.
- `PROGRESS.md` aún dice `not_started` y 50/0/1/1; el implementer lo actualiza a
  `in_progress`/`passing` y, al cierre, a 51 accepted / 0 passing / 0 not_started
  / 1 blocked.
- `ARCHITECTURE.md` conserva frases históricas (v7; iOS en el gate). No copiarlas:
  la fuente operativa es `init.sh`/`AGENTS.md`/`PROGRESS.md`/`feature_list.json`.
- No hay smoke E2E de cierre, upload a Play/TestFlight ni harness UI/E2E
  persistente; la evidencia vive en `composeApp/build/smoke-evidence/` (ignorada
  por git). Los docs mezclan voseo: el README usa es-ES.
## README Contract
### 1. Voz
Español de España con tuteo; términos técnicos en inglés (`build`, `runtime`,
`smoke`, `worktree`). Sin emojis; única imagen, el icono existente.
### 2. Estructura obligatoria (13 secciones)
1. **Título + icono:** `# Playbook` +
   `![Icono de Playbook](docs/design/icon.png)` enlazado al mismo path.
2. **Qué es Playbook:** Android-first, local-first, single-user; notas de diseño
   que construyen un GDD vivo; iOS congelado.
3. **Funcionalidades:** texto, dictado de 30 s, bocetos e imagen generada; Track +
   Géneros; Proyecto singleton + onboarding + Brainstorming de 9 pasos; GDD
   híbrido + compartir + narración; Q&A RAG con citas y “Guardar como Nota”;
   notas relacionadas; lectura en voz alta; sugerencias de clasificación,
   contenido y Brainstorming; design system Playbook.
4. **Estado del MVP:** validado feature a feature; única excepción
   `ios-release-pipeline-first-upload` (`blocked`, account-gated). Sin conteos
   volátiles; enlaza `feature_list.json`.
5. **Quick start** (§3). 6. **Arquitectura** (§4). 7. **Metodología/harness**
   (§5). 8. **Bitácora** (§6). 9. **Verificación** (§7). 10. **Limitaciones y
   riesgos** (§8). 11. **Próximos pasos** (§9). 12. **Índice de docs** (§10). 13.
   **Vigencia:** fecha de cierre y “la fuente operativa es
   `PROGRESS.md`/`feature_list.json`”.
Extensión objetivo: 140–240 líneas, sin duplicar el log.
### 3. Quick start (contrato exacto)
- **Requisitos:** JDK 21 (el runbook de release lo exige; `init.sh` acepta 17+ y
  recomienda 21), Android SDK (`ANDROID_HOME` o `local.properties` con
  `sdk.dir=...`, nunca versionado), dispositivo/emulador API 24+.
- **Gate:** `./init.sh` y `CI=true ./init.sh`. Ambos corren el mismo gate
  Android-only con IA fake; `CI=true` no activa un pipeline.
- **Instalar y abrir:** `./gradlew :composeApp:installDebug
  -Pplaybook.ai.mode=fake` y `adb shell am start -n
  com.playbook.app/.MainActivity`. Advertir que `init.sh` imprime `installDebug`
  sin flag, pero el README añade `fake` porque `gradle.properties` está en `real`;
  sin flag ni key el build falla a propósito. Con varios dispositivos, usar
  `ANDROID_SERIAL`.
- **IA fake/real:** `playbook.ai.mode` con precedencia `-P` > `PLAYBOOK_AI_MODE` >
  `local.properties`; `init.sh` fuerza fake; key
  (`playbook.ai.apiKey`/`PLAYBOOK_AI_API_KEY`/`local.properties`) fuera del repo
  y nunca versionada; en fake `BuildConfig.AI_API_KEY` queda vacío; real
  incompleto falla el build; release real exige
  `-Pplaybook.ai.allowReleaseKey=true`; iOS usa fake.
- **Seguridad:** no mostrar keys; no copiar `local.properties` entre worktrees o
  equipos. Enlazar `docs/release/android.md` para firma y subida manual a Play.
### 4. Arquitectura
- Módulos y dirección (`iosApp → :composeApp → :core`; `:core` sin UI).
- Persistencia SQLDelight 2.1.0, v8, migraciones 1..7, `createDatabase`, drivers
  Android/iOS, `PRAGMA foreign_keys`; adjuntos en storage privado con rutas
  relativas.
- IA: puerto `AiClient` + puertos por modelo, `AiRuntime`, adaptadores
  OpenAI-compatible, `HttpTransport`, fakes deterministas, dimensión 1536 real/8
  fake, reconciliación y estados `CAPTURED`/`PENDING`/`INDEXED`/`FAILED`; sin red
  en gate/tests.
- UI/estado: `App`, `NotesDestination`, `refreshKey`, `LaunchedEffect`,
  `rememberCoroutineScope`; sin DI/ViewModel/Flow/navegación.
- Diseño: `PlaybookTheme` claro, `PlaybookBrandHeader`, tipografías OFL, shapes
  8/16/24, edge-to-edge, AA; sin dark mode.
- GDD híbrido: ficha del Proyecto + Notas por Track; `buildGddDocument`,
  `buildGddMarkdown`, `GddSharer`.
- Observabilidad: `Diagnostics` local best-effort, sin telemetría remota.
### 5. Metodología/harness
Ciclo `planner → implementer → validator`, una feature por sesión, specs en
`docs/specs/` y validaciones en `docs/validations/`; estados `not_started →
in_progress → passing → accepted` (o `blocked`) en `feature_list.json`;
`PROGRESS.md` como bitácora; ramas `feature/*` desde `develop`; worktrees por
feature; gate Android-only siempre fake; WIP=1 por sesión con hasta 3 sesiones
paralelas coordinadas por una integradora, una cola de merge y rebase posterior.
Enlazar `docs/mvp-execution-plan.md` y aclarar que las olas se ampliaron con
`spain-spanish-copy`, `design-system-brand-header` y `note-content-suggestions`.
### 6. Bitácora
Tabla de 6 filas, sin repetir las 99 sesiones: harness/bootstrap; MVP original;
revisión Android-first; olas 1–6; pulido final (copy, design system, sugerencias);
cierre/README. Cada fila enlaza `PROGRESS.md` o `docs/mvp-execution-plan.md`.
Cerrar con 3–5 aprendizajes trazables: validación independiente, gate fake
determinista, migraciones testeadas sin binarios, paralelismo con worktrees y
coste del congelamiento iOS.
### 7. Verificación real
Incluir `CI=true ./init.sh`, `assembleDebug`, `:core:testDebugUnitTest`,
`:composeApp:testDebugUnitTest`, y `:core:iosSimulatorArm64Test` +
`linkDebugFrameworkIosSimulatorArm64` como iOS no bloqueante; smoke manual Android
por feature. Declarar: sin E2E persistente, sin CI/CD versionado, sin smoke E2E de
cierre, sin subida a Play/TestFlight, iOS sin paridad ni smoke. No prometer “todo
verificado en producción”.
### 8. Limitaciones y riesgos
Tabla con referencia: iOS congelado y sólo compila (ADR 0003); upload iOS
`blocked`/account-gated (`docs/release/ios-testflight.md`); IA real key-gated,
red/coste/calidad fuera del gate (ADR 0004, riesgos); sin dark mode (`DESIGN.md`);
subida a Play manual no ejecutada (`docs/release/android.md`); sin harness UI/E2E;
miniaturas sin caché; enlaces con trigger best-effort; un Proyecto/single-user/sin
backend; OCR descartado (ADR 0002); R8/minify desactivado; `gradle.properties` en
real por excepción aceptada.
### 9. Próximos pasos
Sólo los documentados: descongelar iOS y completar TestFlight; subida manual a
Play; dark mode; múltiples Proyectos y Banco de ideas; backend/cuentas/sync;
caché de miniaturas; ciclo de vida de enlaces; reproducción/edición de adjuntos;
Fase B; smoke real de calidad/coste/latencia. Enlazar
`docs/risks-and-open-questions.md`; no es un roadmap comprometido.
### 10. Índice de docs (enlaces obligatorios)
`AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `ARCHITECTURE.md`, `CONTEXT.md`,
`DESIGN.md`, `CONSTRAINTS.md`, `docs/mvp-execution-plan.md`,
`docs/technical-discovery.md`, `docs/risks-and-open-questions.md`, `docs/adr/`,
`docs/specs/`, `docs/validations/`, `docs/release/android.md`,
`docs/release/ios-testflight.md`, `docs/design/icon.png` y
`docs/design/paleta.png`. Para directorios basta el enlace; no enumerar los specs.
### 11. Política de claims
Toda afirmación debe rastrearse a `PROGRESS.md`, `feature_list.json`, un ADR, un
spec o una validación; enlazar la fuente cerca. Sin conteos volátiles, sin
“production-ready”, “publicado”, “CI/CD”, “cobertura total”, “E2E automatizado” ni
“paridad iOS”. Sin rutas absolutas, usuarios, seriales, IDs locales ni secrets.
## Expected File Changes
- `README.md` — create.
- `feature_list.json` — update a `passing` con evidencia; `accepted` sólo tras
  validación independiente.
- `PROGRESS.md` — corregir estado y registrar la sesión.
- No se modifican `core/**`, `composeApp/**`, `iosApp/**`, `init.sh`,
  `gradle.properties`, build files, CI, release, ADRs, specs ni validaciones.
## Durable Documentation Impact
- `README.md`: create (entregable).
- `ARCHITECTURE.md`, `CONSTRAINTS.md`, `AGENTS.md`: not needed; no cambia la
  arquitectura, no hay reglas nuevas y el workflow no cambia.
- `docs/`: not needed; se enlazan documentos existentes.
## Plan y tareas
1. Baseline `CI=true ./init.sh` y estado real de la cola.
2. Redactar `README.md` con la estructura y el quick start exactos.
3. Verificar idioma, enlaces, comandos, secrets y coherencia.
4. Correr el gate, actualizar estado/evidencia y dejar `passing`.
- [ ] Baseline y estado; README con título, icono y 13 secciones.
- [ ] Quick start (JDK 21, SDK, gate, install, adb).
- [ ] IA fake/real, precedencia, key y excepción de `gradle.properties`.
- [ ] Arquitectura, v8, IA, UI/estado, design system y GDD.
- [ ] SDD, worktrees, gate, WIP y paralelismo; bitácora por fases.
- [ ] Verificación/límites, riesgos y próximos pasos; índice de docs.
- [ ] Revisión es-ES/secrets; gate, JSON y `git diff --check`.
## Verification Plan
- `test -f README.md` y `wc -l README.md` (objetivo 140–240).
- Voseo: `grep -nE "Instalá|probá|ejecutá|creá|configurá|Agregar imagen|Generar
  con IA" README.md` sin coincidencias.
- Extraer enlaces relativos y comprobar que existen, incluidos los 17 obligatorios;
  contrastar comandos con `init.sh` y `docs/release/android.md`.
- `grep -nE "sk-|BEGIN .*PRIVATE KEY|password\\s*=|apiKey\\s*=" README.md` sin
  secrets.
- `CI=true ./init.sh` exit 0; `assembleDebug -Pplaybook.ai.mode=fake` OK;
  `python3 -m json.tool feature_list.json >/dev/null` OK; `git diff --check`
  limpio; `git status --porcelain` sólo README + JSON + PROGRESS.
- Si hay dispositivo: `installDebug` + `adb shell am start`, registrando serial;
  si no, declarar “no ejecutado” sin fabricar evidencia. E2E persistente: **no
  aplica** (no hay harness UI/E2E).
## Evidence
README con `wc -l` y secciones; salida de `CI=true ./init.sh` y `assembleDebug`;
chequeo de enlaces/idioma/secrets; JSON válido y `git diff --check`; smoke si hubo
dispositivo (o motivo); estado y sesión en `feature_list.json`/`PROGRESS.md`.
## Validator Checklist
- [ ] Sólo `README.md`, `feature_list.json` y `PROGRESS.md`; README en raíz,
      es-ES, icono y 13 secciones; 17 enlaces resuelven sin evidencia ignorada.
- [ ] Quick start coherente con `init.sh`/release; explica el flag `fake`; IA,
      precedencia, key y excepción de `gradle.properties` sin secrets.
- [ ] Arquitectura/metodología fieles, sin frases obsoletas; bitácora enlaza
      `PROGRESS.md` sin conteos volátiles.
- [ ] Verificación honesta (sin E2E, CI/CD, Play/TestFlight; iOS sólo compila);
      límites/próximos pasos trazables; sin roadmap inventado.
- [ ] Sin secrets, rutas absolutas, usuarios ni seriales; gate exit 0, JSON válido
      y `git diff --check` limpio; evidencia y estado correctos.
## Risks
- README puede envejecer → fecha de vigencia y enlaces, sin conteos.
- Docs históricos inconsistentes → jerarquía de fuentes y no copiar frases v7.
- Entorno sin dispositivo → declarar límite, no fabricar smoke.
- Fuga de secrets → grep y no copiar `local.properties`.
- Voseo histórico → revisión es-ES.
