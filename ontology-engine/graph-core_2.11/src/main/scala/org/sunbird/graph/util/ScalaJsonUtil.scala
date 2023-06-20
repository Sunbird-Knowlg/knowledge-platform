package org.sunbird.graph.util

import java.lang.reflect.{ParameterizedType, Type}

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object ScalaJsonUtil {

	@transient val mapper = new ObjectMapper()
	mapper.registerModule(DefaultScalaModule)

	@throws(classOf[Exception])
	def serialize(obj: AnyRef): String = {
		mapper.writeValueAsString(obj);
	}

	@throws(classOf[Exception])
	def deserialize[T: Manifest](value: String): T = mapper.readValue(value, typeReference[T]);

	private[this] def typeReference[T: Manifest] = new TypeReference[T] {
		override def getType = typeFromManifest(manifest[T])
	}


	private[this] def typeFromManifest(m: Manifest[_]): Type = {
		if (m.typeArguments.isEmpty) { m.runtimeClass }
		// $COVERAGE-OFF$Disabling scoverage as this code is impossible to test
		else new ParameterizedType {
			def getRawType = m.runtimeClass
			def getActualTypeArguments = m.typeArguments.map(typeFromManifest).toArray
			def getOwnerType = null
		}
		// $COVERAGE-ON$
	}
}
