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

    "validate objectCategory with contentPrimaryCategory" should "success if objectCategory present" in {
        val request = new Request()
        request.setRequest(new util.HashMap[String, AnyRef]() {
            {
                put("contentPrimaryCategory", new util.ArrayList[String]() {
                    {
                        add("-text")
                    }
                })
            }
        })
        ChannelManager.validateObjectCategory(request)
        assert(true)
    }


    "validate objectCategory with all type" should "success if objectCategory present" in {
        val request = new Request()
        request.setRequest(new util.HashMap[String, AnyRef]() {
            {
                put("contentPrimaryCategory", new util.ArrayList[String]() {
                    {
                        add("-text")
                    }
                })
                put("collectionPrimaryCategory", new util.ArrayList[String]() {
                    {
                        add("-text")
                    }
                })
                put("assetPrimaryCategory", new util.ArrayList[String]() {
                    {
                        add("-text")
                    }
                })
            }
        })
        ChannelManager.validateObjectCategory(request)
        assert(true)
    }

    "validate objectCategory" should "throw exception" in {
        val exception = intercept[ClientException] {
            val request = new Request()
            request.setRequest(new util.HashMap[String, AnyRef]() {
                {
                    put("contentPrimaryCategory", new util.ArrayList[String]() {
                        {
                            add("xyz")
                        }
                    })
                }
            })
            ChannelManager.validateObjectCategory(request)
        }
        exception.getMessage shouldEqual "Please provide valid category object."
    }
}
