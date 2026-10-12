---
description: Build knowledge-platform (skip tests), optionally a single module
argument-hint: "[module]"
allowed-tools: Bash(mvn *)
---

Build **knowledge-platform**. Module argument (may be empty): `$1`

- No module given → full build: `mvn clean install -DskipTests`
- Module given → build it and its dependencies: `mvn clean install -DskipTests -pl $1 -am`
  (valid modules: `platform-core`, `ontology-engine`, `content-api`, `search-api`, `assessment-api`, `taxonomy-api`, `platform-modules`, `knowlg-service`, `sync-tool`)

Run the appropriate command. On success, report it briefly. On failure, **name the failing module** and quote the first relevant error block (a compile/dependency error) — do not paste the whole reactor log. Common cause: transitive Netty conflicts (Netty is pinned in the play2 `pom.xml`).

## Examples

```
/build                 # full build, skip tests
/build content-api     # build content-api and its dependencies (-pl content-api -am)
```
