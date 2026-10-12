---
paths:
  - "**/*.scala"
  - "**/*.java"
---

# Logging & observability

Logging/telemetry goes through the Java static **`TelemetryManager`**
(`platform-core/platform-telemetry/src/main/java/org/sunbird/telemetry/logger/TelemetryManager.java`),
which emits structured telemetry events via `TelemetryGenerator` — not raw loggers.

## Standard

- Use `TelemetryManager.info(...)`, `.warn(...)`, `.error(...)`, `.audit(...)`, `.access(...)`,
  `.log(...)`. These build structured events; `error(msg, throwable)` extracts the `errCode`
  from a `MiddlewareException` automatically.
- Emit `audit` for state changes and `access` for request entry, matching existing services.

## Anti-patterns to flag (do NOT copy)

Some base/bootstrap code logs incorrectly — do not add more, and prefer `TelemetryManager`:
- `e.printStackTrace()` and `System.out.println(...)` in `BaseActor.java:65-68`.
- `println(...)` in `content-api/content-service/app/modules/ContentModule.scala:27`.

Logback config (levels, appenders) lives per service under `{service}/conf/logback.xml`
(see the `service-config.md` rule).
