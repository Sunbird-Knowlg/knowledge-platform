package org.sunbird.channel

import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.common.dto.Request

import java.util
import org.apache.commons.collections.CollectionUtils
import org.scalamock.scalatest.MockFactory
import org.sunbird.cache.impl.RedisCache
import org.sunbird.util.{ChannelConstants, HTTPResponse, HttpUtil}
import org.sunbird.channel.managers.ChannelManager
import org.sunbird.common.exception.{ClientException}


class TestChannelManager extends AsyncFlatSpec with Matchers with MockFactory {

  implicit val httpUtil: HttpUtil = mock[HttpUtil]

  "ChannelManager" should "return a list of frameworks from search service" in {
      val frameworkList = ChannelManager.getAllFrameworkList()
      assert(CollectionUtils.isNotEmpty(frameworkList))
  }

  ignore should "throw exception if map contains invalid language translation" in {
      val exception = intercept[ClientException] {
          val request = new Request()
          request.setRequest(new util.HashMap[String, AnyRef]() {
              {
                  put("translations", new util.HashMap[String, AnyRef]() {
                      {
                          put("tyy", "dsk")
                      }
                  })
              }
          })
          ChannelManager.validateTranslationMap(request)
      }
      exception.getMessage shouldEqual "Please Provide Valid Language Code For translations. Valid Language Codes are : [as, bn, en, gu, hi, hoc, jun, ka, mai, mr, unx, or, san, sat, ta, te, urd, pj]"
  }

  def getRequest(): Request = {
      val request = new Request()
      request
  }

  ignore should "store license in cache" in {
      val request = new Request()
      request.getRequest.put("defaultLicense","license1234")
      ChannelManager.channelLicenseCache(request, "channel_test")
      assert(null != RedisCache.get("channel_channel_test_license"))
  }

  ignore should "return success for valid objectCategory" in {
      val request = new Request()
      request.setRequest(new util.HashMap[String, AnyRef]() {{
              put(ChannelConstants.CONTENT_PRIMARY_CATEGORIES, new util.ArrayList[String]() {{add("Learning Resource")}})
              put(ChannelConstants.COLLECTION_PRIMARY_CATEGORIES, new util.ArrayList[String]() {{add("Learning Resource")}})
              put(ChannelConstants.ASSET_PRIMARY_CATEGORIES, new util.ArrayList[String]() {{add("Learning Resource")}})
      }})
      ChannelManager.validateObjectCategory(request)
      assert(true)
  }

  ignore should "throw exception for invalid objectCategory" in {
      val exception = intercept[ClientException] {
          val request = new Request()
          request.setRequest(new util.HashMap[String, AnyRef]() {{
              put(ChannelConstants.CONTENT_PRIMARY_CATEGORIES, new util.ArrayList[String]() {{add("xyz")}})
              put(ChannelConstants.COLLECTION_PRIMARY_CATEGORIES, new util.ArrayList[String]() {{add("xyz")}})
              put(ChannelConstants.ASSET_PRIMARY_CATEGORIES, new util.ArrayList[String]() {{add("xyz")}})
          }})
          ChannelManager.validateObjectCategory(request)
      }
      exception.getMessage shouldEqual "Please provide valid : [contentPrimaryCategories,collectionPrimaryCategories,assetPrimaryCategories]"
  }

  ignore should "throw exception for empty objectCategory" in {
      val exception = intercept[ClientException] {
          val request = new Request()
          request.setRequest(new util.HashMap[String, AnyRef]() {{
                  put(ChannelConstants.CONTENT_PRIMARY_CATEGORIES, new util.ArrayList[String]())
              }})
          ChannelManager.validateObjectCategory(request)
      }
      exception.getMessage shouldEqual "Empty list not allowed for contentPrimaryCategories"
  }

  ignore should "throw exception for invalid dataType for objectCategory" in {
      val exception = intercept[ClientException] {
          val request = new Request()
          request.setRequest(new util.HashMap[String, AnyRef]() {{
                  put(ChannelConstants.CONTENT_PRIMARY_CATEGORIES, "test-string")
              }})
          ChannelManager.validateObjectCategory(request)
      }
      exception.getMessage shouldEqual "Please provide valid list for contentPrimaryCategories"
  }

