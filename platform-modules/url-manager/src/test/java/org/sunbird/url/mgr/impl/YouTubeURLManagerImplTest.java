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
 * Test Class Holds Unit Test Cases For YouTubeUrlManagerImpl
 *
 * @see YouTubeURLManagerImpl
 */
public class YouTubeURLManagerImplTest {

	private static IURLManager youtubeMgr = URLFactoryManager.getUrlManager("youtube");

	@Rule
	public final ExpectedException exception = ExpectedException.none();

	@Test
	public void testValidateUrlWithValidUrlValidCriteria() {
		String videoUrl = "https://www.youtube.com/watch?v=owr198WQpM8";
		Map<String, Object> result = youtubeMgr.validateURL(videoUrl, "license");
		assertTrue(MapUtils.isNotEmpty(result));
		assertTrue(result.size() == 2);
		assertTrue(result.containsKey("value"));
		assertTrue(result.containsKey("valid"));
		assertTrue((Boolean) result.get("valid"));
		assertEquals("creativeCommon", (String) result.get("value"));
	}

	@Test
	public void testReadMetadata() {
		String videoUrl = "https://www.youtube.com/watch?v=_UR-l3QI2nE";
		Map<String, Object> result = youtubeMgr.readMetadata(videoUrl);
		assertTrue(MapUtils.isEmpty(result));
	}

	@Test
	public void testValidateUrlWithInvalidUrlValidCriteria() {
		exception.expect(ClientException.class);
		exception.expectMessage("Please Provide Valid YouTube URL!");
		String videoUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy1.pdf";
		Map<String, Object> result = youtubeMgr.validateURL(videoUrl, "license");
	}

	@Test
	public void testValidateUrlWithInvalidCriteria() {
		exception.expect(ClientException.class);
		exception.expectMessage("Please Provide Valid Criteria For Validation. Supported Criteria : [license]");
		String videoUrl = "https://www.youtube.com/watch?v=owr198WQpM8";
		Map<String, Object> result = youtubeMgr.validateURL(videoUrl, "name");

	}

}
