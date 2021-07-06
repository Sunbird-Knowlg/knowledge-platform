package org.sunbird.url.mgr.impl;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.url.mgr.IURLManager;
import org.sunbird.url.util.YouTubeUrlUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This Class Holds Implementation Of IURLManager For YouTube Url
 *
 * @see IURLManager
 */
public class YouTubeURLManagerImpl implements IURLManager {

	private static final List<String> validCriteria = Arrays.asList("license");

	@Override
	public Map<String, Object> validateURL(String url, String validationCriteria) {
		if (StringUtils.isNotBlank(validationCriteria) && validCriteria.contains(validationCriteria)) {
			String license = YouTubeUrlUtil.getLicense(url);
			boolean isValidLicense = YouTubeUrlUtil.isValidLicense(license);
			Map<String, Object> result = new HashMap<>();
			if (StringUtils.isNotBlank(license) && BooleanUtils.isTrue(isValidLicense)) {
				result.put("value", license);
				result.put("valid", isValidLicense);
			}
			return result;
		} else throw new ClientException(ResponseCode.CLIENT_ERROR.name(), "Please Provide Valid Criteria For Validation. Supported Criteria : " + validCriteria);
	}

	@Override
	public Map<String, Object> readMetadata(String url) {
		return null;
	}
}
