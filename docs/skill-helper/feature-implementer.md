# Skill: feature-implementer

- **Skill**: `feature-implementer`
- **Ruta**: `.opencode/skills/feature-implementer/SKILL.md`

**Qué hace**: implementa una feature ya planificada: escribe el código, se autoverifica, registra evidencia, actualiza el estado del harness y los docs durables, y deja el repo listo para un validador independiente.

**Cuándo se activa**: cuando el usuario pide implementar un feature spec, ejecutar la feature seleccionada, actuar como generador/implementador en el flujo planner→generator→validator, o convertir un spec en código. No hace la validación final.

**Cómo está estructurada**: `SKILL.md` (Role Boundary, Inputs, workflow de 6 pasos y output summary) más `references/implementation-rules.md` (reglas de docs durables, autoverificación, regla de `init.sh` y de alcance) y `agents/openai.yaml`. Las reglas van aparte porque son de apoyo y no siempre se necesitan.

**Flujo de funcionamiento**: 6 pasos.

1. Confirmar el contrato: leer el spec (goal, non-goals, escenarios, ficheros esperados, impacto en docs, tareas, verificación, evidencia, checklist).
2. Poner la feature en `in_progress` y dejar las demás `not_started`.
3. Implementar solo esa feature con el cambio más pequeño coherente, siguiendo patrones del repo, WIP=1 y con tests.
4. Autoverificar con el plan de verificación del spec y el gate estándar del repo (estáticos → tests → E2E → manual).
5. Actualizar el estado del harness (`feature_list.json`, `PROGRESS.md`, docs durables, y `CONSTRAINTS.md` si surge una regla MUST/MUST NOT).
6. Dejar estado listo para validación (cambios guardados, sin temporales, evidencia registrada).

**Entradas y salidas**: entradas = `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `docs/specs/<feature-id>.md`, docs durables (`ARCHITECTURE.md`, `CONSTRAINTS.md`, `DESIGN.md`) y los ficheros de la app. Salidas = cambios de código y tests de la feature, `feature_list.json` actualizado (estado + evidencia), `PROGRESS.md` y docs durables, más un resumen. Nunca marca `accepted` ni crea commits.

**Ejemplo práctico**: (genérico, ilustrativo, no es contenido literal de la skill)

```text
Situación: existe docs/specs/export-notes-to-md.md y la feature está not_started
feature-implementer:
 1. Lee el spec y confirma el contrato
 2. Marca export-notes-to-md como in_progress
 3. Implementa el export a markdown siguiendo patrones del repo + un test
 4. Ejecuta la verificación (build + tests) y captura resultados
 5. Actualiza feature_list.json (status passing + evidencia) y PROGRESS.md
 6. Deja el repo listo para el validador
 Salida: código + test + estado/evidencia (no marca accepted, no commitea)
```

**Reglas y límites**: es el implementador, no el planner ni el validator; no amplía el alcance ni implementa features adyacentes; no rediseña el spec en silencio (lo actualiza o marca `blocked`); no declara aceptación y nunca pone `accepted`; `passing` = autoverificado y listo para validar; si queda parcial o sin verificar, deja `in_progress`/`blocked` y no oculta fallos; una feature por sesión; deja el estado comprensible sin depender del chat.
