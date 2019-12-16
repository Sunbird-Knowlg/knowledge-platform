package org.sunbird.cache.mgr

/**
 * Contract for Cache Management
 *
 * @author Kumar Gauraw
 */
trait ICacheManager[T] {

	/**
	 * This method provides key generation implementation for cache.
	 *
	 * @param params
	 * @return String
	 */
	def getKey(params: String*): String

	/**
	 * This method provides implementation of read operation for given key
	 *
	 * @param key
	 * @param handler
	 * @return T
	 */
	def getObject(key: String, handler: (String, String) => T): T

	/**
	 * This method provides implementation of write/save operation for given key
	 *
	 * @param key
	 * @param data
	 * @param ttl
	 */
	def setObject(key: String, data: T, ttl: Int): Unit

	/**
	 * This method provides implementation for read operation with List Value
	 *
	 * @param key
	 * @param handler
	 * @return List[String]
	 */
	def getList(key: String, handler: (String, String) => List[String]): List[String]

	/**
	 * This method provides implementation for write/save operation with List Value
	 *
	 * @param key
	 * @param data
	 * @param isPartialUpdate
	 */
	def setList(key: String, data: List[String], isPartialUpdate: Boolean): Unit

	/**
	 * This method provides implementation for increment operation for value of given key
	 *
	 * @param key
	 * @return Double
	 */
	def increment(key: String): Double

	/**
	 * This method provides implementation for reset/delete operation for given key/keys
	 *
	 * @param key
	 */
	def delete(key: String*): Unit

	/**
	 *
	 * @param key
	 * @param data
	 */
	def delete(key: String, data: List[String]): Unit

	/**
	 * This method provides implementation for publish message operation to Redis Channel.
	 *
	 * @param channel
	 * @param message
	 */
	def publish(channel: String, message: String): Unit

	/**
	 * This method provides implementation for subscribe operation to Redis Channel.
	 *
	 * @param channels
	 */
	def subscribe(channels: String*): Unit

}
