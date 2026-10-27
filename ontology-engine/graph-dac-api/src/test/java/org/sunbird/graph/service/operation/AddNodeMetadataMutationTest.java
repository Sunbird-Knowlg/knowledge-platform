package org.sunbird.graph.service.operation;

import org.junit.Assert;
import org.junit.Test;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.test.BaseTest;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * addNode serialises Lists and Maps to JSON strings on the way to the database and
 * copies those serialised values back onto the node it was handed, so the object the
 * caller keeps no longer matches the shape a graph read produces.
 *
 * <p>Callers that go on to validate or return that node must re-read it instead.
 * VersioningNode.getEditableNode did not, which made the first edit of a published
 * collection fail with "Metadata &lt;field&gt; should be a/an Array value" for every
 * array-typed field without a schema default, while a second attempt succeeded.
 *
 * <p>This pins the behaviour so that a change to it is a deliberate one.
 */
public class AddNodeMetadataMutationTest extends BaseTest {

    @Test
    public void addNodeRewritesListAndMapMetadataOnTheNodeItIsGiven() throws Exception {
        Node node = new Node();
        node.setIdentifier("do_addnode_mutation_probe");
        node.setGraphId("domain");
        node.setNodeType("DATA_NODE");
        node.setObjectType("Content");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", "probe");
        metadata.put("organisation", new ArrayList<>(Arrays.asList("org-1")));
        metadata.put("trackable", new HashMap<String, Object>() {{ put("enabled", "Yes"); }});
        node.setMetadata(metadata);

        Await.result(NodeAsyncOperations.addNode("domain", node), Duration.apply("30s"));

        Object organisation = node.getMetadata().get("organisation");
        Object trackable = node.getMetadata().get("trackable");

        Assert.assertTrue(
                "addNode is expected to leave the list serialised on the caller's node; "
                        + "callers must re-read rather than validate this object",
                organisation instanceof String);
        Assert.assertEquals("[\"org-1\"]", organisation);
        Assert.assertTrue("maps are serialised the same way", trackable instanceof String);

        // Scalars are untouched, so a caller cannot simply assume everything is a String.
        Assert.assertTrue(node.getMetadata().get("name") instanceof String);
        Assert.assertEquals("probe", node.getMetadata().get("name"));
    }

    @Test
    public void readingTheStoredNodeBackGivesTheListShapeAgain() throws Exception {
        Node node = new Node();
        node.setIdentifier("do_addnode_readback_probe");
        node.setGraphId("domain");
        node.setNodeType("DATA_NODE");
        node.setObjectType("Content");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", "readback");
        metadata.put("organisation", new ArrayList<>(Arrays.asList("org-1", "org-2")));
        node.setMetadata(metadata);

        Await.result(NodeAsyncOperations.addNode("domain", node), Duration.apply("30s"));

        Node fromGraph = Await.result(
                SearchAsyncOperations.getNodeByUniqueId("domain", "do_addnode_readback_probe", false, null),
                Duration.apply("30s"));

        Object organisation = fromGraph.getMetadata().get("organisation");
        Assert.assertTrue("a graph read restores the list", organisation instanceof List);
        Assert.assertEquals(Arrays.asList("org-1", "org-2"), organisation);
    }
}
