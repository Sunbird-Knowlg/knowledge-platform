package modules

import com.google.inject.AbstractModule
import org.apache.pekko.actor.Props
import org.sunbird.actor.core.BaseActor
import org.sunbird.actors.ObjectCategoryActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import play.api.libs.concurrent.PekkoGuiceSupport
import taxonomy.utils.ActorNames

import scala.concurrent.{ExecutionContext, Future}

class TestModule extends AbstractModule with PekkoGuiceSupport {
	override def configure(): Unit = {
		bindActor(ActorNames.HEALTH_ACTOR, _ => Props(classOf[TestActor]))
		bindActor(ActorNames.OBJECT_CATEGORY_ACTOR, _ => Props(classOf[TestActor]))
		bindActor(ActorNames.OBJECT_CATEGORY_DEFINITION_ACTOR, _ => Props(classOf[TestActor]))
		bindActor(ActorNames.FRAMEWORK_ACTOR, _ => Props(classOf[TestActor]))
		bindActor(ActorNames.CATEGORY_ACTOR, _ => Props(classOf[TestActor]))
		bindActor(ActorNames.CATEGORY_INSTANCE_ACTOR, _ => Props(classOf[TestActor]))
		bindActor(ActorNames.TERM_ACTOR, _ => Props(classOf[TestActor]))
		bindActor(ActorNames.LOCK_ACTOR, _ => Props(classOf[TestActor]))
		println("Test Module is initialized...")
	}
}

class TestActor extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher

	override def onReceive(request: Request): Future[Response] = {
		Future(ResponseHandler.OK)
	}
}
