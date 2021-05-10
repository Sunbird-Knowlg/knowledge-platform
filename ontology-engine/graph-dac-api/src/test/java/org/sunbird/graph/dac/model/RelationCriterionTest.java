package org.sunbird.graph.dac.model;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RelationCriterionTest {

    @Test
    public void testRelationCriterion_1() {
        RelationCriterion relationCriterion = new RelationCriterion("", "Content");

        List<RelationCriterion> list2 = new ArrayList<>(){{add(new RelationCriterion("",""));}};
        relationCriterion.setName("test_name");
        relationCriterion.setFromDepth(1);
        relationCriterion.setObjectType("Content");
        relationCriterion.setRelations(list2);
        relationCriterion.setOp("test");
        relationCriterion.setOptional(false);
        relationCriterion.setToDepth(1);

        Assert.assertEquals("test_name", relationCriterion.getName());
        Assert.assertEquals("AND", relationCriterion.getOp());
        Assert.assertEquals("Content", relationCriterion.getObjectType());
        Assert.assertEquals(1, relationCriterion.getToDepth());
        Assert.assertEquals(1, relationCriterion.getFromDepth());
        Assert.assertEquals(false, relationCriterion.isOptional());
        Assert.assertTrue(!relationCriterion.getRelations().isEmpty());
    }

    @Test
    public void testRelationCriterion_2() {
        List<RelationFilter> list = new ArrayList<RelationFilter>() {{add(new RelationFilter("testName"));}};
        RelationCriterion relationCriterion_2 = new RelationCriterion(list, "Content");
        relationCriterion_2.setOp("OR");
        relationCriterion_2.setIdentifiers(new ArrayList<>(){{add("do_123");}});
        relationCriterion_2.addRelationCriterion(relationCriterion_2);

        Assert.assertEquals("OR", relationCriterion_2.getOp());
        Assert.assertTrue(!relationCriterion_2.getIdentifiers().isEmpty());
        Assert.assertTrue(!relationCriterion_2.getRelations().isEmpty());
    }

    @Test
    public void testRelationCriterion_3() {
        List<RelationFilter> list = new ArrayList<RelationFilter>() {{add(new RelationFilter("testName"));}};
        RelationCriterion relationCriterion_3 = new RelationCriterion("", "Content");
        relationCriterion_3.setFilters(list);
        relationCriterion_3.addIdentifier("do_123");
        relationCriterion_3.setIdentifiers(new ArrayList<>() {{add("do_123");}});

        MetadataCriterion mc = MetadataCriterion.create(new ArrayList<>() {{add(new Filter("IL_UNIQUE_ID", SearchConditions.OP_EQUAL));}});
        List<MetadataCriterion> listMc = new ArrayList<>() {{add(mc);}};
        relationCriterion_3.setMetadata(listMc);
        relationCriterion_3.addMetadata(mc);

        SearchCriteria sc = new SearchCriteria();
        relationCriterion_3.getCypher(sc, "prevParam");

        Assert.assertTrue(!relationCriterion_3.getFilters().isEmpty());
    }

    @Test
    public void testRelationCriterion_4() {
        RelationCriterion relationCriterion_4 = new RelationCriterion("", "Content");
        relationCriterion_4.setDirection(RelationCriterion.DIRECTION.IN);
        relationCriterion_4.addIdentifier("do_123");
        relationCriterion_4.setIdentifiers(new ArrayList<>() {{add("do_123");}});

        MetadataCriterion mc = MetadataCriterion.create(new ArrayList<>() {{add(new Filter("IL_UNIQUE_ID", SearchConditions.OP_EQUAL));}});
        List<MetadataCriterion> listMc = new ArrayList<>() {{add(mc);}};
        relationCriterion_4.setMetadata(listMc);
        relationCriterion_4.addMetadata(mc);

        SearchCriteria sc = new SearchCriteria();
        relationCriterion_4.getCypher(sc, "prevParam");

        Assert.assertEquals(RelationCriterion.DIRECTION.IN, relationCriterion_4.getDirection());
    }
}
