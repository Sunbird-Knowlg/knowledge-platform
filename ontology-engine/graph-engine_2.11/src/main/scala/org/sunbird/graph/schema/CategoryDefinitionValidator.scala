package org.sunbird.graph.schema

import java.io.{ByteArrayInputStream, File}
import java.net.URI
import java.util.concurrent.CompletionException

import com.typesafe.config.{Config, ConfigFactory}
import org.leadpony.justify.api.JsonSchema
import org.sunbird.common.dto.Request
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.schema.impl.BaseSchemaValidator

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.io.Source

class CategoryDefinitionValidator(schemaName: String, version: String) extends BaseSchemaValidator(schemaName, version){
    private val basePath = {if (Platform.config.hasPath("schema.base_path")) Platform.config.getString("schema.base_path") 
    else "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/schemas/local/"} + File.separator +  schemaName.toLowerCase + File.separator + version + File.separator
    
    override def resolveSchema(id: URI): JsonSchema = {
        null
    }

    def loadSchema(categoryId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): CategoryDefinitionValidator = {
        if(ObjectCategoryDefinitionMap.containsKey(categoryId)){
            this.schema = ObjectCategoryDefinitionMap.get(categoryId).getOrElse("schema", null).asInstanceOf[JsonSchema]
            this.config = ObjectCategoryDefinitionMap.get(categoryId).getOrElse("config", null).asInstanceOf[Config]
        } 
        else {
            prepareSchema(categoryId)
        }
        this
    }

    def prepareSchema(categoryId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
        val jsonString = getFileToString("schema.json")
        val schemaMap: java.util.Map[String, AnyRef] = JsonUtils.deserialize(jsonString, classOf[java.util.Map[String, AnyRef]])
        val configMap: java.util.Map[String, AnyRef] = JsonUtils.deserialize(getFileToString("config.json"), classOf[java.util.Map[String, AnyRef]])
        val request: Request = new Request()
        Await.result(oec.graphService.getNodeByUniqueId("domain", categoryId, false, request).map(node => {
            val nodeSchema = node.getMetadata.getOrDefault("schema", "{}").asInstanceOf[String]
            schemaMap.putAll(JsonUtils.deserialize(nodeSchema, classOf[java.util.Map[String, AnyRef]]))
            configMap.putAll(node.getMetadata.getOrDefault("config", new java.util.HashMap[String, AnyRef]).asInstanceOf[java.util.Map[String, AnyRef]])
            this.schema = readSchema(new ByteArrayInputStream(JsonUtils.serialize(schemaMap).getBytes))
            this.config = ConfigFactory.parseMap(configMap)
            ObjectCategoryDefinitionMap.put(categoryId, Map("schema" -> schema, "config" -> config))
        }) recoverWith { case e: CompletionException => throw e.getCause }, Duration.apply("30 seconds"))
    }
    
    def getFileToString(fileName: String): String = {
        if(basePath startsWith "http") Source.fromURL(basePath + fileName).mkString
        else Source.fromFile(basePath + fileName).mkString
    }
}
