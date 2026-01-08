# JanusGraph Quick Reference - Developer Guide

**Last Updated**: January 7, 2026

---

## 🚀 Quick Start

### Importing Classes
```java
// Core operations
import org.sunbird.graph.service.operation.JanusGraphOperations;
import org.sunbird.graph.service.operation.JanusGraphCollectionOperations;
import org.sunbird.graph.service.operation.JanusGraphSchemaManager;
import org.sunbird.graph.service.operation.GraphAsyncOperations;
import org.sunbird.graph.service.operation.SearchAsyncOperations;

// Utilities
import org.sunbird.graph.service.util.GremlinQueryBuilder;
import org.sunbird.graph.service.util.DriverUtil;

// TinkerPop/Gremlin
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.process.traversal.P;
```

---

## 📖 Common Operations

### 1. Create a Node (Vertex)

```java
// Get traversal source
GraphTraversalSource g = DriverUtil.getGraphTraversalSource("domain", GraphOperation.WRITE);

// Create node with properties
Map<String, Object> properties = new HashMap<>();
properties.put("name", "Introduction to Physics");
properties.put("status", "Live");
properties.put("contentType", "Course");

Vertex node = GremlinQueryBuilder.createVertex(g, "domain", "do_12345", 
    "Content", properties).next();

g.tx().commit();
```

### 2. Create a Relation (Edge)

```java
GraphTraversalSource g = DriverUtil.getGraphTraversalSource("domain", GraphOperation.WRITE);

// Single relation with metadata
Map<String, Object> metadata = new HashMap<>();
metadata.put("relationType", "hasContent");
metadata.put("created", System.currentTimeMillis());

GremlinQueryBuilder.createEdge(g, "domain", "do_course", "do_chapter1", 
    "hasSequenceMember", metadata).next();

g.tx().commit();
```

### 3. Batch Create Relations (One-to-Many)

```java
// Create relations from 1 course to 100 chapters (single query!)
String courseId = "do_course123";
List<String> chapterIds = Arrays.asList("do_ch1", "do_ch2", ..., "do_ch100");

Map<String, Object> createMetadata = new HashMap<>();
createMetadata.put("created", System.currentTimeMillis());

Map<String, Object> matchMetadata = new HashMap<>();
matchMetadata.put("updated", System.currentTimeMillis());

// Synchronous
JanusGraphOperations.createOutgoingRelations("domain", courseId, chapterIds, 
    "hasSequenceMember", createMetadata, matchMetadata);

// OR Async
Future<Response> future = GraphAsyncOperations.createBulkOutgoingRelations(
    "domain", courseId, chapterIds, "hasSequenceMember", 
    createMetadata, matchMetadata);
```

### 4. Create a Collection with Sequence

```java
// Create course with chapters in specific order
Node course = new Node("do_course", "Content");
course.setMetadata(Map.of(
    "name", "Physics 101",
    "status", "Live"
));

List<String> chapters = Arrays.asList("do_ch1", "do_ch2", "do_ch3");

// Creates: course --[hasSequenceMember{IL_SEQUENCE_INDEX=1}]--> ch1
//          course --[hasSequenceMember{IL_SEQUENCE_INDEX=2}]--> ch2
//          course --[hasSequenceMember{IL_SEQUENCE_INDEX=3}]--> ch3
Node result = JanusGraphCollectionOperations.createCollection(
    "domain", "do_course", course, chapters, 
    "hasSequenceMember", "IL_SEQUENCE_INDEX"
);
```

### 5. Find Nodes by Property

```java
GraphTraversalSource g = DriverUtil.getGraphTraversalSource("domain", GraphOperation.READ);

// Find all live courses
List<Vertex> courses = g.V()
    .has("graphId", "domain")
    .has("contentType", "Course")
    .has("status", "Live")
    .toList();

// Or use helper
Future<List<Node>> nodes = SearchAsyncOperations.getNodesByProperty(
    "domain", "contentType", "Course"
);
```

### 6. Batch Find Nodes

```java
GraphTraversalSource g = DriverUtil.getGraphTraversalSource("domain", GraphOperation.READ);

List<String> nodeIds = Arrays.asList("do_123", "do_456", "do_789");

// Single query using P.within()
List<Vertex> nodes = g.V()
    .has("graphId", "domain")
    .has(SystemProperties.IL_UNIQUE_ID.name(), P.within(nodeIds))
    .toList();
```

### 7. Update Node Properties

```java
GraphTraversalSource g = DriverUtil.getGraphTraversalSource("domain", GraphOperation.WRITE);

Map<String, Object> updates = new HashMap<>();
updates.put("status", "Retired");
updates.put("lastUpdatedOn", System.currentTimeMillis());

GremlinQueryBuilder.updateVertex(g, "domain", "do_123", updates).next();
g.tx().commit();
```

