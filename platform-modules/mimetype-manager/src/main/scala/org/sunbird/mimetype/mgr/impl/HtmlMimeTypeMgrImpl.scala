package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}

import scala.concurrent.{ExecutionContext, Future}

object HtmlMimeTypeMgrImpl extends BaseMimeTypeManager with MimeTypeManager {
	override def upload(objectId: String, node: Node, uploadFile: File)(implicit ec: ExecutionContext): Future[Map[String, Any]] = ???

	override def upload(objectId: String, node: Node, fileUrl: String)(implicit ec: ExecutionContext): Future[Map[String, Any]] = ???

}
