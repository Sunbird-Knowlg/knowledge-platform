package org.sunbird.content.transcript.mgr

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.{JsonUtils, Platform, SafeUrlValidator}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.content.util.ContentConstants
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.kafka.client.KafkaClient
import org.sunbird.schema.SchemaValidatorFactory
import org.sunbird.telemetry.logger.TelemetryManager

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/**
 * Enrichment-node-mediated transcript/caption workflow. Every Content that
 * opts in gets one Enrichment node (its AI-feature container); every
 * language gets its own Transcript node linked to that Enrichment. Jobs in
 * the sunbird-ai-platform repo (enrichment-router, caption-generator)
 * consume/produce the same Kafka contracts this class emits/expects.
 */
object TranscriptManager {

  private val GRAPH_ID = "domain"
  private val SCHEMA_VERSION = "1.0"
  private val ENRICHMENT_OBJECT_TYPE = "Enrichment"
  private val ENRICHMENT_SCHEMA_NAME = "enrichment"
  private val TRANSCRIPT_OBJECT_TYPE = "Transcript"
  private val TRANSCRIPT_SCHEMA_NAME = "transcript"

  private val TRANSCRIPTION_TOPIC_KEY = "kafka.topics.media.transcription.request"
  private val DEFAULT_TRANSCRIPTION_TOPIC = "sunbirddev.media.transcription.request"
  private val ENRICHED_METADATA_TOPIC_KEY = "kafka.topics.enriched.metadata"
  private val DEFAULT_ENRICHED_METADATA_TOPIC = "sunbirddev.enriched.metadata"

  private val CONTENT_FOLDER = "cloud_storage.content.folder"
  private val ALLOW_FAILED_LANGUAGES_KEY = "content.transcript.ecar.allow_failed_languages"

  private val VALID_MIME_TYPES = Set("video/mp4", "video/webm")
  private val ACTIVE_STATUSES = Set("Processing", "Review", "Live")
  private val BLOCKING_ECAR_STATUSES = Set("Draft", "Review", "Processing")

  private val kfClient = new KafkaClient

  // ===================== PUBLIC API =====================
  // Backing implementation for EnrichmentObjectHandler (TranscriptObjectHandler).
  // ContentActor's generic object/{create,update,approve,reject} already
  // resolved contentNode/enrichmentNode (creating Enrichment if needed) and
  // validated objectType before calling any of these — no per-endpoint
  // Transcript routes exist anymore, this is reached only through the
  // generic dispatch.

  // POST /content/v4/object/create/:id — three shapes, discriminated by
  // request fields: multipart file -> human VTT upload; languageCode alone
  // (no artifactUrl) -> target-language Draft (absorbed from what
  // ai-pipeline's enrichment-router used to write directly via JanusGraph);
  // otherwise -> AI-trigger (re)generation of the source transcript.
  def createObject(request: Request, contentNode: Node, enrichmentNode: Node)
                   (implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] = {
    val isUpload = request.getRequest.containsKey("file") || request.getRequest.containsKey("fileUrl")
    val languageCode = request.getRequest.getOrDefault("languageCode", "").asInstanceOf[String]
    val artifactUrlParam = request.getRequest.getOrDefault("artifactUrl", "").asInstanceOf[String]

    if (isUpload) createFromUpload(request, contentNode, enrichmentNode)
    else if (StringUtils.isNotBlank(languageCode) && StringUtils.isBlank(artifactUrlParam))
      createTargetDraft(request, contentNode, enrichmentNode, languageCode)
    else createFromGeneration(request, contentNode, enrichmentNode)
  }

