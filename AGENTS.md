# Instrucciones para agentes

**Playbook** es una app móvil de notas de diseño de juegos (Kotlin Multiplatform +
Compose Multiplatform) que construye un GDD vivo a partir de capturas de texto,
voz e imagen, con IA integrada. Es **Android-first**: iOS queda **congelado**
(ver `docs/adr/0003-ios-congelado-mvp.md`).

## Estado actual: bootstrapped (Android-first)
El repo tiene toolchain y app: wrapper de Gradle, version catalog, `:core` (KMP
library compartida), `:composeApp` (UI Compose Multiplatform + app Android +
framework iOS) y host `iosApp` (Xcode, congelado). El gate `./init.sh` es **no
bloqueante y Android-only**: `:composeApp:assembleDebug` + `:core:testDebugUnitTest`.

El **MVP original está completo y `accepted`** (texto/voz/imagen, embeddings,
enlaces, RAG, GDD + export, release Android) — historia y evidencia en
`PROGRESS.md` y `feature_list.json`.

**Revisión de alcance 2026-10-15 (Android-first).** iOS congelado; las Etiquetas
libres se reemplazan por **Géneros** (catálogo fijo + personalizados); aparece el
**Proyecto de Juego** (ficha singleton) con su flujo de **Brainstorming**; el GDD
pasa a ser el **documento** del Proyecto (ficha + secciones por Track); pantalla
principal rediseñada (top bar + iconos + FAB), bocetos con miniatura, dictado
inline de 30s, Q&A con "guardar como Nota", y **IA real** con un proveedor propio
OpenAI-compatible (modelos: chat, embedding, transcribe, tts, image, jev) con
alternancia **fake/real** por build flag (la IA puede **sugerir** clasificación;
el usuario **confirma**). Diseño en `DESIGN.md` (paleta `docs/design/paleta.png`).

**Nunca** versionar secrets, API keys ni keystores (incluida la API key de IA).

Próximas features en cola: las `not_started` de `feature_list.json` (revisión
2026-10-15); `app-icon-configuration`, `ai-client-ports`,
`genres-catalog-persistence` (Géneros: dominio + persistencia v7),
`game-project-persistence` (Proyecto, persistencia v8),
`genres-catalog-ui` (picker de chips) y `ai-fake-real-switch` (IA real
OpenAI-compatible) quedaron implementadas y aceptadas (validación independiente
`accept`). `brainstorming-wizard` quedó `accepted` (spec
`docs/specs/brainstorming-wizard.md`; validación independiente `accept` en
`docs/validations/brainstorming-wizard.md`). `inline-dictation-30s`,
`ai-real-embeddings` y `sketch-thumbnails` también quedaron `accepted` y
mergeadas (specs `docs/specs/inline-dictation-30s.md`,
`docs/specs/ai-real-embeddings.md` y `docs/specs/sketch-thumbnails.md`).
`brainstorming-ai-suggestions` quedó `accepted` (spec
`docs/specs/brainstorming-ai-suggestions.md`; validación independiente `accept`
en `docs/validations/brainstorming-ai-suggestions.md`).

**En vuelo (no tocar ni duplicar):** `brainstorming-note-per-step` (`in_progress`
en `worktree/brainstorming-note-per-step`, spec y código sin commit), pendiente
de revisión y de implementer → validator. `first-run-project-onboarding` quedó
implementada y `accepted` (spec `docs/specs/first-run-project-onboarding.md`;
validación independiente `accept`). La siguiente ready sin dependencias abiertas
es `gdd-document-hybrid` (ya tiene worktree/rama; no tocar desde esta sesión);
luego las demás `not_started` de las olas 3–6. `gdd-export-hybrid` espera
`gdd-document-hybrid` y `readme-project-documentation` cierra el MVP. Para iOS:
el upload firmado (`ios-release-pipeline-first-upload`) sigue
`blocked`/account-gated.

## Leer primero
1. `PROGRESS.md` — estado verificado y próximo paso (fuente de la verdad actual).
2. `feature_list.json` — estado de las features y dependencias.
3. `docs/specs/<feature-id>.md` — contrato de la feature a trabajar.

