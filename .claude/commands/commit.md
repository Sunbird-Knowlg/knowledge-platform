---
description: Create a Conventional Commits git commit for the current changes
argument-hint: "[optional message hint]"
allowed-tools: Bash(git status:*), Bash(git diff:*), Bash(git log:*), Bash(git add:*), Bash(git commit:*)
---

You are creating a git commit for **knowledge-platform** (Scala/Play/Maven). Extra hint from the user (may be empty): $ARGUMENTS

## Pre-loaded context

Status:
!`git status --porcelain=v1 -b`

Staged diff:
!`git diff --staged`

Recent commit style (mirror it):
!`git log --oneline -12`

## Steps

1. Analyze the changes above and determine the commit **type** and **scope**.
2. If **nothing is staged**, propose the files to stage and **ask the user before running `git add`**. Never stage secrets (`.env*`, `*.pem`, `*.key`).
3. By default **don't stage `.md` files** — ask the user before including any documentation changes.
4. Present the proposed commit message and **wait for confirmation before committing**.
5. Create the commit with `git commit`.

## Commit Message Format

```
{type}({scope}): {short description}

{optional body — only if the change needs explanation}
```

### Types
- `feat` — new feature or API
- `fix` — bug fix
- `refactor` — code restructure without behavior change
- `style` — formatting only, no logic change
- `test` — adding or fixing tests
- `chore` — build, config, dependency changes
- `docs` — documentation only

### Scope
Use the module being changed:
- `platform-core` — shared core (BaseActor, exceptions, `Platform` config, TelemetryManager, cache)
- `ontology-engine` — knowledge-graph engine (JanusGraph/Neo4j)
- `content-api` — content management service
- `search-api` — composite search service
- `assessment-api` — QuestionSet / assessment service
- `taxonomy-api` — categories & frameworks service
- `platform-modules` — import / mimetype / url managers
- `knowlg-service` — general knowledge service
- `sync-tool` — data sync tool
- `schema` — request JSON schemas in `schemas/`
- `build` — Maven / pom / dependency changes (e.g. Netty pinning)
- `ci` — GitHub Actions / CircleCI / SonarCloud workflows

Omit the scope only if the change is genuinely cross-cutting.

### Rules
- Subject line: max 72 characters, imperative mood ("add", not "added" or "adds")
- No period at the end of the subject line
- Body (if needed): explain *why*, not *what* — the diff shows what
- Reference issue numbers if relevant: `fixes #123`
- Do **NOT** add a copyright header to any file
- Do **NOT** add `Co-Authored-By: Claude` or any Claude/AI authorship trailer

## Examples

```
feat(content-api): add max-size validation for asset uploads
```

```
fix(taxonomy-api): initialize CSP fields in framework actor test constructor

The test built a Request without the cloud-storage fields, so the actor
NPE'd before the assertion. Populate them to match the runtime path.
```

```
refactor(platform-core): route config reads through the Platform accessor

Removes scattered ConfigFactory calls so all config has a single seam.
```

```
chore(build): pin Netty to match Play 3.0.5 in the play2 pom
```

```
test(search-api): add spec for composite-search filter edge cases
```

---

After analyzing the changes, present the proposed commit message to the user for confirmation before committing.
