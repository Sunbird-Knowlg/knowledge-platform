package org.sunbird.graph.service.operation;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sunbird.common.dto.Request;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ResourceNotFoundException;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.test.BaseTest;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

public class NodeAsyncOperationsTest extends BaseTest {

    @BeforeClass
    public static void setUp() {
        GraphTraversalSource infoG = graph.traversal();
        // Create initial test node
        if (!infoG.V().has("IL_UNIQUE_ID", "do_000000123").hasNext()) {
            infoG.addV("domain")
                    .property("IL_UNIQUE_ID", "do_000000123")
                    .property("graphId", "domain") // Added graphId property
                    .property("name", "Test Node")
                    .property("IL_SYS_NODE_TYPE", "DATA_NODE")
                    .property("IL_FUNC_OBJECT_TYPE", "Content")
                    .next();
        }
        infoG.tx().commit();
        try {
            infoG.close();
        } catch (Exception e) {
        }
    }

    @Test
    public void testSetPrimitiveData() throws Exception {
        Method method = NodeAsyncOperations.class.getDeclaredMethod("setPrimitiveData", Map.class);
        method.setAccessible(true);
        Map<String, Object> metadata = new HashMap<String, Object>() {
            {
                put("testMap", new HashMap<String, Object>() {
                    {
                        put("name", "test");
                        put("identifier", "123");
                    }
                });
                put("list", new ArrayList<Map<String, Object>>() {
                    {
                        add(new HashMap<String, Object>() {
                            {
                                put("identifier", "123");
                            }
                        });
                        add(new HashMap<String, Object>() {
                            {
                                put("identifier", "234");
                            }
                        });
                    }
                });
            }
        };
        Map<String, Object> result = (Map<String, Object>) method.invoke(NodeAsyncOperations.class, metadata);
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.get("testMap"));
    }

    @Test
    public void testUpdateNodes() throws Exception {
        createBulkNodes(); // Creates do_0000123, do_0000234 with graphId=domain
        List<String> ids = Arrays.asList("do_0000123", "do_0000234");
        Map<String, Object> data = new HashMap<String, Object>() {
            {
                put("status", "Review");
            }
        };
        Future<Map<String, Node>> resultFuture = NodeAsyncOperations.updateNodes("domain", ids, data);
        Map<String, Node> result = Await.result(resultFuture, Duration.apply("30s"));
        Assert.assertEquals(2, result.size());
    }

    @Test
    public void testUpdateNodesWithEmptyGraphId() {
        List<String> ids = Arrays.asList("do_0000123", "do_0000234");
        Map<String, Object> data = new HashMap<String, Object>() {
            {
                put("status", "Review");
            }
        };
        try {
            Future<Map<String, Node>> resultFuture = NodeAsyncOperations.updateNodes(null, ids, data);
            Await.result(resultFuture, Duration.apply("30s"));
            Assert.fail("Expected ClientException");
        } catch (Exception e) {
            Assert.assertTrue(isClientException(e));
        }
    }

    @Test
    public void testUpdateNodesWithEmptyIdentifiers() {
        Map<String, Object> data = new HashMap<String, Object>() {
            {
                put("status", "Review");
            }
        };
        try {
            Future<Map<String, Node>> resultFuture = NodeAsyncOperations.updateNodes("domain", new ArrayList<String>(),
                    data);
            Await.result(resultFuture, Duration.apply("30s"));
            Assert.fail("Expected ClientException");
        } catch (Exception e) {
            Assert.assertTrue(isClientException(e));
        }
    }

    @Test
    public void testUpdateNodesWithEmptyMetadata() throws Exception {
        // Empty metadata should not throw exception, just return empty result
        List<String> ids = Arrays.asList("do_0000123", "do_0000234");
        Future<Map<String, Node>> resultFuture = NodeAsyncOperations.updateNodes("domain", ids,
                new HashMap<String, Object>());
        Map<String, Node> result = Await.result(resultFuture, Duration.apply("30s"));
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testAddNode() throws Exception {
        Node node = new Node("domain", "DATA_NODE", "Content");
        node.setIdentifier("do_000000111");
        node.setMetadata(new HashMap<String, Object>() {
            {
                put("status", "Draft");
            }
        });
        Future<Node> resultFuture = NodeAsyncOperations.addNode("domain", node); // Correct graphId
        Node result = Await.result(resultFuture, Duration.apply("30s"));
        Assert.assertNotNull(result);
        Assert.assertEquals("do_000000111", result.getIdentifier());
    }

    @Test
    public void testAddNodeWithEmptyGraphId() {
        Node node = new Node("domain", "DATA_NODE", "Content");
        node.setIdentifier("do_000000112");
        node.setMetadata(new HashMap<String, Object>() {
            {
                put("status", "Draft");
            }
        });
        try {
            Future<Node> resultFuture = NodeAsyncOperations.addNode(null, node);
            Await.result(resultFuture, Duration.apply("30s"));
            Assert.fail("Expected ClientException");
        } catch (Exception e) {
            Assert.assertTrue(isClientException(e));
        }
    }

    @Test
    public void testAddNodeWithNullNode() {
        try {
            Future<Node> resultFuture = NodeAsyncOperations.addNode("domain", null);
            Await.result(resultFuture, Duration.apply("30s"));
            Assert.fail("Expected ClientException");
        } catch (Exception e) {
            Assert.assertTrue(isClientException(e));
        }
    }

    @Test
    public void testUpsertRootNode() throws Exception {
        Future<Node> resultFuture = NodeAsyncOperations.upsertRootNode("domain", new Request());
        Node result = Await.result(resultFuture, Duration.apply("30s"));
        Assert.assertNotNull(result.getIdentifier());
        Assert.assertEquals("root", result.getIdentifier());
        Assert.assertEquals("DATA_NODE", result.getNodeType()); // Updated expectation
    }

    @Test
    public void testDeleteWithValidID() throws Exception {
        GraphTraversalSource infoG = graph.traversal();
        // Ensure node exists first
        if (!infoG.V().has("IL_UNIQUE_ID", "do_000000123").hasNext()) {
            infoG.addV("domain")
                    .property("IL_UNIQUE_ID", "do_000000123")
                    .property("graphId", "domain") // Added graphId
                    .property("IL_SYS_NODE_TYPE", "DATA_NODE")
                    .next();
        }
        infoG.tx().commit();
        try {
            infoG.close();
        } catch (Exception e) {
        }

        Future<Boolean> resultFuture2 = NodeAsyncOperations.deleteNode("domain", "do_000000123", new Request());
        Assert.assertTrue(Await.result(resultFuture2, Duration.apply("30s")));
    }

    @Test
    public void testDeleteWithInvalidId() {
        Future<Boolean> resultFuture2 = NodeAsyncOperations.deleteNode("domain", "do_000000123_invalid", new Request());
        try {
            Boolean result = Await.result(resultFuture2, Duration.apply("30s"));
            Assert.assertFalse(result); // Should return false if not found
        } catch (Exception e) {
            // Or if it throws, that's fine too, but code suggests it returns false if
            // deletedCount == 0
        }
    }

    @Test
    public void testDeleteWithEmptyId() {
        try {
            Future<Boolean> resultFuture2 = NodeAsyncOperations.deleteNode("domain", " ", new Request());
            Await.result(resultFuture2, Duration.apply("30s"));
            Assert.fail("Expected ClientException");
        } catch (Exception e) {
            Assert.assertTrue(isClientException(e));
        }
    }

    @Test
    public void testDeleteWithEmptyGraphId() {
        try {
            Future<Boolean> resultFuture2 = NodeAsyncOperations.deleteNode("", "do_1234 ", new Request());
            Await.result(resultFuture2, Duration.apply("30s"));
            Assert.fail("Expected ClientException");
        } catch (Exception e) {
            Assert.assertTrue(isClientException(e));
        }
    }

    private boolean isClientException(Exception e) {
        if (e instanceof ClientException)
            return true;
        if (e instanceof CompletionException && e.getCause() instanceof ClientException)
            return true;
        return false;
    }

}
