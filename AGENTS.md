# Instrucciones para agentes

**Playbook** es una app móvil de notas de diseño de juegos (Kotlin Multiplatform +
Compose Multiplatform, Android + iOS en paralelo) que construye un GDD vivo a
partir de capturas de texto, voz e imagen.

## Estado actual: bootstrapped
El repo ya tiene toolchain y app mínima: wrapper de Gradle, version catalog,
módulo `:core` (KMP library compartida), módulo `:composeApp` (UI Compose
Multiplatform + app Android + framework iOS) y host `iosApp` (Xcode). `./init.sh`
corre el gate real no bloqueante: Android `assembleDebug`, tests del core y link
del framework iOS. Mapeo de módulos, ids y versiones en `ARCHITECTURE.md` y
`docs/technical-discovery.md`. La primera UI real (lista de notas) llegó con
`notes-list-ui` y el CRUD de texto desde la UI (crear/editar/borrar + refresco)
con `create-text-note` (implementada y `accepted`). La captura por voz nativa
(dictado → Nota con transcripción) llegó con `voice-capture-stt` (implementada y
`passing`, pendiente de validación independiente).
Próxima feature en cola: `note-category-and-tags` (depende de `create-text-note`,
ya `accepted`; requiere spec).

## Leer primero
1. `PROGRESS.md` — estado verificado y próximo paso (fuente de la verdad actual).
2. `feature_list.json` — estado de las features y dependencias.
3. `docs/specs/<feature-id>.md` — contrato de la feature a trabajar.

Contexto de producto y dominio: `CONTEXT.md` (glosario), `docs/build-brief.md`,
`docs/domain-model.md`, `docs/risks-and-open-questions.md`. Al tocar
stack/IA/storage/integraciones: `docs/technical-discovery.md` y `docs/adr/*.md`
(cuando existan).

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
- Nunca commitear secrets, API keys ni keystores.
- `init.sh` debe seguir siendo un gate no bloqueante: ejecuta checks del estado
  actual y **no** levanta procesos de larga duración (sin dev servers).

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
