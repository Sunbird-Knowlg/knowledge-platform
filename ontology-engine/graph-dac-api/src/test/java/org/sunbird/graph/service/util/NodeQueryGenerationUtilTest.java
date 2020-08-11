package org.sunbird.graph.service.util;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.sunbird.graph.dac.model.Node;

import java.lang.reflect.Method;
import java.util.*;

public class NodeQueryGenerationUtilTest {

    @Test
    public void testGenerateUpdateNodesQuery() {
        List<String> ids = Arrays.asList("do_123", "do_234");
        Map<String, Object> metadata = new HashMap<>() {{
            put("version", "3");
            put("status", "Review");
        }};
        Map<String, Object> param = new HashMap<>();
        String query = NodeQueryGenerationUtil.generateUpdateNodesQuery("domain", ids, metadata, param);
        Assert.assertTrue(StringUtils.isNoneBlank(query));
        Assert.assertTrue(MapUtils.isNotEmpty(param));
        Assert.assertTrue(param.size() == 3);
        Assert.assertEquals("MATCH(n:domain) WHERE n.IL_UNIQUE_ID IN {identifiers} SET n.version = {1} ,  n.status = {2}  RETURN n AS ee ;", query);
    }

    @Test
    public void testGenerateDeleteNodeCypherQuery() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("nodeId", "do_123");
            put("metadata", new HashMap<String, Object>());
        }};
        String query = NodeQueryGenerationUtil.generateDeleteNodeCypherQuery(parameterMap);
        Assert.assertTrue(StringUtils.isNoneBlank(query));
        Assert.assertEquals("MATCH (a:domain {IL_UNIQUE_ID: 'do_123'}) DETACH DELETE a RETURN a", query);
    }

    @Test(expected = ClientException.class)
    public void testGenerateDeleteNodeCypherQueryInvalidGraph() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "");
            put("nodeId", "do_123");
        }};
        NodeQueryGenerationUtil.generateDeleteNodeCypherQuery(parameterMap);
    }

    @Test(expected = ClientException.class)
    public void testGenerateDeleteNodeCypherQueryInvalidIdentifier() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("nodeId", "");
        }};
        NodeQueryGenerationUtil.generateDeleteNodeCypherQuery(parameterMap);
    }

    @Test
    public void testGenerateUpdateNodeCypherQuery() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("node", getNode());
        }};
        NodeQueryGenerationUtil.generateUpdateNodeCypherQuery(parameterMap);
        Assert.assertTrue(MapUtils.isNotEmpty((Map<String, Object>) parameterMap.get("queryStatementMap")));
    }

    @Test(expected = ClientException.class)
    public void testGenerateUpdateNodeCypherQueryInvalidNode() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("node", null);
        }};
        NodeQueryGenerationUtil.generateUpdateNodeCypherQuery(parameterMap);
    }

    @Test(expected = ClientException.class)
    public void testGenerateUpdateNodeCypherQueryEmptyGraphId() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "");
            put("node", new Node());
        }};
        NodeQueryGenerationUtil.generateUpdateNodeCypherQuery(parameterMap);
    }

    @Test
    public void testGenerateUpdateNodeCypherQueryEmptyNode() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("node", new Node());
        }};
        NodeQueryGenerationUtil.generateUpdateNodeCypherQuery(parameterMap);
        Assert.assertTrue(MapUtils.isNotEmpty((Map<String, Object>) parameterMap.get("queryStatementMap")));
    }

    @Test
    public void testGenerateUpsertRootNodeCypherQuery() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("rootNode", getNode());
        }};
        String query = NodeQueryGenerationUtil.generateUpsertRootNodeCypherQuery(parameterMap);
        Assert.assertTrue(StringUtils.isNoneBlank(query));
    }

    @Test(expected = ClientException.class)
    public void testGenerateUpsertRootNodeCypherQueryWithoutRootNode() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("rootNode", null);
        }};
        NodeQueryGenerationUtil.generateUpsertRootNodeCypherQuery(parameterMap);
    }

    @Test
    public void testGenerateUpdatePropertyValuesCypherQuery() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("nodeId", "do_123");
            put("metadata", new HashMap<String, Object>());
        }};
        String query = NodeQueryGenerationUtil.generateUpdatePropertyValuesCypherQuery(parameterMap);
        Assert.assertTrue(StringUtils.isNoneBlank(query));
        Assert.assertEquals("MATCH(ee:domain WHERE ee.IL_UNIQUE_ID='do_123' SET ", query);
    }

    @Test(expected = ClientException.class)
    public void testGenerateUpdatePropertyValuesCypherQueryEmptyGraphId() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "");
            put("nodeId", "do_123");
            put("metadata", new HashMap<String, Object>());
        }};
        NodeQueryGenerationUtil.generateUpdatePropertyValuesCypherQuery(parameterMap);
    }

    @Test(expected = ClientException.class)
    public void testGenerateUpdatePropertyValuesCypherQueryEmptyNodeId() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("nodeId", "");
            put("metadata", new HashMap<String, Object>());
        }};
        NodeQueryGenerationUtil.generateUpdatePropertyValuesCypherQuery(parameterMap);
    }

    @Test(expected = ClientException.class)
    public void testGenerateUpdatePropertyValuesCypherQueryWithoutMetadata() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("nodeId", "do_123");
        }};
        NodeQueryGenerationUtil.generateUpdatePropertyValuesCypherQuery(parameterMap);
    }

    @Test
    public void testGenerateRemovePropertyValueCypherQuery() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("nodeId", "do_123");
            put("key", "test_key");
            put("metadata", new HashMap<String, Object>());
        }};
        String query = NodeQueryGenerationUtil.generateRemovePropertyValueCypherQuery(parameterMap);
        Assert.assertTrue(StringUtils.isNoneBlank(query));
        Assert.assertEquals("MATCH(ee:domain) WHERE ee.IL_UNIQUE_ID='do_123' SET ee.test_key=null", query);
    }

    @Test(expected = ClientException.class)
    public void testGenerateRemovePropertyValueCypherQueryEmptyGraphId() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "");
            put("nodeId", "do_123");
            put("key", "test_key");
            put("metadata", new HashMap<String, Object>());
        }};
        NodeQueryGenerationUtil.generateRemovePropertyValueCypherQuery(parameterMap);
    }

    @Test(expected = ClientException.class)
    public void testGenerateRemovePropertyValueCypherQueryEmptyNodeId() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("nodeId", "");
            put("key", "test_key");
            put("metadata", new HashMap<String, Object>());
        }};
        NodeQueryGenerationUtil.generateRemovePropertyValueCypherQuery(parameterMap);
    }

    @Test(expected = ClientException.class)
    public void testGenerateRemovePropertyValueCypherQueryWithoutKey() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("nodeId", "do_123");
            put("metadata", new HashMap<String, Object>());
        }};
        NodeQueryGenerationUtil.generateRemovePropertyValueCypherQuery(parameterMap);
    }

    @Test
    public void testGenerateUpsertNodeCypherQuery() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("node", getNodeWithoutId());
        }};
        NodeQueryGenerationUtil.generateUpsertNodeCypherQuery(parameterMap);
        Assert.assertTrue(MapUtils.isNotEmpty((Map<String, Object>) parameterMap.get("queryStatementMap")));
    }

    @Test(expected = ClientException.class)
    public void testGenerateUpsertNodeCypherQueryInvalidNode() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("node", null);
        }};
        NodeQueryGenerationUtil.generateUpsertNodeCypherQuery(parameterMap);
    }

    @Test
    public void testGenerateCreateNodeCypherQuery() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("node", getNode());
        }};
        NodeQueryGenerationUtil.generateCreateNodeCypherQuery(parameterMap);
        Assert.assertTrue(StringUtils.isNotEmpty((String) ((Map<String, Object>) ((Map<String, Object>) parameterMap.get("queryStatementMap")).get("do_000000123")).get("query")));
    }

    @Test(expected = ClientException.class)
    public void testGenerateCreateNodeCypherQueryEmptyGraphId() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "");
            put("node", getNode());
        }};
        NodeQueryGenerationUtil.generateCreateNodeCypherQuery(parameterMap);
    }

    @Test(expected = ClientException.class)
    public void testGenerateCreateNodeCypherQueryInvalidNode() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("node", null);
        }};
        NodeQueryGenerationUtil.generateCreateNodeCypherQuery(parameterMap);
    }

    @Test
    public void testGenerateRemovePropertyValuesCypherQuery() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("nodeId", "do_123");
            put("keys", new ArrayList<String>());
            put("metadata", new HashMap<String, Object>());
        }};
        String query = NodeQueryGenerationUtil.generateRemovePropertyValuesCypherQuery(parameterMap);
        Assert.assertTrue(StringUtils.isNoneBlank(query));
        Assert.assertEquals("MATCH(ee:domain) WHERE ee.IL_UNIQUE_ID='do_123' SET ", query);
    }

    @Test(expected = ClientException.class)
    public void testGenerateRemovePropertyValuesCypherQueryEmptyGraphId() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "");
            put("nodeId", "do_123");
            put("keys", new ArrayList<String>());
            put("metadata", new HashMap<String, Object>());
        }};
        NodeQueryGenerationUtil.generateRemovePropertyValuesCypherQuery(parameterMap);
    }

    @Test(expected = ClientException.class)
    public void testGenerateRemovePropertyValuesCypherQueryEmptyNodeId() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("nodeId", "");
            put("keys", new ArrayList<String>());
            put("metadata", new HashMap<String, Object>());
        }};
        NodeQueryGenerationUtil.generateRemovePropertyValuesCypherQuery(parameterMap);
    }

    @Test(expected = ClientException.class)
    public void testGenerateRemovePropertyValuesCypherQueryWithoutKey() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("nodeId", "do_123");
            put("metadata", new HashMap<String, Object>());
        }};
        NodeQueryGenerationUtil.generateRemovePropertyValuesCypherQuery(parameterMap);
    }

    @Test
    public void testGetClassicNodeDeleteCypherQuery() throws Exception {
        NodeQueryGenerationUtil nodeQueryGenerationUtil = new NodeQueryGenerationUtil();
        Method getClassicNodeDeleteCypherQueryMethod = NodeQueryGenerationUtil.class.getDeclaredMethod("getClassicNodeDeleteCypherQuery", String.class, String.class);
        getClassicNodeDeleteCypherQueryMethod.setAccessible(true);
        String query = (String) getClassicNodeDeleteCypherQueryMethod.invoke(nodeQueryGenerationUtil, "domain", "do_123");
        Assert.assertTrue(StringUtils.isNoneBlank(query));
        Assert.assertEquals("MATCH(ee:domain)-[r]-() WHERE ee.IL_UNIQUE_ID='do_123' DELETE ee,  r", query);
    }

    @Test
    public void testGenerateImportNodesCypherQuery() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("nodes", new ArrayList<Node>() {{
                add(getNode());
            }});
        }};
        String query = NodeQueryGenerationUtil.generateImportNodesCypherQuery(parameterMap);
        Assert.assertTrue(StringUtils.isNoneBlank(query));
    }

    @Test(expected = ClientException.class)
    public void testGenerateImportNodesCypherQueryEmptyGraphId() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "");
            put("nodes", new ArrayList<Node>() {{
                add(getNode());
            }});
        }};
        NodeQueryGenerationUtil.generateImportNodesCypherQuery(parameterMap);
    }

    @Test(expected = ClientException.class)
    public void testGenerateImportNodesCypherQueryEmptyNodes() {
        Map<String, Object> parameterMap = new HashMap<>() {{
            put("graphId", "domain");
            put("nodes", new ArrayList<Node>());
        }};
        NodeQueryGenerationUtil.generateImportNodesCypherQuery(parameterMap);
    }


    private Node getNode() {
        Node node = new Node("do_000000123", "DATA_NODE", "Content");
        node.setMetadata(new HashMap<>() {{
            put("status", "Draft");
            put("name", "Test Node");
            put("identifier", "do_000000123");
        }});
        return node;
    }

    private Node getNodeWithoutId() {
        Node node = new Node("domain", new HashMap<>() {{
            put("status", "Draft");
            put("name", "Test Node");
        }});
        return node;
    }
}
