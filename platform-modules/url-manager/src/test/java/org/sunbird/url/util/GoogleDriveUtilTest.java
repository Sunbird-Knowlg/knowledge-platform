package org.sunbird.url.util;

import com.google.api.services.drive.model.File;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sunbird.common.exception.ClientException;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * This Class Holds Unit Test Cases For GoogleDriveUtil
 *
 * @see GoogleDriveUrlUtil
 */
public class GoogleDriveUtilTest {

	@Rule
	public final ExpectedException exception = ExpectedException.none();

	@Test
	public void testGetDriveUrlWithValidUrl() {
		String driveUrl = "https://drive.google.com/file/d/1az_AFAoRwu9cXlr1R5pO9fNhHexzJKXo/view?usp=sharing";
		String output = GoogleDriveUrlUtil.getDriveFileId(driveUrl);
		assertEquals("1az_AFAoRwu9cXlr1R5pO9fNhHexzJKXo", output);
	}

	@Test
	public void testGetDriveUrlWithInvalidUrl() {
		String driveUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy1.pdf";
		String output = GoogleDriveUrlUtil.getDriveFileId(driveUrl);
		assertEquals("", output);
	}

	@Test
	public void testGetDriveFileWithValidUrl() {
		String driveUrl = "https://drive.google.com/file/d/1az_AFAoRwu9cXlr1R5pO9fNhHexzJKXo/view?usp=sharing";
		String fileId = GoogleDriveUrlUtil.getDriveFileId(driveUrl);
		File driveFile = GoogleDriveUrlUtil.getDriveFile(fileId);
		assertNotNull(driveFile);
		assertTrue(driveFile instanceof com.google.api.services.drive.model.File);
	}

	@Test
	public void testGetDriveFileWithPrivateUrl() {
		String driveUrl = "https://drive.google.com/file/d/1ZQroCCB-qhXJldNjseAX-dFZ8k7t5QjZ/view?usp=sharing";
		String fileId = GoogleDriveUrlUtil.getDriveFileId(driveUrl);
		assertEquals("1ZQroCCB-qhXJldNjseAX-dFZ8k7t5QjZ", fileId);
		File driveFile = GoogleDriveUrlUtil.getDriveFile(fileId);
		assertEquals(null, driveFile);
	}

	@Test
	public void testGetDriveFileWithInvalidUrl() {
		String driveUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy1.pdf";
		String fileId = GoogleDriveUrlUtil.getDriveFileId(driveUrl);
		File driveFile = GoogleDriveUrlUtil.getDriveFile(driveUrl);
		assertEquals("", fileId);
		assertEquals(null, driveFile);
	}

	@Test
	public void testGetMetadataWithValidUrl() {
		String driveUrl = "https://drive.google.com/file/d/1az_AFAoRwu9cXlr1R5pO9fNhHexzJKXo/view?usp=sharing";
		Map<String, Object> result = GoogleDriveUrlUtil.getMetadata(driveUrl);
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
	public void testGetMetadataWithInvalidUrl() {
		exception.expect(ClientException.class);
		exception.expectMessage("Please Provide Valid Google Drive URL!");
		String driveUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy1.pdf";
		Map<String, Object> result = GoogleDriveUrlUtil.getMetadata(driveUrl);
	}

	@Test
	public void testGetSizeWithValidUrl() {
		String driveUrl = "https://drive.google.com/file/d/1az_AFAoRwu9cXlr1R5pO9fNhHexzJKXo/view?usp=sharing";
		Long result = GoogleDriveUrlUtil.getSize(driveUrl);
		assertTrue(result > 0);
	}

	@Test
	public void testGetSizeWithInvalidUrl() {
		exception.expect(ClientException.class);
		exception.expectMessage("Please Provide Valid Google Drive URL!");
		String driveUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy1.pdf";
		Long result = GoogleDriveUrlUtil.getSize(driveUrl);
	}
}
