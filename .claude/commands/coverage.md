---
description: Generate the Scoverage coverage report and summarize it
allowed-tools: Bash(mvn *)
---

Generate test coverage for **knowledge-platform**:

```
mvn clean install scoverage:report
```

Per-module HTML/XML reports land under each module's `target/site/scoverage/`.

After it finishes, report the **per-module line-coverage %** (read the `scoverage.xml` summaries) and call out any module notably below the others. Note that **SonarCloud** (`.github/workflows/sonarcloud.yml`) is the authoritative quality gate in CI — this local report is for a quick check.

## Examples

```
/coverage    # build with coverage, then summarize per-module line %
```
