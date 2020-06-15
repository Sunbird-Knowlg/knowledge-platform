package org.sunbird.common;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.common.exception.ServerException;

import java.util.HashMap;
import java.util.Map;

public class HttpUtil {

	private static final String PLATFORM_API_USERID = "System";
	private static final String DEFAULT_CONTENT_TYPE = "application/json";

	/**
	 * @param url
	 * @param requestMap
	 * @param headerParam
	 * @return Response
	 * @throws Exception
	 */
	public Response post(String url, Map<String, Object> requestMap, Map<String, String> headerParam)
			throws Exception {
		validateRequest(url, headerParam);
		setDefaultHeader(headerParam);
		if (MapUtils.isEmpty(requestMap))
			throw new ServerException("ERR_INVALID_REQUEST_BODY", "Request Body is Missing!");
		try {
			HttpResponse<String> response = Unirest.post(url).headers(headerParam).body(JsonUtils.serialize(requestMap)).asString();
			return getResponse(response);
		} catch (Exception e) {
			throw new ServerException("ERR_API_CALL", "Something Went Wrong While Making API Call | Error is: " + e.getMessage());
		}
	}

	/**
	 * @param url
	 * @param queryParam
	 * @param headerParam
	 * @return Response
	 * @throws Exception
	 */
	public Response get(String url, String queryParam, Map<String, String> headerParam)
			throws Exception {
		validateRequest(url, headerParam);
		setDefaultHeader(headerParam);
		String reqUrl = StringUtils.isNotBlank(queryParam) ? url + "?" + queryParam : url;
		try {
			HttpResponse<String> response = Unirest.get(reqUrl).headers(headerParam).asString();
			return getResponse(response);
		} catch (Exception e) {
			throw new ServerException("ERR_API_CALL", "Something Went Wrong While Making API Call | Error is: " + e.getMessage());
		}
	}

	/**
	 *  This method is to get file related metadata (size and mimeType)from file url, without downloading.
	 * @param url
	 * @param headers
	 * @return
	 */
	public Map<String, String> getFileMetadata(String url, Map<String, String> headers) {
		try {
			validateRequest(url, headers);
			setDefaultHeader(headers);
			Map<String, String> metadataMap = new HashMap<>();
			HttpResponse<String> response = Unirest.head(url).headers(headers).asString();
			if (response.getStatus() == 200) {
				metadataMap.put("size", response.getHeaders().get("Content-Length").get(0));
				metadataMap.put("mimeType", response.getHeaders().get("Content-Type").get(0));
				return metadataMap;
			} else {
				throw new ClientException("ERR_API_CALL", "Fetching file related metadata Failed with response code " + response.getStatus() + "and message: " + response.getStatusText());
			}
		} catch (Exception e) {
			throw new ServerException("ERR_API_CALL", "Something Went Wrong While Making API Call | Error is: " + e.getMessage());
		}
	}

	private void validateRequest(String url, Map<String, String> headerParam) {
		if (StringUtils.isBlank(url))
			throw new ServerException("ERR_INVALID_URL", "Url Parameter is Missing!");
		if (MapUtils.isEmpty(headerParam))
			throw new ServerException("ERR_INVALID_HEADER_PARAM", "Header Parameter is Missing!");
	}

	private Response getResponse(HttpResponse<String> response) {
		if (null != response && StringUtils.isNotBlank(response.getBody())) {
			try {
				return JsonUtils.deserialize(response.getBody(), Response.class);
			} catch (Exception e) {
				throw new ServerException("ERR_DATA_PARSER", "Unable to parse data! | Error is: " + e.getMessage());
			}
		} else
			return ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), "Null Response Received While Making Api Call!");
	}

	private void setDefaultHeader(Map<String, String> headerParam) {
		if(!headerParam.containsKey("Content-Type"))
			headerParam.put("Content-Type", DEFAULT_CONTENT_TYPE);
		if(!headerParam.containsKey("user-id"))
			headerParam.put("user-id", PLATFORM_API_USERID);
	}
}