  ignore should "add objectCategory into channel read response" in {
      val metaDataMap: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]()
      ChannelManager.setPrimaryAndAdditionCategories(metaDataMap)
      assert(metaDataMap.containsKey(ChannelConstants.CONTENT_PRIMARY_CATEGORIES))
      assert(CollectionUtils.isNotEmpty(metaDataMap.get(ChannelConstants.CONTENT_PRIMARY_CATEGORIES).asInstanceOf[util.List[String]]))
  }

  ignore should "not change objectCategory into channel read response" in {
      val metaDataMap: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](){{
          put(ChannelConstants.CONTENT_PRIMARY_CATEGORIES, new util.ArrayList[String]() {{add("Learning Resource")}})
      }}
      ChannelManager.setPrimaryAndAdditionCategories(metaDataMap)
      assert(metaDataMap.containsKey(ChannelConstants.CONTENT_PRIMARY_CATEGORIES))
      assert(CollectionUtils.isEqualCollection(metaDataMap.get(ChannelConstants.CONTENT_PRIMARY_CATEGORIES).asInstanceOf[util.List[String]],
          new util.ArrayList[String]() {{add("Learning Resource")}}))
  }

  ignore should "return primary categories of a channel" in {
    (httpUtil.post _).expects(*, """{"request":{"filters":{"objectType":"ObjectCategoryDefinition"},"not_exists":"channel","fields":["name","identifier","targetObjectType"]}}""", *)
      .returning(HTTPResponse(200, """{"id":"api.v1.search","ver":"1.0","ts":"2021-02-15T05:25:54.939Z","params":{"resmsgid":"46b5e4b0-6f4e-11eb-90b0-bb9ae961ede2","msgid":"46b4d340-6f4e-11eb-90b0-bb9ae961ede2","status":"successful","err":null,"errmsg":null},"responseCode":"OK","result":{"count":24,"ObjectCategoryDefinition":[{"identifier":"obj-cat:asset_asset_all","name":"Asset","targetObjectType":"Asset","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:certasset_asset_all","name":"CertAsset","targetObjectType":"Asset","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:certificate-template_asset_all","name":"Certificate Template","targetObjectType":"Asset","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:content-playlist_content_all","name":"Content Playlist","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:content-playlist_collection_all","name":"Content Playlist","targetObjectType":"Collection","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:course_content_all","name":"Course","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:course_collection_all","name":"Course","targetObjectType":"Collection","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:course-assessment_content_all","name":"Course Assessment","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:course-unit_content_all","name":"Course Unit","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:course-unit_collection_all","name":"Course Unit","targetObjectType":"Collection","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:digital-textbook_content_all","name":"Digital Textbook","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:digital-textbook_collection_all","name":"Digital Textbook","targetObjectType":"Collection","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:etextbook_content_all","name":"eTextbook","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:explanation-content_content_all","name":"Explanation Content","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:explanation-content_collection_all","name":"Explanation Content","targetObjectType":"Collection","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:learning-resource_content_all","name":"Learning Resource","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:lesson-plan-unit_content_all","name":"Lesson Plan Unit","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:lesson-plan-unit_collection_all","name":"Lesson Plan Unit","targetObjectType":"Collection","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:plugin_content_all","name":"Plugin","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:practice-question-set_content_all","name":"Practice Question Set","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:teacher-resource_content_all","name":"Teacher Resource","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:template_content_all","name":"Template","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:textbook-unit_collection_all","name":"Textbook Unit","targetObjectType":"Collection","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:textbook-unit_content_all","name":"Textbook Unit","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"}]}}"""))
    (httpUtil.post _).expects(*, """{"request":{"filters":{"objectType":"ObjectCategoryDefinition", "channel": "01309282781705830427"},"fields":["name","identifier","targetObjectType"]}}""", *)
      .returning(HTTPResponse(200, """{"id":"api.v1.search","ver":"1.0","ts":"2021-02-15T05:31:41.939Z","params":{"resmsgid":"1589e430-6f4f-11eb-a956-2bb50a66d58f","msgid":"1588abb0-6f4f-11eb-a956-2bb50a66d58f","status":"successful","err":null,"errmsg":null},"responseCode":"OK","result":{"count":3,"ObjectCategoryDefinition":[{"identifier":"obj-cat:course_collection_01309282781705830427","name":"Course","targetObjectType":"Collection","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:exam-question_content_01309282781705830427","name":"Exam Question","targetObjectType":"Content","objectType":"ObjectCategoryDefinition"},{"identifier":"obj-cat:question-paper_collection_01309282781705830427","name":"Question Paper","targetObjectType":"Collection","objectType":"ObjectCategoryDefinition"}]}}"""))

    val primaryCategories = ChannelManager.getChannelPrimaryCategories("01309282781705830427")
    assert(primaryCategories.size() > 0)
  }

  ignore should "return additional categories" in {
    (httpUtil.post _).expects(*, """{"request":{"filters":{"objectType":"ObjectCategory"},"fields":["name","identifier"]}}""", *)
      .returning(HTTPResponse(200, """{"id":"api.v1.search","ver":"1.0","ts":"2021-02-15T08:06:20.058Z","params":{"resmsgid":"afba7fa0-6f64-11eb-a956-2bb50a66d58f","msgid":"afb8aae0-6f64-11eb-a956-2bb50a66d58f","status":"successful","err":null,"errmsg":null},"responseCode":"OK","result":{"ObjectCategory":[{"identifier":"obj-cat:asset","name":"Asset","objectType":"ObjectCategory"},{"identifier":"obj-cat:certificate-template","name":"Certificate Template","objectType":"ObjectCategory"},{"identifier":"obj-cat:classroom-teaching-video","name":"Classroom Teaching Video","objectType":"ObjectCategory"},{"identifier":"obj-cat:content-playlist","name":"Content Playlist","objectType":"ObjectCategory"},{"identifier":"obj-cat:course","name":"Course","objectType":"ObjectCategory"},{"identifier":"obj-cat:course-assessment","name":"Course Assessment","objectType":"ObjectCategory"}],"count":6}}"""))
    val additionalCategories = ChannelManager.getAdditionalCategories()
    assert(additionalCategories.size() > 0)
  }
}
