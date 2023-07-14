package org.sunbird.url.util;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.Slug;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.telemetry.logger.TelemetryManager;
import org.sunbird.url.common.URLErrorCodes;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

/**
 * This Class Provides Utility Methods Which Process Given General Http Based Url
 */
public class HTTPUrlUtil {

	private HTTPUrlUtil(){}
	private static final int BUFFER_SIZE = 4096;

	/**
	 * This Method Returns Size And Type For Given Url
	 *
	 * @param fileUrl
	 * @return Map<String, Object>
	 */
	public static Map<String, Object> getMetadata(String fileUrl) {
		URLConnection conn = null;
		Map<String, Object> metadata = new HashMap<>();
		try {
			URL url = new URL(fileUrl);
			conn = url.openConnection();
			if (conn instanceof HttpURLConnection) {
				((HttpURLConnection) conn).setRequestMethod("HEAD");
			}
			conn.getInputStream();
			metadata.put("size", conn.getContentLengthLong());
			metadata.put("type", conn.getContentType());
			return metadata;
		} catch (UnknownHostException e) {
			throw new ClientException(URLErrorCodes.ERR_INVALID_URL.name(), "Please Provide Valid Url.");
		} catch (FileNotFoundException e) {
			throw new ClientException(URLErrorCodes.ERR_FILE_NOT_FOUND.name(), "File Not Found.");
		} catch (ConnectException e) {
			throw new ClientException(URLErrorCodes.ERR_INVALID_URL.name(), "Please Provide Valid Url.");
		} catch (Exception e) {
			throw new ServerException(URLErrorCodes.SYSTEM_ERROR.name(),
					"Something Went Wrong While Processing Your Request. Please Try Again After Sometime!");
		} finally {
			if (conn instanceof HttpURLConnection) {
				((HttpURLConnection) conn).disconnect();
			}
		}
	}

	/**
	 * Downloads a file from a URL
	 *
	 * @param fileURL
	 *            HTTP URL of the file to be downloaded
	 * @param saveDir
	 *            path of the directory to save the file
	 */
	public static File downloadFile(String fileURL, String saveDir) {
		HttpURLConnection httpConn = null;
		InputStream inputStream = null;
		FileOutputStream outputStream = null;
		File file = null;
		try {
			URL url = new URL(fileURL);
			httpConn = (HttpURLConnection) url.openConnection();
			int responseCode = httpConn.getResponseCode();
			TelemetryManager.log("Response Code: " + responseCode);

			// always check HTTP response code first
			if (responseCode == HttpURLConnection.HTTP_OK) {
				TelemetryManager.log("Response is OK.");

				String fileName = "";
				String disposition = httpConn.getHeaderField("Content-Disposition");
				httpConn.getContentType();
				httpConn.getContentLength();
				TelemetryManager.log("Content Disposition: " + disposition);

				if (StringUtils.isNotBlank(disposition)) {
					int index = disposition.indexOf("filename=");
					if (index > 0) {
						fileName = disposition.substring(index + 10, disposition.indexOf("\"", index+10));
					}
				}
				if (StringUtils.isBlank(fileName)) {
					fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1, fileURL.length());
				}

				// opens input stream from the HTTP connection
				inputStream = httpConn.getInputStream();
				File saveFile = new File(saveDir);
				if (!saveFile.exists()) {
					saveFile.mkdirs();
				}
				String saveFilePath = saveDir + File.separator + fileName;
				TelemetryManager.log("FileUrl :" + fileURL +" , Save File Path: " + saveFilePath);

				// opens an output stream to save into file
				outputStream = new FileOutputStream(saveFilePath);

				int bytesRead = -1;
				byte[] buffer = new byte[BUFFER_SIZE];
				while ((bytesRead = inputStream.read(buffer)) != -1)
					outputStream.write(buffer, 0, bytesRead);
				outputStream.close();
				inputStream.close();
				file = new File(saveFilePath);
				file = Slug.createSlugFile(file);
				TelemetryManager.log("Sluggified File Name: " + file.getAbsolutePath());
			} else {
				TelemetryManager.log("No file to download. Server replied HTTP code: " + responseCode);
			}
		} catch (Exception e) {
			TelemetryManager.error("Error! While Downloading File:"+ e.getMessage(), e);
		} finally {
			try {
				if (null != httpConn)
					httpConn.disconnect();
				if (null != inputStream)
					inputStream.close();
				if (null != outputStream)
					outputStream.close();
			} catch (IOException e) {
				TelemetryManager.error("Error! While Closing the Connection: "+ e.getMessage(), e);
			}
		}

		return file;
	}

}
