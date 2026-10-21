# Playbook

[![Icono de Playbook](docs/design/icon.png)](docs/design/icon.png)

## 2. Qué es Playbook

Playbook es una app **Android-first**, **local-first** y **single-user** de notas
de diseño de juegos. Captura ideas de texto, voz e imagen y las convierte en un
**GDD vivo**: un documento que combina la ficha del **Proyecto de Juego** con las
**Notas** agrupadas por `Track`. iOS queda **congelado**: se conserva y compila,
pero no es alcance ni gate del MVP (ver
[ADR 0003](docs/adr/0003-ios-congelado-mvp.md)). Pensado para un único autor por
instalación, sin cuentas, backend ni sincronización.

## 3. Funcionalidades

- **Captura flexible:** Nota de texto, dictado inline de 30 s, bocetos con
  miniatura e imagen generada desde un prompt.
- **Clasificación:** `Track` (mecánicas, personajes, historia) y **Géneros** con
  catálogo fijo de 12 más personalizados por Nota.
- **Proyecto de Juego:** ficha singleton, onboarding de primer arranque y
  **Brainstorming** de 9 pasos.
- **GDD híbrido:** ficha del Proyecto + secciones por `Track`, con export a
  Markdown, compartir y narración.
- **Q&A sobre tus Notas:** consulta en lenguaje natural con recuperación por
  similitud, citas de las fuentes y “Guardar como Nota”.
- **Notas relacionadas** por similitud y **lectura en voz alta** del Q&A, del
  cuerpo editado y del GDD.
- **Sugerencias con IA** (clasificación, contenido y Brainstorming): la IA
  **sugiere** y tú confirmas; no auto-asigna ni guarda por ti.
- **Design system Playbook** con tema claro, tipografía y cabecera de marca (ver
  [DESIGN.md](DESIGN.md)).

## 4. Estado del MVP

El MVP está implementado y **validado feature a feature**: la validación
independiente consta en [PROGRESS.md](PROGRESS.md) y, cuando existe un artefacto
dedicado, también en [docs/validations/](docs/validations/). La única
excepción abierta es `ios-release-pipeline-first-upload` (`blocked`: la subida
firmada a TestFlight exige una cuenta de Apple Developer Program y credenciales
fuera del repo; ver [docs/release/ios-testflight.md](docs/release/ios-testflight.md)).
El estado vivo y la evidencia están en [feature_list.json](feature_list.json); la
historia, en [PROGRESS.md](PROGRESS.md). Este README cierra la documentación del
MVP y sigue el mismo flujo SDD.

## 5. Quick start

**Requisitos:** JDK 21 (release lo exige; `init.sh` acepta 17+ y recomienda 21),
Android SDK con `platform-tools` y dispositivo/emulador API 24+. Indica el SDK con
`export ANDROID_HOME="…/Android/sdk"` o un `local.properties` con **solo**
`sdk.dir` (ignorado por git, nunca versionado).

**Gate:** ejecuta `./init.sh` y `CI=true ./init.sh`. Ambos corren el **mismo gate
Android-only** (`:composeApp:assembleDebug` + `:core:testDebugUnitTest`) con IA
fake, sin red y sin levantar dev servers; `CI=true` no activa un pipeline, es una
convención del [plan de ejecución](docs/mvp-execution-plan.md).

**Instalar y abrir:** ejecuta
`./gradlew :composeApp:installDebug -Pplaybook.ai.mode=fake` y después
`adb shell am start -n com.playbook.app/.MainActivity`. Con varios dispositivos,
elige uno con `ANDROID_SERIAL`. `init.sh` imprime `installDebug` sin flag, pero este
README añade
`-Pplaybook.ai.mode=fake` a propósito: el repo trae `playbook.ai.mode = real` en
`gradle.properties` por la excepción aceptada de `spain-spanish-copy`. Sin flag y
sin key, el build real falla de forma intencionada.

**IA fake/real.** `playbook.ai.mode` acepta `fake` o `real` y se resuelve con
precedencia **`-P` > `PLAYBOOK_AI_MODE` > `local.properties`**. El default del
build es `fake`, pero este repo define `real` en `gradle.properties`; por eso
`init.sh` y estos comandos fuerzan `-Pplaybook.ai.mode=fake`.

