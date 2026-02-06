package org.sunbird.graph.validator

import java.util
import org.sunbird.graph.BaseSpec
import org.sunbird.common.exception.ResourceNotFoundException
import scala.collection.JavaConverters._

class NodeValidatorSpec extends BaseSpec {

    override def beforeAll(): Unit = {
        super.beforeAll()
        createBulkNodes()
    }

    "validate" should "return nodes when multiple valid IDs are provided" in {
        val ids = util.Arrays.asList("do_0000123", "do_0000234")
        val future = NodeValidator.validate("domain", ids)
        future.map { result =>
            result.size() should be(2)
            result.containsKey("do_0000123") should be(true)
            result.containsKey("do_0000234") should be(true)
        }
    }

    it should "throw ResourceNotFoundException when some IDs are missing" in {
        val ids = util.Arrays.asList("do_0000123", "do_non_existent")
        recoverToSucceededIf[ResourceNotFoundException] {
            NodeValidator.validate("domain", ids)
        }
    }
}
