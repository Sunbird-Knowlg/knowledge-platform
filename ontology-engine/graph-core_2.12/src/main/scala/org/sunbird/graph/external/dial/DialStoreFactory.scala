package org.sunbird.graph.external.dial


import java.util

object DialStoreFactory {

  private val PRIMARY_KEY = util.Arrays.asList("content_id")
  var externalStores: Map[String, DialStore] = Map()

  def getDialStore(externalStoreName: String, primaryKey: util.List[String]): DialStore = {
    val keySpace = externalStoreName.split("\\.")(0);
    val table = externalStoreName.split("\\.")(1);
    val key = getKey(keySpace, table)
    val store = externalStores.getOrElse(key, new DialStore(keySpace, table, primaryKey))
    if (!externalStores.contains(key))
      externalStores += (key -> store)
    store
  }

  private def getKey(keySpace: String, table: String) = {
    "store-" + keySpace + "-" + table
  }

}