- En **fake** la app usa adaptadores deterministas, sin red ni key, y
  `BuildConfig.AI_API_KEY` queda vacío. En **real** la key se toma de
  `playbook.ai.apiKey`, `PLAYBOOK_AI_API_KEY` o `local.properties` y **vive fuera
  del repo**; un modo real incompleto falla el build con un mensaje accionable.
- Un APK **release** en modo real exige el opt-in explícito
  `-Pplaybook.ai.allowReleaseKey=true`; una variable de entorno o
  `local.properties` no lo habilitan. iOS congelado usa siempre el runtime fake.

Runbook de firma y subida manual a Play:
[docs/release/android.md](docs/release/android.md).

**Seguridad.** No muestres, pegues ni versiones keys. No copies
`local.properties` entre worktrees o equipos: además de `sdk.dir` puede contener la
key de IA. Un `local.properties` con solo `sdk.dir` es seguro.

## 6. Arquitectura

La dirección de dependencias es `iosApp → :composeApp → :core`: **`:core`** es una
librería Kotlin Multiplatform sin UI con dominio, persistencia y servicios de IA
detrás de puertos; **`:composeApp`** es la UI Compose Multiplatform, la app Android
y el framework iOS; **`iosApp/`** es el host Xcode congelado. El detalle completo
está en [ARCHITECTURE.md](ARCHITECTURE.md).

**Persistencia.** SQLDelight **2.1.0** sobre **esquema v8**, con migraciones
`1.sqm` a `7.sqm`. `createDatabase` abre la conexión, aplica el esquema y activa
`PRAGMA foreign_keys = ON`; hay drivers Android (`AndroidSqliteDriver`) e iOS
(`NativeSqliteDriver`). Los repositorios cubren Nota, Adjunto, Embedding, Enlace y
Proyecto. Los bytes de imagen y audio viven en storage privado con **rutas
relativas** en la base; no se guardan binarios en SQLite.

**IA.** La lógica de dominio consume el puerto `AiClient` (chat y embeddings) y
puertos por modelo (`TranscribeClient`, `TtsClient`, `ImageClient`, `JevClient`).
`AiRuntime` agrupa los adaptadores, la disponibilidad y la dimensión de embedding:
`fakeAiRuntime()` usa fakes deterministas y 8 dimensiones; `openAiAiRuntime(...)`
usa los 6 adaptadores OpenAI-compatible y 1536 dimensiones. En Android el borde de
red es `UrlConnectionHttpTransport`, que no sigue redirects y ejecuta el I/O fuera
del hilo principal; iOS sigue en fake. La dimensión se reconcilia y los estados de
indexado son `CAPTURED`, `PENDING`, `INDEXED` y `FAILED`. El gate y los tests
**nunca** usan red.

**UI y estado.** `App` es la raíz de la UI y dueña del estado: hoistea
`NotesDestination`, `refreshKey`, `LaunchedEffect`, `rememberCoroutineScope` y los
estados de pantalla. La lectura ocurre en `LaunchedEffect(refreshKey)` y cada
mutación incrementa `refreshKey`. **No hay DI, ViewModel, Flow ni librería de
navegación**; las llamadas `suspend` se lanzan desde la composición.

**Diseño.** Toda la UI vive bajo `PlaybookTheme` (Material 3 claro): paleta de
[DESIGN.md](DESIGN.md), Bricolage Grotesque y JetBrains Mono con licencias OFL
versionadas, shapes de 8/16/24 dp, `PlaybookBrandHeader` en todas las pantallas y
edge-to-edge. El contraste AA se mide con `PlaybookContrastTest`. **Sin dark mode**
ni dynamic color.

**GDD y observabilidad.** El GDD es una proyección pura:
`buildGddDocument(project, notes)` y `buildGddMarkdown(project, notes, now)` en
`:core`; `GddSharer` (en `:composeApp`) comparte en memoria. No hay tabla de GDD.
La observabilidad es local y best-effort mediante `Diagnostics` (sink de archivo
acotado, sin telemetría remota).

