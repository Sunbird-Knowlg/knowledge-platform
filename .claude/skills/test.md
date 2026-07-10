You are running tests for the Knowledge Platform — a multi-module Maven/Scala project.

## Steps

1. **Identify the target module** from:
   - The user's message (e.g., "test taxonomy actors")
   - Recently edited files in the conversation
   - Current working directory

2. **Determine the test scope** (full module, single class, or single method)

3. **Run the appropriate command** (see reference below)

4. **Report results** — pass/fail count, any failures with their error messages

5. **Point to coverage report** if the user wants to see coverage

---

## Command Reference

### Run all tests in a module
```bash
mvn test -pl {module-path}
```

### Run a specific test class
```bash
mvn test -pl {module-path} -Dtest={TestClassName}
```

### Run a specific test method (partial name match works)
```bash
mvn test -pl {module-path} -Dtest={TestClassName}#*{partial-method-name}*
```

### Generate coverage report
```bash
mvn clean install scoverage:report -pl {module-path}
# Report at: {module-path}/target/site/scoverage/index.html
```

---

## Module Path Reference

| Service | Actors Module Path | Service Module Path |
|---------|-------------------|---------------------|
| Taxonomy | `taxonomy-api/taxonomy-actors` | `taxonomy-api/taxonomy-service` |
| Content | `content-api/content-actors` | `content-api/content-service` |
| Assessment | `assessment-api/assessment-actors` | `assessment-api/assessment-service` |
| Search | `search-api/search-actors` | `search-api/search-service` |
| Knowlg | `knowlg-service` | `knowlg-service` |
| Platform Core | `platform-core` | — |
| Ontology Engine | `ontology-engine` | — |
| Platform Modules | `platform-modules` | — |

**Tip:** Tests almost always live in the `{service}-actors` module, not the service module itself.

---

## Test Patterns in This Project

- Tests extend `BaseSpec` (found in each actors module's test directory)
- `OntologyEngineContext` and `GraphService` are mocked — tests don't need a running database
- Network-dependent tests are marked `ignore should` and will be skipped automatically
- Timeout default in TestKit: 10 seconds — if tests hang, check for unhandled actor messages
- Test reports: `target/surefire-reports/`

---

If the module is ambiguous, ask the user which service they're working on before running.
