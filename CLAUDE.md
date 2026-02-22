# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Knowledge Platform** is a graph-based content management and knowledge system built with Scala, Play Framework, and Apache Pekko actors. It provides APIs for managing content, assessments, search, taxonomy, and collections.

**Technology Stack:**
- **Language:** Scala 2.13 with Java 11 compatibility
- **Web Framework:** Play Framework 3.0.5 (Netty-based)
- **Actor Framework:** Apache Pekko 1.0.3 (formerly Akka)
- **Build Tool:** Maven 3.9+
- **Testing:** ScalaTest 3.0.8 + ScalaMock 4.4.0 + Pekko TestKit
- **Databases:** Neo4j/JanusGraph (knowledge graph), Cassandra (distributed), Redis (cache)
- **Messaging:** Kafka
- **Code Coverage:** Scoverage, JaCoCo

## Core Architecture

### Multi-Module Maven Project

```
knowledge-platform/
├── platform-core/              # Shared core utilities
│   ├── actor-core             # Base actor classes
│   ├── platform-common        # Common utilities
│   ├── cassandra-connector    # DB integration
│   ├── kafka-client           # Message broker
│   └── schema-validator       # Schema validation
├── ontology-engine/            # Knowledge graph engine
│   ├── graph-dac-api          # Data access layer
│   ├── graph-core_2.13        # Core graph operations
│   ├── graph-engine_2.13      # High-level graph API
│   └── graph-common           # Shared utilities
├── content-api/                # Content management service
│   ├── content-service        # Play2 application
│   ├── content-actors         # Content business logic
│   ├── hierarchy-manager      # Hierarchy management
│   └── content-controllers    # Play2 controllers
├── search-api/                 # Composite search service
├── assessment-api/             # Assessment/QuestionSet service
├── taxonomy-api/               # Categories/frameworks service
├── platform-modules/           # Import & MIME handling
└── knowlg-service/             # General knowledge service
```

### Request Flow Architecture

1. **Play2 Route** (conf/routes) → Maps HTTP endpoints
2. **Play2 Controller** → Validates request, creates Request object
3. **Actor** (Props.create) → Business logic execution
4. **Graph Service** → Interacts with JanusGraph/Neo4j via graph-engine
5. **Cassandra/Redis** → Persists/retrieves data

Each API service runs independently as a Netty server on Play2.

## Common Development Commands

### Building

```bash
# Build entire project (skip tests)
mvn clean install -DskipTests

# Build specific module
mvn clean install -DskipTests -pl content-api

# Build and run code coverage analysis
mvn clean install -DskipTests scoverage:report
```

### Running Services

Each service runs as a Play2 application on port 9000:

```bash
# Content Service (Content V3+V4 APIs, Collections, Assets)
cd content-api/content-service && mvn play2:run

# Search Service (Composite & Assets search)
cd search-api/search-service && mvn play2:run

# Taxonomy Service (Categories & Frameworks)
cd taxonomy-api/taxonomy-service && mvn play2:run

# Assessment Service (QuestionSets)
cd assessment-api/assessment-service && mvn play2:run

# Knowlg Service
cd knowlg-service && mvn play2:run
```

Health check:
```bash
curl http://localhost:9000/health
```

### Testing

```bash
# Run all tests
mvn test

# Run tests for specific module
mvn test -pl taxonomy-api/taxonomy-actors

# Run single test class
mvn test -pl taxonomy-api/taxonomy-actors -Dtest=ObjectCategoryActorTest

# Run single test method
mvn test -pl taxonomy-api/taxonomy-actors -Dtest=ObjectCategoryActorTest#*should*create*

# Generate coverage report
mvn clean install scoverage:report
```

Test reports are generated in `target/site/scoverage/` for each module.

### Local Development Setup

```bash
# One-step setup (creates Docker containers for all databases)
sh ./local-setup.sh

# Manual setup (if one-step fails)
mkdir -p ~/sunbird-dbs/{neo4j,cassandra,redis,es,kafka}
export sunbird_dbs_path=~/sunbird-dbs
docker compose up
```

Database connections are configured in each service's `conf/application.conf`.

## Key File Locations

### Configuration
- **Service Config:** `{service}/conf/application.conf`
- **Routes:** `{service}/conf/routes`
- **Logback:** `{service}/conf/logback.xml`

### Code Patterns

**Actor Implementation:**
```
{service}/{actors}/src/main/scala/org/sunbird/{service}/actors/*Actor.scala
```

**Tests use BaseSpec trait:**
```
{service}/{actors}/src/test/scala/org/sunbird/actors/BaseSpec.scala
```

Tests mock `OntologyEngineContext` and `GraphService` to avoid real database connections.

**Controllers (Play2):**
```
{service}/{controllers}/src/main/scala/org/sunbird/{service}/controllers/*Controller.scala
```

## Important Architectural Decisions

### Graph Database Migration (Recent)
- Recently migrated from pure Neo4j to JanusGraph backend
- JanusGraph provides better scalability and multi-backend support
- Graph queries go through graph-engine_2.13 → graph-dac-api

### Actor-Based Request Processing
- All business logic runs in Pekko actors
- Actors are created per-request via `Props` in controllers
- Enables async, non-blocking request handling
- Tests use TestKit with 10-second message timeout

### Schema Validation
- Request validation happens in platform-core/schema-validator
- Schemas defined in `schemas/` directory at repo root
- Allows different versions of APIs to coexist

### Dependency Management
- Netty versions are pinned in play2 pom.xml to match Play Framework version
- Transitive dependency conflicts (especially Netty) can cause build failures

## Development Workflow

1. **Create feature branch** from `develop`
2. **Implement changes** (usually in actors or managers)
3. **Write tests** using ScalaTest + ScalaMock pattern
4. **Run tests** in specific module: `mvn test -pl {module}`
5. **Build full project** to verify dependencies: `mvn clean install -DskipTests`
6. **PR to develop** (primary branch is `master`, develop is integration branch)

## Debugging Tips

- Enable debug logging by modifying `logback.xml`
- Service health check: `curl http://localhost:9000/health`
- Actor response timeouts: Check TestKit's 10-second limit if tests fail
- Database connection issues: Verify `application.conf` host/port settings and running containers

## Recent Changes
- Neo4j to JanusGraph migration (#1164)
- Package structure refactoring for better organization
- Assessment Service integration with knowledge platform
