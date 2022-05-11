package org.sunbird.utils

import org.apache.commons.lang.StringUtils
import org.sunbird.common.dto.Request

import java.util
import scala.collection.JavaConverters._
import scala.collection.JavaConversions.{asScalaBuffer}

object BranchingUtil {

	def generateBranchingRecord(nodesModified: util.HashMap[String, AnyRef]): util.HashMap[String, AnyRef] = {
		val idSet = nodesModified.keySet().asScala.toList
		val branchingRecord = new util.HashMap[String, AnyRef]()
		idSet.map(id => {
			val nodeMetaData = nodesModified.getOrDefault(id, new util.HashMap()).asInstanceOf[util.Map[String, AnyRef]].getOrDefault(AssessmentConstants.METADATA, new util.HashMap()).asInstanceOf[util.Map[String, AnyRef]]
			val containsBL = nodeMetaData.containsKey(AssessmentConstants.BRANCHING_LOGIC)
			branchingRecord.put(id, new util.HashMap[String, AnyRef]() {
				{
					if (containsBL) put(AssessmentConstants.BRANCHING_LOGIC, nodeMetaData.get(AssessmentConstants.BRANCHING_LOGIC))
					put(AssessmentConstants.CONTAINS_BL, containsBL.asInstanceOf[AnyRef])
					put(AssessmentConstants.COPY_OF, nodeMetaData.get(AssessmentConstants.COPY_OF).asInstanceOf[String])
				}
			})
			if (containsBL) nodeMetaData.remove(AssessmentConstants.BRANCHING_LOGIC)
			nodeMetaData.remove(AssessmentConstants.COPY_OF)
		})
		branchingRecord
	}

	def hierarchyRequestModifier(request: Request, branchingRecord: util.HashMap[String, AnyRef], identifiers: util.Map[String, String]) = {
		val nodesModified: java.util.HashMap[String, AnyRef] = request.getRequest.get(HierarchyConstants.NODES_MODIFIED).asInstanceOf[java.util.HashMap[String, AnyRef]]
		val hierarchy: java.util.HashMap[String, AnyRef] = request.getRequest.get(HierarchyConstants.HIERARCHY).asInstanceOf[java.util.HashMap[String, AnyRef]]
		val oldToNewIdMap = generateOldToNewIdMap(branchingRecord, identifiers)
		branchingRecord.keySet().asScala.toList.map(id => {
			val nodeInfo = branchingRecord.get(id).asInstanceOf[util.HashMap[String, AnyRef]]
			val node = nodesModified.get(id).asInstanceOf[util.HashMap[String, AnyRef]]
			val nodeMetaData = node.get(AssessmentConstants.METADATA).asInstanceOf[util.HashMap[String, AnyRef]]
			val newId = identifiers.get(id)
			if (nodeInfo.get(AssessmentConstants.CONTAINS_BL).asInstanceOf[Boolean]) {
				val branchingLogic = nodeInfo.get(AssessmentConstants.BRANCHING_LOGIC).asInstanceOf[util.HashMap[String, AnyRef]]
				branchingLogicModifier(branchingLogic, oldToNewIdMap)
				nodeMetaData.put(AssessmentConstants.BRANCHING_LOGIC, branchingLogic)
			}
			node.put(AssessmentConstants.IS_NEW, false.asInstanceOf[AnyRef])
			nodesModified.remove(id)
			nodesModified.put(newId, node)
		})
		hierarchy.keySet().asScala.toList.map(id => {
			val nodeHierarchy = hierarchy.get(id).asInstanceOf[util.HashMap[String, AnyRef]]
			val children = nodeHierarchy.get(AssessmentConstants.CHILDREN).asInstanceOf[util.ArrayList[String]]
			val newChildrenList = new util.ArrayList[String]
			children.map(identifier => {
				if (identifiers.containsKey(identifier)) newChildrenList.add(identifiers.get(identifier)) else newChildrenList.add(identifier)
			})
			nodeHierarchy.remove(AssessmentConstants.CHILDREN)
			nodeHierarchy.put(AssessmentConstants.CHILDREN, newChildrenList)
			if (identifiers.containsKey(id)) {
				hierarchy.remove(id)
				hierarchy.put(identifiers.get(id), nodeHierarchy)
			}
		})
		request
	}

