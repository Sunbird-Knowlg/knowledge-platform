package org.sunbird.graph.dac.model;

import org.junit.Assert;
import org.junit.Test;

public class RelationFilterTest {

    @Test
    public void testRelationFilter() {
        RelationFilter relationFilter_1 = new RelationFilter("");
        RelationFilter relationFilter_2 = new RelationFilter("testName", 1);
        RelationFilter relationFilter_3 = new RelationFilter("testName", 1, 1);
        relationFilter_1.setName("test_name");
        relationFilter_1.setDirection("out");
        relationFilter_1.setFromDepth(1);
        relationFilter_1.setToDepth(1);

        Assert.assertEquals("test_name", relationFilter_1.getName());
        Assert.assertEquals("out", relationFilter_1.getDirection());
        Assert.assertEquals(1, relationFilter_1.getToDepth());
        Assert.assertEquals(1, relationFilter_1.getFromDepth());
    }
}
