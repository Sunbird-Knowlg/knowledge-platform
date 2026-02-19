package org.sunbird.test;

import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.sunbird.graph.service.util.DriverUtil;

import java.lang.reflect.Field;
import java.util.Map;

public class BaseTest {

    protected static JanusGraph graph;

    @BeforeClass
    public static void setup() throws Exception {
        // 1. Setup Embedded In-Memory JanusGraph
        graph = JanusGraphFactory.build().set("storage.backend", "inmemory").open();
        // Register the graph instance in DriverUtil
        injectJanusGraph("domain", graph);
        injectJanusGraph("graphId", graph);
    }

    @AfterClass
    public static void tearDown() {
        if (graph != null) {
            graph.close();
        }
    }

    private static void injectJanusGraph(String graphId, JanusGraph graph) throws Exception {
        Field field = DriverUtil.class.getDeclaredField("janusGraphMap");
        field.setAccessible(true);
        Map<String, JanusGraph> map = (Map<String, JanusGraph>) field.get(null);
        map.put(graphId, graph);
    }

    // Helper for tests to Create Nodes using Native API
    protected void createBulkNodes() {
        String[] ids = { "do_0000123", "do_0000234", "do_0000345" };
        org.janusgraph.core.JanusGraphTransaction tx = graph.newTransaction();
        try {
            for (String id : ids) {
                if (!tx.query().has("IL_UNIQUE_ID", id).vertices().iterator().hasNext()) {
                    org.janusgraph.core.JanusGraphVertex v = tx.addVertex("domain");
                    v.property("IL_UNIQUE_ID", id);
                    v.property("graphId", "domain");
                    v.property("IL_SYS_NODE_TYPE", "DATA_NODE");
                    v.property("IL_FUNC_OBJECT_TYPE", "Content");
                }
            }
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            e.printStackTrace();
        }
    }
}
