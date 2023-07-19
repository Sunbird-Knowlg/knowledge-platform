package org.sunbird.util

import java.util
import scala.collection.JavaConverters._

object AccessVerifierUtil {

  def checkAccess(accessRules: List[AnyRef], userPayload: Map[String, AnyRef]): Boolean = {
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
