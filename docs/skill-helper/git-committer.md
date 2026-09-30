# Skill: git-committer

- **Skill**: `git-committer`
- **Ruta**: `.opencode/skills/git-committer/SKILL.md`

**Qué hace**: define cómo redactar los commits del repositorio siguiendo la especificación Conventional Commits, con reglas propias del proyecto (idioma, task ID, sin emojis) para que el historial sea consistente y legible.

**Cuándo se activa**: cuando el usuario pide crear un commit, y cuando el flujo de features (`feature-flow`) llega a su paso de commit al aceptar una feature.

**Cómo está estructurada**: un único `SKILL.md` sin recursos adjuntos, con las secciones `Format`, `Task IDs`, `Types`, `Additional rules` y `Repository rules`. No necesita `references/`, `scripts/` ni `assets/` porque las reglas son cortas y caben en el cuerpo.

**Flujo de funcionamiento**: inspecciona `git status` y el diff; elige el `type` de la lista y un `scope` breve; detecta si hay un task ID en la rama o provisto por el usuario; construye `<type>(<scope>): <description>` (o `<type>(<scope>): <TASK-ID> - <description>`); añade `!` si rompe compatibilidad; hace stage solo de los archivos del cambio pedido y crea el commit.

**Entradas y salidas**: recibe la intención del usuario, el estado/diff de git y el nombre de la rama (o un task ID explícito); produce commits conformes a Conventional Commits. Nunca hace push salvo que se pida explícitamente.

**Ejemplo práctico**: (genérico, ilustrativo, no es contenido literal de la skill)

```text
Petición: "haz commit de la config de detekt"
Rama: feature/ABC-123-detekt-setup
Ocurre: type=build, scope=detekt, task ID ABC-123 detectado en la rama
Commit: build(detekt): ABC-123 - añadir configuración de detekt

Otro caso (issue de GitHub):
Rama: fix/issue-#123-camera-rotation
Commit: fix(camera): #123 - corregir la rotación de imagen al capturar

Otro caso (Trello, ID provisto por el usuario):
ID: https://trello.com/c/a1b2c3d4
Commit: feature(export): a1b2c3d4 - añadir exportación a markdown
```

**Reglas y límites**: tipos permitidos `feature, fix, docs, style, refactor, performance, test, build, dependencies`; título conciso y descripción en imperativo y en español; sin emojis; prefijo de task ID cuando aplique reconociendo varios formatos (`ABC-123`, `#123`, `AB#123`, Trello) y usando el ID "pelado"; `!` para cambios que rompen compatibilidad; stage solo de lo pedido; nunca secretos, keystores, tokens ni credenciales; commit solo cuando el usuario lo pide y push solo si lo pide. No crea ni modifica skills.