Contexto de producto y dominio: `CONTEXT.md` (glosario), `docs/build-brief.md`,
`docs/domain-model.md`, `docs/risks-and-open-questions.md`, `DESIGN.md`. Al tocar
stack/IA/storage/integraciones: `docs/technical-discovery.md` y `docs/adr/*.md`.

Los documentos del repo se escriben en **español**, con términos técnicos en
inglés.

## Flujo por feature (SDD)
Toda feature se planifica antes de implementarse; hay tres roles separados:
**planner → implementer → validator**. No se implementa una feature sin spec.

1. `$feature-spec` → crear `docs/specs/<feature-id>.md` (una feature por corrida).
2. `$feature-implementer` → implementar la spec, correr la verificación y registrar evidencia.
3. `$feature-validator` → validar contra la spec de forma independiente (`accept`/`revise`/`block`).

`$feature-flow` orquesta los tres roles con los subagentes (`planner`,
`implementer`, `validator` en `.opencode/agents/`). Las skills viven en
`.opencode/skills/` y son la fuente de verdad de cada rol: no dupliques sus
reglas.

Ciclo de estado en `feature_list.json`: `not_started → in_progress → passing →
accepted` (o `blocked`). `passing` = implementada y auto-verificada; solo un
validador independiente (`accept`) habilita `accepted`. Trabaja **una feature a
la vez** (WIP=1).

**Ejecución paralela controlada (MVP restante).** Por decisión del autor, el
backlog pendiente puede ejecutarse con **hasta 3 features en vuelo**, una por
sesión/worktree/rama, coordinadas por una sesión integradora. Sigue vigente:
una feature por sesión, planner → implementer → validator dentro de cada feature,
una sola cola de merge y WIP=1 por sesión. La sesión integradora es la única que
mergea a `develop`; las demás sesiones rebasean después de cada integración. El
plan vivo está en `docs/mvp-execution-plan.md`.

Selección de la próxima feature: la primera en orden de `feature_list.json` que
no esté `passing`/`accepted`, cuyos `depends_on` estén satisfechos (una
dependencia se satisface cuando su estado es `accepted`) y que ya tenga spec.

## Arranque de sesión
1. Confirmar el directorio con `pwd`.
2. Ejecutar `./init.sh`; si la verificación base falla, arreglarla antes de avanzar.
3. Elegir la feature según la regla anterior y leer su spec.

Mantener los cambios dentro del alcance de la feature elegida salvo que un
bloqueo requiera un arreglo de soporte acotado.

## Reglas de trabajo
- Rama nueva con gitflow: `feature/*` desde `develop` (`main` es release).
- No marcar una feature completa solo porque se añadió código.
- No cambiar en silencio las reglas de verificación ni el alcance de la spec.
- Actualizar los artefactos durables (`PROGRESS.md`, `feature_list.json`, docs)
  en lugar de depender de resúmenes del chat.
- Nunca commitear secrets, API keys ni keystores (incluida la de IA).
- `init.sh` debe seguir siendo un gate no bloqueante y **Android-only**: ejecuta
  checks del estado actual y **no** levanta procesos de larga duración (sin dev
  servers). iOS está congelado: no se exige paridad ni verificación iOS, pero el
  proyecto iOS se conserva y debe seguir compilando (los nuevos `expect` aportan
  un `actual` iOS mínimo).
- Los tests y el gate usan **siempre la IA fake** (determinista, sin red ni key).

## Definición de hecho
Una feature está hecha solo si: existe spec y la implementación se ciñe a ella;
el comportamiento objetivo está implementado; la verificación requerida se
ejecutó de verdad; la evidencia quedó en `feature_list.json`/`PROGRESS.md`; el
repo sigue arrancable con `./init.sh`; y los docs relevantes se actualizaron si
cambió el comportamiento de producto, las reglas de dominio, la API o la
verificación.

## Fin de sesión
Actualizar `PROGRESS.md` y `feature_list.json`, registrar riesgos o bloqueos no
resueltos y dejar el repo limpio para que la próxima sesión pueda correr
`./init.sh` de inmediato.
