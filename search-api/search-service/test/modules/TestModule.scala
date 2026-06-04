package modules

import com.google.inject.AbstractModule
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import play.api.libs.concurrent.PekkoGuiceSupport
import utils.ActorNames

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class TestModule extends AbstractModule with PekkoGuiceSupport{

    override def configure() = {
        super.configure()
        bindActor[TestActor](ActorNames.HEALTH_ACTOR)
        bindActor[TestActor](ActorNames.SEARCH_ACTOR)
        bindActor[TestActor](ActorNames.AUDIT_HISTORY_ACTOR)
        bindActor[TestEnrichActor](ActorNames.ENRICH_ACTOR)
        println("Initialized application actors for search-service")
    }
}

class TestActor extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = {
        Future(ResponseHandler.OK)
    }
}

class TestEnrichActor extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = Future {
        val raw = request.getRequest.get("identifiers")
        raw match {
            case list: java.util.List[_] if !list.isEmpty =>
                val ids = list.asScala.map(o => if (o == null) "" else o.toString.trim).filter(_.nonEmpty).toList
                if (ids.isEmpty)
                    throw new ClientException("ERR_INVALID_REQUEST", "identifiers list must not be empty")
                val resp = ResponseHandler.OK()
                resp.put("count", ids.size)
                resp.put("identifiers", list)
                resp.put("failed", new java.util.ArrayList[String]())
                resp
            case _ =>
                throw new ClientException("ERR_INVALID_REQUEST", "identifiers must be a non-empty list")
        }
    }
}