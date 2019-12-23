package org.sunbird.cache.util

import org.sunbird.common.Platform
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

/**
 * This Object Provides Methods To Get And Return Redis Connection Object
 */
trait RedisConnector {

	private val HOST = Platform.getString("redis.host", "localhost")
	private val PORT = Platform.getInteger("redis.port", 6379)
	private val MAX_CONNECTIONS = Platform.getInteger("redis.maxConnections", 128)
	private val INDEX = Platform.getInteger("redis.dbIndex", 0)
	private val jedisPool: JedisPool = new JedisPool(getConfig(), HOST, PORT)

	/**
	 * This Method Returns a connection object from connection pool.
	 *
	 * @return Jedis Object
	 */
	protected def getConnection: Jedis = try {
		val jedis = jedisPool.getResource
		if (INDEX > 0) jedis.select(INDEX)
		jedis
	} catch {
		case e: Exception => throw e
	}

	/**
	 * This Method takes a connection object and put it back to pool.
	 *
	 * @param jedis
	 */
	protected def returnConnection(jedis: Jedis): Unit = {
		try if (null != jedis) jedisPool.returnResource(jedis)
		catch {
			case e: Exception => throw e
		}
	}

	private def getConfig(): JedisPoolConfig = {
		val config: JedisPoolConfig = new JedisPoolConfig()
		config.setMaxTotal(MAX_CONNECTIONS)
		config.setBlockWhenExhausted(true);
		config
	}
}
