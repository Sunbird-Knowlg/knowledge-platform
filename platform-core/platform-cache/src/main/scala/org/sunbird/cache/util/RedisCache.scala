package org.sunbird.cache.util

import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.common.CacheErrorCode
import org.sunbird.common.exception.ServerException
import org.sunbird.telemetry.logger.TelemetryManager
import redis.clients.jedis.JedisPubSub
import org.sunbird.cache.util.RedisConnector.getConnection
import org.sunbird.cache.util.RedisConnector.returnConnection
import scala.collection.JavaConverters.collectionAsScalaIterableConverter

/**
 * This Utility Object Provide Methods To Perform CRUD Operation With Redis
 */
object RedisCache {

	/**
	 * This method store string data into cache for given Key
	 *
	 * @param key
	 * @param data
	 * @param ttl
	 */
	def saveString(key: String, data: String, ttl: Int = 0): Unit = {
		val jedis = getConnection
		try {
			jedis.del(key)
			jedis.set(key, data)
			if (ttl > 0) jedis.expire(key, ttl)
		} catch {
			case e: Exception =>
				TelemetryManager.error("Exception Occurred While Saving String Data to Redis Cache for Key : " + key + "| Exception is:", e)
				throw new ServerException(CacheErrorCode.ERR_CACHE_SAVE_PROPERTY_ERROR.toString, e.getMessage)
		} finally returnConnection(jedis)
	}

	/**
	 * This method read string data from cache for a given key
	 *
	 * @param key
	 * @return String
	 */
	def getString(key: String): String = {
		val jedis = getConnection
		try jedis.get(key)
		catch {
			case e: Exception =>
				TelemetryManager.error("Exception Occurred While Fetching String Data from Redis Cache for Key : " + key + "| Exception is:", e)
				throw new ServerException(CacheErrorCode.ERR_CACHE_GET_PROPERTY_ERROR.toString, e.getMessage)
		} finally returnConnection(jedis)
	}


	/**
	 * This method store/save list data into cache for given Key
	 *
	 * @param key
	 * @param data
	 */
	def saveList(key: String, data: List[String], isPartialUpdate: Boolean = false): Unit = {
		val jedis = getConnection
		try {
			if (isPartialUpdate.equals(true))
				jedis.del(key)
			data.foreach(entry => jedis.sadd(key, entry))
		} catch {
			case e: Exception =>
				TelemetryManager.error("Exception Occurred While Saving List Data to Redis Cache for Key : " + key + "| Exception is:", e)
				throw new ServerException(CacheErrorCode.ERR_CACHE_SAVE_PROPERTY_ERROR.toString, e.getMessage)
		} finally returnConnection(jedis)
	}

	/**
	 * This method read list data from cache for a given key
	 *
	 * @param key
	 * @return List
	 */
	def getList(key: String): List[String] = {
		val jedis = getConnection
		try jedis.smembers(key).asScala.toList
		catch {
			case e: Exception =>
				TelemetryManager.error("Exception Occurred While Fetching List Data from Redis Cache for Key : " + key + "| Exception is:", e)
				throw new ServerException(CacheErrorCode.ERR_CACHE_GET_PROPERTY_ERROR.toString, e.getMessage)
		} finally returnConnection(jedis)
	}

	/**
	 * This method delete data from cache for given key/keys
	 *
	 * @param keys
	 */
	def delete(keys: String*): Unit = {
		val jedis = getConnection
		try jedis.del(keys.map(_.asInstanceOf[String]): _*)
		catch {
			case e: Exception =>
				TelemetryManager.error("Exception Occurred While Deleting Records From Redis Cache for Identifiers : " + keys.toArray + " | Exception is : ", e)
				throw new ServerException(CacheErrorCode.ERR_CACHE_DELETE_PROPERTY_ERROR.toString, e.getMessage)
		} finally returnConnection(jedis)
	}

	/**
	 * This method delete data from cache for all key/keys matched with given pattern
	 *
	 * @param pattern
	 */
	def deleteByPattern(pattern: String): Unit = {
		if (StringUtils.isNotBlank(pattern) && !StringUtils.equalsIgnoreCase(pattern, "*")) {
			val jedis = getConnection
			try {
				val keys = jedis.keys(pattern)
				if (keys != null && keys.size > 0)
					jedis.del(keys.toArray.map(_.asInstanceOf[String]): _*)
			} catch {
				case e: Exception =>
					TelemetryManager.error("Exception Occurred While Deleting Records From Redis Cache for Pattern : " + pattern + " | Exception is : ", e)
					throw new ServerException(CacheErrorCode.ERR_CACHE_DELETE_PROPERTY_ERROR.toString, e.getMessage)
			} finally returnConnection(jedis)
		}
	}

	/**
	 * This method increment the value by 1 into cache for given key and returns the new value
	 *
	 * @param key
	 * @return Double
	 */
	def getIncVal(key: String): Double = {
		val jedis = getConnection
		val inc = 1.0
		try jedis.incrByFloat(key, inc)
		catch {
			case e: Exception =>
				TelemetryManager.error("Exception Occurred While Incrementing Value for Key : " + key + " | Exception is : ", e)
				throw new ServerException(CacheErrorCode.ERR_CACHE_GET_PROPERTY_ERROR.toString, e.getMessage)
		} finally returnConnection(jedis)
	}

	/**
	 * This method push given string message to given channel, which can be consumed by all subscribers of that channel.
	 *
	 * @param channel
	 * @param data
	 */
	def publish(channel: String, data: String): Unit = {
		if (StringUtils.isNotBlank(channel) && StringUtils.isNotBlank(data)) {
			val jedis = getConnection
			try jedis.publish(channel, data)
			catch {
				case e: Exception =>
					TelemetryManager.error("Exception Occurred While Publishing Message to Redis Channel : " + channel + " for data : " + data + " | Exception is : ", e)
					throw new ServerException(CacheErrorCode.ERR_CACHE_PUBLISH_CHANNEL_ERROR.toString, e.getMessage)
			} finally returnConnection(jedis)
		}
	}

	/**
	 * This method subscribe to given channel/channels to receive messages.
	 *
	 * @param pubSub
	 * @param channel
	 */
	def subscribe(pubSub: JedisPubSub, channel: String*): Unit = {
		if (null != channel && null != pubSub) {
			val jedis = getConnection
			try jedis.subscribe(pubSub, channel.map(_.asInstanceOf[String]): _*)
			catch {
				case e: Exception =>
					TelemetryManager.error("Exception Occurred While Subscribing to Redis Channel : " + channel + " | Exception is : ", e)
					throw new ServerException(CacheErrorCode.ERR_CACHE_SUBSCRIBE_CHANNEL_ERROR.toString, e.getMessage)
			}
		}
	}

	/**
	 * This Method Remove Given Data From Existing List For Given Key
	 *
	 * @param key
	 * @param data
	 */
	def deleteFromList(key: String, data: List[String]): Unit = {
		val jedis = getConnection
		try data.foreach(entry => jedis.srem(key, entry))
		catch {
			case e: Exception =>
				TelemetryManager.error("Exception Occurred While Deleting Partial Data From Redis Cache for Key : " + key + "| Exception is:", e)
				throw new ServerException(CacheErrorCode.ERR_CACHE_DELETE_PROPERTY_ERROR.toString, e.getMessage)
		} finally returnConnection(jedis)
	}
}
