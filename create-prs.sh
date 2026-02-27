#!/usr/bin/env bash
# Run this from the knowledge-platform root directory after pushing all branches.
# Requires: gh CLI authenticated (gh auth login)

BASE="develop"
REPO="Sunbird-Knowlg/knowledge-platform"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "fix/content-actor-bitwise-and-npe" \
  --title "fix: replace bitwise & with logical && in ContentActor null guards" \
  --body "$(cat <<'EOF'
## Summary
- Three null-guard conditions in `ContentActor.scala` used the bitwise `&` operator instead of the short-circuit `&&` operator.
- When `node` is `null`, the bitwise form evaluates both sides of the expression, causing a `NullPointerException` on `node.getObjectType`.
- Replaced `&` → `&&` at lines 155, 213, 231.

## Test plan
- [ ] Verify existing unit tests for ContentActor still pass
- [ ] Test content read/update flows with a null node scenario
- [ ] Run `mvn test -pl content-api/content-actors`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "fix/kafka-client-resource-leak" \
  --title "fix: add close() and JVM shutdown hook to KafkaClient" \
  --body "$(cat <<'EOF'
## Summary
- `KafkaClient` instantiated a `KafkaProducer` and `KafkaConsumer` but never closed them, causing a resource leak on JVM shutdown.
- Added a `close()` method that flushes and closes the producer, then closes the consumer.
- Registered a JVM shutdown hook in the constructor so `close()` is called automatically on graceful shutdown.

## Test plan
- [ ] Run `mvn test -pl platform-core/kafka-client`
- [ ] Verify no resource-leak warnings in application logs on shutdown

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "fix/redis-connection-pool-leak" \
  --title "fix: replace deprecated returnResource() with jedis.close() in RedisConnector" \
  --body "$(cat <<'EOF'
## Summary
- `RedisConnector` called the deprecated `JedisPool.returnResource(jedis)` API (removed in Jedis 3.x) to return connections. This can silently fail or leak connections.
- Replaced with `jedis.close()`, which is the correct Jedis 3+ idiom and returns the connection to the pool automatically.
- Added `closePool()` method and a JVM shutdown hook to cleanly close the pool on shutdown.

## Test plan
- [ ] Run `mvn test -pl platform-core/platform-cache`
- [ ] Verify Redis operations work correctly end-to-end

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "fix/cassandra-duplicate-shutdown-hook" \
  --title "fix: guard against duplicate shutdown hooks and improve CassandraConnector cleanup" \
  --body "$(cat <<'EOF'
## Summary
- `CassandraConnector.prepareSession()` registered a JVM shutdown hook on every call, leading to duplicate hooks and redundant close attempts when multiple keyspaces are initialised.
- Added a `shutdownHookRegistered` boolean guard so the hook is registered exactly once.
- Fixed `close()` to iterate sessions individually with per-session error handling and clears the session map after closing.
- Replaced `e.printStackTrace()` with `TelemetryManager.error()` for structured logging.

## Test plan
- [ ] Run `mvn test -pl platform-core/cassandra-connector`
- [ ] Verify no duplicate shutdown hook warnings in logs

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "fix/csv-manager-fileoutputstream-leak" \
  --title "fix: explicitly close FileOutputStream in CollectionCSVManager finally block" \
  --body "$(cat <<'EOF'
## Summary
- `CollectionCSVManager` created a `FileOutputStream` inline inside an `OutputStreamWriter` constructor. If `CSVPrinter` or `OutputStreamWriter` close threw an exception, the underlying `FileOutputStream` would be leaked.
- Extracted `FileOutputStream` into a named variable before the try block and added an explicit `fos.close()` in the finally block.

## Test plan
- [ ] Run `mvn test -pl content-api/collection-csv-actors`
- [ ] Verify CSV export functionality works correctly

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "fix/search-criteria-linkedlist-to-arraylist" \
  --title "fix: replace LinkedList with ArrayList in SearchCriteria" \
  --body "$(cat <<'EOF'
