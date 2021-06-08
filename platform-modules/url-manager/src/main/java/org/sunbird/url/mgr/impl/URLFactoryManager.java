package org.sunbird.url.mgr.impl;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.url.mgr.IURLManager;

/**
 * This Class Provides Factory Implementation for Url Managers.
 */
public class URLFactoryManager {

	private static final IURLManager youtubeUrlManager = new YouTubeURLManagerImpl();
	private static final IURLManager googleDriveUrlManager = new GoogleDriveURLManagerImpl();
	private static final IURLManager generalUrlManager = new GeneralURLManagerImpl();

	private URLFactoryManager(){}

	/**
	 * This Method Returns Instance Of Url Manager Based On Given Provider
	 *
	 * @param provider
	 * @return IURLManager
	 */
	public static IURLManager getUrlManager(String provider) {
		switch (StringUtils.lowerCase(provider)) {
			case "youtube":
				return youtubeUrlManager;
			case "googledrive":
				return googleDriveUrlManager;
			case "general":
				return generalUrlManager;
			default:
				throw new ClientException(ResponseCode.CLIENT_ERROR.name(), "Please Provide Valid Provider");
		}
	}
}
