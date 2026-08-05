package org.sunbird.content.enrichment

import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.content.transcript.mgr.TranscriptManager
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node

import scala.concurrent.{ExecutionContext, Future}

/** Thin adapter — all real logic lives in TranscriptManager, which this just
 * exposes under the generic EnrichmentObjectHandler shape.
 */
object TranscriptObjectHandler extends EnrichmentObjectHandler {

  override val objectType: String = "Transcript"

  override def create(request: Request, contentNode: Node, enrichmentNode: Node)
                      (implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] =
    TranscriptManager.createObject(request, contentNode, enrichmentNode)

  override def update(request: Request, contentNode: Node, enrichmentNode: Node, objectIdentifier: String)
                      (implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] =
    TranscriptManager.updateObject(request, contentNode, enrichmentNode, objectIdentifier)

  override def approve(request: Request, contentNode: Node, enrichmentNode: Node, objectIdentifier: String)
                       (implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] =
    TranscriptManager.approveObject(request, contentNode, enrichmentNode, objectIdentifier)

  override def reject(request: Request, contentNode: Node, enrichmentNode: Node, objectIdentifier: String)
                      (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] =
    TranscriptManager.rejectObject(request, contentNode, enrichmentNode, objectIdentifier)
}
