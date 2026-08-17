---
description: Run tests, optionally scoped to a module and/or test class; summarize failures
argument-hint: "[module] [TestClass]"
allowed-tools: Bash(mvn *)
---

Run tests for **knowledge-platform**. Args — module: `$1`, test class: `$2` (either may be empty).

Choose the command:
- no args → `mvn test`
- module only → `mvn test -pl $1`
- module + class → `mvn test -pl $1 -Dtest=$2`
  (single method: `mvn test -pl $1 -Dtest='$2#*pattern*'`)

Stack is ScalaTest + ScalaMock + Pekko TestKit (tests mock `OntologyEngineContext`/`GraphService`; TestKit uses a 10s timeout — see `.claude/rules/testing.md`).

After running, summarize: total run / passed / failed. For any failure, surface the **test name** and the first assertion/exception line. If tests hang, note the TestKit timeout as the likely cause.

## Examples

```
/test                                         # all tests
/test taxonomy-api                            # one module
/test taxonomy-api ObjectCategoryActorTest    # one test class
```
