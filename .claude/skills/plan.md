You are creating an implementation plan for a change in the Knowledge Platform — a Scala/Play2/Pekko actor-based system.

## Steps

1. **Understand the request** — If the task description is vague, ask one focused clarifying question before proceeding.

2. **Explore the codebase** — Before designing, read relevant existing files:
   - Existing actors/managers in the target service
   - The service's `conf/routes` file
   - Related `BaseSpec` test patterns
   - The relevant `pom.xml` for module placement

3. **Produce the plan** using the structure below.

4. **Confirm before implementing** — Present the plan and ask the user to approve before making any changes.

---

## Plan Structure

### Summary
One sentence: what will be built, in which service, and why.

### Files to Change
List every file that will be created or modified:
```
CREATE  {service}/{actors}/src/main/scala/.../actors/{Name}Actor.scala
MODIFY  {service}/{service-app}/conf/routes
MODIFY  {service}/{controllers}/src/.../controllers/{Name}Controller.scala
CREATE  {service}/{actors}/src/test/scala/.../actors/{Name}ActorTest.scala
```

### Layer Breakdown

**1. Route** (`conf/routes`)
```
{METHOD}  {/path}  {Controller}.{method}
```

**2. Controller** (`{Name}Controller.scala`)
- Validate request fields
- Create actor via `Props.create(classOf[{Name}Actor], ...)`
- Map actor response → HTTP result

**3. Actor** (`{Name}Actor.scala`)
- Package: `org.sunbird.{service}.actors`
- Message types (case classes):
  ```scala
  case class {Operation}Request(context: RequestContext, ...)
  ```
- Core logic or delegation to Manager

**4. Manager** (`{Name}Manager.scala`) — if needed
- Package: `org.sunbird.{service}.managers`
- Graph operations via `OntologyEngineContext`

**5. Graph Operations**
- Methods used: `getNodeAsObject`, `addNode`, `addRelation`, `search`
- Error handling: `Option`/`Either`/`Try`

### Non-Functional Requirements
| Concern | Target |
|---------|--------|
| Latency | (e.g. p99 < 500ms) |
| Throughput | (e.g. expected req/s) |
| Availability | (e.g. 99.9%) |
| Rollout safety | (zero-downtime? needs feature flag?) |

### Trade-offs
- **Why this approach**: (one line)
- **Main risk**: (what could go wrong)
- **Alternative rejected**: (what else was considered and why not)

### Test Plan
- Test class: `{Name}ActorTest.scala` extending `BaseSpec`
- Mocks: `OntologyEngineContext`, `GraphService`
- Test cases:
  - [ ] Happy path
  - [ ] Invalid input → error response
  - [ ] Graph failure → graceful error
  - [ ] Missing node → appropriate response

### Build & Verify
```bash
# Compile the module
mvn clean install -DskipTests -pl {module-path} -am

# Run tests
mvn test -pl {module-path} -Dtest={Name}ActorTest

# Check coverage
mvn clean install scoverage:report -pl {module-path}
```

### Open Questions
List any ambiguities that still need to be resolved (empty if none).

---

Produce this plan now based on the user's request. If anything is unclear, ask first.
