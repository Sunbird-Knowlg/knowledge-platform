package org.sunbird.actors;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.testkit.TestKit;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.*;
import org.sunbird.common.JsonUtils;
import org.sunbird.common.Platform;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseParams;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.common.exception.ServerException;
import org.sunbird.search.client.ElasticSearchUtil;
import org.sunbird.search.util.SearchConstants;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class SearchBaseActorTest {

    protected static ActorSystem system = null;
    protected static final String SEARCH_ACTOR = "SearchActor";
    protected static final String AUDIT_HISTORY_ACTOR = "AuditHistoryActor";
    private static String[] keywords = new String[]{"hindi story", "NCERT", "Pratham", "एकस्टेप", "हिन्दी", "हाथी और भालू", "worksheet", "test"};
    private static String[] contentTypes = new String[]{"Resource", "Collection", "Asset"};
    private static String[] ageGroup = new String[]{"<5","5-6", "6-7", "7-8","8-10",">10","Other"};
    private static String[] gradeLevel = new String[]{"Kindergarten","Class 1", "Class 2", "Class 3", "Class 4","Class 5","Other"};

    @BeforeClass
    public static void beforeTest() throws Exception {
        system = ActorSystem.create();

    }

    @BeforeClass
    public static void before() throws Exception {
        createCompositeSearchIndex();
        Thread.sleep(3000);
    }


    protected Request getSearchRequest() {
        Request request = new Request();
        request.setContext(new HashMap<String, Object>());
        return setSearchContext(request, SEARCH_ACTOR , "INDEX_SEARCH");
    }

    protected Request getCountRequest() {
        Request request = new Request();
        request.setContext(new HashMap<String, Object>());
        return setSearchContext(request, SEARCH_ACTOR , "COUNT");
    }

    protected Request getMetricsRequest() {
        Request request = new Request();
        request.setContext(new HashMap<String, Object>());
        return setSearchContext(request, SEARCH_ACTOR , "METRICS");
    }

    protected Request getAuditRequest() {
        Request request = new Request();
        request.setContext(new HashMap<String, Object>());
        return setSearchContext(request, AUDIT_HISTORY_ACTOR , "SEARCH_OPERATION_AND");
    }

    protected Request getGroupSearchResultsRequest() {
        Request request = new Request();
        return setSearchContext(request, SEARCH_ACTOR , "GROUP_SEARCH_RESULT_BY_OBJECTTYPE");
    }

    protected Request setSearchContext(Request request, String manager, String operation) {
        request.setOperation(operation);
        return request;
    }

    protected Response getResponse(Request request, Props props){
        try{
            ActorRef searchMgr = system.actorOf(props);
            Future<Object> future = Patterns.ask(searchMgr, request, 30000);
            Object obj = Await.result(future, Duration.create(30.0, TimeUnit.SECONDS));
            Response response = null;
            if (obj instanceof Response) {
                response = (Response) obj;
            } else {
                response = ERROR(SearchConstants.SYSTEM_ERROR, "System Error", ResponseCode.SERVER_ERROR);
            }
            return response;
        } catch (Exception e) {
            throw new ServerException(SearchConstants.SYSTEM_ERROR, e.getMessage(), e);
        }
    }

    protected Response getSearchResponse(Request request) {
        final Props props = Props.create(SearchActor.class);
        return getResponse(request, props);
    }

    protected Response getAuditResponse(Request request) {
        final Props props = Props.create(AuditHistoryActor.class);
        return getResponse(request, props);
    }

    protected Response ERROR(String errorCode, String errorMessage, ResponseCode responseCode) {
        Response response = new Response();
        response.setParams(getErrorStatus(errorCode, errorMessage));
        response.setResponseCode(responseCode);
        return response;
    }

    private ResponseParams getErrorStatus(String errorCode, String errorMessage) {
        ResponseParams params = new ResponseParams();
        params.setErr(errorCode);
        params.setStatus(ResponseParams.StatusType.failed.name());
        params.setErrmsg(errorMessage);
        return params;
    }

    protected static void createCompositeSearchIndex() throws Exception {
        SearchConstants.COMPOSITE_SEARCH_INDEX = "testcompositeindex";
        ElasticSearchUtil.initialiseESClient(SearchConstants.COMPOSITE_SEARCH_INDEX,
                Platform.config.getString("search.es_conn_info"));
        System.out.println("creating index: " + SearchConstants.COMPOSITE_SEARCH_INDEX);
        String settings = "{\"analysis\": {       \"analyzer\": {         \"cs_index_analyzer\": {           \"type\": \"custom\",           \"tokenizer\": \"standard\",           \"filter\": [             \"lowercase\",             \"mynGram\"           ]         },         \"cs_search_analyzer\": {           \"type\": \"custom\",           \"tokenizer\": \"standard\",           \"filter\": [             \"standard\",             \"lowercase\"           ]         },         \"keylower\": {           \"tokenizer\": \"keyword\",           \"filter\": \"lowercase\"         }       },       \"filter\": {         \"mynGram\": {           \"type\": \"nGram\",           \"min_gram\": 1,           \"max_gram\": 20,           \"token_chars\": [             \"letter\",             \"digit\",             \"whitespace\",             \"punctuation\",             \"symbol\"           ]         }       }     }   }";
        String mappings = "{\"dynamic_templates\":[{\"longs\":{\"match_mapping_type\":\"long\",\"mapping\":{\"type\":\"long\",\"fields\":{\"raw\":{\"type\":\"long\"}}}}},{\"booleans\":{\"match_mapping_type\":\"boolean\",\"mapping\":{\"type\":\"boolean\",\"fields\":{\"raw\":{\"type\":\"boolean\"}}}}},{\"doubles\":{\"match_mapping_type\":\"double\",\"mapping\":{\"type\":\"double\",\"fields\":{\"raw\":{\"type\":\"double\"}}}}},{\"dates\":{\"match_mapping_type\":\"date\",\"mapping\":{\"type\":\"date\",\"fields\":{\"raw\":{\"type\":\"date\"}}}}},{\"strings\":{\"match_mapping_type\":\"string\",\"mapping\":{\"type\":\"text\",\"copy_to\":\"all_fields\",\"analyzer\":\"cs_index_analyzer\",\"search_analyzer\":\"cs_search_analyzer\",\"fields\":{\"raw\":{\"type\":\"text\",\"fielddata\":\"true\",\"analyzer\":\"keylower\"}}}}}],\"properties\":{\"all_fields\":{\"type\":\"text\",\"analyzer\":\"cs_index_analyzer\",\"search_analyzer\":\"cs_search_analyzer\",\"fields\":{\"raw\":{\"type\":\"text\",\"fielddata\":\"true\",\"analyzer\":\"keylower\"}}}}}";
        ElasticSearchUtil.addIndex(SearchConstants.COMPOSITE_SEARCH_INDEX,
                SearchConstants.COMPOSITE_SEARCH_INDEX_TYPE, settings, mappings);
        insertTestRecords();
    }

    private static void insertTestRecords() throws Exception {
        for (int i=1; i<=30; i++) {
            Map<String, Object> content = getContentTestRecord(null, i, null);
            String id = (String) content.get("identifier");
            addToIndex(id, content);
        }
        Map<String, Object> content = getContentTestRecord("do_10000031", 31, null);
        content.put("name", "31 check name match");
        content.put("description", "हिन्दी description");
        content.put("subject", Arrays.asList("English", "Hindi"));
        content.put("medium", Arrays.asList("English", "Hindi"));
        addToIndex("do_10000031", content);

        content = getContentTestRecord("do_10000032", 32, null);
        content.put("name", "check ends with value32");
        addToIndex("do_10000032", content);

        content = getContentTestRecord("do_10000033", 33, "test-board1");
        content.put("name", "Content To Test Consumption");
        addToIndex("10000033", content);

        content = getContentTestRecord("do_10000034", 34, "test-board3");
        content.put("name", "Textbook-10000034");
        content.put("description", "Textbook for other tenant");
        content.put("status","Live");
        content.put("subject", Arrays.asList("Maths", "Science"));
        content.put("medium", Arrays.asList("English", "Hindi"));
        content.put("relatedBoards", new ArrayList<String>(){{
            add("test-board1");
            add("test-board2");
        }});
        addToIndex("10000034", content);

        content = getContentTestRecord("do_10000035", 35, "test-board4");
        content.put("name", "Test Course - TrainingCourse");
        content.put("description", "Test Course - TrainingCourse");
        content.put("status","Live");
        content.put("mimeType", "application/vnd.ekstep.content-collection");
        content.put("contentType", "Course");
        content.put("courseType", "TrainingCourse");
        addToIndex("10000035", content);
    }

    private static void addToIndex(String uniqueId, Map<String, Object> doc) throws Exception {
        String jsonIndexDocument = JsonUtils.serialize(doc);
        ElasticSearchUtil.addDocumentWithId(SearchConstants.COMPOSITE_SEARCH_INDEX,
                SearchConstants.COMPOSITE_SEARCH_INDEX_TYPE, uniqueId, jsonIndexDocument);
    }

    private static Map<String, Object> getContentTestRecord(String id, int index, String board) {
        String objectType = "Content";
        Date d = new Date();
        Map<String, Object> map = getTestRecord(id, index, "do", objectType);
        map.put("name", "Content_" + System.currentTimeMillis() + "_name");
        map.put("code", "code_" + System.currentTimeMillis());
        map.put("contentType", getContentType());
        map.put("createdOn", new Date().toString());
        map.put("lastUpdatedOn", new Date().toString());
        if(StringUtils.isNotBlank(board))
            map.put("board",board);
        Set<String> ageList = getAgeGroup();
        if (null != ageList && !ageList.isEmpty())
            map.put("ageGroup", ageList);
        Set<String> grades = getGradeLevel();
        if (null != grades && !grades.isEmpty())
            map.put("gradeLevel", grades);
        if (index % 5 == 0) {
            map.put("lastPublishedOn", d.toString());
            map.put("status", "Live");
            map.put("size", 1000432);
        } else {
            map.put("status", "Draft");
            if (index % 3 == 0)
                map.put("size", 564738);
        }
        Set<String> tagList = getTags();
        if (null != tagList && !tagList.isEmpty() && index % 7 != 0)
            map.put("keywords", tagList);
        map.put("downloads", index);
        map.put("visibility", "Default");
        return map;
    }

    private static Map<String, Object> getTestRecord(String id, int index, String prefix, String objectType) {
        Map<String, Object> map = new HashMap<String, Object>();
        if (StringUtils.isNotBlank(id))
            map.put("identifier", id);
        else {
            long suffix = 10000000 + index;
            map.put("identifier", prefix + "_" + suffix);
        }
        map.put("objectType", objectType);
        return map;
    }

    private static String getContentType() {
        return contentTypes[RandomUtils.nextInt(3)];
    }

    private static Set<String> getTags() {
        Set<String> list = new HashSet<String>();
        int count = RandomUtils.nextInt(9);
        for (int i=0; i<count; i++) {
            list.add(keywords[RandomUtils.nextInt(8)]);
        }
        return list;
    }

    private static Set<String> getAgeGroup() {
        Set<String> list = new HashSet<String>();
        int count = RandomUtils.nextInt(2);
        for (int i=0; i<count; i++) {
            list.add(ageGroup[RandomUtils.nextInt(6)]);
        }
        return list;
    }

    private static Set<String> getGradeLevel() {
        Set<String> list = new HashSet<String>();
        int count = RandomUtils.nextInt(2);
        for (int i=0; i<count; i++) {
            list.add(gradeLevel[RandomUtils.nextInt(6)]);
        }
        return list;
    }





    @Test
    public void testSearchByQuery() {
        Request request = getSearchRequest();
        request.put("query", "हिन्दी");
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        // request.put("limit", 1);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 1);
        boolean found = false;
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            String desc = (String) content.get("description");
            if (null != desc && desc.contains("हिन्दी"))
                found = true;
        }
        Assert.assertTrue(found);
    }


    @SuppressWarnings("unchecked")
    @Test
    public void testSearchByQueryForNotEquals() {
        Request request = getSearchRequest();
        request.put("query", "हिन्दी");
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("!=", "31 check name match");
        filters.put("name", map);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
        boolean found = false;
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            String desc = (String) content.get("name");
            if (null != desc && !org.apache.commons.lang.StringUtils.equalsIgnoreCase("31 check name match", desc))
                found = true;
        }
        Assert.assertTrue(found);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchByQueryForNotEqualsText() {
        Request request = getSearchRequest();
        //request.put("query", "हिन्दी");
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("notEquals", "31 check name match");
        filters.put("name", map);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
        boolean found = false;
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            String desc = (String) content.get("name");
            if (null != desc && !org.apache.commons.lang.StringUtils.equalsIgnoreCase("31 check name match", desc))
                found = true;
        }
        Assert.assertTrue(found);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchByQueryForNotIn() {
        Request request = getSearchRequest();
        request.put("query", "हिन्दी");
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("notIn", Arrays.asList("31 check name match"));
        filters.put("name", map);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
        boolean found = true;
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            String desc = (String) content.get("name");
            if (null != desc && org.apache.commons.lang.StringUtils.equalsIgnoreCase("31 check name match", desc))
                found = false;
        }
        Assert.assertTrue(found);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchByQueryForNotEqualsTextUpperCase() {
        Request request = getSearchRequest();
        request.put("query", "हिन्दी");
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("NE", "31 check name match");
        filters.put("name", map);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
        boolean found = false;
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            String desc = (String) content.get("name");
            if (null != desc && !org.apache.commons.lang.StringUtils.equalsIgnoreCase("31 check name match", desc))
                found = true;
        }
        Assert.assertTrue(found);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchByQueryForNotEqualsTextLowerCase() {
        Request request = getSearchRequest();
        request.put("query", "हिन्दी");
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("ne", "31 check name match");
        filters.put("name", map);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
        boolean found = false;
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            String desc = (String) content.get("name");
            if (null != desc && !org.apache.commons.lang.StringUtils.equalsIgnoreCase("31 check name match", desc))
                found = true;
        }
        Assert.assertTrue(found);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchByQueryFields() {
        Request request = getSearchRequest();
        request.put("query", "हिन्दी");
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        List<String> fields = new ArrayList<String>();
        fields.add("description");
        request.put("fields", fields);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
        boolean found = false;
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            String desc = (String) content.get("description");
            if (null != desc && desc.contains("हिन्दी"))
                found = true;
        }
        Assert.assertTrue(found);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchFilters() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() == 35);
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            String objectType = (String) content.get("objectType");
            Assert.assertEquals("Content", objectType);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchArrayFilter() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        List<String> names = new ArrayList<String>();
        names.add("31 check name match");
        names.add("check ends with value32");
        filters.put("name", names);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() == 2);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchStringValueFilter() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String, Object> name = new HashMap<String, Object>();
        name.put("value", "31 check name match");
        filters.put("name", name);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() == 1);
        Map<String, Object> content = (Map<String, Object>) list.get(0);
        String identifier = (String) content.get("identifier");
        Assert.assertEquals("do_10000031", identifier);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchStartsWithFilter() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String, Object> name = new HashMap<String, Object>();
        name.put("startsWith", "31 check");
        filters.put("name", name);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() == 1);
        Map<String, Object> content = (Map<String, Object>) list.get(0);
        String identifier = (String) content.get("identifier");
        Assert.assertEquals("do_10000031", identifier);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchEndsWithFilter() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String, Object> name = new HashMap<String, Object>();
        name.put("endsWith", "value32");
        filters.put("name", name);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() == 1);
        Map<String, Object> content = (Map<String, Object>) list.get(0);
        String identifier = (String) content.get("identifier");
        Assert.assertEquals("do_10000032", identifier);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchMinFilter() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String, Object> name = new HashMap<String, Object>();
        name.put("min", 1000432);
        filters.put("size", name);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() >= 1);
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            Integer identifier = (Integer) content.get("size");
            if (null != identifier)
                Assert.assertTrue(identifier >= 1000432);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchMaxFilter() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String, Object> name = new HashMap<String, Object>();
        name.put("max", 564738);
        filters.put("size", name);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() >= 1);
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            Integer identifier = (Integer) content.get("size");
            if (null != identifier)
                Assert.assertTrue(identifier <= 564738);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchMinMaxFilter() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String, Object> name = new HashMap<String, Object>();
        name.put("min", 564737);
        name.put("max", 564739);
        filters.put("size", name);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            Integer size = (Integer) content.get("size");
            if (null != size)
                Assert.assertTrue(size == 564738);
        }
    }

    @SuppressWarnings("unchecked")
    @Ignore
    @Test
    public void testSearchLTFilter() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String, Object> name = new HashMap<String, Object>();
        name.put("<", 1000432);
        filters.put("size", name);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() >= 1);
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            Double identifier = (Double) content.get("size");
            if (null != identifier)
                Assert.assertTrue(identifier < 1000432);
        }
    }

    @SuppressWarnings("unchecked")
    @Ignore
    @Test
    public void testSearchLEGEFilter() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String, Object> name = new HashMap<String, Object>();
        name.put("<=", 1000432);
        name.put(">=", 1000432);
        filters.put("size", name);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() >= 1);
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            Double identifier = (Double) content.get("size");
            if (null != identifier)
                Assert.assertTrue(identifier == 1000432);
        }
    }

    @SuppressWarnings("unchecked")
    @Ignore
    @Test
    public void testSearchGTFilter() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String, Object> name = new HashMap<String, Object>();
        name.put(">", 564738);
        filters.put("size", name);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() >= 1);
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            Double identifier = (Double) content.get("size");
            if (null != identifier)
                Assert.assertTrue(identifier > 564738);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchValueFilter() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String, Object> name = new HashMap<String, Object>();
        name.put("value", "31 check name match");
        filters.put("name", name);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() >= 1);
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            String identifier = (String) content.get("name");
            Assert.assertTrue(identifier.contains("31 check name match"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchContainsFilter() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        Map<String, Object> name = new HashMap<String, Object>();
        name.put("contains", "check");
        filters.put("name", name);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() >= 1);
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            String identifier = (String) content.get("name");
            Assert.assertTrue(identifier.contains("check"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchExistsCondition() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        List<String> exists = new ArrayList<String>();
        exists.add("objectType");
        request.put("exists", exists);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() >= 1);
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            String objectType = (String) content.get("objectType");
            Assert.assertNotNull(objectType);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchNotExistsCondition() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        List<String> exists = new ArrayList<String>();
        exists.add("size");
        request.put("not_exists", exists);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() >= 1);
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            Double size = (Double) content.get("size");
            Assert.assertNull(size);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchFacets() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        List<String> exists = new ArrayList<String>();
        exists.add("size");
        exists.add("contentType");
        request.put("facets", exists);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("facets");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 1);
        Map<String, Object> facet = (Map<String, Object>) list.get(0);
        Assert.assertEquals("size", facet.get("name").toString());
        List<Object> values = (List<Object>) facet.get("values");
        Assert.assertEquals(2, values.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchLimit() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        request.put("limit", 10);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() == 10);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchEmptyResult() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("InvalidObjectType");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        request.put("limit", 10);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertTrue(null == list || list.size() == 0);
        Integer count = (Integer) result.get("count");
        Assert.assertTrue(count == 0);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchSortAsc() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        request.put("limit", 5);
        Map<String, Object> sort = new HashMap<String, Object>();
        sort.put("identifier", "asc");
        request.put("sort_by", sort);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() == 5);
        Map<String, Object> content1 = (Map<String, Object>) list.get(0);
        String id1 = (String) content1.get("identifier");
        Assert.assertEquals("do_10000001", id1);

        Map<String, Object> content5 = (Map<String, Object>) list.get(4);
        String id5 = (String) content5.get("identifier");
        Assert.assertEquals("do_10000005", id5);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchSortDesc() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        request.put("limit", 2);
        Map<String, Object> sort = new HashMap<String, Object>();
        sort.put("identifier", "desc");
        request.put("sort_by", sort);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() == 2);
        Map<String, Object> content1 = (Map<String, Object>) list.get(0);
        String id1 = (String) content1.get("identifier");
        Assert.assertEquals("do_10000035", id1);

        Map<String, Object> content2 = (Map<String, Object>) list.get(1);
        String id2 = (String) content2.get("identifier");
        Assert.assertEquals("do_10000034", id2);
    }

    @Test
    public void testSearchCount() {
        Request request = getCountRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        Integer count = (Integer) result.get("count");
        Assert.assertNotNull(count);
    }

    @Test
    public void testSearchMetrics() {
        Request request = getMetricsRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        Integer count = (Integer) result.get("count");
        Assert.assertTrue(count == 35);
    }

    @Test
    public void testGroupSearchResults() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();

        Request req = getGroupSearchResultsRequest();
        req.put("searchResult", result);
        Response resp = getSearchResponse(req);
        result = resp.getResult();
        Assert.assertNotNull(result.get("content"));
        List<Object> contents = (List<Object>) result.getOrDefault("content", new ArrayList<Object>());
        Assert.assertEquals(contents.size(), 35);
    }

    @Test
    public void testUnSupportedException() {
        Request request = new Request();
        setSearchContext(request, SEARCH_ACTOR, "Invalid Operation");
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        filters.put("status", new ArrayList<String>());
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Assert.assertEquals("failed", response.getParams().getStatus());
        Assert.assertEquals(ResponseCode.CLIENT_ERROR.code(), response.getResponseCode().code());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchByFields() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        List<String> status = new ArrayList<String>();
        status.add("Draft");
        filters.put("status",status);
        List<String> contentType = new ArrayList<String>();
        contentType.add("Resource");
        contentType.add("Collection");
        filters.put("contentType",contentType);
        request.put("filters", filters);
        List<String> fields = new ArrayList<String>();
        fields.add("name");
        fields.add("code");
        request.put("fields", fields);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
        boolean valid = false;
        for (Object obj : list) {
            Map<String, Object> contents = (Map<String, Object>) obj;
            Set<String> keys = contents.keySet();
            if (keys.size() == fields.size()) {
                if(keys.containsAll(fields)){
                    valid = true;

                }
            }else {
                valid = false;
            }

        }
        Assert.assertTrue(valid);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearchByOffset() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        List<String> status = new ArrayList<String>();
        status.add("Draft");
        filters.put("status",status);
        List<String> contentType = new ArrayList<String>();
        contentType.add("Resource");
        contentType.add("Collection");
        filters.put("contentType",contentType);
        request.put("filters", filters);
        request.put("offset", 0);
        request.put("limit", 1);

        Request request1 = getSearchRequest();
        Map<String, Object> filters1 = new HashMap<String, Object>();
        List<String> objectTypes1 = new ArrayList<String>();
        objectTypes1.add("Content");
        filters1.put("objectType", objectTypes1);
        List<String> status1 = new ArrayList<String>();
        status1.add("Draft");
        filters1.put("status",status1);
        List<String> contentType1 = new ArrayList<String>();
        contentType1.add("Asset");
        contentType1.add("Collection");

        contentType1.add("Resource");
        filters1.put("contentType",contentType1);
        request1.put("filters", filters1);
        request1.put("offset", 4);
        request1.put("limit", 1);
        Response response = getSearchResponse(request);
        Response response1 = getSearchResponse(request1);
        Map<String, Object> result = response.getResult();
        Map<String, Object> result1 = response1.getResult();
        List<Object> list = (List<Object>) result.get("results");
        List<Object> list1 = (List<Object>) result1.get("results");
        Assert.assertNotNull(list);
        Assert.assertNotNull(list1);
        Assert.assertTrue(list.size() > 0);
        Assert.assertTrue(list1.size() > 0);
        boolean validResult = false;
        if(list!=list1){
            validResult = true;
        }
        Assert.assertTrue(validResult);
    }

    @Test
    public void testSoftConstraints() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("notEquals", "31 check name match");
        filters.put("name", map);
        request.put("filters", filters);
        request.put("mode", "soft");
        List<String> fields = new ArrayList<String>();
        fields.add("name");
        fields.add("medium");
        fields.add("subject");
        fields.add("contentType");
        request.put("fields", fields);
        Map<String, Object> softConstraints = new HashMap<String, Object>();
        softConstraints.put("name", 100);
        softConstraints.put("subject", 20);
        request.put("softConstraints", softConstraints);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
    }


    @Test
    public void testSearchImplicitFilter() {
        Request request = getSearchRequest();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        request.put("filters", new HashMap<String, Object>() {{
            put("status", new ArrayList<String>());
            put("objectType", objectTypes);
            put("board", "test-board1");
        }});

        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() == 2);
        boolean found = false;
        boolean foundOriginal = false;
        for (Object obj : list) {
            Map<String, Object> content = (Map<String, Object>) obj;
            List<String> relatedBoards = (List<String>) content.get("relatedBoards");
            if (CollectionUtils.isNotEmpty(relatedBoards) && org.apache.commons.lang.StringUtils.equalsIgnoreCase("do_10000034", (String) content.get("identifier"))
                    && relatedBoards.contains("test-board1"))
                found = true;
            if (org.apache.commons.lang.StringUtils.equalsIgnoreCase("do_10000033", (String) content.get("identifier")) && org.apache.commons.lang.StringUtils.equalsIgnoreCase("test-board1", (String) content.get("board")))
                foundOriginal = true;
        }
        Assert.assertTrue(found);
        Assert.assertTrue(foundOriginal);
    }

    @Ignore
    @Test
    public void testSearchwithModeAndSCFromRequest() {
        Request request = getSearchRequest();
        request.put("mode", "soft");
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        List<String> contentTypes = new ArrayList<String>();
        contentTypes.add("Resource");
        contentTypes.add("Collection");
        contentTypes.add("Asset");
        filters.put("contentType", contentTypes);
        List<String> ageGroup = new ArrayList<>();
        ageGroup.add("5-6");
        filters.put("ageGroup", ageGroup);
        List<String> gradeLevel = new ArrayList<>();
        gradeLevel.add("Class 1");
        filters.put("gradeLevel", gradeLevel);
        List<String> status = new ArrayList<String>();
        status.add("Live");
        filters.put("status", status);
        Map<String, Integer> softConstraints = new HashMap<>();
        softConstraints.put("ageGroup", 3);
        softConstraints.put("gradeLevel", 4);
        request.put("softConstraints", softConstraints);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
        ResponseCode res = response.getResponseCode();
        boolean statusCode = false;
        if (res == ResponseCode.OK) {
            statusCode = true;
        }
        Assert.assertTrue(statusCode);
        boolean found = false;
        Map<String, Object> content = (Map<String, Object>) list.get(0);
        if (null != content && content.containsKey("ageGroup")) {
            found = true;
        }
        Assert.assertTrue(found);
    }

    @SuppressWarnings("unchecked")
    @Ignore
    @Test
    public void testWithoutMode() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        List<String> contentTypes = new ArrayList<String>();
        contentTypes.add("Resource");
        contentTypes.add("Collection");
        contentTypes.add("Asset");
        filters.put("contentType", contentTypes);
        List<String> ageGroup = new ArrayList<>();
        ageGroup.add("5-6");
        filters.put("ageGroup", ageGroup);
        List<String> gradeLevel = new ArrayList<>();
        gradeLevel.add("Class 1");
        filters.put("gradeLevel", gradeLevel);
        List<String> status = new ArrayList<String>();
        status.add("Live");
        filters.put("status", status);
        Map<String, Integer> softConstraints = new HashMap<>();
        softConstraints.put("ageGroup", 3);
        softConstraints.put("gradeLevel", 4);
        request.put("softConstraints", softConstraints);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
        ResponseCode res = response.getResponseCode();
        boolean statusCode = false;
        if (res == ResponseCode.OK) {
            statusCode = true;
        }
        Assert.assertTrue(statusCode);
        boolean found = false;
        Map<String, Object> content = (Map<String, Object>) list.get(0);
        if (null != content && content.containsKey("ageGroup")) {
            found = true;
        }
        Assert.assertTrue(found);
    }

    @SuppressWarnings("unchecked")
    @Ignore
    @Test
    public void testWithoutSoftConstraintRequest() {
        Request request = getSearchRequest();
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        List<String> contentTypes = new ArrayList<String>();
        contentTypes.add("Resource");
        contentTypes.add("Collection");
        contentTypes.add("Asset");
        filters.put("contentType", contentTypes);
        List<String> ageGroup = new ArrayList<>();
        ageGroup.add("5-6");
        filters.put("ageGroup", ageGroup);
        List<String> gradeLevel = new ArrayList<>();
        gradeLevel.add("Class 1");
        filters.put("gradeLevel", gradeLevel);
        List<String> status = new ArrayList<String>();
        status.add("Live");
        filters.put("status", status);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
        ResponseCode res = response.getResponseCode();
        boolean statusCode = false;
        if (res == ResponseCode.OK) {
            statusCode = true;
        }
        Assert.assertTrue(statusCode);
        boolean found = false;
        Map<String, Object> content = (Map<String, Object>) list.get(0);
        if (null != content && content.containsKey("ageGroup")){
            found = true;
        }
        Assert.assertTrue(found);
    }

    @SuppressWarnings("unchecked")
    @Ignore
    @Test
    public void testModeHard() {
        Request request = getSearchRequest();
        request.put("mode", "Hard");
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        List<String> contentTypes = new ArrayList<String>();
        contentTypes.add("Resource");
        contentTypes.add("Collection");
        contentTypes.add("Asset");
        filters.put("contentType", contentTypes);
        List<String> ageGroup = new ArrayList<>();
        ageGroup.add("5-6");
        filters.put("ageGroup", ageGroup);
        List<String> gradeLevel = new ArrayList<>();
        gradeLevel.add("Class 1");
        filters.put("gradeLevel", gradeLevel);
        List<String> status = new ArrayList<String>();
        status.add("Live");
        filters.put("status", status);
        Map<String, Integer> softConstraints = new HashMap<>();
        softConstraints.put("ageGroup", 3);
        softConstraints.put("gradeLevel", 4);
        request.put("softConstraints", softConstraints);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
        ResponseCode res = response.getResponseCode();
        boolean statusCode = false;
        if (res == ResponseCode.OK) {
            statusCode = true;
        }
        Assert.assertTrue(statusCode);
        boolean found = false;
        Map<String, Object> content = (Map<String, Object>) list.get(0);
        if (null != content && content.containsKey("ageGroup")){
            found = true;
        }
        Assert.assertTrue(found);
    }

    @SuppressWarnings("unchecked")
    @Ignore
    @Test
    public void testInvalidMode() {
        Request request = getSearchRequest();
        request.put("mode", "xyz");
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        List<String> contentTypes = new ArrayList<String>();
        contentTypes.add("Resource");
        contentTypes.add("Collection");
        contentTypes.add("Asset");
        filters.put("contentType", contentTypes);
        List<String> ageGroup = new ArrayList<>();
        ageGroup.add("5-6");
        filters.put("ageGroup", ageGroup);
        List<String> gradeLevel = new ArrayList<>();
        gradeLevel.add("Class 1");
        filters.put("gradeLevel", gradeLevel);
        List<String> status = new ArrayList<String>();
        status.add("Live");
        filters.put("status", status);
        Map<String, Integer> softConstraints = new HashMap<>();
        softConstraints.put("ageGroup", 3);
        softConstraints.put("gradeLevel", 4);
        request.put("softConstraints", softConstraints);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
        ResponseCode res = response.getResponseCode();
        boolean statusCode = false;
        if (res == ResponseCode.OK) {
            statusCode = true;
        }
        Assert.assertTrue(statusCode);
        boolean found = false;
        Map<String, Object> content = (Map<String, Object>) list.get(0);
        if (null != content && content.containsKey("ageGroup")){
            found = true;
        }
        Assert.assertTrue(found);
    }

    @SuppressWarnings("unchecked")
    @Ignore
    @Test
    public void testModeBlank() {
        Request request = getSearchRequest();
        request.put("mode", "");
        Map<String, Object> filters = new HashMap<String, Object>();
        List<String> objectTypes = new ArrayList<String>();
        objectTypes.add("Content");
        filters.put("objectType", objectTypes);
        List<String> contentTypes = new ArrayList<String>();
        contentTypes.add("Resource");
        contentTypes.add("Collection");
        contentTypes.add("Asset");
        filters.put("contentType", contentTypes);
        List<String> ageGroup = new ArrayList<>();
        ageGroup.add("5-6");
        filters.put("ageGroup", ageGroup);
        List<String> gradeLevel = new ArrayList<>();
        gradeLevel.add("Class 1");
        filters.put("gradeLevel", gradeLevel);
        List<String> status = new ArrayList<String>();
        status.add("Live");
        filters.put("status", status);
        Map<String, Integer> softConstraints = new HashMap<>();
        softConstraints.put("ageGroup", 3);
        softConstraints.put("gradeLevel", 4);
        request.put("softConstraints", softConstraints);
        request.put("filters", filters);
        Response response = getSearchResponse(request);
        Map<String, Object> result = response.getResult();
        List<Object> list = (List<Object>) result.get("results");
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
        ResponseCode res = response.getResponseCode();
        boolean statusCode = false;
        if (res == ResponseCode.OK) {
            statusCode = true;
        }
        Assert.assertTrue(statusCode);
        boolean found = false;
        Map<String, Object> content = (Map<String, Object>) list.get(0);
        if (null != content && content.containsKey("ageGroup")){
            found = true;
        }
        Assert.assertTrue(found);
    }

}
