package org.sunbird.search;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.processor.SearchProcessor;
import org.sunbird.search.util.SearchConstants;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

public class SubsetOfSearchTest extends BaseSearchTest {

    private static final Duration AWAIT_TIMEOUT = Duration.create(10, TimeUnit.SECONDS);
    private static SearchProcessor processor;

    @BeforeClass
    public static void beforeTest() throws Exception {
        createCompositeSearchIndex();
        processor = new SearchProcessor();
        add("do_only_s1", "fw_a", Arrays.asList("s1"));
        add("do_s1_s2", "fw_a", Arrays.asList("s1", "s2"));
        add("do_s1_s2_s3", "fw_a", Arrays.asList("s1", "s2", "s3"));
        add("do_s1_s4", "fw_a", Arrays.asList("s1", "s4"));
        add("do_only_s4", "fw_a", Arrays.asList("s4"));
        add("do_no_skill", "fw_a", null);
        add("do_upper_s1", "fw_a", Arrays.asList("S1"));
        add("do_other_fw", "fw_b", Arrays.asList("s1"));
        Thread.sleep(3000);
    }

    private static void add(String id, String framework, List<String> skills) throws Exception {
        Map<String, Object> doc = new HashMap<>();
        doc.put("identifier", id);
        doc.put("objectType", "Collection");
        doc.put("primaryCategory", "Course");
        doc.put("status", "Live");
        doc.put("competencyFramework", framework);
        if (skills != null) doc.put("skill", skills);
        addToIndex(id, doc);
    }

    private static Map<String, Object> property(String name, String operation, List<?> values) {
        Map<String, Object> p = new HashMap<>();
        p.put(SearchConstants.propertyName, name);
        p.put(SearchConstants.operation, operation);
        p.put(SearchConstants.values, values);
        return p;
    }

    private Set<String> search(List<Map> properties) throws Exception {
        SearchDTO dto = new SearchDTO();
        dto.setProperties(properties);
        dto.setLimit(100);
        dto.setOperation(SearchConstants.SEARCH_OPERATION_AND);
        Map<String, Object> result = Await.result(processor.processSearch(dto, true), AWAIT_TIMEOUT);
        Set<String> ids = new TreeSet<>();
        for (Object row : (List<?>) result.get("results")) ids.add((String) ((Map<?, ?>) row).get("identifier"));
        return ids;
    }

    @Test
    public void returnsOnlyDocumentsWhoseValuesAreAllInTheList() throws Exception {
        List<Map> properties = new ArrayList<>();
        properties.add(property("skill", SearchConstants.SEARCH_OPERATION_SUBSET_OF, Arrays.asList("s1", "s2", "s3")));
        properties.add(property("competencyFramework", SearchConstants.SEARCH_OPERATION_EQUAL, Arrays.asList("fw_a")));

        Assert.assertEquals(new TreeSet<>(Arrays.asList("do_only_s1", "do_s1_s2", "do_s1_s2_s3", "do_upper_s1")),
                search(properties));
    }

    @Test
    public void respectsTheOtherFilters() throws Exception {
        List<Map> properties = new ArrayList<>();
        properties.add(property("skill", SearchConstants.SEARCH_OPERATION_SUBSET_OF, Arrays.asList("s1")));

        Set<String> ids = search(properties);
        Assert.assertTrue(ids.contains("do_other_fw"));

        properties.add(property("competencyFramework", SearchConstants.SEARCH_OPERATION_EQUAL, Arrays.asList("fw_a")));
        Assert.assertEquals(new TreeSet<>(Arrays.asList("do_only_s1", "do_upper_s1")), search(properties));
    }
}
