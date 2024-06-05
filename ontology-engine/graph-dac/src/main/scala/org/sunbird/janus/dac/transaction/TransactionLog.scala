package org.sunbird.janus.dac.transaction
import org.sunbird.common.JsonUtils

import java.util

object TransactionLog {

  def createTxLog(data : util.Map[AnyRef, AnyRef]): Unit = {
    val propertiesMap = new util.HashMap[String, AnyRef]()
    data.forEach((key, value) => {
      val propertyMap = new util.HashMap[String, AnyRef]()
      propertyMap.put("ov", null)
      propertyMap.put("nv", value)
      propertiesMap.put(key.toString, propertyMap)
    })
    val transactionDataMap = new util.HashMap[String, AnyRef](){
      put("properties", propertiesMap)
    }

    createEvent(transactionDataMap)

  }


  def updateTxLog(oldData: util.Map[AnyRef, AnyRef], newData: util.Map[AnyRef, AnyRef])= {
    val changedPropertiesMap = new util.HashMap[String, AnyRef]()

    newData.forEach((key, newValue) => {
      val oldValue = oldData.get(key)
      if (oldValue != newValue) {
        val propertyMap = new util.HashMap[String, AnyRef]()
        propertyMap.put("ov", oldValue)
        propertyMap.put("nv", newValue)
        changedPropertiesMap.put(key.toString, propertyMap)
      }
    })

    createEvent(changedPropertiesMap)
  }

  def createEvent(transactionDataMap: util.Map[String, AnyRef]): Unit = {
    val event = new util.HashMap[String, AnyRef]() {
      put("ets", "ets")
      put("channel", "channel")
      put("transactionData", transactionDataMap)
      put("mid", "mid")
      put("label", "Test")
      put("nodeType", "DATA_NODE")
      put("userId", "4cd4c690-eab6-4938-855a-447c7b1b8ea9")
      put("createdOn", "2024-05-30T06:31:23.287+0000")
      put("objectType", "Collection")
      put("nodeUniqueId", "do_21406607918175846411")
      put("requestId", null)
      put("operationType", "CREATE")
      put("nodeGraphId", 367.asInstanceOf[Integer])
      put("graphId", "domain")
    }
    println("event  ->" + JsonUtils.serialize(event))
  }

}
