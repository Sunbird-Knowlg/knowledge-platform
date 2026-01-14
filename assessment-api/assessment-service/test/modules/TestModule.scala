package modules

import com.google.inject.AbstractModule
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import play.libs.pekko.PekkoGuiceSupport
import utils.ActorNames

import scala.concurrent.{ExecutionContext, Future}

class TestModule extends AbstractModule with PekkoGuiceSupport {
	override def configure(): Unit = {
		bindActor(classOf[TestActor], ActorNames.HEALTH_ACTOR)
		bindActor(classOf[TestActor], ActorNames.ITEM_SET_ACTOR)
		bindActor(classOf[TestActor], ActorNames.QUESTION_ACTOR)
		bindActor(classOf[TestActor], ActorNames.QUESTION_SET_ACTOR)
		bindActor(classOf[TestActor], ActorNames.QUESTION_V5_ACTOR)
		bindActor(classOf[TestActor], ActorNames.QUESTION_SET_V5_ACTOR)
		println("Test Module is initialized...")
	}
}

class TestActor extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher

	override def onReceive(request: Request): Future[Response] = {
		Future(ResponseHandler.OK)
	}
}
