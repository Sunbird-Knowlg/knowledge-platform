package org.sunbird.url.util;

import com.google.api.services.youtube.model.Video;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sunbird.common.exception.ClientException;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This Class Holds Unit Test Cases For YouTubeUrlUtil
 *
 * @see YouTubeUrlUtil
 */
public class YouTubeUrlUtilTest {

	@Rule
	public final ExpectedException exception = ExpectedException.none();

	@Test
	public void testGetLicenseWithValidUrlPattern1() {
		String videoUrl = "https://www.youtube.com/watch?v=owr198WQpM8";
		String result = YouTubeUrlUtil.getLicense(videoUrl);
		assertEquals("creativeCommon", result);
	}

	@Test
	public void testGetLicenseWithValidUrlWithPattern2() {
		String videoUrl = "http://youtu.be/-wtIMTCHWuI";
		String result = YouTubeUrlUtil.getLicense(videoUrl);
		assertEquals("youtube", result);
	}

	@Test
	public void testGetLicenseWithValidUrlPattern3() {
		String videoUrl = "http://www.youtube.com/v/-wtIMTCHWuI?version=3&autohide=1";
		String result = YouTubeUrlUtil.getLicense(videoUrl);
		assertEquals("youtube", result);
	}

	@Test
	public void testGetLicenseWithValidUrlPattern4() {
		String videoUrl = "https://www.youtube.com/embed/7IP0Ch1Va44";
		String result = YouTubeUrlUtil.getLicense(videoUrl);
		assertEquals("youtube", result);
	}

	// YouTubeUrlUtil doesn't support this url. Content Portal also don't have support for such url type.
	@Test
	public void testGetLicenseWithValidUrlPattern5() {
		exception.expect(ClientException.class);
		exception.expectMessage("Please Provide Valid YouTube URL!");
		String videoUrl = "http://www.youtube.com/attribution_link?a=JdfC0C9V6ZI&u=%2Fwatch%3Fv%3DEhxJLojIE_o%26feature%3Dshare";
		String result = YouTubeUrlUtil.getLicense(videoUrl);
	}

	@Test
	public void testGetLicenseExpectYoutubeLicense() {
		String videoUrl = "https://www.youtube.com/watch?v=_UR-l3QI2nE";
		String result = YouTubeUrlUtil.getLicense(videoUrl);
		assertEquals("youtube", result);
	}

	@Test
	public void testGetLicenseWithInvalidUrl() {
		exception.expect(ClientException.class);
		exception.expectMessage("Please Provide Valid YouTube URL!");
		String videoUrl = "https://goo.gl/bVBJNK";
		String result = YouTubeUrlUtil.getLicense(videoUrl);
	}

	@Test
	public void testIsValidLicenseWithValidLicense() {
		String license = "creativeCommon";
		Boolean isValid = YouTubeUrlUtil.isValidLicense(license);
		assertTrue(isValid);
	}

	@Test
	public void testIsValidLicenseWithInvalidLicense() {
		String license = "youtube-standard";
		Boolean isValid = YouTubeUrlUtil.isValidLicense(license);
		assertFalse(isValid);
	}

	@Test
	public void testGetVideoListWithValidVideoId(){
		String videoId = "owr198WQpM8";
		List<Video> videos = YouTubeUrlUtil.getVideoList(videoId,"status");
		assertTrue(CollectionUtils.isNotEmpty(videos));
	}

	@Test
	public void testGetVideoListWithInvalidVideoId(){
		String videoId = "sunbirdTest0001";
		List<Video> videos = YouTubeUrlUtil.getVideoList(videoId,"status");
		assertTrue(CollectionUtils.isEmpty(videos));
	}

	@Test
	public void tesGetVideoInfoExpectThumbnail() throws Exception {
		String videoUrl = "https://youtu.be/WM4ys_PnrUY";
		Map<String, Object> result = YouTubeUrlUtil.getVideoInfo(videoUrl, "snippet", "thumbnail");
		assertEquals("https://i.ytimg.com/vi/WM4ys_PnrUY/mqdefault.jpg", (String) result.get("thumbnail"));
	}

	@Test
	public void tesGetVideoInfoExpectDuration() throws Exception {
		String videoUrl = "https://youtu.be/WM4ys_PnrUY";
		Map<String, Object> result = YouTubeUrlUtil.getVideoInfo(videoUrl, "contentDetails", "duration");
		assertEquals("1918", (String) result.get("duration"));
	}

	@Test
	public void tesGetVideoInfoExpectMultipleData() throws Exception {
		String videoUrl = "https://youtu.be/WM4ys_PnrUY";
		Map<String, Object> result = YouTubeUrlUtil.getVideoInfo(videoUrl, "status,snippet,contentDetails", "license", "thumbnail", "duration");
		assertEquals("youtube", (String) result.get("license"));
		assertEquals("https://i.ytimg.com/vi/WM4ys_PnrUY/mqdefault.jpg", (String) result.get("thumbnail"));
		assertEquals("1918", (String) result.get("duration"));
	}


}
