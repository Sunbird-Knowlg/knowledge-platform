package org.sunbird.utils.content

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import redis.clients.jedis.{Client, Jedis, Response, Transaction}

import scala.collection.mutable.ListBuffer

class HierarchyRelationCacheSpec extends AnyFlatSpec with Matchers {

  /** Records the commands queued on the transaction; exec()/queueing can be made to fail. */
  private class FakeTx(calls: ListBuffer[String], execResult: java.util.List[AnyRef], failOnSadd: Boolean)
    extends Transaction(new Client("localhost")) {
    override def del(keys: String*): Response[java.lang.Long] = { calls += s"del:${keys.mkString(",")}"; null }
    override def sadd(key: String, members: String*): Response[java.lang.Long] = {
      if (failOnSadd) throw new RuntimeException("sadd failed")
      calls += s"sadd:$key:${members.mkString(",")}"; null
    }
    override def exec(): java.util.List[AnyRef] = { calls += "exec"; execResult }
    override def discard(): String = { calls += "discard"; "OK" }
  }

  private class FakeJedis(tx: Transaction, calls: ListBuffer[String]) extends Jedis("localhost") {
    override def multi(): Transaction = { calls += "multi"; tx }
  }

  private def run(data: List[String], execResult: java.util.List[AnyRef] = new java.util.ArrayList[AnyRef](),
                  failOnSadd: Boolean = false): (Boolean, List[String]) = {
    val calls = ListBuffer[String]()
    val result = HierarchyRelationCache.writeSet(new FakeJedis(new FakeTx(calls, execResult, failOnSadd), calls), "k", data)
    (result, calls.toList)
  }

  "writeSet" should "queue DEL and a single SADD with all entries inside one transaction" in {
    val (ok, calls) = run(List("a", "b", "c"))
    ok shouldBe true
    calls shouldBe List("multi", "del:k", "sadd:k:a,b,c", "exec")
  }

  it should "only delete the key when the data is empty" in {
    val (ok, calls) = run(List())
    ok shouldBe true
    calls shouldBe List("multi", "del:k", "exec")
  }

  it should "return false when the transaction is aborted (exec returns null)" in {
    val (ok, _) = run(List("a"), execResult = null)
    ok shouldBe false
  }

  it should "discard the transaction and return false when queueing fails" in {
    val (ok, calls) = run(List("a"), failOnSadd = true)
    ok shouldBe false
    calls should contain("discard")
    calls should not contain "exec"
  }

  "replaceSet" should "report success without touching Redis when redis is disabled" in {
    HierarchyRelationCache.replaceSet("k", List("a")) shouldBe true
  }
}
