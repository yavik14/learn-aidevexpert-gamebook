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
`notes-list-ui`, el CRUD de texto desde la UI (crear/editar/borrar + refresco)
con `create-text-note`, el puerto de IA con `ai-client-interface`, la decisión de
runtime de IA con `ai-runtime-decision` (ADR + spike test-only, decisión híbrida
cloud-first), la publicación de Android con `android-release-pipeline`
(release/signing con keystore externo + runbook; ver `docs/release/android.md`),
la generación/persistencia de embeddings con `embeddings-generation` (esquema v2
y `1.sqm`), la captura por voz nativa con `voice-capture-stt` (puerto
`VoiceTranscriber` + adaptadores Android/iOS), las Etiquetas libres con
`note-category-and-tags` (esquema v3, migración `2.sqm`) y la captura de imagen
(cámara/galería → Nota con Adjunto) con `image-capture-camera` (esquema v4,
migración `3.sqm`), todas `accepted`. La decisión de viabilidad del OCR del
boceto con `image-ocr-optional` quedó documentada como **DISCARD para el MVP**
(ADR 0002 + docs; validada de forma independiente, `accepted`). La primera vista
de GDD (`gdd-view`) está implementada y auto-verificada (`passing`): proyección
pura de las Notas por `track` (`buildGdd` en `:core`) + pantalla `GddScreen`, sin
tabla ni esquema nuevos; requiere validación independiente para `accepted`. El
pipeline de release de iOS (`ios-release-pipeline-scaffolding`) tiene los
artefactos repo-side verificados (scripts, scheme compartido, firma, versionado,
`.gitignore`, runbook) y está `passing`; el upload firmado a TestFlight
(`ios-release-pipeline-first-upload`) queda `blocked` sin Apple Developer Program
+ credenciales (ver `docs/release/ios-testflight.md`). **Nunca** versionar
keystores ni secrets.
Próximas features en cola: `semantic-linking` y `voice-attachment-storage`
(dependencias ya `accepted`); requieren spec.

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
