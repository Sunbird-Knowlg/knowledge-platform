package org.sunbird.util

object ChannelConstants {
  val DEFAULT_LICENSE: String = "defaultLicense"
  val NODE_ID: String = "node_id"
  val CHANNEL_LICENSE_CACHE_PREFIX: String = "channel_"
  val CHANNEL_LICENSE_CACHE_SUFFIX: String = "_license"
  val LICENSE_REDIS_KEY: String = "edge_license"
  val CONTENT_PRIMARY_CATEGORY: String = "contentPrimaryCategory"
  val COLLECTION_PRIMARY_CATEGORY: String = "collectionPrimaryCategory"
  val ASSET_PRIMARY_CATEGORY: String = "assetPrimaryCategory"
  val CONTENT: String = "content"
  val NAME: String = "name"
  val categoryKeyList: List[String] = List(CONTENT_PRIMARY_CATEGORY,COLLECTION_PRIMARY_CATEGORY,ASSET_PRIMARY_CATEGORY)
}
