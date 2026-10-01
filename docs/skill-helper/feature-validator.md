# Skill: feature-validator

- **Skill**: `feature-validator`
- **Ruta**: `.opencode/skills/feature-validator/SKILL.md`

**Qué hace**: valida de forma independiente una feature ya implementada contra su spec, `feature_list.json`, `PROGRESS.md` y el diff actual; revisa comportamiento, evidencia de verificación, disciplina de alcance, arquitectura, seguridad, docs durables y handoff, y devuelve un veredicto `accept`/`revise`/`block`.

**Cuándo se activa**: cuando el usuario pide validar, revisar, hacer QA, evaluar o aceptar/rechazar una feature después de la implementación. El validador es independiente del implementador: no confía en el autoinforme y juzga con artefactos del repo y evidencia de ejecución.

**Cómo está estructurada**: `SKILL.md` (Role Boundary, Inputs, workflow de 6 pasos y output summary) más dos referencias: `references/validation-rubric.md` (tabla de criterios, regla de `init.sh`, severidades, formato de hallazgos y `Implementation Repair Brief`) y `references/security-checklist.md` (revisión de seguridad acotada a la feature, 9 áreas). Las referencias van aparte porque solo se cargan en el paso 4 (calidad/riesgo) y la de seguridad es extensa.

**Flujo de funcionamiento**: 6 pasos.

1. Fijar el objetivo de revisión: feature, spec, diff, estado y evidencia.
2. Validar contra el spec: goal, non-goals, escenarios, ficheros esperados, tareas, verificación.
3. Reejecutar o inspeccionar la verificación (gate estándar; jerarquía estáticos → tests → E2E → manual).
4. Revisar calidad y riesgo con la rúbrica y la checklist de seguridad.
5. Comprobar los docs durables según el `Durable Documentation Impact` del spec.
6. Emitir veredicto: `accept`, `revise` o `block`.

**Entradas y salidas**: entradas = `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `docs/specs/<feature-id>.md`, git status/diff, docs durables (`ARCHITECTURE.md`, `CONSTRAINTS.md`, `DESIGN.md`) y los ficheros cambiados. Salida = veredicto + resumen (feature y spec, checks reejecutados, hallazgos por severidad, `Implementation Repair Brief` si hay `revise`, resumen de seguridad, estado de docs/harness y follow-up). No marca la aceptación final en los ficheros salvo petición explícita: en el flujo normal, el orquestador persiste `accepted`.

**Ejemplo práctico**: (genérico, ilustrativo, no es contenido literal de la skill)

```text
Situación: export-notes-to-md está "passing" con evidencia en feature_list.json
feature-validator:
 1. Lee spec, diff y evidencia; confirma que hay contrato
 2. Comprueba escenarios, ficheros esperados y alcance (sin features adyacentes)
 3. Reejecuta build + tests del repo
 4. Aplica rúbrica y checklist de seguridad (secrets, auth, input)
 5. Comprueba que AGENTS/ARCHITECTURE/CONSTRAINTS se tocaron solo si hacía falta
 6. Emite veredicto
 Salida:
   Veredicto: revise
   Hallazgo (High): init.sh solo imprime comandos, no los ejecuta
   Repair Brief: hacerlo gate no-bloqueante con set -euo pipefail
   Seguridad: sin hallazgos relevantes
```

**Reglas y límites**: es el validador, no el planner ni el implementador; no acepta por la confianza del implementador ni por su resumen; `passing` = listo para validar, no aceptado; no hace arreglos amplios durante la validación salvo petición explícita; sin spec no valida; `accept` sin hallazgos críticos/altos, `revise` con arreglos accionables, `block` si no se puede validar o es inseguro; cada hallazgo debe ser ejecutable (severidad, evidencia, por qué importa, cambio requerido, pasos, verificación); no edita ficheros ni persiste `accepted` si se ejecuta como validador directo.