### 8. Delete Relations (Batch)

```java
// Delete all relations from course to specific chapters
JanusGraphOperations.deleteOutgoingRelations(
    "domain", "do_course", 
    Arrays.asList("do_ch1", "do_ch2", "do_ch3"),
    "hasSequenceMember"
);
```

### 9. Get All Edges of a Node

```java
GraphTraversalSource g = DriverUtil.getGraphTraversalSource("domain", GraphOperation.READ);

// Get all outgoing edges
List<Edge> edges = GremlinQueryBuilder.getAllEdges(
    g, "domain", "do_course", "hasSequenceMember"
).toList();

// Get edge count
long count = GremlinQueryBuilder.countEdges(
    g, "domain", "do_course", "hasSequenceMember"
).next();
```

### 10. Traverse Subgraph

```java
// Get all nodes and relations within depth=5
Future<SubGraph> subgraph = GraphAsyncOperations.getSubGraph(
    "domain", "do_course", 5
);

// Access nodes and relations
SubGraph sg = subgraph.get();
Map<String, Node> nodes = sg.getNodes();
List<Relation> relations = sg.getRelations();
```

### 11. Create Schema Indices

```java
// Unique constraint (like Neo4j CONSTRAINT)
JanusGraphSchemaManager.createUniqueIndex("domain", "IL_UNIQUE_ID");

// Standard index (like Neo4j INDEX)
JanusGraphSchemaManager.createCompositeIndex("domain", "status");

// Full-text search index (requires Elasticsearch)
JanusGraphSchemaManager.createMixedIndex("domain", "name", "search");

// List all indices
Iterable<String> indices = JanusGraphSchemaManager.listIndices("domain");
```

### 12. Transaction Management

```java
GraphTraversalSource g = DriverUtil.getGraphTraversalSource("domain", GraphOperation.WRITE);

try {
    // Perform multiple operations
    GremlinQueryBuilder.createVertex(g, "domain", "do_1", "Content", props1).next();
    GremlinQueryBuilder.createVertex(g, "domain", "do_2", "Content", props2).next();
    GremlinQueryBuilder.createEdge(g, "domain", "do_1", "do_2", "hasChild", metadata).next();
    
    // Commit transaction
    g.tx().commit();
} catch (Exception e) {
    // Rollback on error
    g.tx().rollback();
    throw e;
}
```

---

## 🎯 Best Practices

### 1. Use Batch Operations for Multiple Nodes
```java
// ❌ BAD: Loop with N queries
for (String endNodeId : endNodeIds) {
    GremlinQueryBuilder.createEdge(g, graphId, startNodeId, endNodeId, relType, metadata).next();
}

// ✅ GOOD: Single batch query
JanusGraphOperations.createOutgoingRelations(graphId, startNodeId, endNodeIds, relType, createMeta, matchMeta);
```

### 2. Separate READ/WRITE Connections
```java
// ❌ BAD: Using WRITE for reads
GraphTraversalSource g = DriverUtil.getGraphTraversalSource("domain", GraphOperation.WRITE);
List<Vertex> nodes = g.V().has("status", "Live").toList();

// ✅ GOOD: Use READ for queries
GraphTraversalSource g = DriverUtil.getGraphTraversalSource("domain", GraphOperation.READ);
List<Vertex> nodes = g.V().has("status", "Live").toList();
```

### 3. Always Commit/Rollback Transactions
```java
// ❌ BAD: No transaction management
GremlinQueryBuilder.createVertex(g, "domain", "do_1", "Content", props).next();

// ✅ GOOD: Explicit commit
try {
    GremlinQueryBuilder.createVertex(g, "domain", "do_1", "Content", props).next();
    g.tx().commit();
} catch (Exception e) {
    g.tx().rollback();
    throw e;
}
```

### 4. Use tryNext().orElse() for MERGE
```java
// ❌ BAD: No MERGE semantics
Edge edge = startVertex.addEdge("REL", endVertex);

// ✅ GOOD: Check existence first
Edge edge = g.V(startId).outE("REL")
    .where(inV().hasId(endId))
    .tryNext()
    .orElse(null);

if (edge == null) {
    edge = startVertex.addEdge("REL", endVertex);
    // ON CREATE logic
} else {
    // ON MATCH logic
}
```

### 5. Use Lazy Evaluation
```java
// ❌ BAD: Collecting all results unnecessarily
g.V().has("status", "Live").toList();  // Loads ALL into memory

// ✅ GOOD: Iterate without collection
g.V().has("status", "Live").iterate();  // Fire and forget

// ✅ GOOD: Get only what you need
Vertex first = g.V().has("status", "Live").next();  // First only
```

---

## 🔍 Debugging & Troubleshooting

### Enable Telemetry Logging
```java
TelemetryManager.log("Creating node: " + nodeId);
TelemetryManager.error("Error creating node", exception);
```

