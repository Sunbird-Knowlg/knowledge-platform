package org.sunbird.actors

import javax.inject.Inject
import org.apache.pekko.actor.AbstractActor
import org.apache.pekko.pattern.pipe
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.health.HealthCheckManager

import scala.concurrent.{ExecutionContext, Future}


class HealthActor @Inject() (implicit oec: OntologyEngineContext) extends AbstractActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    def onReceive(request: Request): Future[Response] = {
        HealthCheckManager.checkAllSystemHealth()
    }

    override def createReceive(): AbstractActor.Receive =
        receiveBuilder()
          .`match`(classOf[Request], (req: Request) => {
            onReceive(req).pipeTo(sender())
          })
          .build()
}
