package org.sunbird.test;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.sunbird.graph.service.util.DriverUtil;

import java.lang.reflect.Field;
import java.util.Map;

public class BaseTest {

    protected static JanusGraph graph;
    protected static GraphTraversalSource g;

    @BeforeClass
    public static void setup() throws Exception {
        // 1. Setup Embedded In-Memory JanusGraph
        graph = JanusGraphFactory.build().set("storage.backend", "inmemory").open();
        g = graph.traversal();

        // 2. Inject this TraversalSource into DriverUtil using Reflection
        injectGraphTraversalSource("domain", "write", g);
        injectGraphTraversalSource("domain", "read", g);
        injectGraphTraversalSource("graphId", "write", g); // used in some tests
        injectGraphTraversalSource("graphId", "read", g);
    }

    @AfterClass
    public static void tearDown() {
        if (graph != null) {
            graph.close();
        }
    }

    private static void injectGraphTraversalSource(String graphId, String operation, GraphTraversalSource g)
            throws Exception {
        String driverKey = graphId + "_" + operation;

        Field field = DriverUtil.class.getDeclaredField("graphTraversalSourceMap");
        field.setAccessible(true);
        Map<String, GraphTraversalSource> map = (Map<String, GraphTraversalSource>) field.get(null);
        map.put(driverKey, g);
    }

    // Helper for tests to Create Nodes using Gremlin
    protected void createBulkNodes() {
        GraphTraversalSource infoG = graph.traversal();
        // Create nodes: do_0000123, do_0000234, do_0000345
        String[] ids = { "do_0000123", "do_0000234", "do_0000345" };
        for (String id : ids) {
            if (!infoG.V().has("IL_UNIQUE_ID", id).hasNext()) {
                infoG.addV("domain")
                        .property("IL_UNIQUE_ID", id)
                        .property("graphId", "domain") // Added graphId property
                        .property("IL_SYS_NODE_TYPE", "DATA_NODE")
                        .property("IL_FUNC_OBJECT_TYPE", "Content")
                        .next();
            }
        }
        infoG.tx().commit();
        try {
            infoG.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
