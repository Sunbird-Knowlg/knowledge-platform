package org.sunbird.graph.external.store

import java.util
import java.util.{Arrays, List}

object ExternalStoreFactory {

    private val PRIMARY_KEY = util.Arrays.asList("identifier")
    var externalStores: Map[String, ExternalStore] = Map()

    def getExternalStore(externalStoreName: String): ExternalStore = {
        val keySpace = externalStoreName.split("\\.")(0);
        val table = externalStoreName.split("\\.")(1);
        val key = getKey(keySpace,table)
        val store = externalStores.getOrElse(key, new ExternalStore(keySpace, table, PRIMARY_KEY))
        if(!externalStores.contains(key))
            externalStores += (key -> store)
        store
    }

    private def getKey(keySpace: String, table: String) = {
        "store-" + keySpace + "-" + table
    }

}
