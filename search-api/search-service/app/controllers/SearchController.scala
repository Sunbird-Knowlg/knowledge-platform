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
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class SearchController @Inject()(@Named(ActorNames.SEARCH_ACTOR) searchActor: ActorRef, loggingAction: LoggingAction, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends SearchBaseController(cc) {

    val mgr: SearchManager = new SearchManager()

    def search() = loggingAction.async { implicit request =>
        val internalReq = getRequest(ApiId.APPLICATION_SEARCH)
        setHeaderContext(internalReq)
        val filters = internalReq.getRequest.getOrDefault(SearchConstants.filters, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        val visibility: util.ArrayList[String] = filters.getOrDefault("visibility",new util.ArrayList[String]).asInstanceOf[util.ArrayList[String]]
        if (visibility.contains("Private")) {
            getErrorResponse(ApiId.APPLICATION_SEARCH, "3.0", "ERR_ACCESS_DENIED", "Cannot access private content through public search api")
        }
        else {
            if (visibility.isEmpty) {
                filters.putAll(Map("visibility" -> "Default").asJava)
            }
            internalReq.getContext.put("filters", filters)
            getResult(mgr.search(internalReq, searchActor), ApiId.APPLICATION_SEARCH)
        }
    }

    def privateSearch() = loggingAction.async { implicit request =>
        val internalReq = getRequest(ApiId.APPLICATION_PRIVATE_SEARCH)
        setHeaderContext(internalReq)
        val channel = internalReq.getContext.getOrDefault("CHANNEL_ID", "").asInstanceOf[String]
        if(!channel.isBlank) {
            val filters = internalReq.getRequest.getOrDefault(SearchConstants.filters,"").asInstanceOf[java.util.Map[String, Object]]
            filters.putAll(Map("channel" -> channel).asJava)
            internalReq.getContext.put("filters", filters)
        }
        getResult(mgr.search(internalReq, searchActor), ApiId.APPLICATION_PRIVATE_SEARCH)
    }

    def count() = Action.async { implicit request =>
        val internalReq = getRequest(ApiId.APPLICATION_COUNT)
        setHeaderContext(internalReq)
        getResult(mgr.count(internalReq, searchActor), ApiId.APPLICATION_COUNT)
    }
}
