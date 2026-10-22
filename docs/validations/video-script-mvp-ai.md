# Validación: video-script-mvp-ai

- **Feature:** `video-script-mvp-ai`
- **Spec:** `docs/specs/video-script-mvp-ai.md`
- **Rama/worktree:** `feature/video-script-mvp-ai` (`worktree/video-script-mvp-ai`), base `origin/develop` `4342edb`
- **Fecha:** 2026-10-22
- **Tipo:** revalidación independiente tras la reparación del veredicto `revise` (Session 103)
- **Veredicto:** **accept**

## 1. Alcance

Feature de solo documentación: guion, storyboard y plan de grabación del vídeo demo
del MVP con IA real. No se graba el vídeo ni se toca producto, código, esquema, IA,
`init.sh`, build files ni `iosApp/`. Se revisa la reparación A–C y la declaración
D–F como riesgos asumidos.

## 2. Verificación ejecutada

| Comprobación | Resultado |
|---|---|
| Duración del guion | Bloques 1–7 = 40+80+80+150+330+60+70 = **810 s (13:30)**; + cierre 40 s = **850 s (14:10)**; límite **900 s (15:00)**; mínimo **600 s (10:00)** |
| Duración del storyboard | P00–P13 = **810 s**; P00–P14 = **850 s**; P15 = 50 s de margen |
| Narración | **2.052 palabras** en los bloques literales de §4 (≈13:30 a 152 ppm) |
| Enlaces y anclas | **176** enlaces relativos (109 guion + 41 storyboard + 26 plan): **0** rutas rotas y **0** anclas inexistentes (slug GitHub) |
| Secrets | Greps de `sk-`, `PLAYBOOK_AI_API_KEY=`, `apiKey=`, `BEGIN … PRIVATE KEY`, `password=`, `local.properties`, rutas `/Users/`: **0** coincidencias |
| JSON | `python3 -m json.tool feature_list.json` → OK |
| Diff | `git diff --check` limpio; `.md` nuevos sin espacios finales ni tabs |
| Gate | `ANDROID_HOME="$HOME/Library/Android/sdk" CI=true ./init.sh` → **exit 0**, `BUILD SUCCESSFUL`, 68 tareas up-to-date |
| Código | Solo docs y artefactos durables; sin cambios en `composeApp/`, `core/`, `iosApp/`, `init.sh` ni build files |

**Límites:** no se re-grabó el vídeo. El smoke real no se ejecutó (es manual,
key-gated y requiere red/key); se revisó su contrato contra `RealAiSmokeTest`.

## 3. Reparación A · Pre-flight real (verificada)

`composeApp/src/androidUnitTest/kotlin/com/playbook/app/ai/RealAiSmokeTest.kt`:

- `smokeAllSixModels` (línea 43) ejecuta **embedding (53) → chat (61) → tts (66) →
  transcribe (70) → image (76) → jev (82)**.
- Si `transcribe` falla, la aserción aborta: `image` y `jev` **no se ejecutan**.
- `smokeRealChat` (línea 189) solo cubre chat + indexado/RAG; no toca `tts`,
  `image` ni `jev`.

`docs/video/plan-grabacion-demo-mvp.md` §5 describe ese orden, el carácter
todo-o-nada, el fallo aislado de `transcribe` y el **pre-flight manual obligatorio**
de `tts`/`image`/`jev` con evidencia sanitizada y `adb shell pm clear` antes de la
toma limpia. Checklist §9.1 y tabla de cobertura coherentes. **Corregido.**

## 4. Reparación B · GDD (verificada)

`core/src/commonMain/kotlin/com/playbook/core/gdd/GddDocument.kt`:

- `projectBlock` (66-80) emite en orden fijo **Géneros, Core Loop, Audiencia,
  Concepto, Inspiraciones, Fantasía, Mecánicas clave, Fuera de alcance** y omite
  los campos vacíos.
- `title` (78) va fuera de la ficha; `GddScreen.kt:324` pinta la cabecera
  `GDD — <título>` (o `GDD`).

