package controllers.v3

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId, ItemSetOperations}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

@Singleton
class ItemSetController @Inject()(@Named(ActorNames.ITEM_SET_ACTOR) itemSetActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

	val objectType = "ItemSet"
	val schemaName: String = "itemset"
	val version = "2.0"

	def create() = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val itemset = body.getOrElse("itemset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
		itemset.putAll(headers)
		val itemSetRequest = getRequest(itemset, headers, ItemSetOperations.createItemSet.toString)
		setRequestContext(itemSetRequest, version, objectType, schemaName)
		getResult(ApiId.CREATE_ITEM_SET, itemSetActor, itemSetRequest)
	}

	def read(identifier: String, fields: Option[String]) = Action.async { implicit request =>
		val headers = commonHeaders()
		val itemset = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
		itemset.putAll(headers)
		itemset.putAll(Map("identifier" -> identifier, "fields" -> fields.getOrElse("")).asInstanceOf[Map[String, Object]])
		val itemSetRequest = getRequest(itemset, headers, ItemSetOperations.readItemSet.toString)
		setRequestContext(itemSetRequest, version, objectType, schemaName)
		getResult(ApiId.READ_ITEM_SET, itemSetActor, itemSetRequest)
	}

	def update(identifier: String) = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val itemset = body.getOrElse("itemset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
		itemset.putAll(headers)
		val itemSetRequest = getRequest(itemset, headers, ItemSetOperations.updateItemSet.toString)
		setRequestContext(itemSetRequest, version, objectType, schemaName)
		itemSetRequest.getContext.put("identifier", identifier);
		getResult(ApiId.UPDATE_ITEM_SET, itemSetActor, itemSetRequest)
	}

	def review(identifier: String) = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val itemset = body.getOrElse("itemset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
		itemset.putAll(headers)
		val itemSetRequest = getRequest(itemset, headers, ItemSetOperations.reviewItemSet.toString)
		setRequestContext(itemSetRequest, version, objectType, schemaName)
		itemSetRequest.getContext.put("identifier", identifier);
		getResult(ApiId.REVIEW_ITEM_SET, itemSetActor, itemSetRequest)
	}

	def retire(identifier: String) = Action.async { implicit request =>
		val headers = commonHeaders()
		val itemset = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
		itemset.putAll(headers)
		val itemSetRequest = getRequest(itemset, headers, ItemSetOperations.retireItemSet.toString)
		setRequestContext(itemSetRequest, version, objectType, schemaName)
		itemSetRequest.getContext.put("identifier", identifier)
		getResult(ApiId.RETIRE_ITEM_SET, itemSetActor, itemSetRequest)
	}
}
