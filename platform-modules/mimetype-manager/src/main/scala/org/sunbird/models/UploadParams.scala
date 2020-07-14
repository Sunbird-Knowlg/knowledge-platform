package org.sunbird.models

import play.api.mvc.QueryStringBindable


case class UploadParams(fileFormat: String = "")

object UploadParams {
    implicit def queryStringBinder(implicit stringBinder: QueryStringBindable[String]) = new QueryStringBindable[UploadParams] {
        override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, UploadParams]] = {
            for {
                fileFormat <- stringBinder.bind("fileFormat", params)
            } yield {
                fileFormat match {
                    case Right(fileFormat) => Right(UploadParams(fileFormat))
                    case _ => Left("Unable to bind UploadParams")
                }
            }
        }
        override def unbind(key: String, params: UploadParams): String = {
            stringBinder.unbind("fileFormat", params.fileFormat)
        }
    }
}
