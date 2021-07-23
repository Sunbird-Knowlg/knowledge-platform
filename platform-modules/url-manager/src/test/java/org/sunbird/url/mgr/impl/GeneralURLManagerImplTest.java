package org.sunbird.url.mgr.impl;

import org.apache.commons.collections4.MapUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sunbird.common.exception.ClientException;
import org.sunbird.url.mgr.IURLManager;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test Class Holds Unit Test Cases For GeneralUrlManagerImpl
 *
 * @see GeneralURLManagerImpl
 */
public class GeneralURLManagerImplTest {

	private static IURLManager genMgr = URLFactoryManager.getUrlManager("general");

	@Rule
	public final ExpectedException exception = ExpectedException.none();

	@Test
	public void testValidateURLWithValidCriteria() {
		String url = "https://ekstep-public-prod.s3-ap-south-1.amazonaws.com/content/do_31225266105597952011502/artifact/2246d6bdafaeae551d93e245fa484cc9_1495686214370.jpeg";
		Map<String, Object> result = genMgr.validateURL(url, "size");
		assertTrue(result.size() == 2);
		assertTrue(result.containsKey("value"));
		assertTrue(result.containsKey("valid"));
		assertTrue((Boolean) result.get("valid"));
		assertTrue((Long) result.get("value") > 0);
	}

	@Test
	public void testValidateURLWithInvalidCriteria() {
		exception.expect(ClientException.class);
		exception.expectMessage("Please Provide Valid Criteria For Validation. Supported Criteria : [size]");
		String url = "https://ekstep-public-prod.s3-ap-south-1.amazonaws.com/content/do_31225266105597952011502/artifact/2246d6bdafaeae551d93e245fa484cc9_1495686214370.jpeg";
		Map<String, Object> result = genMgr.validateURL(url, "type");
	}

	@Test
	public void testValidateURLWithInvalidUrl() {
		exception.expect(ClientException.class);
		exception.expectMessage("File Not Found.");
		String url = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy1.pdf";
		Map<String, Object> result = genMgr.validateURL(url, "size");
	}

	@Test
	public void testReadMetadataWithValidUrl() {
		String url = "https://ekstep-public-prod.s3-ap-south-1.amazonaws.com/content/do_31225266105597952011502/artifact/2246d6bdafaeae551d93e245fa484cc9_1495686214370.jpeg";
		Map<String, Object> result = genMgr.readMetadata(url);
		assertTrue(MapUtils.isNotEmpty(result));
		assertTrue(result.size() == 2);
		assertTrue(result.containsKey("size"));
		assertTrue(result.containsKey("type"));
		assertEquals("image/jpeg", (String) result.get("type"));
		assertTrue((Long) result.get("size") > 0);
	}

	@Test
	public void testReadMetadataWithInvalidUrl() {
		exception.expect(ClientException.class);
		exception.expectMessage("File Not Found.");
		String url = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy1.pdf";
		Map<String, Object> result = genMgr.readMetadata(url);
	}
}
