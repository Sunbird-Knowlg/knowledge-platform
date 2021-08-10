package org.sunbird.url.util;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpResponseException;
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
import org.sunbird.common.Slug;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.telemetry.logger.TelemetryManager;
import org.sunbird.url.common.URLErrorCodes;

import java.io.FileOutputStream;
import java.io.OutputStream;
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
	public static final Integer INITIAL_BACKOFF_DELAY = Platform.config.hasPath("import.initial_backoff_delay") ? Platform.config.getInt("import.initial_backoff_delay") : 1200000;    // 20 min
	public static final Integer MAXIMUM_BACKOFF_DELAY = Platform.config.hasPath("import.maximum_backoff_delay") ? Platform.config.getInt("import.maximum_backoff_delay") : 3900000;    // 65 min
	public static final Integer INCREMENT_BACKOFF_DELAY = Platform.config.hasPath("import.increment_backoff_delay") ? Platform.config.getInt("import.increment_backoff_delay") : 300000; // 5 min
	public static Integer BACKOFF_DELAY = INITIAL_BACKOFF_DELAY;

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


	public static java.io.File downloadFile(String fileId, String saveDir) {
		try {
			Drive.Files.Get getFile = drive.files().get(fileId);
			getFile.setFields("id,name,size,owners,mimeType,properties,permissionIds,webContentLink");
			com.google.api.services.drive.model.File googleDriveFile = getFile.execute();
			TelemetryManager.log("GoogleDriveUtil :: downloadFile ::: Drive File Details:: " + googleDriveFile);
			String fileName = googleDriveFile.getName();
			java.io.File saveFile = new java.io.File(saveDir);
			if (!saveFile.exists()) {
				saveFile.mkdirs();
			}
			String saveFilePath = saveDir + java.io.File.separator + fileName;
			TelemetryManager.log("GoogleDriveUtil :: downloadFile :: File Id :" + fileId + " | Save File Path: " + saveFilePath);
			OutputStream outputStream = new FileOutputStream(saveFilePath);
			getFile.executeMediaAndDownloadTo(outputStream);
			outputStream.close();
			java.io.File file = new java.io.File(saveFilePath);
			file = Slug.createSlugFile(file);
			TelemetryManager.log("GoogleDriveUtil :: downloadFile :: File Downloaded Successfully. Sluggified File Name: " + file.getAbsolutePath());
			if (null != file && BACKOFF_DELAY != INITIAL_BACKOFF_DELAY)
				BACKOFF_DELAY = INITIAL_BACKOFF_DELAY;
			return file;
		} catch(GoogleJsonResponseException ge) {
			TelemetryManager.log("GoogleDriveUtil :: downloadFile :: GoogleJsonResponseException :: Error Occurred while downloading file having id "+fileId + " | Error is ::"+ge.getDetails().toString());
			throw new ServerException(URLErrorCodes.ERR_INVALID_UPLOAD_FILE_URL.name(), "Invalid Response Received From Google API for file Id : " + fileId + " | Error is : " + ge.getDetails().toString());
		} catch(HttpResponseException he) {
			TelemetryManager.log("GoogleDriveUtil :: downloadFile :: HttpResponseException :: Error Occurred while downloading file having id "+fileId + " | Error is ::"+he.getContent());
			he.printStackTrace();
			if(he.getStatusCode() == 403) {
				if (BACKOFF_DELAY <= MAXIMUM_BACKOFF_DELAY)
					delay(BACKOFF_DELAY);
				if (BACKOFF_DELAY == 2400000)
					BACKOFF_DELAY += 1500000;
				else
					BACKOFF_DELAY = BACKOFF_DELAY * INCREMENT_BACKOFF_DELAY;
			} else  throw new ServerException(URLErrorCodes.ERR_INVALID_UPLOAD_FILE_URL.name(), "Invalid Response Received From Google API for file Id : " + fileId + " | Error is : " + he.getContent());
		} catch (Exception e) {
			TelemetryManager.log("GoogleDriveUtil :: downloadFile :: Exception :: Error Occurred While Downloading Google Drive File having Id " + fileId + " : " + e.getMessage());
			e.printStackTrace();
			throw new ServerException(URLErrorCodes.ERR_INVALID_UPLOAD_FILE_URL.name(), "Invalid Response Received From Google API for file Id : " + fileId + " | Error is : " + e.getMessage());
		}
		return null;
	}

	public static void delay(int time) {
		TelemetryManager.log("delay is called with : " + time);
		try {
			Thread.sleep(time);
		} catch (Exception e) {

		}
	}
}
