# Instrucciones para agentes

Este repositorio contiene **Playbook**, una app móvil de notas de diseño de juegos
(Kotlin Multiplatform + Compose Multiplatform) que construye un GDD vivo a partir
de capturas de texto, voz e imagen.

## Leer primero
- `CONTEXT.md` — lenguaje de dominio (glosario).
- `docs/build-brief.md` — problema, objetivos y slice del MVP.
- `docs/domain-model.md` — entidades, relaciones, estados y escenarios.
- `docs/risks-and-open-questions.md` — riesgos y preguntas abiertas.

Leer docs opcionales solo cuando apliquen:
- `docs/technical-discovery.md` — al tocar stack, storage, integraciones (STT, cámara), IA o despliegue.
- `docs/adr/*.md` — cuando una decisión pueda contradecir decisiones ya aceptadas.

## Flujo de arranque

Antes de escribir código:

1. Confirmar el directorio con `pwd`.
2. Leer `PROGRESS.md` para ver el estado verificado y el siguiente paso.
3. Leer `feature_list.json` y elegir la primera feature lista sin terminar en orden de lista.
4. Leer la spec de esa feature en `docs/specs/<feature-id>.md` (ver SDD abajo).
5. Ejecutar `./init.sh`.
6. Si la verificación base falla, arreglar la base antes de añadir trabajo nuevo.

## Flujo Spec Driven Development (SDD)

Toda feature se planifica antes de implementarse. La spec es el contrato entre tres roles separados: **planner → implementer → validator**. No se implementa una feature sin spec.

1. **Planificar** con `$feature-spec`: crear `docs/specs/<feature-id>.md` (una feature por corrida). No escribe código de producto.
2. **Implementar** con `$feature-implementer`: ejecutar la spec, correr la verificación y registrar evidencia.
3. **Validar** con `$feature-validator`: validar contra la spec de forma independiente del implementador.

Reglas del SDD:

- La spec vive en `docs/specs/<feature-id>.md` e incluye: goal, non-goals, escenarios Given/When/Then, research, approach, cambios de archivos esperados, plan, tareas, verificación y checklist del validador.
- Antes de tocar código, leer la spec de la feature y mantener los cambios dentro de su alcance.
- Si la feature no tiene spec, primero planificarla; no implementar a ciegas.
- Si una feature es demasiado amplia para una spec clara (más de un modelo de datos + un flujo, o >250 líneas), dividirla en `feature_list.json` antes de seguir.
- El ciclo de estado en `feature_list.json` acompaña al SDD: `not_started` → `in_progress` → `passing` (implementada y verificada). La aceptación del validador se registra como evidencia.

## Reglas de trabajo

- Trabajar en una feature a la vez.
- No marcar una feature como completa solo porque se añadió código.
- Mantener los cambios dentro del alcance de la feature elegida salvo que un bloqueo requiera un arreglo de soporte acotado.
- No cambiar en silencio las reglas de verificación durante la implementación.
- Actualizar los artefactos durables del repo en lugar de depender de resúmenes de chat.
- Trabajar en una rama nueva siguiendo gitflow estándar (`feature/*` desde `develop`).

## Artefactos requeridos

- `feature_list.json`: fuente de verdad del estado de features.
- `PROGRESS.md`: estado verificado actual y log ligero de sesión.
- `init.sh`: ruta estándar de arranque y verificación.

## Definición de hecho

Una feature está hecha solo cuando se cumple todo:

- existe una spec en `docs/specs/<feature-id>.md` y la implementación se mantiene dentro de su alcance,
- el comportamiento objetivo está implementado,
- la verificación requerida se ejecutó de verdad,
- la evidencia quedó registrada en `feature_list.json` o `PROGRESS.md`,
- el repositorio sigue arrancable desde la ruta estándar,
- los docs relevantes se actualizaron si cambió el comportamiento de producto, las reglas de dominio, la API o la verificación.

## Fin de sesión

Antes de terminar una sesión:

1. Actualizar `PROGRESS.md`.
2. Actualizar `feature_list.json`.
3. Registrar riesgos o bloqueos no resueltos.
4. Dejar el repo limpio para que la próxima sesión pueda correr `./init.sh` de inmediato.
