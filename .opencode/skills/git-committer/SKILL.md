---
name: git-committer
description: Create git commits following the Conventional Commits specification. Use when the user asks to commit changes, create a commit, or when the feature flow reaches its commit-on-acceptance step.
---

# Git Committer

All commits must follow the Conventional Commits specification. Do NOT include emojis in commit messages.

## Format

`<type>(<scope>): <description>`

Or, when a task ID is present in the branch name or explicitly provided:

`<type>(<scope>): <TASK-ID> - <description>`

## Types

- `feature`: Features
- `fix`: Bug Fixes
- `docs`: Documentation
- `style`: Styles and Formatting
- `refactor`: Code Refactoring
- `performance`: Performance Improvements
- `test`: Testing
- `build`: Build System
- `dependencies`: Dependencies and SDKs

## Additional rules

- Keep the title concise.
- Imperative description in Spanish.
- Do NOT include emojis in commit messages.
- If the current branch name contains a task ID (e.g., `MID-88` in `feature/MID-88-detekt-setup`) or the task ID is explicitly provided, include `<TASK-ID> - ` as a prefix in the description (e.g., `build(detekt): MID-88 - <description>`).
- Use `!` when breaking compatibility.

## Repository rules

- Stage only the files that belong to the requested change. Inspect `git status` and the relevant diff first, and never stage unrelated or other-agent changes.
- Never commit secrets, keystores, tokens, or credentials.
- Only commit when the user explicitly asks. Do not push unless the user explicitly asks.
