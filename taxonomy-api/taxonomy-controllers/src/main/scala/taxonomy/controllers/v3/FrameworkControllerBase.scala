package taxonomy.controllers.v3

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

import org.apache.pekko.actor.ActorRef
import play.api.mvc.ControllerComponents
import taxonomy.controllers.BaseController
import taxonomy.utils.Constants

// Shared action bodies for FrameworkController and CompetencyFrameworkController: both route to the
// same FrameworkActor and differ only in objectType/schemaName/bodyKey literals, supplied by each subclass.
abstract class FrameworkControllerBase(frameworkActor: ActorRef, cc: ControllerComponents)(implicit exec: ExecutionContext) extends BaseController(cc) {

    protected val objectType: String
    protected val schemaName: String
    protected val bodyKey: String

    protected def doCreate(apiId: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val framework = body.getOrDefault(bodyKey, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        framework.putAll(headers)
        val frameworkRequest = getRequest(framework, headers, Constants.CREATE_FRAMEWORK)
        setRequestContext(frameworkRequest, Constants.FRAMEWORK_SCHEMA_VERSION, objectType, schemaName)
        getResult(apiId, frameworkActor, frameworkRequest)
    }

    protected def doRead(identifier: String, fields: Option[String], categories: Option[String], apiId: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val framework = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
        framework.putAll(headers)
        framework.putAll(Map(Constants.IDENTIFIER -> identifier, Constants.CATEGORIES -> categories.getOrElse("")).asJava)
        val readRequest = getRequest(framework, headers, Constants.READ_FRAMEWORK)
        setRequestContext(readRequest, Constants.FRAMEWORK_SCHEMA_VERSION, objectType, schemaName)
        getResult(apiId, frameworkActor, readRequest)
    }

    protected def doDispatch(identifier: String, operation: String, apiId: String, identifierInContext: Boolean) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val framework = body.getOrDefault(bodyKey, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        framework.putAll(headers)
        if (!identifierInContext) framework.putAll(Map("identifier" -> identifier).asJava)
        val frameworkRequest = getRequest(framework, headers, operation)
        setRequestContext(frameworkRequest, Constants.FRAMEWORK_SCHEMA_VERSION, objectType, schemaName)
        if (identifierInContext) frameworkRequest.getContext.put(Constants.IDENTIFIER, identifier)
        getResult(apiId, frameworkActor, frameworkRequest)
    }

    protected def doUpdate(identifier: String, apiId: String) = doDispatch(identifier, Constants.UPDATE_FRAMEWORK, apiId, identifierInContext = true)
}
