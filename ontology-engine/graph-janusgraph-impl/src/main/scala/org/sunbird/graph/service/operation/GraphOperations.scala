package org.sunbird.graph.service.operation

import org.apache.tinkerpop.gremlin.driver.Result
import org.janusgraph.core.JanusGraphTransaction
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.transaction.TransactionLog
import org.sunbird.graph.service.common.GraphOperation
import org.sunbird.graph.service.util.DriverUtil

import java.util

object GraphOperations {

  def queryHandler(graphId: String, query: String, nodeId: String, edgeMap: util.Map[String, AnyRef]): util.List[Result] = {
    val client = DriverUtil.getGraphClient(graphId, GraphOperation.WRITE)
    val transactionData: util.Map[AnyRef, AnyRef] = new util.HashMap[AnyRef, AnyRef]
    if(edgeMap.isEmpty){
      val oldData = getElementsMapByNodeId(graphId, nodeId)
      transactionData.put("oldData", oldData)
      val results = client.submit(query).all().get()
      val newData = getElementsMapByNodeId(graphId, nodeId)
      transactionData.put("newData", newData)
      TransactionLog.getMessageObj(graphId, transactionData)
      results
    } else {
      transactionData.put("edgeData", edgeMap)
      val results = client.submit(query).all().get()
      TransactionLog.getMessageObj(graphId, transactionData)
      results
    }
  }

  private def getElementsMapByNodeId(graphId: String, identifier: AnyRef): util.Map[AnyRef, AnyRef] = {
    var resultMap: util.Map[AnyRef, AnyRef] = new util.HashMap[AnyRef, AnyRef]
    val client = DriverUtil.getGraphClient(graphId, GraphOperation.READ)
    val query = s"g.V().hasLabel('$graphId').has('${SystemProperties.IL_UNIQUE_ID}', '$identifier').elementMap()"
    val resultSet = client.submit(query)
    val result = resultSet.one()
    if (result != null) {
      resultMap = result.getObject.asInstanceOf[util.Map[AnyRef, AnyRef]]
    }
    resultMap
  }

  def graphOpenTxn(graphId: String): Unit = {
    val client = DriverUtil.getGraphClient(graphId, GraphOperation.READ)
    val txStartQuery = "graph.tx().open()"
    client.submit(txStartQuery).all().get()
  }

  def graphCloseTxn(graphId: String): Unit = {
    val client = DriverUtil.getGraphClient(graphId, GraphOperation.READ)
    val txCommitQuery = "graph.tx().commit()"
    client.submit(txCommitQuery).all().get()
  }

}
