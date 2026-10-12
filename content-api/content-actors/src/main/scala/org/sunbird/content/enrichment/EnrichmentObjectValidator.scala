package org.sunbird.content.enrichment

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.ClientException

/** Pre-dispatch validation shared by ContentActor's four generic
 * object/{create,update,approve,reject} operations — resolves the request's
 * declared objectType to a registered EnrichmentObjectHandler, or fails
 * clearly before any graph work happens.
 */
object EnrichmentObjectValidator {

  def requireHandler(objectType: String): EnrichmentObjectHandler = {
    if (StringUtils.isBlank(objectType))
      throw new ClientException("ERR_MISSING_OBJECT_TYPE", "objectType is required.")
    EnrichmentObjectHandlerRegistry.get(objectType)
      .getOrElse(throw new ClientException("ERR_UNKNOWN_OBJECT_TYPE", s"Unsupported objectType: '$objectType'."))
  }

  def requireObjectIdentifier(objectIdentifier: String): String = {
    if (StringUtils.isBlank(objectIdentifier))
      throw new ClientException("ERR_MISSING_OBJECT_IDENTIFIER", "objectIdentifier is required.")
    objectIdentifier
  }
}
