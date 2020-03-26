package org.sunbird.actors

import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.health.HealthCheckManager


class HealthActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    @throws[Throwable]
    override def onReceive(request: Request): Future[Response] = {
        HealthCheckManager.checkAllSystemHealth()
    }
}
