---
paths:
  - "**/*.scala"
  - "**/*.java"
---

# Configuration discipline

There is exactly **one** config accessor: the Java `Platform` object
(`platform-core/platform-common/src/main/java/org/sunbird/common/Platform.java`). It loads
Typesafe Config once at startup (`ConfigFactory.load()` with a `systemEnvironment()`
fallback) and exposes typed getters.

## Standard

- **Read config only via `Platform`**: `Platform.getString/getInteger/getBoolean/getLong/
  getDouble/getStringList/getAnyRef(key, default)`. **Always pass a default.**
  Examples: `BaseController.scala:20-28` (`Platform.getLong("actor.timeoutMillisec", 30000L)`),
  `GraphService.scala:16` (`Platform.getBoolean("cloudstorage.metadata.replace_absolute_path", false)`).
- **Never call `ConfigFactory` directly in business code.** Direct construction is confined
  to `Platform` itself. New code that needs a value adds a key to `application.conf` and
  reads it through `Platform`.
- No hardcoded hosts / URLs / timeouts / thresholds / feature flags — put them in config
  with a sensible default in the `Platform` call.

Complements `service-config.md`, which covers the config *files* (`{service}/conf/application.conf`,
routes, logback). This rule is about how *code* reads them.
