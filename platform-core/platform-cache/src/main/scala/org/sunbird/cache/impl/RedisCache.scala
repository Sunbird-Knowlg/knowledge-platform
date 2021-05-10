package org.sunbird.cache.impl

import org.apache.commons.lang3.StringUtils
import org.slf4j.{Logger, LoggerFactory}
import org.sunbird.cache.util.RedisConnector

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
 * This Utility Object Provide Methods To Perform CRUD Operation With Redis
 */
object RedisCache extends RedisConnector {

	private val logger: Logger = LoggerFactory.getLogger(RedisCache.getClass.getCanonicalName)

	/**
	 * This method store string data into cache for given Key
	 *
	 * @param key
	 * @param data
	 * @param ttl
	 */
	def set(key: String, data: String, ttl: Int = 0): Unit = {
		val jedis = getConnection
		try {
			jedis.del(key)
			jedis.set(key, data)
			if (ttl > 0) jedis.expire(key, ttl)
		} catch {
			case e: Exception =>
				logger.error("Exception Occurred While Saving String Data to Redis Cache for Key : " + key + "| Exception is:", e)
				throw e
		} finally returnConnection(jedis)
	}

	/**
	 * This method read string data from cache for a given key
	 *
	 * @param key
	 * @param ttl
	 * @param handler
	 * @return
	 */
	def get(key: String, handler: (String) => String = defaultStringHandler, ttl: Int = 0): String = {
		val jedis = getConnection
		try {
			var data = jedis.get(key)
			if (null != handler && (null == data || data.isEmpty)) {
				data = handler(key)
				if (null != data && !data.isEmpty)
					set(key, data, ttl)
			}
			data
		}
		catch {
			case e: Exception =>
				logger.error("Exception Occurred While Fetching String Data from Redis Cache for Key : " + key + "| Exception is:", e)
				throw e
		} finally returnConnection(jedis)
	}

	/**
	 * This Method Returns Future[String] for given key
	 *
	 * @param key
	 * @param asyncHandler
	 * @param ttl
	 * @param ec
	 * @return Future[String]
	 */
	def getAsync(key: String, asyncHandler: (String) => Future[String], ttl: Int = 0)(implicit ec: ExecutionContext): Future[String] = {
		val jedis = getConnection
		try {
			val data = jedis.get(key)
			if (null != asyncHandler && (null == data || data.isEmpty)) {
				val dataFuture: Future[String] = asyncHandler(key)
				dataFuture.map(value => {
						if (null != value && !value.isEmpty)
							set(key, value, ttl)
					value
				})
			} else Future{data}
		}
		catch {
			case e: Exception =>
				logger.error("Exception Occurred While Fetching String Data from Redis Cache for Key : " + key + "| Exception is:", e)
				throw e
		} finally returnConnection(jedis)
	}

	/**
	 * This method increment the value by 1 into cache for given key and returns the new value
	 *
	 * @param key
	 * @return Double
	 */
	def incrementAndGet(key: String): Double = {
		val jedis = getConnection
		val inc = 1.0
		try jedis.incrByFloat(key, inc)
		catch {
			case e: Exception =>
				logger.error("Exception Occurred While Incrementing Value for Key : " + key + " | Exception is : ", e)
				throw e
		} finally returnConnection(jedis)
	}

	/**
	 * This method store/save list data into cache for given Key
	 *
	 * @param key
	 * @param data
	 * @param isPartialUpdate
	 * @param ttl
	 */
	def saveList(key: String, data: List[String], ttl: Int = 0, isPartialUpdate: Boolean = false): Unit = {
		val jedis = getConnection
		try {
			if (!isPartialUpdate)
				jedis.del(key)
			data.foreach(entry => jedis.sadd(key, entry))
			if (ttl > 0 && !isPartialUpdate) jedis.expire(key, ttl)
		} catch {
			case e: Exception =>
				logger.error("Exception Occurred While Saving List Data to Redis Cache for Key : " + key + "| Exception is:", e)
				throw e
		} finally returnConnection(jedis)
	}

	/**
	 * This method store/save list data into cache for given Key
	 *
	 * @param key
	 * @param data
	 */
	def addToList(key: String, data: List[String]): Unit = {
		saveList(key, data, 0, true)
	}

	/**
	 * This method returns list data from cache for a given key
	 *
	 * @param key
	 * @param handler
	 * @param ttl
	 * @return
	 */
	def getList(key: String, handler: (String) => List[String] = defaultListHandler, ttl: Int = 0): List[String] = {
		val jedis = getConnection
		try {
			var data = jedis.smembers(key).asScala.toList
			if (null != handler && (null == data || data.isEmpty)) {
				data = handler(key)
				if (null != data && !data.isEmpty)
					saveList(key, data, ttl, false)
			}
			data
		} catch {
			case e: Exception =>
				logger.error("Exception Occurred While Fetching List Data from Redis Cache for Key : " + key + "| Exception is:", e)
				throw e
		} finally returnConnection(jedis)
	}

	/**
	 * This method returns list data from cache for a given key
	 *
	 * @param key
	 * @param asyncHandler
	 * @param ttl
	 * @param ec
	 * @return Future[List[String]]
	 */
	def getListAsync(key: String, asyncHandler: (String) => Future[List[String]], ttl: Int = 0)(implicit ec: ExecutionContext): Future[List[String]] = {
		val jedis = getConnection
		try {
			val data = jedis.smembers(key).asScala.toList
			if (null != asyncHandler && (null == data || data.isEmpty)) {
				val dataFuture = asyncHandler(key)
				dataFuture.map(value => {
					if (null != value && !value.isEmpty)
						saveList(key, value, ttl, false)
					value
				})
			} else Future {data}
		} catch {
			case e: Exception =>
				logger.error("Exception Occurred While Fetching List Data from Redis Cache for Key : " + key + "| Exception is:", e)
				throw e
		} finally returnConnection(jedis)
	}

	/**
	 * This Method Remove Given Data From Existing List For Given Key
	 *
	 * @param key
	 * @param data
	 */
	def removeFromList(key: String, data: List[String]): Unit = {
		val jedis = getConnection
		try data.foreach(entry => jedis.srem(key, entry))
		catch {
			case e: Exception =>
				logger.error("Exception Occurred While Deleting Partial Data From Redis Cache for Key : " + key + "| Exception is:", e)
				throw e
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
				logger.error("Exception Occurred While Deleting Records From Redis Cache for Identifiers : " + keys.toArray + " | Exception is : ", e)
				throw e
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
					logger.error("Exception Occurred While Deleting Records From Redis Cache for Pattern : " + pattern + " | Exception is : ", e)
					throw e
			} finally returnConnection(jedis)
		}
	}

	private def defaultStringHandler(objKey: String): String = {
		//Default Implementation Can Be Provided Here
		""
	}

	private def defaultListHandler(objKey: String): List[String] = {
		//Default Implementation Can Be Provided Here
		List()
	}
}
