package org.sunbird.models

import play.api.mvc.QueryStringBindable


case class UploadParams(fileFormat: String = "", validation: Boolean = true)

object UploadParams {
    implicit def queryStringBinder(implicit stringBinder: QueryStringBindable[String], booleanBinder: QueryStringBindable[Boolean]) = new QueryStringBindable[UploadParams] {
        override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, UploadParams]] = {
            for {
                fileFormat <- stringBinder.bind("fileFormat", params)
                validation <- booleanBinder.bind("validation", params)
            } yield {
                (fileFormat, validation) match {
                    case (Right(fileFormat), Right(validation)) => Right(UploadParams(fileFormat, validation))
                    case _ => Left("Unable to bind UploadParams")
                }
            }
        }

        override def unbind(key: String, params: UploadParams): String = {
            stringBinder.unbind("fileFormat", params.fileFormat) + "&" + booleanBinder.unbind("validation", params.validation)
        }
    }
}
