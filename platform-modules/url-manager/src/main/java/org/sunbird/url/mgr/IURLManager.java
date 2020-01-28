package org.sunbird.url.mgr;

import java.util.Map;

/**
 * Contract for URL Utilities
 */
public interface IURLManager {

	/**
	 * This Method Validate The Given Url Based On Given Criteria
	 *
	 * @param url
	 * @param validationCriteria
	 * @return Map<String, Object>
	 */
	Map<String, Object> validateURL(String url, String validationCriteria);

	/**
	 * This Method Returns The Metadata Of Given Url
	 *
	 * @param url
	 * @return Map<String, Object>
	 */
	Map<String, Object> readMetadata(String url);
}
