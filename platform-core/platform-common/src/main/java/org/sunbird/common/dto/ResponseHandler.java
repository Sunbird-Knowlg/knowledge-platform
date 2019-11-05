package org.sunbird.common.dto;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ResponseHandler {

    public static Response handleResponses(List<Response> responses) {
        ResponseCode responseCode = getResponseCode(responses);
        Response response;
        if (StringUtils.equals(ResponseCode.OK.name(), responseCode.name()))
            response = handleSuccessResponse(responseCode, responses);
        else
            response = handleErrorResponse(responseCode, responses);
        return response;
    }

    private static Response handleErrorResponse(ResponseCode priorityResponseCode, List<Response> responses) {
        Response finalResponse = new Response();
        ResponseParams responseParams = new ResponseParams();
        responseParams.setStatus(ResponseParams.StatusType.failed.name());
        responseParams.setErr(priorityResponseCode.name());
        responseParams.setErrmsg(priorityResponseCode.name());
        finalResponse.setResponseCode(priorityResponseCode);
        finalResponse.setParams(responseParams);
        handleUnsuccessfulMessages(finalResponse, responses);
        handleSuccessfulMessages(finalResponse, responses);
        return finalResponse;
    }


    private static Response handleSuccessResponse(ResponseCode priorityResponseCode, List<Response> responses) {
        Response finalResponse = new Response();
        ResponseParams responseParams = new ResponseParams();
        responseParams.setStatus(ResponseParams.StatusType.successful.name());
        finalResponse.setResponseCode(priorityResponseCode);
        finalResponse.setParams(responseParams);
        handleSuccessfulMessages(finalResponse, responses);
        return finalResponse;
    }

    private static ResponseCode getResponseCode(List<Response> responses) {
        List<ResponseCode> responseCodes = responses.stream().map(response -> response.getResponseCode()).collect(Collectors.toList());
        if (responseCodes.contains(ResponseCode.SERVER_ERROR))
            return ResponseCode.SERVER_ERROR;
        if (responseCodes.contains(ResponseCode.PARTIAL_SUCCESS))
            return ResponseCode.PARTIAL_SUCCESS;
        if (responseCodes.contains(ResponseCode.CLIENT_ERROR))
            return ResponseCode.CLIENT_ERROR;
        if (responseCodes.contains(ResponseCode.RESOURCE_NOT_FOUND))
            return ResponseCode.RESOURCE_NOT_FOUND;
        return ResponseCode.OK;
    }

    private static Response handleSuccessfulMessages(Response finalResponse, List<Response> responses) {
        finalResponse.putAll(responses.stream()
                .filter(response -> response.getResponseCode() == ResponseCode.OK)
                .flatMap(response -> response.getResult().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (val1, val2) -> {
                            if (val1 instanceof List) {
                                return new ArrayList() {{
                                    addAll((List) val1);
                                    add(val2);
                                }};
                            } else
                                return (Arrays.asList(val1, val2));
                        })));
        return finalResponse;
    }

    private static Response handleUnsuccessfulMessages(Response finalResponse, List<Response> responses) {
        finalResponse.putAll(responses.stream()
                .filter(response -> response.getResponseCode() != ResponseCode.OK)
                .collect(Collectors.toMap(response -> response.getResponseCode().name(),
                        response -> response.getParams().getErrmsg(),
                        (val1, val2) -> {
                            if (val1 instanceof List) {
                                return new ArrayList() {{
                                    addAll((List) val1);
                                    add(val2);
                                }};
                            } else
                                return (Arrays.asList(val1, val2));
                        })));
        return finalResponse;
    }

    public static Response OK() {
        Response response = new Response();
        response.setParams(getSucessStatus());
        return response;
    }

    private static ResponseParams getSucessStatus() {
        ResponseParams params = new ResponseParams();
        params.setErr("0");
        params.setStatus(ResponseParams.StatusType.successful.name());
        params.setErrmsg("Operation successful");
        return params;
    }

    public static Response ERROR(ResponseCode responseCode, String errorCode, String errorMessage) {
        Response response = new Response();
        response.setParams(getErrorStatus(errorCode, errorMessage));
        response.setResponseCode(responseCode);
        return response;
    }

    private static ResponseParams getErrorStatus(String errorCode, String errorMessage) {
        ResponseParams params = new ResponseParams();
        params.setErr(errorCode);
        params.setStatus(ResponseParams.StatusType.failed.name());
        params.setErrmsg(errorMessage);
        return params;
    }

    public static boolean checkError(Response response) {
        ResponseParams params = response.getParams();
        if (null != params) {
            if (StringUtils.equals(ResponseParams.StatusType.failed.name(), params.getStatus())) {
                return true;
            }
        }
        return false;
    }

    public static String getErrorMessage(Response response) {
        ResponseParams params = response.getParams();
        if (null != params) {
            return params.getErrmsg();
        }
        return null;
    }
}
