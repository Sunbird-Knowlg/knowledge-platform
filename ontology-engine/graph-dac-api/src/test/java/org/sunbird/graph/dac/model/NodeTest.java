package org.sunbird.graph.dac.model;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sunbird.graph.common.enums.SystemProperties;

import java.util.ArrayList;
import java.util.HashMap;

public class NodeTest {
    public static Node node_1 = null;
    public static Node node_2 = null;
    public static Node node_3 = null;

    @BeforeClass
    public static void init() {
        node_1 = new Node();
        node_2 = new Node("domain", "DATA_NODE", "Content");
        node_3 = new Node("domain", new HashMap<>() {{
            put(SystemProperties.IL_UNIQUE_ID.name(), "do_1234");
            put(SystemProperties.IL_SYS_NODE_TYPE.name(), "DATA_NODE");
            put(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), "Content");
        }});
    }

    @Test
    public void testNodeModel_1() throws Exception {
        node_1.setId(30190391);
        node_1.setGraphId("domain");
        node_1.setIdentifier("do_1234");
        node_1.setNodeType("DATA_NODE");
        node_1.setObjectType("Content");

        Assert.assertEquals(30190391, node_1.getId());
        Assert.assertEquals("do_1234", node_1.getIdentifier());
        Assert.assertEquals("domain", node_1.getGraphId());
        Assert.assertEquals("DATA_NODE", node_1.getNodeType());
        Assert.assertEquals("Content", node_1.getObjectType());
    }

    @Test
    public void testNodeModel_2() throws Exception {
        node_2.setMetadata(new HashMap<>() {{
            put("status", "Live");
        }});
        node_2.setExternalData(new HashMap<>() {{
            put("body", "<p>I'm a test body</p>");
        }});

        Assert.assertNotNull(node_2.getMetadata());
        Assert.assertNotNull(node_2.getExternalData());
        Assert.assertEquals("Live", node_2.getMetadata().get("status"));
        Assert.assertEquals("<p>I'm a test body</p>", node_2.getExternalData().get("body"));
    }

    @Test
    public void testNodeModel_3() throws Exception {
        node_3.setOutRelations(new ArrayList<>() {{
            add(new Relation("do_1234", "associatedTo", "do_5678"));
        }});
        node_3.setInRelations(new ArrayList<>() {{
            add(new Relation("do_1357", "associatedTo", "do_1234"));
        }});
        node_3.setAddedRelations(new ArrayList<>() {{
            add(new Relation("do_1234", "associatedTo", "do_5678"));
            add(new Relation("do_1357", "associatedTo", "do_1234"));
        }});
        node_3.setDeletedRelations(new ArrayList<>() {{
            add(new Relation("do_1234", "associatedTo", "do_2468"));
        }});
        node_3.setRelationNodes(new HashMap<>() {{
            put("do_894", node_1);
            put("do_47389", node_2);
        }});

        Assert.assertNotNull(node_3.getOutRelations());
        Assert.assertNotNull(node_3.getInRelations());
        Assert.assertNotNull(node_3.getAddedRelations());
        Assert.assertNotNull(node_3.getDeletedRelations());
        Assert.assertNotNull(node_3.getRelationNodes());
        Assert.assertNotNull(node_3.getRelationNode("do_894"));

        Assert.assertEquals("do_5678", node_3.getOutRelations().get(0).getEndNodeId());
        Assert.assertEquals("do_1234", node_3.getInRelations().get(0).getEndNodeId());
        Assert.assertEquals("do_5678", node_3.getAddedRelations().get(0).getEndNodeId());
        Assert.assertEquals("do_2468", node_3.getDeletedRelations().get(0).getEndNodeId());
        Assert.assertEquals("DATA_NODE", node_3.getRelationNode("do_47389").getNodeType());

    }
}
