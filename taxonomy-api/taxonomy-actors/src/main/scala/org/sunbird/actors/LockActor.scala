package org.sunbird.actors

import org.sunbird.actor.core.BaseActor
import org.sunbird.graph.OntologyEngineContext
import org.apache.commons.lang3.StringUtils
import org.json.JSONObject
import java.sql.Timestamp
import java.util
import java.util.{Date, UUID}
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ServerException}
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

  private val defaultLockExpiryTime = if (Platform.config.hasPath("defaultLockExpiryTime")) Platform.config.getInt("defaultLockExpiryTime")
  else 3600
  val version = "1.0"

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case Constants.CREATE_LOCK => create(request)
      case Constants.REFRESH_LOCK => refresh(request)
      case Constants.RETIRE_LOCK => retire(request)
      case Constants.LIST_LOCK => list(request)
      case _ => ERROR(request.getOperation)
    }
  }

  @throws[Exception]
  private def create(request: Request) = {
    val newDateObj = createExpiryTime()
    val resourceId = request.getRequest.getOrDefault(Constants.RESOURCE_ID, "").asInstanceOf[String]
    if(!request.getRequest.containsKey(Constants.X_DEVICE_ID)) throw new ClientException("ERR_LOCK_CREATION_FAILED", "X-device-Id is missing in headers")
    if(!request.getRequest.containsKey(Constants.X_AUTHENTICATED_USER_ID)) throw new ClientException("ERR_LOCK_CREATION_FAILED", "You are not authorized to lock this resource")
    if(request.getRequest.isEmpty) throw new ClientException("ERR_LOCK_CREATION_FIELDS_MISSING","Error due to required request is missing")
    val schemaValidator = SchemaValidatorFactory.getInstance(basePath)
    schemaValidator.validate(request.getRequest)
    validateResource(request).flatMap( res =>{
      val versionKey: String = res.getMetadata.getOrDefault("versionKey","").asInstanceOf[String]
      val channel: String = res.getMetadata.getOrDefault("channel","").asInstanceOf[String]
      request.put("identifier", resourceId)
      val externalProps = DefinitionNode.getExternalProps("domain", "1.0", "lock")
      oec.graphService.readExternalProps(request, externalProps).map(response => {
        if (!ResponseHandler.checkError(response)) {
              if(request.getRequest.get("userId") == response.getResult.toMap.getOrDefault("createdby", "") &&
                request.getRequest.get("deviceId") == response.getResult.toMap.getOrDefault("deviceid", "") &&
                request.getRequest.get("resourceType") == response.getResult.toMap.getOrDefault("resourcetype", "")){
                    Future {
                      ResponseHandler.OK.put("lockKey", response.getResult.toMap.getOrDefault("lockid", "")).put("expiresAt", response.getResult.toMap.getOrDefault("expiresat", "").toString).put("expiresIn", defaultLockExpiryTime / 60)
                    }
              }
              else if (request.getRequest.get("userId") == response.getResult.toMap.getOrDefault("createdby", ""))
                    throw new ClientException("RESOURCE_SELF_LOCKED", "Error due to self lock , Resource already locked by user ")
              else {
                val creatorInfoStr = response.getResult.get("creatorinfo").asInstanceOf[String]
                val creatorInfoMap = JsonUtils.convertJSONString(creatorInfoStr).asInstanceOf[java.util.Map[String, String]]
                val userName = Option(creatorInfoMap.get("name")).getOrElse("another user")
                throw new ClientException("RESOURCE_LOCKED", s"The resource is already locked by $userName ")
              }
        }
        else {
              val lockId = UUID.randomUUID()
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
                      updateContent(request, channel, res.getGraphId, res.getObjectType, resourceId, versionKey, lockId)
                      Future {
                        ResponseHandler.OK.put("lockKey", lockId).put("expiresAt", newDateObj.toString).put("expiresIn", defaultLockExpiryTime / 60)
                      }
                }
              }
        }
      }).flatMap( f => f).recoverWith { case e: CompletionException => throw e.getCause }
    })
  }

  @throws[Exception]
  private def refresh(request: Request) = {
    val newDateObj = createExpiryTime()
    val resourceId = request.getRequest.getOrDefault(Constants.RESOURCE_ID, "").asInstanceOf[String]
    val userId = request.getRequest.getOrDefault(Constants.X_AUTHENTICATED_USER_ID, "").asInstanceOf[String]
    if(!request.getRequest.containsKey(Constants.X_DEVICE_ID)) throw new ClientException("ERR_LOCK_CREATION_FAILED", "X-device-Id is missing in headers")
    if (!(request.getRequest.containsKey("resourceType") && request.getRequest.containsKey("resourceId") &&
      request.getRequest.containsKey("lockId"))) throw new ClientException("ERR_LOCK_CREATION_FIELDS_MISSING", "Error due to required request is missing")
    validateResource(request).flatMap( res =>{
          val contentLockId = res.getMetadata.get("lockKey")
          if(request.getRequest.getOrDefault("lockId", " ") != contentLockId)
            throw new ClientException("RESOURCE_LOCKED", "Lock key and request lock key does not match")
          request.put("identifier", resourceId)
          val externalProps = DefinitionNode.getExternalProps("domain", "1.0", "lock")
      // TODO : update proper username
      val creatorInfo = new JSONObject(Map("name" -> "username", "id" -> userId).asJava).toString()
      val resourceInfo = new JSONObject(Map("contentType" -> res.getMetadata.getOrDefault("contentType", ""), "framework" -> res.getMetadata.getOrDefault("framework", ""),
        "identifier" -> resourceId, "mimeType" -> res.getMetadata.getOrDefault("mimeType", "")).asJava).toString()
          oec.graphService.readExternalProps(request, externalProps).map(response => {
            if (ResponseHandler.checkError(response)) {
              val createLockReq = request
              createLockReq.put("resourceInfo", resourceInfo)
              createLockReq.put("creatorInfo", creatorInfo)
              createLockReq.put("createdBy", userId)
              if (contentLockId == request.getRequest.getOrDefault("lockId", "")) {
                createLockReq.getRequest.remove("lockId")
                create(createLockReq)
              }
              else throw new ClientException("ERR_LOCK_REFRESHING_FAILED", "no data found from db for refreshing lock")
            }
            else {
              val lockId = response.getResult.toMap.getOrDefault("lockid", "")
              val createdBy = response.getResult.toMap.getOrDefault("createdby", "")
              if (createdBy != userId)
                throw new ClientException("ERR_LOCK_REFRESHING_FAILED", "Unauthorized to refresh this lock")
              request.put("fields", List("expiresat"))
              request.put("values", List(newDateObj))
              oec.graphService.updateExternalPropsWithTtl(request, defaultLockExpiryTime).flatMap { response =>
                if (ResponseHandler.checkError(response))
                  throw new ServerException("ERR_WHILE_UPDAING_TO_CASSANDRA", "Error while updating external props to Cassandra")
                else {
                  Future {
                    ResponseHandler.OK.put("lockKey", lockId).put("expiresAt", newDateObj.toString).put("expiresIn", defaultLockExpiryTime / 60)
                  }
                }
              }
            }
          }).flatMap( f => f).recoverWith { case e: CompletionException => throw e.getCause }
    }).recoverWith { case e: CompletionException => throw e.getCause }
  }

  @throws[Exception]
  private def retire(request: Request) = {
    val resourceId = request.getRequest.getOrDefault(Constants.RESOURCE_ID, "").asInstanceOf[String]
    val userId = request.getRequest.getOrDefault(Constants.X_AUTHENTICATED_USER_ID, "").asInstanceOf[String]
    if(!request.getRequest.containsKey(Constants.X_DEVICE_ID)) throw new ClientException("ERR_LOCK_CREATION_FAILED", "X-device-Id is missing in headers")
    if(!(request.getRequest.containsKey("resourceType") && request.getRequest.containsKey("resourceId"))) throw new ClientException("ERR_LOCK_CREATION_FIELDS_MISSING","Error due to required request is missing")
    validateResource(request).flatMap( res =>{
      request.put("identifier", resourceId)
      val externalProps = DefinitionNode.getExternalProps("domain", "1.0", "lock")
      oec.graphService.readExternalProps(request, externalProps).map(response => {
        if (!ResponseHandler.checkError(response)) {
          val createdBy = response.getResult.toMap.getOrDefault("createdby", "")
          if (createdBy != userId)
            throw new ClientException("ERR_LOCK_RETIRING_FAILED", "Unauthorized to retire lock")
          request.put("identifiers", List(resourceId))
          oec.graphService.deleteExternalProps(request) flatMap { response =>
            if (ResponseHandler.checkError(response))
              throw new ServerException("ERR_WHILE_DELETING_FROM_CASSANDRA", "Error while deleting external props from Cassandra")
            else {
              Future {
                ResponseHandler.OK()
              }
            }
          }
        }
        else throw new ClientException("ERR_LOCK_RETIRING_FAILED","no data found from db for retiring lock")
      }).flatMap( f => f).recoverWith { case e: CompletionException => throw e.getCause }
    }).recoverWith { case e: CompletionException => throw e.getCause }
  }

  @throws[Exception]
  private def list(request: Request) = {
    val filters = request.getRequest.getOrDefault("filters", new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
    val resourceId = filters.getOrDefault("resourceId", "")
    if (!request.getRequest.containsKey(Constants.X_DEVICE_ID))
      throw new ClientException("ERR_LOCK_CREATION_FAILED", "X-device-Id is missing in headers")
    if (resourceId.isInstanceOf[String]) {
      request.getRequest.put("identifier", resourceId)
    }
    else {
        request.getRequest.put("identifiers", resourceId.asInstanceOf[java.util.List[String]].toList)
      }
    val externalProps = DefinitionNode.getExternalProps("domain", "1.0", "lock")
    oec.graphService.readExternalProps(request, externalProps).map(response => {
      if (!ResponseHandler.checkError(response)) {
        Future {
          ResponseHandler.OK.put("count",  response.getResult.size()).put("data", response.getResult.values())
        }
      }
      else throw new ClientException("ERR_LOCK_LISTING_FAILED","error while fetching lock list data from db")
    }).flatMap( f => f).recoverWith { case e: CompletionException => throw e.getCause }
  }

  def updateContent(request : Request, channel: String, graphId: String, objectType: String, resourceId: String, versionKey: String, lockId: UUID)(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
    val contentUpdateReq = new Request()
    contentUpdateReq.setContext(new util.HashMap[String, AnyRef]() {
      {
        putAll(request.getContext)
      }
    })
    contentUpdateReq.getContext.put("graph_id", graphId)
    contentUpdateReq.getContext.put("objectType", objectType)
    contentUpdateReq.getContext.put("channel", channel)
    contentUpdateReq.getContext.put(Constants.SCHEMA_NAME, objectType.toLowerCase)
    contentUpdateReq.getContext.put(Constants.VERSION, version)
    contentUpdateReq.getContext.put(Constants.IDENTIFIER, resourceId)
    contentUpdateReq.put("versionKey", versionKey)
    contentUpdateReq.put("lockKey", lockId.toString)
    contentUpdateReq.put("channel", channel)
    DataNode.update(contentUpdateReq).recoverWith { case e: CompletionException => throw e.getCause }
  }

  private def validateResource(request: Request) = {
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

  def createExpiryTime():Date = {
    val expiryTimeMillis = System.currentTimeMillis() + (defaultLockExpiryTime * 1000)
    new Date(expiryTimeMillis)
  }

}
