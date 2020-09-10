package org.sunbird.graph.dac.model;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class FilterTest {
    public static Filter filter_1 = null;
    public static Filter filter_2 = null;
    public static Filter filter_3 = null;
    public static SearchCriteria sc = new SearchCriteria() {{
        setGraphId("domain");
        setNodeType("DATA_NODE");
        setObjectType("Content");
    }};
    public static String param = "";

    @BeforeClass
    public static void init() {
        filter_1 = new Filter();
        filter_2 = new Filter("prop1", "val1");
        filter_3 = new Filter("prop2", "!=", "val2");
    }

    @Test
    public void testFilterModel_1() {
        filter_1.setOperator("");
        Assert.assertEquals("=", filter_2.getOperator());
    }

    @Test
    public void testFilterModel_2() {
        filter_1.setProperty("testProperty");
        filter_1.setValue("testValue");
        filter_1.setOperator("!=");
        Assert.assertEquals("testProperty", filter_1.getProperty());
        Assert.assertEquals("testValue", filter_1.getValue());
        Assert.assertEquals("!=", filter_1.getOperator());
    }

    @Test
    public void testFilterModel_3() {
        filter_1.setProperty("identifier");
        filter_1.setOperator("=");
        String query = filter_1.getCypher(sc, param);
        Assert.assertEquals(" n.IL_UNIQUE_ID = {4} ", query);
    }

    @Test
    public void testFilterModel_4() {
        filter_1.setProperty("identifier");
        filter_1.setOperator("like");
        String query = filter_1.getCypher(sc, param);
        Assert.assertEquals(" n.IL_UNIQUE_ID =~ {5} ", query);
    }

    @Test
    public void testFilterModel_5() {
        filter_1.setProperty("identifier");
        filter_1.setOperator("startsWith");
        String query = filter_1.getCypher(sc, param);
        Assert.assertEquals(" n.IL_UNIQUE_ID =~ {6} ", query);
    }

    @Test
    public void testFilterModel_6() {
        filter_1.setProperty("identifier");
        filter_1.setOperator("endsWith");
        String query = filter_1.getCypher(sc, param);
        Assert.assertEquals(" n.IL_UNIQUE_ID =~ {7} ", query);
    }

    @Test
    public void testFilterModel_7() {
        filter_1.setProperty("identifier");
        filter_1.setOperator(">");
        String query = filter_1.getCypher(sc, param);
        Assert.assertEquals(" n.IL_UNIQUE_ID > {8} ", query);
    }

    @Test
    public void testFilterModel_8() {
        filter_1.setProperty("identifier");
        filter_1.setOperator(">=");
        String query = filter_1.getCypher(sc, param);
        Assert.assertEquals(" n.IL_UNIQUE_ID >= {9} ", query);
    }

    @Test
    public void testFilterModel_9() {
        filter_1.setProperty("identifier");
        filter_1.setOperator("<");
        String query = filter_1.getCypher(sc, param);
        Assert.assertEquals(" n.IL_UNIQUE_ID < {10} ", query);
    }

    @Test
    public void testFilterModel_10() {
        filter_1.setProperty("identifier");
        filter_1.setOperator("<=");
        String query = filter_1.getCypher(sc, param);
        Assert.assertEquals(" n.IL_UNIQUE_ID <= {1} ", query);
    }

    @Test
    public void testFilterModel_11() {
        filter_1.setProperty("identifier");
        filter_1.setOperator("!=");
        String query = filter_1.getCypher(sc, param);
        Assert.assertEquals(" NOT n.IL_UNIQUE_ID = {2} ", query);
    }

    @Test
    public void testFilterModel_12() {
        filter_1.setProperty("identifier");
        filter_1.setOperator("in");
        String query = filter_1.getCypher(sc, param);
        Assert.assertEquals(" n.IL_UNIQUE_ID in {3} ", query);
    }

}
