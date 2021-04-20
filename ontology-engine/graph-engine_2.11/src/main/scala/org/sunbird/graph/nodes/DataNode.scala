package org.sunbird.graph.nodes

import java.util
import java.util.Optional
import java.util.concurrent.CompletionException

import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.{ClientException, ErrorCodes, ResponseCode}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.{Filter, MetadataCriterion, Node, Relation, SearchConditions, SearchCriteria}
import org.sunbird.graph.schema.{DefinitionDTO, DefinitionFactory, DefinitionNode}
import org.sunbird.parseq.Task

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


object DataNode {

  private val SYSTEM_UPDATE_ALLOWED_CONTENT_STATUS = List("Live", "Unlisted")

    @throws[Exception]
    def create(request: Request, dataModifier: (Node) => Node = defaultDataModifier)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
        DefinitionNode.validate(request).map(node => {
            val response = oec.graphService.addNode(graphId, dataModifier(node))
            response.map(node => DefinitionNode.postProcessor(request, node)).map(result => {
                val futureList = Task.parallel[Response](
                    saveExternalProperties(node.getIdentifier, node.getExternalData, request.getContext, request.getObjectType),
                    createRelations(graphId, node, request.getContext))
                futureList.map(list => result)
            }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause}
        }).flatMap(f => f)
    }

    @throws[Exception]
    def update(request: Request, dataModifier: (Node) => Node = defaultDataModifier)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
        val identifier: String = request.getContext.get("identifier").asInstanceOf[String]
        DefinitionNode.validate(identifier, request).map(node => {
            request.getContext().put("schemaName", node.getObjectType.toLowerCase.replace("image", ""))
            val response = oec.graphService.upsertNode(graphId, dataModifier(node), request)
            response.map(node => DefinitionNode.postProcessor(request, node)).map(result => {
                val futureList = Task.parallel[Response](
                    updateExternalProperties(node.getIdentifier, node.getExternalData, request.getContext, request.getObjectType, request),
                    updateRelations(graphId, node, request.getContext))
                futureList.map(list => result)
            }).flatMap(f => f)  recoverWith { case e: CompletionException => throw e.getCause}
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause}
    }

    @throws[Exception]
    def read(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        DefinitionNode.getNode(request).map(node => {
            val schema = node.getObjectType.toLowerCase.replace("image", "")
            request.getContext().put("schemaName", schema)
            val fields: List[String] = Optional.ofNullable(request.get("fields").asInstanceOf[util.List[String]]).orElse(new util.ArrayList[String]()).toList
            val extPropNameList = DefinitionNode.getExternalProps(request.getContext.get("graph_id").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String], schema)
            if (CollectionUtils.isNotEmpty(extPropNameList) && null != fields && fields.exists(field => extPropNameList.contains(field)))
                populateExternalProperties(fields, node, request, extPropNameList)
            else
                Future(node)
        }).flatMap(f => f) recoverWith {
            case e: CompletionException => throw e.getCause
        }
    }


    @throws[Exception]
    def list(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[util.List[Node]] = {
        val identifiers:util.List[String] = request.get("identifiers").asInstanceOf[util.List[String]]

        if(null == identifiers || identifiers.isEmpty) {
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "identifiers is mandatory")
        } else {
            val mc: MetadataCriterion = MetadataCriterion.create(new util.ArrayList[Filter](){{
                if(identifiers.size() == 1)
                    add(new Filter(SystemProperties.IL_UNIQUE_ID.name(), SearchConditions.OP_EQUAL, identifiers.get(0)))
                if(identifiers.size() > 1)
                    add(new Filter(SystemProperties.IL_UNIQUE_ID.name(), SearchConditions.OP_IN, identifiers))
                new Filter("status", SearchConditions.OP_NOT_EQUAL, "Retired")
            }})

            val searchCriteria =  new SearchCriteria {{
                addMetadata(mc)
                setCountQuery(false)
            }}
            oec.graphService.getNodeByUniqueIds(request.getContext.get("graph_id").asInstanceOf[String], searchCriteria)
        }
    }

    @throws[Exception]
    def bulkUpdate(request: Request)(implicit ec: ExecutionContext,oec: OntologyEngineContext): Future[util.Map[String, Node]] = {
        val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
        val identifiers: util.List[String] = request.get("identifiers").asInstanceOf[util.List[String]]
        val metadata: util.Map[String, AnyRef] = request.get("metadata").asInstanceOf[util.Map[String, AnyRef]]
        oec.graphService.updateNodes(graphId, identifiers, metadata)
    }

    @throws[Exception]
    def deleteNode(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[java.lang.Boolean] = {
        val graphId: String = request.getContext.getOrDefault("graph_id", "").asInstanceOf[String]
        val identifier: String = request.getRequest.getOrDefault("identifier", "").asInstanceOf[String]
        oec.graphService.deleteNode(graphId, identifier, request)
    }

    private def saveExternalProperties(identifier: String, externalProps: util.Map[String, AnyRef], context: util.Map[String, AnyRef], objectType: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        if (MapUtils.isNotEmpty(externalProps)) {
            externalProps.put("identifier", identifier)
            val request = new Request(context, externalProps, "", objectType)
            oec.graphService.saveExternalProps(request)
        } else {
            Future(new Response)
        }
    }

    private def updateExternalProperties(identifier: String, externalProps: util.Map[String, AnyRef], context: util.Map[String, AnyRef], objectType: String, request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        if (MapUtils.isNotEmpty(externalProps)) {
                val req = new Request(request)
                req.put("identifier", identifier)
                req.put("fields", externalProps.asScala.keys.toList)
                req.put("values", externalProps.asScala.values.toList)
                oec.graphService.updateExternalProps(req)
        } else Future(new Response)
    }
    
    private def createRelations(graphId: String, node: Node, context: util.Map[String, AnyRef])(implicit ec: ExecutionContext, oec: OntologyEngineContext) : Future[Response] = {
        val relations: util.List[Relation] = node.getAddedRelations
        if (CollectionUtils.isNotEmpty(relations)) {
            oec.graphService.createRelation(graphId,getRelationMap(relations))
        } else {
            Future(new Response)
        }
    }

    private def populateExternalProperties(fields: List[String], node: Node, request: Request, externalProps: List[String])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        if(StringUtils.equalsIgnoreCase(request.get("mode").asInstanceOf[String], "edit"))
            request.put("identifier", node.getIdentifier)
        val externalPropsResponse = oec.graphService.readExternalProps(request, externalProps.filter(prop => fields.contains(prop)))
        externalPropsResponse.map(response => {
            node.getMetadata.putAll(response.getResult)
            Future {
                node
            }
        }).flatMap(f => f)
    }

    private def updateRelations(graphId: String, node: Node, context: util.Map[String, AnyRef])(implicit ec: ExecutionContext, oec: OntologyEngineContext) : Future[Response] = {
        val request: Request = new Request
        request.setContext(context)

        if (CollectionUtils.isEmpty(node.getAddedRelations) && CollectionUtils.isEmpty(node.getDeletedRelations)) {
            Future(new Response)
        } else {
            if (CollectionUtils.isNotEmpty(node.getDeletedRelations))
                oec.graphService.removeRelation(graphId, getRelationMap(node.getDeletedRelations))
            if (CollectionUtils.isNotEmpty(node.getAddedRelations))
                oec.graphService.createRelation(graphId,getRelationMap(node.getAddedRelations))
            Future(new Response)
        }
    }

    // TODO: this method should be in GraphAsyncOperations.
    private def getRelationMap(relations:util.List[Relation]):java.util.List[util.Map[String, AnyRef]]={
        val list = new util.ArrayList[util.Map[String, AnyRef]]
        for (rel <- relations) {
            if ((StringUtils.isNotBlank(rel.getStartNodeId) && StringUtils.isNotBlank(rel.getEndNodeId)) && StringUtils.isNotBlank(rel.getRelationType)) {
                val map = new util.HashMap[String, AnyRef]
                map.put("startNodeId", rel.getStartNodeId)
                map.put("endNodeId", rel.getEndNodeId)
                map.put("relation", rel.getRelationType)
                if (MapUtils.isNotEmpty(rel.getMetadata)) map.put("relMetadata", rel.getMetadata)
                else map.put("relMetadata", new util.HashMap[String,AnyRef]())
                list.add(map)
            }
            else throw new ClientException("ERR_INVALID_RELATION_OBJECT", "Invalid Relation Object Found.")
        }
        list
    }
    
    private def defaultDataModifier(node: Node) = {
        node
    }

  @throws[Exception]
  def systemUpdate(request: Request, nodeList: util.List[Node], hierarchyKey: String, hierarchyFunc: Option[Request => Future[Response]] = None)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val data: util.Map[String, AnyRef] = request.getRequest
    // validate nodes
    validateNode(nodeList, request)

    // get definition for the object and filter relations
    val definition = getDefinition(request)
    val metadata = filterRelations(definition, data)
    // get status
    val status = getStatus(request, nodeList)
    // Generate request for new metadata
    val newRequest = new Request(request)
    newRequest.putAll(metadata)
    newRequest.getContext.put("versioning", "disabled")
    // Enrich Hierarchy and Update the nodes
    nodeList.map(node => {
      enrichHierarchyAndUpdate(newRequest, node.getIdentifier, status, hierarchyKey, hierarchyFunc)
    }).head
  }

  @throws[Exception]
  private def enrichHierarchyAndUpdate(request: Request, identifier: String, status: String, hierarchyKey: String, hierarchyFunc: Option[Request => Future[Response]] = None)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val metadata: util.Map[String, AnyRef] = request.getRequest

    // Image node cannot be made Live or Unlisted using system call
    if (identifier.endsWith(".img") &&
      SYSTEM_UPDATE_ALLOWED_CONTENT_STATUS.contains(status)) metadata.remove("status")
    if (metadata.isEmpty) throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), s"Invalid Request. Cannot update status of Image Node to $status.")

    // Generate new request object for Each request
    val newRequest = new Request(request)
    newRequest.putAll(metadata)
    newRequest.getContext.put("identifier", identifier)
    // Enrich Hierarchy and Update with the new request
    enrichHierarchy(newRequest, metadata, status, hierarchyKey: String, hierarchyFunc)
      .flatMap(req => update(req)) recoverWith { case e: CompletionException => throw e.getCause}
  }

  private def enrichHierarchy(request: Request, metadata: util.Map[String, AnyRef], status: String, hierarchyKey: String, hierarchyFunc: Option[Request => Future[Response]] = None)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Request] = {
    val identifier = request.getContext.get("identifier").asInstanceOf[String]
    // Check if hierarchy could be enriched
    if (!identifier.endsWith(".img") && SYSTEM_UPDATE_ALLOWED_CONTENT_STATUS.contains(status)) {
      hierarchyFunc match {
        case Some(hierarchyFunc) => {
          // Get current Hierarchy
          val hierarchyRequest = new Request(request)
          hierarchyRequest.put("rootId", identifier)
          hierarchyFunc(hierarchyRequest).map(response => {
            // Add metadata to the hierarchy
            if (response.get(hierarchyKey) != null) {
              val hierarchy = response.get(hierarchyKey).asInstanceOf[util.Map[String, AnyRef]]
              val hierarchyMetadata = new util.HashMap[String, AnyRef]()
              hierarchyMetadata.putAll(hierarchy)
              hierarchyMetadata.putAll(metadata)
              // add hierarchy to the request object
              request.put("hierarchy", hierarchyMetadata)
              request
            } else request
          })
        }
        case _ => Future(request)
      }
    } else Future(request)
  }

  def validateNode(nodes: java.util.List[Node], request: Request): Unit = {
    if (nodes.isEmpty)
      throw new ClientException(ResponseCode.RESOURCE_NOT_FOUND.name(), s"Error! Node(s) doesn't Exists with identifier : ${request.getContext.get("identifier")}.")

    val objectType = request.getContext.get("objectType").asInstanceOf[String]
    nodes.foreach(node => {
      if (node.getMetadata == null && !objectType.equalsIgnoreCase(node.getObjectType) && node.getMetadata.get("status").asInstanceOf[String].equalsIgnoreCase("failed"))
        throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), s"Cannot update content with FAILED status for id : ${node.getIdentifier}.")
    })
  }

  private def getStatus(request: Request, nodeList: util.List[Node]): String = {
    val node = if (nodeList.size() == 1) nodeList.get(0) else {
      if (nodeList.get(0).getIdentifier.endsWith(".img")) nodeList.get(1) else nodeList.get(0)
    }
    val status = if (request.get("status") == null) node.getMetadata.get("status").asInstanceOf[String] else request.get("status").asInstanceOf[String]
    status
  }

  private def getDefinition(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): DefinitionDTO = {
    val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
    val graphId = request.getContext.get("graph_id").asInstanceOf[String]
    val version = request.getContext.get("version").asInstanceOf[String]
    DefinitionFactory.getDefinition(graphId, schemaName, version)
  }

  private def filterRelations(definition: DefinitionDTO, data: util.Map[String, AnyRef]): util.Map[String, AnyRef] = {
    val relations = definition.getRelationsMap().keySet()
    data.filter(item => {
      !relations.contains(item._1)
    })
  }

}
