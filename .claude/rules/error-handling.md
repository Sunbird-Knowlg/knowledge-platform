---
paths:
  - "**/*.scala"
  - "**/*.java"
---

# Error handling

The exception hierarchy and response translation live in **Java `platform-core`** — reuse
them, don't invent per-service error types.

## Standard

- **Throw the typed hierarchy**, never bare `RuntimeException`/strings. Base is
  `MiddlewareException` (`platform-core/platform-common/src/main/java/org/sunbird/common/exception/MiddlewareException.java`),
  carrying `errCode` + messages. Subtypes:
  - `ClientException` — bad input / 4xx (`throw new ClientException("ERR_INVALID_DATA", "…")`)
  - `ResourceNotFoundException` — missing entity
  - `ServerException` — internal / 5xx
- **Every exception has an `(errCode, message)`.** Error codes are defined in
  `ErrorCodes.java` / `ResponseCode.java` (and `GraphEngineErrorCodes.java` for the graph layer).
- **Don't build error responses by hand.** Use `ResponseHandler`
  (`platform-common/.../dto/ResponseHandler.java`): `ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, errCode, msg)`
  or `ResponseHandler.getErrorResponse(e)`. See `BaseController.scala:204`, `:86`.
- **Don't re-implement recovery in each actor.** `BaseActor` centrally recovers every
  failure — `getErrorResponse`/`setResponseCode` in
  `actor-core/src/main/java/org/sunbird/actor/core/BaseActor.java` maps the exception
  subtype to a `ResponseCode` (ClientException→CLIENT_ERROR, ResourceNotFoundException→
  RESOURCE_NOT_FOUND, ServerException/else→SERVER_ERROR) via `recoverWith`. Just throw the
  right subtype from `onReceive` and let the base handle the envelope.

## Refs
`BaseController.scala:60` (throw), `:86` (ask + recover), `:204` (ResponseHandler.ERROR).
