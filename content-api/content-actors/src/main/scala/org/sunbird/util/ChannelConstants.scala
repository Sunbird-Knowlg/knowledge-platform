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
  val OBJECT_CATEGORY: String = "ObjectCategory"
  val ERR_VALIDATING_PRIMARY_CATEGORY: String = "ERR_VALIDATING_PRIMARY_CATEGORY"
  val CONTENT_ADDITIONAL_CATEGORIES: String = "contentAdditionalCategories"
  val COLLECTION_ADDITIONAL_CATEGORIES: String = "collectionAdditionalCategories"
  val ASSET_ADDITIONAL_CATEGORIES: String = "assetAdditionalCategories"
  val categoryKeyList: List[String] = List(CONTENT_PRIMARY_CATEGORIES, COLLECTION_PRIMARY_CATEGORIES, ASSET_PRIMARY_CATEGORIES,
    CONTENT_ADDITIONAL_CATEGORIES, COLLECTION_ADDITIONAL_CATEGORIES, ASSET_ADDITIONAL_CATEGORIES)

}
