package org.sunbird.graph.service.util;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.sunbird.graph.dac.model.Node;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeQueryGenerationUtilTest {

	@Test
	public void testGenerateUpdateNodesQuery() {
		List<String> ids = Arrays.asList("do_123", "do_234");
		Map<String, Object> metadata = new HashMap<String, Object>() {{
			put("version", "3");
			put("status", "Review");
		}};
		Map<String, Object> param = new HashMap<String, Object>();
		String query = NodeQueryGenerationUtil.generateUpdateNodesQuery("domain", ids, metadata, param);
		Assert.assertTrue(StringUtils.isNoneBlank(query));
		Assert.assertTrue(MapUtils.isNotEmpty(param));
		Assert.assertTrue(param.size() == 3);
		Assert.assertEquals("MATCH(n:domain) WHERE n.IL_UNIQUE_ID IN {identifiers} SET n.version = {1} ,  n.status = {2}  RETURN n AS ee ;", query);
	}

	@Test(expected = ClientException.class)
	public void testGenerateDeleteNodeCypherQueryInvalidGraph() {
		Map<String, Object> parameterMap = new HashMap<String, Object>() {{
			put("graphId", "");
			put("nodeId", "do_123");
		}};
		NodeQueryGenerationUtil.generateDeleteNodeCypherQuery(parameterMap);
	}

	@Test(expected = ClientException.class)
	public void testGenerateDeleteNodeCypherQueryInvalidIdentifier() {
		Map<String, Object> parameterMap = new HashMap<String, Object>() {{
			put("graphId", "domain");
			put("nodeId", "");
		}};
		NodeQueryGenerationUtil.generateDeleteNodeCypherQuery(parameterMap);
	}

	@Test
	public void testGenerateUpdateNodeCypherQuery() {
		Map<String, Object> parameterMap = new HashMap<String, Object>() {{
			put("graphId", "domain");
			put("node", getNode());
		}};
		NodeQueryGenerationUtil.generateUpdateNodeCypherQuery(parameterMap);
		Assert.assertTrue(MapUtils.isNotEmpty((Map<String, Object>) parameterMap.get("queryStatementMap")));
	}

	@Test(expected=ClientException.class)
	public void testGenerateUpdateNodeCypherQueryInvalidNode() {
		Map<String, Object> parameterMap = new HashMap<String, Object>() {{
			put("graphId", "domain");
			put("node", null);
		}};
		NodeQueryGenerationUtil.generateUpdateNodeCypherQuery(parameterMap);
	}

	@Test(expected=NullPointerException.class)
	public void testGenerateUpdateNodeCypherQueryEmptyNode() {
		Map<String, Object> parameterMap = new HashMap<String, Object>() {{
			put("graphId", "domain");
			put("node", new Node());
		}};
		NodeQueryGenerationUtil.generateUpdateNodeCypherQuery(parameterMap);
	}

	@Test(expected=ClientException.class)
	public void testGenerateUpsertRootNodeCypherQueryWithoutRootNode() {
		List<String> ids = Arrays.asList("do_123", "do_234");
		Map<String, Object> parameterMap = new HashMap<String, Object>() {{
			put("graphId", "domain");
			put("rootNode", null);
		}};
		NodeQueryGenerationUtil.generateUpsertRootNodeCypherQuery(parameterMap);
	}

	@Test
	public void testGenerateUpdatePropertyValuesCypherQuery() {
		List<String> ids = Arrays.asList("do_123", "do_234");
		Map<String, Object> parameterMap = new HashMap<String, Object>() {{
			put("graphId", "domain");
			put("nodeId", "do_123");
			put("metadata", new HashMap<String, Object>());
		}};
		String query = NodeQueryGenerationUtil.generateUpdatePropertyValuesCypherQuery(parameterMap);
		Assert.assertTrue(StringUtils.isNoneBlank(query));
		Assert.assertEquals("MATCH(ee:domain WHERE ee.IL_UNIQUE_ID='do_123' SET ", query);
	}

	@Test(expected=ClientException.class)
	public void testGenerateUpdatePropertyValuesCypherQueryEmptyGraphId() {
		List<String> ids = Arrays.asList("do_123", "do_234");
		Map<String, Object> parameterMap = new HashMap<String, Object>() {{
			put("graphId", "");
			put("nodeId", "do_123");
			put("metadata", new HashMap<String, Object>());
		}};
		NodeQueryGenerationUtil.generateUpdatePropertyValuesCypherQuery(parameterMap);
	}

	@Test(expected=ClientException.class)
	public void testGenerateUpdatePropertyValuesCypherQueryEmptyNodeId() {
		List<String> ids = Arrays.asList("do_123", "do_234");
		Map<String, Object> parameterMap = new HashMap<String, Object>() {{
			put("graphId", "domain");
			put("nodeId", "");
			put("metadata", new HashMap<String, Object>());
		}};
		NodeQueryGenerationUtil.generateUpdatePropertyValuesCypherQuery(parameterMap);
	}

	@Test(expected=ClientException.class)
	public void testGenerateUpdatePropertyValuesCypherQueryWithoutMetadata() {
		List<String> ids = Arrays.asList("do_123", "do_234");
		Map<String, Object> parameterMap = new HashMap<String, Object>() {{
			put("graphId", "domain");
			put("nodeId", "do_123");
		}};
		NodeQueryGenerationUtil.generateUpdatePropertyValuesCypherQuery(parameterMap);
	}

	private Node getNode() {
		Node node = new Node("domain", "DATA_NODE", "Content");
		node.setIdentifier("do_000000123");
		node.setMetadata(new HashMap<String, Object>() {{
			put("status", "Draft");
			put("name", "Test Node");
			put("identifier", "do_000000123");
		}});
		return node;
	}
}
