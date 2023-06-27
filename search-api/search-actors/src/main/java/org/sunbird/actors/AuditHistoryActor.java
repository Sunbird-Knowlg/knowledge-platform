package org.sunbird.actors;

import akka.dispatch.Mapper;
import akka.util.Timeout;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.json.JsonUtil;
import org.sunbird.common.JsonUtils;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.AuditProperties;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.processor.SearchProcessor;
import org.sunbird.search.util.SearchConstants;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
                        List<Map<String, Object>> results = (List<Map<String, Object>>) lstResult.get("results");
                        for (Map<String, Object> result : results) {
                            if (result.containsKey("logRecord")) {
                                String logRecordStr = (String) result.get("logRecord");
                                if (logRecordStr != null && !logRecordStr.isEmpty()) {
                                    try {
                                        Map<String, Object> logRecord = JsonUtils.deserialize(logRecordStr, Map.class);
                                        result.put("logRecord", logRecord);
                                    } catch (Exception e) {
                                        throw new ServerException("ERR_DATA_PARSER", "Unable to parse data! | Error is: " + e.getMessage());
                                    }
                                }
                            }
                        }
                        return OK(AuditProperties.audit_history_record.name(), lstResult);
                    }

                }, getContext().dispatcher());
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
}

