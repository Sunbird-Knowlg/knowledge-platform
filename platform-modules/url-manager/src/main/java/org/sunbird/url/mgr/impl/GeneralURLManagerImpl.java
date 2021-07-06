package org.sunbird.url.mgr.impl;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.url.mgr.IURLManager;
import org.sunbird.url.util.HTTPUrlUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This Class Holds Implementation Of IURLManager For General Url
 *
 * @see IURLManager
 */
public class GeneralURLManagerImpl implements IURLManager {

	private static final List<String> validCriteria = Arrays.asList("size");
	private static long sizeLimit = 50000000;

	@Override
	public Map<String, Object> validateURL(String url, String validationCriteria) {
		if (StringUtils.isNotBlank(validationCriteria) && validCriteria.contains(validationCriteria)) {
			Map<String, Object> metadata = HTTPUrlUtil.getMetadata(url);
			Long size = metadata.get("size") == null ? 0 : (Long) metadata.get("size");
			Map<String, Object> result = new HashMap<>();
			result.put("value", size);
			result.put("valid", size <= sizeLimit);
			return result;
		} else throw new ClientException(ResponseCode.CLIENT_ERROR.name(), "Please Provide Valid Criteria For Validation. Supported Criteria : " + validCriteria);
	}

	@Override
	public Map<String, Object> readMetadata(String url) {
		return HTTPUrlUtil.getMetadata(url);
	}

}
