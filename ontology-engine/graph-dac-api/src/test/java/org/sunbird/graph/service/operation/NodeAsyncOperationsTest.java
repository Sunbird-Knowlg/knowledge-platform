package org.sunbird.graph.service.operation;

import org.junit.Assert;
import org.junit.Test;
import org.sunbird.common.exception.ClientException;
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

public class NodeAsyncOperationsTest extends BaseTest {


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


}
