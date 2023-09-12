package org.sunbird.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.dispatch.Futures;
import akka.dispatch.Recover;
import akka.pattern.Patterns;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.JsonUtils;
import org.sunbird.common.Platform;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseParams;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.MiddlewareException;
import org.sunbird.common.exception.ResourceNotFoundException;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.common.exception.ServerException;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.util.DefinitionUtil;
import org.sunbird.search.util.SearchConstants;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.concurrent.Future;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public abstract class SearchBaseActor extends AbstractActor {

//    public void onReceive(Object message) throws Exception {
//        if (message instanceof Request) {
//            Request request = (Request) message;
//            invokeMethod(request, getSender());
//        } else if (message instanceof Response) {
//            // do nothing
//        } else {
//            unhandled(message);
//        }
//    }

    public abstract Future<Response> onReceive(Request request) throws Throwable;

    private Future<Response> internalOnReceive(Request request) {
        try {
            return onReceive(request).recoverWith(new Recover<Future<Response>>() {
                @Override
                public Future<Response> recover(Throwable failure) throws Throwable {
                    TelemetryManager.error("Unable to process the request:: Request: " + JsonUtils.serialize(request), failure);
                    return ERROR(request.getOperation(), failure);
                }
            }, getContext().dispatcher());
        } catch (Throwable e) {
            return ERROR(request.getOperation(), e);
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(Request.class, message -> {
            Patterns.pipe(internalOnReceive(message), getContext().dispatcher()).to(sender());
        }).build();
    }

    public Future<Response> ERROR(String operation) {
        Response response = getErrorResponse(new ClientException(ResponseCode.CLIENT_ERROR.name(), "Invalid operation provided in request to process: " + operation));
        return Futures.successful(response);
    }

    protected Future<Response> ERROR(String operation, Throwable exception) {
        return Futures.successful(getErrorResponse(exception));
    }

    private Response getErrorResponse(Throwable e) {
        Response response = new Response();
        ResponseParams params = new ResponseParams();
        params.setStatus(ResponseParams.StatusType.failed.name());
        if (e instanceof MiddlewareException) {
            MiddlewareException mwException = (MiddlewareException) e;
            params.setErr(mwException.getErrCode());
            response.put("messages", mwException.getMessage());
        } else {
            TelemetryManager.error("Error while processing", e);
            params.setErr("ERR_SYSTEM_EXCEPTION");
        }
        TelemetryManager.error("Exception occurred - class :" + e.getClass().getName() + " with message :" , e);
        params.setErrmsg(setErrMessage(e));
        response.setParams(params);
        setResponseCode(response, e);
        return response;
    }

    public Response OK(String responseIdentifier, Object vo) {
        Response response = new Response();
        response.put(responseIdentifier, vo);
        response.setParams(getSucessStatus());
        return response;
    }

    public Response OK(Map<String, Object> responseObjects) {
        Response response = new Response();
        if (null != responseObjects && responseObjects.size() > 0) {
            for (Entry<String, Object> entry : responseObjects.entrySet()) {
                response.put(entry.getKey(), entry.getValue());
            }
        }
        response.setParams(getSucessStatus());
        return response;
    }

    public Response ERROR(String errorCode, String errorMessage, ResponseCode code, String responseIdentifier, Object vo) {
        TelemetryManager.log("ErrorCode: "+ errorCode + " :: Error message: " + errorMessage);
        Response response = new Response();
        response.put(responseIdentifier, vo);
        response.setParams(getErrorStatus(errorCode, errorMessage));
        response.setResponseCode(code);
        return response;
    }

    public void handleException(Throwable e, ActorRef parent) {
        Response response = new Response();
        ResponseParams params = new ResponseParams();
        params.setStatus(ResponseParams.StatusType.failed.name());
        if (e instanceof MiddlewareException) {
            MiddlewareException mwException = (MiddlewareException) e;
            params.setErr(mwException.getErrCode());
        } else {
            params.setErr("ERR_SYSTEM_EXCEPTION");
        }
        TelemetryManager.log("Exception occured in class :"+ e.getClass().getName() + " message: " + e.getMessage());
        params.setErrmsg(setErrMessage(e));
        response.setParams(params);
        setResponseCode(response, e);
        parent.tell(response, getSelf());
    }

    private ResponseParams getSucessStatus() {
        ResponseParams params = new ResponseParams();
        params.setErr("0");
        params.setStatus(ResponseParams.StatusType.successful.name());
        params.setErrmsg("Operation successful");
        return params;
    }

    private ResponseParams getErrorStatus(String errorCode, String errorMessage) {
        ResponseParams params = new ResponseParams();
        params.setErr(errorCode);
        params.setStatus(ResponseParams.StatusType.failed.name());
        params.setErrmsg(errorMessage);
        return params;
    }

    private void setResponseCode(Response res, Throwable e) {
        if (e instanceof ClientException) {
            res.setResponseCode(ResponseCode.CLIENT_ERROR);
        } else if (e instanceof ServerException) {
            res.setResponseCode(ResponseCode.SERVER_ERROR);
        } else if (e instanceof ResourceNotFoundException) {
            res.setResponseCode(ResponseCode.RESOURCE_NOT_FOUND);
        } else {
            res.setResponseCode(ResponseCode.SERVER_ERROR);
        }
    }
    
    protected String setErrMessage(Throwable e){
		if (e instanceof MiddlewareException)
        		return e.getMessage();
        else {
            if (e.getSuppressed().length > 0) {
                return e.getSuppressed()[0].getMessage();
            } else {
                return e.getMessage();
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public SearchDTO getSearchDTO(Request request) throws Exception {
        SearchDTO searchObj = new SearchDTO();
        try {
            Map<String, Object> req = request.getRequest();
            TelemetryManager.log("Search Request: ", req);
            String queryString = (String) req.get(SearchConstants.query);
            int limit = getIntValue(req.get(SearchConstants.limit));
            Boolean fuzzySearch = (Boolean) request.get("fuzzy");
            if (null == fuzzySearch)
                fuzzySearch = false;
            Boolean wordChainsRequest = (Boolean) request.get("traversal");
            if (null == wordChainsRequest)
                wordChainsRequest = false;
            List<Map> properties = new ArrayList<Map>();
            Map<String, Object> filters = (Map<String, Object>) req.get(SearchConstants.filters);
            if (null == filters)
                filters = new HashMap<>();
            if (filters.containsKey("tags")) {
                Object tags = filters.get("tags");
                if (null != tags) {
                    filters.remove("tags");
                    filters.put("keywords", tags);
                }
            }
            if (filters.containsKey("relatedBoards"))
                filters.remove("relatedBoards");

            Object objectTypeFromFilter = filters.get(SearchConstants.objectType);
            String objectType = null;
            if (objectTypeFromFilter != null) {
                if (objectTypeFromFilter instanceof List) {
                    List objectTypeList = (List) objectTypeFromFilter;
                    if (objectTypeList.size() > 0)
                        objectType = (String) objectTypeList.get(0);
                } else if (objectTypeFromFilter instanceof String) {
                    objectType = (String) objectTypeFromFilter;
                }
            }

            Object graphIdFromFilter = filters.get(SearchConstants.graph_id);
            String graphId = null;
            if (graphIdFromFilter != null) {
                if (graphIdFromFilter instanceof List) {
                    List graphIdList = (List) graphIdFromFilter;
                    if (graphIdList.size() > 0)
                        graphId = (String) graphIdList.get(0);
                } else if (graphIdFromFilter instanceof String) {
                    graphId = (String) graphIdFromFilter;
                }
            }
            if (fuzzySearch && filters != null) {
                Map<String, Float> weightagesMap = new HashMap<String, Float>();
                weightagesMap.put("default_weightage", 1.0f);

                if (StringUtils.isNotBlank(objectType) && StringUtils.isNotBlank(graphId)) {
                    Map<String, Object> objDefinition = DefinitionUtil.getMetaData(objectType);
                    String weightagesString = (String) objDefinition.get("weightages");
                    if (StringUtils.isNotBlank(weightagesString)) {
                        weightagesMap = getWeightagesMap(weightagesString);
                    }
                }
                searchObj.addAdditionalProperty("weightagesMap", weightagesMap);
            }

            List<String> exists = null;
            Object existsObject = req.get(SearchConstants.exists);
            if (existsObject instanceof List) {
                exists = (List<String>) existsObject;
            } else if (existsObject instanceof String) {
                exists = new ArrayList<String>();
                exists.add((String) existsObject);
            }

            List<String> notExists = null;
            Object notExistsObject = req.get(SearchConstants.not_exists);
            if (notExistsObject instanceof List) {
                notExists = (List<String>) notExistsObject;
            } else if (notExistsObject instanceof String) {
                notExists = new ArrayList<String>();
                notExists.add((String) notExistsObject);
            }

            Map<String, Object> softConstraints = null;
            if (null != req.get(SearchConstants.softConstraints)) {
                softConstraints = (Map<String, Object>) req.get(SearchConstants.softConstraints);
            }

            String mode = (String) req.get(SearchConstants.mode);
            if (null != mode && mode.equals(SearchConstants.soft)
                    && (null == softConstraints || softConstraints.isEmpty()) && objectType != null) {
                try {
                    Map<String, Object> metaData = DefinitionUtil.getMetaData(objectType);
                    if (null != metaData.get("softConstraints")) {
                        softConstraints = (Map<String, Object>) metaData.get("softConstraints");
                    }
                } catch (Exception e) {
                    TelemetryManager.warn("Invalid soft Constraints" + e.getMessage());
                }
            }
            TelemetryManager.log("Soft Constraints with only Mode: ", softConstraints);
            if (null != softConstraints && !softConstraints.isEmpty()) {
                Map<String, Object> softConstraintMap = new HashMap<>();
                TelemetryManager.log("SoftConstraints:", softConstraints);
                try {
                    for (String key : softConstraints.keySet()) {
                        if (filters.containsKey(key) && null != filters.get(key)) {
                            List<Object> data = new ArrayList<>();
                            Integer boost = 1;
                            Object boostValue = softConstraints.get(key);
                            if (null != boostValue) {
                                try {
                                    boost = Integer.parseInt(boostValue.toString());
                                } catch (Exception e) {
                                    boost = 1;
                                }
                            }
                            data.add(boost);
                            if (filters.get(key) instanceof Map) {
                                data.add(((Map) filters.get(key)).values().toArray()[0]);
                            } else {
                                data.add(filters.get(key));
                            }

                            softConstraintMap.put(key, data);
                            filters.remove(key);
                        }
                    }
                } catch (Exception e) {
                    TelemetryManager.warn("Invalid soft Constraints: " + e.getMessage());
                }
                if (MapUtils.isNotEmpty(softConstraintMap) && softConstraintMap.containsKey("board"))
                    softConstraintMap.put("relatedBoards", softConstraintMap.get("board"));
                searchObj.setSoftConstraints(softConstraintMap);
            }
            TelemetryManager.log("SoftConstraints" + searchObj.getSoftConstraints());

            List<String> fieldsSearch = getList(req.get(SearchConstants.fields));
            List<String> facets = getList(req.get(SearchConstants.facets));
            Map<String, String> sortBy = (Map<String, String>) req.get(SearchConstants.sort_by);
            properties.addAll(getAdditionalFilterProperties(exists, SearchConstants.exists));
            properties.addAll(getAdditionalFilterProperties(notExists, SearchConstants.not_exists));
            // Changing fields to null so that search all fields but returns
            // only the fields specified
            properties.addAll(getSearchQueryProperties(queryString, null));
            properties.addAll(getSearchFilterProperties(filters, wordChainsRequest, request));
            searchObj.setSortBy(sortBy);
            searchObj.setFacets(facets);
            searchObj.setProperties(properties);
            // Added Implicit Filter Properties To Support Collection content tagging to reuse by tenants.
            setImplicitFilters(filters, searchObj);
            searchObj.setLimit(limit);
            searchObj.setFields(fieldsSearch);
            searchObj.setOperation(SearchConstants.SEARCH_OPERATION_AND);
            getAggregations(req, searchObj);

            if (null != req.get(SearchConstants.offset)) {
                int offset = getIntValue(req.get(SearchConstants.offset));
                TelemetryManager.log("Offset: " + offset);
                searchObj.setOffset(offset);
            }

            if (fuzzySearch != null) {
                searchObj.setFuzzySearch(fuzzySearch);
            }
        } catch (ClassCastException e) {
            e.printStackTrace();
            throw new ClientException(SearchConstants.ERR_COMPOSITE_SEARCH_INVALID_PARAMS,
                    "Invalid Input.", e);
        }
        return searchObj;
    }
    private Map<String, Float> getWeightagesMap(String weightagesString)
            throws Exception {
        Map<String, Float> weightagesMap = new HashMap<String, Float>();
        if (weightagesString != null && !weightagesString.isEmpty()) {
            Map<String, Object> weightagesRequestMap = JsonUtils.deserialize(weightagesString, Map.class);

            for (Map.Entry<String, Object> entry : weightagesRequestMap.entrySet()) {
                Float weightage = Float.parseFloat(entry.getKey());
                if (entry.getValue() instanceof List) {
                    List<String> fields = (List<String>) entry.getValue();
                    for (String field : fields) {
                        weightagesMap.put(field, weightage);
                    }
                } else {
                    String field = (String) entry.getValue();
                    weightagesMap.put(field, weightage);
                }
            }
        }
        return weightagesMap;
    }

    private List<Map<String, Object>> getAdditionalFilterProperties(List<String> fieldList, String operation) {
        List<Map<String, Object>> properties = new ArrayList<Map<String, Object>>();
        if (fieldList != null) {
            for (String field : fieldList) {
                String searchOperation = "";
                switch (operation) {
                    case "exists": {
                        searchOperation = SearchConstants.SEARCH_OPERATION_EXISTS;
                        break;
                    }
                    case "not_exists": {
                        searchOperation = SearchConstants.SEARCH_OPERATION_NOT_EXISTS;
                        break;
                    }
                }
                Map<String, Object> property = new HashMap<String, Object>();
                property.put(SearchConstants.operation, searchOperation);
                property.put(SearchConstants.propertyName, field);
                property.put(SearchConstants.values, Arrays.asList(field));
                properties.add(property);
            }
        }
        return properties;
    }

    private Integer getIntValue(Object num) {
        int i = 100;
        if (null != num) {
            try {
                i = (int) num;
            } catch (Exception e) {
                if(num instanceof String){
                    try{
                        return Integer.parseInt((String) num);
                    }catch (Exception ex){
                        throw new ClientException(SearchConstants.ERR_COMPOSITE_SEARCH_INVALID_PARAMS, "Invalid Input.", e);
                    }
                }
                i = new Long(num.toString()).intValue();
            }
        }
        return i;
    }

    private List<Map<String, Object>> getSearchQueryProperties(String queryString, List<String> fields) {
        List<Map<String, Object>> properties = new ArrayList<Map<String, Object>>();
        if (queryString != null && !queryString.isEmpty()) {
            if (null == fields || fields.size() <= 0) {
                Map<String, Object> property = new HashMap<String, Object>();
                property.put(SearchConstants.operation, SearchConstants.SEARCH_OPERATION_LIKE);
                property.put(SearchConstants.propertyName, "*");
                property.put(SearchConstants.values, Arrays.asList(queryString));
                properties.add(property);
            } else {
                for (String field : fields) {
                    Map<String, Object> property = new HashMap<String, Object>();
                    property.put(SearchConstants.operation,
                            SearchConstants.SEARCH_OPERATION_LIKE);
                    property.put(SearchConstants.propertyName, field);
                    property.put(SearchConstants.values, Arrays.asList(queryString));
                    properties.add(property);
                }
            }
        }
        return properties;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List<Map<String, Object>> getSearchFilterProperties(Map<String, Object> filters, Boolean traversal, Request request)
            throws Exception {
        List<Map<String, Object>> properties = new ArrayList<Map<String, Object>>();
        if (null == filters) filters = new HashMap<String, Object>();
        if (!filters.isEmpty()) {
            boolean publishedStatus = checkPublishedStatus(filters);
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                if ("identifier".equalsIgnoreCase(entry.getKey())) {
                    List ids = new ArrayList<>();
                    if (entry.getValue() instanceof String) {
                        ids.add(entry.getValue());
                    } else {
                        ids = (List<String>) entry.getValue();
                    }
                    List<String> identifiers = new ArrayList<>();
                    identifiers.addAll((List<String>) (List<?>) ids);
                    if(!publishedStatus){
                        for (Object id : ids) {
                            identifiers.add(id + ".img");
                        }
                    }
                    entry.setValue(identifiers);
                }
                if (SearchConstants.objectType.equals(entry.getKey())) {
                    List value = new ArrayList<>();
                    if (entry.getValue() instanceof String) {
                        value.add(entry.getValue());
                    } else {
                        value = (List<String>) entry.getValue();
                    }
                    Set<String> objectTypes = new HashSet<>();
                    objectTypes.addAll((List<String>) (List<?>) value);

                    for (Object val : value) {
                        if((StringUtils.equalsIgnoreCase("Content", (String) val) || StringUtils.equalsIgnoreCase("Collection", (String) val) || StringUtils.equalsIgnoreCase("Asset", (String) val))){
                            objectTypes.add("Content");
                            objectTypes.add("Collection");
                            objectTypes.add("Asset");
                        }
                        if((StringUtils.equalsIgnoreCase("Content", (String) val) || StringUtils.equalsIgnoreCase("Collection", (String) val) || StringUtils.equalsIgnoreCase("Asset", (String) val)) && !publishedStatus) {
                            objectTypes.add("ContentImage");
                            objectTypes.add("Asset");
                            objectTypes.add("CollectionImage");
                        }
                    }
                    entry.setValue(new ArrayList<String>(objectTypes));
                }
                Object filterObject = entry.getValue();
                if (filterObject instanceof Map) {
                    Map<String, Object> filterMap = (Map<String, Object>) filterObject;
                    if (!filterMap.containsKey(SearchConstants.SEARCH_OPERATION_RANGE_MIN)
                            && !filterMap.containsKey(SearchConstants.SEARCH_OPERATION_RANGE_MAX)) {
                        for (Map.Entry<String, Object> filterEntry : filterMap.entrySet()) {
                            Map<String, Object> property = new HashMap<String, Object>();
                            property.put(SearchConstants.values, filterEntry.getValue());
                            property.put(SearchConstants.propertyName, entry.getKey());
                            switch (filterEntry.getKey()) {
                                case "startsWith": {
                                    property.put(SearchConstants.operation,
                                            SearchConstants.SEARCH_OPERATION_STARTS_WITH);
                                    break;
                                }
                                case "endsWith": {
                                    property.put(SearchConstants.operation,
                                            SearchConstants.SEARCH_OPERATION_ENDS_WITH);
                                    break;
                                }
                                case SearchConstants.SEARCH_OPERATION_NOT_EQUAL_OPERATOR:
                                case SearchConstants.SEARCH_OPERATION_NOT_EQUAL_TEXT:
                                case SearchConstants.SEARCH_OPERATION_NOT_EQUAL_TEXT_LOWERCASE:
                                case SearchConstants.SEARCH_OPERATION_NOT_EQUAL_TEXT_UPPERCASE:
                                    property.put(SearchConstants.operation,
                                            SearchConstants.SEARCH_OPERATION_NOT_EQUAL);
                                    break;
                                case SearchConstants.SEARCH_OPERATION_NOT_IN_OPERATOR:
                                    property.put(SearchConstants.operation,
                                            SearchConstants.SEARCH_OPERATION_NOT_IN);
                                    break;
                                case SearchConstants.SEARCH_OPERATION_GREATER_THAN:
                                case SearchConstants.SEARCH_OPERATION_GREATER_THAN_EQUALS:
                                case SearchConstants.SEARCH_OPERATION_LESS_THAN_EQUALS:
                                case SearchConstants.SEARCH_OPERATION_LESS_THAN: {
                                    property.put(SearchConstants.operation, filterEntry.getKey());
                                    break;
                                }
                                case "value":
                                case SearchConstants.SEARCH_OPERATION_CONTAINS_OPERATOR: {
                                    property.put(SearchConstants.operation,
                                            SearchConstants.SEARCH_OPERATION_CONTAINS);
                                    break;
                                }
                                case SearchConstants.SEARCH_OPERATION_AND:
                                case SearchConstants.SEARCH_OPERATION_AND_OPERATOR:
                                case SearchConstants.SEARCH_OPERATION_AND_TEXT_LOWERCASE: {
                                    property.put(SearchConstants.operation, SearchConstants.SEARCH_OPERATION_AND);
                                    break;
                                }
                                default: {
                                    TelemetryManager.error("Invalid filters, Unsupported operation:: " + filterEntry.getKey() + ":: filters::" + filters);
                                    throw new Exception("Unsupported operation");
                                }
                            }
                            properties.add(property);
                        }
                    } else {
                        Map<String, Object> property = new HashMap<String, Object>();
                        Map<String, Object> rangeMap = new HashMap<String, Object>();
                        Object minFilterValue = filterMap.get(SearchConstants.SEARCH_OPERATION_RANGE_MIN);
                        if (minFilterValue != null) {
                            rangeMap.put(SearchConstants.SEARCH_OPERATION_RANGE_GTE, minFilterValue);
                        }
                        Object maxFilterValue = filterMap.get(SearchConstants.SEARCH_OPERATION_RANGE_MAX);
                        if (maxFilterValue != null) {
                            rangeMap.put(SearchConstants.SEARCH_OPERATION_RANGE_LTE, maxFilterValue);
                        }
                        property.put(SearchConstants.values, rangeMap);
                        property.put(SearchConstants.propertyName, entry.getKey());
                        property.put(SearchConstants.operation,
                                SearchConstants.SEARCH_OPERATION_RANGE);
                        properties.add(property);
                    }
                } else {
                    boolean emptyVal = false;
                    if (null == filterObject) {
                        emptyVal = true;
                    } else if (filterObject instanceof List) {
                        if (((List) filterObject).size() <= 0)
                            emptyVal = true;
                    } else if (filterObject instanceof Object[]) {
                        if (((Object[]) filterObject).length <= 0)
                            emptyVal = true;
                    }
                    if (!emptyVal) {
                        Map<String, Object> property = new HashMap<String, Object>();
                        property.put(SearchConstants.values, entry.getValue());
                        property.put(SearchConstants.propertyName, entry.getKey());
                        property.put(SearchConstants.operation,
                                SearchConstants.SEARCH_OPERATION_EQUAL);
                        properties.add(property);
                    }
                }
            }
        }

        if (!filters.containsKey("status") && !traversal) {
            Map<String, Object> property = getFilterProperty("status", SearchConstants.SEARCH_OPERATION_EQUAL, Arrays.asList(new String[] { "Live" }));
            properties.add(property);
        }

        if (request != null && StringUtils.equalsIgnoreCase((String) request.getContext().getOrDefault("setDefaultVisibility",""),"true")  && setDefaultVisibility(filters)) {
            Map<String, Object> property = getFilterProperty("visibility", SearchConstants.SEARCH_OPERATION_EQUAL, Arrays.asList(new String[] { "Default" }));
            properties.add(property);
        }
        return properties;
    }

    private void getAggregations(Map<String, Object> req, SearchDTO searchObj) {
        if(null != req.get("aggregations") && CollectionUtils.isNotEmpty((List<Map<String, Object>>) req.get("aggregations"))){
            searchObj.setAggregations((List<Map<String, Object>>) req.get("aggregations"));
        }

    }

    private void setImplicitFilters(Map<String, Object> filters, SearchDTO searchObj) throws Exception {
        Map<String, Object> implicitFilter = new HashMap<String, Object>();
        if (MapUtils.isNotEmpty(filters) && filters.containsKey("board")) {
            for (String key : filters.keySet()) {
                if (StringUtils.equalsIgnoreCase("board", key)) {
                    implicitFilter.put("relatedBoards", filters.get(key));
                } else if (StringUtils.equalsIgnoreCase("status", key)) {
                    implicitFilter.put("status", "Live");
                } else {
                    implicitFilter.put(key, filters.get(key));
                }
            }
            List<Map> implicitFilterProps = new ArrayList<Map>();
            implicitFilterProps.addAll(getSearchFilterProperties(implicitFilter, false, null));
            searchObj.setImplicitFilterProperties(implicitFilterProps);
        }
    }
    private boolean checkPublishedStatus(Map<String, Object> filters) {
        List<String> statuses = Arrays.asList("Live", "Unlisted");
        Object status =filters.get("status");
        List<String> statusList = null;
        if(null == status) {
            return true;
        } else if((status instanceof String) && (statuses.contains(status))){
            statusList = Arrays.asList((String) status);
        } else if(status instanceof String[]) {
            statusList = Arrays.asList((String[]) status);
        } else if(status instanceof List) {
            statusList = (List<String>) status;
        }

        if(CollectionUtils.isNotEmpty(statusList) && statusList.size() == 1 && statuses.contains(statusList.get(0)))
            return true;
        else if(CollectionUtils.isNotEmpty(statusList) && statuses.containsAll(statusList))
            return true;
        else
            return false;

    }
    private List<String> getObjectTypesWithVisibility() {
        List<String> objectTypes = Platform.getStringList("object.withVisibility", Arrays.asList("content", "collection", "asset", "question", "questionset"));
        if (CollectionUtils.isEmpty(objectTypes)) {
            return new ArrayList<String>();
        } else {
            List<String> finalObjectTypes = new ArrayList<String>(objectTypes);
            finalObjectTypes.addAll(objectTypes.stream().map(s -> s + "image").collect(Collectors.toList()));
            return finalObjectTypes;
        }
    }

    private boolean setDefaultVisibility(Map<String, Object> filters) {
        boolean hasVisibility = filters.containsKey("visibility");
        if (!hasVisibility) {
            List<String> objectTypes = ((List<String>) filters.getOrDefault(SearchConstants.objectType, new ArrayList<String>()))
                    .stream().map(s -> s.toLowerCase()).collect(Collectors.toList());
            List<String> configObjectTypes = getObjectTypesWithVisibility();
            return CollectionUtils.containsAny(configObjectTypes, objectTypes);
        } else return false;
    }

    private Map<String, Object> getFilterProperty(String propName, String operation, Object value) {
        return new HashMap<String, Object>() {{
            put(SearchConstants.operation, operation);
            put(SearchConstants.propertyName, propName);
            put(SearchConstants.values, value);
        }};
    }

    @SuppressWarnings("unchecked")
    public List<String> getList(Object param) {
        List<String> paramList;
        try {
            paramList = (List<String>) param;
        } catch (Exception e) {
            String str = (String) param;
            paramList = new ArrayList<String>();
            paramList.add(str);
        }
        return paramList;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCompositeSearchResponse(Map<String, Object> searchResponse) {
        Map<String, Object> respResult = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : searchResponse.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("results")) {
                List<Object> lstResult = (List<Object>) entry.getValue();
                if (null != lstResult && !lstResult.isEmpty()) {
                    Map<String, List<Map<String, Object>>> result = new HashMap<String, List<Map<String, Object>>>();
                    for (Object obj : lstResult) {
                        if (obj instanceof Map) {
                            Map<String, Object> map = (Map<String, Object>) obj;
                            String objectType = ((String) map.getOrDefault("objectType", "")).replaceAll("Image", "");
                            if(StringUtils.equalsIgnoreCase("Collection", objectType) || StringUtils.equalsIgnoreCase("Asset", objectType))
                                map.replace("objectType", "Content");
                            else
                                map.replace("objectType", objectType);
                            if (StringUtils.isNotBlank(objectType)) {
                                String key = getResultParamKey(objectType);
                                if (StringUtils.isNotBlank(key)) {
                                    List<Map<String, Object>> list = result.get(key);
                                    if (null == list) {
                                        list = new ArrayList<Map<String, Object>>();
                                        result.put(key, list);
                                        respResult.put(key, list);
                                    }
                                    String id = (String) map.get("identifier");
                                    if (id.endsWith(".img")) {
                                        id = id.replace(".img", "");
                                        map.replace("identifier", id);
                                    }
                                    list.add(map);
                                }
                            }
                        }
                    }
                }
            } else {
                respResult.put(entry.getKey(), entry.getValue());
            }
        }
        return respResult;
    }

    private String getResultParamKey(String objectType) {
        if (StringUtils.isNotBlank(objectType)) {
            if (StringUtils.equalsIgnoreCase("Domain", objectType))
                return "domains";
            else if (StringUtils.equalsIgnoreCase("Dimension", objectType))
                return "dimensions";
            else if (StringUtils.equalsIgnoreCase("Concept", objectType))
                return "concepts";
            else if (StringUtils.equalsIgnoreCase("Method", objectType))
                return "methods";
            else if (StringUtils.equalsIgnoreCase("Misconception", objectType))
                return "misconceptions";
            else if (StringUtils.equalsIgnoreCase("Content", objectType) || StringUtils.equalsIgnoreCase("Collection", objectType) || StringUtils.equalsIgnoreCase("Asset", objectType))
                return "content";
            else if (StringUtils.equalsIgnoreCase("AssessmentItem", objectType))
                return "items";
            else if (StringUtils.equalsIgnoreCase("ItemSet", objectType))
                return "itemsets";
            else if (StringUtils.equalsIgnoreCase("Word", objectType))
                return "words";
            else if (StringUtils.equalsIgnoreCase("Synset", objectType))
                return "synsets";
            else if (StringUtils.equalsIgnoreCase("License", objectType))
                return "license";
            else
                return objectType;
        }
        return null;
    }

}
