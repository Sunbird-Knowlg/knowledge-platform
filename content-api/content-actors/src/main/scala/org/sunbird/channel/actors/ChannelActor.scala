package org.sunbird.channel.actors

import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response}

import scala.concurrent.{ExecutionContext, Future}

class ChannelActor extends BaseActor {
    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = {
        request.getOperation match {
            case "createChannel" => ???
            case "readChannel" => ???
            case "updateChannel" => ???
            case "listChannel" => ???
            case _ => ???
        }
    }

    def create(request: Request): Future[Response] = ???

    def read(request: Request): Future[Response] = ???

    def update(request: Request): Future[Response] = ???

    def list(request: Request): Future[Response] = ???
}
