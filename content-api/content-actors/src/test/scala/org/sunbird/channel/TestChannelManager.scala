package org.sunbird.channel

import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.common.dto.Request
import java.util

import org.apache.commons.collections.CollectionUtils

import org.sunbird.cache.impl.RedisCache
import org.sunbird.channel.managers.ChannelManager
import org.sunbird.common.exception.ClientException
import org.sunbird.util.ChannelConstants
import org.sunbird.channel.managers.ChannelManager
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ResponseCode}


class TestChannelManager extends AsyncFlatSpec with Matchers {

    "get All framework list" should "return a list of frameworks from search service" in {
        val frameworkList = ChannelManager.getAllFrameworkList()
        assert(CollectionUtils.isNotEmpty(frameworkList))
    }

    "validate translation map" should "throw exception if map contains invalid language translation" in {
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

    "store license in cache" should "store license in cache" in {
        val request = new Request()
        request.getRequest.put("defaultLicense","license1234")
        ChannelManager.channelLicenseCache(request, "channel_test")
        assert(null != RedisCache.get("channel_channel_test_license"))
    }

    it should "return success for valid objectCategory" in {
        val request = new Request()
        request.setRequest(new util.HashMap[String, AnyRef]() {{
                put(ChannelConstants.CONTENT_PRIMARY_CATEGORIES, new util.ArrayList[String]() {{add("Learning Resource")}})
                put(ChannelConstants.COLLECTION_PRIMARY_CATEGORIES, new util.ArrayList[String]() {{add("Learning Resource")}})
                put(ChannelConstants.ASSET_PRIMARY_CATEGORIES, new util.ArrayList[String]() {{add("Learning Resource")}})
        }})
        ChannelManager.validateObjectCategory(request)
        assert(true)
    }

    it should "throw exception for invalid objectCategory" in {
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

    it should "throw exception for empty objectCategory" in {
        val exception = intercept[ClientException] {
            val request = new Request()
            request.setRequest(new util.HashMap[String, AnyRef]() {{
                    put(ChannelConstants.CONTENT_PRIMARY_CATEGORIES, new util.ArrayList[String]())
                }})
            ChannelManager.validateObjectCategory(request)
        }
        exception.getMessage shouldEqual "Empty list not allowed for contentPrimaryCategories"
    }

    it should "throw exception for invalid dataType for objectCategory" in {
        val exception = intercept[ClientException] {
            val request = new Request()
            request.setRequest(new util.HashMap[String, AnyRef]() {{
                    put(ChannelConstants.CONTENT_PRIMARY_CATEGORIES, "test-string")
                }})
            ChannelManager.validateObjectCategory(request)
        }
        exception.getMessage shouldEqual "Please provide valid list for contentPrimaryCategories"
    }

    it should "add objectCategory into channel read response" in {
        val metaDataMap: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]()
        ChannelManager.getObjectCategories(metaDataMap)
        assert(metaDataMap.containsKey(ChannelConstants.CONTENT_PRIMARY_CATEGORIES))
        assert(CollectionUtils.isNotEmpty(metaDataMap.get(ChannelConstants.CONTENT_PRIMARY_CATEGORIES).asInstanceOf[util.ArrayList[String]]))
    }

    it should "not change objectCategory into channel read response" in {
        val metaDataMap: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](){{
            put(ChannelConstants.CONTENT_PRIMARY_CATEGORIES, new util.ArrayList[String]() {{add("Learning Resource")}})
        }}
        ChannelManager.getObjectCategories(metaDataMap)
        assert(metaDataMap.containsKey(ChannelConstants.CONTENT_PRIMARY_CATEGORIES))
        assert(CollectionUtils.isEqualCollection(metaDataMap.get(ChannelConstants.CONTENT_PRIMARY_CATEGORIES).asInstanceOf[util.ArrayList[String]],
            new util.ArrayList[String]() {{add("Learning Resource")}}))
    }
}
