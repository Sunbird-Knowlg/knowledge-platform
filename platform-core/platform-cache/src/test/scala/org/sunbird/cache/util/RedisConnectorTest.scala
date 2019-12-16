package org.sunbird.cache.util

import org.scalatest.{FlatSpec, Matchers}
import redis.clients.jedis.Jedis

class RedisConnectorTest extends FlatSpec with Matchers {

	"get connection" should "return a valid connection object" in {
		val conn: Jedis = RedisConnector.getConnection
		null!=conn shouldEqual true
		conn.isInstanceOf[Jedis] shouldEqual true
		conn.isConnected shouldEqual true
		RedisConnector.returnConnection(conn)
	}

	"return connection" should "submit the connection object to pool" in {
		val conn: Jedis = RedisConnector.getConnection
		conn.isInstanceOf[Jedis] shouldEqual true
		conn.isConnected shouldEqual true
		RedisConnector.returnConnection(conn)
	}
}
