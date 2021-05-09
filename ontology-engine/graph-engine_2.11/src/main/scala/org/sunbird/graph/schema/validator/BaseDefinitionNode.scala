package org.sunbird.graph.schema.validator

import java.util

import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.Request
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.dac.enums.SystemNodeTypes
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.schema.{IDefinition, ObjectCategoryDefinition}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class BaseDefinitionNode(graphId: String, schemaName: String, version: String = "1.0", ocd: ObjectCategoryDefinition = ObjectCategoryDefinition())(implicit ec: ExecutionContext, oec: OntologyEngineContext) extends IDefinition(graphId, schemaName, version, ocd)(ec, oec) {

    val inRelationsSchema: Map[String, AnyRef] = relationsSchema("in")
    val outRelationsSchema: Map[String, AnyRef] = relationsSchema("out")
    val outRelationObjectTypes: List[String] = {
        outRelationsSchema.values.map((e: AnyRef) => e.asInstanceOf[java.util.Map[String, AnyRef]].asScala)
                .flatten(e => {
                    val relType = e.getOrElse("type", "associatedTo")
                    val objects = e.getOrElse("objects", new util.ArrayList).asInstanceOf[java.util.List[String]].asScala
                    objects.map(obj => relType + ":" + obj)
                }).toList.distinct
    }

    private def relationsSchema(direction: String): Map[String, AnyRef] = {
        if (schemaValidator.getConfig.hasPath("relations")) {
            schemaValidator.getConfig.getObject("relations").unwrapped().asScala.filter(entry => {
                val relation = entry._2.asInstanceOf[java.util.Map[String, AnyRef]].asScala
                direction.equalsIgnoreCase(relation.getOrElse("direction", "out").asInstanceOf[String])
            }).map(entry => (entry._1 ,entry._2.asInstanceOf[AnyRef])).toMap
        } else {
            Map()
        }
    }

    override def getNode(input: java.util.Map[String, Object]): Node = {
        val result = schemaValidator.getStructuredData(input)
        val node = new Node(graphId, result.getMetadata)
        val objectType = schemaValidator.getConfig.getString("objectType")
        node.setNodeType(SystemNodeTypes.DATA_NODE.name)
        node.setObjectType(objectType)
        node.setIdentifier(input.getOrDefault("identifier", Identifier.getIdentifier(graphId, Identifier.getUniqueIdFromTimestamp)).asInstanceOf[String])
        input.remove("identifier")
        setRelations(node, result.getRelations)
        if (CollectionUtils.isNotEmpty(node.getInRelations)) node.setAddedRelations(node.getInRelations)
        if (CollectionUtils.isNotEmpty(node.getOutRelations)) node.setAddedRelations(node.getOutRelations)
        node.setExternalData(result.getExternalData)
        node
    }

    @throws[Exception]
    override def validate(node: Node, operation: String, setDefaultValue: Boolean)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        Future{node}
    }

    override def getNode(identifier: String, operation: String, mode: String, versioning: Option[String] = None)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val request: Request = new Request()
        val node: Future[Node] = oec.graphService.getNodeByUniqueId(graphId, identifier, false, request)
        node
    }


    protected def setRelations(node: Node, relations: java.util.Map[String, AnyRef]): Unit = {
        if (MapUtils.isNotEmpty(relations)) {
            def getRelations(schema: Map[String, AnyRef], direction:String): List[Relation] = {
                relations.asScala.filterKeys(key => schema.keySet.contains(key))
                        .flatten(entry => {
                            val relSchema = schema.get(entry._1).get.asInstanceOf[java.util.Map[String, AnyRef]].asScala
                            val relData = entry._2.asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
                            relData.asScala.map(r => {
                                val relation = {
                                    if(StringUtils.equalsAnyIgnoreCase("out", direction))
                                        new Relation(node.getIdentifier, relSchema.get("type").get.asInstanceOf[String], r.get("identifier").asInstanceOf[String])
                                    else
                                        new Relation(r.get("identifier").asInstanceOf[String], relSchema.get("type").get.asInstanceOf[String], node.getIdentifier)
                                }
                                relation
                            })
                        }).toList
            }
            val inRelations = getRelations(inRelationsSchema,"in").asJava
            node.setInRelations(inRelations)
            val outRelations = getRelations(outRelationsSchema,"out").asJava
            node.setOutRelations(outRelations)
        }
    }
}
