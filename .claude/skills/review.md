You are performing a code review of recent changes in the Knowledge Platform repository.

## Steps

1. Run `git diff HEAD` to see all unstaged changes, and `git diff --staged` for staged changes. If the user specified a file or PR, review that instead.
2. Read each changed Scala file in full (not just the diff) to understand context.
3. For each file, produce a structured review.

## Review Output Format

```
## Code Review

### {relative/path/to/File.scala}

**Critical** (must fix before merge)
- Line X: {specific issue and why it matters}

**Warning** (should fix)
- Line X: {issue}

**Suggestion** (optional improvement)
- Line X: {suggestion}

✓ No issues  (use this if the file looks good)
```

After all files:
```
## Summary
Verdict: Approved | Needs Changes | Blocked
{One sentence explaining the verdict}
```

---

## Review Checklist

**Actor & Concurrency**
- [ ] Actor instantiated via `Props.create()` or `Props(new ...)`, not `new Actor()` directly
- [ ] No `Await.result` or `Thread.sleep` inside actors
- [ ] `ask` (?) has explicit timeout
- [ ] Messages are immutable case classes/objects
- [ ] No shared mutable state

**Functional Scala**
- [ ] `var` avoided (use `val` + transformations)
- [ ] `null` avoided (use `Option`/`Either`/`Try`)
- [ ] No unsafe `.get` on `Option`
- [ ] Pattern matching exhaustive on sealed traits
- [ ] Public methods have explicit return types

**Graph Operations**
- [ ] Uses `graph-engine_2.13` abstractions, not raw Neo4j/JanusGraph
- [ ] `OntologyEngineContext` not stored as actor state
- [ ] Graph futures composed with `map`/`flatMap`, not `Await`

**Error Handling**
- [ ] No swallowed exceptions (`catch { case _ => }`)
- [ ] Errors logged with context (requestId, nodeId)
- [ ] User-facing responses don't expose stack traces

**Tests (if test files changed)**
- [ ] Extends `BaseSpec`
- [ ] `OntologyEngineContext` and `GraphService` mocked
- [ ] Happy path + error paths both tested
- [ ] Network-dependent tests use `ignore should`

**Dependencies & Build**
- [ ] No new Netty transitive dependency without pinning in pom.xml
- [ ] No snapshot dependencies added

---

Be direct and line-specific. Skip sections that don't apply (e.g., no actor checks for a utility class). If a file is clean, say so with ✓ and move on.
