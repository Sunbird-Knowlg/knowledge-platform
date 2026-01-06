# Neo4j to JanusGraph Migration - Status Report

## Overview
This document tracks the migration from Neo4j to JanusGraph for the knowledge-platform repository.

## Completed Tasks

### Phase 1: Setup and Dependencies ✅
1. ✅ Updated Maven dependencies in `ontology-engine/graph-dac-api/pom.xml`
   - Removed Neo4j dependencies (neo4j-java-driver, neo4j-graphdb-api, neo4j-bolt, neo4j-cypher)
   - Added JanusGraph dependencies (janusgraph-core, janusgraph-cql)
   - Added Apache TinkerPop dependencies (gremlin-core, gremlin-driver)

2. ✅ Updated `docker-compose.yml`
   - Replaced Neo4j container with JanusGraph container
   - Changed ports from 7473/7474/7687 to 8182 (Gremlin Server)
   - Updated cassandra dependency reference

3. ✅ Updated `DACConfigurationConstants.java`
   - Changed route prefix from `route.bolt.` to `route.gremlin.`
   - Changed default route ID from `DEFAULT_NEO4J_BOLT_ROUTE_ID` to `DEFAULT_JANUSGRAPH_ROUTE_ID`
   - Updated connection pool constants

4. ✅ Created new `DriverUtil.java`
   - Replaced Neo4j Driver with GraphTraversalSource
   - Implemented connection pooling using Gremlin Cluster
   - Updated getRoute() for JanusGraph connection strings (localhost:8182 instead of bolt://localhost:7687)

### Phase 2: Core Utilities ✅
1. ✅ Created `GremlinQueryBuilder.java` - New utility for building Gremlin traversals
   - Vertex operations: create, read, update, delete, search
   - Edge operations: create, read, update, delete  
   - Graph traversal operations: get outgoing/incoming edges, count vertices
   - Helper methods for executing traversals

2. ✅ Created `JanusGraphNodeUtil.java` - Converter between JanusGraph Vertex/Edge and Node model
   - Convert Vertex to Node (with and without relations)
   - Extract properties from Vertex and Edge
   - Create Relation objects from edges

3. ✅ Created `JanusGraphOperations.java` - Graph-level operations
   - createRelation()
   - updateRelation()
   - deleteRelation()

4. ✅ Updated `GraphAsyncOperations.java`
   - createRelation() - uses Gremlin traversals
   - removeRelation() - uses Gremlin traversals
   - getSubGraph() - uses Gremlin path traversals

## Remaining Tasks

### Phase 2: Core Infrastructure (Incomplete)
1. ❌ Update `NodeAsyncOperations.java` (363 lines)
   - Still uses Neo4j Driver, Session, Transaction
   - Methods: addNode(), upsertNode(), updateNode(), deleteNode(), getNodeByUniqueId(), getNodeProperty()
   - Needs complete rewrite using GraphTraversalSource and Gremlin

2. ❌ Update `SearchAsyncOperations.java`
   - executeSearchQuery() - currently uses Cypher queries
   - Needs to use Gremlin traversals with filters

3. ❌ Remove or update `Neo4JBoltGraphOperations.java`
   - This file is deprecated, replaced by `JanusGraphOperations.java`
   - Should be removed or kept for reference

4. ❌ Remove or update `Neo4JBoltSearchOperations.java`
   - Currently uses Neo4j Driver
   - Should be replaced with Gremlin-based search

### Phase 3: Query Generation Utilities
These files still generate Cypher queries and need to be updated or deprecated:

1. ❌ `GraphQueryGenerationUtil.java` (844 lines)
   - Generates Cypher queries for graph operations
   - Should either be removed (use GremlinQueryBuilder directly) or converted to generate Gremlin

2. ❌ `NodeQueryGenerationUtil.java` (551 lines)
   - Generates Cypher queries for node operations
   - Should either be removed or converted

3. ❌ `SearchQueryGenerationUtil.java` (378 lines)
   - Generates Cypher queries for search operations
   - Should either be removed or converted

4. ❌ `BaseQueryGenerationUtil.java` (599 lines)
   - Base class for query generation
   - May need updates or removal

5. ❌ `Neo4jNodeUtil.java`
   - Keep for reference but should not be used
   - Replace all usages with `JanusGraphNodeUtil.java`

### Phase 4: Configuration Updates
1. ❌ Update all `application.conf` files
   - Replace `route.bolt.*` with `route.gremlin.*`
   - Update connection strings from `bolt://localhost:7687` to `localhost:8182`
   - Files to update:
     - `content-api/content-service/conf/application.conf`
     - `search-api/search-service/conf/application.conf`
     - `taxonomy-api/taxonomy-service/conf/application.conf`
     - All test configuration files

2. ❌ Update `README.md`
   - Replace Neo4j setup instructions with JanusGraph
   - Update database ports and connection information
   - Update seed data loading instructions

3. ❌ Update `KNOWLG-SETUP.md`
   - Similar updates as README.md

4. ❌ Update `local-setup.sh`
   - Replace sunbird-dbs/neo4j paths with sunbird-dbs/janusgraph
   - Update docker pull commands
   - Update environment variables

5. ❌ Update Kubernetes/Helm charts
   - `knowlg-automation/helm_charts/neo4j/` → needs JanusGraph equivalent
   - Update all references in other charts

6. ❌ Update Terraform configurations
   - `knowlg-automation/terraform/*/neo4j-provision.tf`

### Phase 5: Testing
1. ❌ Update unit tests
   - Tests currently use Neo4j embedded database or test containers
   - Need to use JanusGraph test setup

2. ❌ Run and fix integration tests

3. ❌ Manual testing
   - Build project successfully
   - Deploy JanusGraph locally
   - Test CRUD operations
   - Test search operations
   - Test relationship operations
   - Verify health checks

### Phase 6: Documentation
1. ❌ Create migration guide for users
2. ❌ Update API documentation if needed
3. ❌ Update master-data loading procedures

## Key Differences Between Neo4j and JanusGraph

| Aspect | Neo4j | JanusGraph |
|--------|-------|------------|
| Query Language | Cypher | Gremlin (Apache TinkerPop) |
| Driver | Neo4j Bolt Driver | Gremlin Driver |
| Connection | bolt://localhost:7687 | localhost:8182 (Gremlin Server) |
| Session | Session | GraphTraversalSource |
| Result | Record, StatementResult | Vertex, Edge, Path |
| Transaction | Transaction (explicit) | Automatic (or explicit with tx()) |
| Labels | Node labels | Vertex labels |
| Relationships | Relationships with types | Edges with labels |

## Build Status
- ❌ Project does not currently compile due to remaining Neo4j dependencies
- Next step: Update `NodeAsyncOperations.java` to use Gremlin

## Recommendations for Completion

1. **Priority Order:**
   1. Fix `NodeAsyncOperations.java` - This is the most critical file
   2. Fix `SearchAsyncOperations.java`
   3. Decide on query generation utilities (remove or convert)
   4. Update configuration files
   5. Update tests
   6. Update documentation

2. **Testing Strategy:**
   - Deploy JanusGraph locally first
   - Test each operation independently
   - Use integration tests to verify end-to-end flows

3. **Rollback Plan:**
   - Keep Neo4j docker setup as backup
   - Document migration steps for easy rollback
   - Consider feature flag for gradual rollout

## Estimated Effort Remaining
- Code changes: 16-24 hours
- Testing: 8-16 hours
- Documentation: 4-8 hours
- **Total: 28-48 hours**

## Notes
- JanusGraph uses Cassandra as storage backend (already in the stack)
- Gremlin is more verbose than Cypher but more flexible
- Some Cypher patterns don't have direct Gremlin equivalents
- Consider performance testing after migration
