package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}

import scala.concurrent.{ExecutionContext, Future}

object HtmlMimeTypeMgrImpl extends BaseMimeTypeManager with MimeTypeManager {
	override def upload(objectId: String, node: Node, uploadFile: File)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, uploadFile)
		isValidPackageStructure(uploadFile, List[String]("index.html"))
		Future {Map[String, AnyRef]("identifier" -> objectId, "artifactUrl" -> "", "size" -> getFileSize(uploadFile).asInstanceOf[AnyRef])}
	}

	override def upload(objectId: String, node: Node, fileUrl: String)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = ???

}
