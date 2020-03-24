package org.sunbird.mimetype.mgr

import java.io.File
import org.sunbird.graph.dac.model.Node
import scala.concurrent.{ExecutionContext, Future}

trait MimeTypeManager {

	@throws[Exception]
	def upload(objectId: String, node: Node, uploadFile: File, filePath: String)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]]

	@throws[Exception]
	def upload(objectId: String, node: Node, fileUrl: String, filePath: String)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]]

}
