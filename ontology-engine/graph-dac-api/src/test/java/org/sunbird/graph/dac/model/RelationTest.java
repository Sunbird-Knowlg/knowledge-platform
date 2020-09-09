package org.sunbird.graph.dac.model;

import org.junit.BeforeClass;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Relationship;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.common.enums.SystemProperties;

import java.util.HashMap;

public class RelationTest {
    public static Relation relation_1 = null;
    public static Relation relation_2 = null;
    public static Relation relation_3 = null;

    @BeforeClass
    public static void init() {
        relation_1 = new Relation();
        relation_2 = new Relation("start_1234", "DATA_NODE", "end_1234");
//        relation_3 = new Relation("domain", );
    }


}
