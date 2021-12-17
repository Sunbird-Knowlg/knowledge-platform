package modules

import com.google.inject.AbstractModule
import org.sunbird.actors.{HealthActor, ItemSetActor, QuestionActor, QuestionSetActor}
import play.libs.akka.AkkaGuiceSupport
import utils.ActorNames

class AssessmentModule extends AbstractModule with AkkaGuiceSupport {

    override def configure() = {
//        super.configure()
        bindActor(classOf[HealthActor], ActorNames.HEALTH_ACTOR)
        bindActor(classOf[ItemSetActor], ActorNames.ITEM_SET_ACTOR)
        bindActor(classOf[QuestionActor], ActorNames.QUESTION_ACTOR)
        bindActor(classOf[QuestionSetActor], ActorNames.QUESTION_SET_ACTOR)
        println("Initialized application actors for assessment-service")
    }
}
