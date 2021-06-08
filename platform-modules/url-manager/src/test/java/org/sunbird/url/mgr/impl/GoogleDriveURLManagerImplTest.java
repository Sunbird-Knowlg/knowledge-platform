package org.sunbird.url.mgr.impl;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sunbird.common.exception.ClientException;
import org.sunbird.url.mgr.IURLManager;
import org.sunbird.url.util.GoogleDriveUrlUtil;

import java.util.Map;

import static org.junit.Assert.assertTrue;

/**
 * Test Class Holds Unit Test Cases For GoogleDriveUrlManagerImpl
 *
 * @see GoogleDriveURLManagerImpl
 */
public class GoogleDriveURLManagerImplTest {

	private static IURLManager gdMgr = URLFactoryManager.getUrlManager("googledrive");

	@Rule
	public final ExpectedException exception = ExpectedException.none();

	@Test
	public void testValidateUrlWithValidUrlValidCriteria() {
		String driveUrl = "https://drive.google.com/file/d/1az_AFAoRwu9cXlr1R5pO9fNhHexzJKXo/view?usp=sharing";
		Map<String, Object> result = gdMgr.validateURL(driveUrl, "size");
		assertTrue(MapUtils.isNotEmpty(result));
		assertTrue(result.size() == 2);
		assertTrue(result.containsKey("value"));
		assertTrue(result.containsKey("valid"));
		assertTrue((Boolean) result.get("valid"));
		assertTrue((Long) result.get("value") > 0);
	}

	@Test
	public void testValidateUrlWithValidUrlInvalidCriteria() {
		exception.expect(ClientException.class);
		exception.expectMessage("Please Provide Valid Criteria For Validation. Supported Criteria : [size]");
		String driveUrl = "https://drive.google.com/file/d/1az_AFAoRwu9cXlr1R5pO9fNhHexzJKXo/view?usp=sharing";
		Map<String, Object> result = gdMgr.validateURL(driveUrl, "name");

	}

	@Test
	public void testValidateUrlWithInvalidUrlValidCriteria() {
		exception.expect(ClientException.class);
		exception.expectMessage("Please Provide Valid Google Drive URL!");
		String driveUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy1.pdf";
		Map<String, Object> result = gdMgr.validateURL(driveUrl, "size");
	}

	@Test
	public void testReadMetadataWithValidUrl() {
		String driveUrl = "https://drive.google.com/file/d/1az_AFAoRwu9cXlr1R5pO9fNhHexzJKXo/view?usp=sharing";
		Map<String, Object> result = gdMgr.readMetadata(driveUrl);
		assertTrue(MapUtils.isNotEmpty(result));
		assertTrue(result.size() == 3);
		assertTrue(result.containsKey("id"));
		assertTrue(result.containsKey("name"));
		assertTrue(result.containsKey("size"));
		assertTrue(StringUtils.isNotBlank((String) result.get("id")));
		assertTrue(StringUtils.isNotBlank((String) result.get("name")));
		assertTrue((Long) result.get("size") > 0);
	}

	@Test
	public void testReadMetadataWithInvalidUrl() {
		exception.expect(ClientException.class);
		exception.expectMessage("Please Provide Valid Google Drive URL!");
		String driveUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy1.pdf";
		Map<String, Object> result = gdMgr.readMetadata(driveUrl);
	}

}
