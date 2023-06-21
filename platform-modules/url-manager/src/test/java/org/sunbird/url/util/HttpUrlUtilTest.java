package org.sunbird.url.util;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ServerException;

import java.io.IOException;
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
		String url = "https://sunbirddevbbpublic.blob.core.windows.net/sunbird-content-staging/content/assets/do_2137327580080128001217/gateway-of-india.jpg";
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
	@Ignore
	@Test
	public void testDownloadFileWithValidUrl() {
		String downloadFolder = "/tmp/content/" + System.currentTimeMillis() + "_temp/do_123";
		String driveUrl = "https://sunbirddevbbpublic.blob.core.windows.net/sunbird-content-staging/content/assets/do_2137327580080128001217/gateway-of-india.jpg";
		java.io.File appIconFile = HTTPUrlUtil.downloadFile(driveUrl,downloadFolder);
		assertTrue(appIconFile.exists());
		try {FileUtils.deleteDirectory(appIconFile.getParentFile().getParentFile());} catch(IOException io) {}
	}

}