## Summary
- `SearchCriteria.java` used `LinkedList` for `fields` and `sortOrder`. These lists are accessed by index (O(n) for LinkedList), which is inefficient.
- Replaced all three `new LinkedList<>()` instantiations with `new ArrayList<>()` for O(1) indexed access.
- Removed the now-unused `import java.util.LinkedList`.

## Test plan
- [ ] Run `mvn test -pl ontology-engine/graph-dac-api`
- [ ] Verify search functionality returns correct results

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "fix/datanode-future-error-handling" \
  --title "fix: use Future.failed() instead of throw in DataNode recoverWith blocks" \
  --body "$(cat <<'EOF'
## Summary
- Six `recoverWith` blocks in `DataNode.scala` re-threw the cause exception with `throw e.getCause`. Throwing inside a `Future` callback breaks the monad chain — the exception propagates as an unhandled exception on the thread rather than as a failed `Future`.
- Replaced all six occurrences with `Future.failed(e.getCause)` so errors remain within the `Future` pipeline and are correctly propagated to callers.

## Test plan
- [ ] Run `mvn test -pl ontology-engine/graph-engine_2.13`
- [ ] Verify error responses for read/write/update operations are correctly surfaced

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "refactor/replace-println-with-logging" \
  --title "refactor: replace println/System.out.println with TelemetryManager logging" \
  --body "$(cat <<'EOF'
## Summary
- Replaced all raw `println` and `System.out.println` calls in business logic with structured `TelemetryManager` calls (debug/error/warn).
- Raw println output goes to stdout and is silently dropped in production; TelemetryManager routes output through the platform's logging pipeline.

Files changed:
- `ContentActor.scala` — `warn` for missing default licence
- `ChannelManager.scala` — `error` for category fetch failure
- `NodeValidator.scala` — `debug` for singular identifier lookup
- `DataSubGraph.scala` — `debug` for subgraph traversal diagnostics
- `StorageService.scala` — `error` for getUri exception
- `CollectionTOCUtil.scala` — `error` for validateDIALCodes exception
- `ObjectCategoryDefinitionActor.scala` — `debug` for fallback _all lookup

## Test plan
- [ ] Run `mvn test -pl content-api/content-actors`
- [ ] Run `mvn test -pl ontology-engine/graph-engine_2.13`
- [ ] Run `mvn test -pl taxonomy-api/taxonomy-actors`
- [ ] Verify log output appears in telemetry logs (not stdout) in a local run

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "refactor/fix-swallowed-exceptions" \
  --title "fix: stop silently swallowing exceptions in ChannelManager and CollectionTOCUtil" \
  --body "$(cat <<'EOF'
## Summary
Three exception catch blocks were silently discarding failures:

- **ChannelManager.populateDefaultersForCreation**: `System.out.println` replaced with `TelemetryManager.error`. Graceful-continue behaviour is preserved (defaults already applied via `putIfAbsent`).
- **CollectionTOCUtil.validateDIALCodes**: returning an empty list on parse failure caused all DIAL codes to appear valid even when the response could not be read. Now logs the error and re-throws a typed `ServerException`.
- **CollectionTOCUtil.searchLinkedContents**: returning an empty list on parse failure silently dropped linked-content results. Now logs the error and re-throws a typed `ServerException`.

## Test plan
- [ ] Run `mvn test -pl content-api/content-actors`
- [ ] Run `mvn test -pl content-api/collection-csv-actors`
- [ ] Verify DIAL code validation surfaces errors correctly

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "refactor/flatten-anti-pattern" \
  --title "refactor: replace .flatMap(f => f) anti-pattern with .flatten" \
  --body "$(cat <<'EOF'
## Summary
- `.flatMap(f => f)` is a verbose identity flatMap — the idiomatic Scala equivalent is `.flatten`.
- Applied mechanically across 31 files (115 occurrences). No behaviour change.

