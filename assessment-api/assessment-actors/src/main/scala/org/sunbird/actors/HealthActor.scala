package org.sunbird.actors

import scala.concurrent.{ExecutionContext, Future}
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.health.HealthCheckManager


class HealthActor extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    @throws[Throwable]
    override def onReceive(request: Request): Future[Response] = {
        HealthCheckManager.checkAllSystemHealth()
    }
}
