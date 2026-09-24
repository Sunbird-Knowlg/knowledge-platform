package taxonomy.controllers.v3

import org.apache.pekko.actor.{ActorRef, ActorSystem}
import org.sunbird.common.Platform
import org.sunbird.common.exception.ClientException
import play.api.mvc.{AnyContent, ControllerComponents, Request}
import taxonomy.controllers.BaseController
import taxonomy.utils.{ActorNames, ApiId, Constants}

import java.io.File
import javax.inject.{Inject, Named}
import scala.concurrent.ExecutionContext

class FrameworkTermController @Inject()(@Named(ActorNames.TERM_ACTOR) termActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val objectType = "Term"
  def createFrameworkTerm(framework: String, category: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    body.put(Constants.FRAMEWORK, framework)
    body.put(Constants.CATEGORY, category)
    body.putAll(headers)
    val termRequest = getRequest(body, headers, Constants.CREATE_TERM)
    setRequestContext(termRequest, Constants.TERM_SCHEMA_VERSION, objectType, Constants.TERM_SCHEMA_NAME)
    getResult(ApiId.CREATE_TERM, termActor, termRequest)
  }

  def readFrameworkTerm(termId: String, framework: String, category: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val term = body.getOrDefault(Constants.TERM, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    term.put(Constants.TERM, termId)
    term.put(Constants.CATEGORY, category)
    term.put(Constants.FRAMEWORK, framework)
    term.putAll(headers)
    val readTermRequest = getRequest(term, headers, Constants.READ_TERM)
    setRequestContext(readTermRequest, Constants.TERM_SCHEMA_VERSION, objectType, Constants.TERM_SCHEMA_NAME)
    getResult(ApiId.READ_TERM, termActor, readTermRequest)
  }

  def updateFrameworkTerm(termId: String, framework: String, category: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val term = body.getOrDefault(Constants.TERM, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    term.put(Constants.TERM, termId)
    term.put(Constants.CATEGORY, category)
    term.put(Constants.FRAMEWORK, framework)
    term.putAll(headers)
    val termRequest = getRequest(term, headers, Constants.UPDATE_TERM)
    setRequestContext(termRequest, Constants.TERM_SCHEMA_VERSION, objectType, Constants.TERM_SCHEMA_NAME)
    termRequest.getContext.put(Constants.TERM, termId)
    getResult(ApiId.UPDATE_TERM, termActor, termRequest)
  }

  def retireFrameworkTerm(termId: String, framework: String, category: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val term = body.getOrDefault(Constants.TERM, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    term.put(Constants.TERM, termId)
    term.put(Constants.CATEGORY, category)
    term.put(Constants.FRAMEWORK, framework)
    term.putAll(headers)
    val termRequest = getRequest(term, headers, Constants.RETIRE_TERM)
    setRequestContext(termRequest, Constants.TERM_SCHEMA_VERSION, objectType, Constants.TERM_SCHEMA_NAME)
    termRequest.getContext.put(Constants.TERM, termId)
    getResult(ApiId.RETIRE_TERM, termActor, termRequest)
  }

  def bulkUpdateTerm() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    body.putAll(headers)
    val termRequest = getRequest(body, headers, Constants.BULK_UPDATE_TERM)
    setRequestContext(termRequest, Constants.TERM_SCHEMA_VERSION, objectType, Constants.TERM_SCHEMA_NAME)
    getResult(ApiId.BULK_UPDATE_TERM, termActor, termRequest)
  }

  private def requestMultipartFormData()(implicit request: Request[AnyContent]): java.util.Map[String, Object] = {
    val reqMap = new java.util.HashMap[String, Object]()
    request.body.asMultipartFormData.foreach { multipartData =>
      multipartData.asFormUrlEncoded.foreach { case (key, values) => if (values.nonEmpty) reqMap.put(key, values.head) }
      if (multipartData.files.nonEmpty) {
        val filePart = multipartData.files.head
        val tempLocation = Platform.getString("competencyframework.upload.temp_location", "/tmp/competencyframework")
        new File(tempLocation).mkdirs()
        val safeName = filePart.filename
        val file = new File(tempLocation + File.separator + System.currentTimeMillis + "_" + safeName)
        filePart.ref.copyTo(file, replace = false)
        reqMap.put("file", file)
        reqMap.put("fileName", safeName)
      }
    }
    if (reqMap.containsKey("file")) reqMap
    else throw new ClientException("ERR_INVALID_DATA", "Please provide a valid file.")
  }

  def bulkValidateTerm() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestMultipartFormData()
    body.putAll(headers)
    val termRequest = getRequest(body, headers, Constants.BULK_VALIDATE_TERM)
    setRequestContext(termRequest, Constants.TERM_SCHEMA_VERSION, objectType, Constants.TERM_SCHEMA_NAME)
    getResult(ApiId.BULK_VALIDATE_TERM, termActor, termRequest)
  }

  def bulkCommitTerm() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestMultipartFormData()
    body.putAll(headers)
    val termRequest = getRequest(body, headers, Constants.BULK_COMMIT_TERM)
    setRequestContext(termRequest, Constants.TERM_SCHEMA_VERSION, objectType, Constants.TERM_SCHEMA_NAME)
    getResult(ApiId.BULK_COMMIT_TERM, termActor, termRequest)
  }

  // Plain JSON body, no file -- same shape as bulkUpdateTerm.
  def bulkDownloadTerm() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    body.putAll(headers)
    val termRequest = getRequest(body, headers, Constants.BULK_DOWNLOAD_TERM)
    setRequestContext(termRequest, Constants.TERM_SCHEMA_VERSION, objectType, Constants.TERM_SCHEMA_NAME)
    getResult(ApiId.BULK_DOWNLOAD_TERM, termActor, termRequest)
  }

  // Plain JSON body, no file -- builds and uploads the xlsx template; bulkDownloadTerm only reads it back.
  def bulkTemplateTerm() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    body.putAll(headers)
    val termRequest = getRequest(body, headers, Constants.BULK_TEMPLATE_TERM)
    setRequestContext(termRequest, Constants.TERM_SCHEMA_VERSION, objectType, Constants.TERM_SCHEMA_NAME)
    getResult(ApiId.BULK_TEMPLATE_TERM, termActor, termRequest)
  }

}