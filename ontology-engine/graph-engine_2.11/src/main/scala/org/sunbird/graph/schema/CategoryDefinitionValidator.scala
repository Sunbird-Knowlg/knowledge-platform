package org.sunbird.graph.schema

import java.io.{ByteArrayInputStream, File}
import java.net.URI
import java.util
import java.util.concurrent.CompletionException

import com.typesafe.config.{Config, ConfigFactory}
import org.leadpony.justify.api.JsonSchema
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.ResourceNotFoundException
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.schema.impl.BaseSchemaValidator
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.io.Source

class CategoryDefinitionValidator(schemaName: String, version: String) extends BaseSchemaValidator(schemaName, version){
    private val basePath = {if (Platform.config.hasPath("schema.base_path")) Platform.config.getString("schema.base_path") 
    else "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/schemas/local/"} + File.separator + schemaName.toLowerCase + File.separator + version + File.separator
    
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

    def prepareSchema(categoryId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Unit = {
        val request: Request = new Request()
        val context = new util.HashMap[String, AnyRef]()
        context.put("schemaName", "objectcategorydefinition")
        context.put("version", "1.0")
        request.setContext(context)
        request.put("identifier", categoryId)
        val response: Response = try {
            Await.result(ExternalPropsManager.fetchProps(request, List("objectMetadata")), Duration.apply("30 seconds"))
        } catch {
            case e: CompletionException => {
                if( e.getCause.isInstanceOf[ResourceNotFoundException]){
                    if("all".equalsIgnoreCase(categoryId.substring(categoryId.lastIndexOf("_") + 1)))
                        throw e.getCause
                    else {
                        try {
                            val updatedId = categoryId.replace(categoryId.substring(categoryId.lastIndexOf("_") + 1), "all")
                            request.put("identifier", updatedId)
                            Await.result(ExternalPropsManager.fetchProps(request, List("objectMetadata")), Duration.apply("30 seconds"))
                        } catch {
                            case e: CompletionException => {
                                throw e.getCause
                            }
                        }
                    }
                }
                else throw e.getCause
            }
        }
        populateSchema(response, categoryId)
    }

    def populateSchema( response: Response, identifier: String) = {
        val jsonString = getFileToString("schema.json")
        val schemaMap: java.util.Map[String, AnyRef] = JsonUtils.deserialize(jsonString, classOf[java.util.Map[String, AnyRef]])
        val configMap: java.util.Map[String, AnyRef] = JsonUtils.deserialize(getFileToString("config.json"), classOf[java.util.Map[String, AnyRef]])
        val objectMetadata = JsonUtils.deserialize(response.getResult.getOrDefault("objectMetadata", "{}").asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
        val nodeSchema = objectMetadata.getOrDefault("schema", new java.util.HashMap[String, AnyRef]).asInstanceOf[java.util.Map[String, AnyRef]]
        schemaMap.getOrDefault("properties", new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]].putAll(nodeSchema.getOrDefault("properties", new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]])
        schemaMap.getOrDefault("required", new java.util.ArrayList[String]()).asInstanceOf[java.util.List[String]].addAll(nodeSchema.getOrDefault("required", new java.util.ArrayList[String]()).asInstanceOf[java.util.List[String]])
        configMap.putAll(objectMetadata.getOrDefault("config", new java.util.HashMap[String, AnyRef]).asInstanceOf[java.util.Map[String, AnyRef]])
        this.schema = readSchema(new ByteArrayInputStream(JsonUtils.serialize(schemaMap).getBytes))
        this.config = ConfigFactory.parseMap(configMap)
        ObjectCategoryDefinitionMap.put(identifier, Map("schema" -> schema, "config" -> config))
    }
    
    def getFileToString(fileName: String): String = {
        if(basePath startsWith "http") Source.fromURL(basePath + fileName).mkString
        else Source.fromFile(basePath + fileName).mkString
    }
}
