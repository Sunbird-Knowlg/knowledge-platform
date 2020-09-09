package org.sunbird.graph.dac.model;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class FilterTest {
    public static Filter filter_1 = null;
    public static Filter filter_2 = null;
    public static Filter filter_3 = null;

    @BeforeClass
    public static void init() {
        filter_1 = new Filter();
        filter_2 = new Filter("prop1", "val1");
        filter_3 = new Filter("prop2", "!=", "val2");
    }

    @Test
    public void testFilterModel_1() {
        filter_1.setProperty("testProperty");
        filter_1.setValue("testValue");
        filter_1.setOperator("!=");
        Assert.assertEquals("testProperty", filter_1.getProperty());
        Assert.assertEquals("testValue", filter_1.getValue());
        Assert.assertEquals("!=", filter_1.getOperator());
    }

    @Test
    public void testFilterModel_2() {
        SearchCriteria sc = new SearchCriteria() {{
           setGraphId("domain");
           setNodeType("DATA_NODE");
           setObjectType("Content");
        }};
        String param = "";
        filter_1.getCypher(sc, param);
    }

}
