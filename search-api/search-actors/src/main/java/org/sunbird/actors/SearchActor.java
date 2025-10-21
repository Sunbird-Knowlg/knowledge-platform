package org.sunbird.actors;

import org.apache.pekko.dispatch.Futures;
import org.apache.pekko.dispatch.Mapper;
import org.apache.pekko.dispatch.Recover;
import org.apache.pekko.util.Timeout;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.JsonUtils;
import org.sunbird.common.Platform;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.processor.SearchProcessor;
import org.sunbird.search.util.DefinitionUtil;
import org.sunbird.search.util.SearchConstants;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SearchActor extends SearchBaseActor {
    private static Timeout WAIT_TIMEOUT = new Timeout(Duration.create(30000, TimeUnit.MILLISECONDS));

    @Override
    public Future<Response> onReceive(Request request) throws Throwable {
        String operation = request.getOperation();
        SearchProcessor processor = new SearchProcessor();
        try{
            if (StringUtils.equalsIgnoreCase("INDEX_SEARCH", operation)) {
                SearchDTO searchDTO = getSearchDTO(request);
                Future<Map<String, Object>> searchResult = processor.processSearch(searchDTO, true);
                return searchResult.map(new Mapper<Map<String, Object>, Response>() {
                    @Override
                    public Response apply(Map<String, Object> lstResult) {
                        String mode = (String) request.getRequest().get(SearchConstants.mode);
                        if (StringUtils.isNotBlank(mode) && StringUtils.equalsIgnoreCase("collection", mode)) {
                            return OK(getCollectionsResult(lstResult, processor, request));
                        } else {
                            return OK(lstResult);
                        }
                    }
                }, getContext().dispatcher()).recoverWith(new Recover<Future<Response>>() {
                    @Override
                    public Future<Response> recover(Throwable failure) throws Throwable {
                        TelemetryManager.error("Unable to process the request:: Request: " + JsonUtils.serialize(request), failure);
                        return ERROR(request.getOperation(), failure);
                    }
                }, getContext().dispatcher());
            } else if (StringUtils.equalsIgnoreCase("COUNT", operation)) {
                Map<String, Object> countResult = processor.processCount(getSearchDTO(request));
                if (null != countResult.get("count")) {
                    Integer count = (Integer) countResult.get("count");
                    return Futures.successful(OK("count", count));
                } else {
                    return Futures.successful(ERROR("", "count is empty or null", ResponseCode.SERVER_ERROR, "", null));
                }
            } else if (StringUtils.equalsIgnoreCase("METRICS", operation)) {
                Future<Map<String, Object>> searchResult = processor.processSearch(getSearchDTO(request), false);
                return searchResult.map(new Mapper<Map<String, Object>, Response>() {
                    @Override
                    public Response apply(Map<String, Object> lstResult) {
                        return OK(getCompositeSearchResponse(lstResult));
                    }
                }, getContext().dispatcher());
            } else if (StringUtils.equalsIgnoreCase("GROUP_SEARCH_RESULT_BY_OBJECTTYPE", operation)) {
                Map<String, Object> searchResponse = (Map<String, Object>) request.get("searchResult");
                return Futures.successful(OK(getCompositeSearchResponse(searchResponse)));
            } else {
                TelemetryManager.log("Unsupported operation: " + operation);
                throw new ClientException(SearchConstants.ERR_INVALID_OPERATION,
                        "Unsupported operation: " + operation);
            }
        } catch (Exception e) {
            TelemetryManager.info("Error while processing the request: REQUEST::" + JsonUtils.serialize(request));
            return ERROR(operation, e);
        }
    }


    @SuppressWarnings({ "rawtypes", "unused" })
    private boolean isEmpty(Object o) {
        boolean result = false;
        if (o instanceof String) {
            result = StringUtils.isBlank((String) o);
        } else if (o instanceof List) {
            result = ((List) o).isEmpty();
        } else if (o instanceof String[]) {
            result = (((String[]) o).length <= 0);
        }
        return result;
    }

    /**
     * @param lstResult
     * @param processor
     * @param parentRequest
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Map<String, Object> getCollectionsResult(Map<String, Object> lstResult, SearchProcessor processor,
                                                     Request parentRequest) {
        List<Map> contentResults = (List<Map>) lstResult.get("results");
        if (null != contentResults && !contentResults.isEmpty()) {
            try {
                List<String> contentIds = new ArrayList<String>();
                for (Map<String, Object> content : contentResults) {
                    contentIds.add((String) content.get("identifier"));
                }

                Request request = new Request(parentRequest);
                Map<String, Object> filters = new HashMap<String, Object>();
                List<String> objectTypes = new ArrayList<String>();
                objectTypes.add("Content");
                objectTypes.add("Collection");
                objectTypes.add("Asset");
                filters.put(SearchConstants.objectType, objectTypes);
                List<String> mimeTypes = new ArrayList<String>();
                mimeTypes.add("application/vnd.ekstep.content-collection");
                filters.put("mimeType", mimeTypes);
                filters.put("childNodes", contentIds);
                request.put(SearchConstants.sort_by,
                        parentRequest.get(SearchConstants.sort_by));
                request.put(SearchConstants.fields,
                        getCollectionFields(getList(parentRequest.get(SearchConstants.fields))));
                request.put(SearchConstants.filters, filters);
                SearchDTO searchDTO = getSearchDTO(request);
                Map<String, Object> collectionResult = Await.result(processor.processSearch(searchDTO, true),
                        WAIT_TIMEOUT.duration());
                collectionResult = prepareCollectionResult(collectionResult, contentIds);
                lstResult.putAll(collectionResult);
                return lstResult;
            } catch (Exception e) {
                TelemetryManager.error("Error while fetching the collection for the contents : ", e);
                return lstResult;
            }

        } else {
            return lstResult;
        }
    }

    /**
     * @param fieldlist
     * @return
     * @return
     */
    private Object getCollectionFields(List<String> fieldlist) {
        List<String> fields = Platform.config.hasPath("search.fields.mode_collection")
                ? Platform.config.getStringList("search.fields.mode_collection")
                : Arrays.asList("identifier", "name", "objectType", "contentType", "mimeType", "size", "childNodes");

        if (null != fieldlist && !fieldlist.isEmpty()) {
            fields.addAll(fieldlist);
            fields = fields.stream().distinct().collect(Collectors.toList());
        }
        return fields;
    }

    /**
     * @param collectionResult
     * @param contentIds
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Map<String, Object> prepareCollectionResult(Map<String, Object> collectionResult, List<String> contentIds) {
        List<Map> results = new ArrayList<Map>();
        for (Map<String, Object> collection : (List<Map>) collectionResult.get("results")) {
            List<String> childNodes = (List<String>) collection.get("childNodes");
            childNodes = (List<String>) CollectionUtils.intersection(childNodes, contentIds);
            collection.put("childNodes", childNodes);
            results.add(collection);
        }
        collectionResult.put("collections", results);
        collectionResult.put("collectionsCount", collectionResult.get("count"));
        collectionResult.remove("count");
        collectionResult.remove("results");
        return collectionResult;
    }


}
