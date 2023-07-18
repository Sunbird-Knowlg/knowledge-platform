package org.sunbird.util

import org.apache.commons.lang3.StringUtils
import org.sunbird.auth.verifier.AccessTokenValidator
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException

import scala.collection.JavaConverters._

import java.util

object AccessVerifierUtil {


  def checkAccess(accessRules: List[AnyRef], request: Request): Boolean = {
    val accessToken = request.getRequest.getOrDefault("consumerId","").asInstanceOf[String]
    if(StringUtils.isEmpty(accessToken)) throw new ClientException("ERR_CONTENT_ACCESS_RESTRICTED", "Please provide valid user token ")

    val userPayload: Map[String, AnyRef] = AccessTokenValidator.verifyUserToken(accessToken, request.getContext).asScala.toMap[String, AnyRef]

    val passedRules = accessRules.filter(accessCondition => {
      val convertedAccessCondition = accessCondition.asInstanceOf[java.util.HashMap[String, AnyRef]].asScala.toMap[String, AnyRef]
      val passedConditions =	convertedAccessCondition.filter(record => {
        userPayload.contains(record._1) && record._2.asInstanceOf[util.ArrayList[String]].asScala.toList.contains(userPayload.getOrElse(record._1,"").asInstanceOf[String])
      })
      if(passedConditions.size == convertedAccessCondition.size) true else false
    })

    if(passedRules.nonEmpty) true else false
  }


}
