package org.sunbird.content.enrichmentobject.actors

import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.content.enrichmentobject.mgr.EnrichmentObjectManager
import org.sunbird.graph.OntologyEngineContext

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
 * Dispatches EnrichmentObject requests, identified by operation name, to the
 * corresponding [[EnrichmentObjectManager]] method.
 */
class EnrichmentObjectActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher

  /**
   * Dispatches an incoming request to the matching EnrichmentObjectManager operation.
   *
   * @param request the incoming request, identified by `request.getOperation`
   * @return the operation's response
   */
  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case "createEnrichmentObject" => EnrichmentObjectManager.create(request)
      case "updateEnrichmentObject" =>
        EnrichmentObjectManager.update(request, request.getContext.get("identifier").asInstanceOf[String])
      case _ => ERROR(request.getOperation)
    }
  }
}
