# opencode Skill Format

Reference for creating skills that opencode loads correctly.

## File layout

A skill lives in its own folder named after the skill, with a `SKILL.md` inside:

```
.opencode/skills/<name>/SKILL.md
```

opencode scans `**/SKILL.md` inside skill directories. The file must be named `SKILL.md`.

| Scope | Path |
| --- | --- |
| Project skills | `.opencode/skill(s)/<name>/SKILL.md` |
| Global skills | `~/.config/opencode/skill(s)/<name>/SKILL.md` |
| External (auto-loaded) | `~/.claude/skills/<name>/SKILL.md`, `~/.agents/skills/<name>/SKILL.md` |

Skills outside the default locations are registered with `skills.paths` (scanned recursively) and `skills.urls` in `opencode.json`.

## Frontmatter

```markdown
---
name: my-skill
description: One sentence covering what the skill does AND when to trigger it. Front-load the literal keywords the user is likely to say.
---

# My Skill

(skill body in markdown: instructions, examples, references)
```

- `name` (required): lowercase, hyphen-separated, up to 64 chars, matching the folder name.
- `description` (effectively required): skills without one are filtered out and never surfaced to the model. Cover _what_ it does and _when_ to use it. Write in third person ("Use when...", not "I help with..."). Front-load concrete trigger keywords; gate with "Use ONLY when..." if the skill should stay quiet on adjacent topics.
- Optional: `license`, `compatibility`, `metadata` (string-string map).

## Invocation

- The model loads a skill through the `skill` tool by its `name`.
- Users can also reach it through a slash command if you add `.opencode/command/<name>.md`.
- There is no `$name` syntax in opencode; that is a Codex/Claude convention.

## Loading and restart

Config, including skills, is loaded when opencode starts and is not hot-reloaded. After creating or editing a skill, agent, command, or config file, tell the user to quit and restart opencode.

## Bundled resources

- `references/`: documentation the body points to for deeper detail. Keep each file focused and add a table of contents over ~300 lines.
- `scripts/`: deterministic, repeatable code the skill can execute. Prefer a script over re-implementing the same logic every run.
- `assets/`: files used in output (templates, fonts, images).

Reference them from `SKILL.md` with clear instructions on when to read or run them.

## Companion files

### Slash command

```
.opencode/command/<name>.md
```

```markdown
---
description: One sentence describing what the command does.
---

Load the `<name>` skill with the skill tool and follow it exactly.

$ARGUMENTS
```

`$ARGUMENTS` is replaced with the user's input; `$1`, `$2`, ... pull positional arguments.

### Subagent wrapper

To run a skill as an isolated role, add an agent at `.opencode/agent/<name>.md`:

```markdown
---
description: When to use this agent.
mode: subagent
---

Load the `<name>` skill with the skill tool and follow it exactly.
```

### Codex runtime (optional)

If the same skills are used from a Codex/OpenAI runtime, add `agents/openai.yaml`:

```yaml
interface:
  display_name: "My Skill"
  short_description: "Short one-line description"
  default_prompt: "Use $my-skill to do the thing."
```

## Validation checklist

- `SKILL.md` exists at `.opencode/skills/<name>/SKILL.md`.
- Frontmatter has `name` and `description`.
- `name` matches the folder and format (lowercase, hyphens, <= 64 chars).
- Description states what the skill does and when to trigger it.
- Body is self-contained and under ~500 lines unless it points to references.
- All referenced files exist.
