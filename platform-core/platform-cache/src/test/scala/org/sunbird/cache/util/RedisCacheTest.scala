package org.sunbird.cache.util

import java.util.concurrent.Executors

import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.sunbird.common.exception.ServerException
import redis.clients.jedis.JedisPubSub

import scala.collection.immutable.Stream.Empty

class RedisCacheTest extends FlatSpec with Matchers with BeforeAndAfterAll {

	var cons_message: String = ""

	override def afterAll() {
		RedisCache.deleteByPattern("kptest*")
	}

	"saveString without ttl" should "hold the data for forever into cache" in {
		RedisCache.saveString("kptest-101", "kptest-value-01")
		val result = RedisCache.getString("kptest-101")
		result shouldEqual "kptest-value-01"
		delay(5000)
		val resultAfter5Sec = RedisCache.getString("kptest-101")
		result shouldEqual resultAfter5Sec
	}

	"saveString with ttl" should "hold the data upto given ttl into cache" in {
		RedisCache.saveString("kptest-102", "kptest-value-02", 2)
		val result = RedisCache.getString("kptest-102")
		result shouldEqual "kptest-value-02"
		delay(2000)
		val resultAfter2Sec = RedisCache.getString("kptest-102")
		resultAfter2Sec shouldBe null
	}

	"getString with valid key" should "return string data for given key" in {
		RedisCache.saveString("kptest-103", "kptest-value-03", 0)
		val result = RedisCache.getString("kptest-103")
		result.isInstanceOf[String] shouldBe true
		result shouldEqual "kptest-value-03"
	}

	"saveList" should "store list data into cache for given key" in {
		val data = List[String]("kp-test-04-list-val-01", "kp-test-04-list-val-02")
		RedisCache.saveList("kptest-104", data)
		val result = RedisCache.getList("kptest-104")
		data.diff(result) shouldBe Empty
	}

	"getList with wrong type key" should "throw an exception" in {
		RedisCache.saveString("kptest-105", "kptest-value-05")
		delay(6000)
		val exception = intercept[ServerException] {
			RedisCache.getList("kptest-105")
		}
		exception.getResponseCode.toString shouldEqual "SERVER_ERROR"
		exception.getErrCode shouldEqual "ERR_CACHE_GET_PROPERTY_ERROR"
		exception.getMessage shouldEqual "WRONGTYPE Operation against a key holding the wrong kind of value"
	}

	"getString with wrong type key" should "throw an exception" in {
		val data = List[String]("kp-test-06-list-val-01", "kp-test-06-list-val-02")
		RedisCache.saveList("kptest-106", data)
		delay(6000)
		val exception = intercept[ServerException] {
			RedisCache.getString("kptest-106")
		}
		exception.getResponseCode.toString shouldEqual "SERVER_ERROR"
		exception.getErrCode shouldEqual "ERR_CACHE_GET_PROPERTY_ERROR"
		exception.getMessage shouldEqual "WRONGTYPE Operation against a key holding the wrong kind of value"
	}

	"delete with key" should "delete the data from cache for given key" in {
		RedisCache.saveString("kptest-107", "kptest-value-07")
		RedisCache.saveString("kptest-108", "kptest-value-08")
		RedisCache.delete("kptest-107", "kptest-108")
		val result = RedisCache.getString("kptest-107")
		val res = RedisCache.getString("kptest-108")
		result shouldBe null
		res shouldBe null
	}

	"deleteByPattern" should "delete data for all the keys matched with pattern" in {
		RedisCache.saveString("kptestp-01", "kptestp-value-01", 0)
		RedisCache.saveString("kptestp-02", "kptestp-value-02", 0)
		RedisCache.deleteByPattern("kptestp-*")
		val result = RedisCache.getString("kptestp-01")
		result shouldBe null
		val res = RedisCache.getString("kptestp-02")
		res shouldBe null
	}

	"getIncVal" should "increase the value for given key by one and return" in {
		RedisCache.saveString("kptest-109", "0", 0)
		val result: Double = RedisCache.getIncVal("kptest-109")
		val exp: Double = 1.0
		exp shouldEqual result
		val res: Double = RedisCache.getIncVal("kptest-109")
		val exp2: Double = 2.0
		exp2 shouldEqual res
	}

	"deleteFromList" should "delete data from list values for given key" in {
		val data = List[String]("kp-test-10-list-val-01", "kp-test-10-list-val-02", "kp-test-10-list-val-03")
		RedisCache.saveList("kptest-110", data)
		val input = List[String]("kp-test-10-list-val-03")
		RedisCache.deleteFromList("kptest-110", input)
		val result = RedisCache.getList("kptest-110")
		result.size shouldBe 2
	}

	"subscribe" should "consume the message from given channel" in {
		val pubSub = new JedisPubSub() {
			override def onMessage(channel: String, msg: String): Unit = {
				processSubscription(channel, msg)
			}
		}
		Executors.newFixedThreadPool(1).execute(new Runnable() {
			override def run(): Unit = {
				RedisCache.subscribe(pubSub, "test-channel-01")
			}
		})
		RedisCache.publish("test-channel-01", "test-data-02")
		RedisCache.publish("test-channel-01", "test-data-03")
		delay(5000)
		"test-data-03".equals(cons_message)
	}

	private def processSubscription(str: String, str1: String): Unit = {
		cons_message = str1
	}

	private def delay(time: Long): Unit = {
		try Thread.sleep(time)
		catch {
			case e: Exception => None
		}
	}
}
