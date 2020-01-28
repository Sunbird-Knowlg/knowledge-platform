package org.sunbird.url.util;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files.Get;
import com.google.api.services.drive.model.File;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.telemetry.logger.TelemetryManager;
import org.sunbird.url.common.URLErrorCodes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This Class Provides Utility Methods Which Process Given Google Drive File Url
 */
public class GoogleDriveUrlUtil {

	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static final JsonFactory JSON_FACTORY = new JacksonFactory();

	private static final String GOOGLE_DRIVE_URL_REGEX = "[-\\w]{25,}";
	private static final String DRIVE_FIELDS = "id, name, size";
	private static final String APP_NAME = Platform.config.hasPath("learning.content.drive.application.name")
			? Platform.config.getString("learning.content.drive.application.name") : "google-drive-url-validation";
	private static final String API_KEY = Platform.config.getString("learning_content_drive_apiKey");

	private static final String ERR_MSG = "Please Provide Valid Google Drive URL!";
	private static final String SERVICE_ERROR = "Unable to Connect To Google Service. Please Try Again After Sometime!";
	private static final List<String> ERROR_CODES = Arrays.asList("dailyLimitExceeded402", "limitExceeded",
			"dailyLimitExceeded", "quotaExceeded", "userRateLimitExceeded", "quotaExceeded402", "keyExpired",
			"keyInvalid");

	private static boolean limitExceeded = false;
	private static Drive drive = null;

	static {
		drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, null).setApplicationName(APP_NAME).build();
	}

	private GoogleDriveUrlUtil(){}

	/**
	 * This Method Returns Metadata For Given Google Drive File Url
	 *
	 * @param driveUrl
	 * @return Map<String, Object>
	 */
	public static Map<String, Object> getMetadata(String driveUrl) {
		String fileId = getDriveFileId(driveUrl);
		if (StringUtils.isBlank(fileId))
			throw new ClientException(URLErrorCodes.ERR_INVALID_URL.name(), ERR_MSG);
		Map<String, Object> result = new HashMap<>();
		File driveFile = getDriveFile(fileId);
		if (null != driveFile) {
			result.put("id", driveFile.get("id"));
			result.put("name", driveFile.get("name"));
			result.put("size", driveFile.get("size"));
		}
		if (MapUtils.isEmpty(result) && !limitExceeded)
			throw new ClientException(URLErrorCodes.ERR_GOOGLE_DRIVE_GET_METADATA.name(), ERR_MSG);

		return result;
	}

	/**
	 * This Method Returns File Size Of Given Google Drive Url
	 *
	 * @param driveUrl
	 * @return Long
	 */
	public static Long getSize(String driveUrl) {
		String fileId = getDriveFileId(driveUrl);
		if (StringUtils.isBlank(fileId))
			throw new ClientException(URLErrorCodes.ERR_INVALID_URL.name(), ERR_MSG);
		File driveFile = getDriveFile(fileId);
		Long size = Long.valueOf(0);
		if (null != driveFile)
			size = driveFile.get("size") == null ? 0 : (Long) driveFile.get("size");

		if (size == 0 && !limitExceeded)
			throw new ClientException(URLErrorCodes.ERR_GOOGLE_DRIVE_SIZE_VALIDATION.name(), ERR_MSG);

		return size;
	}

	/**
	 * This Method Extract And Returns Google Drive File Identifier From Url
	 *
	 * @param url
	 * @return String
	 */
	public static String getDriveFileId(String url) {
		Pattern compiledPattern = Pattern.compile(GOOGLE_DRIVE_URL_REGEX);
		Matcher matcher = compiledPattern.matcher(url);
		if (matcher.find())
			return matcher.group();
		return "";
	}

	/**
	 * This Method Returns Google Drive File Object For Given Drive Url
	 *
	 * @param fileId
	 * @return File
	 */
	public static File getDriveFile(String fileId) {
		File googleDriveFile = null;
		try {
			Get getFile = drive.files().get(fileId);
			getFile.setKey(API_KEY);
			getFile.setFields(DRIVE_FIELDS);
			googleDriveFile = getFile.execute();
		} catch (GoogleJsonResponseException ex) {
			Map<String, Object> error = ex.getDetails().getErrors().get(0);
			String reason = (String) error.get("reason");
			if (ERROR_CODES.contains(reason)) {
				limitExceeded = true;
				TelemetryManager.log("Google Drive API Limit Exceeded. Reason is: " + reason + " | Error Details : " + ex);
			}
		} catch (Exception e) {
			throw new ServerException(URLErrorCodes.SYSTEM_ERROR.name(),
					"Something Went Wrong While Processing Your Request. Please Try Again After Sometime!");
		}
		if (limitExceeded)
			throw new ServerException(URLErrorCodes.ERR_GOOGLE_SERVICE.name(), SERVICE_ERROR);
		return googleDriveFile;
	}
}
