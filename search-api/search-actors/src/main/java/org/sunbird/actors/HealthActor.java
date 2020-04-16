package org.sunbird.actors;

import akka.dispatch.Futures;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.common.Platform;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseParams;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.search.client.ElasticSearchUtil;
import org.sunbird.search.util.SearchConstants;
import scala.concurrent.Future;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HealthActor extends BaseActor {
    @Override
    public Future<Response> onReceive(Request request) throws Throwable {
        ElasticSearchUtil.initialiseESClient(SearchConstants.COMPOSITE_SEARCH_INDEX, Platform.config.getString("search.es_conn_info"));
        return checkIndexHealth();
    }

    private Future<Response> checkIndexHealth() {
        List<Map<String, Object>> checks = new ArrayList<Map<String, Object>>();
        Response response = new Response();
        boolean index = false;
        try {
            index = ElasticSearchUtil.isIndexExists(SearchConstants.COMPOSITE_SEARCH_INDEX);
            if (index == true) {
                checks.add(getResponseData(response, true, "", ""));
                response.put("checks", checks);
            } else {
                checks.add(getResponseData(response, false, "404", "Elastic Search index is not avaialable"));
            }
        } catch (Exception e) {
            checks.add(getResponseData(response, false, "503", e.getMessage()));
        }
        return Futures.successful(response);
    }

    public Map<String, Object> getResponseData(Response response, boolean res, String error, String errorMsg) {
        ResponseParams params = new ResponseParams();
        String err = error;
        Map<String, Object> esCheck = new HashMap<String, Object>();
        esCheck.put("name", "ElasticSearch");
        if (res == true && err.isEmpty()) {
            params.setErr("0");
            params.setStatus(ResponseParams.StatusType.successful.name());
            params.setErrmsg("Operation successful");
            response.setParams(params);
            response.put("healthy", true);
            esCheck.put("healthy", true);
        } else {
            params.setStatus(ResponseParams.StatusType.failed.name());
            params.setErrmsg(errorMsg);
            response.setResponseCode(ResponseCode.SERVER_ERROR);
            response.setParams(params);
            response.put("healthy", false);
            esCheck.put("healthy", false);
            esCheck.put("err", err);
            esCheck.put("errmsg", errorMsg);
        }
        return esCheck;
    }
}
