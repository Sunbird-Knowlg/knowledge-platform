package org.sunbird.graph.schema.validator

import java.util.concurrent.CompletionException

import org.apache.commons.collections4.{CollectionUtils}
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

    @throws[Exception]
    abstract override def validate(node: Node, operation: String = "create")(implicit ec: ExecutionContext): Future[Node] = {
        if (schemaValidator.getConfig.hasPath("validateLicense") && schemaValidator.getConfig.getBoolean("validateLicense")) {
            val license: String = node.getMetadata.getOrDefault("license", "").asInstanceOf[String]
            if (StringUtils.isNotEmpty(license) && StringUtils.isNoneBlank(license)) {
                if (node.getMetadata.containsKey("origin"))
                    throw new ClientException("ERR_LICENSE_VALIDATION", "License can't be updated for content which is copied")
                validateLicenseInCache(node, license) recoverWith { case e: CompletionException => throw e.getCause }
            } else
                setLicense(node) recoverWith { case e: CompletionException => throw e.getCause }
        } else
            Future(node)
    }

    /**
      * This method is used to validate the license stored in cache
      * (if absent read from neo4j and cache is refreshed)
      *
      * @param license
      * @throws
      */
    @throws[Exception]
    private def validateLicenseInCache(node: Node, license: String)(implicit ec: ExecutionContext): Future[Node] = {
        val licenseList = licenseCache.getList("license")
        if (CollectionUtils.isEmpty(licenseList) || (CollectionUtils.isNotEmpty(licenseList) && !licenseList.contains(license))) {
            val resultFuture: Future[Node] = SearchAsyncOperations.getNodeByUniqueId(node.getGraphId, Slug.makeSlug(license), false, new Request())
            resultFuture.map(resultNode => {
                licenseList.add(license)
                licenseCache.setList("license", licenseList, 0)
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
        val channel: String = node.getMetadata.getOrDefault("channel", "in.ekstep").asInstanceOf[String]
        val resultFuture: Future[Node] = SearchAsyncOperations.getNodeByUniqueId(node.getGraphId, channel, false, new Request())
        resultFuture.map(channel => {
            //TODO: Get the default value from environment
            node.getMetadata.put("license", channel.getMetadata.getOrDefault("defaultLicense", "defaultLicense123"))
            Future(node)
        }).flatMap(f => f) recoverWith {
            case e: CompletionException => {
                TelemetryManager.error("Exception occurred while fetching channel", e.getCause)
                if (e.getCause.isInstanceOf[ResourceNotFoundException])
                    throw new ClientException("ERR_INVALID_CHANNEL", "Invalid channel id " + channel + " is provided")
                else
                    throw e.getCause
            }
        }
    }
}
