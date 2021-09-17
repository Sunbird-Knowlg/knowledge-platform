package controllers

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Inject
import com.google.inject.name.Named
import handlers.LoggingAction
import managers.SearchManager
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import scala.concurrent.ExecutionContext

class SearchController @Inject()(@Named(ActorNames.SEARCH_ACTOR) searchActor: ActorRef, loggingAction: LoggingAction, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends SearchBaseController(cc) {

    val mgr: SearchManager = new SearchManager()

    def search() = loggingAction.async { implicit request =>
        val internalReq = getRequest(ApiId.APPLICATION_SEARCH)
        setHeaderContext(internalReq)
        getResult(mgr.search(internalReq, searchActor), ApiId.APPLICATION_SEARCH)
    }

    def privateSearch() = loggingAction.async { implicit request =>
        val internalReq = getRequest(ApiId.APPLICATION_PRIVATE_SEARCH)
        setHeaderContext(internalReq)
        getResult(mgr.search(internalReq, searchActor), ApiId.APPLICATION_PRIVATE_SEARCH)
    }

    def count() = Action.async { implicit request =>
        val internalReq = getRequest(ApiId.APPLICATION_COUNT)
        setHeaderContext(internalReq)
        getResult(mgr.count(internalReq, searchActor), ApiId.APPLICATION_COUNT)
    }
}
