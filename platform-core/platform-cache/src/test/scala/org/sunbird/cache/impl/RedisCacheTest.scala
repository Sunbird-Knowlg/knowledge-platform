package org.sunbird.cache.impl


import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, FlatSpec, Matchers}

import scala.collection.immutable.Stream.Empty
import scala.concurrent.{ExecutionContext, Future}

class RedisCacheTest extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

	var cons_message: String = ""

	override def afterAll() {
		RedisCache.deleteByPattern("kptest*")
	}

	"set without ttl" should "hold the data for forever into cache" in {
		RedisCache.set("kptest-101", "kptest-value-01")
		val result = RedisCache.get("kptest-101")
		result shouldEqual "kptest-value-01"
		val resultAfter5Sec = RedisCache.get("kptest-101")
		result shouldEqual resultAfter5Sec
	}

	"set with ttl" should "hold the data upto given ttl into cache" in {
		RedisCache.set("kptest-102", "kptest-value-02", 2)
		val result = RedisCache.get("kptest-102")
		result shouldEqual "kptest-value-02"
		delay(6000)
		val resultAfter2Sec = RedisCache.get("kptest-102")
		resultAfter2Sec shouldBe ""
	}

	"get with valid key" should "return string data for given key" in {
		RedisCache.set("kptest-103", "kptest-value-03", 0)
		val result = RedisCache.get("kptest-103")
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
		RedisCache.set("kptest-105", "kptest-value-05")
		val exception = intercept[Exception] {
			RedisCache.getList("kptest-105")
		}
		exception.getMessage shouldEqual "WRONGTYPE Operation against a key holding the wrong kind of value"
	}

	"get with wrong type key" should "throw an exception" in {
		val data = List[String]("kp-test-06-list-val-01", "kp-test-06-list-val-02")
		RedisCache.saveList("kptest-106", data)
		val exception = intercept[Exception] {
			RedisCache.get("kptest-106")
		}
		exception.getMessage shouldEqual "WRONGTYPE Operation against a key holding the wrong kind of value"
	}

	"delete with key" should "delete the data from cache for given key" in {
		RedisCache.set("kptest-107", "kptest-value-07")
		RedisCache.set("kptest-108", "kptest-value-08")
		RedisCache.delete("kptest-107", "kptest-108")
		val result = RedisCache.get("kptest-107")
		val res = RedisCache.get("kptest-108")
		result shouldBe ""
		res shouldBe ""
	}

	"deleteByPattern" should "delete data for all the keys matched with pattern" in {
		RedisCache.set("kptestp-01", "kptestp-value-01", 0)
		RedisCache.set("kptestp-02", "kptestp-value-02", 0)
		RedisCache.deleteByPattern("kptestp-*")
		val result = RedisCache.get("kptestp-01")
		result shouldBe ""
		val res = RedisCache.get("kptestp-02")
		res shouldBe ""
	}

	"incrementAndGet" should "increase the value for given key by one and return" in {
		RedisCache.set("kptest-109", "0", 0)
		val result: Double = RedisCache.incrementAndGet("kptest-109")
		val exp: Double = 1.0
		exp shouldEqual result
		val res: Double = RedisCache.incrementAndGet("kptest-109")
		val exp2: Double = 2.0
		exp2 shouldEqual res
	}

	"removeFromList" should "delete data from list values for given key" in {
		val data = List[String]("kp-test-10-list-val-01", "kp-test-10-list-val-02", "kp-test-10-list-val-03")
		RedisCache.saveList("kptest-110", data)
		val input = List[String]("kp-test-10-list-val-03")
		RedisCache.removeFromList("kptest-110", input)
		val result = RedisCache.getList("kptest-110")
		result.size shouldBe 2
	}

	"addToList" should "update list data into cache for given key" in {
		val data = List[String]("kp-test-111-list-val-01", "kp-test-111-list-val-02")
		RedisCache.saveList("kptest-111", data)
		val result = RedisCache.getList("kptest-111")
		data.diff(result) shouldBe Empty
		val updateData = List[String]("kp-test-111-list-val-03", "kp-test-111-list-val-04")
		RedisCache.addToList("kptest-111", updateData)
		val res = RedisCache.getList("kptest-111")
		res.size shouldBe 4
	}

	"saveList with ttl" should "store list data into cache for given key upto ttl given" in {
		val data = List[String]("kp-test-112-list-val-01", "kp-test-112-list-val-02")
		RedisCache.saveList("kptest-112", data, 2)
		val result = RedisCache.getList("kptest-112")
		data.diff(result) shouldBe Empty
		delay(6000)
		val res = RedisCache.getList("kptest-112")
		res.isEmpty shouldBe true
	}

	"getAsync with key not having data in cache" should "return Future[String] from handler" in {
		val future: Future[String] = RedisCache.getAsync("kptest-113", (key: String) => Future("sample-data-handler"), 2)
		future map { result => {
			assert(null != result)
			result shouldEqual "sample-data-handler"
		}
		}
	}

	"getAsync with key having data in cache" should "return Future[String] from cache" in {
		RedisCache.set("kptest-114", "sample-cache-data")
		val future: Future[String] = RedisCache.getAsync("kptest-114", (key: String) => Future("sample-data-handler"), 2)
		future map { result => {
			assert(null != result)
			result shouldEqual "sample-cache-data"
		}
		}
	}

	"getListAsync with key not having data in cache" should "return Future[List[String]] from handler" in {
		val handlerData = List[String]("sample-handler-data1", "sample-handler-data2")
		val future: Future[List[String]] = RedisCache.getListAsync("kptest-115", (key: String) => Future(handlerData), 2)
		future map { result => {
			assert(null != result)
			assert(result.isInstanceOf[List[String]])
			handlerData.diff(result) shouldBe Empty
		}
		}
	}

	"getListAsync with key having data in cache" should "return Future[List[String]] from cache" in {
		val cacheData = List[String]("sample-cache-data1", "sample-cache-data2")
		val handlerData = List[String]("sample-handler-data1", "sample-handler-data2")
		RedisCache.saveList("kptest-116", cacheData)
		val future: Future[List[String]] = RedisCache.getListAsync("kptest-116", (key: String) => Future(handlerData), 2)
		future map { result => {
			assert(null != result)
			assert(result.isInstanceOf[List[String]])
			cacheData.diff(result) shouldBe Empty
		}
		}
	}

	private def delay(time: Long): Unit = {
		try Thread.sleep(time)
		catch {
			case e: Exception => None
		}
	}


}
