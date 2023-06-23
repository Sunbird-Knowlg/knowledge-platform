package org.sunbird.actors;

import akka.dispatch.Mapper;
import akka.util.Timeout;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.JsonUtils;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.exception.ClientException;
import org.sunbird.graph.common.enums.AuditProperties;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.processor.SearchProcessor;
import org.sunbird.search.util.SearchConstants;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AuditHistoryActor  extends SearchBaseActor {


    private static Timeout WAIT_TIMEOUT = new Timeout(Duration.create(30000, TimeUnit.MILLISECONDS));

    @Override
    public Future<Response> onReceive(Request request) throws Throwable {
        String operation = request.getOperation();
        SearchProcessor processor = new SearchProcessor();
        try {
            if (StringUtils.equalsIgnoreCase("SEARCH_OPERATION_AND", operation)) {
                SearchDTO searchDTO = getSearchDTO(request);
                TelemetryManager.log("setting search criteria to fetch audit records from ES: " + searchDTO);
                Future<Map<String, Object>> searchResult = processor.processSearch(searchDTO, true);
                return searchResult.map(new Mapper<Map<String, Object>, Response>() {
                    @Override
                    public Response apply(Map<String, Object> lstResult) {
                        return OK(getCompositeSearchResponse(lstResult));
                    }
                }, getContext().dispatcher());

            }

            else {
                TelemetryManager.log("Unsupported operation: " + operation);
                throw new ClientException(SearchConstants.ERR_INVALID_OPERATION,
                        "Unsupported operation: " + operation);
            }

        } catch (Exception e) {
            TelemetryManager.info("Error while processing the request: REQUEST::" + JsonUtils.serialize(request));
            return ERROR(operation, e);
        }
    }

    public List<String> setSearchCriteria(String versionId) {
        return setSearchCriteria(versionId, false);
    }
    public List<String> setSearchCriteria(String versionId, boolean returnAllFields) {
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
        if (returnAllFields) {
            fields.add("logRecord");
            fields.add("summary");
        } else {
            if (StringUtils.equalsIgnoreCase("1.0", versionId))
                fields.add("logRecord");
            else
                fields.add("summary");
        }
        TelemetryManager.log("returning the search criteria fields: " + fields.size());
        return fields;
    }
}
