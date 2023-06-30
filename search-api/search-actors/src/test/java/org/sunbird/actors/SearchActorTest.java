package org.sunbird.actors;

import org.junit.Assert;
import org.junit.Test;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchActorTest extends SearchBaseActorTest {

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchMediumAndSubject() throws Exception {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        List<String> exists = Arrays.asList("medium", "subject");
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        request.put("limit", 5);
        request.put("exists", exists);
        Map<String, Object> sort = new HashMap<String, Object>();
        sort.put("identifier", "asc");
        request.put("sort_by", sort);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() == 2);
        Map<String, Object> content1 = (Map<String, Object>) list.get(0);
        Assert.assertTrue(content1.get("subject") instanceof List);
        Assert.assertTrue(content1.get("medium") instanceof List);
        Map<String, Object> content2 = (Map<String, Object>) list.get(1);
        Assert.assertTrue(content2.get("subject") instanceof List);
        Assert.assertTrue(content2.get("medium") instanceof List);
    }

}
