package org.sunbird.managers

import org.apache.commons.collections.CollectionUtils
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang.StringUtils
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.graph.utils.{NodeUtil, ScalaJsonUtils}
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.{AssessmentConstants, BranchingUtil, HierarchyConstants}

import java.util
import java.util.concurrent.{CompletionException}
import java.util.{Optional, UUID}
import scala.collection.JavaConversions.{mapAsScalaMap}
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object CopyManager {

  private val originMetadataKeys: util.List[String] = Platform.getStringList("assessment.copy.origin_data", new util.ArrayList[String]())
  private val internalHierarchyProps = List("identifier", "parent", "index", "depth")
  private val metadataNotTobeCopied = Platform.config.getStringList("assessment.copy.props_to_remove")

  def copy(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    validateRequest(request)
    DataNode.read(request).map(node => {
      validateExistingNode(request, node)
      val copiedNodeFuture: Future[Node] = node.getMetadata.get(AssessmentConstants.MIME_TYPE) match {
        case AssessmentConstants.QUESTIONSET_MIME_TYPE =>
          node.setInRelations(null)
          node.setOutRelations(null)
          validateShallowCopyReq(node, request)
          copyQuestionSet(node, request)
        case AssessmentConstants.QUESTION_MIME_TYPE =>
          node.setInRelations(null)
          copyNode(node, request)
      }
      copiedNodeFuture.map(copiedNode => {
        val response = ResponseHandler.OK()
        response.put("node_id", new util.HashMap[String, AnyRef]() {
          {
            put(node.getIdentifier, copiedNode.getIdentifier)
          }
        })
        response.put(AssessmentConstants.VERSION_KEY, copiedNode.getMetadata.get(AssessmentConstants.VERSION_KEY))
        response
      })
    }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
  }

  def validateExistingNode(request: Request, node: Node) = {
    val requestObjectType = request.getObjectType
    val nodeObjectType = node.getObjectType
    if (!StringUtils.equalsIgnoreCase(requestObjectType, nodeObjectType)) throw new ClientException(AssessmentConstants.ERR_INVALID_OBJECT_TYPE, s"Please Provide Valid ${requestObjectType} Identifier")
    if (StringUtils.equalsIgnoreCase(node.getObjectType, AssessmentConstants.QUESTION) && StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault(AssessmentConstants.VISIBILITY, AssessmentConstants.VISIBILITY_PARENT).asInstanceOf[String], AssessmentConstants.VISIBILITY_PARENT))
      throw new ClientException(AssessmentConstants.ERR_INVALID_REQUEST, "Question With Visibility Parent Cannot Be Copied Individually!")
  }

  def copyQuestionSet(originNode: Node, request: Request)(implicit ex: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val copyType = request.getRequest.get(AssessmentConstants.COPY_TYPE).asInstanceOf[String]
    copyNode(originNode, request).map(node => {
      val req = new Request(request)
      req.put(AssessmentConstants.ROOT_ID, request.get(AssessmentConstants.IDENTIFIER))
      req.put(AssessmentConstants.MODE, request.get(AssessmentConstants.MODE))
      HierarchyManager.getHierarchy(req).map(response => {
        val originHierarchy = response.getResult.getOrDefault(AssessmentConstants.QUESTIONSET, new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
        copyType match {
          case AssessmentConstants.COPY_TYPE_SHALLOW => updateShallowHierarchy(request, node, originNode, originHierarchy)
          case _ => updateHierarchy(request, node, originNode, originHierarchy, copyType)
        }
      }).flatMap(f => f)
    }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
  }


  def copyNode(node: Node, request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val copyCreateReq: Future[Request] = getCopyRequest(node, request)
    copyCreateReq.map(req => {
      DataNode.create(req).map(copiedNode => {
        Future(copiedNode)
      }).flatMap(f => f)
    }).flatMap(f => f)
  }

  def updateHierarchy(request: Request, node: Node, originNode: Node, originHierarchy: util.Map[String, AnyRef], copyType: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    prepareHierarchyRequest(originHierarchy, originNode, node, copyType, request).map(req => {
      val hierarchyRequest = new Request(request)
      hierarchyRequest.putAll(req)
      val nodesModified: java.util.HashMap[String, AnyRef] = hierarchyRequest.getRequest.get(HierarchyConstants.NODES_MODIFIED).asInstanceOf[java.util.HashMap[String, AnyRef]]
      val branchingRecord = BranchingUtil.generateBranchingRecord(nodesModified)
      val originalRequest = JsonUtils.deserialize(ScalaJsonUtils.serialize(hierarchyRequest), classOf[Request])
      val BLExists = branchingRecord.exists(BLRecord => BLRecord._2.asInstanceOf[util.HashMap[String, AnyRef]].get(AssessmentConstants.CONTAINS_BL) == true)
      val (updateHierarchyReq, branchingUpdateReq) = if (BLExists) (hierarchyRequest, JsonUtils.deserialize(ScalaJsonUtils.serialize(hierarchyRequest), classOf[Request])) else (originalRequest, new Request())
      UpdateHierarchyManager.updateHierarchy(updateHierarchyReq).map(response => {
        if (!ResponseHandler.checkError(response)) response
        else {
          TelemetryManager.info(s"Update Hierarchy Failed For Copy Question Set Having Identifier: ${node.getIdentifier} | Response is: " + response)
          throw new ServerException("ERR_QUESTIONSET_COPY", "Something Went Wrong, Please Try Again")
        }
      }).map(response => {
        if (BLExists) {
          BranchingUtil.hierarchyRequestModifier(branchingUpdateReq, branchingRecord, response.getResult.get(AssessmentConstants.IDENTIFIERS).asInstanceOf[util.Map[String, String]])
          UpdateHierarchyManager.updateHierarchy(branchingUpdateReq).map(response_ => {
            if (!ResponseHandler.checkError(response_)) node
            else {
              TelemetryManager.info(s"Update Hierarchy Failed For Copy Question Set Having Identifier: ${node.getIdentifier} | Response is: " + response)
              throw new ServerException("ERR_QUESTIONSET_COPY", "Something Went Wrong, Please Try Again")
            }
          })
        } else Future(node)
      }).flatMap(f => f)
    }).flatMap(f => f)
  }

  def prepareHierarchyRequest(originHierarchy: util.Map[String, AnyRef], originNode: Node, node: Node, copyType: String, request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[util.Map[String, AnyRef]] = {
    val children: util.List[util.Map[String, AnyRef]] = originHierarchy.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    if (null != children && !children.isEmpty) {
      val nodesModified = new util.HashMap[String, AnyRef]()
      val hierarchy = new util.HashMap[String, AnyRef]()
      val idMap = new util.HashMap[String, String]()
      hierarchy.put(node.getIdentifier, new util.HashMap[String, AnyRef]() {
        {
          put(AssessmentConstants.CHILDREN, new util.ArrayList[String]())
          put(AssessmentConstants.ROOT, true.asInstanceOf[AnyRef])
          put(AssessmentConstants.PRIMARY_CATEGORY, node.getMetadata.get(AssessmentConstants.PRIMARY_CATEGORY))
        }
      })
      populateHierarchyRequest(children, nodesModified, hierarchy, node.getIdentifier, copyType, request, idMap)
      getExternalData(idMap.keySet().asScala.toList, request).map(exData => {
        idMap.asScala.toMap.foreach(entry => {
          nodesModified.get(entry._2).asInstanceOf[java.util.Map[String, AnyRef]].get("metadata").asInstanceOf[util.Map[String, AnyRef]].putAll(exData.getOrDefault(entry._1, new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]])
        })
        new util.HashMap[String, AnyRef]() {
          {
            put(AssessmentConstants.NODES_MODIFIED, nodesModified)
            put(AssessmentConstants.HIERARCHY, hierarchy)
          }
        }
      })

    } else Future(new util.HashMap[String, AnyRef]())
  }

  def getExternalData(identifiers: List[String], request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[util.Map[String, AnyRef]] = {
    val req = new Request(request)
    req.getContext().putAll(Map("objectType" -> "Question", "schemaName" -> "question").asJava)
    req.put("identifiers", identifiers)
    val result = new util.HashMap[String, AnyRef]()
    val externalProps = DefinitionNode.getExternalProps(req.getContext.getOrDefault("graph_id", "domain").asInstanceOf[String], req.getContext.getOrDefault("version", "1.0").asInstanceOf[String], req.getContext.getOrDefault("schemaName", "question").asInstanceOf[String])
    val externalPropsResponse = oec.graphService.readExternalProps(req, externalProps)
    externalPropsResponse.map(response => {
      identifiers.map(id => {
        val externalData = Optional.ofNullable(response.get(id).asInstanceOf[util.Map[String, AnyRef]]).orElse(new util.HashMap[String, AnyRef]())
        result.put(id, externalData)
      })
      result
    })
  }

  def populateHierarchyRequest(children: util.List[util.Map[String, AnyRef]], nodesModified: util.HashMap[String, AnyRef], hierarchy: util.HashMap[String, AnyRef], parentId: String, copyType: String, request: Request, idMap: java.util.Map[String, String])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Unit = {
    if (null != children && !children.isEmpty) {
      children.asScala.toList.foreach(child => {
        val id = if ("Parent".equalsIgnoreCase(child.get(AssessmentConstants.VISIBILITY).asInstanceOf[String])) {
          val identifier = UUID.randomUUID().toString
          nodesModified.put(identifier, new util.HashMap[String, AnyRef]() {
            {
              put(AssessmentConstants.METADATA, cleanUpCopiedData(new util.HashMap[String, AnyRef]() {
                {
                  putAll(child)
                  put(AssessmentConstants.COPY_OF, child.getOrDefault(AssessmentConstants.IDENTIFIER,""))
                  put(AssessmentConstants.CHILDREN, new util.ArrayList())
                  internalHierarchyProps.map(key => remove(key))
                }
              }, copyType))
              put(AssessmentConstants.ROOT, false.asInstanceOf[AnyRef])
              put(AssessmentConstants.OBJECT_TYPE, child.getOrDefault(AssessmentConstants.OBJECT_TYPE, ""))
              put("isNew", true.asInstanceOf[AnyRef])
              put("setDefaultValue", false.asInstanceOf[AnyRef])
            }
          })
          if (StringUtils.equalsIgnoreCase(AssessmentConstants.QUESTION_MIME_TYPE, child.getOrDefault("mimeType", "").asInstanceOf[String]))
            idMap.put(child.getOrDefault("identifier", "").asInstanceOf[String], identifier)
          identifier
        } else
          child.get(AssessmentConstants.IDENTIFIER).asInstanceOf[String]
        if (StringUtils.equalsIgnoreCase(child.getOrDefault(AssessmentConstants.MIME_TYPE, "").asInstanceOf[String], AssessmentConstants.QUESTIONSET_MIME_TYPE))
          hierarchy.put(id, new util.HashMap[String, AnyRef]() {
            {
              put(AssessmentConstants.CHILDREN, new util.ArrayList[String]())
              put(AssessmentConstants.ROOT, false.asInstanceOf[AnyRef])
              put(AssessmentConstants.PRIMARY_CATEGORY, child.get(AssessmentConstants.PRIMARY_CATEGORY))
            }
          })
        hierarchy.get(parentId).asInstanceOf[util.Map[String, AnyRef]].get(AssessmentConstants.CHILDREN).asInstanceOf[util.List[String]].add(id)
        populateHierarchyRequest(child.get(AssessmentConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]], nodesModified, hierarchy, id, copyType, request, idMap)
      })
    }
  }

  def updateShallowHierarchy(request: Request, node: Node, originNode: Node, originHierarchy: util.Map[String, AnyRef])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val childrenHierarchy = originHierarchy.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    val req = new Request(request)
    req.getContext.put(AssessmentConstants.SCHEMA_NAME, request.getContext.getOrDefault(AssessmentConstants.SCHEMA_NAME, AssessmentConstants.QUESTIONSET_SCHEMA_NAME))
    req.getContext.put(AssessmentConstants.VERSION, request.getContext.getOrDefault(AssessmentConstants.VERSION, AssessmentConstants.SCHEMA_VERSION))
    req.getContext.put(AssessmentConstants.IDENTIFIER, node.getIdentifier)
    req.put(AssessmentConstants.HIERARCHY, ScalaJsonUtils.serialize(new java.util.HashMap[String, AnyRef]() {
      {
        put(AssessmentConstants.IDENTIFIER, node.getIdentifier)
        put(AssessmentConstants.CHILDREN, childrenHierarchy)
      }
    }))
    DataNode.update(req).map(node => node)
  }

  def getCopyRequest(node: Node, request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Request] = {
    val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, new util.ArrayList(), node.getObjectType.toLowerCase.replace("image", ""), request.getContext.getOrDefault(AssessmentConstants.VERSION, "").asInstanceOf[String])
    val requestMap = request.getRequest
    requestMap.remove(AssessmentConstants.MODE)
    requestMap.remove(AssessmentConstants.COPY_SCHEME).asInstanceOf[String]
    val copyType = requestMap.remove(AssessmentConstants.COPY_TYPE).asInstanceOf[String]
    val originData: java.util.Map[String, AnyRef] = getOriginData(metadata, copyType)
    cleanUpCopiedData(metadata, copyType)
    metadata.putAll(requestMap)
    metadata.put(AssessmentConstants.STATUS, "Draft")
    metadata.put(AssessmentConstants.ORIGIN, node.getIdentifier)
    metadata.put(AssessmentConstants.IDENTIFIER, Identifier.getIdentifier(request.getContext.get("graph_id").asInstanceOf[String], Identifier.getUniqueIdFromTimestamp))
    if (MapUtils.isNotEmpty(originData))
      metadata.put(AssessmentConstants.ORIGIN_DATA, originData)
    request.getContext().put(AssessmentConstants.SCHEMA_NAME, node.getObjectType.toLowerCase.replace("image", ""))
    val req = new Request(request)
    req.setRequest(metadata)
    val graphId = request.getContext.getOrDefault("graph_id", "").asInstanceOf[String]
    val version = request.getContext.getOrDefault("version", "").asInstanceOf[String]
    val externalProps = if (StringUtils.equalsIgnoreCase(AssessmentConstants.QUESTIONSET_MIME_TYPE, node.getMetadata.getOrDefault("mimeType", "").asInstanceOf[String])) {
      DefinitionNode.getExternalProps(graphId, version, AssessmentConstants.QUESTIONSET_SCHEMA_NAME).diff(List("hierarchy"))
    } else {
      DefinitionNode.getExternalProps(graphId, version, AssessmentConstants.QUESTION_SCHEMA_NAME)
    }
    val readReq = new Request()
    readReq.setContext(request.getContext)
    readReq.put("identifier", node.getIdentifier)
    readReq.put("fields", externalProps.asJava)
    DataNode.read(readReq).map(node => {
      val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, externalProps.asJava, node.getObjectType.toLowerCase.replace
      ("image", ""), request.getContext.get("version").asInstanceOf[String])
      externalProps.foreach(prop => {
        val propValue = metadata.get(prop)
        if (metadata.containsKey(prop) && propValue != null) {
          req.put(prop, propValue)
        }
      })
      Future(req)
    }).flatMap(f=>f)
  }

  def getOriginData(metadata: util.Map[String, AnyRef], copyType: String): java.util.Map[String, AnyRef] = (Map(AssessmentConstants.COPY_TYPE -> copyType) ++ originMetadataKeys.asScala.filter(key => metadata.containsKey(key)).map(key => key -> metadata.get(key)).toMap).asJava

  def validateRequest(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Unit = {
    val keysNotPresent = AssessmentConstants.REQUIRED_KEYS.filter(key => emptyCheckFilter(request.getRequest.getOrDefault(key, "")))
    if (keysNotPresent.nonEmpty)
      throw new ClientException(AssessmentConstants.ERR_INVALID_REQUEST, "Please provide valid value for " + keysNotPresent)
  }

  def validateShallowCopyReq(node: Node, request: Request) = {
    val copyType: String = request.getRequest.get("copyType").asInstanceOf[String]
    if (StringUtils.equalsIgnoreCase("shallow", copyType) && !StringUtils.equalsIgnoreCase("Live", node.getMetadata.get("status").asInstanceOf[String]))
      throw new ClientException(AssessmentConstants.ERR_INVALID_REQUEST, "QuestionSet With Status " + node.getMetadata.get(AssessmentConstants.STATUS).asInstanceOf[String].toLowerCase + " Cannot Be Partially (Shallow) Copied.")
    if(StringUtils.equalsIgnoreCase("shallow", copyType) && StringUtils.equalsIgnoreCase(request.get(AssessmentConstants.MODE).asInstanceOf[String], "edit"))
      request.getRequest.remove(AssessmentConstants.MODE)
  }

  def emptyCheckFilter(key: AnyRef): Boolean = key match {
    case k: String => k.asInstanceOf[String].isEmpty
    case k: util.Map[String, AnyRef] => MapUtils.isEmpty(k.asInstanceOf[util.Map[String, AnyRef]])
    case k: util.List[String] => CollectionUtils.isEmpty(k.asInstanceOf[util.List[String]])
    case _ => true
  }

  def cleanUpCopiedData(metadata: util.Map[String, AnyRef], copyType: String): util.Map[String, AnyRef] = {
    if (StringUtils.equalsIgnoreCase(AssessmentConstants.COPY_TYPE_SHALLOW, copyType)) {
      metadata.keySet().removeAll(metadataNotTobeCopied.asScala.toList.filter(str => !str.contains("dial")).asJava)
    } else metadata.keySet().removeAll(metadataNotTobeCopied)
    metadata
  }
}