## 7. Metodología y harness

Playbook se construye con **SDD** y tres roles separados:
**planner → implementer → validator**. Cada feature tiene spec en [docs/specs/](docs/specs/);
la validación independiente consta en [PROGRESS.md](PROGRESS.md) y, si hay artefacto
dedicado, también en [docs/validations/](docs/validations/). El ciclo de estado en
[feature_list.json](feature_list.json) es `not_started → in_progress → passing →
accepted` (o `blocked`); `passing` significa implementado y auto-verificado, y solo
un validador independiente habilita `accepted`.

- Una feature por sesión, worktree y rama `feature/*` desde `develop`, con
  **WIP=1 por sesión**; el MVP restante se ejecutó con hasta **3 sesiones en
  paralelo**, coordinadas por una integradora, con una sola cola de merge y rebase
  posterior. El gate es siempre Android-only y con IA fake.
- [PROGRESS.md](PROGRESS.md) es la bitácora; las reglas están en
  [AGENTS.md](AGENTS.md) y los límites operativos en
  [CONSTRAINTS.md](CONSTRAINTS.md).
- El plan vivo de olas y merges está en
  [docs/mvp-execution-plan.md](docs/mvp-execution-plan.md); las olas se ampliaron
  después con `spain-spanish-copy`, `design-system-brand-header` y
  `note-content-suggestions`.

## 8. Bitácora

| Fase | Qué ocurrió | Fuente |
| --- | --- | --- |
| Harness y bootstrap | Se creó el harness SDD, el wrapper de Gradle, `:core`, `:composeApp` y el host iOS. | [PROGRESS.md](PROGRESS.md) |
| MVP original | Texto, voz, imagen, embeddings, enlaces, RAG, GDD, export y release Android quedaron `accepted` con validación independiente. | [PROGRESS.md](PROGRESS.md) |
| Revisión Android-first (2026-10-15) | iOS se congeló; llegaron Géneros, Proyecto, Brainstorming, GDD híbrido, nuevo shell e IA real fake/real. | [ADR 0003](docs/adr/0003-ios-congelado-mvp.md), [plan](docs/mvp-execution-plan.md) |
| Olas 1–6 | Persistencia, UI, IA, onboarding, export, dictado, miniaturas y Q&A se ejecutaron en olas paralelas con validación. | [plan](docs/mvp-execution-plan.md) |
| Pulido final | Copy es-ES, design system con cabecera de marca y sugerencias de contenido en el editor. | [PROGRESS.md](PROGRESS.md), [specs](docs/specs/) |
| Cierre / README | Cierre documental y este README-bitácora; no se ejecutó ni registró un smoke E2E de cierre. | [plan](docs/mvp-execution-plan.md), [PROGRESS.md](PROGRESS.md) |

Aprendizajes trazables: (1) la **validación independiente** encontró defectos
reales que el implementer no vio, así que `passing → accepted` no es burocracia;
(2) un **gate fake determinista** permite compilar y testear sin red, sin key y sin
coste; (3) las **migraciones se prueban sin binarios** en Android/JVM e iOS;
(4) el **paralelismo con worktrees y una cola de merge** funcionó, a costa de
rebases y conflictos en los hotspots de UI; (5) **congelar iOS** reduce coste pero
acumula deuda, porque cada `expect` nuevo exige un `actual` iOS mínimo.

## 9. Verificación real

Gate oficial (Android-only, IA fake): solo `CI=true ./init.sh`; el resto son verificaciones manuales no bloqueantes:

```bash
ANDROID_HOME="…/Android/sdk" CI=true ./init.sh
./gradlew :composeApp:assembleDebug -Pplaybook.ai.mode=fake
./gradlew :core:testDebugUnitTest -Pplaybook.ai.mode=fake
./gradlew :composeApp:testDebugUnitTest -Pplaybook.ai.mode=fake
./gradlew :core:iosSimulatorArm64Test -Pplaybook.ai.mode=fake
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 -Pplaybook.ai.mode=fake
```

