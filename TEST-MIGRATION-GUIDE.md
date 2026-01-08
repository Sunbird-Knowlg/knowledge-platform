# Test Migration Guide - Neo4j to JanusGraph

**Date**: January 7, 2026  
**Status**: Ready for Execution

---

## Overview

This guide provides step-by-step instructions for migrating 20+ Neo4j test suites to validate the JanusGraph implementation.

---

## Test Suites to Migrate

### Priority 1: Core Operations (6 Suites)

1. **Neo4JBoltGraphOperationsTest.java** (12+ tests)
   - Location: `.neo4j-backup/tests/`
   - Focus: Create/Update/Delete relations
   - Replacement: `JanusGraphOperationsTest.java`

2. **Neo4JBoltSearchOperationsTest.java** (8+ tests)
   - Location: `.neo4j-backup/tests/`
   - Focus: Node queries, property searches
   - Replacement: `SearchAsyncOperationsTest.java`

3. **Neo4JBoltCollectionTest.java** (6+ tests)
   - Location: `.neo4j-backup/tests/`
   - Focus: Collection creation/deletion, sequencing
   - Replacement: `JanusGraphCollectionOperationsTest.java`

4. **Neo4JSchemaTest.java** (4+ tests)
   - Location: `.neo4j-backup/tests/`
   - Focus: Index/constraint creation
   - Replacement: `JanusGraphSchemaManagerTest.java`

5. **GraphAsyncOperationsTest.java** (10+ tests)
   - Location: Existing tests to update
   - Focus: Async relation operations
   - Update: Validate new bulk methods

6. **SubGraphTest.java** (5+ tests)
   - Location: Existing tests to update
   - Focus: Depth traversal
   - Update: Validate JanusGraph traversal

### Priority 2: Integration Tests (4 Suites)

7. **ContentServiceIntegrationTest.java**
   - Validate content CRUD with JanusGraph
   
8. **TaxonomyServiceIntegrationTest.java**
   - Validate taxonomy operations

9. **SearchServiceIntegrationTest.java**
   - Validate search functionality

10. **CollectionHierarchyTest.java**
    - Validate nested collections

### Priority 3: Performance Tests (3 Suites)

11. **BatchOperationsPerformanceTest.java**
    - Benchmark P.within() vs sequential queries
    
12. **ConnectionPoolingTest.java**
    - Validate connection management
    
13. **LargeGraphTest.java**
    - Test with 100k+ nodes

---

## Test Migration Template

### Pattern 1: Simple Operation Test

**Before (Neo4j)**:
```java
@Test
public void testCreateRelation() {
    // Setup
    String graphId = "domain";
    String startNodeId = "do_123";
    String endNodeId = "do_456";
    String relationType = "hasContent";
    
    // Execute Cypher
    String cypher = GraphQueryGenerationUtil.generateCreateRelationCypherQuery(params);
    Neo4JBoltGraphOperations.executeQuery(cypher);
    
    // Verify
    String verifyCypher = "MATCH (a)-[r:hasContent]->(b) WHERE a.IL_UNIQUE_ID = {startId} RETURN r";
    Result result = Neo4JBoltGraphOperations.executeQuery(verifyCypher);
    assertNotNull(result);
}
```

**After (JanusGraph)**:
```java
@Test
public void testCreateRelation() {
    // Setup
    String graphId = "domain";
    String startNodeId = "do_123";
    String endNodeId = "do_456";
    String relationType = "hasContent";
    Map<String, Object> metadata = new HashMap<>();
    
    // Execute Gremlin
    JanusGraphOperations.createRelation(graphId, startNodeId, endNodeId, 
        relationType, metadata, null);
    
    // Verify using Gremlin traversal
    GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.READ);
    Edge edge = g.V().has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
        .outE(relationType)
        .where(inV().has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId))
        .tryNext()
        .orElse(null);
    
    assertNotNull(edge);
    assertEquals(relationType, edge.label());
}
```

### Pattern 2: Batch Operation Test

**Before (Neo4j)**:
```java
@Test
public void testBatchCreateRelations() {
    List<String> endNodeIds = Arrays.asList("do_1", "do_2", "do_3");
    
    // Execute with UNWIND
    String cypher = "MATCH (a {IL_UNIQUE_ID: {startId}}) " +
                   "UNWIND {endIds} AS endId " +
                   "MATCH (b {IL_UNIQUE_ID: endId}) " +
                   "MERGE (a)-[r:hasChild]->(b)";
    Neo4JBoltGraphOperations.executeQuery(cypher, params);
    
    // Verify count
    assertEquals(3, countRelations(startNodeId));
}
```