Guion §4.5.4 y storyboard P08 narran la cabecera con el Título, la ficha solo con
campos no vacíos y, en esta toma, solo Título + Géneros. **Corregido.**

## 5. Reparación C · Guardar en Brainstorming (verificada)

`composeApp/src/commonMain/kotlin/com/playbook/app/BrainstormingWizardScreen.kt`:

- “Guardar” está disponible antes del último paso (439-441), incluido Género.
- `save()` (217-230) llama `onSave(buildDraft())`; `App.kt:896-905` persiste,
  recarga y hace `goToList()`.
- El Back invoca `onCancel` (417) sin escribir: Cancelar/volver no persiste.

Guion §4.5.2 incluye `[ACCIÓN: pulsar “Guardar”]` tras sugerir Género y explica
que persiste Título y Géneros, vuelve a la lista y que Cancelar no escribe;
storyboard P06 y checklist del plan lo reflejan. **Corregido.**

## 6. D–F · Riesgos asumidos (verificados como declaración)

`docs/video/plan-grabacion-demo-mvp.md` §14 dice expresamente que **no se reparan**
y que se controlan en el ensayo y la toma:

- **D:** `SemanticLinkingService.kt:61` fija `DEFAULT_SIMILARITY_THRESHOLD = 0.8`
  y el filtro es `score >= threshold`. Regla de ensayo, B06 real y prohibición de
  presentar vacío como enlaces (§14.1; guion §4.5.6; P10).
- **E:** `NoteEditorScreen.kt:947,960` renderiza el panel “Dictado” en Nueva nota.
  Fuera de cuadro, sin interactuar; si entra, repetir o B03 (§14.2; guion §4.5.3;
  P07).
- **F:** el gate (`init.sh:35`) y sus tests usan fake; el smoke real es manual y
  key-gated, fuera del gate y de CI (§14.3; guion §4.3 y §9; P03).

No hay ninguna afirmación de que estos puntos estén resueltos. **Conforme.**

## 7. Duraciones, enlaces y seguridad

- Duraciones exactas y sin cambios: **810 / 850 / 900 / 600 s**.
- **176** enlaces relativos, **0** rotos, **0** anclas inexistentes; las anclas
  nuevas de §14 resuelven.
- Sin secrets, keys, rutas personales ni seriales. La key se trata como archivo
  local/entorno, fuera de cámara.

## 8. Alcance y documentación durable

- `git status`: solo `AGENTS.md`, `PROGRESS.md`, `feature_list.json` y los nuevos
  `docs/specs/video-script-mvp-ai.md` + `docs/video/*.md`. **Sin cambios de código.**
- Diff semántico de `feature_list.json`: solo se añade la feature; ninguna feature
  existente cambia (el reindentado de `design-system-brand-header` es cosmético).
- `feature_list.json`, `PROGRESS.md` y `AGENTS.md` reflejan `passing` y la
  reparación de Sessions 102/103.

## 9. Hallazgos

| Severidad | Hallazgo | Acción |
|---|---|---|
| Baja, no bloqueante | La evidencia de Session 102 y el resumen de `PROGRESS.md` mantienen 653/357/494 líneas y 108/41/20 enlaces; los actuales son **675/370/557** y **109/41/26 (176)**. | Corregir al persistir `accepted`. |
| Informativa | Reindentado cosmético del array de evidencias de `design-system-brand-header` en `feature_list.json`. | Sin cambio semántico; no requiere acción. |

No hay hallazgos bloqueantes ni overclaims nuevos.

## 10. Seguridad

Checklist aplicada al alcance: sin secrets, sin API key, sin keystores, sin rutas
personales ni contenido de `local.properties`; sin capturas ni binarios nuevos.
**Sin hallazgos de seguridad.**

## 11. Handoff

Persistir `accepted` en `feature_list.json`/`PROGRESS.md`/`AGENTS.md` con la
evidencia indicada por el validador y corregir los conteos de líneas/enlaces.
No commit ni push desde la validación.
