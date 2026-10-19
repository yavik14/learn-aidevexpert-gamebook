# Plan de ejecución del MVP restante

## Objetivo

Completar las **18 features `not_started`** del backlog revisado (Android-first)
y cerrar con una **validación end-to-end** y un **README-bitácora**. Queda fuera
`ios-release-pipeline-first-upload` (`blocked`, account-gated) e iOS sigue
congelado (`docs/adr/0003-ios-congelado-mvp.md`).

## Modelo de ejecución

- **Una feature por sesión, worktree y rama** (`feature/<feature-id>` desde
  `develop`).
- **Hasta 3 sesiones en paralelo**, coordinadas por una sesión integradora.
- **WIP=1 dentro de cada feature**: planner → implementer → validator, sin saltos.
- **Paradas de revisión** tras planner, tras implementer y tras validator; el
  commit se hace sólo con el OK del autor.
- La **sesión integradora** es la única que mergea a `develop`; las sesiones de
  feature rebasean después de cada integración.
- El gate es siempre **Android-only** y usa **IA fake**: `CI=true ./init.sh`.
- Nunca se versionan secrets, API keys ni keystores.

## Flujo por feature

1. **Planner**: crear/actualizar `docs/specs/<feature-id>.md`.
   Revisar alcance, non-goals, escenarios, archivos y verificación.
2. **Implementer**: implementar la spec aprobada y autoverificar.
   Revisar diff, tests, evidencia y smoke.
3. **Validator**: validación independiente (`accept`/`revise`/`block`).
   `revise` vuelve al implementer con el brief; `accept` habilita el commit.
4. **Integración**: actualizar `feature_list.json`/`PROGRESS.md`, commit
   Conventional Commit, sync con `develop`, merge y gate en `develop`.

## Olas

| Ola | Sesiones | Features |
|---|---|---|
| 0 | 051 | Plan de ejecución (esta rama, sin feature de producto) |
| 1 | 052–054 | `game-project-persistence`, `genres-catalog-ui`, `ai-fake-real-switch` |
| 2 | 055–057 | `brainstorming-wizard`, `inline-dictation-30s`, `ai-real-embeddings` |
| 3 | 058–060 | `gdd-document-hybrid`, `sketch-thumbnails`, `ai-suggest-classification` |
| 4 | 061–063 | `gdd-export-hybrid`, `transcribe-audio-attachment`, `first-run-project-onboarding` |
| 5 | 064–066 | `image-generation-from-text`, `save-answer-as-note`, `brainstorming-ai-suggestions` |
| 6 | 067–069 | `tts-read-aloud`, `main-screen-playbook-shell`, `ai-real-chat` |
| Cierre | 070 | Validación end-to-end, gate en `develop`, release Android |
| README | 071 | `readme-project-documentation` (último commit) |

### Orden de merge recomendado

- **Ola 1**: `game-project-persistence` → `genres-catalog-ui` → `ai-fake-real-switch`.
- **Ola 2**: `ai-real-embeddings` → `inline-dictation-30s` → `brainstorming-wizard`.
- **Ola 3**: `gdd-document-hybrid` → `sketch-thumbnails` → `ai-suggest-classification`.
- **Ola 4**: `transcribe-audio-attachment` → `gdd-export-hybrid` → `first-run-project-onboarding`.
- **Ola 5**: `brainstorming-ai-suggestions` → `save-answer-as-note` → `image-generation-from-text`.
- **Ola 6**: `ai-real-chat` → `tts-read-aloud` → `main-screen-playbook-shell`.

El orden serializa los hotspots (`App.kt`, `NoteEditorScreen`, `GddScreen`) para
reducir conflictos entre ramas.

## Coordinación paralela

- Cada sesión edita sólo su entrada de `feature_list.json` y agrega su bloque al
  final de `PROGRESS.md`; la coordinadora renumera sesiones si hay solapamiento.
- Un merge a la vez. Antes de integrar, la rama se sincroniza con
  `origin/develop` con `git-sync-branch` (squash a un commit y
  `--force-with-lease`).
- Después de cada merge: `CI=true ./init.sh` en `develop` y
  `git fetch && git rebase origin/develop` en las sesiones activas.
- No se corre `./init.sh` simultáneamente en todos los worktrees; el gate
  completo se reserva para la integración.

## Criterio de cierre del MVP

1. Las 18 features están `accepted`.
2. `CI=true ./init.sh` exit 0 en `develop`.
3. Smoke end-to-end Android: primer arranque → Brainstorming o saltar → Notas de
   texto/voz/boceto con Track y Géneros → indexado → enlaces/GDD → Q&A con citas
   → guardar respuesta como Nota.
4. Smoke manual con IA real (key fuera del repo) en chat, embeddings,
   transcripción, tts, imagen y sugerencia de clasificación.
5. Build/release Android operativo.
6. `README.md` publicado como bitácora final del proyecto (app, metodología,
   harness, arquitectura, verificación, aprendizajes y próximos pasos).

## Riesgos

- **Alcance amplio**: mitigado por olas, paradas y límite de 3 sesiones.
- **Hotspots de UI**: mitigado por el orden de merge.
- **Esquema v8**: sólo `game-project-persistence`; no se paraleliza otro cambio
  de esquema.
- **IA real**: red, costo y key; tests/gate siempre fake.
- **Specs grandes** (`brainstorming-wizard`, `tts-read-aloud`): dividir antes de
  implementar si el planner detecta exceso.
