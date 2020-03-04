package org.sunbird.mimetype.mgr

import java.io.File
import org.sunbird.graph.dac.model.Node
import scala.concurrent.{ExecutionContext, Future}

trait MimeTypeManager {

	@throws[Exception]
	def upload(objectId: String, node: Node, uploadFile: File)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]]

	@throws[Exception]
	def upload(objectId: String, node: Node, fileUrl: String)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]]

}