**After (JanusGraph)**:
```java
@Test
public void testBatchCreateRelations() {
    String startNodeId = "do_course";
    List<String> endNodeIds = Arrays.asList("do_1", "do_2", "do_3");
    
    // Execute batch operation
    JanusGraphOperations.createOutgoingRelations("domain", startNodeId, 
        endNodeIds, "hasChild", new HashMap<>(), new HashMap<>());
    
    // Verify count using Gremlin
    GraphTraversalSource g = DriverUtil.getGraphTraversalSource("domain", GraphOperation.READ);
    long count = g.V().has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
        .outE("hasChild")
        .count()
        .next();
    
    assertEquals(3L, count);
}
```

### Pattern 3: Collection Test

**Before (Neo4j)**:
```java
@Test
public void testCreateCollection() {
    String cypher = GraphQueryGenerationUtil.generateCreateCollectionCypherQuery(
        graphId, collectionId, collection, members, relationType, indexProperty
    );
    Neo4JBoltGraphOperations.executeQuery(cypher);
    
    // Verify sequence
    String verifyCypher = "MATCH (col)-[r:hasSequenceMember]->(m) " +
                         "WHERE col.IL_UNIQUE_ID = {collectionId} " +
                         "RETURN r.IL_SEQUENCE_INDEX ORDER BY r.IL_SEQUENCE_INDEX";
    // Assert indices are 1, 2, 3...
}
```

**After (JanusGraph)**:
```java
@Test
public void testCreateCollection() {
    Node collection = new Node("do_collection", "Content");
    List<String> members = Arrays.asList("do_m1", "do_m2", "do_m3");
    
    // Execute
    JanusGraphCollectionOperations.createCollection("domain", "do_collection", 
        collection, members, "hasSequenceMember", "IL_SEQUENCE_INDEX");
    
    // Verify sequence
    GraphTraversalSource g = DriverUtil.getGraphTraversalSource("domain", GraphOperation.READ);
    List<Map<String, Object>> edges = g.V()
        .has(SystemProperties.IL_UNIQUE_ID.name(), "do_collection")
        .outE("hasSequenceMember")
        .order().by("IL_SEQUENCE_INDEX")
        .project("index", "target")
            .by("IL_SEQUENCE_INDEX")
            .by(inV().values(SystemProperties.IL_UNIQUE_ID.name()))
        .toList();
    
    assertEquals(3, edges.size());
    assertEquals(1, edges.get(0).get("index"));
    assertEquals(2, edges.get(1).get("index"));
    assertEquals(3, edges.get(2).get("index"));
}
```

---

## Test Utilities

### JUnit Setup/Teardown

```java
public class JanusGraphTestBase {
    
    protected static GraphTraversalSource g;
    protected static String testGraphId = "test_domain";
    
    @BeforeClass
    public static void setupClass() {
        // Initialize JanusGraph connection
        g = DriverUtil.getGraphTraversalSource(testGraphId, GraphOperation.WRITE);
        
        // Create test indices
        JanusGraphSchemaManager.createUniqueIndex(testGraphId, "IL_UNIQUE_ID");
        JanusGraphSchemaManager.createCompositeIndex(testGraphId, "status");
    }
    
    @Before
    public void setup() {
        // Clean test data before each test
        g.V().has("graphId", testGraphId).drop().iterate();
        g.tx().commit();
    }
    
    @After
    public void teardown() {
        // Clean up after test
        try {
            g.tx().rollback();
        } catch (Exception e) {
            // Ignore if no active transaction
        }
    }
    
    @AfterClass
    public static void teardownClass() {
        // Close connections
        try {
            if (g != null) g.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Helper methods
    protected Vertex createTestNode(String nodeId, String objectType) {
        return g.addV(objectType)
            .property(SystemProperties.IL_UNIQUE_ID.name(), nodeId)
            .property("graphId", testGraphId)
            .property(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), objectType)
            .next();
    }
    
    protected long countNodes(String objectType) {
        return g.V()
            .has("graphId", testGraphId)
            .has(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), objectType)
            .count()
            .next();
    }
    
    protected long countEdges(String edgeLabel) {
        return g.E()
            .hasLabel(edgeLabel)
            .count()
            .next();
    }
}
```

---

## Performance Benchmarks

### Benchmark Template

```java
@Test
public void benchmarkBatchOperations() {
    String startNodeId = "do_start";
    List<String> endNodeIds = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
        endNodeIds.add("do_end_" + i);
        createTestNode("do_end_" + i, "Content");
    }
    
    // Benchmark batch operation
    long startTime = System.currentTimeMillis();
    JanusGraphOperations.createOutgoingRelations(testGraphId, startNodeId, 
        endNodeIds, "hasChild", new HashMap<>(), new HashMap<>());
    long duration = System.currentTimeMillis() - startTime;
    
    System.out.println("Batch operation (1000 edges): " + duration + "ms");
    
    // Verify
    long count = g.V().has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
        .outE("hasChild")
        .count()
        .next();
    
    assertEquals(1000L, count);
    
    // Expected: < 5000ms for 1000 edges
    assertTrue("Batch operation too slow: " + duration + "ms", duration < 5000);
}
```

