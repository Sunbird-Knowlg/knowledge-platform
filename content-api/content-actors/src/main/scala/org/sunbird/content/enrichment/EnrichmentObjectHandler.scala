package org.sunbird.content.enrichment

import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node

import scala.concurrent.{ExecutionContext, Future}

/**
 * Strategy interface for one Enrichment child object type (Transcript today;
 * any future type — e.g. Summary — implements this same trait and registers
 * itself in EnrichmentObjectHandlerRegistry). ContentActor's four generic
 * object/{create,update,approve,reject} operations dispatch to whichever
 * handler matches the request's objectType, so none of that generic code
 * needs to change when a new object type is added — only a new handler +
 * one registry entry.
 */
trait EnrichmentObjectHandler {
  /** The IL_FUNC_OBJECT_TYPE this handler is responsible for, e.g. "Transcript". */
  def objectType: String

  def create(request: Request, contentNode: Node, enrichmentNode: Node)
            (implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response]

  def update(request: Request, contentNode: Node, enrichmentNode: Node, objectIdentifier: String)
            (implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response]

  def approve(request: Request, contentNode: Node, enrichmentNode: Node, objectIdentifier: String)
             (implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response]

  def reject(request: Request, contentNode: Node, enrichmentNode: Node, objectIdentifier: String)
            (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response]
}
