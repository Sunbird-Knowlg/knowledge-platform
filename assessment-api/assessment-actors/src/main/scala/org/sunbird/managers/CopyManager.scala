package org.sunbird.managers

import org.apache.commons.collections.CollectionUtils
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang.StringUtils
import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.graph.utils.{NodeUtil, ScalaJsonUtils}
import org.sunbird.utils.AssessmentConstants

import java.util
import java.util.UUID
import java.util.concurrent.CompletionException
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
        case AssessmentConstants.QUESTION_MIME_TYPE => //Add mime type
          node.setInRelations(null)
          copyQuestion(node, request)
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
    if (!StringUtils.equalsIgnoreCase(requestObjectType, nodeObjectType)) {
      throw new ClientException(AssessmentConstants.ERR_INVALID_OBJECT_TYPE, "Invalid Object Type: " + requestObjectType)
    }
  }

  def copyQuestionSet(originNode: Node, request: Request)(implicit ex: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val copyType = request.getRequest.get(AssessmentConstants.COPY_TYPE).asInstanceOf[String]
    copyQuestion(originNode, request).map(node => {
      val req = new Request(request)
      req.getContext.put(AssessmentConstants.SCHEMA_NAME, AssessmentConstants.QUESTIONSET_SCHEMA_NAME)
      req.getContext.put(AssessmentConstants.VERSION, AssessmentConstants.SCHEMA_VERSION)
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

  def copyQuestion(node: Node, request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val copyCreateReq: Future[Request] = getCopyRequest(node, request)
    copyCreateReq.map(req => {
      DataNode.create(req).map(copiedNode => {
        Future(copiedNode)
      }).flatMap(f => f)
    }).flatMap(f => f)
  }

  def updateHierarchy(request: Request, node: Node, originNode: Node, originHierarchy: util.Map[String, AnyRef], copyType: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val updateHierarchyRequest = prepareHierarchyRequest(originHierarchy, originNode, node, copyType, request)
    val hierarchyRequest = new Request(request)
    hierarchyRequest.putAll(updateHierarchyRequest)
    hierarchyRequest.getContext.put(AssessmentConstants.SCHEMA_NAME, AssessmentConstants.QUESTIONSET_SCHEMA_NAME)
    hierarchyRequest.getContext.put(AssessmentConstants.VERSION, AssessmentConstants.SCHEMA_VERSION)
    UpdateHierarchyManager.updateHierarchy(hierarchyRequest).map(response => node)
  }

  def prepareHierarchyRequest(originHierarchy: util.Map[String, AnyRef], originNode: Node, node: Node, copyType: String, request: Request): util.HashMap[String, AnyRef] = {
    val children: util.List[util.Map[String, AnyRef]] = originHierarchy.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    if (null != children && !children.isEmpty) {
      val nodesModified = new util.HashMap[String, AnyRef]()
      val hierarchy = new util.HashMap[String, AnyRef]()
      hierarchy.put(node.getIdentifier, new util.HashMap[String, AnyRef]() {
        {
          put(AssessmentConstants.CHILDREN, new util.ArrayList[String]())
          put(AssessmentConstants.ROOT, true.asInstanceOf[AnyRef])
          put(AssessmentConstants.PRIMARY_CATEGORY, node.getMetadata.get(AssessmentConstants.PRIMARY_CATEGORY))
        }
      })
      populateHierarchyRequest(children, nodesModified, hierarchy, node.getIdentifier, copyType, request)
      new util.HashMap[String, AnyRef]() {
        {
          put(AssessmentConstants.NODES_MODIFIED, nodesModified)
          put(AssessmentConstants.HIERARCHY, hierarchy)
        }
      }
    } else new util.HashMap[String, AnyRef]()
  }

  def populateHierarchyRequest(children: util.List[util.Map[String, AnyRef]], nodesModified: util.HashMap[String, AnyRef], hierarchy: util.HashMap[String, AnyRef], parentId: String, copyType: String, request: Request): Unit = {
    if (null != children && !children.isEmpty) {
      children.asScala.toList.foreach(child => {
        //TODO: Change the behavior for public question belonging to same creator.
        val id = if ("Parent".equalsIgnoreCase(child.get(AssessmentConstants.VISIBILITY).asInstanceOf[String])) {
          val identifier = UUID.randomUUID().toString
          nodesModified.put(identifier, new util.HashMap[String, AnyRef]() {
            {
              put(AssessmentConstants.METADATA, cleanUpCopiedData(new util.HashMap[String, AnyRef]() {
                {
                  putAll(child)
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
        populateHierarchyRequest(child.get(AssessmentConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]], nodesModified, hierarchy, id, copyType, request)
      })
    }
  }

  def updateShallowHierarchy(request: Request, node: Node, originNode: Node, originHierarchy: util.Map[String, AnyRef])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val childrenHierarchy = originHierarchy.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    val req = new Request(request)
    req.getContext.put(AssessmentConstants.SCHEMA_NAME, AssessmentConstants.QUESTIONSET_SCHEMA_NAME)
    req.getContext.put(AssessmentConstants.VERSION, AssessmentConstants.SCHEMA_VERSION)
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
    val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, new util.ArrayList(), node.getObjectType.toLowerCase.replace("image", ""), AssessmentConstants.SCHEMA_VERSION)
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


    val graphId = request.getContext.getOrDefault("graph_id","").asInstanceOf[String]
    val version = request.getContext.getOrDefault("version","").asInstanceOf[String]
    val schemaName = AssessmentConstants.QUESTION_SCHEMA_NAME
    val externalProps = DefinitionNode.getExternalProps(graphId,version,schemaName)
    val objectProps = List("editorState","solutions","instructions","hints","media","responseDeclaration","interactions")
    val readReq = new Request()
    readReq.setContext(request.getContext)
    readReq.put("identifier", node.getIdentifier)
    readReq.put("fields", externalProps.asJava)
    DataNode.read(readReq).map(node => {
      val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, externalProps.asJava, node.getObjectType.toLowerCase.replace("image", ""), request.getContext.get("version").asInstanceOf[String])
      //req.put("body",metadata.getOrDefault("body","default body"))
      //req.put("responseDeclaration",metadata.getOrDefault("responseDeclaration",{}.asInstanceOf[AnyRef]))
      //val metaDataScalaMap = metadata.asScala
      externalProps.foreach(prop =>{
        val propValue = metadata.get(prop)
        if(metadata.containsKey(prop) && propValue!=null){
          req.put(prop, propValue)
        }
      })
    })
    Future(req)
  }

  def getOriginData(metadata: util.Map[String, AnyRef], copyType: String): java.util.Map[String, AnyRef] = {
    new java.util.HashMap[String, AnyRef]() {
      {
        putAll(originMetadataKeys.asScala.filter(key => metadata.containsKey(key)).map(key => key -> metadata.get(key)).toMap.asJava)
        put(AssessmentConstants.COPY_TYPE, copyType)
      }
    }
  }

  def validateRequest(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Unit = {
    val keysNotPresent = AssessmentConstants.REQUIRED_KEYS.filter(key => emptyCheckFilter(request.getRequest.getOrDefault(key, "")))
    if (keysNotPresent.nonEmpty)
      throw new ClientException(AssessmentConstants.ERR_INVALID_REQUEST, "Please provide valid value for " + keysNotPresent)
  }

  def validateShallowCopyReq(node: Node, request: Request) = {
    val copyType: String = request.getRequest.get("copyType").asInstanceOf[String]
    if (StringUtils.equalsIgnoreCase("shallow", copyType) && !StringUtils.equalsIgnoreCase("Live", node.getMetadata.get("status").asInstanceOf[String]))
      throw new ClientException(AssessmentConstants.ERR_INVALID_REQUEST, "QuestionSet with status " + node.getMetadata.get(AssessmentConstants.STATUS).asInstanceOf[String].toLowerCase + " cannot be partially (shallow) copied.")
    //TODO: check if need to throw client exception for combination of copyType=shallow and mode=edit
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