  // GET /content/v4/enrichment/read/:id — thin wrapper over fetchEnrichmentMetadata below.
  def readEnrichment(contentNode: Node)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    fetchEnrichmentMetadata(contentNode, requestedKeys = None).map {
      case None => throw new ClientException("ERR_NO_ENRICHMENT_FOUND", "No Enrichment node found for this content.")
      case Some(enrichmentMetadata) => ResponseHandler.OK.put("enrichment", enrichmentMetadata)
    }
  }

  // Live Content->Enrichment->children join (also used by content/v4/read's
  // ?enrich= param) — content/v4/read's own "enrichment" field is otherwise
  // just a denormalized snapshot from whenever that edge was last touched,
  // not live data. Child relation fields (e.g. "transcripts") are merged in
  // under their real schema-declared name via getRelationDefinitionMap, not
  // a hardcoded key, so a future relation (e.g. "summary") needs no code
  // change here. requestedKeys=None means "all"; Some(keys) filters to just
  // those relation fields. No Enrichment node for this content -> None.
  def fetchEnrichmentMetadata(contentNode: Node, requestedKeys: Option[Set[String]])
                              (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Option[util.Map[String, AnyRef]]] = {
    readEnrichmentForContent(contentNode).flatMap {
      case None => Future.successful(None)
      case Some(enrichmentRef) =>
        val readReq = buildTypedRequest(ENRICHMENT_OBJECT_TYPE, ENRICHMENT_SCHEMA_NAME, "", new util.HashMap[String, AnyRef]())
        readReq.getContext.put("identifier", enrichmentRef.getIdentifier)
        readReq.put("identifier", enrichmentRef.getIdentifier)
        readReq.put("fields", new util.ArrayList[String]())
        DataNode.read(readReq).flatMap { enrichmentNode =>
          val relations = Option(enrichmentNode.getOutRelations).map(_.asScala.toSeq).getOrElse(Seq())
          // getEndNodeObjectType/getEndNodeId/getRelationType are real
          // (sourced from the vertex's own IL_FUNC_OBJECT_TYPE/IL_UNIQUE_ID
          // and the edge's own label); everything else on the relation
          // object is not (see readTranscriptChildren's comment) — re-read
          // each child node fully instead.
          val relationDefMap = DefinitionNode.getRelationDefinitionMap(GRAPH_ID, SCHEMA_VERSION, ENRICHMENT_SCHEMA_NAME)
          Future.sequence(relations.map(r => readTypedNode(r.getEndNodeId, r.getEndNodeObjectType, r.getEndNodeObjectType.toLowerCase).map(node => (r, node)))).map { relNodePairs =>
            val enrichmentMetadata = new util.HashMap[String, AnyRef](enrichmentNode.getMetadata)
            // getMetadata alone never carries "identifier" (that's node.getIdentifier,
            // normally injected by NodeUtil.serialize — bypassed here). Without this,
            // callers acting on a specific child (e.g. approveTranscript/rejectTranscript's
            // body-level transcriptId, needed for anything but the default source
            // transcript) have no way to discover that child's real do_... id.
            enrichmentMetadata.put("identifier", enrichmentNode.getIdentifier)
            relNodePairs.groupBy { case (r, _) =>
              val relKey = s"${r.getRelationType}_out_${r.getEndNodeObjectType}"
              relationDefMap.getOrElse(relKey, r.getEndNodeObjectType).asInstanceOf[String]
            }.foreach { case (fieldName, pairs) =>
              if (requestedKeys.forall(_.contains(fieldName)))
                // Reconstructed fresh from the real relation every call -
                // syncEnrichmentTranscriptsFromNode computes an equivalent
                // snapshot in-memory but never persists it (see its own
                // comment), so there's no stale stored value to worry about.
                enrichmentMetadata.put(fieldName, pairs.map { case (_, node) =>
                  val childMetadata = new util.HashMap[String, AnyRef](node.getMetadata)
                  childMetadata.put("identifier", node.getIdentifier)
                  childMetadata
                }.asJava)
            }
            Some(enrichmentMetadata)
          }
        }
    }
  }

  // PATCH /content/v4/object/update/:id/:objectIdentifier — one endpoint, four
  // shapes, discriminated by which fields the caller sends:
  //  - "segments" present: human edits an existing Review transcript's text.
  //  - status="Processing": job reporting it has started work.
  //  - status="Live"/"Review" (+ artifactUrl/captionsUrl/languageCode/...): job reporting completion.
  //  - status="Failed" (+ errorMessage): job reporting failure.
  // This replaces every direct JanusGraph write ai-pipeline's jobs used to
  // make for these same transitions (Draft->Processing->Review/Live/Failed).
  def updateObject(request: Request, contentNode: Node, enrichmentNode: Node, objectIdentifier: String)
                   (implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] = {
    val contentIdentifier = contentNode.getIdentifier
    val channel = contentNode.getMetadata.getOrDefault("channel", "").asInstanceOf[String]

    resolveTargetTranscript(enrichmentNode, objectIdentifier).flatMap { transcriptNode =>
      val transcriptId = transcriptNode.getIdentifier
      val segments = request.getRequest.getOrDefault("segments", new util.ArrayList[util.Map[String, AnyRef]]())
        .asInstanceOf[util.List[util.Map[String, AnyRef]]]
      val requestedStatus = request.getRequest.getOrDefault("status", "").asInstanceOf[String]

      if (!segments.isEmpty) updateBySegmentEdit(contentIdentifier, channel, transcriptNode, segments)
      else if (StringUtils.equalsIgnoreCase(requestedStatus, "Processing"))
        updateStatusOnly(contentIdentifier, channel, transcriptId, "Processing")
      else if (StringUtils.equalsIgnoreCase(requestedStatus, "Failed"))
        updateFailed(contentIdentifier, enrichmentNode, channel, transcriptId, request)
      else if (StringUtils.equalsIgnoreCase(requestedStatus, "Live") || StringUtils.equalsIgnoreCase(requestedStatus, "Review"))
        updateCompletion(contentIdentifier, enrichmentNode, channel, transcriptNode, request, requestedStatus)
      else
        throw new ClientException("ERR_INVALID_UPDATE_REQUEST",
          "Provide either 'segments' (human edit) or a valid 'status' (Processing/Live/Review/Failed) for a job update.")
    }
  }

  private def updateBySegmentEdit(contentIdentifier: String, channel: String, transcriptNode: Node,
                                   segments: util.List[util.Map[String, AnyRef]])
                                  (implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] = {
    val transcriptId = transcriptNode.getIdentifier
    val status = transcriptNode.getMetadata.getOrDefault("status", "Draft").asInstanceOf[String]
    if (!StringUtils.equalsIgnoreCase(status, "Review"))
      throw new ClientException("ERR_TRANSCRIPT_EDIT_NOT_ALLOWED",
        s"Transcript must be in Review status to edit (currently $status).")

    val languageCode = transcriptNode.getMetadata.getOrDefault("languageCode", "en").asInstanceOf[String]
    val transcriptJson = buildTranscriptJson(segments)
    val vttContent = buildVttContent(segments)

    Future {
      uploadTranscriptFiles(contentIdentifier, languageCode, transcriptJson, vttContent)
    }.flatMap { case (transcriptUrl, captionsUrl) =>
      val updateMetadata = new util.HashMap[String, AnyRef]()
      updateMetadata.put("artifactUrl", transcriptUrl)
      updateMetadata.put("captionsUrl", captionsUrl)
      updateMetadata.put("generatedBy", "human-edited")
      updateMetadata.put("lastUpdatedOn", Instant.now().toString)

      val updateReq = buildTypedRequest(TRANSCRIPT_OBJECT_TYPE, TRANSCRIPT_SCHEMA_NAME, channel, updateMetadata)
      updateReq.getContext.put("identifier", transcriptId)

      // Awaited so a failed write (validation/DB error) reports as an error
      // response instead of a false "Transcript updated." success.
      DataNode.update(updateReq).map { _ =>
        ResponseHandler.OK
          .put(ContentConstants.IDENTIFIER, contentIdentifier)
          .put("transcriptId", transcriptId)
          .put("message", "Transcript updated.")
      }
    }
  }

  private def updateStatusOnly(contentIdentifier: String, channel: String, transcriptId: String, status: String)
                               (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val metadata = new util.HashMap[String, AnyRef]()
    metadata.put("status", status)
    metadata.put("lastUpdatedOn", Instant.now().toString)
    val req = buildTypedRequest(TRANSCRIPT_OBJECT_TYPE, TRANSCRIPT_SCHEMA_NAME, channel, metadata)
    req.getContext.put("identifier", transcriptId)
    DataNode.update(req).map { _ =>
      ResponseHandler.OK
        .put(ContentConstants.IDENTIFIER, contentIdentifier)
        .put("transcriptId", transcriptId)
        .put("message", s"Transcript marked $status.")
    }
  }

  private def updateFailed(contentIdentifier: String, enrichmentNode: Node, channel: String, transcriptId: String, request: Request)
                           (implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] = {
    val errorMessage = request.getRequest.getOrDefault("errorMessage", "").asInstanceOf[String]
    val metadata = new util.HashMap[String, AnyRef]()
    metadata.put("status", "Failed")
    metadata.put("errorMessage", errorMessage)
    metadata.put("lastUpdatedOn", Instant.now().toString)
    val req = buildTypedRequest(TRANSCRIPT_OBJECT_TYPE, TRANSCRIPT_SCHEMA_NAME, channel, metadata)
    req.getContext.put("identifier", transcriptId)
    DataNode.update(req).flatMap { _ =>
      syncAndMaybeBuildEcar(contentIdentifier, enrichmentNode, channel).map { _ =>
        ResponseHandler.OK
          .put(ContentConstants.IDENTIFIER, contentIdentifier)
          .put("transcriptId", transcriptId)
          .put("message", "Transcript marked Failed.")
      }
    }
  }

  private def updateCompletion(contentIdentifier: String, enrichmentNode: Node, channel: String, transcriptNode: Node,
                                request: Request, status: String)
                               (implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] = {
    val transcriptId = transcriptNode.getIdentifier
    val sourceLanguage = toBool(transcriptNode.getMetadata.getOrDefault("sourceLanguage", java.lang.Boolean.FALSE))
    val metadata = new util.HashMap[String, AnyRef]()
    metadata.put("status", status)
    metadata.put("errorMessage", null)
    metadata.put("lastUpdatedOn", Instant.now().toString)
    metadata.put("generatedOn", Instant.now().toString)
    metadata.put("autoApproved", java.lang.Boolean.valueOf(StringUtils.equalsIgnoreCase(status, "Live")))
    Seq("languageCode", "language", "code", "artifactUrl", "captionsUrl", "generatedBy").foreach { key =>
      val value = request.getRequest.getOrDefault(key, "").asInstanceOf[String]
      if (StringUtils.isNotBlank(value)) metadata.put(key, value)
    }

    val req = buildTypedRequest(TRANSCRIPT_OBJECT_TYPE, TRANSCRIPT_SCHEMA_NAME, channel, metadata)
    req.getContext.put("identifier", transcriptId)

    DataNode.update(req).flatMap { _ =>
      syncAndMaybeBuildEcar(contentIdentifier, enrichmentNode, channel).map { _ =>
        if (StringUtils.equalsIgnoreCase(status, "Live")) {
          val languageCode = request.getRequest.getOrDefault("languageCode",
            transcriptNode.getMetadata.getOrDefault("languageCode", "").asInstanceOf[String]).asInstanceOf[String]
          pushEnrichedMetadataApprovedEvent(transcriptId, contentIdentifier, enrichmentNode.getIdentifier, sourceLanguage, languageCode, channel)
        }
        ResponseHandler.OK
          .put(ContentConstants.IDENTIFIER, contentIdentifier)
          .put("transcriptId", transcriptId)
          .put("message", s"Transcript marked $status.")
      }
    }
  }

  // POST /content/v4/object/approve/:id/:objectIdentifier
  def approveObject(request: Request, contentNode: Node, enrichmentNode: Node, objectIdentifier: String)
                    (implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] = {
    val contentIdentifier = contentNode.getIdentifier
    val channel = contentNode.getMetadata.getOrDefault("channel", "").asInstanceOf[String]

    resolveTargetTranscript(enrichmentNode, objectIdentifier).flatMap { transcriptNode =>
      val status = transcriptNode.getMetadata.getOrDefault("status", "Draft").asInstanceOf[String]
      if (!StringUtils.equalsIgnoreCase(status, "Review"))
        throw new ClientException("ERR_TRANSCRIPT_NOT_IN_REVIEW",
          s"Transcript must be in Review status to approve (currently $status).")

      val sourceLanguage = toBool(transcriptNode.getMetadata.getOrDefault("sourceLanguage", java.lang.Boolean.FALSE))
      val languageCode = transcriptNode.getMetadata.getOrDefault("languageCode", "").asInstanceOf[String]

      val approveMetadata = new util.HashMap[String, AnyRef]()
      approveMetadata.put("status", "Live")
      approveMetadata.put("autoApproved", java.lang.Boolean.FALSE)
      approveMetadata.put("lastUpdatedOn", Instant.now().toString)

      val approveReq = buildTypedRequest(TRANSCRIPT_OBJECT_TYPE, TRANSCRIPT_SCHEMA_NAME, channel, approveMetadata)
      approveReq.getContext.put("identifier", transcriptNode.getIdentifier)

      DataNode.update(approveReq).flatMap { _ =>
        syncAndMaybeBuildEcar(contentIdentifier, enrichmentNode, channel).map { _ =>
          pushEnrichedMetadataApprovedEvent(transcriptNode.getIdentifier, contentIdentifier,
            enrichmentNode.getIdentifier, sourceLanguage, languageCode, channel)
          ResponseHandler.OK
            .put(ContentConstants.IDENTIFIER, contentIdentifier)
            .put("transcriptId", transcriptNode.getIdentifier)
            .put("message", "Transcript approved.")
        }
      }
    }
  }

  // POST /content/v4/object/reject/:id/:objectIdentifier
  def rejectObject(request: Request, contentNode: Node, enrichmentNode: Node, objectIdentifier: String)
                   (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val contentIdentifier = contentNode.getIdentifier
    val channel = contentNode.getMetadata.getOrDefault("channel", "").asInstanceOf[String]

    resolveTargetTranscript(enrichmentNode, objectIdentifier).flatMap { transcriptNode =>
      val status = transcriptNode.getMetadata.getOrDefault("status", "Draft").asInstanceOf[String]
      if (!StringUtils.equalsIgnoreCase(status, "Review"))
        throw new ClientException("ERR_TRANSCRIPT_NOT_IN_REVIEW",
          s"Transcript must be in Review status to reject (currently $status).")

      val rejectMetadata = new util.HashMap[String, AnyRef]()
      rejectMetadata.put("status", "Draft")
      rejectMetadata.put("lastUpdatedOn", Instant.now().toString)

      val rejectReq = buildTypedRequest(TRANSCRIPT_OBJECT_TYPE, TRANSCRIPT_SCHEMA_NAME, channel, rejectMetadata)
      rejectReq.getContext.put("identifier", transcriptNode.getIdentifier)

      DataNode.update(rejectReq).flatMap { _ =>
        syncEnrichmentTranscripts(enrichmentNode.getIdentifier, channel).map { _ =>
          ResponseHandler.OK
            .put(ContentConstants.IDENTIFIER, contentIdentifier)
            .put("transcriptId", transcriptNode.getIdentifier)
            .put("message", "Transcript rejected. Status reset to Draft.")
        }
      }
    }
  }

  // ===================== create() branches =====================

  private def createFromGeneration(request: Request, contentNode: Node, enrichmentNode: Node)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val contentIdentifier = contentNode.getIdentifier
    val channel = contentNode.getMetadata.getOrDefault("channel", "").asInstanceOf[String]
    val mimeType = contentNode.getMetadata.getOrDefault("mimeType", "").asInstanceOf[String]
    val artifactUrl = contentNode.getMetadata.getOrDefault("artifactUrl", "").asInstanceOf[String]
    val contentStatus = contentNode.getMetadata.getOrDefault("status", "").asInstanceOf[String]

    if (!VALID_MIME_TYPES.contains(mimeType))
      throw new ClientException("ERR_INVALID_MIME_TYPE", s"mimeType must be one of: ${VALID_MIME_TYPES.mkString(", ")}")
    if (StringUtils.isBlank(artifactUrl) || (!artifactUrl.startsWith("http://") && !artifactUrl.startsWith("https://")))
      throw new ClientException("ERR_MISSING_ARTIFACT_URL", "artifactUrl is required and must be an http/https URL")

    readTranscriptChildren(enrichmentNode).flatMap { transcripts =>
      findSourceTranscript(transcripts) match {
        case Some(sourceNode) =>
          val status = sourceNode.getMetadata.getOrDefault("status", "Draft").asInstanceOf[String]
          if (ACTIVE_STATUSES.contains(status))
            throw new ClientException("ERR_TRANSCRIPT_IN_PROGRESS",
              s"A transcription job is already $status for this content.")

          val resetMetadata = new util.HashMap[String, AnyRef]()
          resetMetadata.put("status", "Draft")
          resetMetadata.put("errorMessage", null)
          val resetReq = buildTypedRequest(TRANSCRIPT_OBJECT_TYPE, TRANSCRIPT_SCHEMA_NAME, channel, resetMetadata)
          resetReq.getContext.put("identifier", sourceNode.getIdentifier)
          DataNode.update(resetReq).map { _ =>
            maybeBackfill(contentIdentifier, enrichmentNode.getIdentifier, sourceNode.getIdentifier, artifactUrl, mimeType, contentStatus, channel)
            ResponseHandler.OK
              .put(ContentConstants.IDENTIFIER, contentIdentifier)
              .put("transcriptId", sourceNode.getIdentifier)
              .put("message", "Transcription request accepted.")
          }
        case None =>
          createTranscriptChildNode(contentIdentifier, enrichmentNode.getIdentifier, channel, languageCode = "", sourceLanguage = true)
            .flatMap { transcriptNode =>
            val aiFeaturesMetadata = new util.HashMap[String, AnyRef]()
            aiFeaturesMetadata.put("aiFeatures", util.Arrays.asList("transcript"))
            val enrichmentReq = buildTypedRequest(ENRICHMENT_OBJECT_TYPE, ENRICHMENT_SCHEMA_NAME, channel, aiFeaturesMetadata)
            enrichmentReq.getContext.put("identifier", enrichmentNode.getIdentifier)

            DataNode.update(enrichmentReq).map { _ =>
              maybeBackfill(contentIdentifier, enrichmentNode.getIdentifier, transcriptNode.getIdentifier, artifactUrl, mimeType, contentStatus, channel)
              ResponseHandler.OK
                .put(ContentConstants.IDENTIFIER, contentIdentifier)
                .put("transcriptId", transcriptNode.getIdentifier)
                .put("message", "Transcription request accepted.")
            }
          }
      }
    }
  }

  private def createFromUpload(request: Request, contentNode: Node, enrichmentNode: Node)(implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Response] = {
    val contentIdentifier = contentNode.getIdentifier
    val channel = contentNode.getMetadata.getOrDefault("channel", "").asInstanceOf[String]
    val languageCode = request.getRequest.getOrDefault("languageCode", "").asInstanceOf[String]
    if (StringUtils.isBlank(languageCode))
      throw new ClientException("ERR_MISSING_LANGUAGE_CODE", "languageCode is required for a VTT upload.")

    // isUpload (createObject, above) triggers on either "file" (a real
    // multipart upload) or "fileUrl" (a plain JSON body naming a remote VTT
    // to fetch instead) - both must be handled here, or a fileUrl-only
    // request always falls through to ERR_MISSING_FILE despite passing the
    // isUpload check that routed it here in the first place.
    val (vttFile, isDownloaded) = request.getRequest.get("file") match {
      case f: File => (f, false)
      case _ =>
        val fileUrl = request.getRequest.getOrDefault("fileUrl", "").asInstanceOf[String]
        if (StringUtils.isBlank(fileUrl))
          throw new ClientException("ERR_MISSING_FILE", "A VTT file or fileUrl is required for upload mode.")
        SafeUrlValidator.validate(fileUrl)
        val downloaded = File.createTempFile("transcript_upload_", ".vtt")
        FileUtils.copyURLToFile(new URL(fileUrl), downloaded)
        (downloaded, true)
    }

    readTranscriptChildren(enrichmentNode).flatMap { transcripts =>
      val transcriptNodeFuture: Future[Node] = findTranscriptByLanguage(transcripts, languageCode) match {
        case Some(t) => Future.successful(t)
        case None => createTranscriptChildNode(contentIdentifier, enrichmentNode.getIdentifier, channel, languageCode, sourceLanguage = false)
      }

      transcriptNodeFuture.flatMap { transcriptNode =>
        Future {
          try {
            val folderPath = s"${Platform.getString(CONTENT_FOLDER, "content")}/$contentIdentifier/transcripts/$languageCode"
            ss.uploadFile(folderPath, vttFile, Option(false))(1)
          } finally {
            // Only the fileUrl branch's own downloaded temp file - a real
            // multipart "file" is the caller's (requestObjectFormData's) to
            // clean up, not ours.
            if (isDownloaded) vttFile.delete()
          }
        }.flatMap { captionsUrl =>
          val updateMetadata = new util.HashMap[String, AnyRef]()
          updateMetadata.put("captionsUrl", captionsUrl)
          updateMetadata.put("generatedBy", "human-uploaded")
          updateMetadata.put("status", "Review")
          val updateReq = buildTypedRequest(TRANSCRIPT_OBJECT_TYPE, TRANSCRIPT_SCHEMA_NAME, channel, updateMetadata)
          updateReq.getContext.put("identifier", transcriptNode.getIdentifier)

          // Both writes below must be awaited before responding OK - a
          // caller acting on "pending review" while the write silently
          // failed would be misled into thinking the upload succeeded.
          DataNode.update(updateReq).flatMap { _ =>
            val aiFeaturesMetadata = new util.HashMap[String, AnyRef]()
            aiFeaturesMetadata.put("aiFeatures", util.Arrays.asList("transcript"))
            val enrichmentReq = buildTypedRequest(ENRICHMENT_OBJECT_TYPE, ENRICHMENT_SCHEMA_NAME, channel, aiFeaturesMetadata)
            enrichmentReq.getContext.put("identifier", enrichmentNode.getIdentifier)

            DataNode.update(enrichmentReq).map { _ =>
              ResponseHandler.OK
                .put(ContentConstants.IDENTIFIER, contentIdentifier)
                .put("transcriptId", transcriptNode.getIdentifier)
                .put("message", "Transcript uploaded and pending review.")
            }
          }
        }
      } andThen { case _ => syncEnrichmentTranscripts(enrichmentNode.getIdentifier, channel) }
    }
  }

  // Absorbed from ai-pipeline's enrichment-router, which used to create this
  // node directly via JanusGraph (no HTTP API previously supported creating
  // a Draft for anything but the source language). language is the caller's
  // own display-name lookup (e.g. "Hindi") — passed in rather than
  // duplicated here, since the job already needs that mapping for its own
  // purposes and this avoids maintaining the same language-code table twice.
  private def createTargetDraft(request: Request, contentNode: Node, enrichmentNode: Node, languageCode: String)
                                (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val contentIdentifier = contentNode.getIdentifier
    val channel = contentNode.getMetadata.getOrDefault("channel", "").asInstanceOf[String]
    val language = request.getRequest.getOrDefault("language", "").asInstanceOf[String]

    readTranscriptChildren(enrichmentNode).flatMap { transcripts =>
      findTranscriptByLanguage(transcripts, languageCode) match {
        case Some(existing) =>
          Future.successful(
            ResponseHandler.OK
              .put(ContentConstants.IDENTIFIER, contentIdentifier)
              .put("transcriptId", existing.getIdentifier)
              .put("message", "Transcript already exists for this language.")
          )
        case None =>
          createTranscriptChildNode(contentIdentifier, enrichmentNode.getIdentifier, channel, languageCode,
            sourceLanguage = false, language = language).map { transcriptNode =>
            ResponseHandler.OK
              .put(ContentConstants.IDENTIFIER, contentIdentifier)
              .put("transcriptId", transcriptNode.getIdentifier)
              .put("message", "Draft transcript created.")
          }
      }
    }
  }

  private def maybeBackfill(contentIdentifier: String, enrichmentId: String, transcriptId: String,
                             artifactUrl: String, mimeType: String, contentStatus: String, channel: String): Unit = {
    // Content already Live: router will never see a publish event for it, so
    // emit the transcription request directly instead of waiting for
    // enrichment-router to react to enriched.metadata.
    if (StringUtils.equalsIgnoreCase(contentStatus, "Live"))
      pushTranscriptionRequestEvent(contentIdentifier, enrichmentId, transcriptId, artifactUrl, mimeType, channel)
  }

  // ===================== Enrichment / Transcript node helpers =====================

  // Public: ContentActor's generic object/{create,update,approve,reject}
  // dispatch calls this directly before handing off to whichever
  // EnrichmentObjectHandler matches the request's objectType.
  def findOrCreateEnrichment(contentNode: Node)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
    readEnrichmentForContent(contentNode).flatMap {
      case Some(existing) => Future.successful(existing)
      case None =>
        val channel = contentNode.getMetadata.getOrDefault("channel", "").asInstanceOf[String]
        val identifier = Identifier.getIdentifier(GRAPH_ID, Identifier.getUniqueIdFromTimestamp)
        val metadata = new util.HashMap[String, AnyRef]()
        metadata.put("identifier", identifier)
        metadata.put("name", s"Enrichment_${contentNode.getIdentifier}")
        // Enrichment is 1:1 per Content (findOrCreateEnrichment finds-before-creates),
        // so contentId alone is already unique — the "_enrichment" suffix just makes
        // it unambiguous in logs/dumps which kind of derived node this is, rather
        // than duplicating the opaque generated identifier for no reason.
        metadata.put("code", s"${contentNode.getIdentifier}_enrichment")
        metadata.put("channel", channel)
        metadata.put("contentId", contentNode.getIdentifier)
        metadata.put("aiFeatures", new util.ArrayList[String]())
        val createReq = buildTypedRequest(ENRICHMENT_OBJECT_TYPE, ENRICHMENT_SCHEMA_NAME, channel, metadata)
        DataNode.create(createReq).flatMap { enrichmentNode =>
          // Wires the edge from Content -> Enrichment. Must be added via a
          // Content update, not on Enrichment's own create request: relation
          // validation checks the edge against the CURRENT node's own
          // outRelationObjectTypes, and Enrichment's "usedByContent" is
          // direction "in" (so it's excluded from Enrichment's own out-list
          // by definition — confirmed via BaseDefinitionNode.relationsSchema,
          // which filters strictly on direction). Content's own config.json
          // declares "enrichment" as direction "out", which is where this
          // edge actually needs to originate from to pass validation.
          val contentRelMetadata = new util.HashMap[String, AnyRef]()
          contentRelMetadata.put("enrichment", util.Arrays.asList(new util.HashMap[String, AnyRef]() {
            put("identifier", enrichmentNode.getIdentifier)
          }))
          val contentUpdateReq = buildTypedRequest(ContentConstants.CONTENT_OBJECT_TYPE, ContentConstants.CONTENT_SCHEMA_NAME, channel, contentRelMetadata)
          contentUpdateReq.getContext.put("identifier", contentNode.getIdentifier)
          DataNode.update(contentUpdateReq).map(_ => enrichmentNode)
        }
    }
  }

  // Public: also called directly from ContentActor's generic object/
  // {update,approve,reject} dispatch before it knows which handler to use.
  def readEnrichmentForContent(contentNode: Node)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Option[Node]] = {
    val enrichmentRel = Option(contentNode.getOutRelations).map(_.asScala).getOrElse(Seq())
      .find(r => StringUtils.equalsIgnoreCase(r.getRelationType, "associatedTo") &&
        StringUtils.equalsIgnoreCase(r.getEndNodeObjectType, ENRICHMENT_OBJECT_TYPE))
    enrichmentRel match {
      case None => Future.successful(None)
      case Some(rel) => readTypedNode(rel.getEndNodeId, ENRICHMENT_OBJECT_TYPE, ENRICHMENT_SCHEMA_NAME).map(Some(_))
    }
  }

  // Relation.getEndNodeMetadata()/getStartNodeMetadata() are hardcoded in
  // JanusGraphNodeUtil.createRelation (graph-dac-api) to only ever contain
  // "description"/"status" off the target vertex — confirmed via a direct
  // JanusGraph query showing the edge itself carries zero properties, and
  // via that Java source having no other vertex-property reads. Any other
  // key (sourceLanguage, languageCode, ...) always reads as the default.
  // Only getEndNodeId()/getEndNodeObjectType() are real (sourced from the
  // vertex's own IL_UNIQUE_ID/IL_FUNC_OBJECT_TYPE). So every lookup that
  // needs real Transcript data re-reads each child node fully, same as
  // syncEnrichmentTranscriptsFromNode already did below.
  private def readTranscriptChildren(enrichmentNode: Node)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Seq[Node]] = {
    val transcriptRelations = Option(enrichmentNode.getOutRelations).map(_.asScala.toSeq).getOrElse(Seq())
      .filter(r => StringUtils.equalsIgnoreCase(r.getEndNodeObjectType, TRANSCRIPT_OBJECT_TYPE))
    Future.sequence(transcriptRelations.map(rel => readTypedNode(rel.getEndNodeId, TRANSCRIPT_OBJECT_TYPE, TRANSCRIPT_SCHEMA_NAME)))
  }

  private def findSourceTranscript(transcripts: Seq[Node]): Option[Node] =
    transcripts.find(t => toBool(t.getMetadata.getOrDefault("sourceLanguage", java.lang.Boolean.FALSE)))

  private def findTranscriptByLanguage(transcripts: Seq[Node], languageCode: String): Option[Node] =
    transcripts.find(t => StringUtils.equalsIgnoreCase(t.getMetadata.getOrDefault("languageCode", "").asInstanceOf[String], languageCode))

  // transcriptId is a required URL segment on update/approve/reject (not an
  // optional body field) — no silent fallback to the source transcript here.
  // A caller that means to act on the source transcript still has to name
  // it explicitly; ambiguity about which transcript got acted on is exactly
  // the bug this replaced (updateTranscript used to always hit the source
  // transcript regardless of what a caller intended).
  private def resolveTargetTranscript(enrichmentNode: Node, transcriptId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
    if (StringUtils.isBlank(transcriptId))
      throw new ClientException("ERR_MISSING_TRANSCRIPT_ID", "transcriptId is required.")
    readTranscriptChildren(enrichmentNode).map { transcripts =>
      transcripts.find(t => StringUtils.equalsIgnoreCase(t.getIdentifier, transcriptId))
        .getOrElse(throw new ClientException("ERR_TRANSCRIPT_NOT_FOUND", "No matching Transcript node found under this content's Enrichment."))
    }
  }

  private def createTranscriptChildNode(contentIdentifier: String, enrichmentIdentifier: String, channel: String, languageCode: String,
                                         sourceLanguage: Boolean, language: String = "")
                                        (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
    val identifier = Identifier.getIdentifier(GRAPH_ID, Identifier.getUniqueIdFromTimestamp)
    val metadata = new util.HashMap[String, AnyRef]()
    metadata.put("identifier", identifier)
    metadata.put("name", s"Transcript_$identifier")
    // One Transcript per (content, language) — languageCode is blank only for
    // the not-yet-detected source transcript at creation time, so fall back to
    // "source" there rather than leaving a trailing underscore.
    metadata.put("code", s"${contentIdentifier}_${if (StringUtils.isNotBlank(languageCode)) languageCode else "source"}")
    metadata.put("channel", channel)
    metadata.put("languageCode", languageCode)
    metadata.put("language", language)
    metadata.put("sourceLanguage", sourceLanguage.asInstanceOf[AnyRef])
    metadata.put("status", "Draft")
    val createReq = buildTypedRequest(TRANSCRIPT_OBJECT_TYPE, TRANSCRIPT_SCHEMA_NAME, channel, metadata)
    DataNode.create(createReq).flatMap { transcriptNode =>
      // Wires the edge from Enrichment -> Transcript. Must be added via an
      // Enrichment update, not on Transcript's own create request — same
      // reason as findOrCreateEnrichment's Content update above: relation
      // validation checks the edge against the CURRENT node's own
      // outRelationObjectTypes, and Transcript's "usedByEnrichment" is
      // direction "in" (excluded from Transcript's own out-list by
      // definition). Enrichment's own config.json declares "transcripts" as
      // direction "out", which is where this edge actually needs to
      // originate from to pass validation.
      //
      // DefinitionNode.setRelationship treats the submitted relation list as
      // the *complete* desired set for this relation name — any existing
      // out-relation not resubmitted gets diffed into deletedRelations and
      // removed. So every sibling Transcript's identifier must be resent
      // here alongside the new one, or adding a second/third language wipes
      // every previously-linked Transcript from Enrichment.transcripts.
      //
      // Re-reads the Enrichment node fresh right here instead of trusting a
      // childIds snapshot the caller read earlier in the same request —
      // narrows (does not fully eliminate) a real race where two concurrent
      // creates for different languageCodes each start from the same stale
      // snapshot and the second write drops the first's newly-linked
      // Transcript. Closing this fully needs real locking (e.g. a
      // per-Enrichment lock or an optimistic-concurrency retry), not
      // attempted here.
      readTypedNode(enrichmentIdentifier, ENRICHMENT_OBJECT_TYPE, ENRICHMENT_SCHEMA_NAME).flatMap { freshEnrichmentNode =>
        val freshChildIds = Option(freshEnrichmentNode.getOutRelations).map(_.asScala.toSeq).getOrElse(Seq())
          .filter(r => StringUtils.equalsIgnoreCase(r.getEndNodeObjectType, TRANSCRIPT_OBJECT_TYPE))
          .map(_.getEndNodeId)
        val allChildIds = (freshChildIds :+ transcriptNode.getIdentifier).distinct
        val enrichmentRelMetadata = new util.HashMap[String, AnyRef]()
        enrichmentRelMetadata.put("transcripts", allChildIds.map(id => new util.HashMap[String, AnyRef]() {
          put("identifier", id)
        }).asJava)
        val enrichmentUpdateReq = buildTypedRequest(ENRICHMENT_OBJECT_TYPE, ENRICHMENT_SCHEMA_NAME, channel, enrichmentRelMetadata)
        enrichmentUpdateReq.getContext.put("identifier", enrichmentIdentifier)
        DataNode.update(enrichmentUpdateReq).map(_ => transcriptNode)
      }
    }
  }

  /** Re-reads the Enrichment node itself fresh (never trusts a caller-held
   * in-memory Node — it may predate a relation just added by this same
   * request, e.g. a Transcript child created moments earlier), then
   * re-reads every Transcript child fresh and returns a relationFields-
   * scoped in-memory snapshot (for isEcarReady's caller) — this is NOT
   * persisted back onto the Enrichment node. "transcripts" is the actual
   * graph relation name (Enrichment's config.json: relations.transcripts,
   * direction out, objects [Transcript]); a snapshot entry here has no
   * "identifier" key (only denormalized metadata fields), so writing it
   * under that same key gets misread as a relation-set update whose
   * entries can't resolve an end node, NPEing in
   * AssociationRelation.validate. Live reads (readEnrichment /
   * fetchEnrichmentMetadata) already reconstruct the equivalent view
   * fresh from the real relation each time, so nothing needs this
   * persisted.
   */
  private def syncEnrichmentTranscripts(enrichmentIdentifier: String, channel: String)
                                        (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[util.List[util.Map[String, AnyRef]]] = {
    readTypedNode(enrichmentIdentifier, ENRICHMENT_OBJECT_TYPE, ENRICHMENT_SCHEMA_NAME).flatMap { enrichmentNode =>
      syncEnrichmentTranscriptsFromNode(enrichmentNode, channel)
    }
  }

  private def syncEnrichmentTranscriptsFromNode(enrichmentNode: Node, channel: String)
                                                (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[util.List[util.Map[String, AnyRef]]] = {
    val relationFields: List[String] =
      try {
        val validator = SchemaValidatorFactory.getInstance(TRANSCRIPT_SCHEMA_NAME, SCHEMA_VERSION)
        if (validator.getConfig.hasPath("relationFields")) validator.getConfig.getStringList("relationFields").asScala.toList
        else List("status", "languageCode", "sourceLanguage", "captionsUrl")
      } catch { case _: Exception => List("status", "languageCode", "sourceLanguage", "captionsUrl") }

    val transcriptRelations = Option(enrichmentNode.getOutRelations).map(_.asScala.toSeq).getOrElse(Seq())
      .filter(r => StringUtils.equalsIgnoreCase(r.getEndNodeObjectType, TRANSCRIPT_OBJECT_TYPE))

    Future.sequence(transcriptRelations.map(rel => readTypedNode(rel.getEndNodeId, TRANSCRIPT_OBJECT_TYPE, TRANSCRIPT_SCHEMA_NAME))).map { nodes =>
      nodes.map { n =>
        val m: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]()
        relationFields.foreach(f => m.put(f, n.getMetadata.get(f)))
        m
      }.asJava
    }
  }

  private[mgr] def isEcarReady(transcripts: util.List[util.Map[String, AnyRef]], allowFailedLanguages: Boolean): Boolean = {
    if (transcripts.isEmpty) return false
    val ts = transcripts.asScala
    val sourceOpt = ts.find(t => toBool(t.getOrDefault("sourceLanguage", java.lang.Boolean.FALSE)))
    sourceOpt match {
      case None => false
      case Some(source) =>
        if (!StringUtils.equalsIgnoreCase(source.getOrDefault("status", "").asInstanceOf[String], "Live")) false
        else ts.filterNot(t => toBool(t.getOrDefault("sourceLanguage", java.lang.Boolean.FALSE))).forall { t =>
          val status = t.getOrDefault("status", "").asInstanceOf[String]
          if (BLOCKING_ECAR_STATUSES.contains(status)) false
          else if (StringUtils.equalsIgnoreCase(status, "Failed") && !allowFailedLanguages) false
          else true
        }
    }
  }

  // Shared by approveObject and updateObject's completion/failure paths — a
  // Transcript status change is exactly when Enrichment.transcripts needs
  // re-syncing and the ECAR readiness check needs re-running, regardless of
  // whether the status change came from a human approval or a job report.
  private def syncAndMaybeBuildEcar(contentIdentifier: String, enrichmentNode: Node, channel: String)
                                    (implicit oec: OntologyEngineContext, ec: ExecutionContext, ss: StorageService): Future[Unit] = {
    syncEnrichmentTranscripts(enrichmentNode.getIdentifier, channel).flatMap { transcripts =>
      val allowFailed = Platform.getBoolean(ALLOW_FAILED_LANGUAGES_KEY, true)
      if (isEcarReady(transcripts, allowFailed))
        buildAndUploadEcar(contentIdentifier, enrichmentNode, transcripts).flatMap { ecarUrl =>
          val urlMetadata = new util.HashMap[String, AnyRef]()
          urlMetadata.put("transcriptUrl", ecarUrl)
          val urlReq = buildTypedRequest(ENRICHMENT_OBJECT_TYPE, ENRICHMENT_SCHEMA_NAME, channel, urlMetadata)
          urlReq.getContext.put("identifier", enrichmentNode.getIdentifier)
          DataNode.update(urlReq).map(_ => ())
        }
      else Future.successful(())
    }
  }

  // ===================== ECAR packaging =====================

  private def buildAndUploadEcar(contentIdentifier: String, enrichmentNode: Node, transcripts: util.List[util.Map[String, AnyRef]])
                                 (implicit ss: StorageService, ec: ExecutionContext): Future[String] = Future {
    val baseTemp = new File(Platform.getString("content.upload.temp_location", "/tmp/content"))
    val workDir = new File(baseTemp, s"${contentIdentifier}_ecar_${System.currentTimeMillis()}")
    val contentRoot = new File(workDir, contentIdentifier)
    contentRoot.mkdirs()
    try {
      val manifest = new util.HashMap[String, AnyRef]()
      manifest.put("enrichment", enrichmentNode.getMetadata)
      manifest.put("transcripts", transcripts)
      FileUtils.writeStringToFile(new File(contentRoot, "manifest.json"), JsonUtils.serialize(manifest), StandardCharsets.UTF_8)

      transcripts.asScala.foreach { t =>
        val captionsUrl = t.getOrDefault("captionsUrl", "").asInstanceOf[String]
        val languageCode = t.getOrDefault("languageCode", "").asInstanceOf[String]
        if (StringUtils.isNotBlank(captionsUrl) && StringUtils.isNotBlank(languageCode)) {
          val langDir = new File(contentRoot, s"transcripts/$languageCode")
          langDir.mkdirs()
          FileUtils.copyURLToFile(new URL(captionsUrl), new File(langDir, "captions.vtt"))
        }
      }

      val zipFile = new File(workDir, s"${contentIdentifier}_transcripts.ecar")
      zipDirectory(contentRoot, contentIdentifier, zipFile)

      val folderPath = s"${Platform.getString(CONTENT_FOLDER, "content")}/$contentIdentifier"
      val uploadResult = ss.uploadFile(folderPath, zipFile, Option(false))
      uploadResult(1)
    } finally {
      FileUtils.deleteDirectory(workDir)
    }
  }

  private def zipDirectory(sourceRoot: File, baseEntryName: String, zipFile: File): Unit = {
    val zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))
    try {
      def addFile(file: File, entryPath: String): Unit = {
        if (file.isDirectory) {
          Option(file.listFiles()).getOrElse(Array()).foreach(f => addFile(f, s"$entryPath${f.getName}" + (if (f.isDirectory) "/" else "")))
        } else {
          zos.putNextEntry(new ZipEntry(entryPath))
          zos.write(FileUtils.readFileToByteArray(file))
          zos.closeEntry()
        }
      }
      addFile(sourceRoot, s"$baseEntryName/")
    } finally {
      zos.close()
    }
  }

  // ===================== Kafka event emission =====================

  // Standard BE_JOB_REQUEST envelope used platform-wide for job-to-job Kafka
  // events (same shape VideoEnrichmentHelper.getStreamingEvent builds in
  // knowledge-platform-jobs) — mirrored on the Python side by
  // sunbird_ai_core.kafka.event_schemas._wrap_be_job_request. edata carries
  // the action-specific payload; channel/env live in context, not edata.
  private def buildBeJobRequestEvent(actorId: String, action: String, objectId: String,
                                      channel: String, edata: util.Map[String, AnyRef]): util.HashMap[String, AnyRef] = {
    val ets = System.currentTimeMillis
    val mid = s"LP.$ets.${util.UUID.randomUUID}"

    val pdata = new util.HashMap[String, AnyRef]()
    pdata.put("ver", "1.0")
    pdata.put("id", "org.ekstep.platform")

    val context = new util.HashMap[String, AnyRef]()
    context.put("pdata", pdata)
    context.put("channel", channel)
    context.put("env", Platform.getString("cloud_storage.env", "dev"))

    val actor = new util.HashMap[String, AnyRef]()
    actor.put("id", actorId)
    actor.put("type", "System")

    val obj = new util.HashMap[String, AnyRef]()
    obj.put("ver", "1.0")
    obj.put("id", objectId)

    val fullEdata = new util.HashMap[String, AnyRef](edata)
    fullEdata.put("action", action)

    val event = new util.HashMap[String, AnyRef]()
    event.put("eid", "BE_JOB_REQUEST")
    event.put("ets", ets.asInstanceOf[AnyRef])
    event.put("mid", mid)
    event.put("actor", actor)
    event.put("context", context)
    event.put("object", obj)
    event.put("edata", fullEdata)
    event
  }

  private def pushTranscriptionRequestEvent(contentId: String, enrichmentId: String, transcriptId: String,
                                             artifactUrl: String, mimeType: String, channel: String): Unit = {
    val edata = new util.HashMap[String, AnyRef]()
    edata.put("contentId", contentId)
    edata.put("enrichmentId", enrichmentId)
    edata.put("transcriptId", transcriptId)
    edata.put("artifactUrl", artifactUrl)
    edata.put("mimeType", mimeType)
    val event = buildBeJobRequestEvent("knowlg-service", "media-transcription-request", contentId, channel, edata)

    val topic = Platform.getString(TRANSCRIPTION_TOPIC_KEY, DEFAULT_TRANSCRIPTION_TOPIC)
    TelemetryManager.info(s"Pushing media transcription request for $contentId to topic $topic")
    // Isolated: this runs after the DB write it announces has already
    // succeeded (see maybeBackfill's caller) - a broker hiccup here must not
    // surface as a failure response for a request whose actual state change
    // already landed, since a client retry would then hit
    // ERR_TRANSCRIPT_IN_PROGRESS and mask that the original call worked.
    try {
      kfClient.send(JsonUtils.serialize(event), topic)
    } catch {
      case e: Exception =>
        TelemetryManager.error(s"Failed to push media transcription request for $contentId to topic $topic: ${e.getMessage}", e)
    }
  }

  // Consumed by enrichment-router as sunbird_ai_core.kafka.event_schemas.EnrichedMetadataEvent
  // (edata.contentType=Transcript, edata.action=approved).
  private def pushEnrichedMetadataApprovedEvent(transcriptId: String, contentId: String, enrichmentId: String,
                                                 sourceLanguage: Boolean, languageCode: String, channel: String): Unit = {
    val edata = new util.HashMap[String, AnyRef]()
    edata.put("contentType", "Transcript")
    edata.put("contentId", contentId)
    edata.put("enrichmentId", enrichmentId)
    edata.put("sourceLanguage", sourceLanguage.asInstanceOf[AnyRef])
    edata.put("languageCode", languageCode)
    val event = buildBeJobRequestEvent("knowlg-service", "approved", transcriptId, channel, edata)

    val topic = Platform.getString(ENRICHED_METADATA_TOPIC_KEY, DEFAULT_ENRICHED_METADATA_TOPIC)
    TelemetryManager.info(s"Pushing enriched.metadata (Transcript approved) for $transcriptId to topic $topic")
    // Isolated for the same reason as pushTranscriptionRequestEvent above -
    // this runs after the Transcript's status has already flipped to Live
    // and synced; a broker hiccup here must not turn that already-successful
    // write into a 5xx (a retry would then hit ERR_TRANSCRIPT_NOT_IN_REVIEW,
    // masking that the original approve/complete call actually succeeded).
    try {
      kfClient.send(JsonUtils.serialize(event), topic)
    } catch {
      case e: Exception =>
        TelemetryManager.error(s"Failed to push enriched.metadata approved event for $transcriptId to topic $topic: ${e.getMessage}", e)
    }
  }

  // ===================== generic node request/read helpers =====================

  private def buildTypedRequest(objectType: String, schemaName: String, channel: String, metadata: util.Map[String, AnyRef]): Request = {
    val req = new Request()
    req.setObjectType(objectType)
    val context = new util.HashMap[String, AnyRef]()
    context.put("graph_id", GRAPH_ID)
    context.put("version", SCHEMA_VERSION)
    context.put("objectType", objectType)
    context.put("schemaName", schemaName)
    if (StringUtils.isNotBlank(channel)) context.put("channel", channel)
    req.setContext(context)
    req.setRequest(metadata)
    req
  }

  private def readTypedNode(identifier: String, objectType: String, schemaName: String)
                            (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
    val readReq = buildTypedRequest(objectType, schemaName, "", new util.HashMap[String, AnyRef]())
    readReq.getContext.put("identifier", identifier)
    readReq.put("identifier", identifier)
    readReq.put("fields", new util.ArrayList[String])
    DataNode.read(readReq)
  }

  private def uploadTranscriptFiles(contentIdentifier: String, languageCode: String, transcriptJson: String, vttContent: String)
                                    (implicit ss: StorageService): (String, String) = {
    val transcriptFile = writeToTempFile(s"transcript_$languageCode.json", transcriptJson)
    val vttFile = writeToTempFile(s"captions_$languageCode.vtt", vttContent)
    try {
      val folderPath = s"${Platform.getString(CONTENT_FOLDER, "content")}/$contentIdentifier/transcripts/$languageCode"
      val transcriptUrl = ss.uploadFile(folderPath, transcriptFile, Option(false))(1)
      val captionsUrl = ss.uploadFile(folderPath, vttFile, Option(false))(1)
      (transcriptUrl, captionsUrl)
    } finally {
      transcriptFile.delete()
      vttFile.delete()
    }
  }

  // ===================== VTT / transcript.json builders (unchanged logic) =====================

  // Shape must match ai-pipeline's AI-generated transcript.json exactly
  // (sunbird_ai_core.segment.Segment: {id: int, start: float, end: float,
  // text: str}) — this JSON is read back by multilingual_function.py's
  // segments_from_dicts whenever a human-edited transcript becomes the
  // multilingual source. Previously this wrote start/end as quoted strings
  // and omitted "id" entirely, which crashed that parser with KeyError('id').
  private[mgr] def buildTranscriptJson(segments: util.List[util.Map[String, AnyRef]]): String = {
    val sb = new StringBuilder
    sb.append("""{"segments":[""")
    val segList = segments.asScala
    segList.zipWithIndex.foreach { case (seg, idx) =>
      val id = seg.getOrDefault("id", idx.asInstanceOf[AnyRef]).toString.toDouble.toInt
      val start = seg.getOrDefault("start", "0").toString.toDouble
      val end = seg.getOrDefault("end", "0").toString.toDouble
      // getOrDefault only substitutes when the key is absent - an explicit
      // "text": null segment still returns null here, and escapeJson(null)
      // NPEs on String.replace. Option(...) catches that case too.
      val text = escapeJson(Option(seg.get("text")).map(_.asInstanceOf[String]).getOrElse(""))
      sb.append(s"""{"id":$id,"start":$start,"end":$end,"text":"$text"}""")
      if (idx < segList.size - 1) sb.append(",")
    }
    sb.append("]}")
    sb.toString()
  }

  private[mgr] def buildVttContent(segments: util.List[util.Map[String, AnyRef]]): String = {
    val sb = new StringBuilder
    sb.append("WEBVTT\n\n")
    segments.asScala.zipWithIndex.foreach { case (seg, idx) =>
      val start = formatVttTimestamp(seg.getOrDefault("start", "0").toString)
      val end = formatVttTimestamp(seg.getOrDefault("end", "0").toString)
      val text = Option(seg.get("text")).map(_.asInstanceOf[String]).getOrElse("")
      sb.append(s"${idx + 1}\n$start --> $end\n$text\n\n")
    }
    sb.toString()
  }

  private[mgr] def formatVttTimestamp(seconds: String): String = {
    try {
      val totalSecs = seconds.toDouble
      val h = (totalSecs / 3600).toInt
      val m = ((totalSecs % 3600) / 60).toInt
      val s = (totalSecs % 60).toInt
      val ms = ((totalSecs % 1) * 1000).toInt
      f"$h%02d:$m%02d:$s%02d.$ms%03d"
    } catch {
      case _: NumberFormatException => seconds
    }
  }

  private[mgr] def escapeJson(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

  private def writeToTempFile(fileName: String, content: String): File = {
    val tempDir = new File(Platform.getString("content.upload.temp_location", "/tmp/content"))
    if (!tempDir.exists()) tempDir.mkdirs()
    val file = new File(tempDir, fileName)
    FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8)
    file
  }

  private[mgr] def toBool(v: AnyRef): Boolean = v match {
    case jb: java.lang.Boolean => jb.booleanValue()
    case s: String => s.equalsIgnoreCase("true")
    case _ => false
  }
}
