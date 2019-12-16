package org.sunbird.cache.mgr

import java.util.concurrent.Executors

import org.sunbird.cache.common.CacheErrorCode
import org.sunbird.cache.util.RedisCache
import org.sunbird.common.exception.ServerException
import org.sunbird.telemetry.logger.TelemetryManager
import redis.clients.jedis.JedisPubSub

/**
 * Abstract Implementation For Local Cache
 * @see ICacheManager
 * @tparam T
 */
abstract class LocalCacheManager[T] extends ICacheManager[T] {

	protected def init(channels: String*): Unit = {
		subscribe(channels.map(_.asInstanceOf[String]): _*)
	}

	override def publish(channel: String, message: String): Unit = {
		RedisCache.publish(channel, message)
	}

	override def subscribe(channels: String*): Unit = {
		val pubSub = new JedisPubSub() {
			override def onMessage(channel: String, message: String): Unit = {
				processSubscription(channel, message)
			}
		}
		val pool = Executors.newFixedThreadPool(1)
		try {
			pool.execute(new Runnable() {
				override def run(): Unit = {
					RedisCache.subscribe(pubSub, channels.map(_.asInstanceOf[String]): _*)
				}
			})
		} catch {
			case e: Exception =>
				TelemetryManager.error("Exception Occurred While Subscribing to channels : " + channels + " | Exception is : " + e)
				throw new ServerException(CacheErrorCode.ERR_CACHE_SUBSCRIBE_CHANNEL_ERROR.toString, e.getMessage)
		} finally if (null != pool) pool.shutdown()
	}

	protected def processSubscription(channel: String, message: String): Unit

}
