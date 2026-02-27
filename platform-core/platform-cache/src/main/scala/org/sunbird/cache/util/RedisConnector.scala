package org.sunbird.cache.util

import org.sunbird.common.Platform
import org.sunbird.telemetry.logger.TelemetryManager
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

	registerShutdownHook()

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
	 * This Method takes a connection object and returns it to pool.
	 * Uses jedis.close() which is the modern, non-deprecated approach
	 * and correctly returns the connection to the pool (or closes it if broken).
	 *
	 * @param jedis
	 */
	protected def returnConnection(jedis: Jedis): Unit = {
		if (null != jedis) {
			try jedis.close()
			catch {
				case e: Exception => TelemetryManager.error("Error returning Redis connection to pool: " + e.getMessage, e)
			}
		}
	}

	/**
	 * Closes the JedisPool, releasing all connections.
	 * Called automatically via the JVM shutdown hook.
	 */
	def closePool(): Unit = {
		if (jedisPool != null && !jedisPool.isClosed) {
			try jedisPool.close()
			catch {
				case e: Exception => TelemetryManager.error("Error closing JedisPool: " + e.getMessage, e)
			}
		}
	}

	private def registerShutdownHook(): Unit = {
		Runtime.getRuntime.addShutdownHook(new Thread(() => {
			TelemetryManager.log("Shutting down RedisConnector — closing connection pool")
			closePool()
		}))
	}

	private def getConfig(): JedisPoolConfig = {
		val config: JedisPoolConfig = new JedisPoolConfig()
		config.setMaxTotal(MAX_CONNECTIONS)
		config.setBlockWhenExhausted(true)
		config
	}
}