Affected modules: taxonomy-actors, FrameworkManager, FrameworkActor, CategoryInstanceActor, ObjectCategoryDefinitionActor, ContentActor, HierarchyManager, UpdateHierarchyManager, DataNode, DefinitionNode, RelationValidator, VersioningNode, VersionKeyValidator, FrameworkValidator, CopyManager, FlagManager, AcceptFlagManager, AssetCopyManager, DiscardManager, DIALManager, PublishManager, ReviewManager, UploadManager, ItemSetActor, QuestionActor, QuestionSetActor, assessment CopyManager, parseq Task, CollectionMimeTypeMgrImpl.

## Test plan
- [ ] Run `mvn test` across all modules to verify no behaviour change
- [ ] Spot-check a few changed methods to confirm semantics are identical

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "refactor/fix-package-typo-mangers" \
  --title "fix: rename package org.sunbird.mangers -> org.sunbird.managers (typo)" \
  --body "$(cat <<'EOF'
## Summary
- `FrameworkManager.scala` was placed in `org.sunbird.mangers` (typo: missing 'a') instead of `org.sunbird.managers`.
- Moved the file to the correct `managers/` directory and updated the package declaration.
- Updated all three import sites: `FrameworkActor.scala`, `CategoryActor.scala`, `FrameworkManagerTest.scala`.

## Test plan
- [ ] Run `mvn test -pl taxonomy-api/taxonomy-actors`
- [ ] Verify `FrameworkActor` and `CategoryActor` compile and tests pass

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "refactor/remove-unused-imports" \
  --title "refactor: remove redundant and duplicate import statements" \
  --body "$(cat <<'EOF'
## Summary
- **QuestionActor.scala** and **QuestionSetActor.scala**: removed duplicate `import scala.collection.JavaConverters` (without wildcard), which is entirely subsumed by the existing `import scala.collection.JavaConverters._`.
- **LockActor.scala**: removed `import scala.collection.immutable.{List, Map}` — both types are already in scope via `scala.Predef`. The explicit import was redundant and created a confusing apparent shadow.

## Test plan
- [ ] Run `mvn test -pl assessment-api/assessment-actors`
- [ ] Run `mvn test -pl taxonomy-api/taxonomy-actors`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "refactor/extract-magic-constants" \
  --title "refactor: extract magic literals to named constants" \
  --body "$(cat <<'EOF'
## Summary
Replace inline magic strings and numbers with named constants:

- **ChannelConstants**: added `COMPOSITE_SEARCH_URL_CONFIG_KEY` and `COMPOSITE_SEARCH_URL_DEFAULT`; used in all 4 call sites in `ChannelManager` instead of repeating the raw string literals.
- **DIALConstants**: added `DEFAULT_ERROR_CORRECTION_LEVEL` ("H"), `DEFAULT_PIXELS_PER_BLOCK` (2), `DEFAULT_QR_CODE_MARGIN` (3); `DIALManager.defaultConfig` references them instead of inline literals.
- **ContentConstants**: added `MAX_FILE_PATH_SIZE` (100); `ContentActor` uses it for the pre-signed URL file-path length guard and the error message.

## Test plan
- [ ] Run `mvn test -pl content-api/content-actors`
- [ ] Verify pre-signed URL validation rejects paths longer than 100 characters

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "refactor/null-safety-option" \
  --title "refactor: improve null safety using Scala Option" \
  --body "$(cat <<'EOF'
## Summary
Replace mutable null-initialised variables and explicit null checks with idiomatic `Option` usage:

- **DataSubGraph.scala**: two `var isRoot = false; if (isRoot != null && ...)` blocks replaced with `val isRoot = Option(...).exists(...)`. The guard `isRoot != null` was always true (Scala `Boolean` is never null).
- **RedisCache.scala** (`get`, `getAsync`, `getList`, `getListAsync`): changed `var data: String/List = null` to `var data: Option[...] = None`; replaced `null == data || data.isEmpty` with `data.isEmpty`; return `data.orNull` at the JVM boundary to preserve the existing API contract.
- **DIALManager.scala**: replaced explicit `null` put for missing DIAL codes with `requestMap.get(objectId).map(_.toArray[String]).orNull`, eliminating the if/else branch and the direct null literal.

