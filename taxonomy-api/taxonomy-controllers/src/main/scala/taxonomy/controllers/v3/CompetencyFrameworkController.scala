package taxonomy.controllers.v3

import scala.concurrent.ExecutionContext

import org.apache.pekko.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import javax.inject.{Inject, Named}
import play.api.mvc.ControllerComponents
import taxonomy.utils.{ActorNames, ApiId, Constants}

@Singleton
class CompetencyFrameworkController @Inject()(@Named(ActorNames.FRAMEWORK_ACTOR) frameworkActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends FrameworkControllerBase(frameworkActor, cc) {

    override protected val objectType = "CompetencyFramework"
    override protected val schemaName = Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME
    override protected val bodyKey = Constants.COMPETENCY_FRAMEWORK

    def createCompetencyFramework() = doCreate(ApiId.CREATE_COMPETENCY_FRAMEWORK)

    def readCompetencyFramework(identifier: String, fields: Option[String], categories: Option[String]) = doRead(identifier, fields, categories, ApiId.READ_COMPETENCY_FRAMEWORK)

    def updateCompetencyFramework(identifier: String) = doUpdate(identifier, ApiId.UPDATE_COMPETENCY_FRAMEWORK)

    def retire(identifier: String) = doDispatch(identifier, Constants.RETIRE_FRAMEWORK, ApiId.RETIRE_COMPETENCY_FRAMEWORK, identifierInContext = true)

    def publish(identifier: String) = doDispatch(identifier, Constants.PUBLISH_FRAMEWORK, ApiId.PUBLISH_COMPETENCY_FRAMEWORK, identifierInContext = false)

    def review(identifier: String) = doDispatch(identifier, Constants.REVIEW_FRAMEWORK, ApiId.REVIEW_COMPETENCY_FRAMEWORK, identifierInContext = false)

    def reject(identifier: String) = doDispatch(identifier, Constants.REJECT_FRAMEWORK, ApiId.REJECT_COMPETENCY_FRAMEWORK, identifierInContext = false)
}
