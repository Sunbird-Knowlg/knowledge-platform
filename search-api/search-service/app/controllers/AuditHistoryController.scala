package controllers

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Inject
import com.google.inject.name.Named
import handlers.LoggingAction
import managers.SearchManager
import org.sunbird.search.util.SearchConstants
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import java.util
import org.apache.commons.lang3.StringUtils
import org.sunbird.graph.common.enums.AuditProperties
import org.sunbird.telemetry.logger.TelemetryManager

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class AuditHistoryController @Inject()(@Named(ActorNames.AUDIT_HISTORY_ACTOR) auditHistoryActor: ActorRef, loggingAction: LoggingAction, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends SearchBaseController(cc) {

  val apiVersion = "3.0"
  val traversal = java.lang.Boolean.TRUE
  val mgr: SearchManager = new SearchManager()

  def readAuditHistory(objectId: String, graphId: String) = loggingAction.async { implicit request =>
    val internalReq = getRequest(ApiId.APPLICATION_AUDIT_HISTORY)
    setHeaderContext(internalReq)
    val sortBy = new util.HashMap[String, String]
    sortBy.put(AuditProperties.createdOn.name(), "desc")
    sortBy.put(AuditProperties.operation.name(), "desc")
    val filters = internalReq.getRequest.getOrDefault(SearchConstants.filters, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    filters.putAll(Map(SearchConstants.graphId -> graphId , SearchConstants.objectId -> objectId).asJava)
    internalReq.getRequest.put(SearchConstants.filters, filters)
    internalReq.getRequest.putAll(Map(SearchConstants.fields -> setSearchCriteria(apiVersion), SearchConstants.sort_by -> sortBy ).asJava)
    internalReq.getRequest.put(SearchConstants.traversal, traversal)
    getResult(mgr.getAuditHistory(internalReq, auditHistoryActor), ApiId.APPLICATION_AUDIT_HISTORY)
  }

  def setSearchCriteria(versionId: String): util.List[String] = setSearchCriteria(versionId, false)
  def setSearchCriteria(versionId: String, returnAllFields: Boolean): util.List[String] = {
    val fields = new util.ArrayList[String]
    fields.add("audit_id")
    fields.add("label")
    fields.add("objectId")
    fields.add("objectType")
    fields.add("operation")
    fields.add("requestId")
    fields.add("userId")
    fields.add("graphId")
    fields.add("createdOn")
    if (returnAllFields) {
      fields.add("logRecord")
      fields.add("summary")
    }
    else if (StringUtils.equalsIgnoreCase("3.0", versionId)) fields.add("logRecord")
    else fields.add("summary")
    TelemetryManager.log("returning the search criteria fields: " + fields.size)
    fields
  }
}
