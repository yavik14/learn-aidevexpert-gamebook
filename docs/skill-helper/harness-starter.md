# Skill: harness-starter

- **Skill**: `harness-starter`
- **Ruta**: `.opencode/skills/harness-starter/SKILL.md`

**Qué hace**: crea el harness mínimo de arranque del repo tras el discovery —`AGENTS.md`, `init.sh`, `PROGRESS.md` y `feature_list.json`— para que un agente sepa qué es el proyecto, cómo arrancarlo, cuál es la siguiente feature y cómo verificar el trabajo.

**Cuándo se activa**: cuando el usuario dice usar harness-starter, preparar el harness inicial, crear AGENTS/init/progress/feature list, o pasar de los docs de discovery a un repo listo para agentes de código. No se usa para discovery, código de producto, inicializar frameworks, instalar dependencias ni crear docs extra.

**Cómo está estructurada**: `SKILL.md` (reglas duras, inputs, workflow de 5 pasos y nota de enseñanza) más `references/artifact-templates.md` (plantillas de `AGENTS.md`, `init.sh` pre-bootstrap y bootstrapped, `PROGRESS.md` y el schema/reglas de `feature_list.json`) y `agents/openai.yaml`. Las plantillas van aparte porque son largas y solo se cargan al generar los ficheros.

**Flujo de funcionamiento**: 5 pasos.

1. Inspeccionar el estado actual y respetar los ficheros existentes.
2. Derivar el harness mínimo con las plantillas.
3. Mantener el alcance ajustado: slices por sesión, test de rebanado, `depends_on` y máximo un `in_progress`.
4. Manejar comandos técnicos ausentes con un `init.sh` honesto y provisional.
5. Ejecutar el quality check final.

**Entradas y salidas**: entradas = `CONTEXT.md`, `docs/build-brief.md` (o `product-brief.md`), `docs/domain-model.md`, `docs/risks-and-open-questions.md` y, si existen, `docs/user-and-access-model.md`, `docs/technical-discovery.md`, `docs/mvp-scope.md`, `docs/adr/*.md`. Salidas = exactamente cuatro ficheros: `AGENTS.md`, `init.sh`, `PROGRESS.md`, `feature_list.json`.

**Ejemplo práctico**: (genérico, ilustrativo, no es contenido literal de la skill)

```text
Situación: build-brief ya generó CONTEXT.md y docs/*.md de "app de incidencias"
harness-starter:
 1. Lee los docs de discovery
 2. Crea AGENTS.md (landing corta con flujo de arranque)
 3. Crea init.sh pre-bootstrap, sin comandos inventados
 4. Crea PROGRESS.md (estado verificado + Session 001)
 5. Crea feature_list.json con ~15 slices, depends_on y estados
 Salida: los 4 ficheros; no toca código ni instala nada
```

**Reglas y límites**: no implementa código; no inicializa frameworks ni instala dependencias; no reabre discovery; crea solo los 4 ficheros (nada de `ARCHITECTURE.md`, planes, backlogs ni rúbricas); `AGENTS.md` corto y tipo router; máximo una feature `in_progress`; features verificables en una sola sesión y sin títulos-epic; no inventa comandos; `init.sh` ejecutable o avisar de `chmod +x`. Si no existen docs de discovery, se detiene y pide `build-brief`.
