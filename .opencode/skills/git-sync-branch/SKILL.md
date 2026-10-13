---
name: git-sync-branch
description: Rebase a branch onto a stable branch (default develop), squash the branch into a single commit, and force-push it with lease. Use when the user asks to sync or rebase a branch, bring develop/main into a feature branch, squash a branch to one commit, update a PR after syncing, or "pull develop into my branch and push".
---

# Git Sync Branch

Use this skill to bring a stable branch into a target branch, collapse the target's own work into a single commit, and publish it. It is the repeatable version of "fetch, rebase onto develop, squash to one commit, force-push".

## Hard Rules

- The **target branch is mandatory**. Never guess it; if the user did not name it, stop and ask.
- The **stable branch defaults to `develop`**; the user can override it (for example `main`).
- Fetch first, then base the rebase on **`origin/<stable>`**. Do not `git pull` or otherwise check out the local stable branch: in this repository branches usually live in separate worktrees, and a local checkout of the stable branch will fail or disturb another worktree.
- Require a **clean working tree** (`git status --porcelain` empty). If not clean, stop and report.
- Always push with **`--force-with-lease`**. Never use a bare `--force`.
- On a **conflict or any error, stop**. Do not attempt to resolve conflicts automatically. Report the state and the exact recovery commands.
- Never touch other branches or worktrees, never push the stable branch, and never stage or commit unrelated changes.
- Do **not** run `./init.sh` (verification is out of scope unless the user asks for it).

## Inputs

- `target` — the branch to sync, squash and push. **Required.**
- `stable` — the branch to rebase onto. Default: `develop`.
- `remote` — default: `origin`.
- `message` — optional commit message for the squashed commit.

## Workflow

### 1. Preflight

Run these checks and stop with a clear message on the first failure:

1. `git rev-parse --is-inside-work-tree` — must be inside a git repo.
2. `git status --porcelain` — must be empty (clean tree).
3. `git show-ref --verify --quiet refs/heads/<target>` — the target must exist as a local branch.
4. `<target>` must differ from `<stable>`.
5. Confirm the current branch matches the worktree you are running in:
   - If `git symbolic-ref --short HEAD` equals `<target>`, continue.
   - Otherwise run `git worktree list`:
     - If `<target>` is checked out in another worktree, stop and tell the user to run the skill from that worktree.
     - If it is not checked out anywhere, `git checkout <target>`.

### 2. Fetch

`git fetch <remote> --prune`

Then verify `<remote>/<stable>` exists:

`git show-ref --verify --quiet refs/remotes/<remote>/<stable>` — if missing, stop.

### 3. Bring the stable branch into the target

`git rebase <remote>/<stable>`

If a conflict appears, go to **Conflict Handling** and stop.

### 4. Squash to a single commit

Compute the branch's own commits relative to the stable branch:

- `base=$(git merge-base HEAD <remote>/<stable>)`
- `n=$(git rev-list --count <remote>/<stable>..HEAD)`

Then:

- `n == 0` — the branch has no work ahead of the stable branch. Report it and do not continue to push unless the user confirms.
- `n == 1` — already a single commit. If the user provided `message`, reword with `git commit --amend -m "<message>"`; otherwise leave it unchanged.
- `n > 1` — collapse them:
  1. Capture the current message first: `git log -1 --format=%B`.
  2. `git reset --soft "$base"`
  3. `git commit -m "<message>"` — using the provided `message`, or the captured message when none was given.

Show the result: `git log --oneline -3`.

### 5. Push

`git push --force-with-lease <remote> <target>`

If the push is rejected by the lease (the remote moved), stop and report; the user must fetch and re-run.

### 6. Report

Summarize for the user, in their language, using this shape:

- `Rama estable`: `<stable>` (base `<remote>/<stable>` @ short hash)
- `Rama objetivo`: `<target>`
- `Commits squasheados`: `<n>` → 1
- `Commit nuevo`: `<short hash> <subject>`
- `Push`: resultado (`<remote> <target>`, force-with-lease)
- `Avisos`: cualquier limitación o paso pendiente

## Conflict Handling

When `git rebase` reports a conflict:

1. Stop immediately; do **not** run `reset`, `commit`, or `push`.
2. Report the conflicting paths (`git status`, `git diff --name-only --diff-filter=U`) and the current rebase step.
3. Offer the user exactly two paths:
   - Resolve manually, then `git rebase --continue` and re-run the squash + push steps of this skill.
   - Abort with `git rebase --abort` to return to the pre-rebase state.

## Edge Cases

- Dirty working tree → stop before any rewrite.
- Detached HEAD → stop; ask for an explicit branch.
- `<target>` equals `<stable>` → stop (nothing to sync).
- No commits ahead of the stable branch → report, do not force-push.
- `<remote>/<stable>` missing → stop and ask for the correct stable branch/remote.
- Target branch checked out in another worktree → stop and point the user to that worktree.
- Force-with-lease rejection → stop; the remote changed and must be re-fetched.

## Example

User: "sincroniza feature/gdd-view con develop, déjalo en un commit y súbelo".

```
target=feature/gdd-view   stable=develop   remote=origin
git status --porcelain            # vacío
git fetch origin --prune
git rebase origin/develop         # sin conflictos
n=1                               # ya es un solo commit
git push --force-with-lease origin feature/gdd-view
```

Report the resulting commit and push result.
