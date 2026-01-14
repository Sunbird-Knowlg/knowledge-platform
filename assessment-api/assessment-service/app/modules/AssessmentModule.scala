package modules

import com.google.inject.AbstractModule
import play.api.libs.concurrent.PekkoGuiceSupport
import org.sunbird.actors.{AssessmentItemActor, HealthActor, ItemSetActor, QuestionActor, QuestionSetActor}
import org.sunbird.v5.actors.{QuestionActor => V5QuestionActor, QuestionSetActor => V5QuestionSetActor}
import utils.ActorNames

class AssessmentModule extends AbstractModule with PekkoGuiceSupport {

    override def configure() = {
//        super.configure()
        bindActor[HealthActor](ActorNames.HEALTH_ACTOR)
        bindActor[ItemSetActor](ActorNames.ITEM_SET_ACTOR)
        bindActor[AssessmentItemActor](ActorNames.ASSESSMENT_ITEM_ACTOR)
        bindActor[QuestionActor](ActorNames.QUESTION_ACTOR)
        bindActor[QuestionSetActor](ActorNames.QUESTION_SET_ACTOR)
        bindActor[V5QuestionActor](ActorNames.QUESTION_V5_ACTOR)
        bindActor[V5QuestionSetActor](ActorNames.QUESTION_SET_V5_ACTOR)
        println("Initialized application actors for assessment-service")
    }
}