	def branchingLogicModifier(branchingLogic: util.HashMap[String, AnyRef], oldToNewIdMap: util.Map[String, String]): Unit = {
		branchingLogic.keySet().asScala.toList.map(identifier => {
			val nodeBL = branchingLogic.get(identifier).asInstanceOf[util.HashMap[String, AnyRef]]
			nodeBL.keySet().asScala.toList.map(key => {
				if (StringUtils.equalsIgnoreCase(key, AssessmentConstants.TARGET)) branchingLogicArrayHandler(nodeBL, AssessmentConstants.TARGET, oldToNewIdMap)
				else if (StringUtils.equalsIgnoreCase(key, AssessmentConstants.PRE_CONDITION)) preConditionHandler(nodeBL, oldToNewIdMap)
				else if (StringUtils.equalsIgnoreCase(key, AssessmentConstants.SOURCE)) branchingLogicArrayHandler(nodeBL, AssessmentConstants.SOURCE, oldToNewIdMap)
			})
			if (oldToNewIdMap.containsKey(identifier)) {
				branchingLogic.put(oldToNewIdMap.get(identifier), nodeBL)
				branchingLogic.remove(identifier)
			}
		})
	}

	def generateOldToNewIdMap(branchingRecord: util.HashMap[String, AnyRef], identifiers: util.Map[String, String]): util.Map[String, String] = {
		val oldToNewIdMap = new util.HashMap[String, String]()
		branchingRecord.keySet().asScala.toList.map(id => {
			val nodeInfo = branchingRecord.get(id).asInstanceOf[util.HashMap[String, AnyRef]]
			val newId = identifiers.get(id)
			val oldId = nodeInfo.get(AssessmentConstants.COPY_OF).asInstanceOf[String]
			oldToNewIdMap.put(oldId, newId)
		})
		oldToNewIdMap
	}

	def branchingLogicArrayHandler(nodeBL: util.HashMap[String, AnyRef], name: String, oldToNewIdMap: util.Map[String, String]) = {
		val branchingLogicArray = nodeBL.getOrDefault(name, new util.ArrayList[String]).asInstanceOf[util.ArrayList[String]]
		val newBranchingLogicArray = new util.ArrayList[String]()
		branchingLogicArray.map(id => {
			if (oldToNewIdMap.containsKey(id)) {
				newBranchingLogicArray.add(oldToNewIdMap.get(id))
			} else newBranchingLogicArray.add(id)
		})
		nodeBL.remove(name)
		nodeBL.put(name, newBranchingLogicArray)
	}

	def preConditionHandler(nodeBL: util.HashMap[String, AnyRef], oldToNewIdMap: util.Map[String, String]): Unit = {
		val preCondition = nodeBL.get(AssessmentConstants.PRE_CONDITION).asInstanceOf[util.HashMap[String, AnyRef]]
		preCondition.keySet().asScala.toList.map(key => {
			val conjunctionArray = preCondition.get(key).asInstanceOf[util.ArrayList[String]]
			val condition = conjunctionArray.get(0).asInstanceOf[util.HashMap[String, AnyRef]]
			condition.keySet().asScala.toList.map(logicOp => {
				val conditionArray = condition.get(logicOp).asInstanceOf[util.ArrayList[String]]
				val sourceQuestionRecord = conditionArray.get(0).asInstanceOf[util.HashMap[String, AnyRef]]
				val preConditionVar = sourceQuestionRecord.get(AssessmentConstants.PRE_CONDITION_VAR).asInstanceOf[String]
				val stringArray = preConditionVar.split("\\.")
				if (oldToNewIdMap.containsKey(stringArray(0))) {
					val newString = oldToNewIdMap.get(stringArray(0)) + "." + stringArray.drop(1).mkString(".")
					sourceQuestionRecord.remove(AssessmentConstants.PRE_CONDITION_VAR)
					sourceQuestionRecord.put(AssessmentConstants.PRE_CONDITION_VAR, newString)
				}
			})
		})
	}
}
