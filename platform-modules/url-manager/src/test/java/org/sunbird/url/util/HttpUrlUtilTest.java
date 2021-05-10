package org.sunbird.url.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ServerException;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This Class Holds Unit Test Cases For HttpUrlUtil
 *
 * @see HTTPUrlUtil
 */
public class HttpUrlUtilTest {

	@Rule
	public final ExpectedException exception = ExpectedException.none();

	@Test
	public void testGetMetadataWithValidUrl() {
		String url = "https://ekstep-public-prod.s3-ap-south-1.amazonaws.com/content/do_31225266105597952011502/artifact/2246d6bdafaeae551d93e245fa484cc9_1495686214370.jpeg";
		Map<String, Object> result = HTTPUrlUtil.getMetadata(url);
		assertTrue(result.size() == 2);
		assertTrue(result.containsKey("size"));
		assertTrue(result.containsKey("type"));
		assertEquals("image/jpeg", (String) result.get("type"));
		assertTrue((Long) result.get("size") > 0);
	}

	@Test
	public void testGetMetadataWithInvalidUrlInvalidUrlError() {
		exception.expect(ClientException.class);
		exception.expectMessage("Please Provide Valid Url.");
		String url = "https://abcd/demo.pdf";
		Map<String, Object> result = HTTPUrlUtil.getMetadata(url);
	}

	@Test
	public void testGetMetadataWithValidUrlExpectFileNotFoundError() {
		exception.expect(ClientException.class);
		exception.expectMessage("File Not Found.");
		String url = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy1.pdf";
		Map<String, Object> result = HTTPUrlUtil.getMetadata(url);
	}

	@Test
	public void testGetMetadataWithValidUrlExpectServerException() {
		exception.expect(ServerException.class);
		exception.expectMessage("Something Went Wrong While Processing Your Request. Please Try Again After Sometime!");
		String url = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/";
		Map<String, Object> result = HTTPUrlUtil.getMetadata(url);
	}
}
