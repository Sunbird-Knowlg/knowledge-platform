package modules

import com.google.inject.AbstractModule
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import play.libs.akka.AkkaGuiceSupport
import utils.ActorNames

import scala.concurrent.{ExecutionContext, Future}

class TestModule extends AbstractModule with AkkaGuiceSupport {

    override def configure(): Unit = {
        bindActor(classOf[TestActor], ActorNames.HEALTH_ACTOR)
        bindActor(classOf[TestActor], ActorNames.CONTENT_ACTOR)
        bindActor(classOf[TestActor], ActorNames.LICENSE_ACTOR)
        bindActor(classOf[TestActor], ActorNames.COLLECTION_ACTOR)
        bindActor(classOf[TestActor], ActorNames.CHANNEL_ACTOR)
        bindActor(classOf[TestActor], ActorNames.CATEGORY_ACTOR)
        bindActor(classOf[TestActor], ActorNames.ASSET_ACTOR)
        bindActor(classOf[TestActor], ActorNames.APP_ACTOR)
        bindActor(classOf[TestActor], ActorNames.EVENT_SET_ACTOR)
        bindActor(classOf[TestActor], ActorNames.EVENT_ACTOR)
        bindActor(classOf[TestActor], ActorNames.OBJECT_ACTOR)
        bindActor(classOf[TestActor], ActorNames.COLLECTION_CSV_ACTOR)
        println("Test Module is initialized...")
    }
}

class TestActor extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = {
        Future(ResponseHandler.OK)
    }
}
