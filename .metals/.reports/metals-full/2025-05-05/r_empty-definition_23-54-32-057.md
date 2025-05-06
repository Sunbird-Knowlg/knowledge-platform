error id: `<none>`.
file://<WORKSPACE>/content-api/content-service/app/controllers/v3/CategoryController.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -scala/collection/JavaConverters.setRequestContext.
	 -scala/collection/JavaConverters.setRequestContext#
	 -scala/collection/JavaConverters.setRequestContext().
	 -setRequestContext.
	 -setRequestContext#
	 -setRequestContext().
	 -scala/Predef.setRequestContext.
	 -scala/Predef.setRequestContext#
	 -scala/Predef.setRequestContext().
offset: 1691
uri: file://<WORKSPACE>/content-api/content-service/app/controllers/v3/CategoryController.scala
text:
```scala
package controllers.v3

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import org.sunbird.content.util.CategoryConstants
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

@Singleton
class CategoryController @Inject()(@Named(ActorNames.CATEGORY_ACTOR) categoryActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

    val objectType = "Category"
    val schemaName: String = "category"
    val version = "1.0"

    def create() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val category = body.getOrDefault("category", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        category.putAll(headers)
        val categoryRequest = getRequest(category, headers, CategoryConstants.CREATE_CATEGORY)
        setRequestContext(categoryRequest, version, objectType, schemaName)
        getResult(ApiId.CREATE_CATEGORY, categoryActor, categoryRequest)
    }

    def read(identifier: String, fields: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val category = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
        category.putAll(headers)
        category.putAll(Map("identifier" -> identifier, "fields" -> fields.getOrElse("")).asJava)
        val categoryRequest = getRequest(category, headers,  CategoryConstants.READ_CATEGORY)
        setReque@@stContext(categoryRequest, version, objectType, schemaName)
        getResult(ApiId.READ_CATEGORY, categoryActor, categoryRequest)
    }

    def update(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val category = body.getOrDefault("category", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        category.putAll(headers)
        val categoryRequest = getRequest(category, headers,  CategoryConstants.UPDATE_CATEGORY)
        setRequestContext(categoryRequest, version, objectType, schemaName)
        categoryRequest.getContext.put("identifier", identifier)
        getResult(ApiId.UPDATE_CATEGORY, categoryActor, categoryRequest)
    }

    def retire(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val category = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
        category.putAll(headers)
        val categoryRequest = getRequest(category, headers,  CategoryConstants.RETIRE_CATEGORY)
        setRequestContext(categoryRequest, version, objectType, schemaName)
        categoryRequest.getContext.put("identifier", identifier)
        getResult(ApiId.RETIRE_CATEGORY, categoryActor, categoryRequest)
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.