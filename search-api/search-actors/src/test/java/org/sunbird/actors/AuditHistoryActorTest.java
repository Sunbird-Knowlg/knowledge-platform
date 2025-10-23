package org.sunbird.actors;

import org.apache.pekko.testkit.TestKit;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.graph.common.enums.AuditProperties;
import org.sunbird.search.client.ElasticSearchUtil;
import org.sunbird.search.util.SearchConstants;
import scala.concurrent.duration.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
public class AuditHistoryActorTest extends SearchBaseActorTest{

    @BeforeClass
    public static void before() throws Exception {
        createAuditIndex();
        Thread.sleep(3000);
    }

    @AfterClass
    public static void after() throws Exception {
        System.out.println("deleting index: " + SearchConstants.COMPOSITE_SEARCH_INDEX);
        ElasticSearchUtil.deleteIndex(SearchConstants.COMPOSITE_SEARCH_INDEX);
        TestKit.shutdownActorSystem(system, Duration.create(2, TimeUnit.SECONDS), true);
        system = null;
    }

    boolean traversal = true;
    @SuppressWarnings("unchecked")
    @Test
    public void testReadAuditHistory(){
        Request request = getAuditRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        Map<String, Object> sort = new HashMap<String, Object>();
        sort.put(AuditProperties.createdOn.name(), "desc");
        sort.put(AuditProperties.operation.name(), "desc");
        List<String> fields = new ArrayList<String>();
        fields.add("audit_id");
        fields.add("label");
        fields.add("objectId");
        fields.add("objectType");
        fields.add("operation");
        fields.add("requestId");
        fields.add("userId");
        fields.add("graphId");
        fields.add("createdOn");
        fields.add("logRecord");
        filters.put("graphId","domain");
        filters.put("objectId","1234");
        request.put("filters", filters);
        request.put("sort_by", sort);
        request.put("traversal", traversal);
        request.put("fields", fields);
        request.put("ACTOR","learning.platform");
        request.getContext().put("CHANNEL_ID","in.ekstep");
        request.getContext().put( "ENV","search");
        Response response = getAuditResponse(request);
        Map<String, Object> result = response.getResult();
        Map<String, Object> auditHistoryRecord = (Map<String, Object>) result.get(AuditProperties.audit_history_record.name());
        Assert.assertNotNull(auditHistoryRecord);
        int count = (int) auditHistoryRecord.get("count");
        Assert.assertTrue(count > 1);
        List<Map<String, Object>> results = (List<Map<String, Object>>) auditHistoryRecord.get("results");
        Assert.assertNotNull(results);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testForNullGraphId(){
        Request request = getAuditRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        Map<String, Object> sort = new HashMap<String, Object>();
        sort.put("createdOn", "desc");
        sort.put("operation", "desc");
        List<String> fields = new ArrayList<String>();
        fields.add("audit_id");
        fields.add("label");
        fields.add("objectId");
        fields.add("objectType");
        fields.add("operation");
        fields.add("requestId");
        fields.add("userId");
        fields.add("graphId");
        fields.add("createdOn");
        fields.add("logRecord");
        filters.put("graph_id","");
        filters.put("objectId","1234");
        request.put("filters", filters);
        request.put("sort_by", sort);
        request.put("traversal", traversal);
        request.put("fields", fields);
        request.put("ACTOR","learning.platform");
        request.getContext().put("CHANNEL_ID","in.ekstep");
        request.getContext().put( "ENV","search");
        Response response = getAuditResponse(request);
        Map<String, Object> result = response.getResult();
        Map<String, Object> auditHistoryRecord = (Map<String, Object>) result.get("audit_history_record");
        List<Map<String, Object>> results = (List<Map<String, Object>>) auditHistoryRecord.get("results");
        Assert.assertTrue(results.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testInvalidLogRecord(){
        Request request = getAuditRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        Map<String, Object> sort = new HashMap<String, Object>();
        sort.put("createdOn", "desc");
        sort.put("operation", "desc");
        List<String> fields = new ArrayList<String>();
        fields.add("audit_id");
        fields.add("label");
        fields.add("objectId");
        fields.add("objectType");
        fields.add("operation");
        fields.add("requestId");
        fields.add("userId");
        fields.add("graphId");
        fields.add("createdOn");
        fields.add("logRecord");
        filters.put("graphId","domain");
        filters.put("objectId","do_113807000868651008130");
        request.put("fuzzy",true);
        request.put("exists","invalidExists");
        request.put("not_exists","invalidNotExists");
        request.put("filters", filters);
        request.put("sort_by", sort);
        request.put("traversal", traversal);
        request.put("fields", fields);
        request.put("ACTOR","learning.platform");
        request.getContext().put("CHANNEL_ID","in.ekstep");
        request.getContext().put( "ENV","search");
        Response response = getAuditResponse(request);
        Map<String, Object> result = response.getResult();
        String message = (String) result.get("messages");
        Assert.assertTrue(message.contains("Unable to parse data! | Error is:"));
    }


    @SuppressWarnings("unchecked")
    @Test
    public void testWeightages(){
        Request request = getAuditRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        Map<String, Object> sort = new HashMap<String, Object>();
        sort.put("createdOn", "desc");
        sort.put("operation", "desc");
        List<String> fields = new ArrayList<String>();
        fields.add("audit_id");
        fields.add("label");
        fields.add("objectId");
        fields.add("objectType");
        fields.add("operation");
        fields.add("requestId");
        fields.add("userId");
        fields.add("graphId");
        fields.add("createdOn");
        fields.add("logRecord");
        filters.put("graphId","domain");
        filters.put("objectId","1234");
        filters.put("objectType","Content");
        filters.put("relatedBoards","invalidRelatedBoards");
        request.put("fuzzy",true);
        request.put("filters", filters);
        request.put("sort_by", sort);
        request.put("traversal", traversal);
        request.put("fields", fields);
        request.put("mode", "soft");
        request.put("ACTOR","learning.platform");
        request.getContext().put("CHANNEL_ID","in.ekstep");
        request.getContext().put( "ENV","search");
        Response response = getAuditResponse(request);
        Map<String, Object> result = response.getResult();
        String message = (String) result.get("messages");
        Assert.assertTrue(message.contains("Unable to parse data! | Error is:"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testnullLogRecord(){
        Request request = getAuditRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        Map<String, Object> sort = new HashMap<String, Object>();
        sort.put("createdOn", "desc");
        sort.put("operation", "desc");
        List<String> fields = new ArrayList<String>();
        fields.add("audit_id");
        fields.add("label");
        fields.add("objectId");
        fields.add("objectType");
        fields.add("operation");
        fields.add("requestId");
        fields.add("userId");
        fields.add("graphId");
        fields.add("createdOn");
        fields.add("logRecord");
        filters.put("graphId","domain");
        filters.put("objectId","do_113807000868651008131");
        request.put("filters", filters);
        request.put("sort_by", sort);
        request.put("traversal", traversal);
        request.put("fields", fields);
        request.put("ACTOR","learning.platform");
        request.getContext().put("CHANNEL_ID","in.ekstep");
        request.getContext().put( "ENV","search");
        Response response = getAuditResponse(request);
        Map<String, Object> result = response.getResult();
        Map<String, Object> auditHistoryRecord = (Map<String, Object>) result.get("audit_history_record");
        Assert.assertNotNull(auditHistoryRecord);
        Assert.assertFalse(auditHistoryRecord.containsKey("logRecord"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testInvalidValues(){
        Request request = getAuditRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        Map<String, Object> sort = new HashMap<String, Object>();
        sort.put("createdOn", "desc");
        sort.put("operation", "desc");
        List<String> fields = new ArrayList<String>();
        fields.add("audit_id");
        fields.add("label");
        fields.add("objectId");
        fields.add("objectType");
        fields.add("operation");
        fields.add("requestId");
        fields.add("userId");
        fields.add("graphId");
        fields.add("createdOn");
        fields.add("logRecord");
        filters.put("graphId","");
        filters.put("objectId","");
        filters.put("tags","invalidTags");
        request.put("filters", filters);
        request.put("sort_by", sort);
        request.put("traversal", traversal);
        request.put("fields", fields);
        request.put("ACTOR","learning.platform");
        request.getContext().put("CHANNEL_ID","in.ekstep");
        request.getContext().put( "ENV","search");
        Response response = getAuditResponse(request);
        Map<String, Object> result = response.getResult();
        Map<String, Object> auditHistoryRecord = (Map<String, Object>) result.get("audit_history_record");
        List<Map<String, Object>> results = (List<Map<String, Object>>) auditHistoryRecord.get("results");
        Assert.assertTrue(results.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNullValues(){
        Request request = getAuditRequest();
        request.put("filters", null);
        request.put("sort_by", null);
        request.put("traversal", null);
        request.put("fuzzy",null );
        request.put("fields", null);
        request.put("ACTOR","learning.platform");
        request.getContext().put("CHANNEL_ID","in.ekstep");
        request.getContext().put( "ENV","search");
        Response response = getAuditResponse(request);
        Map<String, Object> result = response.getResult();
        Assert.assertTrue(result.isEmpty());
    }


    @SuppressWarnings("unchecked")
    @Test
    public void testInvalidOperation(){
        Request request = nullOperationRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        Map<String, Object> sort = new HashMap<String, Object>();
        sort.put("createdOn", "desc");
        sort.put("operation", "desc");
        List<String> fields = new ArrayList<String>();
        fields.add("audit_id");
        fields.add("label");
        fields.add("objectId");
        fields.add("objectType");
        fields.add("operation");
        fields.add("requestId");
        fields.add("userId");
        fields.add("graphId");
        fields.add("createdOn");
        fields.add("logRecord");
        filters.put("graphId","domain");
        filters.put("objectId","1234");
        request.put("filters", filters);
        request.put("sort_by", sort);
        request.put("traversal", traversal);
        request.put("fields", fields);
        request.put("ACTOR","learning.platform");
        request.getContext().put("CHANNEL_ID","in.ekstep");
        request.getContext().put( "ENV","search");
        Response response = getAuditResponse(request);
        Map<String, Object> result = response.getResult();
        String message = (String) result.get("messages");
        Assert.assertTrue(message.contains("Unsupported operation"));
    }


    protected Request nullOperationRequest() {
        Request request = new Request();
        request.setContext(new HashMap<String, Object>());
        return setSearchContext(request, AUDIT_HISTORY_ACTOR , "");
    }
}
