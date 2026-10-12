package org.sunbird.utils.content

import org.slf4j.{Logger, LoggerFactory}
import redis.clients.jedis.Jedis
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

	/**
	 * Atomically replaces the set at `key` (DEL + single SADD inside MULTI/EXEC), so readers
	 * never observe a partially written set.
	 *
	 * @return true if the write was applied (or Redis is disabled); false on any failure
	 */
	def replaceSet(key: String, data: List[String]): Boolean = {
		if (!isEnabled) true
		else {
			try {
				val jedis = getConnection
				try writeSet(jedis, key, data)
				finally returnConnection(jedis)
			} catch {
				case e: Exception =>
					logger.error("Redis Connection/Authentication Error for Key : " + key + "| Exception is:", e)
					false
			}
		}
	}

	private[utils] def writeSet(jedis: Jedis, key: String, data: List[String]): Boolean = {
		try {
			val tx = jedis.multi()
			try {
				tx.del(Seq(key): _*)
				if (data.nonEmpty) tx.sadd(key, data: _*)
				// exec() returns null when the transaction was aborted
				tx.exec() != null
			} catch {
				case e: Exception =>
					try tx.discard() catch { case _: Exception => () }
					throw e
			}
		} catch {
			case e: Exception =>
				logger.error("Exception Occurred While Saving Set Data to HierarchyRelationCache for Key : " + key + "| Exception is:", e)
				false
		}
	}
}
