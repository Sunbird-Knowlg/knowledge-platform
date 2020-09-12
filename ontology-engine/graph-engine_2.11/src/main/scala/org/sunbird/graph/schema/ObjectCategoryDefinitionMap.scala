package org.sunbird.graph.schema

import org.sunbird.common.Slug

object ObjectCategoryDefinitionMap {
    
    var categoryDefinitionMap: Map[String, Map[String, AnyRef]] = Map[String, Map[String, AnyRef]]()
    
    def get(id: String):Map[String, AnyRef] = {
        categoryDefinitionMap.getOrElse(id, null)
    }
    
    def put(id: String, data: Map[String, AnyRef]) = {
        categoryDefinitionMap += (id -> data)
    }
    
    def containsKey(id: String): Boolean = {
        categoryDefinitionMap.contains(id)
    }

    def remove(id: String) = {
        categoryDefinitionMap -= id
    }
    
    def prepareCategoryId(categoryName: String, objectType: String, channel: String = "all") = {
        if(!categoryName.isBlank)
            "obj-cat"+ ":" + Slug.makeSlug(categoryName + "_" + objectType + "_" + channel, true)
        else ""
    }
}
