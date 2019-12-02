package org.sunbird.utils

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object ScalaJsonUtils {
    @transient val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    @throws(classOf[Exception])
    def serialize(obj: AnyRef): String = {
        mapper.writeValueAsString(obj);
    }

    @throws(classOf[Exception])
    def deserialize[T: Class](value: String): T = {
        mapper.readValue(value, new TypeReference[T]{})
    }

}
