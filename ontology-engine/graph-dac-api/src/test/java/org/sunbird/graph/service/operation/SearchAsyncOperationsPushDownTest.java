package org.sunbird.graph.service.operation;

import org.janusgraph.core.JanusGraphTransaction;
import org.janusgraph.core.JanusGraphVertex;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sunbird.graph.dac.model.Filter;
import org.sunbird.graph.dac.model.MetadataCriterion;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.SearchConditions;
import org.sunbird.graph.dac.model.SearchCriteria;
import org.sunbird.test.BaseTest;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Covers the indexed-filter push-down in {@link SearchAsyncOperations#executeNativeSearch}.
 *
 * <p>The push-down only narrows which vertices the in-memory filter runs over, so every case
 * here asserts on the returned nodes: pushing predicates down must never change the result.
 */
public class SearchAsyncOperationsPushDownTest extends BaseTest {

    private static final String GRAPH_ID = "domain";

    @BeforeClass
    public static void seed() {
        JanusGraphTransaction tx = graph.newTransaction();
        try {
            addNode(tx, "cat_board", "Category", "DATA_NODE", "Live", "board");
            addNode(tx, "cat_medium", "Category", "DATA_NODE", "Live", "medium");
            addNode(tx, "cat_retired", "Category", "DATA_NODE", "Retired", "gradeLevel");
            addNode(tx, "term_english", "Term", "DATA_NODE", "Live", "english");
            addNode(tx, "content_one", "Content", "DATA_NODE", "Draft", "course-one");
            addNode(tx, "def_node", "Category", "DEFINITION_NODE", "Live", "definition");
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
    }

    private static void addNode(JanusGraphTransaction tx, String id, String objectType,
                                String nodeType, String status, String code) {
        if (tx.query().has("IL_UNIQUE_ID", id).vertices().iterator().hasNext())
            return;
        JanusGraphVertex v = tx.addVertex(GRAPH_ID);
        v.property("IL_UNIQUE_ID", id);
        v.property("graphId", GRAPH_ID);
        v.property("IL_FUNC_OBJECT_TYPE", objectType);
        v.property("IL_SYS_NODE_TYPE", nodeType);
        v.property("status", status);
        v.property("code", code);
        // Not in the indexed allowlist - exercises the in-memory filter path.
        v.property("description", code + "-desc");
    }

    private static List<String> identifiers(SearchCriteria sc) throws Exception {
        Future<List<Node>> future = SearchAsyncOperations.getNodeByUniqueIds(GRAPH_ID, sc);
        List<Node> nodes = Await.result(future, Duration.apply("30s"));
        return nodes.stream().map(Node::getIdentifier).sorted().collect(Collectors.toList());
    }

    private static SearchCriteria criteria(MetadataCriterion mc) {
        SearchCriteria sc = new SearchCriteria();
        sc.addMetadata(mc);
        sc.setCountQuery(false);
        return sc;
    }

    /** The master-category query shape that regressed: equality filters carried as metadata. */
    @Test
    public void testAndEqualityFiltersReturnOnlyMatchingNodes() throws Exception {
        MetadataCriterion mc = MetadataCriterion.create(new ArrayList<Filter>(Arrays.asList(
                new Filter("IL_FUNC_OBJECT_TYPE", SearchConditions.OP_EQUAL, "Category"),
                new Filter("IL_SYS_NODE_TYPE", SearchConditions.OP_EQUAL, "DATA_NODE"),
                new Filter("status", SearchConditions.OP_NOT_EQUAL, "Retired"))));

        Assert.assertEquals(Arrays.asList("cat_board", "cat_medium"), identifiers(criteria(mc)));
    }

    /**
     * status is pushed down as an equality, so this also proves a pushed predicate does not
     * over-filter when combined with others.
     */
    @Test
    public void testEqualityOnPushedKeyNarrowsCorrectly() throws Exception {
        MetadataCriterion mc = MetadataCriterion.create(new ArrayList<Filter>(Arrays.asList(
                new Filter("IL_FUNC_OBJECT_TYPE", SearchConditions.OP_EQUAL, "Category"),
                new Filter("status", SearchConditions.OP_EQUAL, "Retired"))));

        Assert.assertEquals(Arrays.asList("cat_retired"), identifiers(criteria(mc)));
    }

    /** DEFINITION_NODE must be excluded - the nodeType predicate has to be honoured. */
    @Test
    public void testNodeTypeFilterExcludesDefinitionNodes() throws Exception {
        MetadataCriterion mc = MetadataCriterion.create(new ArrayList<Filter>(Arrays.asList(
                new Filter("IL_FUNC_OBJECT_TYPE", SearchConditions.OP_EQUAL, "Category"),
                new Filter("IL_SYS_NODE_TYPE", SearchConditions.OP_EQUAL, "DEFINITION_NODE"))));

        Assert.assertEquals(Arrays.asList("def_node"), identifiers(criteria(mc)));
    }

    /**
     * OR criteria must NOT be pushed down: an OR branch need not hold for every match, so
     * pushing it would drop the nodes that matched the other branch.
     */
    @Test
    public void testOrCriterionIsNotPushedDown() throws Exception {
        MetadataCriterion mc = MetadataCriterion.create(new ArrayList<Filter>(Arrays.asList(
                new Filter("IL_FUNC_OBJECT_TYPE", SearchConditions.OP_EQUAL, "Term"),
                new Filter("IL_FUNC_OBJECT_TYPE", SearchConditions.OP_EQUAL, "Content"))));
        mc.setOp(SearchConditions.LOGICAL_OR);

        Assert.assertEquals(Arrays.asList("content_one", "term_english"), identifiers(criteria(mc)));
    }

    /** An indexed key filters correctly once pushed onto the graph query. */
    @Test
    public void testIndexedKeyFilterIsCorrect() throws Exception {
        MetadataCriterion mc = MetadataCriterion.create(new ArrayList<Filter>(Arrays.asList(
                new Filter("code", SearchConditions.OP_EQUAL, "english"))));

        Assert.assertEquals(Arrays.asList("term_english"), identifiers(criteria(mc)));
    }

    /** A key outside the indexed allowlist is never pushed, and still filters in memory. */
    @Test
    public void testNonIndexedKeyStillFilters() throws Exception {
        MetadataCriterion mc = MetadataCriterion.create(new ArrayList<Filter>(Arrays.asList(
                new Filter("description", SearchConditions.OP_EQUAL, "english-desc"))));

        Assert.assertEquals(Arrays.asList("term_english"), identifiers(criteria(mc)));
    }

    /** Mixed indexed and non-indexed predicates must both be applied. */
    @Test
    public void testIndexedAndNonIndexedFiltersCombine() throws Exception {
        MetadataCriterion mc = MetadataCriterion.create(new ArrayList<Filter>(Arrays.asList(
                new Filter("IL_FUNC_OBJECT_TYPE", SearchConditions.OP_EQUAL, "Category"),
                new Filter("description", SearchConditions.OP_EQUAL, "board-desc"))));

        Assert.assertEquals(Arrays.asList("cat_board"), identifiers(criteria(mc)));
    }

    /** No metadata at all - every node in the graph for this graphId. */
    @Test
    public void testNoMetadataReturnsAllNodes() throws Exception {
        SearchCriteria sc = new SearchCriteria();
        sc.setCountQuery(false);
        Assert.assertTrue(identifiers(sc).containsAll(Arrays.asList("cat_board", "term_english", "content_one")));
    }
}
