You are creating a git commit for changes in the Knowledge Platform repository. Follow these steps:

## Steps

1. Run `git status` to see what's changed
2. Run `git diff --staged` to see staged changes (if nothing staged, run `git diff HEAD` to see all changes)
3. Run `git log --oneline -10` to understand the recent commit message style
4. Analyze the changes and determine the commit type and scope
5. Stage appropriate files if nothing is staged yet (ask the user before staging if unsure)
6. Create the commit

## Commit Message Format

```
{type}({scope}): {short description}

{optional body — only if the change needs explanation}
```

### Types
- `feat` — new feature or API endpoint
- `fix` — bug fix
- `refactor` — code restructure without behavior change
- `test` — adding or fixing tests
- `chore` — build, config, dependency changes
- `docs` — documentation only

### Scope
Use the module or service name:
- `content-api`, `taxonomy-api`, `assessment-api`, `search-api`, `knowlg-service`
- `ontology-engine`, `platform-core`, `platform-modules`
- `graph-engine`, `graph-dac`, `graph-core`
- `schema` — for schema file changes
- `build` — for pom.xml / build config changes

### Rules
- Subject line: max 72 characters, imperative mood ("add", not "added" or "adds")
- No period at the end of the subject line
- Body (if needed): explain *why*, not *what* — the diff already shows what
- Reference issue numbers if relevant: `fixes #123`

## Examples

```
feat(taxonomy-api): add ObjectRelationActor for managing graph relationships

Implements CRUD operations for object relationships via Pekko actor pattern.
Mocks OntologyEngineContext in tests to avoid DB dependency.
```

```
fix(content-api): resolve Netty version conflict with Play Framework 3.0.5

Pins netty-codec-http to 4.1.94.Final in content-service pom.xml to match
Play's required version and prevent NoSuchMethodError at startup.
```

```
test(assessment-api): add unit tests for QuestionSetActor error paths
```

---

After analyzing the changes, present the proposed commit message to the user for confirmation before committing.
