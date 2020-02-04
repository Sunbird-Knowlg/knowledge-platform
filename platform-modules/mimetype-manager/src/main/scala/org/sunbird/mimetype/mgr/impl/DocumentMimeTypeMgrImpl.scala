package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}

import scala.concurrent.{ExecutionContext, Future}

object DocumentMimeTypeMgrImpl extends BaseMimeTypeManager with MimeTypeManager {

	override def upload(objectId: String, node: Node, uploadFile: File)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, uploadFile)
		val result:Array[String] = uploadArtifactToCloud(uploadFile,objectId)
		Future{Map("identifier"->objectId, "node_id"->objectId, "content_url"->result(1), "artifactUrl"->result(1))}
	}

	override def upload(objectId: String, node: Node, fileUrl: String)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, fileUrl)
		Future {Map[String, AnyRef]("identifier" -> objectId, "artifactUrl" -> fileUrl)}
	}

}
