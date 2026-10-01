# Skill: feature-spec

- **Skill**: `feature-spec`
- **Ruta**: `.opencode/skills/feature-spec/SKILL.md`

**Qué hace**: convierte una feature del harness (`feature_list.json`) en una especificación lista para implementar: un contrato de planificación (ni PRD ni código) que permite a otro agente implementar con mínima redescubrición y a un validador juzgar el resultado.

**Cuándo se activa**: cuando el usuario pide planificar una feature, crear un feature spec, aplicar SDD ligero, preparar trabajo para un agente implementador, o generar specs/tareas/plan desde `feature_list.json`. Si no se da un id, elige la primera feature "dependency-ready" que no esté `passing` ni `accepted`.

**Cómo está estructurada**: `SKILL.md` (reglas duras, inputs, workflow de 4 pasos y output summary) más `references/spec-template.md` (la plantilla completa del spec) y `agents/openai.yaml`. La plantilla va aparte porque es larga y solo se carga al redactar el spec.

**Flujo de funcionamiento**: 4 pasos.

1. Seleccionar la feature (id dado o la primera dependency-ready) y comprobar que tiene `id`, `title`, `user_visible_behavior`, `depends_on` y `verification`.
2. Investigar el repo: estado de la app, patrones, datos, APIs, auth, comandos de verificación/E2E y gaps.
3. Escribir el spec en `docs/specs/<feature-id>.md` con la plantilla.
4. Ejecutar el quality gate.

**Entradas y salidas**: entradas = `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, spec existente si lo hay, docs de discovery (`CONTEXT.md`, `docs/product-brief.md`, `docs/domain-model.md`, `docs/user-and-access-model.md`, `docs/technical-discovery.md`, `docs/mvp-scope.md`, `DESIGN.md`, `docs/risks-and-open-questions.md`, `docs/adr/*.md`) y los ficheros de la app relevantes. Salidas = un único `docs/specs/<feature-id>.md` (objetivo 100-250 líneas) más un resumen de salida.

**Ejemplo práctico**: (genérico, ilustrativo, no es contenido literal de la skill)

```text
Situación: feature_list.json tiene "export-notes-to-md" con dependencias ya aceptadas
feature-spec:
 1. La selecciona por ser la primera lista
 2. Investiga el repo y registra los ficheros inspeccionados
 3. Escribe docs/specs/export-notes-to-md.md con: goal, non-goals, job story,
    escenarios Given/When/Then, research, approach, ficheros esperados,
    plan de verificación y validator checklist
 4. Quality gate: un implementador puede ejecutarla sin leer el chat
 Salida: docs/specs/export-notes-to-md.md (no toca código)
```

**Reglas y límites**: una feature por ejecución; no implementa código ni modifica fuentes de la app; no genera specs de todas salvo petición explícita; crea solo `docs/specs/<feature-id>.md`; si la feature es demasiado amplia, para y recomienda dividirla; el spec es el contrato `planner → implementer → validator`; prefiere hallazgos reales del repo y di lo que no se inspeccionó; si falta `feature_list.json`, pide `harness-starter`.
