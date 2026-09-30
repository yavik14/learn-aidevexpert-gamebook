---
name: git-committer
description: Create git commits following the Conventional Commits specification. Use when the user asks to commit changes, create a commit, or when the feature flow reaches its commit-on-acceptance step.
---

# Git Committer

All commits must follow the Conventional Commits specification. Do NOT include emojis in commit messages.

## Format

`<type>(<scope>): <description>`

When a task ID is present in the branch name or provided explicitly:

`<type>(<scope>): <TASK-ID> - <description>`

## Task IDs

Prefix the description with a task ID when the branch name contains one or the user provides one explicitly. IDs come in several formats depending on the tracker, so recognize the common ones instead of assuming a single shape. For example:

- `ABC-123` — Jira, Linear, Azure Boards, and similar key-number systems.
- `#123` — GitHub, GitLab, and Bitbucket issue references.
- `AB#123` — Azure DevOps shorthand.
- Trello card identifiers or short links (e.g. `https://trello.com/c/<id>`).

Use the bare identifier in the message (for example `#123`, not the full issue URL). If something looks like an ID but its format is ambiguous, ask the user before using it, and never invent an ID.

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
- Include the task ID prefix as described in Task IDs when one applies.
- Use `!` when breaking compatibility.

## Repository rules

- Stage only the files that belong to the requested change. Inspect `git status` and the relevant diff first, and never stage unrelated or other-agent changes.
- Never commit secrets, keystores, tokens, or credentials.
- Only commit when the user explicitly asks. Do not push unless the user explicitly asks.
