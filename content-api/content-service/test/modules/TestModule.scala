package modules

import com.google.inject.AbstractModule
import content.utils.ActorNames
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import play.api.libs.concurrent.PekkoGuiceSupport

import scala.concurrent.{ExecutionContext, Future}

class TestModule extends AbstractModule with PekkoGuiceSupport {

    override def configure(): Unit = {
        bindActor[TestActor](ActorNames.HEALTH_ACTOR)
        bindActor[TestActor](ActorNames.CONTENT_ACTOR)
        bindActor[TestActor](ActorNames.LICENSE_ACTOR)
        bindActor[TestActor](ActorNames.COLLECTION_ACTOR)
        bindActor[TestActor](ActorNames.CHANNEL_ACTOR)
        bindActor[TestActor](ActorNames.ASSET_ACTOR)
        bindActor[TestActor](ActorNames.APP_ACTOR)
        bindActor[TestActor](ActorNames.EVENT_SET_ACTOR)
        bindActor[TestActor](ActorNames.EVENT_ACTOR)
        bindActor[TestActor](ActorNames.OBJECT_ACTOR)
        bindActor[TestActor](ActorNames.COLLECTION_CSV_ACTOR)
        println("Test Module is initialized...")
    }
}

class TestActor extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = {
        Future(ResponseHandler.OK)
    }
}
