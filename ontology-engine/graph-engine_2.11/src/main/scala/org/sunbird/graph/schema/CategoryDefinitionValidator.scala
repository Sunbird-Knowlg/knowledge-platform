package org.sunbird.graph.schema

import java.io.{ByteArrayInputStream, File}
import java.net.URI
import java.util

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.lang3.StringUtils
import org.leadpony.justify.api.JsonSchema
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ResourceNotFoundException, ResponseCode, ServerException}
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.schema.impl.BaseSchemaValidator

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.io.Source

class CategoryDefinitionValidator(schemaName: String, version: String) extends BaseSchemaValidator(schemaName, version){
    private val basePath = {if (Platform.config.hasPath("schema.base_path")) Platform.config.getString("schema.base_path") 
    else "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/schemas/local/"} + File.separator + schemaName.toLowerCase + File.separator + version + File.separator
    
    override def resolveSchema(id: URI): JsonSchema = {
        null
    }

    def loadSchema(ocd: ObjectCategoryDefinition)(implicit oec: OntologyEngineContext, ec: ExecutionContext): CategoryDefinitionValidator = {
        val categoryId: String = ObjectCategoryDefinitionMap.prepareCategoryId(ocd.categoryName, ocd.objectType, ocd.channel)
        if(ObjectCategoryDefinitionMap.containsKey(categoryId) && null != ObjectCategoryDefinitionMap.get(categoryId)){
            this.schema = ObjectCategoryDefinitionMap.get(categoryId).getOrElse("schema", null).asInstanceOf[JsonSchema]
            this.config = ObjectCategoryDefinitionMap.get(categoryId).getOrElse("config", null).asInstanceOf[Config]
        }
        else {
            val (schemaMap, configMap) = prepareSchema(ocd)
            this.schema = readSchema(new ByteArrayInputStream(JsonUtils.serialize(schemaMap).getBytes))
            this.config = ConfigFactory.parseMap(configMap)
            ObjectCategoryDefinitionMap.put(categoryId, Map("schema" -> schema, "config" -> config))
        }
        this
    }

    def prepareSchema(ocd: ObjectCategoryDefinition)(implicit oec: OntologyEngineContext, ec: ExecutionContext): (java.util.Map[String, AnyRef], java.util.Map[String, AnyRef]) = {
        val categoryId: String = ObjectCategoryDefinitionMap.prepareCategoryId(ocd.categoryName, ocd.objectType, ocd.channel)
        val request: Request = new Request()
        val context = new util.HashMap[String, AnyRef]()
        context.put("schemaName", "objectcategorydefinition")
        context.put("version", "1.0")
        request.setContext(context)
        request.put("identifier", categoryId)
        val response: Response = {
            val resp = Await.result(oec.graphService.readExternalProps(request, List("objectMetadata")), Duration.apply("30 seconds"))
            if (ResponseHandler.checkError(resp)) {
                if(StringUtils.equalsAnyIgnoreCase(resp.getResponseCode.name(), ResponseCode.RESOURCE_NOT_FOUND.name())) {
                    if ("all".equalsIgnoreCase(ocd.channel))
                        throw new ResourceNotFoundException(resp.getParams.getErr, resp.getParams.getErrmsg + " " + resp.getResult)
                    else {
                        val updatedId = ObjectCategoryDefinitionMap.prepareCategoryId(ocd.categoryName, ocd.objectType, "all")
                        request.put("identifier", updatedId)
                        val channelCatResp = Await.result(oec.graphService.readExternalProps(request, List("objectMetadata")), Duration.apply("30 seconds"))
                        if(StringUtils.equalsAnyIgnoreCase(channelCatResp.getResponseCode.name(), ResponseCode.RESOURCE_NOT_FOUND.name())) {
                          throw new ResourceNotFoundException(channelCatResp.getParams.getErr, channelCatResp.getParams.getErrmsg + " " + channelCatResp.getResult)
                        } else channelCatResp
                    }
                } else throw new ServerException(resp.getParams.getErr, resp.getParams.getErrmsg + " " + resp.getResult)
            } else resp
        }
        populateSchema(response, categoryId)
    }
  
    def populateSchema(response: Response, identifier: String) : (java.util.Map[String, AnyRef], java.util.Map[String, AnyRef]) = {
        val jsonString = getFileToString("schema.json")
        val schemaMap: java.util.Map[String, AnyRef] = JsonUtils.deserialize(jsonString, classOf[java.util.Map[String, AnyRef]])
        val configMap: java.util.Map[String, AnyRef] = JsonUtils.deserialize(getFileToString("config.json"), classOf[java.util.Map[String, AnyRef]])
        val objectMetadata = response.getResult.getOrDefault("objectMetadata", new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
        val nodeSchema = JsonUtils.deserialize(objectMetadata.getOrDefault("schema", "{}").asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
        schemaMap.getOrDefault("properties", new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]].putAll(nodeSchema.getOrDefault("properties", new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]])
        schemaMap.getOrDefault("required", new java.util.ArrayList[String]()).asInstanceOf[java.util.List[String]].addAll(nodeSchema.getOrDefault("required", new java.util.ArrayList[String]()).asInstanceOf[java.util.List[String]])
        configMap.putAll(JsonUtils.deserialize(objectMetadata.getOrDefault("config", "{}").asInstanceOf[String], classOf[java.util.Map[String, AnyRef]]))
        (schemaMap, configMap)
    }

    def getFileToString(fileName: String): String = {
        if(basePath startsWith "http") Source.fromURL(basePath + fileName).mkString
        else Source.fromFile(basePath + fileName).mkString
    }
}
