package org.sunbird.cache.impl

import org.slf4j.{Logger, LoggerFactory}
import org.sunbird.cache.util.RedisConnector
import org.sunbird.common.Platform

/**
 * Redis-backed store for collection hierarchy relationship data (leaf nodes,
 * optional nodes, ancestors). Kept on its own db index — separate from the
 * general-purpose RedisCache index — so it lines up with the same dedicated
 * index used by knowledge-platform-jobs (redis.database.hierarchyRelations.id)
 * and lern-service (hierarchy_relations_redis_index).
 */
object HierarchyRelationCache extends RedisConnector {

	private val logger: Logger = LoggerFactory.getLogger(HierarchyRelationCache.getClass.getCanonicalName)
	override protected val dbIndex: Int = Platform.getInteger("redis.database.hierarchyRelations.id", 10)

	def replaceSet(key: String, data: List[String]): Unit = {
		if (isEnabled) {
			try {
				val jedis = getConnection
				try {
					jedis.del(key)
					if (data.nonEmpty) jedis.sadd(key, data: _*)
				} catch {
					case e: Exception =>
						logger.error("Exception Occurred While Saving Set Data to HierarchyRelationCache for Key : " + key + "| Exception is:", e)
				} finally returnConnection(jedis)
			} catch {
				case e: Exception =>
					logger.error("Redis Connection/Authentication Error for Key : " + key + "| Exception is:", e)
			}
		}
	}
}