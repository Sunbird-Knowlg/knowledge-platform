package org.sunbird.graph.schema.validator

import java.util.concurrent.CompletionException

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.LicenseCache
import org.sunbird.common.Slug
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.schema.IDefinition
import org.sunbird.graph.service.operation.SearchAsyncOperations
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.{ExecutionContext, Future}

trait LicenseValidator extends IDefinition {
    val licenseCache: LicenseCache = new LicenseCache()
    val objectKey: String = "license"
    val mapper = new ObjectMapper()

    @throws[Exception]
    abstract override def validate(node: Node, operation: String = "create")(implicit ec: ExecutionContext): Future[Node] = {
        if (schemaValidator.getConfig.hasPath("validateLicense") && schemaValidator.getConfig.getBoolean("validateLicense")) {
            if (node.getMetadata.containsKey("origin"))
                throw new ClientException("ERR_LICENSE_VALIDATION", "License can't be updated for content which is copied")
            val license: String = node.getMetadata.getOrDefault(objectKey, "").asInstanceOf[String]
            if (StringUtils.isNotEmpty(license) && StringUtils.isNoneBlank(license)) {
                validateLicenseInCache(node, license) recoverWith { case e: CompletionException => throw e.getCause }
            } else
                setLicense(node) recoverWith { case e: CompletionException => throw e.getCause }
        }
        super.validate(node, operation)
    }

    /**
      * This method is used to validate the license stored in cache
      * (if absent read from neo4j and cache is refreshed)
      *
      * @param node
      * @param license
      * @throws
      */
    @throws[Exception]
    private def validateLicenseInCache(node: Node, license: String)(implicit ec: ExecutionContext): Future[Node] = {
        val licenseList = licenseCache.getList(objectKey)
        if (CollectionUtils.isEmpty(licenseList) || (CollectionUtils.isNotEmpty(licenseList) && !licenseList.contains(license))) {
            val resultFuture: Future[Node] = SearchAsyncOperations.getNodeByUniqueId(node.getGraphId, Slug.makeSlug(license), false, new Request())
            resultFuture.map(resultNode => {
                licenseList.add(license)
                licenseCache.setList(objectKey, licenseList, 0)
                Future(node)
            }).flatMap(f => f) recoverWith {
                case e: CompletionException => {
                    TelemetryManager.error("Exception occurred while fetching license", e.getCause)
                    if (e.getCause.isInstanceOf[ResourceNotFoundException])
                        throw new ClientException("ERR_INVALID_LICENSE", "Invalid license name for content with id: " + node.getIdentifier)
                    else
                        throw e.getCause
                }
            }
        } else
            Future(node)
    }

    /**
      *
      * @param node
      * @throws
      */
    @throws[Exception]
    private def setLicense(node: Node)(implicit ec: ExecutionContext): Future[Node] = {
        //TODO: Get the default from configuration
        val channelId: String = node.getMetadata.getOrDefault("channel", "in.ekstep").asInstanceOf[String]
        val channelString: String = licenseCache.getString("channel_" + channelId)
        if (StringUtils.isEmpty(channelString) || StringUtils.isAllBlank(channelString)) {
            val resultFuture: Future[Node] = SearchAsyncOperations.getNodeByUniqueId(node.getGraphId, channelId, false, new Request())
            resultFuture.map(channel => {
                //TODO: Get the default value from configuration
                licenseCache.setString("channel_" + channelId, mapper.writeValueAsString(channel.getMetadata), 0)
                node.getMetadata.put(objectKey, channel.getMetadata.getOrDefault("defaultLicense", "defaultLicense123"))
                Future(node)
            }).flatMap(f => f) recoverWith {
                case e: CompletionException => {
                    if (e.getCause.isInstanceOf[ResourceNotFoundException])
                        throw new ClientException("ERR_INVALID_CHANNEL", "Invalid channel id " + channelId + " is provided")
                    else {
                        TelemetryManager.error("Exception occurred while fetching channel", e.getCause)
                        throw e.getCause
                    }
                }
            }
        } else {
            val channelObject: java.util.Map[String, AnyRef] = mapper.readValue(channelString, classOf[java.util.Map[String, AnyRef]])
            //TODO: Get the default value from configuration
            node.getMetadata.put(objectKey, channelObject.getOrDefault("defaultLicense", "defaultLicense123"))
            Future(node)
        }
    }
}
