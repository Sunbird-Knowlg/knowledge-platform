package org.sunbird.v5.managers

import com.mashape.unirest.http.Unirest
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.AssessmentErrorCodes

import java.util
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object DynamicAssessManager {

  /** Refresh Body: re-runs pool selection for a Dynamic QuestionSet; unlike publish(), doesn't require existing children. */
  def refreshBody(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val lastPublishedBy: String = request.getRequest.getOrDefault("lastPublishedBy", "").asInstanceOf[String]
    val requestId = request.getContext().getOrDefault("requestId", "").asInstanceOf[String]
    val featureName = request.getContext().getOrDefault("featureName", "").asInstanceOf[String]
    request.getRequest.put("identifier", request.getContext.get("identifier"))
    request.put("mode", "edit")
    DataNode.read(request).map(node => {
      if (StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Processing"))
        throw new ClientException(AssessmentErrorCodes.ERR_OBJECT_VALIDATION, "QuestionSet having Processing status can't be sent for refresh.")
      validateSkillPoolCandidates(node)
      if (StringUtils.isNotBlank(lastPublishedBy))
        node.getMetadata.put("lastPublishedBy", lastPublishedBy)
      AssessmentV5Manager.pushRefreshBodyEvent(node.getIdentifier, node.getObjectType)
      ResponseHandler.OK.putAll(Map[String, AnyRef]("identifier" -> node.getIdentifier.replace(".img", ""), "message" -> "Refresh Body Event for QuestionSet is successfully sent for processing").asJava)
    })
  }

  // Rejects a Dynamic QuestionSet's refresh outright if any selected skill's pool is below minCriteria x multiplier.
  private def validateSkillPoolCandidates(node: Node): Unit = {
    if (!StringUtils.equals(node.getMetadata.getOrDefault("primaryCategory", "").asInstanceOf[String], "Dynamic QuestionSet")) return
    val framework = node.getMetadata.getOrDefault("framework", "").asInstanceOf[String]
    // The selected skills are stored in the framework's deepest category field, the same field the pool is searched by.
    val categoryField = resolveCategoryField(framework)
    val skills = selectedSkills(node.getMetadata.get(categoryField))
    if (skills.isEmpty) return
    val threshold = dynamicAssessSetting(node, "minCriteria", 0, 2) * dynamicAssessSetting(node, "multiplier", 1, 2)
    val insufficient = skills.filterNot(hasSufficientPoolCandidates(_, categoryField, "Question", threshold))
    if (insufficient.nonEmpty)
      throw new ClientException("ERR_SKILL_INSUFFICIENT_POOL", s"Fewer than $threshold Live questions found for skill(s): ${insufficient.mkString(", ")}")
  }

  // A Dynamic QuestionSet stores its own minCriteria and multiplier, from config for now, so the editors and the publish job enforce the same values.
  def withDynamicAssessSettings(request: Request): Request = {
    if (StringUtils.equals(String.valueOf(request.getRequest.getOrDefault("primaryCategory", "")), "Dynamic QuestionSet")) {
      request.put("minCriteria", Integer.valueOf(Platform.getInteger("dynamicassess.minCriteria", 2)))
      request.put("multiplier", Integer.valueOf(Platform.getInteger("dynamicassess.multiplier", 2)))
    }
    request
  }

  // The node's own setting, or config's for a set created before it was stored (or holding a value below `min`).
  private def dynamicAssessSetting(node: Node, field: String, min: Int, default: Int): Int = node.getMetadata.get(field) match {
    case n: Number if n.intValue >= min => n.intValue
    case s: String if s.trim.toIntOption.exists(_ >= min) => s.trim.toInt
    case _ => Platform.getInteger(s"dynamicassess.$field", default)
  }

  // A category field's terms as a list, whether the graph stored them as a list, an array or a single value.
  private def selectedSkills(value: AnyRef): List[String] = value match {
    case l: util.List[_] => l.asScala.map(String.valueOf).filter(StringUtils.isNotBlank).toList.distinct
    case a: Array[_] => a.map(String.valueOf).filter(StringUtils.isNotBlank).toList.distinct
    case s: String if StringUtils.isNotBlank(s) => List(s)
    case _ => Nil
  }

  // Deepest (highest-index) category code of the declared framework, e.g. "topic"/"skill"; falls back to "skill".
  private def resolveCategoryField(framework: String): String = {
    if (framework.isBlank) return "skill"
    if (!Platform.config.hasPath("taxonomy.framework.read.url")) {
      TelemetryManager.error(s"resolveCategoryField :: taxonomy.framework.read.url is not configured, falling back to 'skill' for framework=$framework")
      return "skill"
    }
    try {
      val url = Platform.getString("taxonomy.framework.read.url", "") + framework
      val resp = Unirest.get(url).header("Content-Type", "application/json").asString()
      if (resp.getStatus != 200) return "skill"
      val body = JsonUtils.deserialize(resp.getBody, classOf[java.util.Map[String, AnyRef]])
      val result = body.getOrDefault("result", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      val fw = result.getOrDefault("framework", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      val categories = fw.getOrDefault("categories", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala
      if (categories.isEmpty) "skill"
      else categories.maxBy(_.getOrDefault("index", Integer.valueOf(0)).asInstanceOf[Number].intValue()).getOrDefault("code", "skill").asInstanceOf[String]
    } catch {
      case _: Exception => "skill"
    }
  }

  // Fails open (treats as sufficient) on search errors so a transient outage doesn't block a legitimate refresh.
  private def hasSufficientPoolCandidates(skill: String, categoryField: String, poolObjectType: String, threshold: Int): Boolean = {
    try {
      val filters = new util.HashMap[String, AnyRef]()
      filters.put("status", util.Arrays.asList("Live"))
      filters.put("objectType", poolObjectType)
      filters.put(categoryField, util.Arrays.asList(skill))
      val reqMap = new util.HashMap[String, AnyRef]()
      reqMap.put("request", Map("filters" -> filters, "fields" -> util.Arrays.asList("identifier"), "limit" -> Integer.valueOf(1)).asJava)
      val url = Platform.getString("composite.search.url", "https://dev.sunbirded.org/action/composite/v3/search")
      val resp = Unirest.post(url).header("Content-Type", "application/json").body(JsonUtils.serialize(reqMap)).asString()
      if (resp.getStatus != 200) return true
      val body = JsonUtils.deserialize(resp.getBody, classOf[java.util.Map[String, AnyRef]])
      val result = body.getOrDefault("result", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      result.getOrDefault("count", Integer.valueOf(0)).asInstanceOf[Number].intValue() >= threshold
    } catch {
      case _: Exception => true
    }
  }
}
