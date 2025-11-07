package modules

import com.google.inject.AbstractModule
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import play.api.libs.concurrent.PekkoGuiceSupport
import utils.ActorNames

import scala.concurrent.{ExecutionContext, Future}

class TestModule extends AbstractModule with PekkoGuiceSupport{

    override def configure() = {
        super.configure()
        bindActor[TestActor](ActorNames.HEALTH_ACTOR)
        bindActor[TestActor](ActorNames.SEARCH_ACTOR)
        bindActor[TestActor](ActorNames.AUDIT_HISTORY_ACTOR)
        println("Initialized application actors for search-service")
    }
}

class TestActor extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = {
        Future(ResponseHandler.OK)
    }
}