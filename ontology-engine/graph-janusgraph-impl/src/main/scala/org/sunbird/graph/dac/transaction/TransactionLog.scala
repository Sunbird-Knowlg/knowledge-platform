package org.sunbird.graph.dac.transaction
import org.apache.commons.lang3.StringUtils
import org.apache.tinkerpop.gremlin.driver.Result
import org.sunbird.graph.common.enums.{GraphDACParams, SystemProperties}
import org.sunbird.common.JsonUtils
import org.sunbird.graph.service.util.VertexUtil

import java.text.SimpleDateFormat
import java.util
import java.util.{Date, UUID}
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`


object TransactionLog {

  var graphId = ""

  def getMessageObj(graphId: String, transactionData: util.Map[AnyRef, AnyRef]) = {
    this.graphId = graphId
    val userId: String = null
    val requestId: String = null
    val messageMap = new util.ArrayList[util.Map[String, AnyRef]]
    val oldData = transactionData.get("oldData").asInstanceOf[util.Map[AnyRef, AnyRef]]
    val newData = transactionData.get("newData").asInstanceOf[util.Map[AnyRef, AnyRef]]
    val edgeData = transactionData.get("edgeData").asInstanceOf[util.Map[String, AnyRef]]
    messageMap.addAll(getNodeMessages(userId, requestId, oldData, newData))
    messageMap.addAll(getAddedRelationMessages(userId, requestId, edgeData))
  }

  private def getNodeMessages(userId: String, requestId: String,  oldData: util.Map[AnyRef, AnyRef], newData: util.Map[AnyRef, AnyRef]): util.List[util.Map[String, Object]] = {
    val lstMessageMap = new util.ArrayList[util.Map[String, AnyRef]]
    val transactionData = new util.HashMap[String, Object]
    var operationType = GraphDACParams.CREATE.name
    if(!oldData.isEmpty && newData.isEmpty){
      operationType = GraphDACParams.DELETE.name
      transactionData.put("properties", getNodeRemovedPropertyEntry(oldData))
    } else {
      if (!oldData.isEmpty && !newData.isEmpty)
        operationType = GraphDACParams.UPDATE.name
      transactionData.put("properties", getPropertiesMap(oldData, newData))
    }
    if(!transactionData.isEmpty){
      val map = setMessageData(newData, transactionData, userId, requestId, operationType)
      lstMessageMap.add(map)
    }
    lstMessageMap
  }

  def getAddedRelationMessages(userId: String, requestId: String,  edgeData: util.Map[String, AnyRef]): util.List[util.Map[String, Object]] = {
    val lstMessageMap = new util.ArrayList[util.Map[String, AnyRef]]
    val startRelation = new util.HashMap[String, Object]
    startRelation.put("rel", edgeData.get("relationTypeName"))
    startRelation.put("id", edgeData.get("endNodeId"))
    startRelation.put("dir", "OUT")
    startRelation.put("relMetadata", edgeData.get("relMetadata"))
    lstMessageMap
  }

  def setMessageData(data: util.Map[AnyRef, AnyRef], transactionDataMap: util.Map[String, AnyRef], userId: String, requestId: String, operationType: String): util.Map[String, Object] = {
    var user = userId
    if (StringUtils.isEmpty(user)) {
      if (data.containsKey("lastUpdatedBy")) {
        user = data.get("lastUpdatedBy").asInstanceOf[String]
      } else if (data.containsKey("createdBy")) {
        user = data.get("createdBy").asInstanceOf[String]
      } else {
        user = "ANONYMOUS"
      }
    }
    var channelId = ""
    if (data.containsKey("channel"))
      channelId = data.get("channel").asInstanceOf[String]

    val event = new util.HashMap[String, AnyRef]() {
      val ets: Long = System.currentTimeMillis()
      put("ets", ets.toString)
      put("channel", channelId)
      put("transactionData", transactionDataMap)
      put("mid", getUUID)
      put("label", data.get("name"))
      put("nodeType", data.get(SystemProperties.IL_SYS_NODE_TYPE.name))
      put("userId", user)
      put("createdOn", formatCurrentDate)
      put("objectType", data.get(SystemProperties.IL_FUNC_OBJECT_TYPE.name))
      put("nodeUniqueId", data.get(SystemProperties.IL_UNIQUE_ID.name))
      put("requestId", requestId)
      put("operationType", operationType)
      put("nodeGraphId", data.get(SystemProperties.IL_UNIQUE_ID.name))
      put("graphId", "domain")
    }
    println("event  ->" + JsonUtils.serialize(event))
    event
  }

  private def getPropertiesMap(oldData: util.Map[AnyRef, AnyRef], newData: util.Map[AnyRef, AnyRef]): util.HashMap[String, AnyRef] = {
    val finalPropertiesMap = new util.HashMap[String, AnyRef]()
    newData.forEach((key, newValue) => {
      val oldValue = oldData.get(key)
      if (oldValue == null || oldValue != newValue) {
        val propertyMap = new util.HashMap[String, AnyRef]()
        propertyMap.put("ov", oldValue)
        propertyMap.put("nv", newValue)
        finalPropertiesMap.put(key.toString, propertyMap)
      }
    })
    finalPropertiesMap
  }

  private def getNodeRemovedPropertyEntry(oldData: util.Map[AnyRef, AnyRef]): util.HashMap[String, AnyRef] = {
    val finalPropertiesMap = new util.HashMap[String, AnyRef]()
    oldData.forEach((key, newValue) => {
      val propertyMap = new util.HashMap[String, AnyRef]()
        propertyMap.put("ov", newValue)
        propertyMap.put("nv", null)
        finalPropertiesMap.put(key.toString, propertyMap)
    })
    finalPropertiesMap
  }

  def createEvent(data: util.Map[AnyRef, AnyRef], transactionDataMap: util.Map[String, AnyRef], userId: String, requestId: String, operationType: String): Unit = {
    var user = userId
    if (StringUtils.isEmpty(user)) {
      if (data.containsKey("lastUpdatedBy")){
        user = data.get("lastUpdatedBy").asInstanceOf[String]
      } else if (data.containsKey("createdBy")){
        user = data.get("createdBy").asInstanceOf[String]
      }else {
        user = "ANONYMOUS"
      }
    }
    var channelId = ""
    if(data.containsKey("channel"))
      channelId = data.get("channel").asInstanceOf[String]

    val event = new util.HashMap[String, AnyRef]() {
      val ets: Long = System.currentTimeMillis()
      put("ets", ets.toString)
      put("channel", channelId)
      put("transactionData", transactionDataMap)
      put("mid", getUUID)
      put("label", "Test")
      put("nodeType", data.get(SystemProperties.IL_SYS_NODE_TYPE.name))
      put("userId", user)
      put("createdOn", formatCurrentDate)
      put("objectType", data.get(SystemProperties.IL_FUNC_OBJECT_TYPE.name))
      put("nodeUniqueId", data.get(SystemProperties.IL_UNIQUE_ID.name))
      put("requestId", requestId)
      put("operationType", operationType)
      put("nodeGraphId", 367.asInstanceOf[Integer])
      put("graphId", "domain")
    }
    println("event  ->" + JsonUtils.serialize(event))
  }

  private def getUUID = {
    val uid = UUID.randomUUID
    uid.toString
  }

  def formatCurrentDate: String = format(new Date)

  def format(date: Date): String = {
    val sdf = getDateFormat
    if (null != date) try return sdf.format(date)
    catch {
      case e: Exception =>

    }
    null
  }

  private def getDateFormat: SimpleDateFormat = {
    val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    sdf
  }


}
