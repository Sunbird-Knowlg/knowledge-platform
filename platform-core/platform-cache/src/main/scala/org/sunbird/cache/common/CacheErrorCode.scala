package org.sunbird.cache.common

/**
 * Error Codes For platform-cache module
 *
 * @author Kumar Gauraw
 */
object CacheErrorCode extends Enumeration {

	val ERR_CACHE_CONNECTION_ERROR, ERR_CACHE_SAVE_PROPERTY_ERROR, ERR_CACHE_GET_PROPERTY_ERROR, ERR_CACHE_DELETE_PROPERTY_ERROR,
	ERR_CACHE_PUBLISH_CHANNEL_ERROR, ERR_CACHE_SUBSCRIBE_CHANNEL_ERROR = Value

}
