package org.sunbird.common;

import com.mashape.unirest.http.HttpClientHelper;
import com.mashape.unirest.http.HttpResponse;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicStatusLine;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.dto.Response;
import org.sunbird.common.exception.ServerException;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(HttpClientHelper.class)
@PowerMockIgnore({"jdk.internal.reflect.*", "javax.management.*", "sun.security.ssl.*", "javax.net.ssl.*", "javax.crypto.*"})
public class HttpUtilTest {

	private static HttpResponse<String> httpResponse = null;
	private static HttpUtil httpUtil = new HttpUtil();

	@Before
	public void setup() throws Exception {
		String body = "{\"id\":\"api.content.create\",\"ver\":\"3.0\",\"ts\":\"2020-04-19T21:54:12ZZ\",\"params\":{\"resmsgid\":\"47f07524-3246-4731-9eae-17bab692d3a9\",\"msgid\":null,\"err\":null,\"status\":\"successful\",\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"identifier\":\"do_411300343400543846413\",\"node_id\":\"do_411300343400543846413\",\"versionKey\":\"1587333252620\"}}";
		mockStatic(HttpClientHelper.class);
		HttpResponseFactory factory = new DefaultHttpResponseFactory();
		org.apache.http.HttpResponse response = factory.newHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null), null);
		response.setEntity(new StringEntity(body));
		httpResponse = new HttpResponse(response, String.class);
		when(HttpClientHelper.request(Mockito.anyObject(), Mockito.eq(String.class))).thenReturn(httpResponse);
	}

	@Test(expected = ServerException.class)
	public void testValidateRequestWithEmptyUrl() throws Exception {
		Method method = HttpUtil.class.getDeclaredMethod("validateRequest", String.class, Map.class);
		method.setAccessible(true);
		Map<String, String> header = new HashMap<String, String>() {{
			put("x-channel-id", "test-channel");
		}};
		method.invoke(httpUtil, null, header);
	}

	@Test(expected = ServerException.class)
	public void testValidateRequestWithEmptyHeader() throws Exception {
		Method method = HttpUtil.class.getDeclaredMethod("validateRequest", String.class, Map.class);
		method.setAccessible(true);
		method.invoke(httpUtil, "http://test.com", null);
	}

	@Test
	public void testSetDefaultHeader() throws Exception {
		Method method = HttpUtil.class.getDeclaredMethod("setDefaultHeader", Map.class);
		method.setAccessible(true);
		Map<String, String> header = new HashMap<String, String>();
		method.invoke(httpUtil, header);
		assertTrue(header.size() == 2);
		assertTrue(header.containsKey("Content-Type"));
	}

	@Test
	public void testPost() throws Exception {
		Map<String, String> header = new HashMap<String, String>() {{
			put("x-channel-id", "test-channel");
		}};
		Map<String, Object> req = new HashMap<String, Object>() {{
			put("request", new HashMap<String, Object>() {{
				put("content", new HashMap<String, Object>() {{
					put("name", "Test Name");
				}});
			}});
		}};
		Response response = httpUtil.post("http://test.com/abc", req, header);
		validateResponse(response);
	}

	@Test(expected = ServerException.class)
	public void testPostWithInvalidRequest() throws Exception {
		Map<String, String> header = new HashMap<String, String>() {{
			put("x-channel-id", "test-channel");
		}};
		httpUtil.post("http://test.com/abc", new HashMap<String, Object>(), header);
	}

	@Test
	public void testGet() throws Exception {
		Map<String, String> header = new HashMap<String, String>() {{
			put("x-channel-id", "test-channel");
		}};
		Response response = httpUtil.get("http://test.com/abc", null, header);
		validateResponse(response);
	}

	@Test
	public void testGetWithQueryString() throws Exception {
		Map<String, String> header = new HashMap<String, String>() {{
			put("x-channel-id", "test-channel");
		}};
		Response response = httpUtil.get("http://test.com/abc", "mode=edit", header);
		validateResponse(response);
	}

	@Test
	public void testGetResponse() throws Exception {
		Method method = HttpUtil.class.getDeclaredMethod("getResponse", HttpResponse.class);
		method.setAccessible(true);
		Response resp = (Response) method.invoke(httpUtil, httpResponse);
		validateResponse(resp);
	}

	@Test
	public void testGetResponseWithEmptyBody() throws Exception {
		HttpResponseFactory factory = new DefaultHttpResponseFactory();
		org.apache.http.HttpResponse response = factory.newHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null), null);
		response.setEntity(new StringEntity(""));
		HttpResponse<String> httpResp = new HttpResponse(response, String.class);
		Method method = HttpUtil.class.getDeclaredMethod("getResponse", HttpResponse.class);
		method.setAccessible(true);
		Response resp = (Response) method.invoke(httpUtil, httpResp);
		assertTrue(null != resp);
		assertTrue(StringUtils.equalsIgnoreCase("SERVER_ERROR", resp.getResponseCode().toString()));
	}

	@Test(expected = ServerException.class)
	public void testGetResponseWithInvalidBody() throws Exception {
		HttpResponseFactory factory = new DefaultHttpResponseFactory();
		org.apache.http.HttpResponse response = factory.newHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null), null);
		response.setEntity(new StringEntity("test"));
		HttpResponse<String> httpResp = new HttpResponse(response, String.class);
		Method method = HttpUtil.class.getDeclaredMethod("getResponse", HttpResponse.class);
		method.setAccessible(true);
		method.invoke(httpUtil, httpResp);
	}

	private void validateResponse(Response resp) {
		assertTrue(null != resp);
		assertTrue(StringUtils.equalsIgnoreCase("OK", resp.getResponseCode().toString()));
		assertTrue(StringUtils.isNotEmpty(resp.getId()));
		assertTrue(MapUtils.isNotEmpty(resp.getResult()));
		assertTrue(StringUtils.equals("do_411300343400543846413", (String) resp.getResult().get("identifier")));

	}
}
