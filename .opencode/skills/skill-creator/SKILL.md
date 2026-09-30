---
name: skill-creator
description: Create new opencode skills and iteratively improve existing ones. Use when the user wants to turn a workflow or repeated process into a skill, create a SKILL.md from scratch, edit or optimize an existing skill under .opencode/skills, write a skill's name/description/body, or add bundled references, scripts, or assets. Make sure to use this skill whenever the user mentions "skill", "SKILL.md", creating or improving an agent skill, or capturing a workflow for reuse, even if they do not say "skill-creator".
---

# Skill Creator (opencode)

A skill packages instructions and resources so a future agent can do a specialized task without rediscovering it. This skill helps you design, write, test, and improve skills for opencode.

High-level loop:

1. Understand what the skill should do and when it should trigger.
2. Draft `SKILL.md`.
3. Try it on a few realistic prompts, with and without the skill.
4. Review the results with the user.
5. Improve the skill and repeat until it holds up.
6. Add bundled scripts/references when the same work keeps recurring.

Stay flexible: if the user says "I don't need to run tests, just help me write it", skip the test loop and draft with them.

## Hard rules (opencode)

- Skills are files at `.opencode/skills/<name>/SKILL.md` (project) or `~/.config/opencode/skill(s)/<name>/SKILL.md` (global). The folder name must match the frontmatter `name`.
- Frontmatter requires `name` and `description`. `name` is lowercase, hyphen-separated, <= 64 chars. The `description` is the trigger: cover both what the skill does and when to use it, in third person, front-loading the literal keywords the user will say.
- Skills are invoked through the `skill` tool by `name`, not with `$name`. Do not write `$skill` in opencode skills.
- Do not put "when to use" only in the body; the model sees only the `description` before deciding to load the skill.
- After creating or editing a skill, tell the user to restart opencode, because config (including skills) is loaded at startup.
- Read `references/opencode-skill-format.md` for the exact frontmatter fields, locations, and sibling files.

## Capture intent

Figure out where the user is and meet them there. The conversation may already contain the workflow to capture — extract tools used, step order, corrections, and input/output formats from it.

Clarify:

1. What should this skill enable the agent to do?
2. When should it trigger? Which user phrases or contexts?
3. What is the expected output format?
4. Does it need test cases? Objectively verifiable outputs (file transforms, data extraction, code generation, fixed workflows) benefit from tests; subjective outputs (writing, design) are better judged by the user.

Ask about edge cases, example inputs, and dependencies before drafting. Come prepared: inspect existing skills in `.opencode/skills/` for house style and reuse.

## Anatomy and progressive disclosure

```
skill-name/
├── SKILL.md          (required: frontmatter + instructions)
├── references/       (optional: docs loaded only when needed)
├── scripts/          (optional: deterministic, repeatable code)
└── assets/           (optional: templates or files used in output)
```

Use three levels:

1. Metadata (`name` + `description`) — always in context.
2. `SKILL.md` body — loaded on trigger; keep it under ~500 lines.
3. Bundled resources — read only when the skill points to them.

When a skill covers multiple variants, split into `references/<variant>.md` and select in the body. For reference files over ~300 lines, add a table of contents. Prefer bundling a script when test runs show the agent repeatedly writing the same helper.

## Write the SKILL.md

- `name`: identifier matching the folder.
- `description`: what it does and when to trigger, written in third person. Slightly "pushy" helps avoid under-triggering, but stay truthful.
- Body: the workflow, rules, and examples. Add a short "Hard rules" section for non-negotiables, then the steps.

Writing style:

- Use the imperative form.
- Explain why a step matters instead of piling on MUST/NEVER; a clear reason generalizes better than a rigid order.
- Keep it general, not overfit to one example.
- Define exact output templates when the result must be consistent.
- Include short input/output examples when they clarify.

Principle of lack of surprise: a skill must not contain malware, exploit code, or anything that would surprise the user given its description. Do not help create misleading or abusive skills.

## Test the skill

Draft 2-3 realistic prompts a real user would type. Share them: "Here are a few cases I'd like to try — do these look right, or do you want to add more?"

Run each prompt two ways, launched together in the same message so they finish around the same time:

- **With skill**: a `task` subagent (`subagent_type: general`) told to read the skill at its path and then follow it.
- **Baseline**: the same prompt without the skill (new skill) or against a snapshot of the previous version (improving a skill).

Give each subagent the task prompt, any input files, and a directory to save outputs. Ask each to report what it did and any friction it hit reading the skill.

For quick checks, you may instead read the skill and execute the prompt inline — but independent subagents give more honest signal because they have no extra context about how the skill was written.

There is no benchmark viewer or scoring CLI here. Compare outputs yourself and, when useful, save them to a workspace folder and point the user to the files.

## Review and improve

Ask the user what worked and what missed. Useful improvements:

- **Generalize from feedback.** The skill must work for many future prompts, not just today's examples. Avoid fiddly overfit changes and heavy-handed constraints.
- **Keep it lean.** Read the runs for steps that wasted time or confused the agent and cut the parts causing it.
- **Explain the why.** If feedback is terse, work out the real intent and encode the reasoning, not just the rule.
- **Bundle repeated work.** If each run wrote a similar script, move it into `scripts/` and have the skill call it.

Re-run the affected cases after each change and repeat until the user is satisfied or feedback stops revealing real problems.

## Improve the description

The `description` decides triggering. Without a CLI optimizer, refine it by hand:

1. Write ~10 should-trigger and ~8 should-not-trigger queries a real user would type. Make negatives genuinely tricky (adjacent tasks that share keywords), not obviously unrelated.
2. Check each against the description: would it fire correctly? Note near-misses.
3. Tighten wording, front-load the strongest trigger keywords, and re-check.
4. Show the user the before/after and the reasoning.

Keep in mind opencode consults skills mainly for tasks it cannot handle trivially, so test queries should be substantive, multi-step requests rather than one-liners.

## Validate before finishing

- Frontmatter parses and has `name` + `description`.
- `name` matches the folder and its format.
- Every `references/`, `scripts/`, or `assets/` path mentioned in the body exists.
- The body states the workflow and does not rely on this conversation.
- The description covers both what and when.
- You told the user to restart opencode.

## Companion files

When it fits the repo conventions, a skill can be exposed through a slash command at `.opencode/command/<name>.md` (body loads the skill) and, for Codex runtimes, an `agents/openai.yaml`. See `references/opencode-skill-format.md`.
