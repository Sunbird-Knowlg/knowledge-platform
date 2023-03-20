package org.sunbird.graph.util

import java.util

import com.fasterxml.jackson.databind.exc.{InvalidDefinitionException, MismatchedInputException}
import org.apache.commons.lang3.StringUtils
import org.codehaus.jackson.JsonProcessingException
import org.scalatest.{FlatSpec, Matchers}

class ScalaJsonUtilTest extends FlatSpec with Matchers {

	"serializing an empty object" should "Throw InvalidDefinitionException" in {
		assertThrows[InvalidDefinitionException] { // Result type: Assertion
			ScalaJsonUtil.serialize(new Object)
		}
	}

	"serializing an empty object" should "Throw JsonProcessingException" ignore {
		assertThrows[JsonProcessingException] { // Result type: Assertion
			ScalaJsonUtil.serialize(new util.HashMap())
		}
	}

	"serializing a valid Map object" should "Should serialize the object" in {
		val value: String = ScalaJsonUtil.serialize(Map("identifier" -> "do_1234", "status" -> "Draft"))
		assert(StringUtils.equalsIgnoreCase(value, "{\"identifier\":\"do_1234\",\"status\":\"Draft\"}"))
	}

	"serializing a valid List object" should "Should serialize the object" in {
		val value: String = ScalaJsonUtil.serialize(List("identifier", "do_1234", "status", "Draft"))
		assert(StringUtils.equalsIgnoreCase(value, "[\"identifier\",\"do_1234\",\"status\",\"Draft\"]"))
	}

	"deserializing a stringified map" should "Should deserialize the string to map" in {
		val value: Map[String, AnyRef] = ScalaJsonUtil.deserialize[Map[String, AnyRef]]("{\"identifier\":\"do_1234\",\"status\":\"Draft\"}")
		assert(value != null)
		assert(value.getOrElse("status", "").asInstanceOf[String] == "Draft")
	}

	"deserializing a stringified list to map" should "Should throw Exception" in {
		assertThrows[MismatchedInputException] {
			ScalaJsonUtil.deserialize[Map[String, AnyRef]]("[\"identifier\",\"do_1234\",\"status\",\"Draft\"]")
		}
	}

	"deserializing a stringified list" should "Should deserialize the string to list" in {
		val value:List[String] = ScalaJsonUtil.deserialize[List[String]]("[\"identifier\",\"do_1234\",\"status\",\"Draft\"]")
		assert(value != null)
		assert(value.size == 4)
	}

}