Los comandos iOS no bloquean: iOS está congelado, pero se comprueba que compila. Las features
de UI se verificaron con **smoke manual Android** e IA fake cuando hubo dispositivo; si no, se
declaró no ejecutado. Evidencia en `composeApp/build/smoke-evidence/`, ignorada por git.

Límites honestos: **no hay E2E persistente ni harness UI automatizado**; **no hay
CI/CD versionado** (`CI=true` es una convención local del plan); **no hay smoke E2E
de cierre registrado**; **no hay subida a Play ni a TestFlight registrada**, porque
son manuales y requieren cuentas externas; **iOS no tiene paridad ni smoke**, solo
compila. No se afirma que todo esté “verificado en producción”.

## 10. Limitaciones y riesgos

| Límite o riesgo | Referencia |
| --- | --- |
| iOS congelado: solo compila, sin paridad ni smoke. | [ADR 0003](docs/adr/0003-ios-congelado-mvp.md) |
| Upload de iOS `blocked` y account-gated. | [docs/release/ios-testflight.md](docs/release/ios-testflight.md) |
| IA real key-gated; red, coste y calidad quedan fuera del gate. | [ADR 0004](docs/adr/0004-ai-real-integration.md), [riesgos](docs/risks-and-open-questions.md) |
| Sin dark mode. | [DESIGN.md](DESIGN.md) |
| Subida manual a Play no ejecutada en el repo. | [docs/release/android.md](docs/release/android.md) |
| Sin harness UI/E2E persistente. | [PROGRESS.md](PROGRESS.md) |
| Miniaturas sin caché ni redimensionado. | [riesgos](docs/risks-and-open-questions.md) |
| Enlaces con trigger de recálculo best-effort. | [riesgos](docs/risks-and-open-questions.md) |
| Un solo Proyecto, single-user, sin backend ni sync. | [CONTEXT.md](CONTEXT.md), [riesgos](docs/risks-and-open-questions.md) |
| OCR del boceto descartado en el MVP. | [ADR 0002](docs/adr/0002-ocr-decision.md) |
| R8/minify desactivado en release. | [ARCHITECTURE.md](ARCHITECTURE.md) |
| `gradle.properties` en `real` por excepción aceptada. | [CONSTRAINTS.md](CONSTRAINTS.md) |

## 11. Próximos pasos

Solo los documentados en [docs/risks-and-open-questions.md](docs/risks-and-open-questions.md)
y el [plan de ejecución](docs/mvp-execution-plan.md); **no es un roadmap comprometido**:
descongelar iOS y completar TestFlight; subida manual a Play; dark mode; múltiples
Proyectos y Banco de ideas; backend, cuentas y sync; caché de miniaturas; ciclo de
vida de enlaces; reproducción/edición de Adjuntos; Fase B; y smoke key-gated de
calidad, coste y latencia de la IA.

## 12. Índice de docs

- **Estado y reglas:** [AGENTS.md](AGENTS.md), [PROGRESS.md](PROGRESS.md),
  [feature_list.json](feature_list.json), [CONSTRAINTS.md](CONSTRAINTS.md).
- **Producto, proceso y release:** [ARCHITECTURE.md](ARCHITECTURE.md),
  [CONTEXT.md](CONTEXT.md), [DESIGN.md](DESIGN.md),
  [docs/technical-discovery.md](docs/technical-discovery.md),
  [docs/risks-and-open-questions.md](docs/risks-and-open-questions.md),
  [docs/mvp-execution-plan.md](docs/mvp-execution-plan.md), [docs/adr/](docs/adr/),
  [docs/specs/](docs/specs/), [docs/validations/](docs/validations/),
  [docs/release/android.md](docs/release/android.md),
  [docs/release/ios-testflight.md](docs/release/ios-testflight.md),
  [docs/design/icon.png](docs/design/icon.png),
  [docs/design/paleta.png](docs/design/paleta.png).

## 13. Vigencia

Documento vigente a **2026-10-21**. La fuente operativa es
[PROGRESS.md](PROGRESS.md) y [feature_list.json](feature_list.json); este README
resume y enlaza, no los sustituye. Ante conflicto, prevalece la fuente enlazada (o
la spec, ADR o validación aplicable).
