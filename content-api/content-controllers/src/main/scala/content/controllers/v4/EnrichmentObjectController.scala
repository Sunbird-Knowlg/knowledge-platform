package content.controllers.v4

import org.apache.pekko.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import content.controllers.BaseController
import content.utils.{ActorNames, ApiId}

import javax.inject.{Inject, Named}
import scala.concurrent.ExecutionContext

/**
 * Exposes the EnrichmentObject APIs — create, list, update, upload, approve, and
 * reject — as flat, category-agnostic HTTP endpoints under `/object/enrichment`.
 */
@Singleton
class EnrichmentObjectController @Inject() (@Named(ActorNames.ENRICHMENT_OBJECT_ACTOR) enrichmentObjectActor: ActorRef, cc: play.api.mvc.ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

    val objectType = "EnrichmentObject"
    val schemaName: String = "enrichmentobject"
    val version = "1.0"
    val apiVersion = "4.0"

    /**
     * Creates a new EnrichmentObject, or returns an existing match, from the payload
     * under the request's `enrichmentObject` key.
     *
     * @return the created or matched EnrichmentObject
     */
    def create() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body.getOrDefault("enrichmentObject", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        content.putAll(headers)
        val enrichmentRequest = getRequest(content, headers, "createEnrichmentObject")
        setRequestContext(enrichmentRequest, version, objectType, schemaName)
        getResult(ApiId.CREATE_ENRICHMENT_OBJECT, enrichmentObjectActor, enrichmentRequest, version = apiVersion)
    }

    /**
     * Lists EnrichmentObjects matching the filter under the request's `enrichmentObject`
     * key, which must include `parentId`.
     *
     * @return the matching EnrichmentObjects and their count
     */
    def list() = Action.async { implicit request =>
        val body = requestBody()
        val content = body.getOrDefault("enrichmentObject", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        val enrichmentRequest = getRequest(content, commonHeaders(), "listEnrichmentObject")
        setRequestContext(enrichmentRequest, version, objectType, schemaName)
        getResult(ApiId.LIST_ENRICHMENT_OBJECT, enrichmentObjectActor, enrichmentRequest, version = apiVersion)
    }

    /**
     * Writes fields onto an existing EnrichmentObject, from the payload under the
     * request's `enrichmentObject` key.
     *
     * @param identifier the EnrichmentObject to update
     * @return identifier plus the fields that were written
     */
    def update(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body.getOrDefault("enrichmentObject", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        content.putAll(headers)
        val enrichmentRequest = getRequest(content, headers, "updateEnrichmentObject")
        setRequestContext(enrichmentRequest, version, objectType, schemaName)
        enrichmentRequest.getContext.put("identifier", identifier)
        getResult(ApiId.UPDATE_ENRICHMENT_OBJECT, enrichmentObjectActor, enrichmentRequest, version = apiVersion)
    }
}
