package org.sunbird.url.util;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.sunbird.common.Platform;
import org.sunbird.common.Slug;
import org.sunbird.common.exception.ServerException;
import org.sunbird.telemetry.logger.TelemetryManager;
import org.sunbird.url.common.URLErrorCodes;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

public class GoogleDriveUtil {

	private static final JsonFactory JSON_FACTORY = new JacksonFactory();

	private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE_READONLY);
	private static final String APP_NAME = Platform.config.hasPath("import.gdrive.application_name") ? Platform.config.getString("import.gdrive.application_name") : "drive-download-sunbird";
	private static final String SERVICE_ACC_CRED = Platform.config.getString("import.g_service_acct_cred");
	public static final Integer INITIAL_BACKOFF_DELAY = Platform.config.hasPath("import.initial_backoff_delay") ? Platform.config.getInt("import.initial_backoff_delay") : 1200000;    // 20 min
	public static final Integer MAXIMUM_BACKOFF_DELAY = Platform.config.hasPath("import.maximum_backoff_delay") ? Platform.config.getInt("import.maximum_backoff_delay") : 3900000;    // 65 min
	public static final Integer INCREMENT_BACKOFF_DELAY = Platform.config.hasPath("import.increment_backoff_delay") ? Platform.config.getInt("import.increment_backoff_delay") : 300000; // 5 min
	public static Integer BACKOFF_DELAY = INITIAL_BACKOFF_DELAY;
	private static Drive drive = null;

	static {
		try {
			HttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials()).setApplicationName(APP_NAME).build();
		} catch (Exception e) {
			TelemetryManager.log("Error occurred while creating google drive client ::: " + e.getMessage());
			e.printStackTrace();
			throw new ServerException(URLErrorCodes.SYSTEM_ERROR.name(), "Error occurred while creating google drive client ::: "+ e.getMessage());
		}
	}

	private static Credential getCredentials() throws Exception {
		InputStream credentialsStream = new ByteArrayInputStream(SERVICE_ACC_CRED.getBytes(Charset.forName("UTF-8")));
		GoogleCredential credential = GoogleCredential.fromStream(credentialsStream).createScoped(SCOPES);
		return credential;
	}

	public static File downloadFile(String fileId, String saveDir) throws Exception {
		try {
			Drive.Files.Get getFile = drive.files().get(fileId);
			getFile.setFields("id,name,size,owners,mimeType,properties,permissionIds,webContentLink");
			com.google.api.services.drive.model.File googleDriveFile = getFile.execute();
			TelemetryManager.log("GoogleDriveUtil :: downloadFile ::: Drive File Details:: " + googleDriveFile);
			String fileName = googleDriveFile.getName();
			File saveFile = new File(saveDir);
			if (!saveFile.exists()) {
				saveFile.mkdirs();
			}
			String saveFilePath = saveDir + File.separator + fileName;
			TelemetryManager.log("GoogleDriveUtil :: downloadFile :: File Id :" + fileId + " | Save File Path: " + saveFilePath);
			OutputStream outputStream = new FileOutputStream(saveFilePath);
			getFile.executeMediaAndDownloadTo(outputStream);
			outputStream.close();
			File file = new File(saveFilePath);
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
			if(e instanceof ServerException)
				throw e;
			else throw new ServerException(URLErrorCodes.ERR_INVALID_UPLOAD_FILE_URL.name(), "Invalid Response Received From Google API for file Id : " + fileId + " | Error is : " + e.getMessage());
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
