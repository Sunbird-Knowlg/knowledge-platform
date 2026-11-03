package org.sunbird.content.dynamicassess.mgr

import org.apache.commons.lang3.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.ClientException
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.content.publish.mgr.PublishManager
import org.sunbird.content.util.ContentConstants
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.service.common.DACConfigurationConstants
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.util.HttpUtil

import java.util
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object DynamicAssessManager {

	private lazy val httpUtil = new HttpUtil

	def refreshBodyContent(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] = {
		val identifier: String = request.getContext.getOrDefault(ContentConstants.IDENTIFIER, "").asInstanceOf[String]
		val publisher: String = request.getRequest.getOrDefault(ContentConstants.LAST_PUBLISHED_BY, "").asInstanceOf[String]

		if (publisher.isBlank) throw new ClientException("ERR_CONTENT_BLANK_PUBLISHER", "Publisher User Id is blank")

		val readReq = new Request(request)
		readReq.put(ContentConstants.IDENTIFIER, identifier)
		readReq.put(ContentConstants.MODE, ContentConstants.EDIT_MODE)
		DataNode.read(readReq).map(node => {
			if (null != node & StringUtils.isNotBlank(node.getObjectType))
				request.getContext.put(ContentConstants.SCHEMA_NAME, node.getObjectType.toLowerCase())
			if (StringUtils.equalsAnyIgnoreCase(ContentConstants.PROCESSING, node.getMetadata.getOrDefault(ContentConstants.STATUS, "").asInstanceOf[String]))
				throw new ClientException("ERR_NODE_ACCESS_DENIED", "Refresh Body Operation Can't Be Applied On Node Under Processing State")
			// The flag workflow owns flagged content's edit copy (AcceptFlagManager creates a FlagDraft one), so a refresh must not create a plain Draft copy alongside it.
			if (StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault(ContentConstants.STATUS, "").asInstanceOf[String], ContentConstants.FLAGGED, ContentConstants.FLAG_DRAFT, "FlagReview"))
				throw new ClientException("ERR_NODE_ACCESS_DENIED", "Refresh Body Operation Can't Be Applied On Flagged Content")
			validateSkillPoolCandidates(node)
			ensureEditableCopy(request, node).map(editable => {
				editable.getMetadata.put(ContentConstants.LAST_PUBLISHED_BY, publisher)
				PublishManager.refreshBody(request, editable)
			}).flatten
		}).flatten
	}

	// Generated questions must go through review (same as QuestionSet, whose /add writes to its edit copy), so published content gets its ".img" edit copy via the normal versioned update, like AcceptFlagManager.
	private def ensureEditableCopy(request: Request, node: Node)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
		val status = node.getMetadata.getOrDefault(ContentConstants.STATUS, "").asInstanceOf[String]
		if (node.getIdentifier.endsWith(".img") || !List(ContentConstants.LIVE, "Unlisted").contains(status)) Future(node)
		else {
			val req = new Request(request)
			req.getContext.put(ContentConstants.IDENTIFIER, node.getIdentifier)
			req.put(ContentConstants.IDENTIFIER, node.getIdentifier)
			req.put(ContentConstants.VERSION_KEY, Platform.config.getString(DACConfigurationConstants.PASSPORT_KEY_BASE_PROPERTY))
			DataNode.update(req)
		}
	}

	// Rejects a Dynamic Course Assessment's refresh outright if any selected skill's pool is below minCriteria x multiplier.
	private def validateSkillPoolCandidates(node: Node): Unit = {
		if (!StringUtils.equals(node.getMetadata.getOrDefault("primaryCategory", "").asInstanceOf[String], "Dynamic Course Assessment")) return
		val framework = node.getMetadata.getOrDefault("framework", "").asInstanceOf[String]
		// The selected skills are stored in the framework's deepest category field, the same field the pool is searched by.
		val categoryField = resolveCategoryField(framework)
		val skills = selectedSkills(node.getMetadata.get(categoryField))
		if (skills.isEmpty) return
		val threshold = dynamicAssessSetting(node, "minCriteria", 0, 2) * dynamicAssessSetting(node, "multiplier", 1, 2)
		val insufficient = skills.filterNot(hasSufficientPoolCandidates(_, categoryField, "AssessmentItem", threshold))
		if (insufficient.nonEmpty)
			throw new ClientException("ERR_SKILL_INSUFFICIENT_POOL", s"Fewer than $threshold Live questions found for skill(s): ${insufficient.mkString(", ")}")
	}

	// A Dynamic Course Assessment stores its own minCriteria and multiplier, from config for now, so the editors and the publish job enforce the same values.
	def setDynamicAssessSettings(request: Request): Unit = {
		if (!StringUtils.equals(String.valueOf(request.getRequest.getOrDefault("primaryCategory", "")), "Dynamic Course Assessment")) return
		request.put("minCriteria", Integer.valueOf(Platform.getInteger("dynamicassess.minCriteria", 2)))
		request.put("multiplier", Integer.valueOf(Platform.getInteger("dynamicassess.multiplier", 2)))
	}

	// The node's own setting, or config's for content created before it was stored (or holding a value below `min`).
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
			val resp = httpUtil.get(url)
			if (resp.status != 200) return "skill"
			val body = JsonUtils.deserialize(resp.body, classOf[java.util.Map[String, AnyRef]])
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
			val resp = httpUtil.post(url, JsonUtils.serialize(reqMap))
			if (resp.status != 200) return true
			val body = JsonUtils.deserialize(resp.body, classOf[java.util.Map[String, AnyRef]])
			val result = body.getOrDefault("result", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
			result.getOrDefault("count", Integer.valueOf(0)).asInstanceOf[Number].intValue() >= threshold
		} catch {
			case _: Exception => true
		}
	}
}
