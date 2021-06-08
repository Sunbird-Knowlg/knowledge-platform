package org.sunbird.url.util;

import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.url.common.URLErrorCodes;

import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * This Class Provides Utility Methods Which Process Given General Http Based Url
 */
public class HTTPUrlUtil {

	private HTTPUrlUtil(){}

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
		} catch (Exception e) {
			throw new ServerException(URLErrorCodes.SYSTEM_ERROR.name(),
					"Something Went Wrong While Processing Your Request. Please Try Again After Sometime!");
		} finally {
			if (conn instanceof HttpURLConnection) {
				((HttpURLConnection) conn).disconnect();
			}
		}
	}


}
