package org.sunbird.graph.service.operation;

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
		graphDb.execute("UNWIND [{nodeId:'do_000000123', name: 'Test Node'}] as row with row.nodeId as Id CREATE (n:domain{IL_UNIQUE_ID:Id});");

	}

	@Test
	public void testSetPrimitiveData() throws Exception {
		Method method = NodeAsyncOperations.class.getDeclaredMethod("setPrimitiveData", Map.class);
		method.setAccessible(true);
		Map<String, Object> metadata = new HashMap<String, Object>() {{
			put("testMap", new HashMap<String, Object>() {{
				put("name", "test");
				put("identifier", "123");
			}});
			put("list", new ArrayList<Map<String, Object>>() {{
				add(new HashMap<String, Object>(){{
					put("identifier","123");
				}});
				add(new HashMap<String, Object>(){{
					put("identifier","234");
				}});
			}});
		}};
		Map<String, Object> result = (Map<String, Object>) method.invoke(NodeAsyncOperations.class, metadata);
		Assert.assertNotNull(result);
		Assert.assertEquals("{\"identifier\":\"123\",\"name\":\"test\"}", result.get("testMap"));
	}

	@Test
	public void testUpdateNodes() throws Exception {
		createBulkNodes();
		List<String> ids = Arrays.asList("do_0000123", "do_0000234");
		Map<String, Object> data = new HashMap<String, Object>() {{
			put("status", "Review");
		}};
		Future<Map<String, Node>> resultFuture = NodeAsyncOperations.updateNodes("domain",ids, data);
		Map<String, Node> result = Await.result(resultFuture, Duration.apply("30s"));
		Assert.assertTrue(result.size()==2);
	}

	@Test(expected = ClientException.class)
	public void testUpdateNodesWithEmptyGraphId() throws Exception {
		List<String> ids = Arrays.asList("do_0000123", "do_0000234");
		Map<String, Object> data = new HashMap<String, Object>() {{
			put("status", "Review");
		}};
		Future<Map<String, Node>> resultFuture = NodeAsyncOperations.updateNodes(null,ids, data);
		Map<String, Node> result = Await.result(resultFuture, Duration.apply("30s"));
	}

	@Test(expected = ClientException.class)
	public void testUpdateNodesWithEmptyIdentifiers() throws Exception {
		Map<String, Object> data = new HashMap<String, Object>() {{
			put("status", "Review");
		}};
		Future<Map<String, Node>> resultFuture = NodeAsyncOperations.updateNodes("domain", new ArrayList<String>(), data);
		Map<String, Node> result = Await.result(resultFuture, Duration.apply("30s"));
	}

	@Test(expected = ClientException.class)
	public void testUpdateNodesWithEmptyMetadata() throws Exception {
		List<String> ids = Arrays.asList("do_0000123", "do_0000234");
		Future<Map<String, Node>> resultFuture = NodeAsyncOperations.updateNodes("domain", ids, new HashMap<String, Object>());
		Map<String, Node> result = Await.result(resultFuture, Duration.apply("30s"));
	}

	@Test
	public void testAddNode() throws Exception {
		Node node = new Node("domain","DATA_NODE","Content");
		node.setIdentifier("do_000000111");
		node.setMetadata(new HashMap<String, Object>(){{put("status","Draft");}});
		Future<Node> resultFuture = NodeAsyncOperations.addNode("graphId",node);
		Node result = Await.result(resultFuture, Duration.apply("30s"));
		Assert.assertTrue(null!=node);
		Assert.assertEquals("do_000000111",result.getIdentifier());
	}

	@Test(expected = ClientException.class)
	public void testAddNodeWithEmptyGrpahId() throws Exception {
		Node node = new Node("domain","DATA_NODE","Content");
		node.setIdentifier("do_000000112");
		node.setMetadata(new HashMap<String, Object>(){{put("status","Draft");}});
		Future<Node> resultFuture = NodeAsyncOperations.addNode(null, node);
		Node result = Await.result(resultFuture, Duration.apply("30s"));
	}

	@Test(expected = ClientException.class)
	public void testAddNodeWithNullNode() throws Exception {
		Future<Node> resultFuture = NodeAsyncOperations.addNode("domain", null);
		Node result = Await.result(resultFuture, Duration.apply("30s"));
	}

	@Test
	public void testUpsertRootNode() throws Exception {
		Future<Node> resultFuture = NodeAsyncOperations.upsertRootNode("domain", new Request());
		Node result = Await.result(resultFuture, Duration.apply("30s"));
		Assert.assertNotNull(result.getIdentifier());
		Assert.assertEquals("do_ROOT_NODE", result.getIdentifier());
		Assert.assertEquals("ROOT_NODE", result.getNodeType());
	}

	@Test
	public void testDeleteWithValidID() throws Exception {
		Future<Boolean> resultFuture2 = NodeAsyncOperations.deleteNode("domain", "do_000000123", new Request());
		Assert.assertTrue(Await.result(resultFuture2, Duration.apply("30s")));
	}

	@Test
	public void testDeleteWithInvalidId() throws Exception {
		Future<Boolean> resultFuture2 = NodeAsyncOperations.deleteNode("domain", "do_000000123_invalid", new Request());
		try {
			Await.result(resultFuture2, Duration.apply("30s"));
		} catch (CompletionException e) {
			Assert.assertTrue(e.getCause() instanceof ResourceNotFoundException);
		}
	}

	@Test(expected = ClientException.class)
	public void testDeleteWithEmptyId() throws Exception {
		Future<Boolean> resultFuture2 = NodeAsyncOperations.deleteNode("domain", " ", new Request());
		Await.result(resultFuture2, Duration.apply("30s"));
	}

	@Test(expected = ClientException.class)
	public void testDeleteWithEmptyGraphId() throws Exception {
		Future<Boolean> resultFuture2 = NodeAsyncOperations.deleteNode("", "do_1234 ", new Request());
		Await.result(resultFuture2, Duration.apply("30s"));
	}

	private Node getNode() throws Exception {
		Node node = new Node("domain", "DATA_NODE", "Content");
		node.setIdentifier("do_000000123");
		node.setMetadata(new HashMap<String, Object>() {{
			put("status", "Draft");
			put("name", "Test Node for Delete");
			put("identifier", "do_000000123");
		}});
		return node;
	}

}