## Test plan
- [ ] Run `mvn test -pl ontology-engine/graph-engine_2.13`
- [ ] Run `mvn test -pl platform-core/platform-cache`
- [ ] Run `mvn test -pl content-api/content-actors`
- [ ] Verify Redis cache get/getAsync/getList/getListAsync return correct values

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "refactor/extract-taxonomy-util" \
  --title "refactor: extract duplicate generateIdentifier and getNextSequenceIndex to TaxonomyUtil" \
  --body "$(cat <<'EOF'
## Summary
- `TermActor.scala` and `CategoryInstanceActor.scala` each contained byte-for-byte identical private methods: `generateIdentifier()` and `getIndex()`/`getCategoryIndex()`.
- Extracted both into a new `TaxonomyUtil` object in the existing `utils/taxonomy/` package alongside `RequestUtil`.
- Removed duplicate private methods and unused `Slug`/`RelationTypes` imports from both actors.

## Test plan
- [ ] Run `mvn test -pl taxonomy-api/taxonomy-actors`
- [ ] Verify Framework/Category/Term CRUD operations work end-to-end

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "refactor/lock-actor-decompose" \
  --title "refactor: decompose LockActor.create() and refresh() into focused private helpers" \
  --body "$(cat <<'EOF'
## Summary
- `LockActor.create()` was 52 lines with ~8 cyclomatic complexity; `refresh()` was 48 lines.
- Extracted five focused private helpers:
  - `validateDeviceHeader()` — X-device-Id header guard (shared across all 4 operations)
  - `lockOkResponse()` — builds lockKey/expiresAt/expiresIn response (was duplicated in create + refresh)
  - `readLockExternalProps()` — sets identifier + fetches Cassandra external props (was duplicated in create/refresh/retire)
  - `handleExistingLock()` — 3-way ownership comparison for existing lock records
  - `createNewLock()` — persists new lock to Cassandra and updates the content node
- `create()` reduced from 52 → ~20 lines; `refresh()` from 48 → ~35 lines.

## Test plan
- [ ] Run `mvn test -pl taxonomy-api/taxonomy-actors -Dtest=LockActorTest*`
- [ ] Verify lock create/refresh/retire/list flows work correctly

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr create \
  --repo "$REPO" --base "$BASE" \
  --head "refactor/framework-actor-decompose" \
  --title "refactor: decompose FrameworkActor and FrameworkManager complex methods" \
  --body "$(cat <<'EOF'
## Summary

**FrameworkActor (Phase 3.3/3.4):**
- Extract `readChannelNode()` helper shared by `create()` and `publish()`, replacing 8 lines of channel-request boilerplate at each call site.
- Extract `fetchFrameworkFromStorage()` to isolate Cassandra/Redis read logic from `read()`.
- Extract `persistFrameworkHierarchy()` from `publish()`, encapsulating SubGraph read + hierarchy compute + persistence.
- Replace all `.map(...).flatMap(f => f)` chains with direct `.flatMap { ... }` calls.

**FrameworkManager (Phase 3.6):**
- Extract `buildFilteredNodeMetadata()` covering node-null guard, JSON-property conversion, key normalisation, and schema-field filtering.
- Extract `sortedOutRelations()` for the filter+sortBy relation list.
- `getCompleteMetadata()` now delegates to these helpers; recursive calls and return types are unchanged.

## Test plan
- [ ] Run `mvn test -pl taxonomy-api/taxonomy-actors -Dtest=FrameworkActorTest*`
- [ ] Verify Framework publish/read/create flows work correctly

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

echo "All PRs created!"
