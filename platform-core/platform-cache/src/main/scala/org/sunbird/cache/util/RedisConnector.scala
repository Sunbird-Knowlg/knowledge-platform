package org.sunbird.cache.util

import org.sunbird.cache.common.CacheErrorCode
import org.sunbird.common.Platform
import org.sunbird.common.exception.ServerException
import org.sunbird.telemetry.logger.TelemetryManager
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

/**
 * This Object Provides Methods To Get And Return Redis Connection Object
 */
object RedisConnector {

	private val HOST = Platform.getString("redis.host", "localhost")
	private val PORT = Platform.getInteger("redis.port", 6379)
	private val MAX_CONNECTIONS = Platform.getInteger("redis.maxConnections", 128)
	private val INDEX = Platform.getInteger("redis.dbIndex", 0)

	private val config: JedisPoolConfig = new JedisPoolConfig()
	config.setMaxTotal(MAX_CONNECTIONS)
	config.setBlockWhenExhausted(true);
	private val jedisPool: JedisPool = new JedisPool(config, HOST, PORT)


	/**
	 * This Method Returns a connection object from connection pool.
	 *
	 * @return Jedis Object
	 */
	def getConnection: Jedis = try {
		val jedis = jedisPool.getResource
		if (INDEX > 0) jedis.select(INDEX)
		jedis
	} catch {
		case e: Exception =>
			TelemetryManager.error("Exception Occurred While Returning Redis Cache Connection Object to Pool.", e)
			throw new ServerException(CacheErrorCode.ERR_CACHE_CONNECTION_ERROR.toString, e.getMessage)
	}

	/**
	 * This Method takes a connection object and put it back to pool.
	 *
	 * @param jedis
	 */
	def returnConnection(jedis: Jedis): Unit = {
		try if (null != jedis) jedisPool.returnResource(jedis)
		catch {
			case e: Exception =>
				TelemetryManager.error("Exception Occurred While Returning Redis Cache Connection Object to Pool.", e)
				throw new ServerException(CacheErrorCode.ERR_CACHE_CONNECTION_ERROR.toString, e.getMessage)
		}
	}
}
