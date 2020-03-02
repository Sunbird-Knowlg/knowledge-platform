package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}

import scala.concurrent.{ExecutionContext, Future}

object HtmlMimeTypeMgrImpl extends BaseMimeTypeManager with MimeTypeManager {
	override def upload(objectId: String, node: Node, uploadFile: File)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, uploadFile)
		isValidPackageStructure(uploadFile, List[String]("index.html"))
		//TODO: Upload File to Cloud. Need Extraction utility for snapshot. slug should be false.
		Future {Map[String, AnyRef]("identifier" -> objectId, "artifactUrl" -> "", "size" -> getFileSize(uploadFile).asInstanceOf[AnyRef])}
	}

	override def upload(objectId: String, node: Node, fileUrl: String)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, fileUrl)
		val file = copyURLToFile(objectId, fileUrl);
		upload(objectId, node, file)
	}

}