### Check Connection Status
```java
// Verify GraphTraversalSource
GraphTraversalSource g = DriverUtil.getGraphTraversalSource("domain", GraphOperation.READ);
long count = g.V().count().next();
System.out.println("Total vertices: " + count);
```

### Inspect Vertex/Edge Properties
```java
Vertex v = g.V().has(IL_UNIQUE_ID, "do_123").next();
v.properties().forEachRemaining(p -> 
    System.out.println(p.key() + " = " + p.value())
);

Edge e = g.E().hasLabel("hasSequenceMember").next();
e.properties().forEachRemaining(p -> 
    System.out.println(p.key() + " = " + p.value())
);
```

### Common Errors

**Error**: `No such property: IL_UNIQUE_ID`
- **Fix**: Property not indexed. Create composite index:
  ```java
  JanusGraphSchemaManager.createCompositeIndex("domain", "IL_UNIQUE_ID");
  ```

**Error**: `Transaction already committed`
- **Fix**: Don't commit twice. Use single commit at end:
  ```java
  try {
      // All operations
      g.tx().commit();  // Only once
  } catch (Exception e) {
      g.tx().rollback();
  }
  ```

**Error**: `Connection timeout`
- **Fix**: Increase connection pool size:
  ```hocon
  janusgraph.connection.max.pool.size = 100
  ```

---

## 📊 Performance Tips

### 1. Index All Frequently Queried Properties
```java
// Create indices for common filters
JanusGraphSchemaManager.createCompositeIndex("domain", "status");
JanusGraphSchemaManager.createCompositeIndex("domain", "contentType");
JanusGraphSchemaManager.createCompositeIndex("domain", "createdOn");
```

### 2. Use P.within() for Batch Queries
```java
// 100x faster than 100 individual queries
List<String> ids = /* 100 IDs */;
List<Vertex> nodes = g.V().has(IL_UNIQUE_ID, P.within(ids)).toList();
```

### 3. Limit Result Sets
```java
// Get first 100 only
List<Vertex> nodes = g.V().has("status", "Live").limit(100).toList();

// Pagination
List<Vertex> page = g.V()
    .has("status", "Live")
    .range(startIndex, endIndex)
    .toList();
```

### 4. Use Async Operations for Long-Running Tasks
```java
// Non-blocking
Future<Response> future = GraphAsyncOperations.createBulkOutgoingRelations(/* params */);

// Continue other work...

// Wait for result when needed
Response result = Await.result(future, Duration.apply(30, TimeUnit.SECONDS));
```

---

## 🔗 Migration from Neo4j

### Cypher to Gremlin Cheat Sheet

| Neo4j Cypher | JanusGraph Gremlin |
|--------------|-------------------|
| `MATCH (n:domain)` | `g.V().has("graphId", "domain")` |
| `WHERE n.status = "Live"` | `.has("status", "Live")` |
| `RETURN n` | `.toList()` |
| `MATCH (a)-[r:REL]->(b)` | `g.V(a).outE("REL").inV()` |
| `MERGE (n {id: {id}})` | `g.V().has("id", id).tryNext().orElse(null)` |
| `CREATE (n {props})` | `g.addV("label").property("key", val)` |
| `SET n.status = "Retired"` | `v.property("status", "Retired")` |
| `DELETE n` | `g.V(n).drop()` |
| `DETACH DELETE n` | `g.V(n).bothE().drop(); g.V(n).drop()` |
| `UNWIND {ids} AS id` | `.has(P.within(ids))` |
| `count(n)` | `.count().next()` |

### Function Mapping

| Neo4j Function | JanusGraph Equivalent |
|----------------|----------------------|
| `Neo4JBoltGraphOperations.createRelation()` | `JanusGraphOperations.createRelation()` |
| `Neo4JBoltGraphOperations.updateRelation()` | `JanusGraphOperations.updateRelation()` |
| `Neo4JBoltGraphOperations.deleteRelation()` | `JanusGraphOperations.deleteRelation()` |
| `Neo4JBoltSearchOperations.getNodeByUniqueIds()` | `SearchAsyncOperations.getNodeByUniqueIds()` |
| `GraphQueryGenerationUtil.generateCreateCollectionCypherQuery()` | `JanusGraphCollectionOperations.createCollection()` |

---

## 📞 Need Help?

- **Documentation**: [JANUSGRAPH-MIGRATION-COMPLETE.md](JANUSGRAPH-MIGRATION-COMPLETE.md)
- **Full Guide**: [MIGRATION-SUMMARY.md](MIGRATION-SUMMARY.md)
- **Setup**: [README.md](README.md)
- **Team**: #knowlg-dev
- **JanusGraph Docs**: https://docs.janusgraph.org/

---

**Quick Reference Version**: 1.0  
**Last Updated**: January 7, 2026
