package org.sunbird.managers

import org.sunbird.common.dto.Request
import org.sunbird.graph.nodes.DataNode
import scala.concurrent.{ExecutionContext, Future}

object BaseFrameworkManager {

    def validateObject(channelId:String)(implicit ec: ExecutionContext) : Future[Boolean] ={
        var request = new Request()
        request.put("identifier",channelId )
        val contextMap: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef](){
            put("graph_id", "domain")
            put("version" , "1.0")
            put("objectType" , "Channel")
            put("schemaName", "channel")
        }
        request.setObjectType("Channel")
        request.setContext(contextMap)
        DataNode.read(request).map( node => {
            if(null != node)
                true
            else 
                false
        })
    }
}
