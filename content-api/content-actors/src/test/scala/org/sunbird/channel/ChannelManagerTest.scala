package org.sunbird.channel

import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.common.dto.Request
import java.util

import org.sunbird.util.ChannelConstants
import org.sunbird.channel.managers.ChannelManager
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ResponseCode}



class ChannelManagerTest extends AsyncFlatSpec with Matchers{

    "validate license with wrong license" should "should throw client exception" in {
        val exception = intercept[ResourceNotFoundException] {
            val request = new Request()
            request.setRequest(new util.HashMap[String, AnyRef]() {{put(ChannelConstants.DEFAULT_LICENSE, "CC Random")}})
            ChannelManager.validateLicense(request)
        }
        exception.getMessage shouldEqual "License CC Random does not exist"
    }

    "validate license from redis" should "validate the default license from redis" in {
        val request = new Request()
        request.setRequest(new util.HashMap[String, AnyRef]() {{put(ChannelConstants.DEFAULT_LICENSE, "CC BY 4.0")}})
        val resp = ChannelManager.validateLicense(request)
        assert(resp.getResponseCode == ResponseCode.OK)
    }

    "validate license from composite" should "validate the default license from search call" in {
        val request = new Request()
        request.setRequest(new util.HashMap[String, AnyRef]() {{put(ChannelConstants.DEFAULT_LICENSE, "CC-BY-ND")}})
        val resp = ChannelManager.validateLicense(request)
            assert(resp.getResponseCode == ResponseCode.OK)
    }

    def getRequest(): Request = {
        val request = new Request()
        request
    }
}
