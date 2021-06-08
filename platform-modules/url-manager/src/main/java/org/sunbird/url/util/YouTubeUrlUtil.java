package org.sunbird.url.util;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.telemetry.logger.TelemetryManager;
import org.sunbird.url.common.URLErrorCodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This Class Provides Utility Methods Which Process Given YouTube Url
 */
public class YouTubeUrlUtil {

	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static final JsonFactory JSON_FACTORY = new JacksonFactory();

	private static final String APP_NAME = Platform.config.hasPath("learning.content.youtube.application.name")
			? Platform.config.getString("learning.content.youtube.application.name") : "fetch-youtube-license";

	private static final String API_KEY = Platform.config.getString("learning_content_youtube_apikey");

	private static final List<String> VALID_LICENSES = Platform.config.hasPath("learning.valid_license") ?
			Platform.config.getStringList("learning.valid_license") : Arrays.asList("creativeCommon");

	private static final List<String> videoIdRegex = Platform.config.hasPath("youtube.license.regex.pattern") ?
			Platform.config.getStringList("youtube.license.regex.pattern") :
			Arrays.asList("\\?vi?=([^&]*)", "watch\\?.*v=([^&]*)", "(?:embed|vi?)/([^/?]*)", "^([A-Za-z0-9\\-\\_]*)");

	private static final String ERR_MSG = "Please Provide Valid YouTube URL!";
	private static final String SERVICE_ERROR = "Unable to Check License. Please Try Again After Sometime!";
	private static final List<String> errorCodes = Arrays.asList("dailyLimitExceeded402", "limitExceeded",
			"dailyLimitExceeded", "quotaExceeded", "userRateLimitExceeded", "quotaExceeded402", "keyExpired",
			"keyInvalid");

	private static boolean limitExceeded = false;
	private static YouTube youtube = null;

	static {
		youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpRequestInitializer() {
			public void initialize(HttpRequest request) throws IOException {
			}
		}).setApplicationName(APP_NAME).build();
	}

	private YouTubeUrlUtil(){}

	/**
	 * This Method will fetch license for given YouTube Video URL.
	 *
	 * @param videoUrl
	 * @return licenceType
	 */
	public static String getLicense(String videoUrl) {
		Video video = null;
		String videoId = getIdFromUrl(videoUrl);
		if (StringUtils.isBlank(videoId))
			throw new ClientException(URLErrorCodes.ERR_INVALID_URL.name(), ERR_MSG);

		String license = "";
		List<Video> videoList = getVideoList(videoId, "status");
		if (null != videoList && !videoList.isEmpty()) {
			video = videoList.get(0);
		}

		if (null != video) {
			license = video.getStatus().getLicense();
		}

		if (StringUtils.isBlank(license) && !limitExceeded)
			throw new ClientException(URLErrorCodes.ERR_YOUTUBE_LICENSE_VALIDATION.name(), ERR_MSG);

		return license;
	}

	/**
	 * This Method Checks If The Given License Is Valid Or Not
	 *
	 * @param license
	 * @return Boolean
	 */
	public static Boolean isValidLicense(String license) {
		return VALID_LICENSES.contains(license);
	}

	/**
	 * This Method Returns List Of YouTube Videos For Given Video Id And Filter
	 *
	 * @param videoId
	 * @param params
	 * @return List<Video>
	 */
	public static List<Video> getVideoList(String videoId, String params) {
		try {
			YouTube.Videos.List videosListByIdRequest = youtube.videos().list(params);
			videosListByIdRequest.setKey(API_KEY);
			videosListByIdRequest.setId(videoId);
			VideoListResponse response = videosListByIdRequest.execute();
			return response.getItems();
		} catch (GoogleJsonResponseException ex) {
			Map<String, Object> error = ex.getDetails().getErrors().get(0);
			String reason = (String) error.get("reason");
			if (errorCodes.contains(reason)) {
				limitExceeded = true;
				TelemetryManager.log("Youtube API Limit Exceeded. Reason is: " + reason + " | Error Details : " + ex);
			}
		} catch (Exception e) {
			TelemetryManager.error("Error Occurred While Calling Youtube API. Error Details : ", e);
			throw new ServerException(URLErrorCodes.SYSTEM_ERROR.name(),
					"Something Went Wrong While Processing Youtube Video. Please Try Again After Sometime!");
		}
		if (limitExceeded)
			throw new ClientException(URLErrorCodes.ERR_YOUTUBE_SERVICE.name(), SERVICE_ERROR);
		return new ArrayList<>();
	}

	/**
	 * This Method Returns Requested Video Metadata For Given Url And Filter
	 *
	 * @param videoUrl
	 * @param apiParams
	 * @param metadata
	 * @return Map<String, Object>
	 */
	public static Map<String, Object> getVideoInfo(String videoUrl, String apiParams, String... metadata) {
		Video video = null;
		Map<String, Object> result = new HashMap<>();
		String videoId = getIdFromUrl(videoUrl);
		List<Video> videoList = getVideoList(videoId, apiParams);
		if (null != videoList && !videoList.isEmpty()) {
			video = videoList.get(0);
		}
		if (null != video) {
			for (String str : metadata) {
				String res = getResult(str, video);
				if (StringUtils.isNotBlank(res))
					result.put(str, res);
			}
		}
		return result;
	}

	private static String getResult(String str, Video video) {
		String res = null;
		switch (str.toLowerCase()) {
			case "license": {
				res = video.getStatus().getLicense();
				break;
			}
			case "thumbnail": {
				res = video.getSnippet().getThumbnails().getMedium().getUrl();
				break;
			}
			case "duration": {
				res = computeVideoDuration(video.getContentDetails().getDuration());
				break;
			}
			default:
				break;
		}
		return res;
	}

	/**
	 * This Method Computes Duration for Youtube Video
	 *
	 * @param videoDuration
	 * @return String
	 */
	private static String computeVideoDuration(String videoDuration) {
		String youtubeDuration = videoDuration.replaceAll("PT|S", "").replaceAll("H|M", ":");
		String[] values = youtubeDuration.split(":");
		if (null != values) {
			if (values.length == 1) {
				return values[0];
			}
			if (values.length == 2) {
				return String.valueOf((Integer.parseInt(values[0]) * 60) + (Integer.parseInt(values[1]) * 1));
			}
			if (values.length == 3) {
				return String.valueOf((Integer.parseInt(values[0]) * 3600) + (Integer.parseInt(values[1]) * 60) + (Integer.parseInt(values[2]) * 1));
			}
		}
		return "";
	}

	/**
	 * This Method Returns Video Id From Given Url
	 *
	 * @param url
	 * @return String
	 */
	private static String getIdFromUrl(String url) {
		String videoLink = getVideoLink(url);
		return videoIdRegex.stream().map(Pattern::compile).map(compiledPattern -> compiledPattern.matcher(videoLink)).filter(Matcher::find).findFirst().map(matcher -> matcher.group(1)).orElse(null);
	}

	/**
	 * This Method Checks And Return Valid YouTube Url
	 *
	 * @param url
	 * @return String
	 */
	private static String getVideoLink(String url) {
		final String youTubeUrlRegEx = "^(https?)?(://)?(www.)?(m.)?((youtube.com)|(youtu.be))/";
		Pattern compiledPattern = Pattern.compile(youTubeUrlRegEx);
		Matcher matcher = compiledPattern.matcher(url);
		if (matcher.find()) {
			return url.replace(matcher.group(), "");
		}
		return url;
	}
}
