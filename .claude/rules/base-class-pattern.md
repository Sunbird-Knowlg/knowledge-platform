---
paths:
  - "**/actors/**"
  - "**/controllers/**"
  - "**/*Actor.scala"
  - "**/*Controller.scala"
---

# Base-class pattern (actors & controllers)

## Actors

- A new actor **extends the Java `BaseActor`**
  (`platform-core/actor-core/src/main/java/org/sunbird/actor/core/BaseActor.java`) and
  implements the single method `onReceive(request: Request): Future[Response]`. `BaseActor`
  wires the Pekko `Receive` and centralizes error recovery (see `error-handling.md`) — do
  not override `createReceive` or hand-roll recovery.
- Body follows the **operation-match dispatch** idiom:
  `request.getOperation match { case "createX" => …; case "readX" => … }` delegating to
  private methods. Reference: `taxonomy-api/taxonomy-actors/src/main/scala/org/sunbird/actors/FrameworkActor.scala:24-38`.
- Actors take `implicit oec: OntologyEngineContext` (the DI seam — see `interfaces.md`).
- **Actors are Guice singletons, not per-request instances.** Bind in the service
  `*Module.scala` with `bindActor[MyActor](ActorNames.MY_ACTOR)` (e.g.
  `content-api/content-service/app/modules/ContentModule.scala:15-25`), and inject into a
  controller by name: `@Inject()(@Named(ActorNames.MY_ACTOR) actor: ActorRef, …)`. The
  per-request semantics come from the Pekko **ask** pattern, not from creating an actor per
  request.

## Controllers

- A new controller **extends its service's `BaseController`** (each service has its own
  copy, e.g. `content-api/content-controllers/.../content/controllers/BaseController.scala`,
  and `taxonomy`/`assessment`/`search` equivalents). Reuse the inherited `requestBody()`,
  `getRequest(...)`, `getResult(...)` helpers.
- Invoke the actor via `Patterns.ask(actor, request, actorTimeout)` and map
  `Response.getResponseCode` to a Play `Result` (`BaseController.scala:86,99-105`).

> Note: `scala-conventions.md` previously described actors as "created per-request via
> `Props.create`" — that is inaccurate; the pattern is Guice-singleton + ask.
