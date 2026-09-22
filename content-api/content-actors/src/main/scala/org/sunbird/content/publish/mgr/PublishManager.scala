package org.sunbird.content.publish.mgr

import org.apache.commons.lang3.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.Platform
import org.sunbird.common.dto.ResponseParams.StatusType
import org.sunbird.common.dto.{Request, Response, ResponseParams}
import org.sunbird.common.exception.ClientException
import org.sunbird.content.util.ContentConstants
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.kafka.client.KafkaClient
import org.sunbird.mimetype.factory.MimeTypeManagerFactory
import org.sunbird.telemetry.util.LogTelemetryEventUtil

import java.util
import scala.concurrent.{ExecutionContext, Future}

object PublishManager {

	private val kfClient = new KafkaClient

	def publish(request: Request, node: Node)(implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] =
		publishWithAction(request, node, ContentConstants.PUBLISH)

	/**
	 * Refresh Body: re-runs pool-based question selection for a Content (ECML) without a full
	 * creator-driven publish. Pushes edata.action = "refresh-body" instead of "publish", onto the
	 * same instruction topic; the async publish job (RefreshBodyFunction-equivalent) picks it up
	 * and rebuilds `body` from the live question pool.
	 */
	def refreshBody(request: Request, node: Node)(implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] =
		publishWithAction(request, node, ContentConstants.REFRESH_BODY)

	private def publishWithAction(request: Request, node: Node, action: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] = {
		val identifier: String = node.getIdentifier
		val mimeType = node.getMetadata.getOrDefault(ContentConstants.MIME_TYPE, "").asInstanceOf[String]
		val mgr = MimeTypeManagerFactory.getManager(node.getObjectType, mimeType)

		val publishCheckList = request.getContext.getOrDefault(ContentConstants.PUBLIC_CHECK_LIST, List.empty[String]).asInstanceOf[List[String]]
		if (publishCheckList.isEmpty) node.getMetadata.put(ContentConstants.PUBLIC_CHECK_LIST, null)

		val publishType = request.getContext.getOrDefault(ContentConstants.PUBLISH_TYPE, "").asInstanceOf[String]
		node.getMetadata.put(ContentConstants.PUBLISH_TYPE, publishType)

		val status = node.getMetadata.getOrDefault(ContentConstants.STATUS, "").asInstanceOf[String]
		if (action == ContentConstants.REFRESH_BODY && (StringUtils.equalsIgnoreCase(status, ContentConstants.DRAFT) || StringUtils.equalsIgnoreCase(status, ContentConstants.REVIEW_STATUS))) {
			// A Draft/Review node has no live ECAR to refresh yet; just push the trigger, skip mgr.publish's validation/status transition.
			pushInstructionEvent(identifier, node, action)
			val response = new Response
			val param = new ResponseParams
			param.setStatus(StatusType.successful.name)
			response.setParams(param)
			response.put(ContentConstants.PUBLISH_STATUS, s"Refresh Body Event for Content Id '${node.getIdentifier}' is pushed Successfully!")
			response.put(ContentConstants.NODE_ID, node.getIdentifier)
			Future(response)
		} else {
			val publishFuture: Future[scala.collection.Map[String, AnyRef]] = mgr.publish(identifier, node)
			publishFuture.map(result => {
				// Push Instruction Event - Learning code has logic to send publish instruction to different topics based on mimeTypes. That logic is not implemented here due to deprecation of samza jobs.
				pushInstructionEvent(identifier, node, action)

				val response = new Response
				val param = new ResponseParams
				param.setStatus(StatusType.successful.name)
				response.setParams(param)
				val statusMsg = if (action == ContentConstants.REFRESH_BODY) "Refresh Body Event" else "Publish Event"
				response.put(ContentConstants.PUBLISH_STATUS, s"$statusMsg for Content Id '${node.getIdentifier}' is pushed Successfully!")
				response.put(ContentConstants.NODE_ID, node.getIdentifier)

				Future(response)
			}).flatten
		}
	}

	@throws[Exception]
	private def pushInstructionEvent(identifier: String, node: Node, action: String): Unit = {
		val actor: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
		val context: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
		val objectData: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
		val edata: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
		generateInstructionEventMetadata(actor, context, objectData, edata, node, identifier, action)
		val beJobRequestEvent: String = LogTelemetryEventUtil.logInstructionEvent(actor, context, objectData, edata)
		val topic: String = Platform.getString(ContentConstants.KAFKA_PUBLISH_TOPIC,"sunbirddev.publish.job.request")
		if (StringUtils.isBlank(beJobRequestEvent)) throw new ClientException("BE_JOB_REQUEST_EXCEPTION", "Event is not generated properly.")
		kfClient.send(beJobRequestEvent, topic)
	}

	private def generateInstructionEventMetadata(actor: util.Map[String, AnyRef], context: util.Map[String, AnyRef], objectData: util.Map[String, AnyRef], edata: util.Map[String, AnyRef], node: Node, identifier: String, action: String): Unit = {
		val metadata: util.Map[String, AnyRef] = node.getMetadata

		// actor
		actor.put(ContentConstants.ID, node.getObjectType.toLowerCase() + "-publish")
		actor.put(ContentConstants.TYPE, ContentConstants.SYSTEM)

		//context
		context.put(ContentConstants.CHANNEL, metadata.get(ContentConstants.CHANNEL))
		context.put(ContentConstants.P_DATA, new util.HashMap[String, AnyRef]() {{
			put(ContentConstants.ID, ContentConstants.SUNBIRD_PLATFORM)
			put(ContentConstants.VER, ContentConstants.SCHEMA_VERSION)
		}})
		if (Platform.config.hasPath("cloud_storage.env")) {
			val env: String = Platform.getString("cloud_storage.env", "dev")
			context.put(ContentConstants.ENV, env)
		}

		//objectData
		objectData.put(ContentConstants.ID, identifier.replace(".img",""))
		objectData.put(ContentConstants.VER, metadata.get(ContentConstants.VERSION_KEY))

		//edata
		getEData(metadata, edata, identifier, node.getObjectType, action)
	}

	private def getEData(metadata: util.Map[String, AnyRef], edata:  util.Map[String, AnyRef], identifier: String, objectType: String, action: String): Unit = {
		val instructionEventMetadata = new util.HashMap[String, AnyRef]
		edata.put(ContentConstants.PUBLISH_TYPE, metadata.get(ContentConstants.PUBLISH_TYPE))
		instructionEventMetadata.put(ContentConstants.PACKAGE_VERSION, metadata.getOrDefault(ContentConstants.PACKAGE_VERSION,0.asInstanceOf[AnyRef]))
		instructionEventMetadata.put(ContentConstants.MIME_TYPE, metadata.get(ContentConstants.MIME_TYPE))
		instructionEventMetadata.put(ContentConstants.LAST_PUBLISHED_BY, metadata.get(ContentConstants.LAST_PUBLISHED_BY))
		instructionEventMetadata.put(ContentConstants.IDENTIFIER, identifier.replace(".img",""))
		instructionEventMetadata.put(ContentConstants.OBJECT_TYPE, objectType)
		edata.put(ContentConstants.METADATA, instructionEventMetadata)
		edata.put(ContentConstants.ACTION, action)
		edata.put(ContentConstants.CONTENT_TYPE, metadata.get(ContentConstants.CONTENT_TYPE))
	}
}