### Expected Performance Baselines

| Operation | Neo4j Baseline | JanusGraph Target | Threshold |
|-----------|---------------|-------------------|-----------|
| Single node create | 1-2ms | 2-3ms | < 5ms |
| Single edge create | 1-2ms | 2-3ms | < 5ms |
| Batch edge create (100) | 50ms | 30-40ms | < 100ms |
| Batch edge create (1000) | 500ms | 300-400ms | < 1000ms |
| SubGraph (depth=5) | 20ms | 25-30ms | < 50ms |
| Collection create (20 members) | 15ms | 18-22ms | < 50ms |
| Property search (indexed) | 2-3ms | 3-5ms | < 10ms |
| Count query | 1ms | 2ms | < 5ms |

---

## Test Execution Plan

### Phase 1: Unit Tests (Week 1)

1. **Day 1-2**: Migrate core operation tests (6 suites)
   ```bash
   mvn test -Dtest=JanusGraphOperationsTest
   mvn test -Dtest=SearchAsyncOperationsTest
   mvn test -Dtest=JanusGraphCollectionOperationsTest
   ```

2. **Day 3**: Migrate schema tests
   ```bash
   mvn test -Dtest=JanusGraphSchemaManagerTest
   ```

3. **Day 4**: Update async operation tests
   ```bash
   mvn test -Dtest=GraphAsyncOperationsTest
   ```

4. **Day 5**: Review and fix failures

### Phase 2: Integration Tests (Week 2)

1. **Day 1**: Content service tests
2. **Day 2**: Taxonomy service tests
3. **Day 3**: Search service tests
4. **Day 4**: Collection hierarchy tests
5. **Day 5**: End-to-end scenarios

### Phase 3: Performance Tests (Week 3)

1. **Day 1**: Batch operation benchmarks
2. **Day 2**: Connection pooling tests
3. **Day 3**: Large graph tests (100k+ nodes)
4. **Day 4**: Stress tests
5. **Day 5**: Report generation

---

## Continuous Integration

### Jenkins/GitHub Actions Configuration

```yaml
# .github/workflows/janusgraph-tests.yml
name: JanusGraph Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    services:
      janusgraph:
        image: janusgraph/janusgraph:latest
        ports:
          - 8182:8182
      
      cassandra:
        image: cassandra:3.11.8
        ports:
          - 9042:9042
    
    steps:
      - uses: actions/checkout@v2
      
      - name: Set up JDK 11
        uses: actions/setup-java@v2
        with:
          java-version: '11'
      
      - name: Wait for JanusGraph
        run: |
          timeout 60 bash -c 'until curl -f http://localhost:8182; do sleep 2; done'
      
      - name: Run Unit Tests
        run: mvn test -Dtest=*JanusGraph*Test
      
      - name: Run Integration Tests
        run: mvn verify -Dtest=*Integration*Test
      
      - name: Generate Test Report
        run: mvn surefire-report:report
```

---

## Troubleshooting

### Common Issues

**Issue 1**: Tests fail with "Connection timeout"
```bash
# Solution: Increase connection timeout
echo "janusgraph.connection.timeout=60000" >> test.properties
```

**Issue 2**: Index not available
```bash
# Solution: Wait for index availability
JanusGraphSchemaManager.createCompositeIndex(graphId, property);
Thread.sleep(5000); // Wait for async index creation
```

**Issue 3**: Transaction already committed
```bash
# Solution: Don't commit in test utility methods
// Only commit in actual test method
```

---

## Success Criteria

- ✅ All core operation tests passing (100%)
- ✅ All integration tests passing (100%)
- ✅ Performance within 20% of Neo4j baselines
- ✅ No memory leaks in long-running tests
- ✅ Connection pool stable under load
- ✅ Test coverage > 80%

---

## Completion Checklist

- [ ] Phase 1: Unit tests migrated and passing
- [ ] Phase 2: Integration tests migrated and passing
- [ ] Phase 3: Performance benchmarks completed
- [ ] CI/CD pipeline updated
- [ ] Test report generated
- [ ] Performance comparison documented
- [ ] All tests documented in test plan
- [ ] Code coverage report reviewed
- [ ] Production readiness approved

---

## Resources

- **Test Base Class**: [JanusGraphTestBase.java](ontology-engine/graph-dac-api/src/test/java/org/sunbird/graph/service/util/JanusGraphTestBase.java)
- **Test Utilities**: [JanusGraphTestUtil.java](ontology-engine/graph-dac-api/src/test/java/org/sunbird/graph/service/util/JanusGraphTestUtil.java)
- **CI Configuration**: [.github/workflows/janusgraph-tests.yml](.github/workflows/janusgraph-tests.yml)

---

**Next Steps**: Execute Phase 1 (Week 1) - Migrate and run core operation tests
