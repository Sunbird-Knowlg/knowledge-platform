package modules

import com.google.inject.AbstractModule
import org.apache.pekko.routing.FromConfig
import play.api.libs.concurrent.PekkoGuiceSupport
import org.sunbird.actors.{AssessmentItemActor, HealthActor, ItemSetActor, QuestionActor, QuestionSetActor}
import org.sunbird.v5.actors.{QuestionActor => V5QuestionActor, QuestionSetActor => V5QuestionSetActor}
import assessment.utils.ActorNames

class AssessmentModule extends AbstractModule with PekkoGuiceSupport {

    override def configure() = {
        // $COVERAGE-OFF$ Disabling scoverage as this code is impossible to test
        super.configure()
        // Actors bound with FromConfig take router size/dispatcher from pekko.actor.deployment in application.conf
        bindActor[HealthActor](ActorNames.HEALTH_ACTOR, _.withRouter(FromConfig))
        bindActor[ItemSetActor](ActorNames.ITEM_SET_ACTOR, _.withRouter(FromConfig))
        bindActor[AssessmentItemActor](ActorNames.ASSESSMENT_ITEM_ACTOR, _.withRouter(FromConfig))
        bindActor[QuestionActor](ActorNames.QUESTION_ACTOR, _.withRouter(FromConfig))
        bindActor[QuestionSetActor](ActorNames.QUESTION_SET_ACTOR, _.withRouter(FromConfig))
        bindActor[V5QuestionActor](ActorNames.QUESTION_V5_ACTOR, _.withRouter(FromConfig))
        bindActor[V5QuestionSetActor](ActorNames.QUESTION_SET_V5_ACTOR, _.withRouter(FromConfig))
        println("Initialized application actors for assessment-service")
        // $COVERAGE-ON
    }
}
