package org.sunbird.content.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.{Platform, Slug}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.content.util.SchemaConstants
import org.sunbird.util.RequestUtil

import java.util
import javax.inject.Inject
import scala.collection.JavaConverters
import scala.concurrent.{ExecutionContext, Future}
import org.sunbird.content.upload.mgr.SchemaUploadManager

class SchemaActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {
  implicit val ec: ExecutionContext = getContext().dispatcher
  private final val SCHEMA_SLUG_LIMIT: Int = if (Platform.config.hasPath("schema.slug_limit")) Platform.config.getInt("schema.slug_limit") else 3
  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case "createSchema" => create(request)
      case "readSchema" => read(request)
      case "uploadSchema" => upload(request)
      case _ => ERROR(request.getOperation)
    }
  }
  @throws[Exception]
  private def create(request: Request): Future[Response] = {
    val name = request.getRequest.getOrDefault(SchemaConstants.NAME, "").asInstanceOf[String]
    if (!request.getRequest.containsKey("name")) throw new ClientException("ERR_SCHEMA_NAME_REQUIRED", "Unique name is mandatory for Schema creation")
    val slug = Slug.makeSlug(name)
    request.getRequest.put("slug", slug)
    RequestUtil.restrictProperties(request)
    request.getRequest.put(SchemaConstants.IDENTIFIER, Identifier.getIdentifier(slug, Identifier.getUniqueIdFromTimestamp, SCHEMA_SLUG_LIMIT))
    DataNode.create(request).map(node => {
      ResponseHandler.OK.put(SchemaConstants.IDENTIFIER, node.getIdentifier)
    })
  }

  private def read(request: Request): Future[Response] = {
    val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
    request.getRequest.put("fields", fields)
    val schemaId = request.get("identifier").asInstanceOf[String]
    DataNode.read(request).map(node => {
      if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, schemaId)) {
        val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
        ResponseHandler.OK.put("schema", metadata)
      } else throw new ClientException("ERR_INVALID_REQUEST", "Invalid Request. Please Provide Required Properties!")
    })
  }

  def upload(request: Request): Future[Response] = {
    val identifier: String = request.getContext.getOrDefault(SchemaConstants.IDENTIFIER, "").asInstanceOf[String]
    val readReq = new Request(request)
    readReq.put(SchemaConstants.IDENTIFIER, identifier)
    readReq.put("fields", new util.ArrayList[String])
    DataNode.read(readReq).map(node => {
      if (null != node & StringUtils.isNotBlank(node.getObjectType))
        request.getContext.put(SchemaConstants.SCHEMA, node.getObjectType.toLowerCase())
      SchemaUploadManager.upload(request, node)
    }).flatMap(f => f)
  }

}
