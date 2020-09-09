package org.sunbird.util

object ChannelConstants {
  val DEFAULT_LICENSE: String = "defaultLicense"
  val NODE_ID: String = "node_id"
  val CHANNEL_LICENSE_CACHE_PREFIX: String = "channel_"
  val CHANNEL_LICENSE_CACHE_SUFFIX: String = "_license"
  val LICENSE_REDIS_KEY: String = "edge_license"
  val CONTENT_PRIMARY_CATEGORIES: String = "contentPrimaryCategories"
  val COLLECTION_PRIMARY_CATEGORIES: String = "collectionPrimaryCategories"
  val ASSET_PRIMARY_CATEGORIES: String = "assetPrimaryCategories"
  val CONTENT: String = "content"
  val NAME: String = "name"
  val categoryKeyList: List[String] = List(CONTENT_PRIMARY_CATEGORIES,COLLECTION_PRIMARY_CATEGORIES,ASSET_PRIMARY_CATEGORIES)
}
