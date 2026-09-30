---
name: skill-helper
description: Explain how a given opencode skill works, in plain language and with a practical example. Use when the user asks to explain, describe, document, or walk through an existing skill (for example "explain the git-committer skill", "¿cómo funciona feature-flow?", "what does this SKILL.md do?"), or asks for a readable summary of a skill's purpose, trigger, structure, and flow. Do not use to create or edit skills; use skill-creator for that.
---

# Skill Helper

Explain one existing skill so the user understands what it does, when it fires, how it is built, and how it behaves in practice. This is a reading and teaching task, not an authoring task.

## Hard rules

- Explain skills; do not create or modify them. If the user wants to make or change a skill, hand off to the `skill-creator` skill.
- Read the target skill before explaining it. Never guess its contents, rules, or structure.
- Never edit the target skill or its resources during this task.
- Write the explanation in the user's language. Default to Spanish when the request is in Spanish.
- Answer in the chat by default. Only write a document when the user asks to document it.
- The practical example is illustrative and generic. Label it clearly so it is not mistaken for real skill content.

## Workflow

### 1. Identify the target skill

- If the user named a skill, locate it: `.opencode/skills/<name>/SKILL.md` (project) first, then global `~/.config/opencode/skill(s)/<name>/SKILL.md`, then external `~/.claude/skills/<name>/SKILL.md`.
- If the user did not name one, ask which skill, or list the skills available under `.opencode/skills/`.
- If the name does not resolve to a file, say so and show the closest matches; do not invent a skill.

### 2. Read it

Read the `SKILL.md` fully. Then read the resources it points to (`references/`, `scripts/`, `assets/`, sibling `agents/openai.yaml`) only as needed to explain the behavior accurately. Note the frontmatter `name` and `description`, since the description is what triggers the skill.

### 3. Explain it in the chat

Follow this structure so explanations stay consistent and comparable. Render each label in bold, followed immediately by a colon and then the explanation on the same line, like **Qué hace**: <explanation>:

- **Qué hace**: the purpose in one or two sentences.
- **Cuándo se activa**: the trigger conditions implied by the `description` and any "use when" wording.
- **Cómo está estructurada**: `SKILL.md` plus any bundled resources, and why they are separate.
- **Flujo de funcionamiento**: the step-by-step behavior the skill tells an agent to follow.
- **Entradas y salidas**: what the skill consumes and what it produces.
- **Ejemplo práctico**: a short, generic input→output walkthrough (see below).
- **Reglas y límites**: hard rules, boundaries, and what the skill explicitly does not do.

### 4. Add a generic practical example

Invent a small, plausible example that shows the skill in action. Keep it clearly generic and mark it as illustrative. A good example shows: a user request → what the skill loads or does → the resulting output. Do not present the example as real content of the target skill.

Worked illustration of the expected style (for a made-up skill `csv-summarizer`):

```text
Request: "resume este gastos.csv"
What happens: the skill loads, reads the CSV headers, and decides which
columns are numeric.
Output:
  - Filas: 128
  - Total gastos: 3.412,50 €
  - Categoría con más gasto: Vivienda (1.240,00 €)
```

### 5. Offer to document it

After explaining in the chat, ask whether the user wants it saved. If the user asked for documentation up front (or says yes), write it to:

```
docs/skill-helper/<skill-name>.md
```

Use the same section structure as the chat explanation, with a short header naming the explained skill and its path. Create the `docs/skill-helper/` directory if it does not exist. If it is unclear whether the user wants a file, ask before writing.

## Notes

- Prefer concrete, accurate statements taken from the skill over paraphrases that add assumptions.
- If the skill has been updated in the conversation, re-read the file before explaining it.
- Keep the explanation readable: short paragraphs and bullets, no unnecessary jargon.
