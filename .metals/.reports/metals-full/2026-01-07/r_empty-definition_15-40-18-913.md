error id: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/NodeAsyncOperations.java:scala/concurrent/Future#
file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/NodeAsyncOperations.java
empty definition using pc, found symbol in pc: scala/concurrent/Future#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 247
uri: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/NodeAsyncOperations.java
text:
```scala
package org.sunbird.graph.service.operation;

import org.sunbird.common.dto.Request;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.dac.model.Node;
import scala.compat.java8.FutureConverters;
import scala.concurrent.@@Future;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Stub implementation of Node async operations for JanusGraph
 * TODO: Implement proper JanusGraph async operations
 */
public class NodeAsyncOperations {

    public static Future<Node> addNode(String graphId, Node node) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            throw new ServerException("ERR_OPERATION_NOT_IMPLEMENTED", "NodeAsyncOperations.addNode not yet implemented for JanusGraph");
        }));
    }

    public static Future<Node> upsertNode(String graphId, Node node, Request request) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            throw new ServerException("ERR_OPERATION_NOT_IMPLEMENTED", "NodeAsyncOperations.upsertNode not yet implemented for JanusGraph");
        }));
    }

    public static Future<Node> upsertRootNode(String graphId, Request request) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            throw new ServerException("ERR_OPERATION_NOT_IMPLEMENTED", "NodeAsyncOperations.upsertRootNode not yet implemented for JanusGraph");
        }));
    }

    public static Future<Boolean> deleteNode(String graphId, String nodeId, Request request) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            throw new ServerException("ERR_OPERATION_NOT_IMPLEMENTED", "NodeAsyncOperations.deleteNode not yet implemented for JanusGraph");
        }));
    }

    public static Future<Map<String, Node>> updateNodes(String graphId, List<String> identifiers, Map<String, Object> updatedMetadata) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            throw new ServerException("ERR_OPERATION_NOT_IMPLEMENTED", "NodeAsyncOperations.updateNodes not yet implemented for JanusGraph");
        }));
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: scala/concurrent/Future#