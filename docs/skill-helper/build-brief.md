# Skill: build-brief

- **Skill**: `build-brief`
- **Ruta**: `.opencode/skills/build-brief/SKILL.md`

**Qué hace**: convierte un proyecto, feature o cambio de producto aún difuso en un conjunto pequeño de documentos de descubrimiento durables antes de escribir código: lenguaje común, usuarios, flujos, restricciones, alcance del MVP y preguntas abiertas.

**Cuándo se activa**: cuando el usuario tiene una idea de producto/app/herramienta, una dirección de feature vaga, un reemplazo de un sistema existente o un proof-of-concept por definir. No se usa para implementar, programar ni crear el harness del repo.

**Cómo está estructurada**: `SKILL.md` (reglas duras, "controlled outputs", estilo de interacción y un workflow de 10 pasos) más tres referencias: `references/output-documents.md` (plantillas de cada documento), `references/question-patterns.md` (preguntas por fase) y `references/teaching-notes.md` (modo curso/taller). También `agents/openai.yaml` (runtime Codex). La separación mantiene el cuerpo corto y carga cada referencia solo cuando hace falta.

**Flujo de funcionamiento**: sigue 10 pasos.

1. Establecer el marco de descubrimiento y preguntar el idioma de los documentos.
2. Construir lenguaje compartido → `CONTEXT.md`.
3. Descubrir usuarios y acceso.
4. Descubrir flujos y estados del dominio.
5. Descubrir tecnología y restricciones.
6. Definir alcance del MVP con non-goals.
7. Descubrir dirección de diseño si hay UI (`DESIGN.md`, opcionalmente conceptos generados).
8. Registrar decisiones con ADR solo si son difíciles de revertir.
9. Ejecutar el quality gate del discovery.
10. Stop condition y preguntar si pasar a la siguiente fase.

Pregunta **de una en una**, con opción recomendada.

**Entradas y salidas**: entradas = la idea del usuario, docs/repo existentes y las respuestas de la entrevista. Salidas = set compacto (`CONTEXT.md`, `docs/build-brief.md`, `docs/domain-model.md`, `docs/risks-and-open-questions.md`) y, solo si aportan, `docs/user-and-access-model.md`, `docs/technical-discovery.md`, `docs/mvp-scope.md`, `DESIGN.md`, `docs/design/concepts/*.png` y `docs/adr/*.md`. No crea `AGENTS.md`, `PROGRESS.md` ni `feature_list.json` (eso es de `harness-starter`).

**Ejemplo práctico**: (genérico, ilustrativo, no es contenido literal de la skill)

```text
Usuario: "quiero una app para que mi equipo registre incidencias del taller"
build-brief:
 1. Pregunta idioma de los docs -> "español"
 2. Pregunta qué reemplaza -> "un cuaderno de papel y un grupo de WhatsApp"
 3. Afina lenguaje: ¿"incidencia" y "tarea" son lo mismo? -> se fija "incidencia"
 4. Usuarios/roles: operario y supervisor
 5. MVP: registrar incidencia + listado; non-goal: informes y notificaciones
 6. UI sencilla -> no crea DESIGN.md todavía
 Salida: CONTEXT.md, docs/build-brief.md, docs/domain-model.md,
         docs/risks-and-open-questions.md
 Pregunta final: "¿pasamos a harness-starter?"
```

**Reglas y límites**: no implementa código; no crea `AGENTS.md`/`PROGRESS.md`/`feature_list.json` ni planes hasta que el usuario lo pida tras cerrar el discovery; una pregunta a la vez con recomendación; pregunta siempre el idioma antes de escribir docs; crea solo el set compacto (los opcionales solo si reducen confusión); ADR solo si la decisión es difícil de revertir; no avanza de fase por su cuenta. Es un flujo de enseñanza: explica por qué existe cada artefacto.
