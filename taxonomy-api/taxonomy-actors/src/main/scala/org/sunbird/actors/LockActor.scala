package org.sunbird.actors

import org.sunbird.actor.core.BaseActor
import org.sunbird.graph.OntologyEngineContext
import org.apache.commons.lang3.StringUtils

import java.sql.Timestamp
import java.util
import java.util.{Date, UUID}
import scala.collection.JavaConversions._
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException,ServerException}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.schema.SchemaValidatorFactory
import org.sunbird.utils.Constants
import java.util.concurrent.CompletionException
import javax.inject.Inject
import scala.collection.immutable.{List, Map}
import scala.concurrent.{ExecutionContext, Future}

class LockActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor{
  implicit val ec: ExecutionContext = getContext().dispatcher
  private val basePath = if (Platform.config.hasPath("lockSchema.base_path")) Platform.config.getString("lockSchema.base_path")
  else "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/schemas/local/"

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case Constants.CREATE_LOCK => create(request)

      case _ => ERROR(request.getOperation)
    }
  }

  @throws[Exception]
  private def create(request: Request) = {
    val defaultLockExpiryTime = 3600
    val lockId = UUID.randomUUID()
    val newDateObj = createExpiryTime(defaultLockExpiryTime)
    // RequestUtil.restrictProperties(request)
    val resourceId = request.getRequest.getOrDefault(Constants.RESOURCE_ID, "").asInstanceOf[String]
    if(!request.getRequest.containsKey(Constants.X_DEVICE_ID)) throw new ClientException("ERR_LOCK_CREATION_FAILED", "X-device-Id is missing in headers")
    if(!request.getRequest.containsKey(Constants.X_AUTHENTICATED_USER_ID)) throw new ClientException("ERR_LOCK_CREATION_FAILED", "You are not authorized to lock this resource")
    if(request.getRequest.isEmpty) throw new ClientException("ERR_LOCK_CREATION_FIELDS_MISSING","Error due to required request is missing")
    val schemaValidator = SchemaValidatorFactory.getInstance(basePath)
    schemaValidator.validate(request.getRequest)
    validateResource(request).flatMap( res =>{
      request.put("identifier", resourceId)
      val externalProps = DefinitionNode.getExternalProps("domain", "1.0", "lock")
      // val future: Future[Response] = oec.graphService.readExternalProps(request, List("lockId","resourceId","resourceType","resourceInfo","creatorInfo","createdBy","deviceId","expiresAt","createdOn"))
      println("externalProps - lock: " + externalProps)
      oec.graphService.readExternalProps(request, externalProps).map(response => {
        if (!ResponseHandler.checkError(response)) {
              val lockId = response.getResult.toMap.getOrDefault("lockid", "")
              val createdBy = response.getResult.toMap.getOrDefault("createdby", "")
              val expiresAt = response.getResult.toMap.getOrDefault("expiresat", "")
              val deviceId = response.getResult.toMap.getOrDefault("deviceid", "")
              val resourceType = response.getResult.toMap.getOrDefault("resourcetype", "")
              if(request.getRequest.get("userId")==createdBy && request.getRequest.get("deviceId")==deviceId && request.getRequest.get("resourceType")==resourceType){
                    Future {
                      ResponseHandler.OK.put("lockKey", lockId).put("expiresAt", expiresAt).put("expiresIn", defaultLockExpiryTime / 60)
                    }
              }
              else if (request.getRequest.get("userId") == createdBy)
                    throw new ClientException("RESOURCE_SELF_LOCKED", "Error due to self lock , Resource already locked by user ")
              else {
                    println("creatorInfo: " + response.getResult.toMap.get("creatorinfo"))
                      val userName = response.getResult.toMap.get("creatorinfo").flatMap { creatorInfo =>
                      val creatorInfoMap = JsonUtils.convertJSONString(creatorInfo.asInstanceOf[String]).asInstanceOf[Map[String, String]]
                      creatorInfoMap.get("name")
                    }.getOrElse("another user")
                    throw new ClientException("RESOURCE_LOCKED", s"The resource is already locked by $userName ")
              }
        }
        else {
              request.getRequest.remove("resourceId")
              request.getRequest.remove("userId")
              request.put("lockid", lockId)
              request.put("expiresat", newDateObj)
              request.put("createdon", new Timestamp(new Date().getTime))
              oec.graphService.saveExternalPropsWithTtl(request, defaultLockExpiryTime).flatMap { response =>
                if (ResponseHandler.checkError(response)) {
                      throw new ServerException("ERR_WHILE_SAVING_TO_CASSANDRA", "Error while saving external props to Cassandra")
                }
                else {
                      Future {
                        ResponseHandler.OK.put("lockKey", lockId).put("expiresAt", newDateObj).put("expiresIn", defaultLockExpiryTime / 60)
                      }
                }
              }
        }
      }).flatMap( f => f).recoverWith { case e: CompletionException => throw e.getCause }
    })
  }

  private def validateResource(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
    val resourceId = request.getRequest.getOrDefault(Constants.RESOURCE_ID, "").asInstanceOf[String]
    if (resourceId.isEmpty) throw new ClientException("ERR_INVALID_RESOURCE_ID", s"Invalid resourceId: '${resourceId}' ")
    val getRequest = new Request()
    getRequest.setContext(new util.HashMap[String, AnyRef]() {
      {
        putAll(request.getContext)
      }
    })
    getRequest.getContext.put(Constants.SCHEMA_NAME, Constants.LOCK_SCHEMA_NAME)
    getRequest.getContext.put(Constants.VERSION, Constants.LOCK_SCHEMA_VERSION)
    getRequest.put(Constants.IDENTIFIER, resourceId)
    DataNode.read(getRequest)(oec, ec).map(node => {
      if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, resourceId)) node
      else
        throw new ClientException("ERR_RESOURCE_NOT_FOUND", "Error as resource validation failed ")
    })(ec)
  }

  def createExpiryTime(defaultLockExpiryTime: Int): Date = {
    val expiryTimeMillis = System.currentTimeMillis() + (defaultLockExpiryTime * 1000)
    new Date(expiryTimeMillis)
  }

}
